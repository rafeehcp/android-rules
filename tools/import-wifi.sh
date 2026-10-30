#!/usr/bin/env bash
# Copies the phone's saved Wi-Fi names into the app (Android won't let the app read them itself).
# Usage: tools/import-wifi.sh [adb-serial]
set -euo pipefail
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
S=${1:+-s $1}
ssids=$("$ADB" $S shell cmd wifi list-networks | tail -n +2 | sed -E 's/^[0-9]+ +//; s/ {2,}[^ ]+ *$//' | tr -d '\r' | sort -u)
existing=$("$ADB" $S exec-out run-as com.rafeeh.rules sh -c 'cat files/known_ssids.txt 2>/dev/null' || true)
printf '%s\n%s\n' "$existing" "$ssids" | sed '/^$/d' | awk '!seen[$0]++' \
  | "$ADB" $S exec-in run-as com.rafeeh.rules sh -c 'mkdir -p files && cat > files/known_ssids.txt'
echo "Imported $(echo "$ssids" | wc -l) saved networks."
