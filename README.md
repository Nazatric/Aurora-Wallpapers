# Auroro Wallpapers

**Work in progress.**

The application UI is being rebuilt from scratch, one slice at a time, on top of the validated Kotlin data/domain layer (providers, discovery and validation engines, Room/DataStore persistence, settings, download/apply pipeline).

**Slices 1–3 are in:** dark Material 3 shell with a floating pill navigation bar, style chips, a hero pager and a stacked card feed driven by the real discovery engine; a fullscreen detail preview (info sheet with source/rights/licence metadata, rights-gated download, set-wallpaper); a search page with debounced queries, per-source status reporting and a two-column staggered grid that keeps every wallpaper's real aspect ratio; a cover-flow browser behind the pill bar's magic button; and a pinch/drag crop editor whose framing is converted with the same `CropMath` the applier uses, so what you see is what gets set. Motion follows Material 3 Expressive guidance — bouncy springs on chips/hearts/dots, emphasized easings on screen transitions — and artwork is always drawn with `ContentScale.Fit` inside true-aspect containers, never cropped to fake an orientation. Library and Settings are the next slices; their tabs currently show placeholders.

- Repository conventions for contributors and AI agents: [`AGENTS.md`](AGENTS.md)
- Software licences: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
- Asset licences: [`ASSET_LICENSES.md`](ASSET_LICENSES.md)
- Network and data policy: [`PRIVACY.md`](PRIVACY.md)
