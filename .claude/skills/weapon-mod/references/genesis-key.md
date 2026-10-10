# Ω-00 Ginnungagap (the Genesis Key): full map

The Genesis Key's event runs in this order:

1. **Turn the key** on a block 24+ blocks away. Bifröst, a gate in orbit, opens onto the void between universes.
2. **The feed** dives into one universe and pulls back until it is a block in a lattice. The gate cuts that block
   out and drops it down a bridge of light onto the target.
3. **The burst** swallows anyone it reaches, then falls in on itself.
4. **The black spreads**, erasing whoever is left in the zone. Then **the whole world is gone** for everyone on the
   server: all players are gathered round the shooter at the rim of the hole, on a floor of nothing, untouchable.
5. Everything within `ginnungagapRadius` is **erased for good**: a ragged shaft down through bedrock, with
   fissures.
6. **The first turn cracks the key.** Turned again in the black, it shatters. Yggdrasil grows out of the hole, the
   world is rebuilt along its roots, and everyone is sent home.

It is a **server-wide live event**: only one runs at a time, and it survives restarts. The README section
"Ω-00 Ginnungagap, the Genesis Key" is the agreed description.

## Timeline (`gap/GapTimeline.java`, ticks from the key turning)

| Constant | Tick | What happens |
|---|---|---|
| `KEY` | 0 | First person: the key comes up, goes into a lock of light and turns (`client/gap/KeyTurn`). |
| `RISE` | 42 | The camera climbs out of the shooter's eyes over the target into the clouds. |
| `FEED` | 66 | The feed: up to orbit. |
| `GATE` | 92 | Bifröst wakes. |
| `OPEN` | 136 | The gate opens; the camera dives into the other universe. |
| `MAP` | 184 | Pull back out until the universe is a block in the lattice; it's selected. |
| `CUT` | 268 | The block is cut out and drawn through the gate. |
| `SEND` | 310 | The bridge reaches down; the block drops in. |
| `FALL` | 336 | The chase down the bridge. |
| `INBOUND` | 404 | Back in the world: the block comes down. |
| `CONTACT` = `FRAMES` | 428 | It hits; the impact frames (`client/gap/GapFrames`). |
| `BLAST` | 458 | The universe bursts out of its block. |
| `COLLAPSE` | 518 | It falls back in on itself. |
| `ERASURE` | 528 | The black spreads out from the point of contact. |
| `NOTHING` | 628 | Only the shooter is left; the server takes everyone into the void. |
| `RETURN` | 728 | The camera is back in the shooter's eyes; they can move. |
| `END` | 808 | The sequence is over; the event waits for the cracked key. |

The **rebuild** is counted from release: `REBUILD_TREE` 40, `REBUILD_WORLDS` 120, `REBUILD_GATHER` 160,
`REBUILD_SWEEP` 220, `REBUILD_DONE` 520, `REBUILD_END` 640.

Shared geometry used by both sides:

- `MIN_RANGE` 24;
- `BLOCK` 5 (half-size of the falling block);
- `burstHalf(radius, t)`, `blastHalf(radius)`;
- `eraseFront(t)` (the visual wave);
- `eraseReach(t, radius)` (the real erasure, which finishes inside the wave).

**If you retime it:** `tools/gen_sounds.py` has its own copy in the `GAP = dict(...)` near line 1194. Update it and
regenerate the `gap_*` sounds.

## Server

- **`item/GenesisKeyItem`**: `use()` takes one of these branches:
  - **Cracked key, world gone, not before `RETURN`, unmaking done:** `shatter`, then
    `GapManager.release(gap, server, true)` and fire `gap_restored`. The item is used up.
  - **Cracked key otherwise:** `message.shootingstar.gap.cracked` / `.unmaking`.
  - **World already gone:** `.void`.
  - **Another event running:** `.busy`.
  - **No target / too close:** `.no_target` / `.too_close`.
  - **Otherwise:** `GapManager.launch` and `crack(stack)` (CUSTOM_DATA `Cracked`), `COOLDOWN` 40.

  `cracked(stack)` and `mend(stack)` are public. The client model predicate `shootingstar:cracked` (in
  `ShootingStarClient`) swaps the 3D model to the cracked one.
