@file:OptIn(ExperimentalMaterial3Api::class)

package net.iegy.hisn.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.iegy.hisn.R
import net.iegy.hisn.blocking.Allowlist
import net.iegy.hisn.ui.components.BackTopBar
import net.iegy.hisn.ui.components.EmptyState
import net.iegy.hisn.ui.components.TextInputDialog
import net.iegy.hisn.ui.components.UrlText
import net.iegy.hisn.viewmodel.SettingsViewModel

@Composable
fun AllowlistScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val hosts = settings.allowlist.sorted()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars)) {
        BackTopBar(title = stringResource(R.string.settings_allowlist), onBack = onBack)
        Box(Modifier.weight(1f)) {
            if (hosts.isEmpty()) {
                EmptyState(stringResource(R.string.allowlist_empty))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(hosts, key = { it }) { host ->
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            UrlText(host, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { vm.removeAllowed(host) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove))
                            }
                        }
                    }
                }
            }
            FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
            }
        }
    }

    if (adding) {
        TextInputDialog(
            title = stringResource(R.string.allowlist_add),
            firstLabel = stringResource(R.string.allowlist_field),
            initialFirst = "",
            secondIsUrl = true,
            validate = { v, _ -> hostFrom(v) != null },
            onConfirm = { v, _ ->
                hostFrom(v)?.let { vm.addAllowed(it) }
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

/** Accepts "example.com" or a full URL and returns the normalised host, or null if it is not a plausible host. */
private fun hostFrom(input: String): String? {
    val text = input.trim()
    if (text.isEmpty() || text.any { it.isWhitespace() }) return null
    val host = net.iegy.hisn.browser.UrlResolver.hostOf(text)
        ?: text.substringBefore('/').substringBefore('?').substringBefore(':')
    val normalized = Allowlist.normalize(host)
    return normalized.takeIf { it.contains('.') && it.length >= 3 }
}
