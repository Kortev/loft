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
| Client: renderer, bomb renderer, keys, running sounds | `client/AirshipRenderer`, `AirshipBombRenderer`, `AirshipClient` (`init()` from `ChittyClient`), `AirshipSound` |
| The controls on screen (kortev found the keys confusing) | `client/AirshipHud` (a `HudRenderCallback`): aboard, the keys for walking or the wheel and the crew's, lit while held, and the grapple's gauge; on the ground with the grapple, hanging on it or caught, what they can do. Words under `hud.shootingstar.airship.ui.*` |
| Client mixin | `client/mixin/AirshipStandMixin` (riders are drawn standing); `ChittyCameraMixin` turns the third-person camera about the middle of her (`VIEW_CENTRE`), `VIEW_DISTANCE` (24) back |
| Mesh loader | `client/ChittyMesh.get("airship")` (shared with Chitty) |
| Model, bake, icon, renders | `tools/airship_model.py` (`--game` builds her faceted: `tube`, `lathe`, `pipe` and `points` make square bars of at least `THINNEST` and round parts of `LATHE_SIDES`, the envelope has 16 sides, every face is flat; it reuses `chitty_model.export_game` with her settings: a 1024 atlas, `TEXEL_WEIGHT` towards the gondola, `LIT_ALL` (her colours alone, darker in nooks by `SHUT_IN`, every part's normals kept for the game's face lighting) and `PACK_ROTATE = 'AXIS_ALIGNED'`; `AirshipRenderer` draws the texture pixelated, `ChittyTexture(..., true)`) |
| The arms on the envelope | `tools/vulgaria_arms.py` (flat heraldry, each shape parted by a fine gap) |
| The gondola's gilt carving and its relief and gilt maps | `tools/airship_carving.py` (laid out from the film's still of her right side) |
| Sounds | `tools/gen_airship_sounds.py` |
| Baked outputs | `chitty/src/client/resources/assets/shootingstar/meshes/airship.cbm`, `.../textures/entity/airship.png`, `chitty/src/main/resources/assets/shootingstar/textures/item/airship.png` |
| Recipes, unlocks | `recipe/airship.json` (purple and white wool, phantom membranes, chain, gold, a boat), `recipe/airship_bomb.json` (iron, 2 gunpowder, string: 2 bombs); `advancement/recipes/transportation/airship.json`, `advancement/recipes/combat/airship_bomb.json` |
| Advancements | `advancement/airship/`: `root`, `board`, then `grab` and `bomb` |
| Tests | `src/gametest/.../AirshipGameTests.java`, a batch per test (`c_airship_*`): her hitboxes reach into a neighbouring test's box |

## Numbers (`AirshipEntity`)

