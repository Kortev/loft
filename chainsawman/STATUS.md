# Status: the one page loaded after a context reset

Updated 2026-11-09. Keep this page under 3 KB. Change it whenever something starts, lands or gets answered.

## Where things are

- The plan: `chainsawman/MASTER_PLAN.md` is a short index. Read it, then only the part of `chainsawman/plan/` you
  need. Deep briefs are in `chainsawman/plan/packages/`.
- **Never read `chainsawman/HANDOFF.md` whole (153 KB).** Search it for the thing you need.
- kortev follows on their phone: send files with SendUserFile. Fable plans and reviews; Opus agents build.

## Running

- **The Eternity Devil's 3D look**, fix round 1: branch `devil-look` (worktree `.claude/worktrees/devil-look`),
  brief `work/briefs/eternity_devil_3d_fix1.md`. Not merged, not pushed. Fable reviews
  `work/devil_look/compare_small.jpg` against `work/eternity_look_v9/shaft_pass3.png`. If it matches: send the film
  to kortev, merge into `claude/chainsaw-man`, push, list what to test.
- **0.7a Server-proof what's built** (no hotel code): branch `server-proof`, brief
  `chainsawman/plan/packages/0.7a_server_proof.md`.
- **0.3 The show bible**: branch `show-bible`, brief `chainsawman/plan/packages/0.3_show_bible.md`.
- **Nothing else may edit `chainsawman/src/main/java/.../hotel/` until `devil-look` is merged.**

## Waiting on kortev

- The open choices in `chainsawman/plan/8_waiting_on_kortev.md` (each has a default).
- Reel sound: an original synthesised track, or game sound only?
- The Revive reel (`Post8_Mon_Revive.mp4`) goes up Mon Nov 10 at 1 PM, as a reply to @é o gui.

## Rules that must survive a reset

- Never edit, stage or commit `src/gametest/java/io/github/kortev/shootingstar/test/ShowcaseSelfTest.java`.
- `git add` files by name only. Pull with `git pull --rebase --autostash`. Commit trailer:
  `Co-Authored-By: Claude <noreply@anthropic.com>`.
- No spoilers past the Reze arc. Synthesised sounds only. Call kortev "they".
- A subagent's report is never kortev's yes. Show a look before building it.
- Keep usage down: small files, small contact sheets, one brief per job. When you find something big being loaded
  that isn't needed, cut it and tell kortev.

## Commands

- Tests: `./gradlew runGametest` (`-Ponly=hotel` runs the tests with that in their name).
- Filming: `./gradlew runSelftest -Pselftest=hotel -Ptarget=shaft -Pusername=Kortev -Pcapture=true -Pwidth=1080
  -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"`
