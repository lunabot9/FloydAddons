@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package gg.floyd.clickgui

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import org.polyfrost.oneconfig.internal.ui.components.RetainedVisibility
import org.polyfrost.oneconfig.internal.ui.LocalOneConfigContentAlpha
import kotlin.math.pow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.ModuleManager
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import org.polyfrost.oneconfig.internal.ui.LocalCloseRequest
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.components.IconButton
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx
import org.polyfrost.oneconfig.internal.ui.compose.BlurRenderer
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.api.ui.v1.keybind.trackTextInputFocus

/** Dedicated scene: no native OneConfig navigation, preferences, HUD editor or sound lifecycle. */
class FloydSettingsScreen : ComposeScreen(RenderMode.CONTINUOUS) {
    companion object {
        private val rendering = ThreadLocal.withInitial { false }
        @JvmStatic fun isRenderingFloyd(): Boolean = rendering.get()
        private inline fun <T> renderScope(block: () -> T): T {
            val previous = rendering.get()
            rendering.set(true)
            return try { block() } finally { rendering.set(previous) }
        }
    }
    private var openRevision by mutableIntStateOf(0)
    var openingProgress = 0f
        private set
    var openingAnimationFrames = 0
        private set
    var closingRequested by mutableStateOf(false)
        private set
    private var closingAtNanos = 0L
    private var openingKey: Int? = null
    fun prepareOpen(openingKey: Int? = null) {
        this.openingKey = openingKey
        closingRequested = false
        closingAtNanos = 0L
        openRevision++
        openingProgress = 0f
    }
    private var everOpened = false
    private var warmupQueued = false
    override val retainsScene: Boolean get() = true
    override val scrollSpeed: Float get() { FloydOneConfig.recordSettingsScroll(); return .25f }

    fun queueWarmup() {
        if (everOpened || warmupQueued) return
        warmupQueued = true
        SkiaCtx.queueWarmup {
            warmupQueued = false
            if (everOpened) return@queueWarmup
            if (mc.screen is ComposeScreen) { queueWarmup(); return@queueWarmup }
            FloydOneConfig.beginSettingsWarmup()
            val done = try { renderScope { prewarm(19, 1) { frame ->
                if (frame == 0 || frame == 6 || frame == 12) FloydOneConfig.recordSettingsWarmup()
                if (frame == 18) FloydSettingsNavigation.warmupScrollIndex = 0
            } } } finally { FloydOneConfig.endSettingsWarmup() }
            if (!done) queueWarmup()
        }
    }

    override fun init() { everOpened = true; FloydOneConfigTheme.sync(); renderScope { super.init() } }
    override fun onClose() {
        if (closingRequested) return
        closingAtNanos = System.nanoTime()
        closingRequested = true
    }
    override fun removed() { FloydSettingsModelPreviews.close(); ModuleManager.saveConfigurations(); super.removed() }
    // The retained scene may still focus a text field. GLFW sends a character callback after
    // the key callback that opened us, so consume that key's text/repeats until its release.
    override fun charTyped(event: CharacterEvent): Boolean =
        if (openingKey != null) true else super.charTyped(event)

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key == openingKey) return true
        // Recover if focus changed before the opening key's release reached this screen.
        openingKey = null
        return super.keyPressed(event)
    }
    override fun keyReleased(event: KeyEvent): Boolean {
        if (event.key == openingKey) {
            openingKey = null
            return true
        }
        return super.keyReleased(event)
    }
    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean {
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            if (FloydSettingsNavigation.themeEditorOpen) FloydSettingsNavigation.closeThemeEditor() else onClose()
            return true
        }
        return false
    }
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        FloydOneConfigTheme.sync()
        if (closingRequested && System.nanoTime() - closingAtNanos >= 160_000_000L) {
            mc.setScreen(null)
            return
        }
        // Minecraft's backdrop blur is independent of OneConfig's global blur preference.
        if (SkiaCtx.isDeferredComposeBackend) {
            graphics.nextStratum()
            graphics.blurBeforeThisStratum()
            SkiaCtx.requestBlurSnapshot()
        }
        BlurRenderer.drawBlur(8f * openingProgress)
        renderScope { super.extractRenderState(graphics, mouseX, mouseY, delta) }
    }

    @Composable
    override fun compose() {
        val theme = FloydOneConfigTheme.theme
        val density = LocalDensity.current
        val window = LocalWindowInfo.current.containerSize
        val scale = minOf(window.width * .95f / (1391f * density.density),
            window.height * .95f / (700f * density.density), 1.0714286f).coerceAtLeast(.25f)
        CompositionLocalProvider(LocalTheme provides theme,
            LocalDensity provides Density(density.density * scale, density.fontScale),
            LocalCloseRequest provides { onClose() },
            LocalScrollbarStyle provides ScrollbarStyle(24.dp, 8.dp, theme.scrollBarShape, 300,
                theme.textColorSecondary.copy(alpha = .4f), theme.textColorSecondary.copy(alpha = .7f))) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RetainedVisibility(visible = !closingRequested, enter = tween(250, easing = Easing { progress ->
                    if (progress >= 1f) 1f else 1f - 2f.pow(-10f * progress)
                }), exit = tween(160, easing = EaseOutCubic), openKey = openRevision, modifier = Modifier.size(1391.dp, 700.dp)) { alpha ->
                    SideEffect {
                        if (openingProgress != alpha) openingAnimationFrames++
                        openingProgress = alpha
                    }
                    CompositionLocalProvider(LocalOneConfigContentAlpha provides alpha) {
                Row(Modifier.fillMaxSize().clip(theme.backgroundShape)
                    .background(theme.pageBackground).border(1.dp, theme.borderColor, theme.backgroundShape)) {
                    FloydSidebar()
                    Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 19.dp),
                        verticalArrangement = Arrangement.spacedBy(19.dp)) {
                        FloydSettingsHeader()
                        Box(Modifier.weight(1f).fillMaxWidth().clip(androidx.compose.ui.graphics.RectangleShape)) {
                            FloydSettingsPanel()
                        }
                    }
                }
                    }
                }
            }
        }
    }
}

@Composable
fun FloydSettingsSearchField() {
    val theme = LocalTheme.current
    Row(Modifier.width(256.dp).height(32.dp).background(theme.chipBackground, theme.sideBarNavigationEntryShape)
        .border(1.dp, theme.borderColor, theme.sideBarNavigationEntryShape).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon("search", color = theme.textColorSecondary, modifier = Modifier.size(14.dp))
        BasicTextField(value = FloydSettingsNavigation.searchQuery,
            onValueChange = { FloydSettingsNavigation.searchQuery = it }, singleLine = true,
            textStyle = TextStyle(color = theme.textColor, fontSize = 12.sp, fontFamily = theme.typography.family),
            cursorBrush = SolidColor(theme.textColor), modifier = Modifier.weight(1f).trackTextInputFocus(),
            decorationBox = { input ->
                Box { if (FloydSettingsNavigation.searchQuery.isEmpty()) Text("Search mods...", color = theme.textColorSecondary, fontSize = 12.sp); input() }
            })
        if (FloydSettingsNavigation.searchQuery.isNotEmpty()) IconButton("close", Modifier.size(14.dp)) { FloydSettingsNavigation.searchQuery = "" }
    }
}
