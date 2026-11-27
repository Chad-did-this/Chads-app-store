# Developer guide — Chad's App Store

One storefront. Two platforms. Users choose which section to browse.

| Android | iPhone |
| --- | --- |
| Green · APK downloads | Blue · IPA downloads |
| Android package installer | SideStore / AltStore Classic signing |

## Storefront

The website is in [website/](../website/). In **Settings → Pages → Build and deployment → Source**, select **GitHub Actions**, then run the [Deploy website workflow](https://github.com/Chad-did-this/Chads-app-store/actions/workflows/pages.yml). The expected address is https://chad-did-this.github.io/Chads-app-store/ (available only after Pages is enabled).

The catalog checks actual GitHub releases before enabling download links. Android also supports native installation and update checks without GitHub Pages. There are no placeholder APK or IPA downloads.

## Repository

- [App Store/Android/](../App%20Store/Android/) — native Android store in `Store/` and individual Android app projects.
- [App Store/Apple/](../App%20Store/Apple/) — native iPhone store in `Store/` and future iOS app projects.
- [website/](../website/) — responsive, platform-selectable web storefront.
- [docs/](./) — developer documentation and the stable legacy catalog URL.
- [distribution/](../distribution/) — iOS source generator and publishing instructions.
- [.github/workflows/](../.github/workflows/) — independent Android and iOS builds.

Command Center PRO has its own [source organization guide](../App%20Store/Android/command-center-pro/README.md). The three tools use one shared ADB identity and connection dialog. It is an Android app; the Apple folder contains the separate native iPhone storefront.

## First builds

Watch [GitHub Actions](https://github.com/Chad-did-this/Chads-app-store/actions). Both platforms have a starter app called Chad's App Store. The Android workflow requires the permanent developer signing key and stops before publication if that key is missing or invalid. The iOS workflow publishes a device-targeted unsigned IPA for re-signing with a compatible sideloading tool. Neither is an Apple App Store release.

See [setup and installation](../distribution/README.md) for the signing, update, and distribution requirements. No private credentials are included. Hardware Explorer was transferred with authorization and incorporated into Command Center PRO.

## Retired applications

Hardware Explorer, Self Debugger, Android Command Center and Phone Architecture Explorer were consolidated into Command Center PRO. Their original source and workflows are retained in [the pre-consolidation tree](https://github.com/Chad-did-this/Chads-app-store/tree/dddf15cfed6611d8be74a4ca79715ed0458fb05e). Old releases are not part of the active catalog.

## Shared catalog

TXT & HTML Converter is a separate [Android app project](../App%20Store/Android/text-html-converter/README.md). It imports one or several TXT/HTML files through Android's document picker and saves selected files or a batch in either format. Its independent release workflow uses the existing permanent signing key. Default conversion keeps HTML source intact; the optional plain-text HTML page mode escapes markup for display.

`docs/catalog.json` is the single registry used by both already-released native stores and the website. Keep this exact path for backward compatibility; there is no duplicate catalog to maintain.

## Automatic catalog delivery

The Pages workflow generates a public `release-feed.json` from the canonical catalog and published releases using the runner's temporary read-only token. The output contains only public catalog and asset metadata. New releases trigger a Pages refresh through `workflow_run`; the periodic refresh is a fallback. Android and the website prefer this feed, avoiding anonymous API rate limits, and fall back to the original public API. No customer credentials are required. Existing native apps can still use the original catalog URL.
