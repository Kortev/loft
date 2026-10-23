package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.BeamEntity;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.PochitaEntity;
import io.github.kortev.chainsawman.form.Beam;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Frenzy;
import io.github.kortev.chainsawman.form.Grapple;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.Limbs;
import io.github.kortev.chainsawman.form.Merge;
import io.github.kortev.chainsawman.form.MergeTimeline;
import io.github.kortev.chainsawman.form.Saw;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

/**
 * Chainsaw Man's fight, on the server: a slash cuts what is in front (not what is behind) and heals; slashes come no
 * faster than they may; a kill heals a lot; grinding chews and heals; the form drains health, and running out of it
 * leaves you down, not dead; with Pochita nothing kills you, without him it does; someone giving you blood brings you
 * back up at their cost, and lying there long enough does too; the chain yanks what it catches to you and pulls you to
 * a block; the frenzy doubles the saws' damage and won't come again straight away; Beam, the ult, costs blood, carries you,
 * bites what is in front of him, leaps, and dives away.
 */
public class FormGameTests implements FabricGameTest {
	/** How long a player who has just joined can't be hurt (ticks), as the game keeps them. */
	private static final int JOINED = 61;

	/** A slash cuts the thing in front, not the thing behind, and the blood heals. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_slash", tickLimit = 20)
	public void slashCutsWhatIsInFront(TestContext context) {
		ServerPlayerEntity player = fighter(context, "slasher", 0.0);
		HuskEntity front = husk(context, 0.0, 2.0);
		HuskEntity behind = husk(context, 0.0, -2.0);
		player.setHealth(10.0F);
		try {
			context.assertTrue(Saw.slash(player), "the slash did not come");
			context.assertTrue(front.getHealth() < front.getMaxHealth() - 8.0F, "the husk in front was not cut: " + front.getHealth());
			context.assertTrue(behind.getHealth() == behind.getMaxHealth(), "the husk behind was cut");
			context.assertTrue(player.getHealth() == 10.0F + Saw.HIT_HEAL, "the hit did not heal: " + player.getHealth());
			context.assertTrue(!Saw.slash(player), "a second slash came at once");
		} finally {
			done(context, player);
			front.discard();
			behind.discard();
		}
		context.complete();
	}

	/** The kick: what's in front kicked, slowed (dazed), and no second kick at once. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_kick", tickLimit = 20)
	public void kickDazes(TestContext context) {
		ServerPlayerEntity player = fighter(context, "kicker", 0.0);
		HuskEntity front = husk(context, 0.0, 2.0);
		try {
			context.assertTrue(Saw.kick(player), "the kick did not come");
			context.assertTrue(front.getHealth() < front.getMaxHealth(), "the husk was not kicked");
			context.assertTrue(front.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS), "the husk was not dazed");
			context.assertTrue(!Saw.kick(player), "a second kick came at once");
		} finally {
			done(context, player);
			front.discard();
		}
		context.complete();
	}

	/** The saws cut through a raised shield: it's knocked out a while, and the cut lands all the same. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_guard", tickLimit = JOINED + 20)
	public void sawsBeatShields(TestContext context) {
		ServerPlayerEntity player = fighter(context, "cutter", 0.0);
		ServerPlayerEntity guard = player(context, "guard", 0.0);
		Vec3d at = spot(context).add(0.0, 0.0, 2.0);
		guard.teleport(context.getWorld(), at.x, at.y, at.z, 180.0F, 0.0F);
		guard.onTeleportationDone();
		guard.setStackInHand(net.minecraft.util.Hand.OFF_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.SHIELD));
		guard.setCurrentHand(net.minecraft.util.Hand.OFF_HAND);
		// (The test server has players unable to hurt each other.)
		boolean pvp = context.getWorld().getServer().isPvpEnabled();
		context.getWorld().getServer().setPvpEnabled(true);
		// Held up a while (the stand-in never ticks to raise it).
		((io.github.kortev.shootingstar.test.mixin.ItemUseAccessor) guard).shootingstar$setItemUseTimeLeft(guard.getActiveItem().getMaxUseTime(guard) - 10);
		// (Once a new player can be hurt at all.)
		context.waitAndRun(JOINED, () -> {
			try {
				context.assertTrue(guard.isBlocking(), "the guard's shield was not up to begin with");
				float before = guard.getHealth();
				context.assertTrue(Saw.slash(player), "the slash did not come");
				context.assertTrue(guard.getHealth() < before, "the shield stopped the saws");
				context.assertTrue(!guard.isBlocking(), "the shield was still up");
				context.assertTrue(guard.getItemCooldownManager().isCoolingDown(net.minecraft.item.Items.SHIELD), "the shield was not knocked out");
			} finally {
				context.getWorld().getServer().setPvpEnabled(pvp);
				done(context, player);
				done(context, guard);
			}
			context.complete();
		});
	}

	/**
	 * What was under the slasher's crosshair is cut even out at the edge of the sweep, where the slash alone misses it:
	 * what they saw land, lands.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_aim", tickLimit = 40)
	public void aimedHitsLand(TestContext context) {
		ServerPlayerEntity player = fighter(context, "aimer", 0.0);
		HuskEntity side = husk(context, 3.2, 1.4);
		try {
			context.assertTrue(Saw.slash(player), "the first slash did not come");
			context.assertTrue(side.getHealth() >= side.getMaxHealth(), "the slash alone reached the husk off to the side");
		} catch (RuntimeException e) {
			done(context, player);
			side.discard();
			throw e;
		}
		context.waitAndRun(Saw.SLASH_EVERY + 1, () -> {
			try {
				context.assertTrue(Saw.slash(player, side.getId()), "the aimed slash did not come");
				context.assertTrue(side.getHealth() < side.getMaxHealth(), "aimed at, the husk off to the side was not cut (it at "
						+ side.getPos().subtract(player.getPos()) + ", seen " + player.canSee(side) + ", health " + side.getHealth() + ", the slasher fighting "
						+ io.github.kortev.chainsawman.form.Form.fighting(player) + ")");
			} finally {
				done(context, player);
				side.discard();
			}
			context.complete();
		});
	}

	/** In the form the saws cut through blocks many times as fast as hands; out of it, as hands. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_dig", tickLimit = 20)
	public void sawsCutWalls(TestContext context) {
		ServerPlayerEntity player = fighter(context, "cutter", 0.0);
		try {
			float cutting = player.getBlockBreakingSpeed(net.minecraft.block.Blocks.STONE.getDefaultState());
			Form.end(player);
			float hands = player.getBlockBreakingSpeed(net.minecraft.block.Blocks.STONE.getDefaultState());
			context.assertTrue(cutting >= hands * Saw.CUTS * 0.99F, "the saws didn't cut faster: " + cutting + " against " + hands);
		} finally {
			done(context, player);
		}
		context.complete();
	}

	/**
	 * Sawing through a tree's trunk in the form brings the whole tree down, and it bursts into drops where it lands (none of it
	 * set back down as blocks); a log wall without leaves of its own is cut a block at a time.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_fell", tickLimit = 140)
	public void sawsFellTrees(TestContext context) {
		ServerPlayerEntity player = fighter(context, "feller", 0.0);
		ServerWorld world = context.getWorld();
		BlockPos base = BlockPos.ofFloored(spot(context)).add(0, 0, 3);
		BlockPos wall = base.add(5, 0, 0);
		for (BlockPos p : BlockPos.iterate(base.add(-12, -1, -12), base.add(12, -1, 12))) {
			world.setBlockState(p, Blocks.STONE.getDefaultState());
		}
		for (int y = 0; y < 6; y++) {
			world.setBlockState(base.up(y), Blocks.OAK_LOG.getDefaultState());
		}
		for (BlockPos p : BlockPos.iterate(base.add(-2, 3, -2), base.add(2, 6, 2))) {
			if (world.isAir(p)) {
				world.setBlockState(p, Blocks.OAK_LEAVES.getDefaultState());
			}
		}
		for (int y = 0; y < 3; y++) {
			world.setBlockState(wall.up(y), Blocks.OAK_LOG.getDefaultState());
		}
		try {
			context.assertTrue(player.interactionManager.tryBreakBlock(base), "the trunk's bottom log didn't break");
			for (int y = 1; y < 6; y++) {
				context.assertTrue(!world.getBlockState(base.up(y)).isIn(BlockTags.LOGS), "the trunk still stands " + y + " up");
			}
			context.assertTrue(player.interactionManager.tryBreakBlock(wall), "the wall's log didn't break");
			context.assertTrue(world.getBlockState(wall.up()).isOf(Blocks.OAK_LOG), "a log wall came down like a tree");
		} catch (RuntimeException e) {
			done(context, player);
			throw e;
		}
		context.waitAndRun(110, () -> {
			try {
				int logs = 0;
				for (BlockPos p : BlockPos.iterate(base.add(-12, 0, -12), base.add(12, 10, 12))) {
					if (world.getBlockState(p).isIn(BlockTags.LOGS) || world.getBlockState(p).isIn(BlockTags.LEAVES)) {
						logs++;
					}
				}
				context.assertTrue(logs == 2, "the tree was set back down as blocks (" + logs + " logs and leaves about, not the wall's 2)");
			} finally {
				done(context, player);
				for (BlockPos p : BlockPos.iterate(base.add(-12, -1, -12), base.add(12, 10, 12))) {
					world.setBlockState(p, Blocks.AIR.getDefaultState());
				}
			}
			context.complete();
		});
	}

	/** A kill drinks deep: the hit's heal and the kill's. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_kill", tickLimit = 20)
	public void killHeals(TestContext context) {
		ServerPlayerEntity player = fighter(context, "killer", 0.0);
		HuskEntity front = husk(context, 0.0, 2.0);
		front.setHealth(1.0F);
		player.setHealth(4.0F);
		try {
			Saw.slash(player);
			context.assertTrue(!front.isAlive(), "the husk lived");
			context.assertTrue(player.getHealth() == 4.0F + Saw.HIT_HEAL + Saw.KILL_HEAL, "the kill did not heal: " + player.getHealth());
		} finally {
			done(context, player);
			front.discard();
		}
		context.complete();
	}

	/** Held, the saws grind into what is in front every few ticks. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_grind", tickLimit = 40)
	public void grindChews(TestContext context) {
		ServerPlayerEntity player = fighter(context, "grinder", 0.0);
		HuskEntity front = husk(context, 0.0, 1.8);
		Saw.grind(player, true);
		context.waitAndRun(Saw.GRIND_EVERY * 5L, () -> {
			try {
				context.assertTrue(Saw.grinders().contains(player.getUuid()), "the grinding stopped");
				context.assertTrue(front.getHealth() < front.getMaxHealth() - Saw.GRIND_DAMAGE * 3.0F, "the grind did not chew: " + front.getHealth());
			} finally {
				Saw.grind(player, false);
				done(context, player);
				front.discard();
			}
			context.complete();
		});
	}

	/** The form costs a point of health every DRAIN_EVERY ticks; out of it, you go down, not die. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_drain", tickLimit = Saw.DRAIN_EVERY * 4 + 20)
	public void drainTakesYouDown(TestContext context) {
		ServerPlayerEntity player = fighter(context, "drained", 0.0);
		player.setHealth(3.0F);
		context.waitAndRun(Saw.DRAIN_EVERY * 4L + 5, () -> {
			try {
				context.assertTrue(player.isAlive(), "the drain killed");
				context.assertTrue(Downed.is(player), "out of blood, they were not down");
				context.assertTrue(!Form.on(player), "down, they were still in the form");
			} finally {
				done(context, player);
			}
			context.complete();
		});
	}

	/** With Pochita what would kill you leaves you down, and nothing more hurts you; without him you die. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_down", tickLimit = JOINED + 20)
	public void pochitaKeepsYouAlive(TestContext context) {
		ServerPlayerEntity carrier = fighter(context, "carrier", 0.0);
		ServerPlayerEntity unarmed = player(context, "no_pochita", 3.0);
		context.waitAndRun(JOINED, () -> {
			try {
				carrier.damage(carrier.getDamageSources().generic(), 1000.0F);
				context.assertTrue(carrier.isAlive() && carrier.getHealth() >= 1.0F, "with Pochita, they died");
				context.assertTrue(Downed.is(carrier), "with Pochita, they were not down");
				context.assertTrue(!carrier.damage(carrier.getDamageSources().generic(), 5.0F), "down, they were hurt");
				unarmed.damage(unarmed.getDamageSources().generic(), 1000.0F);
				context.assertTrue(unarmed.isDead(), "without Pochita, they lived");
			} finally {
				done(context, carrier);
				done(context, unarmed);
			}
			context.complete();
		});
	}

	/** Down, the mobs give up on you: the one after you lets you be, and none can take you for a target till you're up. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_unseen", tickLimit = 20)
	public void downedMobsLetYouBe(TestContext context) {
		ServerPlayerEntity player = fighter(context, "lying", 0.0);
		HuskEntity husk = husk(context, 0.0, 2.0);
		try {
			husk.setTarget(player);
			context.assertTrue(husk.getTarget() == player && husk.canTarget(player), "the husk was not after them to begin with");
			Downed.down(player);
			context.assertTrue(husk.getTarget() == null, "down, the husk was still after them");
			context.assertTrue(!husk.canTarget(player), "down, the husk could take them for a target");
			Downed.up(player);
			context.assertTrue(husk.canTarget(player), "up, the husk could not go after them again");
		} finally {
			done(context, player);
			husk.discard();
		}
		context.complete();
	}

	/** Someone holding use on you, crouched, gives you their blood and brings you back up. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_feed", tickLimit = Downed.FEED + 40)
	public void bloodBringsYouBack(TestContext context) {
		ServerPlayerEntity down = fighter(context, "fallen", 0.0);
		ServerPlayerEntity friend = player(context, "friend", 1.2);
		Downed.down(down);
		friend.setSneaking(true);
		float before = friend.getHealth();
		for (int t = 0; t <= Downed.FEED + 8; t += 4) {
			context.runAtTick(t + 1, () -> Downed.feeding(friend, down));
		}
		context.runAtTick(Downed.FEED + 14, () -> {
			try {
				context.assertTrue(!Downed.is(down), "given blood, they were still down");
				context.assertTrue(down.getHealth() == Downed.GIVEN, "they came back with " + down.getHealth());
				context.assertTrue(friend.getHealth() <= before - Downed.GIVEN + 0.01F, "the friend gave nothing: " + friend.getHealth());
			} finally {
				done(context, down);
				done(context, friend);
			}
			context.complete();
		});
	}

	/** Left alone long enough, you come back by yourself. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_alone", tickLimit = 20)
	public void aloneYouComeBack(TestContext context) {
		ServerPlayerEntity down = fighter(context, "alone", 0.0);
		Downed.down(down);
		down.setAttached(Downed.LEFT, 3);
		context.waitAndRun(6, () -> {
			try {
				context.assertTrue(!Downed.is(down), "they were still down");
				context.assertTrue(down.getHealth() == Downed.GIVEN, "they came back with " + down.getHealth());
			} finally {
				done(context, down);
			}
			context.complete();
		});
	}

	/** The chain yanks what it catches to you and holds it; let go and fired at a block, it pulls you to it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_chain", tickLimit = 20)
	public void chainYanksAndPulls(TestContext context) {
		ServerPlayerEntity player = fighter(context, "chainer", 0.0);
		HuskEntity far = husk(context, 0.0, 5.0);
		player.setYaw(0.0F);
		player.setPitch(0.0F);
		try {
			HitResult hit = Grapple.fire(player);
			context.assertTrue(hit != null && hit.getType() == HitResult.Type.ENTITY, "the chain did not catch the husk: "
					+ (hit == null ? "nothing" : hit.getType() + " at " + hit.getPos()));
			context.assertTrue(far.getVelocity().z < -0.5, "the husk was not yanked: " + far.getVelocity());
			far.discard();
			BlockPos wall = BlockPos.ofFloored(player.getEyePos().add(0.0, 0.0, 4.0));
			context.getWorld().setBlockState(wall, Blocks.STONE.getDefaultState());
			context.assertTrue(Grapple.holding(player), "the husk was not held on the chain");
			Grapple.release(player);
			context.assertTrue(!Grapple.holding(player), "the chain did not let go");
			Grapple.ready(player);
			player.setVelocity(Vec3d.ZERO);
			player.setYaw(0.0F);
			HitResult block = Grapple.fire(player);
			context.assertTrue(block != null && block.getType() == HitResult.Type.BLOCK, "the chain did not catch the block");
			context.assertTrue(player.getVelocity().z > 0.5, "they were not pulled: " + player.getVelocity());
			context.getWorld().setBlockState(wall, Blocks.AIR.getDefaultState());
		} finally {
			done(context, player);
			far.discard();
		}
		context.complete();
	}

	/** In a frenzy the saws do double, and it won't come again straight away. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_frenzy", tickLimit = 20)
	public void frenzyDoubles(TestContext context) {
		ServerPlayerEntity player = fighter(context, "berserk", 0.0);
		HuskEntity front = husk(context, 0.0, 2.0);
		try {
			context.assertTrue(Frenzy.start(player), "the frenzy did not come");
			context.assertTrue(Frenzy.damage(player) == Frenzy.DAMAGE, "the frenzy did not double the saws");
			Saw.slash(player);
			context.assertTrue(!front.isAlive() || front.getHealth() < front.getMaxHealth() - 18.0F, "the frenzied slash was not doubled: " + front.getHealth());
			Frenzy.end(player);
			context.assertTrue(!Frenzy.start(player), "the frenzy came again at once");
		} finally {
			Frenzy.ready(player);
			done(context, player);
			front.discard();
		}
		context.complete();
	}

	/** Enough harm in the form takes an arm off, then the other; a kill grows one back; out of the form both are back. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_limbs", tickLimit = JOINED + 20)
	public void armsComeOffAndGrowBack(TestContext context) {
		ServerPlayerEntity player = fighter(context, "limbless", 0.0);
		context.waitAndRun(JOINED, () -> {
			HuskEntity front = husk(context, 0.0, 2.0);
			try {
				player.damage(player.getDamageSources().generic(), Limbs.EVERY + 0.5F);
				context.assertTrue(!Limbs.has(player, Limbs.RIGHT) && Limbs.has(player, Limbs.LEFT), "the right arm did not come off first");
				player.setHealth(player.getMaxHealth());
				player.timeUntilRegen = 0;
				player.damage(player.getDamageSources().generic(), Limbs.EVERY + 0.5F);
				context.assertTrue(!Limbs.has(player, Limbs.LEFT), "the left arm did not come off");
				front.setHealth(1.0F);
				context.assertTrue(Saw.slash(player), "with no arms, the head's saw did not slash");
				context.assertTrue(!front.isAlive(), "the head's saw did not kill");
				context.assertTrue(Limbs.has(player, Limbs.LEFT), "the kill did not grow an arm back");
				Form.end(player);
				context.assertTrue(Limbs.has(player, Limbs.RIGHT), "out of the form, the arm was still gone");
			} finally {
				done(context, player);
				front.discard();
			}
			context.complete();
		});
	}

	/** The cord pulled in the form turns you back: no fighting while the saws go in, and out of the form at the splash. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_back", tickLimit = io.github.kortev.chainsawman.form.FormTimeline.BACK_SPLASH + 20)
	public void cordTurnsYouBack(TestContext context) {
		ServerPlayerEntity player = player(context, "turning", 0.0);
		player.setAttached(Heart.MERGED, true);
		Form.start(player);
		Form.pullCord(player);
		try {
			context.assertTrue(Form.on(player) && Form.turning(player) && !Form.fighting(player), "pulling the cord in the form didn't start turning back");
		} catch (RuntimeException e) {
			done(context, player);
			throw e;
		}
		context.runAtTick(io.github.kortev.chainsawman.form.FormTimeline.BACK_SPLASH + 5, () -> {
			try {
				context.assertTrue(!Form.on(player) && !Form.turning(player), "they were still in the form after turning back");
			} finally {
				done(context, player);
			}
			context.complete();
		});
	}

	/** The cord needs blood to start the engine, and takes it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_blood", tickLimit = 20)
	public void cordNeedsBlood(TestContext context) {
		ServerPlayerEntity player = player(context, "bloodless", 0.0);
		player.setAttached(Heart.MERGED, true);
		try {
			Blood.set(player, Blood.TRANSFORM - 1);
			Form.pullCord(player);
			context.assertTrue(Blood.get(player) == Blood.TRANSFORM - 1, "the cord took blood it didn't have");
			Blood.set(player, Blood.MAX);
			Form.pullCord(player);
			context.assertTrue(Blood.get(player) == Blood.MAX - Blood.TRANSFORM, "the cord didn't take its blood: " + Blood.get(player));
		} finally {
			done(context, player);
		}
		context.complete();
	}

	/**
	 * Carrying Pochita, kortev dying the first time doesn't: he becomes their heart, and they come back on their feet,
	 * waiting to pull the cord; the pull is free, and transforms them.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_merge", tickLimit = JOINED + MergeTimeline.END + 30)
	public void pochitaBecomesYourHeart(TestContext context) {
		ServerPlayerEntity player = player(context, ChainsawMan.OWNER, 0.0);
		player.getInventory().insertStack(new ItemStack(ChainsawMan.POCHITA));
		context.waitAndRun(JOINED, () -> {
			try {
				player.damage(player.getDamageSources().generic(), 1000.0F);
				context.assertTrue(player.isAlive() && Merge.is(player), "they didn't start merging");
				context.assertTrue(!io.github.kortev.chainsawman.PochitaItem.carried(player), "Pochita was still in their pack");
				context.assertTrue(!player.damage(player.getDamageSources().generic(), 5.0F), "merging, they were hurt");
			} catch (RuntimeException e) {
				done(context, player);
				throw e;
			}
			context.runAtTick(JOINED + MergeTimeline.END + 10, () -> {
				try {
					context.assertTrue(Heart.merged(player), "Pochita didn't become their heart");
					context.assertTrue(player.getHealth() == player.getMaxHealth(), "they didn't come back whole");
					context.assertTrue(!Downed.is(player), "they were down, not back");
					context.assertTrue(Merge.waiting(player) && !Form.pulling(player), "the cord didn't wait for them to pull it");
					context.assertTrue(!player.damage(player.getDamageSources().generic(), 5.0F), "waiting to pull it, they were hurt");
					Form.pullCord(player);
					context.assertTrue(!Merge.is(player) && Form.pulling(player), "pulling the cord didn't end the merge and transform them");
					context.assertTrue(Blood.get(player) == Blood.MAX, "the first pull took blood: " + Blood.get(player));
				} finally {
					done(context, player);
					player.removeAttached(Heart.MERGED);
				}
				context.complete();
			});
		});
	}

	/** Pochita takes kortev's blood until he's theirs; anyone else's he won't. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_tame", tickLimit = PochitaEntity.FEED + 40)
	public void bloodTamesPochita(TestContext context) {
		ServerPlayerEntity owner = player(context, ChainsawMan.OWNER, 0.0);
		ServerPlayerEntity other = player(context, "stranger", 2.0);
		PochitaEntity pochita = new PochitaEntity(ChainsawMan.POCHITA_ENTITY, context.getWorld());
		Vec3d at = spot(context).add(0.0, 0.0, 1.5);
		pochita.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		pochita.setNoGravity(true);
		context.getWorld().spawnEntity(pochita);
		for (int t = 0; t <= 8; t += 4) {
			context.runAtTick(t + 1, () -> pochita.feed(other));
		}
		for (int t = 12; t <= PochitaEntity.FEED + 20; t += 4) {
			context.runAtTick(t + 1, () -> {
				if (!pochita.isTamed()) {
					pochita.feed(owner);
				}
			});
		}
		context.runAtTick(PochitaEntity.FEED + 30, () -> {
			try {
				context.assertTrue(pochita.isTamed() && pochita.isOwner(owner), "kortev's blood didn't tame him");
			} finally {
				pochita.discard();
				done(context, owner);
				done(context, other);
			}
			context.complete();
		});
	}

	/** Pochita doesn't die: he faints, and kortev's blood brings him round. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_faint", tickLimit = PochitaEntity.WAKE + 40)
	public void pochitaFaints(TestContext context) {
		ServerPlayerEntity owner = player(context, ChainsawMan.OWNER, 0.0);
		PochitaEntity pochita = new PochitaEntity(ChainsawMan.POCHITA_ENTITY, context.getWorld());
		Vec3d at = spot(context).add(0.0, 0.0, 1.5);
		pochita.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		pochita.setNoGravity(true);
		pochita.setOwner(owner);
		pochita.setTamed(true, true);
		context.getWorld().spawnEntity(pochita);
		pochita.damage(context.getWorld().getDamageSources().generic(), 1000.0F);
		context.assertTrue(pochita.isAlive() && pochita.fainted(), "Pochita died, or didn't faint");
		for (int t = 0; t <= PochitaEntity.WAKE + 12; t += 4) {
			context.runAtTick(t + 1, () -> {
				if (pochita.fainted()) {
					pochita.feed(owner);
				}
			});
		}
		context.runAtTick(PochitaEntity.WAKE + 25, () -> {
			try {
				context.assertTrue(!pochita.fainted(), "kortev's blood didn't bring him round");
			} finally {
				pochita.discard();
				done(context, owner);
			}
			context.complete();
		});
	}

	/** Pochita held as a chainsaw: the same slash, at half the damage, and nothing drunk. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_wield", tickLimit = 20)
	public void heldPochitaSlashes(TestContext context) {
		ServerPlayerEntity player = player(context, "wielder", 0.0);
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(ChainsawMan.POCHITA));
		HuskEntity front = husk(context, 0.0, 2.0);
		try {
			context.assertTrue(Saw.slash(player), "held, Pochita didn't slash");
			float lost = front.getMaxHealth() - front.getHealth();
			context.assertTrue(lost > Saw.SLASH_DAMAGE * Saw.WIELDED * 0.8F && lost < Saw.SLASH_DAMAGE * 0.8F, "the slash did " + lost);
		} finally {
			done(context, player);
			front.discard();
		}
		context.complete();
	}

	/** Beam costs blood and puts you on his back; one to a player. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_summon", tickLimit = 20)
	public void beamCarriesYou(TestContext context) {
		ServerPlayerEntity player = fighter(context, "rider", 0.0);
		ground(context, true);
		Blood.set(player, Blood.MAX);
		try {
			context.assertTrue(Beam.summon(player), "Beam did not come");
			context.assertTrue(Blood.get(player) == Blood.MAX - Blood.BEAM, "Beam did not cost his blood: " + Blood.get(player));
			context.assertTrue(player.getVehicle() instanceof BeamEntity, "the player is not on Beam");
			context.assertTrue(Beam.active(player) == player.getVehicle(), "Beam is not the player's own");
			context.assertTrue(!Beam.summon(player), "a second Beam came");
			context.assertTrue(Blood.get(player) == Blood.MAX - Blood.BEAM, "a second call cost blood");
		} finally {
			done(context, player);
			ground(context, false);
		}
		context.complete();
	}

	/** Out of the form, or without the blood, Beam does not come and nothing is spent. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_refused", tickLimit = 20)
	public void beamNeedsTheFormAndTheBlood(TestContext context) {
		ServerPlayerEntity plain = player(context, "plain", 0.0);
		ServerPlayerEntity weak = fighter(context, "weak", 4.0);
		ground(context, true);
		Blood.set(plain, Blood.MAX);
		Blood.set(weak, Blood.BEAM - 1);
		try {
			context.assertTrue(!Beam.summon(plain), "Beam came to someone out of the form");
			context.assertTrue(Blood.get(plain) == Blood.MAX && !plain.hasVehicle(), "the one out of the form lost blood or was carried");
			context.assertTrue(!Beam.summon(weak), "Beam came without the blood for him");
			context.assertTrue(Blood.get(weak) == Blood.BEAM - 1 && !weak.hasVehicle(), "the one short of blood lost blood or was carried");
		} finally {
			done(context, plain);
			done(context, weak);
			ground(context, false);
		}
		context.complete();
	}

	/**
	 * The forward key sends him the way you face, fast, and he swims in the ground: sunk so that only his back and the rider
	 * show; stopped, he rises out onto his legs again.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_swim", tickLimit = 80)
	public void beamSwimsWhenMovingAndRisesWhenStopped(TestContext context) {
		ServerPlayerEntity player = fighter(context, "walker", 0.0);
		ground(context, true, 40);
		Blood.set(player, Blood.MAX);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		double z0 = spot(context).z;
		double surface = spot(context).y;
		double[] speed = {0.0};
		context.runAtTick(Beam.POP_TICKS - 1, () -> context.assertTrue(Math.abs(beam.getY() - surface) < 0.2, "he is not standing on his legs after his summoning: " + (beam.getY() - surface)));
		context.runAtTick(Beam.POP_TICKS + 1, () -> sprintAhead(player));
		context.runAtTick(Beam.POP_TICKS + 20, () -> {
			speed[0] = beam.getZ();
			context.assertTrue(beam.isAlive() && player.getVehicle() == beam, "he dived or lost his rider while swimming");
			context.assertTrue(beam.getZ() - z0 > 12.0, "he did not go far forward: " + (beam.getZ() - z0));
			context.assertTrue(beam.getY() < surface - Beam.SINK * 0.7, "he is not swimming in the ground: " + (beam.getY() - surface));
			context.assertTrue(beam.swimming() > 0.95F, "the clients are not told he swims: " + beam.swimming());
			context.assertTrue(Math.abs(player.getZ() - beam.getZ()) < 1.5, "the rider was left behind");
			context.assertTrue(player.getY() > surface + 0.5 && Math.abs(player.getY() - (beam.getY() + BeamEntity.BACK)) < 0.3, "the rider is not on his back above the ground: " + (player.getY() - surface));
		});
		context.runAtTick(Beam.POP_TICKS + 21, () -> speed[0] = beam.getZ());
		context.runAtTick(Beam.POP_TICKS + 22, () -> context.assertTrue(beam.getZ() - speed[0] > Beam.SPEED * 0.7, "he is not fast: " + (beam.getZ() - speed[0]) + " blocks in a tick"));
		context.runAtTick(Beam.POP_TICKS + 24, () -> player.updateInput(0.0F, 0.0F, false, false));
		context.runAtTick(Beam.POP_TICKS + 48, () -> {
			try {
				context.assertTrue(beam.getY() > surface - 0.25, "he did not rise onto his legs when he stopped: " + (beam.getY() - surface));
				context.assertTrue(beam.swimming() < 0.05F, "he still swims when stopped: " + beam.swimming());
			} finally {
				done(context, player);
				ground(context, false, 40);
			}
			context.complete();
		});
	}

	/**
	 * Without sprinting the forward key walks him: he stays up on his legs on top of the ground at about his old walking speed, the
	 * rider above him, and nothing is dug up.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_walk", tickLimit = 70)
	public void beamWalksOnHisLegs(TestContext context) {
		ServerPlayerEntity player = fighter(context, "stroller", 0.0);
		ground(context, true, 40);
		Blood.set(player, Blood.MAX);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		double z0 = spot(context).z;
		double surface = spot(context).y;
		double[] last = {0.0};
		context.runAtTick(Beam.POP_TICKS + 1, () -> walkAhead(player));
		context.runAtTick(Beam.POP_TICKS + 14, () -> last[0] = beam.getZ());
		context.runAtTick(Beam.POP_TICKS + 30, () -> {
			try {
				double each = (beam.getZ() - last[0]) / 16.0;
				context.assertTrue(beam.getZ() - z0 > 8.0, "he did not walk ahead: " + (beam.getZ() - z0));
				context.assertTrue(each > Beam.WALK * 0.85 && each < Beam.WALK * 1.1, "he did not walk at his walking speed: " + each + " blocks a tick, not " + Beam.WALK);
				context.assertTrue(beam.getY() > surface - 0.1 && beam.getY() < surface + 0.3, "he is not up on his legs on the ground: " + (beam.getY() - surface));
				context.assertTrue(beam.swimming() < 0.05F, "the clients are told he swims: " + beam.swimming());
				context.assertTrue(player.getVehicle() == beam && Math.abs(player.getY() - (beam.getY() + BeamEntity.BACK)) < 0.3, "the rider is not on his back");
				context.assertTrue(!beam.airborne(), "he is in the air on the ground");
			} finally {
				done(context, player);
				ground(context, false, 40);
			}
			context.complete();
		});
	}

	/** Walking he is stopped by a wall (and breaks nothing, mobGriefing on), and steps up a block in his way. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_walk_wall", tickLimit = 120)
	public void beamWalksUpStepsAndStopsAtAWall(TestContext context) {
		ServerPlayerEntity player = fighter(context, "stepper", 0.0);
		ground(context, true, 40);
		Blood.set(player, Blood.MAX);
		BlockPos step = BlockPos.ofFloored(spot(context)).add(0, 0, 8);
		BlockPos wall = BlockPos.ofFloored(spot(context)).add(0, 0, 34);
		for (int x = -3; x <= 3; x++) {
			for (int z = 0; z < 14; z++) {
				context.getWorld().setBlockState(step.add(x, 0, z), Blocks.STONE.getDefaultState());
			}
		}
		wallOf(context, wall, Blocks.STONE);
		boolean grief = context.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
		context.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING).set(true, context.getWorld().getServer());
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		double surface = spot(context).y;
		double[] onStep = {-100.0};
		context.runAtTick(Beam.POP_TICKS + 1, () -> walkAhead(player));
		for (int t = Beam.POP_TICKS + 8; t < Beam.POP_TICKS + 60; t++) {
			context.runAtTick(t, () -> {
				if (beam.getZ() > step.getZ() + 5.0 && beam.getZ() < step.getZ() + 12.0) {
					onStep[0] = Math.max(onStep[0], beam.getY());
				}
			});
		}
		context.runAtTick(Beam.POP_TICKS + 90, () -> {
			try {
				context.assertTrue(onStep[0] > surface + 0.9, "he did not step up the block in his way: " + (onStep[0] - surface));
				context.assertTrue(beam.getZ() < wall.getZ() - 2.5, "he walked into the wall: " + (wall.getZ() - beam.getZ()) + " blocks short of it");
				context.assertTrue(beam.getZ() > wall.getZ() - 6.0, "he did not walk up to the wall: " + (wall.getZ() - beam.getZ()) + " blocks short of it");
				context.assertTrue(!context.getWorld().getBlockState(wall.add(0, 1, 0)).isAir(), "he broke the wall walking");
				context.assertTrue(beam.getY() > surface - 0.1 && beam.swimming() < 0.05F, "he is not up on his legs at the wall");
			} finally {
				context.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING).set(grief, context.getWorld().getServer());
				done(context, player);
				for (int x = -3; x <= 3; x++) {
					for (int z = 0; z < 14; z++) {
						context.getWorld().setBlockState(step.add(x, 0, z), Blocks.AIR.getDefaultState());
					}
				}
				wallOf(context, wall, Blocks.AIR);
				ground(context, false, 40);
			}
			context.complete();
		});
	}

	/** Sprinting he dives (sinks into the ground, fast); letting go of sprint, still walking forward, he surfaces onto his legs and slows to a walk. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_sprint", tickLimit = 100)
	public void sprintDivesAndLettingGoSurfaces(TestContext context) {
		ServerPlayerEntity player = fighter(context, "sprinter", 0.0);
		ground(context, true, 60);
		Blood.set(player, Blood.MAX);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		double surface = spot(context).y;
		double[] last = {0.0};
		context.runAtTick(Beam.POP_TICKS + 1, () -> walkAhead(player));
		context.runAtTick(Beam.POP_TICKS + 12, () -> context.assertTrue(beam.swimming() < 0.05F && beam.getY() > surface - 0.1, "he dived without sprinting"));
		context.runAtTick(Beam.POP_TICKS + 13, () -> player.setSprinting(true));
		context.runAtTick(Beam.POP_TICKS + 15, () -> context.assertTrue(beam.swimming() > 0.0F, "he did not begin to dive when the rider sprinted"));
		context.runAtTick(Beam.POP_TICKS + 33, () -> {
			last[0] = beam.getZ();
			context.assertTrue(beam.swimming() > 0.95F && beam.getY() < surface - Beam.SINK * 0.7, "sprinting did not sink him: " + (beam.getY() - surface));
		});
		context.runAtTick(Beam.POP_TICKS + 34, () -> context.assertTrue(beam.getZ() - last[0] > Beam.SPEED * 0.9, "he is not swimming fast: " + (beam.getZ() - last[0])));
		context.runAtTick(Beam.POP_TICKS + 35, () -> player.setSprinting(false));
		context.runAtTick(Beam.POP_TICKS + 52, () -> last[0] = beam.getZ());
		context.runAtTick(Beam.POP_TICKS + 68, () -> {
			try {
				context.assertTrue(beam.getY() > surface - 0.1, "he did not surface when the sprint was let go: " + (beam.getY() - surface));
				context.assertTrue(beam.swimming() < 0.05F, "the clients are told he still swims: " + beam.swimming());
				double each = (beam.getZ() - last[0]) / 16.0;
				context.assertTrue(each > 0.3 && each < Beam.WALK * 1.05, "he does not walk on after surfacing: " + each + " blocks a tick");
				context.assertTrue(player.getVehicle() == beam, "the rider was lost");
			} finally {
				done(context, player);
				ground(context, false, 60);
			}
			context.complete();
		});
	}

	/** With {@code mobGriefing} on, he tears through a wall: the blocks in his way are broken (his lane), those beside it are not, and he comes out the other side. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_wall", tickLimit = 130)
	public void beamTearsThroughAWall(TestContext context) {
		ServerPlayerEntity player = fighter(context, "walled", 0.0);
		ground(context, true, 40);
		Blood.set(player, Blood.MAX);
		BlockPos wall = BlockPos.ofFloored(spot(context)).add(0, 0, 10);
		wallOf(context, wall, Blocks.STONE);
		boolean grief = context.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
		context.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING).set(true, context.getWorld().getServer());
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		context.runAtTick(Beam.POP_TICKS + 1, () -> sprintAhead(player));
		context.runAtTick(Beam.POP_TICKS + 30, () -> {
			try {
				player.updateInput(0.0F, 0.0F, false, false);
				context.assertTrue(beam.getZ() > wall.getZ() + 4.0, "he did not come out the other side: " + (beam.getZ() - wall.getZ()));
				for (int x = -1; x <= 1; x++) {
					for (int y = 0; y < 3; y++) {
						context.assertTrue(context.getWorld().getBlockState(wall.add(x, y, 0)).isAir(), "the wall is still there at " + x + ", " + y);
					}
				}
				context.assertTrue(!context.getWorld().getBlockState(wall.add(3, 1, 0)).isAir(), "he broke the wall beside his lane");
				context.assertTrue(player.getVehicle() == beam && player.getHealth() > player.getMaxHealth() - 1.0F, "the rider was thrown or hurt");
			} finally {
				context.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING).set(grief, context.getWorld().getServer());
			}
		});
		// (The pieces that flew out of it are gone a moment later.)
		context.runAtTick(Beam.POP_TICKS + 30 + Beam.DEBRIS_LASTS + 20, () -> {
			try {
				context.assertTrue(context.getWorld().getEntitiesByType(net.minecraft.entity.EntityType.FALLING_BLOCK, new net.minecraft.util.math.Box(wall).expand(24.0), e -> true).isEmpty(),
						"the flying debris did not go away");
			} finally {
				done(context, player);
				wallOf(context, wall, Blocks.AIR);
				ground(context, false, 40);
			}
			context.complete();
		});
	}

	/** With {@code mobGriefing} off he leaves a wall standing and swims under it (and out the other side). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_wall_kept", tickLimit = 70)
	public void beamSwimsUnderAWallWhenNothingMayBeBroken(TestContext context) {
		ServerPlayerEntity player = fighter(context, "kept", 0.0);
		ground(context, true, 40);
		Blood.set(player, Blood.MAX);
		BlockPos wall = BlockPos.ofFloored(spot(context)).add(0, 0, 10);
		wallOf(context, wall, Blocks.STONE);
		boolean grief = context.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
		context.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING).set(false, context.getWorld().getServer());
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		double surface = spot(context).y;
		context.runAtTick(Beam.POP_TICKS + 1, () -> sprintAhead(player));
		context.runAtTick(Beam.POP_TICKS + 30, () -> {
			try {
				context.assertTrue(beam.getZ() > wall.getZ() + 4.0, "he did not swim under to the other side: " + (beam.getZ() - wall.getZ()));
				context.assertTrue(beam.getY() < surface - 1.0, "he did not stay under the ground: " + (beam.getY() - surface));
				for (int x = -3; x <= 3; x++) {
					for (int y = 0; y < 4; y++) {
						context.assertTrue(context.getWorld().getBlockState(wall.add(x, y, 0)).isOf(Blocks.STONE), "the wall was broken at " + x + ", " + y + " with mobGriefing off");
					}
				}
				context.assertTrue(player.getVehicle() == beam, "the rider was thrown");
			} finally {
				done(context, player);
				context.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING).set(grief, context.getWorld().getServer());
				wallOf(context, wall, Blocks.AIR);
				ground(context, false, 40);
			}
			context.complete();
		});
	}

	/** Even with {@code mobGriefing} on he does not break what is too hard (obsidian): he swims under it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_obsidian", tickLimit = 70)
	public void beamSwimsUnderObsidian(TestContext context) {
		ServerPlayerEntity player = fighter(context, "hard", 0.0);
		ground(context, true, 40);
		Blood.set(player, Blood.MAX);
		BlockPos wall = BlockPos.ofFloored(spot(context)).add(0, 0, 10);
		wallOf(context, wall, Blocks.OBSIDIAN);
		boolean grief = context.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
		context.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING).set(true, context.getWorld().getServer());
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		context.runAtTick(Beam.POP_TICKS + 1, () -> sprintAhead(player));
		context.runAtTick(Beam.POP_TICKS + 30, () -> {
			try {
				context.assertTrue(beam.getZ() > wall.getZ() + 4.0, "he did not swim under to the other side: " + (beam.getZ() - wall.getZ()));
				for (int x = -3; x <= 3; x++) {
					for (int y = 0; y < 4; y++) {
						context.assertTrue(context.getWorld().getBlockState(wall.add(x, y, 0)).isOf(Blocks.OBSIDIAN), "the obsidian was broken at " + x + ", " + y);
					}
				}
			} finally {
				done(context, player);
				context.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING).set(grief, context.getWorld().getServer());
				wallOf(context, wall, Blocks.AIR);
				ground(context, false, 40);
			}
			context.complete();
		});
	}

	/** The rider sprinting forward (the sprint key held with forward; the game tells the server both): Beam dives and swims. */
	private static void sprintAhead(ServerPlayerEntity player) {
		player.setSprinting(true);
		player.updateInput(0.0F, 1.0F, false, false);
	}

