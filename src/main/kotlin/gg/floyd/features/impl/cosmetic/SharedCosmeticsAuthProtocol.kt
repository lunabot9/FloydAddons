package gg.floyd.features.impl.cosmetic

import com.google.gson.Gson
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.UUID

internal object SharedCosmeticsAuthProtocol {
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    private val gson = Gson()

    fun challengePayload(challengeId: String, serverId: String, profileId: UUID): ByteArray =
        "floyd-cosmetics-v1:$challengeId:$serverId:${profileId.toString().replace("-", "")}".toByteArray(Charsets.UTF_8)

    fun completeBody(
        challengeId: String,
        serverId: String,
        profileId: UUID,
        expiresAt: Instant,
        publicKey: PublicKey,
        certificateSignature: ByteArray,
        privateKey: PrivateKey,
    ): String {
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(privateKey)
        signer.update(challengePayload(challengeId, serverId, profileId))
        return gson.toJson(mapOf(
            "challengeId" to challengeId,
            "expiresAt" to expiresAt.toEpochMilli(),
            "publicKey" to Base64.getEncoder().encodeToString(publicKey.encoded),
            "certificateSignature" to Base64.getEncoder().encodeToString(certificateSignature),
            "challengeSignature" to Base64.getEncoder().encodeToString(signer.sign()),
        ))
    }
}
