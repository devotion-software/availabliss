package dev.joeherbert.availabliss

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.joeherbert.availabliss.core.auth.SessionManager
import dev.joeherbert.availabliss.core.live.LiveConnectionObserver
import dev.joeherbert.availabliss.core.push.Channel
import javax.inject.Inject

@HiltAndroidApp
class AvailablissApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var liveObserver: LiveConnectionObserver

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** Coil's shared loader, with GIFs animating (chat attachments). Network comes from coil-network-okhttp. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .build()

    override fun onCreate() {
        super.onCreate()
        Channel.createAll(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(liveObserver)
        // Injecting SessionManager starts the session lifecycle (see its init block).
        sessionManager.authState
    }
}
