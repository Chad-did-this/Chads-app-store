import pathlib
import re
import sys
import xml.etree.ElementTree as ET


ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "com.chaddidthis.commandpro"
SIGNER_SHA256 = "d983b126a34c94c5e0854498e0db24134b2f25b24604b58f205a9b0418784a0d"
PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.TRANSMIT_IR",
}


def require(condition, message):
    if not condition:
        raise SystemExit("Release APK check failed: " + message)


def full_name(name):
    if name.startswith("."):
        return PACKAGE + name
    if "." not in name:
        return PACKAGE + "." + name
    return name


def check(manifest_path, certificate_path, run_number):
    manifest = ET.parse(manifest_path).getroot()
    version = str(1000 + int(run_number))
    require(manifest.get("package") == PACKAGE, "unexpected package ID")
    require(manifest.get(ANDROID + "versionCode") == version, "wrong version code")
    require(manifest.get(ANDROID + "versionName") == "1.0." + version, "wrong version name")

    sdk = manifest.find("uses-sdk")
    require(sdk is not None, "missing Android SDK declaration")
    require(sdk.get(ANDROID + "minSdkVersion") == "30", "Android 11 support changed")
    require(int(sdk.get(ANDROID + "targetSdkVersion", "0")) >= 35, "target SDK is too old")

    permissions = {
        node.get(ANDROID + "name")
        for node in manifest
        if node.tag in {"uses-permission", "uses-permission-sdk-23"}
    }
    require(permissions == PERMISSIONS, "unexpected permissions: " + repr(sorted(permissions ^ PERMISSIONS)))

    application = manifest.find("application")
    require(application is not None, "missing application")
    require(application.get(ANDROID + "enabled", "true") == "true", "application is disabled")
    require(application.get(ANDROID + "debuggable", "false") == "false", "APK is debuggable")
    require(application.get(ANDROID + "testOnly", "false") == "false", "APK is test-only")
    require(application.get(ANDROID + "allowBackup") == "false", "backup setting changed")
    require(application.get(ANDROID + "usesCleartextTraffic") == "false", "cleartext traffic setting changed")

    activities = {
        full_name(node.get(ANDROID + "name", ""))
        for node in application.findall("activity")
    }
    expected = {PACKAGE + "." + name for name in (
        "home.MainActivity", "hardware.HardwareActivity", "terminal.TerminalActivity", "architecture.ArchitectureActivity"
    )}
    require(activities == expected, "unexpected Android activities")

    aliases = application.findall("activity-alias")
    require(len(aliases) == 1, "expected one compatible launcher alias")
    launcher = aliases[0]
    require(full_name(launcher.get(ANDROID + "name", "")) == PACKAGE + ".MainActivity", "launcher component changed")
    require(full_name(launcher.get(ANDROID + "targetActivity", "")) == PACKAGE + ".home.MainActivity", "wrong launcher target")
    require(launcher.get(ANDROID + "enabled", "true") == "true", "launcher alias is disabled")
    target = next(node for node in application.findall("activity")
                  if full_name(node.get(ANDROID + "name", "")) == PACKAGE + ".home.MainActivity")
    require(target.get(ANDROID + "enabled", "true") == "true", "home activity is disabled")
    children = list(application)
    require(children.index(target) < children.index(launcher), "home activity must precede its launcher alias")
    launcher_filters = [
        node for node in launcher.findall("intent-filter")
        if any(action.get(ANDROID + "name") == "android.intent.action.MAIN" for action in node.findall("action"))
        and any(category.get(ANDROID + "name") == "android.intent.category.LAUNCHER" for category in node.findall("category"))
    ]
    require(len(launcher_filters) == 1, "missing launcher intent filter")

    exported = {
        full_name(node.get(ANDROID + "name", ""))
        for node in application
        if node.tag in {"activity", "activity-alias", "service", "receiver", "provider"}
        and node.get(ANDROID + "exported") == "true"
    }
    require(exported == {PACKAGE + ".MainActivity"}, "unexpected exported components")

    certificate = pathlib.Path(certificate_path).read_text()
    signers = re.findall(r"^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$", certificate, re.MULTILINE)
    require([value.lower() for value in signers] == [SIGNER_SHA256], "original signing certificate changed")
    print("Validated signed release APK: " + PACKAGE + " 1.0." + version)


if __name__ == "__main__":
    require(len(sys.argv) == 4, "expected manifest, certificate report and workflow run number")
    check(*sys.argv[1:])
