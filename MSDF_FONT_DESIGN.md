# Global MSDF Text — Consolidated Design v2 (post-adversarial-review)

**Target:** branch `main`, MC **1.21.11** (mojmap/loom), FloydAddons 2.1.0.
**Mission:** ONE MSDF font path for ALL text — vanilla `mc.font` rendering (chat, tab,
scoreboard, nameplates, signs, tooltips, item counts, every GUI) AND the mod's own
panel/ClickGUI text (currently NanoVG). "Any TTF" user font selection stays supported.
NanoVG keeps shapes/images only (optional later removal); NanoVG **text** dies.

Evidence: `/tmp/msdf-research/*.md` (decompiled-source verification, Caxton 1.21.11
audit, NVG call-site catalog), `/tmp/msdf-review/*.md` (5-lens adversarial review +
synthesis), spike `/tmp/msdfgen-spike/SpikeMSDF.java`. Review verdict: GO; all blocker
edits folded in below. Supersedes MSDF_FONT_PROPOSAL.md (stale: targeted 26.1, wrong API
names, pre-bake-only).

## Decisions

**D1 — Single runtime-generation tier for ALL fonts (bundled + user TTF).**
`org.lwjgl:lwjgl-msdfgen:3.3.4` (+ natives for macos-arm64/x64, windows x64/arm64,
linux x64/arm64; ~425KB) against MC's LWJGL 3.3.3 core — spike-proven on macOS arm64,
zero flags. FreeType resolved from MC's own `lwjgl-freetype` via
`msdf_ft_set_load_callback(name -> ftLib.getFunctionAddress(name))` (set once,
process-global, before any `msdf_ft_init`, closure never freed). ~0.85ms/glyph @32px.
ASCII prebake + lazy per-glyph generation per D11's threading model; disk cache per D12.
`msdf-atlas-gen` (`~/tools/msdf-atlas-gen/build/bin/`) is a dev QA tool only.
Lazy generation dissolves the CJK/charset problem (no tofu, no giant pre-bake).

**Failure modes split three ways (normative):**
(a) **Natives** (environment-dependent): probe the msdfgen binding eagerly inside
`adjustDefaultFont` (prepare executor) wrapped `catch (Throwable)` — link failures are
**Errors** and escape `FontManager.safeLoad`'s `catch (Exception)`; on failure emit the
current vanilla TTF definition instead and log once. (b) **Shaders** (ship-time
artifact): `RenderPipelines.register(...)` our pipelines; compile failure hard-fails the
resource reload by design (build bug caught in P0, not a user condition). (c)
**Mid-session per-glyph generation failure**: that bake returns
`wrappedStitcher.getMissing()` and schedules a one-shot font reload with the vanilla
fallback forced (per-codepoint fallback was frozen at `computeGlyphInfo`; the replaced
TTF provider no longer exists in the active list).

**D2 — Metrics = FreeType *hinted* advances under vanilla's exact flags.**
Vanilla `TrueTypeGlyphProvider`: `FT_Load_Glyph(face, idx, 0x400008)`
(BITMAP_METRICS_ONLY|NO_BITMAP, hinting ON, TARGET_NORMAL) at pixel size
`round(size*oversample)`, advance = `FT_Vector.x/64/oversample`. Our provider computes
`GlyphInfo` the SAME way → `Font.width`, `StringSplitter`, wrapping, obfuscated-width
buckets bit-identical to the current TTF override. msdfgen supplies only visuals.

**Metric→quad transform (normative).** Vanilla baseline sits at `y+7` of the 9-unit
line: `left = x + bearingX`, `up = y + 7 − bearingTop`, `right = left + w/oversample`,
`down = up + rows/oversample`. msdfgen output is y-up, em-normalized, bottom-up bitmaps:
V-flip UVs; store per-glyph autoframe scale + translate per atlas entry; quad =
`left = x + S·(pb_l − pad/s_g)`, `up = y + 7 − S·(pb_t + pad/s_g)` (S = em scale at
quad-build; `pad = PX_RANGE/2 + 1` px inside the cell). **Quad extents == reported
`left/top/right/bottom` extents, pad included** — bounds slightly fatter than vanilla,
accepted (layer-bumping is conservative-up). D5's 2px gutters are packing separation
only; the in-bitmap pad is what protects LINEAR sampling.

