package gg.floyd.events

import gg.floyd.events.core.CancellableEvent
import net.minecraft.client.gui.*
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.MouseButtonEvent

abstract class ScreenEvent(val screen: Screen) : CancellableEvent() {

    class Open(screen: Screen) : ScreenEvent(screen)

    class Close(screen: Screen) : ScreenEvent(screen)

    class MouseClick(screen: Screen, val click: MouseButtonEvent) : ScreenEvent(screen)

    class MouseRelease(screen: Screen, val click: MouseButtonEvent) : ScreenEvent(screen)

    class KeyPress(screen: Screen, val input: KeyEvent) : ScreenEvent(screen)
    class CharType(screen: Screen, val input: CharacterEvent) : ScreenEvent(screen)
    class MouseDrag(screen: Screen, val click: MouseButtonEvent, val deltaX: Double, val deltaY: Double) : ScreenEvent(screen)
    class MouseScroll(screen: Screen, val x: Double, val y: Double, val horizontal: Double, val vertical: Double) : ScreenEvent(screen)

    class Render(screen: Screen, val guiGraphics: GuiGraphics, val mouseX: Int, val mouseY: Int) : ScreenEvent(screen)
    class RenderAfter(screen: Screen, val guiGraphics: GuiGraphics, val mouseX: Int, val mouseY: Int) : ScreenEvent(screen)
}
