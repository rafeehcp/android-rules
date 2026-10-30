package com.rafeeh.rules.ui

import android.content.Context
import android.location.Geocoder
import android.util.Log
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PlaceResult(val name: String, val detail: String, val point: GeoPoint)

/**
 * Photon (OpenStreetMap, built for search-as-you-type) ranked near [near];
 * Android's Geocoder when Photon is unreachable. Blocking: call off the main thread.
 */
fun searchPlaces(context: Context, query: String, near: GeoPoint?): List<PlaceResult> =
    runCatching { photon(context, query, near) }
        .onFailure { Log.w("Rules", "Photon search failed", it) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?: geocoder(context, query)

private fun photon(context: Context, query: String, near: GeoPoint?): List<PlaceResult> {
    val bias = near?.let { "&lat=${it.latitude}&lon=${it.longitude}" }.orEmpty()
    val url = URL("https://photon.komoot.io/api/?limit=8$bias&q=" + URLEncoder.encode(query, "UTF-8"))
    val conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 5000
        readTimeout = 5000
        setRequestProperty("User-Agent", context.packageName)
    }
    val body = conn.inputStream.bufferedReader().use { it.readText() }
    val features = JSONObject(body).getJSONArray("features")
    return (0 until features.length()).mapNotNull { i ->
        val f = features.getJSONObject(i)
        val coords = f.getJSONObject("geometry").getJSONArray("coordinates")
        val p = f.getJSONObject("properties")
        val street = listOfNotNull(p.optString("housenumber").ifBlank { null }, p.optString("street").ifBlank { null }).joinToString(" ")
        val name = p.optString("name").ifBlank { street.ifBlank { null } } ?: return@mapNotNull null
        val detail = listOf(street.takeIf { it != name }, p.optString("district"), p.optString("city"), p.optString("state"), p.optString("country"))
            .filter { !it.isNullOrBlank() }
            .distinct()
            .joinToString(", ")
        PlaceResult(name, detail, GeoPoint(coords.getDouble(1), coords.getDouble(0)))
    }
}

/** Android's geocoder: Google's address data on Play devices, weak on business names. */
@Suppress("DEPRECATION")
fun geocoder(context: Context, query: String): List<PlaceResult> = runCatching {
    Geocoder(context).getFromLocationName(query, 8).orEmpty().map { a ->
        val name = a.featureName ?: a.getAddressLine(0) ?: query
        val detail = listOfNotNull(a.locality, a.adminArea, a.countryName).joinToString(", ")
        PlaceResult(name, detail, GeoPoint(a.latitude, a.longitude))
    }
}.getOrDefault(emptyList())
