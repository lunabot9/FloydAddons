package floydaddons.not.dogshit.client.events.core

import floydaddons.not.dogshit.client.utils.logError

abstract class CancellableEvent : Event {
    var isCancelled = false
        private set

    fun cancel() {
        isCancelled = true
    }

    override fun postAndCatch(): Boolean {
        runCatching {
            EventBus.post(this)
        }.onFailure {
            logError(it, this)
        }
        return isCancelled
    }
}