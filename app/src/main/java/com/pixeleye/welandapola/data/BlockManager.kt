package com.pixeleye.welandapola.data

import android.content.Context

object BlockManager {
    private const val PREFS_NAME = "welandapola_block_prefs"
    private const val KEY_BLOCKED_USERS = "blocked_user_uids"

    fun blockUser(context: Context, userUid: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blocked = prefs.getStringSet(KEY_BLOCKED_USERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        blocked.add(userUid)
        prefs.edit().putStringSet(KEY_BLOCKED_USERS, blocked).apply()
    }

    fun isUserBlocked(context: Context, userUid: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blocked = prefs.getStringSet(KEY_BLOCKED_USERS, emptySet()) ?: emptySet()
        return blocked.contains(userUid)
    }

    fun getBlockedUsers(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_BLOCKED_USERS, emptySet()) ?: emptySet()
    }

    fun unblockUser(context: Context, userUid: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blocked = prefs.getStringSet(KEY_BLOCKED_USERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        blocked.remove(userUid)
        prefs.edit().putStringSet(KEY_BLOCKED_USERS, blocked).apply()
    }
}
