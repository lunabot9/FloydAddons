# Custom skies

All eight sky environments are procedurally generated. No sky photographs or panorama artwork are bundled or used. Cloud Nine caches its generated lattice noise once in an 8 MiB GPU volume; it is mathematical noise, not a sky image.

- Golden Aurora retains its existing procedural curtains and matching upper/lower starfield background.
- Midnight Aurora shares the current main menu palette: charcoal/slate skies, blue and violet curtains, and icy silver highlights with the Midnight Silver palette enabled. It follows menu playback settings and renders at native resolution.
- Cloud Nine generates blue daytime sky and volumetric cumulus over a flat glass ocean. Its procedural noise lattice is generated once and sampled through an isolated 3D texture binding, restoring the previous OpenGL texture/sampler state after each pass. All modes retain 112 cloud samples and cloud detail. Balanced renders at 85% width/height, Performance at 65%, and High at native resolution. Only strongly unresolved noise octaves are filtered, preventing horizontal aliases without flattening the clouds. Empty samples skip lighting work and opaque rays exit early. Cheaper modes skip the unused full-resolution star pass. Shared ray filtering, subtle water distortion and a cool surface tint keep the horizon stable. Balanced and Performance reuse only their prior sky pass in two half-float buffers, reprojected by world direction with Catmull-Rom reconstruction to avoid accumulated rounding and blur, while refreshing 16-pixel tiles across 8 or 16 frames respectively. The horizon and newly exposed pixels always redraw. Resizing, a field-of-view change, a quality/speed change, or a gap over 250 ms invalidates history. High redraws the complete original model every frame for comparison. Terrain and GUI are never included in history.
- Nebula integrates animated three-dimensional gas and absorbing dust through twelve depth samples, with pink/violet emission and procedural stars.
- Emerald Aurora generates flowing curtains with green/cyan light and violet fringes.
- Crimson Eclipse generates its round lunar silhouette, luminous rim, turbulent radial corona, and prominences mathematically.
- Sapphire Galaxy integrates a narrower blue/silver gas band, warm stellar core light, dust lanes, and denser procedural stars.
- Rose Clouds uses sixteen depth samples with moving billows, erosion, light extinction, and warm rim scattering.

Each sky samples continuous world direction. Gas/noise fields evolve over time; camera movement does not reposition them. Animation Speed controls the generated motion. Golden Aurora continues to follow the menu playback setting as before.

The earlier generated images serve only as visual references for color, lighting, and composition. They are archived outside the packaged mod in `.codex-tmp/reference-only-sky-artwork/`. They are not a runtime rendering dependency.

Regression checks reject artwork samplers while allowing only the generated lattice-noise sampler, inspect all three packaged jars for removed panorama textures/classes, compare terrain with sky enabled/disabled, and capture fixed-camera time sequences in a real hidden client.

Render Quality defaults to Balanced. The volumetric Nebula, Sapphire Galaxy, and Rose Clouds passes render soft gas at half width/height, then linearly reconstruct it and add procedural stars at native resolution. The gas pass retains star visibility in alpha, so stars remain occluded by clouds without rerunning the raymarch. Performance uses one-third dimensions; High uses the original native-resolution pass. Golden Aurora, Emerald Aurora, and Crimson Eclipse remain native in every mode because their measured cost is already small.

Local Control offers an explicit `profileSky` action (optional `frames`, 1–240) with results under `render.customSky.gpuProfile`. It uses bounded asynchronous GPU timer queries, never waits for query completion, and issues no queries during normal gameplay. Timings cover the entire sky operation, including reconstruction and stars, rather than just the low-resolution pass.
