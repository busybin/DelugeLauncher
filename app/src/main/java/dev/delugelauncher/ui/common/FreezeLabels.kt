// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.delugelauncher.R
import dev.delugelauncher.data.FreezeChannel
import dev.delugelauncher.data.FreezeMethod

/** The one place a freeze method's name comes from, so a menu and a row never disagree. */
@StringRes
fun freezeMethodName(method: FreezeMethod): Int = when (method) {
    FreezeMethod.STOP -> R.string.freeze_method_stop
    FreezeMethod.SUSPEND -> R.string.freeze_method_suspend
    FreezeMethod.DISABLE -> R.string.freeze_method_disable
    FreezeMethod.HIDE -> R.string.freeze_method_hide
}

@StringRes
fun freezeChannelName(channel: FreezeChannel): Int = when (channel) {
    FreezeChannel.AUTO -> R.string.freeze_channel_auto
    FreezeChannel.SHIZUKU -> R.string.freeze_channel_shizuku
    FreezeChannel.ROOT -> R.string.freeze_channel_root
    FreezeChannel.OWNER -> R.string.freeze_channel_owner
}

@Composable
fun freezeMethodLabel(method: FreezeMethod): String = stringResource(freezeMethodName(method))

@Composable
fun freezeChannelLabel(channel: FreezeChannel): String = stringResource(freezeChannelName(channel))
