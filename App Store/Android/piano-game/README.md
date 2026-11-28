# Piano Game

Endless piano sight-reading practice with Android's native AudioRecord microphone input. The WebView displays bundled offline assets; it does not request browser microphone permission.

The Android activity handles permission requests and stops recording when the app enters the background. NativeMic owns the recording session, audio-source and sample-rate fallbacks, and PCM frames. The assets folder separates the page, styles, pitch detection, microphone bridge, and game logic.

Record single notes without the sustain pedal. Android microphone privacy and other recording apps can block capture; failures return to the Play screen with a retry message. The on-screen test keys work without microphone permission.

Run `node --test tools/game.test.cjs` for pitch, microphone bridge, and scoring checks. Run `gradle :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease` for Android checks. Store releases use the existing permanent signing key and monotonic version codes.

