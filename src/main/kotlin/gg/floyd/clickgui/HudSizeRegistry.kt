package gg.floyd.clickgui

/**
 * Pre-draw size estimates for movable HUD elements, keyed by HUD setting name.
 *
 * HUD-owning modules register their own estimator at module init (so [HudManager] does not have to
 * hardcode element names); unknown HUDs fall back to [DEFAULT_HUD_SIZE]. This is a plain object with
 * no Minecraft/Screen dependency so modules can register from their static initializers without
 * forcing the (Minecraft-dependent) [HudManager] screen to initialize.
 */
object HudSizeRegistry {
    private val DEFAULT_HUD_SIZE = 120 to 40
    private val estimators: MutableMap<String, () -> Pair<Int, Int>> = linkedMapOf()

    fun register(name: String, estimator: () -> Pair<Int, Int>) {
        estimators[name] = estimator
    }

    fun estimate(name: String): Pair<Int, Int> = estimators[name]?.invoke() ?: DEFAULT_HUD_SIZE
}
