# Auroro Wallpapers privacy notice

**Last updated: 2026-10-09**

Auroro Wallpapers does not require an account and does not operate an application server. The app contains no advertising, analytics, telemetry, social tracking or in-app purchase SDK. We do not receive a copy of your wallpaper searches or files.

## Network requests

When you use a source, your device connects directly to that source over HTTPS:

- **Wallhaven:** the public SFW API at `wallhaven.cc` and its published thumbnail/original image hosts. Searches, pagination, source-page opens and image downloads are visible to Wallhaven from your network address as described by Wallhaven's own policies.
- **Wallpaper Abyss:** `api.alphacoders.com` and Alpha Coders image hosts only if you add your own API key and use this source. Requests use the API key you supplied. The key is encrypted on this device with AndroidKeyStore AES-GCM and is not included in Auroro's source, APK build configuration or backups. Alpha Coders handles API request data under its terms.
- **Unsplash:** Auroro makes no Unsplash API or image request. If you choose the browser handoff, the external browser opens Unsplash, which is then governed by Unsplash's own privacy policy. Referral parameters are included on the official-site link.

Image thumbnail and original URLs are served by the relevant publisher. Android/Coil keeps a bounded **temporary** image cache in the app cache directory; it is not displayed as an offline download. You can clear it in Settings. A source may log standard web request data (such as IP address, requested URL, time and user agent); Auroro does not control publisher retention.

## Data stored on your device

Room stores wallpaper source IDs and page/image URLs, metadata, favorites, collection membership, recent viewing history, and download status/file references. DataStore stores display, filter, source and download preferences. These are used only to provide the features in the app. They are not synced to an Auroro server.

The optional Alpha Coders key is encrypted using a device-bound AndroidKeyStore key. If app data is moved to another device, the key cannot be decrypted and is discarded; re-enter it on the new device.

Original downloads are saved to `Pictures/Auroro Wallpapers` through Android MediaStore by default, where other apps such as Gallery can access them. You may choose private app storage instead; Android removes those private files if the app is uninstalled. The app does not request broad shared-storage permissions.

Android app data backup is disabled. Favorites, settings and collection metadata are local and do not transfer through Auroro cloud backup.

## Permissions and notifications

- `INTERNET` and `ACCESS_NETWORK_STATE` are used for supported provider requests and useful offline/network error handling.
- `SET_WALLPAPER` is used only after you choose **Set wallpaper** (or enable the optional set-after-download setting).
- `POST_NOTIFICATIONS` is requested only if you enable download notifications. Downloads still work if notifications are denied.
- Foreground-service permission is used by WorkManager for an active, user-started wallpaper download.

No contacts, location, camera, microphone, advertising ID, or unrelated device permission is requested.

## Retention and deletion

Favorites, collections, history and download records can be managed in the app. Deleting a completed download removes its saved file when Android permits it. Clearing the image cache does not delete originals. Uninstalling removes app-private files and app database/preferences; MediaStore downloads in Pictures remain until you delete them in Gallery or Android storage settings.

## Changes and contact

This notice may change as the app changes. Review the privacy notice shipped with the source at the version you install. For a privacy question or correction to this notice, open an issue in the project repository: <https://github.com/Nazatric/Aurora-Wallpapers/issues>.

Publisher privacy policies: <https://wallhaven.cc/privacy-policy>, <https://alphacoders.com/privacy> and <https://unsplash.com/privacy>.