- **`gap/GapManager`** (≈1100 lines; read its class Javadoc):
  - **`launch`:**
    - settles the target and reads `ginnungagapRadius` and `ginnungagapTerrainDamage` once;
    - adds a chunk ticket (radius/16 + 2 chunks, up to 32);
    - saves `GapState.event`;
    - broadcasts `GapLockPayload`;
    - fires `gap_open`.
  - **`tick`:**
    - `CONTACT..COLLAPSE`: `swallow` (inside the burst cube, if `ginnungagapLethal`);
    - `ERASURE..NOTHING`: `eraseIn` (inside `eraseReach`);
    - `NOTHING`: `take` (marks taken, sets one floor height, `gather`s every player to a place round the shooter,
      fires `gap_void`);
    - while taken: `erase` steps the `Erasure`;
    - after `END`: a command event (no shooter) releases itself; otherwise the event releases once the shooter has
      been offline for `ABSENT_LIMIT` (5 min).
  - The shooter and creative or spectator players are never killed (`spared`).
  - **`release(gap, server, byKey)`:**
    - finishes the hole at once if it was released early;
    - moves anyone out over the hole to the rim;
    - mends or shatters the key if it wasn't turned;
    - broadcasts `GapEndPayload`;
    - moves the gap to `RETURNING`.

    In `RETURNING`: `unlid` at `REBUILD_DONE + 40`, then `sendHome` for everyone at `REBUILD_END`. A player whose
    rebuild ended early sends `GapSettlePayload` (C2S) and is sent home at once.
  - **Players:**
    - `gather` / `place` / `hold`: the void floor sends `GapFloorPayload(floor)`.
    - `warp`: the light holds a player `WARP_DELAY` ticks, then `GapWarpPayload` and the move.
    - `sendHome`, `safe`, `standable`, `toRim`. Home is the position saved in `GapState.homes` when the player was
      gathered. `sendHome` sends them to `safe(home)`: if home is within radius + 8 of the target, its rim. Then
      ground level with home (`levelWith`: two blocks up or down at most, in home's column or within `LEVEL_REACH` 6
      of it), so that someone whose home a fissure split lands on its edge, not at its bottom (kortev's choice; test
      `splitHomeLandsBeside`, through `GapManager.landingFor`); failing that the nearest standable block in home's own
      column, near its height; failing that, the nearest ground round about.
    - The server never puts any ground back. The rebuild is only drawn by the clients; the hole and the fissures are
      there for good.
  - **While the world is gone ("cut")**, Fabric events block attacking, using, placing and breaking. Only the key
    still works. `ALLOW_DAMAGE` protects the shooter during the sequence and everyone held.
  - **Lifecycle:**
    - `SERVER_STARTED` → `recover` (finishes a hole cut short by a crash; sends people home);
    - `SERVER_STOPPING` → `endNow`;
    - JOIN / world change → `introduce` (sends running events);
    - respawn and disconnect are handled too.
  - `mirrorOf` (MIRROR_* blocks), the lock payload's `swapSpot` and `GapSwapPayload` are **legacy**: still
    registered for the network format, but nothing sends or draws them now.
- **`gap/Erasure`**: the hole.
  - The shape: a ragged shaft (`RAGGED` 4) from the build limit through bedrock, plus `CRACKS` 14 fissures.
  - The fissures run out from just inside the rim, 0.25–0.7 × radius long. Where they run is seeded from the target's
    position, so `recover` cuts the same ones again. `farthest(radius)` (about 1.7 × radius + 3) is the farthest out
    any of them can reach: ground beyond it is never touched.
  - It changes blocks quietly (`FORCE_STATE | SKIP_DROPS`), `BLOCK_BUDGET` 40 000 and `READ_BUDGET` 200 000 a tick.
  - `WorldChunkMixin` hands it the light checks, so there is one per column at the bottom when done.
  - Every touched chunk is resent once at the end; `finishBlocks()` does the rest at once.
  - Technical blocks are kept.
- **`gap/VoidFloor`**: the one floor height everyone walks on in the void (server and client).
  - `EntityMixin`: no collision except the floor, no fluids.
  - `LivingEntityMixin`: mobs can't target them.
  - `PlayerEntityMixin`: the server trusts the client's position.
  - `ServerPlayNetworkHandlerMixin`: not kicked for flying.
- **`gap/GapState`** (`PersistentState` `shootingstar_ginnungagap`): `homes` (where each player came from),
  `keys` (mend or shatter when next seen), `event` (to recover after a crash).
- **`command/GapCommand`**: `/ginnungagap open <pos>` and `/ginnungagap release`.
- **Game rules:**
  - `ginnungagapRadius` (96, 16–256);
  - `ginnungagapTerrainDamage` (true);
  - `ginnungagapLethal` (true).
