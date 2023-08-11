#!/usr/bin/env python3
import json
import os
import six
import subprocess
import sys
import wget
from pathlib import Path
from urllib.error import HTTPError

MANIFEST_LOCATION = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"


def load_json(url):
    try:
        with six.moves.urllib.request.urlopen(url) as stream:
            return json.load(stream)
    except HTTPError as e:
        print('HTTP Error')
        print(e)
        sys.exit(-1)


def main():
    # Get latest version from manifest
    manifest = load_json(MANIFEST_LOCATION)
    latest = manifest["latest"]
    snapshot = latest["snapshot"]
    release = latest["release"]

    # Compare with old version
    last_release = release
    last_snapshot_path = Path('last_snapshot.txt')
    if last_snapshot_path.exists():
        with open(last_snapshot_path, 'r') as f:
            if f.readline() == snapshot:
                sys.exit(1)

    # Download version data
    manifestEntry = None
    for entry in manifest["versions"]:
        if entry["id"] == snapshot:
            manifestEntry = entry
            break

    if manifestEntry is None:
        print("VERSION DATA FOR", snapshot, "NOT FOUND")
        sys.exit(1)

    # Rename old server jar as backup and download new one
    if os.path.exists("server_old.jar"):
        os.remove("server_old.jar")
    if os.path.exists("server.jar"):
        os.rename('server.jar', 'server_old.jar')

    print("=== Downloading server...")
    versionData = load_json(manifestEntry["url"])
    serverUrl = versionData["downloads"]["server"]["url"]
    wget.download(serverUrl, "server.jar")

    with open(last_snapshot_path, 'w') as f:
        f.write(snapshot)

    sys.exit(0)


if __name__ == "__main__":
    main()
