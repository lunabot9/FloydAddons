package gg.floyd.features.impl.camera

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.ModuleManager
import gg.floyd.features.impl.render.FloydXray
import net.minecraft.client.CameraType
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Facade over the per-feature camera modules ([FloydFreecam], [FloydFreelook], [FloydF5Customizer]).
 *
 * This used to be a single mega-[Module][gg.floyd.features.Module] that owned every camera setting.
 * Each feature is now its own top-level module; this object keeps all the runtime camera state and
 * math and exposes facade methods that read the new modules so the mixins do not have to change.
 */
object FloydCamera {
    private const val ACCELERATION = 20.0
    private const val MAX_SPEED = 15.0
    private const val SLOWDOWN = 0.05
    private const val DEFAULT_F5_DISTANCE = 4.0f

    private var freecam = false
    private var freelook = false

    private var freecamX = 0.0
    private var freecamY = 0.0
    private var freecamZ = 0.0
    private var freecamYaw = 0f
    private var freecamPitch = 0f
    private var freelookYaw = 0f
    private var freelookPitch = 0f
    private var velForward = 0.0
    private var velLeft = 0.0
    private var velUp = 0.0
    private var lastMoveTime = 0L

    private val freecamSpeed: Float get() = FloydFreecam.speed
    private var freelookDistance: Float
        get() = FloydFreelook.distance
        set(value) { FloydFreelook.distance = value }
    private val f5DisableFront: Boolean get() = FloydF5Customizer.disableFront
    private val f5DisableBack: Boolean get() = FloydF5Customizer.disableBack
    private val f5NoClip: Boolean get() = FloydF5Customizer.noClip
    private val f5ScrollEnabled: Boolean get() = FloydF5Customizer.scrollEnabled
    private val f5ResetOnToggle: Boolean get() = FloydF5Customizer.resetOnToggle
    private var f5Distance: Float
        get() = FloydF5Customizer.f5Distance
        set(value) { FloydF5Customizer.f5Distance = value }

    @JvmStatic
    fun freecamActive(): Boolean = FloydFreecam.enabled && freecam

    @JvmStatic
    fun freelookActive(): Boolean = FloydFreelook.enabled && freelook

    /**
     * Forces freecam/freelook off after configuration load so the player never spawns into a
     * transient camera mode across restarts.
     */
    @JvmStatic
    fun resetTransientModes() {
        if (FloydFreecam.enabled) FloydFreecam.toggle()
        if (FloydFreelook.enabled) FloydFreelook.toggle()
        freecam = false
        freelook = false
        resetFreecamVelocity()
    }

    /** Invoked by [FloydFreecam.onEnable]: capture player position and reset velocity. */
    @JvmStatic
    fun beginFreecam() {
        val player = mc.player ?: return
        freecamX = player.x
        freecamY = player.eyeY
        freecamZ = player.z
        freecamYaw = player.yRot
        freecamPitch = player.xRot
        resetFreecamVelocity()
        lastMoveTime = System.nanoTime()
        freecam = true
        FloydXray.rebuildChunks()
        if (FloydFreelook.enabled) FloydFreelook.toggle()
    }

    /** Invoked by [FloydFreecam.onDisable]. */
    @JvmStatic
    fun endFreecam() {
        freecam = false
        resetFreecamVelocity()
        // Flying the detached camera through terrain leaves stale culling state behind: section
        // occlusion graph computed at the freecam vantage, faces dropped while the camera was inside
        // blocks, particles drawn through walls. Snapping the camera back does not recompute it, so
        // force a full chunk rebuild on the render thread (same flush X-Ray uses on toggle).
        FloydXray.rebuildChunks()
    }