- **Damage:**
  - `swallowed` (by the burst), from `ModDamageTypes.swallowed(world, by)`;
  - `erased` (by the black), from `ModDamageTypes.erased(world, by)`;
  - both with the shooter as attacker.
- **Advancements** `advancement/genesis/`: `key` (root), `open` (child of key), `restored` (child of open), and
  `void` (child of root).
- **Recipe**: `recipe/genesis_key.json` (recovery compass, echo shards, nether star, heavy core). It unlocks on a
  nether star.

## Client

- **`client/gap/ClientGap` / `ClientGaps`**:
  - The mirror of each event and its sound `cues(gap, from, to)`.
  - `holdInput` (START_CLIENT_TICK) swallows keys while the camera is taken.
  - It also tracks `floating()` (on the void floor), `hushes` (world sounds muted), `rebuilding()`, and
    `keyStillWhole()` (for the model predicate).
  - Handlers: `onLock`, `onEnd`, `onFloor`, `skipFeed`, `leftWorld`, `clear`.
- **Camera:** `client/gap/GapCamera` (rise, the feed, the shot from behind of the bridge and the block, a cut per
  impact frame, back to the eyes), `mixin/EntityLookMixin` (holds the head still) and `VoidCameraMixin`.
- **Feed:** `client/feed/GapShots` (`shotAt(s)` picks the shot, then `shot(shot, s, o)` draws it), shown by
  `GapHud` through `Feed.renderGap`. The universes are `gfx/Universe`: a cosmic web of galaxies drawn by
  `ss_galaxy`, inside a glass block drawn by `ss_block`. The galaxies come from `textures/feed/universe.bin`, built
  by `tools/gen_universe.py`. The gate mesh is `gate.ssm`.
- **World:**
  - `client/gap/GapRender`: the bridge, the falling block, the burst, the collapse, the black.
  - `GapFrames`: the impact frames.
  - `TreeRender`: Yggdrasil from `tools/gen_yggdrasil.py`, drawn by `ss_tree`.
  - `VoidFx`: the floor discs, step ripples and warps.
  - `WorldRendererMixin`: no clouds.
  - `HeldItemRendererMixin` + `KeyTurn`: the key turning in first person.
  - `PlayerArmPoseMixin`: others see the key held out.
- **Sound and music:**
  - `RebuildSound`: the rebuild score, faded when hurried.
  - `VoidMusic`: *Mice on Venus* in the black.
  - `MusicTrackerMixin`: vanilla music waits.
  - `VoidSoundMixin`: no world sounds in the void.
  - `VoidToastMixin`: toasts wait.
  - `VoidOverlayMixin`, `VoidPlayerMixin`: no in-wall overlay or push-out on the floor.
- **HUD:** `client/gap/GapHud`: the key readout, the feed, the way out of the black.
- **The key's model**: `tools/gen_textures.py` writes the 3D item model and the cracked override.

## Sounds

These are all from `tools/gen_sounds.py`, each seeded by `zlib.crc32(name)`, all stereo except `gap_swap`:

- `gap_key`, `gap_ambience`, `gap_wake`, `gap_tear`, `gap_map`, `gap_lock`, `gap_extract`, `gap_send`, `gap_fall`;
- `gap_drone`, `gap_inbound`, `gap_swap`, `gap_contact`, `gap_impact`, `gap_blast`, `gap_erase`, `gap_void`,
  `gap_rebuild`.

Their events are `gap.key`, `gap.ambience`, and so on.

## Tests

`src/gametest/.../GapGameTests.java`:

- `liveEvent` (batch `c_gap`): a whole event with fake players, through the void and home.
- `stopMidEvent` (batch `d_gapstop`): the server going down mid-event finishes the hole, removes the floor,
  sends everyone home and shatters the key.
- A home a test expects to find whole (the shooter's in `liveEvent`, the watcher's in `stopMidEvent`) stands on a
  platform past `Erasure.farthest(RADIUS)` (`beyondFissures`).
  - The test server puts its tests at a random x/z every run, so the fissures run somewhere new every time.
  - The shooter's home used to be 30 blocks out, within their reach. About one run in 25 a fissure split it, and
    `safe` set the shooter down at the bottom of it: the old flake, "the shooter was not taken home", 5 blocks low.
  - Put any new test home out there too, unless the test is meant to cope with it being split.

The client self test is `GapSelfTest` (selftest workflow, `test=gap`).
