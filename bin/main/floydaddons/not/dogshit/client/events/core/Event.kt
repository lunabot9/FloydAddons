package floydaddons.not.dogshit.client.events.core

import floydaddons.not.dogshit.client.utils.logError

interface Event {

    fun postAndCatch(): Boolean {
        runCatching {
            EventBus.post(this)
        }.onFailure {
            logError(it, this)
        }
        return false
    }
}