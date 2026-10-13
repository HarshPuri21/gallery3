# Gallery Grabber Android v1.1.1 (Java compilation fix)

Fixed `InterruptedException` at `MainActivity.java` download queue. Includes the modern Android SDK workflow fix from the previous version.

# Gallery Grabber Android — V2PH + Xasiat (v1.1)

Native Java Android project with a **full-screen login browser** and **custom wallpaper**. Designed for GitHub Actions APK builds. Requires **Android 10 (API 29) or newer**. Python, V2DL, and gallery-dl are not needed on Android.

## Build on GitHub without Android Studio

1. Make a new empty GitHub repository, e.g. `gallery-grabber-android`.
2. Extract the ZIP and upload **the contents of `GalleryGrabber_Android/`**, not the containing folder, into the repository root. Preserve `.github/workflows/build-apk.yml` (it's hidden on some file managers).
3. Commit all files on `main`. Open **Actions → Build Android APK → Run workflow**. A push to `main` also triggers the workflow.
4. When the run completes successfully, select it and open **Artifacts → GalleryGrabber-Android-debug-APK**.
5. Download the GitHub artifact ZIP, extract `app-debug.apk`, and install it on your phone. Android might ask permission to install an app from your file manager.

You can also build locally with Android SDK 35 and Gradle 8.7 by running `gradle :app:assembleDebug`. Java 17 is required. The project uses Android Gradle Plugin 8.6.1.

**Do not upload passwords, cookies, or personal pictures to GitHub.** The project ships with no stored sessions or wallpaper.

## Log in using the full-screen browser

1. On the home screen, tap **Site login** and choose **V2PH** or **Xasiat**.
2. The selected website opens in a dedicated browser screen with an editable **HTTPS address bar**, **Go**, **Back**, **Forward**, **Reload**, **Site home**, and **Done**.
3. Sign in on the website. The browser uses Android WebView, JavaScript, DOM storage, and shared persistent WebView cookies.
4. The status line says **site cookies present** or **no cookies detected**. **Cookies are not proof of a successful login**; verify by looking at the site's own signed-in view.
5. Tap **Done** to save cookies and return to the home screen. The downloader reuses that cookie store for image requests.

The browser may not support some third-party OAuth or popup-only login flows; such login systems may require a different implementation. **No site access or paywall bypass is provided.** Login cookies remain in the app's private WebView data. To erase them, use Android Settings → Apps → Gallery Grabber → Storage → Clear storage (this also clears wallpaper preferences, not public image downloads).

## Set a full-screen wallpaper

1. Tap **Wallpaper → Choose image**.
2. Pick a photo using Android's built-in image picker/document picker.
3. The photo fills the entire app background, including **behind the status and navigation bars**, header, controls, and download log. Buttons and panels are translucent; a dark overlay keeps text legible.
4. The wallpaper is **center-cropped** to fill any screen shape, downsampled for memory safety, and automatically reapplied after restarting the app. There is no need for Photos/storage permission.
5. Tap **Wallpaper → Remove wallpaper** to switch back to the gradient background.

Wallpaper is also visible behind the browser's toolbar and system bars. **The webpage itself is opaque** and uses its own styling; the app cannot overlay the wallpaper on third-party site content without obscuring or altering the site's UI.

The chosen image URI stays on-device and is not sent to the download websites. The app retains read permission to it using Android's Storage Access Framework. If the original photo is moved or access is revoked, the app falls back to the default gradient.

## Download albums

1. Paste one or more supported HTTPS **album** URLs (one per line): `https://www.v2ph.com/album/....html` or `https://www.xasiat.com/albums/1234/title/`.
2. Tap **Add to queue**, then **Start**. Pause/Resume and Stop work while the app stays open.
3. Images are saved under `Pictures/Gallery Grabber/V2PH/<album>/` or `Pictures/Gallery Grabber/Xasiat/<album>/` using Android MediaStore, with separate directories for each album. Existing numbered image files are skipped when an album is downloaded again.

Android supports individual album URLs, not full Xasiat member/category/tag collections (those are desktop-only). Xasiat is scraped from the album page; V2PH attempts pagination via `?page=N` until no new images are found (max 300 pages). A site login wall, failed page, or missing images may cause incomplete albums; always verify the saved file count.

## Validation and limitations

- Layout XML, Manifest, theme/drawables, and resource references have been checked; Java domain rules tested separately.
- **Not yet compiled with the full Android SDK or verified on a live phone or against the two sites** in this environment. GitHub Actions performs the actual unit tests and APK compilation.
- Web pages may change, images can be lazy-loaded, signed download links may expire, and cookies may be insufficient for access.
- The app has no background service, so keep it open during downloads. Pause/resume stops between image transfers; it is not byte-range download resumption.
- This repository produces a **debug APK for personal testing**, not a Play Store release.

Only download content that you have permission to access and retain.


## GitHub Actions SDK setup repair (October 2026)

The workflow now uses `android-actions/setup-android@v4` and explicitly requests
only the supported `platform-tools` package. Earlier workflows used v3, which
requested Google's removed legacy `tools` package and stopped before building.

If GitHub reports `Failed to find package 'tools'`, upload the new
`.github/workflows/build-apk.yml` file to the repository root, commit it,
and trigger **Actions → Build Android APK → Run workflow**. The project source
files in `app/` were not changed by this SDK setup repair.

The local checks do not replace a real GitHub/Android SDK compilation. If the
new run fails at a later step, use the new error log for diagnosis.
