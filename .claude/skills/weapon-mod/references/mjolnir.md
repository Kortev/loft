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
| `DRAW` | 116 | The charge relayed in storm to storm by megaflashes from the limb to the target (`ThunderShots.drawRelay`). |
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
- **Blocks** (`registry/ModBlocks`): `FULGURITE` (no light), `CHARGED_FULGURITE` (`block/ChargedFulguriteBlock`: light
  12/9/6, `CHARGE` 3→1 on random ticks then fulgurite, water earths it, shocks what stands on it except a charged
  creeper, sparks; animated texture), `CHARRED_LOG` (pillar). Only some of the crater lining and stretches of the
  scar's main channels are charged, and few fires are lit: thousands of lights and fires kept the lighting and the
  chunk meshes busy for minutes.
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
- **`BoltPath`**: the bolt's channel and branches from the seed (the leader and the stroke draw the same path), from
  the wall cloud's foot (`ClientThunder.wallBase()`, `wallDrop()` under the storm's base `top()`) to the ground.
- **`ThunderRender`** (WorldRenderEvents.LAST): darkens the world under the storm, draws the storm (`drawStorm`: five
  decks of `ss_vortex`, `DECK_HEIGHT`/`DECK_REACH`/`DECK_DENSITY`, then the wall cloud lowering out of the middle from
  `DRAW` to `INBOUND`: an `ss_wall` curtain and an `ss_vortex` disc at its foot; flashes inside from `stormFlash`), the
  dust (`ThunderDust`), then the light: only when `bright()` (the call, the leader, the stroke and after, or a light) into
  an HDR buffer with the light pass (`ss_light`), bloom without its streak and `fxcomp`; otherwise the warning rings go
  straight onto the picture. In the light: the rings (ground heights sampled at the lock), the call, the leader, the
  streamers, the storm's own bolts (`stormBolts`: a crawler for each flash, one in four down to the ground beyond the
  zone), the stroke, afterglow and beads, the scar burning in (ground heights sampled at the stroke; fine branches
  dropped once cooled), the arcs and the crawlers. Then the impact frames and flashes (`ss_thunder`,
  `FRAME_AT`/`FRAME_MODE`). `gloom()` also tints the fog (`BackgroundRendererMixin`).
- **`ThunderWeather`**: under a storm the client's world gets vanilla rain and thunder (`WorldWeatherMixin`, client world
  only), so the sky, fog and light go dark and rain falls; lightning, the call, the stroke and the restrikes flash the
  sky and light (`setLightningTicksLeft`); vanilla's clouds are hidden (`WorldRendererMixin`).
- **`ThunderDust`**: the shockwave's ring of dust (coloured by the ground it rises off) and the crater's steam, soft
  puffs in `ss_dust`, ticked from `ClientThunders.tick`.
- **`ThunderCamera`** (through `CameraDirector`): rise, witness (1.25 r out, low, looking up), overhead (straight
  down while the scar burns, then a crane down and round to a three-quarter view), back to the eyes (across first, high
  and clear of the ground, then straight down into the shooter, so a hill beside them is never flown through). `fovScale` (through
  `GameRendererMixin.getFov`) closes in on the leader and is flung wide by the stroke.
- **`ThunderHud`**: the feed (`Feed.renderThunder`), the lock marker over the target for everyone ("YOU ARE UNDER IT"
  inside the zone), the readout, aim info and a status card with the hammer in hand.
- **`HammerRaise`**: the first-person raise (`HeldItemRendererMixin`). The model lies on the diagonal; it is stood up
  with a 45° turn, as `KeyTurn` does for the key. Its runes flare through `HammerGlow` by `HammerRaise.glow(t)`:
  building to three times their glow at the call, humming bright while the storm gathers, blazing again at the stroke;
  others see the same on the hammer held over the shooter's head (`HeldItemFeatureRendererMixin`). The call's bolt leaves the hammer's head in front of the shooter's eyes, so it is drawn
  thinner within 24 blocks of the camera (`bolt(..., near)`), or its glow would cover the picture.
