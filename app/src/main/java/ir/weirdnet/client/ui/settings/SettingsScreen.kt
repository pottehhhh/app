package ir.weirdnet.client.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.weirdnet.client.BuildConfig
import ir.weirdnet.client.data.repository.AppTheme
import ir.weirdnet.client.ui.theme.BrandCrimson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = ir.weirdnet.client.ui.weirdNetViewModelFactory()),
    onAboutClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.SemiBold) }) }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            SectionHeader("General")
            SettingsSwitchRow(
                title = "Auto-connect on launch",
                subtitle = "Connect to your default profile when WEIRDNET opens",
                checked = state.settings.autoConnectOnLaunch,
                onCheckedChange = viewModel::setAutoConnect
            )
            SettingsSwitchRow(
                title = "Confirm before disconnecting",
                subtitle = "Ask for confirmation before ending an active session",
                checked = state.settings.confirmBeforeDisconnect,
                onCheckedChange = viewModel::setConfirmBeforeDisconnect
            )
            DefaultProfilePicker(
                profiles = state.profiles,
                selectedId = state.settings.defaultProfileId,
                onSelect = viewModel::setDefaultProfile
            )

            Divider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Appearance")
            ThemePicker(current = state.settings.theme, onSelect = viewModel::setTheme)

            Divider(Modifier.padding(vertical = 8.dp))
            SectionHeader("VPN")
            SettingsSwitchRow(
                title = "Allow IPv6",
                subtitle = "Route IPv6 traffic through the tunnel when the server supports it",
                checked = state.settings.ipv6Enabled,
                onCheckedChange = viewModel::setIpv6Enabled
            )
            SettingsSwitchRow(
                title = "Custom DNS",
                subtitle = if (state.settings.customDnsEnabled) "On -- using ${state.settings.customDns}" else "Use the server-provided DNS",
                checked = state.settings.customDnsEnabled,
                onCheckedChange = viewModel::setCustomDnsEnabled
            )
            if (state.settings.customDnsEnabled) {
                ClickableRow(
                    title = "DNS server",
                    subtitle = state.settings.customDns,
                    onClick = { showDnsDialog = true }
                )
            }
            InfoRow(
                title = "Kill switch",
                subtitle = "Android's VPN framework already blocks non-tunneled traffic while WEIRDNET is connecting or connected. For a persistent system-level kill switch, enable \"Always-on VPN\" and \"Block connections without VPN\" for WEIRDNET in Android's own Settings \u2192 Network \u2192 VPN screen."
            )

            Divider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Advanced")
            SettingsSwitchRow(
                title = "Debug logging",
                subtitle = "Keep detailed logs for troubleshooting (never includes secrets)",
                checked = state.settings.debugLoggingEnabled,
                onCheckedChange = viewModel::setDebugLogging
            )
            ClickableRow(title = "About WEIRDNET", subtitle = "Version ${BuildConfig.VERSION_NAME}", onClick = onAboutClick)
            ClickableRow(
                title = "Reset all settings",
                subtitle = "Restores every setting above to its default. Profiles are not deleted.",
                titleColor = BrandCrimson,
                onClick = { showResetConfirm = true }
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset all settings?") },
            text = { Text("Your saved VPN profiles will not be affected.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetAll(); showResetConfirm = false }) {
                    Text("Reset", color = BrandCrimson)
                }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showDnsDialog) {
        var dnsInput by remember { mutableStateOf(state.settings.customDns) }
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            title = { Text("Custom DNS server") },
            text = {
                OutlinedTextField(value = dnsInput, onValueChange = { dnsInput = it }, singleLine = true, placeholder = { Text("1.1.1.1") })
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setCustomDns(dnsInput.trim()); showDnsDialog = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDnsDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClickableRow(title: String, subtitle: String, titleColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DefaultProfilePicker(profiles: List<ir.weirdnet.client.data.model.VpnProfile>, selectedId: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = profiles.firstOrNull { it.id == selectedId }?.name ?: "None"
    Box(Modifier.fillMaxWidth()) {
        ClickableRow(title = "Default profile", subtitle = selectedName, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); expanded = false })
            profiles.forEach { profile ->
                DropdownMenuItem(text = { Text(profile.name) }, onClick = { onSelect(profile.id); expanded = false })
            }
        }
    }
}

@Composable
private fun ThemePicker(current: AppTheme, onSelect: (AppTheme) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTheme.entries.forEach { theme ->
            FilterChip(
                selected = current == theme,
                onClick = { onSelect(theme) },
                label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}
