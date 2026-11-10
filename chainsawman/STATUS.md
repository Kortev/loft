# Status: the one page loaded after a context reset

Updated 2026-11-09. Keep this page under 3 KB. Change it whenever something starts, lands or gets answered.

## Where things are

- The plan: `chainsawman/MASTER_PLAN.md` is a short index. Read it, then only the part of `chainsawman/plan/` you
  need. Deep briefs are in `chainsawman/plan/packages/`.
- **Never read `chainsawman/HANDOFF.md` whole (153 KB).** Search it for the thing you need.
- Show facts: `chainsawman/bible/` (the Fox, the Leech, the Tomato, hunters' gear; merged 2026-11-09).
- kortev follows on their phone: send files with SendUserFile. Fable plans and reviews; Opus agents build.

## Running

- **3.4 the Tomato Devil** (branch `tomato-devil`, brief `chainsawman/plan/packages/3.4_tomato_devil.md`): the
  first devil with a body, walks on eight jointed arms, burn its seeds or it comes back; also opens the `devil/`
  lane (0.6). Done = a played film at `work/tomato/fight.mp4`; Fable reviews, merges, sends it to kortev.
- **The Eternity Devil is parked** (kortev, 2026-11-10: "do the other stuff too and do a big pass"). The arena
  (0.1b) is merged, 261 tests; not very fun yet. kortev named the arm animations. The pass:
  `chainsawman/plan/packages/0.1c_devil_big_pass.md`.
- Flaky in full runs, pass alone: `each_fighter_gets_the_whole_drop`, two form tests.
- Merged earlier: round 3, 0.7a, 0.7b (two-client checks in their briefs, for 0.4). Later: 0.4, 0.2, 2.1.

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
