import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile


def sign(source, destination):
    try:
        key = json.loads(os.environ["ANDROID_SIGNING_JSON"])
        assert all(isinstance(key[field], str) and key[field] for field in ("keystore_b64", "password", "alias"))
        assert all(character not in key["password"] + key["alias"] for character in "\r\n\0")
        data = base64.b64decode(key["keystore_b64"], validate=True)
        assert data
    except Exception:
        raise SystemExit("A valid permanent ANDROID_SIGNING_JSON secret is required. No temporary signing key will be used.") from None

    signer = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0/apksigner"
    destination.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="android-signing-", dir=os.environ.get("RUNNER_TEMP")) as directory:
        keystore = Path(directory) / "developer.p12"
        keystore.write_bytes(data)
        keystore.chmod(0o600)
        environment = dict(os.environ)
        environment.pop("ANDROID_SIGNING_JSON", None)
        environment["CONVERTER_SIGNING_PASSWORD"] = key["password"]
        subprocess.run([
            str(signer), "sign", "--ks", str(keystore), "--ks-type", "PKCS12",
            "--ks-key-alias", key["alias"], "--ks-pass", "env:CONVERTER_SIGNING_PASSWORD",
            "--key-pass", "env:CONVERTER_SIGNING_PASSWORD", "--out", str(destination), str(source)
        ], check=True, env=environment)

    subprocess.run([str(signer), "verify", "--verbose", "--print-certs", str(destination)], check=True)


if __name__ == "__main__":
    sign(Path(sys.argv[1]), Path(sys.argv[2]))
