# FloydLocalControl bridge reference

The bridge binds loopback only. `GET /` and `GET /health` are unauthenticated. For all
other endpoints, read the current token from `local:tokenPath` and send either
`Authorization: Bearer <token>` or `X-FloydAddons-Token: <token>`.

## State and input

- `GET /state` — connection, screen, scaffold, window, server, player, world, hotbar,
  entities, modules, and render diagnostics.
- `POST /chat {message}` — run chat or commands. Do not send section signs on servers.
- `POST /look {yaw,pitch}` or `{deltaYaw,deltaPitch}`.
- `POST /hotbar {slot}`.
- `POST /key {key,pressed|durationMs}` — movement, jump, sneak, sprint, attack, use,
  and tab.
- `POST /mouse {event,x,y,...}` — raw screenshot pixels by default; pass
  `coordinateSpace:"gui"` for GUI-scaled coordinates.
- `POST /type {text,clear?,submit?}` and `POST /replace-text {...}`.

## Screens and actions

- `POST /screen {screen}` — `clickgui`, `legacy`, `hud`, `pause`, `options`, or
  `close`.
- `POST /screenshot {fileName}` — require a `.png` filename with no path separators;
  trust the absolute returned path.
- `POST /action {action,...}` — attack, use, swing, jump, sneak, sprint, connect,
  disconnect, openWorld, createFreshWorld, setSetting, setModuleEnabled, camera,
  fullscreen, closeScreen, reloadConfig, or reloadResources.
- `GET /entities`, `/iconcheck`, `/fontdebug`, and `/perf` provide specialized state.

## GUI driving

Open the screen, capture a screenshot, locate stable text/geometry, send a mouse event,
and capture another screenshot. Chroma accents are poor frame-diff anchors. Left-click
toggles a module; right-click expands a module row, while right-clicking a panel header
collapses the panel. Expansion is runtime-only.

## Known traps

- A stale client can report valid state. Always compare both scaffold fields to the
  current repo version/target.
- `setModuleEnabled` and `setSetting` use lowercase module keys; for example, use
  `x-ray` rather than `X-Ray`.
- `/state` module entries live under `modules.categories[].modules[]`.
- Brigadier word arguments reject colons in this command surface. Prefer bare IDs such
  as `diamond_ore` and inspect a screenshot for chat errors.
- Disable Time Changer before daylight/clock visual tests.
- Use bridge `disconnect`; lower-level disconnect calls can hang on Saving World.
- Defer NVG/OpenGL work to render time. Static/config-time GL work can crash before a
  context exists.
- For render batching, assert `render.batch.lastFlushed`; queued counts may read zero
  between frames.
