package com.rafeeh.rules.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rafeeh.rules.Rule
import com.rafeeh.rules.Trigger

private data class Setup(val label: String, val why: String, val granted: Boolean, val fix: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    rules: List<Rule>,
    activeIds: List<String>,
    resumeTick: Int,
    onAdd: () -> Unit,
    onEdit: (Rule) -> Unit,
    onToggle: (Rule, Boolean) -> Unit,
) {
    val setup = setupItems(resumeTick).filter { !it.granted }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Rules") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add rule") })
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(setup, key = { it.label }) { SetupCard(it) }
            if (rules.isEmpty()) {
                item {
                    Text(
                        "Change your phone's sound when you arrive somewhere or connect to a Wi-Fi network.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(rules, key = { it.id }) { rule ->
                RuleCard(rule, active = rule.id in activeIds, onClick = { onEdit(rule) }, onToggle = { onToggle(rule, it) })
            }
        }
    }
}

@Composable
private fun RuleCard(rule: Rule, active: Boolean, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val trigger = when (val t = rule.trigger) {
        is Trigger.Place -> "When at this place (${t.radiusM.toInt()} m)"
        is Trigger.Wifi -> "When connected to Wi-Fi"
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.title, style = MaterialTheme.typography.titleMedium)
                Text(trigger, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "→ ${rule.action.label}" + if (active) "  ·  active now" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun SetupCard(item: Setup) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.titleSmall)
                Text(item.why, style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = item.fix, modifier = Modifier.padding(start = 8.dp)) { Text("Fix") }
        }
    }
}

@Composable
private fun setupItems(resumeTick: Int): List<Setup> {
    val context = LocalContext.current
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return remember(resumeTick) {
        val fine = context.has(Manifest.permission.ACCESS_FINE_LOCATION)
        listOf(
            Setup("Location", "Needed to know where you are and which Wi-Fi you're on.", fine) {
                fineLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            Setup("Location: Allow all the time", "Rules run in the background. Choose \"Allow all the time\".",
                fine && context.has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                if (fine) singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                else fineLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            },
            Setup("Do Not Disturb access", "Required to switch Silent, Vibrate, Ring and DND.",
                context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            },
            Setup("Notifications", "Shows the small notice that keeps Wi-Fi rules running.",
                context.has(Manifest.permission.POST_NOTIFICATIONS)) {
                singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            Setup("Battery: Unrestricted", "Stops Nothing OS from killing rules in the background.",
                context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) {
                @Suppress("BatteryLife")
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                )
            },
        )
    }
}

private fun Context.has(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
