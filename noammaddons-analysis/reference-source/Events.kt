// SOURCE: https://github.com/Noamm9/NoammAddons/blob/master/src/main/kotlin/noammaddons/events/Events.kt
// Custom event system - all events extend Forge's Event class
// Key events that FloydAddons could adopt:

// @Cancelable class ClickEvent.LeftClickEvent / RightClickEvent
// @Cancelable class PacketEvent.Received(packet) / Sent(packet)
// class RenderOverlayNoCaching(partialTicks)  — HUD render after game overlay
// class RenderOverlay(partialTicks)           — General overlay render
// class RenderWorld(partialTicks)             — 3D world render pass
// @Cancelable class Chat(component)           — Incoming chat message
// @Cancelable class Actionbar(component)      — Action bar text
// class ServerTick                            — Server tick detected
// class Tick                                  — Client tick
// @Cancelable class PreKeyInputEvent(key, char) — Before key processing
// class RenderEntityEvent(entity, x, y, z, pt)  — Before entity render
// class PostRenderEntityEvent(...)              — After entity render
// @Cancelable class SoundPlayEvent(name, vol, pitch, pos) — Sound about to play
// class BossbarUpdateEvent.Pre/Post            — Boss bar health change
// class PostEntityMetadataEvent(entity)        — Entity metadata update (name, etc.)
// class WorldUnloadEvent                       — World unload
// class EntityLeaveWorldEvent(entity, world)   — Entity removed
// class InventoryFullyOpenedEvent(title, windowId, slotCount, items) — Container ready

// For Fabric 1.21.10, these would be replaced by:
// - Fabric API callbacks (HudRenderCallback, WorldRenderEvents, etc.)
// - Custom events via Mixin @Inject + a simple event bus
// - Packet listeners via ClientPlayNetworking or mixin into ClientPlayNetworkHandler
