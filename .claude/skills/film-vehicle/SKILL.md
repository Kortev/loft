---
name: film-vehicle
description: How kortev's mods build film-accurate rideable vehicles in the Chitty Chitty Bang Bang mod (chitty/, its own jar beside The Shooting Star; Chitty is the template) - a Blender model built by a Python script and baked into a game mesh and texture, a boat-style client-driven entity with seats, extra hitboxes and animated parts, synthesised sounds, keys, camera tweaks, game tests. Use when building or changing any vehicle (Chitty, the Vulgarian airship, the Child Catcher's carriage, or a new one).
---

# Film vehicles

A vehicle here is a **film prop you can ride**:

- modelled in Blender **by a script** from photographs;
- baked into one mesh file and one texture atlas;
- drawn part by part, so that everything that moves in the film moves in game;
- driven like a boat (the driver's client moves it);
- with sounds synthesised from a model of the real thing.

**Chitty** is the finished template. Read `references/chitty.md` for her full map before starting. The **Vulgarian
airship** is the second (`references/airship.md`): she shares Chitty's mesh format, exporter and loader. The third,
the **Child Catcher's carriage** (`references/carriage.md`), is drawn by a real vanilla horse hitched into her shafts.

## Where vehicles live

- The film's vehicles are one mod, **Chitty Chitty Bang Bang** (id `chitty`), in the `chitty/` Gradle subproject. It
  builds its own jar (`chitty/build/libs/chitty-chitty-bang-bang-*.jar`) beside The Shooting Star's, from the same
  `./gradlew build`, and needs The Shooting Star installed.
- Code: `chitty/src/main/java/io/github/kortev/chitty/` (one subpackage per new vehicle, e.g. `chitty.airship`) and
  `chitty/src/client/java/io/github/kortev/chitty/client/`. Mixins go in `chitty.client.mixin`, listed in
  `chitty.client.mixins.json`, with handlers prefixed `chitty$`.
- Assets and data: `chitty/src/{main,client}/resources/`, **in the `shootingstar` namespace** (`ShootingStar.id`):
  that is what kept Chittys already in worlds alive through the move, and what puts every vehicle under
  `OwnerOnly`. Her `lang/en_us.json` and `sounds.json` are her own; the game merges them with The Shooting Star's.
- Shared with The Shooting Star (in the root project): `ShootingStar.id`, `OwnerOnly`, `ModCriteria` (the
  `shootingstar:event` trigger), the key category `key.categories.shootingstar`, and the game-test harness.

## The order kortev expects (do not skip the renders)

1. **References.** Film stills and photos, plus kortev's Notion page "claude chitty chitty mod" (the Notion tools
   can fetch it). Write down the proportions in blocks before modelling (Chitty is about 6 blocks long).
