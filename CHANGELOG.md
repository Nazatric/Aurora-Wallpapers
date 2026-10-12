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
- Separated the basic Follow system / Light / Dark control from Advanced theme. Follow system is the default; fresh installs retain Frutiger Aero as the selected design language to match the supplied glass-forward direction. The system mode resolves light/dark live, and Material 3, Frutiger Aero, and Frutiger Zen each provide both mode palettes. Existing combined appearance preferences migrate safely, while removed legacy preset names no longer appear as design options.
- Rebuilt Settings around source-backed glass cards, compact grouped controls, and theme cards with paired Light/Dark swatches. Retained For You topic preferences under ordinary labels; they guide the curated recipe mix and do not add a global metadata rejection rule. Existing non-botanical choices persist; the retired botanical-style ID reads as ordinary Botanical art and is normalized on the next settings save.
- Renamed the old Frutiger Flower discovery query to ordinary botanical-art language, preserving botanical imagery as content rather than presenting it as a design theme.
- Added Light Aero and Dark Zen Compose Settings previews, mode/theme persistence and palette tests, and a Figma/Penpot-ready nine-screen handoff in [`docs/AURORO_DESIGN_SYSTEM.md`](docs/AURORO_DESIGN_SYSTEM.md). These new changes are not yet verified by CI or on-device visual review.

### Preserved behavior

- Room-backed favorites, collections, history, saved downloads, and legacy metadata are retained.
- Set Wallpaper remains independent of Download: it uses a saved file or disposable cache copy and never creates a download row/file by itself.
- Provider errors, rate limits, unavailable sources, partial results, and empty results are distinct; provider metadata and image dimensions are not fabricated.

### Verification status

CI run [38224038282](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38224038282) passed unit tests, lint, debug/release assembly, and debug APK-signature verification; its hosted-emulator smoke stopped after 118 checks on a stale Home-navigation predicate. The predicate was corrected in commit `897498e`. For that commit, CI run [38262565617](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38262565617) passed its build job, but the emulator smoke was cancelled by the next push. CI run [38264239992](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38264239992) failed Kotlin compilation on an unnecessary `mergeDescendants` import; `c8a41da` removed it. Run [38265637835](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38265637835) passed its unit-test/lint/debug-and-release-build/debug-signature job, but emulator smoke stopped after 137 checks because it could not reveal the Material 3 theme title; commit `d3c8d94` narrows theme cards and adds explicit selection checks. Run [38268175235](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38268175235) failed a test that expected asynchronous startup to rewrite a legacy style ID; emulator/signature steps were skipped. The current correction tests read-time translation while preserving stored data and normalizes on the next settings save. A fresh run is pending. Earlier smoke diagnostics showed 30 For You images from two catalogues at one UI state; item-level rights, manual screenshot review, physical-device performance, and production signing remain unverified. See [`BLOCKERS.md`](BLOCKERS.md). No production APK, FPS/frame-time claim, or published release is asserted by this changelog.
