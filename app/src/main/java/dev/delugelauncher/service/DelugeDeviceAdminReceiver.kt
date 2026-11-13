// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * The admin component that lets Deluge act as the phone's device owner.
 *
 * Without a device owner the launcher still works through root or Shizuku; this component is
 * only a handle for the policy calls in [dev.delugelauncher.data.DeviceOwnerPolicy].
 */
class DelugeDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, DelugeDeviceAdminReceiver::class.java)
    }
}
