package net.iegy.hisn

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.webkit.WebView

class HisnApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val isPrivateProcess = getProcessName().endsWith(PRIVATE_PROCESS_SUFFIX)
        if (isPrivateProcess) {
            // Both steps must happen before this process creates its first WebView:
            // 1) remove whatever a previous private session left behind (e.g. if it was swiped away),
            // 2) give this process its own web-data directory so it never shares cookies/cache with the main one.
            PrivateStorage.wipeLeftovers(this)
            WebView.setDataDirectorySuffix(PRIVATE_DATA_SUFFIX)
        }
        container = AppContainer(this, isPrivateProcess)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            container.tabManager.onLowMemory()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        container.tabManager.onLowMemory()
    }

    companion object {
        const val PRIVATE_PROCESS_SUFFIX = ":private"
        const val PRIVATE_DATA_SUFFIX = "private"
    }
}

/** Best-effort removal of web data left in the private process's own directories. */
object PrivateStorage {
    fun wipeLeftovers(context: Context) {
        runCatching {
            context.dataDir.listFiles()
                ?.filter { it.name.startsWith("app_webview_${HisnApp.PRIVATE_DATA_SUFFIX}") }
                ?.forEach { it.deleteRecursively() }
            context.cacheDir.listFiles()
                ?.filter { it.name.contains("webview", ignoreCase = true) && it.name.contains(HisnApp.PRIVATE_DATA_SUFFIX, ignoreCase = true) }
                ?.forEach { it.deleteRecursively() }
        }
    }
}
