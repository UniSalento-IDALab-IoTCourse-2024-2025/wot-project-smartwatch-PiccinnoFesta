package com.example.iotwificlient.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

@Composable
fun WearAppTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        content()
    }
}