from pathlib import Path
import sys
from zipfile import ZipFile


def check_apk(path):
    with ZipFile(path) as apk:
        for resource in (
            "index.html", "css/game.css", "js/pitch.js",
            "js/native-microphone.js", "js/game.js",
        ):
            apk.read("assets/" + resource)

        scripts = "\n".join(
            apk.read(name).decode("utf-8")
            for name in apk.namelist()
            if name.startswith("assets/") and name.endswith((".html", ".js"))
        )
        for obsolete in ("getUserMedia", "AudioContext", "webkitAudioContext"):
            if obsolete in scripts:
                raise SystemExit("Browser microphone code remains in the APK: " + obsolete)
        if "AndroidMic" not in scripts:
            raise SystemExit("The native microphone bridge is missing from the APK.")
    print("Piano APK assets and native microphone integration verified.")


if __name__ == "__main__":
    check_apk(Path(sys.argv[1]))
