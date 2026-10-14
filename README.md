# Auroro Wallpapers

**Work in progress.**

The application UI is being rebuilt from scratch, one slice at a time, on top of the validated Kotlin data/domain layer (providers, discovery and validation engines, Room/DataStore persistence, settings, download/apply pipeline).

**Slices 1–4 are in:** dark Material 3 shell with a floating pill navigation bar; Home feed (style chips, hero pager, stacked true-aspect cards); fullscreen detail with info sheet, rights-gated download and a pinch/drag crop editor whose framing is converted with the same `CropMath` the applier uses; search with debounced `FeedPager` queries, per-source status reporting and a staggered true-aspect grid; a cover-flow browser behind the magic button (full-bleed cropped cards, no letterboxing); Library (favorites grid + download queue with honest statuses, progress, retry/cancel/delete and a storage summary); and Settings with every row mapped to a real stored preference the data layer consumes — discovery cadence, hidden styles, source toggles, save location, notifications, cache limits — while theming/glass rows are shown disabled with a clear "later slice" note. Artwork loads preview/original resolutions (never upscaled thumbnails) and is drawn with `ContentScale.Fit` inside true-aspect containers everywhere orientation honesty matters. Motion follows Material 3 Expressive guidance.

- Repository conventions for contributors and AI agents: [`AGENTS.md`](AGENTS.md)
- Software licences: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
- Asset licences: [`ASSET_LICENSES.md`](ASSET_LICENSES.md)
- Network and data policy: [`PRIVACY.md`](PRIVACY.md)
