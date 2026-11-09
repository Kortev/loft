# Master plan, part: 4c_world

Section 4.4: what's in the world and where (villages, offices, hotels, Pochita's dens). The index is `../MASTER_PLAN.md`.
Marks: **[decided]** kortev said so. **[pick]** Fable's choice, waiting for kortev's yes. **[check]** a show fact to
check on the wiki and Google first.

---

### 4.4 What's in the world, and where

| Thing | Where | How often |
|---|---|---|
| Notice board (poster and phone) | Every village | Put up when a player first walks in, so old worlds get it too |
| Public Safety office | **A building in the village** [decided: kortev, 2026-11-09] | It's one of the village's own buildings, so a new village generates with it, joined to the paths. About one village in three [to tune]. A village that was made before the mod gets one built at its edge when a player first walks in. |
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
- **The merge stays as it is [decided: kortev, 2026-11-09].** Die with Pochita carried or near and you come back as
  Chainsaw Man. No staged ambush: people who play this know the show, and anyone who keeps him dies sooner or later.
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

- **Old worlds:** notice boards and the first attack happen while you play, and a village that's already there gets
  its office built at its edge, so all of it works in a world that's already explored. Hotels and dens appear in chunks nobody has loaded yet, and a mission or the rumour sends
  you to the nearest one. A command is no answer for a stranger.
- **Missions pick real places.** A mission looks for a fitting spot near the office and puts its devil there. It
  never sends you 5000 blocks. A lair mission uses the nearest lair it can find, and isn't offered when none is in
  reach.
- **The hotel is a dungeon anyone can walk into** (kortev, 2026-11-09). Players install the mod and just play; they
  haven't seen a reel and aren't looking for anything. Some will walk into a hotel before they ever see an office.
  So no rank is needed, a sign at the door warns you, and the notice board in every village says which way the
  nearest hotel is. It's tuned so iron gear and a friend can finish it [to tune]. The board's hotel mission at
  2nd Class is the same place, harder and paid. The first devil the game *sends* you is still the weak one at your
  second dusk (4.1).
- **Lairs can't be wrecked.** Today only mining by hand is stopped, and only while the hotel is sealed. TNT, fire
  (it's wool and dark oak), lava, pistons, a portal built inside and chorus fruit all get through, and by day anyone
  can dig into the looping floor (checked in the code). A lair's blocks don't burn, blow up or move. It's rebuilt
  from its plan each time its devil comes back. Nothing teleports in or out while it's sealed.
