package com.plagueid.app.api

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "plagueid_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "name"
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun saveUser(user: UserProfile) {
        prefs.edit().apply {
            putInt(KEY_USER_ID, user.id)
            putString(KEY_EMAIL, user.email)
            putString(KEY_NAME, "${user.firstName} ${user.lastName}")
            apply()
        }
    }

    fun saveSession(token: String, userId: Int, email: String, name: String) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putInt(KEY_USER_ID, userId)
            putString(KEY_EMAIL, email)
            putString(KEY_NAME, name)
            apply()
        }
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getUserName(): String? = prefs.getString(KEY_NAME, null)

    fun getUserEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)

    fun isLoggedIn(): Boolean = !getToken().isNullOrEmpty()

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
