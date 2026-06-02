package com.autodict

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.autodict.ui.navigation.AutodictNavHost
import com.autodict.ui.theme.AutodictTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AutodictTheme {
                AutodictNavHost()
            }
        }
    }
}
