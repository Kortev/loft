# Status: the one page loaded after a context reset

Updated 2026-11-10. Keep this page under 3 KB. Change it whenever something starts, lands or gets answered.

## Where things are

- The plan: `chainsawman/MASTER_PLAN.md` is a short index. Read it, then only the part of `chainsawman/plan/` you
  need. Deep briefs are in `chainsawman/plan/packages/`.
- **Never read `chainsawman/HANDOFF.md` whole (153 KB).** Search it for the thing you need.
- Show facts: `chainsawman/bible/` (the Fox, the Leech, the Tomato, hunters' gear).
- kortev follows on their phone: send files with SendUserFile. Fable plans and reviews; Opus agents build.

## Running

Builders each work on a branch with a brief in `plan/packages/`. Review, merge one at a time, run all
tests (rerun once on a flake), then send the film (over 30 MB: a `_phone.mp4` copy). Two builders at once.
- Merged 2026-11-11 (332 tests): 2.2a Tomato night arrival; 0.1d arm swings; 2.1a notice board, licence,
  yen; 3.3 Leech round 1 (summon only); 0.1e hotel stairs and loop; 3.9 devils grow (2 rounds); 0.1g hotel corridor look; 0.1f Eternity arena (new fight, one look). kortev has the films.
- The Leech is summon only (its arrival is queued). Hotel tests keep `+ READY_WAIT`.
- **Improvement round** (kortev: fix the weak spots, make the strong stronger): `packages/improve_round_2.md`.
  Running: `eternity-arena-2`, `hotel-corridor-2`. Queued: Leech r3, Tomato r3, board and swings. Then reels.
- kortev (2026-11-11): as many reels as possible from every thing built: keep raw footage, list reel ideas.
- After the round: reels, then the office and shop. Scope: one player, friends ok.
- Merged earlier: Tomato round 2, seeds in 3 hits, the teaser (kortev posts `work/tomato/teaser.mp4`).
- Flaky, pass on a rerun: `each_fighter_gets_the_whole_drop`, two form tests, a Beam missile test.

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
