package ir.weirdnet.client.ui.home

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.weirdnet.client.R
import ir.weirdnet.client.core.ConnectionState
import ir.weirdnet.client.data.model.VpnProfile
import ir.weirdnet.client.service.WeirdNetVpnService
import ir.weirdnet.client.ui.theme.BrandCrimson
import ir.weirdnet.client.ui.theme.BrandIceBlue
import ir.weirdnet.client.ui.theme.BrandSteelDim
import ir.weirdnet.client.ui.theme.BrandSuccess
import ir.weirdnet.client.ui.theme.BrandWarning
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = ir.weirdnet.client.ui.weirdNetViewModelFactory()),
    onAddProfileClick: () -> Unit,
    onManageProfilesClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            state.selectedProfile?.let { startTunnel(context, it) }
        }
        // If denied, ConnectionState simply stays Disconnected -- WeirdNetError.VpnPermissionDenied
        // is surfaced the next time the user taps Connect and Android denies again.
    }

    fun requestConnect(profile: VpnProfile) {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startTunnel(context, profile)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        BrandHeader()
        Spacer(Modifier.height(28.dp))
        StatusPill(state.connectionState)
        Spacer(Modifier.height(36.dp))

        ConnectButton(
            connectionState = state.connectionState,
            enabled = state.selectedProfile != null,
            onClick = {
                when (state.connectionState) {
                    is ConnectionState.Connected, is ConnectionState.Connecting ->
                        stopTunnel(context)
                    is ConnectionState.Disconnecting -> {
                        // Deliberately a no-op -- see the comment on isClickable
                        // in ConnectButton for why this state must not fall
                        // through to requestConnect() below.
                    }
                    else -> state.selectedProfile?.let { requestConnect(it) }
                }
            }
        )

        Spacer(Modifier.height(36.dp))

        var showProfilePicker by remember { mutableStateOf(false) }
        val canSwitchProfile = state.connectionState is ConnectionState.Disconnected ||
            state.connectionState is ConnectionState.Error

        CurrentProfileCard(
            profile = state.selectedProfile,
            onClick = {
                if (canSwitchProfile) {
                    showProfilePicker = true
                } else {
                    // Connected/connecting/disconnecting: switching the active
                    // profile from here would be ambiguous (switch to what's
                    // actually running, or just change a selection?), so this
                    // routes to full profile management instead, where
                    // connect/disconnect is unambiguous per-row.
                    onManageProfilesClick()
                }
            },
            onAddProfileClick = onAddProfileClick
        )

        if (showProfilePicker) {
            ProfilePickerDialog(
                profiles = state.allProfiles,
                selectedId = state.selectedProfile?.id,
                onSelect = {
                    viewModel.selectProfile(it)
                    showProfilePicker = false
                },
                onManageProfilesClick = {
                    showProfilePicker = false
                    onManageProfilesClick()
                },
                onDismiss = { showProfilePicker = false }
            )
        }

        Spacer(Modifier.height(20.dp))

        StatsRow(state = state)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = R.drawable.weirdnet_logo),
            contentDescription = "WEIRDNET",
            modifier = Modifier.height(40.dp)
        )
    }
}

@Composable
private fun StatusPill(state: ConnectionState) {
    val (label, color) = when (state) {
        is ConnectionState.Disconnected -> "DISCONNECTED" to BrandSteelDim
        is ConnectionState.Connecting -> "CONNECTING…" to BrandWarning
        is ConnectionState.Connected -> "CONNECTED" to BrandSuccess
        is ConnectionState.Disconnecting -> "DISCONNECTING…" to BrandWarning
        is ConnectionState.Error -> "CONNECTION ERROR" to BrandCrimson
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
    if (state is ConnectionState.Error) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.error.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.error.suggestion?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = BrandIceBlue)
        }
    }
}

