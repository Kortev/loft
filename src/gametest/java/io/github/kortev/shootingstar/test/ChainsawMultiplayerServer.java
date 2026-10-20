package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Grapple;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.Merge;
import io.github.kortev.chainsawman.form.MergeTimeline;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

/**
 * With -Dshootingstar.mptest=server and -Dshootingstar.mptest.scenario=chainsaw, the dedicated server's half of the
 * multiplayer test of the chainsaw (tools/mptest.sh chainsaw): Shooter merges with Pochita while Victim and Netherling
 * stand round and watch, pulls the cord, cuts Victim with the saws, kicks them, chains them and lets them go; is struck
 * down, and Netherling gives them blood to get them up; goes back into the form and turns back out of it with the cord;
 * and last goes into it once more and leaves the game in it. The server checks, as it goes, what each of them ought to
 * have come to, and logs a PASS or a FAIL for each. The clients' half is {@link ChainsawMultiplayerClient}.
 */
public class ChainsawMultiplayerServer implements ModInitializer {
	/** Chainsaw Man; the one he fights; the friend who gives him blood. */
	static final String MAN = "Shooter";
	static final String FOE = "Victim";
	static final String FRIEND = "Netherling";
	private static final String[] NAMES = {MAN, FOE, FRIEND};
	/** How many ticks after everyone is in the merge starts. */
	private static final int MERGE_AT = 60;
	/** How long Netherling has to get them up before it counts as failed (ticks after they went down). */
	private static final int FED_BY = 420;
	private static final List<String> FAILED = new ArrayList<>();
	private static int passed;
	private static boolean setUp;
	private static int ticks;
	private static int setUpAt = -1;
	private static int mergeAt = -1;
	private static Vec3d spawn;
	private static Vec3d heldAt;
	private static boolean wasOn;
	private static boolean wasDown;
	private static int ons;
	private static int onAt = -1;
	private static int downAt = -1;
	private static int upAt = -1;
	private static float friendBefore;
	private static int lastBlood = -1;
	private static boolean wasPulling;
	private static boolean sawTurning;
	private static double nearest = Double.MAX_VALUE;
	private static ServerPlayerEntity left;
	private static int finishedAt = -1;