**Parity preconditions:** `FT_Select_Charmap(FT_ENCODING_UNICODE)`; replicate
`FT_Set_Transform` delta = `shift·oversample` (BYO preserves bundled shift); honor the
`skip` string during cmap enumeration; **empty outline (w==0||rows==0) →
EmptyGlyph-equivalent**: advance-only glyph whose baked `createGlyph` returns **null**,
so `PreparedTextBuilder` skips `markSize` and emits `EmptyArea` — hover/click over
spaces in clickable chat depends on this; a textured space quad breaks it and un-nulls
space-only `bounds()`.

**D3 — Injection at the existing `FloydFontProviders.adjustDefaultFont` seam.**
No `FontType` enum extension: provider constructed in code via the
anonymous-`GlyphProviderDefinition`/`unpack()` pattern the BYO-TTF path already uses.
`MsdfGlyphProvider implements GlyphProvider`:
- `getGlyph(cp)` → memoized `UnbakedGlyph` whose `bake(Stitcher)` ignores the vanilla
  stitcher (legal: `EmptyGlyph` precedent; no consumer downcasts to `BakedSheetGlyph`)
  and returns `FloydMsdfGlyph implements BakedGlyph` bound to our atlas + pipelines.
- `createGlyph(x,y,color,shadowColor,style,boldOffset,shadowOffset)` →
  `FloydMsdfRenderable implements TextRenderable.Styled`, reimplementing
  `BakedSheetGlyph.renderChar` semantics EXACTLY:
  - shadow quad(s) first at depth 0, main at +0.03F z (both zeroed when isGui);
  - bold = second quad offset by `boldOffset` with `extraThickness = 0.1F`, world z
    offset `Z_FIGHTER = 0.001F` (else bold world text z-fights itself);
  - italic shear: `shearTop = 1.0F − 0.25F·up`, `shearBottom = 1.0F − 0.25F·down`,
    added to top/bottom vertex x respectively, **and folded into bounds** via
    `min/max(shearTop, shearBottom)` in `left()`/`right()`;
  - `left/top/right/bottom` include shadow/bold/shear/pad — `PreparedText.bounds()`
    returning null silently drops the whole string from GUI submission;
  - implement `activeRight() = x + advance(bold)` and `style()` (ActiveTextCollector
    hit-tests with active* values — omitting fattens chat click hitboxes).
- Uncovered codepoint → `null` → FontSet falls through per-codepoint to vanilla
  bitmap/unifont. (Inter↔unifont pipeline interleave mid-string is status quo — vanilla
  already splits meshes RED8 vs RGBA8.) `§k` works via `glyphsByWidth` buckets.
- Strikethrough/underline stay vanilla (`whiteGlyph` effects). `disableTextShadow`
  (FontMixin, upstream) unaffected.

**D4 — Pipelines/shaders: static instances, constant UnitRange, ONE mixin total.**
- **4 pipelines**: `floydaddons:pipeline/msdf_text{,_see_through,_polygon_offset,_gui}`.
  `_polygon_offset` = `msdf_text` + `withDepthBias(-1.0F, -10.0F)` (vanilla
  `TEXT_POLYGON_OFFSET` parity — live via `Font.drawInBatch8xOutline`, glow-ink signs).
  `TextRenderable.renderType(DisplayMode)` maps NORMAL/SEE_THROUGH/POLYGON_OFFSET
  exhaustively. Shaders `floydaddons:core/msdf_text*` (#moj_import fog/
  dynamictransforms/projection; vertex format `POSITION_COLOR_TEX_LIGHTMAP`).
  Fragment: median-of-RGB; `screenPxRange = max(0.5*dot(vec2(UNIT_RANGE),
  1/fwidth(uv)), 1.0)`; `UNIT_RANGE = PXRANGE/PAGE_SIZE` as GLSL constants (pxrange=4,
  page=1024). Discard: match vanilla (0.1 normal/GUI; document if see-through deviates).
  Straight alpha, TRANSLUCENT blend (MC FBs linear; no sRGB anywhere).
- STATIC pipeline instances only (GlDevice pipeline cache is an IdentityHashMap), and
  registered (eager compile-failure reporting per D1(b)).
- World `RenderType`s per page via `RenderSetup.builder(pipeline).withTexture("Sampler0",
  pageId, { LINEAR sampler })` + `.useLightmap()` (clone vanilla or sign/nameplate
  lighting changes). World sampler needs NO mixin.
- **The one required mixin**: `@WrapOperation` in `GlyphRenderState.textureSetup()` on
  `SamplerCache.getClampToEdge(FilterMode)` (pin the 1-arg overload; a 2-arg overload
  exists) → LINEAR iff `renderable instanceof FloydMsdfRenderable`. MixinExtras is
  already in the toolchain (repo ships working `@WrapOperation`).

