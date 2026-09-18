package ir.weirdnet.client.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.VpnProfile
import ir.weirdnet.client.ui.theme.BrandCrimson
import ir.weirdnet.client.ui.theme.BrandIceBlue
import ir.weirdnet.client.ui.theme.BrandSteel
import ir.weirdnet.client.ui.theme.BrandWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel = viewModel(factory = ir.weirdnet.client.ui.weirdNetViewModelFactory()),
    onAddProfileClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var profilePendingDelete by remember { mutableStateOf<VpnProfile?>(null) }
    var profilePendingRename by remember { mutableStateOf<VpnProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profiles", fontWeight = FontWeight.SemiBold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProfileClick, containerColor = BrandIceBlue) {
                Icon(Icons.Filled.Add, contentDescription = "Add profile", tint = androidx.compose.ui.graphics.Color.Black)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search profiles") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipRow(current = state.filter, onSelect = viewModel::setFilter)
            }

            Spacer(Modifier.height(8.dp))

            if (state.profiles.isEmpty()) {
                EmptyProfilesState(onAddProfileClick)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(state.profiles, key = { _, profile -> profile.id }) { index, profile ->
                        ProfileRow(
                            profile = profile,
                            canMoveUp = state.canReorder && index > 0,
                            canMoveDown = state.canReorder && index < state.profiles.lastIndex,
                            onFavoriteToggle = { viewModel.toggleFavorite(profile) },
                            onRename = { profilePendingRename = profile },
                            onDuplicate = { viewModel.duplicate(profile) },
                            onDelete = { profilePendingDelete = profile },
                            onMoveUp = { viewModel.moveProfile(profile, -1) },
                            onMoveDown = { viewModel.moveProfile(profile, 1) }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    profilePendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profilePendingDelete = null },
            title = { Text("Delete \"${profile.name}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(profile)
                    profilePendingDelete = null
                }) { Text("Delete", color = BrandCrimson) }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    profilePendingRename?.let { profile ->
        var newName by remember(profile.id) { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { profilePendingRename = null },
            title = { Text("Rename profile") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) viewModel.rename(profile, newName.trim())
                    profilePendingRename = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingRename = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FilterChipRow(current: ProfileFilter, onSelect: (ProfileFilter) -> Unit) {
    val labels = mapOf(
        ProfileFilter.ALL to "All",
        ProfileFilter.FAVORITES to "Favorites",
        ProfileFilter.WIREGUARD to "WireGuard",
        ProfileFilter.XRAY to "Xray"
    )
    labels.forEach { (filterValue, label) ->
        FilterChip(
            selected = current == filterValue,
            onClick = { onSelect(filterValue) },
            label = { Text(label) }
        )
    }
}

@Composable
private fun EmptyProfilesState(onAddProfileClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.VpnKeyOff, contentDescription = null, tint = BrandSteel, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("No profiles yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a profile via link, QR code, or file import to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAddProfileClick) { Text("Add profile") }
    }
}

@Composable
private fun ProfileRow(
    profile: VpnProfile,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onFavoriteToggle: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProtocolBadge(profile.protocol)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    profile.displayAddress(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    if (profile.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (profile.isFavorite) BrandWarning else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuExpanded = false; onDuplicate() })
                    if (canMoveUp) {
                        DropdownMenuItem(text = { Text("Move up") }, onClick = { menuExpanded = false; onMoveUp() })
                    }
                    if (canMoveDown) {
                        DropdownMenuItem(text = { Text("Move down") }, onClick = { menuExpanded = false; onMoveDown() })
                    }
                    DropdownMenuItem(text = { Text("Delete", color = BrandCrimson) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun ProtocolBadge(protocol: ProtocolType) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(BrandIceBlue.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            protocol.displayName.take(2).uppercase(),
            color = BrandIceBlue,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
