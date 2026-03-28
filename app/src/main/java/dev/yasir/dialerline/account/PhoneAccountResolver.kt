package dev.yasir.dialerline.account

import android.content.Context
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import dev.yasir.dialerline.util.Logger
import java.util.Locale

class PhoneAccountResolver {

    fun resolveCallCapableAccounts(context: Context): List<OutgoingPhoneAccount> {
        val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        val rawHandles = try {
            telecomManager.callCapablePhoneAccounts.orEmpty()
        } catch (throwable: Throwable) {
            Logger.w("Accounts", "event=query_failed source=telecom", throwable)
            emptyList()
        }
        if (rawHandles.isEmpty()) {
            return emptyList()
        }

        val subscriptions = loadSubscriptions(context)
        val provisionalAccounts = rawHandles.mapNotNull { handle ->
            toOutgoingAccount(telecomManager, handle, subscriptions)
        }

        val sorted = provisionalAccounts.sortedWith(
            compareBy<OutgoingPhoneAccount>(
                { it.slotIndex ?: Int.MAX_VALUE },
                { it.displayLabel.lowercase(Locale.US) },
                { it.stableKey },
            ),
        )

        return sorted
    }

    private fun toOutgoingAccount(
        telecomManager: TelecomManager,
        handle: PhoneAccountHandle,
        subscriptions: List<SubscriptionInfo>,
    ): OutgoingPhoneAccount? {
        return try {
            val phoneAccount = telecomManager.getPhoneAccount(handle) ?: return null
            val accountMetadata = resolveAccountMetadata(handle, phoneAccount, subscriptions)
            val stableKey = stableKeyFor(handle)
            val systemLabel = phoneAccount.label?.toString()?.takeIf { it.isNotBlank() }
            val carrierLabel = accountMetadata.subscriptionInfo?.carrierName?.toString()?.takeIf { it.isNotBlank() }
            val displayLabel = preferredDisplayLabel(
                slotIndex = accountMetadata.slotIndex,
                carrierLabel = carrierLabel,
                systemLabel = systemLabel,
            )
            OutgoingPhoneAccount(
                phoneAccountHandle = handle,
                stableKey = stableKey,
                displayLabel = displayLabel,
                slotIndex = accountMetadata.slotIndex,
                systemLabel = systemLabel,
            )
        } catch (throwable: Throwable) {
            Logger.w("Accounts", "event=account_map_failed key=${stableKeyFor(handle)}", throwable)
            null
        }
    }

    private fun loadSubscriptions(context: Context): List<SubscriptionInfo> {
        return try {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            subscriptionManager?.activeSubscriptionInfoList.orEmpty()
        } catch (throwable: Throwable) {
            Logger.w("Accounts", "event=query_failed source=subscription", throwable)
            emptyList()
        }
    }

    private fun resolveAccountMetadata(
        handle: PhoneAccountHandle,
        phoneAccount: PhoneAccount,
        subscriptions: List<SubscriptionInfo>,
    ): AccountMetadata {
        val extras = phoneAccount.extras
        // Slot metadata is not standardized across vendors, so keep all heuristics confined here.
        val explicitSlotIndex = knownSlotIndex(extras)
        val subscriptionId = knownSubscriptionId(extras)
        val directSubscription = subscriptionId?.let { id ->
            subscriptions.firstOrNull { it.subscriptionId == id }
        }
        if (explicitSlotIndex != null || directSubscription != null) {
            return AccountMetadata(
                slotIndex = explicitSlotIndex ?: directSubscription?.simSlotIndex?.takeIf { it >= 0 },
                subscriptionInfo = directSubscription,
            )
        }

        val handleId = handle.id.orEmpty()
        val label = phoneAccount.label?.toString().orEmpty()
        val matchingSubscription = subscriptions.firstOrNull { subscription ->
            val subscriptionIdString = subscription.subscriptionId.toString()
            handleId == subscriptionIdString ||
                handleId.contains(subscriptionIdString) ||
                label.equals(subscription.carrierName?.toString(), ignoreCase = true)
        }
        return AccountMetadata(
            slotIndex = matchingSubscription?.simSlotIndex?.takeIf { it >= 0 },
            subscriptionInfo = matchingSubscription,
        )
    }

    private fun preferredDisplayLabel(
        slotIndex: Int?,
        carrierLabel: String?,
        systemLabel: String?,
    ): String {
        val cleanedCarrierLabel = carrierLabel?.takeUnless(::isGenericSimLabel)
        val cleanedSystemLabel = systemLabel?.takeUnless(::isGenericSimLabel)
        return cleanedCarrierLabel
            ?: cleanedSystemLabel
            ?: slotIndex?.let { "SIM ${it + 1}" }
            ?: DEFAULT_SIM_LABEL
    }

    private fun knownSlotIndex(extras: Bundle?): Int? {
        if (extras == null) {
            return null
        }
        for (key in SLOT_INDEX_KEYS) {
            if (extras.containsKey(key)) {
                val value = extras.getInt(key, INVALID_SLOT)
                if (value != INVALID_SLOT) {
                    return value
                }
            }
        }
        return null
    }

    private fun knownSubscriptionId(extras: Bundle?): Int? {
        if (extras == null) {
            return null
        }
        for (key in SUBSCRIPTION_ID_KEYS) {
            if (extras.containsKey(key)) {
                return extras.getInt(key, INVALID_SLOT).takeIf { it != INVALID_SLOT }
            }
        }
        return null
    }

    private fun stableKeyFor(handle: PhoneAccountHandle): String =
        "${handle.componentName.flattenToShortString()}|${handle.id}"

    private fun isGenericSimLabel(label: String): Boolean {
        return label.startsWith(LABEL_SIM_PREFIX, ignoreCase = true) || label.equals(DEFAULT_SIM_LABEL, ignoreCase = true)
    }

    private data class AccountMetadata(
        val slotIndex: Int?,
        val subscriptionInfo: SubscriptionInfo?,
    )

    private companion object {
        const val INVALID_SLOT = -1
        const val LABEL_SIM_PREFIX = "SIM "
        const val DEFAULT_SIM_LABEL = "SIM"

        val SLOT_INDEX_KEYS = listOf(
            "android.telecom.extra.SLOT_INDEX",
            "android.telephony.extra.SLOT_INDEX",
            "slotIndex",
            "slot_index",
            "simSlotIndex",
        )

        val SUBSCRIPTION_ID_KEYS = listOf(
            "android.telecom.extra.SUBSCRIPTION_ID",
            "android.telephony.extra.SUBSCRIPTION_INDEX",
            "subscriptionId",
            "subId",
        )
    }
}
