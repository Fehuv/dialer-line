package dev.yasir.dialerline.account

import android.telecom.PhoneAccountHandle

data class OutgoingPhoneAccount(
    val phoneAccountHandle: PhoneAccountHandle,
    val stableKey: String,
    val displayLabel: String,
    val slotIndex: Int?,
    val systemLabel: String?,
)
