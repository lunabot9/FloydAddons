package floydaddons.not.dogshit.client.features.impl.cosmetic

import floydaddons.not.dogshit.client.features.impl.player.FloydPlayerSizeControls
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydSkinTest {
    @Test
    fun `player size toggle mirrors Floyd module entry`() {
        assertFalse(FloydPlayerSizeControls.isActive(1.0f, 1.0f, 1.0f))
        assertEquals(2.0f, FloydPlayerSizeControls.toggledScale(1.0f, 1.0f, 1.0f))

        assertTrue(FloydPlayerSizeControls.isActive(2.0f, 2.0f, 2.0f))
        assertEquals(1.0f, FloydPlayerSizeControls.toggledScale(2.0f, 2.0f, 2.0f))

        assertTrue(FloydPlayerSizeControls.isActive(1.0f, -0.5f, 1.0f))
        assertEquals(1.0f, FloydPlayerSizeControls.toggledScale(1.0f, -0.5f, 1.0f))
    }

    @Test
    fun `player size active state and toggle are owned by Player Size not Render or Skin`() {
        val root = Path.of("").toAbsolutePath()
        val floyd = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/ClickGuiScreen.java"))
        val skin = Files.readString(root.resolve("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/cosmetic/FloydSkin.kt"))
        val playerSize = Files.readString(root.resolve("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/player/FloydPlayerSize.kt"))
        val render = Files.readString(root.resolve("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/render/FloydRender.kt"))

        assertTrue(floyd.contains("new ModuleEntry(\"Player Size\", \"Change player model scale (XYZ)\""))
        assertTrue(floyd.contains("SkinConfig.setPlayerScale(1.0f);"))
        assertTrue(floyd.contains("SkinConfig.setPlayerScale(2.0f);"))
        assertTrue(playerSize.contains("name = \"Player Size\""))
        assertTrue(playerSize.contains("category = Category.PLAYER"))
        assertTrue(playerSize.contains("fun playerSizeActive(): Boolean ="))
        assertTrue(playerSize.contains("fun togglePlayerSize()"))
        assertFalse(skin.contains("fun playerSizeActive(): Boolean ="))
        assertFalse(render.contains("\"Player Size\""))
    }
}
