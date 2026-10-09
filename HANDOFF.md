# Handoff: The Shooting Star

Start here. This is what the project is, where it stands, what to build next and how to work on it. The deep
how-to lives in two skill books in `.claude/skills/`, which Claude Code loads by itself:

- **`.claude/skills/weapon-mod/`**: how Gungnir and the Genesis Key are built, how to edit them, and how to build a
  new weapon of the same kind.
- **`.claude/skills/film-vehicle/`**: how Chitty Chitty Bang Bang is built (Blender model, game mesh, entity,
  renderer, sounds), and how to build the next vehicles the same way.

## What this is

Fabric mods for Minecraft Java **1.21.1** (Yarn mappings), repo `Kortev/loft`. They are **kortev's own mods for an
SMP** where every player gets a custom mod of their own, with a story and PvP. Only `kortev` can craft their things
(`OwnerOnly`); everyone can use, ride and break them once made.

One Gradle build makes two jars, so all of it is edited, built and tested together:

- **The Shooting Star** (id `shootingstar`, the root project, `src/`): the weapons, plus what both mods share
  (`ShootingStar.id`, `OwnerOnly`, `ModCriteria`) and the game-test harness for both.
- **Chitty Chitty Bang Bang** (id `chitty`, the `chitty/` subproject): the film's vehicles. It needs The Shooting
  Star. Its things keep the `shootingstar` namespace, so Chittys made before the split survived it.

The root `build.gradle` sets up every mod at once (`allprojects`); `./gradlew build` makes both jars, and the dev
runs (`runGametest`, `runSelftest`, `runGenworld`) load both.

The bar is **cinematic and film-accurate, nothing generic**. Every sound is synthesised by a script, every model is
built in Blender by a script, the effects are real shaders, and everything has game tests.

## What's in it (all working, CI green)

| Thing | Item | State |
|---|---|---|
| **SS-03 Gungnir**: orbital kinetic strike with a 22 s cinematic uplink feed, a 128-block crater, molten crust and a spire | `gungnir_uplink` | Done. kortev has more edits planned (not yet specified). |
| **Ω-00 Ginnungagap**: the Genesis Key erases the world round a target; everyone waits in the void; the cracked key rebuilds it | `genesis_key` | Done. kortev has more edits planned (not yet specified). |
| **Chitty Chitty Bang Bang**: drivable, flying, floating car from the film | `chitty` | Done and polished (pleated wings, raft, ejector seat, hamper, dials, lamps, live metal shine, banking view). In her own jar. |
| **The Vulgarian airship**: Baron Bomburst's airship, to fly, with a grapple, a rope ladder and bombs | `airship`, `airship_bomb` | In game (Chitty's jar), waiting on kortev's in-game test. Map: `.claude/skills/film-vehicle/references/airship.md`. |
| **Owner-only crafting** | all recipes | Done: `OwnerOnly.OWNER = "kortev"`; crafters never make the mods' items. It covers everything in the `shootingstar` namespace, whichever jar it is in. |

The latest work is on branch **`claude/gallant-brahmagupta-qi9mw3`** (it carries everything from
`claude/happy-brahmagupta-ueara3`). No PR is open; ask kortev before opening one.

## What to build next

Two new vehicles from *Chitty Chitty Bang Bang*, built exactly like Chitty and in her mod (`chitty/`; see the
`film-vehicle` skill). kortev
wants **renders for approval before baking** anything.

### 1. The Vulgarian airship (kortev's own airship): built

- It's a craftable vehicle to fly, not an enemy.
- It hovers, rises and sinks slowly, and seats a crew in the gondola, with a **ladder**.
- **Grabbing hook on a winch:** lower it to grab a mob, a player, a chest, or even Chitty, and haul it up into the
  sky. In the film it lifts Grandpa Potts' outhouse with him inside.
- **Bombs:** dropped from the gondola, smaller than TNT.
- References:
  - kortev's Notion page **"claude chitty chitty mod"**, which has a Lebaudy airship photo and two other images.
  - Film stills: research the Baron's airship yourself.
- **Decided with kortev so far:**
  - Full scale.
  - The gondola is standing room only; there are no benches.
  - Two pusher propellers at the gondola's stern, each driven by a shaft, pulley and belt as in the film. There is
    no other fan.
  - The emblem and the colours follow the film. The arms must be crisp heraldry, not cartoony.
  - Everything is copied from the film as closely as the logo was. The metal frame stops above the crew's heads.
    The gondola and the propellers hang from it on wires; nothing rigid joins them to it.
  - The gondola's carving follows the film's layout, with a cherub beside a black-letter B at the bow.
  - She lifts six people. In the film she starts losing height with six aboard and Grandpa's hut on the hook.
  - The hook is a **grapple hook**. It is mainly a utility for carrying things. A hooked player gets off only by
    some rule such as a timer.
  - Bombs break blocks. The cost and reload are ours to choose.
  - The ladder is a rope ladder that lets down as far as the hook.
  - The gondola is shorter and deeper, like the film's (its carved side about two to one).
  - Eight places to stand, with lift for six; overloaded, she sinks slowly. The pilot's key throws the passenger
    furthest aft overboard.
  - A hooked player struggles free by holding sneak for 10 s.
  - Bombs: iron, 2 gunpowder and string make 2. She carries a rack of 6, with 1.5 s between drops.
  - Riders walk freely about the gondola and can't fall out. Walking up to the wheel takes it (then the movement keys
    fly her); sneak lets go of it. Sneak gets anyone off: beside her near the ground, or in the air down her rope
    ladder, which lets itself down for them.
  - In third person the camera turns about the middle of her, far enough back to see all of her.
  - The grapple is a weight on a rope (it swings, trails and lands) and lets down 64 blocks. Anyone on the ground can
    take hold of it as it hangs empty and then hook it onto someone within reach, throw it (R) or jump to hang on it;
    she winds up whatever it holds (whoever hangs on comes aboard). Slack rope sags; the drum turns; the rope creaks
    under a load; dragged, the grapple sparks.
