package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.contract.Forever;
import io.github.kortev.chainsawman.contract.ForeverCopyEntity;
import io.github.kortev.chainsawman.contract.ForeverFlags;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * The Eternity contract and its power, Forever: the contract skeleton (granted, cleared, one at a time, never for a hybrid) and
 * Forever itself (the lock, the line of copies, the stored hits, the release, the price, the cooldown, the immunity).
 */
public class ForeverGameTests implements FabricGameTest {
	/** A contract is granted and cleared, and told to the player's saved state. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_contract", tickLimit = 20)
	public void contractGrantAndClear(TestContext context) {
		ServerPlayerEntity player = player(context, "holder");
		try {
			context.assertTrue(Contracts.get(player) == Contract.NONE, "a new player held a contract");
			context.assertTrue(Contracts.grant(player, Contract.ETERNITY) == Contracts.Result.GRANTED, "the grant was refused");
			context.assertTrue(Contracts.has(player, Contract.ETERNITY), "the contract was not held after the grant");
			Contracts.clear(player);
			context.assertTrue(Contracts.get(player) == Contract.NONE, "the contract was still held after clearing");
		} finally {
			leave(context, player);
		}
		context.complete();
	}

	/** One contract at a time: granting again (or another) leaves exactly one, and clearing leaves none. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_one", tickLimit = 20)
	public void oneContractAtATime(TestContext context) {
		ServerPlayerEntity player = player(context, "monogamist");
		try {
			Contracts.grant(player, Contract.ETERNITY);
			Contracts.grant(player, Contract.ETERNITY);
			context.assertTrue(Contracts.get(player) == Contract.ETERNITY, "the contract was not held");
			Contracts.grant(player, Contract.NONE);
			context.assertTrue(Contracts.get(player) == Contract.NONE, "granting none did not end the contract");
		} finally {
			leave(context, player);
		}
		context.complete();
	}

	/** A hybrid (Pochita's heart) cannot hold a contract, and taking the heart gives the contract up. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_hybrid", tickLimit = 20)
	public void hybridsCannotHoldContracts(TestContext context) {
		ServerPlayerEntity hybrid = player(context, "hybrid");
		ServerPlayerEntity hunter = player(context, "hunter");
		try {
			Heart.give(hybrid);
			context.assertTrue(Contracts.grant(hybrid, Contract.ETERNITY) == Contracts.Result.HYBRID, "a hybrid was granted a contract");
			context.assertTrue(Contracts.get(hybrid) == Contract.NONE, "a hybrid holds a contract");
			Contracts.grant(hunter, Contract.ETERNITY);
			Heart.give(hunter);
			context.assertTrue(Contracts.get(hunter) == Contract.NONE, "merging with Pochita did not end the contract");
		} finally {
			Heart.take(hybrid);
			Heart.take(hunter);
			leave(context, hybrid);
			leave(context, hunter);
		}
		context.complete();
	}

	// ---- Forever.

	/** No contract: nothing happens. In Chainsaw Man form: blocked (and no price or cooldown is spent). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_blocked", tickLimit = 40)
	public void foreverNeedsTheContractAndHumanForm(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		ServerPlayerEntity player = holder(context, "blocked", 0.0, husk);
		try {
			Contracts.clear(player);
			Forever.use(player);
			context.assertTrue(!Forever.active(player) && !Forever.locked(husk), "Forever started with no contract");
			context.assertTrue(player.experienceLevel == 10, "a refused use took levels");
			Contracts.grant(player, Contract.ETERNITY);
			Form.start(player);
			context.assertTrue(Form.in(player), "the test player is not in the form");
			Forever.use(player);
			context.assertTrue(!Forever.active(player) && !Forever.locked(husk), "Forever started in Chainsaw Man form");
			context.assertTrue(Forever.cooldownLeft(player) == 0 && player.experienceLevel == 10, "a blocked use spent the price or the cooldown");
		} finally {
			Form.end(player);
			finish(context, player, husk);
		}
		context.complete();
	}

	/** Nothing in sight: refused and nothing is spent. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_notarget", tickLimit = 40)
	public void foreverWithNothingInSightCostsNothing(TestContext context) {
		terrain(context);
		ServerPlayerEntity player = holder(context, "blind", 0.0, null);
		player.setPitch(-80.0F);
		try {
			Forever.use(player);
			context.assertTrue(!Forever.active(player), "Forever started with nothing to hold");
			context.assertTrue(player.experienceLevel == 10 && Forever.cooldownLeft(player) == 0, "a use with no target cost something");
		} finally {
			finish(context, player);
		}
		context.complete();
	}

	/**
	 * The lock holds a mob where it is for 60 ticks (and frees it after, its AI and velocity given back): the copies are there while it
	 * lasts and gone after; a boss is held for 30.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_lock", tickLimit = 140)
	public void theLockHoldsAMobAndFreesIt(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		husk.setAiDisabled(false);
		ServerPlayerEntity player = holder(context, "locker", 0.0, husk);
		try {
			Vec3d[] frozen = new Vec3d[1];
			int[] used = new int[1];
			int[] freed = new int[1];
			context.runAtTick(2, () -> {
				// It is walking when it is caught.
				husk.setVelocity(0.1, 0.0, 0.0);
				frozen[0] = husk.getPos();
				Forever.use(player);
				used[0] = 2;
				context.assertTrue(Forever.locked(husk), "the husk was not locked");
				context.assertTrue(player.experienceLevel == 10 - Forever.PRICE, "the price was not taken: " + player.experienceLevel);
				context.assertTrue(Forever.copies(husk).size() >= 4, "too few copies: " + Forever.copies(husk).size());
				context.assertTrue(husk.isAiDisabled() && husk.hasNoGravity(), "the husk's AI and gravity were not stopped");
			});
			for (int t = 3; t < 120; t++) {
				int tick = t;
				context.runAtTick(t, () -> {
					if (Forever.locked(husk)) {
						context.assertTrue(husk.getPos().squaredDistanceTo(frozen[0]) < 1.0E-4, "the husk moved while locked, tick " + tick + ": " + husk.getPos());
					} else if (freed[0] == 0 && tick > 3) {
						freed[0] = tick;
					}
				});
			}
			context.runAtTick(75, () -> {
				context.assertTrue(freed[0] - used[0] >= Forever.LASTS && freed[0] - used[0] <= Forever.LASTS + Forever.SNAP + 1,
						"the lock lasted " + (freed[0] - used[0]) + " ticks");
				context.assertTrue(!husk.isAiDisabled() && !husk.hasNoGravity(), "the AI and gravity were not given back");
				context.assertTrue(husk.getPos().squaredDistanceTo(frozen[0]) > 0.01, "the husk did not move on after the release: " + husk.getPos());
				context.assertTrue(Forever.copies(husk).isEmpty() && copiesInWorld(context) == 0, "copies were left");
			});
			context.runAtTick(100, () -> {
				context.assertTrue(context.getWorld().getEntitiesByType(ChainsawMan.FOREVER_HUB, e -> true).isEmpty(), "the devil's hold was left in the world");
				context.assertTrue(!ForeverFlags.SERVER.contains(husk.getId()), "the lock flag was left");
				finish(context, player, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	/** A mob caught in the air stays in the air: it does not fall for 60 ticks, and falls after. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_air", tickLimit = 140)
	public void theLockHoldsAMobInTheAir(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F, 5.0);
		// (A mob with no AI does not fall at all, so this one has its AI on.)
		husk.setAiDisabled(false);
		ServerPlayerEntity player = holder(context, "skyhook", 0.0, husk);
		try {
			double[] y = new double[1];
			context.runAtTick(2, () -> {
				y[0] = husk.getY();
				Forever.use(player);
				context.assertTrue(Forever.locked(husk), "the husk was not locked in the air");
				List<ForeverCopyEntity> copies = Forever.copies(husk);
				context.assertTrue(copies.size() >= 4, "too few copies for a mid-air husk: " + copies.size());
				// Each hangs at the husk's height over its own ground.
				for (ForeverCopyEntity c : copies) {
					context.assertTrue(Math.abs(c.getY() - husk.getY()) < 1.6, "a copy is not at the husk's height over the ground: " + c.getY() + " vs " + husk.getY());
				}
			});
			context.runAtTick(60, () -> context.assertTrue(Math.abs(husk.getY() - y[0]) < 1.0E-4 && !husk.isOnGround(), "the husk fell while locked: " + husk.getY() + " from " + y[0]));
			context.runAtTick(100, () -> {
				context.assertTrue(husk.getY() < y[0] - 1.0, "the husk did not fall after the release: " + husk.getY());
				finish(context, player, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	/** A boss (100 health or more) is held for 30 ticks, not 60. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_boss", tickLimit = 100)
	public void aBossIsHeldForHalfAsLong(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 120.0F);
		ServerPlayerEntity player = holder(context, "bosser", 0.0, husk);
		try {
			int[] freed = new int[1];
			context.runAtTick(2, () -> {
				Forever.use(player);
				context.assertTrue(Forever.locked(husk), "the boss was not locked");
			});
			for (int t = 3; t < 90; t++) {
				int tick = t;
				context.runAtTick(t, () -> {
					if (!Forever.locked(husk) && freed[0] == 0) {
						freed[0] = tick;
					}
				});
			}
			context.runAtTick(90, () -> {
				context.assertTrue(freed[0] - 2 >= Forever.BOSS_LASTS && freed[0] - 2 <= Forever.BOSS_LASTS + Forever.SNAP + 1, "the boss was held " + (freed[0] - 2) + " ticks");
				finish(context, player, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	/** A hit on the target is stored, not applied; the stored hit hangs on the hub as a mark; a copy's hit is stored on the target the same way. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_store", tickLimit = 60)
	public void hitsOnTheTargetAndOnCopiesAreStored(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		ServerPlayerEntity player = holder(context, "slasher", 0.0, husk);
		try {
			Forever.use(player);
			context.assertTrue(Forever.locked(husk), "not locked");
			float health = husk.getHealth();
			husk.damage(context.getWorld().getDamageSources().playerAttack(player), 4.0F);
			context.assertTrue(husk.getHealth() == health && Forever.stored(husk) == 1, "a hit on the target was applied or not stored: " + Forever.stored(husk));
			ForeverCopyEntity copy = Forever.copies(husk).get(0);
			context.assertTrue(copy.damage(context.getWorld().getDamageSources().playerAttack(player), 6.0F), "a hit on a copy was not taken");
			context.assertTrue(husk.getHealth() == health && Forever.stored(husk) == 2, "a hit on a copy was applied or not stored on the target: " + Forever.stored(husk));
			context.assertTrue(Math.abs(Forever.storedTotal(husk) - 10.0F) < 1.0E-4, "the stored total is " + Forever.storedTotal(husk));
			context.assertTrue(Forever.hub(husk).marks().length == 2, "the marks are not on the hub");
			// Not a player's hit: not stored.
			husk.damage(context.getWorld().getDamageSources().magic(), 1.0F);
			context.assertTrue(Forever.stored(husk) == 2, "a hit that was no player's was stored");
			context.assertTrue(copy.getBoundingBox().getLengthY() > 1.7 && Math.abs(copy.getWidth() - husk.getWidth()) < 1.0E-4, "a copy is not the target's size");
		} finally {
			finish(context, player, husk);
		}
		context.complete();
	}

	/** Real melee (the attack path with a sword) is stored too. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_melee", tickLimit = 60)
	public void aSwordSwingIsStored(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 1.5, 60.0F);
		ServerPlayerEntity player = holder(context, "swordsman", 0.0, husk);
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		try {
			Forever.use(player);
			context.assertTrue(Forever.locked(husk), "not locked");
			context.runAtTick(25, () -> {
				// (The test's players are not ticked by a connection: tick this one for the weapon and its attack cooldown.)
				for (int i = 0; i < 24; i++) {
					player.playerTick();
				}
				float health = husk.getHealth();
				player.attack(husk);
				context.assertTrue(husk.getHealth() == health, "the swing hurt the locked husk");
				context.assertTrue(Forever.stored(husk) == 1 && Forever.storedTotal(husk) > 3.0F, "the swing was not stored: " + Forever.stored(husk) + " / " + Forever.storedTotal(husk));
				finish(context, player, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	/** Two attackers' hits are both stored; at the release the total times 1.5 goes off at once, credited to the one who stored the most. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_release", tickLimit = 100)
	public void theReleaseDealsTheTotalAndCreditsTheTopAttacker(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		ServerPlayerEntity owner = holder(context, "caller", 0.0, husk);
		ServerPlayerEntity other = player(context, "helper");
		Vec3d far = context.getAbsolute(new Vec3d(-8.5, 40.0, 0.5));
		other.teleport(context.getWorld(), far.x, far.y, far.z, 0.0F, 0.0F);
		try {
			context.runAtTick(2, () -> {
				Forever.use(owner);
				Forever.endsIn(husk, 12);
				husk.damage(context.getWorld().getDamageSources().playerAttack(owner), 3.0F);
				Forever.copies(husk).get(1).damage(context.getWorld().getDamageSources().playerAttack(other), 7.0F);
				husk.damage(context.getWorld().getDamageSources().playerAttack(owner), 2.0F);
				context.assertTrue(Forever.stored(husk) == 3 && Forever.storedTotal(husk) == 12.0F, "the hits of two attackers were not both stored");
				context.assertTrue(husk.getHealth() == 60.0F, "a stored hit was applied");
			});
			context.runAtTick(25, () -> {
				context.assertTrue(!Forever.locked(husk), "the lock did not end");
				context.assertTrue(Math.abs(husk.getHealth() - (60.0F - 18.0F)) < 0.01F, "the release dealt " + (60.0F - husk.getHealth()) + ", not 18");
				context.assertTrue(husk.getAttacker() == other, "the top attacker was not credited: " + husk.getAttacker());
				context.assertTrue(copiesInWorld(context) == 0, "copies were left");
				finish(context, owner, husk);
				leave(context, other);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, owner, husk);
			leave(context, other);
			throw e;
		}
	}

	/** The release is capped at 40 (20 hearts), and kills credit the top attacker. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_cap", tickLimit = 100)
	public void theReleaseIsCappedAtForty(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 90.0F);
		ServerPlayerEntity owner = holder(context, "overkiller", 0.0, husk);
		try {
			context.runAtTick(2, () -> {
				Forever.use(owner);
				Forever.endsIn(husk, 6);
				for (int i = 0; i < 4; i++) {
					husk.damage(context.getWorld().getDamageSources().playerAttack(owner), 20.0F);
				}
			});
			context.runAtTick(25, () -> {
				context.assertTrue(Math.abs(husk.getHealth() - 50.0F) < 0.01F, "the release was not capped at 40: health " + husk.getHealth());
				finish(context, owner, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, owner, husk);
			throw e;
		}
	}

	/** Knockback away from the user grows with the number of hits stored, and a target thrown off the line is flung. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_knock", tickLimit = 100)
	public void theReleaseFlingsTheTargetAwayFromTheUser(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 90.0F);
		husk.setAiDisabled(false);
		ServerPlayerEntity owner = holder(context, "flinger", 0.0, husk);
		try {
			double[] z = new double[1];
			context.runAtTick(2, () -> {
				z[0] = husk.getZ();
				Forever.use(owner);
				Forever.endsIn(husk, 6);
				for (int i = 0; i < 5; i++) {
					husk.damage(context.getWorld().getDamageSources().playerAttack(owner), 2.0F);
				}
			});
			context.runAtTick(15, () -> {
				context.assertTrue(husk.getZ() > z[0] + 0.8, "the target was not flung away from the user: " + (husk.getZ() - z[0]));
				finish(context, owner, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, owner, husk);
			throw e;
		}
	}

	/** The line: past copies lie back along where the target came from, future ones ahead along where it was going; none inside a block, all on ground. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_line", tickLimit = 90)
	public void theLineRunsBackAlongThePathAndOnAhead(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, -6.0, 3.0, 60.0F);
		ServerPlayerEntity owner = holder(context, "tracker", -6.0, null);
		try {
			// It walks along x at 0.2 a tick (carried along by the test: a mob with no AI does not move by itself).
			Vec3d base = husk.getPos();
			for (int t = 1; t <= 40; t++) {
				double along = 0.2 * t;
				context.runAtTick(t, () -> husk.refreshPositionAndAngles(base.x + along, base.y, base.z, 180.0F, 0.0F));
			}
			context.runAtTick(41, () -> {
				// Look at it now it is further along.
				Vec3d to = husk.getPos().add(0.0, 0.9, 0.0).subtract(owner.getEyePos());
				owner.setYaw((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
				owner.setPitch((float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z))));
				context.assertTrue(Forever.historyLength(husk) >= 30, "too little history kept: " + Forever.historyLength(husk));
				Forever.use(owner);
				context.assertTrue(Forever.locked(husk), "not locked");
				List<ForeverCopyEntity> copies = Forever.copies(husk);
				context.assertTrue(copies.size() == 8, "not eight copies on open flat ground: " + copies.size());
				double past = 0.0;
				double future = 0.0;
				int p = 0;
				int f = 0;
				ServerWorld world = context.getWorld();
				for (ForeverCopyEntity c : copies) {
					context.assertTrue(Forever.fits(world, c.getX(), c.getY(), c.getZ(), husk.getWidth(), husk.getHeight()), "a copy is inside a block at " + c.getPos());
					context.assertTrue(Forever.stands(world, BlockPos.ofFloored(c.getPos()).getX(), (int) Math.round(c.getY()), BlockPos.ofFloored(c.getPos()).getZ()), "a copy is not on ground at " + c.getPos());
					double d = c.getX() - husk.getX();
					if (c.slot() < 0) {
						past += d;
						p++;
					} else {
						future += d;
						f++;
					}
					double apart = Math.hypot(c.getX() - husk.getX(), c.getZ() - husk.getZ());
					context.assertTrue(apart > 0.8 && apart < 6.0, "a copy is not on the line near the husk: " + apart);
				}
				context.assertTrue(p == 4 && f == 4, "not four and four: " + p + " / " + f);
				context.assertTrue(past / p < -1.5, "the past copies are not back along the path: mean dx " + past / p);
				context.assertTrue(future / f > 1.5, "the future copies are not on ahead: mean dx " + future / f);
				finish(context, owner, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, owner, husk);
			throw e;
		}
	}

	/** A target that was barely moving still shows a line: spread along its facing. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_still", tickLimit = 40)
	public void aStandingTargetStillShowsALine(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		ServerPlayerEntity owner = holder(context, "stiller", 0.0, husk);
		try {
			Forever.use(owner);
			List<ForeverCopyEntity> copies = Forever.copies(husk);
			context.assertTrue(copies.size() >= 6, "a standing target showed too few copies: " + copies.size());
			double min = 99.0;
			for (ForeverCopyEntity c : copies) {
				min = Math.min(min, Math.hypot(c.getX() - husk.getX(), c.getZ() - husk.getZ()));
			}
			context.assertTrue(min > 0.7, "a copy is on top of the target: " + min);
		} finally {
			finish(context, owner, husk);
		}
		context.complete();
	}

	/** On uneven ground with pillars next to the targets: every copy is outside the blocks and on real ground (or hangs at the target's height). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_ground", tickLimit = 40)
	public void copiesNeverSitInsideBlocks(TestContext context) {
		terrain(context);
		double[][] spots = {{-3.0, 8.0}, {4.0, 3.0}, {-5.0, 6.0}, {5.0, 10.0}};
		for (double[] spot : spots) {
			HuskEntity husk = husk(context, spot[0], spot[1], 60.0F, 0.0);
			ServerPlayerEntity player = holder(context, "grounded" + spot[0], spot[0], husk);
			try {
				Forever.use(player);
				if (!Forever.locked(husk)) {
					// (Not in sight past the terrain: another spot will do.)
					continue;
				}
				for (ForeverCopyEntity c : Forever.copies(husk)) {
					context.assertTrue(Forever.fits(context.getWorld(), c.getX(), c.getY(), c.getZ(), husk.getWidth(), husk.getHeight()), "a copy is inside a block at " + c.getPos());
					BlockPos below = BlockPos.ofFloored(c.getX(), c.getY() - 0.05, c.getZ());
					context.assertTrue(!context.getWorld().getBlockState(below).getCollisionShape(context.getWorld(), below).isEmpty(), "a copy is in the air at " + c.getPos());
				}
			} finally {
				finish(context, player, husk);
			}
		}
		context.complete();
	}

	/** The price is three levels in survival, a refusal with none spent when short, free in creative. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_price", tickLimit = 40)
	public void foreverCostsThreeLevels(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		ServerPlayerEntity poor = holder(context, "poor", -1.0, husk);
		ServerPlayerEntity rich = holder(context, "rich", 1.0, husk);
		try {
			poor.setExperienceLevel(2);
			Forever.use(poor);
			context.assertTrue(!Forever.locked(husk) && poor.experienceLevel == 2 && Forever.cooldownLeft(poor) == 0, "a use that was too dear went through or cost something");
			rich.changeGameMode(GameMode.CREATIVE);
			Forever.use(rich);
			context.assertTrue(Forever.locked(husk) && rich.experienceLevel == 10, "creative paid, or did not start: " + rich.experienceLevel);
			context.assertTrue(Forever.PRICE == 3, "the price changed");
		} finally {
			finish(context, poor, husk);
			finish(context, rich, husk);
		}
		context.complete();
	}

	/** The 30 s cooldown from the release, and an early press says so without a cost. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_cooldown", tickLimit = 100)
	public void foreverHasAThirtySecondCooldown(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		ServerPlayerEntity player = holder(context, "waiter", 0.0, husk);
		try {
			context.runAtTick(2, () -> {
				Forever.use(player);
				Forever.endsIn(husk, 4);
				context.assertTrue(Forever.cooldownLeft(player) == 0, "a cooldown while it is still going");
			});
			context.runAtTick(25, () -> {
				long left = Forever.cooldownLeft(player);
				context.assertTrue(left > Forever.COOLDOWN - 25 && left <= Forever.COOLDOWN, "the cooldown from the release is " + left);
				int levels = player.experienceLevel;
				Forever.use(player);
				context.assertTrue(!Forever.locked(husk) && player.experienceLevel == levels, "it worked during the cooldown");
				Forever.ready(player);
				Forever.use(player);
				context.assertTrue(Forever.locked(husk), "it would not start once the cooldown was skipped");
				finish(context, player, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	/** After a release the same target cannot be locked again for 3 s (no chain-locking a boss with several holders); then it can. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_immune", tickLimit = 140)
	public void aTargetCannotBeLockedAgainForThreeSeconds(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		ServerPlayerEntity first = holder(context, "first", -1.0, husk);
		ServerPlayerEntity second = holder(context, "second", 1.0, husk);
		try {
			context.runAtTick(2, () -> {
				Forever.use(first);
				Forever.endsIn(husk, 4);
			});
			context.runAtTick(15, () -> {
				context.assertTrue(!Forever.locked(husk) && Forever.immuneLeft(husk) > 0, "no immunity after the release: " + Forever.immuneLeft(husk));
				Forever.use(second);
				context.assertTrue(!Forever.locked(husk) && second.experienceLevel == 10, "the target was locked again at once");
			});
			context.runAtTick(100, () -> {
				context.assertTrue(Forever.immuneLeft(husk) == 0, "the immunity did not end");
				Forever.use(second);
				context.assertTrue(Forever.locked(husk), "the target could not be locked again after 3 s");
				finish(context, first, husk);
				finish(context, second, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, first, husk);
			finish(context, second, husk);
			throw e;
		}
	}

	/** A locked player cannot move or attack (the server holds them), and everything is given back after. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_player", tickLimit = 120)
	public void aLockedPlayerCannotMoveOrAttack(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 4.0, 3.0, 60.0F);
		ServerPlayerEntity victim = player(context, "victim");
		Vec3d at = context.getAbsolute(new Vec3d(0.5, 40.0, 8.5));
		victim.teleport(context.getWorld(), at.x, at.y, at.z, 0.0F, 0.0F);
		ServerPlayerEntity owner = holder(context, "binder", 0.0, victim);
		try {
			Forever.use(owner);
			context.assertTrue(Forever.locked(victim), "the player was not locked");
			Vec3d held = victim.getPos();
			victim.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.PositionAndOnGround(held.x + 2.0, held.y, held.z, true));
			context.assertTrue(victim.getPos().squaredDistanceTo(held) < 1.0E-4, "a locked player moved: " + victim.getPos());
			float health = husk.getHealth();
			victim.attack(husk);
			context.assertTrue(husk.getHealth() == health, "a locked player attacked");
			context.runAtTick(80, () -> {
				context.assertTrue(!Forever.locked(victim), "the lock did not end");
				float h = husk.getHealth();
				victim.attack(husk);
				context.assertTrue(husk.getHealth() < h, "the player could not attack after the lock");
				finish(context, owner, husk);
				leave(context, victim);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, owner, husk);
			leave(context, victim);
			throw e;
		}
	}

	/** If the target is killed mid-lock (a command, the void) everything is cleaned up: no copies, no flag, no hold, nothing stored goes off. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_kill", tickLimit = 100)
	public void killingALockedTargetCleansUp(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		ServerPlayerEntity player = holder(context, "killer", 0.0, husk);
		try {
			context.runAtTick(2, () -> {
				Forever.use(player);
				context.assertTrue(Forever.locked(husk), "not locked");
				// (A kill command is a generic kill: it goes through the lock.)
				husk.damage(context.getWorld().getDamageSources().genericKill(), 1000.0F);
				husk.kill();
			});
			context.runAtTick(40, () -> {
				context.assertTrue(!husk.isAlive(), "the husk is alive");
				context.assertTrue(!Forever.locked(husk) && !ForeverFlags.SERVER.contains(husk.getId()), "the lock was left");
				context.assertTrue(copiesInWorld(context) == 0, "copies were left");
				context.assertTrue(context.getWorld().getEntitiesByType(ChainsawMan.FOREVER_HUB, e -> true).isEmpty(), "the hold was left");
				context.assertTrue(!Forever.active(player), "the owner is still marked as holding it");
				finish(context, player, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	/** If the user leaves, the moment still ends normally. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_leave", tickLimit = 100)
	public void theMomentEndsEvenIfTheUserLeaves(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		ServerPlayerEntity player = holder(context, "leaver", 0.0, husk);
		try {
			context.runAtTick(2, () -> {
				Forever.use(player);
				Forever.endsIn(husk, 8);
				husk.damage(context.getWorld().getDamageSources().playerAttack(player), 4.0F);
				leave(context, player);
			});
			context.runAtTick(30, () -> {
				context.assertTrue(!Forever.locked(husk) && copiesInWorld(context) == 0, "the moment did not end after the user left");
				context.assertTrue(husk.getHealth() < 60.0F, "the stored hit did not go off after the user left");
				husk.discard();
				forced(context, false);
				context.complete();
			});
		} catch (RuntimeException e) {
			husk.discard();
			throw e;
		}
	}

	/** History is kept only while someone near holds the contract, and only for what is near. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_forever_history", tickLimit = 60)
	public void historyIsKeptNearContractHolders(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		ServerPlayerEntity player = holder(context, "historian", 0.0, husk);
		try {
			context.runAtTick(20, () -> {
				context.assertTrue(Forever.historyLength(husk) >= 15 && Forever.historyLength(husk) <= Forever.HISTORY, "history length " + Forever.historyLength(husk));
				Contracts.clear(player);
			});
			context.runAtTick(24, () -> {
				context.assertTrue(Forever.historyLength(husk) == 0, "history was kept with no contract holder near");
				finish(context, player, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	// ---- The test ground and helpers.

	/** The test ground: a flat floor at y 39 with a lava pit, pillars, a ledge and a slope (so copies have to find ground at different heights). */
	private static void terrain(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockPos at = BlockPos.ofFloored(context.getAbsolute(new Vec3d(0.5, 40.0, 0.5))).down();
		forced(context, true);
		for (int x = -12; x <= 12; x++) {
			for (int z = -10; z <= 18; z++) {
				int top = 0;
				boolean pillar = x == 4 && z == 5 || x == -3 && z == 9 || x == 5 && z == 11 || x == -5 && z == 7 || x == 2 && z == 12;
				boolean ledge = x >= -7 && x <= -5 && z >= 10 && z <= 12;
				boolean slope = x >= 5 && x <= 9 && z >= 6 && z <= 9;
				if (pillar) {
					top = 2;
				} else if (ledge) {
					top = 1;
				} else if (slope) {
					top = Math.min(3, x - 4);
				}
				for (int y = -9; y <= 0; y++) {
					world.setBlockState(at.add(x, y, z), Blocks.STONE.getDefaultState());
				}
				for (int y = 1; y <= top; y++) {
					world.setBlockState(at.add(x, y, z), Blocks.STONE.getDefaultState());
				}
				for (int y = top + 1; y <= 14; y++) {
					world.setBlockState(at.add(x, y, z), Blocks.AIR.getDefaultState());
				}
				if (x >= 2 && x <= 3 && z >= 8 && z <= 9) {
					world.setBlockState(at.add(x, 0, z), Blocks.LAVA.getDefaultState());
				}
			}
		}
	}

