# Auroro Wallpapers

**Work in progress.**

The application UI is being rebuilt from scratch, one slice at a time, on top of the validated Kotlin data/domain layer (providers, discovery and validation engines, Room/DataStore persistence, settings, download/apply pipeline).

**Slices 1–5 are in:** dark-first Material 3 shell with a floating pill navigation bar whose selection indicator is a single sliding spring pill; Home feed with mode chips (For you / Trending / Newest / Random, each mapped to a real catalogue sort) plus style chips, hero pager and stacked cards that fill their true-aspect containers with `ContentScale.Crop` (no letterbox bars even when a CDN frame disagrees with metadata) and decode through a 1440 px cap for fast scrolling; fullscreen detail with Favorite / Share / Download / Info circles, a big Set-wallpaper button, HDR window colour mode on HDR-capable displays, and honest whole-image preview; a crop editor with rule-of-thirds grid, 90° rotation (the applier maps the rotated rect back onto original pixels and rotates only the cropped region), and Home/Lock/Both icon cards; search with debounced `FeedPager` queries and a staggered grid; a cover-flow showcase with an icon-only All/Portrait/Landscape orientation filter; Library with Favorites, Downloads (All/Completed/Failed filter chips, live progress, retry/cancel/delete) and a real viewing History; and Settings that actually re-theme the app live — theme mode (light/dark/system), design presets, accent hue, AMOLED, contrast boost and font scale all route through a composition-local palette, while the Liquid-Glass renderer and typeface swap remain honestly marked as a later slice. Wallhaven cards/carousels load the large thumb instead of the 4K original, APOD previews cap at 1440 px, detail/apply always use the original. Motion follows Material 3 Expressive guidance (bouncy springs for small elements, emphasized easings for surfaces).

- Repository conventions for contributors and AI agents: [`AGENTS.md`](AGENTS.md)
- Software licences: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
- Asset licences: [`ASSET_LICENSES.md`](ASSET_LICENSES.md)
- Network and data policy: [`PRIVACY.md`](PRIVACY.md)
