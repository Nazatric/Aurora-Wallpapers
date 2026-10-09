# Auroro Wallpapers

**A native Android wallpaper finder with a little more sky, sea and glass.** Built in Kotlin and Jetpack Compose with a restrained Frutiger Aero visual language: deep ocean colour, environmental imagery from real wallpaper sources, polished glass controls and soft aqua/emerald highlights.

> **Auroro Wallpapers is free and ad-free.** No account, advertising SDK, subscription, in-app purchase, analytics or bundled wallpaper catalogue. A result is shown only when a permitted source actually returns it.

## What works today

- **Home and discovery:** For You, Popular, Latest and Random; the For You query is deterministic and based on locally saved/viewed wallpaper tags, with a useful latest feed when there is no history. It is not an AI recommendation service.
- **Live search:** actual Wallhaven results, and Wallpaper Abyss results when the user has configured their own Alpha Coders API key. Sources page independently and report errors or unavailable access without dropping other providers' results.
- **Filters:** real image dimensions drive aspect ratio, orientation and resolution checks. The documented ratio tolerance defaults to 3% and can be changed in Settings. 4K/8K checks use short and long edges, so portrait images are classified correctly. Wallhaven-only categories, palette and sorting are sent using documented API parameters.
- **Wallpaper details:** provider page, source, dimensions, uploader where supplied, file information, tags and source-supported related results; share and open-source actions work.
- **Favorites, local collections and history:** persistent Room metadata; favoriting never downloads the original image.
- **Downloads:** cancellable, streamed original-quality transfers; real progress, image validation, retry/failure states and MediaStore or private-app saves. “Offline” lists persisted files, not Coil/HTTP cache entries.
- **Set wallpaper:** crop preview (pinch, pan and zoom), then Android's `WallpaperManager` applies the chosen crop to Home, Lock or Both. The original is downloaded first when needed.
- **Settings:** system/dark/light appearance, AMOLED option, accents, transparency/contrast, aspect tolerance, bounded cache, download quality/destination, notification preferences, source availability and encrypted Wallpaper Abyss key storage.
- **Navigation:** Home, Search, Collections and Offline bottom navigation; drawer entries for Favorites, Categories, Sources and Settings.

## Wallpaper source status — read before enabling a source

### Wallhaven — real in-app API integration

Auroro calls the **official Wallhaven API v1** over HTTPS (`GET https://wallhaven.cc/api/v1/search` and `GET /w/{id}`). Public SFW search does not require a key. Every API search explicitly fixes `purity=100`; Auroro cannot return sketchy or NSFW results. Search, pagination, relevant/general/anime/people categories, SFW defaults, newest/popularity/random sorting, server-side supported ratio/resolution/color filters, metadata and original/thumbnail URLs are implemented. Geometry is also checked locally against the returned pixel dimensions. The provider documents a limit of 45 API calls/minute; Auroro paces its own calls below that limit and does not retry HTTP 401/403/429. Images remain the property of their original owners; Auroro links back to the Wallhaven page.

Official documentation: <https://wallhaven.cc/help/api>

### Wallpaper Abyss — official API integration, key required

Wallpaper Abyss (Alpha Coders) documents an API at `https://api.alphacoders.com/3.0`. Auroro implements its documented `search` and `newest` calls, independent desktop/phone pagination, metadata, source links and original/thumbnail URLs. The API requires an **Alpha Coders API subscription and an individually issued key**. No key is embedded or included in this repository. Add your own key at **Settings → Wallpaper sources**; Auroro encrypts it on-device using AndroidKeyStore and sends it only as the API's `auth` parameter to `api.alphacoders.com`. Without a configured key, the source is honestly shown as unavailable and the app offers an official-site browser handoff. The API has source-specific capabilities; unsupported sort/category/color combinations are reported, not fabricated.

Official API documentation and subscription details: <https://api.alphacoders.com/api/instructions>

### Unsplash — **browser handoff only; no API calls**

Unsplash's current published API guidelines prohibit replicating the core experience of a wallpaper app, and explicitly describe a wallpaper app that returns Unsplash images for downloading as disallowed. The same guidelines require a confidential API credential and download tracking. Auroro has **no Unsplash API client, scraper, proxy or hidden endpoint**. Its source entry opens Unsplash in the user's browser with the requested referral parameters. It does not show Unsplash images as in-app results or present this handoff as an API integration. A future API integration requires explicit written authorization from Unsplash for this specific use case.

Official guidelines: <https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines> and <https://help.unsplash.com/en/articles/2511257-guideline-replicating-unsplash>

See [`BLOCKERS.md`](BLOCKERS.md) for the precise outstanding access and test limitations.

## Tech and structure

