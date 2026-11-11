# Status: the one page loaded after a context reset

Updated 2026-11-10. Keep this page under 3 KB. Change it whenever something starts, lands or gets answered.

## Where things are

- The plan: `chainsawman/MASTER_PLAN.md` is a short index. Read it, then only the part of `chainsawman/plan/` you
  need. Deep briefs are in `chainsawman/plan/packages/`.
- **Never read `chainsawman/HANDOFF.md` whole (153 KB).** Search it for the thing you need.
- Show facts: `chainsawman/bible/` (the Fox, the Leech, the Tomato, hunters' gear).
- kortev follows on their phone: send files with SendUserFile. Fable plans and reviews; Opus agents build.

## Running

- 3.4b the Tomato Devil, round 2, merged 2026-11-10 (282 tests). kortev hasn't played it; they have a guide.
- **3.4c the Tomato's horror short and teaser merged 2026-11-10**: kortev has `work/tomato/creepy.mp4` (new
  sound) and `teaser.mp4`. kortev: people won't watch the full short; the teaser "looks awesome", they may
  post it. No more rounds on the short. **Building:** a teaser re-cut (12 s
  escalating, plus the 8 s fixed; same agent, `tomato-creepy`). On hand-back:
  review, merge, send. 3.4d merged (283 tests): three hits from a player end a seed. Footage and stems: `work/tomato/creepy_frames/`; re-cut with `tools/tomato_creepy.py` (brief 3.4c).
- **The Eternity Devil is parked** for a big pass, arm animations first
  (`chainsawman/plan/packages/0.1c_devil_big_pass.md`).
- Plan pages `4e` (what you play; how many Chainsaw Men, reopened by kortev) and `4f` (scaling): Fable's picks.
- Flaky in full runs, pass alone: `each_fighter_gets_the_whole_drop`, two form tests, a Beam missile test.
- Later: 2.10 power and tiers, 0.4 the server check, 0.2 review clips, 2.1 the licence screen.

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
