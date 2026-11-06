package com.example.esp32_servo.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/** Uprawnienia runtime dla Androida 12+ (minSdk = 31, brak ścieżek dla starszych wersji). */
object BlePermissions {

    val required: Array<String> = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )

    fun granted(context: Context): Boolean = required.all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
}