	private static void forced(TestContext context, boolean on) {
		BlockPos at = BlockPos.ofFloored(context.getAbsolute(new Vec3d(0.5, 40.0, 0.5))).down();
		for (int cx = (at.getX() - 14) >> 4; cx <= (at.getX() + 14) >> 4; cx++) {
			for (int cz = (at.getZ() - 12) >> 4; cz <= (at.getZ() + 20) >> 4; cz++) {
				context.getWorld().setChunkForced(cx, cz, on);
			}
		}
	}

	private static HuskEntity husk(TestContext context, double x, double z, float health) {
		return husk(context, x, z, health, 0.0);
	}

	/** A husk standing still at {@code (x, z)}, {@code up} blocks over the floor, with {@code health} (no armour: the numbers asked about are what Forever deals). */
	private static HuskEntity husk(TestContext context, double x, double z, float health, double up) {
		Vec3d at = context.getAbsolute(new Vec3d(0.5 + x, 40.0 + up, 0.5 + z));
		HuskEntity husk = new HuskEntity(EntityType.HUSK, context.getWorld());
		husk.refreshPositionAndAngles(at.x, at.y, at.z, 180.0F, 0.0F);
		husk.setAiDisabled(true);
		husk.setPersistent();
		husk.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(health);
		husk.setHealth(health);
		husk.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(0.0);
		husk.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(0.0);
		context.getWorld().spawnEntity(husk);
		return husk;
	}

