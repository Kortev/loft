# Master plan, part: 5_milestones

Section 5: the milestones and their packages. The index is `../MASTER_PLAN.md`.
Marks: **[decided]** kortev said so. **[pick]** Fable's choice, waiting for kortev's yes. **[check]** a show fact to
check on the wiki and Google first.

---

## 5. The milestones

Each milestone ends the same way: all tests pass, a server check, a review clip for kortev, a reel, a pushed tag.
Sizes are in agent runs: S is one run, M is two or three, L is four or more.

### M0. Clear the decks (now)

| # | Package | Size | Notes |
|---|---|---|---|
| 0.1 | Land the 3D devil and the shaft fight | round 3 and 0.1b the arena merged 2026-11-09 (261 tests); parked for a big pass (`packages/0.1c_devil_big_pass.md`) | The 3D look and the shaft fight are merged (2026-11-09). kortev's notes on the film: the devil must fill the corridor's end (the hotel is its body), the grab and the swallow look weak, and the film must show a played fight. Brief: `packages/0.1_devil_round3.md`. Then `packages/0.1b_devil_arena.md`: the shaft's bottom as a boss arena (attack and dodge). |
| 0.2 | **Review clips for everything not judged** | M | One 60-90 s phone clip each for: contracts and bonds, teams, the hotel v2, Forever, the buff. Each with at most three yes/no questions. See section 6. |
| 0.3 | **The show bible** | M | A fact sheet for every devil and thing in this plan: what the wiki and Google say (looks, staging, movement, rules), each with its source, spoiler-filtered, guesses marked. `chainsawman/bible/<name>.md`. No brief is written without one. First four done 2026-11-09: the Fox, the Leech, the Tomato, hunters' gear. |
| 0.4 | The server check | S | A script that starts a real server and two clients with two names, runs a short scene (party up, go down, pick up, a boss hit, **log out and back in, one client leaving mid-fight, a server restart**), and films both screens. Reused at every milestone. |
| 0.5 | Cut what's loaded after a context reset | done | The hook loaded 104 KB of `HANDOFF.md` after every reset. It now loads `STATUS.md`, one page. `HANDOFF.md` stays as a record to search, never to read whole. |
| 0.6 | Lanes ready in the code | S | New packages `safety/` and `devil/`, each registering its own items, entities and sounds, so two agents don't edit the same lines. |
| 0.7 | **Server-proof what's built** (0.7a: everything outside the hotel, merged 2026-11-09; 0.7b: the hotel's share, merged 2026-11-09 (250 tests), `packages/0.7b_hotel_share.md`) | M | Round 2's finds in the code, each with a game test: Pochita tied to his tamer, hearts counted at the merge, a lost Pochita goes back to a den; logging out while down; totems; boss loot per fighter; `oneContractPerDevil` per world; the owner kept by player id, not name; `legendRoar` off by default. |

### M1. The hotel, finished (the first lair)

| # | Package | Size | Gate |
|---|---|---|---|
| 1.1 | Hotel textures in the game | M | kortev's yes on the texture concept (waiting) |
| 1.2 | The devil's sound: the crowd voice, the turn's groan, the pit | S | Synthesised |
| 1.3 | The quest wrapped as a board mission, with the squad's pay | S | Needs M2's board; until then the hotel stays stand-alone |
| 1.4 | The reel: "The floor turned into its mouth" | S | kortev's yes on the 3D devil |
| 1.5 | More hotels, and measured | S | More tries, more biomes, a foundation in place of throwing the try out. A test measures the distance to the nearest hotel from 50 random places; the aim is one within 1000 blocks of most of them. Until the notice board exists (M2), `/locate` finds it. |
| 1.6 | The hotel can't be wrecked or held | M | Protected blocks, rebuilt when the devil returns, no teleporting while sealed, a seal time limit, the fight saved across a restart, awake at any hour for a mission |
| 1.7 | Dying inside throws you out the door with your things; the hotel resets when nobody is left | S | |

### M2. The spine: Public Safety and the loop (the biggest piece)

