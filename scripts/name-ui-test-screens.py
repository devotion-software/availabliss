"""Renames screenshots exported from an .xcresult to their XCTAttachment names and
drops the other exported attachments (logs, video). Usage: name-ui-test-screens.py <dir>"""
import json
import pathlib
import sys

folder = pathlib.Path(sys.argv[1])
manifest = json.loads((folder / "manifest.json").read_text())
keep = set()
for test in manifest:
    for attachment in test.get("attachments", []):
        exported = folder / attachment["exportedFileName"]
        name = attachment.get("suggestedHumanReadableName", "")
        if exported.suffix == ".png" and name[:2].isdigit():
            target = folder / (name.split("_")[0] + ".png")
            exported.rename(target)
            keep.add(target.name)
for path in folder.iterdir():
    if path.name not in keep:
        path.unlink() if path.is_file() else None
