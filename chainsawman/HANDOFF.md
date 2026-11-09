# Handoff: kortev's Chainsaw Man mod (state on 2026-10-22)

You are picking this up with nothing but this GitHub repository (`Kortev/loft`). No chat history, no files from
kortev's PC, and none of the reference images they sent come with it. Everything you need is in this file and the
repo. Read it all before doing anything, then read the repo's own `HANDOFF.md` at the root for the wider project.

## Start here: where things stand (2026-10-22)

**Next (decided 2026-11-06): make this a full solo Chainsaw Man mod.** Read `chainsawman/SOLO_MOD_PLAN.md` first:
phase 0 (the Shooting Star dependency dropped) is done; phase 1 adds devil contracts, starting with the Eternity Devil (its looping
hotel hallway, the fight, the contract it offers). Its first reel replies to a viewer's comment. Also done since this
section was written: the Gojo mod's Hollow Purple (see the root `HANDOFF.md`; kortev hasn't judged its test clip yet).

kortev moves between Claude accounts when one runs out; this section is what the next session needs first. The rest
of the file is the full record.

**Game test harness and the flakes (2026-11-09, fixed: 10 full runs in a row with 217 of 217 passing; before, about 4 of 7 runs had one to three failures).** `./gradlew runGametest` (`-Ponly=name` runs the tests with that in their name). Every test is its own batch (`batchId`), so tests run one after another, not together (only `a_blocks`, `c_chitty`, `e_thunder` hold several); a full run is about 60 to 90 s. Test boxes land at a random place in the world each run, so anything that depended on where a box falls on the chunk grid failed at random. The causes, each fixed at its cause:
- **Chunks beside the test box were not ticking.** The runner keeps only the box's own chunk loaded. A player or mob standing in a neighbouring chunk is not ticked at all (age stays 0, so a joined player's 60 ticks of protection never run out: `nearabuddyyoutaketenpercentless` "the stranger could not be hurt ... age 0"), and a beam crossing a chunk border stops (`beamwalksonhislegs`, `beamburstsupunderamob`...). `test/TestIsolation` + `mixin/TestIsolationMixin` now force and synchronously load a 7x7 chunk square round every box when its batch is placed. `ground()` (Form) and `forced()` (Hotel) also load their chunks at once (`getChunk(..., FULL, true)`) instead of waiting for the ticket to take effect (hotel `ready` gives up after 400 ticks).
- **Leaks between batches.** The Gap tests never removed their players (`shooter`, `erased`, `voided`, `stopshooter` stayed in the player list for every later batch), and a Domain or Gap still running, or forced chunks, game rules, time of day, weather, spawn and loose entities (beam debris: `beamtearsthroughawall` "the flying debris did not go away") carried over too. `TestIsolation.reset` runs before every batch: collapses Domains, ends the Gap, removes every player, discards every non-player entity, unforces every chunk, restores the first batch's game rules, time, weather and spawn. A test must still clean up after itself for what it does inside its own batch, but nothing crosses a batch.
- **Marginal geometry in the tests themselves.** `aimedhitsland`: the husk stood at an angle (cos 0.39) just above the aim's 0.3 limit at normal eye height, and the form's eye height changes, so it was 0.29 at times; the husk is now put level with the slasher's eyes. `gapgametests.liveevent`: the third place in the gathering is 12 blocks round the ring (up to 13.4 on blocks) and the test allowed 12.0; now 14.0 (anyone outside the group is 20+). `hotelgametests.cuttingnearthedevil...`: the devil's spot is random and a door may stand where "3 along" is; `wallAlong` now takes the nearest wall block that has the right effect (within or beyond `Hunt.BLEED_RANGE`). `domaingametests.wholedomain`: the cow, with AI, strolled out of the 12-block Domain before it closed; its AI is off until just before.
- **Still seen about once in ten runs, unexplained: `formgametests.beamthrowshisriderandfetchesthem` "the missile cut only 0.0"** (the thrown rider's flight cut nothing though the beam, rider and husk positions were the same as in passing runs; the test now prints each flight tick's state in its message, so the next failure says where the flight went wrong). `hotelgametests.ifnobodyacceptsitcanbekilledfortheloot` once timed out at 900 ticks (the hotel's chunks not loaded in time; fixed by the synchronous load, not seen since).
- To find a flake: loop the full run and read `build/gametest/junit.xml` after each (the failure message is there, not in the log).

**Done (2026-11-09): contract bonds, "Not yet", Chainsaw Man buffed, the heart's levels, and fixes from the review** (built by Sonnet 5.5 from `work/briefs/bonds_and_buff.md`; plan sections 2b and 8 steps 2 and 5; kortev has not judged it; stills in `work/bonds_buff/` with a `contact_sheet.png`, git-ignored; they come from `BondsSelfTest`: `./gradlew runSelftest -Pselftest=bonds -Pusername=Kortev -Pwidth=1080 -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"`, about 45 s).
- **Bonds** (`contract/Bond`, built so every future contract gets them): the uses of each contract are kept for each player in a persistent attachment (`Bond.USES`, by contract id; kept when the contract ends and across death). Only **uses that locked something count** (`Bond.record`, called when a moment ends, and when it ends early because its targets went; a use with nothing to hold costs and counts nothing). Bond 2 at `TWO` 15 uses, bond 3 at `THREE` 40; `/chainsaw bond <player> <uses>` sets it. The owner is told by `BondPayload` (join, change; `up` = a bond-up) into `ClientBonds`; the new **contract HUD** (`ContractHud`: name, `Bond n`, a bar to the next step, three pips, "Hold [key]: Not yet" at the top step, and the bond-up banner) shows it.
  - **Forever by bond** (`Forever.cubeHalf/lasts/farCopies`; the cube and each hub carry `bond()` so the clients draw the same): bond 2 = a 13-block cube (`CUBE_HALF_BOND2` 6.5), the lock 4 s (`LASTS_BOND2` 80; a boss's 2 s, `BOSS_LASTS_BOND2` 40), 14 far render-only copies each way instead of 11 (`FAR_COPIES_BOND2`, out to about 32 blocks; `ForeverLayout.farTotal`, and `ForeverHubRenderer.farKeep/farRank/farFade` take the total). Bond 3 = the release is bigger: hits x2 (`MULT_BOND3`) up to 60 (`CAP_BOND3`) instead of x1.5 up to 40, knocked 1.35x further, twice the burst particles and a bigger burst drawn (`hub.bond() >= 3`), a louder, lower release sound, a stronger flash. Prices unchanged (3 hunger after the release, no sleep).
  - **A bond-up is a moment**: `Bond.record` plays `forever.bond_up` (a crowd of voices over a clock that stops), the client drains the screen grey (`ForeverHud.catchUp(34)`) and shows `BOND n` and the devil's line (`chainsawman.bond.eternity.2/.3`, with trembling copies of it) for `ClientBonds.MOMENT` 90 ticks.
- **"Not yet"** (`Forever.stepOut`; the key is the switch-contract key **held** for `ChainsawManClient.HOLD` 8 ticks, `NotYetKeyPayload`; with Forever selected at bond 3 a tap is only known on release, so it switches on release; any other time the key acts at once as it always did): you step out of time for `NOT_YET_LASTS` 30 ticks. **Untouchable** (`allowDamage` turns every hit aside but the void and `/kill`); you **can still move** but cannot attack or use anything (the use callbacks and `ForeverAttackMixin`); grey (`ClientNotYet`: the body washed grey in third person, your own screen drained, five copies of you trailing 3 ticks apart, drawn with `ForeverCopyRenderer.drawTarget`; `NotYetPayload` tells everyone). When it ends you step back in where you stand (`forever.step_out`, a burst of blood). It shares Forever's cooldown (from the end) and costs the same price (3 hunger at the end; refused at 0 hunger). **I read "step out where you were" as "step back in where you stand"; if kortev meant "back to where you began" it is one teleport in `Forever.stepBack`.**
- **Chainsaw Man buffed**: slash 13 (`Saw.SLASH_DAMAGE`), finisher 18, armour 14 and toughness 6 in the form (`Form.ARMOR`), heals 2 a hit (`Saw.HIT_HEAL`), blood from cutting a third more (`Blood.HIT` 11, `BITE` 4, `KILL` 33; Eternity's bottle still fills it).
- **The heart levels up** (`form/HeartLevel`): XP is kept on the player (`HeartLevel.XP`; it goes with a stolen heart and is gone when the heart is given up). XP: killing something of the entity type tag `chainsawman:devils` (the Eternity Devil; **add every future devil there**) `KILL_XP` 40; the tag `devil_parts` (the flesh patches and growths, which cannot be killed today, so nothing counts there yet) `PART_XP` 6; drinking Eternity's blood `BLOOD_XP` 10. Level 2 at `LEVEL_2` 30 XP, level 3 at `LEVEL_3` 80. **Level 2**: damage +2 on slash, finisher, lunge and Beam's launch; a deeper engine (idle pitch 0.82, `FormIdleSound`); more teeth on the saws (`SawTeeth`: extra steel fangs along both edges of every bar, built at run time from the bar's own shape and the chain's swatch, so no model change; one every 1.8 px, 1.6 long). **Level 3**: damage +2 again; the chain pulls twice as fast (`Grapple`: the pull and the yank 2x); Beam bites 1.5x (`BEAM_BITE`); blood sprays further (client: 26 drops at 1.6x speed on every saw hit instead of 12); engine pitch 0.66; fangs every 1.3 px and 2.4 long. Each level-up (`HeartLevel.levelUp`): `form.heart_level` (a roar, a burst, a thud), blood bursting from the chest, a red wash and "HEART LEVEL n" with a line on the owner's screen (`BloodHud.banner`, drawn by `ContractHud`); the owner's blood bar shows `Heart n  xp/next`. Events `FormEventPayload.HEART_LEVEL/HEART_XP/HEART_LEVEL_UP`; `/chainsaw heartxp <player> <xp>`.
- **Fixes from the review** (own commit): a fallen or downed player cannot use Forever, Not yet, the switch key, answer the beg or cut a hotel wall; a Forever-locked (or out-of-time) player cannot lift, ask a buddy or pull a cord (the three `UseEntityCallback`s); the beg waits for someone on the Drop screen (`Hotel.DROP_GRACE` 600 ticks more) and the Drop screen closes when the beg ends, with `oneContractPerDevil` rechecked in `drop`; a disconnect broadcasts the cancel of a theft, a fall and a lift; Forever's price and bond count also come when a moment ends early with its owner there; den placement does one den a call with few chunk loads (`PochitaPlace.ensure`: `ENSURE_LOADS` 2, falling back after `ENSURE_TRIES` 6 empty calls) and a Pochita is tagged with his den (`PochitaEntity.den`, kept in `Heart.DEN` at the merge so the world takes that den's heart; lost Pochitas of every den come back, `Extra.lost`); a thief is told their contracts are gone; Yamada's note is saved with him.
- **Numbers kortev might tune**: `Bond.TWO/THREE`; `Forever.CUBE_HALF_BOND2/LASTS_BOND2/BOSS_LASTS_BOND2/FAR_COPIES_BOND2/MULT_BOND3/CAP_BOND3/NOT_YET_LASTS` and the hold time `ChainsawManClient.HOLD`; `Saw.SLASH_DAMAGE/FINISHER_DAMAGE/HIT_HEAL`, `Form.ARMOR`, `Blood.HIT/BITE/KILL`; `HeartLevel.LEVEL_2/LEVEL_3/KILL_XP/PART_XP/BLOOD_XP/DAMAGE_PER_LEVEL/BEAM_BITE/CHAIN_PULL`, the engine pitches in `FormIdleSound`, the teeth in `SawTeeth.draw` (`step`, `tall`).
- **Tests**: `BondGameTests` 8 (counting and the steps, each contract its own and saved, the bond 1 cube, the bond 2 cube and 4 s, the bond 3 burst, Not yet's untouchability/price/cooldown, a fallen player's refusals, a locked player cannot lift), `BuffGameTests` 4 (the numbers, a slash's damage/heal/blood at levels 1-3 and the form's armour, XP from a devil kill and from blood with the level effects, the thresholds), two beg tests in `HotelGameTests`; `FormGameTests.frenzyDoubles` now sets the blood bar (the buffed saws drink more from its slash and kill). 217 game tests in all.

**Done (2026-11-09): teams (everyone goes down, pick-ups, buddies) and the heart's rules** (built by Sonnet 5.5 from `work/briefs/teams.md`, step 3 of `QUEST_MOD_PLAN.md`'s build order, sections 2 and 2b; kortev has not judged it; stills in `work/teams/`, git-ignored). The code is in `form/` (`TeamRules`, `Fallen`, `Buddies`, `Hearts`, `Legend`, `TeamState`), the client in `client/` (`ClientTeams`, `TeamsHud`, `mixin/FallenPoseMixin`), the tests in `TeamGameTests` and the stills in `TeamsSelfTest`.
- **Game rules** (`TeamRules`): `everyoneGoesDown` (true), `heartReturnDays` (0 = off), `heartTheft` (false), `chainsawHearts` (1, at least 1), `legendRoar` (true).
- **Everyone goes down** (`Fallen`): a player who is not a hybrid, with the rule on, who takes a hit that would kill them (not the void, not `/kill`: `lethal`) is left at 1 health on the ground instead (the swimming pose on every client, `DownedPoseMixin`; crawl: speed -80%, no jump; no attack, block use, item use or entity use: the same callbacks `Downed` has; a mob can still hit them, and hybrids' mob-forgetting is not applied). They have `Fallen.TIMER` 600 ticks (30 s, saved with the player: `Fallen.LEFT`, so leaving does not skip it; the join puts them back down). Each hit takes `HIT_COST` 40 ticks (2 s) off (not twice within `HIT_GAP` 10 ticks; a hit that takes the last of it leaves one tick, so the death comes from the next tick, not in the middle of the hit). At zero they die as usual (`bleedOut`: `reset` then a generic kill). Giving up: the key **X** (`key.chainsawman.give_up`, hold 40 ticks, `ClientTeams.tick` sends `TeamActionPayload.GIVE_UP`; no screen button) -> `Fallen.giveUp`. Pick-up: another player crouched within 3 blocks holding use (the game's use-on-entity, repeating every ~4 ticks, valid while the last is within `GAP` 6 ticks) for `LIFT` 60 ticks (`LIFT_BUDDY` 30 with a buddy near) -> up with `UP_HEALTH` 8 (4 hearts), no cost to the helper; letting go cancels; one helper at a time. Their team (their buddy and scoreboard-team mates) get an action-bar line every second. The hybrid order: `Downed.init` registers first, so a hybrid (and the first-time merge) goes the old way.
- **Buddies** (`Buddies`): crouch + right-click with an empty main hand (the target not down) asks (chat both ways, the ask stands 60 s); the other doing the same accepts. One at a time (`BUDDY`, `NAME` attachments, saved, copied on death; `TeamState.ended` carries "your buddy ended it" to someone who was away). `/chainsaw buddy` shows, `/chainsaw buddy end` ends (both sides). Near = within `NEAR` 16 blocks, same world, mutual: 10% less damage (`BuddyDamageMixin`, a `@ModifyVariable` on `PlayerEntity.applyDamage`, before armour) and the half pick-up. When a buddy is down (human or hybrid) the other gets `FormEventPayload.BUDDY_DOWN` every 10 ticks and `TeamsHud` draws an arrow on a ring round the middle of the screen, a label with the name and distance (it times out 30 ticks after the last). `Buddies.pair(a, b)` is the hook for the Public Safety office. `/chainsaw` is now open to everyone for `buddy` and `giveup`; every other subcommand is operator only as before (`ChainsawCommand.OP`).
- **The heart's rules** (`Hearts`, `TeamState`, `PochitaPlace`): `Hearts.update` (every 40 ticks, and called by the tests) records who holds a heart and when they were last in the game (`TeamState.seen`), takes the nearest waiting den for a new holder (`PochitaPlace.taken`), sends back the heart of anyone away `heartReturnDays` in-game days (24000 world ticks each) and makes the world have `chainsawHearts` dens. `PochitaPlace` now keeps the first den as before and more in `extras` (`dens`, `allDens`, `waitingDens`, `taken`, `returnHeart`, `ensure`; saved). `returnHeart` makes over a *taken* den as a new one at least `FAR_BACK` 2000 blocks from where it was (candidates picked by biome from the biome source, which makes no chunks, woods first; only 6 are loaded to see if the den fits; at least 1000 blocks from every player and 600 from every other den), so the world still has the same number of Pochitas. **Giving up**: `/chainsaw giveup` asks (a clickable line), `/chainsaw giveup confirm` within 30 s does it (`Hearts.giveUp`: `strip` = Beam, frenzy and form ended, `Heart.take`; their slots are free because a hybrid holds no contract). **Absence**: the heart goes back the same way (`TeamState.returned`); they are human with a line when they next join (also at the join itself if the days have passed). **Theft**: only with the rule on, only by whoever dealt the blow that downed the hybrid (`Downed.downedBy`, kept from `Downed.down(player, source)`; a mob's blow is no one's), only a player who has no heart: the same hold as the cord revive (crouch, hold use) tears for `THEFT` 100 ticks instead of pulling the cord (`Downed`'s use callback asks `Hearts.theftHold` first). The hybrid mashes jump: each press (the client reads the key itself because a downed hybrid's keys are swallowed) is `TeamActionPayload.STRUGGLE`, which takes `STRUGGLE` 2 ticks off (one press a tick counts), so a press every tick stalls it; any hit on the thief (a damage with an attacker) cancels it; a stolen heart cannot be stolen again for `SETTLE` 24000 ticks (`TeamState.stolen`). On success (`Hearts.steal`) the thief is a hybrid (`Heart.give`: their contracts end, blood at least `Blood.WELL`), the old hybrid is human with their slots free and, down, falls as a human (`Fallen.fall`; with `everyoneGoesDown` off they get up with 4 hearts).
- **The legend** (`Legend`): every time a hybrid pulls the cord (`Form.pullCord`, not the friend's cord revive), each other player in that dimension gets `FormEventPayload.LEGEND` (position, 1200 ticks) and a quiet roar from his direction 12 blocks off (`legend.roar` in `sounds.json` is the engine's start, `form_start`, played at volume 0.55 and pitch 0.7 by a sound packet; no new sound file). `TeamsHud` draws a gold arrow and "CHAINSAW MAN 27 m". `Legend.markerFor` is the server's record of what it sent (for the tests). As a human he is not shown.
- **Look** (client): a fallen human lies face down, head up, propped on the left forearm with the right arm reaching on, a leg drawn up (`FallenPoseMixin`); the helper's right arm reaches down to them (`ReviveCord.shoulder` and `angles`, the cord revive's own) leaning in; for a heart, blood sprays from the chest, more as it goes, and a burst when it is out (`ClientTeams.tick`). `TeamsHud`: DOWN, the time, a bar, the key to give up, who is picking you up and a green bar; a red bar and "Someone is tearing out your heart! Mash Space!" for a hybrid being robbed; a bar under the crosshair for the one lifting or tearing. The screen's edges go red, closing in as the time runs out (`ClientCombat.vignette`).
- **Tests**: `TeamGameTests` (23): a human goes down and cannot attack or use anything, the timer runs out to death, hits shorten it (not twice in a breath; a last hit kills on the next tick), giving up, the void and `/kill`, the rule off, hybrids unchanged, the pick-up with 4 hearts and no cost, letting go cancels and a second pull finishes, standing or far does not lift, buddies ask/accept/one at a time/end, 10% less near and the whole far, the buddy's half-time pick-up, buddies over a relog (and ended while away), giving the heart up (human, slots free, a new den 2000+ blocks off, the same number of dens), absence (rule off, not long enough, long enough; human on joining), theft off by default, theft only for whoever downed them (not a friend, not after a mob, not for a hybrid), the tearing out (the hybrid changes, slots, falls as a human), the struggle and a hit, a day before it can change hands again, `chainsawHearts` 2, the legend's marker only on the cord and only to others. `FormGameTests.pochitaKeepsYouAlive` now turns `everyoneGoesDown` off for the part where a human without Pochita dies. **202 game tests in all (179 before), 23 of them new; in four full runs `TeamGameTests` passed every time, and each full run had one failure, a different known load-sensitive beam test each time: `formgametests.beamburstsupunderamob`, `beamswimswhenmovingandriseswhenstopped`, `beamswimsunderawallwhennothingmaybebroken` (the first run's one failure, `gapgametests.liveevent`, was real: the Gap's own kills must not leave a player down, so `Fallen`'s `lethal` is the void, `/kill` and whatever is tagged `bypasses_invulnerability`, as the Gap's `swallowed` and `erased` are). One run of a `TeamGameTests` test (`nearabuddyyoutaketenpercentless`, the stranger's hit) failed once in an early run, then passed in all later ones: unexplained.**
- **Stills**: `./gradlew runSelftest -Pselftest=teams -Pusername=Kortev -Pwidth=1080 -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"` (`TeamsSelfTest`, its own class, registered in the gametest `fabric.mod.json`, `FilmSkins` and the frame/HUD mixins; about 3 minutes; stills land in `build/selftest/screenshots/teams_*.png`): the down with its timer, the pick-up, the arrow to a buddy, the legend's marker, the heart torn out (heartTheft on), the new den (the world finds the heart a place first, which can take a few seconds).
- **Numbers kortev might tune**: `Fallen.TIMER/HIT_COST/HIT_GAP/LIFT/LIFT_BUDDY/UP_HEALTH` and the crawl speed (-80%) in `Fallen.crawl`, `ClientTeams.GIVE_UP_TICKS`, `Buddies.NEAR/REDUCTION/ASKED`, `Hearts.THEFT/STRUGGLE/SETTLE`, `PochitaPlace.FAR_BACK/FROM_PLAYERS/FROM_DENS/APART/LOADS`, `Legend.LASTS/HEARD_FROM/VOLUME/PITCH`.
- **Unsure**: the lying pose is read from three stills only (the arm propping reads as an arm tucked under); the roar is the engine's start pitched down, not a made-for-it far-off sound; a downed human's mob handling (mobs keep hitting them, so a lone player near a husk dies in a few hits: kortev may want mobs to lose interest as they do for hybrids); the one scoreboard-team rule is extra (team mates get the action-bar line, not the buddy perks); chunk loading when a heart goes back (the biome-first search keeps it to a handful of chunks) was only timed in the test worlds; nothing was tried with a second real client; the Public Safety office, `heartTheft`'s bounty and a "legend on the map" beyond the minute's arrow are not built.

**Done (2026-11-09): the hotel, version 2 mechanics** (built by Sonnet 5.5 from `work/briefs/eternity_hotel_v2_mechanics.md` for the design in `QUEST_MOD_PLAN.md` sections 2, 3 and 6; kortev has not judged it; **the look of the devil and the hotel is untouched**: a separate agent makes the new look and a later round puts it in; stills of the mechanics are in `work/hotel_v2/stills/`, git-ignored). A devil that has you trapped does not bargain, and a price per use costs nothing if you never use the power; so:
- **No deal in the loop.** The offer after laps or a minute, its screen and its timeout are gone (`Hotel.maybeDeal`, `dealTick`, `Deal`, the lang lines, the `HotelGameTests` that served them, the old `LAPS_FOR_DEAL`/`FLOOR_TICKS_FOR_DEAL`/`answerTicks`).
- **The sealed hotel** (`Hotel.sealing`, `Run.sealed`/`inside`): the first player on the loop floor (feet at or above local y 28.8) closes the loop; everyone already in the building at that moment is `inside` (a friend still climbing floors 1-7 is looped on reaching floor 8 and is in the hunt, the squad and the beg). The hotel is sealed until it is over (the devil killed or spared (`Hotel.killed`), or nobody inside any more: `Hotel.open`). The entrance doors are shut every 10 ticks, anyone not inside (outside at the seal, or come in after it) who is found in the hotel is put out onto the plaza (`OUT`, local (27.5, 1, -3.5)) with the action bar line "The doors do not open.", players near the doors get it too, and left-clicking any block of a sealed hotel does not break it (`Hotel.init`: `AttackBlockCallback`). Someone inside who dies or leaves is pruned from `inside` (their drops stay where they fell) and cannot get back in. Hunger is normal and there is no spoiling of food. The loop teleport is as built (for `inside` players; suspended during the fight).
- **The hunt** (`Hunt`): when the loop closes the devil hides at a random wall block of the loop floor's corridor (local x 15-23, y 29-31, z 4 or 8; the rooms behind those walls are its "room wall"; the doors of the top floor stay shut). Signs: its **heartbeat** (`hotel_heartbeat` at the spot inside the wall, every 30 ticks, 22 within 7 blocks, 16 within 3.5: the game's own distance makes it louder), **14 eyes in the wallpaper** (`FleshGrowthEntity.EYE`, 7 a side; open within `EYE_RANGE` 5.5 of the spot, shut for `EYE_SHUT` 30 ticks after a player looks within 9 degrees of one: `Hunt.tick` every 4 ticks), and the wall at the spot **breathes** (`FleshGrowthEntity.BREATH`, a swelling lump, drawn by `FleshGrowthRenderer`). **Cutting** a wall block of the loop floor (`Hunt.cut`, from the left click, `Forever.use` on a wall, anything that calls it; the saws chew through blocks by the same click) bleeds if the spot is on the same wall within `BLEED_RANGE` 2.0 blocks (blood particles, the block red for 50 ticks, `hotel_cut`, `BLEED_DAMAGE` 8 off the hidden devil's health) and is `hotel_knock` and dust if not. It moves to a new spot (at least 5 along the wall, or 3 on the other wall) when someone lingers within `CLOSE` 2.2 blocks for `LINGER` 40 ticks and at the first bleeding cut at a spot, **at most `MAX_MOVES` 2 times** in all; after `CUTS_TO_TEAR` 3 bleeding cuts at a spot it tears out and the fight starts there (`Hotel.startFight(world, h, at, x, health)`: the devil's middle at local x clamped to 20.5-23.8 so it does not plug the stairs, with the health the cuts left it; players east of it are put in front of it; the walls bleed round it). The boss is the existing one (`EternityDevilEntity.baseX`).
- **The squad** (`Squad`, `SquadMemberEntity`, `SquadRenderer`): three hunters of our own, **Yamada, Mori and Sato**, sit starving against the loop floor's walls (x 17.7, 19.6, 22.3) in Public Safety suit skins on the player model (`textures/entity/squad_0-2.png`, tints of `tools/skins/kortev_public_safety.png`), villager-sized when sitting, an iron sword in hand. Right-click one with any food: they get up and follow you (`FOLLOWING`), and in the fight they cut the devil (`KATANA` 6 damage, an iron sword's). Hit to death they are downed (`DOWN`, 2 health, untouchable, sitting) and fed again they get up at 60%. One of the three (random) is mad (`isMad`): he attacks players on sight (`MAD_DAMAGE` 3, shaking hands) until fed or knocked below 40% of his health (`CALM_BELOW`), then follows whoever fed or hit him. **Notes** (`Squad.NOTES`): seven written books on lecterns on the bedside-table slab of the back room (x 10) of floors 1-7 (never in the corridor), and the eighth in Yamada's pocket (dropped when he is fed), one line each, the squad's days with the hints "it watches from the walls", "it bleeds when you cut it", "the heartbeat is louder by room 805" (the spot is random; the hint is only a hint). **Reward**: when the loop breaks (`Hotel.killed`) each rescued (fed) survivor walks into the stairwell and is removed only once no player can see them (`LEAVING`, or after 450 ticks if someone keeps watching) and every player inside gets `Squad.REWARD_XP` 150 and `REWARD_GOLD` 4 gold ingots per survivor (**`Squad.reward` is the hook for Public Safety's yen and rank**).
- **The beg** (`Hotel.beg`/`resolve`/`answer`, `EternityDevilEntity.startBeg`/`giveUp`/`spare`): at `BEG_AT` 15% health it stops attacking, cowers and cannot be hurt, a blow that would take it past the line stops there; its words ("Let me live. I'll give you forever.") are on every screen inside and each player inside gets the contract screen (`ContractScreen`: the devil's name, the power, **the lasting price** "You can never sleep through the night again", **the price each use** "Time catches up with you: it eats your hunger", Accept / Refuse, `Hotel.BEG_TICKS` 200 = 10 s; a hybrid's screen has Accept greyed out and "Your heart leaves no room for another devil"). `oneContractPerDevil` is checked at the answer. If anyone accepts each accepter holds the contract (`Contracts.grant`; with all slots full they are asked which to give up first) and the devil is spared: it tears off part of itself (the collapse, 3 flesh and 2 blood for everyone) and sinks away for `devilRespawnDays`; if nobody does (or time runs out) it fights no more (`broken`, no attacks, no regeneration) and can be killed for the same loot. Either way the loop breaks and the hotel opens. With only hybrids inside the beg lasts `BEG_QUIET` 60 ticks.
- **Contracts: three slots** (`Contracts`): `LIST` (ids, so a contract this mod does not know keeps its slot) and `SELECTED`; `grant` gives `GRANTED`, `HYBRID` (the heart fills all three) or `FULL` (then `replace(slot)`; `DropScreen` is the player's choice), `cycle` is the new key **O** "Switch contract" (U uses the selected one), `has` looks in every slot. Saves from before: the single old attachment `HELD` is read as the first slot and moved over (`ids`).
- **Eternity's new prices** (`Forever`, `EternityBedMixin`, `EternitySleepVoteMixin`): lasting, while you hold it: beds do not work (action bar "Eternity keeps your nights"; a bed still sets your respawn; the time since rest keeps growing, so phantoms come as in vanilla) and you do not count toward the sleep vote (the vote's `SleepManager.update` treats a holder like a spectator, so the others skip the night). Each use: **no XP any more**; after the release `Forever.catchUp` takes `HUNGER` 3 (saturation to 0) and `ForeverCatchUpPayload` keeps the grey drain on you `CATCH_UP` 22 ticks (`ForeverHud.catchUp`); a use at 0 hunger is refused with "There is nothing left of you for Eternity to eat."; free in creative.
- **Numbers kortev might tune**: `Hunt.BLEED_RANGE/BLEED_DAMAGE/CUTS_TO_TEAR/MAX_MOVES/CLOSE/LINGER/EYE_RANGE/EYE_SHUT/LOOK_COS/OUT_X0/OUT_X1`, the heartbeat gaps in `Hunt.tick`, `SquadMemberEntity.KATANA/MAD_DAMAGE/CALM_BELOW`, `Squad.REWARD_XP/REWARD_GOLD/SIT_X`, `EternityDevilEntity.BEG_AT`, `Hotel.BEG_TICKS/BEG_QUIET`, `Forever.HUNGER/CATCH_UP`, `Contracts.SLOTS`.
- **Tests** (HotelGameTests 34, ForeverGameTests 32 (were 24 and 23); 179 game tests in all (163 before); three full runs each had one or two failures, all in tests of other mods or the form: formgametests.aimedhitsland and domaingametests.wholedomain in the first, formgametests.beamwalksonhislegs in the second, formgametests.beamburstsupunderamob in the third (the known load-sensitive flakes; every hotel and Forever test passed in all three)): `HotelGameTests` (nothing is offered in the loop, the hotel seals, a player who dies inside cannot get back in and it opens when nobody is inside, the devil hides and shows itself, cutting near it bleeds and away from it does not, it moves at most twice and tears out after 3 bleeding cuts, feeding makes them follow, the mad one, a downed one fed again, the rescued cut the devil, survivors reward everyone inside and leave, the notes, the beg line and invulnerability, accept spares it, nobody accepting lets it be killed, a hybrid cannot accept, `oneContractPerDevil` at the answer, a full hand asks which to drop, and a whole run with a plain player and survivors and no Chainsaw Man), `ForeverGameTests` (three slots and the fourth asking which to drop, an old save, each use costing 3 hunger after the release and 0 hunger refused, beds refusing a holder, a holder not blocking the night skip). **Hotel tests got their own slots** (`hotel(context, rot, slot)`: 30 to 42 are new; two tests sharing a slot overwrite each other's hotel) and the walkers are armed (the mad hunter hits).
- **Stills**: `./gradlew runSelftest -Pselftest=hotel -Ptarget=hunt -Pusername=Kortev -Pcapture=true -Pwidth=1080 -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"` (`HotelSelfTest.huntLooks`: the sealed door, an eye, a wall bleeding, a survivor sitting and following, the mad one, the tear-out, the beg screen, the loot after a spare). The old clip targets (`accept`, the default) filmed the deal and are obsolete until the new look is in.
- **Unsure**: players on a lower floor at the moment the loop closes are put out (not "inside"); the hint "louder by room 805" is only flavour; the devil's cower is only that it stops (no model change: the look is another agent's); the closed eyes are not drawn at all; heartbeat and cut sounds were checked by their levels only; the loop is not tested with a real second client; the squad's walk to the exit is a walk to the stairwell then a poof, not down eight floors.

**Done (2026-11-07): the Eternity Devil's hotel, its loop, its deal and its boss fight** (the deal, the offer's timing and the begs at 25% and 10% described below are replaced by the version 2 mechanics above) (built by Sonnet 5.5 from `work/briefs/eternity_hotel.md`; kortev has not judged it; clips `work/hotel/hotel_clip.mp4` (the refusal, 49.4 s) and `hotel_accept.mp4` (accepting, 25.0 s), 1080x1920, game sound, no captions, a `*_contact_sheet.png` each, stills in `work/hotel/stills/`, all git-ignored). The show's world, not its story: the hotel, the loop and the devil are the show's; the quest, the offer and the fight are ours.
- **The hotel** (`hotel/HotelPlan` is the plan, `HotelBuilder` puts it down, `HotelStructure` is the worldgen structure, `/chainsaw eternity` places one in front of you facing you): 38 x 14, eight storeys of 4 blocks, a lobby with a front desk, a bell and a lift that does not work, a stairwell (a real switchback, checked by a walk in the tests), eleven rooms a floor, signs, chains, a plaza and floodlights. Its home is an invisible block (`HomeBlock`, `HomeBlockEntity`) buried under the lobby that runs everything (`Hotel.tick`); it remembers where the hotel stands and its turn, whether its devil is away until which day, and who holds its contract. Worldgen: structure `chainsawman:eternity_hotel`, set `random_spread` spacing 80 / separation 36 chunks, biomes in the tag `has_eternity_hotel` (plains, sunflower plains, meadow, taiga, the old-growth taigas, savanna), never on water (heightmap check) or where the nine sampled heights differ by more than 4 (`HotelStructure.STEEP`). Never seen generated in a real world (only through the game's own structure placement in a test): look for one with `/locate structure chainsawman:eternity_hotel`.
- **Day and night**: dead by day (dark windows, doors shut and chained, iron entrance doors that cannot be opened from outside, a button inside each); at night with a player within 96 blocks (`Hotel.NEAR`) and the devil not away: lobby and about four in ten rooms lit (clear glass and an invisible light block), doors open, chains gone, a hum in the lobby (`Hotel.tick`, `HotelBuilder.setNight`). Floors 2 to 7 each have one wrong thing built into the plan (a door number repeated, a painting that comes again, a stain on the carpet, an eye in the wallpaper) and floor 6's door breathes, floor 7's phone rings (server sounds).
- **The loop** (floor 8): the corridor repeats every 12 blocks (`HotelPlan.PERIOD`), walking out of the window `[13, 25)` puts you back 12 blocks with `requestTeleport` and every axis relative (so your speed and look carry across and lag does not matter: `Hotel.shift`); the stairs down put you at the corridor's start; the ends are hidden in a close fog (`HotelFogMixin`, `Hotel.FOG_END` 11). Each lap adds flesh at the corners (drawn flesh entities, `FleshGrowthEntity`: level 1 to 3), flickering lamps and louder whispers. Several players are looped separately.
- **The deal (removed 2026-11-09: see above)** (`Hotel.maybeDeal`, `dealTick`, `answer`; `ContractScreen`, `HotelHud`): after 3 laps or 1200 ticks on the floor the devil speaks in three lines over the screen (written over themselves like many voices) and the contract screen opens (ticks 0, 26, 54 and 84; 20 s to answer, `Hotel.answerTicks`). Accept: `Contracts.grant` (ends any other contract), the loop opens for that player, the hotel's holder is set, and when everyone has answered the devil is away `devilRespawnDays` days. Refuse, a hybrid (a line from the devil) or silence: the fight. `oneContractPerDevil` on and the holder still holding it (`holderStillHolds`): no offer, the fight. Game rules `oneContractPerDevil` (false) and `devilRespawnDays` (3).
- **The fight** (`EternityDevilEntity`, `FleshPatchEntity`; `EternityDevilRenderer`): the walls bleed (`HotelBuilder.bleed`) and it grows out of them (80 ticks, hurt by nothing meanwhile) at the corridor's east end, 4.6 x 4.0, 300 health, boss bar. Its attacks come in turn (bite, tendril, bite, creep, tendril, bite) every 46 ticks (34 below half health, 24 below a quarter): a mouth rises from the floor or the nearer wall and bites where you stand after 24 ticks (7 damage), a tendril rears and slams down a lane between it and you after 30 (9, shoves you back), flesh patches creep along the floor (slow II, a bite of 3 after a second on them). It regenerates 3 health per half second after 80 ticks without a hit. The heart (in its big mouth) shows below 55% health and is covered again above 70%; a hit whose look passes within 0.9 of it does three times as much. It begs at 25% and 10%. Its eyes are low (the top of its box is in the ceiling) so the saws can see it. Killing it: a 70-tick collapse, then 3 glowing Eternity flesh and 2 Eternity blood where it stood; the loop breaks at once (`Hotel.killed`: flesh and bleeding gone, no loop, the lights die over 160 ticks, the hotel dark again), the devil is away `devilRespawnDays` days, and no contract comes of it. Dying in the fight is the usual death (or `Downed` for a hybrid).
- **The devil's look, v4 (kortev approved 2026-11-10, merged from the look branch):** painted first as pixel art at true game resolution (`tools/eternity_devil_concept.py`, composition B "the avalanche": flesh pours from the torn roof into one great maw of human teeth with the heart inside, 23 eyes; renders in `work/eternity_look_v4/`), then built box for box: `tools/eternity_devil_atlas.py` turns the concept's export into `EternityDevilLayout.java` and the 704x512 atlas `src/main/resources/.../textures/entity/eternity_devil.png` (the only copy; an old v3 one under client resources was removed). `EternityDevilRenderer`: about 136 half-block lobe boxes whose sides are cut from their own front's edge strips, 23 three-box eyes (tracking the nearest player, blinking on their own timers, none under 6 texels), the maw cavity with two-box teeth that part over the heart when it is out, twitching small mouths, swaying lobes, warm light on the lower lobes. **The arena** (`hotel/Arena.java`): when the devil tears out it rips open x 23-36, z 1-12, y 29-35 (the rooms' walls, the ceiling and the roof, to the night sky) with sparks, dust and falling debris, grows flesh on the stumps, and puts every block back exactly after a kill, a spare, everyone leaving, or a restart mid-fight (tests in `HotelGameTests`); it always tears out at the corridor's end. Film: `-Pselftest=hotel -Ptarget=devil` (stills; clouds off, a bright moon at tick 14500) and `-Ptarget=devilclip`. Two failed looks before it are history: `work/eternity_look_v2/` (a smooth Blender model) and `work/eternity_look_v3/` (600 random boxes). Weakest: a stepped outline from the side, the burst's sparks reading as white dots, the teeth a little shard-like close up.
- **The shaft fight (2026-11-09, branch `shaft-fight`, built by Sonnet 5.5 from `work/briefs/eternity_shaft_fight.md`; not yet judged by kortev):** the fight now has two phases. *Corridor*: as before (the devil torn out of the wall at the east end, arena open to the sky), plus a **fear** meter (`Shaft.State.fear`): it fills `Shaft.FEAR_TICK` 1/1200 a tick (a minute on its own), +0.06 each time a player is hurt, +0.2 each time one is downed, and at 1.0, no sooner than `TILT_MIN` 800 ticks (40 s) into the fight, **the floor turns**. *The turn* (`Shaft.startTilt`, 44 ticks): the camera rolls a quarter turn with a growing shake (`ClientHotel.roll/shake`, `TiltCameraMixin`, priority 2000 so it also rolls the test's camera), a deep groan (`devil_grow` and `hotel_loop_break` pitched down, a warden roar), the devil's words "Down. Down. Down.", everyone slides toward its end; then `Shaft.enter` builds the **shaft as real blocks** in a reserved space above the roof (local y `Shaft.BASE` = 42 up to 80; `Shaft.design()` is the whole plan, a map of local cell to state): the corridor stood on its end through plan sampling (a corridor cell x is at y `BASE + 36 - x`, across stays z, the corridor's height runs x 17-21: the carpet as red/brown/yellow wool is one wall, the ceiling slab with its lit lamps the opposite, the wallpapered corridor walls the other two), 3 x 3 inside at x 18-20, z 5-7, the room doors opened into nine **alcove ledges** (3 wide, 3 deep, 3 high, a lamp in the roof; `Shaft.ledges()`), a chamber at its foot (x 14-26, z 3-9, 5 high, walls of the arena's flesh blocks, shroomlight in them) whose 6-thick floor is the **mass**, with the **mouth's pit** in it (x 23-25, z 5-7, 4 deep, shroomlight at the bottom) kept 3 blocks east of where a fall from the shaft lands. Every block it replaces is kept in `HomeBlockEntity.shaft` (the same cell format as `Arena`, `Arena.write/read`) and put back by `Shaft.mend` after a kill, a spare, everyone leaving (`endFight`) or a restart mid-fight (`!r.started`); mending puts players, dropped items and squad members in the corridor (local 30.5, 29, 6.5) first, and the loot drops on the mass just before. If the world is too short above the hotel (`getTopY`) it does not build and the fight stays in the corridor (fear back to half). The sky arena is mended at the turn (`Arena.mend`), the flesh patches go. Each player lands in a doorway (`Shaft.place`, local x 19.5, the ledge's y, z at the wall, looking across and 40 degrees down; everyone on a different ledge, from the upper middle), `Hotel.tick`'s inside box grows to `Shaft.TOP` while built, and `Hotel.sealedAt` includes the shaft's space (its blocks cannot be mined). Falls in the shaft do no damage (`fallDistance` reset each tick: it is the mass that hurts).
  *The devil at the bottom*: `EternityDevilEntity.enterShaft` puts it at local (27, BASE-4, 6.5), tracked `SHAFT`, its box 13 x 5 x 7 over the whole chamber floor and the pit (not collidable: players stand and fight inside it), `ShaftMassRenderer` draws lips and teeth round the pit. Its attacks are run by `Shaft.shaftTick` (small, quick, nothing telegraphed): **heads** (`ShaftHeadEntity`: come up under a player on the mass in 6 ticks, bite at 8 for 5 and throw them a little toward the pit, 8 health, a head every 24-37 ticks, at most 4), **arms** (`ShaftArmEntity`: rise up the middle at 0.85 a tick, hand reach 2.9 across so it takes someone standing in a doorway, 4 damage, drag them down at 0.55 a tick by setting their velocity, let go on the mass; 14 health, hit it to break free; one every 60-94 ticks while someone is on a ledge, at most 2 + ledge players / 2), and the **flesh patches** (every 120-159 ticks, near someone on the mass). Blows from a player standing on the mass are worth `MASS_BONUS` 1.5 times as much (dropping down is the strong, risky play); ranged attacks, the chain and Beam from the ledges are normal.
  *Swallowed*: a player in the pit (below its rim, over its cells) is swallowed (`Shaft.swallow`): 1 damage every 10 ticks (not while downed: the usual downed rules apply and a buddy can reach them in the pit), the screen goes dark red (`Devil.SWALLOW` payload, `HotelHud`), and the damage they deal to the devil counts toward cutting out (`Shaft.cutHit`, called from `EternityDevilEntity.damage`): `CUT_NEED` 36 (the saws' 13 a slash: three; a sword's 7: six; fists: many); then they burst out (teleported to the rim and launched up and west onto the mass, not swallowable again for 30 ticks) and the devil takes `PAIN_BURST` 30. A beg lets everyone out.
  *Endurance, not health*: damage cannot kill it. The health it loses is **pain** (`pain()` = (300 - health) / 255, the boss bar fills with it, purple, "Eternity Devil: its pain"), it eases as before (3 every half second after 4 s unhit), and at full pain (the old 15% line) it stops, pushes up its **core** (`ShaftCoreEntity`, the figure-8: two lumps with an eye and a mouth each, over the pit; in the corridor form at the heart's place) and begs: the existing beg and contract flow, unchanged (`Hotel.beg`, the leverage, `spare`). It cannot be hurt while it begs and damage never kills it after. If nobody takes the deal (`giveUp`) the core can be cut (40 health: saws about four slashes, a sword six) and cutting it in two kills the devil (`cutCore`: the existing kill, loot, loop break). The heart that showed at 55% only shows in the corridor form. `Run.shaftOn` (default true) turns the whole turn off: **the older tests set it false** (`hotel()` in `HotelGameTests`) and fight in the corridor; the shaft tests turn it on.
  *Tests* (`HotelGameTests`, 10 new, 49 hotel tests now): fear fills and the turn waits for 40 s (`fearFills...`), the turn builds every block of the design, puts the player in a doorway looking down, the devil's box on the mass and pit, no sky arena, a kill puts every block back and the loot and player in the corridor (`theFloorTurns...`), restart mid-fight (`theShaftIsPutBack...`), swallow/steady damage/saws in three/sword in six/pain burst/no re-swallow (`theMouthSwallows...`), pain fills, damage cannot kill, the core is offered and cut (`painFills...`), the deal at full pain spares it and the shaft goes (`takingTheDeal...`), arms grab, hurt and let go, and hitting one breaks the hold (`anArmTakes...`), heads bite (`headsBite...`), and a plain player with a sword alone wins (`aSwordAlone...`). The old tests that killed it by damage now cut the core (`slay`). **Film**: `-Pselftest=hotel -Ptarget=shaft` (`HotelSelfTest.shaftClip`, one take from your own eyes with the HUD on: corridor, the turn, the arrival, the look down, an arm, on the mass, swallowed, the saws' three blows, full pain, the core, the cut). Not verified: two real clients, the arm's drag and the swallow's hold by real client physics (the test players do not move), play by hand, the look of the placeholders (the 3D look job replaces `ShaftArmRenderer`, `ShaftHeadRenderer`, `ShaftCoreRenderer`, `ShaftMassRenderer`).
