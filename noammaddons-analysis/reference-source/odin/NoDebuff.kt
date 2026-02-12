// SOURCE: https://github.com/odtheking/Odin/blob/main/odinclient/src/main/kotlin/me/odinclient/features/impl/render/NoDebuff.kt
package me.odinclient.features.impl.render

// Comprehensive debuff removal:
// - No Blindness (fog density = 0)
// - No Portal Effect (cancel PORTAL overlay)
// - No Pumpkin Overlay (cancel HELMET overlay)
// - No Shield Particle (cancel SPELL_WITCH and HEART particles)
// - No Water FOV (multiply FOV by 7/6 when underwater)
// - No Fire Overlay (cancel FIRE block overlay)
// - See Through Blocks (cancel BLOCK overlay)
// - No Nausea (static field checked by mixin in EntityLivingBase)
// - No Hurt Cam (static field checked by mixin in EntityRenderer)
//
// 1.21.10 equivalents:
// - Blindness: Mixin GameRenderer fog methods
// - Portal: Cancel InGameOverlayRenderer portal rendering
// - Fire: Cancel InGameOverlayRenderer fire rendering (FloydAddons already has this)
// - Water FOV: Mixin GameRenderer.getFov()
// - Nausea: Mixin LivingEntity.hasStatusEffect() for NAUSEA
// - Hurt cam: Mixin GameRenderer.tiltViewWhenHurt() (FloydAddons already has this)
// - Pumpkin: Mixin InGameHud or override helmet overlay rendering
