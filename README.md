# Auroro Wallpapers

Auroro is a Kotlin Android wallpaper platform. **As of 1.4.0 the entire application UI has been removed at the maintainer's direction — every screen, the design system, and all UI dependencies are gone; nothing was kept.** The `app` module now builds a UI-less shell APK (no launcher activity is declared). The repository retains the full data/domain layer, which continues to be verified by CI unit tests, lint, and debug/release assembly.

## What remains (data/domain layer)

- **Providers:** Wallhaven (official public API search, `purity=100` source policy) and NASA APOD (bounded newest-first archive lane, view-only, no invented dimensions). The Art Institute of Chicago was retired in 1.3.0 after its catalogue repeatedly returned records without usable image dimensions; previously saved AIC records keep their CC0 attribution and remain readable. Reddit and Pixabay are not integrated pending authorized API access; Unsplash's API guidelines explicitly forbid wallpaper applications, so no Unsplash integration ships; Openverse is retired with legacy records preserved. See [`BLOCKERS.md`](BLOCKERS.md) for provider verification status.
- **Discovery:** a bounded 88-recipe query catalogue and session planner drive source-isolated Wallhaven lanes plus the APOD archive lane. Results pass real-dimension quality validation (source-reported pixel dimensions, minimum edge gates, bounded per-recipe counts); local pools, snapshot caches, and recently-seen exclusion keep mixes fresh. Provider failures are isolated per source.
- **Persistence and settings:** Room (with migration tests) stores wallpaper metadata and local user references — never wallpaper bytes; DataStore holds settings, favorites, collections, history, and bounded search history. All data stays on-device; there is no sign-in, sync, telemetry, or advertising.
- **Download and apply pipeline:** `DownloadExecutor` over WorkManager (expedited/foreground data-sync) streams and validates JPEG/PNG/WebP originals into MediaStore or app-private storage, with progress/result notifications, retry, cancellation, and storage checks. A separate wallpaper-apply repository prepares proportion-preserving crop sources from saved originals or disposable cache copies. Rights-disabled APOD records cannot enter download or apply flows.
- **Networking:** Retrofit + OkHttp with per-source clients, bounded HTTP response caching, and provider-validated URL policies (no unvalidated hosts are fetched).

## What was removed in 1.4.0

All Jetpack Compose screens and navigation (`feature/**`), the complete design system including the Aero theme, glass components, and the Kyant0 liquid-glass glue (`core/design/**`), `MainActivity`, `AuroroRoot`, `MainViewModel`/`FeedUiState`, Coil image-loader wiring, launcher/adaptive/monochrome icons, bundled Open Sans fonts, splash/theme/color resources, the emulator UI smoke test and its CI job, the design-system document, and every UI-only dependency (Compose BOM/UI/Foundation/Material 3, Material Icons Extended, Activity/Lifecycle/Navigation-Compose, SplashScreen, Coil, Kyant0 backdrop).

The unreleased 1.3.0 UI work (liquid-glass port, feed-continuation presentation) was removed with the rest of the presentation layer; the 1.3.0 **data-layer** changes (AIC retirement, discovery quality gating) remain in place. [`CHANGELOG.md`](CHANGELOG.md) holds the full record — UI behavior described in older entries no longer exists in this tree.

## Build and verification

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The `Android CI` workflow runs unit tests, lint, and debug + release assembly on every push, verifies APK signatures with `apksigner`, and attaches APKs to GitHub Releases on `v*` tags. Release signing reads `AURORO_KEYSTORE_*` secrets/environment only; no key material is committed. An optional live-provider check (curl-based, no UI involved) lives at `scripts/live_source_smoke.sh`.

## Privacy and rights

Network use and on-device data handling are documented in [`PRIVACY.md`](PRIVACY.md). No wallpaper photographs are bundled; all artwork is requested at runtime from upstream providers under their own terms, and original image rights remain with their rights holders. Software attributions are in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md); remaining asset attributions are in [`ASSET_LICENSES.md`](ASSET_LICENSES.md).
