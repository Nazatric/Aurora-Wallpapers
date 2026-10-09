# Auroro Wallpapers

A native Android wallpaper browser built with Kotlin and Jetpack Compose. The interface uses deep-ocean glass surfaces, restrained aqua/emerald accents and images loaded from real provider catalogues. Auroro has no bundled wallpaper gallery, account, advertising, analytics or in-app purchase SDK.

## Integrated sources

Only **Wallhaven** and **Openverse** are searched in the app.

### Wallhaven

Auroro uses the public Wallhaven API v1 over HTTPS; a key is not required for public search. Requests always specify SFW purity. Search results include provider-reported dimensions, tags, colours, uploader and source-page details where available. Relevance, newest, popular and random ordering use Wallhaven's own API. Categories, its colour palette, supported ratio tokens and minimum-size hints are provider filters; exact ratio and edge thresholds are checked locally against returned pixel dimensions. Results link to their Wallhaven page. Its API does not report an image licence; images remain the responsibility of their original rights holders.

Official API reference: <https://wallhaven.cc/help/api>

### Openverse

Auroro searches the documented Images API at `https://api.openverse.org/v1/images/`. The current API reference says anonymous access is supported and anonymous requests are sufficient for most users; registering for OAuth credentials gives higher limits. Auroro therefore sends **no API key, client secret or bearer token**. It persists a local 20-request/minute burst ceiling based on the current public response header and observes Openverse's `anon_burst` and `anon_sustained` search quotas; provider limits may change. A rate-limit response is shown rather than repeatedly retried. Search pages are fetched incrementally and capped to avoid treating the API as a bulk-download service.

Search uses documented query, tag, category, licence, format-extension, aspect-bucket, size-band and dead-link filters where applicable. Openverse's general `q` search and tag-field search are alternatives: its API ignores tag-field search when `q` is present, so Auroro prevents that combination. Openverse remains relevance-ranked. Its aspect bucket is orientation-only (`tall`, `wide`, or exact `square`), not an exact ratio; Auroro sends it only when the full local tolerance interval fits that orientation (or the user explicitly selects one). Its size filter uses coarse pixel-area bands. Both are safe prefilters, and actual provider-reported dimensions remain authoritative on-device. Creator, attribution text, originating page, provider, tags and licence details are retained and shown when supplied. Openverse indexes third-party works and does not verify every licence claim; check the original page and licence terms before reuse. Direct downloads are enabled only for recognized licences and supported image formats. No-derivatives licences can be saved as originals but are not offered for wallpaper-setting/cropping; deprecated sampling licences are browse-only in Auroro.

Official API reference: <https://api.openverse.org/v1/>; API documentation: <https://docs.openverse.org/>; search behavior: <https://docs.openverse.org/api/reference/search_algorithm.html>

## Search, filters and local data

- Home topics run real searches. For You uses tags from locally saved or viewed wallpapers; it falls back to the latest feed when there are no such tags. It is not an AI recommendation service.
- Search combines enabled providers without inventing a cross-provider popularity score. Each provider keeps its own pagination and ordering; failures are reported alongside results from another source.
- Aspect ratio is `width / height` from provider dimensions. A match uses a relative tolerance (3% by default; adjustable in Settings). Presets include 9:16, 16:9, 4:3, 1:1, 21:9 and 9:19.5, plus custom ratios. Minimum-size filters use the short and long edges so portrait and landscape images are treated consistently. Openverse's coarse server filters never replace local checks. Some provider/filter combinations are unavailable and are not presented as supported.
- Wallpaper cards preserve each image's own aspect ratio in a staggered grid. They use provider-supplied previews when safe; detail views and downloads retain the media URL, and the full composition is fit rather than forced into a crop.
- Favorites, collections, history and wallpaper metadata are stored locally with Room. Collections support creation, rename, deletion and membership changes. Favoriting does not download an image. The database migration keeps existing records and download URIs; retired-provider records cannot be downloaded again, but an existing local file can still be applied.
- Downloads stream and validate the original JPEG, PNG or WebP file, expose progress and cancellation, check storage capacity (including periodic checks when the server omits its content length), and save through MediaStore or private app storage. Offline items refer to saved files, not the disposable image cache. Wallpaper setting uses Android's `WallpaperManager`, with a crop preview and Home, Lock or Both targets where supported.
- Settings include appearance, accent, transparency, aspect tolerance, cache size, download location, notifications, source availability and optional set-after-download behavior. Favorites are never automatically downloaded.

## Build and test

Requires JDK 17, Android SDK Platform 37 and Build Tools 37.0.0.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Install the debug build on a connected device with `./gradlew installDebug`, or install `app/build/outputs/apk/debug/app-debug.apk` with `adb`. A debug APK is debug-signed. Release signing requires a private keystore supplied locally or through the documented GitHub Actions secrets; never commit signing material.

Unit tests use local fixtures and a mock HTTP server; they do not require provider credentials or call live catalogues. An optional manual smoke check calls only the two official public APIs:

```bash
./scripts/live_source_smoke.sh
```

See [BLOCKERS.md](BLOCKERS.md) for verification that could not be run in the current environment, and [PRIVACY.md](PRIVACY.md) for network and on-device data details.

## Design and implementation references

The visual research combines Frutiger Aero's nature-and-technology imagery with restrained translucent surfaces; Apple's Liquid Glass material guidance is used as a visual reference, not as an Android dependency. The Compose UI uses simple layered surfaces instead of continuous blur, and lazy image grids use stable keys. Image loading uses Coil's maintained Compose library. No gallery or wallpaper application code is copied into Auroro.

References: [Frutiger Aero overview](https://frutiger-aero.org/frutiger-aero) · [Waterloo Computer Museum](https://uwaterloo.ca/computer-museum/exhibits/frutiger-aero-future-2000s) · [Apple materials](https://developer.apple.com/design/human-interface-guidelines/materials) · [Liquid Glass overview](https://developer.apple.com/documentation/technologyoverviews/liquid-glass) · [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance) · [Coil](https://github.com/coil-kt/coil) · [Muzei (Apache-2.0)](https://github.com/muzei/muzei) · [Aves (BSD-3-Clause)](https://github.com/deckerst/aves).

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
  feature/             Home, search, filters, detail, collections, offline and settings
app/src/test/           Model, provider, paging, migration, persistence and download tests
```

The app targets Android 36 (minimum Android 10 / API 29) and uses Material 3, Room, DataStore, WorkManager, Coil, Retrofit and OkHttp. Third-party software licences are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Artwork and font notices are in [ASSET_LICENSES.md](ASSET_LICENSES.md).
