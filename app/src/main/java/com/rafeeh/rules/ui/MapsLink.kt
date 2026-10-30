package com.rafeeh.rules.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** Turns pasted coordinates or a Google Maps share (text with a maps.app.goo.gl / google.com/maps link) into a place. */
object MapsLink {
    private val coordinates = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")
    private val url = Regex("""https?://\S+""")
    private val pointPatterns = listOf(
        Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)"""),
        Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)"""),
        Regex("""[?&](?:q|query|ll|center|destination)=(-?\d+\.\d+)(?:,|%2C)(-?\d+\.\d+)"""),
    )
    private val pageCenter = Regex("""APP_INITIALIZATION_STATE=\[\[\[[\d.]+,(-?\d+\.\d+),(-?\d+\.\d+)\]""")

    fun coordinatesIn(text: String): GeoPoint? = coordinates.find(text)?.let { m ->
        val (lat, lng) = m.destructured
        GeoPoint(lat.toDouble(), lng.toDouble()).takeIf { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
    }

    fun isMapsLink(text: String) = url.find(text)?.value?.let { "goo.gl" in it || "google." in it } == true

    /** Blocking (network): call off the main thread. */
    fun resolve(context: Context, text: String): PlaceResult? {
        coordinatesIn(text)?.let { return PlaceResult("Pinned place", "%.5f, %.5f".format(it.latitude, it.longitude), it) }
        val link = url.find(text)?.value ?: return null
        val nameHint = text.substringBefore(link).lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        return runCatching {
            val final = followRedirects(link)
            Log.i("Rules", "Shared link resolved to $final")
            val page by lazy { fetch(final) }
            val name = placeName(final) ?: nameHint ?: queryText(final) ?: "Shared place"
            val point = pointIn(final)
                ?: pointIn(page)
                ?: pageCenter.find(page)?.let { GeoPoint(it.groupValues[2].toDouble(), it.groupValues[1].toDouble()) }
                ?: (queryText(final) ?: name).let { q -> geocoder(context, q).firstOrNull() ?: searchPlaces(context, q, null).firstOrNull() }?.point
                ?: return@runCatching null
            PlaceResult(name, "From Google Maps", point)
        }.onFailure { Log.w("Rules", "Could not resolve $link", it) }.getOrNull()
    }

    private fun pointIn(s: String): GeoPoint? = pointPatterns.firstNotNullOfOrNull { re ->
        re.find(s)?.let { GeoPoint(it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }
    }

    private fun placeName(u: String) = Regex("""/maps/place/([^/@?]+)""").find(u)?.groupValues?.get(1)?.let(::decode)

    private fun queryText(u: String) = runCatching { Uri.parse(u).getQueryParameter("q") }.getOrNull()
        ?.takeIf { coordinatesIn(it) == null && it.isNotBlank() }

    private fun decode(s: String) = URLDecoder.decode(s, "UTF-8").replace('+', ' ').trim()

    private fun followRedirects(start: String): String {
        var current = start
        repeat(8) {
            val conn = open(current).apply { instanceFollowRedirects = false; requestMethod = "GET" }
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (location.isNullOrBlank()) return current
            current = URL(URL(current), location).toString()
            if ("consent.google." in current) {
                Uri.parse(current).getQueryParameter("continue")?.let { current = it }
            }
        }
        return current
    }

    private fun fetch(u: String): String = open(u).inputStream.bufferedReader().use { it.readText() }

    private fun open(u: String) = (URL(u).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36")
        setRequestProperty("Accept-Language", "en")
    }
}
