package gg.floyd.features.impl.pvp.experiments

import gg.floyd.FloydAddonsMod
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

/** Text classification is deliberately independent of nearby unrelated holograms. */
object ExperimentTableStatus {
    data class Line(val text: String, val yellow: Boolean = false, val red: Boolean = false)
    enum class Availability { READY, COOLDOWN, UNKNOWN }
    private val timer = Regex("\\b\\d+\\s*(?:hours?|minutes?|seconds?|[hms])\\b|\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b", RegexOption.IGNORE_CASE)
    fun read(lines: List<Line>): Availability {
        if (lines.none { it.text.contains("Experimentation Table", true) }) return Availability.UNKNOWN
        if (lines.any { it.red || timer.containsMatchIn(it.text) || Regex("cooldown|not available|(?:no|0) (?:available )?(?:sessions?|experiments?)|come back|already (?:done|completed)", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }) return Availability.COOLDOWN
        return if (lines.any { it.yellow } || lines.any { Regex("ready|available|click to|right.?click|start experiment", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }) Availability.READY else Availability.UNKNOWN
    }
    fun line(component: Component): Line {
        val parts = component.toFlatList()
        val legacy = component.string
        return Line(legacy.replace(Regex("§."), ""),
            parts.any { it.style.color?.value in listOf(0xFFFF55, 0xFFAA00) } || legacy.contains("§e"),
            parts.any { it.style.color?.value in listOf(0xFF5555, 0xAA0000) } || legacy.contains("§c") || legacy.contains("§4"))
    }
}

class ExperimentTableOpener {
    private val mc get() = FloydAddonsMod.mc
    private data class Target(val key: String, val position: Vec3, val entity: Entity? = null, val block: net.minecraft.core.BlockPos? = null)
    private var scanAt = 0L
    private var stableKey = ""
    private var readySince = 0L
    private var attempted = ""
    private var attemptAt = Long.MIN_VALUE / 2
    private var pendingTarget: Target? = null
    private var queuedAt = 0L
    var attempts = 0; private set
    var status = "Waiting for a nearby table"; private set
    fun reset() { scanAt = 0; stableKey = ""; readySince = 0; attempted = ""; pendingTarget = null; attempts = 0 }
    fun tick(now: Long, progress: Int, continuingSession: Boolean = false) {
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (mc.screen != null) { pendingTarget = null; return }
        pendingTarget?.let { target ->
            if (now - queuedAt < 100) return
            pendingTarget = null
            if (now - queuedAt > 1000 || target.position.distanceToSqr(player.eyePosition) > 16) return
            val hit = mc.hitResult
            when {
                target.entity != null && hit is EntityHitResult && hit.entity.isAlive && hit.entity.isPickable &&
                    hit.entity === target.entity && hit.entity.customName == null ->
                    mc.gameMode?.interact(player, hit.entity, hit, InteractionHand.MAIN_HAND)
                target.block != null && hit is BlockHitResult && hit.blockPos == target.block && player.mainHandItem.isEmpty ->
                    mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hit)
                else -> { status = "Look at the table to open it"; return }
            }
            attempted = target.key; attemptAt = now; attempts++; status = "Opening available Experimentation Table"
            return
        }
        if (now < scanAt) return
        scanAt = now + 250
        val labels = level.entitiesForRendering().filter { it.customName != null && it.distanceToSqr(player) <= 64 }.toList()
        val anchor = labels.filter { it.customName!!.string.contains("Experimentation Table", true) }.minByOrNull { it.distanceToSqr(player) }
            ?: run { stableKey = ""; attempted = ""; status = "Waiting for a nearby table"; return }
        val column = labels.filter { kotlin.math.abs(it.x - anchor.x) < .6 && kotlin.math.abs(it.z - anchor.z) < .6 && kotlin.math.abs(it.y - anchor.y) < 1.6 }
        val availability = ExperimentTableStatus.read(column.map { ExperimentTableStatus.line(it.customName!!) })
        if (availability != ExperimentTableStatus.Availability.READY && !(continuingSession && availability == ExperimentTableStatus.Availability.COOLDOWN)) {
            stableKey = ""; pendingTarget = null
            if (availability == ExperimentTableStatus.Availability.COOLDOWN) attempted = ""
            status = if (availability == ExperimentTableStatus.Availability.COOLDOWN) "Table is on cooldown" else "Waiting for table availability"
            return
        }
        val key = "${anchor.blockPosition()}:$progress"
        if (key != stableKey) { stableKey = key; readySince = now; return }
        if (key == attempted || now - readySince < 500 || now - attemptAt < 3000) return
        fun eligibleFurniture(entity: Entity): Boolean =
            entity !== player && entity !in column && entity.customName == null && entity.isPickable &&
                (entity !is ArmorStand || !entity.isMarker) && ExperimentTableTargetPolicy.contains(
                    entity.x - anchor.x, entity.y - anchor.y, entity.z - anchor.z, continuingSession)
        // During a known session, prefer the actual crosshair hitbox over a decorative part
        // nearer the hologram. Opening still requires the normal crosshair hit and reach check.
        val aimedFurniture = (mc.hitResult as? EntityHitResult)?.entity?.takeIf(::eligibleFurniture)
        val furniture = aimedFurniture ?: level.entitiesForRendering().filter(::eligibleFurniture)
            .minByOrNull { it.position().distanceToSqr(anchor.position()) }

        val target = furniture?.let { Target(key, it.boundingBox.center, entity = it) } ?: run {
            // Only an identified table/skull below its hologram can be a block target.
            val origin = anchor.blockPosition()
            val pos = (0..3).map { origin.below(it) }.firstOrNull {
                val block = level.getBlockState(it).block
                block == Blocks.ENCHANTING_TABLE || block is SkullBlock
            } ?: run { status = "Move within reach of the table"; return }
            if (!player.mainHandItem.isEmpty) { status = "Use an empty hand to open this table"; return }
            Target(key, Vec3.atCenterOf(pos), block = pos)
        }
        if (target.position.distanceToSqr(player.eyePosition) > 16) { status = "Move within reach of the table"; return }
        // Wait for a normal crosshair hit; Auto Open never changes the player view.
        pendingTarget = target; queuedAt = now
    }
}
