# Victoria Launcher

An open source alternative to [Niagara Launcher](https://niagaralauncher.app) — a
minimal, list-based Android home screen.

![Victoria Launcher](docs/banner.png)

## Install

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="70">](https://f-droid.org/packages/dev.victorialauncher/)

Or grab the APK from [Releases](../../releases). Both carry the same signature,
so you can move between them without uninstalling.

Once it is installed, pick Victoria Launcher under
**Settings → Apps → Default apps → Home app**.

Requires Android 8.0 (API 26) or newer.

## Build

You need JDK 17 and an Android SDK with platform 35.

```sh
./gradlew assembleDebug
```

See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for the full build and contribution
notes, and [ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the code fits together.

## Licence

[GPL-3.0-or-later](LICENSE)
