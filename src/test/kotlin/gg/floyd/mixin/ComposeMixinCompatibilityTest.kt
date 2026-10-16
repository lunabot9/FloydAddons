package gg.floyd.mixin

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ComposeMixinCompatibilityTest {
    @Test
    fun `Compose does not add illegal public static fields to pack mixins`() {
        for (name in listOf("SkyBlockItemModelMixin", "SkyBlockPackRepositoryMixin", "SkyBlockTooltipStyleMixin", "SkyBlockPlayerHeadMixin")) {
            val stream = javaClass.getResourceAsStream("/gg/floyd/mixin/mixins/$name.class")
            assertNotNull(stream, "Missing compiled mixin $name")
            stream.use {
                ClassReader(it).accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitField(access: Int, field: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                        assertFalse(access and Opcodes.ACC_STATIC != 0 && access and Opcodes.ACC_PRIVATE == 0,
                            "$name.$field would make Mixin reject the class during Minecraft startup")
                        return null
                    }
                }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            }
        }
    }
}
