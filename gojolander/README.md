# Gojolander

A Fabric mod for Minecraft Java **1.21.1**: the first friend's own mod on the SMP, beside kortev's
[The Shooting Star](../README.md) and in the same repository and build. He plays the strongest there is, and kortev's
rival. It needs The Shooting Star installed: it is under that mod's rule that only a thing's owner crafts it (crafters do
not make it at all), and only for **him**. Here, his things work for him alone too: on anyone else the blindfold is a
strip of black cloth.

For now he has one thing, the blindfold, and what he casts with it.

## The blindfold

A band of black cloth tied over the eyes, knotted at the back with its two ends hanging. It is worn where a helmet would
be, and keeps out nothing a helmet would: wearing it is going without one.

- **Getting it:** echo shard over black wool, a nether star and black wool, over an echo shard. Only its owner can
  craft it.
- **Whose it is:** until kortev has his Minecraft name it is `gojolander`, a stand-in. An operator sets the real one
  on the server with `/gojolander owner <name>` (no new jar needed); the world keeps it. It can also be changed in
  `Owner.PLACEHOLDER`.

## Domain Expansion: Unlimited Void

With the blindfold on, its owner presses **Z** (rebindable: Domain Expansion). Second by second:

- **0 to 1.1 s, the sign.** He pushes the blindfold up onto his forehead, his eyes burning blue under it, and brings
  his right hand up in front of his face, the first two fingers crossed. His own camera comes out of his eyes and round in
  front of him, close on his face. A held breath, the tick of his fingers crossing, a low swell under glass singing.
  DOMAIN EXPANSION comes up low on the screen. Round him a ring of ink spreads out over the ground, 16 blocks, a seething
  blue-white edge running ahead of it. **Anyone who gets out of the ring now is not caught.**
- **1.1 to 1.5 s, the barrier.** Black flows up out of the ring, round and over, its climbing edge white-hot, the air
  drawn in from every side. His camera pulls back and up to see it close over him.
- **1.5 s, it closes.** A deep, soft blow and a glassy ring. Every living thing inside it but him is **caught**:
  players and mobs, in the air or not (not players in creative or spectator, and not bosses). UNLIMITED VOID, and its
  name in kanji under it. From outside it is a sphere of black, a sheen sliding over it, deep blue at its edges.
- **2 to 7 s, the void.** Inside, the world is gone: endless space, stars and drifting light, threads of light
  streaming past towards a vast black eye ringed with white fire, and still black water underfoot giving it all back.
  He is back in his own eyes, free to move, hit and use anything.
  - **The caught** are held exactly where they were: they cannot move, fall, look round, hit, use or break anything,
    and mobs stop thinking. Nothing knocks them about. What they see is everything there is, all at once: the picture
    torn apart by light rushing in, flickering grids of meaning, flashes of colour and white; what they hear is a roar
    of data and voices and a ringing in the ears.
  - **Nothing crosses the barrier** either way while it stands. Whoever tries is put back where they were; anything
    fired at it or out of it stops against it, in a puff of ink.
  - The Domain does **no harm of its own**. The kill is whatever he does in those five seconds.
- **7 to 7.8 s, the shattering.** Cracks of white light all over it, then it shatters, the shards falling away and the
  world rushing back. The caught go free, **dazed for 5 s** (slowed, and players' sight going in and out).

He can cast it again **5 minutes** after he cast it (the blindfold shows it cooling, through leaving and coming back
too). It ends at once, shattering, if he dies or leaves; it is not cast at all, nor kept, while kortev's Genesis Key has
the world gone.

### Commands (operators)

| Command | What it does |
|---|---|
| `/gojolander owner [<name>]` | Shows, or sets, whose the blindfold is. |
| `/gojolander domain <pos>` | Casts a Domain at `pos` with nobody casting it: everyone inside is caught. |
| `/gojolander collapse` | Shatters every Domain now. |
| `/gojolander ready <player>` | Makes that player's Domain ready again. |

### Game rules (for balancing it against everyone's mods)

| Rule | Default | Range | What it is |
|---|---|---|---|
| `unlimitedVoidRadius` | 16 | 8 to 48 | How far it reaches from where he casts it, in blocks. |
| `unlimitedVoidHoldSeconds` | 5 | 1 to 30 | How long the void holds the caught. |
| `unlimitedVoidCooldownSeconds` | 300 | 0 to 3600 | How long before he can cast it again. |

## How it is made

- `gojolander/` is its own Gradle subproject and jar (`gojolander-<version>.jar`), set up like `chitty/`. Its tests
  (`DomainGameTests`) and its client self test (`DomainSelfTest`, `-Pselftest=domain`, screenshots of it cast, watched
  from outside and caught in) are in the root project's harness.
- The server: `domain/DomainManager` (who may cast, catching, holding, the barrier, letting go), against
  `domain/DomainTimeline`. The client: `ClientDomains` (state, sounds), `DomainRender` with `gl_domain.fsh` (the ring,
  the sphere, the void, the shattering, in one full-screen pass, the void's people drawn again over it), `DomainHud`
  with `gl_flood.fsh` (the flood, the name), `DomainCamera` (his shot), `BlindfoldFeature` and `HandSign` (the cloth,
  the eyes, the sign).
- Sounds: `python tools/gen_gojolander_sounds.py`; textures: `python tools/gen_gojolander_textures.py`.
