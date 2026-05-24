package com.odtheking.odin.keybind

import com.mojang.blaze3d.platform.InputConstants
import com.odtheking.odin.clickgui.settings.impl.KeybindSetting
import com.odtheking.mixin.accessors.KeyMappingAccessor
import com.odtheking.mixin.accessors.KeyMappingCategoryAccessor
import net.minecraft.client.KeyMapping

object KeybindSync {
    private val category: KeyMapping.Category = KeyMappingCategoryAccessor.register("floydaddons.category")
    private val settingsToBindings = linkedMapOf<KeybindSetting, KeyMapping>()
    private val bindingsToSettings = linkedMapOf<KeyMapping, KeybindSetting>()
    private var syncing = false

    @JvmStatic
    fun register(setting: KeybindSetting, translationKey: String): KeyMapping {
        val binding = KeyMapping(translationKey, setting.value.value, category)
        settingsToBindings[setting] = binding
        bindingsToSettings[binding] = setting
        setting.bindKeyMapping(binding)
        return binding
    }

    @JvmStatic
    fun syncFromSetting(setting: KeybindSetting, key: InputConstants.Key) {
        if (syncing) return
        val binding = settingsToBindings[setting] ?: return
        val accessor = binding as KeyMappingAccessor
        if (accessor.getBoundKey() == key) return
        syncing = true
        try {
            accessor.setBoundKey(key)
        } finally {
            syncing = false
        }
    }

    @JvmStatic
    fun syncFromBinding(binding: KeyMapping, key: InputConstants.Key) {
        if (syncing) return
        val setting = bindingsToSettings[binding] ?: return
        syncing = true
        try {
            setting.applyExternalKey(key)
        } finally {
            syncing = false
        }
    }

    @JvmStatic
    fun isSyncing(): Boolean = syncing
}
