package floydaddons.not.dogshit.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

/**
 * Config backing Camera features: Freecam, Freelook, and F5 Customizer.
 * Runtime-only fields (positions/rotations) are not persisted.
 * Persistence goes through FloydAddonsConfig.
 *
 * Freecam movement is frame-based (called from Camera.update every render frame)
 * using nanoTime delta and velocity/acceleration for smooth motion.
 */
public final class CameraConfig {
    private static final double ACCELERATION = 20.0;
    private static final double MAX_SPEED = 15.0;
    private static final double SLOWDOWN = 0.05;

    // --- Freecam ---
    private static volatile boolean freecamEnabled;
    private static double freecamX, freecamY, freecamZ;
    private static float freecamYaw, freecamPitch;
    private static float freecamSpeed = 1.0f;
    private static double velForward, velLeft, velUp;
    private static long lastMoveTime;

    // --- Freelook ---
    private static volatile boolean freelookEnabled;
    private static float freelookYaw, freelookPitch;
    private static float freelookDistance = 4.0f;

    // --- F5 Customizer ---
    private static boolean f5DisableFront;
    private static boolean f5DisableBack;
    private static float f5CameraDistance = 4.0f;
    private static boolean f5ScrollEnabled = true;
    private static boolean f5ResetOnToggle = true;
    private static final float F5_DEFAULT_DISTANCE = 4.0f;

    private CameraConfig() {}

    // ---- Freecam accessors ----

    public static boolean isFreecamEnabled() { return freecamEnabled; }

    public static double getFreecamX() { return freecamX; }
    public static double getFreecamY() { return freecamY; }
    public static double getFreecamZ() { return freecamZ; }
    public static float getFreecamYaw() { return freecamYaw; }
    public static float getFreecamPitch() { return freecamPitch; }
    public static float getFreecamSpeed() { return freecamSpeed; }
    public static void setFreecamSpeed(float v) { freecamSpeed = v; }

    public static void setFreecamYaw(float v) { freecamYaw = v; }
    public static void setFreecamPitch(float v) { freecamPitch = Math.max(-90f, Math.min(90f, v)); }

    public static void toggleFreecam() {
        if (!freecamEnabled) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            freecamX = mc.player.getX();
            freecamY = mc.player.getEyeY();
            freecamZ = mc.player.getZ();
            freecamYaw = mc.player.getYaw();
            freecamPitch = mc.player.getPitch();
            freelookEnabled = false;
            velForward = velLeft = velUp = 0;
            lastMoveTime = System.nanoTime();
            freecamEnabled = true;
        } else {
            freecamEnabled = false;
            velForward = velLeft = velUp = 0;
        }
    }

    /**
     * Called every render frame from Camera.update RETURN inject.
     * Reads key states directly from key bindings and applies
     * velocity-based movement with acceleration/deceleration.
     */
    public static void updateFreecamMovement() {
        if (!freecamEnabled) return;

        long now = System.nanoTime();
        double dt = (now - lastMoveTime) / 1_000_000_000.0;
        lastMoveTime = now;
        if (dt <= 0 || dt > 0.1) dt = 0.016;

        MinecraftClient mc = MinecraftClient.getInstance();
        double fwdImpulse = 0, leftImpulse = 0, upImpulse = 0;

        // Only read input when no screen is open
        if (mc.currentScreen == null) {
            if (mc.options.forwardKey.isPressed()) fwdImpulse += 1;
            if (mc.options.backKey.isPressed()) fwdImpulse -= 1;
            if (mc.options.leftKey.isPressed()) leftImpulse += 1;
            if (mc.options.rightKey.isPressed()) leftImpulse -= 1;
            if (mc.options.jumpKey.isPressed()) upImpulse += 1;
            if (mc.options.sneakKey.isPressed()) upImpulse -= 1;
        }

        double accel = ACCELERATION * freecamSpeed;
        double maxSpd = MAX_SPEED * freecamSpeed;

        velForward = calcVelocity(velForward, fwdImpulse, dt, accel);
        velLeft = calcVelocity(velLeft, leftImpulse, dt, accel);
        velUp = calcVelocity(velUp, upImpulse, dt, accel);

        // Look direction vector (spectator-style: pitch affects forward)
        double yawRad = Math.toRadians(freecamYaw);
        double pitchRad = Math.toRadians(freecamPitch);

        double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

        // Left vector (perpendicular on horizontal plane)
        double leftX = Math.cos(yawRad);
        double leftZ = Math.sin(yawRad);

        double dx = (lookX * velForward + leftX * velLeft) * dt;
        double dy = (lookY * velForward + velUp) * dt;
        double dz = (lookZ * velForward + leftZ * velLeft) * dt;

        // Clamp to max speed
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz) / dt;
        if (speed > maxSpd && speed > 0) {
            double factor = maxSpd / speed;
            velForward *= factor;
            velLeft *= factor;
            velUp *= factor;
            dx *= factor;
            dy *= factor;
            dz *= factor;
        }

        freecamX += dx;
        freecamY += dy;
        freecamZ += dz;
    }

    private static double calcVelocity(double velocity, double impulse, double dt, double accel) {
        if (impulse == 0) {
            return velocity * Math.pow(SLOWDOWN, dt);
        }
        double newVel = accel * impulse * dt;
        if (Math.signum(impulse) == Math.signum(velocity)) {
            newVel += velocity;
        }
        return newVel;
    }

    // ---- Freelook accessors ----

    public static boolean isFreelookEnabled() { return freelookEnabled; }

    public static float getFreelookYaw() { return freelookYaw; }
    public static float getFreelookPitch() { return freelookPitch; }
    public static float getFreelookDistance() { return freelookDistance; }
    public static void setFreelookDistance(float v) { freelookDistance = v; }

    public static void setFreelookYaw(float v) { freelookYaw = v; }
    public static void setFreelookPitch(float v) { freelookPitch = Math.max(-90f, Math.min(90f, v)); }

    public static void toggleFreelook() {
        if (!freelookEnabled) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            freelookYaw = mc.player.getYaw();
            freelookPitch = mc.player.getPitch();
            freecamEnabled = false;
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            freelookEnabled = true;
        } else {
            freelookEnabled = false;
        }
    }

    // ---- F5 Customizer accessors ----

    public static boolean isF5DisableFront() { return f5DisableFront; }
    public static void setF5DisableFront(boolean v) { f5DisableFront = v; }

    public static boolean isF5DisableBack() { return f5DisableBack; }
    public static void setF5DisableBack(boolean v) { f5DisableBack = v; }

    public static float getF5CameraDistance() { return f5CameraDistance; }
    public static void setF5CameraDistance(float v) { f5CameraDistance = v; }

    public static boolean isF5ScrollEnabled() { return f5ScrollEnabled; }
    public static void setF5ScrollEnabled(boolean v) { f5ScrollEnabled = v; }

    public static boolean isF5ResetOnToggle() { return f5ResetOnToggle; }
    public static void setF5ResetOnToggle(boolean v) { f5ResetOnToggle = v; }

    public static float getF5DefaultDistance() { return F5_DEFAULT_DISTANCE; }

    /** Convenience: delegates to the unified config. */
    public static void save() { FloydAddonsConfig.save(); }
}