- **Feed** `client/feed/ThunderShots`: `orbit`, `draw`, `forge`, `leader`. Earth with storms is `ss_storm` through
  `Space.stormEarth` (uniforms: SunObj, Target, Storms, Front, Drain, Vortex, Spin, Charge; NoiseTex as Sampler3). The
  maps are 20 km a texel, so `ss_storm` works their detail out of NoiseTex in kilometres on a stereographic map
  centred on the target, at whatever scale the pixel footprint (`px`) shows: clouds as `heaps` and `puffs` with relief
  lit by the sun and the moon (finite differences towards each), cities as towns and point lights (`sparkle`, cells
  about four pixels apart at any zoom) with roads between, the maps themselves read with a cubic B-spline
  (`smoothMap`) and warped so no texel shows. Light comes through the air (`sunlight`, `twilight`, haze towards the
  limb); the night map's moonlit land is subtracted so only the lights are left. The draw shot's relay is geometry,
  not shader: `RELAY` is 18 chains of `Hop`s (megaflash channels and forks on the cloud tops, made once from a fixed
  seed), each starting out at the limb as the draw camera sees the planet and with its own start and arrival times, so
  they never line up into a ring or reach the target all at once; a hop crawls out as its chain's front (`relayFront`)
  crosses it, flickers and fades, lighting the cloud round it, fainter where the lines crowd together at the target;
  the storms outside a middling chain's front go dark (`Front`/`Drain`). The leader shot is local, in 100 m
  units with the ground at y = 0: a ground plane in `ss_ground` (country, rivers, towns, lit by the leader's tip and the
  storm's flashes, lost in rain haze), three layers of the storm's base in `ss_vortex` (`Detail` 8 so the 200 km disc
  has kilometre-sized cloud, no eye, lit from inside by `CLOUD_FLASHES` one at a time through `Flash`/`FlashFalloff`),
  and the leader with `Fx` beams, seen from 11 km off looking a little up. Shots hand over through flashes: the draw
  shot ends in a white flash the forge opens out of; the forge ends in the dark the leader shot fades up from.
- **Shaders:** `ss_vortex`, `ss_wall`, `ss_dust`, `ss_ground`, `ss_thunder`, `ss_storm` (`Shaders.thunderReady()` checks them; the
  other weapons only need `ready()`). The storm shaders read their noise from `gfx/NoiseTex` (a tiling 256x256 texture
  made once: billows in red and green, ridges in blue, fine grain in alpha) instead of working it out per pixel.
- **Performance:** while a feed covers the screen the world is not drawn (`GameRendererMixin.renderWorld`,
  `ClientThunders.feedCovers`); the leader feed shot uses zoom blur, not the shutter. `gfx/Timings` times the passes
  when `-Dshootingstar.timings=true` (the self test turns it on).

## Sounds

All from `tools/gen_sounds.py`, seeded per name: `mjolnir_raise`, `mjolnir_call` (mono), `mjolnir_denied` (mono),
`thunder_rise`, `thunder_feed`, `thunder_draw`, `thunder_forge`, `thunder_leader`, `thunder_storm` (mono),
`thunder_charge` / `_near`, `thunder_stroke` / `_near`, `thunder_roll` / `_near`, `thunder_arc` (mono),
`thunder_aftermath` / `_near`. Building blocks: `crack` (N-wave), `tear`, `roll`, `sferic`, `tweek`, `whistler`,
`corona`.

## Textures and models

The hammer is modelled in Blender by `tools/mjolnir_model.py` (bpy 4.5, the venv in the handoff; units are the
item's pixels, built upright, the item's centre at the origin):

- **Look**: a head of dark hammered iron about 2:1:1 with polished silver chamfers and a silver band near each end; a
  sunken band on each long face holding a closed four-strand plait (`billiard_loops`: a 45° billiard in a box of odd
  rows and columns, so over and under alternate), its strands raised iron with a glowing channel down each; the thorn
  rune in a ring in a sunken panel on each end; a silver collar, the haft wound in one overlapping leather strap
  (`build_grip`: real ridges, the step shaded sharp), an octagonal steel pommel, a ring and a short wrist loop.
- **Materials** each hold three surfaces switched by `set_surface`: `pbr` (the renders), `design` (the colour the
  texture is painted with, the bake lamp `BAKE_LIGHT` already on its dents and chamfers) and `glow` (how much it glows).
- `--out DIR --renders`: hero, side, rune end, interlace and haft renders, and a 64 px inventory render.
- `--game` (about a minute): unwraps (`TEXEL_WEIGHT`), bakes design × ambient occlusion into
  `textures/entity/mjolnir_baked.png` (512², out of the item atlas) and the glow into `mjolnir_glow.png`, writes `meshes/mjolnir.hbm` (format in
  `HammerMesh`'s Javadoc; the hammer laid on the diagonal by `UPRIGHT_TO_ITEM`, where the old cuboid model lay) and
  writes `models/item/mjolnir.json` from `DISPLAY` (parent `builtin/entity`).
- `--out DIR --views`: the baked copy read back from its file and drawn as the game draws it (nearest texels, the
  entity shader's two lamps, the glow added), placed by the game's own transform chains (`view_matrix`): first person
  in each hand, HammerRaise, third person on a Steve, the ground, an item frame, and slots at GUI scales 1-3. Tune the
  display transforms here, not in the JSON. `--fit` prints how much of the slot, frame and screen it takes.
- `--out DIR --sheet`: one contact sheet of the lot.

In game, `HammerRenderer` (a Fabric `BuiltinItemRendererRegistry` renderer, registered in `ShootingStarClient`) draws the
mesh into `getEntityCutoutNoCull(mjolnir_baked)` lit by the world, then the glowing quads again into
`getEyes(mjolnir_glow)`, full bright and additive. `HammerGlow.boost(level)` / `reset()` brighten the runes for the
hammers drawn in between (up to 3×, drawn over themselves).

`tools/gen_textures.py` keeps only `mjolnir_icon` (the flat icon, the model's particle texture), with
`fulgurite(charge)`, `charred_log`, `charred_log_top`. Block states and models for the three blocks are plain JSON.

## Self test

`selftest` workflow, input `mjolnir` (`src/gametest/.../MjolnirSelfTest.java`): raises the hammer at flat ground with
zombies in the zone and a creeper, a pig and a villager in the ring; films it with stills at every beat; flies round
the crater; photographs it from above, the bolt, the floor, the scar and the crater at night; logs `[perf]` lines every
second (each pass's time, server ticks, lighting and meshing backlogs) and what became of the creatures.

## Tests

`src/gametest/.../ThunderGameTests.java`: `refusalsAndFigure`, `fulguriteDischarges` (batch `e_thunder`), `fullStroke`
(batch `f_thunder`, the real timeline at radius 20), `terrainHeld` (batch `g_thunderheld`, the builder run directly with
terrain off), `burnsUnderTheBolt` (batch `h_thunderbolt`: fulgurite hung over a leaf and a log in the zone, which must
still burn).

The bolt goes up as soon as the crater is open, before the burn has reached the ground under its forks, so every scan
down a column (`scorch`, `channel`) looks straight through fulgurite to the ground under it. `fullStroke` failing now and
then with leaves or planks left in the zone is that, coming back.

## Previewing without the game

The sandbox has no Minecraft, but the shaders can be compiled and drawn in headless Chromium's WebGL2 (GLSL 150 →
`#version 300 es` plus precision lines), with Playwright from node; and `Lichtenberg` compiles on its own with `javac`
to dump and plot a figure. That is how the vortex, the storm Earth and the scar were looked at before CI.
