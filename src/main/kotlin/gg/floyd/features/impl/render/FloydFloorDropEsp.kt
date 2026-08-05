package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.events.RenderEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.Color
import gg.floyd.utils.Colors
import gg.floyd.utils.render.drawTracerFan
import gg.floyd.utils.render.drawWireFrameBox
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min

object FloydFloorDropEsp : Module(
    name = "Floor Drop ESP",
    category = Category.RENDER,
    description = "Highlights clustered floor-drop string piles."
) {
    private const val mobEspRangeSqr = 2500.0
    private const val floorDropClusterRadius = 1.0
    private const val floorDropDisplayCount = 3

    val tracers by BooleanSetting("Tracers", false, desc = "Draws floor-drop tracers.")
    val boxes by BooleanSetting("Boxes", true, desc = "Draws floor-drop boxes.")
    private val color by ColorSetting("Color", Colors.MINECRAFT_GREEN.copy(), desc = "ESP color for floor drops.")

    init {
        on<RenderEvent.Extract> {
            if (!enabled) return@on
            val level = mc.level ?: return@on
            val player = mc.player ?: return@on
            val candidateDisplays = level.entitiesForRendering()
                .filter { it.distanceToSqr(player) <= mobEspRangeSqr }
                .filter(::shouldConsiderFloorDropDisplay)
            val consumedIds = HashSet<Int>()
            val tracerTargets = if (tracers) ArrayList<Vec3>() else null

            for (display in candidateDisplays) {
                if (display.id in consumedIds) continue
                val cluster = candidateDisplays.filter { other ->
                    isSameCluster(display, other, floorDropClusterRadius)
                }
                if (cluster.size < floorDropDisplayCount) continue

                consumedIds.addAll(cluster.map(Entity::getId))
                val box = floorBox(cluster)
                if (boxes) drawWireFrameBox(box, color, thickness = 2f, depth = false)
                if (tracers) tracerTargets?.add(box.center)
            }

            tracerTargets?.takeIf { it.isNotEmpty() }?.let {
                drawTracerFan(it, color, thickness = 2f, depth = false, mirrorBehindCamera = true)
            }
        }
    }

    private fun shouldConsiderFloorDropDisplay(entity: Entity): Boolean {
        if (entity.isRemoved) return false
        if (entityTypeId(entity) != FLOOR_DROP_DISPLAY_TYPE) return false
        return entity.y - entity.blockY <= 1.0
    }

    private fun isSameCluster(a: Entity, b: Entity, radius: Double): Boolean {
        if (a === b) return true
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        if (dx * dx + dz * dz > radius * radius) return false
        return dy * dy <= radius * radius
    }

    private fun floorBox(cluster: List<Entity>): AABB {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY

        for (item in cluster) {
            val box = item.boundingBox
            minX = min(minX, box.minX)
            minY = min(minY, box.minY)
            minZ = min(minZ, box.minZ)
            maxX = max(maxX, box.maxX)
            maxY = max(maxY, box.maxY)
            maxZ = max(maxZ, box.maxZ)
        }

        val blockPos = BlockPos.containing((minX + maxX) / 2.0, minY, (minZ + maxZ) / 2.0)

        return AABB(
            min(blockPos.x + 0.06, minX - 0.05),
            blockPos.y + 0.01,
            min(blockPos.z + 0.06, minZ - 0.05),
            max(blockPos.x + 0.94, maxX + 0.05),
            maxY + 0.08,
            max(blockPos.z + 0.94, maxZ + 0.05),
        )
    }

    private fun entityTypeId(entity: Entity): String =
        BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()

    private const val FLOOR_DROP_DISPLAY_TYPE = "minecraft:item_display"
}
