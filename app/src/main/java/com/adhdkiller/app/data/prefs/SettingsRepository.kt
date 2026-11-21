package com.adhdkiller.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "adhd_settings")

/**
 * User preferences. Nothing here is secret and nothing leaves the device.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val BLOCKER_ENABLED = booleanPreferencesKey("blocker_enabled")
        val BLOCKER_STRICT = booleanPreferencesKey("blocker_strict")
        val LOCKDOWN_ENABLED = booleanPreferencesKey("lockdown_enabled")
        val HARD_MODE = booleanPreferencesKey("hard_mode")
        val ALLOW_LAUNCHER = booleanPreferencesKey("allow_launcher_in_lockdown")
        val ALLOWED_PACKAGES = stringPreferencesKey("allowed_packages")
        val EMERGENCY_NUMBERS = stringPreferencesKey("emergency_numbers")
        val ACCENT_INDEX = intPreferencesKey("accent_index")
        val XP_ENABLED = booleanPreferencesKey("xp_enabled")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val AUTO_START_LOCKDOWN = booleanPreferencesKey("auto_start_lockdown")
        val BLOCK_RULE_SEED_VERSION = intPreferencesKey("block_rule_seed_version")
        val SCREEN_PINNING = booleanPreferencesKey("screen_pinning_enabled")
        val GUARDIAN_LAUNCHER = booleanPreferencesKey("guardian_launcher_enabled")
    }

    val blockerEnabled: Flow<Boolean> = dataStore.data.safe { it[Keys.BLOCKER_ENABLED] ?: true }
    val blockerStrictDefault: Flow<Boolean> = dataStore.data.safe { it[Keys.BLOCKER_STRICT] ?: false }
    val lockdownEnabled: Flow<Boolean> = dataStore.data.safe { it[Keys.LOCKDOWN_ENABLED] ?: false }
    val hardMode: Flow<Boolean> = dataStore.data.safe { it[Keys.HARD_MODE] ?: false }
    val allowLauncherInLockdown: Flow<Boolean> = dataStore.data.safe { it[Keys.ALLOW_LAUNCHER] ?: false }
    val allowedPackages: Flow<List<String>> = dataStore.data.safe {
        it[Keys.ALLOWED_PACKAGES]?.split(',')?.filter { p -> p.isNotBlank() } ?: emptyList()
    }
    val emergencyNumbers: Flow<List<String>> = dataStore.data.safe {
        it[Keys.EMERGENCY_NUMBERS]?.split(',')?.filter { n -> n.isNotBlank() } ?: DEFAULT_EMERGENCY_NUMBERS
    }
    val accentIndex: Flow<Int> = dataStore.data.safe { it[Keys.ACCENT_INDEX] ?: 0 }
    val xpEnabled: Flow<Boolean> = dataStore.data.safe { it[Keys.XP_ENABLED] ?: true }
    val onboarded: Flow<Boolean> = dataStore.data.safe { it[Keys.ONBOARDED] ?: false }
    val autoStartLockdown: Flow<Boolean> = dataStore.data.safe { it[Keys.AUTO_START_LOCKDOWN] ?: true }

    /**
     * Screen pinning is the second enforcement layer the user can switch on. It engages the
     * platform's own pinning mode, which blocks Home and the app switcher. On an unmanaged
     * device the user has to confirm the pinning dialog, and the session can be left with
     * back + recents — the app says so out loud.
     */
    val screenPinningEnabled: Flow<Boolean> = dataStore.data.safe { it[Keys.SCREEN_PINNING] ?: false }

    /**
     * The guardian launcher: this app acting as the device's HOME app. The preference only
     * records the user's intent — whether it is actually in force is read from the system.
     */
    val guardianLauncherEnabled: Flow<Boolean> = dataStore.data.safe { it[Keys.GUARDIAN_LAUNCHER] ?: false }

    /** Version of the shipped default block rules, used to migrate stale seeds. */
    val blockRuleSeedVersion: Flow<Int> = dataStore.data.safe { it[Keys.BLOCK_RULE_SEED_VERSION] ?: 0 }

    suspend fun setBlockerEnabled(value: Boolean) = put(Keys.BLOCKER_ENABLED, value)
    suspend fun setBlockerStrictDefault(value: Boolean) = put(Keys.BLOCKER_STRICT, value)
    suspend fun setLockdownEnabled(value: Boolean) = put(Keys.LOCKDOWN_ENABLED, value)
    suspend fun setHardMode(value: Boolean) = put(Keys.HARD_MODE, value)
    suspend fun setAllowLauncherInLockdown(value: Boolean) = put(Keys.ALLOW_LAUNCHER, value)
    suspend fun setAllowedPackages(value: List<String>) = put(Keys.ALLOWED_PACKAGES, value.joinToString(","))
    suspend fun setEmergencyNumbers(value: List<String>) = put(Keys.EMERGENCY_NUMBERS, value.joinToString(","))
    suspend fun setAccentIndex(value: Int) = put(Keys.ACCENT_INDEX, value)
    suspend fun setXpEnabled(value: Boolean) = put(Keys.XP_ENABLED, value)
    suspend fun setOnboarded(value: Boolean) = put(Keys.ONBOARDED, value)
    suspend fun setAutoStartLockdown(value: Boolean) = put(Keys.AUTO_START_LOCKDOWN, value)
    suspend fun setBlockRuleSeedVersion(value: Int) = put(Keys.BLOCK_RULE_SEED_VERSION, value)
    suspend fun setScreenPinningEnabled(value: Boolean) = put(Keys.SCREEN_PINNING, value)
    suspend fun setGuardianLauncherEnabled(value: Boolean) = put(Keys.GUARDIAN_LAUNCHER, value)

    /**
     * Settings live in credential-encrypted storage, which cannot be read before the device is
     * unlocked for the first time after a boot. Every read therefore falls back to a default
     * instead of throwing.
     */
    private fun <T> Flow<Preferences>.safe(default: (Preferences) -> T): Flow<T> =
        catch { emit(emptyPreferences()) }
            .map { default(it) }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    companion object {
        val DEFAULT_EMERGENCY_NUMBERS = listOf("112", "911", "999", "000", "110", "119")

        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.applicationContext.settingsDataStore)
    }
}
