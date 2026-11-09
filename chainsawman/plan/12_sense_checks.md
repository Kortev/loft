# Master plan, part: 12_sense_checks

Section 12: the record of the sense checks (history; read only when asked why). The index is `../MASTER_PLAN.md`.
Marks: **[decided]** kortev said so. **[pick]** Fable's choice, waiting for kortev's yes. **[check]** a show fact to
check on the wiki and Google first.

---

## 12. The sense checks so far

Round 1 (Fable, 2026-11-09), after kortev's point about Pochita's den. Checked against the code where the plan said
something exists.

| Found | Why it fails | Fix, now in the plan |
|---|---|---|
| One den per world | Nobody knows where it is; a returned heart lands 2000 blocks off with no hint | Many dens, empty once he's found, a rumour at the office (4.4) |
| Devils at night only | Players sleep through nights | Nests by day, missions at any hour (4.4) |
| The first devil "on the first night near a village" | You may not be near one, or asleep | An event that follows the player (4.1) |
| The first devil kills villagers | Your first village is ruined | It goes for you; villagers only run (4.1) |
| The suit as armour | Nobody swaps enchanted netherite for it | Worn over your armour, its own slot (4.3) |
| The katana | Loses to an enchanted netherite sword | A real sword that takes enchantments and grows with rank (4.3) |
| Devils that heal | A new player with a stone sword can't win | Weak devils heal slowly (4.3) |
| Yen as an item | Lost on death, clutter, copy bugs | A number on the licence (4.2) |
| The licence card | Lose it, lose everything | Saved on the player; the card only opens it (4.2) |
| A new guide screen | One more thing to learn | The advancements tab (4.2) |
| Fear | Dying makes the devil stronger, so you die more | A ceiling, fading, a growing bounty (4.5) |
| Mission devils | Spawned in chunks nobody has loaded | They appear when you arrive (4.2) |
| The partner | Followers get stuck and annoy | Catches up like a wolf, can't die for good, can wait at the office (3) |
| The Nether and the End | The plan said nothing | The Overworld only for 1.0 (4.4) |

Checked and fine: the Eternity price stops you sleeping but the bed still sets your respawn (`EternityBedMixin`).

### Round 2 (three Opus checkers, 2026-11-09)

Three agents who hadn't seen Fable's reasoning each read the whole plan and the code through one lens. 36 findings.
"Code" means the checker read it in the code and Fable spot-checked it; "memory" means a Minecraft fact nobody has
tested yet, to be confirmed by the package that builds it.

**Round 1 got these wrong:**

| Round 1 said | What's true | Now |
|---|---|---|
| Hotels are close enough | One *try* per 1280 blocks, most tries thrown out (code) | More hotels, measured (1.5) |
| A lost-property chest at the desk | A shared chest gets robbed; and alone, dying ends the fight (code) | You're thrown out with your things (4.6, 1.7) |
| The partner catches up like a wolf | That needs ground and loaded chunks, and leaves doubles (memory) | Saved on the player (3) |
| The first attack "in or near a village" | Golems kill it first; players far from villages never see it (memory) | Wherever you are; golems stay out (4.1) |
| Old worlds: a command | No help to a stranger | Placed while you play (4.4) |

**Minecraft:**

| Found | How sure | Fix, now in the plan |
|---|---|---|
| Offices "near villages" can't be generated; posters only reach new villages | memory | The office is a village building (kortev); boards and old villages are handled while you play (4.4) |
| A totem never pops when you go down | code | A totem is used first (4.6, 0.7) |
| The hotel only wakes at night; a skipped night resets a party | code | Any hour for a mission (4.4, 1.6) |
| The sealed hotel leaks: TNT, fire, lava, pistons, portals, chorus fruit, digging in by day | code | Lairs can't be wrecked (4.4, 1.6) |
| Nothing rebuilds a wrecked hotel | code | Rebuilt when the devil returns (1.6) |
| Villagers don't run from new mobs | memory | Added to what they flee (2.2) |
| A Pochita made with the world turns up after he's found | reasoned | Dens generate empty (4.4) |
| The rumour's search can stall a server | memory | Cached, with a limit (2.4) |
| Peaceful doesn't remove the hotel's devil and refills hunger | code | Lairs sleep, hunger prices pause (4.6) |
| Phantoms need open sky and a setting | memory | The price brings its own (4.6) |
| A suit slot needs a second mod | fact | Saved with the licence, drawn by the mod (4.3) |

**Fun:**

| Found | Fix, now in the plan |
|---|---|
| No power for hours | Small deals from beaten devils [pick] (4.1, 3.8) |
| The friend without the heart gets nothing | Small deals; the heart already runs on blood (code) (4.4) |
| Nothing says you merge by dying | kortev: leave it. Players know the show, and you'll die with him sooner or later (4.4) |
| Nothing to do at hour 40; yen runs out of uses | 1.0 stops at 1st Class; late yen uses (4.3) |
| Dying in the hotel locks you out for half an hour | kortev: it's a dungeon, you're thrown out. Your things come with you, and you're paid if your friends win (4.6) |
| A contract is a life sentence | End it at the office for a cost [pick] (4.6) |
| The office is a long walk every time | The phone [pick] (4.2) |
| The Fox is useless in the dark, where devils are | Its fact sheet decides; it can't be useless in caves (M4) |
| With a partner you can't die | One pick-up per fight (3) |
| The first hour has no big moment | The first devil's arrival is staged big (4.1) |
| Missions repeat by hour 10 | Mission kinds, one trick per devil (4.2, 3.6) |
| Fear is unseen alone | Out of 1.0 [pick] (4.5) |
| The katana has no use at 4th Class | Cleaner parts sell for more (4.3) |
| Payday is a number changing | A moment you see and hear (4.2) |

**Other people:**

| Found | How sure | Fix, now in the plan |
|---|---|---|
| Anyone holding Pochita can merge; a dropped one is gone for good | code | Tied to his tamer; goes back to a den (4.4, 0.7) |
| A holder who quits keeps the heart forever | code | Returns after 14 days on a server [pick] (4.4) |
| "Dens live again" would hand the heart to whoever saved a den's place | reasoned | One far den, told to anyone who asks (4.4) |
| One idle player holds a hotel shut; logging out is a free exit; a restart loses the fight | code | A time limit, back inside on return, saved (4.6, 1.6) |
| Logging out while down dodges the finish | code | The body stays (4.6, 0.7) |
| Boss loot is one pile; idle spare accounts multiply the pay | code | Loot per fighter, pay for who fought (4.6, 0.7) |
| `oneContractPerDevil` is per hotel | code | Per world (4.6, 0.7) |
| "No NPC on a server" fails when your friend is offline | reasoned | The partner whenever no buddy is near (3) |
| One board for many players; stolen kills; spare accounts farming the first part | reasoned | Missions per party; pay once per player (4.2) |
| A veteran flattens a newcomer | reasoned | Rank bonuses off against players by default (4.6) |
| `legendRoar` is on by default; the owner is kept by name | code | Off; by player id (0.7) |
| The captain and office can be killed or broken | reasoned | They can't (4.6) |
| The two-client scene tests none of this | reasoned | The server check: logging out, a restart, leaving mid-fight (0.4) |

Checked and fine: every key press is a request and the server decides, so nothing is trusted from the client.

**Still not proven, each to be settled by a test in its package:** how far apart villages really are; the order of
going down and the totem check; whether golems attack the first devil; how many hotel tries survive; whether a
second player can merge while one already has (the checker says yes, the owner rule may stop it).
