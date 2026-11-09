# Master plan, part: 3_solo_and_friends

Section 3: solo, friends and servers; the partner. The index is `../MASTER_PLAN.md`.
Marks: **[decided]** kortev said so. **[pick]** Fable's choice, waiting for kortev's yes. **[check]** a show fact to
check on the wiki and Google first.

---

## 3. Solo or multiplayer? Both, from one design [pick]

**Complete alone, better with friends, and server drama as an extra after release.**

Why not multiplayer only: most people who install a mod play alone or with one friend. If the mod
needs a server to be fun, most of them leave in ten minutes.

Why not solo only: the best reels so far are fights between players, kortev has an SMP, and "who got Chainsaw Man?" is
the best server story a mod can have. Also the code is already written the server's way, so nothing is saved by
dropping it.

So one design, in three layers:

| Layer | Who it's for | What it means | When |
|---|---|---|---|
| **Solo** | everyone | Every quest, boss, rank and path can be finished by one player. This is the test for every piece. | Always, from the start |
| **Friends (2-4)** | most servers | Missions shared by a party, bosses scale with the party, buddies, picking each other up. No PvP needed. | Built with each piece, checked with two real clients at each milestone |
| **Server legend** | big SMPs | The one Chainsaw Man shown on the map, the bounty on the heart, heart theft, one contract per devil. All off by default. | After 1.0, as its own update |

**What makes solo fair: a partner [pick].** Public Safety hunters work in pairs. Alone, the office assigns you a
**partner hunter** (our own character, an NPC with a katana). They follow you on missions, fight a little, and
**pick you up when you go down**. With a friend online and near you, your buddy is that friend. That answers the open
question "is going down alone too harsh?": alone you're never really alone. Later, Power can be a partner you earn.

The partner must never be a chore, and must not make you unkillable (round 2):
- **The partner is saved on the player, not as a mob standing in the world.** Their body appears next to you when a
  mission or a fight starts, and goes when you log out, change dimension, fly or ride off. A follower saved in a
  chunk gets stuck in caves and doorways, is left behind in chunks nobody has loaded, and can come back doubled.
- They can't die for good: they go down and are back the next day.
- **One pick-up per fight.** After lifting you they're winded for a few minutes, so going down still matters.
- **You get the partner whenever no real buddy is online and near you,** checked when a mission starts. "On a
  server you get no NPC" fails when your friend is asleep in another time zone, or leaves halfway.
- They never lift you in a fight against another player.

**Rules that follow from this:**
- Nothing in 1.0 may need a second player.
- Nothing in 1.0 may need Chainsaw Man [decided].
- Every piece is tested solo by the game tests, and on a real server with two clients (logging out and back in, a
  restart, one leaving mid-fight) before its milestone closes (section 7). Everything that is one per world breaks on
  a server first.
