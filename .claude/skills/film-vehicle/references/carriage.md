# The Child Catcher's carriage: full map

The Child Catcher's carriage from the film. Her README section (`chitty/README.md`, "The Child Catcher's carriage") is
the agreed description of how she looks and behaves. Keep it in step.

## Files

She is part of the Chitty mod (`chitty/`), in the `io.github.kortev.chitty.carriage` package (main) and beside Chitty's
classes in `io.github.kortev.chitty.client` (client). Her assets and data are in the `shootingstar` namespace.

| What | Where |
|---|---|
| Registration (entity types, item, sounds, the C2S payload); the cage and shove rules (`ALLOW_DAMAGE`, `AttackEntityCallback` at an open door); `init()` from `Chitty.onInitialize` | `carriage/Carriage.java` |
| The carriage (driving, places, the cage and its door, the disguise and its bait, the trap, the villager lure, the whip, her horse's place and hooves, NBT) | `carriage/CarriageEntity.java` |
| Hitboxes: the driver's box (`FRONT`), the back of the cage (`BACK`) and her horse (`HORSE`, turning with the shafts; hitting it is `damageHorse`) | `carriage/CarriagePartEntity.java` |
| Placing item | `carriage/CarriageItem.java` |
| Payload | `CarriageActionPayload` (`ACTION_WHIP`, `ACTION_DISGUISE`). No input payload: the driver's client moves her and nothing on the server needs the reins |
| Getting out of the cage | `mixin/AirshipDismountMixin` asks `CarriageEntity.letsOut` |
| Client: renderer, horse, keys, sound | `client/CarriageRenderer` (parts posed; `disguise_b`, the door's cloth and counter, swings with the door, `swingDoor`; the bait drawn on the counter as ground items; traces and reins as stretched one-block `trace`/`rein` parts from points on the horse's model, `horsePoint`; the thrown disguise drawn where she was), `client/CarriageHorse` (the vanilla `EntityModelLayers.HORSE` model in the black coat, posed as `HorseEntityModel.animateModel` poses a horse from `getLimbPos`/`getLimbSpeed`, and her `HARNESS` layer on it, `carriage_harness.png` in leather, brass and plume bands), `client/CarriageClient` (`init()` from `ChittyClient`; **J** disguise, jump = whip on its rising edge), `client/CarriageSound` |
| Client mixins | `AirshipStandMixin` (the cage's prisoners drawn standing), `CarriageLabelMixin` (no names over a disguised cage's prisoners, `CarriageEntity.hidden`), `ChittyCameraMixin` (third person 2.2 times as far back) |
| Model, bake, icon, renders | `tools/carriage_model.py` (`--game`: faceted, `export_game` with a 1024 atlas, `LIT_ALL`, `PACK_ROTATE` axis-aligned, `UV_CORRECT_ASPECT` off and `square_aspect` so that pictures on long strips of image are not squashed; `build_straps` adds `trace` and `rein`; the disguise is pixel art at `PX` 32 to the block in the wandering trader's palette, `TRADER`; renders with `--out DIR --renders`, `MC_HORSE_TEXTURE` for the preview horse's coat) |
| Sounds | `tools/gen_carriage_sounds.py` (`roll` loop, `whip`, `disguise_on`, `disguise_off`); the horse's hooves, snorts and squeal are vanilla's, played by the carriage (`hooves`), the door is vanilla's iron door and chain |
| Baked outputs | `chitty/src/client/resources/assets/shootingstar/meshes/carriage.cbm`, `.../textures/entity/carriage.png`, `chitty/src/main/resources/assets/shootingstar/textures/item/carriage.png` |
| Recipe, unlock | `recipe/carriage.json` (iron bars, a lead, planks, a minecart), `advancement/recipes/transportation/carriage.json` |
| Advancements | `advancement/carriage/`: `root`, then `catch` (shut the door on someone), `trap` (someone caught by the bait) and `unmask` (the whip in disguise) |
| Tests | `src/gametest/.../CarriageGameTests.java`, a batch per test (`c_carriage_*`) |

## Numbers (`CarriageEntity`)

- **Layout** (blocks, x to her left, z forward; Blender's (x, y, z) is our (-x, z, y)): deck top `DECK_TOP` 1.20; cage
  `CAGE_FRONT` 1.00 to `CAGE_BACK` -1.62, half width 0.875, 2 high; door half width 0.39, hinge `HINGE`; let out at
  `DOOR_OUT` (0, 0, -2.48). `PLACES`: the driver (-0.22, 1.57, 1.34), the box seat (0.32, 1.57, 1.34), four in the cage
  standing at (±0.42, 1.20, 0.55 / -0.95). Axles -0.98 and 0.95, wheel radii 0.52 and 0.43. The horse stands
  `HORSE_AHEAD` 3.08 ahead, turned with the fore-carriage about the front axle by `steer * STEER_MAX` (22 degrees).
- **Speeds** (blocks/tick): `TOP` 0.40 (about a ridden horse's), × `ROAD` 1.4 on a road (faster than one), × `GALLOP`
  1.15 for `GALLOP_TICKS` 70 after a crack of the whip; `ACCEL` 0.007, `BRAKE` 0.03, `REVERSE_TOP` 0.06, `WADE_TOP`
  0.08; turning `TURN` 3.5 degrees a tick, scaled down below 0.06 and by up to 30% at her fastest.
- **Gaits** for the hooves: walk, trot from `TROT` 0.14, gallop from `CANTER` 0.36.
- **The cage:** fits anything up to 1.0 wide and 2.1 high; shoved in from within `DOOR_REACH` 1.4 of the door; mobs
  make a run for it 10 to 40 ticks apart when the door opens.

## Her horse, and why it is hers

kortev first chose a real horse brought along and hitched with a lead, then left it to us ("cool but might be kinda
annoying"). A hitched mob could stray, die on its own, or be left behind: the game takes a vehicle away with a driver
who logs off, but not a horse that is merely next to it, nor one in a chunk that unloaded. Keeping it in the shafts
also took mixins on every horse's movement and AI, and a client-side placement. So the horse is part of the carriage:
`CarriageEntity` keeps where it stands (`horseLocal`, turned with the shafts by `steer`, lifted onto the ground ahead,
`horseLift`) and how its legs go (`limbPos`, `limbSpeed`, as `LivingEntity`'s limb animator), and the client draws the
game's own horse model there. It looks and moves exactly as a black horse does.

## The trap

The disguise is a wandering trader's wagon (kortev: something that makes sense in Minecraft and might actually work,
rather than the film's sweet cart). Its counter is on the door (`disguise_b`), with up to three pieces of bait (`bait`,
three tracked `ItemStack`s for clients, `baiter` who set it). `useBack`: sneaking with an item sets bait, sneaking empty
toggles the door; otherwise, with bait out, the baiter takes it back and anyone else is `spring`-ed: pulled into the cage
(`putInCage`), the door shut, a `SNAP` for clients to fling the door open and slam it. Villagers are drawn to villager
food on the counter (`VillagerEntity.ITEM_FOOD_VALUES`) by their `WALK_TARGET` (`lure`, every second) and sprung at
the door. The whip throws the disguise off (`THROWN`) and spills the bait (`spillBait`).
