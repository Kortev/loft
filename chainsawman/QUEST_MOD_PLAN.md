# Plan: the Chainsaw Man mod as a full quest mod (draft for kortev, 2026-11-08)

kortev (2026-11-08), after the first hotel build:
- It looks bad; mostly the devil, but the rest could be a lot better too.
- A devil that has trapped you has no reason to hand you its power.
- A price you only pay when you use the power is no price: you take the contract and never use it.
- If only one person can be Chainsaw Man, what does everyone else do?
- So: maybe a quest-based mod, multiplayer or solo, with quest lines, and the hotel as part of it.
- Pochita is one per world, and every hybrid is one per world. Contracts can be found by many people.

This plan replaces the "how you get a contract" parts of `SOLO_MOD_PLAN.md`. It keeps everything already built:
Chainsaw Man (the merge, the form, saws, chain, arms, Beam, the cord revive) and Forever. The rules stay: the show's
world, not its story (quests are ours); no spoilers past the Reze arc; plan the look before building; one piece at a
time, each its own reel.

**Rule (kortev, 2026-11-08): everything works without Chainsaw Man.** A hybrid is one per world, and most players in
the finished mod will never have one. Every quest, boss and mystery must be doable by plain hunters with katanas and
contracts. Chainsaw Man is a bonus, never a key.

## 1. Survival with the Chainsaw Man world in it, or a pure Chainsaw Man world?

**Pure Chainsaw Man world:** a custom world (a city, Public Safety, no mining or crafting progression).
- Good: everything fits the show, and a story is easy to tell.
- Bad: it needs a whole city generator and a new progression. It throws away what Minecraft players come for (building, mining, their own base) and doesn't sit in modpacks. Every reel so far was shot in normal Minecraft biomes.

**Survival with the Chainsaw Man world in it (decided, kortev and Opus, 2026-11-08):** normal Minecraft stays (mine, build, survive the night). The show's world lives inside it:
- devils are born from people's fears and roam like mobs;
- Public Safety has outposts;
- devils have lairs (the hotel);
- you make contracts;
- one person per world can find Pochita.

Quest lines start at Public Safety and send you out into your own world.
- Good: it works on any server, solo or with friends. It fits an SMP, where the one Pochita becomes a server-wide event ("who got Chainsaw Man?"). It builds on everything already made.
- Bad: the tone mixes cows and creepers with devils. That's fine; the show is a normal world with devils in it.

**Later, optionally:** a "Public Safety start" world option that spawns you in a small city district with the office. That's a nice extra, not the core.

## 2. Who you are: three paths

Everyone starts as a plain human. What you become is decided by what you find and choose.

| Path | How many | Power | Price |
|---|---|---|---|
| **Devil hunter (contracts)** | anyone | Devil contracts, plus hunter weapons | Each contract's price |
| **Hybrid** | **one per hybrid per world** | Transform with a trigger (Chainsaw Man's cord; later Bomb's pin, Katana's) | Hunted by others; can't hold contracts |
| **Plain human** | anyone | Hunter weapons, missions, teamwork | None; weakest alone |

- **Chainsaw Man:** Pochita's den is placed once per world (already built). Once someone merges, there is no other Chainsaw Man.
- **Hybrids are one kind of power in the world, not "the character"** (kortev, 2026-11-08). Chainsaw Man is the rarest
  thing you can find; most players will be contract hunters.
- **The heart can't be stolen by default** (Opus's suggestion after kortev asked what it would mean for the hybrid
  player: losing hours of progress to one bad fight would feel awful). Instead the heart goes back into the world, and
  a new Pochita den appears somewhere far off, when:
  - the hybrid chooses to give it up (to take contracts instead); or
  - on a server, the hybrid hasn't logged in for a set number of days (a game rule, off by default).
  For PvP servers that want the drama, a game rule `heartTheft` (off by default) lets a downed hybrid's heart be torn out by another player (hold use for 5 s, the revive in reverse); then the old hybrid is human and can take contracts straight away.
