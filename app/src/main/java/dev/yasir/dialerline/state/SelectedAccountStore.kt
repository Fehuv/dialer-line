package dev.yasir.dialerline.state

import android.content.Context

class SelectedAccountStore {

    fun getSelectedAccountKey(context: Context): String? =
        preferences(context).getString(KEY_SELECTED_ACCOUNT, null)

    fun setSelectedAccountKey(context: Context, stableKey: String) {
        preferences(context).edit().putString(KEY_SELECTED_ACCOUNT, stableKey).apply()
    }

    fun clear(context: Context) {
        preferences(context).edit().remove(KEY_SELECTED_ACCOUNT).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFERENCES_NAME = "dialer_line_state"
        const val KEY_SELECTED_ACCOUNT = "selected_account_key"
    }
}
