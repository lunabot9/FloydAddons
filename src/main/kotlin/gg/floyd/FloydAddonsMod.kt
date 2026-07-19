package gg.floyd

import gg.floyd.commands.*
import gg.floyd.events.EventDispatcher
import gg.floyd.events.core.EventBus
import gg.floyd.features.ModuleManager
import gg.floyd.features.impl.misc.FloydDiscordPresence
import gg.floyd.features.impl.misc.FloydLocalControl
import gg.floyd.utils.IrisCompatability
import gg.floyd.utils.font.MsdfPipelines
import gg.floyd.utils.handlers.TickTasks
import gg.floyd.utils.render.RenderBatchManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
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
     * @see gg.floyd.config.ModuleConfig
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
    const val MOD_VERSION = "2.3.1"

    val scope = CoroutineScope(SupervisorJob() + EmptyCoroutineContext)

    override fun onInitializeClient() {
        // Register the MSDF text pipelines before the boot resource reload so ShaderManager's
        // eager compile check covers them from the FIRST reload (a broken shader hard-fails the
        // reload instead of silently skipping draws). Map-put only — no GL is touched here.
        MsdfPipelines.bootstrap()

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            arrayOf(
                mainCommand,
                stalkCommand,
                calculatorCommand,
                autoClickerCommand,
            ).forEach { commodore -> commodore.register(dispatcher) }
        }

        listOf(
            this, TickTasks, EventDispatcher,
            IrisCompatability, RenderBatchManager,
            ModuleManager
        ).forEach { EventBus.subscribe(it) }

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
