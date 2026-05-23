@file:JvmName("Utils")

package com.odtheking.odin.utils

import com.odtheking.odin.FloydAddonsMod
import com.odtheking.odin.FloydAddonsMod.mc
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

inline val String?.noControlCodes: String
    get() {
        val s = this ?: return ""
        val len = s.length

        if (s.indexOf('§') == -1) return s

        val out = CharArray(len)
        var outPos = 0
        var i = 0

        while (i < len) {
            val c = s[i]
            if (c == '§') i += 2
            else {
                out[outPos++] = c
                i++
            }
        }

        return String(out, 0, outPos)
    }

fun logError(throwable: Throwable, context: Any) {
    val message =
        "${FloydAddonsMod.MOD_VERSION} Caught an ${throwable::class.simpleName ?: "error"} at ${context::class.simpleName}."
    FloydAddonsMod.logger.error(message, throwable)

    modMessage(Component.literal("$message §cPlease click this message to copy the FloydAddons error report.").withStyle {
        it
            .withClickEvent(
                ClickEvent.RunCommand(
                    "oddev copy $message \\n``` ${throwable.message} \\n${
                        throwable.stackTraceToString().lineSequence().take(10).joinToString("\n")
                    }```"
                )
            )
            .withHoverEvent(HoverEvent.ShowText(Component.literal("§6Click to copy the error to your clipboard.")))
    })
}

inline val Entity.renderX: Double
    get() =
        xo + (x - xo) * mc.deltaTracker.getGameTimeDeltaPartialTick(true)

inline val Entity.renderY: Double
    get() =
        yo + (y - yo) * mc.deltaTracker.getGameTimeDeltaPartialTick(true)

inline val Entity.renderZ: Double
    get() =
        zo + (z - zo) * mc.deltaTracker.getGameTimeDeltaPartialTick(true)

inline val Entity.renderPos: Vec3
    get() = Vec3(renderX, renderY, renderZ)

inline val Entity.renderBoundingBox: AABB
    get() = boundingBox.move(renderX - x, renderY - y, renderZ - z)
