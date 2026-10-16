package gg.floyd.features.impl.cosmetic

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloydModelFilesTest {
    @Test
    fun `only obj and glb files are loadable`() {
        assertEquals(ModelFileFormat.OBJ, ModelFileFormat.of("MyModel.OBJ"))
        assertEquals(ModelFileFormat.GLB, ModelFileFormat.of("tung_tung_sahur.glb"))
        assertEquals(ModelFileFormat.UNSUPPORTED, ModelFileFormat.of("jenny_dressed.json"))
        assertEquals(ModelFileFormat.UNSUPPORTED, ModelFileFormat.of("no-extension"))
    }

    @Test
    fun `listing surfaces loadable files and pairs the sidecar texture`() {
        val dir = Files.createTempDirectory("floyd-models")
        Files.writeString(dir.resolve("b_model.obj"), "v 0 0 0\n")
        Files.writeString(dir.resolve("b_model.png"), "")
        Files.writeString(dir.resolve("a_model.glb"), "")
        Files.writeString(dir.resolve("jenny_dressed.json"), "")
        Files.writeString(dir.resolve("notes.txt"), "")

        val files = FloydModelFiles.list(dir)

        assertEquals(listOf("a_model.glb", "b_model.obj"), files.map { it.fileName })
        assertEquals(listOf("a_model", "b_model"), files.map { it.name })
        assertEquals(ModelFileFormat.OBJ, files[1].format)
        assertEquals(dir.resolve("b_model.png"), files[1].texturePath)
        assertEquals(ModelFileFormat.GLB, files[0].format)
        assertEquals(dir.resolve("a_model.png"), files[0].texturePath)
    }

    @Test
    fun `a missing model directory lists nothing instead of failing`() {
        val missing = Files.createTempDirectory("floyd-models").resolve("nested")
        assertTrue(FloydModelFiles.list(missing).isEmpty())
    }
}
