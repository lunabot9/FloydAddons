# Auto Experiments

QoL module, disabled by default. Open the Experimentation Table on Hypixel and enable
Auto Experiments. While enabled, the solver owns recognized experiment menus and
suppresses manual mouse clicks/releases instead of pausing. Turn the module off to
interact with those menus manually. Initial menu clicks and delayed refocus clicks
cannot latch a pause. It continues running when Minecraft loses focus. A
reinitialized screen around the same server container preserves the learned sequence.

Auto Open (on by default) detects a nearby Experimentation Table hologram. Yellow or
explicitly available text permits opening; red text, a countdown, or no-session text
blocks it. It waits for you to look at an in-reach furniture entity or identified table
block, then uses the normal interaction API. It never changes camera yaw or pitch. Block
tables require an empty main hand. It makes one attempt per table/stage, with a minimum
three-second separation, and can reopen after a completed add-on or Superpairs. A run
already in progress may reopen a cooling-down table after add-ons to reach the next
experiment, or after Superpairs to reach its authorized renewal.

Chronomatron and Ultrasequencer target **+3 Superpairs clicks**, not a fixed number of
rounds. The solver reads earned reward text from the active round/status controls.
An explicitly advertised chain/series milestone can provide the target when current
reward text is absent. Current earned reward takes precedence. It waits for the server
to start the next watch phase before closing. There is no enchanting-level guess or
hard-coded nine-round cutoff. If neither reward nor milestone metadata is available,
it continues learning/playing; it does not assume that nine rounds earned three clicks.

Superpairs remembers complete reward identities, including book enchantment lore.
It shuffles exploration once per board and claims a known priority pair before scouting
more unseen locations, finishing an already revealed priority reward's mate first.
Each click still waits for the server's reveal and the configured click delay.
The priority order is ultra-rare books, named rare rewards, then other books. With Prioritize
Books enabled, known named rare rewards are eligible: A Beginner's Guide
to Pesthunting, Severed Pincer, Ensnared Snail, Golden Bounty, Severed Hand, Gold Bottle
Cap, Chain of the End Times, Fateful Stinger, End Stone Idol, Troubled Bubble, Vibrant
Coral, Octopus Tendril, and Nadeshiko Dye. Recognition uses exact normalized item names,
not RNG-meter lore or prices. Once priority rewards have been found, it does not
intentionally collect ordinary non-book pairs; if scouting finds no priority rewards, ordinary matches
are permitted. Some boards have more unknown slots than clicks, so full coverage is
not guaranteed. Incidental matches can occur while revealing previously unknown cards.

Extra-click and Instant Find powerups apply immediately. Instant Find follows the same
order: a known ultra-rare book, a known named rare reward, then another known book,
even while scouting is unfinished. Otherwise it reveals an unseen card. Powerups are never clicked
again as if they were ordinary pair cards. The solver waits for mismatches to hide.
Auto Claim closes the board when its clicks are exhausted or its book work is done,
then clicks the explicit claim control in the Superpairs Rewards menu.

Each input uses Minecraft's ordinary container-click API. Add-on inputs follow the
configured delay, advancing the sequence only after a validated click is sent. They
do not require the clicked tile to change: Hypixel need not give per-note feedback.
Superpairs still permits only one reveal in flight and requires a server-owned card
update; five seconds without confirmation pauses it without retrying. Window, screen,
empty cursor, slot, and server identity are checked again before every input.
The module never cancels, suppresses, or crafts inventory packets. This cannot
guarantee that Hypixel permits automation or that its anticheat will accept it.

Auto Advance selects available add-ons, then Superpairs, always choosing the highest
unlocked stakes. It does not downgrade because XP is short. Auto Buy XP opens Experience
Bottles and buys/consumes up to two Titanic bottles per refill, with a configurable
per-bottle price limit (default 1,000,000 coins). An owned Titanic with an explicit
Click to consume offer is consumed directly without requiring a Bazaar price, including
on Ironman. Missing inventory bottles are not clicked. It waits for server XP confirmation
before considering another purchase. Auto Renew uses the table's blaze-powder control
and displayed XP/Bits costs after charges run out. Both options can be disabled.
Charges are tracked from the actual Superpairs menu item; new remaining charges start
a fresh set. Once no further available sessions/renewals remain, status reads Complete.

## Validation

Pure state-machine tests cover reward stopping, repeated notes, incomplete patterns,
server acknowledgement, timeouts, window changes, number ordering, and book identity.
An authenticated local-control action `experimentsFixture` opens deterministic games
only on an integrated server. Its normal server-side ChestMenu receives the actual
vanilla client inputs and reports correct/invalid clicks and rewards through `/state`.
This validates the Minecraft container flow; it is separate from Hypixel server proof.

Protocol research: [SkyHanni's published experiment helpers](https://github.com/SkyHanniStudios/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/inventory/experimentationtable/ExperimentsAddonsHelper.kt)
and [Superpairs API](https://github.com/SkyHanniStudios/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/inventory/experimentationtable/api/ExperimentationSuperpairApi.kt).
The supplied Taunahi Auto-Patch JAR was inspected as an archive only; it is an
obfuscated loader and did not expose a readable solver. No payload was executed or copied.

## OdinClient add-on adaptation

Chronomatron keeps a cumulative note history, appending the first glowing tile once
per CLOCK/input phase. It does not expect the whole pattern to flash again each round.
Ultrasequencer learns the numbered board during GLOWSTONE/watch and orders numeric-name
items by their stack counts, retaining the map while the numbers hide. Both use our
existing normal PICKUP container-click API, not Odin's CLONE input mode. The selected
stakes' advertised +3 chain/series goal survives menu entry; closing waits for the
server to advance beyond the completed goal. Unexpected failed add-on results stay
open for review. Superpairs keeps its own matching and server-confirmation logic.

Reference: [OdinClient AutoExperiments.kt](https://github.com/skies-starred/OdinClient/blob/4df18c564c097ae630ef0eb2aa1f42837e62fdcf/src/main/kotlin/foo/starred/odinclient/features/impl/general/AutoExperiments.kt),
Copyright (c) 2026, Starred, BSD-3-Clause. The full notice is packaged in
`META-INF/licenses/OdinClient-AutoExperiments.txt`.

Local fixtures start at round one, show only the new glowing Chronomatron note at
input time, and use count-based Ultrasequencer tiles without a bookshelf counter or
per-input visual acknowledgements.
