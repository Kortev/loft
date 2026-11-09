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
| Pochita's den | One den near the world's spawn, with no hint. To be changed to many dens (4.4). |
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
**pick you up when you go down**. With a friend online and near you, your buddy is that friend. That answers the open
question "is going down alone too harsh?": alone you're never really alone. Later, Power can be a partner you earn.

The partner must never be a chore, and must not make you unkillable (round 2):
- **The partner is saved on the player, not as a mob standing in the world.** Their body appears next to you when a
  mission or a fight starts, and goes when you log out, change dimension, fly or ride off. A follower saved in a
  chunk gets stuck in caves and doorways, is left behind in chunks nobody has loaded, and can come back doubled.
- They can't die for good: they go down and are back the next day.
- **One pick-up per fight.** After lifting you they're winded for a few minutes, so going down still matters.
- **You get the partner whenever no real buddy is online and near you,** checked when a mission starts. "On a
  server you get no NPC" fails when your friend is asleep in another time zone, or leaves halfway.
- They never lift you in a fight against another player.

**Rules that follow from this:**
- Nothing in 1.0 may need a second player.
- Nothing in 1.0 may need Chainsaw Man [decided].
- Every piece is tested solo by the game tests, and on a real server with two clients (logging out and back in, a
  restart, one leaving mid-fight) before its milestone closes (section 7). Everything that is one per world breaks on
  a server first.

---

## 4. How the mod works as a whole (the part that was missing)

### 4.1 The first hour, as the player lives it [pick]

1. **Minute 0.** A normal Minecraft world. Nothing is forced on you.
2. **The first village.** A Public Safety notice board stands by the bell: a poster ("Devil hunters wanted") and a
   phone. The mod puts it up the first time a player walks into a village, so it's there in worlds that are already
   explored. Reading the poster gives you a **hunter's licence** and marks the nearest office on it.
3. **The first attack.** At your second dusk, **wherever you are**, a weak devil comes for you. It follows the
   player, so it can't be missed by sleeping, or by living far from a village. Its arrival is staged big (it bursts
   out of the ground) though it's weak, so the first minute is worth filming. Near a village, villagers shout and
   run, iron golems leave it to you, and it doesn't kill villagers. A stone or iron sword can kill it, though it
   heals a little. It drops a part. Now you know: devils are real, and they heal.
4. **The office.** The captain (our own character) buys the part for yen, signs you on as 4th Class, pairs you with a
   partner, and points at the mission board.
5. **The first mission.** "A devil was seen near X." The licence shows where. You hunt it, come back, get paid, and
   buy a Public Safety katana, which makes devils **bleed** so they stop healing.
6. **The first deal [pick].** On one of your first missions, a named devil you've beaten begs for its life and
   offers a small deal: one move, and one small price that lasts. People install this for a devil's power. They get
   to say yes or no to one in the first hour or two, not after two ranks. (A devil only deals when you have
   something over it, and a beaten devil is exactly that.)
7. **End of the hour.** You know the whole loop, and the board shows a mission you can't take yet.

If you never read the poster, the first-night devil still happens, and its part says in its tooltip that Public
Safety pays for these. Two ways in, no tutorial screens.

### 4.2 The loop

**Mission → hunt → parts and yen → gear and rank → a bigger devil → a deal.** Then again, one step higher.

The licence is the one screen for all of it: your rank, your yen, your missions, your contracts and their prices,
your partner, your power and power resistance. No quest book, no skill tree [decided: no skill tree].

- **Everything is saved on the player, not in the card.** The card only opens the screen. Lose it and any office
  gives you a new one for free, and a key opens the screen too.
- **Yen is a number on the licence, not an item [pick].** Items are lost on death, clutter chests and get copied by
  bugs. Payday is still a moment you see and hear (an envelope, the count running up). You pay another player from
  the licence, and it takes a day to arrive, so spare accounts can't feed a main one. The first part you sell pays
  its bonus once per player.