	/** The rider walking forward, not sprinting: Beam walks on his legs. */
	private static void walkAhead(ServerPlayerEntity player) {
		player.setSprinting(false);
		player.updateInput(0.0F, 1.0F, false, false);
	}

	/** A wall of {@code block}, 4 high and 7 wide (3 each side) with its middle foot at {@code at}. */
	private static void wallOf(TestContext context, BlockPos at, net.minecraft.block.Block block) {
		for (int x = -3; x <= 3; x++) {
			for (int y = 0; y < 4; y++) {
				context.getWorld().setBlockState(at.add(x, y, 0), block.getDefaultState());
			}
		}
	}

	/** The ground rises and falls under him: a step up of a block, then a drop, and he follows it smoothly (never more than a block in a tick). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_hill", tickLimit = 70)
	public void beamFollowsTheGround(TestContext context) {
		ServerPlayerEntity player = fighter(context, "climber", 0.0);
		ground(context, true, 40);
		Blood.set(player, Blood.MAX);
		// A raised block of floor from 8 to 18 ahead, a block and a half... a block and a bit up (a stone slab on stone).
		BlockPos hill = BlockPos.ofFloored(spot(context)).add(0, 0, 8);
		for (int x = -3; x <= 3; x++) {
			for (int z = 0; z < 10; z++) {
				context.getWorld().setBlockState(hill.add(x, 0, z), Blocks.STONE.getDefaultState());
				context.getWorld().setBlockState(hill.add(x, 1, z), Blocks.STONE_SLAB.getDefaultState());
			}
		}
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		double surface = spot(context).y;
		double[] last = {Double.NaN};
		double[] biggest = {0.0};
		double[] over = {Double.NaN};
		context.runAtTick(Beam.POP_TICKS + 1, () -> sprintAhead(player));
		for (int t = Beam.POP_TICKS + 4; t < Beam.POP_TICKS + 40; t++) {
			int tick = t;
			context.runAtTick(t, () -> {
				if (!Double.isNaN(last[0])) {
					biggest[0] = Math.max(biggest[0], Math.abs(beam.getY() - last[0]));
				}
				last[0] = beam.getY();
				if (beam.getZ() > hill.getZ() + 5.0 && beam.getZ() < hill.getZ() + 8.0 && Double.isNaN(over[0])) {
					over[0] = beam.getY();
				}
			});
		}
		context.runAtTick(Beam.POP_TICKS + 40, () -> {
			try {
				context.assertTrue(!Double.isNaN(over[0]) && over[0] > surface + 1.0 - Beam.SINK - 0.3, "he did not rise with the ground: " + (over[0] - surface));
				context.assertTrue(biggest[0] < 1.0, "he climbed in a step: " + biggest[0] + " blocks in a tick");
				context.assertTrue(context.getWorld().getBlockState(hill.add(0, 1, 5)).isOf(Blocks.STONE_SLAB), "he broke the hill he was to climb");
			} finally {
				done(context, player);
				for (int x = -3; x <= 3; x++) {
					for (int z = 0; z < 10; z++) {
						context.getWorld().setBlockState(hill.add(x, 0, z), Blocks.AIR.getDefaultState());
						context.getWorld().setBlockState(hill.add(x, 1, z), Blocks.AIR.getDefaultState());
					}
				}
				ground(context, false, 40);
			}
			context.complete();
		});
	}

	/**
	 * The attack key is his bite while you ride (the slash action becomes {@code Beam.bite}): what is in front of his mouth
	 * takes {@link Beam#BITE_DAMAGE} (not a slash's), is flung up and forward (an iron golem here: a mob too big to be held, which is
	 * only bitten), and what is behind him is left alone; a second bite straight after is refused.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_bite", tickLimit = 40)
	public void slashBecomesABiteOnBeam(TestContext context) {
		ServerPlayerEntity player = fighter(context, "biter", 0.0);
		ground(context, true);
		Blood.set(player, Blood.MAX);
		IronGolemEntity front = golem(context, 0.0, 2.6);
		HuskEntity behind = husk(context, 0.0, -2.5);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		// (The husks here stand without a mind and their motion is not run: what they were thrown at is what they keep, as velocity.)
		double[] flung = new double[2];
		context.runAtTick(Beam.POP_TICKS + 2, () -> {
			context.assertTrue(Saw.slash(player), "the attack key did nothing from his back");
			context.assertTrue(beam.sinceBite(0.0F) < 2.0F, "his jaw did not snap");
			context.assertTrue(!Saw.slash(player), "a second bite came at once");
		});
		context.runAtTick(Beam.POP_TICKS + 3, () -> {
			flung[0] = front.getVelocity().z;
			flung[1] = front.getVelocity().y;
		});
		context.runAtTick(Beam.POP_TICKS + 6, () -> {
			try {
				float lost = front.getMaxHealth() - front.getHealth();
				context.assertTrue(lost >= Beam.BITE_DAMAGE - 0.5F && lost <= Beam.BITE_DAMAGE + 0.5F, "the bite did " + lost + ", not " + Beam.BITE_DAMAGE);
				context.assertTrue(behind.getHealth() == behind.getMaxHealth(), "the husk behind was bitten");
				context.assertTrue(flung[0] > Beam.FLING_AHEAD * 0.7, "the golem was not flung forward: " + flung[0]);
				context.assertTrue(flung[1] > Beam.FLING_UP * 0.7, "the golem was not flung up: " + flung[1]);
				context.assertTrue(front.getVehicle() == null && !beam.ride().holding(), "a golem too big to be held was grabbed");
			} finally {
				done(context, player);
				front.discard();
				behind.discard();
				ground(context, false);
			}
			context.complete();
		});
	}

	/** A kill from his back pleases him: the clients are told (he chomps and wiggles). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_pleased", tickLimit = 40)
	public void beamIsPleasedByAKill(TestContext context) {
		ServerPlayerEntity player = fighter(context, "killer", 0.0);
		ground(context, true);
		Blood.set(player, Blood.MAX);
		HuskEntity front = husk(context, 0.0, 2.2);
		front.setHealth(6.0F);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		context.runAtTick(Beam.POP_TICKS + 2, () -> {
			context.assertTrue(beam.sinceHappy(0.0F) > 1000.0F, "he was pleased before he killed anything");
			context.assertTrue(Saw.slash(player), "the attack key did nothing from his back");
		});
		context.runAtTick(Beam.POP_TICKS + 6, () -> {
			try {
				context.assertTrue(!front.isAlive(), "the bite did not kill the husk");
				context.assertTrue(beam.sinceHappy(0.0F) < 6.0F, "he is not pleased with his kill: " + beam.sinceHappy(0.0F));
			} finally {
				done(context, player);
				front.discard();
				ground(context, false);
			}
			context.complete();
		});
	}

	/** The use key is his charge (the lunge action becomes {@code Beam.charge}): he dashes ahead, biting what is in the way once and flinging it aside. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_charge", tickLimit = 50)
	public void lungeBecomesAChargeOnBeam(TestContext context) {
		ServerPlayerEntity player = fighter(context, "charger", 0.0);
		ground(context, true);
		Blood.set(player, Blood.MAX);
		HuskEntity inWay = husk(context, 0.0, 5.0);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		double[] from = new double[1];
		double[] aside = new double[1];
		for (int t = Beam.POP_TICKS + 2; t < Beam.POP_TICKS + 2 + Beam.CHARGE_TICKS + 4; t++) {
			context.runAtTick(t, () -> aside[0] = Math.max(aside[0], Math.abs(inWay.getVelocity().x)));
		}
		context.runAtTick(Beam.POP_TICKS + 2, () -> {
			from[0] = beam.getZ();
			context.assertTrue(Saw.lunge(player), "the use key did nothing from his back");
			context.assertTrue(!Saw.lunge(player), "a second charge came at once");
		});
		context.runAtTick(Beam.POP_TICKS + 2 + Beam.CHARGE_TICKS + 4, () -> {
			try {
				context.assertTrue(beam.getZ() - from[0] > Beam.CHARGE_BLOCKS * 0.8, "he did not dash ahead: " + (beam.getZ() - from[0]));
				context.assertTrue(player.getVehicle() == beam, "the rider was thrown");
				float lost = inWay.getMaxHealth() - inWay.getHealth();
				context.assertTrue(lost >= Beam.CHARGE_DAMAGE - 0.5F && lost <= Beam.CHARGE_DAMAGE + 0.5F, "what was in his way took " + lost + ", not " + Beam.CHARGE_DAMAGE + " once");
				context.assertTrue(aside[0] > 0.7, "what was in his way was not flung aside: " + aside[0]);
			} finally {
				done(context, player);
				inWay.discard();
				ground(context, false);
			}
			context.complete();
		});
	}

	/** The jump key makes him breach: out of the ground, up, over, and back in on what is where he lands, for the crash's bite. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_leap", tickLimit = 70)
	public void beamLeaps(TestContext context) {
		ServerPlayerEntity player = fighter(context, "leaper", 0.0);
		ground(context, true, 40);
		Blood.set(player, Blood.MAX);
		// Where he lands (nothing is by his mouth to begin with, so he bites nothing on the way).
		HuskEntity landing = husk(context, 0.0, Beam.LEAP_LONG);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		double ground = spot(context).y;
		int go = Beam.POP_TICKS + 1;
		context.runAtTick(go, () -> player.updateInput(0.0F, 0.0F, true, false));
		context.runAtTick(go + 2, () -> player.updateInput(0.0F, 0.0F, false, false));
		double[] top = {0.0};
		for (int t = go; t < go + Beam.LEAP_TICKS + 4; t++) {
			context.runAtTick(t, () -> top[0] = Math.max(top[0], beam.getY() - ground));
		}
		context.runAtTick(go + Beam.LEAP_TICKS + 12, () -> {
			try {
				context.assertTrue(top[0] > Beam.LEAP_UP * 0.8, "he did not leap: " + top[0]);
				context.assertTrue(beam.getZ() - spot(context).z > Beam.LEAP_LONG * 0.8, "he did not land far ahead: " + (beam.getZ() - spot(context).z));
				context.assertTrue(landing.getHealth() <= landing.getMaxHealth() - Beam.LEAP_DAMAGE + 0.5F, "the crash did not bite what was under it: " + landing.getHealth());
				context.assertTrue(player.getVehicle() == beam, "the rider was thrown");
				context.assertTrue(beam.sinceLand(0.0F) < Beam.LEAP_TICKS + 12, "the clients are not told he crashed in");
			} finally {
				done(context, player);
				landing.discard();
				ground(context, false, 40);
			}
			context.complete();
		});
	}

	/** Swimming fast, the jump key breaches him too: he comes up out of the ground (above it) and goes back in. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_breach", tickLimit = 90)
	public void beamBreachesWhileSwimming(TestContext context) {
		ServerPlayerEntity player = fighter(context, "breacher", 0.0);
		ground(context, true, 60);
		Blood.set(player, Blood.MAX);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		double ground = spot(context).y;
		int go = Beam.POP_TICKS + 1;
		context.runAtTick(go, () -> sprintAhead(player));
		context.runAtTick(go + 16, () -> {
			context.assertTrue(beam.getY() < ground - 1.5, "he was not swimming before the breach: " + (beam.getY() - ground));
			player.updateInput(0.0F, 1.0F, true, false);
		});
		context.runAtTick(go + 18, () -> sprintAhead(player));
		double[] top = {-100.0};
		for (int t = go + 17; t < go + 17 + Beam.LEAP_TICKS + 2; t++) {
			context.runAtTick(t, () -> top[0] = Math.max(top[0], beam.getY() - ground));
		}
		context.runAtTick(go + 17 + Beam.LEAP_TICKS + 4, () -> {
			try {
				context.assertTrue(top[0] > -0.5, "he did not come out of the ground: " + top[0]);
				context.assertTrue(beam.getY() < ground - 2.0, "he did not go back in: " + (beam.getY() - ground) + " at tick " + (go + 17 + Beam.LEAP_TICKS + 4) + " leap ticks " + Beam.LEAP_TICKS);
				context.assertTrue(player.getVehicle() == beam, "the rider was thrown");
			} finally {
				done(context, player);
				ground(context, false, 60);
			}
			context.complete();
		});
	}


	/** A big mob (an iron golem and up) is only bitten: no grab. (The slash test above checks the damage and the fling.) */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_big", tickLimit = 40)
	public void bigMobsAreJustBitten(TestContext context) {
		ServerPlayerEntity player = fighter(context, "bigbiter", 0.0);
		ground(context, true);
		Blood.set(player, Blood.MAX);
		IronGolemEntity golem = golem(context, 0.0, 2.6);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		context.runAtTick(Beam.POP_TICKS + 2, () -> context.assertTrue(Saw.slash(player), "the bite did not come"));
		context.runAtTick(Beam.POP_TICKS + 12, () -> {
			try {
				context.assertTrue(!beam.ride().holding() && golem.getVehicle() == null, "he grabbed a mob too big for his jaws");
				context.assertTrue(golem.getHealth() < golem.getMaxHealth() - Beam.BITE_DAMAGE + 0.5F, "the golem was not bitten: " + golem.getHealth());
				context.assertTrue(beam.sinceGrab(0.0F) > 1000.0F, "the clients were told of a grab");
			} finally {
				done(context, player);
				golem.discard();
				ground(context, false);
			}
			context.complete();
		});
	}

