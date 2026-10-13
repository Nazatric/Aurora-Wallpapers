# Auroro Wallpapers

Auroro is a native Android wallpaper browser built with Kotlin and Jetpack Compose. It uses real provider results, keeps image proportions intact, and separates viewing, favoriting, downloading, and setting a wallpaper. The app is free and ad-free, with no account, ads, telemetry, or bundled wallpaper gallery.

## Sources and rights

Only the sources below are queried by the app. A source can be disabled in **Settings → Wallpaper sources**. Provider availability in this build does not imply that a live endpoint was reachable during this checkout; see [`BLOCKERS.md`](BLOCKERS.md) for verification status.

| Source | App behavior | Rights and restrictions |
| --- | --- | --- |
| **Wallhaven** | Official public API search; used by Search and For You. | Requests use the provider's `purity=100` parameter. This is Wallhaven's source-side policy, not a global Auroro keyword or sensitivity filter. Original image rights remain with their rights holders; the source page is authoritative. |
| **NASA APOD** | APOD Basic archive, newest first; no keyword search. Pixel dimensions are not reported and remain unknown. | APOD credit/copyright metadata identifies provenance but is not a reuse grant. Items are **view-only** in Auroro until item-specific reuse rights have been verified with the rights holder. Download and wallpaper-setting actions stay disabled. |
| **Art Institute of Chicago** | **Retired in 1.3.0.** Removed from For You, Search and discovery. | The catalogue repeatedly returned records without image dimensions or displayable files, which rendered as "Preview unavailable" tiles. Records saved before retirement keep their CC0 attribution and remain readable. |
| **Reddit (`r/Amoledbackgrounds`, `r/wallpaper`)** | **Paused / not integrated.** | No authorized OAuth application is configured. The app will not scrape Reddit HTML, impersonate a client, or bypass API limits. |

**Not integrated:** Pixabay remains out until explicit permission and current terms for wallpaper-file use are confirmed. **Unsplash:** the current [Unsplash API guidelines](https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines) (re-verified 2026-10-12) explicitly forbid replicating Unsplash's core experience in "wallpaper applications", and the `source.unsplash.com/random` endpoint is retired; Auroro therefore ships no Unsplash integration and no deprecated-endpoint use. If Unsplash ever grants this app an exception, an official integration must use the search API with `utm_source`/`utm_medium=referral` attribution, photographer-profile links, CDN hotlinking, download-event tracking through the documented download endpoint, and confidential keys (never embedded in a public APK). Lorem Picsum is omitted because random placeholders do not add curated value. Openverse is retired from the catalogue. Legacy Openverse records, attribution, favorites, and existing local files remain readable, but the app makes no new Openverse requests, previews, downloads, or wallpaper preparations. The Art Institute of Chicago was retired in 1.3.0 after its records repeatedly arrived without usable image dimensions or displayable files; previously saved AIC records keep their CC0 attribution.

### Provider implementation details

- Wallhaven queries are built from documented API parameters; local orientation, exact aspect, and edge-size checks use the API's original pixel dimensions. Search/filter combinations that a provider does not support are reported instead of silently rewritten.
- NASA APOD results require a valid date, image media type, and NASA-hosted media URL. APOD Basic does not return original pixel dimensions; width/height parameters in a rendition URL are not treated as source measurements. Unknown dimensions remain unknown. Its bounded archive lane is separate from the 720 px search-result gate, and every APOD item remains view-only because attribution does not grant reuse permission.
- The retired Art Institute integration required public-domain records and IIIF `info.json` dimensions; in operation the catalogue kept returning records without usable dimensions or files, so the source was removed in 1.3.0 rather than shipping broken previews.
- Search retains source-specific ordering and pagination. One provider's error or unsupported filter does not turn another provider's result into a failure. Loading, partial, empty, unavailable, timeout, and rate-limit states are distinct.

## For You and search

- For You runs a source-isolated Wallhaven query lane plus a bounded, newest-first NASA APOD archive lane. The internal 88-recipe query catalogue drives Wallhaven, and APOD is browsed separately without inventing dimensions. **The mix keeps loading while you scroll:** once the first bounded pass is consumed, For You plans a fresh batch of recipes, excludes everything already shown or recently seen, and appends only genuinely new validated wallpapers; loaded items survive a failed batch, retries are bounded, and an honest end-of-feed state appears only after consecutive batches add nothing new. Search-query results require source-reported dimensions with a minimum 720 px short edge; no global portrait or 1080p gate is imposed. Recipe-label evidence ranks and diagnoses results but never rejects a source-returned image. Requests, pages, concurrency, timeouts, and archive item counts are bounded independently by source.
- **There is no app-wide SFW/NSFW setting or hidden tag/keyword rejection.** The retired preference is removed during migration. For You does not exclude a result merely because of its category or tag; users can apply explicit category or geometry filters, and Wallhaven's own `purity=100` request remains a Wallhaven-specific source policy.
- A cached, validated search mix is shown before refresh when available. Requests are cancellable, obsolete generations are ignored, source failures are isolated, partial successes survive, and recent recipes/wallpaper IDs are stored locally in bounded form. Search sessions keep their own query and page cursor while navigating.
- Orientation and aspect-ratio filters use original provider dimensions, never tile shape or thumbnail estimates. Missing dimensions remain unknown and cannot satisfy a geometry filter; APOD's view-only archive lane is the explicit exception to the search-result quality floor, not a source of guessed geometry.

