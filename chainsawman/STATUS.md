# Status: the one page loaded after a context reset

Updated 2026-11-09. Keep this page under 3 KB. Change it whenever something starts, lands or gets answered.

## Where things are

- The plan: `chainsawman/MASTER_PLAN.md` is a short index. Read it, then only the part of `chainsawman/plan/` you
  need. Deep briefs are in `chainsawman/plan/packages/`.
- **Never read `chainsawman/HANDOFF.md` whole (153 KB).** Search it for the thing you need.
- Show facts: `chainsawman/bible/` (the Fox, the Leech, the Tomato, hunters' gear; merged 2026-11-09).
- kortev follows on their phone: send files with SendUserFile. Fable plans and reviews; Opus agents build.

## Running

- Nothing is building. **0.1b the devil's arena merged 2026-11-09** (261 tests pass): fall through the mouth into
  a lit flesh arena 17 x 12; slam, head lunge, squeeze and grab each have a tell; cuts show; about 4 min with saws.
  kortev has the film (`work/devil_look/arena/fight.mp4`); their notes are the next round. Known weak: the walls
  are a red block checkerboard, close cuts fill the view, the squeeze is lumpy (brief, "What was built").
- Flaky in full runs, pass alone: `each_fighter_gets_the_whole_drop` (blood at the full pack's feet), two form
  tests. `liveEvent` now lays its own ground.
- Merged earlier: round 3 (kortev: its film "looks bad and boring"), 0.7a, 0.7b; two-client checks are in their
  briefs, for 0.4. Next: 0.4 the server check, 0.2 review clips, 2.1 the licence screen.

## Waiting on kortev

- The open choices in `chainsawman/plan/8_waiting_on_kortev.md` : use the defaults.
- The Revive reel (`Post8_Mon_Revive.mp4`) goes up Mon Nov 10 at 1 PM, as a reply to @é o gui.

## Rules that must survive a reset

- Never edit, stage or commit `src/gametest/java/io/github/kortev/shootingstar/test/ShowcaseSelfTest.java`.
- `git add` files by name only. Pull with `git pull --rebase --autostash`. Commit trailer:
  `Co-Authored-By: Claude <noreply@anthropic.com>`.
- No spoilers past the Reze arc. Synthesised sounds only. Call kortev "they".
- kortev (2026-11-09): decide design questions yourself, build, merge after your own review, then send the
  film; they say how it is afterwards. A subagent's report is never kortev's word.
- Keep usage down: small files, small contact sheets, one brief per job. When you find something big being loaded
  that isn't needed, cut it and tell kortev.

## Commands

- Tests: `./gradlew runGametest` (`-Ponly=hotel` runs the tests with that in their name).
- Filming: `./gradlew runSelftest -Pselftest=hotel -Ptarget=shaft -Pusername=Kortev -Pcapture=true -Pwidth=1080
  -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"`
