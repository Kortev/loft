package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.PochitaPlace;
import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.form.Beam;
import io.github.kortev.chainsawman.form.Buddies;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Fallen;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Frenzy;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.Hearts;
import io.github.kortev.chainsawman.form.Legend;
import io.github.kortev.chainsawman.form.TeamRules;
import io.github.kortev.chainsawman.form.TeamState;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

/**
 * Teams and the heart's rules, on the server: everyone goes down instead of dying (a 30 s timer, hits shorten it, giving up, the pick-up,
 * the rule off, hybrids unchanged); buddies (the ask and the accept, 10% less damage near, the faster pick-up, saved over a relog); the
 * heart given up (human again, a new den far away, still one Pochita per world), taken back after a long absence (only with the rule on),
 * torn out of a downed hybrid by whoever downed them (only with the rule on, the struggle, a hit, the day, the slots back); the world's
 * number of hearts; and the legend's marker, sent to the others only when he pulls the cord.
 */
public class TeamGameTests implements FabricGameTest {
	/** How long a player who has just joined can not be hurt (ticks). */
	private static final int JOINED = 61;

	// ------------------------------------------------------------------ going down

	/** A human who would die goes down instead: alive on the ground, a 30 s timer, crawling, unable to attack or use anything. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_down", tickLimit = JOINED + 30)
	public void aHumanGoesDownInsteadOfDying(TestContext context) {
		ServerPlayerEntity p = player(context, "down_human", 0.0);
		ServerPlayerEntity foe = player(context, "down_foe", 3.0);
		context.waitAndRun(JOINED, () -> {
			try {
				double speed = p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
				p.damage(p.getDamageSources().generic(), 1000.0F);
				context.assertTrue(p.isAlive() && !p.isDead(), "a human who took a lethal hit died");
				context.assertTrue(Fallen.is(p), "they were not down");
				context.assertTrue(!Downed.is(p), "a human was downed as a hybrid is");
				context.assertTrue(p.getHealth() == 1.0F, "health was " + p.getHealth());
				int left = p.getAttachedOrElse(Fallen.LEFT, 0);
				context.assertTrue(left == Fallen.TIMER && Fallen.TIMER == 600, "the timer was " + left);
				context.assertTrue(p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) < speed * 0.4, "they could still walk at " + p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
				context.assertTrue(p.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH) < 0.01, "they could still jump");
				context.assertTrue(AttackEntityCallback.EVENT.invoker().interact(p, context.getWorld(), Hand.MAIN_HAND, foe, null) == ActionResult.FAIL,
						"they could attack");
				p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.APPLE));
				context.assertTrue(!UseItemCallback.EVENT.invoker().interact(p, context.getWorld(), Hand.MAIN_HAND).getResult().isAccepted(), "they could use an item");
			} finally {
				done(context, p);
				done(context, foe);
			}
			context.complete();
		});
	}

	/** When the 30 s run out they die as usual. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_down_timer", tickLimit = JOINED + 40)
	public void theTimerRunsOutToDeath(TestContext context) {
		ServerPlayerEntity p = player(context, "bleeds_out", 0.0);
		context.runAtTick(JOINED, () -> {
			p.damage(p.getDamageSources().generic(), 1000.0F);
			context.assertTrue(Fallen.is(p), "they were not down");
			p.setAttached(Fallen.LEFT, 5);
		});
		context.runAtTick(JOINED + 4, () -> context.assertTrue(Fallen.is(p) && p.isAlive(), "they died before the time was up"));
		context.runAtTick(JOINED + 12, () -> {
			try {
				context.assertTrue(p.isDead(), "the time ran out and they were still alive");
				context.assertTrue(!Fallen.is(p), "a dead player was still down");
			} finally {
				done(context, p);
			}
			context.complete();
		});
	}

	/** Each hit while down takes two seconds off (not twice in a breath); a hit that takes the last of it kills on the next tick. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_down_hits", tickLimit = JOINED + 50)
	public void hitsShortenTheTime(TestContext context) {
		ServerPlayerEntity p = player(context, "hit_down", 0.0);
		context.runAtTick(JOINED, () -> {
			p.damage(p.getDamageSources().generic(), 1000.0F);
			context.assertTrue(Fallen.is(p), "they were not down");
			int before = p.getAttachedOrElse(Fallen.LEFT, 0);
			p.damage(p.getDamageSources().generic(), 3.0F);
			p.damage(p.getDamageSources().generic(), 3.0F);
			int after = p.getAttachedOrElse(Fallen.LEFT, 0);
			context.assertTrue(before - after == Fallen.HIT_COST, "two hits in a breath took " + (before - after) + " off, not one hit's " + Fallen.HIT_COST);
			context.assertTrue(p.isAlive() && p.getHealth() == 1.0F, "a hit hurt them while down: " + p.getHealth());
		});
		// A hit that would take more than is left kills them (on the next tick).
		context.runAtTick(JOINED + 15, () -> {
			p.setAttached(Fallen.LEFT, Fallen.HIT_COST - 10);
			p.damage(p.getDamageSources().generic(), 3.0F);
			context.assertTrue(Fallen.is(p), "the last hit killed in the middle of the hit");
		});
		context.runAtTick(JOINED + 22, () -> {
			try {
				context.assertTrue(p.isDead(), "a hit that took the last of the time did not kill them");
			} finally {
				done(context, p);
			}
			context.complete();
		});
	}

	/** They can give up: dead at once. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_down_giveup", tickLimit = JOINED + 20)
	public void givingUpDiesAtOnce(TestContext context) {
		ServerPlayerEntity p = player(context, "gives_up", 0.0);
		ServerPlayerEntity other = player(context, "gives_up_other", 3.0);
		context.waitAndRun(JOINED, () -> {
			try {
				context.assertTrue(!Fallen.giveUp(p), "giving up worked for someone who was not down");
				context.assertTrue(p.isAlive(), "someone not down died by giving up");
				p.damage(p.getDamageSources().generic(), 1000.0F);
				context.assertTrue(Fallen.is(p), "they were not down");
				context.assertTrue(Fallen.giveUp(p), "giving up did not work");
				context.assertTrue(p.isDead(), "they gave up and lived");
				context.assertTrue(!Fallen.is(p), "they gave up and were still down");
			} finally {
				done(context, p);
				done(context, other);
			}
			context.complete();
		});
	}

	/** The void and the kill command still kill outright. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_kill", tickLimit = JOINED + 20)
	public void theVoidAndKillStillKill(TestContext context) {
		ServerPlayerEntity a = player(context, "voided", 0.0);
		ServerPlayerEntity b = player(context, "killed", 3.0);
		context.waitAndRun(JOINED, () -> {
			try {
				a.damage(a.getDamageSources().outOfWorld(), Float.MAX_VALUE);
				b.damage(b.getDamageSources().genericKill(), Float.MAX_VALUE);
				context.assertTrue(a.isDead() && !Fallen.is(a), "the void did not kill outright");
				context.assertTrue(b.isDead() && !Fallen.is(b), "/kill did not kill outright");
			} finally {
				done(context, a);
				done(context, b);
			}
			context.complete();
		});
	}

	/** With the rule off, death is as it always was. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_rule_off", tickLimit = JOINED + 20)
	public void theRuleOffMeansNormalDeath(TestContext context) {
		ServerPlayerEntity p = player(context, "rule_off", 0.0);
		MinecraftServer server = context.getWorld().getServer();
		boolean was = server.getGameRules().getBoolean(TeamRules.EVERYONE_GOES_DOWN);
		server.getGameRules().get(TeamRules.EVERYONE_GOES_DOWN).set(false, server);
		context.waitAndRun(JOINED, () -> {
			try {
				p.damage(p.getDamageSources().generic(), 1000.0F);
				context.assertTrue(p.isDead() && !Fallen.is(p), "with the rule off a human did not die");
			} finally {
				server.getGameRules().get(TeamRules.EVERYONE_GOES_DOWN).set(was, server);
				done(context, p);
			}
			context.complete();
		});
	}

	/** A hybrid keeps its own rules: down as before, not as a human is. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_hybrid", tickLimit = JOINED + 20)
	public void hybridsAreUnchanged(TestContext context) {
		ServerPlayerEntity p = player(context, "hybrid_down", 0.0);
		p.setAttached(Heart.MERGED, true);
		context.waitAndRun(JOINED, () -> {
			try {
				p.damage(p.getDamageSources().generic(), 1000.0F);
				context.assertTrue(Downed.is(p) && !Fallen.is(p), "a hybrid went down the wrong way");
				context.assertTrue(p.getAttachedOrElse(Downed.LEFT, 0) == Downed.ALONE, "a hybrid's ten minutes were not kept");
				context.assertTrue(!p.damage(p.getDamageSources().generic(), 5.0F), "a downed hybrid was hurt");
			} finally {
				done(context, p);
			}
			context.complete();
		});
	}

	/** A zombie next to someone down, with a standing player 6 blocks away, goes for the standing one; with nobody standing near, it keeps at the one down. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_mobs_prefer", tickLimit = JOINED + 30)
	public void mobsPreferAStandingPlayerToOneDown(TestContext context) {
		ServerPlayerEntity down = player(context, "mob_down", 0.0);
		ServerPlayerEntity standing = player(context, "mob_standing", 6.0);
		net.minecraft.entity.mob.ZombieEntity zombie = new net.minecraft.entity.mob.ZombieEntity(context.getWorld());
		Vec3d at = spot(context).add(1.0, 0.0, 0.0);
		zombie.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		zombie.setAiDisabled(true);
		zombie.setNoGravity(true);
		context.getWorld().spawnEntity(zombie);
		context.runAtTick(JOINED, () -> {
			down.damage(down.getDamageSources().generic(), 1000.0F);
			zombie.setTarget(down);
			context.assertTrue(Fallen.is(down), "they were not down");
		});
		context.runAtTick(JOINED + 8, () -> context.assertTrue(zombie.getTarget() == standing, "the zombie stayed on the one down: " + zombie.getTarget()));
		// Nobody standing near: it keeps at them.
		context.runAtTick(JOINED + 10, () -> {
			standing.teleport(context.getWorld(), at.x + 40.0, at.y, at.z, 0.0F, 0.0F);
			zombie.setTarget(down);
		});
		context.runAtTick(JOINED + 20, () -> {
			try {
				context.assertTrue(zombie.getTarget() == down, "with nobody standing near it left the one down: " + zombie.getTarget());
			} finally {
				zombie.discard();
				done(context, down);
				done(context, standing);
			}
			context.complete();
		});
	}

	// ------------------------------------------------------------------ the pick-up

	/** A teammate crouched beside them, holding use for three seconds, gets them up with four hearts; the helper pays nothing. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_lift", tickLimit = JOINED + Fallen.LIFT + 40)
	public void aPickUpGetsThemUpWithFourHearts(TestContext context) {
		ServerPlayerEntity down = player(context, "lift_down", 0.0);
		ServerPlayerEntity friend = player(context, "lift_friend", 1.2);
		friend.setSneaking(true);
		float before = friend.getHealth();
		context.runAtTick(JOINED, () -> {
			down.damage(down.getDamageSources().generic(), 1000.0F);
			context.assertTrue(Fallen.is(down), "they were not down");
		});
		use(context, friend, down, JOINED + 1, JOINED + Fallen.LIFT + 8);
		context.runAtTick(JOINED + Fallen.LIFT / 2, () -> context.assertTrue(Fallen.is(down) && Fallen.lifted(down), "halfway they were not down with a hand on them"));
		context.runAtTick(JOINED + Fallen.LIFT + 6, () -> {
			try {
				context.assertTrue(!Fallen.is(down), "after three seconds they were still down");
				context.assertTrue(down.getHealth() == 8.0F, "they got up with " + down.getHealth() + " health, not four hearts");
				context.assertTrue(friend.getHealth() == before, "the helper paid: " + friend.getHealth());
				context.assertTrue(down.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH) > 0.3, "they got up unable to jump");
			} finally {
				done(context, down);
				done(context, friend);
			}
			context.complete();
		});
	}

	/** Letting go early cancels it (and starts over if they take hold again). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_lift_cancel", tickLimit = JOINED + Fallen.LIFT * 2 + 60)
	public void lettingGoCancelsThePickUp(TestContext context) {
		ServerPlayerEntity down = player(context, "cancel_down", 0.0);
		ServerPlayerEntity friend = player(context, "cancel_friend", 1.2);
		friend.setSneaking(true);
		int t0 = JOINED;
		context.runAtTick(t0, () -> down.damage(down.getDamageSources().generic(), 1000.0F));
		use(context, friend, down, t0 + 1, t0 + 33);
		context.runAtTick(t0 + 50, () -> {
			context.assertTrue(Fallen.is(down), "they were up after the helper let go");
			context.assertTrue(!Fallen.lifted(down), "the pick-up carried on after the helper let go");
		});
		use(context, friend, down, t0 + 60, t0 + 60 + Fallen.LIFT + 8);
		context.runAtTick(t0 + 60 + Fallen.LIFT / 2, () -> context.assertTrue(Fallen.is(down), "the second pick-up finished early (it did not start over)"));
		context.runAtTick(t0 + 60 + Fallen.LIFT + 6, () -> {
			try {
				context.assertTrue(!Fallen.is(down), "taking hold again did not get them up");
			} finally {
				done(context, down);
				done(context, friend);
			}
			context.complete();
		});
	}

	/** Not crouching, or too far, does not lift. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_lift_crouch", tickLimit = JOINED + Fallen.LIFT + 30)
	public void standingOrFarDoesNotLift(TestContext context) {
		ServerPlayerEntity down = player(context, "far_down", 0.0);
		ServerPlayerEntity standing = player(context, "far_standing", 1.2);
		ServerPlayerEntity far = player(context, "far_far", 8.0);
		far.setSneaking(true);
		int t0 = JOINED;
		context.runAtTick(t0, () -> down.damage(down.getDamageSources().generic(), 1000.0F));
		use(context, standing, down, t0 + 1, t0 + Fallen.LIFT + 8);
		use(context, far, down, t0 + 1, t0 + Fallen.LIFT + 8);
		context.runAtTick(t0 + Fallen.LIFT + 14, () -> {
			try {
				context.assertTrue(Fallen.is(down), "someone standing, or too far away, lifted them");
			} finally {
				done(context, down);
				done(context, standing);
				done(context, far);
			}
			context.complete();
		});
	}

	// ------------------------------------------------------------------ buddies

	/** Crouch and right-click with an empty hand to ask; the other does the same to accept; one buddy at a time; /chainsaw buddy end ends it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_buddy_ask", tickLimit = 20)
	public void buddiesAskAndAccept(TestContext context) {
		ServerPlayerEntity a = player(context, "buddy_a", 0.0);
		ServerPlayerEntity b = player(context, "buddy_b", 1.0);
		ServerPlayerEntity c = player(context, "buddy_c", 2.0);
		a.setSneaking(true);
		b.setSneaking(true);
		c.setSneaking(true);
		try {
			clickOn(context, a, b);
			context.assertTrue(Buddies.of(a) == null && Buddies.of(b) == null, "asking made them buddies at once");
			clickOn(context, c, a);
			context.assertTrue(Buddies.of(a) == null, "a request to someone who asked someone else paired them");
			clickOn(context, b, a);
			context.assertTrue(b.getUuid().equals(Buddies.of(a)) && a.getUuid().equals(Buddies.of(b)), "the answer did not make them buddies");
			// One at a time.
			clickOn(context, c, a);
			clickOn(context, a, c);
			context.assertTrue(Buddies.of(c) == null && b.getUuid().equals(Buddies.of(a)), "a third joined a pair");
			// A full hand, or standing, does not ask.
			ServerPlayerEntity d = player(context, "buddy_d", 3.0);
			ServerPlayerEntity e = player(context, "buddy_e", 4.0);
			d.setSneaking(true);
			e.setSneaking(true);
			d.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
			clickOn(context, d, e);
			clickOn(context, e, d);
			context.assertTrue(Buddies.of(d) == null && Buddies.of(e) == null, "a hand with something in it asked");
			done(context, d);
			done(context, e);
			Buddies.end(a);
			context.assertTrue(Buddies.of(a) == null && Buddies.of(b) == null, "ending it did not end it on both sides");
		} finally {
			done(context, a);
			done(context, b);
			done(context, c);
		}
		context.complete();
	}

	/** Near a buddy a hit is 10% less; far from them it is the whole. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_buddy_damage", tickLimit = JOINED + 20)
	public void nearABuddyYouTakeTenPercentLess(TestContext context) {
		ServerPlayerEntity a = player(context, "dmg_a", 0.0);
		ServerPlayerEntity b = player(context, "dmg_b", 4.0);
		ServerPlayerEntity stranger = player(context, "dmg_s", -4.0);
		Buddies.pair(a, b);
		context.waitAndRun(JOINED, () -> {
			try {
				a.setHealth(20.0F);
				a.timeUntilRegen = 0;
				a.damage(a.getDamageSources().generic(), 10.0F);
				float near = 20.0F - a.getHealth();
				context.assertTrue(Math.abs(near - 9.0F) < 0.01F, "near their buddy they lost " + near + ", not 9");
				// Far (past 16 blocks).
				Vec3d at = b.getPos();
				b.teleport(context.getWorld(), at.x + 40.0, at.y, at.z, 0.0F, 0.0F);
				a.setHealth(20.0F);
				a.timeUntilRegen = 0;
				a.damage(a.getDamageSources().generic(), 10.0F);
				float far = 20.0F - a.getHealth();
				context.assertTrue(Math.abs(far - 10.0F) < 0.01F, "far from their buddy they lost " + far + ", not 10");
				// Someone who is not their buddy, near, makes no difference.
				b.teleport(context.getWorld(), at.x, at.y, at.z, 0.0F, 0.0F);
				stranger.setHealth(20.0F);
				stranger.timeUntilRegen = 0;
				boolean hurt = stranger.damage(stranger.getDamageSources().generic(), 10.0F);
				context.assertTrue(hurt, "the stranger could not be hurt: regen " + stranger.timeUntilRegen + " invulnerable " + stranger.isInvulnerable() + " age " + stranger.age);
				context.assertTrue(Math.abs(20.0F - stranger.getHealth() - 10.0F) < 0.01F, "a player with no buddy took less: " + stranger.getHealth());
			} finally {
				done(context, a);
				done(context, b);
				done(context, stranger);
			}
			context.complete();
		});
	}

	/** A buddy near picks them up in 1.5 s, where anyone else takes 3. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_buddy_lift", tickLimit = JOINED + Fallen.LIFT + 80)
	public void aBuddyPicksYouUpFaster(TestContext context) {
		ServerPlayerEntity down = player(context, "bl_down", 0.0);
		ServerPlayerEntity buddy = player(context, "bl_buddy", 1.2);
		ServerPlayerEntity other = player(context, "bl_other", -1.2);
		Buddies.pair(down, buddy);
		buddy.setSneaking(true);
		other.setSneaking(true);
		int t0 = JOINED;
		context.runAtTick(t0, () -> {
			down.damage(down.getDamageSources().generic(), 1000.0F);
			context.assertTrue(Fallen.liftTime(buddy, down) == Fallen.LIFT_BUDDY && Fallen.LIFT_BUDDY == 30 && Fallen.liftTime(other, down) == Fallen.LIFT, "the times were wrong");
		});
		// A stranger first: at 1.5 s plus a little, still down.
		use(context, other, down, t0 + 1, t0 + Fallen.LIFT_BUDDY + 8);
		context.runAtTick(t0 + Fallen.LIFT_BUDDY + 10, () -> context.assertTrue(Fallen.is(down), "a stranger lifted them in the buddy's time"));
		int t1 = t0 + Fallen.LIFT_BUDDY + 20;
		context.runAtTick(t1 - 1, () -> context.assertTrue(!Fallen.lifted(down), "the stranger's hold was still on after they let go"));
		// Then the buddy: up within 1.5 s.
		use(context, buddy, down, t1, t1 + Fallen.LIFT_BUDDY + 8);
		context.runAtTick(t1 + Fallen.LIFT_BUDDY + 6, () -> {
			try {
				context.assertTrue(!Fallen.is(down), "their buddy did not pick them up in 1.5 s");
			} finally {
				done(context, down);
				done(context, buddy);
				done(context, other);
			}
			context.complete();
		});
	}

	/** Buddies are saved with the player: relog and they are still buddies. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_buddy_relog", tickLimit = 20)
	public void buddiesSurviveARelog(TestContext context) {
		GameProfile profile = new GameProfile(UUID.randomUUID(), "relog_a");
		ServerPlayerEntity a = join(context, profile, 0.0);
		ServerPlayerEntity b = player(context, "relog_b", 1.0);
		ServerPlayerEntity back = null;
		try {
			Buddies.pair(a, b);
			leave(a);
			back = join(context, profile, 0.0);
			context.assertTrue(b.getUuid().equals(Buddies.of(back)), "after a relog they had no buddy: " + Buddies.of(back));
			context.assertTrue(back.getUuid().equals(Buddies.of(b)), "their buddy lost them while they were away");
			// Ended while away: they find out when they are back.
			leave(back);
			Buddies.end(b);
			back = join(context, profile, 0.0);
			context.assertTrue(Buddies.of(back) == null, "a buddy who ended it while they were away is still theirs");
		} finally {
			if (back != null) {
				done(context, back);
			}
			done(context, b);
		}
		context.complete();
	}

	// ------------------------------------------------------------------ the heart's rules

	/** Giving the heart up: human again with the slots free, and a new den far away: still one Pochita to find. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_giveup_heart", tickLimit = 40)
	public void givingUpTheHeartMakesThemHumanAndPlacesANewDen(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		ServerPlayerEntity p = player(context, "gives_heart", 0.0);
		try {
			Heart.give(p);
			Form.start(p);
			Hearts.update(server);
			List<BlockPos> before = PochitaPlace.allDens(server);
			int waitingBefore = PochitaPlace.waitingDens(server).size();
			context.assertTrue(Contracts.grant(p, Contract.ETERNITY) == Contracts.Result.HYBRID, "a hybrid could hold a contract");
			context.assertTrue(Hearts.giveUp(p), "giving the heart up did not work");
			context.assertTrue(!Heart.has(p) && !Form.on(p), "they were still a hybrid");
			for (int i = 0; i < Contracts.SLOTS; i++) {
				context.assertTrue(Contracts.grant(p, "devil_" + i) == Contracts.Result.GRANTED, "slot " + i + " was not free");
			}
			List<BlockPos> after = PochitaPlace.allDens(server);
			context.assertTrue(after.size() == before.size(), "the world has " + after.size() + " dens, not " + before.size() + ": more than one Pochita");
			context.assertTrue(PochitaPlace.waitingDens(server).size() == waitingBefore + 1, "the heart's den is not waiting");
			BlockPos placed = null;
			BlockPos old = null;
			for (BlockPos b : after) {
				if (!before.contains(b)) {
					placed = b;
				}
			}
			for (BlockPos b : before) {
				if (!after.contains(b)) {
					old = b;
				}
			}
			context.assertTrue(placed != null && old != null, "no den moved: " + before + " -> " + after);
			double apart = Math.hypot(placed.getX() - old.getX(), placed.getZ() - old.getZ());
			context.assertTrue(apart >= PochitaPlace.FAR_BACK, "the new den is only " + apart + " blocks from the old");
			context.assertTrue(Math.hypot(placed.getX() - p.getX(), placed.getZ() - p.getZ()) >= 1000, "the new den is near a player");
			context.assertTrue(!Hearts.giveUp(p), "someone with no heart gave it up");
		} finally {
			Contracts.wipe(p);
			done(context, p);
		}
		context.complete();
	}

	/** Away longer than heartReturnDays, the heart goes back to the world (only with the rule on) and they are human when they come back. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_absence", tickLimit = 40)
	public void absenceReturnsTheHeartOnlyWhenTheRuleIsOn(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		GameProfile profile = new GameProfile(UUID.randomUUID(), "away_hybrid");
		ServerPlayerEntity p = join(context, profile, 0.0);
		ServerPlayerEntity back = null;
		int days = server.getGameRules().getInt(TeamRules.HEART_RETURN_DAYS);
		// (Hybrids of other tests, long gone from this world, are not this one's business.)
		TeamState.of(server).seen.clear();
		try {
			Heart.give(p);
			Hearts.update(server);
			leave(p);
			TeamState state = TeamState.of(server);
			long now = server.getOverworld().getTime();
			state.seen.put(profile.getId(), now - 5L * Hearts.DAY);
			// Rule off: nothing happens however long.
			server.getGameRules().get(TeamRules.HEART_RETURN_DAYS).set(0, server);
			List<BlockPos> before = PochitaPlace.allDens(server);
			Hearts.update(server);
			context.assertTrue(state.seen.containsKey(profile.getId()) && !state.returned.contains(profile.getId()), "the heart went back with the rule off");
			context.assertTrue(PochitaPlace.allDens(server).equals(before), "a den moved with the rule off");
			// Rule on, not long enough.
			server.getGameRules().get(TeamRules.HEART_RETURN_DAYS).set(9, server);
			Hearts.update(server);
			context.assertTrue(!state.returned.contains(profile.getId()), "the heart went back before the days were up");
			// Rule on, long enough.
			server.getGameRules().get(TeamRules.HEART_RETURN_DAYS).set(3, server);
			Hearts.update(server);
			context.assertTrue(state.returned.contains(profile.getId()) && !state.seen.containsKey(profile.getId()), "the heart did not go back after five days away");
			context.assertTrue(!PochitaPlace.allDens(server).equals(before), "no den was placed for the heart");
			back = join(context, profile, 0.0);
			context.assertTrue(!Heart.has(back), "they were still a hybrid when they came back");
			context.assertTrue(!state.returned.contains(profile.getId()), "they were told twice");
		} finally {
			server.getGameRules().get(TeamRules.HEART_RETURN_DAYS).set(days, server);
			TeamState.of(server).seen.clear();
			TeamState.of(server).returned.clear();
			if (back != null) {
				done(context, back);
			}
		}
		context.complete();
	}

	/** The heartTheft rule off: whoever downed them is just a friend with a cord to pull. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_theft_off", tickLimit = 20)
	public void theftIsOffByDefault(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		context.assertTrue(!server.getGameRules().getBoolean(TeamRules.HEART_THEFT), "heartTheft is on by default");
		ServerPlayerEntity victim = hybrid(context, "off_victim", 0.0);
		ServerPlayerEntity thief = player(context, "off_thief", 1.2);
		try {
			Downed.down(victim, thief.getDamageSources().playerAttack(thief));
			thief.setSneaking(true);
			context.assertTrue(!Hearts.theftHold(thief, victim) && !Hearts.tearing(victim), "the heart could be taken with the rule off");
		} finally {
			done(context, victim);
			done(context, thief);
		}
		context.complete();
	}

	/** Only with the rule on and only for whoever downed them (a mob's blow, or another player's, is no one's chance). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_theft_who", tickLimit = 20)
	public void onlyWhoeverDownedThemCanTearTheHeartOut(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		ServerPlayerEntity victim = hybrid(context, "who_victim", 0.0);
		ServerPlayerEntity thief = player(context, "who_thief", 1.2);
		ServerPlayerEntity friend = player(context, "who_friend", -1.2);
		HuskEntity husk = husk(context, 6.0);
		server.getGameRules().get(TeamRules.HEART_THEFT).set(true, server);
		try {
			thief.setSneaking(true);
			friend.setSneaking(true);
			Downed.down(victim, thief.getDamageSources().playerAttack(thief));
			context.assertTrue(thief.getUuid().equals(Downed.downedBy(victim)), "the blow was not remembered");
			context.assertTrue(!Hearts.theftHold(friend, victim), "a friend who did not down them could tear the heart out");
			context.assertTrue(Hearts.theftHold(thief, victim) && Hearts.tearing(victim), "the one who downed them could not");
			Downed.up(victim);
			// A mob's blow: nobody.
			Downed.down(victim, victim.getDamageSources().mobAttack(husk));
			context.assertTrue(Downed.downedBy(victim) == null, "a mob was remembered as the one who downed them");
			context.assertTrue(!Hearts.theftHold(thief, victim) && !Hearts.theftHold(friend, victim), "a hybrid downed by a mob could be robbed");
			// A thief who is already a hybrid has no room for a second heart.
			Downed.up(victim);
			thief.setAttached(Heart.MERGED, true);
			Downed.down(victim, thief.getDamageSources().playerAttack(thief));
			context.assertTrue(!Hearts.theftHold(thief, victim), "a hybrid took a second heart");
		} finally {
			server.getGameRules().get(TeamRules.HEART_THEFT).set(false, server);
			husk.discard();
			done(context, victim);
			done(context, thief);
			done(context, friend);
		}
		context.complete();
	}

	/** Five seconds of holding tears it out: the thief is the hybrid, the old one human with their slots free (and down as a human). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_theft", tickLimit = Hearts.THEFT + 60)
	public void tearingTheHeartOutMakesTheThiefTheHybrid(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		ServerPlayerEntity victim = hybrid(context, "tear_victim", 0.0);
		ServerPlayerEntity thief = player(context, "tear_thief", 1.2);
		server.getGameRules().get(TeamRules.HEART_THEFT).set(true, server);
		Hearts.update(server);
		Downed.down(victim, thief.getDamageSources().playerAttack(thief));
		thief.setSneaking(true);
		thief.setAttached(io.github.kortev.chainsawman.contract.Contracts.LIST, List.of("eternity"));
		int t0 = (int) context.getTick();
		use(context, thief, victim, t0 + 1, t0 + Hearts.THEFT + 8);
		context.runAtTick(t0 + Hearts.THEFT / 2, () -> {
			context.assertTrue(Heart.has(victim) && !Heart.has(thief), "the heart changed hands halfway");
			context.assertTrue(Hearts.tearing(victim), "nothing was being torn");
		});
		context.runAtTick(t0 + Hearts.THEFT + 6, () -> {
			try {
				context.assertTrue(Heart.has(thief) && !Heart.has(victim), "the heart did not change hands");
				context.assertTrue(!Downed.is(victim), "the old hybrid was still downed as one");
				context.assertTrue(Fallen.is(victim), "the old hybrid, down, did not go down as a human");
				context.assertTrue(Contracts.count(thief) == 0, "the thief kept a contract with a devil in them");
				context.assertTrue(Hearts.stolenAt(server, thief.getUuid()) != null, "the theft was not remembered");
				// The old one is human: all three slots are theirs.
				victim.setAttached(Heart.MERGED, false);
				for (int i = 0; i < Contracts.SLOTS; i++) {
					context.assertTrue(Contracts.grant(victim, "devil_" + i) == Contracts.Result.GRANTED, "slot " + i + " was not free for the old hybrid");
				}
				Contracts.wipe(victim);
			} finally {
				server.getGameRules().get(TeamRules.HEART_THEFT).set(false, server);
				TeamState.of(server).stolen.remove(thief.getUuid());
				done(context, victim);
				done(context, thief);
			}
			context.complete();
		});
	}

	/** Mashing a key slows the hold; a hit on the thief cancels it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_theft_struggle", tickLimit = JOINED + 120)
	public void theStruggleSlowsItAndAHitCancelsIt(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		ServerPlayerEntity victim = hybrid(context, "st_victim", 0.0);
		ServerPlayerEntity thief = player(context, "st_thief", 1.2);
		HuskEntity husk = husk(context, 6.0);
		server.getGameRules().get(TeamRules.HEART_THEFT).set(true, server);
		thief.setSneaking(true);
		int t0 = JOINED;
		context.runAtTick(t0, () -> Downed.down(victim, thief.getDamageSources().playerAttack(thief)));
		use(context, thief, victim, t0 + 1, t0 + 90);
		// Struggling every tick for the first 30: the hold goes nowhere (each press takes two off, each tick adds one).
		for (int t = 2; t <= 30; t++) {
			context.runAtTick(t0 + t, () -> Hearts.struggle(victim));
		}
		context.runAtTick(t0 + 32, () -> context.assertTrue(Hearts.torn(victim) >= 0 && Hearts.torn(victim) <= 3, "struggling did not hold it back: " + Hearts.torn(victim)));
		// Then no struggle: it climbs again.
		context.runAtTick(t0 + 50, () -> context.assertTrue(Hearts.torn(victim) >= 15 && Hearts.torn(victim) < 25, "it did not go on after the struggle: " + Hearts.torn(victim)));
		// A hit on the thief cancels it.
		context.runAtTick(t0 + 52, () -> {
			context.assertTrue(thief.damage(thief.getDamageSources().mobAttack(husk), 2.0F), "the thief could not be hit");
			context.assertTrue(!Hearts.tearing(victim), "a hit on the thief did not cancel it");
		});
		context.runAtTick(t0 + 60, () -> {
			try {
				context.assertTrue(Heart.has(victim) && !Heart.has(thief), "the heart changed hands after a hit");
				context.assertTrue(Downed.is(victim), "the hybrid was not still down");
			} finally {
				server.getGameRules().get(TeamRules.HEART_THEFT).set(false, server);
				husk.discard();
				done(context, victim);
				done(context, thief);
			}
			context.complete();
		});
	}

	/** The heart cannot change hands again for a day: not at once, and then once a day has passed. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_theft_day", tickLimit = 20)
	public void aHeartDoesNotChangeHandsAgainForADay(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		ServerPlayerEntity first = hybrid(context, "day_first", 0.0);
		ServerPlayerEntity second = player(context, "day_second", 1.2);
		ServerPlayerEntity third = player(context, "day_third", -1.2);
		server.getGameRules().get(TeamRules.HEART_THEFT).set(true, server);
		try {
			Hearts.update(server);
			Downed.down(first, second.getDamageSources().playerAttack(second));
			Hearts.steal(second, first);
			context.assertTrue(Heart.has(second) && !Heart.has(first), "the first theft did not work");
			// Straight away the new hybrid is downed by someone else: they cannot have it yet.
			Downed.down(second, third.getDamageSources().playerAttack(third));
			third.setSneaking(true);
			context.assertTrue(Hearts.theftHold(third, second) && !Hearts.tearing(second), "the heart could be torn out again at once");
			// A day later.
			TeamState.of(server).stolen.put(second.getUuid(), server.getOverworld().getTime() - Hearts.SETTLE - 1);
			context.assertTrue(Hearts.theftHold(third, second) && Hearts.tearing(second), "after a day it still could not be torn out");
		} finally {
			server.getGameRules().get(TeamRules.HEART_THEFT).set(false, server);
			TeamState.of(server).stolen.remove(second.getUuid());
			done(context, first);
			done(context, second);
			done(context, third);
		}
		context.complete();
	}

	/** chainsawHearts at 2 allows two hearts (two dens, not three). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_two_hearts", tickLimit = 20)
	public void twoHeartsAllowsTwoDens(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		int was = server.getGameRules().getInt(TeamRules.CHAINSAW_HEARTS);
		try {
			context.assertTrue(was == 1, "chainsawHearts was " + was + " by default, not 1");
			server.getGameRules().get(TeamRules.CHAINSAW_HEARTS).set(2, server);
			Hearts.update(server);
			int dens = PochitaPlace.dens(server);
			context.assertTrue(dens >= 2, "with two hearts the world has " + dens + " dens");
			Hearts.update(server);
			context.assertTrue(PochitaPlace.dens(server) == dens, "the world went on placing dens: " + PochitaPlace.dens(server));
			List<BlockPos> all = PochitaPlace.allDens(server);
			for (int i = 0; i < all.size(); i++) {
				for (int j = i + 1; j < all.size(); j++) {
					context.assertTrue(Math.hypot(all.get(i).getX() - all.get(j).getX(), all.get(i).getZ() - all.get(j).getZ()) > 100, "two dens are on top of each other");
				}
			}
		} finally {
			server.getGameRules().get(TeamRules.CHAINSAW_HEARTS).set(was, server);
		}
		context.complete();
	}

	// ------------------------------------------------------------------ the legend

	/** The marker goes to the other players, and only when he pulls the cord: not when he is merely human with the heart, and not with the rule off. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_team_legend", tickLimit = 20)
	public void theLegendIsShownToOthersOnlyWhenHeTransforms(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		ServerPlayerEntity legend = player(context, "legend", 0.0);
		ServerPlayerEntity a = player(context, "legend_a", 5.0);
		ServerPlayerEntity b = player(context, "legend_b", -5.0);
		try {
			Heart.give(legend);
			context.assertTrue(Legend.markerFor(a) == null && Legend.markerFor(b) == null, "a marker came before he transformed");
			// Rule off: a pull is quiet.
			server.getGameRules().get(TeamRules.LEGEND_ROAR).set(false, server);
			Form.pullCord(legend);
			context.assertTrue(Legend.markerFor(a) == null, "a marker came with the rule off");
			Form.end(legend);
			server.getGameRules().get(TeamRules.LEGEND_ROAR).set(true, server);
			context.assertTrue(Legend.markerFor(a) == null, "a marker came from nothing");
			Form.pullCord(legend);
			Legend.Marker marker = Legend.markerFor(a);
			context.assertTrue(marker != null && Legend.markerFor(b) != null, "the others were not shown him");
			context.assertTrue(marker.legend().equals(legend.getUuid()) && Math.abs(marker.x() - legend.getX()) < 0.01 && Math.abs(marker.z() - legend.getZ()) < 0.01,
					"the marker was not where he transformed");
			context.assertTrue(marker.until() - legend.getServerWorld().getTime() >= Legend.LASTS - 2 && Legend.LASTS == 1200, "the marker did not last a minute");
			context.assertTrue(Legend.markerFor(legend) == null, "he was shown to himself");
		} finally {
			server.getGameRules().get(TeamRules.LEGEND_ROAR).set(true, server);
			done(context, legend);
			done(context, a);
			done(context, b);
		}
		context.complete();
	}

	// ------------------------------------------------------------------ helpers

	private static Vec3d spot(TestContext context) {
		return context.getAbsolute(new Vec3d(0.5, 40.0, 0.5));
	}

	private static ServerPlayerEntity player(TestContext context, String name, double x) {
		return join(context, new GameProfile(UUID.randomUUID(), name), x);
	}

	/** A player in the game (a stand-in with no real client behind it), joined as the game does it, loading what was saved for them. */
	private static ServerPlayerEntity join(TestContext context, GameProfile profile, double x) {
		ServerWorld world = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		Vec3d at = spot(context).add(x, 0.0, 0.0);
		player.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		player.onTeleportationDone();
		player.setNoGravity(true);
		return player;
	}

