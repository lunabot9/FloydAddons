package gg.floyd.features.impl.misc

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen

/** Uses Minecraft's normal menu background lifecycle so vanilla widgets and fonts stay isolated. */
class FloydJoinMultiplayerScreen(parent: Screen) : JoinMultiplayerScreen(parent)
