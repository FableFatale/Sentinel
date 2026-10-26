package app.permissionsentinel.ui.screens

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.LocaleList
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.permissionsentinel.R
import app.permissionsentinel.SentinelViewModel
import app.permissionsentinel.data.SentinelRecord
import app.permissionsentinel.data.UsageSource
import app.permissionsentinel.ui.components.CompactSwitch
import app.permissionsentinel.ui.components.PageHeader
import app.permissionsentinel.ui.components.SectionLabel
import app.permissionsentinel.ui.components.SettingsRow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

private enum class SettingsDialog {
    LANGUAGE,
    THEME,
    OBSERVATION_LIMITS,
}

@Composable
fun SettingsScreen(vm: SentinelViewModel, bottomInset: androidx.compose.ui.unit.Dp) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var openDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val usageGranted = UsageSource.isUsageAccessGranted(context)
    val exportSucceeded = stringResource(R.string.export_succeeded)
    val exportFailed = stringResource(R.string.export_failed)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(exportMetadata(state.records).toByteArray(Charsets.UTF_8))
            } ?: error("Output stream unavailable")
        }
        Toast.makeText(
            context,
            if (result.isSuccess) exportSucceeded else exportFailed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        PageHeader(title = stringResource(R.string.settings_title))
        Spacer(Modifier.height(22.dp))

        SectionLabel(stringResource(R.string.settings_appearance))
        Spacer(Modifier.height(4.dp))
        SettingsRow(
            label = stringResource(R.string.language_title),
            value = when (settings.languageTag) {
                "en" -> "English"
                "zh-CN" -> "简体中文"
                else -> stringResource(R.string.language_system)
            },
            showChevron = true,
            onClick = { openDialog = SettingsDialog.LANGUAGE },
        )
        SettingsRow(
            label = stringResource(R.string.theme_title),
            value = when (settings.themeMode) {
                1 -> stringResource(R.string.theme_light)
                2 -> stringResource(R.string.theme_dark)
                else -> stringResource(R.string.theme_system)
            },
            showChevron = true,
            onClick = { openDialog = SettingsDialog.THEME },
        )

        Spacer(Modifier.height(22.dp))
        SectionLabel(stringResource(R.string.settings_access))
        Spacer(Modifier.height(4.dp))
        SettingsRow(
            label = stringResource(R.string.collection_mode),
            value = stringResource(R.string.collection_mode_standard),
        )
        SettingsRow(
            label = stringResource(R.string.usage_access_title),
            value = if (usageGranted) {
                stringResource(R.string.status_granted)
            } else {
                stringResource(R.string.status_not_granted)
            },
            showChevron = true,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
        )
        SettingsRow(
            label = stringResource(R.string.observation_limits),
            value = stringResource(R.string.observation_limits_value),
            showChevron = true,
            onClick = { openDialog = SettingsDialog.OBSERVATION_LIMITS },
        )

        Spacer(Modifier.height(22.dp))
        SectionLabel(stringResource(R.string.settings_collection))
        Spacer(Modifier.height(4.dp))
        SettingsRow(
            label = stringResource(R.string.haptic_feedback),
            trailing = {
                CompactSwitch(
                    checked = settings.hapticsEnabled,
                    onCheckedChange = vm::setHapticsEnabled,
                )
            },
        )

        Spacer(Modifier.height(22.dp))
        SectionLabel(stringResource(R.string.data_title))
        Spacer(Modifier.height(4.dp))
        SettingsRow(
            label = stringResource(R.string.stored_records),
            value = state.recordCount.toString(),
        )
        SettingsRow(
            label = stringResource(R.string.export_metadata),
            showChevron = true,
            onClick = {
                exportLauncher.launch(context.getString(R.string.export_file_name))
            },
        )
        SettingsRow(
            label = stringResource(R.string.clear_records),
            danger = true,
            onClick = { confirmClear = true },
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.privacy_note_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.height(bottomInset))
    }

    when (openDialog) {
        SettingsDialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.language_title),
            options = listOf(
                "" to stringResource(R.string.language_system),
                "en" to "English",
                "zh-CN" to "简体中文",
            ),
            selected = settings.languageTag,
            onSelect = { tag ->
                applyLanguage(context, vm, tag)
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.theme_title),
            options = listOf(
                "0" to stringResource(R.string.theme_system),
                "1" to stringResource(R.string.theme_light),
                "2" to stringResource(R.string.theme_dark),
            ),
            selected = settings.themeMode.toString(),
            onSelect = { mode ->
                vm.setThemeMode(mode.toInt())
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )
        SettingsDialog.OBSERVATION_LIMITS -> AlertDialog(
            onDismissRequest = { openDialog = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            title = { Text(stringResource(R.string.observation_limits_title)) },
            text = { Text(stringResource(R.string.observation_limits_body)) },
            confirmButton = {
                TextButton(onClick = { openDialog = null }) {
                    Text(stringResource(R.string.confirm_read))
                }
            },
        )
        null -> Unit
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            title = { Text(stringResource(R.string.clear_confirm_title)) },
            text = { Text(stringResource(R.string.clear_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearRecords()
                    confirmClear = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    RadioRow(
                        label = label,
                        selected = selected == value,
                        onClick = { onSelect(value) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun applyLanguage(context: Context, vm: SentinelViewModel, tag: String) {
    vm.setLanguageTag(tag)
    val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
    localeManager.applicationLocales =
        if (tag.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private fun exportMetadata(records: List<SentinelRecord>): String {
    val items = JSONArray()
    records.sortedByDescending { it.timestamp }.forEach { record ->
        items.put(
            JSONObject()
                .put("timestamp", record.timestamp)
                .put("observedAt", Instant.ofEpochMilli(record.timestamp).toString())
                .put("type", record.type.id)
                .put("packageName", record.packageName)
                .put("extra", record.extra)
        )
    }
    return JSONObject()
        .put("schema", "permission-sentinel-metadata-v1")
        .put("exportedAt", Instant.now().toString())
        .put("recordCount", records.size)
        .put("records", items)
        .toString(2)
}
