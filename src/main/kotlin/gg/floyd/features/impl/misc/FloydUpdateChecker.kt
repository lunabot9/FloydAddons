package gg.floyd.features.impl.misc

import com.google.gson.JsonParser
import gg.floyd.FloydAddonsMod
import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

object FloydUpdateChecker {
    private val checked = AtomicBoolean(false)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    private var currentSemver: String? = null
    private var tagSuffix: String? = null
    @Volatile private var pendingMessage: Component? = null

    fun state(): Map<String, Any?> = mapOf(
        "initialized" to (currentSemver != null && tagSuffix != null),
        "currentSemver" to currentSemver,
        "tagSuffix" to tagSuffix,
        "checked" to checked.get(),
        "pendingMessage" to (pendingMessage != null),
        "releaseApi" to "https://api.github.com/repos/lunabot9/FloydAddons/releases"
    )

    fun init() {
        try {
            val version = FloydAddonsMod.MOD_VERSION
            println("[FloydAddons] UpdateChecker: version=$version")
            if (version == "\${version}") return

            if (version.contains("-mc")) {
                val index = version.indexOf("-mc")
                currentSemver = version.substring(0, index)
                val mcVersion = version.substring(index + 3)
                tagSuffix = "-mc$mcVersion"
            } else {
                currentSemver = version
                tagSuffix = "-mc${SharedConstants.getCurrentVersion().id()}"
            }
        } catch (_: Exception) {
        }
    }

    fun tick() {
        if (!FloydCompatibility.shouldCheckUpdates()) return
        if (FloydAddonsMod.mc.player == null) return

        pendingMessage?.let {
            pendingMessage = null
            FloydAddonsMod.mc.gui.chat.addMessage(it)
        }

        if (checked.compareAndSet(false, true)) CompletableFuture.runAsync(::checkAsync)
    }

    private fun checkAsync() {
        val currentSemver = currentSemver ?: return
        val tagSuffix = tagSuffix ?: return

        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/lunabot9/FloydAddons/releases"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) return

            val releases = JsonParser.parseString(response.body()).asJsonArray
            for (element in releases) {
                val release = element.asJsonObject
                if (release["draft"]?.asBoolean == true || release["prerelease"]?.asBoolean == true) continue

                val tag = release["tag_name"]?.asString ?: continue
                if (!tag.endsWith(tagSuffix)) continue

                var remoteSemver = tag.removePrefix("v")
                remoteSemver = remoteSemver.substring(0, remoteSemver.length - tagSuffix.length)
                if (compareSemver(remoteSemver, currentSemver) > 0) {
                    val url = release["html_url"]?.asString ?: return
                    pendingMessage = buildMessage(currentSemver, remoteSemver, url)
                }
                return
            }
        } catch (_: Exception) {
        }
    }

    private fun buildMessage(current: String, remote: String, url: String): Component =
        Component.literal("[FloydAddons] ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Update available: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("v$current").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" → ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("v$remote").withStyle(ChatFormatting.GREEN))
            .append(Component.literal(". ").withStyle(ChatFormatting.YELLOW)))
            .append(
                Component.literal("[Download]").withStyle { style ->
                    style.withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(ClickEvent.OpenUrl(URI.create(url)))
                        .withHoverEvent(HoverEvent.ShowText(Component.literal("Open release page")))
                }
            )

    internal fun compareSemver(left: String, right: String): Int {
        val l = parseSemver(left)
        val r = parseSemver(right)
        for (i in 0..2) {
            if (l[i] != r[i]) return l[i].compareTo(r[i])
        }
        return 0
    }

    internal fun parseSemver(value: String): IntArray {
        val result = IntArray(3)
        value.split(".").take(3).forEachIndexed { index, part ->
            result[index] = part.toIntOrNull() ?: 0
        }
        return result
    }
}
