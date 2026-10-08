# The Shooting Star

A Fabric mod for Minecraft Java **1.21.1** based on the "SS-03 Gungnir" orbital-strike reel.

It is kortev's own mod on a shared server: only **kortev** can craft its things (crafters do not make them at all), and
everyone can use them once they are made.

Use the **Gungnir Uplink** on any block 96–640 blocks away to get a kinetic lock on it. The
minimum is 1.5× the crater radius, so the shooter is never caught in their own blast. A mass driver
ringing Jupiter spins a round up over seven laps to 0.96c and fires it across the asteroid belt.
About 22 seconds later the round lands on the target. It does not explode: it is a spear. It hits
so hard that it blasts out a crater 128 blocks across, planes the ground into glowing molten crust
and is left standing as a spire of hull plating through the full height of the world.

The uplink feed you watch while the round is in flight is real-time 3D with its own GLSL shaders:
Earth with city lights and moving clouds, Jupiter's drifting bands with Io and the ring's shadow
crossing them, a Milky Way that aberrates and blue-shifts as the round nears light speed, and
meshes built in Blender (the spear and its sabot, the coils, the relay satellite, the asteroids), all
rendered in HDR with bloom, anamorphic lens streaks and motion blur. Inside the accelerator the coils
are drawn with exact, analytic motion blur: the first ones go by one at a time, then they rush past
and blur into a tunnel of light without ever strobing.

Back in the world the strike is lit for real: the falling round, the flash, the fireball and the
molten bowl throw their light across the terrain (positions and normals are rebuilt from the depth
buffer). The blast itself is drawn like its anime impact frames: cel-shaded fire, smoke and dust in
flat bands with an ink line round each cloud, a fireball with a white-hot core that breaks up as it
cools, and a mushroom cloud whose cap hangs over the crater for a minute or two.

The sound is synthesised from scratch too (`tools/gen_sounds.py`): the shooter hears a stereo mix of
wind, a drone under the feed, the coils cracking one by one as the spear passes them until they run
together into a roar, a slowed-down release, a rushing bed under the journey in, the seeker's growl
and lock tone, the scream of the round coming in, a beat of silence,
then the boom with their ears ringing, the shock wave and the crater burning; everyone else hears the
boom and the shock wave arrive at the speed of sound.

## What happens when you fire

| Time    | What you see                                                                                 |
|---------|----------------------------------------------------------------------------------------------|
| 0 s     | **Kinetic lock.** A beam drops onto the target and an orange reticle drapes over the ground. |
| 1.3 s   | Your camera rises over the target and up into the cloud deck.                                |
| 2.5 s   | **Uplink feed** starts: out of the clouds and back to Earth from orbit (`EARTH 6,371 KM`, `TARGET`). |
| 3.9 s   | `[ RELAY · JUPITER 5.2 AU ]`: low over the sunlit Earth, the relay satellite fires its laser and the feed rides it out to Jupiter. |
| 5.1 s   | `[ ACCELERATOR WAKING ]`: **THE SHOOTING STAR**. The ring powers up round Jupiter from the breech, Io and its shadow crossing the planet. |
| 7.3 s   | `[ LOADING ]`: the spear in its sabot seats in the breech coil; `[ BREECH LOCKED ]`.        |
| 8.3 s   | `[ LAP 1 / 7 ]` … `[ LAP 7 / 7 ]`: inside the barrel the coils fire one by one as the spear passes, faster and faster until they blur into light; between laps it tears past the camera and over Jupiter's cloud tops. 0.96c. |
| 12.5 s  | `[ RELEASE ]`: out of the muzzle in slow motion, shedding the sabot; then the camera races after the spear and catches it up. |
| 14 s    | `[ DEBRIS FIELD · MAIN BELT ]`: into the asteroid belt at full tilt; the spear punches straight through a boulder without slowing. |
| 15.2 s  | `[ TRANSFER · JUPITER → SOL-3 ]`: the transfer plot, the round's track across the inner system past Mars, range and ETA counting down as it dives in on Earth. |
| 17.6 s  | `[ SEEKER · LOCATING TARGET ]`: the view from the spear's point sweeps past the Moon and hunts across Earth until `TARGET LOCKED · 39.50 N · 98.50 W`. |
| 19 s    | `[ TERMINAL · SOL-3 ]`: the camera pulls back out of the point over the spear; Earth rushes up; re-entry in a sheath of plasma. |
| 20.7 s  | Back in the world: the round falls out of the sky as a blazing star, lighting up the land as it comes. |
| 21.9 s  | **Impact**: a blinding flash, a beat of silence, two seconds of stylised impact frames cutting hard between drawn styles (red edges on black, inverted cyan, stark black and white, posterised orange, red and black, halftone, ink), then the boom, the cartoon fireball, a condensation ring and a shock ring racing out over the ground. |
| 22–27 s | Debris rains out of the crater on inked smoke trails, a column of fire and smoke rises over the spire and spreads into a cap, and a wave of dust rolls out. The shooter's camera rides out the shock wave, then cranes up over the crater. |
| 25 s on | Lightning flickers in the ash column and thunder rolls in after it; embers drift up out of the molten bowl, the round's path hangs in the sky as a trail, and the cap drifts off on the wind. |
| 24.3 s  | `[ IMPACT CONFIRMED ]` · `ZONE 0128 PLANED · SPIRE STANDING · 384 M`                         |

