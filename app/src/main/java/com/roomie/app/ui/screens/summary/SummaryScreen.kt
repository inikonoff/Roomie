package com.roomie.app.ui.screens.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roomie.app.ui.screens.swipe.SummaryUiState
import com.roomie.app.ui.strings.LocalAppStrings
import com.roomie.app.util.formatBytes

@Composable
fun SummaryScreen(
    summary: SummaryUiState,
    onDone: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(strings.allCleanedUp, style = MaterialTheme.typography.headlineSmall)
        Text(
            strings.itemsRemoved(summary.itemCount),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            strings.bytesFreed(formatBytes(summary.freedBytes)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Button(onClick = onDone, modifier = Modifier.padding(top = 32.dp)) {
            Text(strings.done)
        }
    }
}
