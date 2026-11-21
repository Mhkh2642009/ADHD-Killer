package com.adhdkiller.app.core

import android.content.Context
import com.adhdkiller.app.data.db.entity.LockdownScheduleEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * A copy of the lockdown schedule that lives in **device-protected storage**.
 *
 * The Room database lives in credential-encrypted storage and is unreadable before the user
 * unlocks the device. The boot receiver needs to know whether a lockdown window is active
 * *before* the first unlock, so the schedule is mirrored into storage that is available
 * during direct boot.
 */
data class MirrorSchedule(
    val label: String,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val strict: Boolean,
)

/**
 * The enforcement state that has to survive a cold boot.
 *
 * Device Owner used to keep this state inside the operating system (a suspended package stays
 * suspended across a reboot, because the system holds it). Without Device Owner the app has to
 * keep it itself, which means writing it to device-protected storage where the boot receiver and
 * the guardian launcher can read it before the first unlock.
 */
data class MirrorGuard(
    /** True while a lockdown window is running. */
    val active: Boolean = false,
    val label: String = "",
    val endsAtMillis: Long? = null,
    /** Packages that stay usable during the lockdown. */
    val allowedPackages: List<String> = emptyList(),
    /** Packages the lockdown blocks. */
    val blockedPackages: List<String> = emptyList(),
    /** Packages the short-form blocker refuses entirely. */
    val strictPackages: List<String> = emptyList(),
)

class ScheduleMirror(context: Context) {

    private val prefs = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun write(schedules: List<LockdownScheduleEntity>) {
        val array = JSONArray()
        schedules.filter { it.enabled }.forEach { schedule ->
            array.put(
                JSONObject().apply {
                    put(KEY_LABEL, schedule.label)
                    put(KEY_DAYS, schedule.daysMask)
                    put(KEY_START, schedule.startMinute)
                    put(KEY_END, schedule.endMinute)
                    put(KEY_STRICT, schedule.strict)
                }
            )
        }
        prefs.edit().putString(KEY_SCHEDULES, array.toString()).apply()
    }

    fun read(): List<MirrorSchedule> {
        val raw = prefs.getString(KEY_SCHEDULES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                MirrorSchedule(
                    label = obj.optString(KEY_LABEL, "Focus"),
                    daysMask = obj.optInt(KEY_DAYS, 0b1111111),
                    startMinute = obj.optInt(KEY_START, 0),
                    endMinute = obj.optInt(KEY_END, 0),
                    strict = obj.optBoolean(KEY_STRICT, true),
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Writes the enforcement state that the boot path and the guardian launcher need.
     */
    fun writeGuard(guard: MirrorGuard) {
        val json = JSONObject().apply {
            put(KEY_ACTIVE, guard.active)
            put(KEY_LABEL, guard.label)
            if (guard.endsAtMillis != null) put(KEY_ENDS_AT, guard.endsAtMillis)
            put(KEY_ALLOWED, JSONArray(guard.allowedPackages))
            put(KEY_BLOCKED, JSONArray(guard.blockedPackages))
            put(KEY_STRICT_PACKAGES, JSONArray(guard.strictPackages))
        }
        prefs.edit().putString(KEY_GUARD, json.toString()).apply()
    }

    /** Reads the mirrored state, applies [transform] and writes it back. */
    fun updateGuard(transform: (MirrorGuard) -> MirrorGuard) {
        writeGuard(transform(readGuard()))
    }

    fun readGuard(): MirrorGuard {
        val raw = prefs.getString(KEY_GUARD, null) ?: return MirrorGuard()
        return runCatching {
            val obj = JSONObject(raw)
            MirrorGuard(
                active = obj.optBoolean(KEY_ACTIVE, false),
                label = obj.optString(KEY_LABEL, ""),
                endsAtMillis = if (obj.has(KEY_ENDS_AT)) obj.optLong(KEY_ENDS_AT) else null,
                allowedPackages = obj.optJSONArray(KEY_ALLOWED).toStringList(),
                blockedPackages = obj.optJSONArray(KEY_BLOCKED).toStringList(),
                strictPackages = obj.optJSONArray(KEY_STRICT_PACKAGES).toStringList(),
            )
        }.getOrDefault(MirrorGuard())
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
    }

    private companion object {
        const val PREFS = "lockdown_schedule_mirror"
        const val KEY_SCHEDULES = "schedules"
        const val KEY_GUARD = "guard"
        const val KEY_LABEL = "label"
        const val KEY_DAYS = "days"
        const val KEY_START = "start"
        const val KEY_END = "end"
        const val KEY_STRICT = "strict"
        const val KEY_ACTIVE = "active"
        const val KEY_ENDS_AT = "ends_at"
        const val KEY_ALLOWED = "allowed"
        const val KEY_BLOCKED = "blocked"
        const val KEY_STRICT_PACKAGES = "strict_packages"
    }
}
