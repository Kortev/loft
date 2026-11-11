# Status: the one page loaded after a context reset

Updated 2026-11-10. Keep this page under 3 KB. Change it whenever something starts, lands or gets answered.

## Where things are

- The plan: `chainsawman/MASTER_PLAN.md` is a short index. Read it, then only the part of `chainsawman/plan/` you
  need. Deep briefs are in `chainsawman/plan/packages/`.
- **Never read `chainsawman/HANDOFF.md` whole (153 KB).** Search it for the thing you need.
- Show facts: `chainsawman/bible/` (the Fox, the Leech, the Tomato, hunters' gear).
- kortev follows on their phone: send files with SendUserFile. Fable plans and reviews; Opus agents build.

## Running

- **3.4b the Tomato Devil, round 2, merged 2026-11-10** (282 tests): the show's body (15 big eyes, a huge
  toothed mouth), creepy touches, the hop, a readable burst, Pochita eats seeds, a chainsaw shreds them. kortev
  has the film and `model.jpg`; their notes are the next round. Weak: close frames fill the view, the saws kill
  it in 7 s, speckled skin up close ("What was built" in the brief).
- **Building: 3.4c a creepy horror short of the Tomato** (kortev asked; branch `tomato-creepy`, an Opus agent;
  `plan/packages/3.4c_tomato_cinematic.md`). On hand-back: review the sheet, merge, send `work/tomato/creepy.mp4`.
- **The Eternity Devil is parked** for a big pass, arm animations first
  (`chainsawman/plan/packages/0.1c_devil_big_pass.md`).
- New plan pages (kortev, 2026-11-10): `plan/4e_what_you_play.md` (fiends are playable) and `plan/4f_scaling.md`
  (devil tiers, power 1-5). Fable's picks until kortev says otherwise.
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
