package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "quiz_class_settings")

enum class ThemePreference {
  SYSTEM,
  LIGHT,
  DARK;

  companion object {
    fun fromString(raw: String?): ThemePreference =
      entries.firstOrNull { it.name == raw } ?: SYSTEM
  }
}

class SettingsRepository(private val context: Context) {
  private val themeKey = stringPreferencesKey("theme_preference")

  val themePreference: Flow<ThemePreference> =
    context.settingsDataStore.data.map { prefs ->
      ThemePreference.fromString(prefs[themeKey])
    }

  suspend fun setThemePreference(preference: ThemePreference) {
    context.settingsDataStore.edit { prefs ->
      prefs[themeKey] = preference.name
    }
  }
}
