package gg.floyd.utils.font

import gg.floyd.FloydAddonsMod.mc
import net.fabricmc.fabric.api.event.Event
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Proxy

/** Opt-in bridge fixture: exercise the installed Skyblocker renderer in an integrated world. */
object WorldFontCompatFixture {
    private var enabled = false
    private var registered = false
    private var anchor = Vec3.ZERO
    private var frames = 0L
    private var lastError: String? = null

    fun setEnabled(value: Boolean) {
        check(mc.singleplayerServer != null) { "Integrated-server QA only" }
        if (value && !registered) {
            val callback = Class.forName("de.hysky.skyblocker.utils.render.LevelRenderExtractionCallback")
            val collector = Class.forName("de.hysky.skyblocker.utils.render.primitive.PrimitiveCollector")
            val submit = collector.getMethod("submitText", Component::class.java, Vec3::class.java,
                Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            val listener = Proxy.newProxyInstance(callback.classLoader, arrayOf(callback)) { _, method, args ->
                if (method.declaringClass == callback && enabled && mc.singleplayerServer != null) {
                    runCatching {
                        submit.invoke(args!![0], Component.literal("Skyblocker: Inter normal / Au 016"), anchor, 1.6f, false)
                        submit.invoke(args[0], Component.literal("Skyblocker: Inter see-through 16m"), anchor.add(0.0, -0.65, 0.0), 1.6f, true)
                        frames++
                    }.onFailure { lastError = it.cause?.toString() ?: it.toString() }
                }
                null
            }
            @Suppress("UNCHECKED_CAST")
            (callback.getField("EVENT").get(null) as Event<Any>).register(listener)
            registered = true
        }
        if (value) {
            val player = checkNotNull(mc.player)
            anchor = player.position().add(0.0, 2.3, -6.0)
            lastError = null
        }
        enabled = value
    }

    fun state(): Map<String, Any?> = mapOf("enabled" to enabled, "frames" to frames, "error" to lastError)
}
