package net.iegy.hisn.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.webkit.PermissionRequest
import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.iegy.hisn.AppContainer
import net.iegy.hisn.BrowserHost
import net.iegy.hisn.R
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.download.DownloadStart
import net.iegy.hisn.tabs.BrowserEvent
import net.iegy.hisn.ui.browser.BrowserNav
import net.iegy.hisn.ui.browser.BrowserScreen
import net.iegy.hisn.ui.components.UrlText
import net.iegy.hisn.ui.downloads.DownloadsScreen
import net.iegy.hisn.ui.game.DinoGameScreen
import net.iegy.hisn.ui.pdf.PdfReaderScreen
import net.iegy.hisn.ui.library.BookmarksScreen
import net.iegy.hisn.ui.library.HistoryScreen
import net.iegy.hisn.ui.settings.AboutScreen
import net.iegy.hisn.ui.settings.AllowlistScreen
import net.iegy.hisn.ui.settings.LegalScreen
import net.iegy.hisn.ui.settings.SettingsScreen
import net.iegy.hisn.ui.tabs.TabSwitcherScreen
import net.iegy.hisn.viewmodel.BrowserViewModel
import net.iegy.hisn.viewmodel.HisnViewModelFactory

object Routes {
    const val BROWSER = "browser"
    const val TABS = "tabs"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val ALLOWLIST = "settings/allowlist"
    const val ABOUT = "about"
    const val PRIVACY = "legal/privacy"
    const val LICENSES = "legal/licenses"
    const val GAME = "game"
    const val PDF = "pdf"
}

