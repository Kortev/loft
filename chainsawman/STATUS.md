# Status: the one page loaded after a context reset

Updated 2026-11-09. Keep this page under 3 KB. Change it whenever something starts, lands or gets answered.

## Where things are

- The plan: `chainsawman/MASTER_PLAN.md` is a short index. Read it, then only the part of `chainsawman/plan/` you
  need. Deep briefs are in `chainsawman/plan/packages/`.
- **Never read `chainsawman/HANDOFF.md` whole (153 KB).** Search it for the thing you need.
- Show facts: `chainsawman/bible/` (the Fox, the Leech, the Tomato, hunters' gear; merged 2026-11-09).
- kortev follows on their phone: send files with SendUserFile. Fable plans and reviews; Opus agents build.

## Running

- **0.1b the devil's arena** (branch `devil-arena`, brief `chainsawman/plan/packages/0.1b_devil_arena.md`): you
  fall into the mouth and fight inside it; attack and dodge, no damage without a tell (kortev). Fable's picks
  stand. **Not building: stopped 2026-11-09 at the usage limit just after starting; start a new Opus agent
  from the brief.** Done = a played film at `work/devil_look/arena/fight.mp4`; Fable reviews, merges, sends it to kortev.
- Round 3 merged 2026-11-09 (252 tests pass): the devil fills the corridor's end, the torn arena is a stomach,
  saws cut its parts, a played film (`-Ptarget=played`). kortev has the film; their notes are the next round.
  Today's fight lasts 16 s with saws; the arena fixes that.
- Merged 2026-11-09: 0.7a Server-proof and 0.7b the hotel's share (loot into each fighter's pack, pay for who
  fought, one contract per world). Their two-client checks are in their briefs, for 0.4.

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
