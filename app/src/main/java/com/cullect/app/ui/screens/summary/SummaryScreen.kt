package com.cullect.app.ui.screens.summary

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
import com.cullect.app.ui.screens.swipe.SummaryUiState
import com.cullect.app.ui.strings.LocalAppStrings

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
        // Deliberately no "N bytes freed" here — these files are still sitting in Trash, on disk,
        // until it's actually emptied (see TrashFolderScreen for where a real freed-bytes figure
        // belongs). Session counts above are accurate as-is; a byte figure here would just be a
        // number that describes nothing that's actually happened to storage yet.
        if (summary.itemCount > 0) {
            Text(
                strings.pendingTrashCleanup,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Button(onClick = onDone, modifier = Modifier.padding(top = 32.dp)) {
            Text(strings.done)
        }
    }
}
