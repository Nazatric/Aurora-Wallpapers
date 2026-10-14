# Auroro Wallpapers

**Work in progress.**

The application UI is being rebuilt from scratch, one slice at a time, on top of the validated Kotlin data/domain layer (providers, discovery and validation engines, Room/DataStore persistence, settings, download/apply pipeline).

**Slice 2 — Home feed + fullscreen detail — is in:** dark Material 3 shell with a floating pill navigation bar, style chips, a hero pager and a stacked card feed driven by the real discovery engine (continuation batches on scroll, honest loading/empty/error/end states, favorites). Cards keep the provider's real orientation — artwork is drawn with `ContentScale.Fit` inside a container that follows the reported dimensions, so nothing is cropped or distorted. Tapping any card opens the fullscreen detail preview: whole-image view, back/favorite controls, an info sheet (source, dimensions, creator, rights, licence, tags, open-original-page handoff), rights-gated Download, and Set Wallpaper with a home/lock/both target sheet. Search, Library and Settings are the next slices; their tabs currently show placeholders.

- Repository conventions for contributors and AI agents: [`AGENTS.md`](AGENTS.md)
- Software licences: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
- Asset licences: [`ASSET_LICENSES.md`](ASSET_LICENSES.md)
- Network and data policy: [`PRIVACY.md`](PRIVACY.md)
