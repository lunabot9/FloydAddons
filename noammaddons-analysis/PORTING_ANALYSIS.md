# NoammAddons + OdinClient → FloydAddons Porting Analysis

> **Sources:**
> - [github.com/Noamm9/NoammAddons](https://github.com/Noamm9/NoammAddons) (Forge 1.8.9, Kotlin/Java)
> - [github.com/odtheking/Odin](https://github.com/odtheking/Odin) — `odinclient/` cheater module (Forge 1.8.9, Kotlin/Java)
> **Target:** FloydAddons (Fabric 1.21.10, Java 21)
> **Generated:** 2026-02-12

---

## Directory Guide (for Claude agents)

```
noammaddons-analysis/
├── PORTING_ANALYSIS.md              ← THIS FILE (master index)
├── centered-crosshair-ref/          ← Reference code for centered crosshair (1.21.x Fabric)
│   ├── InGameHudMixin.java          ← Crosshair centering mixin
│   └── DrawContextMixin.java        ← Sub-pixel texture drawing support
└── reference-source/                ← Organized source snippets
    ├── misc/                        ← NoammAddons: Animations, Camera, MotionBlur, etc.
    ├── hud/                         ← NoammAddons: HUD overlay features
    ├── esp/                         ← NoammAddons: Entity highlighting features
    ├── general/                     ← NoammAddons: General utility features
    ├── dungeons/                    ← NoammAddons: Dungeon-specific features
    ├── gui/                         ← NoammAddons: Custom GUI screens
    ├── alerts/                      ← NoammAddons: Alert/notification features
    ├── slayers/                     ← NoammAddons: Slayer features
    ├── mixins/                      ← NoammAddons: Java mixin classes
    ├── utils/                       ← NoammAddons: Utility classes
    └── odin/                        ← OdinClient: Cheater module features
        ├── SecretHitboxes.kt        ← Full-block hitbox for levers/buttons/skulls/chests
        ├── CloseChest.kt            ← Auto-close dungeon chests
        ├── AutoClicker.kt           ← CPS-based auto clicker with keybinds
        ├── GhostBlocks.kt           ← Ghost block creation (multiple modes)
        ├── Trajectories.kt          ← Bow/pearl trajectory prediction
        ├── NoDebuff.kt              ← Remove blindness/portal/fire/nausea/hurtcam
        ├── FarmingHitboxes.kt       ← Full-block hitbox for crops
        ├── CancelInteract.kt        ← Cancel fence/hopper interaction for abilities
        └── ChestEsp.kt              ← Chest ESP through walls
```

**FloydAddons source location:** `/home/twaldin/FloydAddons/app/src/main/java/floydaddons/not/dogshit/`
**FloydAddons mixin location:** `/home/twaldin/FloydAddons/app/src/main/java/floydaddons/not/dogshit/mixin/`
**FloydAddons mixin JSON:** `/home/twaldin/FloydAddons/app/src/main/resources/floydaddons.mixins.json`

---

## Platform Differences: Forge 1.8.9 → Fabric 1.21.10

| Aspect | NoammAddons (1.8.9 Forge) | FloydAddons (1.21.10 Fabric) |
|--------|---------------------------|-------------------------------|
| Language | Kotlin + Java | Java 21 |
| Mod Loader | Forge (MinecraftForge) | Fabric (Fabric Loader) |
| Events | `@SubscribeEvent` + `MinecraftForge.EVENT_BUS` | Fabric API callbacks + Mixin `@Inject` |
| Rendering | `GlStateManager` + legacy OpenGL | Modern `DrawContext`, `MatrixStack`, `VertexConsumer` |
| GL Pipeline | Fixed-function OpenGL (GL11/GL20) | Render pipeline / shader-based (`RenderPipeline`) |
| HUD Rendering | `GuiIngame.renderGameOverlay()` | `InGameHud.render()` + `HudRenderCallback` |
| GUI Screens | `GuiScreen` (legacy) | `Screen` (modern Fabric/vanilla) |
| Packets | `S03PacketTimeUpdate`, `S0FPacketSpawnMob` etc. | `TimeUpdateS2CPacket`, NMS packet classes |
| Item Rendering | `ItemRenderer.renderItemInFirstPerson()` | `HeldItemRenderer.renderFirstPersonItem()` |
| Entity Rendering | `RendererLivingEntity` | `LivingEntityRenderer`, `EntityRenderer` |
| Mixin Compat | SpongePowered Mixin 0.8.5 | SpongePowered Mixin (bundled in Fabric) |
| Config | Custom GSON / PogObject | Custom GSON (FloydAddonsConfig) |
| Attack System | No attack cooldown (1.8 combat) | Attack cooldown bar, indicator, 1.9+ combat |
| Block Rendering | Legacy chunk rendering | Modern Sodium-compatible chunk rendering |
| Mapping | MCP (searge) | Yarn (Fabric intermediary) |

### Key 1.21.10-Specific Changes
- **Rendering pipeline overhaul**: 1.21.x uses `RenderPipeline` objects instead of raw GL calls
- **DrawContext**: Replaces direct `Tessellator`/`WorldRenderer` for 2D HUD rendering
- **Matrix3x2fStack**: GUI uses 2D matrix stacks (not 4x4 `MatrixStack`)
- **GuiRenderState**: New state-based GUI rendering system
- **Entity rendering**: `EntityModel` system different from 1.8.9 `ModelBase`
- **Packet system**: Fully refactored packet classes, networking layer changes
- **Sodium integration**: Block rendering may need Sodium-compatible mixins (FloydAddons already has these for X-ray)

---

## Suggested Config & File Structure Changes

### Config System Enhancements
1. **Add per-feature toggle persistence** — Currently `FloydAddonsConfig` is monolithic. Consider a `Map<String, Boolean>` for feature toggles loaded from `config.json`.
2. **Add HUD element positioning system** — NoammAddons uses `GuiElement` + `HudEditorScreen` for draggable HUD elements. FloydAddons has this for Inventory/Scoreboard HUD but should generalize it for new HUD features.
3. **Add color settings to config** — For ESP, block overlay, crosshair features. Store as ARGB integers.

### File Structure Recommendations
```
app/src/main/java/floydaddons/not/dogshit/
├── client/
│   ├── FloydAddonsClient.java          (entry point - exists)
│   ├── FloydAddonsConfig.java          (config manager - exists)
│   ├── features/                       ← NEW: feature subdirectory
│   │   ├── combat/                     ← attack cooldown, classic click
│   │   ├── hud/                        ← HUD overlays (FPS, clock, etc.)
│   │   ├── visual/                     ← animations, camera, motion blur, block overlay
│   │   ├── cosmetic/                   ← capes, cones, skins (move existing here)
│   │   └── misc/                       ← time changer, damage splash, etc.
│   ├── gui/                            ← GUI screens (move existing here)
│   └── util/                           ← Shared utilities
├── mixin/                              (mixins - exists)
```

This mirrors NoammAddons' `features/impl/` hierarchy but adapted for Floyd's Java-only approach.

---

## Feature Analysis

### Priority Tiers
- **P0 — IMMEDIATE** (simple, high impact, already partially implemented or trivial mixin)
- **P1 — HIGH** (moderate effort, strong value, clear implementation path)
- **P2 — MEDIUM** (significant effort, useful features)
- **P3 — LOW** (complex, Hypixel-specific, or niche)
- **N/A — NOT APPLICABLE** (Hypixel Skyblock-specific, no value for general use)

---

## P0 — IMMEDIATE PRIORITY

### 1. No Attack Cooldown Indicator (NEW — NOT FROM NOAMMADDONS)

**What it does:** Hides the attack cooldown progress bar that appears on the crosshair/hotbar in 1.9+ combat. Makes the HUD cleaner, especially for PvP players who prefer 1.8 combat feel.

**Difficulty:** EASY
**Files to create/modify:**
- New mixin: `HiderAttackIndicatorMixin.java`
- Modify: `HidersConfig.java` (add toggle)
- Modify: `floydaddons.mixins.json` (register mixin)

**Implementation approach:**
The attack indicator is rendered in `InGameHud.renderCrosshair()` method. In 1.21.10, the indicator type is controlled by `options.getAttackIndicator()` which returns an `AttackIndicator` enum (OFF, CROSSHAIR, HOTBAR).

```java
// Mixin approach — simplest
@Mixin(InGameHud.class)
public class HiderAttackIndicatorMixin {
    // Option A: Cancel the indicator rendering entirely
    @Inject(method = "renderCrosshair", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/option/GameOptions;getAttackIndicator()Lnet/minecraft/client/option/SimpleOption;"),
        cancellable = true)
    private void hideAttackIndicator(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (HidersConfig.hideAttackCooldown) {
            // Skip the attack indicator portion of renderCrosshair
        }
    }

    // Option B: Override getAttackCooldownProgress to always return 1.0
    // This makes the game think cooldown is always full
}
```

**Alternative simpler approach:** Mixin into `LivingEntity.getAttackCooldownProgress()` to always return `1.0f` when the hider is enabled. This is the cleanest approach — it removes the indicator everywhere and prevents any "weak attack" visual.

```java
@Mixin(LivingEntity.class)
public class HiderAttackCooldownMixin {
    @Inject(method = "getAttackCooldownProgress", at = @At("HEAD"), cancellable = true)
    private void hideAttackCooldown(float baseTime, CallbackInfoReturnable<Float> cir) {
        if (HidersConfig.hideAttackCooldown) {
            cir.setReturnValue(1.0f);
        }
    }
}
```

**Config addition to HidersConfig.java:**
```java
public boolean hideAttackCooldown = false;
```

**References:**
- [Remove Attack Cooldown mod](https://www.curseforge.com/minecraft/mc-mods/remove-attack-cooldown) — CurseForge
- [No Attack Cooldown & Damage Delay](https://modrinth.com/mod/no-attack-cooldown-damage-delay) — Modrinth

---

### 2. Centered Crosshair (NEW — NOT FROM NOAMMADDONS)

**What it does:** Fixes the crosshair position so its center pixel actually matches the true screen center. Vanilla Minecraft's crosshair is off by a half-pixel due to integer coordinate rounding.

**Difficulty:** MODERATE (requires sub-pixel rendering support)
**Files to create/modify:**
- New mixin: `CenteredCrosshairMixin.java`
- New class: `SubpixelTexturedQuad.java` (render state for float-precision drawing)
- New interface: `DrawContextFloatDrawTexture.java`
- Modify: `HidersConfig.java` (add toggle)
- Modify: `floydaddons.mixins.json`

**Implementation approach:**
Full reference implementation available at `centered-crosshair-ref/` directory. The approach:

1. Redirect `InGameHud.renderCrosshair()` to use float coordinates instead of int
2. Calculate true screen center: `framebufferWidth / scaleFactor / 2`
3. Render crosshair at `(center - 7.5, center - 7.5)` with sub-pixel precision
4. Requires a custom `DrawContext` mixin to add float-precision texture drawing

**Key code (from centered-crosshair-ref/InGameHudMixin.java):**
```java
@Redirect(method = "renderCrosshair", at = @At(value = "INVOKE",
    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(...)V", ordinal = 0))
private void drawTextureRedirect(DrawContext instance, RenderPipeline pipeline,
    Identifier sprite, int x, int y, int width, int height) {
    float scaleFactor = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
    float scaledCenterX = (MinecraftClient.getInstance().getWindow().getFramebufferWidth() / scaleFactor) / 2f;
    float scaledCenterY = (MinecraftClient.getInstance().getWindow().getFramebufferHeight() / scaleFactor) / 2f;
    // Draw at sub-pixel position for true centering
    instance.centered_crosshair$drawGuiTexture(pipeline, sprite,
        Math.round((scaledCenterX - 7.5f) * 4) / 4f,
        Math.round((scaledCenterY - 7.5f) * 4) / 4f, 15, 15);
}
```

**Reference:** [github.com/JustAlittleWolf/centered-crosshair](https://github.com/JustAlittleWolf/centered-crosshair)

---

### 3. Smooth Boss Bar

**What it does:** Animates boss health bar changes with cubic easing instead of instant jumps.

**Difficulty:** EASY
**Source ref:** `noammaddons/features/impl/misc/SmoothBossBar.kt`

**NoammAddons approach:**
- Mixin into `GuiIngame.renderBossHealth` → cancel and render custom
- Track `smoothBossBarHealth` with cubic easing: `if (t < 0.5) 4t³ else 1 - (-2t+2)³/2`
- Lerp between current displayed health and target health

**1.21.10 adaptation:**
- In 1.21.10: Boss bars use `BossBarHud.render()` with `ClientBossBar` instances
- Mixin target: `net.minecraft.client.gui.hud.BossBarHud`
- Method: `renderBossBar(DrawContext, int, int, ClientBossBar)`
- The health fraction is `bossBar.getPercent()` (0.0-1.0)
- Store a `Map<UUID, Float>` for smooth interpolation per boss bar

```java
@Mixin(BossBarHud.class)
public class SmoothBossBarMixin {
    @Unique private static final Map<UUID, Float> smoothHealth = new HashMap<>();

    @Redirect(method = "renderBossBar", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/boss/ClientBossBar;getPercent()F"))
    private float smoothPercent(ClientBossBar bar) {
        float target = bar.getPercent();
        float current = smoothHealth.getOrDefault(bar.getUuid(), target);
        float eased = lerp(current, target, 0.1f); // or use cubic easing
        smoothHealth.put(bar.getUuid(), eased);
        return eased;
    }
}
```

---

### 4. Time Changer

**What it does:** Override the client-side world time to a fixed value (day, night, sunset, etc.).

**Difficulty:** EASY
**Source ref:** `noammaddons/features/impl/misc/TimeChanger.kt`

**NoammAddons approach:**
- Intercept `S03PacketTimeUpdate` packet → set custom world time → cancel packet

**1.21.10 adaptation:**
- Packet class: `net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket`
- Mixin into `ClientPlayNetworkHandler.onWorldTimeUpdate()` or use a client packet listener
- After packet is processed, override `world.setTimeOfDay(customTime)`

```java
@Mixin(ClientPlayNetworkHandler.class)
public class TimeChangerMixin {
    @Inject(method = "onWorldTimeUpdate", at = @At("TAIL"))
    private void onTimeUpdate(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        if (RenderConfig.timeChangerEnabled) {
            long[] times = {1000L, 6000L, 12000L, 13000L, 18000L, 23000L};
            MinecraftClient.getInstance().world.setTimeOfDay(times[RenderConfig.timeChangerMode]);
        }
    }
}
```

**Config additions:**
```java
public boolean timeChangerEnabled = false;
public int timeChangerMode = 0; // 0=Day, 1=Noon, 2=Sunset, 3=Night, 4=Midnight, 5=Sunrise
```

---

## P1 — HIGH PRIORITY

### 5. Custom Item Animations (View Model Transform)

**What it does:** Customize the first-person held-item position, rotation, scale, and swing behavior.

**Difficulty:** MODERATE
**Source ref:** `noammaddons/features/impl/misc/Animations.kt`, `noammaddons/mixins/MixinItemRenderer.java`
**FloydAddons existing:** Already has `AnimationConfig.java`, `ItemInHandRendererMixin.java`, `SwingDurationMixin.java`

**NoammAddons approach:**
- X/Y/Z translation (-2.5 to 3.0 range)
- Yaw/Pitch/Roll rotation (-180 to 180)
- Size scaling via `exp(size)` for exponential feel
- Speed: Swing speed modifier with haste ignore option
- No equip reset: `equipProgress` forced to 0
- No swing: Cancel `doItemUsedTransformations`
- Scale swing: Exponential-scaled swing arc
- Hook: `transformFirstPersonItem()` → apply all transforms via `GlStateManager`

**1.21.10 differences:**
- FloydAddons already has `ItemInHandRendererMixin` and `AnimationConfig` — this is mostly **enhancing existing code**
- 1.21.10 uses `MatrixStack` instead of `GlStateManager` for transforms
- `HeldItemRenderer.renderFirstPersonItem()` is the target method
- Swing progress is accessed via `Hand` enum parameters

**What to add to existing AnimationConfig:**
- Exponential size scaling (NoammAddons uses `exp(size)` which feels better than linear)
- Scale swing option (swing arc scales with item size)
- Ignore haste toggle for swing speed
- Button to reset all animation values to defaults

---

### 6. Advanced Camera Features

**What it does:** Smooth sneak animation, custom FOV, remove water FOV, disable blindness/nausea/portal effects, remove face-block overlay, remove selfie cam.

**Difficulty:** MODERATE
**Source ref:** `noammaddons/features/impl/misc/Camera.kt`
**FloydAddons existing:** Already has `CameraConfig.java`, `CameraMixin.java` and friends for freecam/freelook

**NoammAddons features to port:**

1. **Smooth Sneak** — Animated eye height transition when sneaking
   - NoammAddons uses `MixinEntityPlayer` to hook `getEyeHeight()`
   - Lerps between standing and sneaking eye height with cubic easing
   - 1.21.10: Hook `Entity.getStandingEyeHeight()` or `PlayerEntity.getEyeHeight(EntityPose)`

2. **Custom FOV** — Set a fixed FOV value
   - Already partially in FloydAddons camera; ensure it can be fixed independent of effects

3. **Remove Water FOV** — Cancel the FOV reduction underwater
   - 1.21.10: Mixin `GameRenderer.getFov()` or intercept the FOV modifier event

4. **Disable Blindness** — Cancel fog density when blinded
   - Already implemented as a hider in FloydAddons (`HiderFogMixin` existed, now deleted)
   - Re-add if needed

5. **Disable Portal/Nausea Effects**
   - 1.21.10: `InGameOverlayRenderer` handles these; already partially in hiders system

6. **Disable Face Block Overlay** — No block texture when head inside a block
   - Similar to fire overlay hider already present

**Config additions to CameraConfig.java:**
```java
public boolean smoothSneakEnabled = false;
public boolean customFovEnabled = false;
public float customFovValue = 90.0f;
public boolean removeWaterFov = false;
```

---

### 7. Block Overlay (Custom Block Highlight)

**What it does:** Replace the default block outline with customizable outline/fill rendering, custom colors, line width, and phase-through-walls mode.

**Difficulty:** MODERATE
**Source ref:** `noammaddons/features/impl/misc/BlockOverlay.kt`

**NoammAddons approach:**
- Cancel `DrawBlockHighlightEvent`
- On world render: get `mc.objectMouseOver.blockPos`
- Draw using `RenderUtils.drawBlockBox()` with outline/fill/both modes
- Settings: Mode (Outline/Fill/Both), Phase toggle, Line Width, Fill Color, Outline Color

**1.21.10 adaptation:**
- Cancel: Mixin `WorldRenderer.drawBlockOutline()` → cancel at HEAD
- Render: Use `WorldRenderEvents.AFTER_TRANSLUCENT` or `WorldRenderEvents.LAST` callback
- Drawing: Use `VertexConsumerProvider`, `RenderLayer.getLines()`, `MatrixStack`
- `MinecraftClient.getInstance().crosshairTarget` gives the `BlockHitResult`

```java
// Cancel default outline
@Mixin(WorldRenderer.class)
public class BlockOverlayMixin {
    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void cancelOutline(MatrixStack matrices, VertexConsumer vertexConsumer,
        Entity entity, double cameraX, double cameraY, double cameraZ,
        BlockPos blockPos, BlockState blockState, int color, CallbackInfo ci) {
        if (RenderConfig.blockOverlayEnabled) ci.cancel();
    }
}
```

---

### 8. Player Scale & Spin

**What it does:** Visually scale the player model and optionally spin them.

**Difficulty:** EASY
**Source ref:** `noammaddons/features/impl/misc/PlayerModel.kt`
**FloydAddons existing:** Already has `playerScaleX/Y/Z` in `SkinConfig.java` and `PlayerSizeMixin.java`

**NoammAddons additions to port:**
- **Player spin**: Animated rotation on Y-axis
  - Configurable speed (1-25), direction (left/right)
  - Apply to self only or everyone
- **Scale everyone toggle**: Apply scale to all visible players

**1.21.10 approach:**
- Already using `PlayerSizeMixin` for scale transforms
- For spin: Add to the same mixin's `@Inject` at `PlayerEntityRenderer.render()` HEAD/RETURN
- Apply `matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle))` before render
- Pop the matrix after render

---

### 9. Motion Blur

**What it does:** Post-processing effect that blends previous frames for a smooth/cinematic feel.

**Difficulty:** HIGH (rendering pipeline changes)
**Source ref:** `noammaddons/features/impl/misc/MotionBlur.kt`

**NoammAddons approach:**
- Uses two `Framebuffer` objects for ping-pong blending
- Blends current frame with previous using alpha `(strength/10 - 0.1)`
- Uses `GL_SRC_ALPHA / GL_ONE_MINUS_SRC_ALPHA` blending
- Renders fullscreen textured quads

**1.21.10 adaptation:**
- Framebuffer API changed significantly in modern Minecraft
- Use `Framebuffer` from `com.mojang.blaze3d.framebuffer`
- The post-processing shader system could also be used (vanilla has motion blur-like shaders)
- Alternative: Use the `PostEffectProcessor` system which supports custom shader passes
- Register via `WorldRenderEvents.END` or similar callback

**Complexity warning:** This is one of the harder ports due to the rendering pipeline changes. The 1.21.10 Framebuffer API is different, and raw GL calls may conflict with the pipeline system. Consider using Fabric's shader API or the vanilla `PostEffectProcessor` instead.

---

### 10. Damage Splash Formatting

**What it does:** Reformats Hypixel damage splash text to be more readable with formatted numbers and color codes.

**Difficulty:** MODERATE (concept is portable, implementation is Hypixel-specific)
**Source ref:** `noammaddons/features/impl/misc/DamageSplash.kt`

**NoammAddons approach:**
- Intercepts `S0FPacketSpawnMob` (armor stand spawns for damage numbers)
- Regex: `[✧✯]?(\d{1,3}(?:,\d{3})*[⚔+✧❤♞☄✷ﬗ✯]*)`
- Reformats with comma separators and random color codes
- Uppercase option for damage numbers

**1.21.10 adaptation:**
- Armor stand spawning is now via `EntitySpawnS2CPacket` or `EntityTrackerUpdateS2CPacket`
- Custom name is set via entity metadata
- Can intercept metadata updates to modify display names
- The formatting logic (number formatting, color codes) is fully portable

**Note:** This is primarily useful on Hypixel servers. For general use, could adapt to format any armor stand name tag that contains numbers.

---

### 11. FPS/TPS/Clock Display

**What it does:** Simple HUD overlays showing FPS, server TPS, and clock time.

**Difficulty:** EASY
**Source ref:** `noammaddons/features/impl/hud/FpsDisplay.kt`, `TpsDisplay.kt`, `ClockDisplay.kt`

**1.21.10 implementation:**
- FPS: `MinecraftClient.getInstance().getCurrentFps()` — render via `HudRenderCallback`
- TPS: Track time between server tick packets (`WorldTimeUpdateS2CPacket`)
- Clock: `SimpleDateFormat("HH:mm:ss").format(Date())` — render on HUD

All of these use the same pattern:
1. Register `HudRenderCallback.EVENT`
2. Draw text at configurable position using `DrawContext.drawTextWithShadow()`
3. Make position draggable (reuse FloydAddons' existing move-screen pattern)

---

### 12. Dark Mode

**What it does:** Applies a dark tint overlay to reduce screen brightness.

**Difficulty:** EASY
**Source ref:** `noammaddons/features/impl/hud/DarkMode.kt`

**Implementation:**
- Register `HudRenderCallback.EVENT`
- Draw a semi-transparent black rectangle covering the entire screen
- Configurable opacity (0-100%)

```java
HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
    if (RenderConfig.darkModeEnabled) {
        int alpha = (int)(RenderConfig.darkModeOpacity * 255 / 100);
        drawContext.fill(0, 0, width, height, (alpha << 24)); // black with alpha
    }
});
```

---

### 13. Visual Words (Text Replacement)

**What it does:** Replaces specified words throughout all game text rendering.

**Difficulty:** EASY (FloydAddons already has `NickHiderTextRendererMixin` which does exactly this)
**Source ref:** `noammaddons/features/impl/general/VisualWords.kt`, `noammaddons/mixins/MixinFontRenderer.java`

**FloydAddons already has this pattern!** The `NickHiderTextRendererMixin` intercepts all text rendering. To add general word replacement:
- Add a `Map<String, String>` to config for word replacements
- In the existing text renderer mixin, apply replacements after nick hiding

---

### 14. Custom Slot Highlight

**What it does:** Changes the color of the inventory slot highlight (the white overlay when hovering).

**Difficulty:** EASY
**Source ref:** `noammaddons/features/impl/general/CustomSlotHighlight.kt`

**1.21.10 approach:**
- Mixin into `HandledScreen.drawSlotHighlight()` or override the color parameter
- The highlight color is a constant in vanilla; redirect it to a configurable value

---

## P2 — MEDIUM PRIORITY

### 15. Enchantment Colors

**What it does:** Colors enchantment names in tooltips based on enchantment tier/effectiveness.

**Difficulty:** MODERATE
**Source ref:** `noammaddons/features/impl/general/EnchantmentsColors.kt`

**Implementation:**
- Hook tooltip rendering (`ItemStack.getTooltip()` or `Screen.renderTooltip()`)
- Parse enchantment lines, color based on level vs. max level
- Data-driven: JSON map of enchantment → max level

---

### 16. Item Rarity Overlay

**What it does:** Draws a colored background behind inventory slots based on item rarity.

**Difficulty:** MODERATE
**Source ref:** `noammaddons/features/impl/general/ItemRarity.kt`

**Implementation:**
- Hook into `HandledScreen.drawSlot()` → draw colored rectangle before item rendering
- Parse rarity from item tooltip (last line) or from NBT data
- Map rarity to color (Common=white, Uncommon=green, Rare=blue, Epic=purple, Legendary=gold)
- Works on hotbar via HUD render callback

---

### 17. Inventory Search Bar

**What it does:** Adds a search/filter bar to inventory screens.

**Difficulty:** MODERATE-HIGH
**Source ref:** `noammaddons/features/impl/gui/InventorySearchbar.kt`

**Implementation:**
- Mixin into `HandledScreen` to add a `TextFieldWidget` at the top
- On text change, dim/hide non-matching item slots
- Match against item name, lore, enchantments

---

### 18. Scalable Tooltips

**What it does:** Scale and reposition item tooltips.

**Difficulty:** MODERATE
**Source ref:** `noammaddons/features/impl/gui/ScalableTooltips.kt`

**Implementation:**
- Mixin into `Screen.renderTooltip()` or `DrawContext.drawTooltip()`
- Apply `matrices.scale()` before rendering
- Add scroll-to-scale and drag-to-reposition
- Add rarity-colored border to tooltip

---

### 19. No Block Animation (Sword Blocking Bypass)

**What it does:** Prevents the block animation on swords with abilities (relevant for Hypixel items).

**Difficulty:** EASY
**Source ref:** `noammaddons/features/impl/misc/NoBlockAnimation.kt`

**1.21.10 note:** 1.9+ removed sword blocking entirely, so this is only relevant if playing on servers with custom items that add block-like animations. May not be needed.

---

### 20. Bow Hit Sound

**What it does:** Plays a custom sound when hitting with a bow/arrow.

**Difficulty:** EASY
**Source ref:** `noammaddons/features/impl/misc/BowHitSound.kt`

**Implementation:**
- Listen for the `random.successful_hit` sound event (or modern equivalent)
- If holding a bow, play a custom sound instead
- Configurable sound, volume, pitch

---

### 21. Arrow Fix (Shortbow Animation)

**What it does:** Disables bow pullback animation for shortbows (Hypixel-specific).

**Difficulty:** EASY (but Hypixel-specific)
**Source ref:** `noammaddons/features/impl/misc/ArrowFix.kt`

---

### 22. Full Block (Expanded Hitboxes)

**What it does:** Makes skulls, buttons, levers, mushrooms easier to click by expanding their interaction area.

**Difficulty:** MODERATE
**Source ref:** `noammaddons/features/impl/misc/FullBlock.kt`

**NoammAddons approach:** Uses mixins on `BlockButton`, `BlockLever`, `BlockMushroom`, `BlockSkull` to modify collision/selection boxes.

**1.21.10 adaptation:**
- Block shapes are defined by `VoxelShape` returned from `getOutlineShape()`
- Mixin into specific block classes to return `VoxelShapes.fullCube()` for the outline shape
- Or use `Block.FULL_CUBE_SHAPE`

---

### 23. Smooth Sneak Animation

**What it does:** Smoothly animates the camera height change when sneaking instead of instant jump.

**Difficulty:** MODERATE
**Source ref:** `noammaddons/features/impl/misc/Camera.kt` → `SmoothSneak` object

**NoammAddons approach:**
- Cubic easing functions for both sneak-down and unsneak-up
- `getEyeHeightHook()` overrides `EntityPlayer.getEyeHeight()`
- Tracks animation state with `lastX` (0=standing, 1=sneaking)
- Down curve: `OFFSET * (x-1)²  - OFFSET`
- Up curve: `-OFFSET * x²`

**1.21.10 implementation:**
- Mixin target: `Entity.getStandingEyeHeight()` or `PlayerEntity` camera height
- Note: 1.21.10 may already have some sneak animation — check if vanilla's is insufficient
- If needed, override the eye height calculation in a `PlayerEntity` mixin

---

## P3 — LOW PRIORITY (Complex / Niche)

### 24. Cosmetics (Dragon Wings, Halo)

**What it does:** Renders dragon wings and angel halo on players.

**Difficulty:** HIGH
**Source ref:** `noammaddons/features/impl/misc/Cosmetics.kt`

**FloydAddons already has:** Cape system and cone hat rendering. The pattern is similar — add a `FeatureRenderer` layer. Dragon wings would use the Ender Dragon wing model and texture. This is doable but complex 3D model work.

---

### 25. Auto Clicker

**What it does:** Automatically clicks at configurable CPS.

**Difficulty:** EASY
**Source ref:** `noammaddons/features/impl/general/AutoClicker.kt`

**Implementation:**
- Scheduled task that simulates mouse clicks
- Configurable CPS (clicks per second) with random jitter
- Keybind to toggle

**Note:** This is a controversial feature and may be flagged by anti-cheat systems.

---

### 26. Client Branding (Custom Window Title/Icon)

**What it does:** Changes the Minecraft window title and icon.

**Difficulty:** EASY
**Source ref:** `noammaddons/features/impl/misc/ClientBranding.kt`

**1.21.10:** `MinecraftClient.getInstance().getWindow().setTitle()` and GLFW icon setting.

---

### 27. Rat Protection

**What it does:** Blocks suspicious outgoing connections to protect against malicious mods.

**Difficulty:** MODERATE
**Source ref:** `noammaddons/features/impl/misc/RatProtection.kt`

**Implementation:** Custom `ProxySelector` that blocks connections to known-malicious domains.

---

### 28. Inventory Display (Enhanced)

**What it does:** Renders inventory on HUD with more options than FloydAddons currently has.

**Already exists in FloydAddons!** NoammAddons version adds: show hotbar toggle, background rendering. These are incremental improvements to the existing `InventoryHudRenderer`.

---

---

## OdinClient Features (from [github.com/odtheking/Odin](https://github.com/odtheking/Odin) `odinclient/`)

Odin has a separate `odinclient/` module with "cheater" features. These are the portable ones:

### OC1. Secret Hitboxes / Full Block Hitboxes (P0 — EASY)

**What it does:** Expands the hitboxes of small blocks (levers, buttons, skulls, chests, crops) to a full 1x1x1 block, making them much easier to click.

**Source ref:** `odin/SecretHitboxes.kt`, `odin/FarmingHitboxes.kt`
**Related NoammAddons:** `noammaddons/features/impl/misc/FullBlock.kt`

**Odin approach:**
- Block mixins for `BlockButton`, `BlockLever`, `BlockMushroom`, `BlockSkull`, `BlockChest`, `BlockCocoa`, `BlockCrops`, `BlockNetherWart`
- Each mixin returns full-block collision/selection box
- `FarmingHitboxes` uses a mixin accessor (`IBlockAccessor`) to set min/max bounds to 0→1

**1.21.10 implementation:**
```java
// Per-block mixin, e.g. for ButtonBlock:
@Mixin(ButtonBlock.class)
public class FullBlockButtonMixin {
    @Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
    private void fullOutline(BlockState state, BlockView world, BlockPos pos,
        ShapeContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        if (HidersConfig.fullBlockHitboxes) {
            cir.setReturnValue(VoxelShapes.fullCube());
        }
    }
}
```

**Blocks to target:** `ButtonBlock`, `LeverBlock`, `SkullBlock`, `ChestBlock`, `CocoaBlock`, `CropBlock`, `NetherWartBlock`, `MushroomPlantBlock`

**Config:** `HidersConfig.fullBlockHitboxes` (boolean toggle)

---

### OC2. Auto Close Chests (P1 — EASY)

**What it does:** Automatically closes dungeon chests instantly, or closes them on any key press.

**Source ref:** `odin/CloseChest.kt`

**Two modes:**
1. **Auto** — Intercepts the `S2DPacketOpenWindow` (server telling client to open chest) → immediately sends `C0DPacketCloseWindow` back → cancels the open event. The chest is never rendered on screen.
2. **Any Key** — Lets the chest open, but closes it on any key press or mouse click.

**1.21.10 implementation:**
- Auto mode: Mixin into `ClientPlayNetworkHandler.onOpenScreen()` → check if it's a generic chest → send close packet → cancel
- Any Key mode: Mixin into `HandledScreen.keyPressed()` and `HandledScreen.mouseClicked()` → close if it's a plain chest

```java
@Mixin(ClientPlayNetworkHandler.class)
public class AutoCloseChestMixin {
    @Inject(method = "onOpenScreen", at = @At("HEAD"), cancellable = true)
    private void autoClose(OpenScreenS2CPacket packet, CallbackInfo ci) {
        if (!RenderConfig.autoCloseChests) return;
        String title = packet.getName().getString();
        if (title.equals("Chest") || title.equals("Large Chest")) {
            // Send close packet
            MinecraftClient.getInstance().getNetworkHandler()
                .sendPacket(new CloseHandledScreenC2SPacket(packet.getSyncId()));
            ci.cancel();
        }
    }
}
```

---

### OC3. Auto Clicker (P1 — EASY)

**What it does:** Automatically clicks at configurable CPS with random jitter for natural-looking clicks.

**Source ref:** `odin/AutoClicker.kt`

**Key implementation details:**
- Separate left/right click CPS settings (3-15 range)
- Random jitter: `±30ms` offset per click (`(Math.random() - 0.5) * 60`)
- Keybind-activated (hold to click)
- Checks `mc.currentScreen == null` (no clicking in GUIs)
- Timer-based: `nextClickTime = now + (1000/cps) + jitter`

**1.21.10 implementation:**
- Register on `WorldRenderEvents.END` or use a tick callback
- Use `MinecraftClient.getInstance().options.attackKey` / `useKey` state
- Simulate clicks via `MinecraftClient.getInstance().doAttack()` / `doItemUse()`

**Config:**
```java
public boolean autoClickerEnabled = false;
public float autoClickerLeftCps = 10.0f;
public float autoClickerRightCps = 10.0f;
public boolean autoClickerLeftEnabled = true;
public boolean autoClickerRightEnabled = false;
```

---

### OC4. Ghost Blocks (P2 — MODERATE)

**What it does:** Multiple ways to create "ghost blocks" (client-side air blocks where server thinks solid blocks exist).

**Source ref:** `odin/GhostBlocks.kt`

**Modes:**
1. **Gkey** — Hold a keybind, looked-at block becomes air client-side. Configurable range (4.5-80 blocks) and speed.
2. **Ghost Tool** — Spawn a client-side pickaxe/axe with custom Efficiency enchantment level in a specific hotbar slot. Press keybind to create.
3. **Swap Stonk** — Swap to pickaxe, left-click, swap back. Automated tool swap for breaking.
4. **Stonk Delay** — When a block is broken client-side, delay the server's replacement for configurable ms.

**1.21.10 implementation for Gkey mode (simplest):**
```java
// On tick/render, if keybind held:
HitResult hit = mc.crosshairTarget;
if (hit instanceof BlockHitResult blockHit) {
    mc.world.setBlockState(blockHit.getBlockPos(), Blocks.AIR.getDefaultState());
}
```

---

### OC5. Trajectories (Bow/Pearl Prediction) (P2 — MODERATE-HIGH)

**What it does:** Renders the predicted flight path of arrows and ender pearls.

**Source ref:** `odin/Trajectories.kt`

**Algorithm:**
1. Start at player eye position with look direction velocity
2. Each tick: `pos += motion`, `motion.y -= gravity`, `motion *= drag`
3. Pearl physics: drag=0.99, gravity=0.03
4. Arrow physics: drag=0.99, gravity=0.05
5. Check `rayTraceBlocks(pos, pos+motion)` for block collision
6. Check `getEntitiesWithinAABB(pos+motion)` for entity collision
7. Render: line trail, box at impact, plane on hit face, outline on hit entity

**1.21.10 rendering:**
- Use `WorldRenderEvents.AFTER_TRANSLUCENT` callback
- Draw lines with `VertexConsumerProvider` and `RenderLayer.getLines()`
- Boxes with `WorldRenderer.drawBox()`

---

### OC6. Cancel Interact (P1 — EASY)

**What it does:** Prevents interacting with fences, hoppers, fence gates etc. so that item abilities (right-click) work instead. Whitelist allows chests/levers/buttons.

**Source ref:** `odin/CancelInteract.kt`

**1.21.10 approach:**
- Mixin into `ClientPlayerInteractionManager.interactBlock()`
- If target block is in blacklist (fences, hoppers, fence gates), cancel and let the item use happen instead
- Whitelist: `ChestBlock`, `LeverBlock`, `ButtonBlock`, `TrappedChestBlock`

---

### OC7. Chest ESP (P2 — MODERATE)

**What it does:** Renders chests visible through walls with CHAMS or outline rendering.

**Source ref:** `odin/ChestEsp.kt`

**FloydAddons already has Mob ESP** — the pattern is identical. Extend the existing ESP system to also track `BlockEntity` positions (chests). The CHAMS approach uses `GL_POLYGON_OFFSET` to render chest models in front of everything. The outline approach draws wireframe boxes.

---

### OC8. No Debuff (Comprehensive) (P1 — EASY)

**What it does:** Combined debuff remover. Removes: blindness fog, portal overlay, pumpkin overlay, shield particles, water FOV change, fire overlay, in-block overlay, nausea, hurt camera.

**Source ref:** `odin/NoDebuff.kt`

**FloydAddons already has many of these** in the Hiders system. Missing ones to add:
- **No Pumpkin Overlay** — Cancel helmet overlay rendering
- **No Water FOV** — Multiply FOV by 7/6 when in water
- **No Shield Particles** — Filter specific particle types
- **See Through Blocks** — Cancel block face overlay when head is inside block

## N/A — HYPIXEL SKYBLOCK-SPECIFIC (Not Applicable for Porting)

These features are deeply tied to Hypixel Skyblock mechanics and would only be useful if FloydAddons targets Skyblock:

| Feature | Category | Why N/A |
|---------|----------|---------|
| Dungeon Map system (20+ files) | Dungeons | Reads Skyblock dungeon map data |
| Terminal Solver (7 types) | Dungeons | F7 terminal mechanics |
| Puzzle Solvers (9 types) | Dungeons | Dungeon puzzle mechanics |
| M7 Dragons (timers, priority) | Dungeons | M7 boss fight mechanics |
| Dungeon Waypoints & Secrets | Dungeons | Room-specific waypoint data |
| LeapMenu, PartyFinder | Dungeons | Dungeon party system |
| AutoGFS, AutoPotion, AutoUlt | Dungeons | Dungeon auto-actions |
| Score Calculator | Dungeons | Dungeon scoring system |
| Blood Room, Mimic Detector | Dungeons | Dungeon room mechanics |
| F7 Titles, Floor 4 Boss | Dungeons | Floor-specific boss mechanics |
| DungeonBreaker, GhostPick | Dungeons | Dungeon block breaking |
| Architect Draft, DoorKeys | Dungeons | Dungeon item tracking |
| Star Mob ESP | ESP | Dungeon starred mob detection |
| Hidden Mobs ESP | ESP | Dungeon invisible mob reveal |
| Wither ESP | ESP | F7 boss wither tracking |
| Pest ESP | ESP | Garden pest detection |
| All Alert features | Alerts | Dungeon/Skyblock event alerts |
| All Slayer features | Slayers | Slayer boss mechanics |
| Blessing Display | HUD | Dungeon blessing tracking |
| Run Splits, Mask Timers | HUD | Dungeon run timing |
| Spring Boots, Warp Cooldown | HUD | Skyblock item cooldowns |
| Wither Shield Timer | HUD | Skyblock ability cooldown |
| Player HUD (EHP, mana) | HUD | Skyblock action bar parsing |
| Pet Display | HUD | Skyblock pet tracking |
| Chest Profit | Dungeons | Dungeon chest value calc |
| Items Price, Enchant Colors | General | Skyblock economy data |
| Slot Binding, Etherwarp | General | Skyblock item mechanics |
| Cake Numbers, Chat Emojis | General | Skyblock-specific items |
| Gloomlock, Gyro Helper | General | Skyblock item helpers |
| SB Kick protection | General | Skyblock kick tracking |
| WebSocket system | Infra | Party coordination server |
| Custom Party Finder Menu | GUI | Skyblock party finder |
| Custom Pet/Wardrobe Menu | GUI | Skyblock inventory screens |
| Salvage Overlay | GUI | Skyblock salvage prices |
| Profile Viewer | GUI | Skyblock player profiles |

---

## Implementation Roadmap (Suggested Order)

### Phase 1 — Quick Wins (1-2 sessions)
1. No Attack Cooldown Indicator (P0) — New mixin, simple toggle
2. Centered Crosshair (P0) — Reference code ready in `centered-crosshair-ref/`
3. Full Block Hitboxes (OC1, P0) — Per-block mixin returning `VoxelShapes.fullCube()`
4. Time Changer (P0) — Simple packet interception
5. Smooth Boss Bar (P0) — Single mixin redirect
6. Dark Mode (P1) — HUD overlay rectangle

### Phase 2 — Visual & Combat (2-3 sessions)
7. Auto Clicker (OC3, P1) — Timer-based with CPS + jitter
8. Auto Close Chests (OC2, P1) — Packet interception
9. Cancel Interact (OC6, P1) — Block interaction blacklist
10. Block Overlay (P1) — Custom block highlight
11. No Debuff additions (OC8, P1) — Pumpkin/water FOV/shield particles
12. Enhanced Animations (P1) — Improve existing animation system

### Phase 3 — Visual Enhancements (2-3 sessions)
13. Player Spin (P1) — Extend existing PlayerSizeMixin
14. Smooth Sneak (P1) — Eye height animation
15. FPS/TPS/Clock Display (P1) — Simple HUD text
16. Advanced Camera features (P1) — FOV, effects removal
17. Ghost Blocks (OC4, P2) — Client-side block removal

### Phase 4 — UI Improvements (2-3 sessions)
18. Custom Slot Highlight (P2) — Color picker for highlight
19. Scalable Tooltips (P2) — Tooltip zoom/reposition
20. Item Rarity Overlay (P2) — Colored slot backgrounds
21. Visual Words (P2) — Extend existing text replacement system
22. Inventory Search (P2) — Search bar in inventory screens

### Phase 5 — Advanced (ongoing)
23. Trajectories (OC5, P2) — Bow/pearl trajectory rendering
24. Chest ESP (OC7, P2) — Extend existing ESP system to block entities
25. Motion Blur (P1) — Framebuffer post-processing
26. Cosmetics extensions (P3) — Wings, halos

---

## Existing FloydAddons Features (for comparison)

Features FloydAddons ALREADY has that overlap with NoammAddons:
- **Nick Hider** — More sophisticated than NoammAddons (which uses VisualWords for similar effect)
- **Custom Skins** — NoammAddons doesn't have this
- **Cape System** — NoammAddons has dragon wings/halo instead
- **Cone Hat** — Unique to FloydAddons
- **X-Ray Vision** — NoammAddons doesn't have this (uses dungeon-specific features instead)
- **Mob ESP** — Both have ESP; NoammAddons is more specialized (star mobs, wither, etc.)
- **Freecam/Freelook** — NoammAddons doesn't have a dedicated freecam
- **Custom Animations** — Both have; NoammAddons has more settings
- **Classic Click** — Both have
- **Brand Spoofing** — Both have
- **Mod Hiding** — Both have
- **Inventory HUD** — Both have
- **Custom Scoreboard** — Both have
- **Hiders system** — Both have similar hiders (fire overlay, particles, etc.)
- **ClickGUI** — Both have

---

## Config Field Additions Summary

### HidersConfig.java additions:
```java
public boolean hideAttackCooldown = false;
public boolean centeredCrosshair = false;
public boolean fullBlockHitboxes = false;       // OC1: Full-block hitbox for levers/buttons/skulls
public boolean noPumpkinOverlay = false;         // OC8: Remove pumpkin helmet overlay
public boolean noWaterFov = false;               // OC8: Remove water FOV change
public boolean noShieldParticles = false;        // OC8: Remove shield/heart particles
```

### RenderConfig.java additions:
```java
// Time Changer
public boolean timeChangerEnabled = false;
public int timeChangerMode = 0; // 0-5

// Block Overlay
public boolean blockOverlayEnabled = false;
public int blockOverlayMode = 0; // 0=Outline, 1=Fill, 2=Both
public boolean blockOverlayPhase = false;
public float blockOverlayLineWidth = 2.0f;
public int blockOverlayFillColor = 0x3300FF00;
public int blockOverlayOutlineColor = 0xFF00FF00;

// Smooth Boss Bar
public boolean smoothBossBarEnabled = false;

// Dark Mode
public boolean darkModeEnabled = false;
public int darkModeOpacity = 50;

// FPS/TPS/Clock
public boolean fpsDisplayEnabled = false;
public boolean tpsDisplayEnabled = false;
public boolean clockDisplayEnabled = false;
```

### CameraConfig.java additions:
```java
public boolean smoothSneakEnabled = false;
public boolean customFovEnabled = false;
public float customFovValue = 90.0f;
public boolean removeWaterFov = false;
```

### RenderConfig.java additions (Odin features):
```java
// Auto Clicker (OC3)
public boolean autoClickerEnabled = false;
public float autoClickerLeftCps = 10.0f;
public float autoClickerRightCps = 10.0f;
public boolean autoClickerLeftEnabled = true;
public boolean autoClickerRightEnabled = false;

// Auto Close Chests (OC2)
public boolean autoCloseChests = false;
public int autoCloseChestsMode = 0; // 0=Auto, 1=Any Key

// Cancel Interact (OC6)
public boolean cancelInteractEnabled = false;

// Ghost Blocks (OC4)
public boolean ghostBlocksEnabled = false;
public float ghostBlockRange = 8.0f;
```

### New mixin classes needed:
```
mixin/HiderAttackCooldownMixin.java   — Attack cooldown indicator hiding
mixin/CenteredCrosshairMixin.java     — Crosshair centering (+ DrawContext extension)
mixin/TimeChangerMixin.java           — World time override
mixin/SmoothBossBarMixin.java         — Boss bar animation
mixin/BlockOverlayMixin.java          — Custom block highlight
mixin/SmoothSneakMixin.java           — Sneak eye height animation
mixin/FullBlockButtonMixin.java       — Full-block hitbox for buttons (OC1)
mixin/FullBlockLeverMixin.java        — Full-block hitbox for levers (OC1)
mixin/FullBlockSkullMixin.java        — Full-block hitbox for skulls (OC1)
mixin/FullBlockChestMixin.java        — Full-block hitbox for chests (OC1)
mixin/AutoCloseChestMixin.java        — Auto-close chests (OC2)
mixin/CancelInteractMixin.java        — Cancel block interaction (OC6)
```
