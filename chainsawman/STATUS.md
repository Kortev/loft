# Status: the one page loaded after a context reset

Updated 2026-11-10. Keep this page under 3 KB. Change it whenever something starts, lands or gets answered.

## Where things are

- The plan: `chainsawman/MASTER_PLAN.md` is a short index. Read it, then only the part of `chainsawman/plan/` you
  need. Deep briefs are in `chainsawman/plan/packages/`.
- **Never read `chainsawman/HANDOFF.md` whole (153 KB).** Search it for the thing you need.
- Show facts: `chainsawman/bible/` (the Fox, the Leech, the Tomato, hunters' gear).
- kortev follows on their phone: send files with SendUserFile. Fable plans and reviews; Opus agents build.

## Running

Builders started 2026-11-11, each on its own branch with a brief in `plan/packages/`. Review, run all
tests, merge one at a time (shared files: `Devils.java`, `en_us.json`, `sounds.json`), then send the film.
- Merged (291 tests): 2.2a the Tomato's staged arrival as its chance night spawn, 3 quiet nights after.
- `notice-board` (2.1a): village board, licence, yen. `leech-devil` (3.3): model and fight.
- `saw-swings` (0.1d): Chainsaw Man's arm swings. `hotel-walk` (0.1e): stairs bug, seamless loop.
- kortev (2026-11-11): as many reels as possible from every thing built: keep raw footage, list reel ideas.
- Next: 3.9 devils grow (`bible/devil_strength.md`), then the office and shop. Scope: one player, friends ok.
- Merged earlier: Tomato round 2, seeds in 3 hits, the teaser (kortev posts `work/tomato/teaser.mp4`).
- Eternity: kortev finds the fight boring and the hotel ugly; new direction in `packages/0.1c_devil_big_pass.md`.
- Flaky in full runs, pass alone: `each_fighter_gets_the_whole_drop`, two form tests, a Beam missile test.

## Waiting on kortev

- The open choices in `chainsawman/plan/8_waiting_on_kortev.md` : use the defaults.
- The Revive reel (`Post8_Mon_Revive.mp4`) goes up Mon Nov 10 at 1 PM, as a reply to @é o gui.

## Rules that must survive a reset

- Never edit, stage or commit `src/gametest/java/io/github/kortev/shootingstar/test/ShowcaseSelfTest.java`.
- `git add` files by name only. Pull with `git pull --rebase --autostash`. Commit trailer:
  `Co-Authored-By: Claude Fable 5.2 <noreply@anthropic.com>`.
- No spoilers past the Reze arc. Synthesised sounds only. Call kortev "they".
- kortev (2026-11-09): decide design questions yourself, build, merge after your own review, then send the
  film; they say how it is afterwards. A subagent's report is never kortev's word.
- Keep usage down: small files, small contact sheets, one brief per job. When you find something big being loaded
  that isn't needed, cut it and tell kortev.

## Commands

- Tests: `./gradlew runGametest` (`-Ponly=hotel` runs the tests with that in their name).
- Filming: `./gradlew runSelftest -Pselftest=hotel -Ptarget=shaft -Pusername=Kortev -Pcapture=true -Pwidth=1080
  -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"`
