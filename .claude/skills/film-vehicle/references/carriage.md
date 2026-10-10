# The Child Catcher's carriage: full map

The Child Catcher's carriage from the film. Her README section (`chitty/README.md`, "The Child Catcher's carriage") is
the agreed description of how she looks and behaves. Keep it in step.

## Files

She is part of the Chitty mod (`chitty/`), in the `io.github.kortev.chitty.carriage` package (main) and beside Chitty's
classes in `io.github.kortev.chitty.client` (client). Her assets and data are in the `shootingstar` namespace.

| What | Where |
|---|---|
| Registration (entity types, item, sounds, the C2S payload); the horse, cage and shove rules (`ALLOW_DAMAGE`, `UseEntityCallback` on a hitched horse, `AttackEntityCallback` at an open door); `init()` from `Chitty.onInitialize` | `carriage/Carriage.java` |
| The carriage (driving, places, the horse, the cage and its door, the disguise, the whip, NBT) | `carriage/CarriageEntity.java` |
| Hitboxes: the driver's box (`FRONT`) and the back of the cage (`BACK`) | `carriage/CarriagePartEntity.java` |
| Placing item | `carriage/CarriageItem.java` |
| Payload | `CarriageActionPayload` (`ACTION_WHIP`, `ACTION_DISGUISE`). No input payload: the driver's client moves her and nothing on the server needs the reins |
| A horse knows its carriage | `carriage/CarriageHitch` (duck interface), `mixin/CarriageHitchMixin` (on `AbstractHorseEntity`) |
| The hitched horse (main mixins) | `mixin/CarriageHorseMixin` (`LivingEntity`: at the head of `tickMovement` it puts itself in the shafts, `CarriageEntity.holdHorse`; `travel` is replaced by `updateLimbs(carriage.getStride())`; a client ignores the server's position and head turn for it), `mixin/CarriageHorseAiMixin` (`MobEntity.tickNewAi` skipped: no goals, nothing to restore, unlike NoAI) |
| Getting out of the cage | `mixin/AirshipDismountMixin` asks `CarriageEntity.letsOut` |
| Client: renderer, harness, keys, sound | `client/CarriageRenderer` (parts posed; traces and reins as stretched one-block `trace`/`rein` parts from points on the horse's model, `horsePoint`; the thrown disguise drawn where she was), `client/CarriageHarnessFeature` (a feature on every `AbstractHorseEntityRenderer`, boxes copying the transforms of `HorseEntityModel`'s `body` and `head` through `client/mixin/HorseModelAccess`; texture `carriage_harness.png` in leather, brass and plume bands), `client/CarriageClient` (`init()` from `ChittyClient`; **J** disguise, jump = whip on its rising edge), `client/CarriageSound` |
| Client mixins | `AirshipStandMixin` (the cage's prisoners drawn standing), `ChittyCameraMixin` (third person 2.2 times as far back) |
| Model, bake, icon, renders | `tools/carriage_model.py` (`--game`: faceted, `export_game` with a 1024 atlas, `LIT_ALL`, `PACK_ROTATE` axis-aligned, `UV_CORRECT_ASPECT` off so the signs on long strips of image are not squashed; `build_straps` adds `trace` and `rein`; renders with `--out DIR --renders`, `MC_HORSE_TEXTURE` for the preview horse's coat) |
| Sounds | `tools/gen_carriage_sounds.py` (`roll` loop, `whip`, `disguise_on`, `disguise_off`); the horse's hooves are vanilla's, played by the carriage (`hooves`), the door is vanilla's iron door and chain |
| Baked outputs | `chitty/src/client/resources/assets/shootingstar/meshes/carriage.cbm`, `.../textures/entity/carriage.png`, `chitty/src/main/resources/assets/shootingstar/textures/item/carriage.png` |
| Recipe, unlock | `recipe/carriage.json` (iron bars, a lead, planks, a minecart), `advancement/recipes/transportation/carriage.json` |
| Advancements | `advancement/carriage/`: `root`, `hitch`, then `catch` (shut the door on someone) and `unmask` (the whip in disguise) |
| Tests | `src/gametest/.../CarriageGameTests.java`, a batch per test (`c_carriage_*`) |

## Numbers (`CarriageEntity`)

- **Layout** (blocks, x to her left, z forward; Blender's (x, y, z) is our (-x, z, y)): deck top `DECK_TOP` 1.20; cage
  `CAGE_FRONT` 1.00 to `CAGE_BACK` -1.62, half width 0.875, 2 high; door half width 0.39, hinge `HINGE`; let out at
  `DOOR_OUT` (0, 0, -2.48). `PLACES`: the driver (-0.22, 1.57, 1.34), the box seat (0.32, 1.57, 1.34), four in the cage
  standing at (±0.42, 1.20, 0.55 / -0.95). Axles -0.98 and 0.95, wheel radii 0.52 and 0.43. The horse stands
  `HORSE_AHEAD` 3.08 ahead, turned with the fore-carriage about the front axle by `steer * STEER_MAX` (22 degrees).
- **Speeds** (blocks/tick): top = the horse's movement speed attribute × `PULL` 1.35 (a vanilla horse's 0.225 gives
  0.30), × `ROAD` 1.2 on a road, × `GALLOP` 1.4 for `GALLOP_TICKS` 70 after a crack of the whip; `ACCEL` 0.006,
  `BRAKE` 0.025, `REVERSE_TOP` 0.06, `WADE_TOP` 0.08; turning `TURN` 3.5 degrees a tick, scaled down below 0.06.
- **Gaits** for the hooves: walk, trot from `TROT` 0.14, gallop from `CANTER` 0.3.
- **The cage:** fits anything up to 1.0 wide and 2.1 high; shoved in from within `DOOR_REACH` 1.4 of the door; mobs
  make a run for it 10 to 40 ticks apart when the door opens.

## How the horse keeps up

Vanilla animates a mob's legs from how far it moved in its own tick, and a passenger's never move; and a client would
draw a server-placed horse a tick or more behind a carriage its driver moves. So the horse is not a passenger: it puts
itself in the shafts at the head of its own `tickMovement` (and she puts it there after she moves, whichever comes
first), on every side, from her position now and a tick ago (`horseAt`, `prevHorseAt`: its `prevX` and `lastRenderX`
are set too, so it is drawn moving smoothly between them, whichever ticks first). Its height eases onto the ground
ahead of her, a block up or down at most.
