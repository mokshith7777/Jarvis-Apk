package com.mokshith.jarvis.ai.context

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class DeviceStatusSnapshot(
    val localDate: String,
    val localTime: String,
    val timezone: String,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isOnline: Boolean,
    val networkType: String
)

class DeviceContextProvider(private val context: Context) {

    fun snapshot(): DeviceStatusSnapshot {
        val now = ZonedDateTime.now()
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val netType = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> if (online) "Connected" else "Offline"
        }

        return DeviceStatusSnapshot(
            localDate = now.format(DateTimeFormatter.ISO_LOCAL_DATE),
            localTime = now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            timezone = now.zone.id,
            batteryPercent = batteryPct,
            isCharging = charging,
            isOnline = online,
            networkType = netType
        )
    }
}
