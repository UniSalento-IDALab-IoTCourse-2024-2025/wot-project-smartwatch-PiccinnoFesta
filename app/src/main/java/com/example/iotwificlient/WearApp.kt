package com.example.iotwificlient

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.items

@Composable
fun WearApp() {
    MaterialTheme {
        ScalingLazyColumn(
            state = ScalingLazyListState(),
        ) {
            item {
                Text("Benvenuto su Pixel Watch!")
            }
            item {
                Text("L'invio dati avverrà in background.")
            }
        }
    }
}