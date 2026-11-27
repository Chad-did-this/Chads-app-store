import datetime
import json
import pathlib
import struct
import sys
import zlib

ipa, version, build, release_url, source_url = sys.argv[1:]
file = pathlib.Path(ipa)
icon_url = source_url.rsplit("/", 1)[0] + "/icon.png"
def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
width = height = 128
rows = []
for y in range(height):
    row = bytearray([0])
    for x in range(width):
        distance = abs(x-64) + abs(y-64)
        row.extend((101, 228, 161) if distance < 37 else (16, 20, 27))
    rows.append(bytes(row))
pathlib.Path("icon.png").write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b""))
date = datetime.date.today().isoformat()
description = "Native iPhone catalog with Android and Apple sections. This IPA requires compatible signing and sideloading."
entry = {"version": version, "buildVersion": build, "date": date, "downloadURL": release_url, "size": file.stat().st_size, "minOSVersion": "16.0", "localizedDescription": description}
app = {"name": "Chad's App Store", "bundleIdentifier": "com.chaddidthis.universalstore.ios", "developerName": "Chad", "localizedDescription": description, "subtitle": "Universal app catalog", "iconURL": icon_url, "tintColor": "9FC4FF", "versions": [entry], "version": version, "versionDate": date, "downloadURL": release_url, "size": file.stat().st_size, "appPermissions": {"entitlements": [], "privacy": []}}
source = {"name": "Chad's App Store", "identifier": "com.chaddidthis.universalstore.source", "sourceURL": source_url, "iconURL": icon_url, "apps": [app], "news": []}
pathlib.Path("source.json").write_text(json.dumps(source, indent=2) + "\n")
