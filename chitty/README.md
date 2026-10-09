# Chitty Chitty Bang Bang

A Fabric mod for Minecraft Java **1.21.1**: the car from the film, to drive, fly and float, and Baron Bomburst's
Vulgarian airship ([below](#the-vulgarian-airship)). It is built beside [The Shooting Star](../README.md), in the same
repository and build, and needs it installed: its things share The Shooting Star's names and its rule that only
**kortev** crafts them (crafters do not make them at all). Everyone can ride in them and break them once they are made.

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
- **Start her:** taking the wheel swings the starting handle at the front, the old way. Most swings catch: chitty,
  chitty, bang, bang, and she runs. Now and then one doesn't: a cough, a sputter, and she dies; press forward to swing
  it again (she always catches by the third swing). Until she has caught the pedals do nothing. In the air she catches
  at once.
- **Drive:** forward and back to accelerate, brake and reverse, left and right to steer. She climbs a block at a time
  and stops at walls rather than driving her bonnet into them. The gear lever and handbrake move as she is driven. Her
  body rides its springs over the wheels: it squats as she pulls away, dips its nose as she brakes, leans out of a
  turn and bounces over bumps, steps and drops, more on a dirt road than on paving.
- **Rev her:** V (rebindable), standing: the engine roars up and the needle with it, she twists on her springs, and
  let go of after a good roar she often backfires. Rev her and then press forward to let her away in a cloud of tyre
  smoke.
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
- **Riding in her:** everyone aboard turns with her, rides her springs, and in the air the view banks with her as she
  turns. The dashboard's needles show her speed, her height above the sea and the engine's revs. She is solid from her
  radiator to her stern: click anywhere on her to take the seat nearest the click.

She starts with two sputters and two bangs, and backfires (bang bang) every so often as she runs and when the
throttle comes off at speed, each bang a tongue of flame and a puff of dark smoke out of the exhaust. Ticking over she
shakes at every pair of firings, and the strap round her bonnet slaps and its buckle jingles. Her tyres squeal and
smoke on paving when they spin or slide (a revved getaway, a hard stop, a fast tight turn), and on a dirt road she
throws up dust behind her. Once she has run a while her bonnet is hot, and standing, the air over it and over the end
of her pipe shimmers. She splashes into water, and the wind rushes
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

## The Vulgarian airship

Baron Bomburst's airship from the film, built in Blender from its stills:
- **The envelope:** 34 blocks long, white, banded at each end in Vulgaria's purple and black, with Vulgaria's arms on
  each flank (a black griffin with a grey wing rearing behind a shield quartered gold and black, drawn as crisp
  heraldry).
- **The frame:** a bronze frame under the envelope, its bars just over the crew's heads.
- **The gondola:** hung below the frame on wires. It is a little black gondola carved and gilded as the film's, with
  a winged cherub beside the Baron's black-letter B at the bow, rope wound on drums past the bow and a black iron
  engine section at the stern.
- **The propellers:** two pushers behind the stern, on outriggers from the gondola, each turned by a belt over a
  spoked pulley.
- **Below and behind:** a four-tined grapple hangs under the keel and a searchlight stands on the frame. A white
  tailplane and dark fins with the rudder hang under the tail.

- **Put her down:** use the item on the ground; her gondola sits on it and she faces the way you face. Use her to
  board: you come aboard at the free place nearest where you click. Eight can stand in the gondola, and walk about in
  it with the movement keys; nobody can fall out.
- **The controls are on screen:** aboard, a panel at the right lists the keys for what you can do where you are
  (walking about, or at the wheel), each lighting up as you press it, with a gauge for the grapple: how far down it
  is, which way the winch is turning and what is on it. On the ground with the grapple in hand, hanging on it or
  caught on it, a smaller panel says what you can do. F1 hides it with the rest of the HUD.
- **Take the wheel:** walk up to the wheel in the bow and it is yours: now the movement keys fly her, and the view goes
  behind her to see all of her. Sneak to let go of it; you walk about again, in the view you had before.
- **Fly:** forward and back for the propellers, left and right to steer (she turns even standing still), jump to rise
  and sprint to sink. She is heavy to handle, as an airship is, gathering speed and turning with a little lag, but
  quicker than a galloping horse at full speed, and keeps her height wherever she is left, piloted or not. Her envelope keeps out of hills and trees: she stops rather than drive it into them.
- **She lifts six.** With more aboard, a load on her grapple counting as one, she cannot climb and sinks slowly, as
  she does in the film. The pilot's **O** (overboard, rebindable) throws whoever stands furthest aft over the side.
- **The grapple:** anyone aboard works its winch: hold **R** to let it down on its rope (as far as 64 blocks), hold
  **Y** to wind it in (both rebindable); let go and it stops where it is. It never goes up or down by itself. The
  winch runs up to speed and slows smoothly, slower with a load. It is
  a weight on a rope: it swings, trails behind her as she flies and comes to rest with a clank on whatever it lands
  on, and what it carries swings with it. Going, it seizes the first thing its tines meet (lying still it catches
  nothing): a mob, a player, a dropped item, a boat, a minecart, even Chitty. Then the winch stops, and the crew
  decide: wind it in to lift its load (as far as three blocks under her keel) or let it out until the load stands on
  the ground, where it is let go. Wound all the way in, the empty grapple is stowed. A caught player cannot simply
  step off: holding sneak, they struggle (a bar fills as they go), and after ten seconds of it they wrench free and drop.
  You can tell who chose to be there: something caught hangs limp by the back of its collar from the tines, and
  kicks and jerks on the rope; someone hanging on by choice hangs from the ring by both hands. Aboard, the action bar
  says which ("Caught on the grapple: Husk", or "Steve is hanging on the grapple").
- **From below:** while the grapple hangs empty, anyone on the ground can use it to take hold of it. It goes where
  their hand goes, its rope paying out after them. Then:
  - use it on someone (or something) within four blocks to hook it on, for the crew to wind up;
  - press **R** to throw it the way you look: it flies out on its rope and takes hold of the first thing it hits;
  - jump to hang on it: you swing under her as she flies (lean with the movement keys to swing it), and sneak to drop
    off. The crew winding it in bring you up to her keel, and you climb aboard;
  - sneak to let go. Her pulling further away than her rope reaches pulls it out of your hands.
- **The rope:** slack rope sags in a curve; the drum at the bow turns as it winds; the rope creaks under a load; and
  the grapple rattles and strikes sparks when it is dragged along the ground.
- **The rope ladder:** **K** (rebindable, anyone aboard) lets it down from the rail on her left as far as the ground
  (64 blocks at most), or draws it up. Anyone can climb it as a ladder against a wall: walk into it (or jump) to go up,
  sneak to hold on; its rungs knock as you go. It trails behind her as she flies, and carries whoever is on it along.
  Climbing off its top takes you aboard into a free place.
- **Bombs:** craft them (iron, two gunpowder and string make two), then use them on her to fill the rack in the
  gondola, six at most. **B** (rebindable, anyone aboard) drops the next one through the floor, one every one and a
  half seconds. A bomb whistles down and goes off where it strikes the ground, the water or someone, smaller than TNT
  but enough to break the ground.
- **Getting off:** sneak. When she is down (or nearly) you step off beside her. In the air her rope ladder lets itself
  down: keep sneaking and you climb down onto it. At the wheel, sneaking lets go of the wheel first.
- **Riding in her:** everyone aboard stands, walks about (their steps knocking on her boards) and turns with her. She is solid all along: her gondola, her
  envelope (you can stand on it) and her tail have hitboxes, and hitting any of them hits her. In third person the
  camera turns about the middle of her and stands far enough back to see all of her. Nobody aboard takes fall damage. Hit her hard enough and she drops back into an
  item, with any bombs left in her rack.

Her engine putters along in the stern while she is piloted, its two propellers beating the air a little out of step,
and the wind sings in her rigging as she goes. Now and then the great envelope creaks, the winch ratchets as the
grapple goes up and down, and the ladder unrolls with its rungs knocking.

**Craft her** (the recipe unlocks with a phantom membrane), and her bombs (with gunpowder):

```
P W P     P = Purple Wool        W = White Wool
M C M     M = Phantom Membrane   C = Chain
G B G     G = Gold Ingot         B = any Boat

Bombs (shapeless, makes 2): Iron Ingot, Gunpowder, Gunpowder, String
```

Her model, texture and icon come from `tools/airship_model.py`. Its `--game` option builds her faceted, to sit among
Minecraft's blocks, and bakes her colours into one atlas with Chitty's exporter: the game draws it pixelated and lights
each flat face by which way it faces, as it does its own mobs. Its `--out DIR --renders` option renders her, round,
from the film's angles. Three drawing tools supply its
art:
- `tools/vulgaria_arms.py` draws her arms;
- `tools/airship_carving.py` draws the gondola's gilt carving and lights it as raised gold;
- `tools/gen_airship_sounds.py` makes her sounds.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.1**.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) (any 1.21.1 build), The Shooting Star
   (`shooting-star-1.0.0.jar`) and `chitty-chitty-bang-bang-1.0.0.jar` in your `mods` folder.
3. Both are needed on the server and on every client.

## Building

From the repository's root, with The Shooting Star: `./gradlew build` puts this jar in `chitty/build/libs/`, and
`./gradlew runGametest` runs her game tests with everything else's (see the main README).
