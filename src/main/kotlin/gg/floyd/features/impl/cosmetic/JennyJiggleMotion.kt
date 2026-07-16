package gg.floyd.features.impl.cosmetic

import gg.floyd.FloydAddonsMod.mc
import net.minecraft.world.entity.player.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

internal data class JennyJiggleOffset(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

internal class JennyJiggleSpring {
    private var offset = JennyJiggleOffset()
    private var velocity = JennyJiggleOffset()

    fun step(target: JennyJiggleOffset, deltaSeconds: Float): JennyJiggleOffset {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        if (dt <= 0f) return offset
        val acceleration = JennyJiggleOffset(
            (target.x - offset.x) * STIFFNESS - velocity.x * DAMPING,
            (target.y - offset.y) * STIFFNESS - velocity.y * DAMPING,
            (target.z - offset.z) * STIFFNESS - velocity.z * DAMPING,
        )
        velocity = JennyJiggleOffset(
            velocity.x + acceleration.x * dt,
            velocity.y + acceleration.y * dt,
            velocity.z + acceleration.z * dt,
        )
        offset = JennyJiggleOffset(
            (offset.x + velocity.x * dt).coerceIn(-MAX_HORIZONTAL, MAX_HORIZONTAL),
            (offset.y + velocity.y * dt).coerceIn(-MAX_VERTICAL, MAX_VERTICAL),
            (offset.z + velocity.z * dt).coerceIn(-MAX_HORIZONTAL, MAX_HORIZONTAL),
        )
        return offset
    }

    fun current(): JennyJiggleOffset = offset

    companion object {
        private const val STIFFNESS = 92f
        private const val DAMPING = 9.0f
        private const val MAX_HORIZONTAL = 0.075f
        private const val MAX_VERTICAL = 0.052f
    }
}

internal object JennyJiggleMotion {
    private data class EntityMotion(val spring: JennyJiggleSpring, var lastNanos: Long)

    private val motions = ConcurrentHashMap<UUID, EntityMotion>()

    @JvmStatic
    fun sample(entityId: Int): JennyJiggleOffset {
        val player = mc.level?.getEntity(entityId) as? Player ?: return JennyJiggleOffset()
        if (motions.size > 128) motions.clear()
        val now = System.nanoTime()
        val motion = motions.computeIfAbsent(player.uuid) { EntityMotion(JennyJiggleSpring(), now) }
        val deltaSeconds = ((now - motion.lastNanos) / 1_000_000_000.0).toFloat()
        if (deltaSeconds < 1f / 240f) return motion.spring.current()
        motion.lastNanos = now

        val movement = player.deltaMovement
        val yaw = Math.toRadians(player.yRot.toDouble())
        val right = movement.x * cos(yaw) + movement.z * sin(yaw)
        val forward = -movement.x * sin(yaw) + movement.z * cos(yaw)
        val target = JennyJiggleOffset(
            x = (-right * 0.34).toFloat().coerceIn(-0.062f, 0.062f),
            y = (if (player.onGround()) 0f else (movement.y * 0.14).toFloat()).coerceIn(-0.046f, 0.046f),
            z = (forward * 0.34).toFloat().coerceIn(-0.062f, 0.062f),
        )
        return motion.spring.step(target, deltaSeconds)
    }

    fun stateFor(entityId: Int): Map<String, Float> {
        val player = mc.level?.getEntity(entityId) as? Player ?: return zeroState()
        val offset = motions[player.uuid]?.spring?.current() ?: JennyJiggleOffset()
        return mapOf("x" to offset.x, "y" to offset.y, "z" to offset.z)
    }

    private fun zeroState(): Map<String, Float> = mapOf("x" to 0f, "y" to 0f, "z" to 0f)
}
