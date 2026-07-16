package gg.floyd.features.impl.cosmetic

import com.google.gson.Gson
import com.google.gson.JsonObject
import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.events.TickEvent
import gg.floyd.events.WorldEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.impl.player.FloydPlayerSize
import gg.floyd.features.impl.player.FloydNickHider
import net.minecraft.world.entity.player.Player
import net.minecraft.util.Crypt
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object FloydSharedCosmetics : Module(
    name = "Shared Cosmetics",
    category = Category.COSMETIC,
    description = "Shows supported Floyd cosmetics selected by other Floyd users.",
    toggled = true,
) {
    private const val POLL_INTERVAL_TICKS = 1_200
    private const val SESSION_EXPIRY_MARGIN_MS = 60_000L
    private const val SERVICE_URL = "https://floyd-cosmetics.gobsisunfunny.workers.dev"
    private val gson = Gson()
    private val registry = SharedAppearanceRegistry()
    private val lookupInFlight = AtomicBoolean(false)
    private val authInFlight = AtomicBoolean(false)
    private val publishInFlight = AtomicBoolean(false)
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val showOthers by BooleanSetting(
        "Show Other Players",
        true,
        desc = "Shows shared appearances for other players who use Floyd Addons."
    )
    private val shareMine by BooleanSetting(
        "Share My Appearance",
        true,
        desc = "Publishes your supported appearance settings after Minecraft account verification."
    )
    private var ticks = 0
    @Volatile private var session: AuthSession? = loadSession()
    @Volatile private var lastPublishedJson: String? = null
    @Volatile private var lastError: String? = null
    @Volatile private var lastSuccessMs = 0L
    @Volatile private var lastPublishMs = 0L

    init {
        on<TickEvent.ClientEnd> {
            SharedNeckHiderNames.setActive(enabled && showOthers)
            if (++ticks >= POLL_INTERVAL_TICKS) {
                ticks = 0
                refresh()
            }
        }
        on<WorldEvent.Load> {
            ticks = POLL_INTERVAL_TICKS
        }
        on<WorldEvent.Unload> {
            registry.clear()
            SharedNeckHiderNames.clear()
            ticks = 0
        }
    }

    @JvmStatic
    fun appearanceForEntity(entityId: Int): SharedAppearance? {
        if (!enabled || !showOthers) return null
        val entity = mc.level?.getEntity(entityId) as? Player ?: return null
        if (entity.uuid == mc.player?.uuid) return null
        return registry.get(entity.uuid)
    }

    @JvmStatic
    fun localAppearance(): SharedAppearance = SharedAppearance(
        model = SharedModelAppearance(FloydPlayerModel.enabled, FloydPlayerModel.selectedModel(), FloydPlayerModel.shouldShowHeads()),
        cape = SharedCapeAppearance(FloydCape.enabled),
        cone = SharedConeAppearance(
            enabled = FloydConeHat.enabled,
            height = FloydConeHat.height(),
            radius = FloydConeHat.radius(),
            yOffset = FloydConeHat.yOffset(),
            rotation = FloydConeHat.baseRotation(),
            spinSpeed = FloydConeHat.spinSpeed(),
        ),
        skin = SharedSkinAppearance(FloydSkin.isSharedSkinEnabled()),
        size = SharedSizeAppearance(
            FloydPlayerSize.isSharedSizeEnabled(),
            FloydPlayerSize.scaleX(), FloydPlayerSize.scaleY(), FloydPlayerSize.scaleZ()
        ),
        neckHider = SharedNeckHiderAppearance(FloydNickHider.enabled, FloydNickHider.nickname),
    ).sanitized()

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "showOthers" to showOthers,
        "shareMine" to shareMine,
        "serviceConfigured" to normalizedServiceUrl().isNotEmpty(),
        "cachedPlayers" to registry.size(),
        "lookupInFlight" to lookupInFlight.get(),
        "authInFlight" to authInFlight.get(),
        "publishInFlight" to publishInFlight.get(),
        "authenticated" to (validSession() != null),
        "lastSuccessMs" to lastSuccessMs,
        "lastPublishMs" to lastPublishMs,
        "lastError" to lastError,
        "localAppearance" to localAppearance(),
        "minecraftPacketsUsed" to false,
    )

    internal fun applyDirectoryForTesting(requested: Set<UUID>, received: Map<UUID, SharedAppearance>) {
        registry.replace(requested, received)
    }

    private fun refresh() {
        val base = normalizedServiceUrl()
        if (!enabled || base.isEmpty()) return
        if (shareMine) publishOrAuthenticate(base)
        if (!showOthers || !lookupInFlight.compareAndSet(false, true)) return
        val self = mc.player?.uuid
        val profiles = mc.connection?.onlinePlayers.orEmpty()
            .filter { it.profile.id != self }
            .associate { it.profile.id to it.profile.name }
        val uuids = profiles.keys
        if (uuids.isEmpty()) {
            registry.clear()
            SharedNeckHiderNames.clear()
            lookupInFlight.set(false)
            return
        }

        val encoded = URLEncoder.encode(uuids.joinToString(","), StandardCharsets.UTF_8)
        val request = runCatching {
            HttpRequest.newBuilder(URI.create("$base/v1/appearances?uuids=$encoded"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "FloydAddons/${FloydAddonsMod.MOD_VERSION}")
                .GET()
                .build()
        }.getOrElse {
            lastError = it.message
            lookupInFlight.set(false)
            return
        }

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { response, error ->
            try {
                if (error != null) throw error
                if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()}")
                registry.replace(uuids, parseDirectory(response.body()))
                SharedNeckHiderNames.replace(registry.nicknameMappings(profiles))
                lastSuccessMs = System.currentTimeMillis()
                lastError = null
            } catch (failure: Throwable) {
                lastError = failure.message ?: failure.javaClass.simpleName
                FloydAddonsMod.logger.debug("Shared cosmetics refresh failed", failure)
            } finally {
                lookupInFlight.set(false)
            }
        }
    }

    private fun publishOrAuthenticate(base: String) {
        val activeSession = validSession()
        if (activeSession == null) {
            authenticate(base)
            return
        }
        val appearanceJson = gson.toJson(localAppearance())
        if (appearanceJson == lastPublishedJson || !publishInFlight.compareAndSet(false, true)) return
        val request = jsonRequest("$base/v1/appearances/me", "PUT", appearanceJson, activeSession.token)
        if (request == null) {
            publishInFlight.set(false)
            return
        }
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { response, error ->
            try {
                if (error != null) throw error
                if (response.statusCode() == 401) {
                    clearSession()
                    error("Cosmetics session expired")
                }
                if (response.statusCode() !in 200..299) error("Publish HTTP ${response.statusCode()}")
                lastPublishedJson = appearanceJson
                lastPublishMs = System.currentTimeMillis()
                lastError = null
            } catch (failure: Throwable) {
                lastError = failure.message ?: failure.javaClass.simpleName
                FloydAddonsMod.logger.debug("Shared cosmetics publish failed", failure)
            } finally {
                publishInFlight.set(false)
            }
        }
    }

    private fun authenticate(base: String) {
        val user = mc.user
        val uuid = user.profileId
        val username = user.name
        val accessToken = user.accessToken
        if (accessToken.isBlank()) {
            lastError = "Minecraft account is offline; appearance publishing is unavailable"
            return
        }
        if (!authInFlight.compareAndSet(false, true)) return
        val challengeBody = gson.toJson(mapOf("uuid" to uuid.toString(), "username" to username))
        val challengeRequest = jsonRequest("$base/v1/auth/challenge", "POST", challengeBody)
        if (challengeRequest == null) {
            authInFlight.set(false)
            return
        }
        http.sendAsync(challengeRequest, HttpResponse.BodyHandlers.ofString()).whenComplete challenge@{ challengeResponse, challengeError ->
            try {
                if (challengeError != null) throw challengeError
                if (challengeResponse.statusCode() !in 200..299) error("Auth challenge HTTP ${challengeResponse.statusCode()}")
                val challenge = gson.fromJson(challengeResponse.body(), AuthChallenge::class.java)
                val managedKeyPair = mc.profileKeyPairManager.prepareKeyPair().join()
                val certificate = if (managedKeyPair.isPresent) {
                    val keyPair = managedKeyPair.get()
                    val keyData = keyPair.publicKey().data()
                    ProfileCertificate(keyPair.privateKey(), keyData.key(), keyData.keySignature(), keyData.expiresAt())
                } else {
                    FloydAddonsMod.logger.info("[SharedCosmetics] Minecraft profile certificate cache unavailable; requesting a fresh certificate")
                    requestProfileCertificate(accessToken)
                }
                FloydAddonsMod.logger.info("[SharedCosmetics] Minecraft profile certificate ready (expiresAt={})", certificate.expiresAt)
                val completeBody = SharedCosmeticsAuthProtocol.completeBody(
                    challenge.challengeId,
                    challenge.serverId,
                    uuid,
                    certificate.expiresAt,
                    certificate.publicKey,
                    certificate.certificateSignature,
                    certificate.privateKey,
                )
                val completeRequest = jsonRequest("$base/v1/auth/complete", "POST", completeBody)
                    ?: error("Invalid cosmetics service URL")
                http.sendAsync(completeRequest, HttpResponse.BodyHandlers.ofString()).whenComplete { completeResponse, completeError ->
                    try {
                        if (completeError != null) throw completeError
                        if (completeResponse.statusCode() !in 200..299) {
                            FloydAddonsMod.logger.info("[SharedCosmetics] Authentication completion rejected (HTTP {})", completeResponse.statusCode())
                            error("Auth completion HTTP ${completeResponse.statusCode()}")
                        }
                        val completed = gson.fromJson(completeResponse.body(), AuthCompleted::class.java)
                        val newSession = AuthSession(completed.uuid, completed.token, completed.expiresAt)
                        session = newSession
                        saveSession(newSession)
                        lastError = null
                        lastPublishedJson = null
                    } catch (failure: Throwable) {
                        lastError = failure.message ?: failure.javaClass.simpleName
                        FloydAddonsMod.logger.info("[SharedCosmetics] Authentication failed: {}: {}", failure.javaClass.simpleName, lastError)
                    } finally {
                        authInFlight.set(false)
                        if (session != null) publishOrAuthenticate(base)
                    }
                }
                return@challenge
            } catch (failure: Throwable) {
                lastError = failure.message ?: failure.javaClass.simpleName
                FloydAddonsMod.logger.info("[SharedCosmetics] Authentication failed: {}: {}", failure.javaClass.simpleName, lastError)
                authInFlight.set(false)
            }
        }
    }

    private fun jsonRequest(url: String, method: String, body: String, token: String? = null): HttpRequest? = runCatching {
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "FloydAddons/${FloydAddonsMod.MOD_VERSION}")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .build()
    }.onFailure { lastError = it.message }.getOrNull()

    private fun requestProfileCertificate(accessToken: String): ProfileCertificate {
        val request = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/player/certificates"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) error("Minecraft certificate HTTP ${response.statusCode()}")
        val certificate = gson.fromJson(response.body(), MinecraftCertificateResponse::class.java)
        return ProfileCertificate(
            privateKey = Crypt.stringToPemRsaPrivateKey(certificate.keyPair.privateKey),
            publicKey = Crypt.stringToRsaPublicKey(certificate.keyPair.publicKey),
            certificateSignature = Base64.getDecoder().decode(certificate.publicKeySignatureV2),
            expiresAt = Instant.parse(certificate.expiresAt),
        )
    }

    private fun validSession(): AuthSession? {
        val current = session ?: return null
        val ownUuid = mc.user.profileId.toString()
        if (!current.uuid.equals(ownUuid, ignoreCase = true) || current.expiresAt <= System.currentTimeMillis() + SESSION_EXPIRY_MARGIN_MS) {
            clearSession()
            return null
        }
        return current
    }

    private fun loadSession(): AuthSession? = runCatching {
        val path = sessionPath()
        if (!Files.isRegularFile(path)) return null
        gson.fromJson(Files.readString(path), AuthSession::class.java)
    }.getOrNull()

    private fun saveSession(value: AuthSession) {
        runCatching {
            val path = sessionPath()
            Files.createDirectories(path.parent)
            val temporary = path.resolveSibling("${path.fileName}.tmp")
            Files.writeString(temporary, gson.toJson(value))
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { FloydAddonsMod.logger.warn("Could not save shared cosmetics session", it) }
    }

    private fun clearSession() {
        session = null
        lastPublishedJson = null
        runCatching { Files.deleteIfExists(sessionPath()) }
    }

    private fun sessionPath() = FloydAddonsMod.configFile.toPath().resolve("cosmetics-session.json")

    private fun parseDirectory(json: String): Map<UUID, SharedAppearance> {
        val root = gson.fromJson(json, JsonObject::class.java)
        val entries = root.getAsJsonObject("appearances") ?: return emptyMap()
        return entries.entrySet().mapNotNull { (key, value) ->
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: return@mapNotNull null
            val appearance = runCatching { gson.fromJson(value, SharedAppearance::class.java) }.getOrNull()
                ?: return@mapNotNull null
            uuid to appearance
        }.toMap()
    }

    private fun normalizedServiceUrl(): String = SERVICE_URL

    private data class AuthChallenge(val challengeId: String, val serverId: String, val expiresAt: Long)
    private data class AuthCompleted(val token: String, val expiresAt: Long, val uuid: String)
    private data class AuthSession(val uuid: String, val token: String, val expiresAt: Long)
    private data class ProfileCertificate(
        val privateKey: PrivateKey,
        val publicKey: PublicKey,
        val certificateSignature: ByteArray,
        val expiresAt: Instant,
    )
    private data class MinecraftCertificateResponse(
        val keyPair: MinecraftCertificateKeyPair,
        val publicKeySignatureV2: String,
        val expiresAt: String,
    )
    private data class MinecraftCertificateKeyPair(val privateKey: String, val publicKey: String)
}
