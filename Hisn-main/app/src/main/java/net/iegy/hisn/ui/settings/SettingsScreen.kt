@file:OptIn(ExperimentalMaterial3Api::class)

package net.iegy.hisn.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import net.iegy.hisn.BrowserHost
import net.iegy.hisn.BuildConfig
import net.iegy.hisn.R
import net.iegy.hisn.browser.SearchEngine
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.data.ClearRange
import net.iegy.hisn.settings.AppLanguage
import net.iegy.hisn.settings.NewTabPage
import net.iegy.hisn.settings.StartupBehavior
import net.iegy.hisn.settings.ThemeMode
import net.iegy.hisn.ui.components.BackTopBar
import net.iegy.hisn.ui.components.ChoiceDialog
import net.iegy.hisn.ui.components.ConfirmDialog
import net.iegy.hisn.ui.components.SectionHeader
import net.iegy.hisn.ui.components.TextInputDialog
import net.iegy.hisn.ui.library.rangeLabel
import net.iegy.hisn.viewmodel.SettingsViewModel

private enum class Dialog { THEME, LANGUAGE, SEARCH, HOME, STARTUP, CLEAR_HISTORY, CLEAR_DATA, PRIVATE_INFO, CUSTOM_URL, TEXT_ZOOM }

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    host: BrowserHost,
    onBack: () -> Unit,
    onAllowlist: () -> Unit,
    onAbout: () -> Unit,
    onPrivacy: () -> Unit,
    onLicenses: () -> Unit,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val rules by vm.ruleCount.collectAsStateWithLifecycle()
    val updating by vm.updatingFilters.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var pendingRange by remember { mutableStateOf<ClearRange?>(null) }

    val themeLabels = mapOf(
        ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark,
    )
    val languageLabels = mapOf(
        AppLanguage.SYSTEM to R.string.lang_system, AppLanguage.ARABIC to R.string.lang_arabic, AppLanguage.ENGLISH to R.string.lang_english,
    )
    val startupLabels = mapOf(
        StartupBehavior.START_PAGE to R.string.startup_start_page, StartupBehavior.RESTORE_TABS to R.string.startup_restore,
    )
    val homeLabels = mapOf(
        NewTabPage.START_PAGE to R.string.home_start_page, NewTabPage.CUSTOM_URL to R.string.home_custom,
    )
    val lastUpdate = if (s.lastFilterUpdate == 0L) stringResource(R.string.filters_never)
    else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.lastFilterUpdate))

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars)) {
        BackTopBar(title = stringResource(R.string.title_settings), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_general))
            PrefRow(stringResource(R.string.settings_theme), stringResource(themeLabels.getValue(s.theme))) { dialog = Dialog.THEME }
            PrefRow(stringResource(R.string.settings_language), stringResource(languageLabels.getValue(s.language))) { dialog = Dialog.LANGUAGE }
            PrefRow(stringResource(R.string.settings_search_engine), s.searchEngine.label) { dialog = Dialog.SEARCH }
            PrefRow(
                stringResource(R.string.settings_new_tab_page),
                if (s.newTabPage == NewTabPage.CUSTOM_URL && s.customHomeUrl.isNotBlank()) s.customHomeUrl
                else stringResource(homeLabels.getValue(s.newTabPage)),
            ) { dialog = Dialog.HOME }
            PrefRow(stringResource(R.string.settings_startup), stringResource(startupLabels.getValue(s.startup))) { dialog = Dialog.STARTUP }
            SwitchRow(stringResource(R.string.settings_desktop_default), stringResource(R.string.settings_desktop_default_sub), s.desktopByDefault) { vm.setDesktopDefault(it) }
            SwitchRow(stringResource(R.string.settings_show_recent), null, s.showRecent) { vm.setShowRecent(it) }
            PrefRow(stringResource(R.string.settings_text_size), stringResource(R.string.text_size_value, s.textZoom)) { dialog = Dialog.TEXT_ZOOM }
            SwitchRow(stringResource(R.string.settings_background_media), stringResource(R.string.settings_background_media_sub), s.backgroundMedia) { vm.setBackgroundMedia(it) }
            PrefRow(stringResource(R.string.settings_default_browser), stringResource(R.string.settings_default_browser_sub)) { host.requestDefaultBrowser() }
            PrefRow(stringResource(R.string.settings_autofill), stringResource(R.string.settings_autofill_sub)) { host.openAutofillSettings() }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_blocking))
            SwitchRow(stringResource(R.string.settings_blocking_toggle), stringResource(R.string.settings_blocking_sub, rules), s.blockingEnabled) { vm.setBlocking(it) }
            PrefRow(stringResource(R.string.settings_allowlist), stringResource(R.string.settings_allowlist_sub, s.allowlist.size), onAllowlist)
            SwitchRow(stringResource(R.string.settings_filters_auto), stringResource(R.string.settings_filters_auto_sub), s.autoUpdateFilters) { vm.setAutoUpdate(it) }
            PrefRow(
                stringResource(R.string.settings_filters_update),
                if (updating) stringResource(R.string.filters_updating) else stringResource(R.string.filters_last_update, lastUpdate),
            ) {
                vm.updateFilters { ok ->
                    Toast.makeText(context, if (ok) R.string.filters_update_ok else R.string.filters_update_failed, Toast.LENGTH_SHORT).show()
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_rafiqi_section))
            SwitchRow(
                stringResource(R.string.settings_prayer_notifications),
                stringResource(R.string.settings_prayer_notifications_sub),
                s.prayerNotifications,
            ) { enable ->
                if (enable) {
                    // Request notification permission (Android 13+) then exact-alarm access if needed.
                    val needNotif = Build.VERSION.SDK_INT >= 33 &&
                        !host.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                    if (needNotif) {
                        host.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) { result ->
                            val granted = result[Manifest.permission.POST_NOTIFICATIONS] == true
                            if (granted) {
                                vm.setPrayerNotifications(true)
                                maybeOpenExactAlarmSettings(context)
                            } else {
                                Toast.makeText(context, R.string.msg_prayer_notif_denied, Toast.LENGTH_LONG).show()
                                vm.setPrayerNotifications(false)
                            }
                        }
                    } else {
                        vm.setPrayerNotifications(true)
                        maybeOpenExactAlarmSettings(context)
                    }
                } else {
                    vm.setPrayerNotifications(false)
                    net.iegy.hisn.rafiqi.PrayerScheduler(context.applicationContext).cancelAll()
                }
            }
            PrefRow(
                stringResource(R.string.settings_prayer_permissions),
                stringResource(R.string.settings_prayer_permissions_sub),
            ) {
                val needNotif = Build.VERSION.SDK_INT >= 33 &&
                    !host.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                if (needNotif) {
                    host.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) { result ->
                        val granted = result[Manifest.permission.POST_NOTIFICATIONS] == true
                        Toast.makeText(
                            context,
                            if (granted) R.string.msg_prayer_notif_granted else R.string.msg_prayer_notif_denied,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                maybeOpenExactAlarmSettings(context, force = true)
            }
            PrefRow(
                stringResource(R.string.settings_open_rafiqi),
                stringResource(R.string.settings_open_rafiqi_sub),
            ) {
                // Navigation to Rafiqi is handled by the host via a special URL; Settings stays open otherwise.
                Toast.makeText(context, R.string.rafiqi_open_hint, Toast.LENGTH_SHORT).show()
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_privacy))
            PrefRow(stringResource(R.string.settings_clear_history), stringResource(R.string.settings_clear_history_sub)) { dialog = Dialog.CLEAR_HISTORY }
            PrefRow(stringResource(R.string.settings_clear_data), stringResource(R.string.settings_clear_data_sub)) { dialog = Dialog.CLEAR_DATA }
            PrefRow(stringResource(R.string.settings_private_info), stringResource(R.string.settings_private_info_sub)) { dialog = Dialog.PRIVATE_INFO }
            SwitchRow(stringResource(R.string.settings_https_only), stringResource(R.string.settings_https_only_sub), s.httpsOnly) { vm.setHttpsOnly(it) }
            SwitchRow(stringResource(R.string.settings_clear_on_exit), stringResource(R.string.settings_clear_on_exit_sub), s.clearOnExit) { vm.setClearOnExit(it) }
            SwitchRow(stringResource(R.string.settings_app_lock), stringResource(R.string.settings_app_lock_sub), s.appLock) { enable ->
                if (enable && !host.canUseAppLock()) {
                    Toast.makeText(context, R.string.msg_app_lock_unavailable, Toast.LENGTH_LONG).show()
                } else {
                    vm.setAppLock(enable)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_backup))
            PrefRow(stringResource(R.string.settings_backup_export), stringResource(R.string.settings_backup_export_sub)) {
                host.startExportBackup()
            }
            PrefRow(stringResource(R.string.settings_backup_import), stringResource(R.string.settings_backup_import_sub)) {
                host.startImportBackup()
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.title_downloads))
            PrefRow(stringResource(R.string.settings_download_location), stringResource(R.string.downloads_location), null)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.title_about))
            PrefRow(stringResource(R.string.settings_about), stringResource(R.string.app_name_both), onAbout)
            PrefRow(stringResource(R.string.settings_privacy_policy), null, onPrivacy)
            PrefRow(stringResource(R.string.settings_licenses), null, onLicenses)
            PrefRow(
                stringResource(R.string.settings_version),
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}",
                null,
            )

            Text(
                text = stringResource(R.string.credit_designed_by),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
    }

    when (dialog) {
        Dialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to stringResource(themeLabels.getValue(it)) },
            selected = s.theme,
            onSelect = { vm.setTheme(it) },
            onDismiss = { dialog = null },
        )
        Dialog.TEXT_ZOOM -> ChoiceDialog(
            title = stringResource(R.string.settings_text_size),
            options = listOf(75, 90, 100, 115, 130, 150, 175, 200).map { it to "$it%" },
            selected = s.textZoom,
            onSelect = { vm.setTextZoom(it) },
            onDismiss = { dialog = null },
        )
        Dialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = AppLanguage.entries.map { it to stringResource(languageLabels.getValue(it)) },
            selected = s.language,
            onSelect = { vm.setLanguage(it) { host.recreateForLanguageChange() } },
            onDismiss = { dialog = null },
        )
        Dialog.SEARCH -> ChoiceDialog(
            title = stringResource(R.string.settings_search_engine),
            options = SearchEngine.entries.map { it to it.label },
            selected = s.searchEngine,
            onSelect = { vm.setSearchEngine(it) },
            onDismiss = { dialog = null },
        )
        Dialog.HOME -> ChoiceDialog(
            title = stringResource(R.string.settings_new_tab_page),
            options = NewTabPage.entries.map { it to stringResource(homeLabels.getValue(it)) },
            selected = s.newTabPage,
            onSelect = {
                vm.setNewTabPage(it)
                if (it == NewTabPage.CUSTOM_URL) dialog = Dialog.CUSTOM_URL
            },
            onDismiss = { if (dialog == Dialog.HOME) dialog = null },
        )
        Dialog.CUSTOM_URL -> TextInputDialog(
            title = stringResource(R.string.home_custom),
            firstLabel = stringResource(R.string.field_url),
            initialFirst = s.customHomeUrl,
            secondIsUrl = true,
            validate = { url, _ -> UrlResolver.normalizeWebUrl(url) != null },
            onConfirm = { url, _ ->
                UrlResolver.normalizeWebUrl(url)?.let { vm.setCustomHome(it) }
                dialog = null
            },
            onDismiss = {
                if (UrlResolver.normalizeWebUrl(s.customHomeUrl) == null) vm.setNewTabPage(NewTabPage.START_PAGE)
                dialog = null
            },
        )
        Dialog.STARTUP -> ChoiceDialog(
            title = stringResource(R.string.settings_startup),
            options = StartupBehavior.entries.map { it to stringResource(startupLabels.getValue(it)) },
            selected = s.startup,
            onSelect = { vm.setStartup(it) },
            onDismiss = { dialog = null },
        )
        Dialog.CLEAR_HISTORY -> ChoiceDialog(
            title = stringResource(R.string.history_clear_title),
            options = ClearRange.entries.map { it to stringResource(rangeLabel(it)) },
            selected = ClearRange.ALL,
            onSelect = { pendingRange = it },
            onDismiss = { dialog = null },
        )
        Dialog.CLEAR_DATA -> ConfirmDialog(
            title = stringResource(R.string.settings_clear_data),
            message = stringResource(R.string.clear_data_message),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = {
                dialog = null
                vm.clearWebsiteData { Toast.makeText(context, R.string.clear_data_done, Toast.LENGTH_SHORT).show() }
            },
            onDismiss = { dialog = null },
        )
        Dialog.PRIVATE_INFO -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_private_info)) },
            text = { Text(stringResource(R.string.private_info_full)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_ok)) }
            },
        )
        null -> Unit
    }

    pendingRange?.let { range ->
        ConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            message = stringResource(
                if (range == ClearRange.ALL) R.string.history_clear_all_message else R.string.history_clear_range_message,
                stringResource(rangeLabel(range)),
            ),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = { vm.clearHistory(range); pendingRange = null; dialog = null },
            onDismiss = { pendingRange = null },
        )
    }
}

@Composable
private fun PrefRow(title: String, subtitle: String?, onClick: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
