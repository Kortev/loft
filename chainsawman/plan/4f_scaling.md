# Master plan, part: 4f_scaling

How a human hunter, a hybrid and a fiend each get stronger. The index is `../MASTER_PLAN.md`.
Marks: **[decided]** kortev said so. **[pick]** Fable's choice, waiting for kortev's word. **[check]** a show fact to
check on the wiki and Google first.

kortev (2026-11-10): "we gotta think how the fiends will scale. AND also how hybrids will scale." All below is [pick].

---

## One ladder of devils, three ways up it

Devils come in five tiers. Each kind of player has a **power** from 1 to 5, shown on the licence. Hit a devil above
your power and your cuts count for less and its hits for more (the plan's "power and power resistance"). So all
three kinds meet the same content in the same order, but climb by different means and feel different doing it.

| Tier | Devils | Human: rank | Hybrid: heart | Fiend |
|---|---|---|---|---|
| 1 | Weak ones near villages (Tomato) | 4th Class | Heart 1, at the merge | Fiend 1, when it takes your body |
| 2 | Named common devils in their homes | 3rd Class | Heart 2 | Fiend 2 |
| 3 | The first lair (the hotel) | 2nd Class | Heart 3 | Fiend 3 |
| 4 | Bigger lairs | 1st Class | Heart 4 | Fiend 4, the top |
| 5 | World bosses (after 1.0) | Special Division | Heart 5 | (gorged, a Fiend 4 fights like a 5) |

## How each one climbs

- **Human: the office.** Missions and a rank trial (`4b_ladder.md`). Steady, never lost. Grows wide: more
  contracts (up to three), gear, the suit, a partner.
- **Hybrid: the heart eats.** The heart levels on the blood of devils **of its own tier or above**. A tier below
  gives a tenth; lower gives nothing. So twenty Tomatoes never make Heart 3: you have to go and kill something
  bigger. About five kills at your tier, or one lair boss, fills a level. Grows tall: few, big jumps. The level
  belongs to the heart and goes with it if it's stolen [built].
- **Fiend: feast and famine.** Levels are earned like the heart's (what it eats, by tier) and are never lost. But
  its strength right now swings with how full of blood it is: gorged, it fights a level above itself; starved, a
  level below, and it looks it (horns and colour grow and fade). A fiend is always hunting.

At the same tier: hybrid in form > gorged fiend ≈ human with three contracts and a partner > hungry fiend.

## What a level gives (never only a number)

Each level is a jump you see and feel [decided for ranks; same rule here]: a look change, one new move or a clear
upgrade of one, and the power step. No skill tree [decided].

- **Hybrid:** the whole Chainsaw Man kit stays from the merge, as built. Hearts 2 and 3 as built (more teeth, a
  deeper engine, faster chain, Beam bites harder). Hearts 4 and 5 are new peaks, designed when their tier exists.
- **Blood Fiend / Shark Fiend:** one move per level, from the show's own [check each]. Blood: a blade, a hammer,
  thrown spears, one huge weapon. Shark: swimming through the ground, the bite lunge, carrying a rider, the shark
  form. Written when the fiend package is briefed.

## What has to change in the code

- `form/HeartLevel.java` today: 3 levels at 30 and 80 XP, 40 XP for any devil kill. Two kills max the heart. That
  was fine with one devil in the game; it breaks as soon as common devils exist. Retune to tiers (package 2.10).
- Every devil gets a tier (a data tag per tier, so new devils only need a line).
- Until 2.10: the Tomato is worth a small fixed amount to the heart, not a full devil's 40.

## Still open (Fable decides at the brief)

A party with mixed power: whose tier sets the mission. Whether armour counts while in the form. PvP between kinds.
