package com.rafeeh.rules

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/** Makes the OS-side registrations (geofences, Wi-Fi watcher) match the stored rules. Safe to call repeatedly. */
object Triggers {
    fun sync(context: Context) {
        val rules = RuleStore.load(context).filter { it.enabled }
        syncGeofences(context, rules)
        val wantsWifi = rules.any { it.trigger is Trigger.Wifi }
        if (wantsWifi) WifiWatchService.start(context) else WifiWatchService.stop(context)
        SoundEngine.refresh(context)
    }

    @SuppressLint("MissingPermission")
    private fun syncGeofences(context: Context, rules: List<Rule>) {
        val client = LocationServices.getGeofencingClient(context)
        val intent = geofenceIntent(context)
        client.removeGeofences(intent).addOnCompleteListener {
            val fences = rules.mapNotNull { rule ->
                val t = rule.trigger as? Trigger.Place ?: return@mapNotNull null
                Geofence.Builder()
                    .setRequestId(rule.id)
                    .setCircularRegion(t.lat, t.lng, t.radiusM)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            }
            if (fences.isEmpty() || !hasBackgroundLocation(context)) return@addOnCompleteListener
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT)
                .addGeofences(fences)
                .build()
            client.addGeofences(request, intent)
                .addOnFailureListener { Log.e("Rules", "addGeofences failed", it) }
        }
    }

    private fun geofenceIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, GeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    fun hasBackgroundLocation(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e("Rules", "Geofence error ${event.errorCode}")
            return
        }
        val ids = event.triggeringGeofences.orEmpty().map { it.requestId }
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> ids.forEach { SoundEngine.enter(context, it) }
            Geofence.GEOFENCE_TRANSITION_EXIT -> ids.forEach { SoundEngine.exit(context, it) }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Triggers.sync(context)
}
