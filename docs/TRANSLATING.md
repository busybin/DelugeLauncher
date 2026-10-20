# Translating

Strings live in `app/src/main/res/values/strings.xml`; translations are the same file under
`values-<code>/`.

Copy `values/strings.xml` to `values-<code>/strings.xml`, translate the text between the tags and
open a pull request. Keep `name="..."` untouched, keep format markers (`%1$d`, `%1$s`) in the
same order, and write `&amp;` instead of a bare `&`.

A missing string falls back to English, so partial translations are fine.

`.weblate` in the repository root configures a hosted Weblate project; it does nothing until
someone with an account adds the repository at <https://hosted.weblate.org>.
