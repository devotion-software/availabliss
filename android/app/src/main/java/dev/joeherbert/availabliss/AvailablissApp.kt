package dev.joeherbert.availabliss

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import dagger.hilt.android.HiltAndroidApp
import dev.joeherbert.availabliss.core.auth.SessionManager
import dev.joeherbert.availabliss.core.common.ApplicationScope
import dev.joeherbert.availabliss.core.common.CrashReporting
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveConnectionObserver
import dev.joeherbert.availabliss.core.push.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AvailablissApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var liveObserver: LiveConnectionObserver
    @Inject lateinit var users: UserRepository

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** Coil's shared loader, with GIFs animating (chat attachments). Network comes from coil-network-okhttp. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .build()

    override fun onCreate() {
        // Before anything else (Hilt's injection included), so a crash while starting up is reported.
        CrashReporting.start(this)
        super.onCreate()
        // The signed-in user's id goes with Sentry events; nothing else about them.
        appScope.launch {
            users.user.map { it?.id }.distinctUntilChanged().collect { CrashReporting.setUser(it) }
        }
        Channel.createAll(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(liveObserver)
        // Injecting SessionManager starts the session lifecycle (see its init block).
        sessionManager.authState
    }
}