	/** A player with the contract and ten levels on the test ground at {@code x}, looking at {@code at}. */
	private static ServerPlayerEntity holder(TestContext context, String name, double x, LivingEntity at) {
		ServerPlayerEntity player = player(context, name);
		Vec3d feet = context.getAbsolute(new Vec3d(0.5 + x, 40.0, 0.5));
		player.teleport(context.getWorld(), feet.x, feet.y, feet.z, 0.0F, 0.0F);
		Contracts.grant(player, Contract.ETERNITY);
		player.setExperienceLevel(10);
		player.setOnGround(true);
		if (at != null) {
			Vec3d eye = player.getEyePos();
			Vec3d to = at.getPos().add(0.0, at.getHeight() * 0.5, 0.0).subtract(eye);
			player.setYaw((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
			player.setPitch((float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z))));
		}
		return player;
	}

	private static void finish(TestContext context, ServerPlayerEntity player, LivingEntity... mobs) {
		Contracts.clear(player);
		Forever.ready(player);
		leave(context, player);
		for (LivingEntity m : mobs) {
			m.discard();
		}
		forced(context, false);
	}

	private static int copiesInWorld(TestContext context) {
		return context.getWorld().getEntitiesByType(ChainsawMan.FOREVER_COPY, e -> true).size();
	}

	static ServerPlayerEntity player(TestContext context, String name) {
		ServerWorld world = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		Vec3d at = context.getAbsolute(new Vec3d(0.5, 40.0, 0.5));
		player.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		player.onTeleportationDone();
		player.setNoGravity(true);
		return player;
	}

	static void leave(TestContext context, ServerPlayerEntity player) {
		Form.end(player);
		context.getWorld().getServer().getPlayerManager().remove(player);
	}
}