- **Loot**: `ChainsawMan.ETERNITY_FLESH` (10 hunger, nausea 15 s, always edible), `ETERNITY_BLOOD` (`EternityBloodItem`, drunk in 32 ticks: `Blood.set(MAX)`, the bottle back); textures from `tools/gen_hotel_textures.py`.
- **Sounds** (`tools/gen_chainsaw_sounds.py`, all synthesised): `hotel_hum` (loops), `hotel_whisper`, `hotel_voice` (a crowd speaking), `hotel_bleed`, `hotel_phone`, `hotel_breath`, `hotel_loop_break`, `hotel_seal`, `devil_grow`, `devil_bite`, `devil_slam`, `devil_regen`, `devil_hurt`, `devil_death`.
- **Numbers to tune**: everything named above (`Hotel.NEAR`, `LAPS_FOR_DEAL`, `FLOOR_TICKS_FOR_DEAL`, `SPEECH_AT`, `OPENS_AT`, `answerTicks`, `FLESH_DROP`, `BLOOD_DROP`, `DYING_TICKS`; `EternityDevilEntity.MAX_HEALTH`, `GROW_TICKS`, `REGEN`, `REGEN_DELAY`, `HEART_SHOW/HIDE/MULT/RADIUS`, `BITE_DAMAGE`, `TENDRIL_DAMAGE`, the attack gaps in `attacks`; `HotelStructure.STEEP`, the structure set's spacing).
- **Tests** (`HotelGameTests`, 24): placed whole at every turn, a walk from the street to floor 8, in the world's generation, the game places the structure itself, night and day (doors, chains, lamps, windows, lights), the wrong things heard, the loop at all four turns (position across and height, look and speed kept, laps, a freed player, another floor, the stairs, the flesh and its going), the deal after 3 laps and after a minute, accepting (older contract ended, loop opened, devil away), a hybrid refused, refusing and silence, `oneContractPerDevil`, two players answering differently, the devil (growing, regenerating, the heart, its attacks, the saws cut it and it falls slowly), the kill (loot, no contract, loop gone, lights dying), the flesh and the blood, the devil back after the days (and each hotel its own; the respawn clock is game ticks, `awayUntilTick`), the deal offered again when the devil is back, one contract per offer, the laps forgotten off the floor. Review fixes: `dealt` cleared, a leaver who accepted still sends the devil away, riders put off before a teleport, hotels only wake/change when all their chunks are loaded, home blocks only registered in a server world. `later()` in the tests adds to the tick map after the tick (nested runAtTick can crash the test server). The full suite (165 tests) shows a random Form/Gap flake in about half the runs (beamtearsthroughawall, liveevent...): it passes alone and is load/world-state sensitive. The tests of a batch run at once, so each has its own 300-block slot and the devil tests wait for their chunks to tick (`ready`, `later`). `-Ponly=hotel` runs just them. **163 game tests in all** (141 before), all passing in the last three full runs; earlier full runs with heavier hotel tests had one to two flakes each, all of tests that depend on tick timing (`beamTearsThroughAWall`, `sawsFellTrees`, `beamWalksOnHisLegs`, `aimedHitsLand`, `aCubeHoldsAtMostEight`, once my own `silenceStartsTheFight`): load from the hotel tests running beside them, cut by dropping the clearing of the neighbourhood and making the deal tests wait for their chunks; each of those passes alone.
- **Filming**: `./gradlew runSelftest -Pselftest=hotel -Pusername=Kortev -Pcapture=true -Pwidth=1080 -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"` (`HotelSelfTest`, its own class; the shared mixins and `FilmSkins` check `HotelSelfTest.active()`; about 10 minutes), `-Ptarget=accept` for the other clip, `-Ptarget=fight` for the fight alone, `-Ptarget=look` for 40 stills; then `MUX_EXCLUDE=music python work/tools/mux-audio-noprobe.py build/selftest/capture out.mp4` and `-af volume=3dB,alimiter=limit=0.97`. `work/tools/sheet.py` makes contact sheets and `work/tools/clipsheet.py` the start, middle and end frames of every shot. The site is a plains found once (`SITE`, `GROUND` 62: the ground is fixed, not looked up, or each take would be built on the last), levelled; the hotel is the real structure code. Staged: the site, you put on each floor of the climb (the stairs are real and were walked in the tests), the devil's health set to 150 for the fight (so it ends in a clip's time), yours kept up, kortev a hybrid in the main clip (Denji refusing, pulling the real cord key) and human in the accept clip. Every shot's camera is at least 1.5 blocks off anything, 1.2 to 2.5 high over the floor, near level.
- **Unsure**: the walls' bleeding is blocks of red terracotta, pink terracotta and nether wart (it reads as blood-stained wall, not as flesh); the heart in the mouth is small in the clips (the fight is shot low from the side, three-quarter front); a second player sees the first one blink across the loop (their own view is seamless); shader packs may ignore the corridor fog; the contract screen's font is the game's; the first-person glance in the loop shot turns the head 50 degrees to the sign; sounds were checked by their levels and spectra only; multiplayer, a real survival fight and generation in a real world are untested.

