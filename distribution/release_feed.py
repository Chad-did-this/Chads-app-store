"""Generate public release metadata for the storefront, without exposing authentication."""
import json
import os
import pathlib
import plistlib
import re
import time
import urllib.request

repo = os.environ["GITHUB_REPOSITORY"]
catalog = json.loads(pathlib.Path("docs/catalog.json").read_text())
prefixes = [(a["releasePrefix"], a["asset"]) for a in catalog]
releases = []
page = 1
while True:
    request = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/releases?per_page=100&page={page}",
        headers={"Authorization": "Bearer " + os.environ["GH_TOKEN"],
                 "Accept": "application/vnd.github+json",
                 "User-Agent": "ChadsStoreFeed/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        batch = json.load(response)
    if not isinstance(batch, list):
        raise ValueError("Invalid release response")
    for r in batch:
        tag = r["tag_name"]
        if r["draft"] or r.get("prerelease"):
            continue
        allowed = {name for prefix, name in prefixes if tag.startswith(prefix)
                   and re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", tag[len(prefix):])}
        if not allowed:
            continue
        assets = []
        for a in r["assets"]:
            if a["name"] not in allowed and not any(a["name"] == n+".sha256" for n in allowed):
                continue
            if not a["browser_download_url"].startswith(f"https://github.com/{repo}/releases/download/"):
                continue
            assets.append({k:a.get(k) for k in ("name","size","browser_download_url","digest")})
        if assets:
            releases.append({"tag_name":tag,"draft":False,"assets":assets})
    if len(batch)<100:
        break
    page += 1
    if page>100:
        raise RuntimeError("Release list too large")
pathlib.Path("website/release-feed.json").write_text(json.dumps(
    {"generated":int(time.time()),"catalog":catalog,"releases":releases},indent=2)+"\n")
print(f"Generated public feed: {len(catalog)} catalog entries, {len(releases)} releases")

# Publish the OTA manifest for the newest available iPhone storefront build.
# This describes the IPA; it does not sign or provision the application.
ios_app = next(a for a in catalog if a["id"] == "ios-store" and a["platform"] == "ios")
candidates = []
for release in releases:
    tag = release["tag_name"]
    prefix = ios_app["releasePrefix"]
    if not tag.startswith(prefix):
        continue
    version = tag[len(prefix):]
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        continue
    for asset in release["assets"]:
        if asset["name"] == ios_app["asset"] and asset["size"] > 0:
            candidates.append((tuple(map(int, version.split("."))), asset["browser_download_url"]))
if not candidates:
    raise RuntimeError("No published iPhone store IPA for OTA manifest")
version, ipa_url = max(candidates)
manifest = {"items": [{
    "assets": [{"kind": "software-package", "url": ipa_url}],
    "metadata": {
        "bundle-identifier": ios_app["packageId"],
        "bundle-version": str(version[-1]),
        "kind": "software",
        "title": ios_app["name"],
    },
}]}
manifest_path = pathlib.Path("website/ios/manifest.plist")
manifest_path.parent.mkdir(parents=True, exist_ok=True)
manifest_path.write_bytes(plistlib.dumps(manifest, fmt=plistlib.FMT_XML))
print("Generated iPhone OTA manifest for", ".".join(map(str, version)))