Everyone nearby sees the beam, the reticle, the falling star, the blast and the shock wave (the
boom arrives at the speed of sound, so far-off players hear it late). Only the player who fired gets
the feed and the camera shots. Press **Backspace** (rebindable) to skip the feed.

## What the strike does to the world

- **Bowl:** a white-hot bowl around the spire, about 70 blocks across and 22 deep at the default
  size, with lava pooled at its base.
- **Planed zone:** everything above the impact level (up to 140 blocks of hillside) inside the
  crater radius (64 blocks by default, a zone 128 blocks across) is vaporised. The ground is
  resurfaced with **Molten Crust**, which cools over about a quarter of an hour into **Fused Crust**.
  Water quenches it instantly.
- **Rim:** a raised lip of blackstone, basalt and magma. Hills just outside the zone are sheared
  into a rubble slope rising from the rim, and debris rains down for a while after the impact.
- **Scorched ring:** out to 1.5× the radius, leaves are stripped, glass shatters, sand fuses to
  glass, snow melts and fires start.
- **Spire:** a round, 5-block-wide column of Gungnir Hull with glowing coil bands. It runs from
  the bottom of the world to the build limit and has tail fins at the top.
- **Entities:** anything inside the planed zone is killed. In the scorched ring beyond it,
  entities take less damage the farther out they are, catch fire and get thrown outward.
  Unbreakable blocks (bedrock, command blocks, barriers) are never touched.

## Getting it

- **Creative:** the Combat tab (uplink) and the Building Blocks tab (hull, coil band, fused crust).
- **Survival:** craft it. The recipe unlocks once you have a Nether Star.

  ```
   R        R = Lightning Rod      E = Echo Shard
  E S E     S = Nether Star        N = Netherite Ingot
  N C N     C = Compass
  ```

## Ω-00 Ginnungagap, the Genesis Key

