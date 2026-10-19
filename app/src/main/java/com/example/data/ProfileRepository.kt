package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DnsProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dns_toggle_settings")

class ProfileRepository(private val context: Context) {

    private val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    private val KEY_CUSTOM_PROFILES_JSON = stringPreferencesKey("custom_profiles_json")
    private val KEY_BUILTIN_ENABLED_MAP = stringPreferencesKey("builtin_enabled_map")

    @Volatile
    private var cachedActiveProfile: DnsProfile = DnsProfile.ADGUARD

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                activeProfileFlow.collect { profile ->
                    cachedActiveProfile = profile
                }
            } catch (ignored: Exception) {}
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ProfileRepository? = null

        fun getInstance(context: Context): ProfileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProfileRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val profilesFlow: Flow<List<DnsProfile>> = context.dataStore.data.map { prefs ->
        loadProfilesFromPreferences(prefs)
    }

    val activeProfileFlow: Flow<DnsProfile> = context.dataStore.data.map { prefs ->
        val activeId = prefs[KEY_ACTIVE_PROFILE_ID] ?: DnsProfile.ADGUARD.id
        val all = loadProfilesFromPreferences(prefs)
        val selected = all.firstOrNull { it.id == activeId }
            ?: all.firstOrNull { it.isEnabled }
            ?: DnsProfile.ADGUARD
        cachedActiveProfile = selected
        selected
    }

    fun getActiveProfileCached(): DnsProfile = cachedActiveProfile

    suspend fun getActiveProfile(): DnsProfile {
        return try {
            activeProfileFlow.first()
        } catch (e: Exception) {
            cachedActiveProfile
        }
    }

    suspend fun setActiveProfile(profileId: String) {
        try {
            context.dataStore.edit { prefs ->
                prefs[KEY_ACTIVE_PROFILE_ID] = profileId
            }
        } catch (ignored: Exception) {}
    }

    suspend fun getNextEnabledProfile(): DnsProfile {
        val prefs = try {
            context.dataStore.data.first()
        } catch (e: Exception) {
            return cachedActiveProfile
        }
        val allProfiles = loadProfilesFromPreferences(prefs)
        val enabledProfiles = allProfiles.filter { it.isEnabled }

        if (enabledProfiles.isEmpty()) {
            return DnsProfile.ADGUARD
        }

        if (enabledProfiles.size == 1) {
            return enabledProfiles.first()
        }

        val currentActiveId = prefs[KEY_ACTIVE_PROFILE_ID] ?: enabledProfiles.first().id
        val currentIndex = enabledProfiles.indexOfFirst { it.id == currentActiveId }

        val nextIndex = if (currentIndex >= 0) {
            (currentIndex + 1) % enabledProfiles.size
        } else {
            0
        }

        val nextProfile = enabledProfiles[nextIndex]
        setActiveProfile(nextProfile.id)
        cachedActiveProfile = nextProfile
        return nextProfile
    }

    suspend fun setProfileEnabled(profileId: String, isEnabled: Boolean) {
        try {
            context.dataStore.edit { prefs ->
                val builtIn = DnsProfile.BUILT_IN_PROFILES.find { it.id == profileId }
                if (builtIn != null) {
                    val currentMap = parseEnabledMap(prefs[KEY_BUILTIN_ENABLED_MAP])
                    currentMap[profileId] = isEnabled
                    prefs[KEY_BUILTIN_ENABLED_MAP] = serializeEnabledMap(currentMap)
                } else {
                    val custom = parseCustomProfiles(prefs[KEY_CUSTOM_PROFILES_JSON]).toMutableList()
                    val index = custom.indexOfFirst { it.id == profileId }
                    if (index != -1) {
                        custom[index] = custom[index].copy(isEnabled = isEnabled)
                        prefs[KEY_CUSTOM_PROFILES_JSON] = serializeCustomProfiles(custom)
                    }
                }
            }
        } catch (ignored: Exception) {}
    }

    suspend fun saveCustomProfile(profile: DnsProfile) {
        try {
            context.dataStore.edit { prefs ->
                val custom = parseCustomProfiles(prefs[KEY_CUSTOM_PROFILES_JSON]).toMutableList()
                val index = custom.indexOfFirst { it.id == profile.id }
                if (index != -1) {
                    custom[index] = profile
                } else {
                    custom.add(profile)
                }
                prefs[KEY_CUSTOM_PROFILES_JSON] = serializeCustomProfiles(custom)
            }
        } catch (ignored: Exception) {}
    }

    suspend fun deleteCustomProfile(profileId: String) {
        try {
            context.dataStore.edit { prefs ->
                val custom = parseCustomProfiles(prefs[KEY_CUSTOM_PROFILES_JSON]).toMutableList()
                custom.removeAll { it.id == profileId }
                prefs[KEY_CUSTOM_PROFILES_JSON] = serializeCustomProfiles(custom)

                if (prefs[KEY_ACTIVE_PROFILE_ID] == profileId) {
                    prefs[KEY_ACTIVE_PROFILE_ID] = DnsProfile.ADGUARD.id
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun loadProfilesFromPreferences(prefs: Preferences): List<DnsProfile> {
        return try {
            val builtInEnabledMap = parseEnabledMap(prefs[KEY_BUILTIN_ENABLED_MAP])
            val builtIn = DnsProfile.BUILT_IN_PROFILES.map { p ->
                p.copy(isEnabled = builtInEnabledMap[p.id] ?: true)
            }
            val custom = parseCustomProfiles(prefs[KEY_CUSTOM_PROFILES_JSON])
            builtIn + custom
        } catch (e: Exception) {
            DnsProfile.BUILT_IN_PROFILES
        }
    }

    private fun parseCustomProfiles(jsonStr: String?): List<DnsProfile> {
        if (jsonStr.isNullOrEmpty()) return emptyList()
        return try {
            val list = mutableListOf<DnsProfile>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DnsProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        dohUrl = obj.getString("dohUrl"),
                        protocol = obj.optString("protocol", "DOH"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isBuiltIn = false
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeCustomProfiles(profiles: List<DnsProfile>): String {
        val array = JSONArray()
        profiles.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("dohUrl", p.dohUrl)
            obj.put("protocol", p.protocol)
            obj.put("isEnabled", p.isEnabled)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseEnabledMap(str: String?): MutableMap<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        if (str.isNullOrEmpty()) return map
        try {
            val obj = JSONObject(str)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getBoolean(k)
            }
        } catch (ignored: Exception) {}
        return map
    }

    private fun serializeEnabledMap(map: Map<String, Boolean>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}
