# Master plan, part: 6_how_we_work

Sections 6-7: kortev's time, lanes, briefs, the four checks. The index is `../MASTER_PLAN.md`.
Marks: **[decided]** kortev said so. **[pick]** Fable's choice, waiting for kortev's yes. **[check]** a show fact to
check on the wiki and Google first.

---

## 6. kortev's time is the limit, so the plan is built round it

Opus agents can build three things at once. kortev can look at maybe one a day, on a phone. So:

1. **A review clip, not a pile.** Every finished piece becomes one short portrait clip with at most three questions,
   each answerable with a tap. Fable cuts it down before sending. No walls of text.
2. **At most three things waiting on kortev at a time.** If three are waiting, agents work on things that need no
   judging (tests, the server check, fact sheets, tools), not on new looks.
3. **Looks are judged before they're built, always** [decided]: fact sheet, then concept, then kortev's yes, then the
   build.
4. **Mechanics get a default.** For rule and number calls, Fable picks, builds it, and says so in the clip. kortev
   overrules any time. Looks never get a default.
5. **One list.** Section 8 is the only list of what waits on kortev. It's kept current.

---

## 7. How the agents run

**Who does what**
- **Fable 5.2:** this plan, every brief, judging looks against the show, reviewing film and diffs, merging, talking
  to kortev.
- **Opus 5.5 agents:** building, concept art, fact sheets, filming, tests. One brief file each, in `work/briefs/`.

**The four checks** (kortev, 2026-11-09: double, triple, quadruple check that it makes sense). Every design, before
its brief is written, has to pass all four, written down in the brief:
1. **The show:** is it true to the wiki and Google? (the fact sheet)
2. **Minecraft:** does it work with how Minecraft really behaves? Beds skip nights, dropped items vanish in five
   minutes, chunks unload, worlds are endless, players already have enchanted netherite, followers get stuck.
3. **Fun:** walk through it as a player, alone, in the first ten minutes and in the tenth hour. Where is it a chore,
   a dead end, unfair, or something you'd never find?
4. **Other people:** two friends, and a big server. What if someone is offline, late, greedy or mean?
For the big pieces (this plan, each milestone's design) the last three are also run by separate checker agents who
haven't seen Fable's reasoning, so it isn't one mind agreeing with itself.

**One package, start to end**
1. Fact sheet exists (0.3). If not, that's step one.
2. Fable writes the brief: the goal, the checked facts with sources, the four checks, the files it may touch, what
   done looks like, what to film.
3. If it has a look: the agent makes the concept, Fable reviews it, kortev says yes.
4. The agent builds in its own worktree and branch, commits piece by piece, files added by name.
5. `./gradlew runGametest` all passing, with new tests for the new thing.
6. The agent films it in portrait and makes a small contact sheet.
7. Fable reviews the sheet and the diff. A fix round if needed.
8. Fable merges to `claude/chainsaw-man`, pushes, and sends kortev the clip with a numbered list of what to test.

**Lanes, so agents never touch the same files**

| Lane | Owns | Runs alongside |
|---|---|---|
| Hotel | `hotel/`, the Eternity assets | anything |
| Public Safety | `safety/` (new) | anything but itself: one agent at a time |
| Devils | `devil/` (new), one sub-folder per devil | two devils at once is fine |
| Looks | `tools/`, `work/` (no Java) | anything |
| Checks | tests, the server-check script, the docs | anything |

At most three building agents at once, one per lane. Shared files (the mod's main class, the language file) are
changed only at merge time, by Fable.

**Standing rules for every agent** (in every brief)
- No spoilers past the Reze arc, in code, comments, commits and reports.
- Never touch `src/gametest/java/io/github/kortev/shootingstar/test/ShowcaseSelfTest.java`.
- Synthesised sounds only.
- Check show facts on the wiki and Google; mark guesses.
- Keep image reads small: contact sheets and crops.
- Report short: what's in, the test result, the film paths, what isn't verified, what's weakest.

**Keeping usage down**
- Opus agents cost more than Sonnet did. Briefs stay tight, and a package that misses twice comes back to Fable
  instead of a third blind try.
- Rote jobs (re-filming, contact sheets) can stay on Sonnet if kortev wants to save usage [pick: yes].
