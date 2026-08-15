package gg.floyd.features.impl.render

import com.google.gson.JsonParser
import gg.floyd.FloydAddonsMod
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

internal data class TreecapitatorLowestBinSnapshot(
    val price: Long? = null,
    val status: String = "idle",
    val lastUpdatedMs: Long = 0L,
    val error: String? = null,
)

/** Low-frequency, non-blocking Treecapitator lowest-BIN lookup backed by SkyCofl. */
internal object TreecapitatorLowestBinPrice {
    internal const val SOURCE_NAME = "SkyCofl"
    internal const val SOURCE_URL = "https://sky.coflnet.com/api/auctions/tag/TREECAPITATOR_AXE/active/bin"
    private const val REFRESH_INTERVAL_MS = 60_000L
    private const val MAX_RESPONSE_CHARS = 1_000_000

    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val inFlight = AtomicBoolean(false)

    @Volatile private var lastAttemptMs = 0L
    @Volatile private var current = TreecapitatorLowestBinSnapshot()

    fun snapshot(): TreecapitatorLowestBinSnapshot = current

    fun refreshIfNeeded(force: Boolean = false, now: Long = System.currentTimeMillis()) {
        if (!force && now - lastAttemptMs < REFRESH_INTERVAL_MS) return
        if (!inFlight.compareAndSet(false, true)) return
        lastAttemptMs = now
        current = current.copy(status = "loading", error = null)

        val request = HttpRequest.newBuilder(URI.create(SOURCE_URL))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("User-Agent", "FloydAddons/${FloydAddonsMod.MOD_VERSION} Treecapitator guide")
            .GET()
            .build()

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { response, failure ->
            try {
                if (failure != null) throw failure
                if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()}")
                require(response.body().length <= MAX_RESPONSE_CHARS) { "response is too large" }
                current = TreecapitatorLowestBinSnapshot(
                    price = parseLowestBin(response.body()),
                    status = "ready",
                    lastUpdatedMs = System.currentTimeMillis(),
                )
            } catch (error: Throwable) {
                current = current.copy(
                    status = if (current.price == null) "error" else "stale",
                    error = error.message ?: error.javaClass.simpleName,
                )
                FloydAddonsMod.logger.debug("Treecapitator lowest-BIN refresh failed", error)
            } finally {
                inFlight.set(false)
            }
        }
    }

    internal fun parseLowestBin(json: String): Long {
        val root = JsonParser.parseString(json)
        require(root.isJsonArray) { "expected an auction array" }
        return root.asJsonArray.asSequence()
            .mapNotNull { element ->
                val auction = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                if (auction.get("bin")?.asBoolean != true) return@mapNotNull null
                if (auction.get("tag")?.asString != "TREECAPITATOR_AXE") return@mapNotNull null
                auction.get("startingBid")?.asLong?.takeIf { it > 0L }
            }
            .minOrNull()
            ?: error("no active Treecapitator BIN listings")
    }
}
