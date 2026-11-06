package com.example.esp32_servo

import android.content.Context
import com.example.esp32_servo.ble.BleManager
import com.example.esp32_servo.data.BleServoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Ręczne wstrzykiwanie zależności – przy jednym repozytorium framework DI byłby zbędny.
 * Przechowuje wyłącznie kontekst aplikacji, nigdy Activity.
 */
class AppContainer(context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val bleManager = BleManager(context.applicationContext, applicationScope)

    val servoRepository = BleServoRepository(bleManager, applicationScope)
}
