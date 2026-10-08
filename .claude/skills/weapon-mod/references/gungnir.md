# SS-03 Gungnir: full map

An orbital kinetic strike. Using the uplink on a block 16+ blocks away fires a spear from an accelerator ring round
Jupiter. A 22-second feed follows it to Earth, and it lands as a falling star. It planes a 128-block zone (by
default), leaves a white-hot bowl, a scorched ring, molten crust and a spire from bedrock to the build limit, and
kills everything in the zone. The README's "What happens when you fire" table is the agreed second-by-second design.
Keep it in step with any change.

## Timeline (`strike/StrikeTimeline.java`, ticks from the lock)

| Constant | Tick | What happens |
|---|---|---|
| `LOCK` | 0 | Beam and reticle on the target; lock sound. |
| `RISE` | 26 | The shooter's camera rises over the target into the cloud deck (`CameraDirector`, `AerialHaze`). |
| `ORBIT` | 50 | The feed starts: Earth from orbit. |
| `RELAY` | 78 | Relay satellite fires its laser and rides it to Jupiter. |
| `WAKE` | 102 | The accelerator ring wakes round Jupiter. |
| `LOADING` | 146 | Spear in its sabot seats in the breech. |
| `LAPS` | 166 | 7 laps (`LAP_COUNT`) of the ring, faster each time. |
| `RELEASE` | 250 | Out of the muzzle in slow motion. |
| `DEBRIS` | 280 | Asteroid belt; punches through a boulder. |
| `TRANSIT` | 304 | Transfer plot to Earth. |
| `TERMINAL` | 352 | Seeker view hunts the target. |
| `REENTRY` | 380 | Plasma sheath. |
| `INBOUND` | 414 | The feed ends; the falling star in the world. |
| `IMPACT` | 438 | Server carves; flash, impact frames, fireball. |
| `IMPACT_FRAME_END` | IMPACT+44 | The stylised impact frames end. |
| `WIDE_END` | IMPACT+110 | The wide shot ends. |
| `CAMERA_END` | WIDE_END+24 | The camera is handed back. |
| `END` | IMPACT+200 | The strike is forgotten (crater carving may still finish after). |

The class also holds `ENTRY` / `EXIT` (where the round enters and leaves the frame in the lap shots) and the lap
speed maths. `Feed.showing(t)` is `ORBIT ≤ t < INBOUND`.

**If you retime it:**
- `tools/gen_sounds.py` has the feed's sections hard-written in seconds (see `feed_ambience`'s comment and `CAPS`).
- Fix those to match, run `python3 tools/gen_sounds.py feed_ambience …`, and commit the new `.ogg` files.

## Server

- **`item/UplinkItem`**
  - `use()`: `aim()` (`Targeting.findTarget` from the eyes, `Targeting.MAX_RANGE` = 640).
  - Refuses with `message.shootingstar.no_solution` (no target), `danger_close` (closer than
    `Targeting.minRange(radius)` = 1.5× the crater radius, at least `MIN_RANGE` 16) or `cycling` (`StrikeManager.isBusy`).
  - Otherwise `StrikeManager.launch` and a cooldown of `COOLDOWN` = IMPACT+60.
  - Tooltip `item.shootingstar.gungnir_uplink.tooltip.0..2`.
- **`strike/Targeting`**: `findTarget` (a voxel ray), `settle` (drops a hit on a canopy or plant down to the ground
  under it), `minRange` = max(16, 1.5 × radius), `DEFAULT_RADIUS` 64.
- **`strike/StrikeManager`**:
  - `launch` settles the target, reads `gungnirCraterRadius` once and adds a chunk ticket. The ticket covers
    scorch (1.5r) / 16 + 2 chunks and expires at END+200.
  - Then it broadcasts `StrikeLockPayload(id, target, shooter, age 0, radius)` and fires `gungnir_lock`.
  - `tick`: `age++`.
    - At `IMPACT` it builds an `ImpactBuilder`, calls `start()`, and broadcasts
      `StrikeImpactPayload(id, target, radius, zoneDiameter or 0 when terrain is off, spireHeight)`.
    - It fires `gungnir_impact`, plus `gungnir_danger_close` if the shooter is within 60 blocks.
    - After that it calls `step()` each tick until carved.
  - JOIN: re-sends locks for strikes before IMPACT with their current age.
  - `cancelAll` (by command) only cancels strikes that haven't hit.
- **`strike/ImpactBuilder`** (the crater):
  - Shape:
    - `radius` (game rule, 8–160);
    - bowl: radius 0.55r, depth 0.62 × bowl;
    - scorch out to 1.5r;
    - the shock front grows as scorch × (t / waveTicks)^0.45, with waveTicks = max(18, 0.75r).
  - Columns are sorted by distance and carved as the front reaches them, at most `BLOCK_BUDGET` 60 000 blocks a tick.
    - `plane` vaporises above the impact level, at most `MAX_CUT` 140 up, and resurfaces with `crust(heat)`.
    - `scorch` strips leaves, breaks glass, fuses sand, melts snow and starts fires.
    - A rim of blackstone, basalt and magma.
  - The spire (`buildSpire`): radius² `SPIRE_R2` 6, `GUNGNIR_HULL` with `GUNGNIR_COIL` bands every `BAND_SPACING`
    24, fins `FIN_HEIGHT` 30 × `FIN_SPAN` 8 at the top. It runs from the bottom of the world to the build limit, and
    only if `gungnirSpire` is on.
  - Entities (`blastEntities` / `hit`):
    - inside the radius: 1000 damage (`ModDamageTypes.kineticStrike`, no attacker yet);
    - in the ring beyond: 3 + 15 × ring², fire, and knockback outward.
  - Ejecta (falling blocks) are spawned at tick 3. `set()` skips blocks with hardness < 0 and removes block
    entities.
  - `gungnirTerrainDamage` off means no blocks are changed, but entities are still hit.