- Kotlin, **Jetpack Compose**, Material 3 with a custom Aero theme, Navigation Compose and ViewModel-owned observable state.
- Gradle Kotlin DSL, version catalogue, Kotlin serialization, Retrofit 3 / OkHttp 5, Coroutines/Flow, Coil 3.
- Room (favorites, collections, viewing history, wallpaper/download metadata); DataStore (settings); AndroidKeyStore AES-GCM for the user's Alpha Coders key; WorkManager (cancellable foreground downloads); MediaStore and WallpaperManager.
- The common provider contract retains each provider's ID, page/original/thumbnail URLs, dimensions, metadata and attribution. Pagination cursors are independent. Results deduplicate only by stable identifiers and canonical URLs—never by visual similarity. Popularity/relevance scores are not compared across providers.
- `minSdk 29` (Android 10); `targetSdk 36`; `compileSdk 37`; JDK 17.

Project structure:

```text
app/src/main/java/com/auroro/wallpapers/
  app/                 Application container, activity, navigation and state
  core/model/          Wallpaper, aspect/resolution rules, local filters and deduplication
  core/network/        HTTPS clients, host allow-list, retries and error mapping
  core/network/*/      Official Wallhaven and Alpha Coders API DTOs
  core/data/           Providers, independent-source paging, repositories and preferences
  core/database/       Room entities, DAOs and migration entry point
  core/data/download/  Streaming downloads, MediaStore, crop and WallpaperManager
  core/design/         Aero palette, glass materials, type, motion and reusable components
  feature/             Home, search, filters, details, collections, offline and settings screens
app/src/test/           Model, provider/pager, Room repository, configuration and download tests
```

## Build, install and tests

Prerequisites: JDK 17, Android SDK Platform 37, Android build-tools 37.0.0, and network access to Google's/Maven dependency repositories.

```bash
# Verify tests, Android lint, and both APK variants
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease

# Install the debug APK on a connected Android device (USB debugging enabled)
./gradlew installDebug

# Or install the generated file with adb
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is **debug-signed**, not release-signed. A release build is signed only when you configure a private release keystore. Never commit signing files or passwords. For the GitHub Actions release-signing job, configure these repository secrets: `AURORO_KEYSTORE_BASE64`, `AURORO_KEYSTORE_PASSWORD`, `AURORO_KEY_ALIAS`, and `AURORO_KEY_PASSWORD`. If those four secrets are absent, the workflow reports that it did not sign a release APK and publishes the installable debug APK instead.

The CI workflow runs `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleRelease`, and uploads APKs plus test/lint reports as artifacts. Open **Actions → Android CI → the successful run → Artifacts → `auroro-wallpapers-apk`** to download the universal APK. A `v*` tag build also attaches generated APKs to its GitHub Release when signing is configured.

Tests use deterministic local fixtures and a mock HTTP server; they do not call live wallpaper services or require a provider key. On a networked machine, optional live source checks are separate from unit tests: `./scripts/live_source_smoke.sh` (set `ALPHA_CODERS_API_KEY` only if you have your own active subscription key). Unsplash is deliberately skipped by policy. Upstream rate limits and keys are never unit-test prerequisites.

## Configuration and privacy

- **Wallpaper Abyss key:** enter the key you obtained from Alpha Coders in Settings. It is encrypted at rest with a device-bound AndroidKeyStore key. It is never compiled into `BuildConfig`, tracked by Git or backed up by Auroro.
- **Wallhaven:** no key needed for the public SFW API. The application never sends a sketchy/NSFW request.
- **Unsplash:** no API key is accepted or sent; this source opens the official website externally.
- Download location defaults to `Pictures/Auroro Wallpapers` through MediaStore. “App storage only” is available when the user prefers private files. No broad storage permission is requested.
- Favorites, collections, history and settings are local. Saved images use the chosen Android storage location. There is no Auroro account, backend, telemetry or analytics.

See [`PRIVACY.md`](PRIVACY.md), [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md), [`ASSET_LICENSES.md`](ASSET_LICENSES.md) and [`LICENSE`](LICENSE).

## Design research and visual verification

The design draws on the optimistic nature-plus-technology imagery and translucent Aero Glass of 2004–2013 interfaces, while using Liquid Glass guidance only as a reference for a separate, restrained controls/navigation layer. It uses original vector sphere artwork, Open Sans (not a copied Frutiger/Segoe font), luminous edge highlights and opaque enough content surfaces for contrast. Expensive blur and continuously running effects are intentionally avoided.

References read: <https://frutiger-aero.org/frutiger-aero>, <https://uwaterloo.ca/computer-museum/exhibits/frutiger-aero-future-2000s>, <https://developer.apple.com/design/human-interface-guidelines/materials>, <https://developer.apple.com/documentation/technologyoverviews/liquid-glass> and <https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass>.

The current coding environment has no Android SDK, emulator or supplied reference image file, so it cannot capture a running-device screenshot or compare pixels against the concept. The Android CI build and automated tests are the available verification path; no screenshot-based visual test or physical-device profiling is claimed.
