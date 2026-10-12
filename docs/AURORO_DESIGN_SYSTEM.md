# Auroro visual system — Figma / Penpot handoff

**Status:** Figma and Penpot design-file connectors are not available in this session, so this document is the editable handoff specification. The Compose app remains the implementation source of truth. It is based on the supplied nine-screen reference: blue/cyan glass chrome, image-first browsing, explicit search/filter/detail/crop flows, collections, downloads, settings, and a theme preview.

## 1. Product and visual principles

1. **The wallpaper is the product.** Artwork stays untinted and preserves source proportions. Chrome is quiet around image grids; provider, rights, dimensions, and actions appear on the detail page.
2. **Glass must sample its backdrop.** Use the existing Haze source/effect path for foreground chrome where it improves hierarchy. A translucent fill or gradient by itself is not the glass treatment. Keep a high-contrast opaque fallback for accessibility, reduced rendering, and unsupported renderers.
3. **Three coherent design languages, two modes each.** Basic mode is `Follow system` (default), `Light`, or `Dark`. Independently choose `Material 3`, `Frutiger Aero`, or `Frutiger Zen`; fresh installs select Frutiger Aero to follow the supplied glass-forward direction. All three design languages use the selected mode; no design language silently overrides it.
4. **Source truth over decoration.** Do not display fabricated result counts, image dimensions, attribution, license, or provider availability. Unknown metadata is labelled unknown or omitted. One failed catalogue must not blank working catalogues.
5. **Access is part of the design.** Touch targets are at least 48 dp where practical, selected states are not colour-only, text scales with Android accessibility settings, focus is visible, and images have useful semantics.

## 2. Frame, grid, and spacing

- Primary target frame: **360 × 800 dp**, portrait; verify at 320 dp width and large font scale before release. Keep landscape/tablet responsive rather than stretching phone cards.
- Respect status/navigation bars. Screen content uses the app's current safe-drawing insets; immersive wallpaper/crop views retain horizontal safe areas and own their overlays.
- Base spacing: 4 dp. Common steps: 4, 8, 12, 16, 20, 24, 32 dp. Screen gutter: 16–18 dp. Inter-card gap: 8–12 dp.
- Typography hierarchy: display 28–32 sp; screen title 24–28 sp; section title 18–20 sp; body 14–16 sp; supporting metadata 12–14 sp. Do not render important status below 12 sp.
- Shape tokens: M3 uses 4/8/12/16/28 dp corners; Aero 8/12/16/22/30 dp; Zen 6/10/14/20/26 dp. Keep artwork corners independent of theme so a theme change cannot crop the art differently.
- Wallpaper grids use responsive staggered columns (minimum tile width 148 dp), 8–10 dp gutters, and source-reported image proportions. Never force portrait or upscale a thumbnail into an apparent original.
- Bottom navigation is a fixed, safe-area-aware surface with four destinations: Home, Search, Collections, Downloads. Settings and source management remain reachable from the drawer and contextual routes.

## 3. Color and material tokens

The implementation stores semantic tokens in `core/design/Color.kt`. Figma/Penpot variables should mirror the following names rather than copy hex values into individual components:

| Token | Use |
|---|---|
| `backdrop.top / mid / bottom` | Screen atmosphere behind cards; palette-specific and non-interactive |
| `glass.tint` | Stable Haze tint, distinct from wallpaper art |
| `glass.rim.light / dark` | Directional edge separation; never the only contrast cue |
| `glass.highlight` | Restrained top-edge specular highlight |
| `text.primary / secondary / tertiary` | Text hierarchy; confirm contrast in both modes |
| `accent / accent.light / accent.deep / onAccent` | Selected controls, links, progress and focus |
| `surface.solid` | Opaque surface for reduced transparency and information-dense rows |
| `status.error / warning / success` | Status meaning with a text/icon label, not hue alone |
| `scrim` | Modal/drawer focus isolation |

### Design-theme directions

- **Material 3:** neutral cool gray/lavender surface family, conventional elevation and control hierarchy; restrained indigo/blue seed, teal/green secondary. Light and dark use M3 surface contrast rather than a high-glow background.
- **Frutiger Aero:** clear sky/cyan, fresh green, water-like glow and source-aware Haze glass. Light mode stays near-white; dark mode uses deep ocean shades. Keep glass behind controls, never over the artwork.
- **Frutiger Zen:** sage, quiet sea-glass, warm stone and calmer contrast. Less saturated glow, softer density, and the same legible source-aware surface behavior.
- **Dark mode:** dark text is never used on dark glass. AMOLED black is an optional dark-mode surface preference, not another theme.
- The current accent selector is a three-choice accent seed (Aqua, Emerald, Sky blue); it may adjust accent tokens but does not change the selected design language or mode.

