# Þ-01 Mjölnir: full map

The Shooting Star's own strike, made to replace Gungnir's power with something that is not Gungnir. Raising the hammer
at a block 96+ blocks away calls the planet's thunderstorms (the global circuit) down on it in one bolt, 18.3 s later.
It blasts a crater fused to charged fulgurite and leaves the bolt standing in it petrified, burns everything wooden in
the radius, kills everything in the radius, burns a Lichtenberg scar out to 1.5× the radius, and arcs to whatever stands
in the ring beyond. The README section "Þ-01 Mjölnir" and its time table are the agreed description.

It is built like Gungnir (a strike that is over in half a minute, not saved), but shares only the general machinery:
`Targeting`, `Feed`/`Space`/`gfx`, `Shaders`, `CameraDirector`, `ScreenShake`. Nothing in it uses Gungnir's own classes,
so Gungnir can be removed without touching it.

## Timeline (`thunder/ThunderTimeline.java`, ticks from the hammer going up)

| Constant | Tick | What happens |
|---|---|---|
| `RAISE` | 0 | First person: the hammer comes up overhead and lights up (`client/thunder/HammerRaise`). Others see the arm raised (`PlayerArmPoseMixin`, THROW_SPEAR). |
| `CALL` | 16 | A bolt from the hammer's head to the storm over the target (`ThunderRender.call`); `mjolnir.call` for everyone else. |
| `RISE` | 30 | The shooter's camera climbs over the target looking up into the storm (`ThunderCamera.rise`, `AerialHaze` dark deck). |
| `FEED` | 54 | The feed: orbit over the night side (`ThunderShots.orbit`). |
| `DRAW` | 116 | The ring of charge closes on the target. |
| `FORGE` | 206 | The supercell winds up; MJÖLNIR. |
| `LEADER` | 276 | Down the eye after the stepped leader (local scene, units of 100 m). |
| `INBOUND` | 326 | Back in the world: leader steps down, streamers, corona buzz. |
| `STROKE` | 366 | Server builds; the stroke, impact frames. |
| `RESTRIKES` | +5, +11, +19 | The channel flashes again (`channelFlash`). |
| `FRAMES_END` | +26 | Frames over; the camera cuts high overhead. |
| `WIDE_END` | +120 | The overhead shot ends. |
| `CAMERA_END` | WIDE_END+24 | Back in the shooter's eyes. |
| `END` | +240 | Forgotten (the ground may still be settling). |

Shared maths: `coreRadius` (0.42 r), `scarRadius` (1.5 r), `minRange` (= scar radius), `cloudBase(targetY, topY)`,
`vortexRadius(t, r)`, `leaderReach(t)` (stepped, quickening), `scarFront(e)` (`SCAR_SPEED` 2.5 blocks of path a tick),
`BURN_SPEED` (6 blocks a tick), `channelFlash(e)`.

**If you retime it:** `tools/gen_sounds.py` keeps its own copy in the `THUNDER` dict (and `RESTRIKES`). Update it and
regenerate the `mjolnir_*` and `thunder_*` sounds.

## Server

- **`item/MjolnirItem`**: `use()` → `aim` (`Targeting.findTarget`, 640), `refusal(target, eye, minRange)` (static,
  tested: `message.shootingstar.mjolnir.no_target` / `.too_close`), busy → `.gathering`; else `ThunderManager.launch`
  and a cooldown of `STROKE + 60`.
- **`thunder/ThunderManager`**: like `StrikeManager`. `launch` settles the target, reads `mjolnirRadius` once, draws a
  seed, adds a chunk ticket (scar + 16 blocks, up to 18 chunks), broadcasts `ThunderLockPayload(id, target, shooter, age,
  radius, seed)` and fires `mjolnir_raise`. At `STROKE` it makes a `ThunderBuilder`, `start()`s it, broadcasts
  `ThunderStrokePayload(id, target, radius, seed, terrain, boltHeight)` and `ThunderArcsPayload` and fires
  `mjolnir_stroke`; then `step()`s it each tick. JOIN re-sends locks before the stroke. `cancelAll` drops strikes not yet
  struck (`ThunderCancelPayload`).
- **`thunder/Lichtenberg`**: the scar's figure, grown from the seed (pure Java, no Minecraft classes, so it can be run
  and drawn outside the game). Arms keep to their own headings so they stay spread round; forks pull back to running
  outward; step length grows with the radius so big scars branch like small ones. `segments()` (with path distances
  `from`/`to`) for the client; `cells(skip)` rasterises it into ground cells sorted by arrival for the server.
- **`thunder/ThunderBuilder`** (the ground):
  - columns sorted by distance, processed as the burn front (`BURN_SPEED`) reaches them: inside `core` `blast` (bowl,
    everything above up to `MAX_CUT` 120 vaporised, lined with charged fulgurite, a lip of shards); beyond it `scorch`
    (leaves off, logs → `CHARRED_LOG`, burnable blocks burned, plants, glass, then `ground`: grass → coarse dirt or tuff,
    sand → glass, snow off, fires). Block entities stop the scan (containers ride it out).
  - scar cells processed as `scarFront` reaches them: `channel` digs a trench (depth from width and centrality, ≤ 4)
    and lines it with charged fulgurite (charge from width).
  - the petrified bolt (`buildBolt`, `capsule`) is built once the core is carved, from the seed: a zigzag column from the
    crater floor `boltHeight` up (1.5 r, under the cloud base, inside the world), forks near the top, charged heart.
  - creatures: `hitCore` on the stroke's tick, `groundCurrent` as the burn front passes out to `radius` (1000 damage,
    `thunderstruck`, the shooter as attacker); `planArcs` picks the ring (radius..scar), nearest first, up to 32, and
    chains from each to the nearest unhit creature within 14 blocks, 3 deep, never the shooter; `land` deals `arced`
    damage, fire, a push, and vanilla `onStruckByLightning` (charges creepers, converts pigs and villagers). Fires
    `mjolnir_chain` (≥ 5 arcs) and `mjolnir_charged`.
  - `BLOCK_BUDGET` 60 000 writes a tick; `set` skips hardness < 0.
