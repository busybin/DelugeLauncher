// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R

/**
 * The look the access page established, so the freeze screens share it instead of inventing
 * a second one: 20dp gutters, surface-variant cards with the medium shape, an 8dp state dot,
 * a title-medium headline over a body-small summary, and hairline dividers at 40% outline.
 */

/** The green used for an affirmative mark, in both schemes. */
@Composable
fun positiveMarkColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF66BB6A) else Color(0xFF2E7D32)

/** The card surface every section sits on. */
@Composable
fun AccessSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Column { content() }
    }
}

/** A sentence card: one headline, the detail under it, then whatever the caller adds. */
@Composable
fun AccessStateCard(
    title: String,
    body: String,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 6.dp),
            )
            content?.invoke(this)
        }
    }
}

/**
 * One fold-out card: a state dot, the name, a one-line summary and the details behind a tap.
 * A null [active] hides the dot, for a card that describes rather than reports a channel.
 */
@Composable
fun ChannelCard(
    title: String,
    summary: String,
    active: Boolean? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    AccessSurface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active != null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            if (active) positiveMarkColor() else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Chevron(expanded)
        }
        if (action != null) {
            Box(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 4.dp)) { action() }
        }
        if (expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                content()
            }
        }
    }
}

/** A row inside a card that opens another screen. No dot: it reports nothing yet. */
@Composable
fun NavRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    divider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.rotate(-90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (divider) {
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
fun Chevron(expanded: Boolean) {
    Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = stringResource(
            if (expanded) R.string.access_collapse else R.string.access_expand
        ),
        modifier = Modifier.rotate(if (expanded) 180f else 0f),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SectionDivider() {
    HorizontalDivider(
        Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

/** A label on the left, a yes or no on the right. */
@Composable
fun StatusRow(label: String, ok: Boolean, known: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (!known) {
                stringResource(R.string.access_unknown)
            } else {
                stringResource(if (ok) R.string.access_yes else R.string.access_no)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (known && ok) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            },
        )
    }
}

/** A label on the left, a value on the right. */
@Composable
fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
fun Step(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}

@Composable
fun RowScope.HeaderCell(@StringRes label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(0.8f),
    )
}

@Composable
fun RowScope.Mark(ok: Boolean) {
    Box(Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
        Icon(
            if (ok) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = stringResource(
                if (ok) R.string.access_yes else R.string.access_no
            ),
            tint = if (ok) positiveMarkColor() else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
    }
}