	/** Where his jaws hold what they have: ahead of his middle and above his feet (blocks), as the model's head is. */
	private static Vec3d jaws(BeamEntity beam) {
		return beam.getPos().add(beam.heading().multiply(1.94)).add(0.0, 1.5, 0.0);
	}

	/**
	 * The bite on a mob no bigger than a Ravager catches it: it rides at his mouth (a passenger), and for a second it is shaken, hurt
	 * at the end of each swing, while his riding keys go on working; then it is flung far the way you look. (A husk with plenty of health.)
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_grab", tickLimit = 90)
	public void beamGrabsThrashesAndFlings(TestContext context) {
		ServerPlayerEntity player = fighter(context, "grabber", 0.0);
		ground(context, true, 40);
		Blood.set(player, Blood.MAX);
		HuskEntity prey = husk(context, 0.0, 2.6);
		prey.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200.0);
		prey.setHealth(200.0F);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		int go = Beam.POP_TICKS + 2;
		double[] from = new double[1];
		float[] caught = new float[1];
		context.runAtTick(go, () -> {
			from[0] = beam.getZ();
			context.assertTrue(Saw.slash(player), "the bite did not come");
			context.assertTrue(prey.getVehicle() == beam && beam.ride().holding(), "his jaws did not catch it");
			caught[0] = prey.getHealth();
			context.assertTrue(!Saw.slash(player), "a second bite came while he held it");
		});
		// His riding keys go on working: the forward key sends him on while he thrashes.
		context.runAtTick(go + 3, () -> sprintAhead(player));
		context.runAtTick(go + BeamEntity.GRAB_TICKS - 2, () -> {
			context.assertTrue(prey.getVehicle() == beam, "it was let go before the thrash was over");
			context.assertTrue(beam.getZ() - from[0] > 6.0, "he did not go on swimming while he thrashed: " + (beam.getZ() - from[0]));
			context.assertTrue(prey.getPos().distanceTo(jaws(beam)) < 3.5, "what he held was not at his mouth: " + prey.getPos().distanceTo(jaws(beam)));
			context.assertTrue(caught[0] - prey.getHealth() > Beam.SHAKE_DAMAGE * 3 - 0.5F, "the shakes did not hurt it: " + (caught[0] - prey.getHealth()));
		});
		context.runAtTick(go + BeamEntity.GRAB_TICKS + 3, () -> {
			try {
				context.assertTrue(prey.getVehicle() == null && !beam.ride().holding(), "it was not let go");
				float lost = 200.0F - prey.getHealth();
				context.assertTrue(lost > Beam.BITE_DAMAGE + Beam.SHAKE_DAMAGE * 5 + Beam.FLING_DAMAGE - 1.0F, "the grab hurt it only " + lost);
				context.assertTrue(prey.getVelocity().z > Beam.FLING_SPEED * 0.6, "it was not flung the way the rider looks: " + prey.getVelocity());
				context.assertTrue(beam.ride().hardest() >= 3, "the rider was not told of the fling: " + beam.ride().hardest());
				context.assertTrue(beam.ride().hits() >= 7, "the rider felt only " + beam.ride().hits() + " hits");
				context.assertTrue(beam.sinceGrab(0.0F) < 40.0F, "the clients were not told of the grab");
			} finally {
				done(context, player);
				prey.discard();
				ground(context, false, 40);
			}
			context.complete();
		});
	}

	/**
	 * The ambush: swimming, the attack key with a mob ahead too far to bite sends him under it; he erupts straight up beneath it, and it is
	 * thrown far higher than a bite's fling and hurt, a ring of the ground bursts up round the hole as debris, the rider is told it is the
	 * hardest hit, he comes down and swims on.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_ambush", tickLimit = 100)
	public void beamBurstsUpUnderAMob(TestContext context) {
		ServerPlayerEntity player = fighter(context, "ambusher", 0.0);
		ground(context, true, 60);
		Blood.set(player, Blood.MAX);
		HuskEntity prey = husk(context, 0.0, 34.0);
		prey.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200.0);
		prey.setHealth(200.0F);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		double ground = spot(context).y;
		int go = Beam.POP_TICKS + 1;
		context.runAtTick(go, () -> sprintAhead(player));
		int[] fired = {-1};
		double[] launched = new double[1];
		double[] top = {-100.0};
		int[] pieces = new int[1];
		for (int t = go + 8; t < go + 50; t++) {
			int tick = t;
			context.runAtTick(t, () -> {
				double gap = prey.getZ() - (beam.getZ() + 2.2);
				if (fired[0] < 0 && gap < 7.5 && gap > 5.0) {
					context.assertTrue(Saw.slash(player), "the attack key did nothing with a mob ahead");
					fired[0] = tick;
					context.assertTrue(beam.ride().ambush() == 0, "he did not begin the rush: " + beam.ride().ambush());
				}
				if (fired[0] >= 0) {
					top[0] = Math.max(top[0], beam.getY() - ground);
					launched[0] = Math.max(launched[0], prey.getVelocity().y);
					if (beam.ride().ambush() == 1 && pieces[0] == 0) {
						pieces[0] = context.getWorld().getEntitiesByClass(FallingBlockEntity.class, new net.minecraft.util.math.Box(prey.getBlockPos()).expand(8.0), b -> true).size();
					}
				}
			});
		}
		context.runAtTick(go + 52, () -> {
			try {
				context.assertTrue(fired[0] >= 0, "no ambush began");
				context.assertTrue(launched[0] > 1.6, "the mob was not thrown sky-high: " + launched[0]);
				context.assertTrue(prey.getMaxHealth() - prey.getHealth() > Beam.AMBUSH_DAMAGE - 1.0F, "it was not hurt: " + prey.getHealth());
				context.assertTrue(top[0] > -1.0, "he did not come up out of the ground: " + top[0]);
				context.assertTrue(pieces[0] >= 6, "the ground did not burst up round the hole: " + pieces[0]);
				context.assertTrue(beam.ride().hardest() == 4, "the rider was not told it was the hardest hit: " + beam.ride().hardest());
				context.assertTrue(beam.sinceAmbush(0.0F) < 100.0F, "the clients were not told");
				context.assertTrue(player.getVehicle() == beam, "the rider was thrown");
				context.assertTrue(beam.getY() < ground - 0.3, "he did not dive back in: " + (beam.getY() - ground));
				context.assertTrue(beam.ride().ambush() == -1, "the ambush did not end");
			} finally {
				done(context, player);
				prey.discard();
				ground(context, false, 60);
			}
			context.complete();
		});
	}

	/** Cooldown: a second ambush straight after the first is not begun (the key is a plain bite then). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_ambush_cool", tickLimit = 140)
	public void ambushesComeNoFasterThanTheirCooldown(TestContext context) {
		ServerPlayerEntity player = fighter(context, "twice", 0.0);
		ground(context, true, 60);
		Blood.set(player, Blood.MAX);
		HuskEntity first = husk(context, 0.0, 30.0);
		HuskEntity second = husk(context, 0.0, 33.0);
		for (HuskEntity h : new HuskEntity[] {first, second}) {
			h.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200.0);
			h.setHealth(200.0F);
		}
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		int go = Beam.POP_TICKS + 1;
		context.runAtTick(go, () -> sprintAhead(player));
		int[] fired = {-1, -1};
		for (int t = go + 8; t < go + 60; t++) {
			int tick = t;
			context.runAtTick(t, () -> {
				double gap = first.getZ() - (beam.getZ() + 2.2);
				if (fired[0] < 0 && gap < 7.5 && gap > 5.0) {
					Saw.slash(player);
					fired[0] = tick;
				}
				if (fired[0] >= 0 && fired[1] < 0 && tick >= fired[0] + 14 && beam.ride().ambush() == -1) {
					// Straight after the eruption, with the other mob ahead and too far to bite: the key must not start another.
					double gap2 = second.getZ() - (beam.getZ() + 2.2);
					if (gap2 > 4.5 && gap2 < 8.0) {
						Saw.slash(player);
						fired[1] = tick;
						context.assertTrue(beam.ride().ambush() == -1, "a second ambush began inside the cooldown (" + (tick - fired[0]) + " ticks after the first)");
					}
				}
			});
		}
		context.runAtTick(go + 70, () -> {
			try {
				context.assertTrue(fired[0] >= 0, "no ambush began");
				context.assertTrue(fired[1] < 0 || fired[1] - fired[0] < Beam.AMBUSH_EVERY, "the cooldown test did not run");
			} finally {
				done(context, player);
				first.discard();
				second.discard();
				ground(context, false, 60);
			}
			context.complete();
		});
	}

	/** A mob close enough to bite is bitten (and held), not ambushed. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_ambush_close", tickLimit = 90)
	public void aCloseMobIsBittenNotAmbushed(TestContext context) {
		ServerPlayerEntity player = fighter(context, "closer", 0.0);
		ground(context, true, 40);
		Blood.set(player, Blood.MAX);
		HuskEntity prey = husk(context, 0.0, 14.0);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		int go = Beam.POP_TICKS + 1;
		context.runAtTick(go, () -> sprintAhead(player));
		boolean[] bit = new boolean[1];
		for (int t = go + 4; t < go + 24; t++) {
			context.runAtTick(t, () -> {
				double gap = prey.getZ() - (beam.getZ() + 2.2);
				if (!bit[0] && gap < 2.5 && gap > 0.0) {
					bit[0] = Saw.slash(player);
				}
			});
		}
		context.runAtTick(go + 26, () -> {
			try {
				context.assertTrue(bit[0], "no bite came");
				context.assertTrue(beam.sinceAmbush(0.0F) > 1000.0F, "he ambushed a mob he could bite");
				context.assertTrue(prey.getHealth() <= prey.getMaxHealth() - Beam.BITE_DAMAGE + 0.5F, "it was not bitten");
			} finally {
				done(context, player);
				prey.discard();
				ground(context, false, 40);
			}
			context.complete();
		});
	}

	/**
	 * The chain key while you ride throws you like a missile the way you look: off his back, fast and flat, saws out; what is on the way is cut
	 * (a wide cut with the real saw damage); you land unhurt, Beam swims to where you came down and you are on his back again; the next throw is
	 * refused for a moment, and out of the form he simply dives.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_launch", tickLimit = 160)
	public void beamThrowsHisRiderAndFetchesThem(TestContext context) {
		ServerPlayerEntity player = fighter(context, "missile", 0.0);
		ground(context, true, 64);
		Blood.set(player, Blood.MAX);
		HuskEntity inWay = husk(context, 0.0, 32.0);
		inWay.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200.0);
		inWay.setHealth(200.0F);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		// (Swimming, so the rider is low on his back, near the ground the husk stands on.)
		context.runAtTick(Beam.POP_TICKS + 1, () -> sprintAhead(player));
		int go = Beam.POP_TICKS + 14;
		float[] before = new float[1];
		context.runAtTick(go, () -> {
			before[0] = player.getHealth();
			context.assertTrue(Beam.launch(player), "the chain key did not throw the rider");
			context.assertTrue(!player.hasVehicle() && beam.ride().loose(), "the rider is still on his back");
			context.assertTrue(player.getVelocity().z > Beam.LAUNCH_SPEED * 0.9, "they were not thrown the way they look: " + player.getVelocity());
			context.assertTrue(!Beam.launch(player), "a second throw came at once");
			context.assertTrue(beam.sinceLaunch(0.0F) < 3.0F, "the clients were not told of the throw");
		});
		context.runAtTick(go + 1, () -> {
			// Where they come down: well ahead of him, as the throw takes them (the test player is not moved by a client).
			Vec3d landed = player.getPos().add(0.0, 0.0, 18.0);
			player.teleport(context.getWorld(), landed.x, landed.y, landed.z, 0.0F, 0.0F);
			player.onTeleportationDone();
		});
		context.runAtTick(go + Beam.LAUNCH_TICKS + 1, () -> {
			context.assertTrue(beam.ride().flying() == -1, "the flight did not end");
			float lost = inWay.getMaxHealth() - inWay.getHealth();
			context.assertTrue(lost > Saw.FINISHER_DAMAGE - 1.0F, "the missile cut only " + lost);
			context.assertTrue(beam.ride().hardest() >= 3, "the slash was not felt as a hard hit: " + beam.ride().hardest());
			context.assertTrue(player.getHealth() >= before[0], "the rider was hurt");
			context.assertTrue(beam.ride().loose() && beam.isAlive(), "Beam was not swimming to them");
		});
		// (Back on him, the forward key up: the floor here is short.)
		context.runAtTick(go + Beam.LAUNCH_TICKS + 16, () -> player.updateInput(0.0F, 0.0F, false, false));
		context.runAtTick(go + Beam.LAUNCH_TICKS + 40, () -> {
			context.assertTrue(player.getVehicle() == beam && !beam.ride().loose(), "Beam did not take the rider back: " + beam.ride().loose() + " at " + beam.getPos() + " for " + player.getPos());
			context.assertTrue(player.getY() > spot(context).y + 0.3, "the rider is not on his back: " + (player.getY() - spot(context).y) + ", he is at " + (beam.getY() - spot(context).y) + " swim " + beam.swimming());
			context.assertTrue(!Beam.launch(player), "a throw came inside the cooldown");
		});
		context.runAtTick(go + Beam.LAUNCH_EVERY + 8, () -> {
			context.assertTrue(player.getVehicle() == beam && Beam.launch(player), "no second throw after the cooldown");
			// Out of the form, with him fetching: he dives.
			Form.end(player);
		});
		context.runAtTick(go + Beam.LAUNCH_EVERY + 12, () -> {
			try {
				context.assertTrue(beam.diving(), "he did not dive when the rider left the form");
			} finally {
				done(context, player);
				inWay.discard();
				ground(context, false, 64);
			}
			context.complete();
		});
	}

	/** Sneaking gets you off: he dives, is gone, and you are left standing. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_sneak", tickLimit = Beam.POP_TICKS + Beam.DIVE_TICKS + 40)
	public void beamDivesWhenYouSneak(TestContext context) {
		ServerPlayerEntity player = fighter(context, "dismounter", 0.0);
		ground(context, true);
		Blood.set(player, Blood.MAX);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		int go = Beam.POP_TICKS + 3;
		context.runAtTick(go, () -> player.updateInput(0.0F, 0.0F, false, true));
		context.runAtTick(go + 2, () -> context.assertTrue(!player.hasVehicle() && beam.diving(), "he did not dive when the rider sneaked"));
		context.runAtTick(go + 2 + Beam.DIVE_TICKS + 4, () -> {
			try {
				context.assertTrue(!beam.isAlive(), "he was not gone after diving");
				context.assertTrue(Beam.active(player) == null, "he was still the player's");
				context.assertTrue(player.getY() > spot(context).y - 0.2, "the rider was left under the ground: " + (player.getY() - spot(context).y));
			} finally {
				done(context, player);
				ground(context, false);
			}
			context.complete();
		});
	}

	/** Leaving the form ends it too: he dives and you are left off. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_leave", tickLimit = 30)
	public void beamDivesWhenYouLeaveTheForm(TestContext context) {
		ServerPlayerEntity player = fighter(context, "leaver", 0.0);
		ground(context, true);
		Blood.set(player, Blood.MAX);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		context.runAtTick(4, () -> Form.end(player));
		context.runAtTick(8, () -> {
			try {
				context.assertTrue(!player.hasVehicle() && beam.diving(), "he stayed with a rider who left the form");
			} finally {
				done(context, player);
				ground(context, false);
			}
			context.complete();
		});
	}

	/** After {@link Beam#LASTS} he is gone, and the player is off. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_beam_time", tickLimit = Beam.LASTS + Beam.DIVE_TICKS + 40)
	public void beamLeavesAfterTwentySeconds(TestContext context) {
		ServerPlayerEntity player = fighter(context, "timed", 0.0);
		ground(context, true);
		Blood.set(player, Blood.MAX);
		Beam.summon(player);
		BeamEntity beam = (BeamEntity) player.getVehicle();
		context.runAtTick(Beam.LASTS - 10, () -> context.assertTrue(player.getVehicle() == beam && !beam.diving(), "he left early"));
		context.runAtTick(Beam.LASTS + 5, () -> context.assertTrue(!player.hasVehicle() && beam.diving(), "he did not dive when his time was up"));
		context.runAtTick(Beam.LASTS + Beam.DIVE_TICKS + 8, () -> {
			try {
				context.assertTrue(!beam.isAlive(), "he was not gone after the dive");
				context.assertTrue(!player.hasVehicle(), "the player was still on him");
			} finally {
				done(context, player);
				ground(context, false);
			}
			context.complete();
		});
	}

	/**
	 * A small floor of stone under where these tests stand ({@code on}), or taken away again: Beam swims along the ground, and
	 * the tests' spot is in the air.
	 */
	private static void ground(TestContext context, boolean on) {
		ground(context, on, 9);
	}

