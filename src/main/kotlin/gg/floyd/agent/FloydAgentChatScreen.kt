@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package gg.floyd.agent

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.ModuleManager
import gg.floyd.utils.font.ClickGuiFont
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.*
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.client.sounds.SoundManager
import org.polyfrost.oneconfig.internal.ui.sound.UiSounds
import org.polyfrost.oneconfig.internal.ui.sound.UiSoundEvent

/** A local chat surface: messages never go through Minecraft/server chat. */
class FloydAgentChatScreen(private val parent: Screen?, private var openingKey: Int? = null, private val embedded: Boolean = false) : Screen(Component.literal("${FloydAgent.petName} Chat")) {
    companion object {
        var buttonSoundCount = 0
            private set
    }
    private lateinit var input: EditBox
    private var left = 0; private var top = 0; private var paneWidth = 0; private var paneHeight = 0
    private var scroll = 0
    private var openedAt = System.nanoTime()
    private var closingAt: Long? = null
    fun startClosing() { closingAt = System.nanoTime(); blurMessage() }
    fun closingFinished() = closingAt?.let { System.nanoTime() - it >= 240_000_000L } ?: false
    private val signInButtons = mutableListOf<Button>()
    private var signOutButton: Button? = null
    override fun isPauseScreen() = false
    override fun extractBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}
    override fun init() {
        openedAt = System.nanoTime()
        paneWidth = minOf(300, width - 20); paneHeight = minOf(260, height - 58)
        left = width - paneWidth - 8; top = height - paneHeight - if (embedded) 24 else 8
        signInButtons.clear()
        input = EditBox(ClickGuiFont.font, left + 14, top + paneHeight - 47, paneWidth - 85, 15, Component.literal("Message companion"))
        input.setBordered(false); input.setMaxLength(4000); input.setHint(Component.literal("Type a message...")); addRenderableWidget(input)
        button("Send", left + paneWidth - 61, top + paneHeight - 53, 51, 20, true) { send() }
        signInButtons += button("Sign in with ChatGPT", left + 10, top + paneHeight - 78, (paneWidth - 24) / 2, 18) { FloydAgent.signInWith(0) }
        signInButtons += button("Sign in with Claude", left + paneWidth / 2 + 2, top + paneHeight - 78, (paneWidth - 24) / 2, 18) { FloydAgent.signInWith(1) }
        signOutButton = button("Sign out", left + 10, top + paneHeight - 78, paneWidth - 20, 18) { FloydAgent.signOut() }
        button("Stop", left + 10, top + paneHeight - 27, 60, 18) { FloydAgent.stopResponse() }
        button("Close", left + paneWidth - 66, top + paneHeight - 27, 56, 18) { onClose() }
        setInitialFocus(input)
        if (embedded) blurMessage()
    }
    private fun button(label: String, bx: Int, by: Int, bw: Int, bh: Int, accent: Boolean = false, click: () -> Unit): Button {
        return addRenderableWidget(object : Button(bx, by, bw, bh, Component.literal(label), { click() }, { it.get() }) {
            override fun playDownSound(soundManager: SoundManager) {
                UiSounds.play(UiSoundEvent.CLICK)
                buttonSoundCount++
            }
            override fun extractContents(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
                val p = AgentChatStyle.palette
                val hovered = mouseX in x until x + width && mouseY in y until y + height
                NVGPIPRenderer.draw(g, x, y, width, height, renderScaleMultiplier = mc.window.guiScale.toFloat() / NVGRenderer.devicePixelRatio(), localCoordinates = true, outerPadding = 2) {
                    NVGRenderer.rect(0f, 0f, width.toFloat(), height.toFloat(), p[if (accent || isFocused) "Accent" else "Border"], 5f)
                    if (!accent && !isFocused) NVGRenderer.rect(.6f, .6f, width - 1.2f, height - 1.2f, p[if (hovered) "Hover Background" else "Control Background"], 4.4f)
                    val text = label
                    val font = NVGRenderer.defaultFont
                    val tw = NVGRenderer.textWidthImmediate(text, 9f, font)
                    NVGRenderer.textImmediate(text, (width - tw) / 2f, (height - 9f) / 2f, 9f, p[if (accent || isFocused) "Accent Text" else "Primary Text"], font)
                }
            }
        })
    }
    fun containsPoint(x: Double, y: Double) = x >= left && x <= left + paneWidth && y >= top && y <= top + paneHeight
    fun focusMessage() { setFocused(input); input.isFocused = true }
    fun blurMessage() { input.isFocused = false; setFocused(null) }
    fun draftLength() = if (::input.isInitialized) input.value.length else 0
    private fun send() { val text = input.value; if (text.isNotBlank()) { FloydAgent.send(text); input.value = ""; scroll = 0 } }
    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key == openingKey) return true
        openingKey = null
        if (input.isFocused && (event.key == 257 || event.key == 335)) { send(); return true }
        return super.keyPressed(event)
    }
    override fun charTyped(event: CharacterEvent): Boolean = if (openingKey != null) true else super.charTyped(event)
    override fun keyReleased(event: KeyEvent): Boolean {
        if (event.key == openingKey) { openingKey = null; return true }
        return super.keyReleased(event)
    }
    override fun onClose() { if (embedded) { FloydAgent.blurInlineChat(); mc.screen?.onClose(); return }; if (AgentAudio.recording) FloydAgent.stopResponse(); ModuleManager.saveConfigurations(); if (parent !is net.minecraft.client.gui.screens.ChatScreen) FloydAgent.animateChatClose(this); mc.setScreen(parent) }
    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        signInButtons.forEach { it.visible = !FloydAgent.connected }
        signOutButton?.visible = FloydAgent.connected
        val p = AgentChatStyle.palette
        val font = ClickGuiFont.font
        input.setTextColor(p["Primary Text"])
        val t = ((System.nanoTime() - (closingAt ?: openedAt)) / 240_000_000.0).coerceIn(0.0, 1.0)
        val expansion = (if (closingAt != null) (1.0 - t) * (1.0 - t) * (1.0 - t) else 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t)).toFloat()
        top = height - paneHeight - if (embedded) (8 + 16 * expansion).toInt() else 8
        val animatedW = 124f + (paneWidth - 124f) * expansion
        val animatedH = 30f + (paneHeight - 30f) * expansion
        val ox = paneWidth - animatedW; val oy = paneHeight - animatedH
        AgentChatStyle.panel(g, (left + ox).toInt(), (top + oy).toInt(), animatedW.toInt().coerceAtLeast(1), animatedH.toInt().coerceAtLeast(1))
        NVGPIPRenderer.draw(g, left, top, paneWidth, paneHeight, renderScaleMultiplier = mc.window.guiScale.toFloat() / NVGRenderer.devicePixelRatio(), localCoordinates = true, outerPadding = 2) {
            if (expansion > .98f) {
            NVGRenderer.rect(10f, 32f, paneWidth - 20f, 1f, p["Divider"], 0f)
            NVGRenderer.rect(9f, paneHeight - 54f, paneWidth - 83f, 21f, p["Border"], 5f)
            NVGRenderer.rect(9.6f, paneHeight - 53.4f, paneWidth - 84.2f, 19.8f, p["Control Background"], 4.4f)
            }
        }
        if (expansion < .02f) {
            g.drawString(font, FloydAgent.petName, (left + ox + 10).toInt(), (top + oy + 5).toInt(), p["Accent"], false)
            FloydAgent.renderPet(g)
            return
        }
        g.enableScissor((left + ox + 2).toInt(), (top + oy + 2).toInt(), left + paneWidth - 2, top + paneHeight - 2)
        g.pose().pushMatrix()
        g.pose().translate((left + paneWidth).toFloat(), (top + paneHeight).toFloat())
        g.pose().scale(expansion, expansion)
        g.pose().translate(-(left + paneWidth).toFloat(), -(top + paneHeight).toFloat())
        g.drawString(font, FloydAgent.petName, left + 10, top + 10, p["Accent"], false)
        val mode = if (AgentAudio.recording) "Listening" else if (FloydAgent.phase != "idle") FloydAgent.status else if (FloydAgent.connected) "${if (FloydAgent.provider == 0) "ChatGPT" else "Claude"} connected" else "Sign in"
        g.drawString(font, mode, left + paneWidth - font.width(mode) - 10, top + 10, if (AgentAudio.recording) 0xFFFF8585.toInt() else p["Secondary Text"], false)
        g.enableScissor(left + 10, top + 38, left + paneWidth - 10, top + paneHeight - 86)
        val rows = FloydAgent.displayHistory().flatMap { message ->
            val text = "${FloydAgent.messageName(message.role)}: ${message.text}"
            ClickGuiFont.font.split(Component.literal(text), paneWidth - 24).map { it to (if (message.role == "You") p["Accent"] else p["Primary Text"]) } + listOf(null to -1)
        }
        val visible = ((paneHeight - 128) / 10).coerceAtLeast(1)
        val end = if (AgentAudio.recording || FloydAgent.phase == "transcribing") rows.size else (rows.size - scroll).coerceIn(0, rows.size)
        rows.subList((end - visible).coerceAtLeast(0), end).forEachIndexed { i, (row, color) -> if (row != null) g.drawString(font, row, left + 12, top + 39 + i * 10, color, false) }
        if (rows.isEmpty()) g.drawString(font, "Sign in, then say hello.", left + 12, top + 45, p["Secondary Text"], false)
        g.disableScissor()
        val hint = if (AgentAudio.recording) "Listening" else "Hold ${FloydAgent.pushToTalkLabel()} to talk"
        g.drawString(font, font.plainSubstrByWidth(hint, paneWidth - 148), left + 80, top + paneHeight - 22, p["Secondary Text"], false)
        super.extractRenderState(g, mouseX, mouseY, deltaTicks)
        g.pose().popMatrix()
        g.disableScissor()
        FloydAgent.renderPet(g)
    }
    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (FloydAgent.beginPetDrag(event)) return true
        return super.mouseClicked(event, doubleClick)
    }
    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (FloydAgent.dragPet(event.x(), event.y())) return true
        return super.mouseDragged(event, deltaX, deltaY)
    }
    override fun mouseReleased(event: MouseButtonEvent): Boolean { if (FloydAgent.endPetDrag()) return true; return super.mouseReleased(event) }
    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scroll = (scroll + if (verticalAmount > 0) 3 else -3).coerceIn(0, (FloydAgent.displayHistory().sumOf { ClickGuiFont.font.split(Component.literal("${FloydAgent.messageName(it.role)}: ${it.text}"), paneWidth - 24).size + 1 } - 1).coerceAtLeast(0))
        return true
    }
}
