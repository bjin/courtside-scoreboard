package io.github.bjin.courtside.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bjin.courtside.R
import io.github.bjin.courtside.core.ActionKind
import io.github.bjin.courtside.core.Feedback
import io.github.bjin.courtside.core.RemoteLogEntry
import io.github.bjin.courtside.core.Side
import io.github.bjin.courtside.data.AppLanguage
import io.github.bjin.courtside.data.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MenuDialog(
    settings: Settings,
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    remoteLog: List<RemoteLogEntry>,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onSwap: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = if (settings.darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.92f),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.menu_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        LanguagePicker(language, onLanguageSelected)
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onSwap) { Text(stringResource(R.string.menu_swap)) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onExit) { Text(stringResource(R.string.menu_exit)) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onDismiss) { Text(stringResource(R.string.menu_close)) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            SectionTitle(stringResource(R.string.menu_section_display))
                            Toggle(R.string.setting_keep_screen_on, settings.keepScreenOn) { v -> onUpdate { it.copy(keepScreenOn = v) } }
                            Toggle(R.string.setting_max_brightness, settings.maxBrightness) { v -> onUpdate { it.copy(maxBrightness = v) } }
                            Toggle(R.string.setting_highlight_server, settings.highlightServer) { v -> onUpdate { it.copy(highlightServer = v) } }
                            Toggle(R.string.setting_lock_screen, settings.showOnLockScreen, R.string.setting_lock_screen_note) { v ->
                                onUpdate { it.copy(showOnLockScreen = v) }
                            }
                            Toggle(R.string.setting_haptics, settings.haptics) { v -> onUpdate { it.copy(haptics = v) } }
                            Toggle(R.string.setting_dark, settings.darkTheme) { v -> onUpdate { it.copy(darkTheme = v) } }
                            SectionTitle(stringResource(R.string.menu_section_about))
                            Note(stringResource(R.string.about_text))
                            Note(stringResource(R.string.about_privacy))
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            SectionTitle(stringResource(R.string.menu_section_watch))
                            Note(stringResource(R.string.watch_mapping))
                            Toggle(R.string.setting_volume_reset, settings.volumeReset, R.string.setting_volume_reset_note) { v ->
                                onUpdate { it.copy(volumeReset = v) }
                            }
                            Toggle(R.string.setting_phone_volume, settings.phoneVolumeFallback, R.string.setting_phone_volume_note) { v ->
                                onUpdate { it.copy(phoneVolumeFallback = v) }
                            }
                            Toggle(R.string.setting_claim_keys, settings.claimMediaButtons, R.string.setting_claim_keys_note) { v ->
                                onUpdate { it.copy(claimMediaButtons = v) }
                            }
                            SectionTitle(stringResource(R.string.menu_section_log))
                            if (remoteLog.isEmpty()) Note(stringResource(R.string.log_empty))
                            remoteLog.forEach { LogLine(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePicker(language: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val systemName = stringResource(R.string.language_system)
    val currentName = if (language == AppLanguage.SYSTEM) systemName else language.nativeName
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("${stringResource(R.string.menu_language)}: $currentName")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (option == AppLanguage.SYSTEM) systemName else option.nativeName,
                            fontWeight = if (option == language) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        if (option != language) onSelected(option)
                    },
                    modifier = Modifier.semantics { selected = option == language },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Toggle(label: Int, checked: Boolean, note: Int? = null, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            if (note != null) {
                Text(stringResource(note), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

@Composable
private fun LogLine(entry: RemoteLogEntry) {
    val outcome = entry.outcome?.let { outcomeText(it) } ?: stringResource(R.string.log_ignored)
    val caller = entry.caller?.let { " · $it" }.orEmpty()
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "${timeFormat.format(Date(entry.wallTimeMillis))}  ${entry.input} → $outcome",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        if (caller.isNotEmpty()) {
            Text(caller.removePrefix(" · "), fontSize = 11.sp)
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun outcomeText(feedback: Feedback): String = stringResource(
    when (feedback.kind) {
        ActionKind.POINT -> if (Side.LEFT in feedback.sides) R.string.status_point_left else R.string.status_point_right
        ActionKind.UNDO -> R.string.status_undo
        ActionKind.RESET -> R.string.status_reset
        ActionKind.SWAP -> R.string.status_swap
        ActionKind.RESET_ARMED -> R.string.status_reset_armed
        ActionKind.NOTHING_TO_UNDO -> R.string.status_nothing_to_undo
        ActionKind.MAX_SCORE -> R.string.status_max
    },
)
