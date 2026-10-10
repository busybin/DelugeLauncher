# Deluge Launcher

A minimal, list-based Android home screen — a hard fork of
[Victoria Launcher](https://github.com/adelmonte/victoria-launcher) by
[adelmonte](https://github.com/adelmonte), itself an open source alternative to
[Niagara Launcher](https://niagaralauncher.app).

The home screen holds a short list of favorites and nothing you did not put there.
Everything else lives in an alphabetical app list you reach by swiping in from either
edge, with an A-Z strip that highlights every letter currently on screen.

## What this fork changes

A few things differ from upstream, mostly where the original got in the way:

- A blue Material 3 theme and its own icon, instead of the original look.
- Faster cold starts: icons are cached on disk, and the icons library and the
  navigation library are both gone.
- The A-Z strip follows the list as you scroll, and sits on the side the list is
  not aligned to.
- Folder rows say how many apps they hold and whether they are open.
- Widget height follows the widget itself, unless you set it by hand.
- Swiping a row for its shortcuts only fires sideways, so scrolling is never
  stolen by a drag that started at an angle.
- Settings has a changelog, and a restored backup re-pins the shortcuts it names.

The in-app changelog (Settings → About → Changelog) has the release-by-release list.

## Install

Requires Android 8.0 (API 26) or newer.

Once it is installed, pick Deluge Launcher under **Settings → Apps → Default apps → Home
app**.

## Getting started

The home screen starts almost empty on purpose — everything on it is put there by you.

- **Long-press the wallpaper** for the menu: favorites, widgets, edit layout, settings.
- **Swipe in from either edge** for the A-Z list, then slide along the letters to jump to
  one. Tap the edge and let go to just open it.
- **Long-press any app**, on either screen, to rename it, change its icon, hide it, or
  file it in a folder.
- **Edit layout** gives you drag handles for the order and steppers for every gap, height
  and margin.

Everything else lives in Settings, which has a search box over the whole of it.

Two things are worth knowing. Double-tapping the A-Z strip to lock the screen needs an
accessibility service, and if that toggle is greyed out you have to allow restricted
settings from App info first — Android blocks it for anything installed outside a store.
And **Export settings** is how you keep your setup.

Work profiles are picked up automatically. A private space needs Android 15 or later and
Deluge set as your default home app.

## Build

You need JDK 17 and an Android SDK with platform 35.

```sh
./gradlew assembleDebug
```

Translations are welcome — see [TRANSLATING.md](docs/TRANSLATING.md).
[CONTRIBUTING.md](docs/CONTRIBUTING.md) has the full build notes, and
[ARCHITECTURE.md](docs/ARCHITECTURE.md) how the code fits together.

## Credits and license

Forked from **Victoria Launcher** by adelmonte
(https://github.com/adelmonte/victoria-launcher), which this project could not exist
without. The original architecture, most of the code and much of the explanatory
commentary are theirs.

Licensed under the **GNU General Public License v3.0 or later** — see [LICENSE](LICENSE).
As a modified version of the original work, the same license applies to it: the source is
here, complete, and the changes are recorded in the git history and in [NOTICE](NOTICE).

This project is **not affiliated with Victoria Launcher, Niagara Launcher, or their
authors**. "Victoria Launcher" and "Niagara Launcher" are named only to say where this
came from and what it is an alternative to.