**D5 — Atlas: mod-owned 1024×1024 RGBA8 pages.**
Shelf packing, 2px gutters. **Encode (normative):** msdfgen emits **unclamped** float
RGB (spike: −2.9..4.1); generation uses the spike's symmetric
`distance_mapping(−range/2, +range/2)` so 0.5 = glyph edge; encode R,G,B **verbatim**
per channel as `round(clamp(v,0,1)·255)`, A=255. Unwritten texels (gutters + page
background) fill RGB=0x000000 A=255 ("far outside" under the median) — a 0.5-gray fill
bleeds half-coverage halo boxes under LINEAR at every quad border. **Page cap: 8 pages
(32MB)**; at cap, bake → missing-glyph + WARN once. Page ids carry a generation suffix
(`floydaddons:msdf/g<gen>_<n>`). Lifecycle per D11.

**D6 — Float-width API for mod UI.** `MsdfFontMetrics` sources every number from the
live `FontSet` resolution: `width(text, sizePx)` = Σ style-aware
`BakedGlyph.info().getAdvance(bold)` as un-ceiled floats (the same values `Font.width`
ceils), × `sizePx/9` matching the renderer's `matrix.scale(size/9)` mapping. **Never
sum from a raw FT face** — re-hinting at sizePx is non-linear vs the provider's
`round(size·oversample)` hinting — and per-codepoint fallback is inherited (❈ ✪ measure
with their unifont advances, exactly as rendered): divergence impossible by
construction. Adds `wrappedBounds(text, maxWidth, sizePx)` backed by `Font.split` +
9-unit line height (tooltip boxes sized by the same splitter that wraps the drawn
text). Replaces `NVGRenderer.textWidth`/`wrappedTextBounds` per-surface, same commit as
that surface's renderer flip — never globally ahead. Lockstep width sites: scoreboard
`buildScoreboardLayout` textWidth, `drawScoreboardTextMc` segX, HudSizeRegistry editor
box, TextInputHandler caret. Caret granularity 0.25u·(size/9): acceptable.

**D7 — NVG text dies via deferral + mc.font replay** (catalog: all ClickGUI transforms
translate/scale-only; scissors axis-aligned; one `rotate` wraps an image; `globalAlpha`
unused; `textShadow` dead):
1. Delete dead `drawScoreboardDeferred` path.
2. Land float-width API (no caller flips).
3. HUD panels → mc.font: generalize the scoreboard's mc-font TEXT mode into a shared
   helper (signature: segments|plain string, per-call scale, alignment, DisplayMode,
   light — day tracker plain strings; inventory per-call scale + right-align).
   **DELETE the `scoreboardHudMinecraftFont` setting** and force the mc-font path
   (re-defaulting doesn't change saved configs; a user-flippable NVG mode would
   re-create the blaze3d-after-NVG black-render corruption once step 4 lands). Eager-init
   the NVG context independent of text (devicePixelRatio()/activeFont() move off
   NVGRenderer). NVG leaves the world-end pass.
4. Collapse `PostHudOverlay` two-phase BACKGROUND/TEXT split + inter-panel
   `resetBetweenPanels` (keep one end-of-pass reset while NVG serves the ClickGUI).
   Verify nothing else NVG runs in the world-end pass. Delete stale "ESP overhead
   NanoVG-font" comments.
