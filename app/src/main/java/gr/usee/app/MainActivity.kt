package gr.usee.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import gr.usee.app.ui.theme.UseeOfficialAppTheme
import gr.usee.app.ui.screens.UseeApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UseeOfficialAppTheme {
                UseeApp()
            }
        }
    }
}