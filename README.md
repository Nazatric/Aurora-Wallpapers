# Auroro Wallpapers

A native Android wallpaper browser built with Kotlin and Jetpack Compose. The interface uses deep-ocean glass surfaces, restrained aqua/emerald accents and images loaded from real provider catalogues. Auroro has no bundled wallpaper gallery, account, advertising, analytics or in-app purchase SDK.

## Integrated sources

Only **Wallhaven** and **Openverse** are searched in the app.

### Wallhaven

Auroro uses the public Wallhaven API v1 over HTTPS; a key is not required for public search. Requests always specify SFW purity. Search results include provider-reported dimensions, tags, colours, uploader and source-page details where available. Relevance, newest, popular and random ordering use Wallhaven's own API for supported query combinations. A blank-query relevance request was observed to return no rows while reporting a nonzero total, so blank Wallhaven searches use `date_added` (including when the default relevance setting reaches the query builder); keyword relevance remains unchanged. Categories, its colour palette, supported ratio tokens and minimum-size hints are provider filters; exact ratio and edge thresholds are checked locally against returned pixel dimensions. Results link to their Wallhaven page. Its API does not report an image licence; images remain the responsibility of their original rights holders.

Official API reference: <https://wallhaven.cc/help/api>

### Openverse

Auroro searches the documented Images API at `https://api.openverse.org/v1/images/`. The current API reference says anonymous access is supported and anonymous requests are sufficient for most users; registering for OAuth credentials gives higher limits. Auroro therefore sends **no API key, client secret or bearer token**. It persists a local 20-request/minute burst ceiling based on the current public response header and observes Openverse's `anon_burst` and `anon_sustained` search quotas; provider limits may change. A rate-limit response is shown rather than repeatedly retried. Search pages are fetched incrementally and capped to avoid treating the API as a bulk-download service.

Search uses documented query, tag, category, licence, format-extension, aspect-bucket, size-band and dead-link filters where applicable. Openverse's general `q` search and tag-field search are alternatives: its API ignores tag-field search when `q` is present, so Auroro prevents that combination. Openverse remains relevance-ranked. Its aspect bucket is orientation-only (`tall`, `wide`, or exact `square`), not an exact ratio; Auroro sends it only when the full local tolerance interval fits that orientation (or the user explicitly selects one). Its size filter uses coarse pixel-area bands. Both are safe prefilters, and actual provider-reported dimensions remain authoritative on-device. Creator, attribution text, originating page, provider, tags and licence details are retained and shown when supplied. Openverse indexes third-party works and does not verify every licence claim; check the original page and licence terms before reuse. Direct downloads are enabled only for recognized licences and supported image formats. No-derivatives licences can be saved as originals but are not offered for wallpaper-setting/cropping; deprecated sampling licences are browse-only in Auroro.

Official API reference: <https://api.openverse.org/v1/>; API documentation: <https://docs.openverse.org/>; search behavior: <https://docs.openverse.org/api/reference/search_algorithm.html>

## Search, filters and local data

- Home topics run real searches. For You uses tags from locally saved or viewed wallpapers; it falls back to the latest feed when there are no such tags. It is not an AI recommendation service.
- Search combines enabled providers without inventing a cross-provider popularity score. Each provider keeps its own pagination and ordering; failures are reported alongside results from another source. Home tabs and Search retain independent in-memory requests, results and pager cursors while navigating; an interrupted combined page is rolled back and safely retried.
- Aspect ratio is `width / height` from provider dimensions. A match uses a relative tolerance (3% by default; adjustable in Settings). Presets include 9:16, 16:9, 4:3, 1:1, 21:9 and 9:19.5, plus custom ratios. Minimum-size filters use the short and long edges so portrait and landscape images are treated consistently. Openverse's coarse server filters never replace local checks. Some provider/filter combinations are unavailable and are not presented as supported.
- Wallpaper cards preserve each image's own aspect ratio in a staggered grid. They use provider-supplied previews when safe; detail views and downloads retain the media URL, and the full composition is fit rather than forced into a crop.
- Favorites, collections, viewing history and wallpaper metadata are stored locally with Room. Up to eight recent search terms are stored in DataStore and can be cleared from Search. Collections support creation, rename, deletion and membership changes. Favoriting does not download an image. The database migration keeps existing records and download URIs; retired-provider records cannot be downloaded again, but an existing local file can still be applied.
- Downloads stream and validate the original JPEG, PNG or WebP file, expose progress and cancellation, check storage capacity (including periodic checks when the server omits its content length), and save through MediaStore or private app storage. Downloads, active/failed queue entries and viewing history are separate views; saved files are never confused with the disposable image cache. Wallpaper setting uses Android's `WallpaperManager`, with a full-screen crop preview and Home, Lock or Both targets where supported.
- Settings include appearance, accent, transparency, aspect tolerance, cache size, download location, notifications, source availability and optional set-after-download behavior. Favorites are never automatically downloaded.

## Build and test

Requires JDK 17, Android SDK Platform 37 and Build Tools 37.0.0.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Install the debug build on a connected device with `./gradlew installDebug`, or install `app/build/outputs/apk/debug/app-debug.apk` with `adb`. A debug APK is debug-signed. Release signing requires a private keystore supplied locally or through the documented GitHub Actions secrets; never commit signing material.