@Composable
private fun ConnectButton(
    connectionState: ConnectionState,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val isConnecting = connectionState is ConnectionState.Connecting
    val isDisconnecting = connectionState is ConnectionState.Disconnecting
    val isTransitioning = isConnecting || isDisconnecting
    val isConnected = connectionState is ConnectionState.Connected
    val isError = connectionState is ConnectionState.Error

    val ringColor = when {
        isConnected -> BrandSuccess
        isError -> BrandCrimson
        isTransitioning -> BrandWarning
        else -> BrandIceBlue
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale"
    )
    val appliedScale = if (isTransitioning) pulseScale else 1f

    // Disconnecting is intentionally NOT clickable: with it enabled, tapping the
    // button while a disconnect is already in flight fell through to the "start
    // a new connection" branch in HomeScreen's onClick and silently reconnected
    // instead of doing nothing, which is not what a user tapping "WAIT…" expects.
    // Connecting stays clickable so the user can cancel an in-progress attempt.
    val isClickable = (enabled && !isDisconnecting) || isConnected || isConnecting

    Box(
        modifier = Modifier
            .size(180.dp)
            .scale(appliedScale)
            .clip(CircleShape)
            .background(ringColor.copy(alpha = if (isConnected) 0.16f else 0.10f))
            .border(3.dp, ringColor, CircleShape)
            .clickable(enabled = isClickable) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = when {
                    isConnected -> Icons.Filled.Power
                    isError -> Icons.Filled.CloudOff
                    else -> Icons.Filled.PowerOff
                },
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    isConnected -> "DISCONNECT"
                    isTransitioning -> "WAIT…"
                    else -> "CONNECT"
                },
                style = MaterialTheme.typography.labelLarge,
                color = ringColor
            )
        }
    }
}

@Composable
private fun CurrentProfileCard(
    profile: VpnProfile?,
    onClick: () -> Unit,
    onAddProfileClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (profile != null) onClick() else onAddProfileClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (profile != null) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${profile.protocol.displayName} • ${profile.displayAddress()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("No profile selected", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Tap to add your first profile",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                if (profile != null) "Change" else "Add",
                color = BrandIceBlue,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun StatsRow(state: HomeUiState) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    val connected = state.connectionState as? ConnectionState.Connected

    LaunchedEffect(connected?.connectedAtEpochMs) {
        if (connected == null) {
            elapsedSeconds = 0
            return@LaunchedEffect
        }
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - connected.connectedAtEpochMs) / 1000
            delay(1000)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(Modifier.weight(1f), "Download", formatSpeed(state.downloadSpeedBps), formatBytes(state.totalDownloaded))
        StatCard(Modifier.weight(1f), "Upload", formatSpeed(state.uploadSpeedBps), formatBytes(state.totalUploaded))
        StatCard(Modifier.weight(1f), "Duration", formatDuration(elapsedSeconds), null)
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, primary: String, secondary: String?) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            secondary?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfilePickerDialog(
    profiles: List<VpnProfile>,
    selectedId: String?,
    onSelect: (VpnProfile) -> Unit,
    onManageProfilesClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a profile") },
        text = {
            Column {
                profiles.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(profile) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = profile.id == selectedId, onClick = { onSelect(profile) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${profile.protocol.displayName} • ${profile.displayAddress()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onManageProfilesClick) { Text("Manage profiles") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun startTunnel(context: android.content.Context, profile: VpnProfile) {
    val intent = Intent(context, WeirdNetVpnService::class.java)
        .setAction(WeirdNetVpnService.ACTION_CONNECT)
        .putExtra(WeirdNetVpnService.EXTRA_PROFILE_ID, profile.id)
    context.startForegroundService(intent)
}

private fun stopTunnel(context: android.content.Context) {
    val intent = Intent(context, WeirdNetVpnService::class.java)
        .setAction(WeirdNetVpnService.ACTION_DISCONNECT)
    context.startService(intent)
}

private fun formatSpeed(bps: Long): String {
    val kbps = bps / 1024.0
    return if (kbps < 1024) "%.1f KB/s".format(kbps) else "%.2f MB/s".format(kbps / 1024.0)
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1024) "%.2f MB".format(mb) else "%.2f GB".format(mb / 1024.0)
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
