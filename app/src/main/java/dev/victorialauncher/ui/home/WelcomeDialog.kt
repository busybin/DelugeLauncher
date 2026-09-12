// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.victorialauncher.R

/**
 * Shown once, on a genuinely new install.
 *
 * A blank list-based home screen gives no hint that a long press or the settings exist, and
 * the three things that make it yours are all invisible until somebody tells you about them.
 * Deliberately a dialog over the home screen rather than a destination: the HOME key unwinds
 * the navigation stack, which would dismiss a screen the moment it appeared.
 */
@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.welcome_title)) },
        text = {
            Column {
                Text(stringResource(R.string.welcome_favorites))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.welcome_widget))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.welcome_layout))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.welcome_got_it)) }
        },
    )
}
