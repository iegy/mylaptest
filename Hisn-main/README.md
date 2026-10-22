<div align="center">

# حصن — HISN Browser

**تصفّح أسرع. خصوصية أقوى. تحكّم أكبر.**
**Browse Faster. Stay in Control.**

تصميم وتطوير Mohammed Hussein | iegy.net
Designed & Developed by Mohammed Hussein | iegy.net

© 2026 Mohammed Hussein. All rights reserved.

</div>

HISN (حصن, "fortress") is a native Android web browser written in Kotlin with Jetpack Compose and Material 3. It is a real browser app, not a website wrapped in an APK: tabs, bookmarks, history, downloads, private browsing, ad/tracker blocking and settings are implemented natively around Android's WebView engine. The interface is Arabic-first (full RTL) with English included.

> **Status:** the source was written and statically checked in an environment that could not reach the Android SDK, Google Maven or Gradle's servers, so **the app has not yet been compiled, run on a device, or run through its tests there**. The first GitHub Actions run is the first real build. Expect to fix a few compile errors in the Compose UI layer on that first run. See [Verification status](#verification-status).

## Screenshots

_No genuine screenshots yet._ Run the debug APK and add them to `docs/screenshots/`.

## Features

* **Start page** rendered locally (no remote fonts/scripts/images): logo, search field, editable shortcut grid (add / edit / reorder / remove), bookmarks row, recently-visited list with *Hide* and *Clear* controls, quick access to downloads, history, bookmarks, settings and private browsing.
* **Address bar:** URL-or-search detection (Unicode/Arabic safe, percent-encoded), HTTPS/HTTP indicator, clear, reload/stop, progress bar, copy and share. `javascript:`, `file:`, `content:`, `data:` and similar are refused; text is never treated as a local path; `mailto:`/`tel:` open other apps only after a confirmation.
* **Multi-tab browsing:** switcher grid, close one / others / all, reopen closed tab, `target=_blank` and user-initiated popups (scripted popups are refused), per-tab history and desktop-site mode.
* **Memory strategy:** at most 5 live WebViews (2 on low-RAM devices); the least recently used background tab is frozen (state saved, WebView destroyed) and restored on demand; low-memory signals freeze all but the visible tab; opening the tab switcher never reloads pages.
* **Bookmarks** (folders, search, edit, open in current/new tab) and **history** (search, delete, clear by range) in Room. Private visits never reach history.
* **Private browsing:** separate window, task, **process** and WebView data directory; no history, no saved tabs, no bookmarks; cookies/storage/cache wiped when the session is closed; leftovers wiped on next start; hidden from screenshots/recents. Not anonymity — see below.
* **Advanced ad & tracker blocking:** bundled offline hosts rules, cached StevenBlack hosts + EasyList/EasyPrivacy updates, supported domain/path rules and exceptions, conservative cosmetic CSS filtering, global and per-site controls, and blocked-request counts. The parser intentionally skips unsupported rule syntax rather than guessing; it is not a full uBlock Origin/AdGuard replacement.
* **Downloads:** Android DownloadManager, sanitised file names, progress/cancel/retry/open/share, completion notification, storage/connectivity checks. Direct media files (MP4/WebM/…) are downloadable when the site serves them as ordinary files.
* **Media download (personal offline use):** menu action that scans the page for progressive media (`video`/`source`/`og:video` + observed CDN URLs), shows a mandatory legal disclaimer, then enqueues via DownloadManager. Protected/adaptive streams (typical on YouTube, TikTok, Instagram, Facebook) are not extracted; the UI states that clearly.
* **Web compatibility:** file upload, fullscreen video, desktop site, error/offline page with Retry, camera/microphone only after an explicit prompt each time.
* **Settings** (DataStore): theme (light/dark/system), language (Arabic/English/system), search engine (Google/Bing/DuckDuckGo), blocking, allowlist, new-tab page, startup behaviour, desktop default, clear history, clear website data, about, privacy policy, licenses, version.

## Supported Android versions

Android 9 (API 28) and newer. `compileSdk`/`targetSdk` 35.

## Browser engine

