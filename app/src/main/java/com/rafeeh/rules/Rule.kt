package com.rafeeh.rules

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed interface Trigger {
    data class Place(val lat: Double, val lng: Double, val radiusM: Float, val label: String) : Trigger
    data class Wifi(val ssid: String) : Trigger
}

enum class SoundAction(val label: String) {
    SILENT("Silent"),
    VIBRATE("Vibrate"),
    RING("Ring"),
    DND("Do Not Disturb"),
}

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val trigger: Trigger,
    val action: SoundAction,
    val enabled: Boolean = true,
) {
    val title: String
        get() = when (trigger) {
            is Trigger.Place -> trigger.label
            is Trigger.Wifi -> trigger.ssid
        }
}

object RuleStore {
    private const val PREFS = "rules"
    private const val KEY = "rules"

    fun load(context: Context): List<Rule> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }

    fun save(context: Context, rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(toJson(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    private fun toJson(rule: Rule) = JSONObject().apply {
        put("id", rule.id)
        put("action", rule.action.name)
        put("enabled", rule.enabled)
        when (val t = rule.trigger) {
            is Trigger.Place -> {
                put("type", "place")
                put("lat", t.lat)
                put("lng", t.lng)
                put("radius", t.radiusM.toDouble())
                put("label", t.label)
            }
            is Trigger.Wifi -> {
                put("type", "wifi")
                put("ssid", t.ssid)
            }
        }
    }

    private fun fromJson(o: JSONObject) = Rule(
        id = o.getString("id"),
        action = SoundAction.valueOf(o.getString("action")),
        enabled = o.getBoolean("enabled"),
        trigger = when (o.getString("type")) {
            "place" -> Trigger.Place(o.getDouble("lat"), o.getDouble("lng"), o.getDouble("radius").toFloat(), o.getString("label"))
            else -> Trigger.Wifi(o.getString("ssid"))
        },
    )
}
