package gg.floyd.events

import com.mojang.blaze3d.platform.InputConstants
import gg.floyd.events.core.CancellableEvent
import gg.floyd.events.core.Event
import gg.floyd.utils.render.RenderConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.AbstractLevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet

class InputEvent(val key: InputConstants.Key) : CancellableEvent() // better mixin is prob ideal no need for cancellable

class ChatPacketEvent(val value: String, val component: Component) : Event // mixin instead of packet (still needs to run before vanilla processing for hideMessage()

interface TickEvent : Event {
    class ClientStart(val client: Minecraft) : TickEvent
    class ClientEnd(val client: Minecraft) : TickEvent
    class Start(val world: ClientLevel) : TickEvent
    class End(val world: ClientLevel) : TickEvent
    object Server : TickEvent
}

interface WorldEvent : Event {
    object Load : WorldEvent
    object Unload : WorldEvent
}

abstract class RenderEvent(open val context: AbstractLevelRenderContext) : Event {
    class Extract(override val context: LevelExtractionContext, val consumer: RenderConsumer) : RenderEvent(context)
    class Last(override val context: LevelRenderContext) : RenderEvent(context)
}

abstract class PartyEvent(val members: List<String>) : Event {
    class Leave(members: List<String>) : PartyEvent(members)
}

abstract class PacketEvent(val packet: Packet<*>) : CancellableEvent() { // ideally used less
    class Receive(packet: Packet<*>) : PacketEvent(packet)
    class Send(packet: Packet<*>) : PacketEvent(packet)
}
