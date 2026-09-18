package ir.weirdnet.client.ui.addprofile

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.weirdnet.client.core.DeepLinkRepository
import ir.weirdnet.client.core.parser.ConfigParser
import ir.weirdnet.client.ui.qr.QrScanScreen
import ir.weirdnet.client.ui.theme.BrandCrimson
import ir.weirdnet.client.ui.theme.BrandIceBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ImportMode { MENU, LINK, QR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProfileScreen(
    viewModel: AddProfileViewModel = viewModel(factory = ir.weirdnet.client.ui.weirdNetViewModelFactory()),
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    var mode by remember { mutableStateOf(ImportMode.MENU) }
    var linkText by remember { mutableStateOf("") }
    var clipboardSuggestion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        clipboardSuggestion = readClipboardIfImportable(context)
    }

    // Consumes whatever MainActivity deposited from a tapped vless://... link or
    // an opened .conf file (see DeepLinkRepository's doc comment). Keyed on the
    // flow's value, not Unit, so this also fires if a second deep link arrives
    // while this screen is already on-screen, not just on first composition.
    val pendingDeepLink by DeepLinkRepository.pending.collectAsState()
    LaunchedEffect(pendingDeepLink) {
        val payload = pendingDeepLink ?: return@LaunchedEffect
        DeepLinkRepository.consume()
        viewModel.parseInput(payload)
    }

    LaunchedEffect(state) {
        if (state is AddProfileUiState.Saved) onDone()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Reading is done off the main thread: Storage Access Framework
            // document providers (Google Drive, other cloud-backed pickers) can
            // perform real network I/O here, which would otherwise risk an ANR.
            coroutineScope.launch {
                val fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                val content = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (content != null) {
                    viewModel.parseInput(content, fileName)
                } else {
                    viewModel.reportFileReadError()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add profile", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (mode == ImportMode.MENU) onBack() else mode = ImportMode.MENU
                    }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state is AddProfileUiState.Preview -> ProfilePreviewCard(
                    state = state as AddProfileUiState.Preview,
                    onNameChange = viewModel::updatePreviewName,
                    onSave = viewModel::confirmSave,
                    onCancel = viewModel::reset
                )
                state is AddProfileUiState.Error -> ImportErrorCard(
                    error = (state as AddProfileUiState.Error),
                    onDismiss = viewModel::reset
                )
                mode == ImportMode.QR -> QrScanScreen(
                    onPayloadScanned = { payload -> viewModel.parseInput(payload) },
                    onCancel = { mode = ImportMode.MENU }
                )
                mode == ImportMode.LINK -> LinkImportForm(
                    linkText = linkText,
                    onLinkTextChange = { linkText = it },
                    onSubmit = { viewModel.parseInput(linkText) }
                )
                else -> ImportMenu(
                    clipboardSuggestion = clipboardSuggestion,
                    onLinkClick = { mode = ImportMode.LINK },
                    onQrClick = { mode = ImportMode.QR },
                    onFileClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    onClipboardImport = { clipboardSuggestion?.let { viewModel.parseInput(it) } }
                )
            }
        }
    }
}

@Composable
private fun ImportMenu(
    clipboardSuggestion: String?,
    onLinkClick: () -> Unit,
    onQrClick: () -> Unit,
    onFileClick: () -> Unit,
    onClipboardImport: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (clipboardSuggestion != null) {
            ImportOptionCard(
                icon = Icons.Filled.ContentPaste,
                title = "Import from clipboard",
                subtitle = "A configuration link was detected on your clipboard",
                onClick = onClipboardImport,
                highlighted = true
            )
        }
        ImportOptionCard(Icons.Filled.Link, "Paste a link", "vless://, vmess://, trojan://, or ss://", onLinkClick)
        ImportOptionCard(Icons.Filled.QrCodeScanner, "Scan a QR code", "Use your camera to scan a configuration", onQrClick)
        ImportOptionCard(Icons.Filled.FileOpen, "Import a file", "WireGuard .conf or other supported files", onFileClick)
    }
}

@Composable
private fun ImportOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) BrandIceBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(18.dp),
        onClick = onClick
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = BrandIceBlue, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LinkImportForm(linkText: String, onLinkTextChange: (String) -> Unit, onSubmit: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Paste your configuration link below", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = linkText,
            onValueChange = onLinkTextChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            placeholder = { Text("vless://...") }
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSubmit, enabled = linkText.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

@Composable
private fun ProfilePreviewCard(
    state: AddProfileUiState.Preview,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val profile = state.profile
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Review before saving", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = profile.name,
            onValueChange = onNameChange,
            label = { Text("Profile name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                DetailRow("Protocol", profile.protocol.displayName)
                DetailRow("Server", profile.server)
                DetailRow("Port", profile.port.toString())
                DetailRow("Transport", profile.transport.label)
                DetailRow("Security", profile.security.name)
                // Credentials are never shown here -- requirement #6.
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save profile") }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ImportErrorCard(error: AddProfileUiState.Error, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(error.error.message, style = MaterialTheme.typography.titleMedium, color = BrandCrimson)
        error.error.suggestion?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDismiss) { Text("Try again") }
    }
}

private fun readClipboardIfImportable(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.text?.toString() ?: return null
    return if (ConfigParser.looksImportable(text)) text else null
}
