# Source and verification blockers

This file records genuine access/verification limits; it is not a substitute for implemented work.

## Wallpaper Abyss API key and subscription

- The official Alpha Coders API exists and Auroro has implemented the documented API 3.0 `search` and `newest` requests, response normalization, desktop/phone lanes and pagination.
- Alpha Coders requires an API subscription and an individually issued key. No subscription key was provided, so a live Wallpaper Abyss response could not be tested from this project build.
- Add the user's own key in **Settings → Wallpaper sources** to make this integration available. The key is encrypted locally with AndroidKeyStore; the app does not provide or proxy a shared key.
- Without a key, the source is marked unavailable and can be opened on the official Wallpaper Abyss site. No site scraping or private endpoint is used.

## Unsplash API authorization

- The current Unsplash guidelines explicitly say that the API may not replicate the core experience of an unofficial client or wallpaper application; they give downloading Unsplash images from a wallpaper app as a prohibited example. The guidelines also require a confidential key and download tracking.
- Auroro therefore makes **no Unsplash API calls** and does not scrape, proxy or disguise a client. The app offers an official-site browser handoff with referral parameters.
- Implementing an API client would require prior explicit written permission from Unsplash for this exact wallpaper-aggregation use case (and an authorized credential path). No such approval or key was supplied.

## Environment verification limits

- This sandbox has no JDK, Android SDK, emulator or attached reference image file. Android compilation, lint and unit tests are therefore delegated to the checked-in GitHub Actions workflow; screenshot capture, physical-device verification, frame profiling and pixel comparison against the concept image cannot be performed here.
- The build sandbox permits outbound access only to a small set of package/code hosts, not `wallhaven.cc` or `alphacoders.com`. No live source smoke test was run from this environment. Mock HTTP fixtures cover repeatable local tests; `scripts/live_source_smoke.sh` is an optional manual live check for a networked machine.

These constraints do not affect the on-device public Wallhaven SFW integration. User-visible status always distinguishes an unavailable source from an empty successful result.
