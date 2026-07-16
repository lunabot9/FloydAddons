package gg.floyd.features.impl.cosmetic

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal const val SHARED_APPEARANCE_VERSION = 1

data class SharedModelAppearance(
    val enabled: Boolean = false,
    val id: String = FloydPlayerModelSelection.models.first(),
    val showHeads: Boolean = false,
)

data class SharedCapeAppearance(val enabled: Boolean = false, val id: String = "default")

data class SharedConeAppearance(
    val enabled: Boolean = false,
    val id: String = "default",
    val height: Float = 0.45f,
    val radius: Float = 0.25f,
    val yOffset: Float = -0.5f,
    val rotation: Float = 0.0f,
    val spinSpeed: Float = 0.0f,
)

data class SharedSkinAppearance(val enabled: Boolean = false, val id: String = "default")

data class SharedSizeAppearance(
    val enabled: Boolean = false,
    val x: Float = 1.0f,
    val y: Float = 1.0f,
    val z: Float = 1.0f,
)

data class SharedNeckHiderAppearance(
    val enabled: Boolean = false,
    val nickname: String = "",
)

data class SharedAppearance(
    val version: Int = SHARED_APPEARANCE_VERSION,
    val model: SharedModelAppearance = SharedModelAppearance(),
    val cape: SharedCapeAppearance = SharedCapeAppearance(),
    val cone: SharedConeAppearance = SharedConeAppearance(),
    val skin: SharedSkinAppearance = SharedSkinAppearance(),
    val size: SharedSizeAppearance = SharedSizeAppearance(),
    val neckHider: SharedNeckHiderAppearance = SharedNeckHiderAppearance(),
) {
    fun sanitized(): SharedAppearance = copy(
        version = SHARED_APPEARANCE_VERSION,
        model = model.copy(
            id = model.id.takeIf(FloydPlayerModelSelection.models::contains)
                ?: FloydPlayerModelSelection.models.first()
        ),
        cape = cape.copy(id = "default"),
        cone = cone.copy(
            id = "default",
            height = cone.height.coerceIn(0.1f, 1.5f),
            radius = cone.radius.coerceIn(0.05f, 0.8f),
            yOffset = cone.yOffset.coerceIn(-1.5f, 0.5f),
            rotation = cone.rotation.coerceIn(0.0f, 360.0f),
            spinSpeed = cone.spinSpeed.coerceIn(0.0f, 360.0f),
        ),
        skin = skin.copy(id = "default"),
        size = size.copy(
            x = size.x.coerceIn(-1.0f, 5.0f),
            y = size.y.coerceIn(-1.0f, 5.0f),
            z = size.z.coerceIn(-1.0f, 5.0f),
        ),
        neckHider = neckHider.copy(nickname = neckHider.nickname.filterNot(Char::isISOControl).take(32))
    )
}

/** Render-thread-safe cache populated by the Floyd cosmetics presence service. */
internal class SharedAppearanceRegistry {
    private val appearances = ConcurrentHashMap<UUID, SharedAppearance>()

    fun get(uuid: UUID): SharedAppearance? = appearances[uuid]

    fun nicknameMappings(profiles: Map<UUID, String>): Map<String, String> = profiles.mapNotNull { (uuid, username) ->
        val neckHider = appearances[uuid]?.neckHider
        if (username.isBlank() || neckHider == null || !neckHider.enabled || neckHider.nickname.isBlank()) null
        else username to neckHider.nickname
    }.toMap()

    fun replace(requested: Set<UUID>, received: Map<UUID, SharedAppearance>) {
        requested.forEach { uuid ->
            val appearance = received[uuid]
            if (appearance == null || appearance.version != SHARED_APPEARANCE_VERSION) {
                appearances.remove(uuid)
            } else {
                appearances[uuid] = appearance.sanitized()
            }
        }
    }

    fun clear() = appearances.clear()

    fun size(): Int = appearances.size
}

/** Side-effect-free render lookup kept separate from the authenticated network module. */
internal object SharedNeckHiderNames {
    @Volatile private var active = false
    @Volatile private var current: Map<String, String> = emptyMap()

    fun setActive(value: Boolean) {
        active = value
    }

    fun replace(value: Map<String, String>) {
        current = value.toMap()
    }

    fun mappings(): Map<String, String> = if (active) current else emptyMap()

    fun hasMappings(): Boolean = active && current.isNotEmpty()

    fun clear() {
        current = emptyMap()
    }
}
