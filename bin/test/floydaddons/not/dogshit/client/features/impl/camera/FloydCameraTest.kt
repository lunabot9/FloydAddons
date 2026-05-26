package floydaddons.not.dogshit.client.features.impl.camera

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.minecraft.client.CameraType

class FloydCameraTest {
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

    @Test
    fun `camera cycle reset persists like Floyd perspective mixin`() {
        val root = Path.of("").toAbsolutePath()
        val floyd = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/mixin/CameraPerspectiveMixin.java")).replace("\r\n", "\n")
        val active = Files.readString(root.resolve("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/camera/FloydCamera.kt")).replace("\r\n", "\n")

        assertTrue(floyd.contains("CameraConfig.setF5CameraDistance(CameraConfig.getF5DefaultDistance());"))
        assertTrue(floyd.contains("FloydAddonsConfig.save();"))
        assertTrue(active.contains("if (shouldResetF5OnToggle()) {\n            f5Distance = DEFAULT_F5_DISTANCE\n            ModuleManager.saveConfigurations()\n        }"))
    }

    @Test
    fun `F5 scroll distance persists like Floyd mouse mixin`() {
        val root = Path.of("").toAbsolutePath()
        val floyd = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/mixin/CameraMouseMixin.java")).replace("\r\n", "\n")
        val activeCamera = Files.readString(root.resolve("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/camera/FloydCamera.kt")).replace("\r\n", "\n")
        val activeMixin = Files.readString(root.resolve("src/main/java/floydaddons/not/dogshit/mixin/mixins/CameraMouseMixin.java")).replace("\r\n", "\n")

        assertTrue(floyd.contains("CameraConfig.setF5CameraDistance(Math.max(1.0f, Math.min(20.0f, newDist)));"))
        assertTrue(floyd.contains("FloydAddonsConfig.save();"))
        assertTrue(activeCamera.contains("fun adjustF5DistanceAndSave(scroll: Double)"))
        assertTrue(activeCamera.contains("adjustF5Distance(scroll)\n        ModuleManager.saveConfigurations()"))
        assertTrue(activeMixin.contains("FloydCamera.adjustF5DistanceAndSave(vertical);"))
    }

    @Test
    fun `freecam and freelook toggles remain runtime-only like Floyd camera config`() {
        val root = Path.of("").toAbsolutePath()
        val floydConfig = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/CameraConfig.java")).replace("\r\n", "\n")
        val floydPersistence = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsConfig.java")).replace("\r\n", "\n")
        val activeCamera = Files.readString(root.resolve("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/camera/FloydCamera.kt")).replace("\r\n", "\n")
        val runtimeBoolean = Files.readString(root.resolve("src/main/kotlin/floydaddons/not/dogshit/client/clickgui/settings/impl/BooleanSetting.kt")).replace("\r\n", "\n")

        assertTrue(floydConfig.contains("private static volatile boolean freecamEnabled;"))
        assertTrue(floydConfig.contains("private static volatile boolean freelookEnabled;"))
        assertTrue(floydConfig.contains("Runtime-only fields (positions/rotations) are not persisted."))
        assertTrue(floydPersistence.contains("data.freecamSpeed = CameraConfig.getFreecamSpeed();"))
        assertTrue(floydPersistence.contains("data.freelookDistance = CameraConfig.getFreelookDistance();"))
        assertTrue(!floydPersistence.contains("data.freecamEnabled"))
        assertTrue(!floydPersistence.contains("data.freelookEnabled"))

        assertTrue(activeCamera.contains("RuntimeBooleanSetting(\"Freecam\", false"))
        assertTrue(activeCamera.contains("RuntimeBooleanSetting(\"Freelook\", false"))
        assertTrue(!activeCamera.contains("var freecam by BooleanSetting(\"Freecam\""))
        assertTrue(!activeCamera.contains("var freelook by BooleanSetting(\"Freelook\""))
        assertTrue(runtimeBoolean.contains("class RuntimeBooleanSetting("))
        assertTrue(!runtimeBoolean.substringAfter("class RuntimeBooleanSetting(").substringBefore("override val isHovered").contains("Saving"))
    }
}
