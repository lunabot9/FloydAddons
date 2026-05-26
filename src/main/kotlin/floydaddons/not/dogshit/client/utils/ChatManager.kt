package floydaddons.not.dogshit.client.utils

import floydaddons.not.dogshit.client.events.ChatPacketEvent
import net.minecraft.network.chat.Component
import java.util.concurrent.ConcurrentLinkedQueue

object ChatManager {
    private val cancelQueue = ConcurrentLinkedQueue<Component>()

    fun ChatPacketEvent.hideMessage() {
        cancelQueue.add(component)
    }

    internal fun shouldCancelMessage(message: Component): Boolean {
        return cancelQueue.remove(message)
    }
}