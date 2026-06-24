package dev.wfhdiary.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.wfhDiaryDataStore: DataStore<Preferences> by preferencesDataStore(name = "wfh_diary_prefs")

object WfhDiaryPreferenceKeys {
    val apiBackendMode = stringPreferencesKey("api_backend_mode")
}
