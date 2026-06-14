package gg.floyd

import gg.floyd.commands.*
import gg.floyd.events.EventDispatcher
import gg.floyd.events.core.EventBus
import gg.floyd.features.ModuleManager
import gg.floyd.features.impl.misc.FloydDiscordPresence
import gg.floyd.features.impl.misc.FloydLocalControl
import gg.floyd.mixin.FloydMixinErrorHandler
import gg.floyd.utils.IrisCompatability
import gg.floyd.utils.errorMessage
import gg.floyd.utils.font.MsdfPipelines
import gg.floyd.utils.handlers.TickTasks
import gg.floyd.utils.render.ItemStateRenderer
import gg.floyd.utils.render.RenderBatchManager
import gg.floyd.utils.render.PanelBlurPIPRenderer
import gg.floyd.utils.render.RoundRectPIPRenderer
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
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
    const val MOD_VERSION = "2.2.0"

    val scope = CoroutineScope(SupervisorJob() + EmptyCoroutineContext)

    /** Guards the one-shot in-game notice about features a mod conflict disabled (see below). */
    private var mixinConflictNoticeShown = false

    override fun onInitializeClient() {
        // Register the MSDF text pipelines before the boot resource reload so ShaderManager's
        // eager compile check covers them from the FIRST reload (a broken shader hard-fails the
        // reload instead of silently skipping draws). Map-put only — no GL is touched here.
        MsdfPipelines.bootstrap()

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
            PanelBlurPIPRenderer(context.vertexConsumers())
        }

        SpecialGuiElementRegistry.register { context ->
            ItemStateRenderer(context.vertexConsumers())
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            onClientStopping()
        }

        // If a mod/launcher conflict forced FloydMixinErrorHandler to skip a mixin (instead of
        // crashing), tell the player which feature is unavailable once chat exists.
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> announceMixinConflictsOnce() }
    }

    private fun announceMixinConflictsOnce() {
        if (mixinConflictNoticeShown) return
        val disabled = FloydMixinErrorHandler.disabledFeatures()
        if (disabled.isEmpty()) return
        mixinConflictNoticeShown = true
        errorMessage("Disabled due to a conflict with another mod: ${disabled.joinToString(", ")}.")
    }

    internal fun onClientStopping() {
        FloydLocalControl.stop()
        FloydDiscordPresence.shutdown()
        ModuleManager.saveConfigurations()
    }
}
