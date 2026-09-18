package ir.weirdnet.client.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.weirdnet.client.BuildConfig
import ir.weirdnet.client.R

private data class OpenSourceComponent(
    val name: String,
    val license: String,
    val notice: String
)

private val openSourceComponents = listOf(
    OpenSourceComponent(
        "WireGuard for Android (com.wireguard.android:tunnel)",
        "Apache License 2.0",
        "Copyright (C) WireGuard LLC. Used under the Apache-2.0 license; WEIRDNET does not claim authorship."
    ),
    OpenSourceComponent(
        "Jetpack Compose & AndroidX libraries",
        "Apache License 2.0",
        "Copyright (C) The Android Open Source Project."
    ),
    OpenSourceComponent(
        "Kotlin & kotlinx.coroutines",
        "Apache License 2.0",
        "Copyright (C) JetBrains s.r.o. and contributors."
    ),
    OpenSourceComponent(
        "ML Kit Barcode Scanning",
        "Google APIs Terms of Service / Apache License 2.0 (client libraries)",
        "Copyright (C) Google LLC."
    ),
    OpenSourceComponent(
        "CameraX",
        "Apache License 2.0",
        "Copyright (C) The Android Open Source Project."
    ),
    OpenSourceComponent(
        "Accompanist Permissions",
        "Apache License 2.0",
        "Copyright (C) The Android Open Source Project."
    ),
    OpenSourceComponent(
        "AndroidLibXrayLite",
        "LGPL-3.0",
        "Copyright (C) 2dust and contributors. Provides the Xray-core engine used for VLESS/VMess/Trojan/Shadowsocks profiles; added as a local AAR (see docs/XRAY_INTEGRATION.md)."
    ),
    OpenSourceComponent(
        "Xray-core",
        "Mozilla Public License 2.0",
        "Copyright (C) Xray-core / Project X contributors. Bundled inside the AndroidLibXrayLite AAR above; WEIRDNET does not modify or redistribute its source."
    ),
    OpenSourceComponent(
        "hev-socks5-tunnel",
        "MIT License",
        "Copyright (C) hev and contributors. Provides the TUN-to-SOCKS5 bridge used to route Xray-based tunnels; added as a local AAR (see docs/XRAY_INTEGRATION.md)."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("About", fontWeight = FontWeight.SemiBold) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.weirdnet_logo),
                        contentDescription = "WEIRDNET",
                        modifier = Modifier.size(120.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("WEIRDNET", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))
                }
            }

            item {
                Text(
                    "OPEN SOURCE LICENSES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(openSourceComponents) { component ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(component.name, style = MaterialTheme.typography.titleMedium)
                        Text(component.license, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(component.notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "WEIRDNET does not collect analytics, does not phone home, and stores all profiles and settings only on this device. This build has no account system, payment system, or backend dependency.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
