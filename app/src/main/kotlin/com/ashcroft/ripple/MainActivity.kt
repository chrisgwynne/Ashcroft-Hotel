package com.ashcroft.ripple

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ashcroft.ripple.core.designsystem.RippleTheme
import com.ashcroft.ripple.navigation.RippleNavHost
import dagger.hilt.android.AndroidEntryPoint

/** The single Activity; Compose owns everything above it. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RippleTheme {
                RippleNavHost()
            }
        }
    }
}