- **Blocks** (`registry/ModBlocks`): `FULGURITE`, `CHARGED_FULGURITE` (`block/ChargedFulguriteBlock`: `CHARGE` 3→1 on
  random ticks then fulgurite, water earths it, shocks what stands on it, sparks), `CHARRED_LOG` (pillar).
- **`command/MjolnirCommand`**: `/mjolnir strike <pos>`, `/mjolnir cancel`.
- **Game rules:** `mjolnirRadius` (64, 8–160), `mjolnirTerrainDamage` (true), `mjolnirPetrifiedBolt` (true).
- **Damage:** `thunderstruck` (in every bypass tag, like Gungnir's) and `arced` (bypasses armour and shields only).
- **Advancements** `advancement/mjolnir/`: `raise` → `stroke` → `chain`, `charged`. The root also unlocks on holding it.
- **Recipe** `recipe/mjolnir.json` (netherite, heavy core, lightning rods, nether star, breeze rod), unlocked by a nether
  star.

## Client (`client/thunder/`)

- **`ClientThunder` / `ClientThunders`**: the mirror and its sound cues (raise, call, rise, feed, draw, forge, leader,
  storm, charge; the stroke, roll and aftermath at the speed of sound in `strokeSounds`; arcs in `onArcs`), holding at
  `STROKE - 1` until the server's stroke, the HUD hidden during the cinematic, `flying()` for `Culling` (called from
  `ClientStrikes.tick`), `raising(player)` for the arm pose, sparks and smoke particles.
- **`BoltPath`**: the bolt's channel and branches from the seed (the leader and the stroke draw the same path).
- **`ThunderRender`** (WorldRenderEvents.LAST): darkens the world under the storm, draws the vortex (`ss_vortex`, three
  layers at the cloud base, flashes inside it from `stormFlash`), then into an HDR buffer: the warning rings (ground
  heights sampled at the lock), the call, the leader, the streamers, the stroke, afterglow and beads, the thunderclap
  shell (`ss_shell`), the scar burning in (ground heights sampled at the stroke), the arcs and the crawlers; lights
  through `ss_light`; bloom and `fxcomp`; then the impact frames and flashes (`ss_thunder`, `FRAME_AT`/`FRAME_MODE`).
  `gloom()` also tints the fog (`BackgroundRendererMixin`).
- **`ThunderCamera`** (through `CameraDirector`): rise, witness (1.25 r out, low, looking up), overhead (straight
  down, under the cloud base), back to the eyes.
- **`ThunderHud`**: the feed (`Feed.renderThunder`), the lock marker over the target for everyone ("YOU ARE UNDER IT"
  inside the zone), the readout, aim info and a status card with the hammer in hand.
- **`HammerRaise`**: the first-person raise (`HeldItemRendererMixin`). The model lies on the diagonal; it is stood up
  with a 45° turn, as `KeyTurn` does for the key.
- **Feed** `client/feed/ThunderShots`: `orbit`, `draw`, `forge`, `leader`. Earth with storms is `ss_storm` through
  `Space.stormEarth` (uniforms: Target, Storms, Front, Drain, Vortex, Spin, Charge); the leader shot draws a fine
  `Mesh.spherePatch` round the target at 100 m units with `Space.stormEarthPatch`, cloud layers with `ss_vortex`, and
  the leader with `Fx` beams.
- **Shaders:** `ss_vortex`, `ss_thunder`, `ss_storm` (`Shaders.thunderReady()` checks them; the other weapons only need
  `ready()`).

## Sounds

All from `tools/gen_sounds.py`, seeded per name: `mjolnir_raise`, `mjolnir_call` (mono), `mjolnir_denied` (mono),
`thunder_rise`, `thunder_feed`, `thunder_draw`, `thunder_forge`, `thunder_leader`, `thunder_storm` (mono),
`thunder_charge` / `_near`, `thunder_stroke` / `_near`, `thunder_roll` / `_near`, `thunder_arc` (mono),
`thunder_aftermath` / `_near`. Building blocks: `crack` (N-wave), `tear`, `roll`, `sferic`, `tweek`, `whistler`,
`corona`.

## Textures and models

`tools/gen_textures.py`: `mjolnir_atlas` (32×32) + `mjolnir_model` (cuboids, laid on the diagonal), `mjolnir_icon`,
`fulgurite(charge)`, `charred_log`, `charred_log_top`. Block states and models for the three blocks are plain JSON.

## Tests

`src/gametest/.../ThunderGameTests.java`: `refusalsAndFigure`, `fulguriteDischarges` (batch `e_thunder`), `fullStroke`
(batch `f_thunder`, the real timeline at radius 20), `terrainHeld` (batch `g_thunderheld`, the builder run directly with
terrain off).

## Previewing without the game

The sandbox has no Minecraft, but the shaders can be compiled and drawn in headless Chromium's WebGL2 (GLSL 150 →
`#version 300 es` plus precision lines), with Playwright from node; and `Lichtenberg` compiles on its own with `javac`
to dump and plot a figure. That is how the vortex, the storm Earth and the scar were looked at before CI.
