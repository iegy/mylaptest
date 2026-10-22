package net.iegy.hisn

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.WindowManager
import kotlinx.coroutines.launch
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.tabs.TabManager

/**
 * The private-browsing window. Runs in its own process (":private") with its own WebView data directory, in its
 * own task. See HisnApp for how the process is isolated and AppContainer for what it deliberately lacks.
 */
class PrivateActivity : BaseBrowserActivity() {
    override val isPrivateSession: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keeps private pages out of screenshots and the recent-apps thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
    }

    override fun onBeforeContent(intent: Intent) {
        applyLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunchIntent(intent)
    }

    private fun applyLaunchIntent(intent: Intent) {
        container.applyPrivateSnapshot(PrivateSnapshot.read(intent, container.settings.value))
        val tabs: TabManager = container.tabManager
        val url = intent.getStringExtra(PrivateSnapshot.EXTRA_URL)?.takeIf { UrlResolver.isWebUrl(it) }
        intent.removeExtra(PrivateSnapshot.EXTRA_URL)
        if (url != null) {
            tabs.newTab(url = url)
        } else if (tabs.model.value.tabs.isEmpty()) {
            tabs.openNewTab()
        }
    }

    override fun openPrivateWindow(url: String?) {
        if (url != null) container.tabManager.openLinkInNewTab(url) else container.tabManager.openNewTab()
    }

    override fun closePrivateSession() {
        container.scope.launch {
            container.tabManager.clearAllSessionData()
            // Back to the regular browser, then end this process so nothing private stays in memory.
            startActivity(
                Intent(this@PrivateActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            finishAndRemoveTask()
            Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 300)
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            // Covers the system finishing the task (e.g. user dismissed it): still wipe synchronously-reachable state.
            container.scope.launch { container.tabManager.clearAllSessionData() }
        }
        super.onDestroy()
    }
}
