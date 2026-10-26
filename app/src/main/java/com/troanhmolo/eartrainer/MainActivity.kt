package com.troanhmolo.eartrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.troanhmolo.eartrainer.ui.navigation.EarTrainerNavHost
import com.troanhmolo.eartrainer.ui.theme.EarTrainerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EarTrainerTheme {
                EarTrainerNavHost()
            }
        }
    }
}
