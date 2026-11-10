# Status: the one page loaded after a context reset

Updated 2026-11-09. Keep this page under 3 KB. Change it whenever something starts, lands or gets answered.

## Where things are

- The plan: `chainsawman/MASTER_PLAN.md` is a short index. Read it, then only the part of `chainsawman/plan/` you
  need. Deep briefs are in `chainsawman/plan/packages/`.
- **Never read `chainsawman/HANDOFF.md` whole (153 KB).** Search it for the thing you need.
- Show facts: `chainsawman/bible/` (the Fox, the Leech, the Tomato, hunters' gear; merged 2026-11-09).
- kortev follows on their phone: send files with SendUserFile. Fable plans and reviews; Opus agents build.

## Running

- **The Eternity Devil, round 3** (branch `devil-round3`, brief `chainsawman/plan/packages/0.1_devil_round3.md`).
  The 3D look is merged. kortev's notes on the film: the devil must fill the corridor's end (the hotel is its body),
  the grab and the swallow look weak, and the film showed no fight. Done = a played-fight film at
  `work/devil_look/round3/fight.mp4`; Fable reviews it, then sends it to kortev **before** merging.
- **0.7b the hotel's share** (branch `hotel-share`, brief `chainsawman/plan/packages/0.7b_hotel_share.md`): loot per
  fighter, pay for who fought, one contract per world. Review the diff, run all tests, merge, push.
- Both work in `hotel/`: round 3 owns the look, the arena, the grab and the film; 0.7b owns loot, pay, the holder.
- 0.7a Server-proof is merged (241 tests pass). Its two-client checks are listed in its brief, for 0.4.

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
