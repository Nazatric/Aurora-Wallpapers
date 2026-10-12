# Changelog

Notable changes to Auroro Wallpapers are recorded here. Version 0.1 is prepared but is **not a published production release**; production distribution requires a configured signing key and verified release APK.

## [0.1] — pending verification and signed release

### Added and changed

- Rebuilt the native Compose presentation across Home, search and filters, image detail/crop, favorites, collections, downloads/history, source status, settings, navigation, and empty/error/loading states while retaining Room-backed user data and the existing download/apply separation.
- Replaced the active source set with Wallhaven, NASA APOD, and Art Institute of Chicago public-domain/Open Access results. Added provider allow-lists, metadata mapping, dimension-aware filters, bounded page caches, and conservative rate pacing.
- Kept NASA APOD records view-only unless an individual reuse permission is independently confirmed; credit/copyright fields are not treated as a licence.
- Disabled Reddit until official OAuth authorization is available. Removed Openverse catalogue requests and its global sensitivity setting/filter; retained legacy saved records and local files without new Openverse network use.
- Rebuilt For You as source-isolated Wallhaven and Art Institute query lanes plus a bounded, newest-first NASA APOD archive lane. Six catalogue recipes have AIC-specific query mappings, one museum query is attempted per mix, and partial provider failures stay isolated.
- Removed recipe/tag evidence as a hard result-rejection gate and removed the forced portrait/1080p For You request. Search-query results now require known source-reported dimensions with a 720 px minimum short edge; APOD dimensions remain unknown and its separate archive lane stays view-only.
- Removed hidden tag/category rejection from discovery quality checks; Wallhaven's source-side `purity=100` policy remains specific to Wallhaven. Reduced Glass/accessibility modes also skip unused Haze backdrop capture.
- Replaced the launcher and notification glyph with the verified Apache-2.0 Google Material Icons `landscape` asset, retaining an adaptive icon background, transparent foreground, and monochrome layer. Added source/licence attribution.
- Moved retired credential cleanup off the main thread and deferred WorkManager lookup until the download repository needs it.

### Preserved behavior

- Room-backed favorites, collections, history, saved downloads, and legacy metadata are retained.
- Set Wallpaper remains independent of Download: it uses a saved file or disposable cache copy and never creates a download row/file by itself.
- Provider errors, rate limits, unavailable sources, partial results, and empty results are distinct; provider metadata and image dimensions are not fabricated.

### Verification status

CI run [38221005867](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38221005867) passed unit tests, lint, debug/release assembly, and debug APK-signature verification. Its hosted-emulator smoke passed the Home/title and initial For You checks, then reached Search but failed to find results because `adb input text "ocean"` appeared in the UI as `oean`. The script now injects characters individually and verifies the exact query before submit; rerun pending. Hosted logs show Wallhaven query results, while an Art Institute sample had unknown dimensions and no size-usable rows. These are partial emulator-run observations, not a completed smoke journey or comprehensive provider/rights audit. Local Android tooling, manual screenshot review, physical-device performance measurements, and production signing remain outstanding. See [`BLOCKERS.md`](BLOCKERS.md). No production APK, FPS/frame-time claim, or published release is asserted by this changelog.
