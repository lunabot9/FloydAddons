package gg.floyd.features.impl.misc

import gg.floyd.FloydAddonsMod
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.OptionsScreen

/** Uses the same stable background/widget path as Options opened inside a world or server. */
class FloydOptionsScreen(parent: Screen) : OptionsScreen(parent, FloydAddonsMod.mc.options, false)
