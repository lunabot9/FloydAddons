// SOURCE: https://github.com/odtheking/Odin/blob/main/odinclient/src/main/kotlin/me/odinclient/features/impl/render/ChestEsp.kt
// Chest ESP with two render modes:
// 1. CHAMS - Renders chests through walls using polygon offset trick
//    - GL_POLYGON_OFFSET_FILL + doPolygonOffset(1, -1000000)
//    - Makes chests render in front of everything
// 2. OUTLINE - Draws wireframe box at chest position
// Features:
// - Hide clicked chests (tracks C08PacketPlayerBlockPlacement)
// - Only dungeon / Only Crystal Hollows filters
// - Custom color with alpha
// - Scans loadedTileEntityList every 200ms for TileEntityChest positions
//
// 1.21.10: Use BlockEntityRendererFactory or WorldRenderEvents
// Track chest interactions via mixin on ClientPlayerInteractionManager
