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

## 1. Survival with the Chainsaw Man world in it, or a pure Chainsaw Man world?

**Pure Chainsaw Man world:** a custom world (a city, Public Safety, no mining or crafting progression).
- Good: everything fits the show, and a story is easy to tell.
- Bad: it needs a whole city generator and a new progression. It throws away what Minecraft players come for (building, mining, their own base) and doesn't sit in modpacks. Every reel so far was shot in normal Minecraft biomes.

**Survival with the Chainsaw Man world in it (recommended):** normal Minecraft stays (mine, build, survive the night). The show's world lives inside it:
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
- **The heart can change hands (new, a game rule, on by default).** In the show everyone wants Chainsaw Man's heart (Reze is sent for it). A downed Chainsaw Man can have his heart **torn out by another player**: hold use on him for 5 s while he's down, like the revive in reverse. The heart passes to them, and they become Chainsaw Man; the old one is human again. Mobs never do this. It's a big SMP hook: hunt Chainsaw Man, defend him, betray him.
- **Bomb and Katana, later:** each comes from its own quest line's end, one per world. The heart rule applies to them too.
- **Hybrid or contracts, never both** (kept). Taking a hybrid heart ends your contracts; a hybrid is refused by every devil.

## 3. Contracts done right

**When a devil deals.** A devil only bargains when it gains something and has leverage. There are three ways to get a contract:
1. **It's beaten and begs for its life.** You have the leverage now, so it offers its contract so you'll spare it. You choose: finish it (loot) or take the deal (its power, its price). Devils beg in the show's world, as the Eternity Devil does. The first plan used this and was dropped for the "offer in the loop"; kortev's point about leverage brings it back.
2. **You summon it.** Draw a circle, bring an offering (meat, gold, a rare item), and say its name. It turns up and names its price. A weak devil is easy to summon; a big one only answers someone of high enough Public Safety rank, and only if it's fed well.
3. **Public Safety lends one.** At high ranks, the office can introduce you to a devil it keeps contracts with (the show's hunters get theirs through their work). Your rank is the leverage.

**Prices hurt from the moment you sign, not only when you use the power.** Each contract has:
- **A lasting price, felt every day**, sized to the power. It ends when the contract ends, except the body price, which is gone for good.
- **Sometimes a small fee per use**, on top.

Examples (each devil picks one; all to be tuned with kortev):
- **Eternity:** *it takes your rest.* You can never sleep through the night again: beds don't work for you, and you don't count toward the server's sleep. Phantoms find you. You can see the nights getting longer for you (darker evenings). The fee per use stays small: 1 XP level, not 3.
- **Ghost (an eye):** one side of your screen is dark for good, even after the contract ends.
- **Fox (it bites back):** each use costs a bite of hunger and a heart for a minute; it refuses to come out in the dark.
- **Curse (years of your life):** a lifespan clock. Each use takes a day off it, and at zero you die and the contract ends. It's shown on the contract screen. The heaviest price for the biggest hit.
- **Rules:** no max-heart prices (kortev). The price is always shown plainly on the contract screen before you accept.

**How many contracts?** In the show hunters hold several (Aki has three). Proposal: up to **3**, and every price stacks, so the stacked prices are the real limit. Any number of players can hold the same devil's contract (`oneContractPerDevil` stays as a server option).

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
- **Hunter gear:**
  - the Public Safety katana and suit (armour);
  - a pistol (the show's hunters carry guns), possibly later;
  - devil-blood rations (heal hybrids).

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

**Floor 8: the loop, and the devil's demand.**
- The corridor loops (built, seamless).
- In the loop, **hunger drains faster and food goes bad** (rotten flesh). Being trapped forever is the threat: the show's squad starved and turned on each other.
- After a few laps the devil speaks, and it *demands*: **"Give me one of you and the rest can leave."** If a Chainsaw Man is in the group: **"Give me Chainsaw Man."** Alone: "Stay. Stay forever."
- **The group decides:**
  - **Hand someone over:** walk them into the flesh, or everyone agrees on the screen. The one given is swallowed and the rest are freed. The swallowed player is **inside the devil** (its stomach: the show's scene, fine for the boss fight). They fight their way out from inside while the others can come back in from outside. Nobody is left to die for free.
  - **Refuse and fight together:** the walls bleed and it comes through them.
- **The fight:** a real boss. Each player has a job:
  - contract hunters use their powers;
  - Chainsaw Man saws and drinks its blood;
  - plain hunters cut its eyes and mouths with katanas.
  Its heart shows when enough flesh is cut away. It regenerates if you stop; it's eternity.
- **When it's beaten it begs** (that's the leverage): "Let me live, and I'll give you forever." Each player who wants it can take the contract and pay its price (they "spare" it; it goes away for `devilRespawnDays`). Or anyone can finish it: loot, the loop breaks, and Public Safety pays the mission. My suggestion when the group disagrees: whoever takes the deal gets it first (a few seconds to answer), then anyone can still finish the devil for loot, so nobody's choice is taken away. **Open question for kortev.**
- **The mission reward:** yen, rank, and the squad's last note.

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

## 8. Order to build (each piece its own reel)

1. **The plan, agreed with kortev** (this file).
2. **Contracts done right:** the beaten-devil bargain, the lasting prices and Eternity's new price. Up to 3 contracts. Reel: "Would you take this deal?"
3. **The heart rule:** tearing out Chainsaw Man's heart. Reel: "Your friend can steal your Chainsaw Man heart."
4. **Public Safety:** the outpost, the officer, the notice board, yen and ranks, the katana and suit. Reel: "I added Public Safety to Minecraft."
5. **Common devils:** Zombie and Bat first, then the fear-strength rule. Each devil is a reel.
6. **The Eternity Devil's look:** storyboard, renders, kortev's yes, then the model.
7. **The hotel quest line rebuilt:** textures, the squad's story, the demand, swallowed and inside, the group fight, the beg. The biggest reel.
8. Then the Fox, the Ghost and the Curse, each with a quest line; Bomb and Katana as hybrid quest lines; Power as a companion.

## Questions for kortev

1. Survival with the Chainsaw Man world in it (recommended), or a pure Chainsaw Man world?
2. Can Chainsaw Man's heart be torn out by another player (a server option, on by default)?
3. Up to 3 contracts with stacking prices, or one at a time?
4. Eternity's price: "you can never sleep through the night again". Good, or something else?
5. When a group disagrees at the end (deal or kill), which wins?
