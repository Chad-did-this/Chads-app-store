# TXT & HTML Converter

An independent Android app in Chad's App Store. Open `.txt`, `.html` or `.htm` files through the system file picker, edit or paste text, and save new copies as TXT or HTML. No internet connection, account, broad storage access or USB debugging is needed.

The default conversion keeps the source code, Unicode characters, line endings and supported text encoding intact. HTML to TXT keeps the HTML source; it does not remove tags. When a TXT file contains ordinary words, select **Make plain text into a readable HTML page** to produce a complete HTML document with escaped text and preserved spacing. That option only affects HTML output.

Select several files to enable **Save all as .html** and **Save all as .txt**. Batch export asks for a destination folder and creates a new output file for every open document. Existing files and source files are preserved. Providers may add a number to duplicate output names. Up to 32 documents can be open, each imported file may be up to 5 MB, and the session holds up to 10 million UTF-16 code units. Supported encodings are UTF-8 and BOM-marked UTF-16 LE/BE. Unsupported or malformed encodings produce an error instead of silently replacing characters.

## Source folders

- `conversion/`: text decoding, encoding, safe file names and HTML generation.
- `files/`: system document-provider reads, writes and batch destinations.
- `home/`: screen, session state and background operations.
- `app/src/test/`: conversion, Unicode, encoding and file-name regression tests.
- `tools/`: checks on the final release manifest.

Edits and selected files survive rotation. Android instance-state recovery uses an app-private cache snapshot rather than placing file contents in an Android activity bundle. The app does not execute uploaded HTML.

## Build and release

Use Java 17, Gradle 8.9 and Android SDK 35:

```sh
gradle -p "App Store/Android/text-html-converter" :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

The dedicated GitHub workflow signs the release APK with the existing `ANDROID_SIGNING_JSON` developer key, verifies its signature and package metadata, and publishes `text-html-converter.apk` plus a SHA-256 checksum under a versioned `text-html-converter-v1.0.N` release. The shared `docs/catalog.json` registry makes the app appear in the existing Android storefront; users do not need a new store installation. A successful release refreshes the public release feed.
