# Master plan, part: 4d_dying_and_options

Sections 4.5-4.7: fear, dying, difficulty, server options, what saves. The index is `../MASTER_PLAN.md`.
Marks: **[decided]** kortev said so. **[pick]** Fable's choice, waiting for kortev's yes. **[check]** a show fact to
check on the wiki and Google first.

---

### 4.5 Fear: the world remembers [decided in outline, numbers to tune]

**Back in, in a single-player shape (2026-11-11).** kortev: "the more the devil's feared, and the longer they're
alive in general, the stronger they get. Is that going to be a thing?" Yes. The server legend update that held
this is dropped, so it moves to package 3.9, built after 2.2a. **Fable's pick: each devil grows by itself**, so
you can see it, instead of a hidden score per type:

- **A devil left alive gets stronger.** Each night it lives it grows a step: more health, hits a little harder, a
  little bigger, and it looks worse (for the Tomato: more eyes open, darker). Five steps, about +50% at the top.
- **Fear speeds it up.** Villagers running from it, a player who ran from it or died to it: each counts as a
  step, at most one extra step a night. It frightens villagers; it still doesn't kill them (villages stay).
- **It's worth more, not only harder:** a grown devil drops more. And it never grows while nobody is near (no
  loaded chunks, no growth), so a devil you never met isn't waiting at full size.
- Show facts (`bible/devil_strength.md`, from search excerpts: the wiki refused the fetch): fear of its name
  makes a devil strong (stated). Blood heals a devil (shown). "Longer alive means stronger" is not in the show,
  so growing by the night is ours; tie each step to fear or blood where we can.

The older outline, kept for reference (the per-type score is not built unless the above feels thin):

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
