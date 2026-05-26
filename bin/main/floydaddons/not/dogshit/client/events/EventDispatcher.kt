package floydaddons.not.dogshit.client.events

import floydaddons.not.dogshit.client.FloydAddonsMod.mc
import floydaddons.not.dogshit.client.events.core.onReceive
import floydaddons.not.dogshit.client.utils.ChatManager
import floydaddons.not.dogshit.client.utils.noControlCodes
import floydaddons.not.dogshit.client.utils.render.RenderBatchManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.network.protocol.game.*

object EventDispatcher {

    init {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> WorldEvent.Load.postAndCatch() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> WorldEvent.Unload.postAndCatch() }

        ClientTickEvents.START_CLIENT_TICK.register { client -> TickEvent.ClientStart(client).postAndCatch() }
        ClientTickEvents.END_CLIENT_TICK.register { client -> TickEvent.ClientEnd(client).postAndCatch() }
        ClientTickEvents.START_WORLD_TICK.register { world -> TickEvent.Start(world).postAndCatch() }
        ClientTickEvents.END_WORLD_TICK.register { world -> TickEvent.End(world).postAndCatch() }

        WorldRenderEvents.END_EXTRACTION.register { handler -> RenderEvent.Extract(handler, RenderBatchManager.renderConsumer).postAndCatch() }
        WorldRenderEvents.END_MAIN.register { context -> RenderEvent.Last(context).postAndCatch() }

        ScreenEvents.AFTER_INIT.register { _, screen, _, _ -> ScreenEvent.Open(screen).postAndCatch() }
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            ScreenEvents.remove(screen).register {
                ScreenEvent.Close(screen).postAndCatch()
            }
            ScreenMouseEvents.allowMouseClick(screen).register { screen, event ->
                !ScreenEvent.MouseClick(screen, event).postAndCatch()
            }
            ScreenMouseEvents.allowMouseRelease(screen).register { screen, event ->
                !ScreenEvent.MouseRelease(screen, event).postAndCatch()
            }
            ScreenKeyboardEvents.allowKeyPress(screen).register { screen, event ->
                !ScreenEvent.KeyPress(screen, event).postAndCatch()
            }
        }

        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            if (overlay) return@register true
            !ChatManager.shouldCancelMessage(text)
        }

        onReceive<ClientboundSystemChatPacket> {
            if (!overlay) ChatPacketEvent(content.string.noControlCodes, content).postAndCatch()
        }
    }
}
