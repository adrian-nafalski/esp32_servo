package com.example.esp32_servo

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class ServoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Pierwszy plan / tło całej aplikacji – NIE pojedynczej Activity. Dzięki temu
        // zmiana konfiguracji (obrót, motyw) nie jest traktowana jak przejście do tła.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.servoRepository.onAppForegroundChanged(inForeground = true)
            }

            override fun onStop(owner: LifecycleOwner) {
                container.servoRepository.onAppForegroundChanged(inForeground = false)
            }
        })
    }
}
