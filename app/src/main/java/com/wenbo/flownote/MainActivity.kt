package com.wenbo.flownote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.wenbo.flownote.di.LocalAppContainer
import com.wenbo.flownote.navigation.FlowNoteApp
import com.wenbo.flownote.ui.theme.FlowNoteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as FlowNoteApplication).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                FlowNoteTheme {
                    FlowNoteApp()
                }
            }
        }
    }
}
