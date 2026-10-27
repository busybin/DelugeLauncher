# Deluge Launcher

<p align="center">
  <a href="https://buymeacoffee.com/busybin"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me a Coffee" height="56"></a>
</p>

A minimal, list-based Android launcher that can freeze apps.

Apps are frozen from the home screen instead of from a second app with a second list to keep in
sync: a sleeping app keeps its row, folder, favorite slot and tag, drawn grey, and a tap wakes
it and opens it.

A hard fork of [Victoria Launcher](https://github.com/adelmonte/victoria-launcher) by
[adelmonte](https://github.com/adelmonte), itself an open source alternative to
[Niagara Launcher](https://niagaralauncher.app).

This fork exists for freezing, then for speed and feel: fast cold start, a thumb-reachable A-Z
strip, gestures that hold up under a real finger.

## App freezing

- **The freeze list is the home screen.** No second list to maintain, no second place to look.
- **Frozen apps keep their place.** Folders, favorite slots, names and icons survive, including
  after Disable or Hide, so nothing has to be re-added.
- **Four methods**, one tab each, with what each one breaks stated next to it: Stop, Suspend,
  Disable, Hide.
- **Triggers:** freeze all, the corner button, a shortcut, or automatic freezing after the screen
  goes off — skipping the app in front, apps with notifications, and a phone on a charger.
  Unlocking thaws the list if you enable it.
- **A tap is a temporary thaw.** The row stays and the next trigger puts it back to sleep.
- **Fast by design.** One package-manager pass reads every app's state, a run only touches what
  is not already asleep, and through Shizuku each app is a single binder call — hiding included
  when the manager runs as root. A 170-app list settles in well under a second of IPC.
- **Root or Shizuku.** Shizuku is recommended and works without root (Android 11+, over wireless
  debugging). Hide needs root. The screen names what answered, with its version. Any manager that
  declares the Shizuku API is found, including Shizuku-Next and Shevery.
- **A paused app opened from elsewhere** gets an Unpause and open button in the system dialog
  instead of a dead end.
- **Widgets and pinned shortcuts** stay, greyed, and a tap unfreezes.
- **Protected apps** are never frozen while the guard is on (the default): the current keyboard,
  the home app, enabled accessibility services, Shizuku and every installed root manager.
- **Work profile and private space apps** can be frozen too, each profile tracked separately from
  the main one with the same package name.
- **More ways in and out:** a quick settings tile freezes or thaws the whole list, a listed app
  can pin its own toggle shortcut to the home screen, and the list can be exported to a file and
  imported on another phone.
- **System apps** are behind an off-by-default switch with a boot warning. Turning freezing off
  thaws everything first.
- **Limits:** hiding is not invisible to `MATCH_UNINSTALLED_PACKAGES`. The automatic trigger is a
  foreground service and comes back after a reboot.

The approach follows [Hail](https://github.com/aistra0528/Hail), credited in [NOTICE](NOTICE).

## Other changes in this fork

- Faster start: icons cached on disk, icon-pack and navigation libraries removed.
- A-Z strip follows the list, highlights the letters on screen, sits opposite the list alignment
  and within thumb reach.
- A row only answers a sideways swipe, so angled drags scroll instead of opening a shortcut menu.
- Apps open out of their icon and shrink back into it on a gesture back.
- Notification text under an app name in favorites, off by default: the app's newest three, each
  one opening its own notification, with a text size of its own. A dot when icons are hidden.
- Widgets can live inside a folder, above its apps, each with its own height.
- Japanese app names file under kana rows, kanji under a row of their own.
- The clock widget follows the launcher's font.
- Folders file under their own names in the app list, so one is reachable by scrolling the
  alphabet; a long press manages one, adds it to the home screen or deletes it.
- Favorite rules show or hide favorites by Wi-Fi, time of day, headphones or a daily open
  limit.
- Private space apps get a padlock row at the end of the list that opens the space in place,
  installs apps into it and reaches its settings; the freeze screen offers Android 15's stronger
  isolation when the phone has it.
- The A-Z strip's edge setting is the whole reach from the screen edge, so it leaves the icons alone.
- Pinyin search by initials or full spelling, and pinyin ordering for Chinese app names.
- Tags with scrubbing, and a Frequent tag that follows usage.
- Per-app lock, uninstall and hide from the long-press menu.
- Full-bleed lists, one or two columns, in either orientation.
- Corner button with more than ten actions; the favorites swipes offer the same list.
- Widget height follows the widget unless set by hand; frozen widgets are not cleaned away.
- Night dark theme, wallpaper blur, pinch-to-resize icons, reduced motion, custom night hours.
- Two-column settings with search over every row, localized changelog, Back to the section it
  came from.
- Settings → About → check for updates reads the release page. That read is the app's only
  network call and sends nothing.

## Install

Android 8.0 (API 26) or newer. Freezing needs root (Magisk, KernelSU or APatch) or
[Shizuku](https://shizuku.rikka.app/).

Pick Deluge Launcher under **Settings → Apps → Default apps → Home app**.

The home screen starts empty on purpose: long-press the wallpaper for favorites, widgets, edit
layout and settings; swipe in from either edge for the A-Z list; long-press any app to rename,
re-icon, tag, lock, freeze, hide, uninstall or file it into a folder.

Double-tap-to-lock on the A-Z strip needs the accessibility service; the toggle stays greyed
until restricted settings are allowed from App info. **Export settings** covers every setting,
the language and the pinned shortcuts; the freeze list and what is frozen stay on the device.
Work profiles are picked up automatically; a private space needs Android 15+ and Deluge as the
default home app.

## Build

JDK 17 and an Android SDK with platform 37 (versions are the `baseline.*` keys in
`gradle.properties`).

```sh
./gradlew assembleDebug
```

Release builds need your own signing key; see [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) for build
notes and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the code layout.

## Credits and license

Forked from **Victoria Launcher** by adelmonte (https://github.com/adelmonte/victoria-launcher),
whose architecture and code this builds on. The freezing model follows **Hail** by aistra0528;
the privilege layer is **Shizuku**. Both are named in [NOTICE](NOTICE).

GNU General Public License v3.0 or later — see [LICENSE](LICENSE).

Not affiliated with Victoria Launcher, Niagara Launcher or their authors; those names only say
where this came from and what it is an alternative to.
