package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.contract.Echo;
import io.github.kortev.chainsawman.contract.EchoEntity;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * The Eternity contract and its power, Echo: the contract skeleton (granted, cleared, one at a time, never for a hybrid) and
 * Echo itself (the crowd of copies round a target on uneven ground, their mirroring, the damage cap, the finale, the cooldown).
 */
public class EchoGameTests implements FabricGameTest {
	/** A contract is granted and cleared, and told to the player's saved state. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_contract", tickLimit = 20)
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
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_one", tickLimit = 20)
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
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_hybrid", tickLimit = 20)
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

	// ---- Echo.

	/** No contract: nothing happens. In Chainsaw Man form: blocked (and no cooldown is spent). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_blocked", tickLimit = 40)
	public void echoNeedsTheContractAndHumanForm(TestContext context) {
		ServerPlayerEntity player = player(context, "blocked");
		try {
			Echo.use(player);
			context.assertTrue(!Echo.active(player), "Echo started with no contract");
			Contracts.grant(player, Contract.ETERNITY);
			Form.start(player);
			context.assertTrue(Form.in(player), "the test player is not in the form");
			Echo.use(player);
			context.assertTrue(!Echo.active(player), "Echo started in Chainsaw Man form");
			context.assertTrue(Echo.cooldownLeft(player) == 0, "a blocked Echo spent the cooldown");
		} finally {
			Form.end(player);
			Contracts.clear(player);
			leave(context, player);
		}
		context.complete();
	}

	/** The test ground: a flat floor at y 39 with a lava pit, pillars, a ledge and a slope (so the ring has to find ground at different heights). */
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

	/** A husk standing still {@code z} blocks in front of the owner, on the flat part of the ground, strong enough to take what is thrown at it. */
	private static HuskEntity husk(TestContext context, double z) {
		Vec3d at = context.getAbsolute(new Vec3d(0.5, 40.0, 0.5 + z));
		HuskEntity husk = new HuskEntity(EntityType.HUSK, context.getWorld());
		husk.refreshPositionAndAngles(at.x, at.y, at.z, 180.0F, 0.0F);
		husk.setAiDisabled(true);
		husk.setPersistent();
		husk.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(500.0);
		husk.setHealth(500.0F);
		// (No armour: the numbers asked about are what the echo deals.)
		husk.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(0.0);
		context.getWorld().spawnEntity(husk);
		return husk;
	}

	/** A player with the contract on the test ground, looking at {@code at}. */
	private static ServerPlayerEntity echoer(TestContext context, String name, LivingEntity at) {
		ServerPlayerEntity player = player(context, name);
		Contracts.grant(player, Contract.ETERNITY);
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
		Echo.ready(player);
		leave(context, player);
		for (LivingEntity m : mobs) {
			m.discard();
		}
		forced(context, false);
	}

	private static int copiesInWorld(TestContext context) {
		return context.getWorld().getEntitiesByType(ChainsawMan.ECHO_ENTITY, e -> true).size();
	}

