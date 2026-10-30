package com.rafeeh.rules

import android.content.Context
import java.io.File

/**
 * Apps can't read the phone's saved Wi-Fi list since Android 10, so the app keeps its own:
 * every network it sees the phone connect to, plus a one-time import of
 * `adb shell cmd wifi list-networks` written to this file via `run-as`.
 */
object KnownNetworks {
    private fun file(context: Context) = File(context.filesDir, "known_ssids.txt")

    @Synchronized
    fun load(context: Context): List<String> {
        val f = file(context)
        return if (f.exists()) f.readLines().map { it.trim() }.filter { it.isNotEmpty() }.distinct() else emptyList()
    }

    @Synchronized
    fun remember(context: Context, ssid: String) {
        if (ssid.isBlank() || ssid in load(context)) return
        file(context).appendText(ssid + "\n")
    }
}