- **kortev's idea (2026-11-08, open): being a hybrid takes contract slots, and the heart can be stolen.** Opus likes
  it, with two changes. First, the heart fills **all 3 slots**: a devil living in you leaves no room for another. That is our balance
  choice, not the show's: the show (to the Reze arc) never says a hybrid can't make a contract; Denji just never does.
  One slot would let Chainsaw Man also hold two contracts. Second, stealing needs safeguards:
  - only from a hybrid who is downed;
  - a 5 s hold anyone can interrupt;
  - the heart can't change hands again for an in-game day;
  - the old owner gets their 3 slots back at once, so they can start making contracts.
  It fixes the luck problem: whoever finds Pochita first isn't set for good, and on a server the hybrid becomes
  everyone's target. Solo, it changes nothing.
- **One Chainsaw Man on a big server: the server's legend** (kortev and Opus, 2026-11-08). That's the show's own
  premise: there is one Chainsaw Man and everyone wants his heart. Keep one by default, and make him worth it:
  - **really overpowered:** the strongest thing on the server, a world boss who is a player;
  - **on the map when he's in the form:** every time he pulls the cord, everyone on the server hears the engine roar
    and gets his direction (a marker on the map or a compass for a minute). As a human he's hidden. So he chooses:
    full power and be found, or hide;
  - **a bounty on the heart:** Public Safety posts it as the top mission, and whoever takes the heart becomes the
    legend (with `heartTheft` on, and the fairness rules above);
  - on a big server the other hybrids (Bomb, Katana, later more) are one each too, so a few players are legends, not
    only one;
  - **a game rule `chainsawHearts` (default 1)** for servers that want more than one.
- **Bomb and Katana, later:** each comes from its own quest line's end, one per world. The heart rule applies to them too.
- **Hybrid or contracts, never both** (kept). Taking a hybrid heart ends your contracts; a hybrid is refused by every devil.

## 2b. Balance: no glass cannons (kortev, 2026-11-08; proposals)

- **Is Chainsaw Man strong enough?** He was tuned for a server: a saw slash is 10 (a netherite sword is 8), +10 armour,
  heals 1 a hit, plus the chain, regrowing arms, Beam and coming back from being downed. A hunter with three contracts
  (Forever alone releases up to 40) would match or beat him. If he's one per world and can be stolen, he should be
  **the strongest single player on the server**: he beats any one hunter, and a team of hunters can take him. Proposal:
  more damage and toughness in the form, faster blood healing, and the saws break through block-type defences.
