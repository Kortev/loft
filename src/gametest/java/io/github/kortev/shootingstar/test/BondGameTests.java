package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.contract.Bond;
import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.contract.Forever;
import io.github.kortev.chainsawman.contract.ForeverCubeEntity;
import io.github.kortev.chainsawman.contract.ForeverHubEntity;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Contract bonds (three steps that change what Forever looks like and does) and "Not yet": only a use that locked something counts, the
 * steps come at 15 and 40 uses, each contract has its own, they are saved; bond 2 is a 13-block cube held 4 s with longer lines; bond 3
 * has a bigger release and opens "Not yet" (untouchable 1.5 s, sharing Forever's cooldown and price).
 */
public class BondGameTests implements FabricGameTest {
	/** The steps come at 15 and 40 counted uses, a use that locked nothing does not count, and one that locked something does. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_bond_count", tickLimit = 70)
	public void onlyUsesThatLockedSomethingCount(TestContext context) {
		floor(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		ServerPlayerEntity player = holder(context, "counter", 0.0, husk);
		try {
			context.assertTrue(Bond.step(0) == 1 && Bond.step(14) == 1 && Bond.step(15) == 2 && Bond.step(39) == 2 && Bond.step(40) == 3 && Bond.step(500) == 3, "the steps are not at 15 and 40");
			context.assertTrue(Bond.uses(player, Contract.ETERNITY) == 0 && Bond.step(player, Contract.ETERNITY) == 1, "a new contract has a bond already");
			// Looking at the sky: nothing to lock, nothing counted.
			player.setPitch(-85.0F);
			Forever.use(player);
			context.assertTrue(!Forever.active(player) && Bond.uses(player, Contract.ETERNITY) == 0, "a use with nothing to hold counted");
			// A real one counts when the moment ends.
			holder(context, player, husk);
			context.runAtTick(3, () -> {
				Forever.use(player);
				context.assertTrue(Forever.locked(husk), "the husk was not locked");
				context.assertTrue(Bond.uses(player, Contract.ETERNITY) == 0, "the use counted before it was over");
				Forever.endsIn(husk, 2);
			});
			context.runAtTick(30, () -> {
				context.assertTrue(Bond.uses(player, Contract.ETERNITY) == 1, "the use that locked something did not count: " + Bond.uses(player, Contract.ETERNITY));
				Bond.set(player, Contract.ETERNITY, Bond.TWO - 1);
				context.assertTrue(Bond.record(player, Contract.ETERNITY) == 2 && Bond.step(player, Contract.ETERNITY) == 2, "the 15th use did not deepen the bond");
				context.assertTrue(Bond.record(player, Contract.ETERNITY) == 0, "a use inside a step announced a bond-up");
				Bond.set(player, Contract.ETERNITY, Bond.THREE - 1);
				context.assertTrue(Bond.record(player, Contract.ETERNITY) == 3 && Bond.step(player, Contract.ETERNITY) == 3, "the 40th use did not deepen the bond");
				context.assertTrue(Bond.record(player, Contract.ETERNITY) == 0 && Bond.step(player, Contract.ETERNITY) == 3, "there is a step past the third");
				finish(context, player, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	/** Each contract has its own bond, and it is saved with the player (and kept when the contract ends). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_bond_each", tickLimit = 20)
	public void eachContractHasItsOwnBondAndItIsSaved(TestContext context) {
		ServerPlayerEntity player = ForeverGameTests.player(context, "bonded");
		try {
			Contracts.grant(player, Contract.ETERNITY);
			Contracts.grant(player, "fox");
			Bond.set(player, Contract.ETERNITY, 20);
			Bond.set(player, "fox", 3);
			context.assertTrue(Bond.step(player, Contract.ETERNITY) == 2 && Bond.uses(player, "fox") == 3 && Bond.step(Bond.uses(player, "fox")) == 1, "the two contracts share a bond");
			Bond.record(player, Contract.ETERNITY);
			context.assertTrue(Bond.uses(player, Contract.ETERNITY) == 21 && Bond.uses(player, "fox") == 3, "a use of one touched the other");
			java.util.Map<String, Integer> saved = player.getAttached(Bond.USES);
			context.assertTrue(saved != null && saved.get("eternity") == 21 && saved.get("fox") == 3, "the bonds are not kept in the saved attachment: " + saved);
			Contracts.clear(player);
			context.assertTrue(Bond.uses(player, Contract.ETERNITY) == 21, "ending the contract lost the bond");
		} finally {
			Contracts.clear(player);
			ForeverGameTests.leave(context, player);
		}
		context.complete();
	}

	/** At bond 1 the cube is 9 blocks across and holds only what is within it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_bond_one", tickLimit = 40)
	public void bondOneCubeIsNineBlocks(TestContext context) {
		floor(context);
		HuskEntity near = husk(context, 0.0, 8.0, 60.0F);
		HuskEntity wide = husk(context, 6.0, 8.0, 60.0F);
		ServerPlayerEntity player = holder(context, "small_cube", 0.0, near);
		try {
			context.runAtTick(2, () -> {
				Forever.use(player);
				var cubes = context.getWorld().getEntitiesByType(ChainsawMan.FOREVER_CUBE, e -> true);
				context.assertTrue(cubes.size() == 1 && cubes.get(0).bond() == 1 && cubes.get(0).half() == 4.5, "the cube is not 9 blocks at bond 1");
				context.assertTrue(Forever.locked(near) && !Forever.locked(wide), "the cube held what was 6 blocks off");
				var hubs = context.getWorld().getEntitiesByType(ChainsawMan.FOREVER_HUB, e -> true);
				context.assertTrue(hubs.size() == 1 && Forever.farCopies(hubs.get(0).bond()) == 11, "the line does not run 11 far copies at bond 1");
				finish(context, player, near, wide);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, near, wide);
			throw e;
		}
	}

	/** At bond 2 the cube is 13 blocks, the lock lasts 4 s and the lines run further (14 far copies, about 32 blocks). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_bond_two", tickLimit = 130)
	public void bondTwoCubeIsThirteenBlocksAndLastsFourSeconds(TestContext context) {
		floor(context);
		HuskEntity near = husk(context, 0.0, 8.0, 60.0F);
		HuskEntity wide = husk(context, 6.0, 8.0, 60.0F);
		ServerPlayerEntity player = holder(context, "big_cube", 0.0, near);
		Bond.set(player, Contract.ETERNITY, Bond.TWO);
		try {
			int[] freed = new int[1];
			context.runAtTick(2, () -> {
				Forever.use(player);
				var cubes = context.getWorld().getEntitiesByType(ChainsawMan.FOREVER_CUBE, e -> true);
				context.assertTrue(cubes.size() == 1 && cubes.get(0).bond() == 2 && cubes.get(0).half() == 6.5, "the cube is not 13 blocks at bond 2");
				context.assertTrue(Forever.locked(near) && Forever.locked(wide), "the bigger cube did not hold what is 6 blocks off");
				var hubs = context.getWorld().getEntitiesByType(ChainsawMan.FOREVER_HUB, e -> true);
				context.assertTrue(hubs.size() == 2 && Forever.farCopies(hubs.get(0).bond()) == 14 && Forever.FAR_COPIES_BOND2 > Forever.FAR_COPIES, "the lines do not run further at bond 2");
			});
			for (int t = 3; t < 120; t++) {
				int tick = t;
				context.runAtTick(t, () -> {
					if (!Forever.locked(near) && freed[0] == 0 && tick > 3) {
						freed[0] = tick;
					}
				});
			}
			context.runAtTick(120, () -> {
				int held = freed[0] - 2;
				context.assertTrue(Forever.lasts(2, false) == 80 && held >= 80 && held <= 80 + Forever.SNAP + 1, "the lock lasted " + held + " ticks, not 4 s");
				finish(context, player, near, wide);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, near, wide);
			throw e;
		}
	}

	/** The release goes off bigger at bond 3: the stored hits times 2 (cap 60) instead of times 1.5 (cap 40; three hits of 10 reach both caps). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_bond_burst", tickLimit = 60)
	public void bondThreeReleaseIsBigger(TestContext context) {
		floor(context);
		HuskEntity small = husk(context, 0.0, 8.0, 200.0F);
		HuskEntity big = husk(context, -14.0, 8.0, 200.0F);
		ServerPlayerEntity first = holder(context, "plain_burst", 0.0, small);
		ServerPlayerEntity deep = holder(context, "deep_burst", -14.0, big);
		Bond.set(deep, Contract.ETERNITY, Bond.THREE);
		try {
			context.runAtTick(2, () -> {
				Forever.use(first);
				Forever.use(deep);
				context.assertTrue(Forever.locked(small) && Forever.locked(big), "a husk was not locked");
				for (int i = 0; i < 3; i++) {
					small.damage(first.getDamageSources().playerAttack(first), 10.0F);
					big.damage(deep.getDamageSources().playerAttack(deep), 10.0F);
				}
				context.assertTrue(small.getHealth() == 200.0F && big.getHealth() == 200.0F, "the hits were not stored");
				Forever.endsIn(small, 2);
				Forever.endsIn(big, 2);
			});
			context.runAtTick(30, () -> {
				float a = 200.0F - small.getHealth();
				float b = 200.0F - big.getHealth();
				context.assertTrue(Math.abs(a - 40.0F) < 1.0F, "the plain release did " + a + ", not the cap of 40");
				context.assertTrue(Math.abs(b - 60.0F) < 1.0F, "the bond 3 release did " + b + ", not 60");
				finish(context, first, small);
				finish(context, deep, big);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, first, small);
			finish(context, deep, big);
			throw e;
		}
	}

	/**
	 * "Not yet" opens at bond 3 only: the player is untouchable for 1.5 s, cannot use Forever while out of time, steps back in with the same
	 * price as a use (3 hunger) and the same cooldown; refused at bonds 1 and 2, and during Forever's own cooldown.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_bond_notyet", tickLimit = 140)
	public void notYetIsUntouchableAndSharesTheCooldownAndPrice(TestContext context) {
		floor(context);
		HuskEntity husk = husk(context, 0.0, 8.0, 60.0F);
		ServerPlayerEntity player = holder(context, "stepper", 0.0, husk);
		try {
			Bond.set(player, Contract.ETERNITY, Bond.TWO);
			context.assertTrue(!Forever.stepOut(player) && !Forever.notYet(player), "Not yet opened at bond 2");
			Bond.set(player, Contract.ETERNITY, Bond.THREE);
			context.runAtTick(65, () -> {
				context.assertTrue(Forever.stepOut(player) && Forever.notYet(player), "Not yet did not begin at bond 3");
				context.assertTrue(player.getHungerManager().getFoodLevel() == 20, "the price was taken at the start (it comes when you step back in)");
				float health = player.getHealth();
				player.timeUntilRegen = 0;
				player.damage(player.getDamageSources().generic(), 8.0F);
				player.damage(player.getDamageSources().mobAttack(husk), 8.0F);
				context.assertTrue(player.getHealth() == health, "a hit landed while out of time: " + player.getHealth());
				Forever.use(player);
				context.assertTrue(!Forever.active(player) && !Forever.locked(husk), "Forever worked while out of time");
				context.assertTrue(!Forever.stepOut(player) || Forever.notYetLeft(player) <= Forever.NOT_YET_LASTS, "a second Not yet while the first was on");
			});
			context.runAtTick(73, () -> context.assertTrue(Forever.notYet(player) && Forever.notYetLeft(player) > 15, "it ended early: " + Forever.notYetLeft(player)));
			context.runAtTick(105, () -> {
				context.assertTrue(!Forever.notYet(player), "it did not end after 1.5 s");
				context.assertTrue(player.getHungerManager().getFoodLevel() == 20 - Forever.HUNGER, "the price was not 3 hunger: " + player.getHungerManager().getFoodLevel());
				context.assertTrue(Forever.cooldownLeft(player) > 400, "it did not share Forever's cooldown: " + Forever.cooldownLeft(player));
				context.assertTrue(!Forever.stepOut(player), "Not yet came again inside the cooldown");
				Forever.use(player);
				context.assertTrue(!Forever.active(player), "Forever came inside the shared cooldown");
				player.timeUntilRegen = 0;
				float health = player.getHealth();
				player.damage(player.getDamageSources().generic(), 2.0F);
				context.assertTrue(player.getHealth() < health, "still untouchable after stepping back in");
				finish(context, player, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	// ---- The ground and the actors.

	private static void floor(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockPos at = BlockPos.ofFloored(context.getAbsolute(new Vec3d(0.5, 40.0, 0.5))).down();
		for (int cx = (at.getX() - 26) >> 4; cx <= (at.getX() + 26) >> 4; cx++) {
			for (int cz = (at.getZ() - 12) >> 4; cz <= (at.getZ() + 22) >> 4; cz++) {
				world.setChunkForced(cx, cz, true);
			}
		}
		for (int x = -24; x <= 24; x++) {
			for (int z = -10; z <= 20; z++) {
				for (int y = -3; y <= 0; y++) {
					world.setBlockState(at.add(x, y, z), Blocks.STONE.getDefaultState());
				}
				for (int y = 1; y <= 12; y++) {
					world.setBlockState(at.add(x, y, z), Blocks.AIR.getDefaultState());
				}
			}
		}
	}

	private static HuskEntity husk(TestContext context, double x, double z, float health) {
		Vec3d at = context.getAbsolute(new Vec3d(0.5 + x, 40.0, 0.5 + z));
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

	private static ServerPlayerEntity holder(TestContext context, String name, double x, LivingEntity at) {
		ServerPlayerEntity player = ForeverGameTests.player(context, name);
		Vec3d feet = context.getAbsolute(new Vec3d(0.5 + x, 40.0, 0.5));
		player.teleport(context.getWorld(), feet.x, feet.y, feet.z, 0.0F, 0.0F);
		Contracts.grant(player, Contract.ETERNITY);
		player.getHungerManager().setFoodLevel(20);
		player.setOnGround(true);
		holder(context, player, at);
		return player;
	}

	/** Turns {@code player} to look at {@code at}. */
	private static void holder(TestContext context, ServerPlayerEntity player, LivingEntity at) {
		Vec3d eye = player.getEyePos();
		Vec3d to = at.getPos().add(0.0, at.getHeight() * 0.5, 0.0).subtract(eye);
		player.setYaw((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
		player.setPitch((float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z))));
	}

	private static void finish(TestContext context, ServerPlayerEntity player, LivingEntity... mobs) {
		Contracts.clear(player);
		Forever.ready(player);
		ForeverGameTests.leave(context, player);
		for (LivingEntity m : mobs) {
			m.discard();
		}
	}
}
