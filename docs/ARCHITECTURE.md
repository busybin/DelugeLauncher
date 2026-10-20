# Architecture

Single Android module: Kotlin, Jetpack Compose, `minSdk 26` (versions are the `baseline.*` keys
in `gradle.properties`). No analytics, no ads, no accounts.

## Layout

```
data/       PackageManager queries, preferences (DataStore), folders, pinyin, freezing
ui/         home, A-Z app list, settings, theme; DelugeNavHost hosts the graph
media/      notification listener and Now Playing card
service/    haptics, status-bar fader, accessibility, automatic freezing
widget/     AppWidgetHost, plain widgets, clock widgets
```

## Non-obvious constraints

- The launcher does not draw the wallpaper: `windowShowWallpaper` lets the system one show
  through a transparent window, so "dim wallpaper" is a scrim `Box`.
- `LongPressFrameLayout` wraps every embedded widget. A Compose long-press detector would swallow
  the touch stream and break the widget's own buttons.
- The A-Z strip bows by translating each letter along a gaussian; scaling the glyphs pixelated
  them. Scrub position arrives as lambdas read inside `graphicsLayer` to keep the work in the
  draw phase.
- `recordTouchPosition` does not consume the down event, so `combinedClickable` and scrolling
  parents still arbitrate normally.
- The app-list overlay stays composed while hidden (measured, never placed) — rebuilding it on
  open made it take a beat to appear. An `AppWidgetHostView` that is never placed loses its
  layout, so the home screen behind it stays laid out too.
- Gesture exclusion covers the scrub band only; Android honors 200dp per side.

## Permissions

| Permission | Why | Optional |
|---|---|---|
| `INTERNET` | Reads the release page for the update check. Nothing is sent. | Yes |
| `QUERY_ALL_PACKAGES` | A launcher must enumerate apps, icon packs and widget providers. | No |
| `VIBRATE` | Haptic tick while scrubbing letters. | No |
| Accessibility service | Opens the shade and locks the screen; ignores every event. | Yes |
| Notification listener | Reads the media session for Now Playing. | Yes |

`BIND_APPWIDGET` is not requested: it is signature-level, and `AppWidgetHost` works without it
for the current home app.

## Known gaps

- No baseline profile.
- Unit tests cover framework-free logic only; anything touching `PackageManager` or Compose needs
  Robolectric or an instrumented test, neither of which is set up yet.
