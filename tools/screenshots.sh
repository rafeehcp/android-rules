#!/usr/bin/env bash
# Captures README screenshots from a demo copy of the app (com.rafeeh.rules.demo) with sample rules,
# so no real rules, networks or locations appear; crops the status bar so notifications don't show.
# The phone must be unlocked with the screen on. Needs Python with Pillow.
# Usage: tools/screenshots.sh [adb-serial]
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.."
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
A=("$ADB" ${1:+-s "$1"})
PKG=com.rafeeh.rules.demo
OUT=docs/screenshots

shot() { sleep "${2:-2}"; "${A[@]}" exec-out screencap -p > "$OUT/$1.png"; echo "saved $OUT/$1.png"; }
tap_text() {
  "${A[@]}" shell uiautomator dump /sdcard/ui.xml >/dev/null
  read -r x y < <("${A[@]}" exec-out cat /sdcard/ui.xml | python -c "
import re,sys
for m in re.finditer(r'<node [^>]*?text=\"([^\"]*)\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', sys.stdin.read()):
    if m.group(1) == sys.argv[1]:
        x1,y1,x2,y2 = map(int, m.groups()[1:]); print((x1+x2)//2, (y1+y2)//2); break
" "$1")
  [ -n "${x:-}" ] || { echo "no element with text: $1" >&2; exit 1; }
  "${A[@]}" shell input tap "$x" "$y"
}

./gradlew.bat -q assembleScreenshots
"${A[@]}" install -r app/build/outputs/apk/screenshots/app-screenshots.apk >/dev/null
"${A[@]}" shell pm clear "$PKG" >/dev/null
mkdir -p "$OUT"

"${A[@]}" shell am start -W -n "$PKG/com.rafeeh.rules.MainActivity" >/dev/null
shot setup

for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION POST_NOTIFICATIONS; do
  "${A[@]}" shell pm grant "$PKG" "android.permission.$p"
done
"${A[@]}" shell cmd notification allow_dnd "$PKG"
"${A[@]}" shell dumpsys deviceidle whitelist +"$PKG" >/dev/null
"${A[@]}" shell am force-stop "$PKG"

python - <<'PY' | "${A[@]}" exec-in run-as "$PKG" sh -c 'mkdir -p shared_prefs && cat > shared_prefs/rules.xml'
import json, html
rules = [
    {"id": "demo-1", "type": "place", "label": "LuLu Mall, Kochi", "lat": 10.0274822, "lng": 76.3079131, "radius": 250.0, "action": "VIBRATE", "enabled": True},
    {"id": "demo-2", "type": "place", "label": "Cochin International Airport", "lat": 10.154486, "lng": 76.3924889, "radius": 800.0, "action": "SILENT", "enabled": True},
    {"id": "demo-3", "type": "wifi", "ssid": "Office Wi-Fi", "action": "DND", "enabled": True},
    {"id": "demo-4", "type": "wifi", "ssid": "Home", "action": "RING", "enabled": False},
]
print("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
      f"    <string name=\"rules\">{html.escape(json.dumps(rules))}</string>\n</map>")
PY

"${A[@]}" shell am start -W -n "$PKG/com.rafeeh.rules.MainActivity" >/dev/null
shot rules

tap_text "LuLu Mall, Kochi"
shot edit-place 6
"${A[@]}" shell input keyevent KEYCODE_BACK

"${A[@]}" shell am force-stop "$PKG"
"${A[@]}" uninstall "$PKG" >/dev/null
python - "$OUT" <<'PY'
import sys
from PIL import Image
for n in ["setup", "rules", "edit-place"]:
    p = f"{sys.argv[1]}/{n}.png"
    im = Image.open(p).convert("RGB")
    w, h = im.size
    im = im.crop((0, 130, w, h - 60))
    im.resize((540, round(im.size[1] * 540 / w)), Image.LANCZOS).save(p, optimize=True)
PY
echo "done; demo app removed"
