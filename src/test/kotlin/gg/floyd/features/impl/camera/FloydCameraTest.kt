package gg.floyd.features.impl.camera

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import net.minecraft.client.CameraType
import java.nio.file.Files
import java.nio.file.Path

class FloydCameraTest {
    @Test
    fun `mouse hook wraps the exact player turn arguments without captured-local ordering`() {
        val source = Files.readString(
            Path.of("src/main/java/gg/floyd/mixin/mixins/CameraMouseMixin.java")
        )

        assertContains(source, "@WrapOperation(")
        assertContains(source, "Operation<Void> original")
        assertFalse(source.contains("LocalCapture"))
    }

    @Test
    fun `camera state exposes Floyd module controls for local smokes`() {
        val state = FloydCamera.state()

        assertEquals(true, state["enabled"])
        assertEquals(false, state["freecam"])
        assertEquals(false, state["freelook"])
        assertEquals(false, state["freecamActive"])
        assertEquals(false, state["freelookActive"])
        assertEquals(1.0f, state["freecamSpeed"])
        assertEquals(4.0f, state["freelookDistance"])
    }

    @Test
    fun `camera cycle preserves vanilla order when no modes are disabled`() {
        assertEquals(
            CameraType.THIRD_PERSON_BACK,
            FloydCamera.nextCameraTypeAfter(CameraType.FIRST_PERSON, disableFront = false, disableBack = false)
        )
        assertEquals(
            CameraType.THIRD_PERSON_FRONT,
            FloydCamera.nextCameraTypeAfter(CameraType.THIRD_PERSON_BACK, disableFront = false, disableBack = false)
        )
        assertEquals(
            CameraType.FIRST_PERSON,
            FloydCamera.nextCameraTypeAfter(CameraType.THIRD_PERSON_FRONT, disableFront = false, disableBack = false)
        )
    }

    @Test
    fun `camera cycle skips disabled front camera`() {
        assertEquals(
            CameraType.FIRST_PERSON,
            FloydCamera.nextCameraTypeAfter(CameraType.THIRD_PERSON_BACK, disableFront = true, disableBack = false)
        )
    }

    @Test
    fun `camera cycle skips disabled back camera`() {
        assertEquals(
            CameraType.THIRD_PERSON_FRONT,
            FloydCamera.nextCameraTypeAfter(CameraType.FIRST_PERSON, disableFront = false, disableBack = true)
        )
    }

    @Test
    fun `camera cycle falls back to first person when both third person modes are disabled`() {
        assertEquals(
            CameraType.FIRST_PERSON,
            FloydCamera.nextCameraTypeAfter(CameraType.FIRST_PERSON, disableFront = true, disableBack = true)
        )
    }
}