## 4. Components and variants

Create these as Figma/Penpot components with the indicated properties. Compose counterparts should reuse tokens and interactions rather than duplicate one-off styling.

- `TopBar`: title, optional subtitle, leading menu/back, optional trailing actions; 48 dp icon targets; title truncates before actions.
- `BottomNavItem`: selected/unselected, optional badge only for a real count, icon + label, 48 dp minimum.
- `GlassPanel`: regular/clear, selected, full/balanced/reduced, reduced-transparency fallback, increased-contrast border. Live variants use Haze source sampling; fallback is opaque enough for text.
- `WallpaperTile`: loading/loaded/error; source-neutral; image uses fit and true aspect ratio; title/count not overlaid on art; click opens detail.
- `FilterChip`: selected/unselected/disabled; 44–48 dp high; accessible label includes active state.
- `SourceStatusRow`: enabled/loading/success/partial error/disabled-by-user; names the source and its real status.
- `PrimaryAction`: enabled/loading/disabled; apply and download are separate actions.
- `EmptyState`: concise title, explanatory message, one relevant action; separate no-results, no-source, offline, and error variants.
- `LoadingState`: skeleton cards preserve layout; progress text is announced once, not for every tile.
- `ErrorBanner`: provider name, truthful cause and retry action where appropriate; dismissible only if the message remains available elsewhere.
- `ThemeCard`: three design-language variants, each previewing both Light and Dark palettes; selected state has border + check + semantics, not tint alone.

## 5. Screen specifications

### 5.1 Home — For You

- Header: `Auroro`, short subtitle, menu, search, and refresh. No invented image count or decorative fake category count.
- Featured hero (only when a suitable real result exists): one wide image with source proportions and a direct detail action. If no suitable featured item exists, collapse the hero instead of showing a placeholder advertisement.
- Main feed: image-first masonry grid. The source's returned rows, after local validation/deduplication, define the visible count. Never invent filled sections; show partial source status below the heading or in a compact banner.
- Loading: 4–6 layout-matched skeletons and one progress indicator. Empty/error: name which catalogues failed and preserve any successful catalogue results.
- Pull/refresh respects persisted per-source `retry_after` budget. Tapping a tile opens detail; swipe/back restores grid position.

### 5.2 Search, filters, and results

- Search input at top, 56 dp minimum, clear action and keyboard submit. Recent terms are local-only, capped, removable, and do not submit until chosen.
- Source tabs: All plus only integrated catalogues; unsupported/disabled sources show their actual state rather than a fake selectable result.
- Filters open a dedicated sheet/screen: source, source-specific category/colour/purity behavior, orientation, exact ratio, minimum dimensions when supported, and sort. Unknown dimensions never satisfy a positive minimum by assumption.
- Filter controls describe source ownership; Wallhaven category/purity options must not be presented as global filters for NASA or the Art Institute.
- Debounce may start a new request only after a normalized query change. Cancel the previous job immediately on query/filter changes. Preserve existing results while loading the next request and label them as previous only if the UI could confuse them with current results.
- Results count is the count of currently displayable, deduplicated rows, not the provider's raw JSON row count. End-of-results and “load more” are explicit.

### 5.3 Explore / discovery feed

- Reuse the same image tiles, but label the surface `Explore` or `For You` consistently; do not expose internal query recipe IDs as categories.
- Feed mixes are provider-backed; query text and metadata evidence may influence ranking, not silently reject valid source rows. Keep source lanes independent and disclose partial errors.
- Empty states distinguish all-source failure, rate limit/cooldown, no dimensions passing a declared local filter, and genuinely empty responses.
- A refresh control observes the saved cooldown and explains the remaining wait rather than issuing repeated requests.

### 5.4 Wallpaper detail

- Large, crisp image with a fit-to-frame preview, actual source dimensions only when reported, and source name.
- Compact information card: title, creator, source page, date/category, license/rights and attribution exactly as supplied or verified; unknown fields remain absent/unknown.
- Actions: `Set wallpaper`, `Download`, `Favourite`, `Add to collection`, `Share/open source`, and `Crop` as capabilities permit. Set-only uses a temporary cached image and never creates a persistent download record.
- Provider-specific restrictions disable only the unavailable action and state why. Do not imply an APOD item has blanket rights; APOD rights are item-specific.

### 5.5 Crop / set flow

- Full-screen canvas showing the actual decoded image, draggable and pinch-zoomable; crop viewport follows the device screen ratio and safe-area target.
- A crop preview never substitutes a thumbnail for the original. Reset and Apply remain distinct, with a target choice for Home, Lock, or both if the platform permits.
- Applying reports Android permission/system confirmation, success, cancellation, or failure accurately. A failure retains the crop and returns to the detail action without creating a download.
- System bars and actions remain reachable at large font scale. Test asymmetric pan bounds, portrait/landscape ratios, and crop coordinates against original pixels.