## Image, cache, and local data

- Gallery tiles use provider previews and `ContentScale.Fit`; they do not force-crop the underlying wallpaper. Provider attribution, rights, original dimensions, and source-page links live in detail views rather than on top of browse images.
- Coil owns a bounded memory cache (14% of the app's available memory) and a disk cache controlled by Settings. A small HTTP response cache is separate. Room stores wallpaper metadata and local user references—not wallpaper bytes. NASA APOD page metadata has a bounded time-to-live cache.
- Favorites, collections, recent history, search terms, filters, and appearance choices stay on-device. Recent search history is bounded and clearable. There is no sign-in or sync service.
- **Download** streams and validates supported JPEG, PNG, or WebP originals into Android MediaStore or app-private storage. Queue state, progress, retry, cancellation, storage checks, and deletion are separate from the disposable image cache.
- **Set Wallpaper** is a separate action. It uses an existing saved original or a disposable app-cache copy, then opens target selection and a proportion-preserving crop flow. It does not enqueue work or create/change a Download record or persistent download file. Rights-disabled APOD records cannot enter this flow.

## Interface and accessibility

The app uses a shared image-led Compose design across Home, Search, filters, source status, detail/crop, favorites, collections, downloads, settings, menus, and dialogs. Cards preserve image geometry; provider facts stay in detail. System font scaling is respected, controls have accessible labels and touch targets, and source/query errors are visible instead of represented as successful empty feeds.

Appearance has three independent mode choices—**Follow system** (the default), **Light**, and **Dark**—plus Advanced themes **Material 3**, **Frutiger Aero**, and **Frutiger Zen**, each supporting all three modes. New installs keep Frutiger Aero as the selected design theme to match the supplied glass-forward direction. System theme changes are observed while Follow system is selected. Theme and mode are stored independently. The retired Frutiger Flower name is not exposed as a theme or discovery topic. Existing preferred/skipped For You topics remain available under ordinary labels; saved legacy botanical-topic IDs are read as **Botanical art** and normalized on the next ordinary settings update; topic choices bias the query mix rather than globally rejecting provider results. The design handoff for all nine reference screens, interaction states, and Figma/Penpot-ready tokens is [`docs/AURORO_DESIGN_SYSTEM.md`](docs/AURORO_DESIGN_SYSTEM.md).

Live glass is rendered by the Apache-2.0 [Kyant0 backdrop](https://github.com/Kyant0/AndroidLiquidGlass) engine (`io.github.kyant0:backdrop` 2.0.1) — the same upstream library the GPL-3.0 BitChord app vendors; Auroro's glue in `core/design/LiquidGlass.kt` is a clean-room implementation and copies no BitChord code. One page-level recording captures the NavHost content with the ambient gradient painted as its opaque base, so the bottom navigation bar and the drawer sheet — mounted as siblings *outside* the recording — blur, saturate and (on API 33+) refract the real, scrolling artwork behind them. Wallpaper detail and crop mount their own local recordings on the displayed image, so their floating controls sample the artwork itself, and pages now scroll behind the floating bar, which is what makes the effect read as glass. Panels inside scrolling content deliberately keep the lightweight Aero treatment (translucent tint, specular rim): capturing inside the recording would self-sample, and full-resolution captures on every card would wreck scroll performance. **Reduced effects** or **Reduce transparency** disable all capture and use a legible opaque fallback; **Balanced** keeps blur but skips the refraction shader. Blur needs API 31+ and the lens API 33+; below that the same fallback applies, and no interaction or meaning depends on blur. Image tiles themselves are not blurred. Frame-time effects are measured on the hosted CI emulator (see CHANGELOG 1.3.0); no physical-device latency, overdraw, or battery result is available.

The adaptive launcher foreground uses Google's Apache-2.0 Material Icons `wallpaper` glyph on a transparent foreground layer, with adaptive and monochrome icon resources. Asset provenance and font licences are recorded in [`ASSET_LICENSES.md`](ASSET_LICENSES.md); third-party software licences are in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

### Implementation references considered

- [AndroidX Compose samples](https://github.com/android/compose-samples) and AndroidX guidance informed native Compose navigation, system insets, semantics, and image layout; Auroro does not embed a WebView.
- [Android Compose performance guidance](https://developer.android.com/develop/ui/compose/performance) and [graphics guidance](https://developer.android.com/develop/ui/compose/graphics/draw/overview) informed stable list keys, bounded lazy grids, image sizing, and restrained effects. These are implementation choices, not measured performance results.
- [Coil](https://github.com/coil-kt/coil) is used for lifecycle-aware image requests with explicit memory/disk bounds. AndroidX Macrobenchmark/Baseline Profile remains a candidate for follow-up; no benchmark module or physical-device result is claimed.
- [Muzei](https://github.com/muzei/muzei) and [Aves](https://github.com/deckerst/aves) were considered as open-source wallpaper/gallery references, not dependencies; no application code or wallpaper assets were copied.
- [Google Material Icons](https://github.com/google/material-design-icons) supplied the licensed launcher/notification glyph. The Kyant0 backdrop engine replaced Haze in 1.3.0 and remains the single backdrop-capture/render dependency to avoid duplicate blur/overdraw systems.
- Web/CSS-oriented UI repositories and skills that require a separately configured design server were not used as app runtime dependencies. No icon/image source with unclear or incompatible licensing was copied.

## Build and verification

Requirements: JDK 17, Android SDK Platform 37, and Android Gradle Plugin-compatible Build Tools.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Install a local debug build with `./gradlew installDebug`. A debug-signed APK is for development/testing only and is **not** a production release. Release builds use R8 and resource shrinking. Production signing reads a private keystore path/password and alias from environment variables; CI expects the four `AURORO_*` signing secrets. Never commit or share signing keys or passwords.

The emulator smoke script installs the produced APK and exercises live provider-backed flows, navigation, filter controls, favorites/collections, settings, a set-only wallpaper path, and the separate Download queue where the emulator can reach the providers. Emulator screenshots are machine-collected artifacts; they are not a substitute for manual visual review or physical-device testing. No FPS, frame-time, memory, startup-latency, battery, or APK-size claim is made without a recorded measurement. Macrobenchmark/Baseline Profile and representative-device performance validation remain follow-up work.

**Current verification:** CI runs [38300321558](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38300321558) and [38304599388](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38304599388) passed end-to-end: unit tests, lint, debug + release assembly, `apksigner` debug-signature verification, and the complete hosted-emulator UI smoke (**181 checks**), including provider-backed search, orientation filters, favorites/collections, download and set-wallpaper separation, offline detail from local storage, settings persistence across process recreation, and honest empty/error states. Measured cold-start, warm-restart and whole-journey `gfxinfo` frame figures from the hosted API 35 emulator are recorded in [`CHANGELOG.md`](CHANGELOG.md); they are emulator results, not physical-device performance. Live-provider yields vary with rate limits; item-level rights review, physical-device measurement, and production signing remain outstanding — see [`BLOCKERS.md`](BLOCKERS.md). Do not infer production-release readiness from a debug signature or source code alone.

`versionName` is **1.2.0** and `versionCode` is **4** (the published `v1.0.0`/`v1.1.0` releases predate this Compose rebuild; this train continues from `v1.1.0` rather than reusing the retired `0.1` plan). No production-signed APK is produced without a configured signing key; until the `AURORO_KEYSTORE_*` secrets exist, the `v1.2.0` GitHub Release carries the CI **debug-signed** APK explicitly labeled "(debug build)" for evaluation only — see [`BLOCKERS.md`](BLOCKERS.md) and [`CHANGELOG.md`](CHANGELOG.md).

## Project layout

```text
app/src/main/java/com/auroro/wallpapers/
  app/                 Application container, navigation, state and Compose root
  core/model/          Provider-neutral metadata, geometry, filters and discovery
  core/network/        HTTPS allow-lists, HTTP clients, pacing and error mapping
  core/data/           Wallhaven, NASA APOD, paging and repositories
  core/database/       Room entities, DAOs and data-preserving migrations
  core/data/download/  Download pipeline, disposable apply cache and WallpaperManager
  core/design/         Shared palette, liquid-glass surfaces, typography and components
  feature/             Home, Search, detail, offline, filters, collections and settings
app/src/test/           Provider, policy, discovery, migration, persistence and download tests
```

The app uses Kotlin, Compose, Room, DataStore, WorkManager, Coil, Retrofit, OkHttp, and the Kyant0 backdrop liquid-glass engine. It targets Android 36 (minimum Android 10 / API 29) and compiles against SDK 37. See [`PRIVACY.md`](PRIVACY.md) for network/on-device data handling and [`BLOCKERS.md`](BLOCKERS.md) for outstanding checks.
