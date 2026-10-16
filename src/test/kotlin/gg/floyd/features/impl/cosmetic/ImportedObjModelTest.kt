package gg.floyd.features.impl.cosmetic

import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportedObjModelTest {
    private fun cube(height: Float) = """
        v 0 0 0
        v 1 0 0
        v 1 $height 0
        v 0 $height 0
        f 1 2 3
        f 1 3 4
    """.trimIndent()

    @Test
    fun `authored player part groups keep their animation binding`() {
        val model = ImportedObjModel.parse("o body\n${cube(1f)}".byteInputStream())

        assertTrue(model.isGrouped)
    }

    @Test
    fun `group keywords are accepted as well as objects`() {
        val model = ImportedObjModel.parse("g left_arm\n${cube(1f)}".byteInputStream())

        assertTrue(model.isGrouped)
    }

    @Test
    fun `files without player groups render as one rigid body`() {
        val model = ImportedObjModel.parse("o Cube\n${cube(1f)}".byteInputStream())

        assertFalse(model.isGrouped)
        assertEquals(1f, model.height())
    }

    @Test
    fun `height fitting scales uniformly so any export unit scale works`() {
        val model = ImportedObjModel.parse("o Cube\n${cube(32f)}".byteInputStream())

        assertEquals(32f, model.height())
        val fitted = model.fittedToHeight(1.8f)
        assertEquals(1.8f, fitted.height(), 0.001f)
        assertFalse(fitted.isGrouped)
    }

    @Test
    fun `box fitting keeps the authored player proportions`() {
        val model = ImportedObjModel.parse("o body\n${cube(2f)}".byteInputStream())

        assertEquals(4f, model.fittedToBox(Vec3(2.0, 4.0, 1.0)).height(), 0.001f)
    }
}
