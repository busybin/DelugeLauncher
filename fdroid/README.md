# Distribution

| Channel | Built by | Signed by | Notes |
|---|---|---|---|
| GitHub Releases | us (Gradle) | us (`app/keys_release.jks`) | main channel; the in-app update check points here |
| Self-hosted F-Droid repo | us (`fdroid update`) | APKs: us; index: repo key | same APK signature, so users can move over from GitHub without uninstalling |
| F-Droid main repo (optional) | F-Droid | F-Droid | re-signed, so it cannot upgrade over the other two; the inclusion policy asks for ≥3 months of public development |
| IzzyOnDroid | — | — | not used: its inclusion policy rejects LLM-generated code and the request template requires AI disclosure |
| Google Play | — | — | not used |

## Release preparation

1. Bump `versionCode` / `versionName` (same plain integer) and add
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
2. Build `assembleRelease` and check `apksigner verify --print-certs` against the fingerprint below.
3. Tag the commit with that integer; `.github/workflows/release.yml` builds, signs and attaches
   `deluge-launcher-<tag>.apk` to the GitHub release.
4. Update `fdroid/dev.delugelauncher.yml` (`Builds`, `CurrentVersion`, `CurrentVersionCode`) with the
   tag's full commit hash.
5. Re-run `fdroid update` for the self-hosted repo so the new version is in the index.

## Self-hosted F-Droid repo

One shared repository will host all four apps (Deluge, GitRehub, Leftover, IRL Noob) and
keeps every APK signature: only the index is signed with a separate repo key. Point the F-Droid client at the
repository URL and it can install and update any of them.

## F-Droid main repo (optional)

[`dev.delugelauncher.yml`](./dev.delugelauncher.yml) is the submission draft: copy it to
`metadata/dev.delugelauncher.yml` in fdroiddata, fill in the release and open an MR. F-Droid
re-signs with its own key, so its builds cannot upgrade over the GitHub/self-hosted ones.

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
