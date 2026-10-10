// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/**
 * The launcher's own navigation, in place of `navigation-compose`.
 *
 * What this app needs of a navigator is a stack of at most a few screens, one of which is
 * always "home", and string routes so a call site can say where it wants to go. The
 * navigation library brings a graph, deep links, a type-safe argument system and an
 * activity integration for exactly that: it is a few thousand classes on the classpath
 * and a slice of every cold start, for a back stack that is a list.
 *
 * Used through the same shape the library was, on purpose: `composable("route") { … }`,
 * `navigate("route")`, `popBackStack()`. The screens were written against that shape, and
 * a hand-rolled navigator is not a reason to rewrite them.
 */
@Stable
class Router(startRoute: String = "home") {

    var stack by mutableStateOf(listOf(startRoute))
        private set

    /**
     * Replaces the stack wholesale, for restoring it after the Activity is rebuilt.
     *
     * The language picker is the reason this exists: applying a language wraps every
     * Context, so the Activity has to be recreated, and a stack kept only in composition
     * would drop whoever was three screens deep back onto the home screen.
     */
    fun restore(routes: List<String>) {
        stack = routes.ifEmpty { listOf("home") }
    }

    /** The route being shown. */
    val top: String get() = stack.last()

    /**
     * Where the app is, in the shape callers already read.
     *
     * Only the route is ever asked for — "are we on home" — so this is a route and nothing
     * else rather than an argument bundle nobody looks at.
     */
    val currentDestination: Destination get() = Destination(top)

    fun navigate(route: String) {
        // Going where we already are is a no-op rather than a second copy on the stack, which
        // is what the library did with launchMode single-top and what a double tap expects.
        if (top == route) return
        stack = stack + route
    }

    /** Pops one screen. False from the last one, where there is nothing to go back to. */
    fun popBackStack(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        return true
    }

    /** Pops back to [route], leaving it in place unless [inclusive]. */
    fun popBackStack(route: String, inclusive: Boolean): Boolean {
        val at = stack.indexOfLast { it == route }
        if (at < 0) return false
        val kept = if (inclusive) stack.take(at) else stack.take(at + 1)
        stack = kept.ifEmpty { listOf("home") }
        return true
    }
}

class Destination(val route: String)

/**
 * Saves the stack across the Activity being rebuilt — a language change, a rotation, a
 * theme change — so the screen that was open is still open afterwards.
 */
val RouterSaver: Saver<Router, ArrayList<String>> = Saver(
    save = { ArrayList(it.stack) },
    restore = { saved -> Router().also { it.restore(saved) } },
)

/** What a `{placeholder}` route hands its screen, shaped like the library's entry. */
class NavBackStackEntry(val arguments: NavArguments)

class NavArguments(private val values: Map<String, String>) {
    fun getString(key: String): String? = values[key]
}

/**
 * The routes a [RouterHost] knows, registered afresh on each composition.
 *
 * Registration is a handful of lambdas, so rebuilding it is cheaper than remembering it and
 * working out when it is stale.
 */
class RouterScope {
    private val routes = mutableMapOf<String, @Composable (NavBackStackEntry) -> Unit>()

    fun composable(route: String, content: @Composable () -> Unit) {
        routes[route] = { content() }
    }

    /**
     * A route with `{placeholders}`, named apart from [composable] rather than overloaded
     * on it: an argument-less lambda and one that ignores its argument are the same lambda,
     * so two overloads make every plain call ambiguous.
     */
    fun composableArgs(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
        routes[route] = content
    }

    /** The block for [route], with any `{placeholders}` filled in, or null when unknown. */
    fun resolve(route: String): Pair<NavBackStackEntry, @Composable (NavBackStackEntry) -> Unit>? {
        routes[route]?.let { return NavBackStackEntry(NavArguments(emptyMap())) to it }
        val wanted = route.split('/')
        for ((pattern, block) in routes) {
            val parts = pattern.split('/')
            if (parts.size != wanted.size) continue
            val args = mutableMapOf<String, String>()
            var matched = true
            for (i in parts.indices) {
                val part = parts[i]
                if (part.startsWith("{") && part.endsWith("}")) {
                    args[part.substring(1, part.length - 1)] = wanted[i]
                } else if (part != wanted[i]) {
                    matched = false
                    break
                }
            }
            if (matched) return NavBackStackEntry(NavArguments(args)) to block
        }
        return null
    }
}

/**
 * Draws whatever the router is pointed at. Deliberately without a transition: these are
 * screens of a launcher, opened by a tap that has already happened, and a cross-fade
 * between them reads as lag rather than as polish.
 */
@Composable
fun RouterHost(router: Router, content: @Composable RouterScope.() -> Unit) {
    val scope = RouterScope()
    scope.content()
    val resolved = scope.resolve(router.top) ?: return
    resolved.second(resolved.first)
}
