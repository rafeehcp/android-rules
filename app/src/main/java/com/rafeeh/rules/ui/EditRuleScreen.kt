package com.rafeeh.rules.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.net.wifi.WifiManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rafeeh.rules.KnownNetworks
import com.rafeeh.rules.Rule
import com.rafeeh.rules.SoundAction
import com.rafeeh.rules.Trigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRuleScreen(
    initial: Rule,
    isNew: Boolean,
    onSave: (Rule) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initialPlace = initial.trigger as? Trigger.Place
    val initialWifi = initial.trigger as? Trigger.Wifi
    var isPlace by remember { mutableStateOf(initial.trigger is Trigger.Place) }
    var point by remember { mutableStateOf(initialPlace?.takeIf { !isNew }?.let { GeoPoint(it.lat, it.lng) }) }
    var focus by remember { mutableStateOf(point) }
    var radius by remember { mutableStateOf(initialPlace?.radiusM ?: 150f) }
    var label by remember { mutableStateOf(initialPlace?.label.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf(initialWifi?.ssid.orEmpty()) }
    var action by remember { mutableStateOf(initial.action) }
    var status by remember { mutableStateOf<String?>(null) }

    fun pick(p: GeoPoint) {
        point = p
        scope.launch {
            val name = withContext(Dispatchers.IO) { context.placeName(p) }
            if (name != null && (label.isBlank() || label.startsWith("Pinned"))) label = name
            else if (label.isBlank()) label = "Pinned place"
        }
    }

    var here by remember { mutableStateOf<GeoPoint?>(null) }
    var results by remember { mutableStateOf(emptyList<PlaceResult>()) }
    var searching by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        context.currentLocation { here = it; if (isNew) focus = it }
    }

    LaunchedEffect(query) {
        if (query.trim().length < 3) {
            results = emptyList()
            status = null
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        val found = try {
            withContext(Dispatchers.IO) { searchPlaces(context, query.trim(), here ?: point) }
        } finally {
            searching = false
        }
        results = found
        status = if (found.isEmpty()) "No places found for \"${query.trim()}\"" else null
    }

    val canSave = if (isPlace) point != null && label.isNotBlank() else ssid.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add rule" else "Edit rule") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (!isNew) TextButton(onClick = onDelete) { Text("Delete") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Trigger", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(isPlace, { isPlace = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Location") }
                SegmentedButton(!isPlace, { isPlace = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Wi-Fi network") }
            }

            if (isPlace) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search a place") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                    },
                )
                if (results.isNotEmpty()) {
                    Card {
                        results.forEach { r ->
                            ListItem(
                                headlineContent = { Text(r.name) },
                                supportingContent = r.detail.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    keyboard?.hide()
                                    focus = r.point
                                    point = r.point
                                    label = r.name
                                    query = ""
                                },
                            )
                        }
                    }
                }
                Text("Tap the map to drop the pin.", style = MaterialTheme.typography.bodySmall)
                MapPicker(
                    point = point,
                    radiusM = radius,
                    focus = focus,
                    onPick = ::pick,
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                )
                OutlinedButton(onClick = { context.currentLocation { focus = it; pick(it) } }) { Text("Use my current location") }
                Text("Radius: ${radius.toInt()} m", style = MaterialTheme.typography.bodyMedium)
                Slider(value = radius, onValueChange = { radius = it }, valueRange = 75f..1000f)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("Wi-Fi network name (SSID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val networks = remember { context.wifiChoices() }
                val shown = networks.filter { ssid.isBlank() || it.ssid.contains(ssid, ignoreCase = true) }
                if (networks.isNotEmpty()) {
                    Text("Saved and nearby networks", style = MaterialTheme.typography.bodySmall)
                    Card {
                        shown.forEach { choice ->
                            ListItem(
                                headlineContent = { Text(choice.ssid) },
                                supportingContent = choice.note?.let { { Text(it) } },
                                leadingContent = { RadioButton(selected = ssid == choice.ssid, onClick = null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.selectable(ssid == choice.ssid, onClick = { ssid = choice.ssid }),
                            )
                        }
                        if (shown.isEmpty()) {
                            Text("No saved network matches. The name you typed will be used.", Modifier.padding(16.dp))
                        }
                    }
                }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(4.dp))
            Text("Action", style = MaterialTheme.typography.titleMedium)
            SoundAction.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().selectable(action == option, onClick = { action = option }).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = action == option, onClick = null)
                    Text(option.label, Modifier.padding(start = 12.dp))
                }
            }
            Text(
                "Your previous sound setting comes back when you leave.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val trigger = if (isPlace) {
                        val p = point!!
                        Trigger.Place(p.latitude, p.longitude, radius, label.trim())
                    } else Trigger.Wifi(ssid.trim())
                    onSave(initial.copy(trigger = trigger, action = action))
                },
            ) { Text("Save") }
        }
    }
}

@SuppressLint("MissingPermission")
private fun Context.currentLocation(onResult: (GeoPoint) -> Unit) {
    runCatching {
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc -> loc?.let { onResult(GeoPoint(it.latitude, it.longitude)) } }
    }
}

@Suppress("DEPRECATION")
private fun Context.placeName(p: GeoPoint): String? = runCatching {
    val a = Geocoder(this).getFromLocation(p.latitude, p.longitude, 1)?.firstOrNull() ?: return null
    listOfNotNull(a.featureName?.takeIf { !it.matches(Regex("[0-9A-Z+]+")) }, a.subLocality ?: a.locality)
        .distinct()
        .joinToString(", ")
        .ifBlank { null }
}.getOrNull()

private data class WifiChoice(val ssid: String, val note: String?)

/** Current network first, then saved networks (in range ones before the rest), then unsaved nearby ones. */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun Context.wifiChoices(): List<WifiChoice> {
    val wifi = getSystemService(WifiManager::class.java)
    val current = runCatching { wifi.connectionInfo?.ssid?.removeSurrounding("\"") }.getOrNull()
        ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
    current?.let { KnownNetworks.remember(this, it) }
    val nearby = runCatching { wifi.scanResults.map { it.SSID } }.getOrDefault(emptyList()).filter { it.isNotBlank() }.toSet()
    val saved = KnownNetworks.load(this).sortedWith(compareBy({ it !in nearby }, { it.lowercase() }))
    return buildList {
        current?.let { add(WifiChoice(it, "Connected now")) }
        saved.filter { it != current }.forEach { add(WifiChoice(it, if (it in nearby) "Saved · in range" else "Saved")) }
        nearby.filter { it != current && it !in saved }.sorted().forEach { add(WifiChoice(it, "Nearby")) }
    }
}
