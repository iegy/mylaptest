// Top-level build file. Plugin versions live in gradle/libs.versions.toml so the
// whole toolchain (AGP / Kotlin / KSP) is pinned in a single, coherent place.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
