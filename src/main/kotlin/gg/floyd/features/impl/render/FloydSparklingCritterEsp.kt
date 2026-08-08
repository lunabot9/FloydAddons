package gg.floyd.features.impl.render

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.events.RenderEvent
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.Colors
import gg.floyd.utils.modMessage
import gg.floyd.utils.render.drawTracerFan
import gg.floyd.utils.render.drawWireFrameBox
import gg.floyd.utils.renderBoundingBox
import gg.floyd.utils.renderPos
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Highlights sparkling critters that are already present in the client world.
 *
 * Detection deliberately reads only [ClientLevel.entitiesForRendering]. It never requests entities,
 * interacts with them, or sends a packet, so the maximum observable range remains the overlap between
 * the player's configured render distance and the server's normal entity-tracking distance.
 */
object FloydSparklingCritterEsp : Module(
    name = "Sparkling Critter ESP",
    category = Category.RENDER,
    description = "Highlights loaded SPARKLING critters across your configured render distance and announces new detections in chat."
) {
    val tracers by BooleanSetting("Tracers", false, desc = "Draws tracers to sparkling critters.")
    val hitboxes by BooleanSetting("Hitboxes", true, desc = "Draws boxes around sparkling critters.")
    private val color by ColorSetting("Color", Colors.ACCENT.copy(), desc = "Color for sparkling critter tracers and hitboxes.")

    private val resolvedMobIds = linkedSetOf<Int>()
    private val fallbackLabelIds = linkedSetOf<Int>()
    private val announcedLabelUuids = hashSetOf<UUID>()
    private val renderHits = AtomicLong()
    private val notificationHits = AtomicLong()
    private var lastDetection: Detection? = null

    init {
        on<TickEvent.End> {
            if (enabled) scanLoadedCritters()
        }

        on<RenderEvent.Extract> {
            if (!enabled) return@on
            val level = mc.level ?: return@on
            val tracerTargets = if (tracers) ArrayList<Vec3>() else null

            for (id in resolvedMobIds) {
                val mob = level.getEntity(id) ?: continue
                renderCritter(mob.renderBoundingBox, mob.renderPos.add(0.0, mob.bbHeight / 2.0, 0.0), tracerTargets)
            }
            for (id in fallbackLabelIds) {
                val label = level.getEntity(id) as? ArmorStand ?: continue
                val box = critterNametagBox(label.x, label.y, label.z)
                renderCritter(box, box.center, tracerTargets)
            }

            tracerTargets?.takeIf { it.isNotEmpty() }?.let {
                drawTracerFan(it, color, thickness = 2f, depth = false, mirrorBehindCamera = true)
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> clearRuntimeState() }
    }

    override fun onEnable() {
        clearRuntimeState()
        super.onEnable()
    }

    override fun onDisable() {
        clearRuntimeState()
        super.onDisable()
    }

    private fun RenderEvent.Extract.renderCritter(
        box: AABB,
        tracerTarget: Vec3,
        tracerTargets: MutableList<Vec3>?
    ) {
        renderHits.incrementAndGet()
        if (hitboxes) drawWireFrameBox(box, color, thickness = 2f, depth = false)
        if (tracers) tracerTargets?.add(tracerTarget)
    }

    private fun scanLoadedCritters() {
        val level = mc.level ?: return clearTargets()
        val player = mc.player ?: return clearTargets()
        val renderDistanceChunks = mc.options.effectiveRenderDistance
        val loadedEntities = level.entitiesForRendering().toList()
        val labels = loadedEntities
            .filterIsInstance<ArmorStand>()
            .filterNot(Entity::isRemoved)
            .filter(::isSparklingCritterLabel)
            .filter {
                isWithinRenderDistance(
                    player.blockX,
                    player.blockZ,
                    it.blockX,
                    it.blockZ,
                    renderDistanceChunks
                )
            }
        val mobs = loadedEntities
            .filterIsInstance<LivingEntity>()
            .filterNot { it is ArmorStand || it === player || it.isRemoved }
        val mobsById = mobs.associateBy(Entity::getId)
        val claimedMobIds = hashSetOf<Int>()

        resolvedMobIds.clear()
        fallbackLabelIds.clear()

        for (label in labels) {
            announceOnce(label)
            val direct = mobsById[label.id - 1]
                ?.takeIf { isNearCritterLabel(it.x, it.y, it.z, label.x, label.y, label.z) }
                ?.takeIf { claimedMobIds.add(it.id) }
            val mob = direct ?: mobs
                .asSequence()
                .filterNot { claimedMobIds.contains(it.id) }
                .filter { isNearCritterLabel(it.x, it.y, it.z, label.x, label.y, label.z) }
                .minByOrNull { horizontalDistanceSqr(it, label) }
                ?.also { claimedMobIds.add(it.id) }

            if (mob == null) fallbackLabelIds.add(label.id) else resolvedMobIds.add(mob.id)
        }
    }

    private fun announceOnce(label: ArmorStand) {
        if (!announcedLabelUuids.add(label.uuid)) return
        val name = sparklingCritterName(label.customName?.string ?: label.name.string)
        val detection = Detection(name, label.blockX, label.blockY, label.blockZ)
        lastDetection = detection
        notificationHits.incrementAndGet()
        // addClientSystemMessage is entirely client-side; this never enters the network connection.
        modMessage(detectionMessage(detection.name, detection.x, detection.y, detection.z))
    }

    private fun clearTargets() {
        resolvedMobIds.clear()
        fallbackLabelIds.clear()
    }

    private fun clearRuntimeState() {
        clearTargets()
        announcedLabelUuids.clear()
        lastDetection = null
    }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "tracers" to tracers,
        "hitboxes" to hitboxes,
        "renderDistanceChunks" to runCatching { mc.options.effectiveRenderDistance }.getOrNull(),
        "resolvedMobCount" to resolvedMobIds.size,
        "fallbackLabelCount" to fallbackLabelIds.size,
        "announcedCritterCount" to announcedLabelUuids.size,
        "lastDetection" to lastDetection?.let {
            mapOf("name" to it.name, "x" to it.x, "y" to it.y, "z" to it.z)
        },
        "renderHits" to renderHits.get(),
        "notificationHits" to notificationHits.get()
    )

    private fun isSparklingCritterLabel(entity: Entity): Boolean =
        isSparklingCritterLabelText(entity.name.string) ||
            isSparklingCritterLabelText(entity.displayName.string) ||
            entity.customName?.string?.let(::isSparklingCritterLabelText) == true

    internal fun isSparklingCritterLabelText(text: String): Boolean =
        sparklingCritterName(text).contains(SPARKLING_CRITTER_MARKER, ignoreCase = true)

    internal fun sparklingCritterName(text: String): String {
        val stripped = text.replace(FORMATTING_CODE, "").trim()
        if (!stripped.startsWith('{') && !stripped.startsWith('[')) return stripped
        return runCatching { componentText(JsonParser.parseString(stripped)) }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: stripped
    }

    private fun componentText(element: JsonElement): String = when {
        element.isJsonPrimitive -> element.asString
        element.isJsonArray -> element.asJsonArray.joinToString("") { componentText(it) }
        element.isJsonObject -> buildString {
            val objectValue = element.asJsonObject
            objectValue.get("text")?.takeIf { it.isJsonPrimitive }?.asString?.let(::append)
            objectValue.get("extra")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { append(componentText(it)) }
        }
        else -> ""
    }

    internal fun detectionMessage(name: String, x: Int, y: Int, z: Int): String =
        "§6Sparkling critter detected: §f$name §7at §f$x, $y, $z"

    /** Matches Minecraft's chunk-based view-distance boundary without loading or requesting a chunk. */
    internal fun isWithinRenderDistance(
        playerBlockX: Int,
        playerBlockZ: Int,
        entityBlockX: Int,
        entityBlockZ: Int,
        renderDistanceChunks: Int
    ): Boolean {
        val playerChunkX = Math.floorDiv(playerBlockX, CHUNK_SIZE)
        val playerChunkZ = Math.floorDiv(playerBlockZ, CHUNK_SIZE)
        val entityChunkX = Math.floorDiv(entityBlockX, CHUNK_SIZE)
        val entityChunkZ = Math.floorDiv(entityBlockZ, CHUNK_SIZE)
        return abs(entityChunkX - playerChunkX) <= renderDistanceChunks &&
            abs(entityChunkZ - playerChunkZ) <= renderDistanceChunks
    }

    internal fun isNearCritterLabel(
        mobX: Double,
        mobY: Double,
        mobZ: Double,
        labelX: Double,
        labelY: Double,
        labelZ: Double
    ): Boolean {
        val dx = mobX - labelX
        val dz = mobZ - labelZ
        if (dx * dx + dz * dz > LABEL_MAX_HORIZONTAL_DISTANCE_SQR) return false
        val dy = mobY - labelY
        return dy <= 1.0 && dy >= -5.0
    }

    internal fun critterNametagBox(x: Double, labelY: Double, z: Double): AABB = AABB(
        x - NAMETAG_BOX_HALF_WIDTH,
        labelY - NAMETAG_BOX_HEIGHT,
        z - NAMETAG_BOX_HALF_WIDTH,
        x + NAMETAG_BOX_HALF_WIDTH,
        labelY,
        z + NAMETAG_BOX_HALF_WIDTH
    )

    private fun horizontalDistanceSqr(first: Entity, second: Entity): Double {
        val dx = first.x - second.x
        val dz = first.z - second.z
        return dx * dx + dz * dz
    }

    private data class Detection(val name: String, val x: Int, val y: Int, val z: Int)

    private val FORMATTING_CODE = Regex("§.")
    private const val SPARKLING_CRITTER_MARKER = "SPARKLING"
    private const val CHUNK_SIZE = 16
    private const val LABEL_MAX_HORIZONTAL_DISTANCE_SQR = 4.0
    private const val NAMETAG_BOX_HALF_WIDTH = 0.45
    private const val NAMETAG_BOX_HEIGHT = 2.0
}
