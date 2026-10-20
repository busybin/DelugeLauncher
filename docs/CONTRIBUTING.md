# Contributing

## Building

JDK 17 and an Android SDK with platform 37.

```sh
export ANDROID_HOME=/path/to/android-sdk   # or sdk.dir=... in local.properties
./gradlew assembleDebug                    # app/build/outputs/apk/debug/
./gradlew test
./gradlew lint
```

`assembleRelease` produces an unsigned APK unless `keystore.properties` exists in the project
root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Both the file and the keystore are
gitignored.

## Code style

`kotlin.code.style=official`, and match the surrounding code instead of reformatting files you
only partly touch. Comments explain why, not what — if you change something from
`docs/ARCHITECTURE.md`, update it there too.

## License

Contributions are GPL-3.0-or-later. New source files carry the same SPDX header as the rest.
