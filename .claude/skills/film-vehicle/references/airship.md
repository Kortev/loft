# The Vulgarian airship: full map

Baron Bomburst's airship from the film. Her README section (`chitty/README.md`, "The Vulgarian airship") is the agreed
description of how she looks and behaves. Keep it in step.

## Files

She is part of the Chitty mod (`chitty/`), in the `io.github.kortev.chitty.airship` package (main) and beside
Chitty's classes in `io.github.kortev.chitty.client` (client). Her assets and data are in the `shootingstar`
namespace.

| What | Where |
|---|---|
| Registration (entity types, items, sounds, C2S payloads); `init()` called from `Chitty.onInitialize` | `airship/Airship.java` |
| The airship (flying, places, lift, grapple, ladder, bombs, overboard, NBT) | `airship/AirshipEntity.java` |
| Hitboxes: the gondola's bow and stern, six along the envelope, the tail | `airship/AirshipPartEntity.java` |
| The grapple's holder, which a caught thing rides; a player struggles free by holding sneak for 10 s | `airship/AirshipHookEntity.java` |
| A falling bomb (power 2.5, TNT's 4) | `airship/AirshipBombEntity.java` |
| Placing item | `airship/AirshipItem.java`; the bomb item is a plain `Item` |
| Payloads | `AirshipInputPayload` (the pilot's controls, Chitty's `ChittyControls`), `AirshipActionPayload` (`ACTION_` codes), `AirshipWalkPayload` (where a rider has walked to in the gondola) |
| Mixins (main, `chitty.mixins.json`) | `mixin/AirshipDismountMixin` (sneaking aboard is `AirshipEntity.letsGo`'s to decide; never off the grapple until struggled free), `mixin/AirshipLadderMixin` (the rope ladder is climbable, and solid on its side towards her, so walking into it climbs it: `intoLadder`), `mixin/AirshipStrideMixin` (riders' legs go as they walk about her, not as she moves) |
| Client: renderer, bomb renderer, keys and hints, running sounds | `client/AirshipRenderer`, `AirshipBombRenderer`, `AirshipClient` (`init()` from `ChittyClient`), `AirshipSound` |
| Client mixin | `client/mixin/AirshipStandMixin` (riders are drawn standing); `ChittyCameraMixin` turns the third-person camera about the middle of her (`VIEW_CENTRE`), `VIEW_DISTANCE` (24) back |
| Mesh loader | `client/ChittyMesh.get("airship")` (shared with Chitty) |
| Model, bake, icon, renders | `tools/airship_model.py` (`--game` reuses `chitty_model.export_game` with her settings: a 4096 atlas, `TEXEL_WEIGHT`, `GAME_LIT = ('helm',)`) |
| The arms on the envelope | `tools/vulgaria_arms.py` (flat heraldry, each shape parted by a fine gap) |
| The gondola's gilt carving and its relief and gilt maps | `tools/airship_carving.py` (laid out from the film's still of her right side) |
| Sounds | `tools/gen_airship_sounds.py` |
| Baked outputs | `chitty/src/client/resources/assets/shootingstar/meshes/airship.cbm`, `.../textures/entity/airship.png`, `chitty/src/main/resources/assets/shootingstar/textures/item/airship.png` |
| Recipes, unlocks | `recipe/airship.json` (purple and white wool, phantom membranes, chain, gold, a boat), `recipe/airship_bomb.json` (iron, 2 gunpowder, string: 2 bombs); `advancement/recipes/transportation/airship.json`, `advancement/recipes/combat/airship_bomb.json` |
| Advancements | `advancement/airship/`: `root`, `board`, then `grab` and `bomb` |
| Tests | `src/gametest/.../AirshipGameTests.java`, a batch per test (`c_airship_*`): her hitboxes reach into a neighbouring test's box |

## Numbers (`AirshipEntity`)

- **Speeds** (blocks/tick): `TOP` 0.42, `ACCEL` 0.005, `REVERSE_TOP` 0.12, `RISE` 0.11, `SINK` 0.14, `HEAVY_SINK`
  0.035; turning `TURN` 1.6 and `TURN_STANDING` 0.7 degrees a tick.
