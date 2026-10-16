# Submitting NoXXX to F-Droid

The app repository already has what F-Droid asks for: a GPL-3.0 license, only open-source
dependencies, a command-line Gradle build, version tags (`v1.0.1` matches `versionName "1.0.1"`) and
store listing text and images under `fastlane/metadata/android/en-US/`.

To submit:

1. Create a GitLab account and fork https://gitlab.com/fdroid/fdroiddata.
2. In your fork, make a branch named `com.noxxx.guard` and copy `com.noxxx.guard.yml` from this folder
   to `metadata/com.noxxx.guard.yml`.
3. Commit with the message `New App: com.noxxx.guard`, push, and open a merge request. The fdroiddata CI
   builds the app; fix anything it reports and push again.
4. Answer reviewers' questions on the merge request.

Things reviewers will likely ask about:

- **NonFreeNet anti-feature:** the app contacts google.com and cloudflare.com for the time and relies on
  a third-party DNS filter. The draft already declares this.
- **Image model:** `app/src/main/assets/nsfw_mobilenet_v2.tflite` is a binary file. It comes from
  [GantMan/nsfw_model](https://github.com/GantMan/nsfw_model) (MIT), MobileNetV2 224.
- **Accessibility service and device admin:** used only as described in the app description.

For each new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, and push a matching `v<versionName>` tag.
F-Droid picks up new tags by itself.
