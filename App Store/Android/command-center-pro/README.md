# Android Command Center PRO

One Android 11+ APK with hardware diagnostics, live telemetry, an ADB terminal, saved scripts, quick diagnostics and 12 architecture scans. Package ID: `com.chaddidthis.commandpro`.

## Source organization

The Java source root is `app/src/main/java/com/chaddidthis/commandpro/`. Each main feature has its own folder and matching Java package. The shared ADB and screen helpers are grouped separately.

| File | Responsibility |
| --- | --- |
| `home/MainActivity.java` | Home screen and navigation to the three tools. |
| `hardware/HardwareActivity.java` | Standard hardware scan, HDMI/USB-C diagnostics, vendor light inspection and report export. |
| `hardware/DeepScan.java` | Detailed read-only hardware and firmware probes used by the hardware screen. |
| `hardware/LiveMonitor.java` | Live sensor and battery readings; monitoring stops when the hardware screen pauses. |
| `terminal/TerminalActivity.java` | Command tabs, history, quick scans, saved scripts and console export. |
| `architecture/ArchitectureActivity.java` | The 12 fixed read-only ADB subsystem scans and their reports. |
| `shared/adb/AdbConnectionDialog.java` | Shared pairing, connection and disconnection UI for both advanced tools. |
| `shared/adb/AdbEndpoint.java` | Validates pasted IP:port values and restricts connections to addresses belonging to this phone. |
| `shared/adb/LocalAdb.java` | The single persisted ADB identity, connection, serialized worker and timeout scheduler. |
| `shared/ui/InsetsSupport.java` | Android 15 system bar, display cutout and keyboard spacing. |

Hardware changes belong in `hardware/`, terminal changes in `terminal/`, and architecture scan changes in `architecture/`. Connection changes belong in `shared/adb/`; common screen-spacing changes belong in `shared/ui/`. The root folder contains no loose Java source files.

The hardware screen does not need ADB. Both advanced tools use the same `LocalAdb` connection and the same connection dialog. They connect only to loopback or an IP address assigned to this phone, and retain the connection until explicitly disconnected or Wireless debugging is disabled. Each activity closes its active command stream when destroyed without closing the shared connection.

## Pairing and connection

1. Enable Wireless debugging. Keep Android's pairing-code window visible using split screen.
2. Paste the whole **IP address:port** from **Pair device with pairing code** into the pairing address field. Enter the **six-digit PIN** separately, then tap **PAIR**.
3. Open CONNECT again. Paste the whole **IP address & port** from the main Wireless debugging screen into the connection address field, then tap **CONNECT**. This port is different from the pairing port. Connecting does not use a PIN.

Both address fields also accept just a port, using `127.0.0.1`. IPv6 addresses use `[address]:port`. The pairing identity is saved privately; the PIN is cleared on submission and is not saved or logged. Reconnecting does not require pairing again while Android still trusts the saved identity.

## Build and store listing

- Build project: this folder's `settings.gradle`, `build.gradle` and `app/build.gradle`.
- Build and publication: [command-center-pro.yml](../../../.github/workflows/command-center-pro.yml).
- Publication uses the release build. The signed APK is checked for its package/version, four required permissions, non-debuggable flag, private tool activities and original signing certificate before upload.
- The original launcher component is retained as an activity alias pointing to `home/MainActivity.java`, preserving the existing launcher identity for installed updates.
- Catalog: the single `command-center-pro` entry in [docs/catalog.json](../../../docs/catalog.json).
- The Android store remains in the neighboring `Store/` project. The iPhone store remains in `App Store/Apple/Store/`; Command Center PRO has no iOS build.

Generated builds and release metadata are not source files. The repository ignores Android build directories, release packaging output, generated iOS source files and the generated website release feed. Permanent Android signing stays in the existing Actions secret; future updates must use the same key and package ID.

Legacy phone-state and shared-storage permissions from merged dependencies are explicitly removed. Reports use Android's document picker, while the ADB identity and saved scripts use private app storage.

## Local data and access

Commands execute individually with confirmation. They are not a root shell or a persistent PTY. Restricted Android and vendor interfaces remain restricted. Activity output and histories are in memory; saved scripts and the ADB identity stay on the device. Reports are shared only when exported. No cloud upload.

LibADB Android, Conscrypt and Bouncy Castle remain required dependencies. Physical-device testing is required for pairing, hardware-specific probes and installation.

The full installed-package inventory intentionally uses `QUERY_ALL_PACKAGES`. Its manifest declaration has a targeted lint exception for the Google Play package-visibility advisory so the existing device diagnostics remain available in this repository-distributed APK. Other lint checks remain enabled.

Hardware Explorer PRO, Self Debugger, Android Command Center and Phone Architecture Explorer were consolidated here. Their retired projects and workflows are available in [Git history](https://github.com/Chad-did-this/Chads-app-store/tree/dddf15cfed6611d8be74a4ca79715ed0458fb05e), outside the active source tree and catalog. This package does not automatically migrate old app-private reports or scripts.
