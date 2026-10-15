# NoXXX

An Android app that blocks adult content on your own phone. It doesn't need a VPN, so it works
alongside a VPN you already use.

| Layer | How it works |
|---|---|
| **Websites** | Android's Private DNS is pointed at a family filter (Cloudflare for Families by default), which filters every app. CleanBrowsing Family also forces SafeSearch, but its servers can fail to load normal sites such as Canvas and Okta logins. On top of that, supported browsers' address bars are checked against a bundled list of 76,798 domains, plus your own sites and words. |
| **Searches** | A built-in list of blocked words and phrases (plus your own) is checked against browser address bars and search boxes in other apps. Matching is whole-word only, so a blocked word inside a longer, normal word never triggers. A blocked search is cleared. |
| **Images** | On Android 11+, a shrunken copy of the screen is classified on-device about every 1.5 s by a MobileNetV2 model. If it detects explicit content, the screen is covered and the app is sent back. Nothing is saved or uploaded. |
| **Apps** | Pick any apps to block. Browsers NoXXX can't monitor, and private/incognito tabs, can also be blocked. |
| **Settings lock** | Once protection starts, Home, stats and history stay open, and adding protection always works. Loosening anything (turning a feature off, unblocking, uninstalling) needs an unlock: request it, wait out the cool-off (1 hour to 7 days, 24 h by default), then open a 15-minute edit window. Settings relock by themselves. |
| **Lockdown** | Freezes every setting and pauses unlock requests for 1–90 days. It can be extended but not shortened. |
| **Partner** | Optional, and only during setup. An accountability partner's password opens an unlock immediately and ends lockdown. Once protection starts, a partner can't be added (not even in an unlock window), so you can't give yourself one later to skip the cool-off. |
| **Tamper protection** | While settings are locked, only screens that can switch NoXXX off are closed: its own Accessibility page, its App info (Force stop, Uninstall, Clear storage), "Stop NoXXX?" dialogs, device-admin deactivation, the uninstall prompt and the Private DNS dialog. Lists that just include NoXXX (the Accessibility list, app lists, battery and notification lists) stay usable. A locked screen is left with Back plus a short message, so you stay in Settings where you were. Device admin blocks a plain uninstall. |
| **Stats** | Current and best streak, blocks today / this week / all time, a 7-day chart and a breakdown by type. |
| **Extras** | Block history, optional app PIN. |
| **Look** | Material You (Material 3): colors follow your wallpaper on Android 12+, light/dark follows the system, themed launcher icon. |

## Build

1. Open this folder in **Android Studio** (Ladybug or newer) and let Gradle sync.
2. Use **Run ▶** with your phone connected, or **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   - The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
   - The release build is signed with the debug key so it can be sideloaded without extra setup.
3. Unit tests: `./gradlew testDebugUnitTest`

Toolchain: AGP 8.7.3, Gradle 8.11.1, Kotlin 2.1.0, compileSdk/targetSdk 35, minSdk 28 (Android 9).
Image detection needs Android 11 (API 30) or newer.

## Phone setup

The first time NoXXX opens, a **setup guide** walks through everything one step at a time:
choosing your protections, each permission, apps to block, your cool-off and an optional partner
password. Until you tap **Start protection**, the app is in setup mode: every change applies instantly,
nothing is locked, and you can reopen the guide from the checklist icon on the Home tab. After that the
guide is gone; anything that gets switched off can be fixed from the Setup card on Home.

The guide covers:

1. **Accessibility service**: turn on *NoXXX*.
   On Android 13+, a sideloaded app's switch may be greyed out. Open *App info → ⋮ → Allow restricted settings*, then try again.
2. **Private DNS**: *Settings → Network & internet → Private DNS → Private DNS provider hostname* →
   `family.cloudflare-dns.com` (or another provider listed on the Websites tab).
3. **Auto-repair (optional)**: from a computer, run
   `adb shell pm grant com.noxxx.guard android.permission.WRITE_SECURE_SETTINGS`.
   NoXXX can then switch Private DNS back on by itself if it gets changed.
4. **Uninstall protection**: turn on device admin.
5. **Battery**: let NoXXX ignore battery optimization so it isn't stopped in the background.
6. Set up the Websites, Apps, Images and Lock tabs, then tap **Start protection**.

### Using your own VPN

Private DNS and a VPN usually coexist, but some VPN apps send DNS through their own servers instead.
With your VPN connected, check the Home tab:

- **"Private DNS: family.cloudflare-dns.com" shown**: you're covered.
- **Not filtering**: either set your VPN app's custom DNS to a family filter (Cloudflare for Families
  `1.1.1.3` / `1.0.0.3`, or CleanBrowsing Family `185.228.168.168` / `185.228.169.168`), or turn off
  *Require Private DNS* on the Websites tab. Address-bar checking and image detection keep working
  either way. Do this during setup mode so it doesn't have to wait out the cool-off.

