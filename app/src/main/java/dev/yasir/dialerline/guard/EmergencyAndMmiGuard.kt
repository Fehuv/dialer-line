package dev.yasir.dialerline.guard

import android.content.Context
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import dev.yasir.dialerline.util.Logger

class EmergencyAndMmiGuard {

    fun shouldOverride(context: Context, uri: Uri?): Boolean {
        val dialString = extractDialString(uri) ?: return false
        val normalized = PhoneNumberUtils.stripSeparators(dialString)
        if (normalized.isBlank()) {
            return false
        }
        if (isEmergencyNumber(context, normalized)) {
            Logger.i("Guard", "event=bypass reason=emergency number=$normalized")
            return false
        }
        return true
    }

    fun extractDialString(uri: Uri?): String? {
        if (uri == null || uri.scheme != SCHEME_TEL) {
            return null
        }
        return Uri.decode(uri.schemeSpecificPart)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun isEmergencyNumber(context: Context, number: String): Boolean {
        return try {
            val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            telephonyManager?.isEmergencyNumber(number) == true
        } catch (throwable: Throwable) {
            Logger.w("Guard", "event=emergency_check_failed number=$number", throwable)
            false
        }
    }

    private companion object {
        const val SCHEME_TEL = "tel"
    }
}
