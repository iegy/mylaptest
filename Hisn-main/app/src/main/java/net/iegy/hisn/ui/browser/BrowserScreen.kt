@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package net.iegy.hisn.ui.browser

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import net.iegy.hisn.browser.ScreenshotSaver
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Javascript
import net.iegy.hisn.browser.AppLinks
import net.iegy.hisn.browser.HomeShortcut
import net.iegy.hisn.ui.components.ConfirmDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import net.iegy.hisn.ui.components.UrlText
import net.iegy.hisn.viewmodel.Suggestion
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.iegy.hisn.BrowserHost
import net.iegy.hisn.R
import net.iegy.hisn.blocking.Allowlist
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.download.DownloadStart
import net.iegy.hisn.tabs.TabState
import net.iegy.hisn.ui.components.horizontalSwipe
import net.iegy.hisn.ui.home.HomeScreen
import net.iegy.hisn.viewmodel.BrowserViewModel

class BrowserNav(
    val openTabs: () -> Unit,
    val openBookmarks: () -> Unit,
    val openHistory: () -> Unit,
    val openDownloads: () -> Unit,
    val openSettings: () -> Unit,
    val openAbout: () -> Unit,
    val openGame: () -> Unit = {},
    val openPdf: (uri: String, title: String?) -> Unit = { _, _ -> },
)