**Done (2026-11-07): a friend pulls your cord to bring you back** (built by Sonnet 5.5 from `work/briefs/cord_revive.md`; kortev has not judged it; from @é o gui's comment, kortev: from either form, straight back as Chainsaw Man). Clip `work/revive_reel/revive.mp4` (17 s, no music, mean -17 dB), `contact_sheet.png`, `stills/`, all git-ignored.
- **Feature**: a downed player (`Downed`) with Pochita's heart; a friend crouches and holds use for 3 s (`FEED` 60, pays `GIVEN` 6 health as before). `Downed.feeding` now starts a pull (`FormEventPayload.REVIVE`: value = puller's entity id, 0 let go, -1 yanked; sounds `form.revive_pull` 3 s, stopped on let-go). At the end `Downed.revive`: `up(down, true)` (no gulp sound) then `Form.revived`: transformation from `FormTimeline.REVIVE_SKIP` (10) via `FormPullPayload(player, skip)`, no blood spent, sound `form.revive_start` (the start sound from tick 10 with a rope crack and wet spray; made in `tools/gen_chainsaw_sounds.py`). Form on at the burst 10 ticks later, roar at 26. Alone: unchanged (human). Having been in the form or not makes no difference (going down ends the form).
- **Look** (client, `ReviveCord`, `FormFeature.reviveCord`, `FormPoseMixin`): the friend's right arm aimed at the cord's root (slid out to the body's near edge: the downed lie face down) then drawing up and back over the 3 s (`draw`); cord drawn root to hand in 10 pieces, slack and sagging then taut and humming (`taut`), handle at the hand; smoke at each cough of the sound (`COUGHS`), blood drips; let-go slides it back in 6 ticks; yank snaps it in 3 and sprays blood at chest and limbs; small camera shake; `DreamHud` yields while the cord is held. The downed player's own view of it is only the shake.
- **Tests** (`FormGameTests`): human revived to the form, form-then-down revived to the form, friend pays, letting go cancels (and a second pull finishes), alone stays human. **122 game tests, all passing** (119 before; no flakes seen in two runs).
- **The reel**: `./gradlew runSelftest -Pselftest=revive -Pusername=Kortev -Pcapture=true "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"` (`-Ptarget=bare` = no mobs, to look at the pull), then `MUX_EXCLUDE=music python work/tools/mux-audio-noprobe.py build/selftest/capture out.mp4` and `-af volume=2.5dB,alimiter=limit=0.97`. Own class `ReviveSelfTest` (registered in the gametest `fabric.mod.json`; also in `FilmSkins` and the `Reel*`/`FilmFrame`/lens mixins). A snowy taiga at dusk, snow falling (`locateBiome`, a round clearing levelled and snowed); Chainsaw Man (Kortev skin) against 3 husks and 7 strays (own AI, spawned on a ring; husks 8 health so a cut kills) with health held at 1.5 from tick 44, so the strays' arrows down him; the friend (`Yakuza1` skin) is a second `ServerPlayerEntity` (as in the game tests) moved along a route at sprint speed (`friendTick`), kneeling and firing `UseEntityCallback` every 4 ticks (what held use does); on the yank his health is set full and a third wave (9 husks, 2 strays) spawns; beats: fight (to ~2.6 s), friend runs in, pull (3 s), yank (own shot) then the mod's transformation camera, then the rampage and "What should I add next?". Cameras avoid mobs (`steer`, `blockage`). Staged: spawn spots, timing, health, the friend's route, cameras. 
- **Fix round (same day)**: the revive cord is now a tan rope with a dark handle (`textures/entity/revive_cord.png`, half-width 0.09), the friend leans back as he pulls; the reel's first wave is 4 weak husks close and 6 strays far off on the side away from the friend's approach; at the down the strays' bows are emptied and the first wave's target on the friend is cleared every tick (no hits in the pull), six slowed husks (slowness) close in from the far side during it (slowness removed at the yank), a low side shot by the chest is held (c 16-42), the downed body is shown alone about 1 s, and the friend steps back after the yank so the saws do not cut him. One stray arrow can still graze him now and then.
- **Pull motion (third pass, kortev: "weak, weird and long")**: a pull-start's short violent tugs, not a stretch. `ReviveCord.TUGS` ticks 20 and 40: the cord shoots out (3 ticks) and snaps back (2) to 0.4 then 0.5 blocks (root to fist), then the yank (the event) 0.8; between, the friend grips the handle over the chest, cord short and slack. `ReviveCord.grip` works out the fist (the arm swings back and up until the cord is that long; mixin poses the arm from it, `FormFeature.reviveCord` draws the transformation's own rope in 16 pieces to it); the friend's body rocks back with each tug (hardest on the yank), the downed body jolts (`jolt`), smoke puffs at each tug, a bigger blood burst at the chest on the yank, camera shake for anyone near (`shake`). `form_revive_pull` is regenerated as grip creaks, a tug and cough at 1.0 s, a harder tug and sputter at 2.0 s, tension into 3.0 s. The friend kneels beside the torso (0.55). The reel's low side shot holds through both tugs and the yank.
- **Unsure**: timing varies with the mobs (down came at tick 52 to 92 across runs; the clip is 16.5 to 17 s); a husk sometimes crowds the cord shots; sounds checked by their levels only; the first-person view of the friend (own arm not posed) and a real second client untested.

