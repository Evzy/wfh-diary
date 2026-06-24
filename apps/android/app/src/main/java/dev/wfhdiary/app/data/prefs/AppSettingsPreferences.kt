package dev.wfhdiary.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppSettingsPreferences(context: Context) {
    private val dataStore = context.applicationContext.wfhDiaryDataStore

    val apiBackendMode: Flow<ApiBackendMode> = dataStore.data.map { prefs ->
        ApiBackendMode.fromStorageKey(prefs[WfhDiaryPreferenceKeys.apiBackendMode])
    }

    suspend fun setApiBackendMode(mode: ApiBackendMode) {
        dataStore.edit { it[WfhDiaryPreferenceKeys.apiBackendMode] = mode.storageKey }
    }

    suspend fun resolvedApiBaseUrl(
        mode: ApiBackendMode,
        localBuildDefault: String,
        azureBuildDefault: String,
    ): String {
        val pick =
            when (mode) {
                ApiBackendMode.API_LOCAL -> localBuildDefault.trim()
                ApiBackendMode.API_AZURE -> azureBuildDefault.trim()
            }
        val noTrail = pick.trimEnd('/')
        return "$noTrail/"
    }
}
