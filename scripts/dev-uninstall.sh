#!/usr/bin/env bash
# Removes NoXXX Dev (com.noxxx.guard.debug) from a USB-connected phone, skipping the in-app wait.
# Works only for dev builds: Android lets adb remove the device admin of test-only apps.
# On Samsung this leaves a stale Knox record; after reinstalling, turn protection on with dev-admin-on.sh.
set -euo pipefail
PKG=com.noxxx.guard.debug
adb shell dpm remove-active-admin "$PKG/com.noxxx.guard.admin.GuardAdminReceiver" >/dev/null 2>&1 || true
adb uninstall "$PKG"
