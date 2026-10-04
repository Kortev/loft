# The Shooting Star

A Fabric mod for Minecraft Java **1.21.1** based on the "SS-03 Gungnir" orbital-strike reel.

Use the **Gungnir Uplink** on any block 96–640 blocks away to get a kinetic lock on it. The
minimum is 1.5× the crater radius, so the shooter is never caught in their own blast. A mass driver
ringing Jupiter spins a round up over seven laps to 0.96c and fires it across the asteroid belt.
About 17 seconds later the round lands on the target. It does not explode: it is a spear. It hits
so hard that it blasts out a crater 128 blocks across, planes the ground into glowing molten crust
and is left standing as a spire of hull plating through the full height of the world.

The uplink feed you watch while the round is in flight is real-time 3D with its own GLSL shaders:
Earth with city lights and moving clouds, Jupiter's drifting bands, a Milky Way that aberrates and
blue-shifts as the round nears light speed, Blender-modelled coils, relay and asteroids, all
rendered in HDR with bloom, anamorphic lens streaks and motion blur.

Back in the world the strike is lit for real: the falling round, the flash, the fireball and the
molten bowl throw their light across the terrain (positions and normals are rebuilt from the depth
buffer), the air over the crater shimmers with heat and the light stays dusty orange for a while.
The sound is synthesised from scratch too (`tools/gen_sounds.py`): the shooter hears a stereo mix of
wind, a drone under the feed, coil whine, the scream of the round coming in, the boom, the shock
wave and the crater burning; everyone else hears the boom and the shock wave arrive at the speed of
sound.

## What happens when you fire

| Time   | What you see                                                                                 |
|--------|----------------------------------------------------------------------------------------------|
| 0 s    | **Kinetic lock.** A beam drops onto the target and an orange reticle drapes over the ground. |
| 1.3 s  | Your camera rises over the target.                                                           |
| 2.5 s  | **Uplink feed** starts: pull back to Earth from orbit (`EARTH 6,371 KM`, `TARGET`).          |
| 3.9 s  | `[ RELAY · JUPITER 5.2 AU ]`: a warp jump out to Jupiter.                                     |
| 5.1 s  | `[ ACCELERATOR WAKING ]`: **THE SHOOTING STAR**. The ring powers up from the breech.           |
| 7.3 s  | `[ LOADING ]`: the round seats in the breech coil.                                           |
| 8.3 s  | `[ LAP 1 / 7 ]` … `[ LAP 7 / 7 ]`: the round accelerates to 0.96c through the coils.          |
| 13.3 s | `[ DEBRIS FIELD · MAIN BELT ]`: the round crosses the asteroid belt toward Earth.             |
| 14.6 s | `[ TERMINAL · SOL-3 ]`: re-entry.                                                             |
| 15.8 s | Back in the world: the round falls out of the sky as a blazing star with a plasma trail, lighting up the land as it comes. |
| 17 s   | **Impact**: a blinding flash and stylised impact frames (red edges on black, inverted cyan, posterised orange, halftone, ink), then the fireball dome, a condensation shell and a shock ring racing out over the ground. |
| 17–21 s | Debris rains out of the crater, kicking up dust where it lands, and a column of fire and smoke rises over the spire and spreads into a cap. The shooter's camera rides out the shock wave, then cranes up over the crater. |
| 20 s on | Lightning flickers in the ash column and thunder rolls in after it; embers drift up out of the molten bowl, the air over it shimmers, and the round's path through the sky hangs there as a smoke trail. |
| 18 s   | `[ IMPACT CONFIRMED ]` · `ZONE 0128 PLANED · SPIRE STANDING · 384 M`                         |

Everyone nearby sees the beam, the reticle, the falling star, the blast and the shock wave (the
boom arrives at the speed of sound, so far-off players hear it late). Only the player who fired gets
the feed and the camera shots. Press **Backspace** (rebindable) to skip the feed.

## What the strike does to the world

- **Bowl:** a white-hot bowl around the spire, about 70 blocks across and 22 deep at the default
  size, with lava pooled at its base.
- **Planed zone:** everything above the impact level (up to 140 blocks of hillside) inside the
  crater radius (64 blocks by default, a zone 128 blocks across) is vaporised. The ground is
  resurfaced with **Molten Crust**, which cools over several minutes into **Fused Crust**. Water
  quenches it instantly.
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

## Commands and game rules

- `/gungnir strike <pos>`: call a strike on a position (operators).
- `/gungnir cancel`: call off every strike that has not landed yet.

| Game rule              | Default | Meaning                                                  |
|------------------------|---------|----------------------------------------------------------|
| `gungnirTerrainDamage` | `true`  | `false` keeps terrain intact (entities are still hit).   |
| `gungnirCraterRadius`  | `64`    | Radius of the planed zone, 8–160.                        |
| `gungnirSpire`         | `true`  | Whether the spent round is left standing as a spire.     |

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
   `shooting-star-1.0.0.jar` in your `mods` folder.
3. The mod is needed on both the server and every client that should see the feed and effects.

## Building

```sh
./gradlew build            # jar in build/libs/
./gradlew runGametest      # server game tests: targeting, crust cooling, a full strike
./gradlew runClient        # play in a dev client
```

The `selftest` workflow plays a full strike in a real client under a virtual display and records it
as a video. The client and the integrated server run in lockstep and every frame is rendered at an
exact game time, so the result is a smooth 30 fps video even on a software renderer. The mod's
sounds are mixed in, and a screenshot of every phase is saved alongside.

The block and GUI textures and the sounds are generated by `tools/gen_textures.py` and
`tools/gen_sounds.py` (Python 3 with Pillow and NumPy, plus ffmpeg for the sounds). The feed's
meshes are built in Blender by `tools/models.py` (`pip install "bpy==4.5.*"`), and its planet and
sky maps are downloaded and prepared from NASA's originals by `tools/fetch_maps.py`.

## Credits

The feed's planet and sky maps are public-domain NASA imagery:

- Earth: *Blue Marble: Next Generation* (July 2004), the Blue Marble cloud layer, and *Black
  Marble 2016* city lights, from NASA Earth Observatory.
- Jupiter: *Cassini's Best Maps of Jupiter* (PIA07782), NASA/JPL/Space Science Institute.
- Milky Way and stars: *Deep Star Maps 2020*, NASA/Goddard Space Flight Center Scientific
  Visualization Studio (Gaia DR2, Hipparcos-2 and Tycho-2 data).
