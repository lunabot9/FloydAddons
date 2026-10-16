# FloydAddons 2.4.10

This release fixes custom cosmetics (player model, custom skin, cape, cone hat and
player size) rendering the wrong look for your own player inside recordings, for
Minecraft `26.1`, `26.1.2`, and `26.2`.

Changes in this release:

- **Fixed your own cosmetics in recordings.** Replay playback does not run your
  body as the entity the client controls: the client is logged in as a neutral
  stand-in while the recorded body is a separate player entity. Every self check
  compared only entity ids, so your own body in a recording was treated as
  someone else's and rendered the shared (published) appearance instead of your
  local settings — reported as a recording showing a different player model than
  live gameplay. Self detection now falls back to profile identity (local UUID,
  account UUID, then player-list name), and the shared-appearance lookup never
  returns an entry for your own body.
- **Fixed shared appearances losing the selected model.** The cosmetics service
  allowlisted only three model ids, so publishing any other selection silently
  stored the default `Tung Tung Sahur` and other clients rendered the wrong
  model. The service now keeps any bounded, printable model label; receiving
  clients already canonicalize the label and fall back to their own default for
  anything they cannot render.
- Added `FloydSelfPlayerTest` coverage for the identity rules (recorded body by
  account UUID, offline-mode match by name, other players never self), plus
  service sanitizer coverage for preserved model labels and unusable ids. The
  build matrix is green across 26.1, 26.1.2, and 26.2.

Live sessions are unchanged: the entity-id check still short-circuits first, so
nothing changes for the controlled body in normal gameplay.

Downloads:

- `FloydAddons-2.4.10-26.1.jar` for Minecraft `26.1`
- `FloydAddons-2.4.10-26.1.2.jar` for Minecraft `26.1.2`
- `FloydAddons-2.4.10-26.2.jar` for Minecraft `26.2`
- `SHA256SUMS-v2.4.10.txt` for artifact verification

Modrinth builds require both Fabric API and Fabric Language Kotlin.