@Composable
fun BrowserScreen(vm: BrowserViewModel, host: BrowserHost, nav: BrowserNav) {
    val model by vm.model.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bookmarked by vm.bookmarkedUrls.collectAsStateWithLifecycle()
    val tab = model.selected
    val isPrivate = vm.isPrivate
    val addressFocus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    var addressFocused by remember { mutableStateOf(false) }
    var findOpen by remember(tab?.id) { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // لقطة للجزء الظاهر من الصفحة: تُلتقط فوراً عند الضغط ثم تُحفظ في Pictures/Hisn.
    fun takeScreenshot(fullPage: Boolean) {
        val current = tab ?: return
        if (current.isHome) return
        if (fullPage) Toast.makeText(context, R.string.msg_screenshot_working, Toast.LENGTH_SHORT).show()
        vm.tabs.captureScreenshot(current.id, fullPage) { shot ->
            if (shot == null) {
                Toast.makeText(context, R.string.msg_screenshot_failed, Toast.LENGTH_SHORT).show()
                return@captureScreenshot
            }
            val bitmap = shot.bitmap
            val title = current.title.ifBlank { UrlResolver.hostOf(current.url).orEmpty() }
            fun save() {
                scope.launch {
                    val saved = ScreenshotSaver.save(context, bitmap, title)
                    bitmap.recycle()
                    val message = when {
                        saved == null -> R.string.msg_screenshot_failed
                        shot.truncated -> R.string.msg_screenshot_truncated
                        else -> R.string.msg_screenshot_saved
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
            val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q && !host.hasPermission(permission)) {
                host.requestPermissions(arrayOf(permission)) { granted ->
                    if (granted[permission] == true) save()
                    else {
                        bitmap.recycle()
                        Toast.makeText(context, R.string.msg_screenshot_permission, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                save()
            }
        }
    }

    fun closeFind() {
        findOpen = false
        tab?.let { vm.tabs.clearFind(it.id) }
    }
    // Leaving a tab (switch/close) removes its highlights; the bar itself resets through remember(tab?.id).
    DisposableEffect(tab?.id) {
        val leaving = tab?.id
        onDispose { leaving?.let { vm.tabs.clearFind(it) } }
    }

    BackHandler {
        when {
            findOpen -> closeFind()
            host.handleBackInFullscreen() -> Unit
            tab == null -> host.exitApp()
            !tab.isHome && tab.canGoBack -> vm.tabs.goBack(tab.id)
            !tab.isHome && tab.parentId != null && model.tabs.any { it.id == tab.parentId } -> vm.tabs.close(tab.id)
            !tab.isHome -> vm.tabs.goHome(tab.id)
            else -> host.exitApp()
        }
    }

    if (isPrivate && model.tabs.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) { host.closePrivateSession() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                tab == null -> Unit
                tab.isHome -> HomeScreen(
                    vm = vm,
                    settings = settings,
                    onSearchClick = {
                        // requestFocus alone moves the cursor but does not always raise the keyboard from a touch.
                        addressFocus.requestFocus()
                        keyboard?.show()
                    },
                    onOpen = { vm.tabs.navigate(tab.id, it) },
                    nav = nav,
                    host = host,
                )
                else -> WebContent(vm = vm, tab = tab)
            }
            if (tab != null && addressFocused && suggestions.isNotEmpty()) {
                SuggestionList(
                    items = suggestions,
                    onPick = { picked ->
                        focusManager.clearFocus()
                        vm.tabs.navigate(tab.id, picked.url)
                    },
                )
            }
        }

        if (tab != null) {
            if (findOpen && !tab.isHome) {
                FindBar(
                    tab = tab,
                    onQuery = { vm.tabs.findInPage(tab.id, it) },
                    onStep = { forward -> vm.tabs.findNext(tab.id, forward) },
                    onClose = ::closeFind,
                )
            }
            if (tab.isLoading && !tab.isHome) {
                LinearProgressIndicator(
                    progress = { tab.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            } else {
                Box(Modifier.fillMaxWidth().height(2.dp))
            }

            AddressBar(
                tab = tab,
                isPrivate = isPrivate,
                focusRequester = addressFocus,
                onSubmit = { vm.tabs.navigate(tab.id, it) },
                onReload = { vm.tabs.reload(tab.id) },
                onStop = { vm.tabs.stop(tab.id) },
                onScreenshot = ::takeScreenshot,
                onNextTab = { switchTab(vm, +1) },
                onPreviousTab = { switchTab(vm, -1) },
                onEditing = { text, focused ->
                    addressFocused = focused
                    vm.suggestionQuery.value = if (focused) text else ""
                },
            )

            BottomBar(
                tab = tab,
                tabCount = model.tabs.size,
                isPrivate = isPrivate,
                menu = {
                    PageMenu(
                        vm = vm,
                        host = host,
                        tab = tab,
                        bookmarked = tab.url in bookmarked,
                        blockingEnabled = settings.blockingEnabled,
                        siteAllowed = UrlResolver.hostOf(tab.url)?.let { Allowlist.isAllowed(settings.allowlist, it) } == true,
                        jsBlocked = UrlResolver.hostOf(tab.url)?.let { Allowlist.isAllowed(settings.jsBlockedHosts, it) } == true,
                        hasClosedTabs = model.closed.isNotEmpty(),
                        nav = nav,
                        onFind = { findOpen = true },
                    )
                },
                onBack = {
                    if (!tab.isHome && tab.canGoBack) vm.tabs.goBack(tab.id)
                    else if (!tab.isHome) vm.tabs.goHome(tab.id)
                },
                onForward = { vm.tabs.goForward(tab.id) },
                onNewTab = { vm.tabs.openNewTab() },
                onTabs = {
                    vm.tabs.captureThumbnail(tab.id)
                    nav.openTabs()
                },
                onNextTab = { switchTab(vm, +1) },
                onPreviousTab = { switchTab(vm, -1) },
            )
        }
    }
}

/** Selects the neighbouring tab; [delta] is +1 for the next one and -1 for the previous one. */
private fun switchTab(vm: BrowserViewModel, delta: Int) {
    val model = vm.tabs.model.value
    val idx = model.tabs.indexOfFirst { it.id == model.selectedId }
    val target = model.tabs.getOrNull(idx + delta) ?: return
    if (idx >= 0) vm.tabs.select(target.id)
}

@Composable
private fun AddressBar(
    tab: TabState,
    isPrivate: Boolean,
    focusRequester: FocusRequester,
    onSubmit: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onScreenshot: (fullPage: Boolean) -> Unit,
    onNextTab: () -> Unit,
    onPreviousTab: () -> Unit,
    onEditing: (text: String, focused: Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }
    // Voice search hands audio to the system recogniser, so it is not offered in the private window.
    val voiceAvailable = remember { !isPrivate && context.packageManager.resolveActivity(voiceIntent, 0) != null }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) onSubmit(spoken)
    }
    val shown = if (tab.url == "about:blank") "" else tab.url
    var field by remember(tab.id, shown) { mutableStateOf(TextFieldValue(shown)) }
    var focused by remember { mutableStateOf(false) }
    // Report what the user is typing (not the URL that was merely selected on focus) so suggestions can follow.
    LaunchedEffect(field.text, focused) {
        onEditing(if (field.text == shown) "" else field.text, focused)
    }
    val secure = UrlResolver.isSecure(tab.url)
    val insecure = !tab.isHome && UrlResolver.schemeOf(tab.url) == "http"

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    Surface(
        modifier = Modifier
            .weight(1f)
            // Swipe the bar to move between tabs (works even though the text field fills most of it).
            .horizontalSwipe(onNext = onNextTab, onPrevious = onPreviousTab),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.height(46.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (icon, tint, description) = when {
                isPrivate && tab.isHome -> Triple(Icons.Filled.VisibilityOff, MaterialTheme.colorScheme.primary, stringResource(R.string.private_mode))
                tab.isHome -> Triple(Icons.Filled.Search, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.address_hint))
                insecure -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.error, stringResource(R.string.not_secure))
                secure -> Triple(Icons.Filled.Lock, MaterialTheme.colorScheme.primary, stringResource(R.string.secure))
                else -> Triple(Icons.Filled.Info, MaterialTheme.colorScheme.onSurfaceVariant, "")
            }
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))

            Box(modifier = Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                val textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDirection = TextDirection.Content,
                )
                if (field.text.isEmpty()) {
                    Text(
                        text = stringResource(if (isPrivate) R.string.address_hint_private else R.string.address_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        focusManager.clearFocus()
                        onSubmit(field.text)
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            focused = state.isFocused
                            if (state.isFocused) field = field.copy(selection = TextRange(0, field.text.length))
                        },
                )
            }

            if (focused && field.text.isNotEmpty()) {
                IconButton(onClick = { field = TextFieldValue("") }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear), modifier = Modifier.size(20.dp))
                }
            } else if (field.text.isEmpty() && voiceAvailable) {
                IconButton(onClick = { voiceLauncher.launch(voiceIntent) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.action_voice_search), modifier = Modifier.size(20.dp))
                }
            } else if (!tab.isHome) {
                if (tab.isLoading) {
                    IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_stop), modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton(onClick = onReload, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_reload), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    if (!tab.isHome) {
        Spacer(Modifier.width(8.dp))
        var shotMenu by remember { mutableStateOf(false) }
        Box {
            FilledTonalIconButton(onClick = { shotMenu = true }, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = stringResource(R.string.action_screenshot),
                    modifier = Modifier.size(22.dp),
                )
            }
            DropdownMenu(expanded = shotMenu, onDismissRequest = { shotMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.screenshot_visible)) },
                    onClick = { shotMenu = false; onScreenshot(false) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.screenshot_full_page)) },
                    onClick = { shotMenu = false; onScreenshot(true) },
                )
            }
        }
    }
    }
}

