# The Shooting Star

A Fabric mod for Minecraft Java **1.21.1** based on the "SS-03 Gungnir" orbital-strike reel.

Use the **Gungnir Uplink** on any block 42–640 blocks away to get a kinetic lock on it. The
minimum is 1.5× the crater radius, so the shooter is never caught in their own blast. A mass driver
ringing Jupiter spins a round up over seven laps to 0.96c and fires it across the asteroid belt.
About 17 seconds later the round lands on the target: it planes a crater into glowing molten crust
and leaves a spire of hull plating standing through the full height of the world.

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
| 15.8 s | The round becomes a falling star above the target.                                           |
| 17 s   | **Impact**: a halftone impact frame, a shockwave, then a wide shot of the spire.             |
| 18 s   | `[ IMPACT CONFIRMED ]` · `ZONE 0056 PLANED · SPIRE STANDING · 384 M`                         |

Everyone nearby sees the beam, the reticle, the falling star and the impact. Only the player who
fired gets the feed and the camera shots. Press **Backspace** (rebindable) to skip the feed.

## What the strike does to the world

- **Bowl:** a white-hot bowl around the spire with a ring of lava at its base.
- **Planed zone:** everything above the impact level inside the crater radius (28 blocks by
  default) is vaporised. The ground is resurfaced with **Molten Crust**, which cools over several
  minutes into **Fused Crust**. Water quenches it instantly.
- **Rim:** a lip of blackstone, basalt and magma, plus debris thrown out by the impact.
- **Scorched ring:** out to 1.5× the radius, leaves are stripped, glass shatters, sand fuses to
  glass, snow melts and fires start.
- **Spire:** a round, 5-block-wide column of Gungnir Hull with glowing coil bands. It runs from
  the bottom of the world to the build limit and has tail fins at the top.
- **Entities:** anything in the bowl is killed. Anything farther out takes less damage the farther
  it is, catches fire and gets thrown outward. Unbreakable blocks (bedrock, command blocks,
  barriers) are never touched.

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
| `gungnirCraterRadius`  | `28`    | Radius of the planed zone, 8–64.                         |
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

The `selftest` workflow plays a full strike in a real client under a virtual display and saves a
screenshot of every phase. The art and sounds are generated by the scripts in `tools/`, which need
Python 3 with Pillow and NumPy, plus ffmpeg for the sounds.
