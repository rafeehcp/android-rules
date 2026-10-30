package com.rafeeh.rules

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rafeeh.rules.ui.EditRuleScreen
import com.rafeeh.rules.ui.RulesScreen
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    private var rules by mutableStateOf(emptyList<Rule>())
    private var resumeTick by mutableIntStateOf(0)

    /** null = list screen; a Rule = editing it (a fresh Rule for "add"). */
    private var editing by mutableStateOf<Rule?>(null)
    private var editingIsNew by mutableStateOf(false)
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        enableEdgeToEdge()
        handleShare(intent)
        setContent {
            val context = LocalContext.current
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = scheme) {
                val current = editing
                if (current == null) {
                    RulesScreen(
                        rules = rules,
                        activeIds = remember(rules, resumeTick) { SoundEngine.activeIds(this) },
                        resumeTick = resumeTick,
                        onAdd = {
                            editingIsNew = true
                            editing = Rule(trigger = Trigger.Place(0.0, 0.0, 150f, ""), action = SoundAction.SILENT)
                        },
                        onEdit = { editingIsNew = false; editing = it },
                        onToggle = { rule, on -> save(rules.map { if (it.id == rule.id) it.copy(enabled = on) else it }) },
                    )
                } else {
                    key(current.id) { EditRuleScreen(
                        initial = current,
                        isNew = editingIsNew,
                        onSave = { saved ->
                            sharedText = null
                            save(if (editingIsNew) rules + saved else rules.map { if (it.id == saved.id) saved else it })
                            editing = null
                        },
                        onDelete = { sharedText = null; save(rules.filter { it.id != current.id }); editing = null },
                        onCancel = { sharedText = null; editing = null },
                        sharedText = sharedText,
                    ) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** A place shared from Google Maps opens a new location rule pre-filled from the link. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        sharedText = text
        editingIsNew = true
        editing = Rule(trigger = Trigger.Place(0.0, 0.0, 150f, ""), action = SoundAction.SILENT)
    }

    override fun onResume() {
        super.onResume()
        rules = RuleStore.load(this)
        Triggers.sync(this)
        resumeTick++
    }

    private fun save(updated: List<Rule>) {
        RuleStore.save(this, updated)
        rules = updated
        Triggers.sync(this)
        resumeTick++
    }
}
