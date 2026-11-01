package dev.joeherbert.availabliss

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.joeherbert.availabliss.core.auth.SessionManager
import dev.joeherbert.availabliss.core.live.LiveConnectionObserver
import dev.joeherbert.availabliss.core.push.Channel
import javax.inject.Inject

@HiltAndroidApp
class AvailablissApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var liveObserver: LiveConnectionObserver

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Channel.createAll(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(liveObserver)
        // Injecting SessionManager starts the session lifecycle (see its init block).
        sessionManager.authState
    }
}
