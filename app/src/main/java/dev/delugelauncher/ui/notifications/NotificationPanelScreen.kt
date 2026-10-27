// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.notifications

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.media.NotificationAction
import dev.delugelauncher.media.NotificationEntry
import dev.delugelauncher.media.NotificationGateway
import dev.delugelauncher.ui.common.AppIcon

/**
 * The shade, drawn by the launcher itself.
 *
 * Not a replacement for the system's: it exists so a swipe down on the home screen lands on
 * something with the launcher's own feel, and so the text that a minimal home screen has no
 * room for is one gesture away. Anything the system shade does beyond showing, tapping,
 * dismissing and replying is out of scope on purpose.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationPanelScreen(
    entries: List<NotificationEntry>,
    /** Installed apps by `package|u<serial>`, for the icon and name over each group. */
    appsByAppKey: Map<String, AppInfo>,
    accessGranted: Boolean,
    /** Whether the setting that keeps notifications for this screen is on. */
    panelEnabled: Boolean,
    /**
     * Opens a notification, with the app it came from when one is installed. The caller
     * checks the app lock before either the notification's own intent or the app row runs.
     */
    onOpenNotification: (NotificationEntry, AppInfo?) -> Unit,
    onOpenSettings: () -> Unit,
    /** Whether a gesture that opens the panel is available at all, for the empty-state hint. */
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val surface = MaterialTheme.colorScheme.surface
    // Grouped in the order of each group's newest notification, so the app that just spoke is
    // on top rather than wherever its package name happens to sort.
    val groups = remember(entries) {
        entries.groupBy { it.appKey }
            .toList()
            .sortedByDescending { (_, list) -> list.maxOf { it.postTime } }
    }
    var replyingTo by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (entries.any { it.canDismiss }) {
                        IconButton(onClick = { NotificationGateway.dismissAll() }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.notifications_clear_all),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.padding(padding),
        ) {
            if (!panelEnabled) {
                item(key = "panel-off") {
                    PanelMessage(
                        title = stringResource(R.string.notifications_panel_off),
                        detail = stringResource(R.string.notifications_panel_off_detail),
                        action = null,
                        onAction = {},
                    )
                }
            } else if (!accessGranted) {
                item(key = "no-access") {
                    PanelMessage(
                        title = stringResource(R.string.notifications_access_missing),
                        detail = stringResource(R.string.notifications_access_detail),
                        action = stringResource(R.string.settings_open_settings),
                        onAction = onOpenSettings,
                    )
                }
            } else if (entries.isEmpty()) {
                item(key = "empty") {
                    PanelMessage(
                        title = stringResource(R.string.notifications_empty),
                        detail = stringResource(R.string.notifications_empty_detail),
                        action = null,
                        onAction = {},
                    )
                }
            }

            groups.forEach { (appKey, list) ->
                item(key = "group:$appKey") {
                    val app = appsByAppKey[appKey]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (app != null) {
                            AppIcon(app = app, sizeDp = 22)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = app?.label ?: list.first().packageName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (list.size > 1) {
                            Text(
                                text = list.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
                items(list, key = { "n:" + it.notificationKey }) { entry ->
                    NotificationCard(
                        entry = entry,
                        replying = replyingTo == entry.notificationKey,
                        draft = draft,
                        onDraftChange = { draft = it },
                        onStartReply = {
                            replyingTo = if (replyingTo == entry.notificationKey) null else entry.notificationKey
                            draft = ""
                        },
                        onSendReply = { action ->
                            NotificationGateway.send(context, action, draft)
                            replyingTo = null
                            draft = ""
                        },
                        onAction = { action -> NotificationGateway.send(context, action) },
                        onOpen = { onOpenNotification(entry, appsByAppKey[appKey]) },
                        onDismiss = {
                            NotificationGateway.dismiss(entry.notificationKey)
                            if (replyingTo == entry.notificationKey) replyingTo = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelMessage(
    title: String,
    detail: String,
    action: String?,
    onAction: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        if (action != null) {
            Spacer(Modifier.size(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationCard(
    entry: NotificationEntry,
    replying: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onStartReply: () -> Unit,
    onSendReply: (NotificationAction) -> Unit,
    onAction: (NotificationAction) -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val replied = entry.actions.firstOrNull { it.replyKey != null }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (entry.title.isNotBlank()) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.text.isNotBlank() && entry.text != entry.title) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        entry.postTime,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = dim,
                )
            }
            if (entry.canDismiss) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.notifications_dismiss),
                        modifier = Modifier.size(18.dp),
                        tint = dim,
                    )
                }
            } else {
                Spacer(Modifier.width(36.dp))
            }
        }

        if (entry.actions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entry.actions.forEach { action ->
                    if (action.replyKey != null) {
                        TextButton(onClick = onStartReply) { Text(action.title) }
                    } else {
                        TextButton(onClick = { onAction(action) }) { Text(action.title) }
                    }
                }
            }
        }

        if (replying && replied != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.notifications_reply_hint)) },
                    maxLines = 3,
                )
                IconButton(
                    onClick = { onSendReply(replied) },
                    enabled = draft.isNotBlank(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.notifications_send),
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}
