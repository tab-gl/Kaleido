package com.tabgl.kaleido

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tabgl.kaleido.data.rememberAppSettings
import com.tabgl.kaleido.ui.KaleidoApp
import com.tabgl.kaleido.ui.theme.KaleidoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = rememberAppSettings()
            KaleidoTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                KaleidoApp(settings = settings)
            }
        }
    }
}
