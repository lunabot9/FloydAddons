package gg.floyd.features.impl.pvp

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.platform.InputConstants
import gg.floyd.clickgui.settings.Setting.Companion.withDependency
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.KeybindSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.clickgui.settings.impl.StringSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.customData
import gg.floyd.mixin.accessors.KeyMappingAccessor
import net.minecraft.client.KeyMapping
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import org.lwjgl.glfw.GLFW
import kotlin.jvm.optionals.getOrNull
import kotlin.random.Random

/**
 * Auto clicker adapted from skies-starred/OdinClient's BSD-3-Clause AutoClicker module.
 * Floyd owns the persistence and click invokers; the user-facing behavior mirrors the reference.
 */
object FloydAutoClicker : Module(
    name = "Auto Clicker",
    category = Category.PVP,
    description = "Automatically left-clicks, right-clicks, or both while configured hold keys are pressed.",
) {
    private val whitelistOnly by BooleanSetting(
        "Whitelist Only",
        false,
        desc = "Only clicks while holding an item added with /ac add left|right or /ac a left|right.",
    )
    private val allowBreaking by BooleanSetting(
        "Allow Breaking Blocks",
        false,
        desc = "Continues breaking the targeted block while the left-click activation key is held.",
    )
    private val blockDungeonBreaker by BooleanSetting(
        "Block Dungeon Breaker",
        true,
        desc = "Prevents the auto clicker from running while Dungeon Breaker is held.",
    )
    private val terminatorOnly by BooleanSetting(
        "Terminator Only",
        false,
        desc = "Left-clicks at the configured CPS only while holding Terminator and holding use.",
    )
    private val minimumCps by NumberSetting(
        "Minimum CPS",
        5.0f,
        3.0,
        15.0,
        0.5,
        desc = "Minimum clicks per second for Terminator-only mode.",
        unit = " CPS",
    ).withDependency { terminatorOnly }
    private val maximumCps by NumberSetting(
        "Maximum CPS",
        8.0f,
        3.0,
        15.0,
        0.5,
        desc = "Maximum clicks per second for Terminator-only mode.",
        unit = " CPS",
    ).withDependency { terminatorOnly }

    private val enableLeftClick by BooleanSetting(
        "Enable Left Click",
        true,
        desc = "Enables left-click automation.",
    ).withDependency { !terminatorOnly }
    private val enableRightClick by BooleanSetting(
        "Enable Right Click",
        true,
        desc = "Enables right-click automation.",
    ).withDependency { !terminatorOnly }
    private val maximumLeftCps by NumberSetting(
        "Maximum Left CPS",
        8.0f,
        3.0,
        15.0,
        0.5,
        desc = "Maximum left clicks per second.",
        unit = " CPS",
    ).withDependency { !terminatorOnly }
    private val minimumLeftCps by NumberSetting(
        "Minimum Left CPS",
        5.0f,
        3.0,
        15.0,
        0.5,
        desc = "Minimum left clicks per second.",
        unit = " CPS",
    ).withDependency { !terminatorOnly }
    private val maximumRightCps by NumberSetting(
        "Maximum Right CPS",
        8.0f,
        3.0,
        15.0,
        0.5,
        desc = "Maximum right clicks per second.",
        unit = " CPS",
    ).withDependency { !terminatorOnly }
    private val minimumRightCps by NumberSetting(
        "Minimum Right CPS",
        5.0f,
        3.0,
        15.0,
        0.5,
        desc = "Minimum right clicks per second.",
        unit = " CPS",
    ).withDependency { !terminatorOnly }
    private val leftClickKeybind by KeybindSetting(
        "Left Click",
        GLFW.GLFW_KEY_UNKNOWN,
        desc = "Hold this key to run left-click automation.",
    ).withDependency { !terminatorOnly }
    private val rightClickKeybind by KeybindSetting(
        "Right Click",
        GLFW.GLFW_KEY_UNKNOWN,
        desc = "Hold this key to run right-click automation.",
    ).withDependency { !terminatorOnly }

    private var leftWhitelistJson by StringSetting(
        "Left Whitelist Data", "[]", 16_384, desc = "Internal left-click whitelist storage.",
    ).hide()
    private var rightWhitelistJson by StringSetting(
        "Right Whitelist Data", "[]", 16_384, desc = "Internal right-click whitelist storage.",
    ).hide()

    private var nextLeftClickAt = 0L
    private var nextRightClickAt = 0L
    private var previousVanillaAttackDown: Boolean? = null

    override fun onDisable() {
        nextLeftClickAt = 0L
        nextRightClickAt = 0L
        restoreVanillaAttackKey()
        super.onDisable()
    }

    @JvmStatic
    fun beforeVanillaKeybinds() {
        restoreVanillaAttackKey()
        if (!enabled) return
        val player = mc.player ?: return
        if (mc.screen != null || player.isUsingItem) return
        val now = System.currentTimeMillis()

        if (terminatorOnly) {
            if (mc.gameMode?.isDestroying == true) return
            if (heldSkyBlockId() != "TERMINATOR" || !mc.options.keyUse.isDown || now < nextLeftClickAt) return
            nextLeftClickAt = now + clickDelayMs(minimumCps.toDouble(), maximumCps.toDouble())
            queueVanillaClick(mc.options.keyAttack)
            return
        }

        if (blockDungeonBreaker && heldSkyBlockId() == "DUNGEONBREAKER") return

        val held = heldIdentity() ?: return
        val leftAllowed = !whitelistOnly || held in leftWhitelist()
        val rightAllowed = !whitelistOnly || held in rightWhitelist()
        if (!leftAllowed && !rightAllowed) return
        val leftHeld = leftAllowed && enableLeftClick && leftClickKeybind.isHeld()
        val rightHeld = rightAllowed && enableRightClick && rightClickKeybind.isHeld()
        val hit = mc.hitResult as? BlockHitResult
        val level = mc.level
        val targetingSolidBlock = hit != null && level != null && !level.getBlockState(hit.blockPos).isAir
        val action = if (leftHeld) leftClickAction(allowBreaking, targetingSolidBlock) else LeftClickAction.SKIP
        if (shouldFeedVanillaAttack(action)) {
            overrideVanillaAttackHold(true)
        } else if (shouldSuppressVanillaAttackHold(action)) {
            overrideVanillaAttackHold(false)
        }
        if ((action == LeftClickAction.ATTACK || action == LeftClickAction.CLICK_BLOCK) &&
            mc.gameMode?.isDestroying != true && now >= nextLeftClickAt
        ) {
            nextLeftClickAt = now + clickDelayMs(minimumLeftCps.toDouble(), maximumLeftCps.toDouble())
            queueVanillaClick(mc.options.keyAttack)
        }

        if (rightHeld && shouldQueueRightClick(mc.options.keyUse.isDown) && now >= nextRightClickAt) {
            nextRightClickAt = now + clickDelayMs(minimumRightCps.toDouble(), maximumRightCps.toDouble())
            queueVanillaClick(mc.options.keyUse)
        }
    }

    @JvmStatic
    fun afterVanillaKeybinds() {
        restoreVanillaAttackKey()
    }

    fun heldIdentity(): String? {
        val stack = mc.player?.mainHandItem ?: return null
        if (stack.isEmpty) return null
        return heldIdentity(
            stack.customData.getString("uuid").getOrNull(),
            stack.customData.getString("id").getOrNull(),
            stack.hoverName.string,
        )
    }

    fun addWhitelist(side: ClickSide, item: String): Boolean {
        val values = whitelist(side).toMutableSet()
        if (!values.add(item)) return false
        saveWhitelist(side, values)
        return true
    }

    fun removeWhitelist(side: ClickSide, item: String): Boolean {
        val values = whitelist(side).toMutableSet()
        if (!values.remove(item)) return false
        saveWhitelist(side, values)
        return true
    }

    fun clearWhitelist(side: ClickSide?) {
        if (side == null || side == ClickSide.LEFT) saveWhitelist(ClickSide.LEFT, emptySet())
        if (side == null || side == ClickSide.RIGHT) saveWhitelist(ClickSide.RIGHT, emptySet())
    }

    fun whitelistSummary(): String = "Auto Clicker whitelist:\nLeft: ${summary(leftWhitelist())}\nRight: ${summary(rightWhitelist())}"

    fun whitelist(side: ClickSide): Set<String> = when (side) {
        ClickSide.LEFT -> leftWhitelist()
        ClickSide.RIGHT -> rightWhitelist()
    }

    private fun leftWhitelist(): Set<String> = decodeWhitelist(leftWhitelistJson)
    private fun rightWhitelist(): Set<String> = decodeWhitelist(rightWhitelistJson)

    private fun saveWhitelist(side: ClickSide, values: Set<String>) {
        val encoded = encodeWhitelist(values)
        when (side) {
            ClickSide.LEFT -> leftWhitelistJson = encoded
            ClickSide.RIGHT -> rightWhitelistJson = encoded
        }
        ModuleManager.saveConfigurations()
    }

    private fun heldSkyBlockId(): String? = mc.player?.mainHandItem?.skyBlockId()

    private fun ItemStack.skyBlockId(): String? = customData.getString("id").getOrNull()

    private fun InputConstants.Key.isHeld(): Boolean {
        if (this == InputConstants.UNKNOWN || value < 0) return false
        return if (value > GLFW.GLFW_MOUSE_BUTTON_LAST) InputConstants.isKeyDown(mc.window, value)
        else GLFW.glfwGetMouseButton(mc.window.handle(), value) == GLFW.GLFW_PRESS
    }

    enum class ClickSide { LEFT, RIGHT }

    internal enum class LeftClickAction { SKIP, ATTACK, CLICK_BLOCK, BREAK_BLOCK }

    internal fun leftClickAction(allowBreaking: Boolean, targetingSolidBlock: Boolean): LeftClickAction = when {
        targetingSolidBlock && allowBreaking -> LeftClickAction.BREAK_BLOCK
        targetingSolidBlock -> LeftClickAction.CLICK_BLOCK
        else -> LeftClickAction.ATTACK
    }

    internal fun shouldFeedVanillaAttack(action: LeftClickAction): Boolean = action == LeftClickAction.BREAK_BLOCK

    internal fun shouldSuppressVanillaAttackHold(action: LeftClickAction): Boolean =
        action == LeftClickAction.CLICK_BLOCK

    internal fun shouldQueueRightClick(vanillaUseKeyDown: Boolean): Boolean = !vanillaUseKeyDown

    private fun queueVanillaClick(mapping: KeyMapping) {
        val accessor = mapping as KeyMappingAccessor
        accessor.clickCount = accessor.clickCount + 1
    }

    private fun overrideVanillaAttackHold(isDown: Boolean) {
        val attackKey = mc.options.keyAttack
        if (previousVanillaAttackDown == null) previousVanillaAttackDown = attackKey.isDown
        attackKey.isDown = isDown
    }

    private fun restoreVanillaAttackKey() {
        previousVanillaAttackDown?.let { mc.options.keyAttack.isDown = it }
        previousVanillaAttackDown = null
    }

    internal fun randomizedCps(minimum: Double, maximum: Double, randomUnit: Double = Random.nextDouble()): Double {
        val lower = minOf(minimum, maximum).coerceAtLeast(0.1)
        val upper = maxOf(minimum, maximum).coerceAtLeast(lower)
        return lower + ((upper - lower) * randomUnit.coerceIn(0.0, 1.0))
    }

    internal fun clickDelayMs(
        minimumCps: Double,
        maximumCps: Double,
        cpsRandomUnit: Double = Random.nextDouble(),
        jitterRandomUnit: Double = Random.nextDouble(),
    ): Long =
        ((1000.0 / randomizedCps(minimumCps, maximumCps, cpsRandomUnit)) +
            ((jitterRandomUnit.coerceIn(0.0, 1.0) - 0.5) * 60.0))
            .toLong()
            .coerceAtLeast(1L)

    internal fun heldIdentity(uuid: String?, id: String?, name: String?): String? =
        uuid?.takeIf(String::isNotBlank) ?: id?.takeIf(String::isNotBlank) ?: name?.takeIf(String::isNotBlank)

    internal fun encodeWhitelist(values: Set<String>): String = gson.toJson(values.sorted())

    internal fun decodeWhitelist(value: String): Set<String> = runCatching {
        gson.fromJson<List<String>>(value, stringListType).filter(String::isNotBlank).toCollection(linkedSetOf())
    }.getOrDefault(emptySet())

    private fun summary(values: Set<String>): String = values.joinToString(", ").ifEmpty { "empty" }

    private val gson = Gson()
    private val stringListType = object : TypeToken<List<String>>() {}.type
}
