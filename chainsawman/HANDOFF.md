# Handoff: kortev's Chainsaw Man mod (state on 2026-10-19)

You are picking this up with nothing but this GitHub repository (`Kortev/loft`). No chat history, no files from
kortev's PC, and none of the reference images they sent come with it. Everything you need is in this file and the
repo. Read it all before doing anything, then read the repo's own `HANDOFF.md` at the root for the wider project.

## Where the work is

- **Branch: `claude/chainsaw-man`.** All of this mod's work is there and nowhere else. It is **not merged into
  `main`**, and `main` is far behind it. Work on this branch (or a branch off it); push to it.
- It was cut from `claude/gojolander-domain` (open PR Kortev/loft#3: the friend's mod) with
  `claude/iris-fullscreen-effects` (open PR Kortev/loft#2: shader-pack support, `WorldPasses`) merged in, because
  neither PR is merged yet and this mod needs both. Those two PRs conflict in one file
  (`gojolander/src/client/resources/gojolander.client.mixins.json`); it is already resolved on this branch.
- **No PR and nothing into `main` without kortev's OK.** Merging is their call.
- CI (`build` workflow: `build` and `gametest` jobs) is green on the branch as of the last commit. All 55 game tests
  pass.

## The project in one paragraph

`Kortev/loft` is one Gradle build (Fabric, Minecraft **1.21.1**, Yarn mappings, Java 21) of several mods for kortev's
SMP, each its own subproject and jar: **The Shooting Star** (root, `src/`, id `shootingstar`: kortev's weapons, plus
everything shared: `OwnerOnly`, `ModCriteria`, the `client/gfx` helpers `Post`, `Target`, `Shaders`, `WorldPasses`,
and the game-test harness), **Chitty Chitty Bang Bang** (`chitty/`), the friend's **Gojolander** (`gojolander/`, the
Domain Expansion), and now **Chainsaw Man** (`chainsawman/`, id `chainsawman`): kortev's own powers. Every SMP player
gets a mod of their own; this one is kortev's.

## Rules kortev has set (follow them every time)

1. **No spoilers past the Reze arc.** kortev has only watched the anime, through the end of the Reze arc (the
   movie), and has not read the manga. In every question, option, name, comment and commit, draw only on season 1
   and the Reze arc. Never mention or hint at later forms, characters or events. (An early question once offered a
   later-manga form as an ult option; they noticed.) If you research, do it text-only and keep what you report safe.
2. **Never name the mods or their items in public content**: Shorts titles, captions, pinned comments, public PR
   descriptions. The series is becoming an SMP and kortev wants the mystery kept. Names in the code are fine.
3. **One piece at a time, committed and pushed as soon as it works.** Get each piece in the game first even before
   it does anything, then add each ability on its own. Commit (one topic, a subject in plain words, a body that says
   why), push, then start the next piece.
4. **Ask before design calls** (what a power does, balance, PvP, how something looks); decide technical calls
   yourself. Keep messages short and plain. Refer to kortev as "they".
5. **Show renders/screenshots before baking or shipping a look**, and match the anime. kortev reacts strongly to
   things that look invented instead of matched; when they send references, follow them exactly.
6. **End a round of work with a numbered list of what to test in game.**

## What the mod does now (all pushed)

- **Pochita** (`chainsawman:pochita`): an item, carried anywhere in the inventory. Only kortev can craft it
  (`OwnerOnly.register(MOD_ID, () -> "kortev")`). Recipe: chains, orange wool, a nether star.
- **The cord key (G)**: with Pochita carried, pulling the cord transforms you; pulling it again turns you back (the
  engine sputters and dies). Losing Pochita, dying or leaving also turns you back. The server owns the state
  (`form/Form.java`) and tells every client (`FormPayload`, `FormPullPayload`).
- **The form's look**: your head replaced by the chainsaw head, chainsaws through both forearms, a starter cord on the
  chest. Your own head, hat layer, helmet and anything worn on the head are hidden. The arm saws also show in first
  person. The chains creep round their bars all the time and race when you swing.
- **The transformation is a ~3 s cinematic** (`form/FormTimeline.java` holds every beat, in ticks; the server, the
  pictures and the sounds all read it):
  - 0: the camera cuts round in front of you; your hand comes up to the cord;
  - 4-12: one long yank, the cord stretched from chest to hand (drawn by `FormFeature.cord`), head snapping back;
  - 13: the cord snaps back; 14 and 17: the engine catches and coughs (smoke, camera jolts);
  - 20 (BURST): the form comes on; the head appears in a spray of blood and its saw grows out to full length;
  - 23-32: the arm saws push out through the fists and grow; the arms brace out (camera low and wide, the poster's
    angle);
  - 36 (ROAR): hard cut to the face, FOV punched in, anime **impact frames** (the picture in flat red/black/white,
    then its negative, with speed lines; shader `csm_impact`) and a red vignette; arms flung wide, head thrown back;
    anyone within 16 blocks feels a shake;
  - 52-62: the camera eases back into first person.
  Keys and look are held during it. Everyone nearby sees the body animation, the cord, the growing saws and the
  blood; only the puller gets the camera, zoom, impact frames and vignette.
- **Sounds** (synthesised, `tools/gen_chainsaw_sounds.py`): the cord rip, the engine start cut to the timeline
  (catch, cough, burst, roar, settling to idle), the idle loop while transformed, the engine dying.

## The design kortev approved (don't change it without asking)

### The powers (decided; most not built yet)

- Transform: Pochita carried + the cord key. **Built.**
- **Chainsaw attacks**: the saws from head and arms as heavy, revving melee that shreds players and mobs, with sparks
  and blood. Not built.
- **Blood healing**: you heal by hitting and killing things (drinking blood). Kept small; it's the passive. Not built.
- **Health drain**: while transformed your health slowly drains; you stay up by hitting things. This is the form's
  cost and what keeps it fair against the friend's Domain. Not built.
- **Chain grapple**: fire the chain from your arm to swing to blocks or yank a player to you. Not built.
- **Revive once**: Pochita's heart brings you back the first time you die, then a cooldown. Not built.
- **The ult: blood frenzy.** The big burst version: you go berserk, every hit heals a lot, saws do double damage,
  you can't be slowed, red screen, the engine screaming, ~15 s. **No cutscene** for the ult (kortev: a frenzy doesn't
  need one), just a hit as it kicks in (roar, red pulse, the eye flaring). Not built. (A "shark ride" ult from the
  Reze arc was liked as a possible second ult later.)