	@Override
	public void onInitialize() {
		if (!"server".equals(System.getProperty("shootingstar.mptest")) || !scenario()) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerWorld world = server.getOverworld();
			world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
			world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
			world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
			world.setTimeOfDay(5000);
			world.setWeather(12000, 0, false, false);
			ShootingStar.LOGGER.info("[mptest] server ready (chainsaw)");
		});
		ServerTickEvents.END_SERVER_TICK.register(ChainsawMultiplayerServer::tick);
	}

	static boolean scenario() {
		return "chainsaw".equals(System.getProperty("shootingstar.mptest.scenario"));
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		if (ticks > 20 * 60 * 8) {
			ShootingStar.LOGGER.error("[mptest] timed out");
			summary();
			server.stop(false);
			return;
		}
		if (!setUp) {
			for (String name : NAMES) {
				if (server.getPlayerManager().getPlayer(name) == null) {
					return;
				}
			}
			setUp(server);
			setUp = true;
			setUpAt = ticks;
			return;
		}
		ServerWorld world = server.getOverworld();
		ServerPlayerEntity man = server.getPlayerManager().getPlayer(MAN);
		ServerPlayerEntity foe = server.getPlayerManager().getPlayer(FOE);
		ServerPlayerEntity friend = server.getPlayerManager().getPlayer(FRIEND);
		if (man == null) {
			// Gone, in the form the third time: the form should go with them.
			if (left != null && finishedAt < 0) {
				check(ons == 3, "Shooter left in the form the third time (" + ons + " times in it)");
				check(!Form.on(left), "leaving in the form takes it off them");
				finishedAt = ticks;
			}
			if (finishedAt >= 0 && (ticks >= finishedAt + 200 || server.getPlayerManager().getCurrentPlayerCount() == 0)) {
				summary();
				ShootingStar.LOGGER.info("[mptest] everyone has left; stopping");
				server.stop(false);
			}
			return;
		}
		if (foe == null || friend == null) {
			return;
		}
		left = man;
		if (ticks - setUpAt == MERGE_AT) {
			// Death, but not: Pochita comes out of their pack (the server starting it, as if they had died; only kortev
			// dies into it in the game).
			Merge.start(man);
			mergeAt = ticks;
			ShootingStar.LOGGER.info("[mptest] the merge starts");
		}
		merge(man, foe);
		boolean on = Form.on(man);
		boolean pulling = Form.pulling(man);
		if (pulling && !wasPulling && lastBlood >= 0 && ons == 1) {
			// The second time: it costs blood.
			int spent = lastBlood - Blood.get(man);
			check(Math.abs(spent - Blood.TRANSFORM) <= 2, "pulling the cord again costs " + Blood.TRANSFORM + " blood (it cost " + spent + ")");
		}
		wasPulling = pulling;
		if (!pulling) {
			lastBlood = Blood.get(man);
		}
		if (Form.turning(man)) {
			sawTurning = true;
		}
		if (on && !wasOn) {
			ons++;
			onAt = ticks;
			ShootingStar.LOGGER.info("[mptest] Shooter is in the form ({} times), blood {}", ons, Blood.get(man));
			if (ons == 1) {
				check(!Merge.is(man), "the merge is over once they pull the cord");
				check(Blood.get(man) >= Blood.MAX - 1, "the first pull, at the end of the merge, is free (blood " + Blood.get(man) + ")");
			}
		}
		if (!on && wasOn) {
			ShootingStar.LOGGER.info("[mptest] Shooter is out of the form ({}), downed {}", ons, Downed.is(man));
			if (ons == 2) {
				check(sawTurning && !Downed.is(man), "the second time, they turn back out of it with the cord");
				check(man.isAlive() && man.getHealth() > 0.0F, "turning back leaves them standing");
			}
		}
		wasOn = on;
		if (ons == 1 && onAt >= 0) {
			fight(world, man, foe, friend, ticks - onAt);
		}
		boolean down = Downed.is(man);
		if (down && !wasDown) {
			downAt = ticks;
		}
		if (!down && wasDown) {
			upAt = ticks;
			check(upAt - downAt < FED_BY, "Netherling's blood gets them up (" + (upAt - downAt) + " ticks down)");
			check(Math.abs(man.getHealth() - Downed.GIVEN) < 1.5F, "up with " + Downed.GIVEN + " health (" + man.getHealth() + ")");
			check(friend.getHealth() <= friendBefore - Downed.GIVEN + 0.5F, "it cost Netherling their own (" + friendBefore + " to " + friend.getHealth() + ")");
		}
		wasDown = down;
		if (downAt >= 0 && upAt < 0 && ticks == downAt + FED_BY) {
			check(false, "Netherling's blood gets them up (still down after " + FED_BY + " ticks)");
		}
		if (upAt >= 0 && ticks == upAt + Downed.RISE + 6) {
			check(man.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) > 0.01, "once up, they can walk again");
		}
	}

	/** The merge, from where it starts to where they stand waiting to pull the cord. */
	private static void merge(ServerPlayerEntity man, ServerPlayerEntity foe) {
		if (mergeAt < 0) {
			return;
		}
		int age = ticks - mergeAt;
		if (age == 20) {
			check(Merge.is(man), "the merge is on");
			check(!man.getInventory().contains(new ItemStack(ChainsawMan.POCHITA)), "Pochita came out of their pack");
			float before = man.getHealth();
			boolean hurt = man.damage(man.getDamageSources().playerAttack(foe), 8.0F);
			check(!hurt && man.getHealth() >= before, "a hit from Victim does nothing while it plays");
			heldAt = man.getPos();
		}
		// Their client walks forwards all through it: they should lie where they fell.
		if (age == MergeTimeline.LEAP && heldAt != null) {
			double moved = man.getPos().distanceTo(heldAt);
			check(moved < 0.3, "they lie where they fell, walking or not (moved " + String.format(Locale.ROOT, "%.2f", moved) + ")");
		}
		if (age == MergeTimeline.END + 2) {
			check(Heart.merged(man) && Heart.has(man), "Pochita is their heart at the end of it");
			check(man.getHealth() >= man.getMaxHealth() && Blood.get(man) >= Blood.MAX - 1, "whole, and full of blood");
			check(Merge.waiting(man), "on their feet, waiting for them to pull the cord");
		}
	}

	/** The fight, {@code in} ticks after they were first in the form (their client presses the keys, a while after). */
	private static void fight(ServerWorld world, ServerPlayerEntity man, ServerPlayerEntity foe, ServerPlayerEntity friend, int in) {
		Vec3d at = spawn;
		if (in == 30) {
			// Face to face, in reach of the saws.
			look(world, man, at, at.add(2.5, 0.0, 0.0), 1.0);
			look(world, foe, at.add(2.5, 0.0, 0.0), at, 1.4);
			ShootingStar.LOGGER.info("[mptest] Shooter and Victim face to face");
		}
		if (in == 105) {
			check(foe.isAlive() && foe.getHealth() < foe.getMaxHealth() - 12.0F, "the saws cut Victim (" + foe.getHealth() + " of " + foe.getMaxHealth() + ")");
		}
		if (in == 130) {
			check(foe.hasStatusEffect(StatusEffects.SLOWNESS), "the kick dazes Victim");
		}
		if (in == 140) {
			// Out of reach: for the chain.
			foe.removeStatusEffect(StatusEffects.SLOWNESS);
			look(world, foe, at.add(9.0, 0.0, 0.0), at, 1.4);
			look(world, man, at, at.add(9.0, 0.0, 0.0), 0.9);
			nearest = Double.MAX_VALUE;
		}
		if (in > 150 && in <= 190) {
			nearest = Math.min(nearest, foe.getPos().distanceTo(man.getPos()));
		}
		if (in == 190) {
			check(Grapple.holding(man), "the chain caught Victim and holds them");
			check(nearest < 6.0, "it yanked them in (" + String.format(Locale.ROOT, "%.1f", nearest) + " blocks at the nearest, from 9)");
		}
		if (in == 225) {
			check(!Grapple.holding(man), "fired again, it lets them go");
		}
		if (in == 240) {
			// Struck down: what would have killed them doesn't.
			man.damage(man.getDamageSources().playerAttack(foe), 500.0F);
			check(Downed.is(man) && man.isAlive(), "struck down, they're down, not dead");
			check(!Form.on(man), "down, out of the form");
		}
		if (in == 250) {
			// Netherling kneels by them, to give them blood.
			Vec3d lying = man.getPos();
			look(world, friend, lying.add(-1.4, 0.0, 0.0), lying, 0.3);
			friend.setHealth(friend.getMaxHealth());
			friendBefore = friend.getHealth();
			ShootingStar.LOGGER.info("[mptest] Netherling beside them at {}", friend.getPos());
		}
	}

	private static void check(boolean ok, String what) {
		if (ok) {
			passed++;
			ShootingStar.LOGGER.info("[mptest] PASS {}", what);
		} else {
			FAILED.add(what);
			ShootingStar.LOGGER.error("[mptest] FAIL {}", what);
		}
	}

	private static void summary() {
		ShootingStar.LOGGER.info("[mptest] {} passed, {} failed{}", passed, FAILED.size(), FAILED.isEmpty() ? "" : ": " + FAILED);
	}

	/**
	 * A stone floor round spawn, cleared above; Shooter in the middle with Pochita in their pack, Victim in front of
	 * them and Netherling off to the side, both watching them. Victim has the health for a fight with the saws.
	 */
	private static void setUp(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		BlockPos s = world.getSpawnPos();
		world.getChunk(s.getX() >> 4, s.getZ() >> 4);
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, s.getX(), s.getZ());
		for (int dx = -8; dx <= 14; dx++) {
			for (int dz = -8; dz <= 10; dz++) {
				int x = s.getX() + dx;
				int z = s.getZ() + dz;
				world.getChunk(x >> 4, z >> 4);
				world.setBlockState(new BlockPos(x, y - 1, z), Blocks.SMOOTH_STONE.getDefaultState());
				for (int up = 0; up < 6; up++) {
					world.setBlockState(new BlockPos(x, y + up, z), Blocks.AIR.getDefaultState());
				}
			}
		}
		spawn = new Vec3d(s.getX() + 0.5, y, s.getZ() + 0.5);
		for (String name : NAMES) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
			player.changeGameMode(GameMode.SURVIVAL);
			player.getInventory().clear();
			player.clearStatusEffects();
			player.setHealth(player.getMaxHealth());
			player.getHungerManager().setFoodLevel(20);
		}
		ServerPlayerEntity man = server.getPlayerManager().getPlayer(MAN);
		ServerPlayerEntity foe = server.getPlayerManager().getPlayer(FOE);
		ServerPlayerEntity friend = server.getPlayerManager().getPlayer(FRIEND);
		man.getInventory().insertStack(new ItemStack(ChainsawMan.POCHITA));
		foe.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, 20 * 60 * 10, 19, false, false));
		foe.setHealth(foe.getMaxHealth());
		look(world, man, spawn, spawn.add(5.0, 0.0, 0.0), 1.0);
		look(world, foe, spawn.add(5.0, 0.0, 0.0), spawn, 0.4);
		look(world, friend, spawn.add(2.0, 0.0, 6.0), spawn, 0.4);
		ShootingStar.LOGGER.info("[mptest] set up at {}: {} with Pochita, {} in front, {} to the side", spawn, MAN, FOE, FRIEND);
	}

	/** {@code player} at {@code from}, looking at {@code high} blocks up off the ground at {@code at}. */
	private static void look(ServerWorld world, ServerPlayerEntity player, Vec3d from, Vec3d at, double high) {
		double dx = at.x - from.x;
		double dz = at.z - from.z;
		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(at.y + high - (from.y + player.getStandingEyeHeight()), Math.sqrt(dx * dx + dz * dz))
				* MathHelper.DEGREES_PER_RADIAN);
		player.teleport(world, from.x, from.y, from.z, yaw, pitch);
		player.networkHandler.syncWithPlayerPosition();
	}
}