HISN renders pages with **Android System WebView** (Chromium-based, updated through the device's app store). The engine sits behind the `BrowserEngine` / `EngineSession` interfaces (`app/src/main/java/net/iegy/hisn/browser/`), so another engine could be evaluated later.

## Honest limitations

* WebView is not Chrome: **no Chrome extensions**, no sync, no built-in password manager, and some features depend on the installed WebView version. HISN does not claim feature or performance parity with Quetta or Chrome, and no benchmarks have been run.
* Ad blocking is **domain-level request blocking**. No cosmetic filtering, no blocking of same-domain ads, no HTTPS inspection.
* **Media download** offers progressive files found in the page (`<video>` / `<source>` / `og:video`) and media URLs observed while loading. A mandatory disclaimer states personal offline use only. No HLS/M3U8 remux, no `blob:` downloads, and nothing that bypasses DRM, paywalls or authentication. YouTube / TikTok / Instagram / Facebook often serve protected streams that cannot be extracted from WebView — the UI says so instead of failing silently.
* Private browsing hides nothing from websites, your network or your ISP. Downloaded files stay on the device. Bookmarks and settings changes are disabled in private windows. If the private process is killed abnormally, its web data directory is cleaned the next time a private window starts (best effort; the directory naming is a WebView implementation detail).
* No thumbnails in the tab switcher (cards show the site name) to keep memory low.
* Geolocation is not offered to pages. Third-party cookies are off by default, which can break some embedded logins.
* Debug APKs are for testing. Nothing here means the app is Google Play ready.

## Privacy

No analytics, no ad SDKs, no developer server. History, bookmarks, settings and download lists stay on the device and are excluded from cloud backup. The only requests HISN makes itself are the sites you open, your chosen search engine, and the optional filter-list download from `raw.githubusercontent.com`. Full text: [PRIVACY.md](PRIVACY.md) (also in the app).

## Third-party licenses

AndroidX, Jetpack Compose, Kotlin and kotlinx.coroutines are Apache-2.0. Filter lists: see [docs/FILTER_LISTS.md](docs/FILTER_LISTS.md) (StevenBlack/hosts is MIT). The in-app list is `app/src/main/assets/legal/licenses.txt`.

## Build locally

Requirements: JDK 17+, Android SDK with platform 35 (Android Studio installs both).

```bash
./gradlew testDebugUnitTest     # unit tests
./gradlew lintDebug             # Android lint
./gradlew assembleDebug         # -> app/build/outputs/apk/debug/app-debug.apk
```

## Build with GitHub Actions and get the APK

1. Push this repository to GitHub (branch `main` or `master`).
2. Open the **Actions** tab → **Android CI** (it also runs on pull requests, or start it with **Run workflow**).
3. When the run succeeds, open it and scroll to **Artifacts**, then download **`Hisn-Browser-APK`**. It contains **`Hisn-Browser-debug.apk`**.
4. Unzip, copy the APK to your phone, and open it. Android will ask you to allow installing apps from the source you opened it with (Files, Chrome, …); allow it for that app, then install.

The workflow (`.github/workflows/android.yml`) runs unit tests, lint, builds the debug APK, fails if the APK is missing, renames it, and uploads it. Test and lint reports are uploaded as `Hisn-Browser-reports`.

## Signed releases

`.github/workflows/release.yml` runs when you push a tag like `v1.0.0`. Add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 your-release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

With all four present it builds a release APK, verifies the signature with `apksigner`, and publishes it to a GitHub Release. Without them it publishes **nothing** (an unsigned release is not offered as distributable) and uploads the debug APK as an artifact instead. The keystore is decoded to a temporary file and shredded afterwards; secrets are never available to pull-request builds.

Create a keystore once with: `keytool -genkeypair -v -keystore hisn-release.jks -alias hisn -keyalg RSA -keysize 4096 -validity 10000`. Never commit it (`*.jks` is git-ignored).

## Change the version

Edit `hisn.versionName` / `hisn.versionCode` in `gradle.properties`, or pass `-Phisn.versionName=1.2.0 -Phisn.versionCode=12`. The release workflow derives them from the tag and run number.

## Project structure

```
app/src/main/java/net/iegy/hisn/
  HisnApp, AppContainer      process setup (private process isolation), dependency container
  MainActivity, PrivateActivity, BaseBrowserActivity, BrowserHost
  browser/    UrlResolver, BrowserEngine + WebViewEngine, ExternalLinks, HistoryPolicy
  tabs/       TabsModel (pure logic), TabManager (sessions, memory, lifecycle), TabsStore
  blocking/   DomainMatcher, Allowlist, BlockingEngine (load/update/decide)
  download/   DownloadController, FileNameSanitizer
  data/       Room entities, DAOs, repositories
  settings/   Settings, SettingsRepository (DataStore), BootPrefs (locale/theme at launch)
  viewmodel/  ViewModels + factory
  ui/         theme, components, browser, home, tabs, library, downloads, settings
app/src/main/assets/   filters/, legal/ (privacy AR/EN, licenses)
app/src/test/          unit + Robolectric tests
.github/workflows/     android.yml, release.yml
```

## Verification status

What was actually done in the authoring environment (no Android SDK / Maven access):

* The pure-logic code (URL resolution, file-name sanitising, domain matching, tab model, history policy) was compiled with Kotlin 2.0.21 and exercised with a throwaway check program — all checks passed.
* The engine, tab manager, download controller, blocking engine and data layer were **type-checked** with the Kotlin compiler against a real `android.jar` (API 30) plus small stubs for Room/WebKit/R — no errors.
* The Jetpack Compose UI, ViewModels, activities, DataStore code, Room schema generation, resources packaging, lint, the JUnit/Robolectric test suite and the Gradle build were **not** compiled or run. The workflow YAML parses; it has not run on GitHub.
* Dependency versions (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, …) and action versions (`@v4`) were chosen from knowledge, not resolved against live repositories; newer stable releases may exist.

## Troubleshooting

* **Gradle can't find the SDK:** set `ANDROID_HOME`, or add `sdk.dir=/path/to/sdk` to `local.properties`.
* **"Unsupported class file major version":** use JDK 17 (`JAVA_HOME`).
* **Install blocked on the phone:** allow "Install unknown apps" for the app you opened the APK with.
* **Pages look blank / WebView crashes:** update *Android System WebView* and Chrome in the Play Store.
* **A site breaks:** use the menu's per-site blocking switch, or add it to the allowlist.

## Credit

**حصن | HISN Browser** — تصميم وتطوير Mohammed Hussein | iegy.net
Designed & Developed by Mohammed Hussein | iegy.net
© 2026 Mohammed Hussein. All rights reserved.
