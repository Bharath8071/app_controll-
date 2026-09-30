package com.bharath.focusguard.data.prefs

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the standard 10-minute cooldown enforced after a planned session ends.
 * Even if the user still has remaining total daily budget, the app cannot be used
 * for the next 10 minutes.
 */
class CooldownManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val inMemoryMap = ConcurrentHashMap<String, Long>()

    init {
        // Load non-expired cooldowns into memory cache
        val now = System.currentTimeMillis()
        val all = prefs.all
        for ((key, value) in all) {
            if (value is Long && value > now) {
                inMemoryMap[key] = value
            } else if (value is Long && value <= now) {
                prefs.edit().remove(key).apply()
            }
        }
    }

    /**
     * Starts a standard 10-minute cooldown (or custom duration) for the given package.
     */
    fun startCooldown(packageName: String, durationMillis: Long = DEFAULT_COOLDOWN_MILLIS): Long {
        val expiresAt = System.currentTimeMillis() + durationMillis
        inMemoryMap[packageName] = expiresAt
        prefs.edit().putLong(packageName, expiresAt).apply()
        return expiresAt
    }

    /**
     * Returns the expiry timestamp in millis if currently active, or null if expired or not set.
     */
    fun getCooldownExpiresAt(packageName: String): Long? {
        val now = System.currentTimeMillis()
        val inMem = inMemoryMap[packageName]
        if (inMem != null) {
            if (now < inMem) return inMem
            inMemoryMap.remove(packageName)
            prefs.edit().remove(packageName).apply()
            return null
        }

        val stored = prefs.getLong(packageName, 0L)
        if (stored > now) {
            inMemoryMap[packageName] = stored
            return stored
        } else if (stored > 0L) {
            prefs.edit().remove(packageName).apply()
        }
        return null
    }

    fun isUnderCooldown(packageName: String): Boolean {
        return getCooldownExpiresAt(packageName) != null
    }

    fun hasActiveCooldowns(): Boolean {
        val now = System.currentTimeMillis()
        return inMemoryMap.values.any { it > now }
    }

    fun getRemainingMillis(packageName: String): Long {
        val expiresAt = getCooldownExpiresAt(packageName) ?: return 0L
        return (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun clearCooldown(packageName: String) {
        inMemoryMap.remove(packageName)
        prefs.edit().remove(packageName).apply()
    }

    companion object {
        private const val PREFS_NAME = "focusguard_cooldowns"
        const val DEFAULT_COOLDOWN_MILLIS = 10 * 60_000L // Standard 10 minutes

        @Volatile
        private var instance: CooldownManager? = null

        fun getInstance(context: Context): CooldownManager =
            instance ?: synchronized(this) {
                instance ?: CooldownManager(context).also { instance = it }
            }
    }
}
