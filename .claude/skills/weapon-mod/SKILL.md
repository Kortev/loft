---
name: weapon-mod
description: How The Shooting Star's cinematic weapons are built (SS-03 Gungnir's orbital strike, the Ω-00 Genesis Key's Ginnungagap, Þ-01 Mjölnir's god-bolt) and the exact recipe for editing them or adding a new weapon of the same kind - an item that triggers a server-timed event with a client cinematic (feed, camera shots, world effects, synthesised sound), world changes, game rules, advancements and game tests. Use whenever the task is to change Gungnir, the Genesis Key or Mjölnir, or to make a new weapon, strike, event or "ultimate" item for this mod.
---

# Weapons of The Shooting Star

A weapon here is not an item that does damage. It is an **event**: using the item locks onto a target, then a fixed
**timeline** of ticks plays out on the server and on every client together. The server only decides what happens
to the world and to entities. The clients turn the same timeline into a cinematic: a full-screen 3D feed, camera
shots, effects drawn into the world, a HUD and a synthesised soundtrack.

Three exist, and they are the templates:

- **SS-03 Gungnir** (`gungnir_uplink`): a 22-second strike; a 128-block crater, molten crust and a spire.
  Read `references/gungnir.md`.
- **Ω-00 Ginnungagap** (`genesis_key`): a world-scale event; the world round the target is erased, everyone waits
  in the void, and the cracked key rebuilds it. Read `references/genesis-key.md`.
- **Þ-01 Mjölnir** (`mjolnir`): an 18-second strike as strong as Gungnir but its own: a storm over the target, a bolt
  that blasts a fused crater, burns the zone, scars the ground in a Lichtenberg figure and arcs on through the ring,
  and is left standing petrified. It shares no class with Gungnir. Read `references/mjolnir.md`.

Read the reference for the weapon you are touching before editing it. For a new weapon, read them and copy the
nearest: Mjölnir (or Gungnir) for a strike that changes terrain and is over, the Genesis Key for an event that holds
players and must survive restarts. Mjölnir is the newest and the most self-contained strike to copy from.

## The shape every weapon has

```
Item.use ──► XManager.launch(world, target, shooter)        [server, main source set]
               │  Targeting.settle; game rules read ONCE and frozen into the event
               │  chunk ticket over the whole affected area
               │  ModNetworking.broadcast(world, XLockPayload(id, target, shooter, age=0, size...))
               │  ModCriteria.fire(shooter, "x_lock")
               ▼
XManager.tick (ServerTickEvents.END_SERVER_TICK): age++ against XTimeline constants
               │  at IMPACT: XImpactPayload; start a budgeted world builder (N blocks/tick)
               │  entities: damage with the mod's DamageType, shooter as attacker
               ▼  at END: forget it (persist first if it must survive a restart)

Client [client source set]
  ShootingStarClient: registerGlobalReceiver(XLockPayload) ──► ClientXs.onLock
  ClientXs.tick (END_CLIENT_TICK): age++ in step with the server; cues(from, to) plays sounds at timeline marks;
                                   holds just short of IMPACT until the server's impact payload arrives
  Feed (HudRenderCallback) ── Feed.Sequence.render(t) → 3D shots offscreen (HDR) → bloom → Overlay text
  CameraDirector / CameraMixin ── the shooter's camera shots (rise, impact, wide)
  WorldRenderEvents.LAST ── effects in the world (beam, falling object, fireball, rings, debris) in an HDR light pass
  HudRenderCallback ── HUD over the world;  GameRendererMixin ── ScreenShake
```

Everything is keyed by **one `XTimeline` class of tick constants in the main source set**. The server, every client
cue, every shot and every effect reads the same constants, so they cannot drift. Change a phase's length there and
everything follows.

## Rules that make it work first time

1. **The timeline is the contract.**
   - Put every phase boundary in `XTimeline` as a named `int` tick, plus any pure maths both sides need (e.g.
     `GapTimeline.burstHalf`, `StrikeTimeline.lapVelocity`).
   - Never hard-code a tick anywhere else. Write `IMPACT + 44` against the constant if you need an offset.
2. **The server is authoritative only for consequences.** It decides:
   - the target (`Targeting.findTarget` / `settle`);
   - the size, from game rules read at launch and frozen in the event, so a rule changed mid-flight can't desync
     clients;
   - the world edits, the damage, the advancements.

   It never sends per-frame visuals; clients compute those from `(age, target, size)`.