## How word blocking avoids false positives

- Text is decoded (`+`, `%20`), lowercased and split into words. A term matches only as a whole word,
  or as a whole phrase in order. Simple plurals count; parts of words never do.
- Number-for-letter spellings are undone only inside words that mix letters and digits.
- The built-in list (`app/src/main/assets/keywords.txt`) is generated by `scripts/update-keywords.py`.
  It keeps only entries tagged sexual with moderate-or-stronger severity, then drops any single word
  that's among the 10,000 most common ordinary English words, and any word or phrase that appears
  anywhere in a large body of ordinary text (Linux documentation, about 62 million words).
- The list is never displayed in the app. If something normal still gets blocked, add it under
  **Websites → Allowed words**.
- Search boxes in other apps are recognised by their id, hint or type ("search", "query" and so on).
  Message boxes and password fields are skipped.

To rebuild the list: `python3 scripts/update-keywords.py --fetch --exclude-corpus /usr/share/doc`

## How the settings lock works

- **Always allowed:** viewing Home and stats, blocking more sites/apps/words, turning protection on,
  raising sensitivity, lengthening the cool-off, starting a lockdown.
- **Needs an unlock:** anything that loosens protection. Those controls show a lock icon.
- **Unlocking:** tap *Request unlock* (Home, any settings tab, or the Lock tab). After the cool-off it
  becomes *ready* for 24 hours; open it to get **15 minutes** where everything can be changed,
  including system settings for NoXXX. Then it locks again. You can cancel or relock early.

The timers resist tricks:

- Time is counted with Android's monotonic clock, so changing the date/time doesn't speed it up.
- Time while the phone is switched off is only credited once it can be checked against HTTPS
  `Date` headers on both sides of the reboot. Setting the clock forward and rebooting gains nothing.

## Removing NoXXX

1. Request an unlock and wait out the cool-off.
2. Open the unlock. For the next 15 minutes, system settings aren't guarded.
3. *Settings → Security → Device admin apps* → turn off NoXXX.
4. Uninstall.

## Limits (be honest with yourself about these)

- **Safe mode, factory reset, or adb** can get around any non-root app. A device-owner build would
  be needed to close those gaps.
- **Force-stopping NoXXX turns its Accessibility service off** (Android does this for every app).
  That's why tamper protection closes App info and Samsung's Device care. If it ever happens,
  turn the service back on from the Home tab.
- Tamper protection recognises dangerous screens by their title and English button labels (Force stop,
  Uninstall, Clear storage, Deactivate, Stop, Turn off). On a phone set to another language, App info may
  not be recognised; NoXXX's own Accessibility page and the uninstall prompt still are.
- Image detection is a statistical model, so expect occasional misses and false alarms. Adjust the
  sensitivity, or skip apps like games to save battery.
- Apps that block screenshots (FLAG_SECURE) can't be scanned. In a browser this is treated as a
  private tab: a fully black browser screen for about 4.5 s is blocked.
- Browser updates can rename address-bar views. Private DNS still covers browsers when that happens.

## Privacy

Everything runs on the phone. The only network requests NoXXX makes are HEAD requests to
google.com / cloudflare.com, to read the time. DNS goes to whichever Private DNS provider you chose.

## Project layout

```
app/src/main/java/com/noxxx/guard/
  policy/    Policy, cool-off/lockdown engine, tamper-resistant clock, password hashing (pure Kotlin, unit-tested)
  data/      GuardStore (persisted state), BlockLog, NetworkTime
  web/       DomainBlocklist (hashed, ~640 KB RAM), UrlChecker, PrivateDns status/enforcement
  vision/    ImageClassifier (LiteRT), ScreenScanner (accessibility screenshots, tiling)
  monitor/   GuardAccessibilityService, BlockOverlay, BrowserRegistry, TamperGuard
  admin/     Device admin receiver        boot/  Boot receiver
  ui/        Compose UI: Home, Websites, Apps, Images, Cool-off
app/src/main/assets/
  blocklist.txt              domain list (refresh with scripts/update-blocklist.sh)
  nsfw_mobilenet_v2.tflite   image model
```

## Credits

- Domain list: [StevenBlack/hosts](https://github.com/StevenBlack/hosts) adult-content extension (MIT)
- Word list: [dsojevic/profanity-list](https://github.com/dsojevic/profanity-list) (MIT), filtered as described above; [google-10000-english](https://github.com/first20hours/google-10000-english) is used at build time only
- Image model: [GantMan/nsfw_model](https://github.com/GantMan/nsfw_model) MobileNetV2 224 (MIT)
- DNS filtering: [CleanBrowsing](https://cleanbrowsing.org) and [Cloudflare for Families](https://one.one.one.one/family/)
