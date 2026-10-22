package net.iegy.hisn

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.data.BackupException
import net.iegy.hisn.settings.BootPrefs
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : BaseBrowserActivity() {
    override val isPrivateSession: Boolean = false

    // The lock must be known before DataStore has loaded, so it is read from the small boot mirror.
    override fun lockRequired(): Boolean = BootPrefs.appLock(this)

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    // Save a backup: the system file picker asks where, then the data is written there.
    private val exportBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri == null) return@registerForActivityResult
            container.scope.launch {
                val message = try {
                    val stream = contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
                    stream.use { container.backup.write(it) }
                    getString(R.string.backup_export_done)
                } catch (e: Exception) {
                    getString(R.string.backup_export_failed)
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }

    // Restore a backup: merges the file into the current data, nothing is deleted.
    private val importBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            container.scope.launch {
                val message = try {
                    val stream = contentResolver.openInputStream(uri) ?: throw IOException("No input stream")
                    val summary = stream.use { container.backup.restore(it) }
                    if (summary.languageChanged) recreate()
                    getString(R.string.backup_import_done, summary.bookmarks, summary.shortcuts, summary.history)
                } catch (e: BackupException) {
                    getString(R.string.backup_import_invalid)
                } catch (e: Exception) {
                    getString(R.string.backup_import_failed)
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { container.tabManager.model.value.tabs.isEmpty() }
        super.onCreate(savedInstanceState)
    }

    override fun startExportBackup() {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        exportBackupLauncher.launch("hisn-backup-$stamp.hisn")
    }

    override fun startImportBackup() {
        importBackupLauncher.launch(arrayOf("*/*"))
    }

    override fun onBeforeContent(intent: Intent) {
        handleViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    /**
     * Opens what another app handed to us in a new tab: a link (VIEW), shared text (SEND) or text selected in
     * another app and sent through "Search with Hisn" (PROCESS_TEXT). Text that is not an address becomes a search.
     */
    private fun handleViewIntent(intent: Intent) {
        val text: String? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { UrlResolver.isWebUrl(it) }
            Intent.ACTION_SEND ->
                if (intent.type?.startsWith("text/") == true) intent.getStringExtra(Intent.EXTRA_TEXT) else null
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }?.trim()?.take(2_000)
        if (text.isNullOrBlank()) return
        // Consume so a configuration change does not reopen the same link.
        intent.action = null
        val query = Regex("https?://\\S+").find(text)?.value ?: text
        container.scope.launch {
            // On a cold start the saved tabs are still being restored; wait so the link is not lost.
            container.tabManager.model.first { it.tabs.isNotEmpty() }
            val id = container.tabManager.newTab()
            container.tabManager.navigate(id, query)
        }
    }

    override fun requestDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                if (roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    Toast.makeText(this, R.string.msg_default_browser_already, Toast.LENGTH_SHORT).show()
                } else {
                    roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                }
                return
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun openAutofillSettings() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(Uri.parse("package:none")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: SecurityException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun onDestroy() {
        val wipe = isFinishing && container.settings.value.clearOnExit
        super.onDestroy()
        // Best effort: if the process is killed first, the same wipe runs again at the next start.
        if (wipe) container.scope.launch { container.clearBrowsingData() }
    }

    override fun openPrivateWindow(url: String?) {
        val intent = Intent(this, PrivateActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(PrivateSnapshot.write(intent, container.settings.value, url))
    }

    override fun closePrivateSession() = Unit
}
