package com.corelink.frame.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.corelink.frame.hardware.HardwareProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.hardwareDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "frame_hardware"
)

@Singleton
class HardwarePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.hardwareDataStore

    private object Keys {
        val PROFILE_ID = stringPreferencesKey("profile_id")
        val ASSESSED_AT = longPreferencesKey("assessed_at")
        val TOTAL_RAM_BYTES = longPreferencesKey("total_ram_bytes")
        val RATIONALE = stringPreferencesKey("rationale")
    }

    val lastProfile: Flow<HardwareProfile?> = dataStore.data.map { prefs ->
        val id = prefs[Keys.PROFILE_ID] ?: return@map null
        HardwareProfile.entries.find { it.id == id }
    }

    val lastAssessedAt: Flow<Long?> = dataStore.data.map { it[Keys.ASSESSED_AT] }

    suspend fun saveAssessment(
        profile: HardwareProfile,
        totalRamBytes: Long,
        rationale: String,
        assessedAt: Long = System.currentTimeMillis()
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.PROFILE_ID] = profile.id
            prefs[Keys.ASSESSED_AT] = assessedAt
            prefs[Keys.TOTAL_RAM_BYTES] = totalRamBytes
            prefs[Keys.RATIONALE] = rationale
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