3. **Clients age in step and wait for proof.**
   - `ClientX.age` advances on client ticks.
   - At the consequence tick the client **holds** until the server's impact payload arrives (`holdTicks`, dropped
     after 100).
   - Late joiners get a lock payload with the current `age` (see `StrikeManager.init` JOIN handler).
   - Clear everything on world change and disconnect.
4. **Only the shooter gets the cinematic.** `mine = player.uuid == payload.shooter`.
   - Everyone else in range sees and hears the world effects.
   - Placed world sounds are **mono** (Minecraft only positions mono). The shooter's close-ups are **stereo** `.near`
     variants played non-positionally.
   - Far players hear booms late (speed of sound) via delayed cues.
5. **Big world edits are budgeted and quiet.**
   - Precompute the columns sorted by distance and carve outward a front at a time, with a block budget per tick
     (`ImpactBuilder.BLOCK_BUDGET = 60_000`).
   - Set blocks with `NOTIFY_LISTENERS | FORCE_STATE`.
   - Never touch blocks with hardness < 0 (bedrock, barriers, command blocks). Remove block entities without drops.
   - For millions of blocks (Genesis Key `Erasure`): change blocks without notifying, gather one light check per
     column, then resend each touched chunk **once**.
   - Hold a `ChunkTicketType` over the whole area for the whole event, with 2 chunks of margin so edges tick and
     changes reach players.
6. **Gate destruction behind game rules** (`ModGameRules`: radius, terrain damage on/off, lethal on/off) and give
   `/command` entry points for operators (`GungnirCommand`, `GapCommand`), with a null shooter allowed.
7. **Damage carries meaning.**
   - Each weapon has its own `DamageType` json in `data/shootingstar/damage_type/` with a death message key.
   - Build the source with the shooter as attacker when there is one (`ModDamageTypes.erased(world, by)`), so the
     kill counts as theirs in PvP. (Gungnir's `kineticStrike(world)` has no attacker yet.)
   - Tag it in `data/minecraft/tags/damage_type/bypasses_*.json` if it should ignore armour etc.
8. **Advancements use one trigger.**
   - `shootingstar:event` (`ModCriteria.EVENT`) with an event name string. A new weapon needs only new constants
     fired with `ModCriteria.fire(player, NAME)`, plus jsons in `data/shootingstar/advancement/<weapon>/`.
   - An advancement's icon must be an **item** that exists (a block with no item breaks loading).
9. **Long events persist.**
   - Anything that moves players or holds the world in a strange state saves itself in a `PersistentState`
     (`GapState`): who came from where, the running event, owed item fixes.
   - It also finishes or undoes itself on `SERVER_STOPPING` and `SERVER_STARTED`.
10. **Lock the player's input instead of fighting it** while their camera is taken (`ClientGaps.holdInput` swallows
    key presses at `START_CLIENT_TICK`; `EntityLookMixin` holds the head). Give a skip key (`SKIP_FEED`, Backspace),
    and honour `ClientConfig` (`feed`, `cameraShots`, `screenShake`).
11. **Crafting is owner-only for free.** Any recipe whose result is in the `shootingstar` namespace is blocked for
    everyone but `OwnerOnly.OWNER`. Nothing to add per weapon.

## Recipe: a new weapon of the same kind

Name it once (say `thunder`, item `thunder_rod`, timeline `ThunderTimeline`) and create, in this order:

1. **Design the timeline on paper first**, as Gungnir's README table does (time → what the shooter sees, what
   everyone sees, what happens to the world). Get kortev's OK on it. Then write `XTimeline` (main source set, its
   own package `io.github.kortev.shootingstar.<x>`).
2. **Server event:** `XManager` (copy `StrikeManager` for a strike, `GapManager` for a held event).
   - It holds the list of live events and `init()`: tick, `SERVER_STOPPED` clear, JOIN sync.
   - It also has `launch(world, hit, shooter)`, `isBusy(uuid)`, `cancelAll(server)` and `active()`.
   - Register `init()` in `ShootingStar.onInitialize`, after `ModNetworking.init()`.