### The look (decided after several rounds; keep it)

- **The head** is a chainsaw's engine with a face, built from anime stills kortev sent (they are not in the repo:
  the description is in `tools/chainsaw_model.py`'s docstring and its constants). An **orange visor shell over the
  front of a black engine** (the orange goes on the outside), four curved vent slots on each side whose inner two are
  the glowing yellow eye, a ridge along the top, a jaw plate that is a V from the front and ends in a downward point,
  long straight white teeth in an open mouth (a dark hole behind them, not a wall), the neck and cheeks bound in
  wire tendons, and on the black engine: notched side covers with a round socket, a ledge, a nub, a screw, a short
  chain hanging down each side behind the visor, stepped armour plates on top, a bolted back plate, a finned
  cylinder, ribs down the back of the neck, a rear D handle, a pole sticking up behind the bar, hoses. The bar comes
  straight out of the front of the visor, long, white and bloodied.
- **The arm saws**: centred on the arm, the blade's width running **top to bottom** (teeth along the top and bottom
  of an arm held out, flat faces to the sides), starting just below the shoulder, coming **through the middle of the
  fist** (the fist split, bloody gashes), and reaching far past it.
- **Style**: flat anime colours with dark ink lines, at chunky pixel density to sit with a Minecraft skin. kortev
  rejected the earlier realistic, scratched, grimy baked textures outright ("do NOT fit minecraft AT ALL OR even the
  style of csm"). The game texture is now painted straight from the mesh (see below), not rendered.
- **Not too boxy, not too smooth**: a middle ground; rounded, bevelled shapes on a blocky body.

### kortev's skin

They're redrawn in the Public Safety uniform, keeping their own hooded shark-onesie head: `tools/kortev_skin.py`.
Their original skin is `tools/skins/kortev_onesie.png`; the final one they settled on is
`tools/skins/kortev_public_safety.png`, made with
`python tools/kortev_skin.py tools/skins/kortev_onesie.png OUT.png --hood --under`. kortev disliked two earlier
attempts that invented a new hair shape: if you touch the skin again, stay close to their own pixels and ask first.

## How it's built

### Files (`chainsawman/`)

| Piece | Where |
|---|---|
| Mod entry, item, sounds | `src/main/java/.../ChainsawMan.java`, `PochitaItem.java`, `ChainsawSounds.java` |
| Server state, timeline | `form/Form.java`, `form/FormTimeline.java` |
| Payloads | `network/PullCordPayload` (C2S key), `FormPayload` (on/off), `FormPullPayload` (someone pulled) |
| Client entry, key | `src/client/java/.../client/ChainsawManClient.java` |
| Who's in the form, pulls, idle sound | `client/ClientForms.java`, `FormIdleSound.java` |
| Drawing the form | `client/FormFeature.java` (feature renderer: parts on bones, chain frames, growth, the cord), `FormMesh.java` (loads `form.csm`) |
| Cinematic | `client/FormCinematic.java` (beats' particles, your camera shots, shake, input hold, impact/vignette timing), `CordPull.java` (body poses), `FormHud.java` + `shaders/core/csm_impact.*` |
| Mixins | `client/mixin/`: hide head/hat/helmet/head items, first-person saws, pose, camera, FOV |
| Assets | `form.csm` (mesh), `textures/entity/form.png`, sounds, lang |

### The model pipeline (`tools/chainsaw_model.py`, Blender as a Python module)

- Needs Python 3.11 with `pip install "bpy==4.5.*" numpy pillow` (keep the venv outside the repo). kortev's PC has
  Blender 4.3 instead, which runs it as `blender -b --factory-startup --python-use-system-env --python
  tools/chainsaw_model.py -- --game` (Pillow put on `PYTHONPATH`), but **4.3 unwraps the painted parts differently
  from 4.5**: a full 4.3 export changes the approved look. On 4.3, export to a scratch copy and splice only the parts
  you changed into the committed `form.csm` (the chains were done that way; their swatches sit in the same place).
- `--out DIR --renders [--only hero,front,side,head,head_side,head_back,arm_saw]` renders preview stills (Cycles,
  realistic materials, for judging shape). `CSM_SAMPLES=12` for a quick look.
- `--game` builds the rest pose, unwraps, **paints** the texture (`paint_toon`: flat colour per material from the
  `TOON` table, a light/shadow tone cut against `TOON_LIGHT`, ink on edges sharper than `INK_ANGLE`, banded cords,
  blood splats from 3D noise; small many-pieced parts take flat swatches instead, `SWATCHED`), and writes
  `chainsawman/src/client/resources/assets/chainsawman/meshes/form.csm` and `textures/entity/form.png`. It takes
  about 15 seconds. Commit both outputs.
- `form.csm` format (CSM2) is documented in `export_game`'s docstring: parts with the bone they follow (`head`,
  `body`, `right_arm`, `left_arm`), an optional grow root/axis for saw parts (`*_bar`, `*_chainK`), and quads.
  Each chain is exported in 12 frames (`*_chain0..11`), each a quarter of a cutter further round its bar; the game
  shows one at a time to run the chain (it counts the frames from the mesh). Their blood is on the links, not on a
  place (`chain_tones`, `CHAIN_BLOOD`: a fleck of blood on some links, about as much as before), so it runs round with the chain; the
  12 frames are the chain moving one whole pattern on, so they loop without a jump. A one-quad part named `rope` only carries the cord's colour.
- Coordinates: the build is in skin pixels, feet at the origin, facing -y, z up; `to_game` maps to each bone's
  model space.

### Sounds

`python tools/gen_chainsaw_sounds.py [form_cord form_start form_idle form_stop]` (NumPy, SciPy, ffmpeg; uses
`tools/gen_sounds.py`'s helpers). Its beat constants mirror `FormTimeline`; if you retime the timeline, change them
too and regenerate. Commit the `.ogg`s.

### Testing

- **CI is your compiler**: push and read the `build` workflow (`build` + `gametest` jobs). The `build` job also runs
  `.github/scripts/check-jars.py`, which checks each jar holds its own assets and a refmap for every mixin config.
- **Seeing it**: run the `selftest` workflow by hand with `test` = `form`. It photographs a stand-in of kortev (their
  skin from `tools/skins/`) in the form from all round, with the form off, the stand-in pulling the cord frame by
  frame (`form_pull_NN.png`), and **your own transformation from your camera** (`form_cine_NN.png`), plus a
  first-person shot. Screenshots come back as the `selftest-screenshots` artifact. The test is
  `src/gametest/java/.../test/FormSelfTest.java`. (The game never draws your own player while the camera is another
  entity, so the test films a client-side stand-in; it also forces every outer skin layer on, which a stand-in
  otherwise never shows.)
- On a PC: `./gradlew runClient` plays as `kortev` and wears `run/skins/kortev.png` (`DevSkins`, only active with the
  `-Dshootingstar.devskins` flag the dev run sets; `seedSkins` puts the Public Safety skin there if nothing is).
  `./gradlew runSelftest -Pselftest=form -Pusername=kortev` runs the test locally; it makes its world first, as CI
  does (`selftestWorld`), so it quick-plays straight in.
- Through your own transformation your hand, the HUD and pop-ups are hidden until the camera is back in your eyes
  (`FormHandHiddenMixin`, `FormHudHiddenMixin`, `FormToastHiddenMixin`; the impact frames still draw). The test films
  it with the HUD on to show that.
- Yarn 1.21.1 names differ from Mojang's; check a doubtful one against the mappings before pushing.

## What's next, in order

### 1. A polish pass on the transformation (kortev said yes to all of this)

1. ~~**The head grows instead of swapping**~~ **done** (PUNCH, HEAD_OPEN; `FormFeature.punch`, `split`): for a few ticks before BURST, the bar punches out through the forehead of
   the player's ordinary head, then the head splits open in the blood and the chainsaw head is there. (Today it is
   swapped inside the blood burst and swells the last bit into place.) The human head is the player model's own; a
   way in is to keep it drawn until BURST and draw `head_bar` (grown from its root) on it from ~BURST-4.
2. ~~**Blood on the lens**~~ **taken out of the transformation.** It was built (flat chunky splats on the puller's
   screen at the burst, dripping, dissolving pixel by pixel before the roar) and kortev tried it: in the
   transformation it looked bad and arcade-y and isn't needed. **But kortev wants it kept** as a possible start for
   how the gameplay looks: blood on your screen when you kill things with the saws. The code is saved in the history:
   `git show e85a346:chainsawman/src/client/java/io/github/kortev/chainsawman/client/FormLens.java` (it was drawn
   from `FormHud.render`). Ask kortev before using it, and rework the look rather than reusing it as it was.
3. **Manga sound-effect lettering on the impact frames**: big brushed katakana for the engine's roar (e.g. ヴゥン)
   slammed across the frame.
4. **The eye ignites at the roar**: dark until ROAR, then a flash as it lights. (The eye is the glowing `eye`
   material; flag bit 0 in the mesh.)
5. **The cord on the chest all the time** while Pochita is carried (needs the server to tell clients who carries
   Pochita), so the yank grabs something already there; and make the stretched cord read better in the puller's own
   camera.
6. **Sparks and exhaust**: sparks off the chains while they race (swinging), exhaust from the back of the head while
   the engine revs.

(Also offered, not yet chosen: a voice clip at the roar, which kortev would have to pick, as the Domain uses a clip
they chose.)

### 2. Turning back

The transformation in reverse: the saws slide back into the arms and head, the engine dies (`form_stop`), a last
splash, your own head back. A shorter beat sheet in `FormTimeline`, the same machinery.

### 3. Then the abilities, one at a time (ask kortev about numbers before balancing)

Attacks, then blood healing with the health drain, the chain grapple, the revive, the frenzy ult. Each one: build,
test, commit, push, show kortev, then the next.

### Later: Pochita as a real animal (kortev's idea, 2026-10-19; not designed yet)

kortev wants Pochita to stop being a crafted item:

- Pochita is **an animal in the world that you tame**.
- A tamed Pochita can be used as an **ordinary chainsaw weapon**.
- If you **die while you have it**, you come back **as Chainsaw Man**, the way it happens in season 1. You no longer
  need the item to be the form.

This changes the decided design above: how you get Pochita (no recipe), what starts the form (death rather than only
the cord), and the "revive once" power, which it largely becomes. Before building any of it, ask kortev the open
questions: where Pochita spawns and how you tame it; whether the cord still transforms you after that first death;
what happens to Pochita (the weapon) once you have merged; and whether only kortev can tame it, as only kortev can
craft it now.

## Not checked yet

- The sounds by ear in the game (only checked as files).
- The form and the impact frames with a shader pack (Sodium + Iris + Complementary). Full-screen and in-world effects
  must go through `WorldPasses` or the HUD, never straight on `WorldRenderEvents.LAST` (a pack paints over that).
  `FormHud` draws in the HUD pass.
- Multiplayer: another player watching a transformation (should work: everything is driven by payloads to all).
