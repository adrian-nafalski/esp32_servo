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

        // Systemowy splash screen (API 31+) wyświetla się sam na podstawie Theme.Servo
        // i znika, gdy aplikacja narysuje pierwszą klatkę. Niczego tu nie wstrzymujemy –
        // dodajemy jedynie krótkie wygaszenie zamiast nagłego cięcia. Przy wyłączonych
        // animacjach systemowych ViewPropertyAnimator kończy się natychmiast.
        splashScreen.setOnExitAnimationListener { splashView ->
            splashView.animate()
                .alpha(0f)
                .setDuration(SPLASH_EXIT_DURATION_MS)
                .withEndAction { splashView.remove() }
                .start()
        }

        enableEdgeToEdge()
        setContent {
            ServoTheme {
                val viewModel: ServoViewModel = viewModel(factory = ServoViewModel.Factory)
                ServoRoute(viewModel)
            }
        }
    }

    private companion object {
        const val SPLASH_EXIT_DURATION_MS = 180L
    }
}