	/**
	 * It spawns its copies round the target on uneven ground: most of the fourteen, every one standing on solid ground with its
	 * feet and head free of blocks (and not in lava), at more than one height, all round the target and within the ring; a hit
	 * on a copy only flickers it; and they stay so as they settle and mirror.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_ground", tickLimit = 80)
	public void echoCopiesStandOnUnevenGround(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 8.0);
		ServerPlayerEntity player = echoer(context, "ringer", husk);
		try {
			Echo.use(player);
			context.assertTrue(Echo.active(player), "Echo did not start");
			context.assertTrue(Echo.target(player) == husk, "the husk was not the target");
			List<EchoEntity> copies = Echo.copies(player);
			context.assertTrue(copies.size() >= 10 && copies.size() <= Echo.COPIES, "too few copies: " + copies.size());
			int past = 0;
			for (EchoEntity e : copies) {
				if (!e.future()) {
					past++;
				}
			}
			context.assertTrue(past >= 3 && past <= copies.size() - 3, "not a mix of past and future: " + past + " of " + copies.size());
			context.runAtTick(30, () -> {
				List<EchoEntity> now = Echo.copies(player);
				context.assertTrue(now.size() == copies.size(), "copies were lost");
				Set<Integer> heights = new HashSet<>();
				for (EchoEntity e : now) {
					context.assertTrue(context.getWorld().isSpaceEmpty(e), "a copy is inside a block at " + e.getPos());
					BlockPos feet = e.getBlockPos();
					BlockPos below = feet.down();
					context.assertTrue(!context.getWorld().getBlockState(below).getCollisionShape(context.getWorld(), below).isEmpty(), "a copy is in the air at " + e.getPos());
					context.assertTrue(e.isOnGround(), "a copy is not on the ground at " + e.getPos());
					context.assertTrue(context.getWorld().getFluidState(feet).isEmpty() && context.getWorld().getFluidState(below).isEmpty(), "a copy is in a fluid at " + e.getPos());
					double d = Math.hypot(e.getX() - husk.getX(), e.getZ() - husk.getZ());
					context.assertTrue(d >= 3.0 && d <= 7.5, "a copy is not in the ring: " + d);
					heights.add(feet.getY());
					// Hit, it only flickers.
					context.assertTrue(!e.damage(context.getWorld().getDamageSources().generic(), 5.0F) && e.getHealth() == e.getMaxHealth(), "a copy took a hit");
				}
				context.assertTrue(heights.size() >= 2, "the ring is all at one height: " + heights);
				context.assertTrue(player.getHealth() == player.getMaxHealth(), "the owner was hurt");
			});
			context.runAtTick(40, () -> {
				finish(context, player, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	/** With nothing to look at the crowd rings the owner, on ground all the same. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_self", tickLimit = 60)
	public void echoRingsYourselfWithNoTarget(TestContext context) {
		terrain(context);
		ServerPlayerEntity player = echoer(context, "lonely", null);
		player.setPitch(-80.0F);
		try {
			Echo.use(player);
			context.assertTrue(Echo.active(player) && Echo.target(player) == player, "the crowd did not ring the owner");
			context.runAtTick(20, () -> {
				List<EchoEntity> copies = Echo.copies(player);
				context.assertTrue(copies.size() >= 8, "too few copies: " + copies.size());
				for (EchoEntity e : copies) {
					context.assertTrue(context.getWorld().isSpaceEmpty(e) && e.isOnGround(), "a copy is not standing on ground at " + e.getPos());
					context.assertTrue(e.distanceTo(player) >= 2.5 && e.distanceTo(player) <= 8.0, "a copy is not round the owner: " + e.distanceTo(player));
				}
				finish(context, player);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player);
			throw e;
		}
	}

	/** A copy jumps when the owner jumps: some of the future ones and some of the past ones rise off the ground after it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_jump", tickLimit = 90)
	public void echoCopiesJumpWhenYouJump(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 8.0);
		ServerPlayerEntity player = echoer(context, "jumper", husk);
		try {
			Echo.use(player);
			List<EchoEntity> copies = Echo.copies(player);
			double[] base = new double[copies.size()];
			double[] top = new double[copies.size()];
			Vec3d at = player.getPos();
			context.runAtTick(25, () -> {
				for (int i = 0; i < copies.size(); i++) {
					base[i] = copies.get(i).getY();
					top[i] = base[i];
				}
				player.setOnGround(true);
			});
			context.runAtTick(26, () -> {
				// The jump: off the ground and 0.5 up.
				player.setOnGround(false);
				player.teleport(context.getWorld(), at.x, at.y + 0.5, at.z, player.getYaw(), player.getPitch());
			});
			context.runAtTick(30, () -> {
				player.teleport(context.getWorld(), at.x, at.y, at.z, player.getYaw(), player.getPitch());
				player.setOnGround(true);
			});
			for (int t = 26; t < 60; t++) {
				context.runAtTick(t, () -> {
					for (int i = 0; i < copies.size(); i++) {
						top[i] = Math.max(top[i], copies.get(i).getY());
					}
				});
			}
			context.runAtTick(62, () -> {
				int future = 0;
				int past = 0;
				for (int i = 0; i < copies.size(); i++) {
					if (top[i] - base[i] > 0.4) {
						if (copies.get(i).future()) {
							future++;
						} else {
							past++;
						}
					}
				}
				finish(context, player, husk);
				context.assertTrue(future >= 1 && past >= 1, "copies did not jump: future " + future + ", past " + past);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	/** Walking in closes the ring: the copies come nearer the target as the owner does. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_close", tickLimit = 130)
	public void echoRingClosesWhenYouWalkIn(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 8.0);
		ServerPlayerEntity player = echoer(context, "closer", husk);
		try {
			Echo.use(player);
			double[] before = new double[1];
			Vec3d at = player.getPos();
			context.runAtTick(20, () -> before[0] = mean(Echo.copies(player), husk));
			for (int t = 0; t < 20; t++) {
				int step = t + 1;
				context.runAtTick(22 + t, () -> player.teleport(context.getWorld(), at.x, at.y, at.z + 0.2 * step, player.getYaw(), player.getPitch()));
			}
			context.runAtTick(110, () -> {
				double after = mean(Echo.copies(player), husk);
				finish(context, player, husk);
				context.assertTrue(before[0] >= 3.8, "the ring started too close: " + before[0]);
				context.assertTrue(after <= before[0] - 1.2, "the ring did not close: " + before[0] + " to " + after);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	private static double mean(List<EchoEntity> copies, LivingEntity target) {
		double sum = 0.0;
		for (EchoEntity e : copies) {
			sum += Math.hypot(e.getX() - target.getX(), e.getZ() - target.getZ());
		}
		return sum / Math.max(1, copies.size());
	}

	/**
	 * The damage cap: with the ring closed in round the target and the owner swinging every tick, the copies hit for the echo's
	 * little hit, and the target takes one echo hit at most every five ticks however many swing.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_cap", tickLimit = 160)
	public void echoHitsAreCappedPerTarget(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 8.0);
		ServerPlayerEntity player = echoer(context, "swinger", husk);
		try {
			Echo.use(player);
			Vec3d at = player.getPos();
			for (int t = 0; t < 30; t++) {
				int step = t + 1;
				context.runAtTick(10 + t, () -> player.teleport(context.getWorld(), at.x, at.y, at.z + 0.2 * step, player.getYaw(), player.getPitch()));
			}
			List<Integer> hits = new ArrayList<>();
			float[] last = new float[1];
			context.runAtTick(80, () -> last[0] = husk.getHealth());
			for (int t = 81; t < 130; t++) {
				int now = t;
				context.runAtTick(t, () -> {
					Echo.swung(player);
					if (husk.getHealth() < last[0] - 0.001F) {
						context.assertTrue(Math.abs(last[0] - husk.getHealth() - Echo.HIT) < 0.01F, "an echo hit was not " + Echo.HIT + ": " + (last[0] - husk.getHealth()));
						hits.add(now);
					}
					last[0] = husk.getHealth();
				});
			}
			context.runAtTick(135, () -> {
				finish(context, player, husk);
				context.assertTrue(hits.size() >= 3, "the copies hardly hit: " + hits);
				for (int i = 1; i < hits.size(); i++) {
					context.assertTrue(hits.get(i) - hits.get(i - 1) >= Echo.HIT_GAP, "two echo hits within " + Echo.HIT_GAP + " ticks: " + hits);
				}
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	/** The finale removes every copy and the target takes the one combined hit; then the cooldown. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_finale", tickLimit = 100)
	public void echoFinaleCollapsesTheCrowdIntoOneHit(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 8.0);
		ServerPlayerEntity player = echoer(context, "finisher", husk);
		try {
			Echo.use(player);
			int[] count = new int[1];
			float[] before = new float[1];
			context.runAtTick(20, () -> {
				count[0] = Echo.copies(player).size();
				before[0] = husk.getHealth();
				Echo.finaleIn(player, 1);
			});
			context.runAtTick(60, () -> {
				context.assertTrue(!Echo.active(player), "the echo was still going after the finale");
				context.assertTrue(Echo.copies(player).isEmpty() && copiesInWorld(context) == 0, "copies were left");
				float expected = Math.min(Echo.FINALE_MAX, Echo.FINALE_BASE + Echo.FINALE_EACH * count[0]);
				context.assertTrue(Math.abs(before[0] - husk.getHealth() - expected) < 0.01F, "the finale hit was " + (before[0] - husk.getHealth()) + ", not " + expected);
				context.assertTrue(Echo.cooldownLeft(player) > Echo.COOLDOWN - 100, "no cooldown after the finale: " + Echo.cooldownLeft(player));
				Echo.use(player);
				context.assertTrue(!Echo.active(player), "Echo started again during the cooldown");
				Echo.ready(player);
				Echo.use(player);
				context.assertTrue(Echo.active(player), "Echo would not start after the cooldown");
				finish(context, player, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	/** If the target dies the crowd collapses back early, with no hit and nothing left behind. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_dies", tickLimit = 100)
	public void echoCollapsesEarlyWhenTheTargetDies(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 8.0);
		ServerPlayerEntity player = echoer(context, "killer", husk);
		try {
			Echo.use(player);
			context.runAtTick(15, () -> husk.kill());
			context.runAtTick(60, () -> {
				context.assertTrue(!Echo.active(player), "the echo went on after its target died");
				context.assertTrue(copiesInWorld(context) == 0, "copies were left");
				context.assertTrue(Echo.cooldownLeft(player) > 0, "no cooldown after the echo");
				finish(context, player, husk);
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk);
			throw e;
		}
	}

	/** A mob that was after the owner switches, at some point, to one of the copies. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_mob", tickLimit = 330)
	public void echoMobsLoseTrackOfYou(TestContext context) {
		terrain(context);
		HuskEntity husk = husk(context, 8.0);
		ServerPlayerEntity player = echoer(context, "hunted", husk);
		ZombieEntity zombie = new ZombieEntity(context.getWorld());
		Vec3d at = context.getAbsolute(new Vec3d(0.5, 40.0, 3.5));
		zombie.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		zombie.setAiDisabled(true);
		zombie.setPersistent();
		context.getWorld().spawnEntity(zombie);
		try {
			zombie.setTarget(player);
			Echo.use(player);
			boolean[] switched = new boolean[1];
			for (int t = 1; t < 290; t++) {
				context.runAtTick(t, () -> {
					if (zombie.getTarget() instanceof EchoEntity) {
						switched[0] = true;
					}
				});
			}
			context.runAtTick(300, () -> {
				context.assertTrue(player.getHealth() == player.getMaxHealth(), "the owner was hurt");
				finish(context, player, husk, zombie);
				context.assertTrue(switched[0], "the mob never switched to a copy");
				context.complete();
			});
		} catch (RuntimeException e) {
			finish(context, player, husk, zombie);
			throw e;
		}
	}

	// Helpers.

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
