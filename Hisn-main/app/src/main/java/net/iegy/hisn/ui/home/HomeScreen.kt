@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package net.iegy.hisn.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.iegy.hisn.BrowserHost
import net.iegy.hisn.R
import net.iegy.hisn.browser.AppLinks
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.data.ShortcutEntity
import net.iegy.hisn.settings.Settings
import net.iegy.hisn.ui.browser.BrowserNav
import net.iegy.hisn.ui.components.ConfirmDialog
import net.iegy.hisn.ui.components.HisnLogo
import net.iegy.hisn.ui.components.TextInputDialog
import net.iegy.hisn.ui.components.UrlText
import net.iegy.hisn.viewmodel.BrowserViewModel

@Composable
fun HomeScreen(
    vm: BrowserViewModel,
    settings: Settings,
    onSearchClick: () -> Unit,
    onOpen: (String) -> Unit,
    nav: BrowserNav,
    host: BrowserHost,
) {
    val isPrivate = vm.isPrivate
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val bookmarks by vm.homeBookmarks.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<ShortcutEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ShortcutEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 12.dp)) {
                HisnLogo(size = 76.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.app_name_display),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        item {
            Surface(
                onClick = onSearchClick,
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = stringResource(if (isPrivate) R.string.address_hint_private else R.string.address_hint),
                        modifier = Modifier.padding(start = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Embedded Rafiqi companion (local assets + prayer notifications).
        if (!isPrivate) {
            item {
                Surface(
                    onClick = { onOpen(net.iegy.hisn.rafiqi.RafiqiUrls.HOME) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "☪",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.rafiqi_card_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = stringResource(R.string.rafiqi_card_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                        Text(
                            text = stringResource(R.string.rafiqi_open),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (isPrivate) {
            item { PrivateBanner() }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { QuickAction(Icons.Filled.Download, R.string.title_downloads, nav.openDownloads) }
                    item { QuickAction(Icons.Filled.History, R.string.title_history, nav.openHistory) }
                    item { QuickAction(Icons.Filled.Bookmark, R.string.title_bookmarks, nav.openBookmarks) }
                    item { QuickAction(Icons.Filled.VisibilityOff, R.string.action_new_private_tab) { host.openPrivateWindow() } }
                    item { QuickAction(Icons.Filled.QrCodeScanner, R.string.menu_qr_scanner) { onOpen(AppLinks.QR_TOOL) } }
                    item { QuickAction(Icons.Filled.Settings, R.string.title_settings, nav.openSettings) }
                }
            }
        }

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 4,
            ) {
                shortcuts.forEach { shortcut ->
                    ShortcutTile(
                        title = shortcut.title,
                        onClick = { onOpen(shortcut.url) },
                        onLongClick = if (isPrivate) null else ({ actionsFor = shortcut }),
                    )
                }
                if (!isPrivate) {
                    AddTile(onClick = { adding = true })
                }
            }
        }

        if (!isPrivate && bookmarks.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.title_bookmarks), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(bookmarks, key = { it.id }) { b ->
                            AssistChip(
                                onClick = { onOpen(b.url) },
                                label = { Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp)) },
                            )
                        }
                    }
                }
            }
        }

        if (!isPrivate && settings.showRecent && recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.home_recent),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.setShowRecent(false) }) { Text(stringResource(R.string.action_hide)) }
                    TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.action_clear)) }
                }
            }
            items(recent, key = { it.id }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(entry.url) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Letter(entry.title.ifBlank { UrlResolver.displayHost(entry.url).orEmpty() }, 36)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(entry.title.ifBlank { entry.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        UrlText(UrlResolver.displayHost(entry.url) ?: entry.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            // Discreet creator credit; tapping opens the creator's site in a tab.
            Text(
                text = stringResource(R.string.credit_designed_by),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen("https://iegy.net/") }
                    .padding(8.dp),
            )
        }
    }

    if (adding) {
        TextInputDialog(
            title = stringResource(R.string.shortcut_add),
            firstLabel = stringResource(R.string.field_title),
            initialFirst = "",
            secondLabel = stringResource(R.string.field_url),
            secondIsUrl = true,
            validate = { _, url -> UrlResolver.normalizeWebUrl(url) != null },
            onConfirm = { title, url ->
                UrlResolver.normalizeWebUrl(url)?.let { vm.addShortcut(title, it) }
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
    editing?.let { shortcut ->
        TextInputDialog(
            title = stringResource(R.string.shortcut_edit),
            firstLabel = stringResource(R.string.field_title),
            initialFirst = shortcut.title,
            secondLabel = stringResource(R.string.field_url),
            initialSecond = shortcut.url,
            secondIsUrl = true,
            validate = { _, url -> UrlResolver.normalizeWebUrl(url) != null },
            onConfirm = { title, url ->
                UrlResolver.normalizeWebUrl(url)?.let { vm.updateShortcut(shortcut, title, it) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    actionsFor?.let { shortcut ->
        ShortcutActionsDialog(
            title = shortcut.title,
            onEdit = { editing = shortcut; actionsFor = null },
            onRemove = { vm.removeShortcut(shortcut.id); actionsFor = null },
            onMoveEarlier = { vm.moveShortcut(shortcut.id, -1); actionsFor = null },
            onMoveLater = { vm.moveShortcut(shortcut.id, +1); actionsFor = null },
            onDismiss = { actionsFor = null },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            message = stringResource(R.string.history_clear_all_message),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = { vm.clearAllHistory(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: Int, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

@Composable
private fun ShortcutTile(title: String, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Letter(title, 52)
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shortcut_add), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.action_add), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** First-letter avatar, used instead of favicons so no icon is fetched from anywhere. */
@Composable
fun Letter(text: String, sizeDp: Int) {
    val letter = text.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
    Box(
        modifier = Modifier.size(sizeDp.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = if (sizeDp >= 48) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun PrivateBanner() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    stringResource(R.string.private_mode),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                stringResource(R.string.private_banner_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.private_banner_limits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ShortcutActionsDialog(
    title: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                @Composable
                fun Item(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, run: () -> Unit) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = run).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(label, modifier = Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Item(stringResource(R.string.action_edit), Icons.Filled.Edit, onEdit)
                Item(stringResource(R.string.shortcut_move_earlier), Icons.AutoMirrored.Filled.ArrowBack, onMoveEarlier)
                Item(stringResource(R.string.shortcut_move_later), Icons.AutoMirrored.Filled.ArrowForward, onMoveLater)
                Item(stringResource(R.string.action_remove), Icons.Filled.Delete, onRemove)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