Look at a block at least 24 blocks away and use the key. Bifröst, a gate in orbit over the target,
opens onto the void between universes, where they hang in a lattice, each in a block of dark glass. The
feed dives through into universe 4,096,113, beside one of its galaxies, then pulls back out past the cosmic
web of its two trillion galaxies until the whole of it is one block among the others, and it is selected.
The gate draws that block through and drops it down a bridge of light onto the target.
It bursts, and the black takes the whole world: everyone in it is left in nothing, free to walk, seeing
only each other, unable to touch anything or be touched. Everything within `ginnungagapRadius` of the target
is erased for good: a ragged shaft down through bedrock, fissures split out across the ground from its rim.
The first turn cracks the key, and only the cracked key brings the world back: turned in the black, it
shatters, Yggdrasil grows out of the hole, and the world is put back along its roots, block by block, for
everyone. (If whoever holds the cracked key is gone for five minutes, the world comes back by itself;
`/ginnungagap release` brings it back at once.) Skipping the feed shows all of it from your own eyes, free
to move, as any other player sees it.
The feed, and the rebuild, can be hurried with the same key as Gungnir's skip (Backspace).

- **Try it:** `/give @s shootingstar:genesis_key`, or `/ginnungagap open <pos>` (operators).
- **Craft it:**

  ```
    R       R = Recovery Compass   E = Echo Shard
  E S E     S = Nether Star        H = Heavy Core
    H
  ```

## Þ-01 Mjölnir

The Shooting Star's own strike: as strong as Gungnir, but nothing like it. Raise the hammer at any block 96–640
blocks away and a bolt leaps from its head into the sky over the target, and the storm comes. The feed follows the
charge of the whole planet's thunderstorms, the global circuit, as it is drawn in to that one spot; the storm over
the target winds up into a vortex; the stepped leader feels its way down out of it; and the bolt comes down.

The feed is real-time 3D with its own shaders, like Gungnir's: the night side of Earth from orbit with every
thunderstorm on it flickering inside its clouds and red sprites leaping above them, a ring of charge closing across
the planet with filaments of current running in ahead of it and the storms it passes going dark, the supercell
winding into one vortex lit by lightning that never stops, and the leader stepping down out of the storm's base over
the dark country under it. The sound under it is the planet's own radio noise of lightning (the clicks of sferics, the pings of
tweeks and the falling whistles of whistlers) over a drone breathing with the Earth-ionosphere cavity's 7.83 Hz.

In the world everyone sees the storm boil out of the sky where the call went into it and wind up overhead: decks of
cloud turning over the target, a wall cloud lowering out of the middle, lightning flickering through it and now and
then a bolt down to the ground out under its edge. Under it the sky goes slate, the light goes and rain comes. Rings
of light on the ground mark the zone (and, fainter, the edge of the arcs); whoever called it stands with the hammer
held up; the leader steps down out of the wall cloud, sparks reach up off everyone standing under it, and the stroke
comes: a blinding channel with its branches, three restrikes down the same path, the channel breaking up into beads
of violet light as it dies, a ring of dust thrown up and racing out across the ground, spider lightning racing across
the storm's base, the scar burning out across the ground and steam boiling off the crater. Thunder reaches you at the
speed of sound.

### What happens when you raise it

| Time    | What you see                                                                                 |
|---------|----------------------------------------------------------------------------------------------|
| 0 s     | **The hammer goes up** over your head and lights up, trembling as the charge builds in it.   |
| 0.8 s   | **The call**: a bolt leaps from the hammer up into the sky over the target, with a crack and a clap of thunder; your eyes follow it up as the storm boils out where it went in. Everyone can see who called it. |
| 1.5 s   | Your camera climbs out over the target, looking up into the storm as it turns, and rises into its dark base. |
| 2.7 s   | **The feed** comes out of the storm cloud into orbit over the night side: `[ Þ-01 MJÖLNIR · GLOBAL CIRCUIT ]`, `THUNDERSTORMS · 1,812 ACTIVE`. |
| 5.8 s   | `[ DRAWING THE CIRCUIT ]`: a ring of charge closes across the planet on the target, `CHARGE · POTENTIAL · STORMS DRAINED` counting up. |
| 10.3 s  | `[ SUPERCELL · TARGET ]`: down onto the storm over the target as it winds into one vortex: **MJÖLNIR**, struck like an anvil. |
| 13.8 s  | `[ STEPPED LEADER ]`: the leader stepping down out of the storm's base, seen from kilometres off over the dark country under it, `LEADER ALTITUDE` counting down. |
| 16.3 s  | Back in the world on you, from over your shoulder, hammer held up, the storm looming over the target far off; then low at the edge of the zone as the leader comes on down out of the wall cloud, streamers rise off everything under it and the air buzzes. |
| 18.3 s  | **The stroke**: the flash, then impact frames cut between drawn styles (a blue photographic negative, the strobe silhouette, ink on pale paper, posterised violet), cutting back to white with each restrike. |
| 19.6 s  | Cut to high over the strike, looking straight down as the dust races out and the scar burns out across the ground, then craning down and round to the crater, steaming, the bolt left standing in it: `[ STROKE CONFIRMED ]`. |
| 25.5 s  | Back to your own eyes. The storm unwinds and the sky clears.                                 |

