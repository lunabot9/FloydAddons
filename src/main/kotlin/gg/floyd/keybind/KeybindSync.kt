package gg.floyd.keybind

import com.mojang.blaze3d.platform.InputConstants
import gg.floyd.clickgui.settings.impl.KeybindSetting
import gg.floyd.mixin.accessors.KeyMappingAccessor
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier

/**
 * Bidirectional bridge between FloydAddons [KeybindSetting]s and vanilla [KeyMapping]s, so that a
 * keybind changed in FloydAddons' click GUI shows up in the vanilla Controls screen (and vice
 * versa).
 *
 * Each registered setting owns one [KeyMapping] that is added to the vanilla controls list. The
 * [syncing] flag prevents the two setters from re-entering each other.
 */
object KeybindSync {

    /**
     * The shared category that all FloydAddons keybinds appear under in the vanilla Controls screen.
     * Resolves its display name via the lang key `key.category.floydaddons.category`.
     */
    private val category: KeyMapping.Category =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("floydaddons", "category"))

    /** Maps each FloydAddons [KeyMapping] back to the setting that owns it, for [syncFromBinding]. */
    private val bindings: MutableMap<KeyMapping, KeybindSetting> = hashMapOf()

    /**
     * Guards against re-entrancy: when we push a change from one side to the other, the resulting
     * setter must not push it straight back.
     */
    private var syncing = false

    @JvmStatic
    fun isSyncing(): Boolean = syncing

    /**
     * Builds and registers a [KeyMapping] for [setting] under [translationKey], wiring up
     * bidirectional sync. The created [KeyMapping] is returned (also stored on the setting via
     * [KeybindSetting.bindKeyMapping]).
     */
    fun register(setting: KeybindSetting, translationKey: String): KeyMapping {
        val mapping = KeyMapping(translationKey, setting.value.value, category)
        setting.bindKeyMapping(mapping)
        bindings[mapping] = setting
        // Reflect the setting's persisted key onto the freshly created mapping.
        syncFromSetting(setting, setting.value)
        return mapping
    }

    /**
     * Pushes [newKey] from a FloydAddons [setting] onto its bound vanilla [KeyMapping]. Invoked from
     * [KeybindSetting]'s value setter when the user rebinds inside FloydAddons.
     */
    fun syncFromSetting(setting: KeybindSetting, newKey: InputConstants.Key) {
        if (syncing) return
        val mapping = setting.keyMapping ?: return
        if ((mapping as KeyMappingAccessor).boundKey == newKey) return
        syncing = true
        try {
            // Use setKey (not the raw accessor) so vanilla's internal key->mapping registry is
            // rebuilt; the syncing guard stops the resulting setKey injection from looping back.
            mapping.setKey(newKey)
        } finally {
            syncing = false
        }
    }

    /**
     * Pushes [newKey] from a vanilla [mapping] onto the FloydAddons setting that owns it. Invoked
     * from [gg.floyd.mixin.mixins.KeyBindingMixin] when the user rebinds in the vanilla Controls
     * screen.
     */
    @JvmStatic
    fun syncFromBinding(mapping: KeyMapping, newKey: InputConstants.Key) {
        if (syncing) return
        val setting = bindings[mapping] ?: return
        if (setting.value == newKey) return
        syncing = true
        try {
            setting.applyExternalKey(newKey)
        } finally {
            syncing = false
        }
    }
}
