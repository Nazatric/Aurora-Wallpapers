# Auroro Wallpapers

Auroro is a native Android wallpaper browser built with Kotlin and Jetpack Compose. It uses real provider results, keeps image proportions intact, and separates viewing, favoriting, downloading, and setting a wallpaper. The app is free and ad-free, with no account, ads, telemetry, or bundled wallpaper gallery.

## Sources and rights

Only the sources below are queried by the app. A source can be disabled in **Settings → Wallpaper sources**. Provider availability in this build does not imply that a live endpoint was reachable during this checkout; see [`BLOCKERS.md`](BLOCKERS.md) for verification status.

| Source | App behavior | Rights and restrictions |
| --- | --- | --- |
| **Wallhaven** | Official public API search; used by Search and For You. | Requests use the provider's `purity=100` parameter. This is Wallhaven's source-side policy, not a global Auroro keyword or sensitivity filter. Original image rights remain with their rights holders; the source page is authoritative. |
| **NASA APOD** | Current APOD Basic archive, newest first; no keyword search. | APOD credit/copyright metadata identifies provenance but is not a reuse grant. Items are **view-only** in Auroro until item-specific reuse rights have been verified with the rights holder. Download and wallpaper-setting actions stay disabled. |
| **Art Institute of Chicago** | Official artwork search and IIIF image metadata. | Both the API query and mapper require `is_public_domain=true` and an image ID. Qualifying Open Access works are attributed and marked CC0; exact IIIF dimensions are used for geometry filters. |
| **Reddit (`r/Amoledbackgrounds`, `r/wallpaper`)** | **Paused / not integrated.** | No authorized OAuth application is configured. The app will not scrape Reddit HTML, impersonate a client, or bypass API limits. |

**Not integrated:** Pixabay remains out until explicit permission and current terms for wallpaper-file use are confirmed. Lorem Picsum is omitted because random placeholders do not add curated value. Openverse is retired from the catalogue. Legacy Openverse records, attribution, favorites, and existing local files remain readable, but the app makes no new Openverse requests, previews, downloads, or wallpaper preparations.

### Provider implementation details

- Wallhaven queries are built from documented API parameters; local orientation, exact aspect, and edge-size checks use the API's original pixel dimensions. Search/filter combinations that a provider does not support are reported instead of silently rewritten.
- NASA APOD results require a valid date, image media type, and NASA-hosted media URL. Optional title, credit, copyright, and dimensions are preserved when supplied; unknown facts are not inferred. Because the API's attribution fields do not grant reuse permission, every APOD item remains view-only.
- Art Institute API search is constrained to public-domain records with images. The official IIIF `info.json` response supplies original dimensions; if that lookup fails, dimensions remain unknown and geometry filters may exclude the item rather than guessing from a thumbnail.
- Search retains source-specific ordering and pagination. One provider's error or unsupported filter does not turn another provider's result into a failure. Loading, partial, empty, unavailable, timeout, and rate-limit states are distinct.

## For You and search

- For You is built from validated Wallhaven results only. Its internal visual-query catalogue contains 88 recipes, each mapped to real Wallhaven text queries; a heading appears only when returned provider metadata supports that recipe. Results are deduplicated, dimension-checked, filtered to portrait 1080p-or-larger originals, and bounded by per-session request, page, concurrency, and timeout limits. Recipe metadata matching is for discovery relevance only.
- **There is no app-wide SFW/NSFW setting or hidden tag/keyword rejection.** The retired preference is removed during migration. For You does not exclude a result merely because of its category or tag; users can apply explicit category or geometry filters, and Wallhaven's own `purity=100` request remains a Wallhaven-specific source policy.
- A cached validated mix is shown before refresh when available. Requests are cancellable, obsolete generations are ignored, partial successes survive provider errors, and recent recipes/wallpaper IDs are stored locally in bounded form. Search sessions keep their own query and page cursor while navigating.
- Orientation and aspect-ratio filters use original provider dimensions, never tile shape or thumbnail estimates. Missing dimensions remain unknown and cannot satisfy a geometry filter.

## Image, cache, and local data