3. **World change:** an `XBuilder` with `start()` and `step()` → done, budgeted, as `ImpactBuilder`.
4. **Item:** `XItem extends Item`.
   - `use()` aims (`Targeting.findTarget`); refuses with an action-bar message and the denied sound (no target,
     too close, busy).
   - On the server it calls `launch` and sets a cooldown ≥ the event's length. It also has a tooltip
     (`item.shootingstar.<x>.tooltip.N`).
   - Register it in `ModItems` (`maxCount(1).rarity(EPIC).fireproof()`), and add it to the Combat tab there.
5. **Payloads:**
   - `network/XLockPayload` (id, target, shooter, age, sizes) and `XImpactPayload` (+ `XCancelPayload` if it can be
     called off), as records with a `PacketCodec.tuple`.
   - Register them `playS2C` in `ModNetworking.init`. Send with `ModNetworking.broadcast(world, …)`.
6. **Rules, damage, criteria:**
   - `ModGameRules` (camelCase name prefixed with the weapon, sane min/max).
   - `ModDamageTypes` key + json + lang `death.attack.shootingstar.<x>` (and `.player`).
   - `ModCriteria` names + advancement jsons (`parent` chains to `shootingstar:root` or the weapon's own root;
     `frame`; lang `advancements.shootingstar.<x>_<event>.title/.description`).
7. **Command:** `command/XCommand` with operator `strike <pos>` / `cancel`, registered from `ShootingStar`.
8. **Client mirror:** `client/ClientX` (state, `time(tickDelta)`, `mine`, `cinematic()`) and `client/ClientXs`
   (`onLock` / `onImpact` / `onCancel`, `tick`, `cues`, `skipFeed`, `clear`). Wire the receivers and the tick in
   `ShootingStarClient`.
9. **Cinematic:**
   - A `client/feed/XShots implements Feed.Sequence` (`Overlay render(t, fbW, fbH, guiW, guiH)`) that switches on
     timeline phases, as `Shots` / `GapShots` do. Add a `Feed.renderX(ctx, t)` beside `Feed.renderGap`, and call it
     from the HUD callback while the feed shows (`HudEffects.render` for Gungnir, `GapHud.render` for the Genesis
     Key). Reuse `Space`, `gfx/Mesh`, `gfx/Fx`, `gfx/Post` and `gfx/Target`.
   - The words over the feed are `Overlay` fields that the shot fills: `header`, `title` / `subtitle`, `banner`,
     `footer`, `labels`, `bars`, plus grading (`flash`, `exposure`, `zoomBlur`…).
   - Camera shots in `CameraDirector`.
   - World effects registered on `WorldRenderEvents.LAST`, and HUD on `HudRenderCallback`.
   - New GLSL: `src/client/resources/assets/shootingstar/shaders/core/ss_<name>.json/.vsh/.fsh` (beside `ss_glow`,
     `ss_impact`, `ss_galaxy`…), registered in `Shaders.register` with its vertex format. Feed meshes (`.ssm`) live
     in `src/client/resources/assets/shootingstar/meshes/`.
10. **Assets** (generated, then committed):
    - Sounds: a recipe function in `tools/gen_sounds.py` plus a `SOUNDS` entry `(fn, stereo?)`, a `LEVELS` entry
      and a `CAPS` entry if long.
    - For each sound, register it in `ModSounds` (`<x>.name` → file `<x>_name`), add a `sounds.json` entry with a
      subtitle, and the lang `subtitles.shootingstar.<x>.name`.
    - Textures: a function in `tools/gen_textures.py` called from `main()`, plus item model json in
      `models/item/`.
    - Feed meshes: a `build_*` in `tools/models.py` (→ `.ssm`).
11. **Recipe** `data/shootingstar/recipe/<item>.json` (shaped) and its unlock advancement in
    `data/shootingstar/advancement/recipes/combat/<item>.json`. Add it to the README too.
12. **Lang:** every key above in `assets/shootingstar/lang/en_us.json` (item name, tooltip lines, messages,
    subtitles, death messages, advancements, game rule names `gamerule.<rule>`, command feedback).
13. **Game tests** in `src/gametest/java/.../test/XGameTests.java`, registered in `src/gametest/resources/fabric.mod.json`
    under `fabric-gametest`. At least:
    - targeting refuses too-close and no-target;
    - a full event on flat stone through the real timeline (`tickLimit = END + margin`), checking what is left
      (blocks at known offsets, an entity killed with the right damage type, unbreakables untouched);
    - game rules respected (terrain off leaves blocks).

    Use `EMPTY_STRUCTURE` and a batch id of its own. Connected fake players come from the helper in
    `GapGameTests.player` / `ChittyGameTests.player`.
