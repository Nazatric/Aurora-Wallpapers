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
- Separated the basic Follow system / Light / Dark control from Advanced theme. Follow system is now the default and resolves system light/dark state live; Material 3, Frutiger Aero, and Frutiger Zen each provide both mode palettes. Existing combined appearance preferences migrate safely, while removed legacy preset names no longer appear as design options.
- Rebuilt Settings around source-backed glass cards, compact grouped controls, and theme cards with paired Light/Dark swatches. Removed the duplicate Preferred styles / Exclude from Auto controls and their hidden discovery-filter state; old values are cleared during migration so For You is not silently narrowed.
- Renamed the old Frutiger Flower discovery query to ordinary botanical-art language, preserving botanical imagery as content rather than presenting it as a design theme.
- Added Light Aero and Dark Zen Compose Settings previews, mode/theme persistence and palette tests, and a Figma/Penpot-ready nine-screen handoff in [`docs/AURORO_DESIGN_SYSTEM.md`](docs/AURORO_DESIGN_SYSTEM.md). These new changes are not yet verified by CI or on-device visual review.

### Preserved behavior

- Room-backed favorites, collections, history, saved downloads, and legacy metadata are retained.
- Set Wallpaper remains independent of Download: it uses a saved file or disposable cache copy and never creates a download row/file by itself.
- Provider errors, rate limits, unavailable sources, partial results, and empty results are distinct; provider metadata and image dimensions are not fabricated.

### Verification status

CI run [38224038282](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38224038282) passed unit tests, lint, debug/release assembly, and debug APK-signature verification; its hosted-emulator smoke stopped after 118 checks on a stale Home-navigation predicate. The predicate was corrected in commit `897498e`. For that commit, CI run [38262565617](https://github.com/Nazatric/Aurora-Wallpapers/actions/runs/38262565617) passed its build job; the hosted-emulator smoke was still running at the last poll. The theme/settings redesign and its updated smoke journey in this working tree are newer and have not been built or tested by that run. Earlier smoke diagnostics showed 30 For You images from two catalogues at one UI state; item-level rights, manual screenshot review, physical-device performance, and production signing remain unverified. See [`BLOCKERS.md`](BLOCKERS.md). No production APK, FPS/frame-time claim, or published release is asserted by this changelog.
