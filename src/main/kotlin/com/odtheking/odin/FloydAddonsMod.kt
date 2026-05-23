package com.odtheking.odin

import com.odtheking.odin.commands.*
import com.odtheking.odin.events.EventDispatcher
import com.odtheking.odin.events.core.EventBus
import com.odtheking.odin.features.ModuleManager
import com.odtheking.odin.features.impl.misc.FloydDiscordPresence
import com.odtheking.odin.features.impl.misc.FloydLocalControl
import com.odtheking.odin.utils.IrisCompatability
import com.odtheking.odin.utils.handlers.TickTasks
import com.odtheking.odin.utils.render.ItemStateRenderer
import com.odtheking.odin.utils.render.RenderBatchManager
import com.odtheking.odin.utils.render.RoundRectPIPRenderer
import com.odtheking.odin.utils.ui.rendering.NVGPIPRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

object FloydAddonsMod : ClientModInitializer {

    val logger: Logger = LogManager.getLogger("FloydAddons")

    @JvmStatic
    val mc: Minecraft = Minecraft.getInstance()

    /**
     * Main config file location.
     * @see com.odtheking.odin.config.ModuleConfig
     */
    val configFile: File = File(mc.gameDirectory, "config/floydaddons/").apply {
        try {
            if (isFile) delete() // Delete old bugged files that prevent creating the directory
            if (!exists()) mkdirs()
        } catch (e: Exception) {
            println("Error initializing module config\n${e.message}")
            logger.error("Error initializing module config", e)
        }
    }

    const val MOD_ID = "floydaddons"
    const val MOD_NAME = "Floyd Addons"
    const val MOD_VERSION = "0.1.0"

    val scope = CoroutineScope(SupervisorJob() + EmptyCoroutineContext)

    override fun onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            arrayOf(
                mainCommand,
                stalkCommand,
            ).forEach { commodore -> commodore.register(dispatcher) }
        }

        listOf(
            this, TickTasks, EventDispatcher,
            IrisCompatability, RenderBatchManager,
            ModuleManager
        ).forEach { EventBus.subscribe(it) }

        SpecialGuiElementRegistry.register { context ->
            NVGPIPRenderer(context.vertexConsumers())
        }

        SpecialGuiElementRegistry.register { context ->
            RoundRectPIPRenderer(context.vertexConsumers())
        }

        SpecialGuiElementRegistry.register { context ->
            ItemStateRenderer(context.vertexConsumers())
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            onClientStopping()
        }
    }

    internal fun onClientStopping() {
        FloydLocalControl.stop()
        FloydDiscordPresence.shutdown()
        ModuleManager.saveConfigurations()
    }
}