- **Standing and walking:** people come aboard at `PLACES[8]` (the wheel, then two, two and three across, feet on the
  floor 0.42 up), then walk where they like on the floor (`FLOOR_HALF_WIDTH` 0.6 either side, `FLOOR_AFT` -0.46 to
  `FLOOR_FORE` 1.02) at `WALK` 0.15 a tick, keeping `ELBOW_ROOM` 0.5 from one another. Each rider's client moves them
  (`AirshipClient.walk`, the movement keys the way they face) and tells the server (`AirshipWalkPayload`, kept to the
  floor and a walker's pace by `AirshipEntity.walk`); the server shares where everyone stands (`STANDS`, by entity id)
  and other clients ease them there.
- **The wheel:** whoever walks within `HELM_REACH` (0.22) of `HELM_SPOT` (`PLACES[0]`) takes it (`HELM`, the entity id)
  and their movement keys fly her. Sneaking lets go; to take it again they walk away and back. At the wheel the client
  switches to third person and back to the view they had when they let go (`AirshipClient.tick`).
- **Walking looks:** clients ease other riders to where they stand and move their legs (`strides`), knock their steps
  on the boards, and bob the walker's own view (`updatePassengerPosition`).
- **Getting off** (`letsGo`): sneak. Down or within 2 blocks of the ground, off beside her; in the air the ladder lets
  itself down and, still sneaking, they get off onto it once it hangs `LADDER_OFF` (2) blocks.
- **Lift:** `LIFT` 6: with more aboard (a load on the grapple counts one) she cannot climb and sinks.
- **Grapple:** `LINE_OUT` (0, 0, 0.25); `HOOK_GRIP` 0.95 from its ring to its tines; `CARRY` 3.0 under her keel;
  `LINE_MAX` 64; the rope pays out at `HOOK_DOWN` 0.45 and in at `HOOK_UP` 0.2 a tick. States: `UP, LOWERING, DOWN,
  LIFTING, HOLDING, SETTING, RAISING` (worked by one key, R) and `HELD` (in someone's hand on the ground).
  - It swings (`swingGrapple`, server): a Verlet point at its ring under `HOOK_GRAVITY` 0.05, keeping `HOOK_DAMPING`
    0.985 of its speed a tick (0.97 loaded), never further from `lineOut()` than the rope paid out (`hookDrop`). Its
    lowest point (tines, or a load's feet) is raycast against blocks: it rests on them and slides a little; a hard
    landing clanks and puffs dust (`thud`). Clients draw it from `HOOK_AT` (eased), the rope and grapple turned along
    the rope (`AirshipRenderer`).
  - Its head (`AirshipHookEntity`) is in the world while the grapple is out: loads ride it; hanging empty it can be
    used (`takeHoldOfGrapple`), and then follows `handOf` the holder (`HOOK_HELD`). Using it on something within
    `HOOK_REACH` 4 hooks it on (`hookOnto`, from Fabric's `UseEntityCallback` in `Airship.init`); sneaking, R aboard or
    going beyond the rope lets go.
- **Ladder:** hangs from `LADDER_TOP` on her left rail; climbers hang on `LADDER_LINE`, 0.25 further out. It is
  stacked from one-rung parts (`LADDER_PITCH` 0.32) down to the ground (`LINE_MAX`, 64, at most).
- **Rack:** `RACK` 6 bombs, `BOMB_COOLDOWN` 30 ticks; bombs fall through the floor at `RACK_Z`.
- **Envelope boxes** (`ENVELOPE`: z, bottom, width, height): for the hitboxes and for keeping her envelope out of
  hills (`clearEnvelope`).

## Mesh parts

`body`, `helm` (the wheel, game-lit), `prop_r/l` and `prop_r/l_pulley` (turn about z), `rudder`, `elevator`, `coil` (the
grapple's rope on its drum: scaled thinner as it pays out), `hook` (moved down by the drop), `rope` (a block long,
scaled to the drop), `ladder` (one rung), `bomb_0..5`. Markers: `stand_0..7`, `line`, `ladder_top`, `exhaust`.

## Keys

All rebindable, in `key.categories.shootingstar`:
- **R:** the grapple.
- **K:** the ladder.
- **B:** drop a bomb.
- **O:** overboard (pilot only).

For the pilot, jump rises and sprint sinks.