5. ClickGUI: queue `text/drawWrappedString` (capture `nvgCurrentTransform` 6-floats +
   screen-space scissor + layer). **Primary venue: in-PIP `Font.drawInBatch` between
   NVG sub-frames** — layer-0 text bakes into the PIP texture after the base-shapes
   sub-frame, below the layer-1 sub-frame. (GuiGraphics replay CANNOT serve layer-0:
   one PIP → text composites above the single blit; two PIPs → `submitText`'s node
   search lands text above PIP1's node while the blit goes *into* it. GuiGraphics may
   serve at most layer-1/top text, and any drained text must be submitted AFTER
   `submitBlitToCurrentLayer`.) In-PIP plumbing is NOT the scoreboard helper
   (`applyScreenProjection`/`bindMainFbo` no-op during GUI rendering): re-run the
   slot-FBO rebind before each subsequent `nvgEndFrame`; pose = slot-ortho × dpr ×
   `renderScale` (assert =1 for ClickGUI's identity pose). Fractional positions bake
   into the pose via direct `GuiTextRenderState` construction where needed (drawString
   x/y are ints). Budget the vertical-anchor delta (NVG ALIGN_TOP em-box vs mc.font
   ascent). Flip `textWidth`/`wrappedTextBounds` to float API same commit. Widget files
   untouched. `FLOYD_NVG_TEXT=1` flips NVG text rendering AND measurement as one unit
   (single env read at NVGRenderer init, WARN once); ClickGUI-only; deleted in step 7.
6. Dragged panel + tooltip = layer 1: second NVG sub-frame for shapes + layer-1 text
   via in-PIP drawInBatch after that sub-frame (also keeps text under toasts/F3).
   CORRECTION (live-found, 2026-06-10): two-layer replay is insufficient when BASE
   panels overlap at rest (lower panel's text bleeds over the upper panel's shapes) —
   generalize to per-panel layers: panel i = layer i (one NVG sub-frame + replay per
   panel), dragged+tooltip = topmost. Also normative: the post-NVG "GlStateManager
   resync" must FORCE-DESYNC raw↔cache (toggle each cached state, zero raw+cached
   texture bindings on units 0-3, flip GlCommandEncoder.lastProgram via a degenerate
   draw) — NanoVG desyncs BOTH GlStateManager's cache and the encoder's lastProgram,
   and cache-pathed "resyncs" no-op. ALL raw NVG texture creation (fontstash atlas,
   nvgCreateImageRGBA) must be wrapped to pin unit 0 and re-zero raw+cached bindings,
   else glyphs baked that frame upload into texture 0 and die permanently.
7. Delete NVG text methods + fontMap (~120 lines) + the env flag.
8. Optional follow-up (NOT this mission): port shapes/images off NanoVG ("B-lite").

**D8 — World text batching:** our own draws already `endBatch()` immediately. Vanilla
nameplates/signs with custom RenderTypes flush per layer-switch in `Immediate` —
correct but per-nameplate draws; accept initially, fixed-buffer registration only if
profiling demands.

**D9 — any-TTF:** `FloydFont.customFontPath()` bytes flow into the same
`MsdfGlyphProvider` (new FT face + msdfgen + own cache dir). Failure → vanilla-TTF
fallback chain (BYO → bundled → vanilla), crash-safe.

**D10 — Settings mapping:** `fontDisplaySize`→`runtimeFontSize` (effective max 16;
cap-20 unreachable) and `runtimeFontOversample` (always 4 in practice) parameterize FT
METRICS size only (D2) — restated: the oversample cap survives because changing it
changes every advance (metric parity), not atlas quality. OPEN ITEM (decide in P3/P4):
whether mod-UI surfaces normalize/clamp the size coupling (panel text inherits
`fontDisplaySize` quantization up to ×2.2 at S=16) and what `globalCustomFont=OFF`
means for ClickGUI text (vanilla bitmap font vs forced bundled font).

