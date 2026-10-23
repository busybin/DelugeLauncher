// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

/**
 * Takes the other-profile names out of a crash trace before it can be copied.
 *
 * A launcher hosts other apps' widgets, so a trace can name an app the user deliberately keeps
 * out of sight. Every row key for a second profile ends in `|u<serial>`, and a `ComponentInfo`
 * carries the package itself; both are replaced rather than guessed at. The predicate is handed
 * in so this stays testable, and so "another profile" means what the caller can actually see.
 *
 * Over-redaction is deliberate: a report missing a package is a worse report, a report naming a
 * private app is a different kind of problem.
 */
object CrashRedaction {
    /** `|u0` is the main profile and says nothing; any other serial does. */
    private val profileSuffix = Regex("""\|u(\d+)""")

    /** Dotted names in a trace: packages, classes, and framework symbols alike. */
    private val dottedName = Regex("""[A-Za-z][A-Za-z0-9_$]*(?:\.[A-Za-z0-9_$]+)+""")

    /** A package and every class below it: `com.a`, `com.a.B`, `com.a.B.C`. */
    private fun prefixes(token: String): List<String> = buildList {
        var end = token.indexOf('.')
        while (end > 0) {
            add(token.substring(0, end))
            end = token.indexOf('.', end + 1)
        }
        add(token)
    }

    fun redact(trace: String, isOtherProfile: (String) -> Boolean): String {
        // A token is usually a class (`pkg.Class`), while the predicate knows packages. Any
        // prefix ending at a dot is therefore asked too, and the whole token goes when one of
        // them belongs to another profile — leaving the class name behind would name it just
        // as well as the package did.
        val answers = HashMap<String, Boolean>()
        fun foreign(name: String): Boolean = answers.getOrPut(name) { isOtherProfile(name) }
        val foreignTokens = dottedName.findAll(trace)
            .map { it.value }
            .toSet()
            .filter { token -> prefixes(token).any(::foreign) }
        var out = trace
        // Longest first: replacing a package that prefixes another would leave half of the
        // longer one behind, naming exactly what was meant to go.
        foreignTokens.sortedByDescending { it.length }.forEach { name ->
            out = out.replace(
                Regex("""(?<![\w.$])${Regex.escape(name)}(?![\w$])"""),
                "[private]",
            )
        }
        return profileSuffix.replace(out) { match ->
            if (match.groupValues[1] == "0") match.value else "|u[profile]"
        }
    }
}
