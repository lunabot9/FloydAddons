package gg.floyd.features.impl.camera

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.KeybindSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.clickgui.settings.impl.RuntimeBooleanSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.modMessage
import net.minecraft.client.CameraType
import org.lwjgl.glfw.GLFW
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

object FloydCamera : Module(
    name = "Camera",
    category = Category.CAMERA,
    description = "Floyd freecam, freelook, F5 distance, scroll, and no-clip controls.",
    toggled = true,
) {
    private const val ACCELERATION = 20.0
    private const val MAX_SPEED = 15.0
    private const val SLOWDOWN = 0.05
    private const val DEFAULT_F5_DISTANCE = 4.0f

    var freecam by RuntimeBooleanSetting("Freecam", false, desc = "Detached spectator-style camera.")
    var freelook by RuntimeBooleanSetting("Freelook", false, desc = "Orbit camera around the player.")
    private val freecamKey by KeybindSetting("Toggle Freecam", GLFW.GLFW_KEY_UNKNOWN, desc = "Floyd freecam toggle key.").onPress {
        if (!enabled) toggle()
        toggleFreecam()
        modMessage(if (freecamActive()) "Freecam enabled." else "Freecam disabled.")
    }
    private val freelookKey by KeybindSetting("Toggle Freelook", GLFW.GLFW_KEY_V, desc = "Floyd freelook toggle key.").onPress {
        if (!enabled) toggle()
        toggleFreelook()
        modMessage(if (freelookActive()) "Freelook enabled." else "Freelook disabled.")
    }
    val freecamSpeed by NumberSetting("Speed", 1.0f, 0.1f, 10.0f, 0.1f, desc = "Movement speed for freecam.")
    var freelookDistance by NumberSetting("Distance", 4.0f, 1.0f, 20.0f, 0.5f, desc = "Third-person freelook distance.")
    var f5DisableFront by BooleanSetting("Disable Front Cam", false, desc = "Skips front-facing third person when cycling camera.")
    var f5DisableBack by BooleanSetting("Disable Back Cam", false, desc = "Skips back-facing third person when cycling camera.")
    var f5NoClip by BooleanSetting("No Third-Person Clipping", false, desc = "Stops third-person camera distance from being clipped by blocks.")
    var f5ScrollEnabled by BooleanSetting("Scrolling Changes Distance", false, desc = "Mouse wheel changes third-person camera distance.")
    var f5ResetOnToggle by BooleanSetting("Reset F5 Scrolling", false, desc = "Resets third-person camera distance when cycling camera.")
    var f5Distance by NumberSetting("Camera Distance", DEFAULT_F5_DISTANCE, 1.0f, 20.0f, 0.5f, desc = "Third-person camera distance.")

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

    override fun onDisable() {
        freecam = false
        freelook = false
        resetFreecamVelocity()
        super.onDisable()
    }

    @JvmStatic
    fun freecamActive(): Boolean = enabled && freecam

    @JvmStatic
    fun freelookActive(): Boolean = enabled && freelook

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
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
    fun toggleFreecam() {
        val player = mc.player ?: return
        if (!freecamActive()) {
            freecamX = player.x
            freecamY = player.eyeY
            freecamZ = player.z
            freecamYaw = player.yRot
            freecamPitch = player.xRot
            freelook = false
            resetFreecamVelocity()
            lastMoveTime = System.nanoTime()
            freecam = true
        } else {
            freecam = false
            resetFreecamVelocity()
        }
    }

    @JvmStatic
    fun toggleFreelook() {
        val player = mc.player ?: return
        if (!freelookActive()) {
            freelookYaw = player.yRot
            freelookPitch = player.xRot
            freecam = false
            resetFreecamVelocity()
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK)
            freelook = true
        } else {
            freelook = false
        }
    }

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
    @JvmStatic fun shouldNoClipF5(): Boolean = enabled && f5NoClip
    @JvmStatic fun shouldScrollF5(): Boolean = enabled && f5ScrollEnabled
    @JvmStatic fun shouldResetF5OnToggle(): Boolean = enabled && f5ResetOnToggle
    @JvmStatic fun shouldDisableFrontCamera(): Boolean = enabled && f5DisableFront
    @JvmStatic fun shouldDisableBackCamera(): Boolean = enabled && f5DisableBack

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
        if (freecam) {
            freecam = false
            resetFreecamVelocity()
        }
        freelook = false
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
