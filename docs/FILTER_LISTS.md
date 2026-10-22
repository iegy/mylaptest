# Filter lists and licenses

HISN uses a layered, on-device filtering engine. It does not install certificates, decrypt HTTPS, or send browsing history to a filter server.

| Source | Purpose | License / notes |
| --- | --- | --- |
| `app/src/main/assets/filters/default_hosts.txt` | Small offline starter list | Original compilation by Mohammed Hussein (© 2026) |
| [StevenBlack unified hosts](https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts) | Domain-based ad/tracker blocking | MIT; see upstream repository for notices |
| [EasyList](https://easylist.to/easylist/easylist.txt) | Supported subset of network and cosmetic rules | See EasyList project terms and notices |
| [EasyPrivacy](https://easylist.to/easylist/easyprivacy.txt) | Supported subset of tracker rules | See EasyList project terms and notices |

## Safety and compatibility

- Lists are fetched only after user action or the existing weekly refresh; never from the WebView request callback.
- Each list is size-bounded and replaced atomically after validation. A failed download preserves the previous file.
- Host lists work offline. Advanced rules are cached in the app's private storage.
- The engine supports domain-anchored rules (`||host^`), common URL/path patterns, `@@` exceptions, `$third-party`, `$domain=` and common `##` / `#@#` cosmetic rules.
- Unsupported Adblock Plus options, procedural cosmetic selectors, and complex regex rules are intentionally ignored rather than interpreted incorrectly. Resource-type-specific rules are skipped until request type can be mapped consistently across supported WebView versions.
- Cosmetic rules are limited to conservative CSS selectors and injected as CSS only; filter lists cannot execute JavaScript. Broad page-hiding selectors such as `body`, `html`, and `*` are rejected. A `MutationObserver` lets CSS apply to elements inserted after initial page load.
- Main-frame navigations are never intercepted. Site allowlist entries disable both network and cosmetic filtering for that site. Host-list matches are third-party-only, while explicit supported EasyList URL rules may match same-site ad paths.
- This is a meaningful improvement over domain-only blocking, but it is not a complete uBlock Origin / AdGuard-compatible parser. Some first-party, server-inserted, script-driven, and unsupported-rule ads will remain.