@Composable
fun BrowserRoot(container: AppContainer, host: BrowserHost) {
    val context = LocalContext.current
    val factory = remember(container) { HisnViewModelFactory(container) }
    val nav = rememberNavController()
    val browserVm: BrowserViewModel = viewModel(factory = factory)
    val isPrivate = host.isPrivateSession

    BrowserEventHandler(browserVm, host)

    val backToBrowser: () -> Unit = { nav.popBackStack(Routes.BROWSER, inclusive = false) }
    val openInCurrentTab: (String) -> Unit = { url ->
        val tabs = browserVm.tabs
        val id = tabs.model.value.selectedId ?: tabs.newTab()
        tabs.navigate(id, url)
        backToBrowser()
    }
    val openInNewTab: (String) -> Unit = { url ->
        browserVm.tabs.openLinkInNewTab(url)
        backToBrowser()
    }

    NavHost(
        navController = nav,
        startDestination = Routes.BROWSER,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() },
    ) {
        composable(Routes.BROWSER) {
            BrowserScreen(
                vm = browserVm,
                host = host,
                nav = BrowserNav(
                    openTabs = { nav.navigate(Routes.TABS) },
                    openBookmarks = { nav.navigate(Routes.BOOKMARKS) },
                    openHistory = { nav.navigate(Routes.HISTORY) },
                    openDownloads = { nav.navigate(Routes.DOWNLOADS) },
                    openSettings = { nav.navigate(Routes.SETTINGS) },
                    openAbout = { nav.navigate(Routes.ABOUT) },
                    openGame = { nav.navigate(Routes.GAME) },
                    openPdf = { uri, title ->
                        val encoded = android.net.Uri.encode(uri)
                        val t = android.net.Uri.encode(title.orEmpty())
                        nav.navigate("${Routes.PDF}?uri=$encoded&title=$t")
                    },
                ),
            )
        }
        composable(Routes.TABS) {
            TabSwitcherScreen(vm = browserVm, host = host, onBack = backToBrowser)
        }
        if (!isPrivate) {
            composable(Routes.BOOKMARKS) {
                BookmarksScreen(
                    vm = viewModel(factory = factory),
                    onBack = { nav.popBackStack() },
                    onOpen = openInCurrentTab,
                    onOpenInNewTab = openInNewTab,
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    vm = viewModel(factory = factory),
                    onBack = { nav.popBackStack() },
                    onOpen = openInCurrentTab,
                    onOpenInNewTab = openInNewTab,
                )
            }
            composable(Routes.DOWNLOADS) {
                DownloadsScreen(
                    vm = viewModel(factory = factory),
                    onBack = { nav.popBackStack() },
                    onOpenPdf = { uri, title ->
                        val encoded = android.net.Uri.encode(uri.toString())
                        val t = android.net.Uri.encode(title.orEmpty())
                        nav.navigate("${Routes.PDF}?uri=$encoded&title=$t")
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    vm = viewModel(factory = factory),
                    host = host,
                    onBack = { nav.popBackStack() },
                    onAllowlist = { nav.navigate(Routes.ALLOWLIST) },
                    onAbout = { nav.navigate(Routes.ABOUT) },
                    onPrivacy = { nav.navigate(Routes.PRIVACY) },
                    onLicenses = { nav.navigate(Routes.LICENSES) },
                )
            }
            composable(Routes.ALLOWLIST) {
                AllowlistScreen(vm = viewModel(factory = factory), onBack = { nav.popBackStack() })
            }
        }
        
        composable(Routes.GAME) {
            DinoGameScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.PDF + "?uri={uri}&title={title}",
            arguments = listOf(
                androidx.navigation.navArgument("uri") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("title") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val uri = android.net.Uri.decode(entry.arguments?.getString("uri").orEmpty())
            val title = android.net.Uri.decode(entry.arguments?.getString("title").orEmpty()).ifBlank { null }
            PdfReaderScreen(
                uriString = uri,
                title = title,
                onBack = { nav.popBackStack() },
                onShare = { shareUri ->
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("application/pdf")
                        .putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(
                        android.content.Intent.createChooser(send, null)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                isPrivate = isPrivate,
                onBack = { nav.popBackStack() },
                onPrivacy = { nav.navigate(Routes.PRIVACY) },
                onLicenses = { nav.navigate(Routes.LICENSES) },
                onOpenSite = { openInNewTab("https://iegy.net/") },
            )
        }
        composable(Routes.PRIVACY) { LegalScreen(titleRes = R.string.settings_privacy_policy, asset = "privacy", onBack = { nav.popBackStack() }) }
        composable(Routes.LICENSES) { LegalScreen(titleRes = R.string.settings_licenses, asset = "licenses", onBack = { nav.popBackStack() }) }
    }
}

/** Collects one-shot events from the tab manager and shows the dialogs / system UI they need. */
@Composable
private fun BrowserEventHandler(vm: BrowserViewModel, host: BrowserHost) {
    val context = LocalContext.current
    var externalLink by remember { mutableStateOf<String?>(null) }
    var permission by remember { mutableStateOf<BrowserEvent.WebPermission?>(null) }
    var longPress by remember { mutableStateOf<BrowserEvent.LongPress?>(null) }

    LaunchedEffect(vm) {
        vm.tabs.events.collect { event ->
            when (event) {
                is BrowserEvent.Message ->
                    Toast.makeText(
                        context,
                        event.arg?.let { context.getString(event.res, it) } ?: context.getString(event.res),
                        Toast.LENGTH_SHORT,
                    ).show()
                is BrowserEvent.ExternalLink -> externalLink = event.url
                is BrowserEvent.WebPermission -> {
                    permission?.deny?.invoke()
                    permission = event
                }
                is BrowserEvent.FileChooser -> host.launchFileChooser(event.request)
                is BrowserEvent.LongPress -> longPress = event
                is BrowserEvent.ShowCustomView -> host.showCustomView(event.view, event.onHidden)
                BrowserEvent.HideCustomView -> host.hideCustomView()
            }
        }
    }

    externalLink?.let { url ->
        AlertDialog(
            onDismissRequest = { externalLink = null },
            title = { Text(stringResource(R.string.external_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.external_message))
                    UrlText(url, Modifier.padding(top = 8.dp), maxLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!host.openExternal(url)) {
                        Toast.makeText(context, R.string.external_no_app, Toast.LENGTH_SHORT).show()
                    }
                    externalLink = null
                }) { Text(stringResource(R.string.action_open)) }
            },
            dismissButton = { TextButton(onClick = { externalLink = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    permission?.let { request ->
        val wantsCamera = PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources
        val wantsMic = PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources
        val wantsDrm = PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in request.resources
        val what = listOfNotNull(
            if (wantsCamera) stringResource(R.string.perm_camera) else null,
            if (wantsMic) stringResource(R.string.perm_microphone) else null,
            if (wantsDrm) stringResource(R.string.perm_protected_media) else null,
        ).joinToString(" • ")
        AlertDialog(
            onDismissRequest = {
                request.deny()
                permission = null
            },
            title = { Text(stringResource(R.string.perm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.perm_message, what))
                    UrlText(request.origin, Modifier.padding(top = 8.dp), maxLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    permission = null
                    grantWebPermission(host, request)
                }) { Text(stringResource(R.string.action_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    request.deny()
                    permission = null
                }) { Text(stringResource(R.string.action_deny)) }
            },
        )
    }

    longPress?.let { event ->
        LongPressDialog(vm, host, event, context) { longPress = null }
    }
}

/** Asks Android for the runtime permissions behind the web request, then grants only what was actually allowed. */
private fun grantWebPermission(host: BrowserHost, request: BrowserEvent.WebPermission) {
    val androidFor = mapOf(
        PermissionRequest.RESOURCE_VIDEO_CAPTURE to Manifest.permission.CAMERA,
        PermissionRequest.RESOURCE_AUDIO_CAPTURE to Manifest.permission.RECORD_AUDIO,
    )
    val missing = request.resources.mapNotNull { androidFor[it] }.filterNot { host.hasPermission(it) }
    fun finish() {
        val granted = request.resources.filter { res ->
            val needed = androidFor[res]
            needed == null || host.hasPermission(needed)
        }
        if (granted.isEmpty()) request.deny() else request.grant(granted)
    }
    if (missing.isEmpty()) finish() else host.requestPermissions(missing.toTypedArray()) { finish() }
}

@Composable
private fun LongPressDialog(
    vm: BrowserViewModel,
    host: BrowserHost,
    event: BrowserEvent.LongPress,
    context: Context,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val link = event.target.linkUrl
    val image = event.target.imageUrl
    val referer = vm.model.value.tabs.firstOrNull { it.id == event.tabId }?.url

    @Composable
    fun Action(label: String, run: () -> Unit) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    run()
                    onDismiss()
                }
                .padding(vertical = 12.dp),
        )
    }

    fun toast(start: DownloadStart) {
        val message = when (start) {
            is DownloadStart.Started -> context.getString(R.string.msg_download_started, start.fileName)
            is DownloadStart.Failed -> context.getString(start.messageRes)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { UrlText((link ?: image).orEmpty(), maxLines = 2) },
        text = {
            Column {
                if (link != null) {
                    Action(stringResource(R.string.lp_open_new_tab)) {
                        vm.tabs.openLinkInNewTab(link, select = true, parentId = event.tabId)
                    }
                    if (!host.isPrivateSession) {
                        Action(stringResource(R.string.lp_open_private)) { host.openPrivateWindow(link) }
                    }
                    Action(stringResource(R.string.lp_copy_link)) { clipboard.setText(AnnotatedString(link)) }
                    Action(stringResource(R.string.action_share)) {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    if (UrlResolver.isDirectMedia(link)) {
                        Action(stringResource(R.string.lp_download_file)) { toast(vm.enqueueDownload(link, referer)) }
                    }
                }
                if (image != null) {
                    Action(stringResource(R.string.lp_open_image)) {
                        vm.tabs.openLinkInNewTab(image, select = true, parentId = event.tabId)
                    }
                    Action(stringResource(R.string.lp_download_image)) { toast(vm.enqueueDownload(image, referer)) }
                    Action(stringResource(R.string.lp_copy_image_link)) { clipboard.setText(AnnotatedString(image)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
