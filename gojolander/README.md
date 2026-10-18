# Gojolander

A Fabric mod for Minecraft Java **1.21.1**: the first friend's own mod on the SMP, beside kortev's
[The Shooting Star](../README.md) and in the same repository and build. He plays the strongest there is, and kortev's
rival. It needs The Shooting Star installed: it is under that mod's rule that only a thing's owner crafts it (crafters do
not make it at all), and only for **him**. Here, his things work for him alone too: on anyone else the blindfold is a
strip of black cloth. (Until he is made its owner, it is open to everyone: see below.)

For now he has one thing, the blindfold, and what he casts with it.

## The blindfold

A band of black cloth tied over the eyes, knotted at the back with its two ends hanging. It is worn where a helmet would
be, and keeps out nothing a helmet would: wearing it is going without one.

- **Getting it:** echo shard over black wool, a nether star and black wool, over an echo shard. Only its owner can
  craft it.
- **Whose it is:** nobody's until kortev has his Minecraft name, and **while it is nobody's, anyone can craft it and
  cast with it**, to try it out. An operator makes it his alone on the server with `/gojolander owner <name>` (no new
  jar needed); the world keeps it, and from then on only he crafts it and only he can cast. (The stand-in for nobody,
  `-unclaimed-` in `Owner.PLACEHOLDER`, is a name no account can have.)

## Domain Expansion: Unlimited Void

His ult. With the blindfold on, he presses **Z** (rebindable: Domain Expansion). Second by second:

- **0 to 1.5 s, the eyes.** A hard cut to his face, close, the camera drifting slowly round him as his left hand comes
  up to his brow, hooks the blindfold and pushes it up onto his forehead, his head dipping a little, and his eyes open,
  burning blue; the hand drops. A breath drawn in; a cold chime as they light. Round him a ring of ink starts out over
  the ground, ragged, fingers of it branching ahead. The blindfold's ends swing, and whip about as the black closes.
- **1.5 to 1.9 s, the sign.** His right hand snaps up in front of his chin, the first two fingers crossed, and the
  camera punches in on it. DOMAIN EXPANSION comes up on the screen.
- **1.9 to 4.3 s, the barrier.** A cut to wide, the camera craning back and up round him. The ink reaches its full
  32 blocks at 2.5 s and the black floods up out of it like a liquid, fingers racing ahead of a white-hot curling lip,
  drips running back down, the air drawn in from every side faster and faster. **Anyone who gets out of the ring
  before it closes is not caught**: about 4 s from the key, against a sprint of a block every 3.6 ticks.
- **4.3 s, it closes.** One deep, soft blow and a glassy ring, cut off at once. Every living thing inside it but him
  is **caught**: players and mobs, in the air or not (not players in creative or spectator, and not bosses). For
  everyone in it, black and silence; his camera is back in his eyes. From outside it is a sphere of black, its skin
  rippling, stars deep inside it, the air bending round its edge and ink pooled at its foot.
- **5 s, the void opens** out of the great eye, over the whole sky, with a vast chord of sound. UNLIMITED VOID, and
  its name brushed in ink under it: 無量空処. Endless space, light flowing past in slow rivers that curve round the eye
  as they run in to it, the eye a black disc bending the stars round it in a ring of white fire, a band of light across
  it, and still black water underfoot giving it all back, rippling out round him. Whoever is in there with him is dark
  but for the eye's light along their edges. He is free to move, hit and use anything.
  - **The caught** are held exactly where they were for the whole of it: they cannot move, fall, look round, hit, use or
    break anything, and mobs stop thinking. Nothing knocks them about. Seen by anyone else they tremble where they
    hang, players with their heads tipped back, arms out from them. What they see is everything there is, all at
    once: the picture torn apart by light rushing in, flickering grids of meaning, flashes of colour and white; what
    they hear is a roar of data and voices and a ringing in the ears.
  - **Nothing crosses the barrier** either way while it stands. Whoever tries is put back where they were; anything
    fired at it or out of it stops against it, in a puff of ink.
- **15 to 16.5 s, the shattering.** Cracks run out across it from the eye (from outside, from the side facing you), in
  long rays and rings, then it shatters, the shards falling away nearest first, edges catching the light, the world
  behind and its air rushing back. His left hand comes up again and pulls the blindfold back down over his eyes. The
  caught go free **broken: half a heart**, and slow, weak and half blind for 15 s. The finishing is left to him.

He can cast it again **15 minutes** after he cast it (the blindfold shows it cooling, through leaving and coming back
too). There is no other cost. It ends at once, shattering, if he dies or leaves (then the caught are only shaken, not
broken), or if kortev's Genesis Key takes the world; and it is not cast at all while a Genesis Key event is going on
(from the key turning until everyone is home). Killing him while he casts it, before it closes, stops it.

### Commands (operators)

| Command | What it does |
|---|---|
| `/gojolander owner [<name>]` | Shows, or sets, whose the blindfold is (nobody's, and so anyone's, until set). |
| `/gojolander domain <pos>` | Casts a Domain at `pos` with nobody casting it: everyone inside is caught. |
| `/gojolander collapse` | Shatters every Domain now. |
| `/gojolander ready <player>` | Makes that player's Domain ready again. |

### Game rules (for balancing it against everyone's mods)

| Rule | Default | Range | What it is |
|---|---|---|---|
| `unlimitedVoidRadius` | 32 | 8 to 64 | How far it reaches from where he casts it, in blocks. |
| `unlimitedVoidHoldSeconds` | 10 | 1 to 30 | How long the void stays open, holding the caught. |
| `unlimitedVoidCooldownSeconds` | 900 | 0 to 7200 | How long before he can cast it again. |
| `unlimitedVoidBreaks` | true | | Whether whoever it holds to the end is left at half a heart. |

## How it is made

- `gojolander/` is its own Gradle subproject and jar (`gojolander-<version>.jar`), set up like `chitty/`. Its tests
  (`DomainGameTests`) and its client self test (`DomainSelfTest`, `-Pselftest=domain`, screenshots of it cast, watched
  from outside and caught in) are in the root project's harness.
- The server: `domain/DomainManager` (who may cast, catching, holding, the barrier, letting go), against
  `domain/DomainTimeline`. The client: `ClientDomains` (state, sounds), `DomainRender` with `gl_domain.fsh` (the ink,
  the black rising, the sphere, the black and the reveal, the void, the shattering, in one full-screen pass) and
  `gl_rim.fsh` (whoever is in the void with you, lit by the eye), `gl_sky.fsh` (the void's smooth sky, painted
  into a small panorama every other frame so the pass only looks it up: the pass is about a fifth of what it was), `DomainHud` with `gl_flood.fsh` (the flood, the
  name), `DomainCamera` (his shots and the punch-in), `BlindfoldFeature`, `HandSign`, `PlayerModelMixin` and
  `HeldTrembleMixin` (the cloth, the eyes, his hands lifting and lowering it, the sign, the caught trembling). The self
  test logs the frame rate and each pass's cost once a second (`[perf]` in the log).
- Sounds: `python tools/gen_gojolander_sounds.py`; textures: `python tools/gen_gojolander_textures.py`.
