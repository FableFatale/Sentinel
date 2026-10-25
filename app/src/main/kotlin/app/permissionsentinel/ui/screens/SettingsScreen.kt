package app.permissionsentinel.ui.screens

import android.app.LocaleManager
import android.content.Intent
import android.os.LocaleList
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.permissionsentinel.data.AppSettings
import app.permissionsentinel.data.UsageSource
import app.permissionsentinel.ui.components.SectionCard

@Composable
fun SettingsScreen(vm: SentinelViewModel, bottomInset: androidx.compose.ui.unit.Dp) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(14.dp))

        SectionCard {
            Text(
                text = stringResource(R.string.usage_access_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            val granted = UsageSource.isUsageAccessGranted(context)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (granted) stringResource(R.string.status_granted)
                    else stringResource(R.string.status_not_granted),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!granted) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) {
                        Text(stringResource(R.string.open_settings))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        SectionCard {
            Text(
                text = stringResource(R.string.theme_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            RadioRow(
                label = stringResource(R.string.theme_system),
                selected = settings.themeMode == 0,
            ) { vm.setThemeMode(0) }
            RadioRow(
                label = stringResource(R.string.theme_light),
                selected = settings.themeMode == 1,
            ) { vm.setThemeMode(1) }
            RadioRow(
                label = stringResource(R.string.theme_dark),
                selected = settings.themeMode == 2,
            ) { vm.setThemeMode(2) }
        }
        Spacer(Modifier.height(12.dp))

        SectionCard {
            Text(
                text = stringResource(R.string.language_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            RadioRow(
                label = stringResource(R.string.language_system),
                selected = settings.languageTag.isBlank(),
            ) { applyLanguage(context, vm, "") }
            RadioRow(
                label = "English",
                selected = settings.languageTag == "en",
            ) { applyLanguage(context, vm, "en") }
            RadioRow(
                label = "简体中文",
                selected = settings.languageTag == "zh-CN",
            ) { applyLanguage(context, vm, "zh-CN") }
        }
        Spacer(Modifier.height(12.dp))

        SectionCard {
            Text(
                text = stringResource(R.string.data_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.records_count, state.recordCount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { confirmClear = true }) {
                Text(stringResource(R.string.clear_records))
            }
        }
        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.height(bottomInset))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_confirm_title)) },
            text = { Text(stringResource(R.string.clear_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearRecords()
                    confirmClear = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun applyLanguage(context: android.content.Context, vm: SentinelViewModel, tag: String) {
    vm.setLanguageTag(tag)
    val lm = context.getSystemService(LocaleManager::class.java) ?: return
    lm.applicationLocales =
        if (tag.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
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
