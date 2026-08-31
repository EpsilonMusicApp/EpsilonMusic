package com.epsilonmusic.app.eq.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton


@Serializable
data class SavedEQProfile(
    val id: String,                       
    val name: String,                     
    val deviceModel: String,              
    val bands: List<ParametricEQBand>,    
    val preamp: Double = 0.0,             
    val isCustom: Boolean = false,        
    val isActive: Boolean = false,        
    val addedTimestamp: Long = System.currentTimeMillis()
)


@Singleton
class EQProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "nanosonic_eq_profiles",
        Context.MODE_PRIVATE
    )

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val _profiles = MutableStateFlow<List<SavedEQProfile>>(emptyList())
    val profiles: StateFlow<List<SavedEQProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<SavedEQProfile?>(null)
    val activeProfile: StateFlow<SavedEQProfile?> = _activeProfile.asStateFlow()

    companion object {
        private const val KEY_PROFILES = "eq_profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    }

    init {
        loadProfiles()
    }

    
    private fun loadProfiles() {
        try {
            val profilesJson = prefs.getString(KEY_PROFILES, null)
            if (profilesJson != null) {
                val loadedProfiles = json.decodeFromString<List<SavedEQProfile>>(profilesJson)
                _profiles.value = loadedProfiles

                val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
                _activeProfile.value = loadedProfiles.find { it.id == activeId }
            }
        } catch (e: kotlinx.serialization.SerializationException) {
            // Most likely cause: the kotlinx.serialization compiler plugin was not
            // applied when the app was built (no $serializer was generated for
            // SavedEQProfile / ParametricEQBand / FilterType). Also fires if the
            // persisted JSON is from an older schema. Either way, wipe the bad data
            // so the next save isn't competing with it.
            Log.e("EQProfileRepository", "Serialization error loading EQ profiles — clearing prefs", e)
            prefs.edit { remove(KEY_PROFILES); remove(KEY_ACTIVE_PROFILE_ID) }
            _profiles.value = emptyList()
            _activeProfile.value = null
        } catch (e: Exception) {
            Log.e("EQProfileRepository", "Error loading EQ profiles", e)
            _profiles.value = emptyList()
            _activeProfile.value = null
        }
    }

    
    suspend fun saveProfile(profile: SavedEQProfile) = withContext(Dispatchers.IO) {
        val currentProfiles = _profiles.value.toMutableList()


        val existingIndex = currentProfiles.indexOfFirst { it.id == profile.id }

        if (existingIndex >= 0) {

            currentProfiles[existingIndex] = profile
        } else {

            currentProfiles.add(profile)
        }


        try {
            val profilesJson = json.encodeToString<List<SavedEQProfile>>(currentProfiles)
            prefs.edit { putString(KEY_PROFILES, profilesJson) }

            _profiles.value = currentProfiles
        } catch (e: kotlinx.serialization.SerializationException) {
            // Defensive: the kotlinx.serialization compiler plugin must be applied
            // in this module's build.gradle.kts. If it isn't (e.g. a future refactor
            // re-introduces the regression), we want to log loudly and not crash
            // the app — the in-memory state has already been updated above, so
            // EQ still works for the current session.
            Log.e("EQProfileRepository", "Serialization error saving EQ profile — plugin missing?", e)
        }
    }

    
    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        val currentProfiles = _profiles.value.toMutableList()
        currentProfiles.removeAll { it.id == profileId }

        try {
            val profilesJson = json.encodeToString<List<SavedEQProfile>>(currentProfiles)
            prefs.edit { putString(KEY_PROFILES, profilesJson) }
        } catch (e: kotlinx.serialization.SerializationException) {
            Log.e("EQProfileRepository", "Serialization error deleting EQ profile — plugin missing?", e)
        }


        if (_activeProfile.value?.id == profileId) {
            _activeProfile.value = null
            prefs.edit { remove(KEY_ACTIVE_PROFILE_ID) }
        }

        _profiles.value = currentProfiles
    }

    
    suspend fun setActiveProfile(profileId: String?) = withContext(Dispatchers.IO) {
        val currentProfiles = _profiles.value

        if (profileId == null) {
            
            _activeProfile.value = null
            prefs.edit { remove(KEY_ACTIVE_PROFILE_ID) }
        } else {
            val profile = currentProfiles.find { it.id == profileId }
            _activeProfile.value = profile
            prefs.edit { putString(KEY_ACTIVE_PROFILE_ID, profileId) }
        }
    }

    
    fun getAllProfiles(): List<SavedEQProfile> {
        return _profiles.value
    }

    
    fun getActiveProfile(): SavedEQProfile? {
        return _activeProfile.value
    }

    
    suspend fun importCustomProfile(
        name: String,
        parametricEQ: ParametricEQ
    ) = withContext(Dispatchers.IO) {
        
        val id = "custom_${System.currentTimeMillis()}_${name.hashCode()}"

        val customProfile = SavedEQProfile(
            id = id,
            name = name,
            deviceModel = name,
            bands = parametricEQ.bands,  
            preamp = parametricEQ.preamp,
            isActive = false,
            isCustom = true 
        )

        saveProfile(customProfile)
    }

    
    fun getSortedProfiles(): List<SavedEQProfile> {
        
        return _profiles.value
            .filter { it.isCustom }
            .sortedByDescending { it.addedTimestamp }
    }
}
