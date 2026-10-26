package app.permissionsentinel.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sentinelDataStore by preferencesDataStore(name = "sentinel_store")

/** Append-only local observation log, capped by [RecordCodec.MAX_LINES]. */
class RecordStore(private val context: Context) {

    val records: Flow<List<SentinelRecord>> = context.sentinelDataStore.data.map { prefs ->
        RecordCodec.decodeAll(prefs[KEY_RECORDS] ?: "")
    }

    suspend fun append(record: SentinelRecord) {
        context.sentinelDataStore.edit { prefs ->
            val existing = RecordCodec.decodeAll(prefs[KEY_RECORDS] ?: "")
            prefs[KEY_RECORDS] = RecordCodec.encodeAll(RecordCodec.trim(existing + record))
        }
    }

    suspend fun clear() {
        context.sentinelDataStore.edit { it.remove(KEY_RECORDS) }
    }

    private companion object {
        val KEY_RECORDS = stringPreferencesKey("records")
    }
}

data class AppSettings(
    /** 0 = system, 1 = light, 2 = dark */
    val themeMode: Int = 0,
    /** "" = system, "en", "zh-CN" */
    val languageTag: String = "",
    val hapticsEnabled: Boolean = true,
)

class SettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.sentinelDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME] ?: 0,
            languageTag = prefs[KEY_LANG] ?: "",
            hapticsEnabled = prefs[KEY_HAPTICS] ?: true,
        )
    }

    suspend fun setThemeMode(mode: Int) {
        context.sentinelDataStore.edit { it[KEY_THEME] = mode }
    }

    suspend fun setLanguageTag(tag: String) {
        context.sentinelDataStore.edit { it[KEY_LANG] = tag }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.sentinelDataStore.edit { it[KEY_HAPTICS] = enabled }
    }

    private companion object {
        val KEY_THEME = intPreferencesKey("theme_mode")
        val KEY_LANG = stringPreferencesKey("language_tag")
        val KEY_HAPTICS = booleanPreferencesKey("haptics_enabled")
    }
}
