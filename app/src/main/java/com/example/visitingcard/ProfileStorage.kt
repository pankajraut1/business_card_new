package com.example.visitingcard

import android.content.Context
import android.content.SharedPreferences

class ProfileStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun ensureForUser(userId: String) {
        val cachedUserId = prefs.getString(KEY_USER_ID, null)
        if (cachedUserId == null) {
            prefs.edit().putString(KEY_USER_ID, userId).apply()
            return
        }
        if (cachedUserId != userId) {
            // New user signed in; wipe previous user's cached profile
            prefs.edit().clear().putString(KEY_USER_ID, userId).apply()
        }
    }

    fun saveProfile(profile: Map<String, String>) {
        prefs.edit()
            .putString(KEY_NAME, profile[KEY_NAME])
            .putString(KEY_OCCUPATION, profile[KEY_OCCUPATION])
            .putString(KEY_EMAIL, profile[KEY_EMAIL])
            .putString(KEY_PHONE, profile[KEY_PHONE])
            .putString(KEY_INSTAGRAM, profile[KEY_INSTAGRAM])
            .putString(KEY_WEBSITE, profile[KEY_WEBSITE])
            .putString(KEY_ADDRESS, profile[KEY_ADDRESS])
            .putLong(KEY_SYNCED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getProfile(): Map<String, String> {
        val map = HashMap<String, String>()
        map[KEY_NAME] = prefs.getString(KEY_NAME, "") ?: ""
        map[KEY_OCCUPATION] = prefs.getString(KEY_OCCUPATION, "") ?: ""
        map[KEY_EMAIL] = prefs.getString(KEY_EMAIL, "") ?: ""
        map[KEY_PHONE] = prefs.getString(KEY_PHONE, "") ?: ""
        map[KEY_INSTAGRAM] = prefs.getString(KEY_INSTAGRAM, "") ?: ""
        map[KEY_WEBSITE] = prefs.getString(KEY_WEBSITE, "") ?: ""
        map[KEY_ADDRESS] = prefs.getString(KEY_ADDRESS, "") ?: ""
        return map
    }

    fun hasProfile(): Boolean {
        return (prefs.getString(KEY_NAME, null) != null)
    }

    fun lastSyncedAt(): Long = prefs.getLong(KEY_SYNCED_AT, 0L)

    fun markDirty() {
        prefs.edit().putBoolean(KEY_DIRTY, true).apply()
    }

    fun clearDirty() {
        prefs.edit().putBoolean(KEY_DIRTY, false).apply()
    }

    fun isDirty(): Boolean = prefs.getBoolean(KEY_DIRTY, false)

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "profile_prefs"
        private const val KEY_USER_ID = "profile_user_id"
        const val KEY_NAME = "Name"
        const val KEY_OCCUPATION = "Occupation"
        const val KEY_EMAIL = "Email"
        const val KEY_PHONE = "Phone"
        const val KEY_INSTAGRAM = "Instagram"
        const val KEY_WEBSITE = "Website"
        const val KEY_ADDRESS = "Address"
        private const val KEY_SYNCED_AT = "synced_at"
        private const val KEY_DIRTY = "dirty"
    }
}
