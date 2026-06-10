| Feature | Arena | Δp50 ms (spread) | Δp99 ms (spread) | Δalloc MB/s | Δfps | finding | top sections (on) |
|---|---|---|---|---|---|---|---|
| Block Search | perfarena-ores | -0.186 (0.047) | -3.953 (9.384) | +0.54 (0.10) | +4.1 | YES | RenderBatchManager.Last p50=3670us/2712B; PostHud.total p50=57us/0B; FloydMobEsp.Extract p50=1us/35B |
| Player ESP | perfarena-ents | -0.161 (0.538) | -0.277 (0.823) | +1.80 (7.28) | +1.9 | no | PostHud.total p50=98us/0B; RenderBatchManager.Last p50=8us/120B; FloydMobEsp.Extract p50=1us/0B |
| ClickGUI | perfarena-hud | -0.152 (0.177) | -1.377 (4.557) | +18.50 (4.73) | +2.9 | YES | ClickGUI.nvgPip p50=786us/149371B; ClickGUI.textReplay p50=131us/120320B; PostHud.total p50=82us/0B |
| Inventory HUD | perfarena-hud | +0.109 (0.302) | +0.301 (7.370) | -0.86 (13.65) | -4.5 | no | PostHud.total p50=786us/38984B; PostHud.InventoryHud p50=524us/38984B; RenderBatchManager.Last p50=12us/328B |
| HUD | perfarena-hud | +0.080 (0.149) | -2.244 (4.357) | -0.15 (0.36) | -0.4 | no | PostHud.total p50=82us/0B; RenderBatchManager.Last p50=5us/176B; FloydMobEsp.Extract p50=2us/0B |
| Mob ESP | perfarena-ents | +0.025 (0.108) | -0.007 (0.024) | +2.37 (1.42) | -0.1 | YES | PostHud.total p50=82us/0B; FloydMobEsp.Extract p50=7us/23537B; RenderBatchManager.Last p50=4us/80B |
| Day Tracker | perfarena-hud | -0.016 (0.042) | -1.949 (4.967) | +1.42 (4.96) | +0.6 | no | PostHud.total p50=459us/7571B; PostHud.DayTracker p50=229us/7570B; RenderBatchManager.Last p50=12us/176B |
| X-Ray | perfarena-ores | +0.006 (0.100) | -0.016 (0.037) | -0.01 (0.08) | -0.1 | no | PostHud.total p50=98us/0B; RenderBatchManager.Last p50=4us/232B; FloydMobEsp.Extract p50=1us/35B |
| Custom Scoreboard | perfarena-hud | -0.000 (0.119) | +1.867 (9.540) | -13.95 (17.17) | -1.6 | no | PostHud.total p50=229us/41288B; PostHud.Scoreboard p50=131us/41287B; RenderBatchManager.Last p50=6us/232B |
