package dev.yasir.dialerline.hook

class OutgoingCallInterceptor(
    private val strategies: List<CallPlacementHookStrategy>,
) {
    fun register() {
        strategies.forEach { it.register() }
    }
}
