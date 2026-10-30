package com.rafeeh.rules

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Android delivers Wi-Fi connect/disconnect only to a live callback, and SSIDs only to a
 * callback registered with FLAG_INCLUDE_LOCATION_INFO, so Wi-Fi rules need this foreground service.
 */
class WifiWatchService : Service() {
    private lateinit var connectivity: ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val ssid = (caps.transportInfo as? WifiInfo)?.ssid?.removeSurrounding("\"")
            if (ssid != null && ssid != WifiManager.UNKNOWN_SSID) apply(ssid)
        }

        override fun onLost(network: Network) = apply(null)
    }

    override fun onCreate() {
        super.onCreate()
        connectivity = getSystemService(ConnectivityManager::class.java)
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(request, callback, Handler(Looper.getMainLooper()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        connectivity.unregisterNetworkCallback(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun apply(ssid: String?) {
        RuleStore.load(this).filter { it.enabled && it.trigger is Trigger.Wifi }.forEach { rule ->
            if ((rule.trigger as Trigger.Wifi).ssid == ssid) SoundEngine.enter(this, rule.id)
            else SoundEngine.exit(this, rule.id)
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Wi-Fi rules", NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_rules)
            .setContentTitle("Watching Wi-Fi for rules")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "wifi"

        fun start(context: Context) = context.startForegroundService(Intent(context, WifiWatchService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, WifiWatchService::class.java))
    }
}
