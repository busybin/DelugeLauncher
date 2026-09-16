# Translating

Translations live in `app/src/main/res/values-<code>/strings.xml`, alongside the
English originals in `app/src/main/res/values/strings.xml`.

## By pull request

Copy `values/strings.xml` to `values-<code>/strings.xml`, translate the text
between the tags, and open a pull request. Two things to watch:

- Keep every `name="..."` exactly as it is. That is what the code looks the
  string up by; the text between the tags is the only part to change.
- Keep the format markers — `%1$d`, `%1$s` — and in the same order. They are
  filled in at runtime with a number or a name.
- Write `&amp;` rather than a bare `&`, or the file is not valid XML and
  nothing will build.

A missing string falls back to English, so a partial translation is fine and a
translation that has fallen behind a release is not a problem.

## Via Weblate

`.weblate` in the repository root points [Weblate](https://weblate.org) at these
files. It is the configuration for a hosted project, not a service that runs
here — someone with an account has to add the repository at
<https://hosted.weblate.org> for it to do anything.
