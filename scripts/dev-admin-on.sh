#!/usr/bin/env bash
# Turns on uninstall protection (device admin) for NoXXX Dev over adb.
# On Samsung, removing the admin with adb (dev-uninstall.sh) leaves a stale Knox record, after which
# the Activate button in Settings fails silently with "Admin is already added". This skips that screen.
set -euo pipefail
PKG=${PKG:-com.noxxx.guard.debug}
adb shell dpm set-active-admin --user 0 "$PKG/com.noxxx.guard.admin.GuardAdminReceiver"
