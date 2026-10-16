package gg.floyd.features.impl.cosmetic

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ACCOUNT_NAME = "DUNIONIC"

class FloydSelfPlayerTest {
    private val accountUuid: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val offlineUuid: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val otherUuid: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @Test
    fun `controlled local body is always self`() {
        assertTrue(
            FloydSelfPlayer.isLocalProfile(
                entityUuid = accountUuid,
                entityName = ACCOUNT_NAME,
                localUuid = accountUuid,
                accountUuid = accountUuid,
                accountName = ACCOUNT_NAME,
            )
        )
    }

    @Test
    fun `recorded body is self when it carries the account uuid`() {
        assertTrue(
            FloydSelfPlayer.isLocalProfile(
                entityUuid = accountUuid,
                entityName = ACCOUNT_NAME,
                localUuid = offlineUuid,
                accountUuid = accountUuid,
                accountName = ACCOUNT_NAME,
            )
        )
    }

    @Test
    fun `recorded body is self on offline mode servers through the player list name`() {
        assertTrue(
            FloydSelfPlayer.isLocalProfile(
                entityUuid = offlineUuid,
                entityName = ACCOUNT_NAME,
                localUuid = null,
                accountUuid = accountUuid,
                accountName = ACCOUNT_NAME,
            )
        )
    }

    @Test
    fun `player list names match case insensitively and trimmed`() {
        assertTrue(
            FloydSelfPlayer.isLocalProfile(
                entityUuid = offlineUuid,
                entityName = " dunionic ",
                localUuid = null,
                accountUuid = accountUuid,
                accountName = ACCOUNT_NAME,
            )
        )
    }

    @Test
    fun `another player is never self`() {
        assertFalse(
            FloydSelfPlayer.isLocalProfile(
                entityUuid = otherUuid,
                entityName = "notlunabot",
                localUuid = accountUuid,
                accountUuid = accountUuid,
                accountName = ACCOUNT_NAME,
            )
        )
    }

    @Test
    fun `missing identities never match`() {
        assertFalse(
            FloydSelfPlayer.isLocalProfile(
                entityUuid = null,
                entityName = null,
                localUuid = accountUuid,
                accountUuid = accountUuid,
                accountName = ACCOUNT_NAME,
            )
        )
        assertFalse(
            FloydSelfPlayer.isLocalProfile(
                entityUuid = offlineUuid,
                entityName = "   ",
                localUuid = accountUuid,
                accountUuid = accountUuid,
                accountName = ACCOUNT_NAME,
            )
        )
        assertFalse(
            FloydSelfPlayer.isLocalProfile(
                entityUuid = offlineUuid,
                entityName = ACCOUNT_NAME,
                localUuid = null,
                accountUuid = null,
                accountName = null,
            )
        )
    }
}
