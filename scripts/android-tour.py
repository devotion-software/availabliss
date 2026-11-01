"""Signs in to the Android app on a running emulator with a real email code (read
from Mailpit) and tours the main screens, saving a screenshot of each.

    python3 scripts/android-tour.py <seeded user email> [output dir]

Needs `make dev`, `make db-up`, an emulator running the debug build, and adb on
PATH or in ~/Library/Android/sdk/platform-tools.
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB") or os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
PACKAGE = "dev.joeherbert.availabliss"
MAILPIT = os.environ.get("MAILPIT_URL", "http://localhost:8027")

email = sys.argv[1]
out_dir = sys.argv[2] if len(sys.argv) > 2 else "android/build/tour-screens"
os.makedirs(out_dir, exist_ok=True)
failures: list[str] = []


def adb(*args: str, binary: bool = False):
    result = subprocess.run([ADB, *args], capture_output=True, check=True)
    return result.stdout if binary else result.stdout.decode()


def screen_nodes() -> list[ET.Element]:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return list(ET.fromstring(adb("shell", "cat", "/sdcard/ui.xml")).iter("node"))


def find(label: str, timeout: float = 10) -> ET.Element | None:
    """First node whose text or content description starts with `label`."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        for node in screen_nodes():
            for attr in ("text", "content-desc"):
                if node.get(attr, "").startswith(label):
                    return node
        time.sleep(0.5)
    return None


def tap(label: str, timeout: float = 10) -> bool:
    node = find(label, timeout)
    if node is None:
        failures.append(f"'{label}' not found")
        print(f"✗ '{label}' not found")
        return False
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1.5)
    return True


def type_text(text: str) -> None:
    adb("shell", "input", "text", text.replace("+", "\\+").replace("@", "\\@"))


def snapshot(name: str) -> None:
    with open(os.path.join(out_dir, f"{name}.png"), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))
    print(f"✓ {name}")


def latest_code() -> str:
    query = urllib.parse.urlencode({"query": f"to:{email}"})
    for _ in range(30):
        with urllib.request.urlopen(f"{MAILPIT}/api/v1/search?{query}") as res:
            messages = json.load(res)["messages"]
        if messages and re.match(r"^\d{6}", messages[0]["Subject"]):
            return messages[0]["Subject"][:6]
        time.sleep(0.5)
    raise SystemExit(f"No sign-in code arrived for {email}")


# Fresh start: clear app data so we're signed out, then launch.
adb("shell", "pm", "clear", PACKAGE)
for permission in ("POST_NOTIFICATIONS", "ACCESS_LOCAL_NETWORK"):  # the latter reaches the dev server
    adb("shell", "pm", "grant", PACKAGE, f"android.permission.{permission}")
adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
time.sleep(4)

if find("Email me a code", 20) is None:
    raise SystemExit("Sign-in screen didn't appear")
snapshot("01-sign-in")
tap("you@example.com")
type_text(email)
adb("shell", "input", "keyevent", "KEYCODE_BACK")  # hide the keyboard
tap("Email me a code")
time.sleep(2)
snapshot("02-code")
type_text(latest_code())

if find("Your groups", 20) is None:
    snapshot("xx-after-code")
    raise SystemExit("Groups home didn't appear after sign-in")
time.sleep(2)
snapshot("03-groups-home")

if tap("Lake District crew"):
    time.sleep(2)
    snapshot("04-group-month")
    for label, name in [("Week", "05-group-week"), ("Day", "06-group-day")]:
        if tap(label):
            time.sleep(1.5)
            snapshot(name)
    if tap("Chat"):
        time.sleep(1.5)
        snapshot("07-group-chat")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)
    for label, name in [("Proposals", "08-group-proposals"), ("Timing", "09-group-timing")]:
        if tap(label):
            snapshot(name)
    for _ in range(3):
        if find("Your groups", 2) is not None:
            break
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)

for label, name in [("My time", "10-my-time"), ("Activity", "11-activity"), ("You", "12-you"), ("Appearance", "13-appearance")]:
    if tap(label):
        snapshot(name)

print(f"\nScreenshots: {out_dir}")
if failures:
    print(f"{len(failures)} step(s) failed: {', '.join(failures)}")
    sys.exit(1)
print("All steps passed")
