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

## Phase 0: stand alone (DONE 2026-11-06)

- Drop the `shootingstar` dependency from `chainsawman/src/main/resources/fabric.mod.json`. The mod only uses
  `OwnerOnly` (Pochita's crafting lock) and the gfx helpers `Post`, `Shaders`, `Target`. Copy those three helpers into
  the chainsawman client package; drop the owner lock (anyone can craft or find Pochita). Update the description.
- The game tests can stay in the shared harness; the published jar must run with only Fabric API.
- Done: helpers copied to `client/gfx` (Post and Target as they were, Shaders cut to its uniform setters; the `csm_screen` vertex shader), no gradle dependency on the root. The owner gates (feeding Pochita, merge, the den hint) now read `Owner` (saved with the world, unclaimed by default = anyone): `/chainsaw owner <player>` or `clear`; kortev's SMP runs `/chainsaw owner kortev` once.
- Check: the chainsawman jar alone in a clean Fabric instance loads, finds Pochita's den, transforms.

## Phase 1: devil contracts, starting with the Eternity Devil

**The contract system** (`contract/` package):
- A player holds **one contract at a time** (making a new one ends the old). Server-owned state saved on the player,
  synced to clients like `Form`.
- **Contract powers work in human form only.** Chainsaw Man form and contracts stay two different ways to fight.
- One key, "Use contract" (pick a free default; check conflicts with Chitty, Gojolander and vanilla: the Hollow
  Purple work found X and C already taken).
- **How you get one (as built for the Eternity Devil, 2026-11-07):** the devil **offers a contract** while it has you
  in its loop (a short screen in the show's style: its name, the power in one line, the price, Accept / Refuse); refuse
  and you fight it. Killing it drops its loot (flesh and blood) and gives no contract. That choice is the hook for reels
  and comments ("would you take it?"). The first plan (beat it down and it offers) was dropped: it did not fit the show.
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
- **The contract power, Eternity** (the first idea, a looping hallway pocket for a target, was dropped for **Forever**:
  see the Forever entry below). Its price is 3 XP levels a use.
- The look must be **shown to kortev as renders first** (a turnaround of the devil and the hallway) before anything is
  baked. Opus does the look itself if a Sonnet attempt misses, as it did for Beam v8.
- Sounds synthesized like the others; no ripped audio.
- Game tests: the loop teleports seamlessly both ways; the devil offers a contract at low health; accepting sets the
  contract and price; killing drops loot and frees the player; contract power traps and returns a mob; one contract at
  a time; contracts blocked in Chainsaw Man form.

**The look, first pass (2026-11-06):** `python tools/eternity_drawings.py work/eternity_look` draws
`eternity_devil.png` (front, side, the heart) and `eternity_hallway.png` (your view, the devil grown out of the walls,
the loop from above) at 1 px = 1/16 block. Sent to kortev; waiting on their verdict. Drawn from text descriptions only
(the wikis block fetching), so kortev's own stills would beat it.

**The contract: still open (2026-11-06).** kortev's points so far (HANDOFF rule 6: the show's world, not its story):
- The hotel stays as the devil's lair and boss arena, but **the quest round it is our own**, not the anime's plot.
- **The power is not the loop.** The devil's power is eternity in general; it just happened to take over a hotel.
  Ideas put to kortev, none picked yet: **Echo** (Opus's pick: your last 5 s replay as an afterimage, again and
  again, for ~15 s), frozen moment (5 s, stored hits land at once), rewind (snap back to a marked moment and its
  health), undying (10 s on half a heart, some damage catches up after), endless wound (the target can't heal for 20 s).
  A second round: endless fall, stuck in a loop (the target replays its own last 3 s), one more time (cheat death
  once a day), everlasting shots (projectiles orbit you), make it eternal.
  **Leading (kortev likes it, not locked in): Echo as a multiplying crowd.** A see-through copy replays your last
  5 s; each copy splits in two when its replay ends (1→2→4→…→32, each a moment behind the last), a rolling wave of
  you within ~20 blocks; at the end (~20 s) every copy turns to the target and strikes at once, then fades. Each
  copy hits weakly and a target takes echo hits only a few times a second. Copies are render-only ghosts, with only
  their hits worked out on the server. kortev: big visuals are how the mod should stand apart from other mods.
  **kortev's next step (2026-11-06): a crowd of you all around at once** (they likened it to a hero who duplicates
  himself; make it our own, not that scene). Current shape: press and you shatter into ~12-16 copies ringing the
  target (some on blocks above), each **you from a different moment in eternity**: **past** copies faded and
  greyish with trails behind them (where they've been), **future** copies bright and outlined with a faint ghost
  path ahead of them (where they're going), you in the middle in full colour. They copy your moves in a ripple round
  the ring; the ring closes in or widens as you step; after ~15 s it collapses back into you with one last hit from
  every copy.
  **Decided (kortev, 2026-11-06): Echo is the Eternity contract's power; build it.** The copies **mirror your moves**
  (a ripple round the ring); a mix of past and future you, and **you're disguised among them** (to others you look
  like one more copy; mobs lose track of you). The copies stand on real ground at different heights, jump, step up,
  and never sit inside blocks. Lots of visuals and atmosphere: it's for videos. Brief: `work/briefs/eternity_echo.md`
  (on GraehamsPC). The price and how the contract is gained are still open; a test command grants it for now.
  **Echo replaced (kortev, 2026-11-07).** Echo v1 was built (see HANDOFF); kortev: too easy to tell who's who, not
  cool, and a ring round one target fails with several enemies or a target that walks out. A v2 (copies spread to
  every enemy and chase) was briefed (`work/briefs/eternity_echo_v2.md`) then shelved: kortev asked what a contract
  with this devil would really be. In the show (to the Reze arc) a contract either calls the devil down on someone
  (Fox bites, Ghost grabs, Curse kills) or, for an idea devil like Future, the devil lives in you and gives you a piece
  of what it is; prices are a fee per use (Fox), lifespan (Curse), an eye (Ghost). Contract powers come from the devil
  itself (its idea), not the show's scenes; the show's scenes are for the boss fight.
  **Now (kortev agreed, 2026-11-07): "Forever", lock a target in an endless moment.** Call it on a target: it freezes
  in one instant and every moment of it shows at once (a fading line of copies back along where it came from and on
  to where it would have gone); every hit you land is stored and all of them go off together when the moment ends.
  Starting numbers: 3 s (shorter on bosses), stored hits at ~1.5x with a cap, ~30 s cooldown. kortev: "the visuals
  have to make sense for the Eternity Devil", so the devil itself shows up when it's used (its flesh, its many eyes
  and mouths, its crowd voice), the way the Fox contract shows the fox's head. Price proposed, not confirmed: a fee
  per use, it eats a few XP levels (your time). Storyboard first: `tools/eternity_contract_drawings.py`.
  **Built (2026-11-07), waiting on kortev's verdict:** see the Forever entry in `HANDOFF.md` (price 3 XP levels, 3 s / 1.5 s on bosses, hits x 1.5 capped at 40, 30 s cooldown; clip `work/forever/forever_clip.mp4`). The Echo notes above are history; Echo is removed.
- **No max-heart prices** (kortev worried they'd stack; they can't, one contract at a time, but still no hearts).
  The price is what the devil asks for; ideas: beds stop working for you, faster hunger.
- **Killing and getting a contract are different things.** Killing gives loot (its flesh, a lot of hunger and
  nausea, and its blood, a bottle that refills Chainsaw Man's blood bar completely); a contract is a deal the devil
  offers with its price, in the loop, to whoever it has trapped.
- **The hotel quest (built 2026-11-07; the quest is our own, not the anime's plot):** the hotel stands dead by day
  (dark, chained) and wakes at night when a player is near (lit lobby, open doors, a bell on the desk). Floors 2 to 7
  are each a little more wrong (a repeated door number, a painting that comes again, a stain that spreads, an eye in
  the wallpaper, a door that breathes, a phone ringing in an empty room); on 8F the corridor loops, with flesh at the
  corners growing each lap. After 3 laps (or a minute) the devil speaks and offers: **Accept** and you get the
  contract and walk out (the devil goes away for `devilRespawnDays`); **Refuse**, a hybrid or silence and the walls
  bleed and it grows out of them at the corridor's end: 300 health, mouths and tendrils and creeping flesh, it
  regenerates, its heart shows in its mouth when it is cut open (three times the damage), it begs as it dies. Its death
  breaks the loop (the lights die). Details, numbers and files: the hotel entry in `HANDOFF.md`.

**First reel (a reply to @Dreclo's comment), as built (2026-11-07), two clips in `work/hotel/`:** the hotel lit at night,
the lobby, each floor's wrong thing, the 8th floor's corridor that loops (the same door number twice: "wait..."),
flesh at the corners, the offer, **refuse**, the walls bleed and it grows out of them, the fight as Chainsaw Man, its
heart, the kill and the loot; or **accept**, the loop opens, walk out (and then use Forever on a mob). Real keys and
paths, every beat bigger.

## Hybrid or contracts, never both (kortev, 2026-11-06)

A player is either a **hybrid** (a devil inside you, transformed by a trigger: Chainsaw Man's cord now, later the
**Bomb** hybrid's pin (Reze's) and the **Katana** hybrid (Katana Man's)) or a **devil hunter with a contract**. A
hybrid can't make contracts (a devil offering one to a hybrid is refused), and taking a contract means giving up your
hybrid devil (as built for the Eternity Devil: it refuses a hybrid with a line and fights them). That makes the first
big choice in the mod. Contracts come from a devil's offer (the Eternity Devil offers it in its loop); killing a devil
gives loot, not its contract.

**How many people per devil:** by default a devil can have contracts with any number of players (each player takes its
offer themselves). A game rule `oneContractPerDevil` (default false) makes each devil's contract belong to one
player per world, first come, until they die or end it: for servers that want each player's powers to be their own.
The hybrids could take the same rule later.

**Devils come back:** a devil's home (the Eternity Devil's hotel and the like) gets its devil back some in-game days
after it was killed or spared (game rule `devilRespawnDays`, default 3), so it can be fought again for loot, by other
players for their own contracts, or by you to switch contracts. Each new hotel in the world has its own devil too.

## Later phases (one devil each, each its own reel; all season 1 / Reze arc)

1. **Fox Devil** (Aki's): a giant fox head bursts out and bites the target. Very recognisable.
2. **Curse Devil** (Aki's): stab a target with the nail three times and a huge devil crushes it.
3. **Ghost Devil** (Himeno's): an invisible hand that grabs and chokes.
4. **World devils as plain enemies** to fight in the form: Bat Devil, Leech Devil, and the like.
5. **Power** as a companion who fights with blood weapons.
6. Release: a Modrinth/CurseForge page, a description, screenshots; the link goes in every reel's description.