- Gallery tiles use provider previews and `ContentScale.Fit`; they do not force-crop the underlying wallpaper. Provider attribution, rights, original dimensions, and source-page links live in detail views rather than on top of browse images.
- Coil owns a bounded memory cache (14% of the app's available memory) and a disk cache controlled by Settings. A small HTTP response cache is separate. Room stores wallpaper metadata and local user references—not wallpaper bytes. NASA APOD and Art Institute page metadata have bounded time-to-live caches.
- Favorites, collections, recent history, search terms, filters, and appearance choices stay on-device. Recent search history is bounded and clearable. There is no sign-in or sync service.
- **Download** streams and validates supported JPEG, PNG, or WebP originals into Android MediaStore or app-private storage. Queue state, progress, retry, cancellation, storage checks, and deletion are separate from the disposable image cache.
- **Set Wallpaper** is a separate action. It uses an existing saved original or a disposable app-cache copy, then opens target selection and a proportion-preserving crop flow. It does not enqueue work or create/change a Download record or persistent download file. Rights-disabled APOD records cannot enter this flow.

## Interface and accessibility

The app uses a shared image-led Compose design across Home, Search, filters, source status, detail/crop, favorites, collections, downloads, settings, menus, and dialogs. Cards preserve image geometry; provider facts stay in detail. System font scaling is respected, controls have accessible labels and touch targets, and source/query errors are visible instead of represented as successful empty feeds.

Haze 2.0.1 Glass is used only where backdrop glass is appropriate. The regular material samples an earlier Compose backdrop with size-aware blur/refraction and a directional specular highlight; when a simplified renderer cannot provide those optics, Haze preserves tint/shape and approximates lighting rather than making meaning depend on blur. The app's reduced-transparency mode skips backdrop rendering for an opaque, legible fallback. Image tiles themselves are not blurred. Overdraw and battery impact still need measurement on supported physical devices. Haze 2.0.1 is pinned; its 2.0 line is beta and Glass APIs are experimental. The separately evaluated, Apache-2.0 [Kyant0 AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) project was not added: its README documents a lower-level backdrop effect without high-level app components, duplicating the selected Haze capture/render path.

The adaptive launcher foreground uses Google's Apache-2.0 Material Icons `landscape` glyph on a transparent foreground layer, with adaptive and monochrome icon resources. Asset provenance and font licences are recorded in [`ASSET_LICENSES.md`](ASSET_LICENSES.md); third-party software licences are in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

### Implementation references considered

- [AndroidX Compose samples](https://github.com/android/compose-samples) and AndroidX guidance informed native Compose navigation, system insets, semantics, and image layout; Auroro does not embed a WebView.
- [Android Compose performance guidance](https://developer.android.com/develop/ui/compose/performance) and [graphics guidance](https://developer.android.com/develop/ui/compose/graphics/draw/overview) informed stable list keys, bounded lazy grids, image sizing, and restrained effects. These are implementation choices, not measured performance results.
- [Coil](https://github.com/coil-kt/coil) is used for lifecycle-aware image requests with explicit memory/disk bounds. AndroidX Macrobenchmark/Baseline Profile remains a candidate for follow-up; no benchmark module or physical-device result is claimed.
- [Muzei](https://github.com/muzei/muzei) and [Aves](https://github.com/deckerst/aves) were considered as open-source wallpaper/gallery references, not dependencies; no application code or wallpaper assets were copied.
- [Google Material Icons](https://github.com/google/material-design-icons) supplied the licensed launcher/notification glyph. Haze and AndroidLiquidGlass were evaluated above; Haze remains the single backdrop-capture/render dependency to avoid duplicate blur/overdraw systems.
- Web/CSS-oriented UI repositories and skills that require a separately configured design server were not used as app runtime dependencies. No icon/image source with unclear or incompatible licensing was copied.

## Build and verification

Requirements: JDK 17, Android SDK Platform 37, and Android Gradle Plugin-compatible Build Tools.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Install a local debug build with `./gradlew installDebug`. A debug-signed APK is for development/testing only and is **not** a production release. Release builds use R8 and resource shrinking. Production signing reads a private keystore path/password and alias from environment variables; CI expects the four `AURORO_*` signing secrets. Never commit or share signing keys or passwords.

The emulator smoke script installs the produced APK and exercises live provider-backed flows, navigation, filter controls, favorites/collections, settings, a set-only wallpaper path, and the separate Download queue where the emulator can reach the providers. Emulator screenshots are machine-collected artifacts; they are not a substitute for manual visual review or physical-device testing. No FPS, frame-time, memory, startup-latency, battery, or APK-size claim is made without a recorded measurement. Macrobenchmark/Baseline Profile and representative-device performance validation remain follow-up work.

**Current local verification:** this checkout's Android build/runtime has not yet been validated in the present environment. See [`BLOCKERS.md`](BLOCKERS.md). Do not infer a successful APK, install, screenshot review, live provider response, or production release from source code alone.

`versionName` is **0.1** and `versionCode` is **3**. No production APK or release is produced without a configured signing key; see [`CHANGELOG.md`](CHANGELOG.md).

## Project layout

```text
app/src/main/java/com/auroro/wallpapers/
  app/                 Application container, navigation, state and Compose root
  core/model/          Provider-neutral metadata, geometry, filters and discovery
  core/network/        HTTPS allow-lists, HTTP clients, pacing and error mapping
  core/data/           Wallhaven, NASA APOD, Art Institute, paging and repositories
  core/database/       Room entities, DAOs and data-preserving migrations
  core/data/download/  Download pipeline, disposable apply cache and WallpaperManager
  core/design/         Shared palette, Haze surfaces, typography and components
  feature/             Home, Search, detail, offline, filters, collections and settings
app/src/test/           Provider, policy, discovery, migration, persistence and download tests
```

The app uses Kotlin, Compose, Room, DataStore, WorkManager, Coil, Retrofit, OkHttp, and Haze. It targets Android 36 (minimum Android 10 / API 29) and compiles against SDK 37. See [`PRIVACY.md`](PRIVACY.md) for network/on-device data handling and [`BLOCKERS.md`](BLOCKERS.md) for outstanding checks.
