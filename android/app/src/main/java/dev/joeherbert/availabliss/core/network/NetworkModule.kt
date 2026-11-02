package dev.joeherbert.availabliss.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.joeherbert.availabliss.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton

/** OkHttp client for long-lived streams (SSE): no read timeout. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamingClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val PLANNER_PATH_SUFFIX = "/planner"
    private const val PLANNER_TIMEOUT_SECONDS = 90


    /**
     * Shared JSON config. `encodeDefaults = false` omits optional request fields left at
     * their default (`= null`), while `explicitNulls = true` still sends nulls for
     * required-nullable fields that have no default (e.g. `Requirement.window`).
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = true
        isLenient = true
    }

    @Provides
    @Singleton
    @Named("baseUrl")
    fun provideBaseUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/".toHttpUrl()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        cookieJar: PasskeyCookieJar,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .addInterceptor(authInterceptor)
        // The AI planner can take 20-60s to think; everything else keeps the 30s timeout.
        .addInterceptor { chain ->
            val slow = chain.request().url.encodedPath.endsWith(PLANNER_PATH_SUFFIX)
            if (slow) chain.withReadTimeout(PLANNER_TIMEOUT_SECONDS, TimeUnit.SECONDS).proceed(chain.request()) else chain.proceed(chain.request())
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                        redactHeader(BetterAuthApi.AUTH_TOKEN_HEADER)
                    },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    @StreamingClient
    fun provideStreamingClient(client: OkHttpClient): OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json, @Named("baseUrl") baseUrl: HttpUrl): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): AvailablissApi = retrofit.create(AvailablissApi::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): BetterAuthApi = retrofit.create(BetterAuthApi::class.java)
}