- **`block/MoltenCrustBlock`**: `HEAT` 3 → 1 on random ticks, then `FUSED_CRUST`. Water quenches it at once. The
  block has **no item** (it's why `advancement/gungnir/danger_close.json`, which uses it as an icon, fails to load:
  an open bug).
- **`command/GungnirCommand`**: `/gungnir strike <pos>` and `/gungnir cancel`, for operators.
- **Game rules** (`registry/ModGameRules`):
  - `gungnirTerrainDamage` (true);
  - `gungnirCraterRadius` (64, 8–160);
  - `gungnirSpire` (true).
- **Damage** `kinetic_strike` (json + bypass tags + `death.attack.shootingstar.kinetic_strike`).
- **Advancements** `advancement/gungnir/`: `lock`, then `impact`, then `danger_close`.
- **Recipe**: `recipe/gungnir_uplink.json` (lightning rod, echo shards, nether star, netherite, compass). It unlocks
  on having a nether star (`advancement/recipes/combat/gungnir_uplink.json`).

## Client

- **`client/Aim`**: while the uplink is held: target, distance, danger-close and cooldown, for the HUD readout and
  the reticle.
- **`ClientStrike` / `ClientStrikes`**:
  - The mirror of each strike.
  - `onLock` plays the lock sound for the shooter and a beacon sound at the target.
  - `tick` ages strikes and holds at IMPACT−1 until `onImpact` (it gives up after 100 ticks).
  - `onImpact` builds an `ImpactScene` (if within 1200 blocks), adds it to `WorldFx`, and calls
    `ImpactEffects.trigger`.
  - `cues(from, to)` plays the feed's sounds at timeline marks:
    - `held()` for feed sounds that stop on skip;
    - `master()` for non-positional sounds;
    - `at()` for placed mono sounds.
  - It also hides the HUD while the cinematic owns the screen, and pauses `Culling` while the camera flies.
- **Feed**:
  - `client/feed/Feed` (offscreen HDR `Target`, bloom, motion-blur shutter, `Overlay` text).
  - `client/feed/Shots` has one method per phase: `orbit`, `relay`, `wake`, `loading`, `laps` (`tunnel` /
    `exterior`), `release`, `debris`, `transit` (`plot`), `terminal` (`seekerHud`).
  - `client/feed/Space` draws the sky, planets, meshes and plasma.
  - Planet maps come from `tools/fetch_maps.py` (NASA, JPEG, loaded by `gfx/Tex`).
  - Meshes (spear, sabot, ring, relay) come from `tools/models.py` (`.ssm`).
- **Camera**:
  - `client/camera/CameraDirector`: the rise before the feed, then one continuous shot from the edge of the blast
    zone through the hit, the shock wave and the crane over the crater. Applied by `mixin/CameraMixin`.
  - `ScreenShake`: the rumble, the jolt, the shock front.
  - `AerialHaze` + `BackgroundRendererMixin`: haze and cloud deck on the rise, and the dust after (`render/Dust`).
- **World**:
  - `render/WorldEffects` (AFTER_TRANSLUCENT): the lock beam and the reticle rings.
  - `world/WorldFx` (LAST): the falling star, the fireball, the condensation shell, the shock ring, debris, smoke,
    then the impact frames and grading.
  - `world/ImpactScene`: one impact's simulation, scaled by the radius.
  - `render/ImpactEffects`: the boom at the speed of sound, embers, keeping the carved terrain drawn.
- **HUD**: `render/HudEffects` draws the lock marker, readout, aim info and status card, and calls `Feed.render`.

## Sounds

These are all from `tools/gen_sounds.py`:

- `uplink_lock`, `uplink_denied`, `camera_rise`;
- `feed_*` (zoom, ambience, relay, wake, load, coils, lap, release, strike, cruise, transit, locate, reentry);
- `strike_inbound` / `_near`, `strike_impact` / `_near`, `strike_rumble` / `_near`, `strike_aftermath` / `_near`.

`_near` versions are stereo for the shooter; plain ones are mono and placed in the world. Their events are
registered in `registry/ModSounds` as `uplink.lock`, `feed.zoom`, `strike.impact.near`, and so on.

## Tests

`src/gametest/.../StrikeGameTests.java`:

- `targetingAndCrust` and `kineticDamageKills` (batch `a_blocks`);
- `fullStrike` (batch `b_strike`), which runs the real timeline to IMPACT+120 and checks the crater.

The client self test is `ClientSelfTest` (selftest workflow, `test=true`).
