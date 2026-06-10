| Feature | Arena | Δp50 ms (spread) | Δp99 ms (spread) | Δalloc MB/s | Δfps | finding | top sections (on) |
|---|---|---|---|---|---|---|---|
| Block Search | perfarena-ores | -0.270 (0.073) | -0.840 (0.048) | +0.42 (0.04) | +4.6 | YES | RenderBatchManager.Last p50=3670us/2632B; PostHud.total p50=57us/1B; FloydBlockSearch.Extract p50=1us/104B |
| Inventory HUD | perfarena-hud | -0.072 (0.049) | -0.135 (0.299) | +21.20 (0.18) | +0.9 | YES | PostHud.total p50=918us/196864B; PostHud.InventoryHud p50=786us/196864B; RenderBatchManager.Last p50=7us/232B |
| Day Tracker | perfarena-hud | -0.063 (0.396) | -1.573 (9.906) | +1.51 (3.63) | +2.3 | no | PostHud.total p50=229us/7952B; PostHud.DayTracker p50=115us/7952B; RenderBatchManager.Last p50=5us/232B |
| X-Ray | perfarena-ores | +0.059 (0.141) | +1.062 (9.920) | -0.04 (0.15) | -1.1 | no | PostHud.total p50=82us/0B; RenderBatchManager.Last p50=6us/360B; FloydMobEsp.Extract p50=1us/0B |
| Player ESP | perfarena-ents | -0.037 (0.079) | -0.009 (0.047) | +0.03 (3.96) | +0.3 | no | PostHud.total p50=197us/3832B; PostHud.InventoryHud p50=66us/3832B; RenderBatchManager.Last p50=8us/120B |
| Mob ESP | perfarena-ents | -0.032 (0.158) | -0.015 (0.051) | +6.82 (3.06) | +0.4 | YES | PostHud.total p50=131us/3832B; PostHud.InventoryHud p50=49us/3832B; RenderBatchManager.Last p50=29us/7752B |
| Custom Scoreboard | perfarena-hud | +0.020 (0.125) | -0.084 (0.330) | +16.86 (0.63) | -0.1 | YES | PostHud.total p50=655us/232896B; PostHud.Scoreboard p50=459us/232895B; HudLayer.elements p50=82us/149556B |
| ClickGUI | perfarena-hud | -0.015 (0.165) | -0.374 (0.396) | +20.02 (0.47) | +1.0 | YES | ClickGUI.nvgPip p50=786us/168284B; ClickGUI.textReplay p50=131us/125920B; PostHud.total p50=82us/0B |
| HUD | perfarena-hud | -0.001 (0.163) | +0.103 (0.193) | -0.05 (0.30) | -0.2 | no | PostHud.total p50=98us/0B; RenderBatchManager.Last p50=7us/232B; FloydMobEsp.Extract p50=2us/0B |
