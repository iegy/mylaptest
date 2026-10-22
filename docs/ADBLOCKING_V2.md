# HISN Advanced Blocking implementation notes

## Included in this build

- Domain blocking from the bundled starter hosts list and cached StevenBlack unified hosts list.
- EasyList and EasyPrivacy downloads, each validated independently and saved through a temporary file plus atomic replacement where supported.
- Conservative support for `||host^`, common domain-anchored paths, exact URL anchors, `@@` exceptions, `$third-party`, `$domain=`, and common cosmetic selectors (`##` and `#@#`).
- CSS-only cosmetic injection after a page finishes loading. A MutationObserver handles subsequently inserted elements without re-writing the style element unless the CSS changes.
- Site allowlist applies to both request filtering and cosmetic filtering. Main-frame navigations are not intercepted.
- Request-time decisions use in-memory immutable snapshots; no file/network I/O occurs in `shouldInterceptRequest`.
- Unit tests cover domain rules, exceptions, domain options, cosmetic selectors, and unsafe selector rejection.

## Important compatibility note

This is an intentionally conservative parser, not a complete implementation of the Adblock Plus filter syntax. Rules with unsupported resource-type options, regular-expression syntax, procedural cosmetic filters, scriptlets, or other advanced options are skipped. This is safer than silently interpreting them incorrectly. Ads delivered from the same origin as site content and ads rendered inside video streams may still appear. No HTTPS interception or custom certificate is used.

## Try it

1. Build and install the debug APK using Android Studio or the included GitHub Actions workflow.
2. Open Settings → Ad/tracker blocking and tap **Update filter lists now** while online.
3. Open several ad-supported pages, compare the blocked-request counter, and confirm page navigation / login / video still work.
4. Use the per-site switch or allowlist if a page breaks.
5. Review the GitHub Actions build and unit-test results before treating the build as production-ready.
