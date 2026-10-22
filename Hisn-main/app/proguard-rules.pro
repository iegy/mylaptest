# HISN Browser — R8 rules.
# Room, Compose, DataStore and AndroidX ship their own consumer rules, so only
# project-specific keeps are needed here.

# Keep line numbers for readable crash reports without exposing source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WebView clients are referenced by the framework through overridden methods only,
# so no extra keep rules are required. No JavaScript interfaces are exposed.
