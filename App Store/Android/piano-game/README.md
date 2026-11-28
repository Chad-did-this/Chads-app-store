# Piano Game

Endless piano sight-reading practice with Android's native AudioRecord microphone input. The WebView displays bundled offline assets; it does not request browser microphone permission.

The Android activity handles permission requests and stops recording when the app enters the background. NativeMic owns the recording session, audio-source and sample-rate fallbacks, and PCM frames. The assets folder separates the page, styles, pitch detection, microphone bridge, game logic, and note-study screens.

The Flashcards screen immediately shows C D E F G in separate Right hand and Left hand sections, with all names visible. Right-hand beginner practice starts at C3; the left-hand row starts one octave lower at C2. Each Starting C control changes only that hand and its beginner practice range. Every piano key contains its own staff note, including ledger lines and accidentals, and can be tapped or keyboard-activated to enlarge the note. Tap a letter or piano key to enlarge its staff note and piano key. The note guide pauses active practice and preserves the current notes and score. Study screens need no microphone permission. Note-name labels start enabled in scrolling practice.

Record single notes without the sustain pedal. Android microphone privacy and other recording apps can block capture; failures return to the Play screen with a retry message. The on-screen test keys work without microphone permission.

Run `node --test tools/game.test.cjs` for pitch, microphone bridge, and scoring checks. Run `gradle :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease` for Android checks. Store releases use the existing permanent signing key and monotonic version codes.


Pitch and octave naming are separate. By default, middle C (MIDI 60) is labeled C3; Settings can switch to C4 naming without transposing notes or moving their staff positions. Beginner right-hand notes use MIDI 60/62/64/65/67, and left-hand notes use MIDI 48/50/52/53/55. Staff placement always uses the actual MIDI pitch. The previous lower-C mapping must not be restored just to change octave labels.

Microphone sensitivity defaults to High, with Low, Normal, High and Very high available in Settings and during play. The same minimum RMS threshold is used by pitch detection and note-onset tracking. High uses 0.0002, compared with the former fixed 0.004 cutoff; Very high uses 0.00006. YIN input is normalized after measuring raw RMS, while periodicity checks and two-frame note confirmation remain active. The level meter reflects the chosen sensitivity. These changes accept weaker recorded signals; they do not alter hardware microphone gain or establish a tested physical pickup distance.

The practice screen anchors the score and staff above a reserved footer. Feedback, note hints and microphone messages have fixed-height slots, and the meter and sensitivity selector use their own control row. Changes in message length cannot resize the footer. Unchanged text is not rewritten on every animation frame. Run `python3 tools/check_layout.py` with Chrome installed to check rendered positions, document height and scroll position across six viewport sizes, changing messages, counters and note-name visibility.
