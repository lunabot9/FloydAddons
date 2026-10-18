# Custom skies

All six sky environments are generated entirely in GLSL. No sky photographs, panorama textures, or image samplers are bundled or used by the world-sky renderer.

- Golden Aurora retains its existing procedural curtains and matching upper/lower starfield background.
- Nebula integrates animated three-dimensional gas and absorbing dust through twelve depth samples, with pink/violet emission and procedural stars.
- Emerald Aurora generates flowing curtains with green/cyan light and violet fringes.
- Crimson Eclipse generates its round lunar silhouette, luminous rim, turbulent radial corona, and prominences mathematically.
- Sapphire Galaxy integrates a narrower blue/silver gas band, warm stellar core light, dust lanes, and denser procedural stars.
- Rose Clouds uses sixteen depth samples with moving billows, erosion, light extinction, and warm rim scattering.

Each sky samples continuous world direction. Gas/noise fields evolve over time; camera movement does not reposition them. Animation Speed controls the generated motion. Golden Aurora continues to follow the menu playback setting as before.

The earlier generated images serve only as visual references for color, lighting, and composition. They are archived outside the packaged mod in `.codex-tmp/reference-only-sky-artwork/`. They are not a runtime rendering dependency.

Regression checks enforce the shader's no-sampler contract, inspect all three packaged jars for removed panorama textures/classes, compare terrain with sky enabled/disabled, and capture fixed-camera time sequences in a real hidden client.

Render Quality defaults to Balanced. The volumetric Nebula, Sapphire Galaxy, and Rose Clouds passes render soft gas at half width/height, then linearly reconstruct it and add procedural stars at native resolution. The gas pass retains star visibility in alpha, so stars remain occluded by clouds without rerunning the raymarch. Performance uses one-third dimensions; High uses the original native-resolution pass. Golden Aurora, Emerald Aurora, and Crimson Eclipse remain native in every mode because their measured cost is already small.

Local Control offers an explicit `profileSky` action (optional `frames`, 1–240) with results under `render.customSky.gpuProfile`. It uses bounded asynchronous GPU timer queries, never waits for query completion, and issues no queries during normal gameplay. Timings cover the entire sky operation, including reconstruction and stars, rather than just the low-resolution pass.
