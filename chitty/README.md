# Chitty Chitty Bang Bang

A Fabric mod for Minecraft Java **1.21.1**: the car from the film, to drive, fly and float. It is built beside
[The Shooting Star](../README.md), in the same repository and build, and needs it installed: Chitty's things share
its names and its rule that only **kortev** crafts them (crafters do not make them at all). Everyone can ride in her
and break her once she is made.

GEN 11, the car from the film, built in Blender from photographs of it: a long polished aluminium bonnet, an
egg-shaped radiator (a gold rim round a grey honeycomb, round over the top and wider low down) with the great brass
headlamps set into its rim, the GEN 11 plate hung under it and a leather strap round the bonnet; a boat of varnished
red and white cedar behind, open over the front seat (its edge cut down in a U each side to step in over) and decked
over to a pointed stern, the back seat sunk in an oval well in the deck with wood all round it, a wicker hamper on a
rack under the point; black wings over red artillery wheels, black running boards with brass grilles, a red spare
wheel stood against the bonnet just behind the front wing, four copper pipes out of the bonnet into the great exhaust
along the running board, the brass serpent horn running low along the bonnet over the front wing, a brass spotlight
with a carrying handle on each post of the windscreen, and the gear lever and handbrake outside the driver's door.

- **Put her down:** use the item on the ground or on water; she faces the way you face. Use her to get in: you take
  the seat nearest where you click (the driver's, the one beside it or either of the two in the back), and the one in
  the driver's seat (the right-hand one) drives. Sneak to get out.
- **Drive:** forward and back to accelerate, brake and reverse, left and right to steer. She climbs a block at a time
  and stops at walls rather than driving her bonnet into them. The gear lever and handbrake move as she is driven and
  the starting handle swings as she starts.
- **Fly:** G (rebindable) opens the wings, slowly and with a great creaking. Each side wing is one pleated red and
  yellow cloth, folded up on edge under the running board; its back edge stays along her side while the front edge
  swings out and the pleats flatten as it unfolds, until it lies out flat with a mast standing up at its end and a
  propeller turning flat on top. The nose wing, four sections spreading from a line under the GEN 11 plate to five bat
  points (the middle one furthest out), and the tail wing, five sections behind under the hamper, unfold the same way,
  pleats flattening as they spread; and the pusher propeller unfolds on its shaft on the stern. Then jump at speed to
  lift off.
  Hold jump to climb, sprint to dive, forward for more speed; slow down too far and she sinks. G again folds them
  away; opened by hand they stay open on the ground until you put them away.
- **Off a cliff:** she falls, and only as the ground (or the sea) comes up at her do the wings spring out by
  themselves and pull her up out of the dive, as long as someone is aboard.
- **Float:** the raft is hers alone, there is no key for it. In the water she wades and settles at first; after a
  couple of seconds she turns her wheels flat and blows up a great pink raft under herself, pointed at both ends, and
  drives a screw behind. Never with her wings out: come down on the water from the air and the wings fold and the raft
  comes up at once; open the wings on the water and she lifts off it. Drive up a bank and she climbs out onto the
  land, where the raft goes down again. She does it with nobody aboard too: get out and run as the tide comes in, and
  she blows it up herself.
- **Ejector:** X (rebindable), for the driver: the back seat springs up out of its well on two brass springs and
  bounces back down, throwing whoever is on it high into the air. Players float down under slow falling; mobs come down
  as they will. A mob put in her (`/ride`) takes the back seat first.
- **Hamper:** use the hamper on her stern to open it (27 stacks; it has its own hitbox, hers being too short to reach
  it); sneak and use her to take it off (it spills what it holds) or put it back on.
- **Horn:** H (rebindable) squeezes the serpent's bulb.
- **Riding in her:** everyone aboard turns with her, and in the air the view banks with her as she turns. The
  dashboard's needles show her speed, her height above the sea and the engine's revs. At night (or underground, or in
  heavy rain) her headlamps and spotlights throw soft beams ahead of her while she runs. She is solid from her
  radiator to her stern: click anywhere on her to take the seat nearest the click.

She starts with two sputters and two bangs, and backfires (bang bang) every so often as she runs and when the
throttle comes off at speed, each bang a tongue of flame and a puff of dark smoke out of the exhaust. She splashes into water, and the wind rushes
past her as she flies or falls fast. Her engine runs chit-ty chit-ty: it fires in pairs, a hard firing and a softer one hard on
its heels, and is three sounds made at different revs and crossfaded as hers rise and fall. Her polished aluminium
and brass shine as you look at them, reflecting the sky, the sun and the ground she is on, rather than having
reflections painted on. Nobody aboard takes fall damage.
Hit her hard enough and she drops back into an item, as a boat does. In third person the camera stands twice as far
back while you ride in her.

**Craft her** (the recipe unlocks with an elytra):

```
 E        E = Elytra          G = Gold Ingot
G M G     M = Minecart        P = Piston
P B P     B = any Boat
```

The model, its texture and the item icon come from `tools/chitty_model.py` (Blender's Python module,
`pip install "bpy==4.5.*"` on Python 3.11): `--game` unwraps every part into one atlas, bakes her look into it with
Cycles (the sky and soft shadows; for the polished metal only how shut in it is, the game shining it live) and writes
the game's mesh and texture; `--out DIR
--renders` writes `.blend` and `.glb` files and renders her on the road, flying and afloat. Her sounds come from
`tools/gen_chitty_sounds.py`, which models the engine (each firing and its rush of gas through its own length of header
into one long flexible pipe, heard outdoors) rather than imitating it.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.1**.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) (any 1.21.1 build), The Shooting Star
   (`shooting-star-1.0.0.jar`) and `chitty-chitty-bang-bang-1.0.0.jar` in your `mods` folder.
3. Both are needed on the server and on every client.

## Building

From the repository's root, with The Shooting Star: `./gradlew build` puts this jar in `chitty/build/libs/`, and
`./gradlew runGametest` runs her game tests with everything else's (see the main README).
