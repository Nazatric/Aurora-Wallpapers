# Artwork and font licences

## Runtime wallpaper imagery

No wallpaper photographs are bundled. Wallhaven, NASA APOD and qualifying Art Institute of Chicago Open Access media are requested at runtime. Wallhaven media remains subject to its original creator's rights; NASA APOD media is **view-only in Auroro until reuse rights are confirmed for that individual item**; Art Institute records are included only when the API marks them public domain and are attributed to the museum. Reddit is disabled pending authorized API access. See [`README.md`](README.md) for source-specific behavior and [`PRIVACY.md`](PRIVACY.md) for network use.

## Launcher and notification icon

The launcher foreground, monochrome adaptive-icon layer and notification glyph use Google's **Material Icons `landscape`** icon, under Apache License 2.0. The 24 px upstream SVG is preserved at [`licenses/MaterialLandscape.svg`](licenses/MaterialLandscape.svg); the Android vectors are scale-adjusted copies of its path. Source: <https://github.com/google/material-design-icons/blob/master/src/image/landscape/materialicons/24px.svg>. Licence: <https://github.com/google/material-design-icons/blob/master/LICENSE>.

The adaptive icon background is an original Auroro color gradient. Its foreground is transparent outside the licensed glyph and the manifest references adaptive launcher resources, including the Android 13+ monochrome layer.

## Open Sans

Open Sans regular, semibold and bold TTF files are bundled under the SIL Open Font License 1.1. The font is by the Open Sans Project Authors; it is not a copy of the proprietary Frutiger or Segoe UI typefaces and does not imply endorsement.

- Source: <https://github.com/google/fonts/tree/main/ofl/opensans>
- Licence text: [`licenses/OpenSans-OFL.txt`](licenses/OpenSans-OFL.txt)

## Auroro-created interface artwork

The app's color palette, gradients, component styling and interface layout are project work. No provider logos, wallpaper photographs or unlicensed image assets are bundled.
