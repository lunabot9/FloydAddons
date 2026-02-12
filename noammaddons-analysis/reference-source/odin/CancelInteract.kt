// SOURCE: https://github.com/odtheking/Odin/blob/main/odinclient/src/main/kotlin/me/odinclient/features/impl/skyblock/CancelInteract.kt
// Cancels block interactions (fences, hoppers, etc.) so item abilities can be used instead
// Whitelist: levers, chests, buttons (always interact)
// Blacklist: fences, fence gates, hoppers (cancel interact, use item ability)
// Also has "No Break Reset" which prevents lore updates from resetting mining progress
//
// 1.21.10 equivalent:
// Mixin into ClientPlayerInteractionManager.interactBlock() or
// the isAir check in MinecraftClient.doItemUse()
// Return true for blacklisted blocks to skip their interaction
