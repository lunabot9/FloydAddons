package gg.floyd.features.impl.pvp.experiments

/** Furniture can comprise multiple armor stands extending beyond its centered hologram. */
object ExperimentTableTargetPolicy {
    fun contains(dx: Double, dy: Double, dz: Double, continuingSession: Boolean): Boolean {
        val radius = if (continuingSession) 2.5 else .9
        return kotlin.math.abs(dx) < radius && kotlin.math.abs(dz) < radius && dy <= 0 && dy > -3
    }
}
