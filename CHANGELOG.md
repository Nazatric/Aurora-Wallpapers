# Changelog

Notable changes to Auroro Wallpapers are recorded here.

> The v0.1 entry is prepared but has **not** been published or tagged. Production distribution must wait until durable release signing is configured and the APK signature is verified.

## [0.1] — pending signed release

### Added

- Native Kotlin and Jetpack Compose wallpaper discovery with 88 internal, source-mapped visual recipes. For You only presents recipes after provider metadata and local quality/sensitivity filters validate usable results.
- Wallhaven SFW discovery and truthful multi-source search, with provider-aware query parameters, real image metadata, bounded paging, rate-limit handling, partial-result/error states, and cancellation.
- Clearly attributed Openverse archive/search results, including provider details and licence information when supplied; unknown metadata remains unknown.
- Local favorites, collections, viewing history, preferences, and separate Download queue/persistence.
- A distinct Set Wallpaper flow that prepares from a reusable saved original or disposable cache file, with Home/Lock/Both targets and proportion-aware crop controls; setting does not create a download record.
- Appearance, accent, accessibility, reduced-transparency, discovery, repeat-avoidance, and sensitivity preferences.

### Improved

- Recipe selection is randomized but stable within a session, avoids recent repeats where possible, and uses bounded, validated caches and request budgets.
- Search and discovery preserve successful sources while reporting empty, partial, timeout, provider-error, and rate-limit outcomes rather than presenting failures as successful results.
- Wallpaper cards preserve source image proportions; detail, offline, and download paths retain the real provider metadata and available attribution.

### Verification and distribution

- App version name: `0.1`; Android `versionCode`: `3`.
- The hosted Android CI build, unit tests, lint, APK signature checks, and API 35 emulator smoke passed for the current branch. See [BLOCKERS.md](BLOCKERS.md) for the run link and verification limits.
