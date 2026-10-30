package com.rafeeh.rules

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.json.JSONArray

/**
 * Rules enter and exit; the engine owns the phone's sound state. The first rule to activate
 * snapshots the user's own ringer/DND setting as the baseline, the most recently activated rule
 * wins while several overlap, and the baseline is restored once none are active.
 */
object SoundEngine {
    private const val PREFS = "engine"
    private const val TAG = "Rules"

    @Synchronized
    fun enter(context: Context, ruleId: String) {
        val active = loadActive(context)
        if (ruleId !in active) active += ruleId
        reconcile(context, active)
    }

    @Synchronized
    fun exit(context: Context, ruleId: String) {
        val active = loadActive(context)
        active -= ruleId
        reconcile(context, active)
    }

    @Synchronized
    fun refresh(context: Context) = reconcile(context, loadActive(context))

    @Synchronized
    fun activeIds(context: Context): List<String> = loadActive(context)

    private fun reconcile(context: Context, candidates: MutableList<String>) {
        val rules = RuleStore.load(context).filter { it.enabled }.associateBy { it.id }
        val active = candidates.filter { it in rules }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val audio = context.getSystemService(AudioManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)

        if (!notifications.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "Do Not Disturb access not granted; cannot change sound mode")
            saveActive(context, active)
            return
        }

        val hasBaseline = prefs.contains("ringer")
        if (active.isEmpty()) {
            if (hasBaseline) {
                notifications.setInterruptionFilter(prefs.getInt("filter", NotificationManager.INTERRUPTION_FILTER_ALL))
                audio.ringerMode = prefs.getInt("ringer", AudioManager.RINGER_MODE_NORMAL)
                prefs.edit().remove("ringer").remove("filter").apply()
            }
        } else {
            if (!hasBaseline) {
                prefs.edit()
                    .putInt("ringer", audio.ringerMode)
                    .putInt("filter", notifications.currentInterruptionFilter)
                    .apply()
            }
            when (rules.getValue(active.last()).action) {
                SoundAction.SILENT -> audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                SoundAction.VIBRATE -> audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                SoundAction.RING -> audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                SoundAction.DND -> notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        }
        saveActive(context, active)
    }

    private fun loadActive(context: Context): MutableList<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("active", "[]")
        val arr = JSONArray(raw)
        return MutableList(arr.length()) { arr.getString(it) }
    }

    private fun saveActive(context: Context, active: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("active", JSONArray(active).toString())
            .apply()
    }
}