Only the player who raised the hammer gets the feed and the camera shots. **Backspace** skips the feed, as for
Gungnir.

### What the stroke does to the world

- **Crater:** where the channel lands, a bowl about 54 blocks across and 14 deep at the default size is blown out
  (everything above it, up to 120 blocks of hillside, goes) and fused to **Fulgurite**, still charged here and there.
- **The petrified bolt:** the bolt is left standing in its crater as a jagged column of fulgurite, kinked the way the
  channel was, forking near the top and still charged at its heart: about 96 blocks tall by default.
- **Burn zone:** out to the strike radius (`mjolnirRadius`, 64 by default), every leaf burns off, every trunk is left
  a black **Charred Log**, anything wooden burns to ash (chests and other containers ride it out), grass burns to bare
  earth, sand fuses to glass, snow and ice go, and a few fires are left burning.
- **The scar:** a Lichtenberg figure burned out through the ground to 1.5× the radius, the fern of branching channels
  the current leaves: trenches up to four deep near the middle, every one lined with fulgurite, the main channels
  still charged along stretches of them, glowing brightest where the channel was widest.
- **Charged Fulgurite** glows white-blue, current running through its veins, and spits sparks; it bleeds its charge
  away over about a quarter of an hour into dark **Fulgurite**. Water earths it at once, and standing on it gives you a
  shock (a charged creeper stands on it unharmed).
- **Creatures:** everything inside the strike radius dies. Everything in the ring beyond it, out to the edge of the
  scar, is hit by an arc off the bolt (weaker farther out), set on fire and thrown; and each arc jumps on to the
  nearest creature it has not hit within 14 blocks, up to three times. Arcs are lightning, so they turn pigs into
  zombified piglins and villagers into witches; a creeper they charge, and leave unharmed. The arcs never touch whoever raised the hammer.
  Kills count as theirs.
- Unbreakable blocks (bedrock, command blocks, barriers) are never touched.

### Getting it

- **Creative:** the Combat tab (the hammer) and the Building Blocks tab (fulgurite, charred log).
- **Survival:** craft it. The recipe unlocks once you have a Nether Star.

  ```
  N H N     N = Netherite Ingot   H = Heavy Core
  L S L     L = Lightning Rod     S = Nether Star
    B       B = Breeze Rod
  ```

## Chitty Chitty Bang Bang

GEN 11, the car from the film, is a mod of its own, built here beside this one and needing it: see
[chitty/README.md](chitty/README.md). Her things are under the same rule: only kortev crafts them.

## Commands and game rules

- `/gungnir strike <pos>`: call a strike on a position (operators).
- `/gungnir cancel`: call off every strike that has not landed yet.
- `/ginnungagap open <pos>`: open a Ginnungagap on a position (operators).
  From a command block or the console there is no shooter: everyone watches from outside, and the world
  comes back by itself at the end.