- **The phone [pick].** The notice board in every village has a phone. Missions come in there and pay goes out
  there. Only rank-ups, the trial and the shop need the office. Without it, every mission is two long walks.
- **Missions belong to a party, not to the board.** Every hunter gets their own, their places don't overlap, and
  the kill counts for the party that holds the mission, not for whoever carries the proof. So on a busy server the
  first player can't take them all, and a stranger can't cash in your devil.
- **Missions come in kinds:** a pair, a nest with a leader, a time limit, a devil that runs. And each devil has one
  trick of its own. Otherwise four devils times three missions is all there is by hour 10.
- **The guide is Minecraft's own advancements tab:** a Chainsaw Man page that shows the next step (read the poster,
  sell a part, take a mission, reach 3rd Class, ...). No new screen to learn.
- **A mission's devil appears when you get there,** not when you take the mission, so it's never lost in chunks
  nobody has loaded.

### 4.3 The ladder [pick, numbers to tune]

| Rank | You fight | You unlock | Minecraft gear it expects |
|---|---|---|---|
| 4th Class | Weak devils near villages, zombie groups | Katana, partner, the board, small deals | Iron |
| 3rd Class | Named common devils in their homes (caves, swamps, ruins) | Knives, the suit, summoning a weak devil | Iron to diamond |
| 2nd Class | **The first lair: the hotel** ("The missing squad") | The office lends you a contract | Diamond |
| 1st Class | Bigger lairs, one each update | Pistol, long coat, bigger devils answer you | Diamond to netherite |
| Special Division (after 1.0) | World bosses, the hybrid quest lines | The last suit, the top missions | Netherite |

- Each rank is a jump you can feel and see [decided]: a badge, a new suit look, new missions, more power and more
  power resistance together.
- A rank costs missions done **and** one rank trial (a harder hunt the captain sets). No grinding the same mission.
- Pochita isn't on the ladder. He's a find [decided: one Chainsaw Man per world].
- **1.0 stops at 1st Class.** A rank with nothing to fight is a let-down, so Special Division opens when its bosses
  exist. The top of 1.0 is the hotel again, harder, and the hardest mission kinds.
- **Late yen has uses:** weapon upgrades made from devil parts, better offerings for summoning, a phone for your own
  base.
- **The suit goes over your armour, not instead of it [pick].** Nobody takes off enchanted netherite for a suit. So
  the suit is a look and a power-resistance bonus in its own slot on the licence, and your Minecraft armour keeps
  doing its job against ordinary damage. It's saved with the licence and drawn by the mod over your armour, so it
  needs no second mod for the slot, other players see it, and nothing drops when you die.
- **The katana is a real sword [pick]:** it takes sword enchantments, and it upgrades with rank so it's never worse
  than the vanilla sword you'd have by then. Otherwise players keep their Sharpness V sword and skip it. It's worth
  using from the first day too: a devil killed with it leaves a cleaner part that sells for more.
- **Weak devils heal slowly,** so a new player with a stone sword can still kill one. Bleeding only becomes a must
  from 3rd Class devils up.

### 4.4 What's in the world, and where

| Thing | Where | How often |
|---|---|---|
| Notice board (poster and phone) | Every village | Put up when a player first walks in, so old worlds get it too |
| Public Safety office | Beside a village | About one village in three [to tune], built when a player first comes near. Minecraft can't generate one structure "near" another, so it's placed while you play. |
| Common devils | Where their fear fits, at night or underground | Rarer than vanilla mobs [decided] |
| Devil nests | Caves, swamps, ruins. Devils live here by day too. | A few per office area, used by missions |
| Lairs (the hotel first) | Rare structures | Today the hotel gets one *try* per 1280 blocks, and a try is thrown out unless it lands in one of 7 biomes on flat dry ground (checked in the code). So the nearest is often 2000-4000 blocks off. M1 fixes and measures this. |
| Pochita's dens | **Many, in forests all over the world** (kortev, 2026-11-09) | About one per 800 blocks [number to tune] |

