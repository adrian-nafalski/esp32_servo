package com.example.esp32_servo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.esp32_servo.ui.ServoRoute
import com.example.esp32_servo.ui.ServoViewModel
import com.example.esp32_servo.ui.theme.ServoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            ServoTheme {
                val viewModel: ServoViewModel = viewModel(factory = ServoViewModel.Factory)
                ServoRoute(viewModel)
            }
        }
    }
}
