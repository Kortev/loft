# Handoff: kortev's Chainsaw Man mod (state on 2026-10-22)

You are picking this up with nothing but this GitHub repository (`Kortev/loft`). No chat history, no files from
kortev's PC, and none of the reference images they sent come with it. Everything you need is in this file and the
repo. Read it all before doing anything, then read the repo's own `HANDOFF.md` at the root for the wider project.

## Start here: where things stand (2026-10-22)

kortev moves between Claude accounts when one runs out; this section is what the next session needs first. The rest
of the file is the full record.

**Done (2026-10-21): Pochita's spot in the world, redone** (kortev: "this hollowed log looks like shit"; for the mod
itself, not for a reel). kortev picked **a big fallen tree** (over a den under a rock, or a junk pile), with **a blood
trail leading to him** and **signs of a fight** round it; they approved the second pass from photos.

- `PochitaPlace.den` builds it, used by `choose` (the world's own Pochita, placed once per world) and by `waiting`
  (`/chainsaw pochita`, `/chainsaw scene`): a clearing levelled and cleared in among the trees; the roots torn up at
  one end, standing on edge over the hole they left (packed dirt in the middle, mangrove roots through the ring round
  it, a ragged rim, hanging roots under); a round trunk (a plus-shaped slice) raised a block off the ground from the
  roots to near its middle, so he lies in the gap under it, then down on the ground to the break; moss carpet over
  it, vines down its sides; a jagged snapped-off end with bare oak fence posts as splinters (placed last with
  `FORCE_STATE`, or they join up into a fence); the crown down in a heap of persistent leaves past the break; broken
  branches, scattered leaves and streaks of coarse dirt (the fight); a trail of redstone-dust drops in to him.
- **Where** (`choose`): 32 tries 250-450 blocks from spawn for dirt ground in a forest, taiga, plains or meadow
  (`wooded`) that's level (`level`: 8 samples round the footprint, at most 2 more than 4 blocks off); if none,
  `woods` asks the world for the nearest such biome (`locateBiome`, 3200 blocks) from a point in that ring and looks
  round in it for a level spot (its nearest edge is often hilly); then any dry level spot from the ring; then spawn.
- **Old worlds**: the saved state has `Built` (0 for the old log) and `Axis`; on server start an old one is made over
  into the fallen tree once round the same spot (blocks only; Pochita stays), unless he's already someone's heart.
  kortev's survival world (`run/saves/New World (23)`, Pochita at x 123, y 118, z 379) will get this the next time
  it's opened: **not yet seen working** (the test world's Pochita is already a heart, so the test can't show it).
- **Photograph it** with `./gradlew runSelftest -Pselftest=reel1 -Pusername=Denji -Ptarget=den`: it uses the world's
  own tree if it has one, or builds one with `woods` near the test player (who moves 40 blocks on each run, since the
  test world keeps everything) and saves `build/selftest/screenshots/reel_den_*.png` (wide, along the trail, under
  the trunk, the roots, the break, the other side). The cameras stand inside the clearing: further out is in the trees.

**In progress: showcase reels** (2026-10-22). kortev judged reel 1 (the episode recreated, below) would lose viewers
long before its chainsaw part and dropped the idea: the reels are now **mod showcases**, one feature each, about
10 s, filmed tall (1080x1920) in a village at dusk, "CHAINSAW MAN MOD" in orange over every one, no music (kortev adds
a trending sound when posting). Only **what he does** (kortev: don't show the ult). The test is
`src/gametest/.../test/ShowcaseSelfTest.java`: `./gradlew runSelftest -Pselftest=showcase -Ptarget=<reel>
-Pusername=Kortev -Pcapture=true`, then `mux-audio.py` as below (stills: `build/selftest/screenshots/showcase_*.png`).
Done and approved: `transform` (the cord pulled among zombies, the combo, ends on "What should I add next?" for the
comments: kortev wanted that question on the one likeliest to spread), `saws` (grinding out through a hut wall, the
lunge, the combo, a grind; ends on a kill, to loop), `chain` (two zombies yanked off a tower and cut down in the air,
then up the tower on the chain; the flight down is carried by the test, as the yank alone doesn't clear the edge),
`arms` (two vindicators chop both arms off, the head's saw kills grow them back), `pochita` (along his blood's trail
to the fallen tree, your blood into him, he wakes, picked up and held as a chainsaw, a husk cut down; a husk, as a
zombie burns at dusk), `merge` (kortev: question hook + emotional beat + payoff; "What happens if you die holding
Pochita?" over you in a horde, killed, the mod's own merge, the pull, the horde cut down). The merge reel is filmed
whole (41.5 s) and **cut by hand** to 19.3 s: after `mux-audio.py`, keep 0-2.95 s (hook, death), 23.9-32.45 s (his
"show me your dream" line through waking) and 33.65-41.4 s (the pull to the end and its question), joined with
ffmpeg's trim/atrim and concat (50 ms audio fades at the joins); if the merge's timeline changes, find the cuts again.
All six approved by kortev, then a second pass (2026-10-22, from my review, kortev: "do all of it"): **every reel
opens on a hook** (`hook()`, two lines, white then orange) **and ends on a question for the comments** (`ask()`,
kortev: comment-farm on all of them, not one); `transform` is now **your own eyes** ("POV: you're Chainsaw Man": the
mod's own transformation camera, then three kills with the real attack key, then both saws revving), as the merge
already shows it from outside; endings differ (saws: one ground down, no combo; arms: both saws revving; chain: a
third flung up from the ground below and cut at the tower's edge); Pochita starts nearer him. All reshot with the eased
head turns. kortev approved the second pass to post (2026-10-22). `held` photographs Pochita held (with `-Pcapture=true`, a video of
him in use with the real attack key). kortev: the head must move like a real person's, especially in first person (no
snapping round: eased turns, the body lagging the head, a little sway). Lessons: a camera straight in front makes
the head's saw a spike at the lens (always three quarters on); cameras closer than ~2.6 blocks drop into first
person; the test world keeps entities between takes (`strays` clears them); grass between a low camera and you hides
you. kortev offered to ask YouTube Studio's AI for advice; nothing back from it yet.

**Done (2026-10-22): Pochita held is a real chainsaw** (kortev's picks): his 3D model in both hands at your waist
(`HeldPochita`: a feature for everyone's view, the arm pose in `FormPoseMixin`, his item model shrunk to nothing in the
hands so the flat picture is only his icon); no click swings: holding attack revs him (`Saw.REV`) then cuts in front
continuously (`Saw.HELD_BITE`, about his old sword damage a second), kicking back; wood and plants cut fast (his
`tool()` component); the engine idles while held, screams while cutting. **To fix later (kortev: "looks really bad from
the first person")**: his first-person view (`HeldPochita.renderFirstPerson`, the `VIEW_*`/`ARM_*` numbers): ask
kortev what they want it to look like before reworking it.

**Done (2026-10-22): Beam replaces the frenzy as the ult, second version** (built overnight from kortev's picks; kortev said the
first looked nothing like Beam and was useless, so this one is his real look and a moveset of his own; kortev has not seen
it yet: judge it from the photographs). The ult key (B, `ChainsawManClient.FRENZY`, `FormActionPayload.FRENZY`, "Beam (ult)"
in the lang file) summons **Beam, the shark fiend in his big shark form** (the Reze arc), for `Blood.BEAM` = 60 (the blood
bar's mark moved to it; `Frenzy.java`, its game test and `Blood.FRENZY` stay as dormant code). In the form and able to fight,
he bursts up out of the ground under you in a spray of the ground's own blocks (he needs a clear space about 2 wide and 2.4
tall within 3 blocks of you, else the action bar says so and nothing is spent) and you stand on his back; one to a player,
20 seconds (`Beam.LASTS`), gone when you leave the form or go down, and he dives back into the ground when he goes.

- **His look** (`client/BeamModel.java`, `BeamRenderer.java`, texture `textures/entity/beam.png` painted by
  `tools/gen_chainsaw_textures.py`'s `beam_entity`, 256x160, box corners in `BEAM_BOXES` mirroring `BeamModel.corner`; the
  layout was packed by a throwaway script, change both together; third pass 2026-10-22 from kortev's review of the stills): Minecraft
  boxes at Pochita's pixel density, a huge heavy torpedo about 5 blocks long, 2.2 deep and 2 wide, stepped boxes (narrow
  back and belly, wide middle, head tapering to a blunt snout), slung low: his belly 0.8 blocks off the ground, his back 3.
  **Four thin crab legs** out of the lower sides of his body (front pair just behind the head, back pair in the rear third):
  the upper part out sideways and up past his flank to a high knee, the shin down and in to a point on the ground (feet 1.5
  times his body width apart), that walk a gait from the ground he covers (diagonal pairs) and tuck in the air. Navy on top,
  mint white over the whole lower half of body and head (up almost to the eyes) with a sawtooth line from snout to tail; a head
  two fifths of him with a huge gape (dark red inside, pink gums, many small cream sawtooth teeth painted as flat rows along both
  jaws) over a **lower jaw that snaps** on a bite; **three eyes** (white, big black pupil, a pixel proud) in a diagonal row up each
  side above the back corner of the mouth; five dark back spikes, gill lines, a crescent tail that sways, small pectorals.
  **The chain is his reins**: `ChainRender.reins` draws two strips of the form's chain from the rider's hands (low in front of you
  in your own eyes) to the corners of his mouth. The rider stands on the middle of his back (`SawPose.ride`: knees bent, feet
  apart, arms forward on the reins, or held out grinding).
- **Riding** (`form/Beam.java`; `BeamEntity` is a plain `Entity`, not saved, server-driven: `getControllingPassenger()` is
  nobody; hitbox 2 x 2.4; the game's own collision, `getStepHeight()` 2, and his own gravity): the forward key walks him the
  way you look (turning 12 degrees a tick, up to 0.6 blocks a tick, back stops, strafe nudges); he steps up a block or two and
  stops at a wall. Your keys while riding (an action-bar line shows them with your own key names, at the start and again at
  2.5 s):
  **attack = bite** (`FormActionPayload.SLASH` goes to `Beam.bite` through `Saw.slash`; no swing is predicted on the client):
  everything in a cone about 3.5 blocks in front of his mouth takes 16 of **your** `playerAttack` damage (kills are yours
  through `Saw.init`'s `AFTER_DEATH`), survivors are flung (1.2 up, 1.5 forward as velocity), at most every 12 ticks (a click up
  to 2 ticks early still counts, as for the slash); **hold attack = your saws grind** from his back as before;
  **use = charge** (`LUNGE` goes to `Beam.charge` through `Saw.lunge`): 9 blocks in 8 ticks, 12 damage once to each thing in
  the way, flung aside, every 30 ticks, with the lunge's speed lines and wider view (`ClientCombat.lunging` reads his
  `sinceCharge`); **jump = leap**: about 4 up and 8 forward on ballistic physics, crashing down in a ring of the ground's
  blocks with 16 damage to everything within 3 blocks (every 40 ticks); **sneak = get off** (he dives). The chain key is off
  while you ride. A rider spared the fall of getting off in the air (`SAFE`), and the `IN_WALL` damage of his head in a roof.
- **Sounds** (synthesised, `tools/gen_chainsaw_sounds.py`, unchanged from the first version): `beam_burst`, `beam_swim` (now
  his pounding feet, looped, louder with his speed, silent standing), `beam_bite`, `beam_dive`. **Only checked as files, not by
  ear.**
- **Tests**: ten Beam game tests in `FormGameTests` (86 in all, all passing): summon, refused, walks the way you look,
  stops at a wall, the slash becomes a bite (16 damage, flung, behind untouched, no second at once), the lunge becomes a charge
  (dashes, 12 once, flung aside), leap, dives on sneak, on leaving the form, after 20 s. `./gradlew runSelftest -Pselftest=showcase
  -Ptarget=beamlook -Pusername=Kortev` photographs him in daylight (`build/selftest/screenshots/showcase_beamlook_*.png`: side,
  front, behind, mouth with a husk in front, two bites, the charge, the leap, walking, your own eyes).
- **Not verified**: the sounds by ear; how his motion *feels* (the glide between the server's positions, turn rate, stepping up
  over trees and buildings, the camera on his back in first person: only photographed and logged); multiplayer; a shader pack.
  **Not like the stills**: his head and teeth are boxes (the stills' head is smoothly rounded; the lower jaw is three steps, so it
  reads as a staircase when open), the white is flat mint without the stills' shading, the legs have no flat blade tips, the
  rider is as tall as he is deep (the stills have a smaller rider), and the reins run steeply down to the mouth corners. The reel
  `beam` in `ShowcaseSelfTest` still expects the old automatic bites (he now bites only when the attack key is pressed), so it
  needs a `Saw.slash` call per bite before it is filmed again, and its cameras were set for the smaller Beam.

**Waiting on kortev**: nothing on reel 1 any more (dropped for the showcases).

**kortev's queue of ideas** (ask before building any): the ult is Beam now (above; the frenzy was dropped for looking the
same as normal attacks); the merge dead
a while first, as reel 1 has it (below, "Later ideas"); the black chainsaw (build nothing until kortev describes
it); the "I'll give you my heart" clip. Older open items: the shader-pack check (needs
kortev's OK to download Fabric 1.21.1 Sodium + Iris), the multiplayer test (written, never run), balance after play,
the stretched cord in your own camera, the chain's drag in water (not filmed), hearts against the blood bar.

**How kortev works with you right now**: often on their phone, remoting in, unable to test. Send videos and pictures
(the app's send-file tool) of every change, short and plain. The recipes:

- Game tests: `./gradlew runGametest` (84, all passing; `aimedHitsLand` flaked once in nine runs).
- Self tests with pictures and video: `./gradlew runSelftest -Pselftest=form -Pusername=kortev -Ptarget=...`
  (`merge`, `limbs`, `pvp`, `kill`, `scene`, `chain`, ...) and `-Pselftest=reel1 -Pusername=Denji` (the reel;
  `-Ptarget=cast`, `set`, `den` for pictures). Add `-Pcapture=true` for a video with its sounds, frame by frame, into
  `build/selftest/capture`; then `python .github/scripts/render-loops.py build/selftest/capture` and
  `python .github/scripts/mux-audio.py build/selftest/capture out.mp4`.
- Video needs `ffmpeg` on the PATH of the shell that runs gradle. This PC has none installed: copy Python's
  imageio-ffmpeg binary (`python -c "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())"`) into a folder
  as `ffmpeg.exe` and put that folder first on the PATH. `mux-audio.py` also wants `ffprobe`, which that package
  lacks: in a copy of the script, read the video's length off `ffmpeg -i` instead.
- On this PC, bash here-documents containing apostrophes fail in the tool shell: write edits as Python scripts to a
  file and run them.

**Things learned the hard way**: client-only stand-in players never get the mod's downed pose (their pose stays
standing; the reel lays them down itself); spectators see invisible entities (don't test hiding things as a
spectator); the test world keeps everything between runs (the reel clears its set's entities at setup and takes your
heart away again); a model poking out through something drawn rather than built (the reel's dumpster) looks like a
stray entity: log the entities near it before chasing one.

## Where the work is

- **Branch: `claude/chainsaw-man`.** All of this mod's work is there and nowhere else. It is **not merged into
  `main`**, and `main` is far behind it. Work on this branch (or a branch off it); push to it.
- It was cut from `claude/gojolander-domain` (open PR Kortev/loft#3: the friend's mod) with
  `claude/iris-fullscreen-effects` (open PR Kortev/loft#2: shader-pack support, `WorldPasses`) merged in, because
  neither PR is merged yet and this mod needs both. Those two PRs conflict in one file
  (`gojolander/src/client/resources/gojolander.client.mixins.json`); it is already resolved on this branch.
- **No PR and nothing into `main` without kortev's OK.** Merging is their call.
- CI (`build` workflow: `build` and `gametest` jobs) was green on the branch when last checked. All 76 game tests
  pass locally (2026-10-20).

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
2. **One piece at a time, committed and pushed as soon as it works.** Get each piece in the game first even before
   it does anything, then add each ability on its own. Commit (one topic, a subject in plain words, a body that says
   why), push, then start the next piece.
3. **Ask before design calls** (what a power does, balance, PvP, how something looks); decide technical calls
   yourself. Keep messages short and plain. Refer to kortev as "they".
4. **Show renders/screenshots before baking or shipping a look**, and match the anime. kortev reacts strongly to
   things that look invented instead of matched; when they send references, follow them exactly.
5. **End a round of work with a numbered list of what to test in game.**

(There was once a rule against naming the mods in public content; kortev dropped it on 2026-10-20: naming them is
fine.)

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
  (catch, cough, burst, roar, settling to idle), the idle loop while transformed, the engine dying; and for the fight:
  slash, hit, grind loop, chain, frenzy kick and its loop, going down, coming back up.
- **The fight** (built 2026-10-19 with the numbers kortev picked; all in `form/`, the server owns it, `FormEventPayload`
  tells every client what to show):
  - **Saws** (`Saw`): the attack key in the form is a slash, not a punch: a combo of three (right arm across, left arm
    back, both overhead and down), 10 damage each, the third 14 with more knockback, through everything in a cone in
    front (3.5 blocks), at most one every 12 ticks. **Held**, the attack key grinds: both saws out, 2.5 damage every 4
    ticks to what is in front, slowing it. On a block, holding it mines (cutting through). Blood and sparks on every
    hit. Armour stands don't count (nothing to drink).
  - **Blood healing**: a slash that hits heals half a heart, every third grinding bite that hits half a heart, a kill 3
    hearts. **Health drain**: in the form you lose half a heart every 3 s.
  - **Never dying** (`Downed`), with Pochita carried, in the form or not: what would kill you leaves you down instead:
    lying flat (the crawl pose), arms and legs gone (raw stumps), unable to move, fight or use anything, and nothing
    hurts you. Someone crouching by you and holding use on you for 3 s gives you 3 hearts of their own and you're back
    up; alone, you come back after 10 minutes. Saved with the player (a data attachment), so leaving doesn't skip it.
    The void and `/kill` still kill. The drain running out puts you down too. This replaced "revive once".
  - **Chain** (`Grapple`, key R): fired the way you look, 24 blocks, catching anything within half a block of the
    aim; a living thing is yanked to you (2 damage), a block pulls you to it (no fall damage after); every 3 s. Drawn
    as steel links (`ChainRender`, `form_chain.png`).
  - **Blood frenzy** (`Frenzy`; no longer on a key: Beam replaced it as the ult, 2026-10-22): 15 s of double saw damage, 4 times the healing, no Slowness, cobwebs and berry
    bushes don't slow you; the screen pulses red, the engine screams, the eye throws sparks. Then 5 minutes to recharge.
  - **Animations**: third person `SawPose` (combo, grind, chain throw, frenzy stance); first person `FirstPersonSaws`
    shows both saw arms and plays the same moves (`FirstPersonFormMixin` takes over the hand; eating or a shield
    still shows normally).

### Second round (2026-10-19, kortev's picks from a list of improvements)

- **Heavy hits**: a hit holds the swing still a moment (everyone sees it), kicks your camera, punches the view in.
- **Brutal kills**: a mob killed by the saws bursts into blood, flesh and bone and is gone (`Saw.tearApart`). Not yet:
  actually cutting the body in two.
- **The lunge**: attacking while sprinting throws you forward headfirst, the head's saw cutting what you run
  through (12 damage, once each; 10 blood).
- **The chain held** (`Grapple`): what it catches is yanked to you and bound within 4.5 blocks for 5 s; caught in a
  block you **swing** from it (the client moves you: `ClientCombat.swing`), reeling in, until you jump or press R.
- **His stance** (`SawPose.stance`): arms a little forward and out standing; bent in, arms back, running.
- **Blood as fuel** (`Blood`, bar over the food bar, `BloodHud`): 0 to 100, kept with the player. Gains: 8 a hit, 3 a
  grinding bite, 25 a kill, and out of the form a point every 2 s. Costs: the cord 25 (the engine won't start
  without it), the chain 15, the lunge 10, the frenzy 60. These replaced the chain's and the frenzy's cooldowns.
- **Losing arms** (`Limbs`): in the form every 6 hearts of harm takes an arm off (right, then left), its saw with
  it, a stump at the shoulder. One arm: three quarters damage; none: the head's saw alone, half. A kill grows one
  back; out of the form or back up, both are back.
- **Pochita the animal** (`PochitaEntity`, `PochitaModel`): rare in the wild (forests, plains, meadows), hurt; tamed
  as Denji did, by giving him your blood (hold use on him, 4 s, half a heart a second; kortev only). Tamed he follows
  and sits; crouch + use picks him up as the Pochita item, a chainsaw weapon (`PochitaItem.weapon`); used on the
  ground he's set down. **Carry him when you die** (kortev, first time) and he becomes your heart (`Heart.MERGED`,
  kept for good): back whole, full of blood, transforming. From then on only the heart matters: the cord and never
  dying are the heart's, not the item's. The crafting recipe is gone.
- **To try it on a PC**: `./gradlew runClient` (plays as kortev); survival, cheats on; `/summon chainsawman:pochita`
  (or the spawn egg), hold right-click on him till he's yours, crouch + right-click to pick him up, then
  `/damage @s 100` to merge (not `/kill`: that one still kills). Then G transforms.

## The design kortev approved (don't change it without asking)

### The powers (all built; the numbers are above, chosen by kortev: both kinds of attack, medium drain, downed with
a way out, the frenzy every 5 minutes)

- Transform: Pochita carried + the cord key.
- **Chainsaw attacks**: heavy slashes and holding to grind, with new attack animations instead of a punch (kortev
  asked for the form's combat to be a full overhaul of the game's).
- **Blood healing** (the passive) and the **health drain** (the cost, what keeps it fair against the friend's Domain).
- **Chain grapple**: to blocks, or yanking a player or mob to you.
- **Never dying**: kortev's own idea, replacing "revive once": down instead of dead until someone gives you blood.
  Overpowered (you can't die) and a real weakness (alone you're stuck for 10 minutes).
- **The ult: blood frenzy** (replaced by Beam, 2026-10-22; see "Start here"). **No cutscene** (kortev: a frenzy doesn't need one), just the kick as it starts. (The
  "shark ride" ult from the Reze arc was liked as a possible second ult: it became the ult, Beam.)

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
| Mixins | `client/mixin/`: hide head/hat/helmet/head items, first-person saws, pose, camera, FOV, the attack key (`FormAttackMixin`), the downed body; common `mixin/`: the downed pose, the frenzy ignoring slowing blocks |
| The fight (server) | `form/Saw.java`, `Downed.java`, `Grapple.java`, `Frenzy.java`; `network/FormActionPayload` (C2S: slash, grind, chain, frenzy), `FormEventPayload` (S2C: what everyone should see) |
| The fight (client) | `client/ClientCombat.java` (state, keys, loops), `SawPose.java`, `FirstPersonSaws.java`, `ChainRender.java` |
| Tests | `src/gametest/.../FormGameTests.java` (the fight, server side); `FormSelfTest` photographs it (`fight_*`, `fp_*`, `tp_downed*`) |
| Assets | `form.csm` (mesh), `textures/entity/form.png`, `form_flesh.png`, `form_chain.png`, sounds, lang |

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
`tools/gen_sounds.py`'s helpers). Its beat constants mirror `FormTimeline` and `MergeTimeline`; if you retime a
timeline, change them too and regenerate. Commit the `.ogg`s. (Writing a long track, like the 31 s `form_merge`, in
one go crashed libsndfile here without a word; the script writes in chunks.)

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
  does (`selftestWorld`), so it quick-plays straight in. `-Ptarget=` films one part only: `merge` (the merge by
  tick, `form_merge_wait_*`, the pull, then turning back `form_back_NN`), `cine` (your own transformation),
  `ring` (the form from round about), `meshhead` (the same with the old mesh head), `limbs`, `pvp`, `kill`,
  `chain`, `scene`; add `-Pcapture=true` for a video of it.
- Through your own transformation your hand, the HUD and pop-ups are hidden until the camera is back in your eyes
  (`FormHandHiddenMixin`, `FormHudHiddenMixin`, `FormToastHiddenMixin`; the impact frames still draw). The test films
  it with the HUD on to show that.
- **Trying it all in the game** (`ChainsawCommand`, operators): in a new world with cheats on (superflat is best:
  it flattens and clears a 33-block square round you), `/chainsaw scene` builds a test ground (a row of mobs standing
  still in front of you, a wild Pochita fainted under a fallen tree to your right), puts you in survival with full blood and
  without Pochita's heart, and lists what to try. Then `/chainsaw mobs | pochita | heart | reset | blood | down | up`;
  and `/chainsawhead boxes | mesh` (client) switches between the two heads. Film it with `-Ptarget=scene`.
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
3. ~~**Manga sound-effect lettering on the impact frames**~~ **done**: ヴゥン in heavy black pixel strokes, white
   outline, blood-red shadow (`textures/gui/form_roar.png`, painted by `gen_chainsaw_textures.py`'s `roar`), each
   letter slammed in a tick after the last round the head (never over the face), shaking, gone 16 ticks after the
   roar (`FormHud.lettering`, in your own transformation only).
4. ~~**The eye ignites at the roar**~~ **done**: dark through the transformation, then at ROAR a pixel star flares on
   each eye and dies away (`FormFeature.flare`, `textures/entity/form_flare.png`, drawn added on; the eyes are the
   mesh's glowing vertices, flag bit 0). In a frenzy the eyes burn red, throbbing.
5. ~~**The cord on the chest all the time**~~ **done** for whoever has the heart: the server tells everyone who
   has it (`FormEventPayload.HEART`, on joining and at the merge), and `FormFeature` draws the cord on their chest
   until it is grabbed. (Still open: making the stretched cord read better in the puller's own camera.)
6. ~~**Sparks and exhaust**~~ **exhaust done**: light grey puffs off the top of the pole behind the bar, now and then
   idling, more while the saws work or in a frenzy, a burst at the roar (`ClientCombat.exhaust`; black smoke read as a
   blob of hair on the head, so it's the game's white smoke). Sparks come off what the saws hit (the server's hit
   particles); none off the chains in the air.

(Also offered, not yet chosen: a voice clip at the roar, which kortev would have to pick, as the Domain uses a clip
they chose.)

### 2. ~~Turning back~~ done

The cord pulled again in the form turns you back (`FormTimeline` BACK_*, `Form.TURNING`, `FormEventPayload.TURN`):
the engine dies, the arms' saws slide back in through the fists while the arms are held out shaking, the head bows
as its saw goes in, and at BACK_SPLASH (14 ticks) the chainsaw head comes away in a splash of blood, your own head
thrown back. No fighting while it plays. The merge's chat line now says the cord does both.

### 3. The abilities: built (see "The fight" and "Second round" above). Next is kortev trying them in game and
tuning the numbers.

### Pochita, settled with kortev (2026-10-20)

- **One per world** (`PochitaPlace`): placed once, fainted under a big fallen tree in woods or grassland 250-450 blocks from spawn (see "Start here"); kortev
  hears him whimpering within 40 blocks; lost for real (void, /kill), he's back there 2 minutes later; never again
  once he's someone's heart. No natural spawns, no recipe.
- **Can't die**: faints (on his side) until kortev gives him blood again (`PochitaEntity.WAKE`).
- **Merging**: the first death with him **carried or tamed within 32 blocks** (`Merge`) plays the anime's scene, long
  (about 31 s), its beats in `MergeTimeline`'s table, the pictures in `MergeCinematic`, the sound in `form_merge.ogg`.
  You go over and the colour drains; then **the dream** (`MergeCinematic.dream`): the world falls away into black fog
  (`DreamFogMixin`, no sky or clouds `DreamSkyMixin`, nothing else drawn `DreamEntitiesMixin`), you lying in a pool of
  light (`dream_floor.png`), motes drifting. Pochita limps out of the dark, sniffs you, climbs onto your chest and
  they talk (the anime's voices); over his "I always loved hearing you talk about your dreams" come **the memories**
  (`MergeMemories`): four little scenes built of real blocks floating far up in the void, sepia (`csm_impact`'s
  Sepia), you in your own skin and him in his model: the shack holding him, bread with him looking up, asleep with him
  curled beside you, him as the chainsaw against a zombie. Then he springs up off you and plunges into your chest,
  leaving his tail, the cord, sticking out of you; your heart starts in the black; you wake through your own eyes; you
  get up and stand there with "[G] Pull the cord" until you pull it yourself, as Denji does (first pull free; after
  `Merge.WAIT`, 60 s, it pulls itself).
- **The merge's third pass, from a storyboard (2026-10-20)**: kortev wants the merge to be close to the best part of
  the mod, and found it full of problems (the camera inside heads and blocks, Pochita floating over you, a confusing
  see-through heart shot, a weak start and end). I drew a storyboard of 18 cuts (an artifact:
  https://claude.ai/artifact/1oJPh3jRN7Um3y6a9Z6Asq), kortev said go, and `MergeTimeline`'s table is now those cuts.
  Every shot goes through `MergeCinematic.framed` (pulled in toward what it looks at if a block is in the way) and is
  placed a step off you and him; when the camera is in your own eyes (`ownEyes`) your head isn't drawn
  (`HeadHiddenMixin`), which is what the grey blur waking up was (the inside of your hood). Pochita stands on your
  shirt (`SURFACE`, toward your middle at `ON` so his head is clear of your face), nudges your cheek with his nose
  (`HEAD_HALF`, `SNOUT`), sits to listen with his head on one side (`Pose.roll`, `sitting`). The see-through chest
  (`SeeThroughMixin`) is gone: he plunges in nose first (PLUNGE) and the cord is left (SUNK). Your heart starts in
  the black, three beats (`BEATS`), each a red flash with ドクン lettered (`Sfx.HEARTBEAT`, new glyphs in
  `form_sfx.png`); your eyes open on the real sky (EYES, the gasp at GASP); you rise (RISE, 18 ticks); and the last
  cut eases in on the cord. `form_merge.ogg` was regenerated to the new beats.
- **The merge staged again, for feeling (2026-10-20)**: kortev found the whole thing (talking, flashbacks, all of it)
  silly, though not too long. Up close, Minecraft's blocks read as comedy: so no close-ups of Pochita's face, no
  jaw flapping (his jaw rests a little open; `PochitaModel.lie` folds his legs), and he never stands on you. Wide
  shots in the dream's dark (its fog hides all past about 11 blocks, so he comes out of it); lit by your light as he
  nears you (`render`'s light by distance); he noses at you and lies down against you, chin on your chest
  (LIES), lifting his head to your face as you speak; up at your side for the deal, nodding with his words
  (`mouth`), a warm light behind him (`glow`, `dream_glow.png`) and his front in shadow; the flashbacks dimmer, wider,
  a warm light behind them. It has no music: music was tried (chords and a piano) and kortev
  wants it to feel empty, just you talking to him; only the dark's low hum under the voices.
- **The merge's second pass (2026-10-20)**: Pochita's mouth moves with his voice (`MergeCinematic.VOICE_LEVELS`, the
  clips' loudness tick by tick: measure them again if the clips change), his tail going, leaning in on "show me your
  dream", your head lifting to him as you speak; the memories move (rocking him, bites of bread, breathing, the saw
  shaking into the zombie, blood) under an old film's flicker, grain and dark edges (`csm_impact` Film), a flash at
  each cut; the dream has its own sound (a soft chord, a music box under the memories, a whoosh as he leaps, deep
  muffled beats inside your chest, the world rushing back and a gasp).
- **The voices in the merge** are the anime's own (Japanese), cut from a clip kortev chose
  (`sounds/form_merge_voice_1..5.ogg`): you ask if he took your body; he loved hearing your dreams; this is a
  contract; show me your dream; and you crying his name as he dives at you (at SINK). `MergeTimeline.LINES`,
  `LINE_LENGTHS` and `POCHITA_SAYS` time them as the clip paces them; the captions (`merge.chainsawman.line1..5`)
  are kortev's English, his warm and yours white. The clip says "this is a contract" where the caption also has
  "I'll give you my heart".
- **His look**: a Minecraft animal, boxes and pixels (`client/PochitaModel.java`, texture painted by
  `tools/gen_chainsaw_textures.py`'s `pochita_entity`, 64x64), after kortev judged the smooth anime render didn't fit
  the game. `tools/pochita_model.py` (Blender, anime style) stays only as a reference.
- On the black chainsaw kortev asked about: it is past the Reze arc; told only "Part 1, read on from chapter 53"; build
  nothing of it until kortev describes what they want from it.

### The fight's look, polished (2026-10-20, my passes while kortev slept)

- **Changed after kortev played it (2026-10-20)**: the lunge is **right-click** at nothing or at a mob (sprinting into
  an attack kept lunging by accident; at a block or sneaking, right-click does what it always did); the form's drain
  is halved (half a heart every 6 s) and the form makes you tough (10 armour, 6 toughness, `Form.ARMOR`), as you
  went down too easily; the chain is the saw's own chain (`form_saw_chain.png`, teeth and all) off the end of the
  right saw, whipping out in a wave, taut when it catches; caught on a block it zips you straight there
  (`ClientCombat.swing`) instead of swinging.
- **From the show, kortev's picks (2026-10-20)**: the life steal stays how you drink blood (kortev: Denji drinks it
  fighting); **arms grow back** from it (`Limbs.drank`: every 25 blood drunk cutting, a kill's worth, brings a lost
  arm back, growing out of the stump on screen); **you get bloodier** as you fight (`ClientCombat.bloody`: hits and
  kills soak the form darker and redder, dripping; water and rain wash it off); **you faint from blood loss**
  (`Blood.FAINT`): turned back with under 30 blood you're slow and the world drains grey and dims at the edges with a
  slow beat, till you've made 50 again; eating makes blood (`EatBloodMixin`, 3 a point of food).
- **The door in your dreams** (season 1; `DreamHud`, `textures/gui/dream_door.png`): down, the dreams alternate
  between Pochita and a dark corridor with a door at its end, light leaking round it, Pochita in front of it: "Denji...
  don't open that door."
- **Blood on the lens for kills** (`LensBlood`, `textures/gui/lens_blood.png`), reworked from the transformation's
  (saved at e85a346) as kortev asked: a kill within 4.5 blocks, in your own eyes, throws 1 to 4 dark pixel splats onto
  the edges of the picture (never the middle), sliding a little down and gone in about 2 seconds.
- **Sound effects lettered** (`Sfx`, `textures/gui/form_sfx.png`, glyphs painted in `gen_chainsaw_textures.py`):
  your kills ブシャッ, the overhead chop landing ザシュッ, grinding into something ギャリギャリ, slammed onto your
  screen off to one side, tilted and shaking, gone in a moment. A new word needs its glyphs drawn there (16 by 16).
- **From the show, my picks of the set pieces (kortev: "do what you want")**: the saws **cut through blocks** ten times
  as fast as hands in the form (`FormDigMixin`, `Saw.CUTS`; not obsidian and harder), to tear through a building; the
  **kick** (V, `Saw.kick`): Denji's dirty kick, short, knocking back and dazing (slowness) what it hits; **dragged
  under**: something caught on your chain, in water with you, is pulled down and held under, drowning (as he drags
  Reze into the sea; `Grapple` tick). The drag isn't filmed yet; Beam the shark (a second ult) is left for later.
- **Slash trails** (`SawTrails`, `textures/entity/form_trail.png`): each slash leaves a smear behind the saw's tip,
  white at the edge going to blood red, gone in 3 ticks; sampled from where the bar is drawn each frame, so it follows
  the real arc. In your own view it is shorter and reaches further down the bar, or it reads as a thin line.
- **The lunge** sells its speed: the head's saw leaves the same smear, your view stretches wider, and white speed
  lines stream in from the edges of your screen (`FormHud.speedLines`, `ClientCombat.lunging`).
- **The frenzy has a look from outside** now: the red eyes, a red mist of blood rising round the body, the exhaust
  pouring. (Inside it was already the red pulsing screen edge.)
- **Getting up** (`GetUp`, kortev asked for it to look a lot better): no more tipping up like a plank. Back up after
  being down, your arms and legs grow back out of their stumps in blood as you push up on your hands, get a foot
  under you and stand (`GetUpMixin`, `GetUpLeanMixin` stops the game's crawl leaning you as well; the server holds you
  still for `Downed.RISE`, 26 ticks). At the end of the merge you sit up with your legs out, rock forward into a
  crouch and stand (RISE to END is now 30 ticks; END moved to 626 and `form_merge.ogg` with it).
- **Limbs come off** (`Severed`): going down, the arms and legs still on you fly off in a spray of blood, spinning,
  land and lie there 4 seconds before a puff; an arm lost mid-fight does the same. Each is the player's own arm or
  leg, their skin and sleeve, drawn on its own.
- **No zoom while held still** (`HeldStillFovMixin`): down or merging, your speed is taken away and the game zoomed
  your view in by half, as it does for slowness; every merge shot had been framed against that.

### The head: boxes, chosen (2026-10-20)

kortev chose the box head (`FormHeadModel`, painted by `gen_chainsaw_textures.py`'s `form_head`, its eye lit by a glow
layer like a spider's; the saw stays the mesh's) over the mesh's, "a tiny bit less boxy": **not smaller** (a scaled
down try was wrong), but the visor's top bevelled in from the sides and stepping down from the brow to the engine,
the ridge running down the slope, the prow narrowing to the mouth, the engine's top edge bevelled. It's on by
default; `/chainsawhead mesh` (client) and the selftest's `-Ptarget=meshhead` show the old one to compare.

### Round of 2026-10-20 (kortev on their phone: reviewed by video)

- **Mobs leave you be while you're down** (or dead while Pochita comes): any mob after you gives up as you go down
  (`Downed.forgotten`), and none can take you for a target till you're up (`DownedUnseenMixin`, on
  `PlayerEntity.canTakeDamage`, which the game's targeting asks).
- **The missing merge line**: "I always loved hearing you talk about your dreams" plays as the camera cuts to the
  memories, 90 blocks over your body, so it was out of earshot (and the dream's music with it). While your own merge
  plays and the camera is far from your body, you hear from your body (`DreamHearingMixin`,
  `MergeCinematic.hearing`). (The clip itself never says "I'll give you my heart": the caption does.)
- **Arms lost leave bleeding stubs** (`Stumps`, texture `form_stump`): the upper arm's stub (`STUB` of it), its end
  torn round the bone, gushing as it comes off, then spurting with the heartbeat and dripping; in first person the
  stump where that hand was, tipped so you see its end, drops running off it, and a splash of blood on that side
  of your screen (`LensBlood.lost`). Arms grow back out of the stubs. The server's burst as an arm comes off isn't
  sent to the one losing it (it went off inside their head).
- **The attacks, for PvP** (kortev picked all four):
  - *Instant swings*: your slash swings and sounds the moment you click (`ClientCombat.slashNow`, predicting the
    combo step as `Saw.slash` will); the server's SWING only corrects the step. The server plays the slash sound to
    everyone but the slasher. A click up to `BUFFER` ticks early slashes the moment it can; the server takes a
    slash up to `LAG_SLACK` ticks early.
  - *Hits that land*: the slash sends what's under your crosshair (`FormActionPayload.target`); the server hits it
    too if it's in front, in sight and within the reach plus `AIM_SLACK` (`Saw.aimed`). You step in after whoever
    you slash (`stepIn`). A player hit by the first two slashes keeps only `COMBO_HOLD` of the knockback; the
    finisher launches them (about 6 or 7 blocks).
  - *Impact*: a saw biting into a player (or any finisher) sends IMPACT: sparks, the new `form_crunch` sound, your
    swing held `IMPACT_PAUSE` ticks and your camera kicked hard (`kickSize`); the one bitten is jolted.
  - *Saws beat shields*: a raised shield (anyone's, any mod's blocking item) is cut through and knocked out for
    `GUARD_DOWN` ticks, as an axe does (`Saw.breakGuard`), with the break sound.
- **Videos for kortev**: the form self test records its part as a video with its sounds, `-Pcapture=true` (needs
  ffmpeg on the PATH; with only Python's imageio-ffmpeg, copy its exe in as `ffmpeg.exe`, and `mux-audio.py` wants
  `ffprobe` too, or read the length off `ffmpeg -i`). Then `.github/scripts/render-loops.py` and
  `.github/scripts/mux-audio.py build/selftest/capture out.mp4`. New targets: `limbs` (arms lost and grown back) and
  `pvp` (a stand-in player, `Rival`, with a shield: the combo, first person then from behind). The recorder now
  judges sounds from where the game hears, not the camera.

### Reels: anime scenes recreated in the mod, for content (kortev's idea, 2026-10-20)

kortev wants a run of short vertical reels, each an anime scene recreated in the mod and fully animated (scripted,
not played live). Reel 1 is episode 1, from the storyboard kortev approved (an Artifact, "Reel 1: Betrayed"), then
restaged as kortev remembers the episode: no chainsaw fight; Denji and Pochita stabbed in the back at the same moment by
two of the yakuza; the Zombie Devil rising; the yakuza turning; the zombies finishing Denji off on the floor; dragged
out and thrown in a dumpster with Pochita; dead a while in the dark of it, his blood dripping onto Pochita, who stirs
just enough; the mod's own merge; the lid bursting open and Denji rising with the cord in his chest; then (kortev:
"have the chainsaw stuff too, worst case we cut it later") he pulls it, the mod's own transformation plays, and as
Chainsaw Man he cuts down the zombies by the bin and the ones out of the back door (the form's own slashes, the kills
cut in two), walks in, leaps and cuts the Zombie Devil in two in a rain of its blood. No voices but the merge's. About
96 seconds; the chainsaw part from 80 s, to cut if kortev wants.

- **Run it**: `./gradlew runSelftest -Pselftest=reel1 -Pusername=Denji` (with ffmpeg on the PATH and
  `-Pcapture=true` to record it; then `render-loops.py` and `mux-audio.py` as for the form's videos). `-Ptarget=cast`
  photographs the cast and the Zombie Devil, `-Ptarget=set` the set. It saves `reel_frame_<tick>.png` stills along
  the way, to look over without the video.
- **All of it is test code** (`src/gametest/.../test`): `ReelSelfTest` (the script: each cut's blocking, camera, lens,
  sounds and events, by tick), `ReelSet` (the shack, the warehouse, the alley, built high over the clouds at night in
  the rain, with its lamps), `ReelZombieDevil` (its own model, `tools/reel_textures.py`), `ReelDumpster` (drawn, its lid
  swinging), and mixins for the stand-ins' poses (`ReelPoseMixin`, `ReelBodyMixin`), no names over heads, no HUD but
  the mod's lettering, lens blood and the merge's bars, the lens per cut, and the set gone in the merge's dream (no
  blocks, clouds or rain). The actors are client-only stand-ins in skins from `tools/skins/reel` (Denji in kortev's
  onesie); you're swapped in for Denji's stand-in in the dumpster, for the merge.
- **What the mod opened up for it** (no change in play): `Sfx.show`, `Severed.off`, `CutDown.killed` (a film's
  stand-in can be cut down), `LensBlood.filmed` and `splash` (blood on a film camera's lens), `MergeCinematic.prompt`
  (the end's "Pull the cord" off), and the merge's lens opened up when filmed tall.
- **Waiting on kortev**: their notes on the cut (and whether the chainsaw ending stays). No music (kortev let it go).

### 4. Still to do from kortev's picks (agreed, not built yet)

- **The transformation polish** above still open: the stretched cord in your own camera.
- Done from the smaller follow-ups: **kills cut the body in two** (`CutDown`, `CutInTwoMixin`): the server still
  takes the body away with its burst of gore, but the client keeps it a moment and draws it as two halves, split
  where the model's parts hang from (the legs below the middle, the rest above): the top sliced off sideways, tipping
  and falling, the legs folding a beat later, blood pouring out of the cut, then a puff. A mob in one piece just
  falls. Film it with `-Ptarget=kill` (a husk through the whole combo, then a cow). The wild Pochita lies fainted
  under his fallen tree; taming him, or bringing him round, plays `pochita_tame`.

### Later ideas kortev has had (not designed; ask before building)

- **The merge dead a while first** (2026-10-20): Denji lying dead for a moment before Pochita comes, as reel 1 has it
  in the dumpster (his blood dripping onto Pochita, who stirs).
- **Blood on the lens for kills**: see the polish list above; saved in the history.

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
- Multiplayer: another player watching a transformation (should work: everything is driven by payloads to all), and
  the fight against another player (the downed pose and the stumps are only checked on yourself in third person; the
  self test's stand-in can't lie down).
- The fight's numbers in real play, and fighting with a shader pack.