- **Speeds** (blocks/tick): `TOP` 0.8, `ACCEL` 0.018, `REVERSE_TOP` 0.25, `RISE` 0.28, `SINK` 0.32, `HEAVY_SINK`
  0.035; turning `TURN` 2.8 and `TURN_STANDING` 1.4 degrees a tick (kortev found the first, realistic speeds too
  slow to be worth flying her).
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
  `LINE_MAX` 64. States: `UP` (stowed), `OUT` (on its rope, empty or with a load: a caught thing, or someone hanging
  on by choice, the head `voluntary`, who drops off when they sneak) and `HELD` (in someone's hand on the ground).
  - It moves only by its winch (`setWinch(by, way)`, `winchRope`): the crew hold R (`ACTION_PAY_OUT`) or Y
    (`ACTION_WIND_IN`), and letting go sends `ACTION_WINCH_STOP`; it stops if whoever works it leaves her. The winch
    eases (`WINCH_EASE`) to `HOOK_DOWN` 0.45 / `HOOK_UP` 0.3 a tick, `LOADED_DOWN` 0.22 / `LOADED_UP` 0.2 with a load.
    Let out onto the ground it leaves `SLACK` 2 and stops; a load let out until it stands is let go there. Wound in, a
    caught load stops at `CARRY`, a volunteer at `HOOK_ABOARD` (0.6) climbs aboard (`comeAboard`), and the empty
    grapple at 0 is stowed. Catching something or someone hanging on stops the winch. Nothing else moves it.
  - It takes hold only while it is going: let out by the winch, swept as she flies faster than `SWEEP`, or thrown
    (`THROWN_FLIGHT` ticks); never of whoever has just got off it, let go of it or thrown it (`spare`, `SPARE` ticks).
    Meeting one of Chitty's hitboxes, it takes Chitty (`whole`).
  - It never swings up past her keel (`KEEL_CLEARANCE`, keeping within its rope), and snagged on something she flies
    away from it is dragged free once it is held `SNAG` past its rope.
  - It swings (`swingGrapple`, server): a Verlet point at its ring under `HOOK_GRAVITY` 0.05, keeping `HOOK_DAMPING`
    0.985 of its speed a tick (0.97 loaded), never further from `lineOut()` than the rope paid out (`hookDrop`). Its
    lowest point (tines, or a load's feet) is raycast against blocks: it rests on them and slides a little; a hard
    landing clanks and puffs dust (`thud`). Clients draw it from `HOOK_AT` (eased), the rope and grapple turned along
    the rope (`AirshipRenderer`).
  - Its head (`AirshipHookEntity`) is in the world while the grapple is out: loads ride it; hanging empty it can be
    used (`takeHoldOfGrapple`), and then follows `handOf` the holder (`HOOK_HELD`). Using it on something within
    `HOOK_REACH` 4 hooks it on (`hookOnto`, from Fabric's `UseEntityCallback` in `Airship.init`). Their grapple key
    throws it (`ACTION_THROW`, `throwGrapple`: `THROW_SPEED` 1.0 the way they look, `THROW_SLACK` 16 more rope; it
    spares the thrower, and catches along its path, `catchable`). Jumping with it
    hangs on (`hangOn`: from the crown, the rope drawn taut where they are; leaning pumps the swing, `HOOK_PUMP`;
    holding jump climbs the rope at `CLIMB`, read through the `mixin/AirshipJumper` accessor). Sneaking or going beyond
    the rope lets go.
  - Whatever it takes hold of is hung by its collar where it stands (`grab`), never from wherever the tines met it;
    its lowest point is lifted out of any block it ends up in (`outOfTheGround`); and nothing on a grapple takes
    suffocation damage (`Airship.init`).
  - `AirshipRenderer.drawGrapple` draws the rope and grapple in her yaw frame, untilted, from where the rope leaves her
    tilted keel: they hang where what is on them hangs, whatever her pitch, bank or rocking.
  - Rope looks: `AirshipRenderer.drawRope` sags slack rope (paid out more than the distance) in a curve; the `coil`
    drum turns by `drop / DRUM_RADIUS`; a loaded rope creaks; dragged along the ground it rattles and sparks.
  - Caught or by choice: the head syncs `VOLUNTARY` and counts kicks (`AGITATION`). A caught thing hangs by its collar
    (`hangBelow`, `COLLAR` ahead of the tines), a volunteer by both hands from the ring; `client/mixin/AirshipHangPoseMixin`
    poses them (arms up, or limp and thrashing when kicking). Caught players kick as they struggle (a bar in the action
    bar); caught mobs kick now and then; each kick jolts the grapple (`jolt`). The ship syncs the head's id (`HOOK_HEAD`)
    and `AirshipClient` tells the crew what hangs on it.
- **Ladder:** hangs from `LADDER_TOP` on her left rail; climbers hang on `LADDER_LINE`, 0.25 further out. It is
  stacked from one-rung parts (`LADDER_PITCH` 0.32) down to the ground (`LINE_MAX`, 64, at most). It trails behind
  her as she goes (`ladderLean`, eased towards atan(1.2 x forward speed); `ladderAt` is where a climber is at a depth),
  carries climbers along (`ladderCarry`), and knocks its rungs as they climb (`AirshipLadderMixin`).
- **Looks and sounds:** she pitches and rolls about the floor of her gondola (`AirshipRenderer.PIVOT_Y`), so it stays
  under the feet of those aboard; the propellers run up and down (`spinRate`); hits sound of canvas or wood (`hurt`,
  parts 2 and up are canvas); broken, she bursts into canvas and wood; put down, her canvas rustles.
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