2. **Model script** `tools/<vehicle>_model.py` (Blender `bpy`), built like `tools/chitty_model.py` (below).
3. **Renders → kortev.**
   - Run `--out DIR --renders --only a,b,c` with `CHITTY_SAMPLES=24` (or the new script's own variable) for quick
     looks.
   - Send the PNGs (SendUserFile) and wait for their OK. Fix what they say and render again.
   - They expect this: baking unapproved shapes costs a round and their patience.
4. **Entity and behaviour** (server and client), then **bake** (`--game`, about 6 min) and commit the mesh, texture
   and icon.
5. **Sounds** (`tools/gen_<vehicle>_sounds.py`), registration, `sounds.json`, subtitles.
6. **Item, recipe, advancements, lang, README.**
7. **Game tests**; push; CI green.
8. A **numbered in-game test list** for kortev.

## The model script (copy the shape of `tools/chitty_model.py`)

- **Setup:** Python 3.11 venv: `python3.11 -m venv ~/.bpy && ~/.bpy/bin/pip install "bpy==4.5.*" numpy pillow`.
  Run from the repo root: `~/.bpy/bin/python tools/<x>_model.py …`.
- **Axes:**
  - Blender units are blocks. The vehicle faces +Y, +X is its right, +Z is up, and the wheels sit on Z = 0.
  - The game's axes are x to its **left**, y up, z forward: `to_mc(v) = (-x, z, y)`, and
    `quat_to_mc(q) = (-q.x, q.z, q.y, q.w)`.
- **Layout first:** named constants at the top (wheel radius, track, axles, hull sections as tables of
  `(y, half-width, top, bottom)`), so a fix is a number change.
- **Parts:**
  - Every part that moves in game is its own object, **origin on its pivot**, with a custom property `part` holding
    its game name.
  - Static geometry is `part = 'body'` (merged into one part); see-through is `'glass'`.
  - Seats, the exhaust, lamps and similar are **empties** with `part = 'marker'`. They're exported as named points,
    so code never hard-codes a position the model owns. (Chitty's `SEATS` predate this; prefer markers.)
- **Builders:**
  - One `build_*()` per assembly.
  - Helpers you can reuse: `material`, `loft` (sections → skin), `lathe`, `tube` (along a Catmull path),
    `add_box`, `ellipsoid`, `bevel`, `solidify`, `text_object`, `empty`.
  - Procedural textures with PIL (`plank_texture`, `wicker_texture`, …).
- **`pose(mode, …)`** poses the Blender scene for renders (road / flying / water, wing fraction, seat lift). Render
  shots are tuples `(name, mode, camera, look_at, lens, lift, water)` in `main()`.
- **`--game` → `export_game()`:**
  - Unwraps every part into one atlas (`TEXEL_WEIGHT` gives detail to what is seen close up).
  - Bakes light with Cycles: a sky world, plus an even world for `GAME_LIT` parts.
  - Writes `chitty/src/client/resources/assets/shootingstar/meshes/<x>.cbm`,
    `chitty/src/client/resources/assets/shootingstar/textures/entity/<x>.png` and the 32×32 item icon
    `chitty/src/main/resources/assets/shootingstar/textures/item/<x>.png` (`render_icon`).
- **The `.cbm` (CBM2) format** is documented in `export_game`'s docstring:
  - per part: name, pivot, rest quaternion, four animation floats `a..d`, then quads of (x, y, z, u, v, rgba,
    normal, flags);
  - the flags: bit 0 glow; bits 1–3 the polished-metal index (`SHINE`);
  - then the markers.

  For a new vehicle, **reuse the format and the loader**. Make `ChittyMesh` load any `.cbm` by id (or extract a
  shared `VehicleMesh`) instead of copying it.
- **Also reuse rather than copy** where it's generic: import the helpers from `chitty_model` (guard its `main()`),
  or move them into `tools/vehicle_lib.py` in the same commit that first needs them.

## The entity (copy the shape of `ChittyEntity`)

- **Registration** (`Chitty.java`):
  - `EntityType` (`SpawnGroup.MISC`, `maxTrackingRange(10)`, `trackingTickInterval(1)`) plus a part type
    (`disableSaving`, `disableSummon`) if the vehicle is longer than about 2 blocks;
  - the item (`maxCount(1)`) and its creative tab;
  - its `SoundEvent`s, registered right there;
  - its C2S payloads with server receivers that check the sender is riding (and driving, where it matters).

  `Chitty` is the mod's main entry point (`onInitialize`). A new vehicle's registration class gets a static `init()`
  called from there.
- **`extends Entity`** (not a mob):
  - `DataTracker` for what other clients must see (state bits, seating, throttle, steer, damage wobble);
  - `readCustomDataFromNbt` / `writeCustomDataToNbt` for what is saved;
  - `getPickBlockStack`; damage like a boat (wobble, then break into the item; creative breaks at once).
- **Driving (client-authoritative, like a boat):**
  - In `tick()`, `isLogicalSideForUpdatingMovement()` means "I move it". That side runs `drive(controls)` and the
    others `lerp()` (override `updateTrackedPositionAndAngles` and the `getLerpTarget*` methods).
  - The driver's client reads its keys through `ChittyEntity.client` (`ClientHooks`, set by `ChittyClient`, because
    main code can't see client classes). It sends `ChittyInputPayload(controls, state)` when they change, and the
    server's `applyInput` stores them.
  - With nobody driving, the server moves it (falling, floating, coasting).
  - `adopt()` takes over smoothly when the mover changes.
- **Speeds** are named constants in blocks per tick (`ROAD_TOP`, `AIR_TOP`, `TAKEOFF`, `GRAVITY`…). Behaviour is a
  `Mode` enum (`ROAD`, `WATER`, `WADE`, `AIR`, `FALL`) chosen each tick.
- **Seats:**
  - `addPassenger` / `removePassenger` with `SEATING` (two bits a seat) and `getPassengerAttachmentPos` from the seat
    table.
  - Click the nearest free seat (`interactAt` → `nearestFreeSeat`).
  - `getControllingPassenger` is whoever is in the driver's seat.
  - `updatePassengerPosition` turns passengers with the vehicle (yaw, and pitch delta "nod"); `clampPassengerYaw`
    limits the head; `updatePassengerForDismount` lets them out at a safe side.
- **Long vehicles:**
  - An entity's box is a square about its middle, so `ChittyPartEntity`s (FRONT / BACK / HAMPER) keep to her each
    tick.
  - They're solid, using one uses her at that spot, hitting one hits her, and they're re-spawned on load, never
    saved.
- **Advancements:** `award("chitty_fly")` fires `ModCriteria.fire` for every player aboard. The jsons are in
  `data/shootingstar/advancement/<vehicle>/` (with its own root).

## The client

- **`ChittyClient.onInitializeClient()`** (the mod's client entry point; a new vehicle's client `init()` is called
  from there):
  - `EntityRendererRegistry` (the parts get `EmptyEntityRenderer`);
  - keys (`KeyBindingHelper`, category `key.categories.shootingstar`);
  - a resource reload listener that reloads the mesh;
  - the `ClientHooks`;
  - the client tick (sounds, sending input, action-bar hints).
- **Renderer:**
  - Rotate by yaw, then tilt/bank about a pivot.
  - Draw `body` once, then each part: push, `translate(pivot)`, `multiply(rest)`, its animation, `draw`, pop.
  - Animation amounts come from tick-interpolated getters on the entity (`getWingOpen(tickDelta)`,
    `getWheelSpin`, …), which advance in the client tick.
  - Use the `entityCutoutNoCull` layer for the atlas and `entityTranslucent` for glass.
- **Lighting:**
  - Baked parts are drawn evenly lit (normals up) because the light is in the texture.
  - `GAME_LIT` parts (wheels) keep normals.
  - Polished metal is shaded live by `ChittyShine`.
  - `glow` vertices draw at full light.
- **Sounds:**
  - Running loops are `MovingSoundInstance`s that follow the vehicle (`ChittySound`): several loops made at set
    revs, crossfaded by revs, pitched near what they were made at.
  - One-shots are played at the entity.
  - Everything placed is **mono**.
- **Camera/view mixins:**
  - `ChittyCameraMixin`: the third-person distance is doubled for a big vehicle.
  - `ChittyViewMixin`: the first-person view banks with her (roll at `tiltViewWhenHurt` HEAD).
- **Extras:** `ChittyTexture` (smooth filtering and mipmaps for a baked
  atlas, registered under the renderer's `TEXTURE`).

## Sounds script (copy `tools/gen_chitty_sounds.py`)

- It imports the building blocks from `gen_sounds.py` (filters, noise, `reverb`, `spaces`, `loudness`, `limit`) and
  seeds each sound by `zlib.crc32(name)`.
- **Model the source** rather than imitate it. Chitty's engine is firings through a header into a booming pipe,
  fired in uneven pairs for the "chit-ty" rhythm. For the airship: slow props, a big gas envelope creaking,
  winch ratchets. For the carriage: hooves at trot and gallop rhythms, iron tyres, cage chains.
- **Loops** must wrap exactly: a whole number of cycles, with firings and filters periodic (`periodic`,
  `loop_noise`).
- Place one-shots' events where the entity does them (Chitty's bangs land on `START_BANG_1/2` ticks).
- `python3 tools/gen_<x>_sounds.py [names]` writes `.ogg` into `chitty/src/main/resources/assets/shootingstar/sounds/`
  (set `g.OUT` as `gen_chitty_sounds.py` does).
  Add each to the mod's `sounds.json` with a `subtitle`, and add `subtitles.shootingstar.<x>.<name>` to its lang.

## Lessons from Chitty (each cost a round)

- **Film accuracy is the bar.** Count parts in the stills (wing sections, wheel spokes, plate text). kortev will
  compare.
- **Check intersections in renders** from several angles, close up, before baking: parts poking through
  (pipes through the horn, dials behind the bulkhead, the snake through the pipes).
- **Animations:**
  - Ease them with smoothstep. `backOut` overshoot made the pleats spread apart.
  - A folding cloth stays one piece hinged along the body, unfolding in vertical pleats; it never swings out and
    folds back in.
- **Hitboxes** must cover the whole visible vehicle (part entities), or you can't click the hamper or nose.
- **No vanilla step sounds:** `Entity` plays the ground's step sound as it moves, and it read as horrible clanking.
  Leave it out (and for a carriage, make hoof sounds of your own).
- **Riding feel:**
  - Passengers turn with the vehicle.
  - Their pitch follows its pitch.
  - The view banks in the air.
  - No fall damage for anyone aboard (`handleFallDamage` → false).
- **Mobs as passengers:**
  - `/ride <mob> mount <vehicle>` puts a mob in. The back seats take mobs first.
  - Don't NoAI test mobs if you need them to be thrown or moved.
- **Messages to kortev:** short, renders attached, then a test list.

## Tests (`src/gametest/.../ChittyGameTests.java`, batch `c_chitty`)

- **Patterns:** a flat platform built in code (`EMPTY_STRUCTURE`); husks as riders (the sun can't burn them);
  `car.launch(speed, wings)` to set it moving; checks with `context.assertTrue` in `context.runAtTick(t, …)` / `runAtEveryTick`, ending in
  `context.complete()`.
- **Write one test per behaviour.** Chitty has `floats`, `landsSafely`, `washesOut`, `catchesHerself`,
  `heldOpen`, `climbsOutOfWater`, `ejects`, `chooseSeat`, `hitboxes`, `hamper`, `noFallDamage`, `stopsAtWall`,
  `seatsFour`, `breaksIntoItem` and `onlyTheOwnerCrafts`.
- The tests live in the one harness at the root (`src/gametest/`), which loads every mod in the build. New
  vehicles get their own class and batch there, registered in `src/gametest/resources/fabric.mod.json`.
- `DataGameTests.everyAdvancementLoads` checks every advancement in the namespace, whichever jar it is in.
- CI's `build` job also runs `.github/scripts/check-jars.py` on the built jars: add a new vehicle's key files to the
  Chitty jar's list there.
- **The filmed self test** (`ChittySelfTest`, selftest workflow `test=chitty`) drives a whole scripted run and
  records video. Run it only when kortev asks.