@Composable
private fun BottomBar(
    tab: TabState,
    tabCount: Int,
    isPrivate: Boolean,
    menu: @Composable () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNewTab: () -> Unit,
    onTabs: () -> Unit,
    onNextTab: () -> Unit,
    onPreviousTab: () -> Unit,
) {
    val tabsDescription = stringResource(R.string.action_tabs)
    Surface(
        modifier = Modifier.horizontalSwipe(onNext = onNextTab, onPrevious = onPreviousTab),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = !tab.isHome) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            IconButton(onClick = onForward, enabled = !tab.isHome && tab.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.action_forward))
            }
            IconButton(onClick = onNewTab) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_tab))
            }
            IconButton(onClick = onTabs) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .size(26.dp)
                        .semantics { contentDescription = tabsDescription },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (tabCount > 99) "99+" else tabCount.toString(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            menu()
        }
    }
}

@Composable
private fun PageMenu(
    vm: BrowserViewModel,
    host: BrowserHost,
    tab: TabState,
    bookmarked: Boolean,
    blockingEnabled: Boolean,
    siteAllowed: Boolean,
    jsBlocked: Boolean,
    hasClosedTabs: Boolean,
    nav: BrowserNav,
    onFind: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var open by remember { mutableStateOf(false) }
    var confirmTranslate by remember { mutableStateOf(false) }
    var showMediaDownload by remember { mutableStateOf(false) }
    val isPrivate = vm.isPrivate
    val isPage = !tab.isHome
    val siteHost = UrlResolver.hostOf(tab.url)
    val canBlock = isPage && UrlResolver.isWebUrl(tab.url) && siteHost != null

    fun close() { open = false }

    if (showMediaDownload) {
        MediaDownloadSheet(
            pageUrl = tab.url,
            pageTitle = tab.title,
            onDismiss = { showMediaDownload = false },
            onFind = { onResult -> vm.tabs.findMediaCandidates(tab.id, onResult) },
            onDownload = { url ->
                showMediaDownload = false
                val message = when (val r = vm.enqueueDownload(url, tab.url)) {
                    is DownloadStart.Started -> context.getString(R.string.msg_download_started, r.fileName)
                    is DownloadStart.Failed -> context.getString(r.messageRes)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
        )
    }

    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
    }
    if (confirmTranslate) {
        ConfirmDialog(
            title = stringResource(R.string.menu_translate),
            message = stringResource(R.string.menu_translate_message),
            confirmLabel = stringResource(R.string.menu_translate),
            onConfirm = {
                confirmTranslate = false
                val language = java.util.Locale.getDefault().language.ifBlank { "en" }
                vm.tabs.navigate(tab.id, AppLinks.translate(tab.url, language))
            },
            onDismiss = { confirmTranslate = false },
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = ::close) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_new_tab)) },
            leadingIcon = { Icon(Icons.Filled.Add, null) },
            onClick = { close(); vm.tabs.openNewTab() },
        )
        if (!isPrivate) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_new_private_tab)) },
                leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                onClick = { close(); host.openPrivateWindow() },
            )
        }
        if (hasClosedTabs) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_reopen_tab)) },
                leadingIcon = { Icon(Icons.Filled.Replay, null) },
                onClick = { close(); vm.tabs.reopenClosed() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_qr_scanner)) },
            leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null) },
            onClick = { close(); vm.tabs.openLinkInNewTab(AppLinks.QR_TOOL) },
        )
        if (isPage) {
            HorizontalDivider()
            
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_find_in_page)) },
                leadingIcon = { Icon(Icons.Filled.ManageSearch, null) },
                onClick = { close(); onFind() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_reader_mode)) },
                leadingIcon = { Icon(Icons.Filled.Book, null) },
                onClick = { close(); vm.tabs.toggleReader(tab.id) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_print)) },
                leadingIcon = { Icon(Icons.Filled.Print, null) },
                onClick = { close(); vm.tabs.printPage(tab.id) },
            )
            if (UrlResolver.isWebUrl(tab.url)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_translate)) },
                    leadingIcon = { Icon(Icons.Filled.Translate, null) },
                    onClick = { close(); confirmTranslate = true },
                )
            }
            if (!isPrivate && UrlResolver.isWebUrl(tab.url)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_add_home)) },
                    leadingIcon = { Icon(Icons.Filled.AddToHomeScreen, null) },
                    onClick = {
                        close()
                        if (!HomeShortcut.request(context, tab.title, tab.url)) {
                            Toast.makeText(context, R.string.msg_shortcut_unsupported, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            if (canBlock) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.menu_js_site))
                            Text(
                                stringResource(if (jsBlocked) R.string.menu_js_site_off else R.string.menu_js_site_on),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.Javascript, null) },
                    trailingIcon = { Switch(checked = !jsBlocked, onCheckedChange = null) },
                    onClick = {
                        vm.setSiteJavaScript(siteHost.orEmpty(), enabled = jsBlocked)
                        close()
                        vm.tabs.reload(tab.id)
                    },
                )
            }

            if (!isPrivate && UrlResolver.isWebUrl(tab.url)) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (bookmarked) R.string.action_remove_bookmark else R.string.action_add_bookmark)) },
                    leadingIcon = { Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null) },
                    onClick = { close(); vm.toggleBookmark(tab.url, tab.title) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_share)) },
                leadingIcon = { Icon(Icons.Filled.Share, null) },
                onClick = {
                    close()
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tab.url)
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_copy_link)) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = {
                    close()
                    clipboard.setText(AnnotatedString(tab.url))
                    Toast.makeText(context, R.string.msg_link_copied, Toast.LENGTH_SHORT).show()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_desktop_site)) },
                leadingIcon = { Icon(Icons.Filled.DesktopWindows, null) },
                trailingIcon = { Checkbox(checked = tab.desktopMode, onCheckedChange = null) },
                onClick = { close(); vm.tabs.setDesktopMode(tab.id, !tab.desktopMode) },
            )
            if (canBlock && blockingEnabled) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.menu_site_blocking))
                            val subtitle = if (siteAllowed) stringResource(R.string.menu_site_blocking_off)
                            else pluralStringResource(R.plurals.menu_blocked_count, tab.blockedCount, tab.blockedCount)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.Shield, null) },
                    trailingIcon = { Switch(checked = !siteAllowed, onCheckedChange = null) },
                    onClick = {
                        vm.setSiteBlocking(siteHost.orEmpty(), blockingOn = siteAllowed)
                        close()
                        vm.tabs.reload(tab.id)
                    },
                )
            }
            if (isPage && UrlResolver.isWebUrl(tab.url)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_download_media)) },
                    leadingIcon = { Icon(Icons.Filled.Download, null) },
                    onClick = {
                        close()
                        showMediaDownload = true
                    },
                )
            }
            if (UrlResolver.isDirectMedia(tab.url)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_download_file)) },
                    leadingIcon = { Icon(Icons.Filled.Download, null) },
                    onClick = {
                        close()
                        val message = when (val r = vm.enqueueDownload(tab.url, null)) {
                            is DownloadStart.Started -> context.getString(R.string.msg_download_started, r.fileName)
                            is DownloadStart.Failed -> context.getString(r.messageRes)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_start_page)) },
                leadingIcon = { Icon(Icons.Filled.Home, null) },
                onClick = { close(); vm.tabs.goHome(tab.id) },
            )
        }
        HorizontalDivider()
        if (!isPrivate) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_bookmarks)) },
                leadingIcon = { Icon(Icons.Filled.Bookmark, null) },
                onClick = { close(); nav.openBookmarks() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_history)) },
                leadingIcon = { Icon(Icons.Filled.History, null) },
                onClick = { close(); nav.openHistory() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_downloads)) },
                leadingIcon = { Icon(Icons.Filled.Download, null) },
                onClick = { close(); nav.openDownloads() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_game)) },
                leadingIcon = { Icon(Icons.Filled.SportsEsports, null) },
                onClick = { close(); nav.openGame() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                onClick = { close(); nav.openSettings() },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_game)) },
                leadingIcon = { Icon(Icons.Filled.SportsEsports, null) },
                onClick = { close(); nav.openGame() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_about)) },
                leadingIcon = { Icon(Icons.Filled.Info, null) },
                onClick = { close(); nav.openAbout() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_close_private)) },
                leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                onClick = { close(); host.closePrivateSession() },
            )
        }
    }
}