### 5.6 Collections and Favourites

- Collections index uses user-created names, real saved counts, clear create/rename/delete actions, and an empty state that starts with `Create collection`.
- Favourites use a dedicated saved-only grid, not a provider refetch. Removal asks only when destructive/bulk; an empty collection links to Home/Search.
- A wallpaper may be in a collection without being downloaded. Local metadata, DB identifiers, favourites and collection membership survive process death and update transactionally.

### 5.7 Downloads

- Show real download task states: queued, active/progress, complete, failed, missing local file. Include file size only when measured and original/URI status only when verified.
- Downloaded files use a local thumbnail, true image ratio and explicit actions (open, set, delete). Deleting the file is not the same as removing a favourite or collection membership.
- A `Set wallpaper` shortcut from a download applies the stored local file. No “download” action may be implied by a successful set-only operation.

### 5.8 Settings and source management

- Settings section order: Display mode → Advanced theme → Typography → Effects & accessibility → For You → Image matching → Downloads → Cache → Sources & information.
- Display mode is the three-way segmented choice `Follow system` / `Light` / `Dark`; the initial default is Follow system. The selected mode is independent of Advanced theme.
- Advanced themes show Frutiger Aero first on a fresh install, followed by Material 3 and Frutiger Zen; every card previews the actual Light and Dark palette tokens. The retired Frutiger Flower name appears neither as a theme nor a discovery topic; botanical art remains an ordinary topic.
- For You retains hourly rotation, recently-shown avoidance, repeat window, preferred/skipped topics, and reset history. Topic choices bias existing curated recipes; they never gate returned items by tags, ratings, or global content classifications. Keep them visibly optional and separate from provider enablement.
- Source management lists each integrated provider, its terms/link, user enablement, data actually returned, and current success/failure/cooldown. No secret entry for an unapproved provider; Reddit remains off until authorized API access and rights are verified.
- Cache controls state that preview cache is disposable and do not threaten saved files. Settings remain on-device and survive app restarts.

### 5.9 Advanced theme preview

- Provide one card per design system and a Light/Dark preview for each; changing preview is non-destructive until the user selects a theme.
- Include sample top bar, glass card over a patterned backdrop, selected chip, artwork tile, text hierarchy, and disabled/selected controls. The artwork sample is licensed/owned or clearly an abstract design-system swatch, never a misleading provider result.
- Preview the full nine-screen flow after the cards are implemented: Home, Search, Explore, detail, crop, Collections/Favourites, Downloads, Settings, and theme preview.

## 6. Motion, performance, and validation

- Motion is brief and purposeful (roughly 120–220 ms), uses one shared easing curve, and is disabled under Reduce motion. Do not animate the entire wallpaper grid on every state update.
- Coil image requests share URL-based memory/disk cache keys; request only thumbnails in grids, defer original downloads to the explicit Download/Set path, cancel obsolete requests, and keep saved files outside the disposable cache.
- Haze effects must be benchmarked on an identified physical device with OS/build, display mode, refresh rate, image load state, and settings recorded. Report frame timing and dropped frames; do not claim 120 Hz performance from simulator output.
- Visual QA: 320/360/411 dp widths, 1.0×/1.3× font scale, light/dark/system transition while app is foregrounded, talkback labels, high contrast/reduced transparency, offline/cache states, slow image, provider error, zero results, and partial success.
- Provider QA: source-specific filters/pagination, true metadata and rights, cancellation, rate-limit persistence, deduplication and usable-result counts. Test against mocked fixtures and approved public endpoints; never use fabricated success as production evidence.
- Release QA: build the actual release variant, verify the configured production certificate (debug signing is not production signing), install/launch, run smoke routes, and publish only an independently verified signed APK. Refuse to overwrite an existing release.

## 7. Compose implementation map

- Theme model and persisted migration: `core/data/SettingsRepository.kt` (`ThemeMode` + `DesignTheme`).
- Semantic tokens and palettes: `core/design/Color.kt`; live system-mode resolution and Material scheme: `core/design/Theme.kt`.
- Backdrop and Haze-backed material components: `core/design/Glass.kt` and app root `AmbientBackdrop` source.
- Settings controls and preview cards: `feature/settings/SettingsScreen.kt`.
- Screen flows: `feature/home`, `feature/search`, `feature/wallpaper`, `feature/collections`, `feature/offline`, and `app/AuroroRoot.kt`.

This spec is an implementation contract, not evidence that every listed flow has already passed device testing. Record incomplete screens and unverified states in the release checklist instead of marking them complete by design alone.
