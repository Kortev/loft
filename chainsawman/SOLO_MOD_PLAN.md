# Plan: Chainsaw Man as a full solo mod (decided 2026-11-06)

kortev (2026-11-06): "just do whatever you think is best for a full solo mod". This is the plan Opus chose. It came
from a viewer comment (@Dreclo: "a playable Eternity and a Denji enemy that will rip you up until you give up your
heart") and kortev's points: Denji is already the playable character, so no Denji enemy; the mod should be a normal
public Chainsaw Man mod apart from the SMP; "people have different abilities".

The HANDOFF rules still apply: **no spoilers past the Reze arc**, one piece at a time committed and pushed, **show
renders before baking a look** (match the anime; kortev reacts badly to invented looks; Beam took 8 tries), end each
round with a numbered list of what to test in game. Opus plans and reviews looks; Sonnet 5.5 subagents build from
brief files.

## The idea in one line

Two ways to be strong, both from the show: **find Pochita and become Chainsaw Man**, or **beat devils and make
contracts with them** like the Public Safety devil hunters. Every contract has a power and a price.

## Phase 0: stand alone (small, do first)

- Drop the `shootingstar` dependency from `chainsawman/src/main/resources/fabric.mod.json`. The mod only uses
  `OwnerOnly` (Pochita's crafting lock) and the gfx helpers `Post`, `Shaders`, `Target`. Copy those three helpers into
  the chainsawman client package; drop the owner lock (anyone can craft or find Pochita). Update the description.
- The game tests can stay in the shared harness; the published jar must run with only Fabric API.
- Check: the chainsawman jar alone in a clean Fabric instance loads, finds Pochita's den, transforms.

## Phase 1: devil contracts, starting with the Eternity Devil

**The contract system** (`contract/` package):
- A player holds **one contract at a time** (making a new one ends the old). Server-owned state saved on the player,
  synced to clients like `Form`.
- **Contract powers work in human form only.** Chainsaw Man form and contracts stay two different ways to fight.
- One key, "Use contract" (pick a free default; check conflicts with Chitty, Gojolander and vanilla: the Hollow
  Purple work found X and C already taken).
- **How you get one:** beat a devil down to low health and it stops fighting and **offers a contract** (a short
  screen in the show's style: its name, the power, the price, Accept / Kill it). Killing it drops its loot instead.
  That choice is the hook for reels and comments ("would you take it?").
- **Prices** are real and permanent while the contract lasts (for example max hearts, hunger, a lifespan-style
  countdown). Shown plainly before you accept.

**The Eternity Devil** (anime season 1, the hotel arc):
- **Where:** a rare abandoned hotel structure in the world (a small worldgen structure, plus `/chainsaw eternity` to
  place one).
- **The encounter:** step onto its 8th-floor hallway and the hallway **loops**: walk off either end or take the stairs
  and you are back at the start (a real looping space, built once as a sealed pocket and teleported through seamlessly).
  Signs that something is wrong build up, then the devil **grows out of the walls**: a huge mass of flesh with many
  eyes and mouths, as in the anime.
- **The fight:** it regenerates and grows; flesh tendrils and mouths attack. Beat it down and it offers its contract.
  Kill it and the loop breaks and you are back in the hotel.
- **The contract power, Eternity:** trap a target (mob or player) in a looping hallway pocket for a while; they come
  back where they were taken from. Price: decide and say why (for example 2 max hearts while held).
- The look must be **shown to kortev as renders first** (a turnaround of the devil and the hallway) before anything is
  baked. Opus does the look itself if a Sonnet attempt misses, as it did for Beam v8.
- Sounds synthesized like the others; no ripped audio.
- Game tests: the loop teleports seamlessly both ways; the devil offers a contract at low health; accepting sets the
  contract and price; killing drops loot and frees the player; contract power traps and returns a mob; one contract at
  a time; contracts blocked in Chainsaw Man form.

**First reel (a reply to @Dreclo's comment):** a normal hallway → it loops back → "wait..." → flesh pushes out of the
walls → the fight → it offers the contract → accept → use Eternity on a mob. Real mob AI, real keys, every beat bigger.

## Later phases (one devil each, each its own reel; all season 1 / Reze arc)

1. **Fox Devil** (Aki's): a giant fox head bursts out and bites the target. Very recognisable.
2. **Curse Devil** (Aki's): stab a target with the nail three times and a huge devil crushes it.
3. **Ghost Devil** (Himeno's): an invisible hand that grabs and chokes.
4. **World devils as plain enemies** to fight in the form: Bat Devil, Leech Devil, and the like.
5. **Power** as a companion who fights with blood weapons.
6. Release: a Modrinth/CurseForge page, a description, screenshots; the link goes in every reel's description.