@Composable
private fun FindBar(
    tab: TabState,
    onQuery: (String) -> Unit,
    onStep: (forward: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val noResults = query.isNotBlank() && tab.findCount == 0
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.find_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQuery(it)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textDirection = TextDirection.Content,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onStep(true) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            val counter = when {
                query.isBlank() -> ""
                noResults -> stringResource(R.string.find_no_results)
                else -> "${tab.findIndex + 1}/${tab.findCount}"
            }
            Text(
                text = counter,
                style = MaterialTheme.typography.labelMedium,
                color = if (noResults) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = { onStep(false) }, enabled = tab.findCount > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.find_previous))
            }
            IconButton(onClick = { onStep(true) }, enabled = tab.findCount > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.find_next))
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        }
    }
}

/** Bookmark and history matches for what is being typed; covers the page area, right above the address bar. */
@Composable
private fun SuggestionList(items: List<Suggestion>, onPick: (Suggestion) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            items(items, key = { it.url }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(item) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (item.isBookmark) Icons.Filled.Bookmark else Icons.Filled.History,
                        contentDescription = stringResource(
                            if (item.isBookmark) R.string.suggestion_bookmark else R.string.suggestion_history,
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(item.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        UrlText(item.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaDownloadSheet(
    pageUrl: String,
    pageTitle: String,
    onDismiss: () -> Unit,
    onFind: ((List<net.iegy.hisn.browser.MediaCandidate>) -> Unit) -> Unit,
    onDownload: (String) -> Unit,
) {
    var accepted by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<net.iegy.hisn.browser.MediaCandidate>?>(null) }
    val isPlatform = net.iegy.hisn.browser.MediaDownload.isKnownStreamingPlatform(pageUrl)

    if (!accepted) {
        ConfirmDialog(
            title = stringResource(R.string.media_dl_disclaimer_title),
            message = stringResource(R.string.media_dl_disclaimer),
            confirmLabel = stringResource(R.string.media_dl_accept),
            onConfirm = { accepted = true },
            onDismiss = onDismiss,
        )
        return
    }

    LaunchedEffect(accepted) {
        if (candidates == null && !searching) {
            searching = true
            onFind { list ->
                candidates = list
                searching = false
            }
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.media_dl_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                when {
                    searching || candidates == null -> {
                        Text(stringResource(R.string.media_dl_searching))
                    }
                    candidates.isNullOrEmpty() -> {
                        Text(
                            stringResource(
                                if (isPlatform) R.string.media_dl_none_platform else R.string.media_dl_none,
                            ),
                        )
                    }
                    else -> {
                        Text(
                            stringResource(R.string.media_dl_pick),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        candidates.orEmpty().forEach { item ->
                            val kind = when {
                                item.isLikelyAudioOnly -> stringResource(R.string.media_dl_audio)
                                else -> stringResource(R.string.media_dl_video)
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onDownload(item.url) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text("$kind — ${item.label}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(
                                        item.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.media_dl_legal_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.media_dl_cancel))
            }
        },
    )
}
