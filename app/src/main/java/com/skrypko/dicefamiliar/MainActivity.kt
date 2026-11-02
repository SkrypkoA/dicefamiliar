package com.skrypko.dicefamiliar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.skrypko.dicefamiliar.ui.navigation.AppNavHost
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiceFamiliarTheme {
                AppNavHost()
            }
        }
    }
}