**Done (2026-11-07): Forever, the Eternity contract's power, replacing Echo** (built by Sonnet 5.5 from `work/briefs/eternity_forever.md`, kortev's storyboard `work/eternity_contract_look/forever_storyboard.png`; kortev has not judged it: clip `work/forever/forever_clip.mp4` (11.4 s, 1080x1920, game sound, no captions; second round: level open ground, the hero side shot of the whole line, lumps as clusters of lobes in deep reds, slow motion on the lock and the release), `contact_sheet.png`, `stills/`, all git-ignored). Echo (kortev rejected it) is gone: code, payload, swing mixin, shader, sounds, tests.
- **Round 4 (2026-11-07, kortev: "a cube of people, feel infinite, it glitches, work on players"):**
  - **A cube of frozen time.** U on what you look at (or the block, within 24) holds everything living in a 9x9x9 cube round it (`Forever.CUBE_HALF` 4.5), but you and your own tamed pets, the nearest `CAP_LOCKS` 8, each its own lock (line of copies, stored hits, release); they all end together (`Cube`: 60 ticks, 30 if any is a boss; players are never bosses). Same price (taken last, only if the cube was made), cooldown and immunity. `ForeverCubeEntity` + `ForeverCubeRenderer` (faint shimmering faces and edges), `csm_drain` now drains only what is inside the cube (the screen pass reads the depth, `Sampler1`, and the world matrices `ClientForever.projection/view` kept at `WorldRenderEvents.LAST`); particles hang still inside it (`ForeverParticleMixin`); the near-you drain is lighter.
  - **Infinite.** Each line carries on past its last real copy as render-only copies (`ForeverLayout.far`, 11 each way, about 24 blocks, spacing growing, paler and fainter with distance, skipped inside blocks or over a drop of 3+, not drawn beyond 48 blocks of the camera). The flesh is one low lump at the feet of each held thing and each near copy.
  - **Crowded cube, thinned (round 5, for 5 held things):** far render-only copies scale with the number of holders (`ForeverHubRenderer.farKeep`: 11 each way for 1-2, 6 for 3-4, 4 for 5+, spread evenly along the line, still fading to nothing at the end; the 4+4 hittable copies untouched); where another line's far copy stands within 0.8 block (and 1.2 up) the fainter one is skipped (`shadowed`); flesh lumps are shared out (`ClientForever.layout`: about 14 for the whole cube, at least 2 per held thing, nearest the middle first; lumps, eyes, mouths and cracks follow); the cube's edges are only a trace (alpha about 0.035) and fade to nothing within 3 blocks of the camera (full only past 3), the faces too within 3 blocks of their plane (`ForeverCubeRenderer`). Film: width/height 1080x1920 needed; the clip is now about 9.3 s. Game tests: 163 in all, all passing (one run hit a vanilla GameTestState crash and one a `formgametests.aimedhitsland` failure, both flakes; third run clean).
  - **The stagger, found.** Causes, in order of size: (1) a locked thing's limb animator was drawn from `pos - speed * (1 - tickDelta)` and its idle animation from `age + tickDelta`, so with nothing ticking it swept forward and snapped back every tick (fixed in `ForeverPoseMixin`: the limb position is moved by exactly that term, and the animation clock is the age); (2) a client entity whose tick is cancelled kept its old "previous" position/rotation, so it slid between the old and new values over and over (the lock payload now carries the frozen state and `ClientForever.freeze` sets position, facing, stride and the previous values once; the whole client `LivingEntity.tick` stops); (3) the server set no-gravity and NoAI on the mob (saved with it!) and pinned it every tick: now nothing is set on it (the freeze mixins do it) and the pin only acts if it was displaced. Measured by stepping 30 consecutive frames of a still camera on a frozen mob and a frozen player (`-Ptarget=jitter`, `work/forever/jitter_frozen_*.mp4`): the mean difference between frames fell from 2.5 to 13 to about 0.2 within a tick and about 1 across a tick boundary (looks identical to the eye; what is left is edge flicker where translucent copies overlap, not found).
  - **Players** are locked like mobs (held on the server: no move packets, no attack, no use, no teleport back unless displaced; on their own client no move, look, attack or use). Game tests: a cube holding several mobs and a player, the cap of 8 (nearest first), you and your pets left out, a cube on a block, a held player's stored hits going off, orphaned hubs removing themselves. A real two-client test through the multiplayer harness was not made (a server-side player stands in).
  - **Reviewer fixes**: no flags saved on the frozen; cooldown maps pruned; the price taken last and the setup wrapped (a failed cube leaves nothing); the lock whispers stopped only for players within 64 blocks; and for the cord revive (`Downed`): dying or respawning clears being down, the friend needs more than `GIVEN` health and pays with `setHealth`, one puller at a time, the stop sound only within 16 blocks, `up()` lets go of a held cord, the puller must be in the same world.
  - **Clip**: `ForeverSelfTest` now films four vindicators and the second player charging together, held in one cube (their lines receding, the cube from outside, kortev cutting through several, all released together, flung); `-Ptarget=jitter` for the still camera. Film in a worktree (`loft-forever`) when the main checkout's game is in use.
- **Contracts** (`contract/Contract`, `Contracts`; changed 2026-11-09: three slots, see the version 2 mechanics above): was one held at a time; a hybrid still can't hold one; `/chainsaw contract <player> eternity|clear`; key **U** "Use contract" (`UseContractPayload` to `Forever.use`).
- **Forever** (`contract/Forever`, server): human form and the Eternity contract only. Target = the living thing looked at within 24 (`RANGE`), else an action bar line and no cost. **Price (changed 2026-11-09: see the version 2 mechanics above; it was 3 levels, `PRICE`)**: now 3 hunger after the release. Cooldown 30 s from the release (`COOLDOWN` 600), a target can't be locked again for 3 s after (`IMMUNITY` 60). The lock lasts `LASTS` 60 ticks, `BOSS_LASTS` 30 for max health 100+ (and the Wither, Ender Dragon, Warden). Frozen exactly where it is, mid-air too: `ForeverFreezeMixin` cancels `LivingEntity.tickMovement` and `updateLimbs` for anything in `ForeverFlags` (server set and client set, told by `ForeverLockPayload`), plus NoAI, no gravity, position pinned; velocity, AI and gravity given back at the release. A locked player: `ForeverMoveMixin` (no move packets), `ForeverAttackMixin`, Fabric use/dig callbacks, client mixins for look and actions. Copies: 4 past (where it really was: `HISTORY` 40 ticks of positions kept for everything within 32 of any holder, real height used) and 4 future (navigation path, else its heading from the history, else facing), 0.9-1.2 apart, each on real ground or at its height over the ground, never in a block (`standable`, `fits`). `ForeverCopyEntity` is the hitbox (target's size): a hit on it, or on the target (`ALLOW_DAMAGE`), from a player (melee, arrows, anything with a player attacker) is stored (`Hit`) and not applied; the attack cooldown scaling is the game's own. Release: eyes shut and teeth bite (`ENDING`, `SNAP` 4 ticks), then one hit of total x 1.5 (`MULT`), at most 40 (`CAP`), credited to whoever stored the most (kill credit), knockback away from the user growing with the hits (max 2.6), burst, flesh sinks (`SINK` 10). User dying/leaving: the moment still ends; `/kill`, the target dying or leaving: everything cleaned up, nothing goes off.
- **Look** (client): `ForeverHubEntity` (one per lock, carries target/owner/phase/seed/marks), `ForeverHubRenderer` + `ForeverLayout` + `ForeverGeo` (one low lump, about a block across and half a block high, a cluster of lobes, at the feet of the held thing and of every moment of it, remade when the copies change: `ForeverLayout`, eyes with lids staring at the target, a lens of uneven teeth on every lump, jagged cracks, sinews winding up each copy's legs and the target's, the red slashes, the burst), `ForeverCopyRenderer` + `ForeverTint` (the target's own renderer in the copy's own stride and yaw, drained grey-green, fading by distance 1 to 4, dark rim, marks; snapping in streaked), `ForeverHud` + `shaders/core/csm_drain` (world drained to grey-sepia, flesh and blood keep colour; strongest for user and held; held player's edges darken and the devil's eyes stare in; flash at the release). Textures: `tools/gen_forever_textures.py` (palette and helpers of `tools/eternity_drawings.py`). Sounds: `forever_lock` (squelches, a crowd of 34 formant-filtered whispers, a deep clock that stops), `forever_hit`, `forever_release` in `tools/gen_chainsaw_sounds.py`.
- **Numbers to tune** (the bond numbers are in the 2026-11-09 bonds entry at the top): `Forever.PRICE/LASTS/BOSS_LASTS/COOLDOWN/IMMUNITY/MULT/CAP/SIDE/SPACING_*`, the knockback in `blow`; look: `ForeverHubRenderer` (`RISE`, `LID_OPEN`, mark size and count 14, sinew radius), `ForeverCopyRenderer.ALPHA/DRAIN`, `ForeverHud` strength by distance (0.9 near to 0.28 at 24).
- **Tests** (`ForeverGameTests`, 23: contracts, human form only and contract only, nothing in sight costs nothing, the lock holds a mob 60 ticks and frees it with AI and gravity back, mid-air hold, boss 30, hits on target and on a copy stored, a sword swing stored, two attackers and the credit, the 40 cap, the fling, the line back along the path and on ahead, a standing target still shows a line, copies never in blocks near pillars, the 3-level price, the 30 s cooldown, the 3 s immunity, a locked player can't move or attack, a kill cleans up, the user leaving, history kept only near holders). Test mobs with no AI don't move or fall at all (use AI on for physical tests). 133 game tests in all, all passing (two full runs, no flakes seen; `formgametests.aimedhitsland` failed once in the first run and passed in the rest, a flake).
- **The clip**: `./gradlew runSelftest -Pselftest=forever -Pusername=Kortev -Pcapture=true -Pwidth=1080 -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"` (`ForeverSelfTest`, own class; the shared mixins and `FilmSkins` check `ForeverSelfTest.active()`), then `MUX_EXCLUDE=music python work/tools/mux-audio-noprobe.py build/selftest/capture out.mp4` and `-af volume=3dB,alimiter=limit=0.97`. A fixed dark forest edge in the test world (-453, 2601) at sunset, a clearing taken out of the trees and levelled (gentle swells, natural at the rim). A vindicator (own AI, spawned 10 blocks off; recording starts once it is moving) charges along the ground; the real key U is pressed at 9 blocks (aim sighted first, key pressed again if nothing was in the sights); kortev (iron sword) runs along the line and cuts copies with `attackEntity` (the call the attack key makes); a second real server-side player (`Yakuza1`) cuts the far copy; the release; a ravager charges (brought in to 8-9 blocks if the terrain stops it), boss lock, release. After: the second player locks you and your own screen is photographed (first and third person). `-Ptarget=look` photographs the devil alone from all round. Shots (every one kept 1.5 blocks off lumps, copies and bodies, 1.2-2.5 high, within 25 degrees of level, see `ForeverSelfTest.frame`): the charge, three quarters on, the hero shot (three quarters down the line from just beyond its far end, eye height, the near copies big and the line receding), a close shot of three figures, you cutting from behind, the release wide in slow motion, pulled back. Staged: the ground, the ledge, the spawn spots, the cameras, kortev's health topped up so he is not killed before the end, the cooldown skipped for the second lock.
- **Unsure**: kortev's cuts on the vindicator are hard to see (the hover height and the camera); the boss beat's cuts depend on where the ravager charges from; the HUD eyes are round with a square pupil, not the square iris; the sweep particle of kortev's own sword can cross the lens at the release; the first beat is slow to start (about 2 s before the lock); sounds checked by their levels and spectra only; multiplayer, shader packs and a real survival fight are untested; copies of a target in armour/with held items use the target's own renderer so they carry them.

**Done (2026-10-21): Pochita's spot in the world, redone** (kortev: "this hollowed log looks like shit"; for the mod
itself, not for a reel). kortev picked **a big fallen tree** (over a den under a rock, or a junk pile), with **a blood
trail leading to him** and **signs of a fight** round it; they approved the second pass from photos.