| # | Package | Size | Gate |
|---|---|---|---|
| 2.1 | The licence: item, screen, saved data (rank, yen, missions) | M | A mock screen to kortev first |
| 2.2 | The notice board (poster and phone), the first attack, the two ways in | M | Uses the weak devil from 3.4. **2.2a merged 2026-11-11 (291 tests)**: not a scripted first attack but the Tomato's staged arrival as its chance night spawn (`packages/2.2a_first_night.md`). 2.1a the board, licence and yen merged 2026-11-11 (`packages/2.1a_notice_board.md`). |
| 2.3 | The office as a village building (added to the village's own building lists; [to confirm in a test: never two in one village]), the captain, and the office built at the edge of villages that already exist | M | kortev's yes on the Public Safety look (waiting) |
| 2.4 | The board and missions: hunt, clear a nest, find the missing | L | |
| 2.5 | Yen, the shop, selling parts | S | |
| 2.6 | Ranks, the rank trial, the rank-up moment | M | |
| 2.7 | Weapons: katana, knives, pistol. Devils bleed. | M | Looks to kortev first [check: what hunters carry] |
| 2.8 | The suit and its three looks; power and power resistance | M | |
| 2.9 | The partner, saved on the player (solo); parties (friends) | M | |
| 2.10 | Power: devil tiers, one power number for hunters, hearts and fiends; the heart retuned to eat by tier | M | `4f_scaling.md` |

Reel: "I added Public Safety to Minecraft."

### M3. Devils in the world

| # | Package | Size | Gate |
|---|---|---|---|
| 3.1 | The Zombie Devil and its zombies | M | kortev's yes on the concept (waiting) |
| 3.2 | The Bat Devil | M | kortev's yes on the concept (waiting) |
| 3.3 | The Leech Devil | round 1 merged 2026-11-11 (308 tests) | `packages/3.3_leech_devil.md`: model, tongue, latch, trample; summon only. Round 2 merged 2026-11-11 (`packages/3.3b_leech_round2.md`): wrinkled body, a reach fighter whose limbs you cut. |
| 3.4 | The Tomato Devil (the weak first-night devil) [pick] | merged 2026-11-10 (274 tests) | Brief `packages/3.4_tomato_devil.md`; the `devil/` lane exists (0.6 done). Round 2 merged 2026-11-10 (282 tests): `packages/3.4b_tomato_round2.md`. The horror short merged 2026-11-10 (`packages/3.4c_tomato_cinematic.md`); a sound pass and a 7.7 s teaser followed the same day. kortev has all three videos. 3.4d merged (283 tests): seeds can be cut up in three hits. |
| 3.5 | Nests, spawn rules, parts | M | |
| 3.6 | Mission kinds, and one trick per devil | S | Fear and the bounty moved to the server legend update |
| 3.7 | Summoning: the circle, the offering, a weak devil's deal | M | [check: how summoning is shown] |
| 3.8 | Small deals: a beaten devil begs | M | kortev said yes (2026-11-09); each deal's visual shown first |
| 3.9 | Devils grow: a devil left alive gets stronger each night, faster when feared | merged 2026-11-11 (320 tests); look round 2 asked | kortev asked for it 2026-11-11 (`4d_dying_and_options.md` 4.5). Next after 2.2a; the Tomato first. Check the show's rule on the wiki before the brief. |

Each devil is its own reel.

### M4. The Fox, then the public release

| # | Package | Size | Gate |
|---|---|---|---|
| 4.1 | The Fox Devil's contract, lent by the office at 2nd Class (the ranks are ours; the show names none) | M | Facts checked in `chainsawman/bible/fox_devil.md`: the hand sign and "Kon", the giant head bites and swallows, you pay in bits of your own body, it can't reach a sealed-off place, and it drops a contractor it sours on. The show has no rule about the dark, so it works in caves. To design: a price that lasts; it doesn't come inside a sealed lair [pick, as in the show]. Visual first. |
| 4.2 | A clean-install test: only Fabric API, a new world, the first hour played by script | S | |
| 4.3 | The saved-world test (4.7) | S | |
| 4.4 | The page: Modrinth and CurseForge, description, pictures, the link in every reel | S | kortev posts it |

**Release here, as an early version [pick].** By now the loop is whole: the office, four devils, two contracts
(Eternity, Fox), one lair, Chainsaw Man. Shipping early gets real players sooner, and what they say picks
what's next. The older plan had the release last.

### M5 and on: updates, each one a reel run

| Update | What | Notes |
|---|---|---|
| 4.5 | Play as a fiend: the Blood Fiend, then the Shark Fiend | kortev (2026-11-10): people will want to play as Power and Beam. `4e_what_you_play.md`. L each [check] |
| 5 | The Ghost Devil: a lair and a contract | Price: an eye [decided in outline] [check] |
| 6 | The Curse Devil: a lair and a contract | Price: years of life [decided in outline] [check: how the nail works] |
| 7 | The Katana hybrid quest line | One per world [check] |
| 8 | The Reze arc: the Bomb hybrid quest line, and a storm-sized world boss | The biggest update [check] |
| 9 | Power as a partner you earn | [check] |
| 10 | ~~The server legend pack~~ | Dropped 2026-11-11: the mod is for one player and a few friends (section 3, Scope) |

Candidates kortev can add or drop: a Future Devil contract, the Snake as a lair boss instead of a common mob
[check], a "Public Safety start" world.
