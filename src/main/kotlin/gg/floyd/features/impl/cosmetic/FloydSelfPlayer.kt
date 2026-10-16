package gg.floyd.features.impl.cosmetic

import gg.floyd.FloydAddonsMod.mc
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.util.UUID

/**
 * Decides whether a rendered player entity is the local player's own body.
 *
 * `Minecraft.player.id == entityId` is only a valid self test while the client owns the body
 * it controls. Recording playback breaks that assumption: replay mods such as Flashback log
 * the client in as a neutral stand-in under its own entity id and an offline profile UUID, and
 * spawn the recorded body as a separate player entity. Entity-id-only self checks therefore
 * classified your own body as somebody else's, so cosmetics were resolved through the shared
 * appearance directory instead of your local settings - which is why a recording showed a
 * different (sanitized) player model while live gameplay showed the selected one.
 *
 * Falling back to the profile identity (local UUID, account UUID, then the player-list name)
 * keeps self-owned cosmetics self-owned in replays, camera/freecam sessions, or any session
 * where the controlled entity is not the body being rendered. Nothing here changes what is
 * sent to a server, entity dimensions, or gameplay state.
 */
internal object FloydSelfPlayer {

    /** True when [id] belongs to the local player's own rendered body. */
    fun isSelf(id: Int): Boolean {
        val local = mc.player
        if (local != null && local.id == id) return true
        val entity = mc.level?.getEntity(id) ?: return false
        return isSelfEntity(entity)
    }

    /** True when [entity] is the local player's own rendered body. */
    fun isSelfEntity(entity: Entity): Boolean {
        val local = mc.player
        if (local != null && local.id == entity.id) return true
        if (entity !is Player) return false
        return isLocalProfile(
            entityUuid = entity.uuid,
            entityName = mc.connection?.getPlayerInfo(entity.uuid)?.profile?.name,
            localUuid = local?.uuid,
            accountUuid = mc.user.profileId,
            accountName = mc.user.name,
        )
    }

    /** Profile ids that belong to the local player, so shared lookups can exclude them. */
    fun localProfileIds(): Set<UUID> = buildSet {
        mc.player?.uuid?.let { add(it) }
        add(mc.user.profileId)
    }

    /** Pure identity check with no game state, kept separate so it stays unit-testable. */
    internal fun isLocalProfile(
        entityUuid: UUID?,
        entityName: String?,
        localUuid: UUID?,
        accountUuid: UUID?,
        accountName: String?,
    ): Boolean {
        if (entityUuid != null) {
            if (localUuid != null && entityUuid == localUuid) return true
            if (accountUuid != null && entityUuid == accountUuid) return true
        }
        val name = entityName?.trim().orEmpty()
        val own = accountName?.trim().orEmpty()
        return name.isNotEmpty() && own.isNotEmpty() && name.equals(own, ignoreCase = true)
    }
}
