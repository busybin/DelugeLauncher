// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.delugelauncher.data.DailyOpens
import dev.delugelauncher.data.RuleContext
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

/**
 * Watches what the favorite rules ask about: the network, the headphones and the clock.
 *
 * Kept as a composable rather than a service: rules only decide what the home screen draws, so
 * they only have to be right while it is on screen. Nothing here is written down.
 */
@Composable
fun rememberRuleContext(dailyOpens: DailyOpens): RuleContext {
    val context = LocalContext.current
    var wifiSsid by remember { mutableStateOf(readWifiSsid(context)) }
    var headset by remember { mutableStateOf(hasHeadset(context)) }
    var minute by remember { mutableIntStateOf(minuteOfDay()) }

    DisposableEffect(context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        val network = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifiSsid = readWifiSsid(context)
            }

            override fun onLost(network: Network) {
                wifiSsid = readWifiSsid(context)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: android.net.NetworkCapabilities,
            ) {
                wifiSsid = readWifiSsid(context)
            }
        }
        val devices = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                headset = hasHeadset(context)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                headset = hasHeadset(context)
            }
        }
        // Both registrations are refused on some devices for reasons of their own — a network
        // callback needs a permission this app does have, but an odd ROM can still say no —
        // and a rule reading a stale value is better than the home screen crashing.
        runCatching { connectivity?.registerDefaultNetworkCallback(network) }
        runCatching {
            audio?.registerAudioDeviceCallback(devices, Handler(Looper.getMainLooper()))
        }
        onDispose {
            runCatching { connectivity?.unregisterNetworkCallback(network) }
            runCatching { audio?.unregisterAudioDeviceCallback(devices) }
        }
    }

    // The clock is the one input no callback reports. Half a minute is enough for a rule that
    // turns the screen over on the hour, and the SSID is re-read with it so a network that
    // changed without a callback does not stay wrong for long.
    LaunchedEffect(context) {
        while (true) {
            delay(30_000)
            minute = minuteOfDay()
            wifiSsid = readWifiSsid(context)
            headset = hasHeadset(context)
        }
    }

    val today = dayKey()
    return RuleContext(
        wifiSsid = wifiSsid,
        headset = headset,
        minuteOfDay = minute,
        opens = if (dailyOpens.day == today) dailyOpens.counts else emptyMap(),
    )
}

/** The connected network's name, or null without the location permission or off Wi-Fi. */
internal fun readWifiSsid(context: Context): String? {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!granted) return null
    val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
    val raw = runCatching { wifi.connectionInfo }.getOrNull() ?: return null
    @Suppress("DEPRECATION")
    val ssid = raw.ssid?.trim('"')
    return ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
}

/** Whether anything is plugged into, or paired with, the phone's audio output. */
private fun hasHeadset(context: Context): Boolean {
    val audio = context.getSystemService(AudioManager::class.java) ?: return false
    return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
        when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
            -> true

            else -> false
        }
    }
}

private fun minuteOfDay(): Int {
    val now = Calendar.getInstance()
    return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
}

internal fun dayKey(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(java.util.Date())
