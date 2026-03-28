package dev.yasir.dialerline.state

import android.content.Context
import dev.yasir.dialerline.account.OutgoingPhoneAccount
import dev.yasir.dialerline.account.PhoneAccountResolver

data class SimSelectionState(
    val accounts: List<OutgoingPhoneAccount>,
    val selected: OutgoingPhoneAccount?,
) {
    val canToggle: Boolean = accounts.size >= 2
}

class SimSelectionController(
    private val phoneAccountResolver: PhoneAccountResolver,
    private val selectedAccountStore: SelectedAccountStore,
) {

    fun getState(context: Context): SimSelectionState {
        val accounts = phoneAccountResolver.resolveCallCapableAccounts(context)
        val selected = resolveSelectedAccount(context, accounts)
        return SimSelectionState(accounts = accounts, selected = selected)
    }

    fun getSelectedOrFallback(context: Context): OutgoingPhoneAccount? {
        val state = getState(context)
        return state.selected
    }

    fun toggle(context: Context): OutgoingPhoneAccount? {
        val state = getState(context)
        if (!state.canToggle) {
            return state.selected
        }
        val currentIndex = state.accounts.indexOfFirst { it.stableKey == state.selected?.stableKey }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % state.accounts.size
        val next = state.accounts[nextIndex]
        selectedAccountStore.setSelectedAccountKey(context, next.stableKey)
        return next
    }

    private fun resolveSelectedAccount(
        context: Context,
        accounts: List<OutgoingPhoneAccount>,
    ): OutgoingPhoneAccount? {
        if (accounts.isEmpty()) {
            selectedAccountStore.clear(context)
            return null
        }
        val savedKey = selectedAccountStore.getSelectedAccountKey(context)
        val selected = accounts.firstOrNull { it.stableKey == savedKey } ?: accounts.first()
        if (selected.stableKey != savedKey) {
            selectedAccountStore.setSelectedAccountKey(context, selected.stableKey)
        }
        return selected
    }
}