**Pochita: many dens, one Pochita** (kortev, 2026-11-09). Today the code makes a single den, 250-450 blocks from the
world's spawn, and nothing tells you it's there. A player who walks the other way never finds it, and a heart that
comes back lands 2000 blocks off with no hint at all. So:
- Dens generate all over the world. **While a heart is free, Pochita is in whichever den you find.** The dens
  themselves generate empty, and he appears when a player comes within about 40 blocks. (A Pochita made with the
  world would sit in chunks nobody has loaded and turn up after he'd been found.)
- Once someone merges, every den is **empty**: the nest is there, he isn't, and you can see someone got to him
  first. A Pochita that someone else has tamed but not merged with goes back to being wild.
- `chainsawHearts` (default 1) is how many players can merge, **counted at the merge**, not at the den.
- **A carried Pochita belongs to whoever tamed him.** Today anyone holding him can merge, so one left in a chest can
  be stolen, and one dropped on the ground is gone for good after five minutes (checked in the code). He's tied to
  his tamer, and if he's lost he goes back to a den.
- **How you merge has to be shown [pick].** Today you become Chainsaw Man only by dying with him carried or near,
  and nothing says so. A careful player never dies and never finds out. So once he trusts you, at the next dusk a
  pack of zombies comes for him. Going down beside him is the merge. The game makes the moment happen.
- If the heart comes back to the world (given up, or stolen and lost), **one** den far from every player gets him,
  and the office tells anyone who asks. Not every den at once: whoever wrote down a den's place would win in
  minutes.
- **A holder who quits doesn't keep the heart forever [pick].** On a server, the heart returns after about 14 days
  of the server running without them. Today that's switched off, so the first player to leave empties every den for
  good. It never happens in a single-player world.
- While a heart is free, the office has a rumour ("something whimpers in the woods to the east") that points at the
  nearest den. So he can be found by luck or by asking.
- **Chainsaw Man already has a running cost** (checked in the code): the form runs on blood, which you get by
  cutting. So the heart isn't free power, and the friend who didn't get it has the small deals (4.1).

**Night isn't required.** Players sleep through nights, so a mod whose devils only come at night is a mod nobody
sees. Devils live in nests by day, missions work at any hour, and devils don't burn in the sun. The hotel breaks
this today: it only wakes at night (checked in the code), and a server where one sleeper skips the night resets a
party on its way up. For a mission it wakes at any hour [pick]; inside, it's always night.

**The Overworld only, for 1.0.** No devils in the Nether or the End, and no mission ever points there.

- **Old worlds:** notice boards, offices and the first attack are placed while you play, so they work in a world
  that's already explored. Hotels and dens appear in chunks nobody has loaded yet, and a mission or the rumour sends
  you to the nearest one. A command is no answer for a stranger.
- **Missions pick real places.** A mission looks for a fitting spot near the office and puts its devil there. It
  never sends you 5000 blocks. A lair mission uses the nearest lair it can find, and isn't offered when none is in
  reach.
- **Lairs can't be wrecked.** Today only mining by hand is stopped, and only while the hotel is sealed. TNT, fire
  (it's wool and dark oak), lava, pistons, a portal built inside and chorus fruit all get through, and by day anyone
  can dig into the looping floor (checked in the code). A lair's blocks don't burn, blow up or move. It's rebuilt
  from its plan each time its devil comes back. Nothing teleports in or out while it's sealed.

### 4.5 Fear: the world remembers [decided in outline, numbers to tune]

**Moved out of 1.0 (round 2).** Alone you'd hardly notice it, it needs careful tuning, and missions get their
variety from mission kinds (4.2). It comes with the server legend update, and it's counted per office area, not per
world, so one group's deaths don't make devils stronger for a newcomer far away.

A devil is the fear of its name. Each devil type has a fear score per world: it goes up when that type kills players
or villagers, and down when it's hunted. High fear means more health, bigger groups, and its name in chat when it
shows up. The board posts a bounty on the most feared devil. Alone or on a server, every world ends up with its own
villain.

**It must not snowball.** A player who keeps dying to one devil would make it stronger, and then die more. So fear
has a ceiling (about +50%), fades by itself over a few days, and the bounty grows with it: the feared devil is worth
more, not only harder.

### 4.6 Dying, difficulty and options

- Everyone goes down instead of dying, and a partner or friend can pick you up [decided].
- **A totem of undying is used first.** Today going down happens before Minecraft looks for a totem, so it never
  pops and you bleed out holding it (checked in the code; the exact order is still to confirm in a test).
- **Logging out while down doesn't save you.** Today your body just goes, and you come back when it's safe. Your
  body stays for the rest of the timer.
- **Dying in the hotel [pick]: the devil keeps you.** Today you're put outside, your things lie on the floor for
  five minutes, and alone the fight simply ends. Better, and true to the place: you wake against a corridor wall,
  starving, one more trapped hunter. A friend feeds you (or your partner does, once) and you're back in the fight.
  If everyone is down, the devil spits you all out, and the hotel holds each player's things at the desk for their
  owner only.
- **A contract can be ended at the office, at a cost that hurts [pick]:** the bond lost, a fee, one last price.
  Today the only way out is taking a fourth contract, so "never sleep again" is for life, and most players either
  never sign or regret it and quit.
- **A price has to cost the same alone and on a server.** On a server the others skip the night for an Eternity
  holder, and Minecraft's phantoms need open sky and a setting that's often off. So prices count uses, not time, and
  bring their own trouble (their own phantoms).
- Vanilla difficulty scales devil damage the usual way. Peaceful: no devils, lairs sleep, prices that use hunger
  pause, the office still trades.
- **A boss pays everyone who fought it.** Its loot goes into each fighter's inventory (today it's one pile on the
  floor), pay goes to everyone who hurt it, alive or not, and the boss grows with the party, so spare accounts
  standing idle don't multiply the pay.
- **A sealed lair can't be held hostage.** The seal has a time limit and idle players are put out. Logging out
  inside puts you back inside. A sealed fight is saved across a server restart.
- **Against other players, rank bonuses are off by default,** so a veteran can't flatten a newcomer.
- Every server choice is a game rule with a safe default: `chainsawHearts`, `heartTheft`, `oneContractPerDevil`
  (per world; it's per hotel today), `devilRespawnDays`, `legendRoar` (off; it's on in the code today), power damage
  against players. One command lists them all.
- The captain can't be killed, and the office can't be broken.

### 4.7 What saves, and what must never break

Once the mod is public, people's worlds matter. From the release on:
- every saved thing (rank, yen, contracts, fear, the heart, lairs) carries a version number;
- an update must load the last version's world, checked by a test that loads a saved world from the last release.

---

## 5. The milestones

Each milestone ends the same way: all tests pass, a server check, a review clip for kortev, a reel, a pushed tag.
Sizes are in agent runs: S is one run, M is two or three, L is four or more.

### M0. Clear the decks (now)

| # | Package | Size | Notes |
|---|---|---|---|
| 0.1 | Land the 3D devil and the shaft fight | running | Fable reviews the film against the concept, sends it, merges if kortev likes it. |
| 0.2 | **Review clips for everything not judged** | M | One 60-90 s phone clip each for: contracts and bonds, teams, the hotel v2, Forever, the buff. Each with at most three yes/no questions. See section 6. |
| 0.3 | **The show bible** | M | A fact sheet for every devil and thing in this plan: what the wiki and Google say (looks, staging, movement, rules), each with its source, spoiler-filtered, guesses marked. `chainsawman/facts/<name>.md`. No brief is written without one. |
| 0.4 | The server check | S | A script that starts a real server and two clients with two names, runs a short scene (party up, go down, pick up, a boss hit, **log out and back in, one client leaving mid-fight, a server restart**), and films both screens. Reused at every milestone. |
| 0.5 | Split the handoff | S | `HANDOFF.md` is 104 KB and is re-read after every context reset. Cut it to a short "start here" plus one file per area, so agents read only their area and stop colliding in one file. |
| 0.6 | Lanes ready in the code | S | New packages `safety/` and `devil/`, each registering its own items, entities and sounds, so two agents don't edit the same lines. |
| 0.7 | **Server-proof what's built** | M | Round 2's finds in the code, each with a game test: Pochita tied to his tamer, hearts counted at the merge, a lost Pochita goes back to a den; logging out while down; totems; boss loot per fighter; `oneContractPerDevil` per world; the owner kept by player id, not name; `legendRoar` off by default. |

### M1. The hotel, finished (the first lair)

| # | Package | Size | Gate |
|---|---|---|---|
| 1.1 | Hotel textures in the game | M | kortev's yes on the texture concept (waiting) |
| 1.2 | The devil's sound: the crowd voice, the turn's groan, the pit | S | Synthesised |
| 1.3 | The quest wrapped as a board mission, with the squad's pay | S | Needs M2's board; until then the hotel stays stand-alone |
| 1.4 | The reel: "The floor turned into its mouth" | S | kortev's yes on the 3D devil |
| 1.5 | More hotels, and measured | S | More tries, more biomes, a foundation in place of throwing the try out. A test measures the distance to the nearest hotel from 50 random places. |
| 1.6 | The hotel can't be wrecked or held | M | Protected blocks, rebuilt when the devil returns, no teleporting while sealed, a seal time limit, the fight saved across a restart, awake at any hour for a mission |
| 1.7 | The devil keeps you (dying inside) | M | kortev's yes (section 8) |

### M2. The spine: Public Safety and the loop (the biggest piece)

| # | Package | Size | Gate |
|---|---|---|---|
| 2.1 | The licence: item, screen, saved data (rank, yen, missions) | M | A mock screen to kortev first |
| 2.2 | The notice board (poster and phone), the first attack, the two ways in | M | Uses the weak devil from 3.4 |
| 2.3 | The office building and the captain, placed beside a village while you play | M | kortev's yes on the Public Safety look (waiting) |
| 2.4 | The board and missions: hunt, clear a nest, find the missing | L | |
| 2.5 | Yen, the shop, selling parts | S | |
| 2.6 | Ranks, the rank trial, the rank-up moment | M | |
| 2.7 | Weapons: katana, knives, pistol. Devils bleed. | M | Looks to kortev first [check: what hunters carry] |
| 2.8 | The suit and its three looks; power and power resistance | M | |
| 2.9 | The partner, saved on the player (solo); parties (friends) | M | |

Reel: "I added Public Safety to Minecraft."

### M3. Devils in the world

| # | Package | Size | Gate |
|---|---|---|---|
| 3.1 | The Zombie Devil and its zombies | M | kortev's yes on the concept (waiting) |
| 3.2 | The Bat Devil | M | kortev's yes on the concept (waiting) |
| 3.3 | The Leech Devil | M | Fact sheet, concept, kortev's yes |
| 3.4 | The Tomato Devil (the weak first-night devil) [pick] | S | Fact sheet, concept, kortev's yes |
| 3.5 | Nests, spawn rules, parts | M | |
| 3.6 | Mission kinds, and one trick per devil | S | Fear and the bounty moved to the server legend update |
| 3.7 | Summoning: the circle, the offering, a weak devil's deal | M | [check: how summoning is shown] |
| 3.8 | Small deals: a beaten devil begs | M | kortev's yes (section 8); each deal's visual shown first |

Each devil is its own reel.

### M4. The Fox, then the public release

| # | Package | Size | Gate |
|---|---|---|---|
| 4.1 | The Fox Devil's contract, lent by the office at 2nd Class | M | Fact sheet [check: the hand sign, the head, what it costs, whether it really refuses the dark], visual first. It needs a price that lasts, and it can't be useless in caves, where devils are. |
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
| 10 | The server legend pack: the map marker, the bounty on the heart, fear per office area | Section 3's third layer |

Candidates kortev can add or drop: a Future Devil contract, the Snake as a lair boss instead of a common mob
[check], a "Public Safety start" world.

---

## 6. kortev's time is the limit, so the plan is built round it

Opus agents can build three things at once. kortev can look at maybe one a day, on a phone. So:

1. **A review clip, not a pile.** Every finished piece becomes one short portrait clip with at most three questions,
   each answerable with a tap. Fable cuts it down before sending. No walls of text.
2. **At most three things waiting on kortev at a time.** If three are waiting, agents work on things that need no
   judging (tests, the server check, fact sheets, tools), not on new looks.
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

**The four checks** (kortev, 2026-11-09: double, triple, quadruple check that it makes sense). Every design, before
its brief is written, has to pass all four, written down in the brief:
1. **The show:** is it true to the wiki and Google? (the fact sheet)
2. **Minecraft:** does it work with how Minecraft really behaves? Beds skip nights, dropped items vanish in five
   minutes, chunks unload, worlds are endless, players already have enchanted netherite, followers get stuck.
3. **Fun:** walk through it as a player, alone, in the first ten minutes and in the tenth hour. Where is it a chore,
   a dead end, unfair, or something you'd never find?
4. **Other people:** two friends, and a big server. What if someone is offline, late, greedy or mean?
For the big pieces (this plan, each milestone's design) the last three are also run by separate checker agents who
haven't seen Fable's reasoning, so it isn't one mind agreeing with itself.

**One package, start to end**
1. Fact sheet exists (0.3). If not, that's step one.
2. Fable writes the brief: the goal, the checked facts with sources, the four checks, the files it may touch, what
   done looks like, what to film.
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
| Checks | tests, the server-check script, the docs | anything |

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
8. **Pochita's dens** (4.4): many dens, empty once he's found, a rumour at the office. Your idea; the numbers are mine.
9. **The suit over your armour, yen as a number, the katana as a real sword** (4.2, 4.3). Pick: yes to all three.

New, from the three checkers (round 2, section 12). The first three matter most:
10. **Small deals from beaten devils, from the first hour** (4.1). Pick: yes. It's the biggest change: each common
    devil needs one move with a look worth filming.
11. **Dying in the hotel: the devil keeps you,** and a friend feeds you back in (4.6). Pick: yes.
12. **Ending a contract at the office, at a cost** (4.6). Pick: yes.
13. **The merge is staged:** zombies come for Pochita once he trusts you (4.4). Pick: yes.
14. **A phone on every village's notice board** for missions and pay (4.2). Pick: yes.
15. **Out of 1.0:** fear scores, and the Special Division rank (4.3, 4.5). Pick: yes.
16. **On a server, a heart whose holder is gone 14 days comes back** (4.4). Pick: yes.

Still open from before:
17. The hotel textures: yes or changes?
18. The Public Safety look: yes or changes?
19. The Zombie and Bat Devil concepts: yes or changes?
20. May you move during "Not yet"?
21. Reel sound: an original synthesised track, or game sound only?

(The old question "is going down alone too harsh?" is answered by the partner, if 2 is a yes.)

Everything else round 2 found is a fix with no choice in it, so it's simply in the plan.

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
| It works in tests and breaks with two players | The server check at every milestone, from M0; package 0.7 fixes what round 2 found |
| The plan reads well and plays badly | The four checks on every brief; three checker agents on every milestone's design |
| Three agents collide | Lanes; shared files only at merge |
| The review pile grows | The three-waiting limit in section 6 |
| The 3D devil is too heavy for weak PCs | Frame cost is measured in 0.1; a simpler mesh is the fallback |
| A wiki page spoils later story | Agents filter; reports never carry anything past the Reze arc |
| An update breaks people's worlds | The saved-world test from the first release |

---

## 11. The first wave, the moment kortev says go

Three agents, three lanes, none needing a look decision:
1. **Checks:** the server check (0.4), then server-proofing what's built (0.7), then splitting the handoff (0.5).
2. **Looks:** the show bible (0.3), starting with the Fox, the Leech, the Tomato and what hunters carry.
3. **Public Safety:** the licence's mock screen (2.1), concept only, for kortev's yes.

And Fable: review and land the 3D devil (0.1), then cut the review clips (0.2).

---

## 12. The sense checks so far

Round 1 (Fable, 2026-11-09), after kortev's point about Pochita's den. Checked against the code where the plan said
something exists.

| Found | Why it fails | Fix, now in the plan |
|---|---|---|
| One den per world | Nobody knows where it is; a returned heart lands 2000 blocks off with no hint | Many dens, empty once he's found, a rumour at the office (4.4) |
| Devils at night only | Players sleep through nights | Nests by day, missions at any hour (4.4) |
| The first devil "on the first night near a village" | You may not be near one, or asleep | An event that follows the player (4.1) |
| The first devil kills villagers | Your first village is ruined | It goes for you; villagers only run (4.1) |
| The suit as armour | Nobody swaps enchanted netherite for it | Worn over your armour, its own slot (4.3) |
| The katana | Loses to an enchanted netherite sword | A real sword that takes enchantments and grows with rank (4.3) |
| Devils that heal | A new player with a stone sword can't win | Weak devils heal slowly (4.3) |
| Yen as an item | Lost on death, clutter, copy bugs | A number on the licence (4.2) |
| The licence card | Lose it, lose everything | Saved on the player; the card only opens it (4.2) |
| A new guide screen | One more thing to learn | The advancements tab (4.2) |
| Fear | Dying makes the devil stronger, so you die more | A ceiling, fading, a growing bounty (4.5) |
| Mission devils | Spawned in chunks nobody has loaded | They appear when you arrive (4.2) |
| The partner | Followers get stuck and annoy | Catches up like a wolf, can't die for good, can wait at the office (3) |
| The Nether and the End | The plan said nothing | The Overworld only for 1.0 (4.4) |

Checked and fine: the Eternity price stops you sleeping but the bed still sets your respawn (`EternityBedMixin`).

### Round 2 (three Opus checkers, 2026-11-09)

Three agents who hadn't seen Fable's reasoning each read the whole plan and the code through one lens. 36 findings.
"Code" means the checker read it in the code and Fable spot-checked it; "memory" means a Minecraft fact nobody has
tested yet, to be confirmed by the package that builds it.

**Round 1 got these wrong:**

| Round 1 said | What's true | Now |
|---|---|---|
| Hotels are close enough | One *try* per 1280 blocks, most tries thrown out (code) | More hotels, measured (1.5) |
| A lost-property chest at the desk | A shared chest gets robbed; and alone, dying ends the fight (code) | The devil keeps you; things held per owner (4.6, 1.7) |
| The partner catches up like a wolf | That needs ground and loaded chunks, and leaves doubles (memory) | Saved on the player (3) |
| The first attack "in or near a village" | Golems kill it first; players far from villages never see it (memory) | Wherever you are; golems stay out (4.1) |
| Old worlds: a command | No help to a stranger | Placed while you play (4.4) |

**Minecraft:**

| Found | How sure | Fix, now in the plan |
|---|---|---|
| Offices "near villages" can't be generated; posters only reach new villages | memory | Notice boards and offices placed while you play (4.4) |
| A totem never pops when you go down | code | A totem is used first (4.6, 0.7) |
| The hotel only wakes at night; a skipped night resets a party | code | Any hour for a mission (4.4, 1.6) |
| The sealed hotel leaks: TNT, fire, lava, pistons, portals, chorus fruit, digging in by day | code | Lairs can't be wrecked (4.4, 1.6) |
| Nothing rebuilds a wrecked hotel | code | Rebuilt when the devil returns (1.6) |
| Villagers don't run from new mobs | memory | Added to what they flee (2.2) |
| A Pochita made with the world turns up after he's found | reasoned | Dens generate empty (4.4) |
| The rumour's search can stall a server | memory | Cached, with a limit (2.4) |
| Peaceful doesn't remove the hotel's devil and refills hunger | code | Lairs sleep, hunger prices pause (4.6) |
| Phantoms need open sky and a setting | memory | The price brings its own (4.6) |
| A suit slot needs a second mod | fact | Saved with the licence, drawn by the mod (4.3) |

**Fun:**

| Found | Fix, now in the plan |
|---|---|
| No power for hours; the video promised one | Small deals from beaten devils [pick] (4.1, 3.8) |
| The friend without the heart gets nothing | Small deals; the heart already runs on blood (code) (4.4) |
| Nothing says you merge by dying | The merge is staged [pick] (4.4) |
| Nothing to do at hour 40; yen runs out of uses | 1.0 stops at 1st Class; late yen uses (4.3) |
| Dying in the hotel locks you out for half an hour | The devil keeps you [pick] (4.6) |
| A contract is a life sentence | End it at the office for a cost [pick] (4.6) |
| The office is a long walk every time | The phone [pick] (4.2) |
| The Fox is useless in the dark, where devils are | Its fact sheet decides; it can't be useless in caves (M4) |
| With a partner you can't die | One pick-up per fight (3) |
| The first hour has no big moment | The first devil's arrival is staged big (4.1) |
| Missions repeat by hour 10 | Mission kinds, one trick per devil (4.2, 3.6) |
| Fear is unseen alone | Out of 1.0 [pick] (4.5) |
| The katana has no use at 4th Class | Cleaner parts sell for more (4.3) |
| Payday is a number changing | A moment you see and hear (4.2) |

**Other people:**

| Found | How sure | Fix, now in the plan |
|---|---|---|
| Anyone holding Pochita can merge; a dropped one is gone for good | code | Tied to his tamer; goes back to a den (4.4, 0.7) |
| A holder who quits keeps the heart forever | code | Returns after 14 days on a server [pick] (4.4) |
| "Dens live again" would hand the heart to whoever saved a den's place | reasoned | One far den, told to anyone who asks (4.4) |
| One idle player holds a hotel shut; logging out is a free exit; a restart loses the fight | code | A time limit, back inside on return, saved (4.6, 1.6) |
| Logging out while down dodges the finish | code | The body stays (4.6, 0.7) |
| Boss loot is one pile; idle spare accounts multiply the pay | code | Loot per fighter, pay for who fought (4.6, 0.7) |
| `oneContractPerDevil` is per hotel | code | Per world (4.6, 0.7) |
| "No NPC on a server" fails when your friend is offline | reasoned | The partner whenever no buddy is near (3) |
| One board for many players; stolen kills; spare accounts farming the first part | reasoned | Missions per party; pay once per player (4.2) |
| A veteran flattens a newcomer | reasoned | Rank bonuses off against players by default (4.6) |
| `legendRoar` is on by default; the owner is kept by name | code | Off; by player id (0.7) |
| The captain and office can be killed or broken | reasoned | They can't (4.6) |
| The two-client scene tests none of this | reasoned | The server check: logging out, a restart, leaving mid-fight (0.4) |

Checked and fine: every key press is a request and the server decides, so nothing is trusted from the client.

**Still not proven, each to be settled by a test in its package:** how far apart villages really are; the order of
going down and the totem check; whether golems attack the first devil; how many hotel tries survive; whether a
second player can merge while one already has (the checker says yes, the owner rule may stop it).