**D11 — Threading & native lifecycle (normative).**
(a) Provider construction runs on FontManager's background prepare executor and is
CPU-only: parse bytes, `FT_New_Memory_Face` (buffer outlives face), pre-enumerate the
cmap via `FT_Get_First_Char`/`FT_Get_Next_Char`. ZERO GPU calls in the ctor.
(b) `getGlyph(cp)` is metrics-only, never runs msdfgen. It is swept over the full union
cmap twice per reload (background `finalizeProviderLoading` + game-thread
`selectProviders`) — must be a cheap map-miss for uncovered codepoints, memoize one
`UnbakedGlyph` per codepoint, vanilla locking discipline (volatile entry +
`synchronized(face)` double-checked). `getSupportedGlyphs()` = cmap keyset.
(c) All GPU work — `createTexture`, `writeToTexture`, TextureManager registration —
only inside `bake()` on the render thread. Pages created lazily at first bake, never in
the ctor (serializes page lifecycle against `FontManager.apply`'s close-old ordering).
World RenderType/page memos live on the provider instance, dropped in `close()`.
(d) Async ASCII prebake produces CPU bitmaps + disk-cache entries ONLY; uploads happen
at bake. Prebake thread and render-thread generation share one provider generation lock
over the msdfgen handle + FT face (FT faces are not thread-safe) with per-glyph memos.
(e) `close()`: set closed flag under the generation lock, cancel-and-join the prebake,
tear down msdfgen font → msdfgen FT handle → `FT_Done_Face` → `memFree(fontBuffer)`.
Generation/upload only inside bake, never from `buildVertices`.

**D12 — Disk cache (normative):** entry = per-glyph bitmap + metrics blob keyed by
`sha256(fontBytes) + CACHE_VERSION` (msdfgen/binding version, edge-coloring angle,
pxrange, scaling mode, y-flip) + glyph px size. Writes: temp file + `ATOMIC_MOVE`.
Reads validate (length/header) → treat corrupt as miss. Bounded eviction (LRU by dir
mtime, ~64MB). Two concurrent clients share the dir safely (atomic moves, read-only
after publish).

## Phases (each = shippable commit + live-client proof via floyd-client-testing skill)

**P0 spike — must prove ALL of:**
- [ ] Natives resolve under Knot/Prism from the shipped jar (3.3.4-on-3.3.3), in the
      live client via 38769 — log-line proof + debug endpoint reporting provider class,
      natives status, page count (POSITIVE signal; screenshots alone can show fallback).
- [ ] Probe failure path: break natives → reload completes, vanilla TTF active, 1 log.
- [ ] Sampler mixin: LINEAR iff FloydMsdfRenderable; vanilla glyphs untouched.
- [ ] Foreign-pipeline batching in GuiRenderer incl. mixed MSDF/unifont string.
- [ ] Encode: crisp edges, no halo boxes at quad borders, guiScale 2/3/4 + guiScale 1
      retina (0.5× minification check).
- [ ] Baseline parity: MSDF + unifont share the y+7 baseline in one string;
      underline/strikethrough placement correct.
- [ ] Italic shear (1.0−0.25·up/down incl. bounds) + bold offset vs vanilla TTF.
- [ ] bounds()/markSize self-consistent with padded quads; space-only string renders
      nothing; hover-over-space in clickable chat works (EmptyGlyph path).
- [ ] DisplayMode switch exhaustive incl. POLYGON_OFFSET (no crash).
- [ ] Threading: ctor GL-free; pages at first render-thread bake; F3+T reload-spam ×10
      + chat burst → no GL assert, no UAF, no crash.
- [ ] Registered pipelines compile at reload; one intentional-breakage run confirms
      hard-fail.
- [ ] Blend over PIP-less path.

**P1 full provider:** lazy gen + async prebake + disk cache (D12); reload lifecycle;
world RenderTypes (nameplates/signs/ESP); bold/italic/shadow/obfuscated/effects parity;
custom-TTF; degradation paths. Tests: `Font.width` A/B endpoint (old TTF vs MSDF) over
full cmap = 0 delta; cold-cache glyph-storm (200-char CJK chat + §k scoreboard) — no
multi-frame stall, bounded pages (else add reserve-rect + N-glyphs-per-frame async
fill); truncated-cache-entry regenerates; BYO CJK font page cap respected; local
resource-pack `minecraft:default` override coexists (server-pack vector, SP-testable).
**P2 mod-UI prep:** float-width API + dead-code deletion (D7 1-2).
**P3 HUD panels:** D7 3-4. Per-panel screenshots; layout-shift gate; CustomNameReplacer
idempotence; `globalCustomFont=OFF` + size-slider extremes (S=8/16) matrix rows.
**P4 ClickGUI:** D7 5-7. Gates: measured-width A/B (NVG float vs new float) per widget
string ≤2px or deliberate re-layout; tooltip wrap inside box; fractional-advance chroma
title crisp; caret at fractional offsets; scissored viewport 1px-clip; per-widget
vertical diff vs NVG baseline; toasts/F3 above ClickGUI text mid-drag.
**P5 hardening:** perf A/B; Hypixel surfaces (scoreboard ❈✪, SkyBlock menus — split
matrix into SP-testable vs Hypixel-only; never send `§` via bridge `/chat`); adversarial
review workflow over the full diff; delete MSDF_FONT_PROPOSAL.md + dead font assets
(duplicate font.ttf, unused inter.json/fonts/) after confirming no references; decide
R10 (guiScale-1-at-dpr-1 minification) as accepted-unknown or pre-mip pages.

## Verification matrix (per surface × {guiScale 2,3,4} × retina; screenshot + crop)
chat (incl. clickable links + hover-over-space), tab list, scoreboard sidebar (vanilla),
tooltips, item stack counts, ClickGUI (title chroma, panel titles, module rows, all 11
setting widgets, search caret, tooltip wrap, drag, scroll), HUD panels (custom
scoreboard incl. chroma + fallback segments, inventory counts, day tracker), nameplates
(SEE_THROUGH + normal), signs (incl. glow-ink outline), F3 debug, `§klmno` codes,
mixed MSDF/unifont string, custom user TTF swap live, `globalCustomFont` OFF.
