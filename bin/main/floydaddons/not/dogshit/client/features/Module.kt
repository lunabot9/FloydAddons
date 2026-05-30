package floydaddons.not.dogshit.client.features

import floydaddons.not.dogshit.client.FloydAddonsMod
import floydaddons.not.dogshit.client.clickgui.settings.AlwaysActive
import floydaddons.not.dogshit.client.clickgui.settings.Setting
import floydaddons.not.dogshit.client.clickgui.settings.impl.HUDSetting
import floydaddons.not.dogshit.client.events.core.EventBus
import floydaddons.not.dogshit.client.features.impl.render.ClickGUIModule
import floydaddons.not.dogshit.client.utils.modMessage
import net.minecraft.client.gui.GuiGraphics
import org.lwjgl.glfw.GLFW
import kotlin.reflect.full.hasAnnotation

/**
 * Class that represents a module. And handles all the settings.
 * @author Aton
 */
abstract class Module(
    val name: String,
    val key: Int? = GLFW.GLFW_KEY_UNKNOWN,
    category: Category? = null,
    @Transient var description: String,
    toggled: Boolean = false,
) {

    /**
     * Map containing all settings for the module,
     * where the key is the name of the setting.
     *
     * Since the map is a [LinkedHashMap], order is preserved.
     */
    val settings: LinkedHashMap<String, Setting<*>> = linkedMapOf()

    /**
     * Category for this module.
     */
    @Transient
    val category: Category = category ?: getCategoryFromPackage(this::class.java)

    /**
     * Whether this module should appear in the click GUI/module browser.
     */
    @Transient
    open val visibleInGui: Boolean = true

    /**
     * Flag for if the module is enabled/disabled.
     *
     * When true, it is registered to the [EventBus].
     * When false, it is unregistered, unless the module has the [AlwaysActive] annotation.
     */
    var enabled: Boolean = toggled
        private set

    protected inline val mc get() = FloydAddonsMod.mc

    /**
     * Indicates if the module has the annotation [AlwaysActive],
     * which keeps the module registered to the eventbus, even if disabled
     */
    @Transient
    val alwaysActive = this::class.hasAnnotation<AlwaysActive>()

    init {
        if (alwaysActive || enabled) {
            @Suppress("LeakingThis")
            EventBus.subscribe(this)
        }
    }

    /**
     * Invoked when module is enabled.
     *
     * It is recommended to call super so it can properly subscribe to the eventbus
     */
    open fun onEnable() {
        if (!alwaysActive) EventBus.subscribe(this)
    }

    /**
     * Invoked when module is disabled.
     *
     * It is recommended to call super so it can properly subscribe to the eventbus
     */
    open fun onDisable() {
        if (!alwaysActive) EventBus.unsubscribe(this)
    }

    /**
     * Invoked when the main keybind is pressed.
     *
     * By default, it toggles the module.
     */
    open fun onKeybind() {
        toggle()
        if (ClickGUIModule.enableNotification) modMessage("$name ${if (enabled) "§aenabled" else "§cdisabled"}.")
    }

    /**
     * Toggles the module and invokes [onEnable]/[onDisable].
     */
    fun toggle() {
        enabled = !enabled
        if (enabled) onEnable()
        else onDisable()
    }

    /**
     * Registers a [Setting] to this module and returns itself.
     */
    fun <K : Setting<*>> registerSetting(setting: K): K {
        settings[setting.name] = setting
        return setting
    }

    operator fun <K : Setting<*>> K.unaryPlus(): K = registerSetting(this)

    @Suppress("FunctionName")
    fun HUD(
        name: String,
        desc: String,
        toggleable: Boolean = true,
        x: Int = 10,
        y: Int = 10,
        scale: Float = 2f,
        anchorRight: Boolean = false,
        block: GuiGraphics.(example: Boolean) -> Pair<Int, Int>
    ): HUDSetting = HUDSetting(name, x, y, scale, toggleable, anchorRight, desc, this, block)

    private companion object {
        private fun getCategoryFromPackage(clazz: Class<out Module>): Category {
            val packageName = clazz.packageName
            return when {
                packageName.contains("render") -> Category.RENDER
                packageName.contains("hiders") -> Category.HIDERS
                packageName.contains("player") -> Category.PLAYER
                packageName.contains("camera") -> Category.CAMERA
                packageName.contains("cosmetic") -> Category.COSMETIC
                packageName.contains("qol") -> Category.QOL
                packageName.contains("misc") -> Category.MISC
                else -> throw IllegalStateException(
                    "Module ${clazz.name} failed to get category from the package it is in." +
                            "Either manually assign a category," +
                            " or put it under any valid Floyd package (render, hiders, player, camera, cosmetic, qol, misc))"
                )
            }
        }
    }
}