- **Why power mods make glass cannons:** damage grows much faster than defence. In vanilla, big single hits mostly go
  through armour, so netherite helps against mobs and normal swords but not against a 40-damage hit. Netherite alone
  won't fix it. What will:
  - **No dodging game** (kortev, 2026-11-08: not a Souls-like or an MMO; hits stay instant). Instead, **power and
    protection scale together**, so fights between equals last, and a veteran still clearly beats a new player:
    - **Power resistance**, one stat that cuts damage from powers (contracts, hybrid attacks, devils). It comes from:
      - your Public Safety rank (each rank adds some);
      - each contract you hold (the devil in you hardens you);
      - the hunter suit's tier (upgraded with yen);
      - a hybrid's heart.
    - **Power damage grows with your rank too**, at about the same rate. Two 1st Class hunters trade many hits; a 1st
      Class against a newcomer wins fast.
    - It's shown on your rank card, so you can see how far along you are. This is the "something like a skill tree"
      kortev asked for, without a tree to manage.
    - A game rule still scales all power damage against players (for servers that want longer PvP).
  - **Scaling has to be fun, not just numbers** (kortev, 2026-11-08). Every step up is something you see and use:
    - **Big steps you notice, not small ones you don't:** each rank is a jump you feel, never +2%.
    - **A rank-up is a moment:** the captain hands you a new badge at the office, your rank shows next to your name, and
      a new suit look and the next tier of missions unlock.
    - **Contracts grow with you:** the more you use a contract, the deeper the bond (3 steps). Each step changes the
      power you can see. Forever's cube grows and its lines run longer; at the last step you can also freeze yourself
      out of a hit. The Fox's head gets bigger. A second move opens at the top step.
    - **The suit shows your rank:** plain at 4th Class, then a long coat, then a Special Division look.
    - **Hybrids grow too:** feeding on devils' blood levels the heart, and the form shows it (more saws, a louder
      engine, Beam stronger).
    - Every one of these is a reel moment ("I hit Special Division in my Chainsaw Man mod").
  - **Every path has defence, not only damage:** each contract gives something protective as well (Eternity: you can
    hold an arrow or a blow in the moment and step out of it), the hunter suit is real armour, and hybrids heal.
  - **Everyone goes down instead of dying** (the hybrid's downed state for every player, a game rule, on by default):
    when you'd die you fall, and a teammate can pick you up (hold use, as the revive works now). Alone, you die as
    usual after a short time. That makes teams strong and one burst much less final.
  - **Buddies (from the show: Public Safety hunters work in pairs):** pair with one player at the office. Near your buddy
    you take a little less damage and pick each other up faster.
  - **No skill tree:** ranks, contracts and the suit are the scaling (above). Ranks also give small perks (a faster
    pick-up, more food from rations).
- **Is stealing a downed hybrid's heart fair?** Only if downing him is earned and his team can protect him. So:
  - the thief must have downed him themselves (not a mob);
  - he can struggle, slowing the hold;
  - anyone can interrupt;
  - with buddies and everyone-goes-down, a hybrid with friends near is hard to rob;
  - it's a server option.

## 3. Contracts done right

**When a devil deals.** A devil only bargains when it gains something and has leverage. There are three ways to get a contract:
1. **It's beaten and begs for its life.** You have the leverage now, so it offers its contract so you'll spare it. You choose: finish it (loot) or take the deal (its power, its price). Devils beg in the show's world, as the Eternity Devil does. The first plan used this and was dropped for the "offer in the loop"; kortev's point about leverage brings it back.
2. **You summon it.** Draw a circle, bring an offering (meat, gold, a rare item), and say its name. It turns up and names its price. A weak devil is easy to summon; a big one only answers someone of high enough Public Safety rank, and only if it's fed well.
3. **Public Safety lends one.** At high ranks, the office can introduce you to a devil it keeps contracts with (the show's hunters get theirs through their work). Your rank is the leverage.

**Prices hurt from the moment you sign, not only when you use the power.** Each contract has:
- **A lasting price, felt every day**, sized to the power. It ends when the contract ends, except the body price, which is gone for good.
- **Sometimes a small fee per use**, on top.

Examples (each devil picks one; all to be tuned with kortev):
- **Eternity:** *it takes your rest.* You can never sleep through the night again: beds don't work for you, and you don't count toward the server's sleep. Phantoms find you. You can see the nights getting longer for you (darker evenings). kortev likes it (2026-11-08).
  **Each use: time catches up with you.** The moment you froze is taken from you: after the release your hunger drops by
  3 (a drumstick and a half) and the world stays grey round you for a second. That replaces the 3 XP levels. It stops
  you from spamming it (with the cooldown), and it's the devil's idea, not a number.
- **Ghost (an eye):** one side of your screen is dark for good, even after the contract ends.
- **Fox (it bites back):** each use costs a bite of hunger and a heart for a minute; it refuses to come out in the dark.
- **Curse (years of your life):** a lifespan clock. Each use takes a day off it, and at zero you die and the contract ends. It's shown on the contract screen. The heaviest price for the biggest hit.
- **Rules:** no max-heart prices (kortev). The price is always shown plainly on the contract screen before you accept.

**How many contracts? Up to 3, and every price stacks** (kortev agreed, 2026-11-08). In the show hunters hold several (Aki has three). The stacked prices are the real limit. Any number of players can hold the same devil's contract (`oneContractPerDevil` stays as a server option).

## 4. Public Safety: the quest hub

- **Outposts:** a small office building that generates near villages (about one per 1000 blocks). It has a front desk, a notice board and a few hunters.
- **The officer:** our own character, a Public Safety captain who gives missions. Not Makima or any show character.
- **Missions from the board:**
  - "A devil was seen at X": a map marker, kill it, bring back proof (its horn or eye).
  - "Escort a civilian".
  - "Clear a nest".
  - "Find the missing squad".
  - Missions scale with how many players take them.
- **Pay and rank:** missions pay in **yen** (a currency item) and raise your rank: 4th Class, 3rd Class, 2nd Class, 1st Class, Special Division. Yen buys hunter gear, offerings and rations. Rank unlocks bigger missions and the bigger devils that will answer a summons.
- **Hunter gear and weapons (to plan in full).** In the show, hunters fight with ordinary weapons (katanas, knives,
  guns), and their real power is their contracts. Kept that way, with one rule so the weapons matter:
  - **Devils regenerate** unless they're bled. Vanilla weapons hurt them but they heal back; **hunter weapons make
    them bleed** (no regeneration for a few seconds after a hit). So a plain hunter with a katana is useful, and a
    contract or Chainsaw Man is stronger again.
  - The weapons:
    - the Public Safety katana (the main weapon, bought with yen or given at a rank);
    - knives (fast, thrown);
    - a pistol (slow to reload, strong; the show's hunters carry them).
  - The Public Safety suit (armour).
  - Devil-blood rations, which heal hybrids.
  - **Devil parts are worth money** (in the show, Denji pays his debt by selling devil corpses): parts sell for yen.
    Our own addition, to decide: upgrading a weapon with a devil's part (a Bat fang on a knife), each with a small effect.

## 5. Devils in the world

- **Devils are fears.** A devil's strength comes from how much people fear it. In game, a devil type that keeps killing players on a server grows stronger: more health, and its name appears in the chat when it spawns. Hunting it down makes it weaker again. A server ends up with its own feared devil.
- **Common devils (mobs, season 1 / Reze arc):**
  - the Zombie Devil and its zombies (the show's first episode);
  - the Bat Devil (caves);
  - the Leech Devil (swamps);
  - the Tomato Devil;
  - the Snake Devil.
  Each spawns where its fear fits, at night or underground, rarer than vanilla mobs. Each drops its flesh and blood.
- **Lair devils (bosses with a quest line each):**
  - the Eternity Devil (the hotel);
  - later the Fox, the Ghost, the Curse.

## 6. The hotel, redone as a quest line: "The missing squad"

**The mission.** At 2nd Class, the board posts "A Public Safety squad went into a hotel and never came out". Take it alone or as a group, and it marks the hotel.

**The building, done properly (look first, then build).**
- The hotel gets its own textures: patterned hotel carpet, striped wallpaper, room doors with brass numbers, a working lift panel, a lobby with a real desk and bell.
- The lighting is low and warm, and the lamps flicker.
- Floors 1-7 rot step by step. The wallpaper peels and stains, and the wrongness gets stronger: the same room twice, a painting that follows, a door breathing, the phone ringing.
- **The missing squad's bodies and notes** tell the story as you climb: day 1 "we can't find the way down", day 3 "Yamada stopped talking". These are our own characters.

**Floor 8: the loop. The devil doesn't bargain: it wants to keep you forever.**
kortev (2026-11-08): nobody would ever hand over a friend, and with respawning it would only be annoying (drop
your items, die, walk back). So there is no demand. A devil that has you trapped has no reason to deal: it just
keeps you, and it feeds on the fear of being stuck forever. The quest is getting out.
- **The trap:** the corridor loops (built, seamless). Once the loop closes, **the hotel is sealed**: time stands
  still inside, so nobody from outside can get in to help until it's over (kortev, 2026-11-08). Hunger is normal, and
  food does not go bad (kortev: a new rule just for one devil would be pointless). If you run out of food, that's on
  you; being stuck with nothing to do is reason enough to get out.
- **The squad (the mission's goal):** two or three of the missing hunters are still alive in the loop. They're our
  own characters, NPCs, starving, sitting against the walls. Feed one and they get up and follow you, and they fight
  with you later (katanas). One of them has gone mad with fear and attacks you: knock him down, or feed him to calm
  him. Getting them out is what Public Safety pays for.
- **Finding the devil: a hunt, because it's alive in there** (replaces the random puzzles, which didn't fit how the
  devil works: it wouldn't leave clues, and rooms opening when you do things is a game, not a devil). The loop is the
  devil's own body, so it has to be somewhere inside it, hiding in the walls. A living thing can't hide perfectly:
  - you hear its **heartbeat**, louder the closer you are;
  - **eyes open in the wallpaper** and shut when you look at them;
  - a wall is warm and breathes;
  - **cut any wall and it bleeds** if the devil is behind it (any weapon or power: a katana, an axe, a contract).
  The squad's notes are story and hints ("it bleeds when you cut it", "it watches from the walls"), not puzzle keys.
  Where it hides is random each time, and when you get close or hurt it, it moves. When it's found and cut deep
  enough, it tears out of the wall: the fight.
- **Dying in there:** you respawn at your bed, outside, and you can't get back in until the hotel opens again. Your
  dropped items stay inside, where you died.
- **The fight:** a real boss. Each player has a job:
  - contract hunters use their powers;
  - Chainsaw Man saws and drinks its blood;
  - plain hunters and the rescued squad cut its eyes and mouths with katanas.
  Its heart shows when enough flesh is cut away. It regenerates if you stop; it's eternity.
- **When it's beaten, it begs** (now you have the leverage): "Let me live, and I'll give you forever." Each player
  has a few seconds to take the contract (each pays their own price).
  - **If anyone takes it,** the devil is spared and pays for its life by tearing off part of itself: everyone still
    gets its flesh and blood, then it sinks into the walls and is gone for `devilRespawnDays`.
  - **If nobody takes it,** it dies: loot.
  Either way the loop breaks and nobody's choice costs anyone else anything (Opus's answer to question 5).
- **The mission reward:** yen and rank for each rescued hunter, and the squad's last note.
- **Coming back (replay):**
  - the devil returns after `devilRespawnDays` with a new squad and a new mystery (the same hotel, re-rolled);
  - there are several hotels in a world;
  - the loot stays worth having (its blood refills a hybrid, and everything sells to Public Safety for yen);
  - other players can come for their own contracts;
  - a devil that keeps winning grows feared and stronger (section 5).
  Replay mostly comes from the world having many devils and missions, not from one lair.

**The devil itself (what looked worst): a real model, not a pile of lumps.**
- Build it the way Chitty was built: a Blender model made by a Python script, baked into a game mesh and texture.
- The show's Eternity Devil is a huge mass of grey-pink flesh filling the corridor, with **many human-like eyes** and **wide mouths of human teeth**.
- The model is about 6 wide x 4 high x 10 deep. Its mouths and eyelids are animated parts, and its eyes track players.
- It pushes out through the wall, with the wall's blocks cracking away.
- A storyboard and renders go to kortev first, with no building until they say yes (as Forever was done).

## 7. Multiplayer and solo

- Everything works solo. Missions and bosses scale with the number of players who took the mission (health and adds).
- **A party:** players who take a mission together share its marker, progress and pay.
- The hybrid hearts and the feared devils are what make a server's story: who has Chainsaw Man, who took which contract, which devil the server fears.

## 8. Order to build (each piece its own reel; updated 2026-11-08 after kortev liked the plan)

1. **The plan** (this file): kortev likes it (2026-11-08).
2. **Contracts done right.**
   - 3 slots, with the hybrid heart filling all 3.
   - The beaten-devil bargain.
   - Lasting prices: Eternity's no-sleep, and its per-use price, time catching up with you.
   - Contract bonds (3 steps that change the power you see).
   - Reel: "Would you take this deal?"
3. **Teams.** Everyone goes down instead of dying, picking up a teammate, and buddies. Then the heart going back to the world and the `heartTheft` option. Reel: "Your friend can save you in my Chainsaw Man mod".
4. **Public Safety.**
   - The outpost, the captain, the board and missions.
   - Yen and ranks, with the rank-up moment.
   - Power resistance and rank scaling.
   - The katana, knives and suit tiers; devils bleed.
   - Reel: "I added Public Safety to Minecraft".
5. **Chainsaw Man buffed** to be the strongest single player, with the heart levelling up from devil blood.
6. **Common devils:** Zombie and Bat first, then the feared-devil rule. Each devil is a reel.
7. **The Eternity Devil's look:** storyboard, renders, kortev's yes, then the model.
8. **The hotel rebuilt as the "missing squad" quest:**
   - textures;
   - the sealed loop;
   - the squad's notes and survivors;
   - the hunt in the walls;
   - the group fight and the beg.
   The biggest reel.
9. Then the Fox, the Ghost and the Curse, each with a quest line; Bomb and Katana as hybrid quest lines; Power as a companion.

## Decided (kortev, 2026-11-08)

1. Survival with the Chainsaw Man world in it.
2. Hybrids are one rare kind of power, one per world each. Proposed, needs kortev's OK: the heart can't be stolen by default; it goes back to the world when given up or after long absence; `heartTheft` is a PvP server option.
3. Up to 3 contracts, prices stack.
4. Eternity: never sleep through the night again. Proposed, needs kortev's OK: each use, time catches up with you (hunger down 3, the grey lingers a second) instead of 3 XP levels.
5. The hotel has no demand. Proposed, needs kortev's OK: rescue the squad, find the devil in the walls, fight; when it begs, anyone can take the deal and everyone still gets the loot.
