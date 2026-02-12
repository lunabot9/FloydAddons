// SOURCE: https://github.com/odtheking/Odin/blob/main/odinclient/src/main/kotlin/me/odinclient/features/impl/render/Trajectories.kt
// Bow/pearl trajectory prediction with entity hit detection
// Key algorithm: simulates projectile physics tick-by-tick
// - Pearl: velocity * 0.99 per tick, -0.03 gravity per tick
// - Arrow: velocity * 0.99 per tick, -0.05 gravity per tick
// - Checks block collision via rayTraceBlocks
// - Checks entity collision via getEntitiesWithinAABBExcludingEntity
// - Supports Terminator triple-shot trajectories (0, -5, +5 yaw offset)
// - Renders: lines, boxes at impact, planes on block face, entity outlines
// FULL SOURCE CODE: See /tmp output or fetch from GitHub directly
// (File is ~250 lines, saved separately for reference)
