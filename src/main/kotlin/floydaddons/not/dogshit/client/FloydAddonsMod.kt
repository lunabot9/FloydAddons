package floydaddons.not.dogshit.client

import floydaddons.not.dogshit.client.commands.*
import floydaddons.not.dogshit.client.events.EventDispatcher
import floydaddons.not.dogshit.client.events.core.EventBus
import floydaddons.not.dogshit.client.features.ModuleManager
import floydaddons.not.dogshit.client.features.impl.misc.FloydDiscordPresence
import floydaddons.not.dogshit.client.features.impl.misc.FloydLocalControl
import floydaddons.not.dogshit.client.utils.IrisCompatability
import floydaddons.not.dogshit.client.utils.handlers.TickTasks
import floydaddons.not.dogshit.client.utils.render.ItemStateRenderer
import floydaddons.not.dogshit.client.utils.render.RenderBatchManager
import floydaddons.not.dogshit.client.utils.render.RoundRectPIPRenderer
import floydaddons.not.dogshit.client.utils.ui.rendering.NVGPIPRenderer
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
    val mc: Minecraft
        get() = Minecraft.getInstance() ?: error("Minecraft client is not initialized yet")

    /**
     * Main config file location.
     * @see floydaddons.not.dogshit.client.config.ModuleConfig
     */
    val configFile: File by lazy {
        val gameDirectory = Minecraft.getInstance()?.gameDirectory ?: File("run")
        File(gameDirectory, "config/floydaddons/").apply {
        try {
            if (isFile) delete() // Delete old bugged files that prevent creating the directory
            if (!exists()) mkdirs()
        } catch (e: Exception) {
            println("Error initializing module config\n${e.message}")
            logger.error("Error initializing module config", e)
        }
        }
    }

    const val MOD_ID = "floydaddons"
    const val MOD_NAME = "Floyd Addons"
    const val MOD_VERSION = "2.0.3"

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
