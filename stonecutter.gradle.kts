plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2"

stonecutter parameters {
    replacements.string(current.parsed >= "26.2") {
        replace(".setScreen(", ".gui.setScreen(")
        replace("\"renderArmWithItem\"", "\"submitArmWithItem\"")
        replace("\"renderFire\"", "\"submitFire\"")
        replace(".options.hideGui", ".gui.hud.isHidden()")
        replace(".gui.chat", ".gui.hud.getChat()")
        replace(".isSingleplayer", ".hasSingleplayerServer()")
        replace(".gameRenderer.lighting", ".gameRenderer.lighting()")
        replace(".gameRenderer.mainCamera", ".gameRenderer.mainCamera()")
        replace(".gameRenderer.renderBuffers", ".gameRenderer.renderBuffers()")
        replace(".gameRenderer.featureRenderDispatcher", ".gameRenderer.featureRenderDispatcher()")
        replace("com.mojang.blaze3d.textures.TextureFormat", "com.mojang.blaze3d.GpuFormat")
        replace("TextureFormat.RGBA8", "GpuFormat.RGBA8_UNORM")
        replace("TextureFormat.DEPTH32", "GpuFormat.D32_FLOAT")
        replace("RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET, RenderPipelines.FOG_SNIPPET)", "RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET)")
        replace("RenderPipeline.builder(RenderPipelines.GUI_TEXT_SNIPPET, RenderPipelines.FOG_SNIPPET)", "RenderPipeline.builder(RenderPipelines.GUI_TEXT_SNIPPET)")
        replace("RenderPipelines.FOG_SNIPPET", "RenderPipelines.MATRICES_FOG_SNIPPET")
        replace(".withSampler(\"Sampler0\")", ".withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withSampler(\"Sampler0\").build())")
        replace(".withSampler(\"Sampler2\")", ".withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withSampler(\"Sampler2\").build())")
        replace(".withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)", ".withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR).withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)")
        replace(".bufferSize(786432)", "/* buffer sizing is automatic in 26.2 */")
        replace(".withUniform(\"u\", UniformType.UNIFORM_BUFFER)", ".withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withUniform(\"u\", UniformType.UNIFORM_BUFFER).build())")
    }

    replacements.regex(current.parsed >= "26.2") {
        replace("\\bMultiBufferSource\\.BufferSource\\b", "SubmitNodeCollector /* buffer-source */", "(?!x)x", "\$0")
        replace("\\bMultiBufferSource\\b(?!\\.BufferSource)", "SubmitNodeCollector", "(?!x)x", "\$0")
        replace("(?<!\\.)\\b(mc|client|minecraft)\\.screen\\b", "$1.gui.screen()", "(?<!\\.)\\b(mc|client|minecraft)\\.gui\\.screen\\(\\)", "$1.screen")
        replace("FloydAddonsMod\\.mc\\.screen\\b", "FloydAddonsMod.mc.gui.screen()", "FloydAddonsMod\\.mc\\.gui\\.screen\\(\\)", "FloydAddonsMod.mc.screen")
        replace("Minecraft\\.getInstance\\(\\)\\.screen\\b", "Minecraft.getInstance().gui.screen()", "Minecraft\\.getInstance\\(\\)\\.gui\\.screen\\(\\)", "Minecraft.getInstance().screen")
        replace("(?<!\\.)\\b(mc|client)\\.mainRenderTarget\\b", "$1.gameRenderer.mainRenderTarget()", "(?<!\\.)\\b(mc|client)\\.gameRenderer\\.mainRenderTarget\\(\\)", "$1.mainRenderTarget")
        replace("Minecraft\\.getInstance\\(\\)\\.mainRenderTarget\\b", "Minecraft.getInstance().gameRenderer.mainRenderTarget()", "Minecraft\\.getInstance\\(\\)\\.gameRenderer\\.mainRenderTarget\\(\\)", "Minecraft.getInstance().mainRenderTarget")
    }
}