Unit tests use local fixtures and a mock HTTP server; they do not require provider credentials or call live catalogues. GitHub Actions also installs the built APK on an Android 15 emulator and exercises real Wallhaven/Openverse search, orientation filtering, detail/favorite/download flows, saved download records, the wallpaper target chooser, settings/provider configuration and navigation. When a release keystore is configured, the smoke test prefers that signed release APK; otherwise it installs the debug build. Emulator screenshots and a JSON report are uploaded as a CI artifact. Live provider availability is reported as such and is not converted into successful empty results. An optional manual API smoke check calls only the two official public APIs:

```bash
./scripts/live_source_smoke.sh
```

See [BLOCKERS.md](BLOCKERS.md) for verification that could not be run in the current environment, and [PRIVACY.md](PRIVACY.md) for network and on-device data details.

## Design and implementation references

The design follows the supplied wallpaper-app screen reference: deep teal image-led surfaces, cyan selected chips, rounded translucent controls and a compact Home/Search/Collections/Downloads bar. It combines Frutiger Aero's nature-and-technology palette with aspect-preserving provider images; there is no bundled wallpaper artwork. The ambient backdrop uses the current real provider thumbnail over a contrast-safe palette fallback, and the immersive detail screen fits the full wallpaper over a dimmed crop of that same image. **Haze 2.0.1 Glass** (Apache-2.0) samples those rendered pixels as a Haze source and applies size-aware blur, refraction, tint, specular light and a shaped rim; glass is not just a translucent gradient. The 2.0.1 GitHub release is non-prerelease, but Haze describes its 2.0 line as beta and Glass APIs remain experimental; this integration stays pinned and should be re-evaluated as upstream stabilizes. Haze's built-in Balanced renderer and platform fallbacks avoid enabling its experimental Android window-backdrop path. Older/simplified renderers keep a tinted, highlighted surface when advanced optics are unavailable. The Settings "Reduce transparency" preference increases material tint. As with any custom visual effect, performance and contrast still need measurement on representative Android devices.

The duplicate app-name bar was caused by declaring `Theme.Auroro.Splash` in the manifest without calling AndroidX `installSplashScreen()`: the splash theme's `postSplashScreenTheme` was therefore never installed. `MainActivity` now installs the splash screen before `super.onCreate`; the app uses the no-action-bar theme, draws edge-to-edge and applies route-aware safe-drawing insets while retaining Android's status/navigation controls. The CI smoke checks the home brand is shown once, not duplicated by a native title bar; see [BLOCKERS.md](BLOCKERS.md) for the latest run and the remaining physical-device limitations.

The user-provided reference repositories were reviewed for compatible, licensed guidance. [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) is Apache-2.0 and describes a Compose Multiplatform backdrop-glass library, but the existing Haze integration already provides source-backed material, so a competing runtime dependency was not added. [Android Compose Samples](https://github.com/android/compose-samples) is Apache-2.0 and remains a native Compose reference; no web view or browser UI was introduced. [pacifio/ui](https://github.com/pacifio/ui) is MIT-licensed but web/CSS-oriented, and [Stitch skills](https://github.com/google-labs-code/stitch-skills) require a Stitch MCP server that is not configured here; neither supplied app code or assets. The [frontend-design skill](https://github.com/anthropics/skills/tree/main/skills/frontend-design) (Apache-2.0) informed the subject-led palette, hierarchy and restraint; no source was copied. Unicons' GitHub licence metadata is not asserted (its README points to IconScout's Simple License), while [YouXianMing/Animations](https://github.com/YouXianMing/Animations) is an iOS/Swift collection without a declared licence; no icons, images or animation code were taken from either. No directory tools or external galleries are embedded in Auroro. No gallery or wallpaper application code is copied into the app.

References: [Frutiger Aero overview](https://frutiger-aero.org/frutiger-aero) · [Waterloo Computer Museum](https://uwaterloo.ca/computer-museum/exhibits/frutiger-aero-future-2000s) · [Haze 2.0.1 release and license](https://github.com/chrisbanes/haze/releases/tag/2.0.1) · [Haze Glass guide](https://github.com/chrisbanes/haze/blob/2.0.1/docs/effects/glass.md) · [Haze performance guide](https://github.com/chrisbanes/haze/blob/2.0.1/docs/performance.md) · [Android Compose insets](https://developer.android.com/develop/ui/compose/system/insets) · [Android Compose graphics](https://developer.android.com/develop/ui/compose/graphics/draw/overview) · [Apple materials (visual reference only)](https://developer.apple.com/design/human-interface-guidelines/materials) · [NoSignups.net](https://nosignups.net/) · [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance) · [Coil](https://github.com/coil-kt/coil) · [Muzei (Apache-2.0)](https://github.com/muzei/muzei) · [Aves (BSD-3-Clause)](https://github.com/deckerst/aves).

## Project layout

```text
app/src/main/java/com/auroro/wallpapers/
  app/                 Application container, navigation and state
  core/model/          Wallpaper metadata, geometry, filters and deduplication
  core/network/        HTTPS clients, URL policy, pacing and error mapping
  core/data/           Wallhaven/Openverse providers, paging and repositories
  core/database/       Room entities, DAOs and migrations
  core/data/download/  Streaming downloads, file storage, crop and WallpaperManager
  core/design/         Aero palette, glass surfaces and reusable components
  feature/             Home, search, filters, detail, collections, downloads and settings
app/src/test/           Model, provider, paging, migration, persistence and download tests
```

The app targets Android 36 (minimum Android 10 / API 29) and uses Material 3, Room, DataStore, WorkManager, Coil, Retrofit and OkHttp. Third-party software licences are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Artwork and font notices are in [ASSET_LICENSES.md](ASSET_LICENSES.md).