- **Built and in game.** kortev OK'd the renders and asked for her baked and ported. She is in Chitty's jar, mapped
  in `.claude/skills/film-vehicle/references/airship.md` and described in `chitty/README.md`.
  - kortev's first test: boarding put them in a crew place, so she would not fly; there was no way to the wheel and no
    clear way off; the camera was too close. Hence walking, the wheel, sneaking off and the new camera.
  - Next: kortev's next in-game test, and fixing what they report.

### 2. The Child Catcher's carriage

- There is **no Child Catcher mob**, just his carriage as a craftable vehicle.
- It's pulled by **two fast black plumed horses** that trot and gallop as it drives, faster than a horse on roads.
- The **cage on the back locks**: anyone put in it (mob or player) can't get out until the driver opens it. It's a
  prisoner transport for PvP.
- References: two photos on the same Notion page, plus film stills.

Both are only craftable by kortev; the existing `OwnerOnly` check already covers every recipe in the mod.

### Order of work that kortev expects

1. References.
2. Blender model.
3. Renders sent to kortev for OK.
4. Fix what they say.
5. Entity and behaviour.
6. Bake.
7. Sounds.
8. Recipe, advancements and README.
9. Game tests.
10. Push and CI green.
11. A short in-game test checklist for kortev.

kortev tests in game themselves and reports back. Don't spend usage on the self-test video workflow unless asked.

## Open items

- **Chitty: a polish pass like the airship's, saved for later (kortev: "maybe save that").** Improve what she has
  rather than add to it, and ask the same question about her look in Minecraft (pixel density, face shading,
  faceting) once kortev has chosen for the airship.

- **Chitty polish, kortev's go-ahead pending (do it only after the airship is finished, and not before kortev says
  so).** kortev wants her to stop feeling empty and lifeless, like the old racecar she is:
  1. Take out her working headlamp beams (ChittyLamps).
  2. Her body moves: she squats when you accelerate, dips her nose when you brake, leans in corners and bounces over
     bumps.
  3. At idle she shakes in time with the chit-ty firing, and the bonnet straps rattle.
  4. Heat shimmer over the bonnet and exhaust, dust off the wheels on dirt, and tyre smoke on hard launches and turns.
  5. She is started the old way, cranked at the front, and now and then doesn't catch.
  6. A key to blip the throttle, so she roars and backfires while standing still.
- **Flaky game test** `gapgametests.liveevent`: it failed once ("the shooter was not taken home", at home x/z but 5
  blocks lower) and passed on re-run. Find the real cause; the player probably landed before the ground under home
  was back.
- kortev's planned edits to Gungnir and the Genesis Key: ask what they are.

## How to work here

- **No local Gradle or Minecraft in agent sandboxes. CI is the compiler.**
  - Push, then read GitHub Actions (`build` workflow: jobs `build` and `gametest`) with the GitHub tools
    (`actions_list` → runs on the branch, `get_job_logs` with `failed_only`).
  - The `build` job also checks both jars (`.github/scripts/check-jars.py`: each holds its own things and a refmap
    for every mixin config) and uploads them together as the `mods` artifact. The game tests run the dev build, so
    this is the only check of the jars kortev actually installs.
  - Before pushing, re-read your diff for Yarn 1.21.1 API names. A wrong name costs a CI round.
  - Yarn mapping files can be fetched raw from `https://raw.githubusercontent.com/FabricMC/yarn/1.21.1/mappings/<path>.mapping`
    to check a method's name.
- **Blender** (models): a Python 3.11 venv with `bpy==4.5.*`:
  `python3.11 -m venv .bpy && .bpy/bin/pip install "bpy==4.5.*" numpy pillow` (keep it outside the repo, e.g. in a
  scratch dir).
  - Quick look: `--out DIR --renders --only shot1,shot2` with `CHITTY_SAMPLES=24`.
  - Full game bake: `--game`, about 6 minutes.
- **Sounds**: `python3 tools/gen_sounds.py [names]` (weapons) and `python3 tools/gen_chitty_sounds.py [names]` (car).
  They need NumPy, SciPy and ffmpeg, and write `.ogg` straight into the assets. Generated assets are committed.
- **Never kill a process with a pattern that matches your own command line** (`pkill -f chitty_model` kills the shell
  running it). Find the PID first, then `kill PID`.
- **Commits:**
  - One topic each. Subject like `Chitty: what changed, in plain words`, with a body that explains why.
  - End with the attribution trailers your harness asks for.
  - Push to the working branch; don't force-push others' work.
- **Code style:**
  - Tabs, lines up to 120 columns.
  - Long plain-English Javadoc that describes what a thing *is* and *does* in the world.
  - Constants named for what they mean.
  - Mixin handler methods prefixed `shootingstar$`.
- **Self test (video)**: Actions → `selftest`, input `test` = `gap` | `true` (Gungnir) | `chitty`. Set `publish` to
  push screenshots and video to the `selftest-output` branch. It's slow and expensive, so only run it when kortev
  wants it.

## Working with kortev

- Show model changes as renders **before** baking, and match the film; they will say "this is why you're supposed to
  check with me" otherwise.
- Ask when a design call is theirs (behaviour, balance, PvP rules); decide the technical calls yourself.
- Keep messages short. End a round of work with a numbered list of what to test in game.
- When kortev reports a bug from in game, find the cause before changing anything (e.g. the "clanking" was a new
  sound, not the engine).
