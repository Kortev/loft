# Master plan: the Chainsaw Man mod, from here to release

Written by Fable 5.2 on 2026-11-09, at kortev's ask: one big plan for the whole mod, then Opus 5.5 agents build it.
**This is a draft until kortev says yes.** Nothing new starts from it before that, except what's already running.

How this sits with the other plans:
- `QUEST_MOD_PLAN.md` holds the design that's decided (paths, contracts, prices, Public Safety, the hotel quest).
- `SOLO_MOD_PLAN.md` holds the history of the contract ideas.
- **This file holds the order, the whole-world design that was missing, how the agents run, and what waits on kortev.**
  Where this file and an older one disagree, this one wins once kortev has said yes.

Marks used below: **[decided]** kortev said so. **[pick]** Fable's choice, waiting for kortev's yes. **[check]** a
show fact from memory or an older plan that must be checked on the wiki and Google before anyone designs from it.

---

## 1. The mod in one line, and what "finished" means

**Normal Minecraft, with the Chainsaw Man world living in it.** Devils roam, Public Safety hires you, devils offer
deals that cost you something real, and one player per world can find Pochita.

**Version 1.0 is finished when a stranger can install the jar and, with no video and no wiki:**
1. learn within ten minutes that devils exist and where to go;
2. play a full loop: take a mission, hunt a devil, get paid, rank up, get stronger;
3. choose a path: contracts, or a hybrid heart;
4. beat one full lair quest (the hotel) alone or with friends;
5. do all of it on a server with friends without anything breaking.

Everything after that (more devils, more hybrids) is an update, each one a reel.

---

## 2. Where things stand today

