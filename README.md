# Deluge Launcher

A minimal, list-based Android home screen — a hard fork of
[Victoria Launcher](https://github.com/adelmonte/victoria-launcher) by
[adelmonte](https://github.com/adelmonte), itself an open source alternative to
[Niagara Launcher](https://niagaralauncher.app).

The home screen holds a short list of favorites and nothing you did not put there.
Everything else lives in an alphabetical app list you reach by swiping in from either
edge, with an A-Z strip that highlights every letter currently on screen.

## What this fork changes

- Rebranded as Deluge Launcher, with a blue Material 3 theme and its own icon.
- Ten translations plus Traditional Chinese, and a language picker in settings.
- The A-Z strip tracks the list while scrolling, and sits on the side the list is not
  aligned to.
- Faster cold start: icons are cached on disk, the icon set is hand-drawn rather than
  pulled from a library of thousands, and the navigation library is gone.
- Folder rows show how many apps they hold and whether they are open.
- Widget height follows the widget itself unless it is set by hand; dead widgets left
  over from a backup are cleaned up.
- Swipe-for-shortcuts only fires on a sideways swipe, so scrolling is never stolen.
- Set as default: the home screen offers to make Deluge your home app, dismissibly.
- Restoring a settings file re-pins the shortcuts it named.

See the in-app changelog (Settings → About → Changelog) for the release-by-release
list.

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
