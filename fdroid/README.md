# Distribution (F-Droid / IzzyOnDroid / GitHub Releases)

All channels must use the same signing key, or switching channels forces a reinstall.

| Channel | Built by | Signed by |
|---|---|---|
| GitHub Releases | us (Gradle) | us (`app/keys_release.jks`) |
| IzzyOnDroid | us, APK attached to the tag's release | us |
| F-Droid main repo | F-Droid | F-Droid, unless reproducible builds are set up |

## IzzyOnDroid (recommended)

It takes developer-built, developer-signed APKs and lists them within 24 hours. Per release:

1. Tag is plain digits (= `versionCode`).
2. Release carries the signed APK.
3. `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` describes the update.
4. Ask for a listing at <https://codeberg.org/IzzyOnDroid/repodata/issues>.

## F-Droid main repo (optional)

[`dev.delugelauncher.yml`](./dev.delugelauncher.yml) is the submission draft: copy it to
`metadata/dev.delugelauncher.yml` in fdroiddata, fill in the release and open an MR. F-Droid
re-signs with its own key, so its builds cannot upgrade over the GitHub/Izzy ones.

## Anti-features

None. The update check is off by default and the app needs no network service.

## Signing

- `app/keys_release.jks` and `keystore.properties` are not committed.
- Certificate SHA-256 (reference only; reproducible builds are not set up):
  `342e3ca62956b4f11f35cbd5cb742bc39644be97c3bd6a335d9f75a38fb5e8c0`
- Alias `deluge`, RSA 4096, valid until 2054-03-05.
- Keep this key: a new one cannot upgrade over the installed app.
- Without `keystore.properties` the release APK is unsigned (Gradle prints a warning); never
  publish it.
