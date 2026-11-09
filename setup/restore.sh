#!/usr/bin/env bash
# Undoes this setup and hands Home back to Google TV. Everything was disabled, never uninstalled.
# Needs ADB open on the TV. From the repo root: TV_HOST=<tv ip> setup/restore.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address}"
TV="tools/venv/bin/python -I tools/tv.py"
$TV 'settings delete secure enabled_accessibility_services; settings put secure accessibility_enabled 0'
$TV 'settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop'
tools/restore.sh setup/lists/03-home-ambient.txt com.google.android.apps.tv.launcherx
tools/restore.sh setup/lists/02-preloaded.txt
tools/restore.sh setup/lists/01-telemetry.txt
echo "Done. Reboot the TV. To remove the launcher: $TV 'pm uninstall com.gathin.tvhome'"
