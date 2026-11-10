# Master plan, part: 4d_dying_and_options

Sections 4.5-4.7: fear, dying, difficulty, server options, what saves. The index is `../MASTER_PLAN.md`.
Marks: **[decided]** kortev said so. **[pick]** Fable's choice, waiting for kortev's yes. **[check]** a show fact to
check on the wiki and Google first.

---

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
- **Logging out while down doesn't save you.** Today your body just goes, and you come back when it's safe. The
  bleed-out clock keeps running while you're gone: come back after it and you're dead where you fell.
- **Dying in a lair throws you out of it [decided: kortev, 2026-11-09].** Lairs are dungeons, and a dungeon kicks
  you out. ("The devil keeps you" was offered and turned down: cool, but not how a dungeon works.) Fable's picks for
  the details: you land outside the front door with your things, because today they lie on the floor inside for five
  minutes and are gone (checked in the code). What you lose is the run. Alone, the lair resets. If friends are still
  fighting, you wait at the door, and you're still paid if they win.
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