- `PochitaPlace.den` builds it, used by `choose` (the world's own Pochita, placed once per world) and by `waiting`
  (`/chainsaw pochita`, `/chainsaw scene`): a clearing levelled and cleared in among the trees; the roots torn up at
  one end, standing on edge over the hole they left (packed dirt in the middle, mangrove roots through the ring round
  it, a ragged rim, hanging roots under); a round trunk (a plus-shaped slice) raised a block off the ground from the
  roots to near its middle, so he lies in the gap under it, then down on the ground to the break; moss carpet over
  it, vines down its sides; a jagged snapped-off end with bare oak fence posts as splinters (placed last with
  `FORCE_STATE`, or they join up into a fence); the crown down in a heap of persistent leaves past the break; broken
  branches, scattered leaves and streaks of coarse dirt (the fight); a trail of redstone-dust drops in to him.
- **Where** (`choose`): 32 tries 250-450 blocks from spawn for dirt ground in a forest, taiga, plains or meadow
  (`wooded`) that's level (`level`: 8 samples round the footprint, at most 2 more than 4 blocks off); if none,
  `woods` asks the world for the nearest such biome (`locateBiome`, 3200 blocks) from a point in that ring and looks
  round in it for a level spot (its nearest edge is often hilly); then any dry level spot from the ring; then spawn.
- **Old worlds**: the saved state has `Built` (0 for the old log) and `Axis`; on server start an old one is made over
  into the fallen tree once round the same spot (blocks only; Pochita stays), unless he's already someone's heart.
  kortev's survival world (`run/saves/New World (23)`, Pochita at x 123, y 118, z 379) will get this the next time
  it's opened: **not yet seen working** (the test world's Pochita is already a heart, so the test can't show it).
- **Photograph it** with `./gradlew runSelftest -Pselftest=reel1 -Pusername=Denji -Ptarget=den`: it uses the world's
  own tree if it has one, or builds one with `woods` near the test player (who moves 40 blocks on each run, since the
  test world keeps everything) and saves `build/selftest/screenshots/reel_den_*.png` (wide, along the trail, under
  the trunk, the roots, the break, the other side). The cameras stand inside the clearing: further out is in the trees.

**In progress: showcase reels** (2026-10-22). kortev judged reel 1 (the episode recreated, below) would lose viewers
long before its chainsaw part and dropped the idea: the reels are now **mod showcases**, one feature each, about
10 s, filmed tall (1080x1920) in a village at dusk, "CHAINSAW MAN MOD" in orange over every one, no music (kortev adds
a trending sound when posting). Only **what he does** (kortev: don't show the ult). The test is
`src/gametest/.../test/ShowcaseSelfTest.java`: `./gradlew runSelftest -Pselftest=showcase -Ptarget=<reel>
-Pusername=Kortev -Pcapture=true`, then `mux-audio.py` as below (stills: `build/selftest/screenshots/showcase_*.png`).
Done and approved: `transform` (the cord pulled among zombies, the combo, ends on "What should I add next?" for the
comments: kortev wanted that question on the one likeliest to spread), `saws` (grinding out through a hut wall, the
lunge, the combo, a grind; ends on a kill, to loop), `chain` (two zombies yanked off a tower and cut down in the air,
then up the tower on the chain; the flight down is carried by the test, as the yank alone doesn't clear the edge),
`arms` (two vindicators chop both arms off, the head's saw kills grow them back), `pochita` (along his blood's trail
to the fallen tree, your blood into him, he wakes, picked up and held as a chainsaw, a husk cut down; a husk, as a
zombie burns at dusk), `merge` (kortev: question hook + emotional beat + payoff; "What happens if you die holding
Pochita?" over you in a horde, killed, the mod's own merge, the pull, the horde cut down). The merge reel is filmed
whole (41.5 s) and **cut by hand** to 19.3 s: after `mux-audio.py`, keep 0-2.95 s (hook, death), 23.9-32.45 s (his
"show me your dream" line through waking) and 33.65-41.4 s (the pull to the end and its question), joined with
ffmpeg's trim/atrim and concat (50 ms audio fades at the joins); if the merge's timeline changes, find the cuts again.
All six approved by kortev, then a second pass (2026-10-22, from my review, kortev: "do all of it"): **every reel
opens on a hook** (`hook()`, two lines, white then orange) **and ends on a question for the comments** (`ask()`,
kortev: comment-farm on all of them, not one); `transform` is now **your own eyes** ("POV: you're Chainsaw Man": the
mod's own transformation camera, then three kills with the real attack key, then both saws revving), as the merge
already shows it from outside; endings differ (saws: one ground down, no combo; arms: both saws revving; chain: a
third flung up from the ground below and cut at the tower's edge); Pochita starts nearer him. All reshot with the eased
head turns. kortev approved the second pass to post (2026-10-22). `held` photographs Pochita held (with `-Pcapture=true`, a video of
him in use with the real attack key). kortev: the head must move like a real person's, especially in first person (no
snapping round: eased turns, the body lagging the head, a little sway). Lessons: a camera straight in front makes
the head's saw a spike at the lens (always three quarters on); cameras closer than ~2.6 blocks drop into first
person; the test world keeps entities between takes (`strays` clears them); grass between a low camera and you hides
you. kortev offered to ask YouTube Studio's AI for advice; nothing back from it yet.

**Done (2026-10-22): Pochita held is a real chainsaw** (kortev's picks): his 3D model in both hands at your waist
(`HeldPochita`: a feature for everyone's view, the arm pose in `FormPoseMixin`, his item model shrunk to nothing in the
hands so the flat picture is only his icon); no click swings: holding attack revs him (`Saw.REV`) then cuts in front
continuously (`Saw.HELD_BITE`, about his old sword damage a second), kicking back; wood and plants cut fast (his
`tool()` component); the engine idles while held, screams while cutting. **To fix later (kortev: "looks really bad from
the first person")**: his first-person view (`HeldPochita.renderFirstPerson`, the `VIEW_*`/`ARM_*` numbers): ask
kortev what they want it to look like before reworking it.

**Done (2026-10-22): Beam replaces the frenzy as the ult, second version** (built overnight from kortev's picks; kortev said the
first looked nothing like Beam and was useless, so this one is his real look and a moveset of his own; kortev has not seen
it yet: judge it from the photographs). The ult key (B, `ChainsawManClient.FRENZY`, `FormActionPayload.FRENZY`, "Beam (ult)"
in the lang file) summons **Beam, the shark fiend in his big shark form** (the Reze arc), for `Blood.BEAM` = 60 (the blood
bar's mark moved to it; `Frenzy.java`, its game test and `Blood.FRENZY` stay as dormant code). In the form and able to fight,
he bursts up out of the ground under you in a spray of the ground's own blocks (he needs a clear space about 2 wide and 2.4
tall within 3 blocks of you, else the action bar says so and nothing is spent) and you stand on his back; one to a player,
20 seconds (`Beam.LASTS`), gone when you leave the form or go down, and he dives back into the ground when he goes.

- **His look** (v8, 2026-10-23, **approved: "Beam v8 looks good"**; built by Opus after Sonnet's v1-v7 were all rejected). The
  proportions are the two pixel-art drawings kortev approved, `tools/beam_profile.py` (side) and `tools/beam_front.py` (front)
  (1 drawing px = 1/16 block = 1 model px), **read off them by eye into a handful of big boxes, Pochita's way**:
  `tools/beam_mob.py` lists every box as X along him (the side drawing's x) and h above the ground: the chest and middle each a
  big box with narrower steps on top (rounding his back) and under; the head a dome stepped down to the snout, hinged at the
  mouth's corner; the roof of the mouth set in under it (gum and upper teeth painted round it); the lower jaw its own part
  hanging open `JAW_REST` 0.5 rad (shut is 0, `MOUTH_OPEN` = `JAW_REST`), a chin step under it and the lower teeth stood up round
  its top; the rear and the tail's stalk narrowing, hinged so they sway; the crescent tail fin, five back spikes and the two
  pectoral fins are **flat cut-out planes** (no thickness, painted, the rest clear: the renderer is cutout no-cull); three eyes
  a side are boxes standing 4 px out, painted round; legs are a thigh box and a shin plus a dark claw box through the drawn
  hip/knee/foot (`beam_boxes.LEG_SPECS`). **47 boxes**, texture 512 x 256. Paint is **flat, Pochita-style**: navy above a
  zigzag line (6 px teeth, 3 high; the line's height along him and across his front follows the drawings), mint below, mint
  shade underneath, gill slits, the throat dark red down the open mouth, no outlines; colours a shade lighter than the
  drawings' (the game darkens a mob's sides). `python tools/beam_mob.py --java` writes `client/BeamLayout.java` (do not edit)
  and `textures/entity/beam.png` (`gen_chainsaw_textures.py`'s `beam_entity` paints the same); `--preview DIR` renders it in
  Python (rough; the game is the check: `beamlook`). It reuses `beam_boxes.py`'s part/box/texture-layout/Java machinery;
  v6's slice builder in that file and `beam_paint.py`/`beam_check.py` are no longer used for the game. He is **about 9 blocks
  long, 4.4 to the spike tips, 3 wide**; `BeamEntity.BACK` is 62/16, the hitbox still 2 x 2.4 and the bite's `MOUTH` 2.2 /
  reach 3.5 (gameplay left alone). `BeamModel.java` only poses the parts.
  **The chain is his reins**: `ChainRender.reins` draws two strips of the form's chain from the rider's hands (low in front of you
  in your own eyes) to the corners of his mouth. The rider stands on the middle of his back (`SawPose.ride`: knees bent, feet
  apart, arms forward on the reins, or held out grinding).
- **Riding, second version: he swims through the ground** (2026-10-23, phase 1 of "Beam with a soul"; kortev on the first reel: "it just
  looks dumb, it's not the crazy action stuff we see in the movie, it feels soulless"). `form/Beam.java`; `BeamEntity` is a plain `Entity`,
  not saved, server-driven: `getControllingPassenger()` is nobody; hitbox 2 x 2.4; **no game collision at all now**: `Beam.travel` sets
  his place from his own rules. Your keys while riding (an action-bar line shows them with your own key names, at the start and again at
  2.5 s: "Walk on his legs   [Sprint] Dive and swim   [Attack] Bite ...", `chainsawman.beam.keys`):
  - **Walk on his legs, sprint to swim** (2026-10-23, kortev: "we can walk with legs and swim with sprint"). The forward key alone **walks** him
    on top of the ground on his crab legs (`Beam.WALK` = 0.6 blocks a tick, the old walking speed; he turns 11 degrees a tick; back
    stops, strafe nudges): `Beam.travel` with `dive` false. He follows the ground (`surfaceAt` finds the standable top within 2 up and 8
    down, a wall column has none; where the ground has dropped away it looks 48 down) under his middle and 3 ahead and behind, averaged, the
    height eased a tick at a time, so he **steps up 2 blocks** and a long step is climbed at its middle; he is **stopped at a wall** (`walkStep`:
    eight probe columns, his feet 1 either side and his snout 3.4 ahead, must all have ground he can reach; it slides along a wall; if the
    column he stands in has none he is let out) and **breaks nothing**. **Sprinting** (the rider's `isSprinting()` and forward held) he
    **dives**: `ride.swim` 0 to 1 at 0.1 a tick (half a second each way), he **sinks `SINK` = 2.7 blocks** into the ground (only his back,
    spikes and you above it), speeds to `Beam.SPEED` = 1.2 (a surge of 0.24 a tick) and tears through walls as below, with the wake. Let go of
    sprint (or forward) and he **surfaces** back onto his legs and slows to a walk. A spray of the ground, a ring and `beam_dive` mark the
    dive in (`ride.swim` past 0.12) and the surfacing (below 0.45), in `Beam.pose`. The charge, the rush under a mob, the breach and swimming
    to a thrown rider dive on their own (`dive` true), whatever the sprint; the legs fold up while he swims or is in the air
    (`BeamModel.animate`, the entity's tracked `SWIM`). Water counts as ground (he swims along a lake's top).
  - **How the sprint reaches the server**: two things stop it on a real client. (1) Vanilla stops a rider sprinting unless the mount is one they
    steer and it stands on the ground (`ClientPlayerEntity.canVehicleSprint`, `Entity.isOnGround`): `client/mixin/BeamSprintMixin` makes
    `canVehicleSprint` true for a `BeamEntity` and `BeamEntity.isOnGround()` is true unless he is airborne, so the game's own sprint (the sprint
    key, or a double tap of forward, kept while forward is held) works on his back. (2) But the game then sends **no sprint packet** for a
    rider (`ClientPlayerEntity.tick` calls `sendSprintingPacket` only if the root vehicle is the one they steer), so the server never saw
    it (found by filming `beamswim` with real keys: the client sprinted, Beam walked). `ClientCombat.tick` sends `FormActionPayload.SPRINT`
    (on / off, on every change of `isSprinting()` while riding), `Beam.sprint` keeps it in `Ride.sprint`, and `Beam.tick` dives when that
    or the server player's own `isSprinting()` is set with forward held. In the tests `player.setSprinting(true)` + `updateInput(0, 1, ...)`
    stand in (`sprintAhead` / `walkAhead` in `FormGameTests`); in the showcase the real client keys (`forwardKey`, `sprintKey`, `swimKeys`)
    are pressed, so the whole path is exercised.
  - **The gait** (`BeamModel.animate`, from the entity's client `gait`, 0 to 1 of the walking speed): diagonal pairs stepping (front right
    with back left), a bounce twice a cycle, the body rocking and nodding, the hindquarters, stalk and tail whipping on a beat late, the head
    bobbing and swaying and still looking at the nearest mob; each footfall is a thud (`BeamClient.footfalls`, `beam_step`) and
    kicked-up ground. `BeamEntity.STRIDE` 5.2 blocks a cycle.
  - **Through walls**: `Beam.plow` breaks, every tick, the solid blocks of his lane (from 2 behind to 5.2 ahead of his middle, 1.75 each
    side, from the ground up to the rider's head, 2 high at the snout) that are not in a column he can climb (`surfaceAt` not NaN: steps,
    hills, low walls are ground, not obstacles) and not too hard (`TOO_HARD`, hardness 20: obsidian, bedrock and the like are swum
    under), nearest first, at most `PLOW_CAP` = 36 a tick: `world.breakBlock(pos, false)` (the break sound and particles, no drops), and
    about one in three (at most 4 a tick) becomes a `FallingBlockEntity` thrown ahead and up, destroyed on landing and after
    `DEBRIS_LASTS` = 24 ticks (`Ride.debris`, `sweep`). **`mobGriefing` off: nothing is broken**; he swims under, pushed 0.9 lower
    (`ride.under`) while a wall is in his lane, and the rider clips through it (the `IN_WALL` damage of a rider on Beam is cancelled).
  - **The wake** (`Beam.wake`, server particles): the ground's own block particles (`spray`) thrown up and out in a V from either side of
    his head, along his flank, his fin and behind his tail, plus dust clouds of the ground's colour: up to about 35 packets a tick at full
    speed. The churn sound is `beam_swim`, a 0.6 s one-shot that `BeamClient` plays every 6 ticks while he swims (volume and pitch from
    his speed): **not a loop, because `Capture` logs a sound once, at its start, and could not mix a loop into a test clip.**
  - **Shape of the motion**: the tail beats an S (trunk, rear, stalk, tail yaw as a travelling wave, amplitudes 0.08 / 0.28 / 0.45 / 0.7 rad
    times his speed), he leans into turns (`BeamRenderer` rolls the model by his turn rate; checked from inside a U-turn: the inner side
    goes down), **porpoises** (`BOB` 0.36 blocks, his pitch with it, from `ride.wave`) and at full speed, every 36 to 66 ticks, **arcs out
    of the ground and back in** (`ARC_UP` 1.7, 18 ticks, a spray each end). His pitch and height go to the clients in his place (the
    glide is 2 ticks).
  - **attack = bite** (`FormActionPayload.SLASH` goes to `Beam.bite` through `Saw.slash`; no swing is predicted on the client): he
    **lunges up out of the ground** (`ride.lunge`, 10 ticks) and everything in a cone about 3.5 blocks in front of his mouth takes 16 of
    **your** `playerAttack` damage (kills are yours through `Saw.init`'s `AFTER_DEATH`), survivors are flung (1.2 up, 1.5 forward as
    velocity), at most every 12 ticks (a click up to 2 ticks early still counts, as for the slash). The mouth (`Beam.mouth`) is measured as
    if he stood on the ground. **hold attack = your saws grind** from his back as before.
  - **use = charge** (`LUNGE` goes to `Beam.charge` through `Saw.lunge`): now `CHARGE_BLOCKS` = 12 in 8 ticks (faster than his cruise),
    12 damage once to each thing in the way, flung aside, every 30 ticks, rising to the surface as it goes, with the lunge's speed lines
    and wider view (`ClientCombat.lunging` reads his `sinceCharge`).
  - **jump = breach**: out of the ground `LEAP_UP` 5.2 blocks above the level he swims at (so about 2.5 above the ground), `LEAP_LONG` 14
    forward (at least as fast as he was going) on ballistic physics (16 ticks), nose up then down; **where he goes back in** (`land`) a
    ring and two big sprays of the ground and 16 damage to everything within 3.2 blocks, every 40 ticks. The clients get `LAND_AT` (the
    camera jolt).
  - **sneak = get off** (he dives from where he is and you are put on the ground above him; not while he is in the air). The chain key is
    off while you ride. A rider is spared the fall of getting off in the air (`SAFE`), and the `IN_WALL` damage of his head in a roof.
  - **Summoning** (`POP_TICKS` = 26; your keys are his after that): he comes up from 3 blocks down, flies out of the ground, lands on his
    legs and hops once for joy; the clients (all on `BeamEntity.age`, in `BeamModel.animate`) thrash his body and tail, curl his head up
    and back to look at you with his jaw flung wide, and `beam_screech` sounds at tick 7.
  - **Personality** (all the client's, `BeamEntity.look` and `BeamModel`): standing, **his tail wags** like a dog's (`wag`), **his three
    eyes dart** (each eye is now **its own part, hinged at its middle**: `tools/beam_mob.py` makes them parts with the same boxes, the
    same places and the same texture, so his look is unchanged; the model rolls and slides them a hair: `eyes[][]`), **his jaw chatters**
    (a run of five snaps every 3 to 5 seconds, with `beam_chatter`), and **his head turns to the nearest `MobEntity` within 16 blocks**
    (re-chosen every 8 ticks, eased; with nothing near, a slow glance about). **After a kill from his back** (`AFTER_DEATH` in
    `Beam.init`; tracked `HAPPY_AT`) a happy chomp (two quick snaps and `beam_bite` pitched up) and a wiggle of his rear.
  - **Feel for the rider** (`BeamClient`): the view widens up to 11 percent with his speed (`fov`, through `ClientCombat.fov`), and the
    camera shakes a little (`shake`, through `FormCinematic.shake`: 0.009 blocks at full speed, 0.07 at the breach's crash, fading in 5
    ticks). Only seen on the test clip, not judged in a real ride.
  - He is **lit where his back is** (`BeamRenderer.getBlockLight` / `getSkyLight`): the game lights an entity by the block at its eyes,
    which inside the ground is dark (he drew black). `Beam.room` now tests a tenth up, not a hundredth: a dirt path is a sixteenth lower
    than the grass beside it, and the summoning was refused standing on one.
- **Sounds, remade (2026-10-25; kortev: "I think we need to fix Beam's sounds"; not yet judged by kortev: the audition
  `work/beam_sounds/audition.mp3` / `.mp4` and the clips `beamaction.mp4` / `beamswim.mp4` there, git-ignored, were sent)**:
  `tools/gen_chainsaw_sounds.py` now layers pitched, low-passed vanilla Minecraft sounds (read from the loom asset cache
  `~/.gradle/caches/fabric-loom/assets`, index `1.21.1-17.json`, at generation time only: the mod's own `.ogg` files are baked, nothing
  vanilla is read at runtime; the helper `vanilla(key)`) over the synth sub-bass, impacts and debris: `beam_burst` (stone, deepslate and
  dripstone breaks, an explosion and a big fall pitched down, a sub sweep, a throw of earth), new **`beam_step`** (`ChainsawSounds.BEAM_STEP`,
  `beam.step`: the iron golem walk at 0.55, a sub thud, dirt and gravel; `BeamClient.footfalls` plays it instead of the vanilla golem
  step), `beam_swim` (dragon and warden growls at about 0.5, brown rumble, gravel; a sine envelope so the 0.3 s retriggers run together,
  about 1 dB of ripple), `beam_bite` (ravager bite, golem hit, dolphin eat, bone crunch, cut short for the thrash), `beam_dive` (warden dig
  pitched down, mud and rooted dirt, a sinking sweep), `beam_screech` (a breath, the ravager roar pitched up with dolphin, hoglin, warden
  and wolf, then a seven-cry cackle of ravager, zoglin and hoglin bits at 1.6 to 2.3, a gravel throat under it), `beam_chatter` (bone-block
  clacks, quieter at -16 dB), `beam_slam` (two explosions, a big fall, deepslate and stone breaks, a hoglin hit, a sub drop, a long rain of
  stone; the loudest at about -7 dB) and `beam_launch` (the golem throw, a hoglin hit, a whip of air). Levels: burst and screech -9, bite and
  launch -10, step -12, swim and dive -13. `make` raises the gain until the limited sound reaches its level. The scratch scripts to rebuild
  the audition are in `work/beam_sounds/` (`run.sh` needs ffmpeg on PATH or `FFMPEG_DIR`). Still only checked by the numbers and the
  spectrograms, not by ear: if kortev says a sound is wrong, that is the one to redo.
- **Tests**: Beam game tests in `FormGameTests` (101 in all; the riding tests that swim now sprint: `sprintAhead`; new: **walks on his legs
  above the ground at the walking speed, steps up a block and is stopped at a wall breaking nothing, sprint dives and letting go surfaces**; also): summon, refused, **sinks when sprinting (fast, the rider above
  the ground) and rises when stopped**, **tears through a wall with `mobGriefing` on** (his lane broken, the wall beside it not, the
  debris gone later), **swims under it with the rule off, and under obsidian with it on**, follows a hill (never a step of a block in a
  tick), the slash becomes a bite (16 damage, flung, behind untouched, no second at once), **a kill pleases him**, the lunge becomes a
  charge, leap, **breaches while swimming**, dives on sneak (and the rider is put on the ground), on leaving the form, after 20 s. The
  test floor is `ground(context, on, length)` and keeps its chunks loaded (he stops at the edge of what is). `./gradlew runSelftest
  -Pselftest=showcase -Ptarget=beamlook -Pusername=Kortev` photographs him in daylight as before (the shots predate swimming: they show
  him standing).
- **The test clip `beamswim`** (not a reel: no captions, no title; a clip for kortev to judge how he moves): `./gradlew runSelftest
  -Pselftest=showcase -Ptarget=beamswim -Pusername=Kortev -Pcapture=true "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"` (the `jvmargs`
  is for a PC short of memory: the game died with "insufficient memory" twice with its default 8 GB heap, with the machine's commit
  nearly full), then `python work/tools/mux-audio-noprobe.py build/selftest/capture work/beam_soul/clip.mp4` and a gain of 1.8 dB with a
  limiter, for a mean of about -17 dB. 1080 x 1920, 15.9 s. The set (`swimSet`): a flat green field made at a fixed place in the test
  world (`SW_SPOT`, running east at 376 73 -424; everything on it up to 14 blocks above the ground is cleared), two dirt paths (the way
  out and the way back), fence posts with lanterns, trees, a built house across the way out. The route is a loop: out 100 blocks,
  **through the house**, a half circle to the left, back on a parallel line. The beats: the summoning (a camera low in front: the burst,
  the thrash, the look back, the screech), the surge away (from behind), alongside and low, **above and behind** (his back, the S, the V
  of the ground), the house from the side and from beyond it, the U-turn from its middle (leaning), the breach (the real jump key) from
  low between the ways, **your own eyes** on his back (2.6 s), then he stops, rises on his legs and idles in a broadside shot (tail,
  eyes, chatter, head on two real husks coming at him with their own AI), and in a close shot bites one dead with the real attack key and
  chomps. Nothing is faked but spawn spots, timing and cameras (your keys are the real ones).
- **Phase 2: the big moves and hits that land hard** (2026-10-23; kortev wanted "the crazy action stuff we see in the movie"; **not yet seen by kortev**: judge it from `work/beam_soul/action.mp4`, git-ignored, on GraehamsPC). All in `form/Beam.java` (keys), `BeamEntity` (tracked times `GRAB_AT` / `AMBUSH_AT` / `LAUNCH_AT`), `client/BeamClient`, `BeamModel`:
  - **Ambush** (the attack key while he swims, with a mob on the ground `AMBUSH_REACH` 8.5 blocks ahead of his mouth, too far to bite, within about 45 degrees of your look): `Beam.bite` starts `Ride.amb` = 0: he digs `AMBUSH_DIP` 1.3 deeper and rushes at it (`rushStep`, 1.5 a tick, turning 45 degrees a tick, 3 to 14 ticks), and when his head is under it (`AMBUSH_UNDER` 3.0 short of its middle) `erupt`s: `AMBUSH_DAMAGE` 24 to everything within 2.6 of it, the mob thrown straight up at `AMBUSH_LAUNCH` 2.1 a tick (about 20 blocks; the others 0.75 of that), a ring of 16 ground blocks torn up as `FallingBlockEntity` debris (real blocks removed, so a pit; only where `mobGriefing` is on, else only particles), the new `beam_slam` sound, `BEAM_HIT` level 4 with impact frames. He flies up on his own ballistics (`amb` = 1, `ambLift`, nose up 40 degrees), crashes back in through `land` and swims on. `ride.surf` is frozen while he is over the hole. Cooldown `AMBUSH_EVERY` 30 ticks from the eruption. A mob in the bite's cone is bitten instead.
  - **Grab and thrash** (the bite on a mob up to `GRAB_WIDTH` 2.0 x `GRAB_HEIGHT` 2.4, no player, nothing riding it): the bite hurts it as before then `grab`s it: it becomes a **passenger of Beam** (`startRiding`), positioned in `BeamEntity.updatePassengerPosition` from `BeamEntity.shake(sinceGrab)` the same on the server and every client (so it is smooth; the server's own 3-tick entity updates would not be), swung with his head side to side three times over `GRAB_TICKS` 22; each of 6 swings ends in `SHAKE_DAMAGE` 3 and blood; then `throwPrey`: flung `FLING_SPEED` 2.1 along your look, `FLING_DAMAGE` 6, level 3 hit with flash. He rises out of the ground to hold it (`GRAB_RISE`); his riding keys work throughout. Bigger mobs are just bitten and flung.
  - **Rider launch** (the chain key while you ride: `ChainsawMan` sends CHAIN to `Beam.launch` when riding): `Ride.loose`: you are taken off him and sent along your look at `LAUNCH_SPEED` 2.3 for `LAUNCH_TICKS` 9 (the server re-sends your velocity each tick with gravity off; `flightTick` follows the same path itself, a wide cut `LAUNCH_WIDE` 1.4, once per mob, `Saw.FINISHER_DAMAGE` x your multipliers, stops at a wall, `Saw.impact` sparks, level 3 hit with flash and the ザシュッ letters); you land unhurt (`SAFE`). Meanwhile `looseTick`: Beam swims to you (`FETCH_SPEED` 1.5, straight at you when within 10) and `pickUp`s you when he is within 2.4 and you are near his ground; gives up and dives after `FETCH_TICKS` 100, over `FETCH_RANGE` 70, or when you leave the form. While loose, the attack key is the saws' own slash. Cooldown `LAUNCH_EVERY` 60 ticks.
  - **Hits that land hard**: `Beam.hit` tells the rider alone (`FormEventPayload.BEAM_HIT`, value 1 to 4 plus `BEAM_FLASH` 8 / `BEAM_CHOP` 16) and `BeamClient.hit` does: a camera shake by level (`HIT_SHAKE`, also in third person), **a hit-stop** (`HIT_HOLD` frames: 1, 1, 3, 4 for levels 1 to 4; `BeamHoldMixin` stops the **client's render clock** for those frames, so the view and everything in it hold still, on that client only; the test capture's own clock does the same in `Capture.beginFrame`), and for the big ones the impact frames (first two held frames, `FormCinematic.impactStyle`) and a word (ブシャッ / ザシュッ). Levels: bite 1 (2 when it grabs), thrash shake 2, charge 2, breach crash 2 or 3, launch slash 3, fling 3, pickup 2, ambush 4.
  - **First person on his back**: `BeamClient.eyes` (the `FormCameraMixin`): the camera is raised `eyeUp` 1.2 and drawn back `eyeBack` 3.5 along him and tilted `eyeDown` 6 degrees (chosen from stills: `-Ptarget=beamfp`), so his head tip, jaw and the ground rushing past show; it is still a lot of blue back (his model is big flat boxes and his spikes are edge-on planes), but you see him bite and the head swing. The test capture does not hide your body for it any more (`CaptureCameraMixin`).
  - **Tests**: 98 in all (`FormGameTests`: grab thrash and fling, big mobs bitten, ambush erupts and launches with debris, ambush cooldown, a close mob is bitten, launch throws / cuts / fetches / refuses a second throw / dives out of the form; the old bite test now uses an iron golem). Three unrelated tests (`terrainHeld`, `aimedHitsLand`, `liveEvent`, `beamFollowsTheGround`) flake now and then on this PC with the small heap, with or without these changes.
  - **The test clip `beamaction`** (14 s, 1080 x 1920, no captions; `-Ptarget=beamaction`, then `mux-audio-noprobe.py`, then a -1.2 dB gain with a limiter for a mean of about -16 dB; PATH needs the ffmpeg folder): the beamswim field, the lane 18 blocks to the left, west end extended to x -105. A Ravager runs at him and he ambushes it; a second is caught, thrashed and flung; a pack of husks and he throws you into it and swims back for you; then your own eyes, a third Ravager ambushed, a fourth caught and thrashed. Real mobs with their own AI (the test sets their target when in range and spawns them), the keys pressed on the server at the instant they would take (`actKeys`), the cameras the test's (with the rider's shake added to them). **Looks wrong or stiff**: Beam's body barely moves while he thrashes (the head and tail do; the trunk is rigid), the flung mob does not spin, the Ravager is a stiff vanilla model and red from the hurt flash for most of it, the launched mob is small in the sky shot, first person is still mostly a blue slab, the first second of the clip is a long chase of nothing; the sounds `beam_slam` and `beam_launch` are only checked as files.
- **Not verified**: the sounds by ear; how his motion *feels* in a real game (the glide between the server's positions at 24 blocks a
  second, the camera shake and wider view in first person: only photographed); multiplayer; a shader pack; riding over water, lava,
  trees, a real village, caves and cliffs (the tests are a flat floor, a wall and a hill). **What still looks stiff** (from the clip): in
  your own eyes his back is a flat blue slab filling the bottom of the picture (the model is big flat boxes; only the spray and the
  passing ground show speed); only the top 1.2 blocks of him show while he swims, so the S of his body is read from the tail's swing and
  from above, not from a low side camera; the burst-up at the summoning is a fixed ballistic hop, the same every time. (`beamlook`'s shots were set for the old walking Beam and are not refilmed.)
- **Beam's reel, refilmed** (2026-10-23, after walk-and-sprint; `-Ptarget=beam`, in `ShowcaseSelfTest`: `beam()`, `reelKeys`, `beamSet`/`campBuild`; **not yet seen by kortev**:
  `work/beam_reel/beam_v2.mp4`, git-ignored, on GraehamsPC; 12.7 s, 1080 x 1920, mean about -17 dB, the first 2 s about -17 dB). Film with
  `-Pcapture=true "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"` and the ffmpeg folder on the PATH, then `python work/tools/mux-audio-noprobe.py
  build/selftest/capture out.mp4` and `-af "volume=-1dB,alimiter=limit=0.97"`. The set is a camp at sunset on the clip's flat field run from its east
  end west toward the sun (215 blocks, chunks forced so the far raiders do not fall through): a lane, fence posts and lanterns, a house on the lane
  to swim through, a well, hay, tents, campfires, two more houses. The beats (ticks / 20): 0 to 1.4 s the summoning (ground-level close shot, then
  the front of his head as he screeches); 1.5 s walks (past the lens; from behind, low, with the raiders at the sides of the lane running in);
  2.9 s the sprint, a dive under them at the lens, then above and behind; 4.2 s the house bursting (from the side, from beyond it); 5 s first person
  (0.7 s); 5.6 s the ambush under a Ravager (ground-level, then a tilt up after it, the sky); 7 s he surfaces and walks on to the vindicators; 8 s
  a vindicator caught (close at the jaws, low in front), shaken, flung (wide shot following it); 9.6 s the chain key throws you into a pack
  (from beside, then following you through it); 10.5 s he is under you again; the hero shot low and close at his open mouth, the question.
  Real mobs with their own AI (the test only spawns them, sets their target and health, and presses the keys at the instants a player would:
  the attack key for the ambush within its own rule, for the bite when one is in the jaws, the chain key with the pack 11 to 20 blocks ahead),
  the real client keys for walking and sprinting (forward, then sprint; to surface the forward key is let go for two ticks, because the game
  keeps a sprint going while forward is held). **Timing varies from run to run** with the mobs' AI (the ambush key came at f 122 every time, the
  rest within a few ticks; once the far vindicators fell through unloaded chunks, which the forced chunks now prevent); if a run gives up
  ("gave up at f=601 in phase N" in the log) just film it again. **What does not read well**: in your own eyes his back is still a blue slab; the
  flung vindicator is a small dark figure in a big sky (it reads best from the take where the camera is ahead of him, not always); the dive
  itself (legs folding, sinking) happens as he passes the lens, so mostly the spray shows it; the raiders' clothes are dark against his blue.

**Latest (2026-10-22, afternoon) — read this first:**
- **Tree felling (2026-10-23, kortev asked for it):** in the form, sawing through a tree's trunk brings the whole tree
  down (`form/Felling.java`, on `PlayerBlockBreakEvents.AFTER`): the logs above the cut joined to it and the tree's own
  (non-persistent) leaves topple away from you as falling blocks, faster the higher up, and burst into their drops where they
  land. A trunk with fewer than 6 of its own leaves isn't a tree (a log cabin's wall still cuts a block at a time). Game
  test `sawsFellTrees` (87 tests).
- **Next reels: the "Chainsaw Man vs ___" series** (kortev, 2026-10-25): crossovers with kortev's own other mods in this
  repo, 20-40 s each, a real fight with a story (the opponent arrives, he's nearly beaten, fights back with his
  abilities, a big finish), every beat bigger than the last, ending on "Who should he fight next?" so the comments pick
  the next episode. Order: **vs the Genesis Key** (Thursday Oct 29; ties to kortev's best reel, the Genesis Key showcase:
  8k views, 400 likes, 30 comments), then vs Mjölnir, vs Gungnir (dodging on Beam), Beam vs Chitty (a chase), and vs Gojo
  once kortev finishes the Gojo mod (not done; don't use it yet). The other mod's weapon is used by a second (test) player
  through its real paths; same rules as all reels (nothing fake). Research and kortev's numbers say short (10 s) reels
  get little engagement; aim at 20-40 s that still hold (see the memory notes).
- **Next big thing: a long-form devlog** (kortev's pick, 2026-10-25): "I made a Chainsaw Man mod for Minecraft", about 6-10
  min, every feature plus the story of building it (Beam took eight tries, from the voxel blob to the swimming shark: the
  pictures of every version are in `work/`). Claude films and edits all of it (gameplay through the showcase self tests,
  captions, sound); kortev records a voiceover over it (they'd rather not do first-person commentary for the boss-fight
  idea). Plan the edit and a script draft for the voiceover first and show kortev.
- **Reels engagement** (kortev, 2026-10-25): retention is good, especially the latest two, but almost no likes or
  comments. Ideas to try are in the session's last messages; see `showcase-reels/POSTING_PLAN.md`.
- **Beam's new reel approved** (2026-10-23, kortev: "good to post Wednesday"): `showcase-reels/Post7_Wed_Beam.mp4` (the
  walking/swimming Beam, from `work/beam_reel/beam_v2.mp4`). kortev keeps first person on his back as it is ("I like
  seeing that you're riding him"). Ideas for more Beam reels later, spaced out: one built round the rider throw, and
  "Beam vs the Warden" as the answer to the first reel's question if the comments argue it.
- **Arms reel redone (2026-10-23, not yet seen by kortev; `work/reels_v3/arms_v2.mp4`, git-ignored, 14.8 s):** the Warden is
full strength (500, its own AI). Its real blows take both arms (Easy: 16 a blow, one arm each); the head's saw slashes it twice
(real attack key) while it does; he looks at it, then up, and fires the real chain key at a band of carved stone above a one-block
ledge (4 up, along the north wall) and is pulled up; the Warden walks to the foot and its sonic boom (6 a hit) throws him up each
~104 ticks; he holds the attack key down on its head from the ledge (out of its claws) and the blood drunk regrows both arms
(`Limbs.drank`: 25 blood an arm). **Order differs from the first plan** (regrow, then escape): on the floor the Warden's blow
every 20 ticks retakes a regrown arm at once (`Limbs` keeps counting damage while both are gone), so no regrowth can finish there.
Boom 2 (6 more) leaves the arms (damage count under 12) but a third would take one: the reel ends before it. Staging: spawn spot,
anger, the set (ledge, band), keys, cameras; difficulty Easy; 400 max health. Nothing else touched.
**All seven reels are final** (2026-10-23) in `showcase-reels/` with `POSTING_PLAN.md` (titles, descriptions, tags,
  schedule Thu Oct 22 to Wed Oct 28). kortev's rules, on top of the showcase style: **each reel looks different** (a
  friend's advice: its own place, mobs, kind of hook and question: Saws a daytime forest, diamond axe vs the saws with
  felling; Chain a night cliff, the grab before the caption; Arms the Deep Dark against a real Warden; Beam a desert at
  sunset against a real pillager patrol; `hook()`, `ask()`, `hookFrom()`), and **nothing may look fake**: mobs have their
  own AI and come at you (`wake()` in ShowcaseSelfTest), the player's moves go through the real keys and paths, no
  frozen, teleported or carried mobs and no damage put on by the test; staging is only spawn spots, timing, targets,
  health and the camera. Files named `OLD_...` are superseded versions. Open: the Arms Warden has 36 health so the saws
  finish it (kortev asked whether to keep it at full strength); in the Beam reel the client once replayed the
  transformation's roar mid-ride (cause not found; the test clears `ClientForms.PULLED` each tick; may be a real bug).
- **How kortev wants the work done**: Opus (the main session) plans and reviews; **Sonnet 5.5 subagents execute** from
  briefs written to files (the briefs are in the session scratchpad; write new ones the same way). Keep usage down:
  review videos/photos as small contact sheets, not full-size images.
- **Showcase reels: final and posting.** Six videos in `showcase-reels/` at the repo root (git-ignored, local to the PC
  "GraehamsPC"): `Post1_Thu_Merge.mp4` … `Post6_Tue_Arms.mp4`, with `POSTING_PLAN.md` (titles, descriptions, tags,
  schedule: one a day from Thu Oct 22, 1 PM CDT). kortev's rules for them: **showcase style** (captions/titles say
  plainly what the feature is, no skits, no meme captions); the same end question on every reel, "What should I add
  next?"; trending music added in each app, not baked in. Captions live in `ShowcaseSelfTest.hook()`/`ask()`. Film in
  a separate worktree (`../loft-film`) when another agent is using the game, so two games don't share the test world.
  Send kortev files as chat attachments (they download them there; Drive upload of video doesn't work).
- **Beam (the ult) is being redesigned.** Moveset is built and approved in principle (bite LMB, saw hold, charge RMB,
  leap Space, Shift off; 60 blood, 20 s). The look: three box versions were rejected (didn't look like Beam); a smooth
  Blender model (`tools/beam_model.py`, renders in the scratchpad `beam_v4_*`) got "I like the idea, but it should match
  the box head and Pochita". v5 (that design in boxes) was "honestly worse"; v6 (810 thin slices traced from two pixel-art drawings kortev approved)
  "too many small boxes, looks like ass"; v7 (Sonnet, 43 boxes) "horrible"; **v8 (Opus, 47 big boxes, flat paint, see "His
  look") is approved.** Next: film his showcase reel.
  References in **`work/beam_refs/`** at the repo root (git-ignored, on GraehamsPC): kortev's still, the official IMAX
  poster (anitrendz.com), Prime 1 Studio's Reze Arc statue (product photos). The briefs Sonnet worked from are in
  `work/briefs/`, the v4 comparisons in `work/beam_v4_compare/`, and the helper scripts in `work/tools/`
  (`film_all.sh` films reels in a row; `mux-audio-noprobe.py` mixes a capture's sound without ffprobe). ffmpeg: Python's
  imageio-ffmpeg exe copied in as `ffmpeg.exe`, first on the PATH. Show kortev side-by-sides (reference | blueprint | game) before filming Beam's reel; his old reel is
  `OLD_beam_being_rebuilt.mp4`, not to be posted.
- **Usage setup**: prompt suggestions off; `.claude/settings.json` has a SessionStart "compact" hook that re-prints
  this section after a compaction. Third-party compaction plugins were checked and skipped (unverifiable).

**Next**: kortev judges the `beamswim` test clip (2026-10-23; `work/beam_soul/clip.mp4`, git-ignored, on GraehamsPC); then kortev judges phase 2 of "Beam with a soul" (built 2026-10-23: `work/beam_soul/action.mp4`); then kortev judges the refilmed Beam reel (`work/beam_reel/beam_v2.mp4`) and Beam walking on his legs / swimming on sprint.

**kortev's queue of ideas** (ask before building any): the ult is Beam now (above; the frenzy was dropped for looking the
same as normal attacks); the merge dead
a while first, as reel 1 has it (below, "Later ideas"); the black chainsaw (build nothing until kortev describes
it); the "I'll give you my heart" clip. Older open items: the shader-pack check (needs
kortev's OK to download Fabric 1.21.1 Sodium + Iris), the multiplayer test (written, never run), balance after play,
the stretched cord in your own camera, the chain's drag in water (not filmed), hearts against the blood bar.

**How kortev works with you right now**: often on their phone, remoting in, unable to test. Send videos and pictures
(the app's send-file tool) of every change, short and plain. The recipes:

- Game tests: `./gradlew runGametest` (84, all passing; `aimedHitsLand` flaked once in nine runs).
- Self tests with pictures and video: `./gradlew runSelftest -Pselftest=form -Pusername=kortev -Ptarget=...`
  (`merge`, `limbs`, `pvp`, `kill`, `scene`, `chain`, ...) and `-Pselftest=reel1 -Pusername=Denji` (the reel;
  `-Ptarget=cast`, `set`, `den` for pictures). Add `-Pcapture=true` for a video with its sounds, frame by frame, into
  `build/selftest/capture`; then `python .github/scripts/render-loops.py build/selftest/capture` and
  `python .github/scripts/mux-audio.py build/selftest/capture out.mp4`.
- Video needs `ffmpeg` on the PATH of the shell that runs gradle. This PC has none installed: copy Python's
  imageio-ffmpeg binary (`python -c "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())"`) into a folder
  as `ffmpeg.exe` and put that folder first on the PATH. `mux-audio.py` also wants `ffprobe`, which that package
  lacks: in a copy of the script, read the video's length off `ffmpeg -i` instead.
- On this PC, bash here-documents containing apostrophes fail in the tool shell: write edits as Python scripts to a
  file and run them.

**Things learned the hard way**: client-only stand-in players never get the mod's downed pose (their pose stays
standing; the reel lays them down itself); spectators see invisible entities (don't test hiding things as a
spectator); the test world keeps everything between runs (the reel clears its set's entities at setup and takes your
heart away again); a model poking out through something drawn rather than built (the reel's dumpster) looks like a
stray entity: log the entities near it before chasing one.

## Where the work is

- **Branch: `claude/chainsaw-man`.** All of this mod's work is there and nowhere else. It is **not merged into
  `main`**, and `main` is far behind it. Work on this branch (or a branch off it); push to it.
- It was cut from `claude/gojolander-domain` (open PR Kortev/loft#3: the friend's mod) with
  `claude/iris-fullscreen-effects` (open PR Kortev/loft#2: shader-pack support, `WorldPasses`) merged in, because
  neither PR is merged yet and this mod needs both. Those two PRs conflict in one file
  (`gojolander/src/client/resources/gojolander.client.mixins.json`); it is already resolved on this branch.
- **No PR and nothing into `main` without kortev's OK.** Merging is their call.
- CI (`build` workflow: `build` and `gametest` jobs) was green on the branch when last checked. All 76 game tests
  pass locally (2026-10-20).

## The project in one paragraph

`Kortev/loft` is one Gradle build (Fabric, Minecraft **1.21.1**, Yarn mappings, Java 21) of several mods for kortev's
SMP, each its own subproject and jar: **The Shooting Star** (root, `src/`, id `shootingstar`: kortev's weapons, plus
everything shared: `OwnerOnly`, `ModCriteria`, the `client/gfx` helpers `Post`, `Target`, `Shaders`, `WorldPasses`,
and the game-test harness), **Chitty Chitty Bang Bang** (`chitty/`), the friend's **Gojolander** (`gojolander/`, the
Domain Expansion), and now **Chainsaw Man** (`chainsawman/`, id `chainsawman`): kortev's own powers, a public mod that needs only Fabric API (since 2026-11-06 it has its own copies of the `Post`, `Target` and `Shaders` helpers in `client/gfx` and no code from the root; the game-test harness in the root still loads it). Every SMP player
gets a mod of their own; this one is kortev's.

## Rules kortev has set (follow them every time)

1. **No spoilers past the Reze arc.** kortev has only watched the anime, through the end of the Reze arc (the
   movie), and has not read the manga. In every question, option, name, comment and commit, draw only on season 1
   and the Reze arc. Never mention or hint at later forms, characters or events. (An early question once offered a
   later-manga form as an ult option; they noticed.) If you research, do it text-only and keep what you report safe.
2. **One piece at a time, committed and pushed as soon as it works.** Get each piece in the game first even before
   it does anything, then add each ability on its own. Commit (one topic, a subject in plain words, a body that says
   why), push, then start the next piece.
3. **Ask before design calls** (what a power does, balance, PvP, how something looks); decide technical calls
   yourself. Keep messages short and plain. Refer to kortev as "they".
4. **Show renders/screenshots before baking or shipping a look**, and match the anime. kortev reacts strongly to
   things that look invented instead of matched; when they send references, follow them exactly.
   **Check every fact first, so kortev never has to correct it** (kortev, 2026-11-09). Before designing, briefing
   or building anything from the show, check it on the wiki (https://chainsaw-man.fandom.com, its episode and chapter
   pages and its image galleries; read it in the built-in browser, since plain fetches get blocked) and with a Google
   search. Check more than looks:
   - where things are and which way they face;
   - how the scene is staged (the Eternity Devil's mouth is a pit below a corridor turned 90 degrees, not a face on a
     wall: kortev caught that one);
   - how it moves;
   - its rules.
   Put the checked facts and their source in every agent brief; mark anything unconfirmed as a guess. Spoiler rule 1
   still applies.
5. **End a round of work with a numbered list of what to test in game.**
6. **The show's world, not its story** (kortev, 2026-11-06). Take the world building from the show: the devils and
   what they are (a devil is the fear of its name), how contracts and devil hunters work, the looks. The quests,
   encounters and events in the mod are our own and don't have to replay the anime's plot (the Eternity Devil keeps
   its hotel as a lair and boss arena, but the quest round it and its contract power are new).

(There was once a rule against naming the mods in public content; kortev dropped it on 2026-10-20: naming them is
fine.)

## What the mod does now (all pushed)

- **Pochita** (`chainsawman:pochita`): an item, carried anywhere in the inventory. Anyone can craft it (the owner lock
  was removed in phase 0). Feeding him, merging and the den hint are open to everyone unless an owner is set:
  `/chainsaw owner <player>` (op only, kept with the world; `clear` unsets; `Owner.java`). **kortev's SMP needs
  `/chainsaw owner kortev` run once.** Recipe: chains, orange wool, a nether star.
- **The cord key (G)**: with Pochita carried, pulling the cord transforms you; pulling it again turns you back (the
  engine sputters and dies). Losing Pochita, dying or leaving also turns you back. The server owns the state
  (`form/Form.java`) and tells every client (`FormPayload`, `FormPullPayload`).
- **The form's look**: your head replaced by the chainsaw head, chainsaws through both forearms, a starter cord on the
  chest. Your own head, hat layer, helmet and anything worn on the head are hidden. The arm saws also show in first
  person. The chains creep round their bars all the time and race when you swing.
- **The transformation is a ~3 s cinematic** (`form/FormTimeline.java` holds every beat, in ticks; the server, the
  pictures and the sounds all read it):
  - 0: the camera cuts round in front of you; your hand comes up to the cord;
  - 4-12: one long yank, the cord stretched from chest to hand (drawn by `FormFeature.cord`), head snapping back;
  - 13: the cord snaps back; 14 and 17: the engine catches and coughs (smoke, camera jolts);
  - 20 (BURST): the form comes on; the head appears in a spray of blood and its saw grows out to full length;
  - 23-32: the arm saws push out through the fists and grow; the arms brace out (camera low and wide, the poster's
    angle);
  - 36 (ROAR): hard cut to the face, FOV punched in, anime **impact frames** (the picture in flat red/black/white,
    then its negative, with speed lines; shader `csm_impact`) and a red vignette; arms flung wide, head thrown back;
    anyone within 16 blocks feels a shake;
  - 52-62: the camera eases back into first person.
  Keys and look are held during it. Everyone nearby sees the body animation, the cord, the growing saws and the
  blood; only the puller gets the camera, zoom, impact frames and vignette.
- **Sounds** (synthesised, `tools/gen_chainsaw_sounds.py`): the cord rip, the engine start cut to the timeline
  (catch, cough, burst, roar, settling to idle), the idle loop while transformed, the engine dying; and for the fight:
  slash, hit, grind loop, chain, frenzy kick and its loop, going down, coming back up.
- **The fight** (built 2026-10-19 with the numbers kortev picked; all in `form/`, the server owns it, `FormEventPayload`
  tells every client what to show):
  - **Saws** (`Saw`): the attack key in the form is a slash, not a punch: a combo of three (right arm across, left arm
    back, both overhead and down), 10 damage each, the third 14 with more knockback, through everything in a cone in
    front (3.5 blocks), at most one every 12 ticks. **Held**, the attack key grinds: both saws out, 2.5 damage every 4
    ticks to what is in front, slowing it. On a block, holding it mines (cutting through). Blood and sparks on every
    hit. Armour stands don't count (nothing to drink).
  - **Blood healing**: a slash that hits heals half a heart, every third grinding bite that hits half a heart, a kill 3
    hearts. **Health drain**: in the form you lose half a heart every 3 s.
  - **Never dying** (`Downed`), with Pochita carried, in the form or not: what would kill you leaves you down instead:
    lying flat (the crawl pose), arms and legs gone (raw stumps), unable to move, fight or use anything, and nothing
    hurts you. Someone crouching by you and holding use on you for 3 s gives you 3 hearts of their own and you're back
    up; alone, you come back after 10 minutes. Saved with the player (a data attachment), so leaving doesn't skip it.
    The void and `/kill` still kill. The drain running out puts you down too. This replaced "revive once".
  - **Chain** (`Grapple`, key R): fired the way you look, 24 blocks, catching anything within half a block of the
    aim; a living thing is yanked to you (2 damage), a block pulls you to it (no fall damage after); every 3 s. Drawn
    as steel links (`ChainRender`, `form_chain.png`).
  - **Blood frenzy** (`Frenzy`; no longer on a key: Beam replaced it as the ult, 2026-10-22): 15 s of double saw damage, 4 times the healing, no Slowness, cobwebs and berry
    bushes don't slow you; the screen pulses red, the engine screams, the eye throws sparks. Then 5 minutes to recharge.
  - **Animations**: third person `SawPose` (combo, grind, chain throw, frenzy stance); first person `FirstPersonSaws`
    shows both saw arms and plays the same moves (`FirstPersonFormMixin` takes over the hand; eating or a shield
    still shows normally).

### Second round (2026-10-19, kortev's picks from a list of improvements)

- **Heavy hits**: a hit holds the swing still a moment (everyone sees it), kicks your camera, punches the view in.
- **Brutal kills**: a mob killed by the saws bursts into blood, flesh and bone and is gone (`Saw.tearApart`). Not yet:
  actually cutting the body in two.
- **The lunge**: attacking while sprinting throws you forward headfirst, the head's saw cutting what you run
  through (12 damage, once each; 10 blood).
- **The chain held** (`Grapple`): what it catches is yanked to you and bound within 4.5 blocks for 5 s; caught in a
  block you **swing** from it (the client moves you: `ClientCombat.swing`), reeling in, until you jump or press R.
- **His stance** (`SawPose.stance`): arms a little forward and out standing; bent in, arms back, running.
- **Blood as fuel** (`Blood`, bar over the food bar, `BloodHud`): 0 to 100, kept with the player. Gains: 8 a hit, 3 a
  grinding bite, 25 a kill, and out of the form a point every 2 s. Costs: the cord 25 (the engine won't start
  without it), the chain 15, the lunge 10, the frenzy 60. These replaced the chain's and the frenzy's cooldowns.
- **Losing arms** (`Limbs`): in the form every 6 hearts of harm takes an arm off (right, then left), its saw with
  it, a stump at the shoulder. One arm: three quarters damage; none: the head's saw alone, half. A kill grows one
  back; out of the form or back up, both are back.
- **Pochita the animal** (`PochitaEntity`, `PochitaModel`): rare in the wild (forests, plains, meadows), hurt; tamed
  as Denji did, by giving him your blood (hold use on him, 4 s, half a heart a second; kortev only). Tamed he follows
  and sits; crouch + use picks him up as the Pochita item, a chainsaw weapon (`PochitaItem.weapon`); used on the
  ground he's set down. **Carry him when you die** (kortev, first time) and he becomes your heart (`Heart.MERGED`,
  kept for good): back whole, full of blood, transforming. From then on only the heart matters: the cord and never
  dying are the heart's, not the item's. The crafting recipe is gone.
- **To try it on a PC**: `./gradlew runClient` (plays as kortev); survival, cheats on; `/summon chainsawman:pochita`
  (or the spawn egg), hold right-click on him till he's yours, crouch + right-click to pick him up, then
  `/damage @s 100` to merge (not `/kill`: that one still kills). Then G transforms.

## The design kortev approved (don't change it without asking)

### The powers (all built; the numbers are above, chosen by kortev: both kinds of attack, medium drain, downed with
a way out, the frenzy every 5 minutes)

- Transform: Pochita carried + the cord key.
- **Chainsaw attacks**: heavy slashes and holding to grind, with new attack animations instead of a punch (kortev
  asked for the form's combat to be a full overhaul of the game's).
- **Blood healing** (the passive) and the **health drain** (the cost, what keeps it fair against the friend's Domain).
- **Chain grapple**: to blocks, or yanking a player or mob to you.
- **Never dying**: kortev's own idea, replacing "revive once": down instead of dead until someone gives you blood.
  Overpowered (you can't die) and a real weakness (alone you're stuck for 10 minutes).
- **The ult: blood frenzy** (replaced by Beam, 2026-10-22; see "Start here"). **No cutscene** (kortev: a frenzy doesn't need one), just the kick as it starts. (The
  "shark ride" ult from the Reze arc was liked as a possible second ult: it became the ult, Beam.)

### The look (decided after several rounds; keep it)

- **The head** is a chainsaw's engine with a face, built from anime stills kortev sent (they are not in the repo:
  the description is in `tools/chainsaw_model.py`'s docstring and its constants). An **orange visor shell over the
  front of a black engine** (the orange goes on the outside), four curved vent slots on each side whose inner two are
  the glowing yellow eye, a ridge along the top, a jaw plate that is a V from the front and ends in a downward point,
  long straight white teeth in an open mouth (a dark hole behind them, not a wall), the neck and cheeks bound in
  wire tendons, and on the black engine: notched side covers with a round socket, a ledge, a nub, a screw, a short
  chain hanging down each side behind the visor, stepped armour plates on top, a bolted back plate, a finned
  cylinder, ribs down the back of the neck, a rear D handle, a pole sticking up behind the bar, hoses. The bar comes
  straight out of the front of the visor, long, white and bloodied.
- **The arm saws**: centred on the arm, the blade's width running **top to bottom** (teeth along the top and bottom
  of an arm held out, flat faces to the sides), starting just below the shoulder, coming **through the middle of the
  fist** (the fist split, bloody gashes), and reaching far past it.
- **Style**: flat anime colours with dark ink lines, at chunky pixel density to sit with a Minecraft skin. kortev
  rejected the earlier realistic, scratched, grimy baked textures outright ("do NOT fit minecraft AT ALL OR even the
  style of csm"). The game texture is now painted straight from the mesh (see below), not rendered.
- **Not too boxy, not too smooth**: a middle ground; rounded, bevelled shapes on a blocky body.

### kortev's skin

They're redrawn in the Public Safety uniform, keeping their own hooded shark-onesie head: `tools/kortev_skin.py`.
Their original skin is `tools/skins/kortev_onesie.png`; the final one they settled on is
`tools/skins/kortev_public_safety.png`, made with
`python tools/kortev_skin.py tools/skins/kortev_onesie.png OUT.png --hood --under`. kortev disliked two earlier
attempts that invented a new hair shape: if you touch the skin again, stay close to their own pixels and ask first.

## How it's built

### Files (`chainsawman/`)

| Piece | Where |
|---|---|
| Mod entry, item, sounds | `src/main/java/.../ChainsawMan.java`, `PochitaItem.java`, `ChainsawSounds.java` |
| Server state, timeline | `form/Form.java`, `form/FormTimeline.java` |
| Payloads | `network/PullCordPayload` (C2S key), `FormPayload` (on/off), `FormPullPayload` (someone pulled) |
| Client entry, key | `src/client/java/.../client/ChainsawManClient.java` |
| Who's in the form, pulls, idle sound | `client/ClientForms.java`, `FormIdleSound.java` |
| Drawing the form | `client/FormFeature.java` (feature renderer: parts on bones, chain frames, growth, the cord), `FormMesh.java` (loads `form.csm`) |
| Cinematic | `client/FormCinematic.java` (beats' particles, your camera shots, shake, input hold, impact/vignette timing), `CordPull.java` (body poses), `FormHud.java` + `shaders/core/csm_impact.*` |
| Mixins | `client/mixin/`: hide head/hat/helmet/head items, first-person saws, pose, camera, FOV, the attack key (`FormAttackMixin`), the downed body; common `mixin/`: the downed pose, the frenzy ignoring slowing blocks |
| The fight (server) | `form/Saw.java`, `Downed.java`, `Grapple.java`, `Frenzy.java`; `network/FormActionPayload` (C2S: slash, grind, chain, frenzy), `FormEventPayload` (S2C: what everyone should see) |
| The fight (client) | `client/ClientCombat.java` (state, keys, loops), `SawPose.java`, `FirstPersonSaws.java`, `ChainRender.java` |
| Tests | `src/gametest/.../FormGameTests.java` (the fight, server side); `FormSelfTest` photographs it (`fight_*`, `fp_*`, `tp_downed*`) |
| Assets | `form.csm` (mesh), `textures/entity/form.png`, `form_flesh.png`, `form_chain.png`, sounds, lang |

### The model pipeline (`tools/chainsaw_model.py`, Blender as a Python module)

- Needs Python 3.11 with `pip install "bpy==4.5.*" numpy pillow` (keep the venv outside the repo). kortev's PC has
  Blender 4.3 instead, which runs it as `blender -b --factory-startup --python-use-system-env --python
  tools/chainsaw_model.py -- --game` (Pillow put on `PYTHONPATH`), but **4.3 unwraps the painted parts differently
  from 4.5**: a full 4.3 export changes the approved look. On 4.3, export to a scratch copy and splice only the parts
  you changed into the committed `form.csm` (the chains were done that way; their swatches sit in the same place).
- `--out DIR --renders [--only hero,front,side,head,head_side,head_back,arm_saw]` renders preview stills (Cycles,
  realistic materials, for judging shape). `CSM_SAMPLES=12` for a quick look.
- `--game` builds the rest pose, unwraps, **paints** the texture (`paint_toon`: flat colour per material from the
  `TOON` table, a light/shadow tone cut against `TOON_LIGHT`, ink on edges sharper than `INK_ANGLE`, banded cords,
  blood splats from 3D noise; small many-pieced parts take flat swatches instead, `SWATCHED`), and writes
  `chainsawman/src/client/resources/assets/chainsawman/meshes/form.csm` and `textures/entity/form.png`. It takes
  about 15 seconds. Commit both outputs.
- `form.csm` format (CSM2) is documented in `export_game`'s docstring: parts with the bone they follow (`head`,
  `body`, `right_arm`, `left_arm`), an optional grow root/axis for saw parts (`*_bar`, `*_chainK`), and quads.
  Each chain is exported in 12 frames (`*_chain0..11`), each a quarter of a cutter further round its bar; the game
  shows one at a time to run the chain (it counts the frames from the mesh). Their blood is on the links, not on a
  place (`chain_tones`, `CHAIN_BLOOD`: a fleck of blood on some links, about as much as before), so it runs round with the chain; the
  12 frames are the chain moving one whole pattern on, so they loop without a jump. A one-quad part named `rope` only carries the cord's colour.
- Coordinates: the build is in skin pixels, feet at the origin, facing -y, z up; `to_game` maps to each bone's
  model space.

### Sounds

`python tools/gen_chainsaw_sounds.py [form_cord form_start form_idle form_stop]` (NumPy, SciPy, ffmpeg; uses
`tools/gen_sounds.py`'s helpers). Its beat constants mirror `FormTimeline` and `MergeTimeline`; if you retime a
timeline, change them too and regenerate. Commit the `.ogg`s. (Writing a long track, like the 31 s `form_merge`, in
one go crashed libsndfile here without a word; the script writes in chunks.)

### Testing

- **CI is your compiler**: push and read the `build` workflow (`build` + `gametest` jobs). The `build` job also runs
  `.github/scripts/check-jars.py`, which checks each jar holds its own assets and a refmap for every mixin config.
- **Seeing it**: run the `selftest` workflow by hand with `test` = `form`. It photographs a stand-in of kortev (their
  skin from `tools/skins/`) in the form from all round, with the form off, the stand-in pulling the cord frame by
  frame (`form_pull_NN.png`), and **your own transformation from your camera** (`form_cine_NN.png`), plus a
  first-person shot. Screenshots come back as the `selftest-screenshots` artifact. The test is
  `src/gametest/java/.../test/FormSelfTest.java`. (The game never draws your own player while the camera is another
  entity, so the test films a client-side stand-in; it also forces every outer skin layer on, which a stand-in
  otherwise never shows.)
- On a PC: `./gradlew runClient` plays as `kortev` and wears `run/skins/kortev.png` (`DevSkins`, only active with the
  `-Dshootingstar.devskins` flag the dev run sets; `seedSkins` puts the Public Safety skin there if nothing is).
  `./gradlew runSelftest -Pselftest=form -Pusername=kortev` runs the test locally; it makes its world first, as CI
  does (`selftestWorld`), so it quick-plays straight in. `-Ptarget=` films one part only: `merge` (the merge by
  tick, `form_merge_wait_*`, the pull, then turning back `form_back_NN`), `cine` (your own transformation),
  `ring` (the form from round about), `meshhead` (the same with the old mesh head), `limbs`, `pvp`, `kill`,
  `chain`, `scene`; add `-Pcapture=true` for a video of it.
- Through your own transformation your hand, the HUD and pop-ups are hidden until the camera is back in your eyes
  (`FormHandHiddenMixin`, `FormHudHiddenMixin`, `FormToastHiddenMixin`; the impact frames still draw). The test films
  it with the HUD on to show that.
- **Trying it all in the game** (`ChainsawCommand`, operators): in a new world with cheats on (superflat is best:
  it flattens and clears a 33-block square round you), `/chainsaw scene` builds a test ground (a row of mobs standing
  still in front of you, a wild Pochita fainted under a fallen tree to your right), puts you in survival with full blood and
  without Pochita's heart, and lists what to try. Then `/chainsaw mobs | pochita | heart | reset | blood | down | up`;
  and `/chainsawhead boxes | mesh` (client) switches between the two heads. Film it with `-Ptarget=scene`.
- Yarn 1.21.1 names differ from Mojang's; check a doubtful one against the mappings before pushing.

## What's next, in order

### 1. A polish pass on the transformation (kortev said yes to all of this)

1. ~~**The head grows instead of swapping**~~ **done** (PUNCH, HEAD_OPEN; `FormFeature.punch`, `split`): for a few ticks before BURST, the bar punches out through the forehead of
   the player's ordinary head, then the head splits open in the blood and the chainsaw head is there. (Today it is
   swapped inside the blood burst and swells the last bit into place.) The human head is the player model's own; a
   way in is to keep it drawn until BURST and draw `head_bar` (grown from its root) on it from ~BURST-4.
2. ~~**Blood on the lens**~~ **taken out of the transformation.** It was built (flat chunky splats on the puller's
   screen at the burst, dripping, dissolving pixel by pixel before the roar) and kortev tried it: in the
   transformation it looked bad and arcade-y and isn't needed. **But kortev wants it kept** as a possible start for
   how the gameplay looks: blood on your screen when you kill things with the saws. The code is saved in the history:
   `git show e85a346:chainsawman/src/client/java/io/github/kortev/chainsawman/client/FormLens.java` (it was drawn
   from `FormHud.render`). Ask kortev before using it, and rework the look rather than reusing it as it was.
3. ~~**Manga sound-effect lettering on the impact frames**~~ **done**: ヴゥン in heavy black pixel strokes, white
   outline, blood-red shadow (`textures/gui/form_roar.png`, painted by `gen_chainsaw_textures.py`'s `roar`), each
   letter slammed in a tick after the last round the head (never over the face), shaking, gone 16 ticks after the
   roar (`FormHud.lettering`, in your own transformation only).
4. ~~**The eye ignites at the roar**~~ **done**: dark through the transformation, then at ROAR a pixel star flares on
   each eye and dies away (`FormFeature.flare`, `textures/entity/form_flare.png`, drawn added on; the eyes are the
   mesh's glowing vertices, flag bit 0). In a frenzy the eyes burn red, throbbing.
5. ~~**The cord on the chest all the time**~~ **done** for whoever has the heart: the server tells everyone who
   has it (`FormEventPayload.HEART`, on joining and at the merge), and `FormFeature` draws the cord on their chest
   until it is grabbed. (Still open: making the stretched cord read better in the puller's own camera.)
6. ~~**Sparks and exhaust**~~ **exhaust done**: light grey puffs off the top of the pole behind the bar, now and then
   idling, more while the saws work or in a frenzy, a burst at the roar (`ClientCombat.exhaust`; black smoke read as a
   blob of hair on the head, so it's the game's white smoke). Sparks come off what the saws hit (the server's hit
   particles); none off the chains in the air.

(Also offered, not yet chosen: a voice clip at the roar, which kortev would have to pick, as the Domain uses a clip
they chose.)

### 2. ~~Turning back~~ done

The cord pulled again in the form turns you back (`FormTimeline` BACK_*, `Form.TURNING`, `FormEventPayload.TURN`):
the engine dies, the arms' saws slide back in through the fists while the arms are held out shaking, the head bows
as its saw goes in, and at BACK_SPLASH (14 ticks) the chainsaw head comes away in a splash of blood, your own head
thrown back. No fighting while it plays. The merge's chat line now says the cord does both.

### 3. The abilities: built (see "The fight" and "Second round" above). Next is kortev trying them in game and
tuning the numbers.

### Pochita, settled with kortev (2026-10-20)

- **One per world** (`PochitaPlace`): placed once, fainted under a big fallen tree in woods or grassland 250-450 blocks from spawn (see "Start here"); kortev
  hears him whimpering within 40 blocks; lost for real (void, /kill), he's back there 2 minutes later; never again
  once he's someone's heart. No natural spawns, no recipe.
- **Can't die**: faints (on his side) until kortev gives him blood again (`PochitaEntity.WAKE`).
- **Merging**: the first death with him **carried or tamed within 32 blocks** (`Merge`) plays the anime's scene, long
  (about 31 s), its beats in `MergeTimeline`'s table, the pictures in `MergeCinematic`, the sound in `form_merge.ogg`.
  You go over and the colour drains; then **the dream** (`MergeCinematic.dream`): the world falls away into black fog
  (`DreamFogMixin`, no sky or clouds `DreamSkyMixin`, nothing else drawn `DreamEntitiesMixin`), you lying in a pool of
  light (`dream_floor.png`), motes drifting. Pochita limps out of the dark, sniffs you, climbs onto your chest and
  they talk (the anime's voices); over his "I always loved hearing you talk about your dreams" come **the memories**
  (`MergeMemories`): four little scenes built of real blocks floating far up in the void, sepia (`csm_impact`'s
  Sepia), you in your own skin and him in his model: the shack holding him, bread with him looking up, asleep with him
  curled beside you, him as the chainsaw against a zombie. Then he springs up off you and plunges into your chest,
  leaving his tail, the cord, sticking out of you; your heart starts in the black; you wake through your own eyes; you
  get up and stand there with "[G] Pull the cord" until you pull it yourself, as Denji does (first pull free; after
  `Merge.WAIT`, 60 s, it pulls itself).
- **The merge's third pass, from a storyboard (2026-10-20)**: kortev wants the merge to be close to the best part of
  the mod, and found it full of problems (the camera inside heads and blocks, Pochita floating over you, a confusing
  see-through heart shot, a weak start and end). I drew a storyboard of 18 cuts (an artifact:
  https://claude.ai/artifact/1oJPh3jRN7Um3y6a9Z6Asq), kortev said go, and `MergeTimeline`'s table is now those cuts.
  Every shot goes through `MergeCinematic.framed` (pulled in toward what it looks at if a block is in the way) and is
  placed a step off you and him; when the camera is in your own eyes (`ownEyes`) your head isn't drawn
  (`HeadHiddenMixin`), which is what the grey blur waking up was (the inside of your hood). Pochita stands on your
  shirt (`SURFACE`, toward your middle at `ON` so his head is clear of your face), nudges your cheek with his nose
  (`HEAD_HALF`, `SNOUT`), sits to listen with his head on one side (`Pose.roll`, `sitting`). The see-through chest
  (`SeeThroughMixin`) is gone: he plunges in nose first (PLUNGE) and the cord is left (SUNK). Your heart starts in
  the black, three beats (`BEATS`), each a red flash with ドクン lettered (`Sfx.HEARTBEAT`, new glyphs in
  `form_sfx.png`); your eyes open on the real sky (EYES, the gasp at GASP); you rise (RISE, 18 ticks); and the last
  cut eases in on the cord. `form_merge.ogg` was regenerated to the new beats.
- **The merge staged again, for feeling (2026-10-20)**: kortev found the whole thing (talking, flashbacks, all of it)
  silly, though not too long. Up close, Minecraft's blocks read as comedy: so no close-ups of Pochita's face, no
  jaw flapping (his jaw rests a little open; `PochitaModel.lie` folds his legs), and he never stands on you. Wide
  shots in the dream's dark (its fog hides all past about 11 blocks, so he comes out of it); lit by your light as he
  nears you (`render`'s light by distance); he noses at you and lies down against you, chin on your chest
  (LIES), lifting his head to your face as you speak; up at your side for the deal, nodding with his words
  (`mouth`), a warm light behind him (`glow`, `dream_glow.png`) and his front in shadow; the flashbacks dimmer, wider,
  a warm light behind them. It has no music: music was tried (chords and a piano) and kortev
  wants it to feel empty, just you talking to him; only the dark's low hum under the voices.
- **The merge's second pass (2026-10-20)**: Pochita's mouth moves with his voice (`MergeCinematic.VOICE_LEVELS`, the
  clips' loudness tick by tick: measure them again if the clips change), his tail going, leaning in on "show me your
  dream", your head lifting to him as you speak; the memories move (rocking him, bites of bread, breathing, the saw
  shaking into the zombie, blood) under an old film's flicker, grain and dark edges (`csm_impact` Film), a flash at
  each cut; the dream has its own sound (a soft chord, a music box under the memories, a whoosh as he leaps, deep
  muffled beats inside your chest, the world rushing back and a gasp).
- **The voices in the merge** are the anime's own (Japanese), cut from a clip kortev chose
  (`sounds/form_merge_voice_1..5.ogg`): you ask if he took your body; he loved hearing your dreams; this is a
  contract; show me your dream; and you crying his name as he dives at you (at SINK). `MergeTimeline.LINES`,
  `LINE_LENGTHS` and `POCHITA_SAYS` time them as the clip paces them; the captions (`merge.chainsawman.line1..5`)
  are kortev's English, his warm and yours white. The clip says "this is a contract" where the caption also has
  "I'll give you my heart".
- **His look**: a Minecraft animal, boxes and pixels (`client/PochitaModel.java`, texture painted by
  `tools/gen_chainsaw_textures.py`'s `pochita_entity`, 64x64), after kortev judged the smooth anime render didn't fit
  the game. `tools/pochita_model.py` (Blender, anime style) stays only as a reference.
- On the black chainsaw kortev asked about: it is past the Reze arc; told only "Part 1, read on from chapter 53"; build
  nothing of it until kortev describes what they want from it.

### The fight's look, polished (2026-10-20, my passes while kortev slept)

- **Changed after kortev played it (2026-10-20)**: the lunge is **right-click** at nothing or at a mob (sprinting into
  an attack kept lunging by accident; at a block or sneaking, right-click does what it always did); the form's drain
  is halved (half a heart every 6 s) and the form makes you tough (10 armour, 6 toughness, `Form.ARMOR`), as you
  went down too easily; the chain is the saw's own chain (`form_saw_chain.png`, teeth and all) off the end of the
  right saw, whipping out in a wave, taut when it catches; caught on a block it zips you straight there
  (`ClientCombat.swing`) instead of swinging.
- **From the show, kortev's picks (2026-10-20)**: the life steal stays how you drink blood (kortev: Denji drinks it
  fighting); **arms grow back** from it (`Limbs.drank`: every 25 blood drunk cutting, a kill's worth, brings a lost
  arm back, growing out of the stump on screen); **you get bloodier** as you fight (`ClientCombat.bloody`: hits and
  kills soak the form darker and redder, dripping; water and rain wash it off); **you faint from blood loss**
  (`Blood.FAINT`): turned back with under 30 blood you're slow and the world drains grey and dims at the edges with a
  slow beat, till you've made 50 again; eating makes blood (`EatBloodMixin`, 3 a point of food).
- **The door in your dreams** (season 1; `DreamHud`, `textures/gui/dream_door.png`): down, the dreams alternate
  between Pochita and a dark corridor with a door at its end, light leaking round it, Pochita in front of it: "Denji...
  don't open that door."
- **Blood on the lens for kills** (`LensBlood`, `textures/gui/lens_blood.png`), reworked from the transformation's
  (saved at e85a346) as kortev asked: a kill within 4.5 blocks, in your own eyes, throws 1 to 4 dark pixel splats onto
  the edges of the picture (never the middle), sliding a little down and gone in about 2 seconds.
- **Sound effects lettered** (`Sfx`, `textures/gui/form_sfx.png`, glyphs painted in `gen_chainsaw_textures.py`):
  your kills ブシャッ, the overhead chop landing ザシュッ, grinding into something ギャリギャリ, slammed onto your
  screen off to one side, tilted and shaking, gone in a moment. A new word needs its glyphs drawn there (16 by 16).
- **From the show, my picks of the set pieces (kortev: "do what you want")**: the saws **cut through blocks** ten times
  as fast as hands in the form (`FormDigMixin`, `Saw.CUTS`; not obsidian and harder), to tear through a building; the
  **kick** (V, `Saw.kick`): Denji's dirty kick, short, knocking back and dazing (slowness) what it hits; **dragged
  under**: something caught on your chain, in water with you, is pulled down and held under, drowning (as he drags
  Reze into the sea; `Grapple` tick). The drag isn't filmed yet; Beam the shark (a second ult) is left for later.
- **Slash trails** (`SawTrails`, `textures/entity/form_trail.png`): each slash leaves a smear behind the saw's tip,
  white at the edge going to blood red, gone in 3 ticks; sampled from where the bar is drawn each frame, so it follows
  the real arc. In your own view it is shorter and reaches further down the bar, or it reads as a thin line.
- **The lunge** sells its speed: the head's saw leaves the same smear, your view stretches wider, and white speed
  lines stream in from the edges of your screen (`FormHud.speedLines`, `ClientCombat.lunging`).
- **The frenzy has a look from outside** now: the red eyes, a red mist of blood rising round the body, the exhaust
  pouring. (Inside it was already the red pulsing screen edge.)
- **Getting up** (`GetUp`, kortev asked for it to look a lot better): no more tipping up like a plank. Back up after
  being down, your arms and legs grow back out of their stumps in blood as you push up on your hands, get a foot
  under you and stand (`GetUpMixin`, `GetUpLeanMixin` stops the game's crawl leaning you as well; the server holds you
  still for `Downed.RISE`, 26 ticks). At the end of the merge you sit up with your legs out, rock forward into a
  crouch and stand (RISE to END is now 30 ticks; END moved to 626 and `form_merge.ogg` with it).
- **Limbs come off** (`Severed`): going down, the arms and legs still on you fly off in a spray of blood, spinning,
  land and lie there 4 seconds before a puff; an arm lost mid-fight does the same. Each is the player's own arm or
  leg, their skin and sleeve, drawn on its own.
- **No zoom while held still** (`HeldStillFovMixin`): down or merging, your speed is taken away and the game zoomed
  your view in by half, as it does for slowness; every merge shot had been framed against that.

### The head: boxes, chosen (2026-10-20)

kortev chose the box head (`FormHeadModel`, painted by `gen_chainsaw_textures.py`'s `form_head`, its eye lit by a glow
layer like a spider's; the saw stays the mesh's) over the mesh's, "a tiny bit less boxy": **not smaller** (a scaled
down try was wrong), but the visor's top bevelled in from the sides and stepping down from the brow to the engine,
the ridge running down the slope, the prow narrowing to the mouth, the engine's top edge bevelled. It's on by
default; `/chainsawhead mesh` (client) and the selftest's `-Ptarget=meshhead` show the old one to compare.

### Round of 2026-10-20 (kortev on their phone: reviewed by video)

- **Mobs leave you be while you're down** (or dead while Pochita comes): any mob after you gives up as you go down
  (`Downed.forgotten`), and none can take you for a target till you're up (`DownedUnseenMixin`, on
  `PlayerEntity.canTakeDamage`, which the game's targeting asks).
- **The missing merge line**: "I always loved hearing you talk about your dreams" plays as the camera cuts to the
  memories, 90 blocks over your body, so it was out of earshot (and the dream's music with it). While your own merge
  plays and the camera is far from your body, you hear from your body (`DreamHearingMixin`,
  `MergeCinematic.hearing`). (The clip itself never says "I'll give you my heart": the caption does.)
- **Arms lost leave bleeding stubs** (`Stumps`, texture `form_stump`): the upper arm's stub (`STUB` of it), its end
  torn round the bone, gushing as it comes off, then spurting with the heartbeat and dripping; in first person the
  stump where that hand was, tipped so you see its end, drops running off it, and a splash of blood on that side
  of your screen (`LensBlood.lost`). Arms grow back out of the stubs. The server's burst as an arm comes off isn't
  sent to the one losing it (it went off inside their head).
- **The attacks, for PvP** (kortev picked all four):
  - *Instant swings*: your slash swings and sounds the moment you click (`ClientCombat.slashNow`, predicting the
    combo step as `Saw.slash` will); the server's SWING only corrects the step. The server plays the slash sound to
    everyone but the slasher. A click up to `BUFFER` ticks early slashes the moment it can; the server takes a
    slash up to `LAG_SLACK` ticks early.
  - *Hits that land*: the slash sends what's under your crosshair (`FormActionPayload.target`); the server hits it
    too if it's in front, in sight and within the reach plus `AIM_SLACK` (`Saw.aimed`). You step in after whoever
    you slash (`stepIn`). A player hit by the first two slashes keeps only `COMBO_HOLD` of the knockback; the
    finisher launches them (about 6 or 7 blocks).
  - *Impact*: a saw biting into a player (or any finisher) sends IMPACT: sparks, the new `form_crunch` sound, your
    swing held `IMPACT_PAUSE` ticks and your camera kicked hard (`kickSize`); the one bitten is jolted.
  - *Saws beat shields*: a raised shield (anyone's, any mod's blocking item) is cut through and knocked out for
    `GUARD_DOWN` ticks, as an axe does (`Saw.breakGuard`), with the break sound.
- **Videos for kortev**: the form self test records its part as a video with its sounds, `-Pcapture=true` (needs
  ffmpeg on the PATH; with only Python's imageio-ffmpeg, copy its exe in as `ffmpeg.exe`, and `mux-audio.py` wants
  `ffprobe` too, or read the length off `ffmpeg -i`). Then `.github/scripts/render-loops.py` and
  `.github/scripts/mux-audio.py build/selftest/capture out.mp4`. New targets: `limbs` (arms lost and grown back) and
  `pvp` (a stand-in player, `Rival`, with a shield: the combo, first person then from behind). The recorder now
  judges sounds from where the game hears, not the camera.

### Reels: anime scenes recreated in the mod, for content (kortev's idea, 2026-10-20)

kortev wants a run of short vertical reels, each an anime scene recreated in the mod and fully animated (scripted,
not played live). Reel 1 is episode 1, from the storyboard kortev approved (an Artifact, "Reel 1: Betrayed"), then
restaged as kortev remembers the episode: no chainsaw fight; Denji and Pochita stabbed in the back at the same moment by
two of the yakuza; the Zombie Devil rising; the yakuza turning; the zombies finishing Denji off on the floor; dragged
out and thrown in a dumpster with Pochita; dead a while in the dark of it, his blood dripping onto Pochita, who stirs
just enough; the mod's own merge; the lid bursting open and Denji rising with the cord in his chest; then (kortev:
"have the chainsaw stuff too, worst case we cut it later") he pulls it, the mod's own transformation plays, and as
Chainsaw Man he cuts down the zombies by the bin and the ones out of the back door (the form's own slashes, the kills
cut in two), walks in, leaps and cuts the Zombie Devil in two in a rain of its blood. No voices but the merge's. About
96 seconds; the chainsaw part from 80 s, to cut if kortev wants.

- **Run it**: `./gradlew runSelftest -Pselftest=reel1 -Pusername=Denji` (with ffmpeg on the PATH and
  `-Pcapture=true` to record it; then `render-loops.py` and `mux-audio.py` as for the form's videos). `-Ptarget=cast`
  photographs the cast and the Zombie Devil, `-Ptarget=set` the set. It saves `reel_frame_<tick>.png` stills along
  the way, to look over without the video.
- **All of it is test code** (`src/gametest/.../test`): `ReelSelfTest` (the script: each cut's blocking, camera, lens,
  sounds and events, by tick), `ReelSet` (the shack, the warehouse, the alley, built high over the clouds at night in
  the rain, with its lamps), `ReelZombieDevil` (its own model, `tools/reel_textures.py`), `ReelDumpster` (drawn, its lid
  swinging), and mixins for the stand-ins' poses (`ReelPoseMixin`, `ReelBodyMixin`), no names over heads, no HUD but
  the mod's lettering, lens blood and the merge's bars, the lens per cut, and the set gone in the merge's dream (no
  blocks, clouds or rain). The actors are client-only stand-ins in skins from `tools/skins/reel` (Denji in kortev's
  onesie); you're swapped in for Denji's stand-in in the dumpster, for the merge.
- **What the mod opened up for it** (no change in play): `Sfx.show`, `Severed.off`, `CutDown.killed` (a film's
  stand-in can be cut down), `LensBlood.filmed` and `splash` (blood on a film camera's lens), `MergeCinematic.prompt`
  (the end's "Pull the cord" off), and the merge's lens opened up when filmed tall.
- **Waiting on kortev**: their notes on the cut (and whether the chainsaw ending stays). No music (kortev let it go).

### 4. Still to do from kortev's picks (agreed, not built yet)

- **The transformation polish** above still open: the stretched cord in your own camera.
- Done from the smaller follow-ups: **kills cut the body in two** (`CutDown`, `CutInTwoMixin`): the server still
  takes the body away with its burst of gore, but the client keeps it a moment and draws it as two halves, split
  where the model's parts hang from (the legs below the middle, the rest above): the top sliced off sideways, tipping
  and falling, the legs folding a beat later, blood pouring out of the cut, then a puff. A mob in one piece just
  falls. Film it with `-Ptarget=kill` (a husk through the whole combo, then a cow). The wild Pochita lies fainted
  under his fallen tree; taming him, or bringing him round, plays `pochita_tame`.

### Later ideas kortev has had (not designed; ask before building)

- **The merge dead a while first** (2026-10-20): Denji lying dead for a moment before Pochita comes, as reel 1 has it
  in the dumpster (his blood dripping onto Pochita, who stirs).
- **Blood on the lens for kills**: see the polish list above; saved in the history.

### Later: Pochita as a real animal (kortev's idea, 2026-10-19; not designed yet)

kortev wants Pochita to stop being a crafted item:

- Pochita is **an animal in the world that you tame**.
- A tamed Pochita can be used as an **ordinary chainsaw weapon**.
- If you **die while you have it**, you come back **as Chainsaw Man**, the way it happens in season 1. You no longer
  need the item to be the form.

This changes the decided design above: how you get Pochita (no recipe), what starts the form (death rather than only
the cord), and the "revive once" power, which it largely becomes. Before building any of it, ask kortev the open
questions: where Pochita spawns and how you tame it; whether the cord still transforms you after that first death;
what happens to Pochita (the weapon) once you have merged; and whether only kortev can tame it, as only kortev can
craft it now.

## Not checked yet

- The sounds by ear in the game (only checked as files).
- The form and the impact frames with a shader pack (Sodium + Iris + Complementary). Full-screen and in-world effects
  must go through `WorldPasses` or the HUD, never straight on `WorldRenderEvents.LAST` (a pack paints over that).
  `FormHud` draws in the HUD pass.
- Multiplayer: another player watching a transformation (should work: everything is driven by payloads to all), and
  the fight against another player (the downed pose and the stumps are only checked on yourself in third person; the
  self test's stand-in can't lie down).
- The fight's numbers in real play, and fighting with a shader pack.
