package net.iegy.hisn

import android.view.View
import net.iegy.hisn.browser.FileChooserRequest

/** Activity-level capabilities the Compose UI needs but cannot perform itself. */
interface BrowserHost {
    val isPrivateSession: Boolean

    fun launchFileChooser(request: FileChooserRequest)
    fun hasPermission(permission: String): Boolean
    fun requestPermissions(permissions: Array<String>, onResult: (Map<String, Boolean>) -> Unit)

    fun showCustomView(view: View, onHidden: () -> Unit)
    fun hideCustomView()

    /** If a fullscreen video is showing, closes it and returns true (so Back is consumed). */
    fun handleBackInFullscreen(): Boolean

    /** Hands a vetted external link to another app. Returns false if no app can handle it. */
    fun openExternal(url: String): Boolean

    /** Opens the separate private-browsing window (its own task and process). */
    fun openPrivateWindow(url: String? = null)

    /** Private process only: wipes all private data, returns to the regular browser and ends the process. */
    fun closePrivateSession()

    /** Sends the app to the background (Back on the start page). */
    fun exitApp()

    fun recreateForLanguageChange()

    /** Regular window only: lets the user pick where to save / which file to restore a data backup from. */
    fun startExportBackup() {}
    fun startImportBackup() {}

    /** Regular window only: asks Android to make Hisn the default browser. */
    fun requestDefaultBrowser() {}

    /** Opens the system screen where the autofill / password manager is chosen. */
    fun openAutofillSettings() {}

    /** True when the device has a fingerprint, face or screen lock that the app lock can use. */
    fun canUseAppLock(): Boolean = false
}
