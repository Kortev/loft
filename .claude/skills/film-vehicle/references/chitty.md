# Chitty Chitty Bang Bang: full map

GEN 11 from the 1968 film. She drives, flies on pleated wings, floats on a pink raft, has four seats, an ejector, a
hamper and a horn. Her README (`chitty/README.md`) is the agreed description of how she looks and behaves. Keep it in
step.

## Files

She is a mod of her own (id `chitty`, jar `chitty-chitty-bang-bang`), in the `chitty/` subproject. Main code is
`chitty/src/main/java/io/github/kortev/chitty/`, client code `chitty/src/client/java/io/github/kortev/chitty/client/`,
and her assets and data are under `chitty/src/{main,client}/resources/`, still in the `shootingstar` namespace.

| What | Where |
|---|---|
| Registration (entity types, item, sounds, C2S payloads) and the mod's main entry point | `Chitty.java` |
| The car (1400 lines: physics, seats, wings, raft, ejector, hamper, NBT) | `ChittyEntity.java` |
| Extra hitboxes FRONT / BACK / HAMPER | `ChittyPartEntity.java` |
| Driver input (forward, turn, up, down, packed in a byte) | `ChittyControls.java` |
| Payloads | `ChittyInputPayload` (controls, state), `ChittyHornPayload`, `ChittyEjectPayload` |
| Placing item | `ChittyItem.java` (raycast with fluids; on water she starts with her raft up) |
| Client entry point, keys, input sending, hints | `client/ChittyClient.java` |
| Mesh loader (CBM2) | `client/ChittyMesh.java` (`meshes/chitty.cbm`) |
| Renderer (posing every part) | `client/ChittyRenderer.java` |
| Live metal reflections | `client/ChittyShine.java` |
| Engine and flight loops | `client/ChittySound.java` |
| Pixelated atlas (faceted look) | `client/ChittyTexture.java` (`pixelated` true, as the airship's) |
| Third-person distance ×2 | `client/mixin/ChittyCameraMixin.java` (config `chitty.client.mixins.json`) |
| First-person bank | `client/mixin/ChittyViewMixin.java` |
| Manifest | `chitty/src/main/resources/fabric.mod.json` (depends on `shootingstar`) |
| Lang, sounds list | her own `assets/shootingstar/lang/en_us.json` and `sounds.json` (the game merges them with The Shooting Star's) |
| Model, bake, icon, renders | `tools/chitty_model.py` (≈2400 lines) |
| Sounds | `tools/gen_chitty_sounds.py` |
| Baked outputs | `chitty/src/client/resources/assets/shootingstar/meshes/chitty.cbm`, `.../textures/entity/chitty.png`, `chitty/src/main/resources/assets/shootingstar/textures/item/chitty.png` |
| Recipe, unlock | `data/shootingstar/recipe/chitty.json` (elytra, gold, minecart, pistons, boat); `advancement/recipes/transportation/chitty.json` |
| Advancements | `advancement/chitty/`: `root`, `start`, then `fly` / `float`; `clouds` after `fly` |
| Tests | in the shared harness at the root: `src/gametest/java/.../test/ChittyGameTests.java` (batch `c_chitty`), `ChittySelfTest.java` (filmed) |
| README | `chitty/README.md` |

## Entity constants (`ChittyEntity`)

- **Speeds** (blocks/tick):
  - `ROAD_TOP` 0.75, `ROAD_ACCEL` 0.012, `BRAKE` 0.035, `REVERSE_TOP` 0.18;
  - `WATER_TOP` 0.42, `WATER_ACCEL` 0.008;
  - `AIR_TOP` 1.3, `AIR_ACCEL` 0.014, `AIR_MIN` 0.28 (below it the wings stop holding her);
  - `TAKEOFF` 0.45, `GRAVITY` 0.08.
- **Shape:** `NOSE` 1.5, `TAIL` 1.75 (from the middle), `TILT_PIVOT` 0.8, `EXHAUST` (-0.83, 0.63, -1.02).
  Entity box 2.0 × 1.6.
- **Seats** `SEATS[]`:
  - the driver (right) (-0.30, 0.75, -0.10);
  - the front passenger (0.30, 0.75, -0.10);
  - the back pair (∓0.20, 0.75, -1.30).

  `DRIVER` 0, `BACK_SEAT` 2. Fill order: `FRONT_FIRST` for players, `BACK_FIRST` for mobs.
- **Timings:**
  - `FLOAT_DELAY` 40 / `FLOAT_DELAY_ALONE` 60 (ticks wading before the raft);
  - `BEACHED` 20;
  - `WINGS_BITE` 36 (ticks until the wings bear her);
  - `RESCUE_FALL` 5 blocks, `SWOOP` 14 (the self-rescue from a fall);
  - `START_BANG_1/2` 15 / 20 (a catching crank runs after the second); `START_FAIL` 32, `START_COUGH_1/2` 9 / 16 (a
    crank that doesn't); `CATCHES` 0.7 (the third swing always catches);
  - `IDLE_RPM` 440, `REV_RPM` 2700, `STANDING` 0.05 (slower than this she can be revved);
  - the springs: `SQUAT` 140 and `LEAN` 105 degrees per block/tick² of pull, at most `MAX_SWAY` 4.5;
  - `WHEEL_TRACK` 0.70 / `REAR_WHEELS` -1.70 (where the tyres smoke and throw dust), `BONNET_TOP/BACK/LENGTH` (where it
    shimmers);
  - `CLOUDS` y 192 (the clouds advancement);
  - `EJECT_STATUS` 90 (entity status for the ejector), `EJECT_SETTLED` 60.
- **State bits** (tracked `STATE`): `STATE_WINGS` 1, `STATE_WINGS_HELD` 2 (opened by hand, they stay out on the
  ground), `STATE_FLOATS` 4.
- **Tracked data:** `STATE`, `SEATING` (two bits per passenger), `HAMPER`, `STEER`, `THROTTLE`, `ENGINE`
  (`ENGINE_OFF` / `CRANKING` / `RUNNING`, the server's `startUp()`), `REV`, and the damage wobble trio.
- **Starting:** a player taking the driver's seat calls `crank()`; it catches or not (`START` / `START_FAIL`); stalled
  on the ground, the driver's forward pedal cranks again; in the air she catches at once. `isEngineRunning()` needs
  `ENGINE_RUNNING` and a driver; until then `drive()` ignores the pedals. `crank(boolean)` for tests,
  `alwaysCatches` for the filmed self test.
- **Revving:** `ChittyControls.rev` (V); the server sets `REV` while standing and out of gear, and lets go of it after
  more than 6 ticks with a backfire half the time.
- **Modes:** `ROAD`, `WATER`, `WADE`, `AIR`, `FALL`, in `drive()`.
  - Nose-into-wall stop: `noseHits`.
  - Climbing banks: `bankAhead`.
  - Self-rescue: `aboutToHit`.
- **Client-tick getters** for the renderer, all interpolated: `getWingOpen`, `getFloatOpen`, `getWheelSpin`,
  `getPropSpin`, `getScrewSpin`, `getGearLever`, `getBrakeLever`, `getCrankSpin`, `getDial(0 speed, 1 height,
  2 revs)`, `getEjectLift`, `getSteer`, `getBank`, `getTilt`; and her body on its springs, `getBodyPitch`,
  `getBodyRoll`, `getBodyHeave` (`suspension()`: three damped `Spring`s), with `getFirings` and `getShake` for the
  idle shake (the renderer's `spring()` kicks at each pair of firings).
- **Client-tick looks:** `getRpm()` (the engine sound and the rev needle use it), `tyres()` (slip on paving smokes,
  `WHITE_SMOKE`, and squeals, `getSqueal()` for `ChittySound`'s `SKID` layer; off paving, block and `DUST_PLUME`
  particles), `bonnetHeat()` (`Chitty.HEAT` particles, `ChittyHeatParticle`).
- **Behaviours:**
  - `ejectBackSeat()`: launches the back seat's riders, with slow falling for players.
  - `toggleHamper()`: sneak-use takes it off and spills it stack by stack behind her.
  - `honk()`.
  - `bangBang()`: a backfire with flame and smoke, also when the throttle comes off at speed.
  - `launch(speed, wings)`: for tests.

## Mesh parts (names the renderer knows)

- **`body` and `glass`:** the static parts.
- **Wheels:** `wheel_fl`, `wheel_fr`, `wheel_rl`, `wheel_rr`. They roll; the fronts steer; all turn flat on the raft.
- **Wings:** `wing_l_0..7`, `wing_r_0..7` (pleated fan panels; `a..d` = open angle, dihedral, folded angle,
  draw-in). The masts `mast_l/r` hang off panel 0, with the rotors `rotor_l/r` on them.
- **Nose and tail fans:** `nosefan_c_0..3` and `tailfan_c_0..4`, posed like the wings (`poseFan`). `tailprop` is
  the pusher propeller.
- **Raft:** `float` (it inflates by scale) and `screw`.
- **Controls:** `steering_wheel`, `lever_gear`, `lever_brake`, `crank`.
- **Dials:** `needle_speed`, `needle_height`, `needle_revs`.
- **Ejector:** `seat_rear` and `spring_l/r`.
- **`hamper`:** hidden when she has none.
- **Markers:** `seat_driver`, `seat_front_passenger`, `seat_rear_left`, `seat_rear_right`, `exhaust`,
  `beam_lamp_l/r`, `beam_spot_l/r` (unused since her lamp beams went).

The renderer has `PLEAT_CLOSED` 0.07, `WING_DROP` 0.15 and `TAIL_DROP` 0.04. These must match `WING`, `NOSEFAN` and
`TAILFAN` in the model script.

## Model script options

- `--game`: bake and export (about 6 min). Bake env: `CHITTY_BAKE_SAMPLES` (96), `CHITTY_UV_MARGIN`,
  `CHITTY_UV_SHAPE`.
- `--out DIR`: writes `.blend` and per-pose `.glb`.
- `--renders [--only a,b]`: the render shots, at `CHITTY_SAMPLES` (96 by default; 24 is fine for looks). The shots
  are:
  - on the road: `road_front`, `road_rear`, `road_side`, `road_left`, `rear_top`, `front`, `cockpit`, `door_right`,
    `door_left`, `spare`, `dash`;
  - flying: `flying`, `flying_below`, `flying_rear`, `flying_front`;
  - the nose: `nose_photo`, `nose_left`, `nose_plan`;
  - on the water: `water`, `water_top`;
  - the fans opening: `unfold_30`, `unfold_60`, `wing_folded`, `nose_folded`, `nose_unfold`, `tail_folded`,
    `tail_unfold`;
  - `eject`.

  Mode strings: `road@0.3` means the wings 30% out, and `road^0.75` means the back seat thrown 0.75 up.
- Materials that the game shines (`SHINE`): aluminium 1, brass 2, chrome 3, copper 4, aluminium_dull 5.
  - `GLOW`: `bulb_glow`, `eye`.
  - `GAME_LIT`: `wheel_` (moot in her faceted game build: `--game` sets `LIT_ALL`, so the game lights every part).
- **Faceted (`FACET`, set in `main()` by `--game` or `--facet`, never on import):** `sides(r)` gives a round part 4/6/8/12
  flat sides with a flat on top; `res(n)` takes a sixth the curve steps; `tube` makes square or eight-sided bars of at
  least `THINNEST` (`facet_bar`), broken at corners over 35°; bevels are one chamfer; faces are flat. `--game` also bakes
  a 1024 colours-only atlas (`LIT_ALL`, `PACK_ROTATE = 'AXIS_ALIGNED'`, `--atlas N` to change it). kortev chose it from
  comparison renders (the game's look: `cbm_preview`-style, flat faces lit as the game lights them).
  - `GLASS_TINT`: per-vertex tint and alpha.

## Sounds

Events are registered in `Chitty.java` as `chitty.<name>` (file `chitty_<name>.ogg`):

- the loops `engine_idle` / `engine_low` / `engine_high`, made at 440 / 1100 / 2200 rpm (`ChittySound.IDLE_RPM`…);
- `flight` (props and wind as the wings open), `wind` (speed through the air);
- `start`, `bang`, `horn`;
- `wings_out` / `wings_in`, `floats` / `floats_down`;
- `eject`, `crash`.

All are mono. Vanilla splash on entering water. **No ground step sounds** (removed: they clanked).

## Keys

All three are rebindable, in `key.categories.shootingstar`:

- **G** wings (`key.shootingstar.chitty_wings`);
- **H** horn;
- **X** ejector (driver only).

Jump climbs (or takes off at speed with the wings out); sprint dives.
