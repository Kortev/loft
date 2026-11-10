# Master plan: the Chainsaw Man mod, from here to release

Written by Fable 5.2 on 2026-11-09, at kortev's ask. **A draft until kortev says yes.**

**This file is only the index.** The plan is in small files under `plan/`, so nobody has to read all of it to do one
job (kortev, 2026-11-09: rereading a big file full of things you don't need costs a lot of usage). Read this page,
then only the part you need.

## The mod in one line

Normal Minecraft, with the Chainsaw Man world living in it. Devils roam, Public Safety hires you, devils offer deals
that cost you something real, and one player per world can find Pochita.

## Rules that hold everywhere

1. **Design for someone who installs the mod and just plays.** No video, no wiki, and they didn't come from a reel.
2. Complete alone, better with friends. Everything is tested on a real server too.
3. The show's world, not its story. No spoilers past the Reze arc.
4. One Chainsaw Man per world; many dens; empty once he's found.
5. Lairs are dungeons: die and you're thrown out.
6. A devil deals only when you have something over it, and the price hurts.
7. Every power needs a look worth filming, shown to kortev before it's built.
8. Every design passes four checks before building: the show, Minecraft, fun, other people (`plan/6_how_we_work.md`).
9. Fable plans and reviews; Opus agents build.

## The parts

| File in `plan/` | Holds | Read it when |
|---|---|---|
| `1_goal_and_state.md` | What "finished" means; what's built today | Starting cold |
| `3_solo_and_friends.md` | Solo, friends, servers; the partner | Anything with a second player |
| `4a_first_hour_and_loop.md` | The first hour; missions, yen, the phone | Public Safety work |
| `4b_ladder.md` | Ranks, the suit, the katana | Ranks, gear, shop |
| `4c_world.md` | Villages, offices, hotels, Pochita's dens | Anything placed in the world |
| `4d_dying_and_options.md` | Going down, dying, game rules, saving | Death, servers, settings |
| `4e_what_you_play.md` | Hybrid, contract, partner, hunted; fiends | A new devil, contract or playable thing |
| `5_milestones.md` | M0-M5 and every package, one line each | Picking what's next |
| `6_how_we_work.md` | Lanes, briefs, the four checks | Before writing a brief |
| `8_waiting_on_kortev.md` | Open questions for kortev | Before asking kortev anything |
| `9_reels_risks_first_wave.md` | Reels, risks, the first three agents | Starting a wave |
| `12_sense_checks.md` | What the checkers found, and why | Only when asking "why is it this way?" |
| `packages/` | One deep brief per package | Building that package |

Section numbers in the text (like "4.4" or "section 8") match the file names.

## Where the depth goes

The parts above say *what* and *why* in a few lines each. The depth (exact behaviour, numbers, files to touch,
tests, what done looks like) goes in **one brief per package** in `plan/packages/`, named by its number
(`0.7_server_proof.md`). See `plan/packages/README.md` for the shape.

- A brief is written **just before its package is built**, not months ahead. Deep plans for far-off packages go stale
  and cost usage twice.
- A brief stands alone. The agent building it reads its brief and the code, and nothing else in `plan/`. So the
  brief copies in the few facts it needs.
- When a package is done, its brief gets a short "what was built" at the bottom, and its line in
  `5_milestones.md` is marked done.

## Reading rules, to keep usage down

- `STATUS.md` is the one page loaded after a context reset: what's running, what waits on kortev, the rules that
  must survive. Keep it under 3 KB and current.
- Never read `HANDOFF.md` whole (153 KB). Search it for the thing you need.
- When you find something big being loaded that isn't needed, cut it and tell kortev.
- `QUEST_MOD_PLAN.md` and `SOLO_MOD_PLAN.md` are older. Where they disagree with `plan/`, `plan/` wins once kortev
  has said yes.
- No part file grows past about 8 KB. If one does, split it.
- Pictures: small contact sheets, never full-size frames.

## The milestones, one line each

| | What | State |
|---|---|---|
| M0 | Clear the decks: land the 3D devil, review clips, the show bible, the server check, server-proof what's built | 0.1 running |
| M1 | The hotel, finished: textures, sound, more hotels, can't be wrecked, the reel | waiting |
| M2 | Public Safety and the loop: licence, notice board, office, missions, yen, ranks, weapons, the partner | waiting |
| M3 | Devils in the world: Zombie, Bat, Leech, Tomato, nests, small deals | waiting |
| M4 | The Fox, then the public release | waiting |
| M5 | Updates, one at a time | later |