    /** Invoked by [FloydFreelook.onEnable]: set third-person-back view. */
    @JvmStatic
    fun beginFreelook() {
        val player = mc.player ?: return
        freelookYaw = player.yRot
        freelookPitch = player.xRot
        resetFreecamVelocity()
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK)
        freelook = true
        if (FloydFreecam.enabled) FloydFreecam.toggle()
    }

    /** Invoked by [FloydFreelook.onDisable]. */
    @JvmStatic
    fun endFreelook() {
        freelook = false
    }

    @JvmStatic
    fun toggleFreecam() = FloydFreecam.toggle()

    @JvmStatic
    fun toggleFreelook() = FloydFreelook.toggle()

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to true,
        "freecam" to freecam,
        "freelook" to freelook,
        "freecamActive" to freecamActive(),
        "freelookActive" to freelookActive(),
        "freecamSpeed" to freecamSpeed,
        "freelookDistance" to freelookDistance,
        "freecamPosition" to mapOf(
            "x" to freecamX,
            "y" to freecamY,
            "z" to freecamZ,
            "yaw" to freecamYaw,
            "pitch" to freecamPitch
        ),
        "freelookRotation" to mapOf(
            "yaw" to freelookYaw,
            "pitch" to freelookPitch
        ),
        "f5" to mapOf(
            "disableFront" to f5DisableFront,
            "disableBack" to f5DisableBack,
            "noClip" to f5NoClip,
            "scrollEnabled" to f5ScrollEnabled,
            "resetOnToggle" to f5ResetOnToggle,
            "distance" to f5Distance
        )
    )

    @JvmStatic
    fun updateFreecamMovement() {
        if (!freecamActive()) return

        val now = System.nanoTime()
        var dt = (now - lastMoveTime) / 1_000_000_000.0
        lastMoveTime = now
        if (dt <= 0 || dt > 0.1) dt = 0.016

        var fwdImpulse = 0.0
        var leftImpulse = 0.0
        var upImpulse = 0.0
        if (mc.screen == null) {
            if (mc.options.keyUp.isDown) fwdImpulse += 1.0
            if (mc.options.keyDown.isDown) fwdImpulse -= 1.0
            if (mc.options.keyLeft.isDown) leftImpulse += 1.0
            if (mc.options.keyRight.isDown) leftImpulse -= 1.0
            if (mc.options.keyJump.isDown) upImpulse += 1.0
            if (mc.options.keyShift.isDown) upImpulse -= 1.0
        }

        val accel = ACCELERATION * freecamSpeed
        val maxSpeed = MAX_SPEED * freecamSpeed
        velForward = calcVelocity(velForward, fwdImpulse, dt, accel)
        velLeft = calcVelocity(velLeft, leftImpulse, dt, accel)
        velUp = calcVelocity(velUp, upImpulse, dt, accel)

        val yawRad = Math.toRadians(freecamYaw.toDouble())
        val pitchRad = Math.toRadians(freecamPitch.toDouble())
        val lookX = -sin(yawRad) * cos(pitchRad)
        val lookY = -sin(pitchRad)
        val lookZ = cos(yawRad) * cos(pitchRad)
        val leftX = cos(yawRad)
        val leftZ = sin(yawRad)

        var dx = (lookX * velForward + leftX * velLeft) * dt
        var dy = (lookY * velForward + velUp) * dt
        var dz = (lookZ * velForward + leftZ * velLeft) * dt
        val speed = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz) / dt
        if (speed > maxSpeed && speed > 0) {
            val factor = maxSpeed / speed
            velForward *= factor
            velLeft *= factor
            velUp *= factor
            dx *= factor
            dy *= factor
            dz *= factor
        }

        freecamX += dx
        freecamY += dy
        freecamZ += dz
    }

    @JvmStatic fun freecamX(): Double = freecamX
    @JvmStatic fun freecamY(): Double = freecamY
    @JvmStatic fun freecamZ(): Double = freecamZ
    @JvmStatic fun freecamYaw(): Float = freecamYaw
    @JvmStatic fun freecamPitch(): Float = freecamPitch
    @JvmStatic fun freelookYaw(): Float = freelookYaw
    @JvmStatic fun freelookPitch(): Float = freelookPitch
    @JvmStatic fun currentFreelookDistance(): Float = freelookDistance
    @JvmStatic fun currentF5Distance(): Float = f5Distance
    @JvmStatic fun shouldNoClipF5(): Boolean = FloydF5Customizer.enabled && f5NoClip
    @JvmStatic fun shouldScrollF5(): Boolean = FloydF5Customizer.enabled && f5ScrollEnabled
    @JvmStatic fun shouldResetF5OnToggle(): Boolean = FloydF5Customizer.enabled && f5ResetOnToggle
    @JvmStatic fun shouldDisableFrontCamera(): Boolean = FloydF5Customizer.enabled && f5DisableFront
    @JvmStatic fun shouldDisableBackCamera(): Boolean = FloydF5Customizer.enabled && f5DisableBack

    @JvmStatic
    fun nextCameraTypeAfter(current: CameraType, disableFront: Boolean, disableBack: Boolean): CameraType {
        val values = CameraType.entries
        var next = current
        repeat(values.size) {
            next = values[(next.ordinal + 1).floorMod(values.size)]
            if (next == CameraType.THIRD_PERSON_FRONT && disableFront) return@repeat
            if (next == CameraType.THIRD_PERSON_BACK && disableBack) return@repeat
            return next
        }
        return CameraType.FIRST_PERSON
    }

    private fun Int.floorMod(modulus: Int): Int = Math.floorMod(this, modulus)

    @JvmStatic
    fun adjustFreecamLook(deltaX: Double, deltaY: Double) {
        freecamYaw += (deltaX * 0.15).toFloat()
        freecamPitch = (freecamPitch + (deltaY * 0.15).toFloat()).coerceIn(-90f, 90f)
    }

    @JvmStatic
    fun adjustFreelook(deltaX: Double, deltaY: Double) {
        freelookYaw += (deltaX * 0.15).toFloat()
        freelookPitch = (freelookPitch + (deltaY * 0.15).toFloat()).coerceIn(-90f, 90f)
    }

    @JvmStatic
    fun adjustFreelookDistance(scroll: Double) {
        freelookDistance = (freelookDistance - scroll.toFloat() * 0.5f).coerceIn(1.0f, 20.0f)
    }

    @JvmStatic
    fun adjustF5Distance(scroll: Double) {
        f5Distance = (f5Distance - scroll.toFloat() * 0.5f).coerceIn(1.0f, 20.0f)
    }

    @JvmStatic
    fun adjustF5DistanceAndSave(scroll: Double) {
        adjustF5Distance(scroll)
        ModuleManager.saveConfigurations()
    }

    @JvmStatic
    fun onCameraCycle() {
        if (freecamActive()) toggleFreecam()
        if (freelookActive()) toggleFreelook()
        if (shouldResetF5OnToggle()) {
            f5Distance = DEFAULT_F5_DISTANCE
            ModuleManager.saveConfigurations()
        }
    }

    @JvmStatic
    fun disableCameraModes() {
        if (FloydFreecam.enabled) FloydFreecam.toggle()
        if (FloydFreelook.enabled) FloydFreelook.toggle()
    }

    private fun calcVelocity(velocity: Double, impulse: Double, dt: Double, accel: Double): Double {
        if (impulse == 0.0) return velocity * SLOWDOWN.pow(dt)
        val newVelocity = accel * impulse * dt
        return if (impulse.sign == velocity.sign) newVelocity + velocity else newVelocity
    }

    private fun resetFreecamVelocity() {
        velForward = 0.0
        velLeft = 0.0
        velUp = 0.0
    }
}