| Piece | State |
|---|---|
| Chainsaw Man (merge, form, saws, chain, arms, Beam, cord revive, buff, heart levels) | Built. Reels posted. Some parts not judged in game by kortev. |
| Contracts (3 slots, bonds, "Not yet", lasting prices), Forever | Built, not judged. |
| Teams (everyone goes down, pick-ups, buddies, the heart's rules) | Built, not judged. |
| The hotel, version 2 (the hunt in the walls, the squad, the beg) | Built, not judged. Its look is old. |
| The shaft fight (the floor turns 90 degrees) | Built on branch `shaft-fight`, 48 hotel tests pass. Not merged. |
| The Eternity Devil in 3D | Meshes made. An agent is putting them in the game now (branch `devil-look`). |
| Hotel textures, Public Safety look, Zombie and Bat concepts | Concepts made, waiting for kortev's yes. |
| Public Safety (outposts, missions, yen, ranks, weapons, suit) | Not started. |
| Devils in the world | Not started. |
| How a new player finds anything | **Not designed until this plan.** |
| Two real players on one server | **Never tested.** |
| A public download | Not started. |

Game tests: 229, all passing on the main branch.

**The honest picture:** a lot is built, and almost none of it has been judged by kortev in the game. The slow part
isn't building. It's kortev's time to look at things. Section 6 is built round that.

---

## 3. Solo or multiplayer? Both, from one design [pick]

**Complete alone, better with friends, and server drama as an extra after release.**

Why not multiplayer only: most people who download a mod from a short video play alone or with one friend. If the mod
needs a server to be fun, most of them leave in ten minutes.

Why not solo only: the best reels so far are fights between players, kortev has an SMP, and "who got Chainsaw Man?" is
the best server story a mod can have. Also the code is already written the server's way, so nothing is saved by
dropping it.

So one design, in three layers:

| Layer | Who it's for | What it means | When |
|---|---|---|---|
| **Solo** | everyone | Every quest, boss, rank and path can be finished by one player. This is the test for every piece. | Always, from the start |
| **Friends (2-4)** | most servers | Missions shared by a party, bosses scale with the party, buddies, picking each other up. No PvP needed. | Built with each piece, checked with two real clients at each milestone |
| **Server legend** | big SMPs | The one Chainsaw Man shown on the map, the bounty on the heart, heart theft, one contract per devil. All off by default. | After 1.0, as its own update |

**What makes solo fair: a partner [pick].** Public Safety hunters work in pairs. Alone, the office assigns you a
**partner hunter** (our own character, an NPC with a katana). They follow you on missions, fight a little, and
**pick you up when you go down**. On a server, your buddy is a real player and you get no NPC. That answers the open
question "is going down alone too harsh?": alone you're never really alone. Later, Power can be a partner you earn.

**Rules that follow from this:**
- Nothing in 1.0 may need a second player.
- Nothing in 1.0 may need Chainsaw Man [decided].
- Every piece is tested solo by the game tests, and with two real clients before its milestone closes (section 7).

---

## 4. How the mod works as a whole (the part that was missing)

### 4.1 The first hour, as the player lives it [pick]

1. **Minute 0.** A normal Minecraft world. Nothing is forced on you.
2. **The first village.** A Public Safety poster hangs on a wall: "Devil hunters wanted." Reading it gives you a
   **hunter's licence** (a card item) and marks the nearest office on it.
3. **The first night near a village.** A weak devil attacks. Villagers shout and run. Normal weapons hurt it, but it
   heals. Kill it anyway and it drops a part. Now you know: devils are real, and they heal.
4. **The office.** The captain (our own character) buys the part for yen, signs you on as 4th Class, pairs you with a
   partner, and points at the mission board.
5. **The first mission.** "A devil was seen near X." The licence shows where. You hunt it, come back, get paid, and
   buy a Public Safety katana, which makes devils **bleed** so they stop healing.
6. **End of the hour.** You know the whole loop, and the board shows a mission you can't take yet.

If you never read the poster, the first-night devil still happens, and its part says in its tooltip that Public
Safety pays for these. Two ways in, no tutorial screens.

### 4.2 The loop

**Mission → hunt → parts and yen → gear and rank → a bigger devil → a deal.** Then again, one step higher.

The licence is the one screen for all of it: your rank, your missions, your contracts and their prices, your partner,
your power and power resistance. No quest book, no skill tree [decided: no skill tree].

### 4.3 The ladder [pick, numbers to tune]

| Rank | You fight | You unlock | Minecraft gear it expects |
|---|---|---|---|
| 4th Class | Weak devils near villages, zombie groups | Katana, partner, the board | Iron |
| 3rd Class | Named common devils in their homes (caves, swamps, ruins) | Knives, the suit, summoning a weak devil | Iron to diamond |
| 2nd Class | **The first lair: the hotel** ("The missing squad") | The office lends you a contract | Diamond |
| 1st Class | Bigger lairs, one each update | Pistol, long coat, bigger devils answer you | Diamond to netherite |
| Special Division | World bosses, the hybrid quest lines | The last suit, the top missions | Netherite |

- Each rank is a jump you can feel and see [decided]: a badge, a new suit look, new missions, more power and more
  power resistance together.
- A rank costs missions done **and** one rank trial (a harder hunt the captain sets). No grinding the same mission.
- Pochita's den isn't on the ladder. It's a find [decided: one per world].

### 4.4 What's in the world, and where

| Thing | Where | How often |
|---|---|---|
| Poster | Every village | Always |
| Public Safety office | Near villages | About one per 1000 blocks [decided] |
| Common devils | Where their fear fits, at night or underground | Rarer than vanilla mobs [decided] |
| Devil nests | Caves, swamps, ruins | A few per office area, used by missions |
| Lairs (the hotel first) | Rare structures | Far apart; a mission points you to one |
| Pochita's den | One place | Once per world [decided] |

- **Old worlds:** everything appears in chunks nobody has loaded yet. A command places each thing for worlds that are
  already explored (the hotel's exists).
- **Missions pick real places.** A mission looks for a fitting spot near the office and puts its devil there. It
  never sends you 5000 blocks.

### 4.5 Fear: the world remembers [decided in outline, numbers to tune]

A devil is the fear of its name. Each devil type has a fear score per world: it goes up when that type kills players
or villagers, and down when it's hunted. High fear means more health, bigger groups, and its name in chat when it
shows up. The board posts a bounty on the most feared devil. Alone or on a server, every world ends up with its own
villain.

### 4.6 Dying, difficulty and options

- Everyone goes down instead of dying, and a partner or friend can pick you up [decided].
- Vanilla difficulty scales devil damage the usual way. Peaceful: no devils, the office still trades.
- Every server choice is a game rule with a safe default: `chainsawHearts`, `heartTheft`, `oneContractPerDevil`,
  `devilRespawnDays`, power damage against players. One command lists them all.

### 4.7 What saves, and what must never break

Once the mod is public, people's worlds matter. From the release on:
- every saved thing (rank, yen, contracts, fear, the heart, lairs) carries a version number;
- an update must load the last version's world, checked by a test that loads a saved world from the last release.

---

## 5. The milestones

Each milestone ends the same way: all tests pass, a two-client check, a review clip for kortev, a reel, a pushed tag.
Sizes are in agent runs: S is one run, M is two or three, L is four or more.

### M0. Clear the decks (now)

| # | Package | Size | Notes |
|---|---|---|---|
| 0.1 | Land the 3D devil and the shaft fight | running | Fable reviews the film against the concept, sends it, merges if kortev likes it. |
| 0.2 | **Review clips for everything not judged** | M | One 60-90 s phone clip each for: contracts and bonds, teams, the hotel v2, Forever, the buff. Each with at most three yes/no questions. See section 6. |
| 0.3 | **The show bible** | M | A fact sheet for every devil and thing in this plan: what the wiki and Google say (looks, staging, movement, rules), each with its source, spoiler-filtered, guesses marked. `chainsawman/facts/<name>.md`. No brief is written without one. |
| 0.4 | The two-client check | S | A script that starts a server and two clients with two names, runs a short scene (party up, go down, pick up, a boss hit), and films both screens. Reused at every milestone. |
| 0.5 | Split the handoff | S | `HANDOFF.md` is 104 KB and is re-read after every context reset. Cut it to a short "start here" plus one file per area, so agents read only their area and stop colliding in one file. |
| 0.6 | Lanes ready in the code | S | New packages `safety/` and `devil/`, each registering its own items, entities and sounds, so two agents don't edit the same lines. |

### M1. The hotel, finished (the first lair)

| # | Package | Size | Gate |
|---|---|---|---|
| 1.1 | Hotel textures in the game | M | kortev's yes on the texture concept (waiting) |
| 1.2 | The devil's sound: the crowd voice, the turn's groan, the pit | S | Synthesised |
| 1.3 | The quest wrapped as a board mission, with the squad's pay | S | Needs M2's board; until then the hotel stays stand-alone |
| 1.4 | The reel: "The floor turned into its mouth" | S | kortev's yes on the 3D devil |

### M2. The spine: Public Safety and the loop (the biggest piece)

| # | Package | Size | Gate |
|---|---|---|---|
| 2.1 | The licence: item, screen, saved data (rank, yen, missions) | M | A mock screen to kortev first |
| 2.2 | The poster, the first-night devil, the two ways in | S | Uses one weak devil from 3.1 |
| 2.3 | The office building and the captain | M | kortev's yes on the Public Safety look (waiting) |
| 2.4 | The board and missions: hunt, clear a nest, find the missing | L | |
| 2.5 | Yen, the shop, selling parts | S | |
| 2.6 | Ranks, the rank trial, the rank-up moment | M | |
| 2.7 | Weapons: katana, knives, pistol. Devils bleed. | M | Looks to kortev first [check: what hunters carry] |
| 2.8 | The suit and its three looks; power and power resistance | M | |
| 2.9 | The partner NPC (solo), parties (friends) | M | |

Reel: "I added Public Safety to Minecraft."

### M3. Devils in the world

| # | Package | Size | Gate |
|---|---|---|---|
| 3.1 | The Zombie Devil and its zombies | M | kortev's yes on the concept (waiting) |
| 3.2 | The Bat Devil | M | kortev's yes on the concept (waiting) |
| 3.3 | The Leech Devil | M | Fact sheet, concept, kortev's yes |
| 3.4 | The Tomato Devil (the weak first-night devil) [pick] | S | Fact sheet, concept, kortev's yes |
| 3.5 | Nests, spawn rules, parts | M | |
| 3.6 | Fear scores and the bounty | S | |
| 3.7 | Summoning: the circle, the offering, a weak devil's deal | M | [check: how summoning is shown] |

Each devil is its own reel.

### M4. The Fox, then the public release

| # | Package | Size | Gate |
|---|---|---|---|
| 4.1 | The Fox Devil's contract, lent by the office at 2nd Class | M | Fact sheet [check: the hand sign, the head, what it costs], visual first |
| 4.2 | A clean-install test: only Fabric API, a new world, the first hour played by script | S | |
| 4.3 | The saved-world test (4.7) | S | |
| 4.4 | The page: Modrinth and CurseForge, description, pictures, the link in every reel | S | kortev posts it |

**Release here, as an early version [pick].** By now the loop is whole: the office, four devils, two contracts
(Eternity, Fox), one lair, Chainsaw Man. Every reel since October has had people asking for a download. Shipping
early turns views into players, and their comments pick what's next. The older plan had the release last.

### M5 and on: updates, each one a reel run

| Update | What | Notes |
|---|---|---|
| 5 | The Ghost Devil: a lair and a contract | Price: an eye [decided in outline] [check] |
| 6 | The Curse Devil: a lair and a contract | Price: years of life [decided in outline] [check: how the nail works] |
| 7 | The Katana hybrid quest line | One per world [check] |
| 8 | The Reze arc: the Bomb hybrid quest line, and a storm-sized world boss | The biggest update [check] |
| 9 | Power as a partner you earn | [check] |
| 10 | The server legend pack: the map marker, the bounty on the heart | Section 3's third layer |

Candidates kortev can add or drop: a Future Devil contract, the Snake as a lair boss instead of a common mob
[check], a "Public Safety start" world.

---

## 6. kortev's time is the limit, so the plan is built round it

Opus agents can build three things at once. kortev can look at maybe one a day, on a phone. So:

1. **A review clip, not a pile.** Every finished piece becomes one short portrait clip with at most three questions,
   each answerable with a tap. Fable cuts it down before sending. No walls of text.
2. **At most three things waiting on kortev at a time.** If three are waiting, agents work on things that need no
   judging (tests, the two-client check, fact sheets, tools), not on new looks.
3. **Looks are judged before they're built, always** [decided]: fact sheet, then concept, then kortev's yes, then the
   build.
4. **Mechanics get a default.** For rule and number calls, Fable picks, builds it, and says so in the clip. kortev
   overrules any time. Looks never get a default.
5. **One list.** Section 8 is the only list of what waits on kortev. It's kept current.

---

## 7. How the agents run

**Who does what**
- **Fable 5.2:** this plan, every brief, judging looks against the show, reviewing film and diffs, merging, talking
  to kortev.
- **Opus 5.5 agents:** building, concept art, fact sheets, filming, tests. One brief file each, in `work/briefs/`.

**One package, start to end**
1. Fact sheet exists (0.3). If not, that's step one.
2. Fable writes the brief: the goal, the checked facts with sources, the files it may touch, what done looks like,
   what to film.
3. If it has a look: the agent makes the concept, Fable reviews it, kortev says yes.
4. The agent builds in its own worktree and branch, commits piece by piece, files added by name.
5. `./gradlew runGametest` all passing, with new tests for the new thing.
6. The agent films it in portrait and makes a small contact sheet.
7. Fable reviews the sheet and the diff. A fix round if needed.
8. Fable merges to `claude/chainsaw-man`, pushes, and sends kortev the clip with a numbered list of what to test.

**Lanes, so agents never touch the same files**

| Lane | Owns | Runs alongside |
|---|---|---|
| Hotel | `hotel/`, the Eternity assets | anything |
| Public Safety | `safety/` (new) | anything but itself: one agent at a time |
| Devils | `devil/` (new), one sub-folder per devil | two devils at once is fine |
| Looks | `tools/`, `work/` (no Java) | anything |
| Checks | tests, the two-client script, the docs | anything |

At most three building agents at once, one per lane. Shared files (the mod's main class, the language file) are
changed only at merge time, by Fable.

**Standing rules for every agent** (in every brief)
- No spoilers past the Reze arc, in code, comments, commits and reports.
- Never touch `src/gametest/java/io/github/kortev/shootingstar/test/ShowcaseSelfTest.java`.
- Synthesised sounds only.
- Check show facts on the wiki and Google; mark guesses.
- Keep image reads small: contact sheets and crops.
- Report short: what's in, the test result, the film paths, what isn't verified, what's weakest.

**Keeping usage down**
- Opus agents cost more than Sonnet did. Briefs stay tight, and a package that misses twice comes back to Fable
  instead of a third blind try.
- Rote jobs (re-filming, contact sheets) can stay on Sonnet if kortev wants to save usage [pick: yes].

---

## 8. Waiting on kortev

New, from this plan:
1. **Solo, friends and server as three layers** (section 3). Fable's pick: yes.
2. **A partner NPC when you play alone** (section 3). Pick: yes.
3. **The first hour** (4.1): the poster, the first-night devil, the licence. Pick: yes.
4. **The ladder** (4.3). Pick: yes, numbers tuned later.
5. **Release early, after the Fox** (M4). Pick: yes.
6. **The Tomato Devil as the weak first devil.** Pick: yes, if its fact sheet fits.
7. **Sonnet for rote filming.** Pick: yes.

Still open from before:
8. The hotel textures: yes or changes?
9. The Public Safety look: yes or changes?
10. The Zombie and Bat Devil concepts: yes or changes?
11. May you move during "Not yet"?
12. Reel sound: an original synthesised track, or game sound only?

(The old question "is going down alone too harsh?" is answered by the partner, if 2 is a yes.)

---

## 9. Reels

- One reel per package that has something to show, shot from the real game with real keys.
- 12-16 s, each beat bigger than the last [decided]. Two versions when there's music.
- Order for the next ones: Revive (Mon Nov 10) → the hotel's shaft → "Would you take this deal?" → Public Safety →
  one per devil → the Fox → "It's out. Link below."
- From the release on, every description carries the download link.

---

## 10. What could go wrong

| Risk | What we do |
|---|---|
| Looks get rebuilt many times (Beam took 8) | Fact sheet and concept before any build; Fable judges before kortev sees it |
| Public Safety is big and dull to watch being built | It's cut into nine packages, each showing something; the licence screen comes first |
| It works in tests and breaks with two players | The two-client check at every milestone, from M0 |
| Three agents collide | Lanes; shared files only at merge |
| The review pile grows | The three-waiting limit in section 6 |
| The 3D devil is too heavy for weak PCs | Frame cost is measured in 0.1; a simpler mesh is the fallback |
| A wiki page spoils later story | Agents filter; reports never carry anything past the Reze arc |
| An update breaks people's worlds | The saved-world test from the first release |

---

## 11. The first wave, the moment kortev says go

Three agents, three lanes, none needing a look decision:
1. **Checks:** the two-client check (0.4), then splitting the handoff (0.5).
2. **Looks:** the show bible (0.3), starting with the Fox, the Leech, the Tomato and what hunters carry.
3. **Public Safety:** the licence's mock screen (2.1), concept only, for kortev's yes.

And Fable: review and land the 3D devil (0.1), then cut the review clips (0.2).
