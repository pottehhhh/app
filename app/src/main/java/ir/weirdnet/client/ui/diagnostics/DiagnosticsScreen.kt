package ir.weirdnet.client.ui.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.weirdnet.client.core.ConnectionState
import ir.weirdnet.client.data.model.LogLevel
import ir.weirdnet.client.ui.theme.BrandCrimson
import ir.weirdnet.client.ui.theme.BrandSuccess
import ir.weirdnet.client.ui.theme.BrandWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = viewModel(factory = ir.weirdnet.client.ui.weirdNetViewModelFactory())) {
    val state by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var copiedFeedback by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Diagnostics", fontWeight = FontWeight.SemiBold) }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ConnectionSummaryCard(state.connectionState)

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(viewModel.buildDiagnosticsReport(state)))
                        copiedFeedback = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copiedFeedback) "Copied!" else "Copy diagnostics")
                }
                OutlinedButton(onClick = viewModel::clearLogs, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear logs")
                }
            }

            Text(
                "LOGS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(state.logs, key = { it.id }) { entry ->
                    LogLine(level = entry.level, tag = entry.tag, message = entry.message)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ConnectionSummaryCard(state: ConnectionState) {
    val profile = when (state) {
        is ConnectionState.Connected -> state.profile
        is ConnectionState.Connecting -> state.profile
        is ConnectionState.Disconnecting -> state.profile
        is ConnectionState.Error -> state.profile
        else -> null
    }
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Current profile", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(profile?.name ?: "None", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (profile != null) {
                Text("${profile.protocol.displayName} • ${profile.displayAddress()}", style = MaterialTheme.typography.bodyMedium)
            }
            if (state is ConnectionState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(state.error.message, color = BrandCrimson, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LogLine(level: LogLevel, tag: String, message: String) {
    val color = when (level) {
        LogLevel.ERROR -> BrandCrimson
        LogLevel.WARN -> BrandWarning
        LogLevel.INFO -> BrandSuccess
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        "[$tag] $message",
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.padding(vertical = 3.dp)
    )
}
