// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.LanguagePrefs

/**
 * One selectable language.
 *
 * [tag] is the BCP-47 tag stored in the preference; [label] is the language's own name, not
 * translated, since a list of languages is the one screen where the name in the language you
 * cannot read yet is the point.
 */
private data class LanguageOption(val tag: String?, val label: String)

/** The eight languages kept in sync, in order, plus the system default. */
private val LANGUAGES = listOf(
    LanguageOption(null, "System default"),
    LanguageOption("en", endonym("en")),
    LanguageOption("zh-CN", endonym("zh-CN")),
    LanguageOption("es", endonym("es")),
    LanguageOption("fr", endonym("fr")),
    LanguageOption("ar", endonym("ar")),
    LanguageOption("pt-BR", endonym("pt-BR")),
    LanguageOption("ru", endonym("ru")),
    LanguageOption("ja", endonym("ja")),
)

/** A language's name in its own script. */
private fun endonym(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercase() }
}

/** The tag currently in force, or null while the system's own language is being used. */
fun currentLanguageTag(context: Context): String? = LanguagePrefs.read(context)

/**
 * Picks the launcher's own language.
 *
 * Choosing one recreates the Activity: the language is applied by wrapping every Context,
 * which happens before the first composition, so a screen already built has to be built
 * again to change what it says. That is the same restart Android's own per-app language
 * does, and why the choice is written before the call rather than after it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val selected = currentLanguageTag(context)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(LANGUAGES) { option ->
                val isSelected = when {
                    option.tag == null -> selected == null
                    selected == null -> false
                    else -> selected.startsWith(option.tag)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            LanguagePrefs.write(context, option.tag)
                            context.findActivity()?.recreate()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = null)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = option.tag?.let { LanguagePrefs.displayName(it) }
                            ?: stringResource(R.string.settings_language_system),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** The Activity behind a Context, which a Compose `LocalContext` is wrapped inside. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
