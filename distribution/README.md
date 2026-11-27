# Distribution and installation

## Web store
The website source is in `website/`. Enable Pages under Settings → Pages → Build and deployment → Source → GitHub Actions, then run the Deploy website workflow. Keep `docs/catalog.json` at its existing path: released Android and iPhone apps read that registry directly, and the website uses the same source. The Android app fetches the catalog directly from public GitHub and opens the Android installer after verifying APK integrity, package ID, version and existing signer. The iOS app also fetches the shared catalog directly from GitHub. It provides IPA download links and SideStore / AltStore source sharing, not a system installer.

## Android
Android publishing builds require a permanent signing key. If the signing secret is missing or invalid, the build fails instead of publishing an APK signed with a temporary key. Configure the dedicated signing keystore through repository Actions secret `ANDROID_SIGNING_JSON` containing `keystore_b64` (base64 PKCS12), `password`, and `alias` (same password for key). Keep a backup and never commit the key. The workflow re-signs the APK with this key before release. Android still requires user confirmation. Changing signing keys can require reinstalling.

## iPhone
The workflow uses Xcode on a macOS runner and packages an unsigned device-targeted app as an IPA. It is not installable directly by tapping a download. Use a compatible sideloading tool and your own Apple account, or implement an authorized Apple distribution workflow separately. SideStore/AltStore account restrictions still apply.

The generated SideStore source is published at https://github.com/Chad-did-this/Chads-app-store/releases/download/ios-source/source.json only after the iOS build succeeds. Add it in a compatible sideloading tool. The catalog must declare matching bundle identifier and version. The integration needs real-device testing.

## Adding apps
Create an independent app project under `App Store/Android/` or `App Store/Apple/`. Each platform has its native storefront in `Store/`. Add independent build/publish steps and a matching entry in docs/catalog.json. For iOS, extend the source generator to list additional apps. Preserve package identifiers and use versioned release assets.

Do not publish personal credentials, unauthorized private source code or private signing keys in this public repository.

A commit containing `[layout only]` runs build validation but skips publishing new APKs and IPAs for a folder-only migration.

Android APK signing must use the same key across future versions of a package. A one-time uninstall/reinstall is necessary for installations signed with an earlier temporary key. Build verification prints public certificate fingerprints, not the private key or password. Keep the original keystore and secret backup privately; do not regenerate them for updates.