- `/ginnungagap release`: end every Ginnungagap and let reality back in.
- `/mjolnir strike <pos>`: raise Mjölnir at a position (operators).
- `/mjolnir cancel`: call off every Mjölnir strike whose bolt has not come down yet.

| Game rule              | Default | Meaning                                                  |
|------------------------|---------|----------------------------------------------------------|
| `gungnirTerrainDamage` | `true`  | `false` keeps terrain intact (entities are still hit).   |
| `gungnirCraterRadius`  | `64`    | Radius of the planed zone, 8–160.                        |
| `gungnirSpire`         | `true`  | Whether the spent round is left standing as a spire.     |
| `ginnungagapRadius`    | `96`    | Radius erased by a Ginnungagap, 16–256.                  |
| `ginnungagapTerrainDamage` | `true` | `false` leaves the blocks in place.                  |
| `mjolnirRadius`        | `64`    | Radius of the zone Mjölnir kills everything in, 8–160; the scar and arcs reach 1.5×. |
| `mjolnirTerrainDamage` | `true`  | `false` keeps terrain intact (creatures are still hit).  |
| `mjolnirPetrifiedBolt` | `true`  | Whether the bolt is left standing in its crater.         |

## Client options

`config/shootingstar.properties`:

```properties
feed=true          # show the uplink feed when you fire
cameraShots=true   # let the feed move your camera (rise, sky, impact and wide shots)
screenShake=1.0    # 0 turns shake off
```

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.1**.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) (any 1.21.1 build) and
   `shooting-star-1.0.0.jar` in your `mods` folder, and `chitty-chitty-bang-bang-1.0.0.jar` beside them for Chitty.
3. The mod is needed on both the server and every client that should see the feed and effects.

## Building

Needs a JDK 21 installed (on Windows: `winget install EclipseAdoptium.Temurin.21.JDK`). Gradle runs
on it even when your default Java is newer.

One build makes every mod, each in its own jar: The Shooting Star from this folder, Chitty Chitty Bang Bang from
`chitty/` (a Gradle subproject that the root `build.gradle` sets up along with this one). Open the root folder in an
IDE and both are there to edit together. CI's `mods` download has both jars.

```sh
./gradlew build            # jars in build/libs/ and chitty/build/libs/
./gradlew runGametest      # server game tests for both mods: targeting, crust cooling, a full strike, Chitty
./gradlew runClient        # play in a dev client with both mods
```

The `selftest` workflow (choose `gap`, `true` for a Gungnir strike, `mjolnir` for a Mjölnir strike, or `chitty` for the car) plays a full strike in a real client under a virtual display and records it
as a video. The client and the integrated server run in lockstep and every frame is rendered at an
exact game time, so the result is a smooth 30 fps video even on a software renderer. The mod's
sounds are mixed in, and a screenshot of every phase is saved alongside.

The block and GUI textures and the sounds are generated by `tools/gen_textures.py` and
`tools/gen_sounds.py` (Python 3 with Pillow and NumPy, plus ffmpeg for the sounds). The feed's
meshes are built in Blender by `tools/models.py` (`pip install "bpy==4.5.*"`), the cosmic web in
Ginnungagap's block by `tools/gen_universe.py` (NumPy and SciPy), Yggdrasil's light by
`tools/gen_yggdrasil.py` (NumPy), and its planet and
sky maps are downloaded and prepared from NASA's originals by `tools/fetch_maps.py`.

## Credits

The feed's planet and sky maps are public-domain NASA imagery:

- Earth: *Blue Marble: Next Generation* (July 2004), the Blue Marble cloud layer, and *Black
  Marble 2016* city lights, from NASA Earth Observatory.
- Jupiter: *Cassini's Best Maps of Jupiter* (PIA07782), NASA/JPL/Space Science Institute.
- Milky Way and stars: *Deep Star Maps 2020*, NASA/Goddard Space Flight Center Scientific
  Visualization Studio (Gaia DR2, Hipparcos-2 and Tycho-2 data).