	/** They leave the way a real player does (what is saved for them is saved; the game's disconnect events run). */
	private static void leave(ServerPlayerEntity player) {
		player.networkHandler.onDisconnected(new net.minecraft.network.DisconnectionInfo(Text.literal("test")));
	}

	/** A hybrid (the heart, no form). */
	private static ServerPlayerEntity hybrid(TestContext context, String name, double x) {
		ServerPlayerEntity player = player(context, name, x);
		player.setAttached(Heart.MERGED, true);
		return player;
	}

	private static HuskEntity husk(TestContext context, double x) {
		Vec3d at = spot(context).add(x, 0.0, 0.0);
		HuskEntity husk = new HuskEntity(EntityType.HUSK, context.getWorld());
		husk.refreshPositionAndAngles(at.x, at.y, at.z, 180.0F, 0.0F);
		husk.setAiDisabled(true);
		husk.setNoGravity(true);
		context.getWorld().spawnEntity(husk);
		return husk;
	}

	/** {@code from} right-clicking {@code on} through the game's own path, every 4 ticks (as a held use repeats) from tick {@code from} to {@code to}. */
	private static void use(TestContext context, ServerPlayerEntity by, ServerPlayerEntity on, int from, int to) {
		for (int t = from; t <= to; t += 4) {
			context.runAtTick(t, () -> UseEntityCallback.EVENT.invoker().interact(by, context.getWorld(), Hand.MAIN_HAND, on, null));
		}
	}

	private static void clickOn(TestContext context, ServerPlayerEntity by, ServerPlayerEntity on) {
		UseEntityCallback.EVENT.invoker().interact(by, context.getWorld(), Hand.MAIN_HAND, on, null);
	}

	/** Up, out of everything, gone. */
	private static void done(TestContext context, ServerPlayerEntity player) {
		Beam.end(player);
		Form.end(player);
		Frenzy.end(player);
		Downed.up(player);
		Fallen.up(player, null);
		if (Buddies.of(player) != null) {
			Buddies.end(player);
		}
		player.removeAttached(Heart.MERGED);
		context.getWorld().getServer().getPlayerManager().remove(player);
	}

	/** Every player in a list is gone (for a test that made several). */
	@SuppressWarnings("unused")
	private static void all(TestContext context, List<ServerPlayerEntity> players) {
		for (ServerPlayerEntity p : new ArrayList<>(players)) {
			done(context, p);
		}
	}
}
