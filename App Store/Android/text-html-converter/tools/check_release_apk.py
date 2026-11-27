import sys
import xml.etree.ElementTree as ET

manifest = ET.parse(sys.argv[1]).getroot()
android = "{http://schemas.android.com/apk/res/android}"
package = "com.chaddidthis.texthtml"

assert manifest.attrib["package"] == package, "Incorrect package identifier"
assert int(manifest.attrib[android + "versionCode"]) == int(sys.argv[2]), "Incorrect version code"
assert manifest.attrib[android + "versionName"] == "1.0." + sys.argv[2], "Incorrect version name"
sdk = manifest.find("uses-sdk")
assert sdk.attrib[android + "minSdkVersion"] == "26", "Incorrect minimum SDK"
assert sdk.attrib[android + "targetSdkVersion"] == "35", "Incorrect target SDK"
application = manifest.find("application")
assert application.attrib.get(android + "debuggable", "false") == "false", "Debug APK is not a release"
assert application.attrib.get(android + "allowBackup") == "false", "Drafts must remain private"

allowed_permissions = {package + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
for node in manifest.findall("uses-permission"):
    assert node.attrib[android + "name"] in allowed_permissions, "Unexpected app permission"

exported = []
for kind in ("activity", "activity-alias", "service", "receiver", "provider"):
    for component in application.findall(kind):
        if component.attrib.get(android + "exported") == "true":
            if kind == "receiver" and component.attrib[android + "name"] == "androidx.profileinstaller.ProfileInstallReceiver":
                assert component.attrib.get(android + "permission") == "android.permission.DUMP", "Profile installer must be system-protected"
                continue
            name = component.attrib[android + "name"]
            if name.startswith("."):
                name = package + name
            elif "." not in name:
                name = package + "." + name
            exported.append(name)
assert exported == [package + ".home.MainActivity"], "Unexpected exported component"
print("Release package, version, SDK, permissions and component checks passed.")
