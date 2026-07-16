package gg.floyd.features.impl.cosmetic

import java.util.UUID
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedAppearanceTest {
    @Test
    fun `untrusted appearance values are restricted to supported assets and ranges`() {
        val sanitized = SharedAppearance(
            version = 99,
            model = SharedModelAppearance(true, "not-installed", true),
            cape = SharedCapeAppearance(true, "local-file.png"),
            cone = SharedConeAppearance(true, "remote-file.png", 99f, -5f, 10f, -20f, 900f),
            skin = SharedSkinAppearance(true, "arbitrary.png"),
            size = SharedSizeAppearance(true, -5f, 99f, 3f),
            neckHider = SharedNeckHiderAppearance(true, "Nick\u0000name that is much longer than thirty-two characters"),
        ).sanitized()

        assertEquals(SHARED_APPEARANCE_VERSION, sanitized.version)
        assertEquals(FloydPlayerModelSelection.models.first(), sanitized.model.id)
        assertEquals("default", sanitized.cape.id)
        assertEquals("default", sanitized.cone.id)
        assertEquals(1.5f, sanitized.cone.height)
        assertEquals(0.05f, sanitized.cone.radius)
        assertEquals(0.5f, sanitized.cone.yOffset)
        assertEquals(0.0f, sanitized.cone.rotation)
        assertEquals(360.0f, sanitized.cone.spinSpeed)
        assertEquals("default", sanitized.skin.id)
        assertEquals(-1.0f, sanitized.size.x)
        assertEquals(5.0f, sanitized.size.y)
        assertEquals("Nickname that is much longer tha", sanitized.neckHider.nickname)
    }

    @Test
    fun `registry exposes enabled shared neck hider names by username`() {
        val registry = SharedAppearanceRegistry()
        val enabled = UUID.randomUUID()
        val disabled = UUID.randomUUID()
        registry.replace(setOf(enabled, disabled), mapOf(
            enabled to SharedAppearance(neckHider = SharedNeckHiderAppearance(true, "Floyd")),
            disabled to SharedAppearance(neckHider = SharedNeckHiderAppearance(false, "Hidden")),
        ))

        assertEquals(mapOf("RealPlayer" to "Floyd"), registry.nicknameMappings(mapOf(
            enabled to "RealPlayer",
            disabled to "OtherPlayer",
        )))
    }

    @Test
    fun `registry replaces requested players and rejects incompatible protocol versions`() {
        val registry = SharedAppearanceRegistry()
        val retained = UUID.randomUUID()
        val removed = UUID.randomUUID()

        registry.replace(setOf(retained, removed), mapOf(
            retained to SharedAppearance(model = SharedModelAppearance(true, "Jenny")),
            removed to SharedAppearance(version = 2),
        ))

        assertEquals("Jenny", registry.get(retained)?.model?.id)
        assertNull(registry.get(removed))

        registry.replace(setOf(retained), emptyMap())
        assertNull(registry.get(retained))
    }

    @Test
    fun `authentication protocol signs a bound challenge without exposing secrets`() {
        val profileId = UUID.fromString("c3431b64-c164-4fd2-8257-1cb724baee65")
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val complete = SharedCosmeticsAuthProtocol.completeBody(
            challengeId = "challenge-id",
            serverId = "proof-id",
            profileId = profileId,
            expiresAt = Instant.ofEpochMilli(1_800_000_000_000),
            publicKey = keys.public,
            certificateSignature = byteArrayOf(1, 2, 3),
            privateKey = keys.private,
        )
        val root = com.google.gson.JsonParser.parseString(complete).asJsonObject
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(keys.public)
        verifier.update(SharedCosmeticsAuthProtocol.challengePayload("challenge-id", "proof-id", profileId))

        assertTrue(verifier.verify(Base64.getDecoder().decode(root["challengeSignature"].asString)))
        assertTrue(complete.contains("challenge-id"))
        assertFalse(complete.contains("minecraft-secret"))
        assertFalse(complete.contains("accessToken"))
    }
}
