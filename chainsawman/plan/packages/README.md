# Package briefs

One file per package from `../5_milestones.md`, named by its number: `0.7_server_proof.md`, `2.3_office.md`.
Fable writes a brief just before the package is built. The agent building it reads this brief and the code, and
nothing else in `plan/`.

Keep a brief under about 6 KB. If it needs more, the package is too big: split it.

## The shape

```
# <number> <name>

## What the player gets
Two or three lines, from the player's side.

## Decided
What kortev has said yes to. What's Fable's pick. Nothing the agent must guess.

## Facts
Only the ones this package needs, copied in:
- show facts, each with its source (wiki page or search), spoilers filtered;
- Minecraft facts, marked "tested" or "from memory, test it first";
- what the code does today, with file names.

## Build
The behaviour in order, with numbers. Files to add or change. What must not be touched.

## The four checks
One line each for the show, Minecraft, fun, other people: what could go wrong here and how the design answers it.

## Tests
The game tests to add, by name. What the server check must show, if another player is involved.

## Done looks like
What Fable will look at: test result, a small contact sheet or a short clip, the diff.

## What was built
Filled in at the end: what changed, what's still off.
```

## Rules for every package

- Work in a git worktree under `.claude/worktrees/`. Commit by file name. Don't push.
- Never touch `src/gametest/java/io/github/kortev/shootingstar/test/ShowcaseSelfTest.java`.
- No spoilers past the Reze arc. Synthesised sounds only.
- A look is shown to kortev before it's built.
- `./gradlew runGametest` passes after the last edit.