14. **README:** a section in the house voice (what you see, second by second; what it does to the world; getting it;
    commands and game rules tables).
15. **Push, read CI** (`build` + `gametest` jobs), fix, repeat. Then give kortev a numbered in-game test list.

## Editing the existing ones: where things live

| To change… | Edit |
|---|---|
| When anything happens | `StrikeTimeline` / `GapTimeline` constants (everything follows) |
| Range, too-close distance | `Targeting.MAX_RANGE`, `Targeting.minRange` (Gungnir); `GapTimeline.MIN_RANGE` |
| Crater shape / blocks / spire | `ImpactBuilder` (`bowlRadius`, `bowlDepth`, `scorchRadius`, `SPIRE_R2`, `FIN_*`, `BAND_SPACING`, `MAX_CUT`, `plane`, `scorch`, `crust`) |
| Crust cooling | `MoltenCrustBlock` (`HEAT` 3→1 on random ticks, then `FUSED_CRUST`; water quenches) |
| Who dies / is spared in the gap | `GapManager.spared`, `swallow`, `eraseIn`; rule `ginnungagapLethal` |
| Shape of the gap's hole | `Erasure` (ragged shaft, fissures); `GapTimeline.eraseReach` |
| Where people wait / are sent home | `GapManager.gather`, `place`, `rim`, `sendHome`, `safe`, `standable` |
| The feed's shots | `client/feed/Shots.java` (Gungnir), `GapShots.java` (Genesis Key): one method per phase |
| Feed text and marks | the `Overlay` fields each shot fills (`header`, `title`, `banner`, `labels`, `bars`, `flash`…) |
| Camera shots outside the feed | `client/camera/CameraDirector` (Gungnir), `client/gap/GapCamera` (Genesis Key) |
| The blast in the world | `client/world/ImpactScene` (simulation), `WorldFx` (drawing, impact frames, grading) |
| The gap in the world | `client/gap/GapRender`, `GapFrames` (impact frames), `TreeRender` (Yggdrasil), `VoidFx` (floor, warps) |
| Shake | `client/camera/ScreenShake` |
| A sound | its recipe in `tools/gen_sounds.py`, then `python3 tools/gen_sounds.py <name>`; its cue in `ClientStrikes.cues` / `ClientGaps` |
| Lock beam / reticle | `client/render/WorldEffects` |
| A texture | `tools/gen_textures.py`, then `python3 tools/gen_textures.py` |
| A feed mesh | `tools/models.py` (Blender), then `python tools/models.py` |

## Gotchas

- Yarn 1.21.1 names differ from Mojang's and from newer versions:
  - `TypedActionResult` (not `ActionResult` for `Item.use`).
  - `world.getRegistryManager().get(RegistryKeys.X)`.
  - `PacketCodec.tuple`, `CustomPayload.Id`.
  - `DataComponentTypes.CUSTOM_DATA` for item NBT (the cracked key).

  Check a doubtful name against the raw Yarn mapping file before pushing.
- Payload registration is in **main** (both sides); receivers are in **client** (`ShootingStarClient`) or main
  (C2S, e.g. `GapSettlePayload`).
- `ModNetworking.send` checks `canSend`, so vanilla clients don't crash.
- A placed sound must be mono in the generator (`SOUNDS[name] = (fn, False)`).
- Sound files are `<event with dots → underscores>.ogg`. `sounds.json` keys are the event names.
- **The sounds are cut to the timeline.**
  - `gen_sounds.py` keeps its own copy of the beats: the `GAP` dict for the Genesis Key, and seconds written into
    the Gungnir feed recipes and `CAPS`.
  - When you retime a weapon, update those to match and regenerate its sounds, or they drift off the pictures.
- Keep the generators deterministic (fixed seeds; the Genesis Key's sounds seed per name with `zlib.crc32`), so
  regenerating one sound doesn't change the rest.
- Shaders load with the vanilla ones (`CoreShaderRegistrationCallback`). Guard drawing with `Shaders.ready()` and
  fall back to black.
- Culling: camera shots that fly where the world was never seen need `Culling.update(client, flying)` (sections are
  culled from what they looked like when last built).
- The client self test (`selftest` workflow, inputs `true` for Gungnir, `gap` for the Genesis Key) films the whole
  event at an exact frame rate. It's expensive, so only run it when kortev asks. Their in-game test is the usual
  check.