	/** The same floor, {@code length} blocks long ahead (z) of the spot. */
	private static void ground(TestContext context, boolean on, int length) {
		BlockPos at = BlockPos.ofFloored(spot(context)).down();
		// (Kept loaded: he stops at the edge of what is.)
		for (int cx = (at.getX() - 3) >> 4; cx <= (at.getX() + 3) >> 4; cx++) {
			for (int cz = (at.getZ() - 3) >> 4; cz <= (at.getZ() + length + 8) >> 4; cz++) {
				context.getWorld().setChunkForced(cx, cz, on);
			}
		}
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= length; z++) {
				context.getWorld().setBlockState(at.add(x, 0, z), on ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
			}
		}
	}

	/** A player in the form, with Pochita's heart, at {@code x} across the test's spot, facing along +z. */
	private static ServerPlayerEntity fighter(TestContext context, String name, double x) {
		ServerPlayerEntity player = player(context, name, x);
		player.setAttached(Heart.MERGED, true);
		Form.start(player);
		return player;
	}

	private static ServerPlayerEntity player(TestContext context, String name, double x) {
		ServerWorld world = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		Vec3d at = spot(context).add(x, 0.0, 0.0);
		player.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		// (A real client would confirm the teleport; until then the game keeps a player from harm.)
		player.onTeleportationDone();
		player.setNoGravity(true);
		return player;
	}

	/** An iron golem standing still (no mind, no gravity), {@code z} blocks ahead of the test's spot. */
	private static IronGolemEntity golem(TestContext context, double x, double z) {
		Vec3d at = spot(context).add(x, 0.0, z);
		IronGolemEntity golem = new IronGolemEntity(EntityType.IRON_GOLEM, context.getWorld());
		golem.refreshPositionAndAngles(at.x, at.y, at.z, 180.0F, 0.0F);
		golem.setAiDisabled(true);
		golem.setNoGravity(true);
		context.getWorld().spawnEntity(golem);
		return golem;
	}

	/** A husk standing still (no mind, no gravity), {@code z} blocks ahead of the test's spot. */
	private static HuskEntity husk(TestContext context, double x, double z) {
		Vec3d at = spot(context).add(x, 0.0, z);
		HuskEntity husk = new HuskEntity(EntityType.HUSK, context.getWorld());
		husk.refreshPositionAndAngles(at.x, at.y, at.z, 180.0F, 0.0F);
		husk.setAiDisabled(true);
		husk.setNoGravity(true);
		context.getWorld().spawnEntity(husk);
		return husk;
	}

	/**
	 * Where these tests stand: high over their test box, in the air (everything here floats), clear of every other
	 * test's ground and what stands on it (a Domain's cow, say), which the saws and the chain would otherwise reach.
	 */
	private static Vec3d spot(TestContext context) {
		return context.getAbsolute(new Vec3d(0.5, 40.0, 0.5));
	}

	/** Out of the form, up, gone. */
	private static void done(TestContext context, ServerPlayerEntity player) {
		Beam.end(player);
		Form.end(player);
		Downed.up(player);
		Frenzy.end(player);
		Frenzy.ready(player);
		Grapple.ready(player);
		context.getWorld().getServer().getPlayerManager().remove(player);
	}
}
