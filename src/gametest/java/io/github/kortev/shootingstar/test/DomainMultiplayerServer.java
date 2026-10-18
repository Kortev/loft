package io.github.kortev.shootingstar.test;

import io.github.kortev.gojolander.GojoRules;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.Owner;
import io.github.kortev.gojolander.domain.DomainManager;
import io.github.kortev.gojolander.domain.DomainTimeline;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
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
 * With -Dshootingstar.mptest=server and -Dshootingstar.mptest.scenario=domain, the dedicated server's half of the
 * multiplayer test of the Domain (tools/mptest.sh domain): Shooter is its owner and casts it, Victim stands inside it
 * and is caught, and Netherling looks on from outside. The first one runs to its end; the caster is then refused while
 * it cools down, made ready again, and casts a second, and leaves the game in the middle of it. The server checks, as
 * it goes, what each of them ought to have come to, and logs a PASS or a FAIL for each. The clients' half is
 * {@link DomainMultiplayerClient}.
 */
public class DomainMultiplayerServer implements ModInitializer {
	static final String CASTER = "Shooter";
	static final String CAUGHT = "Victim";
	static final String ONLOOKER = "Netherling";
	private static final String[] NAMES = {CASTER, CAUGHT, ONLOOKER};
	private static final int RADIUS = 32;
	private static final int HOLD_SECONDS = 6;
	private static final List<String> FAILED = new ArrayList<>();
	private static int passed;
	private static boolean setUp;
	private static int ticks;
	private static int round;
	private static int domainId = -1;
	private static int endedAt = -1;
	private static Vec3d heldAt;
	private static boolean readied;
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
			ShootingStar.LOGGER.info("[mptest] server ready (domain)");
		});
		ServerTickEvents.END_SERVER_TICK.register(DomainMultiplayerServer::tick);
	}

	static boolean scenario() {
		return "domain".equals(System.getProperty("shootingstar.mptest.scenario"));
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
			return;
		}
		ServerPlayerEntity caster = server.getPlayerManager().getPlayer(CASTER);
		ServerPlayerEntity caught = server.getPlayerManager().getPlayer(CAUGHT);
		ServerPlayerEntity onlooker = server.getPlayerManager().getPlayer(ONLOOKER);
		DomainManager.Domain domain = DomainManager.active().isEmpty() ? null : DomainManager.active().get(0);
		if (domain != null && domain.id != domainId) {
			domainId = domain.id;
			round++;
			endedAt = -1;
			String by = domain.caster == null ? "nobody" : name(server, domain.caster);
			check(CASTER.equals(by), "round " + round + ": the Domain is cast by " + CASTER + " (it was " + by + ")");
			check(round <= 2, "no Domain beyond the two the caster casts (round " + round + ")");
		}
		if (domain != null && caught != null && onlooker != null) {
			int age = domain.age();
			if (age == DomainTimeline.CLOSE + 5) {
				check(domain.caught().contains(caught.getUuid()), "round " + round + ": " + CAUGHT + " is caught");
				check(!domain.caught().contains(onlooker.getUuid()), "round " + round + ": " + ONLOOKER + " (outside) is not caught");
				check(caster != null && !domain.caught().contains(caster.getUuid()), "round " + round + ": the caster is not caught");
				check(DomainManager.isHeld(caught), "round " + round + ": " + CAUGHT + " is held still");
				heldAt = caught.getPos();
			}
			// Their client walks forwards all through the void: they should not have gone anywhere.
			if (round == 1 && age == DomainTimeline.collapse(domain.hold) - 5 && heldAt != null) {
				double moved = caught.getPos().distanceTo(heldAt);
				check(moved < 0.2, "round 1: " + CAUGHT + " stays where they were caught, walking or not (moved "
						+ String.format(Locale.ROOT, "%.2f", moved) + ")");
			}
			// Just after the void lets go (before their hunger has them healing): broken, and dazed.
			if (round == 1 && age == DomainTimeline.collapse(domain.hold) + 1) {
				check(caught.getHealth() <= 1.0F, "round 1: " + CAUGHT + " is broken to half a heart (" + caught.getHealth() + ")");
				check(caught.hasStatusEffect(StatusEffects.SLOWNESS) && caught.hasStatusEffect(StatusEffects.DARKNESS),
						"round 1: " + CAUGHT + " is dazed after it");
			}
		}
		if (domain == null && domainId >= 0 && endedAt < 0) {
			endedAt = ticks;
			report(server, "round " + round + " over");
			if (round == 1 && caught != null && caster != null && onlooker != null) {
				check(!DomainManager.isHeld(caught), "round 1: " + CAUGHT + " is let go");
				check(caster.getHealth() >= caster.getMaxHealth() && !caster.hasStatusEffect(StatusEffects.SLOWNESS),
						"round 1: the caster is untouched");
				check(onlooker.getHealth() >= onlooker.getMaxHealth() && !onlooker.hasStatusEffect(StatusEffects.SLOWNESS),
						"round 1: " + ONLOOKER + " (outside) is untouched");
			}
			if (round == 2 && caught != null) {
				check(caster == null, "round 2: it ended because the caster left (" + CASTER + " still here: " + (caster != null) + ")");
				check(!DomainManager.isHeld(caught), "round 2: " + CAUGHT + " is let go when the caster leaves");
				check(caught.getHealth() > 1.0F, "round 2: cut short, it does not break " + CAUGHT + " (" + caught.getHealth() + ")");
				finishedAt = ticks;
			}
		}
		// The caster tries again while it cools down (their client, 40 ticks after the end): nothing should come of it.
		// Then it is made ready, as an operator would, and the one they caught healed.
		if (round == 1 && endedAt >= 0 && !readied && ticks == endedAt + 120) {
			check(DomainManager.active().isEmpty(), "round 1: casting again while it cools down does nothing");
			readied = true;
			if (caster != null) {
				DomainManager.ready(caster);
			}
			if (caught != null) {
				caught.clearStatusEffects();
				caught.setHealth(caught.getMaxHealth());
			}
			ShootingStar.LOGGER.info("[mptest] the caster's Domain is ready again");
		}
		if (finishedAt >= 0 && ticks == finishedAt + 200 || finishedAt >= 0 && server.getPlayerManager().getCurrentPlayerCount() == 0) {
			summary();
			ShootingStar.LOGGER.info("[mptest] everyone has left; stopping");
			server.stop(false);
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

	private static String name(MinecraftServer server, UUID uuid) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
		return player == null ? Objects.toString(uuid) : player.getGameProfile().getName();
	}

	private static void report(MinecraftServer server, String when) {
		for (String name : NAMES) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
			if (player == null) {
				ShootingStar.LOGGER.info("[mptest] {}: {} is gone", when, name);
				continue;
			}
			ShootingStar.LOGGER.info("[mptest] {}: {} at {} health {} held {} effects {}", when, name,
					String.format(Locale.ROOT, "%.1f %.2f %.1f", player.getX(), player.getY(), player.getZ()), player.getHealth(),
					DomainManager.isHeld(player), player.getStatusEffects().stream().map(e -> e.getEffectType().getIdAsString()).toList());
		}
	}

	/**
	 * Shooter at spawn wearing the blindfold, made its owner; Victim 10 blocks in front of him, inside where it will
	 * reach, wearing one too (theirs will do nothing); Netherling 48 blocks behind him, outside it, looking on.
	 */
	private static void setUp(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		world.getGameRules().get(GojoRules.VOID_RADIUS).set(RADIUS, server);
		world.getGameRules().get(GojoRules.VOID_HOLD).set(HOLD_SECONDS, server);
		Owner.set(server, CASTER);
		BlockPos spawn = world.getSpawnPos();
		Vec3d at = stand(world, spawn.getX(), spawn.getZ());
		for (String name : NAMES) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
			player.changeGameMode(GameMode.SURVIVAL);
			player.getInventory().clear();
			player.clearStatusEffects();
			player.setHealth(player.getMaxHealth());
			player.getHungerManager().setFoodLevel(20);
		}
		ServerPlayerEntity caster = server.getPlayerManager().getPlayer(CASTER);
		ServerPlayerEntity caught = server.getPlayerManager().getPlayer(CAUGHT);
		ServerPlayerEntity onlooker = server.getPlayerManager().getPlayer(ONLOOKER);
		caster.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
		caught.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
		DomainManager.ready(caster);
		Vec3d front = stand(world, spawn.getX() + 10, spawn.getZ());
		Vec3d behind = stand(world, spawn.getX() - 48, spawn.getZ());
		look(world, caster, at, front);
		look(world, caught, front, at);
		look(world, onlooker, behind, at);
		ShootingStar.LOGGER.info("[mptest] set up: {} at {}, {} at {}, {} at {}; owner {}", CASTER, at, CAUGHT, front, ONLOOKER, behind, Owner.get());
	}

	/** The ground at x, z, with a stone floor under it (it might be water or leaves). */
	private static Vec3d stand(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		world.setBlockState(new BlockPos(x, y - 1, z), Blocks.STONE.getDefaultState());
		world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
		world.setBlockState(new BlockPos(x, y + 1, z), Blocks.AIR.getDefaultState());
		return new Vec3d(x + 0.5, y, z + 0.5);
	}

	private static void look(ServerWorld world, ServerPlayerEntity player, Vec3d from, Vec3d at) {
		double dx = at.x - from.x;
		double dz = at.z - from.z;
		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		player.teleport(world, from.x, from.y, from.z, yaw, 0.0F);
		player.networkHandler.syncWithPlayerPosition();
	}
}
