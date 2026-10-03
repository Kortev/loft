package io.github.kortev.shootingstar.strike;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.network.ModNetworking;
import io.github.kortev.shootingstar.network.StrikeCancelPayload;
import io.github.kortev.shootingstar.network.StrikeImpactPayload;
import io.github.kortev.shootingstar.network.StrikeLockPayload;
import io.github.kortev.shootingstar.registry.ModGameRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class StrikeManager {
	/** Keeps the target area loaded and ticking until the crater is finished. */
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("shootingstar_strike",
			Comparator.comparingLong(ChunkPos::toLong), StrikeTimeline.END + 200);

	private static final List<Strike> STRIKES = new ArrayList<>();
	private static int nextId = 1;

	private StrikeManager() {
	}

	public static final class Strike {
		final int id;
		final RegistryKey<World> dimension;
		final BlockPos target;
		final UUID shooter;
		/** Crater radius, fixed when the lock is made so a game rule change mid-flight cannot desync clients. */
		final int radius;
		int age;
		@Nullable
		ImpactBuilder impact;
		boolean carved;

		Strike(int id, RegistryKey<World> dimension, BlockPos target, UUID shooter, int radius) {
			this.id = id;
			this.dimension = dimension;
			this.target = target;
			this.shooter = shooter;
			this.radius = radius;
		}

		public int id() {
			return id;
		}

		public BlockPos target() {
			return target;
		}

		public int age() {
			return age;
		}

		public boolean finished() {
			return age >= StrikeTimeline.END && (impact == null || carved);
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(StrikeManager::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STRIKES.clear());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			for (Strike strike : STRIKES) {
				if (strike.dimension == player.getWorld().getRegistryKey() && strike.age < StrikeTimeline.IMPACT) {
					ModNetworking.send(player, new StrikeLockPayload(strike.id, strike.target, strike.shooter, strike.age,
							strike.radius));
				}
			}
		});
	}

	/** Locks a strike onto {@code hit}. The shooter may be null for strikes called in by command. */
	public static Strike launch(ServerWorld world, BlockPos hit, @Nullable ServerPlayerEntity shooter) {
		BlockPos target = Targeting.settle(world, hit);
		int radius = world.getGameRules().getInt(ModGameRules.CRATER_RADIUS);
		Strike strike = new Strike(nextId++, world.getRegistryKey(), target, shooter != null ? shooter.getUuid() : Util.NIL_UUID,
				radius);
		STRIKES.add(strike);

		// Load everything out to the edge of the scorched ring, so no part of the crater is cut off by unloaded chunks.
		int scorch = Math.round(radius * 1.5F);
		ChunkPos chunk = new ChunkPos(target);
		world.getChunkManager().addTicket(TICKET, chunk, MathHelper.clamp(MathHelper.ceil(scorch / 16.0), 1, 16), chunk);

		ModNetworking.broadcast(world, new StrikeLockPayload(strike.id, target, strike.shooter, 0, radius));
		ShootingStar.LOGGER.info("Kinetic lock #{} on {} in {}", strike.id, target.toShortString(), world.getRegistryKey().getValue());
		return strike;
	}

	/** True while the player's last strike is still in flight. */
	public static boolean isBusy(UUID shooter) {
		for (Strike strike : STRIKES) {
			if (strike.shooter.equals(shooter) && strike.age < StrikeTimeline.IMPACT) {
				return true;
			}
		}
		return false;
	}

	public static List<Strike> active() {
		return List.copyOf(STRIKES);
	}

	/** Calls off every strike that has not hit yet. Returns how many were cancelled. */
	public static int cancelAll(MinecraftServer server) {
		int cancelled = 0;
		for (Iterator<Strike> it = STRIKES.iterator(); it.hasNext(); ) {
			Strike strike = it.next();
			if (strike.impact != null) {
				continue;
			}
			ServerWorld world = server.getWorld(strike.dimension);
			if (world != null) {
				ModNetworking.broadcast(world, new StrikeCancelPayload(strike.id));
			}
			it.remove();
			cancelled++;
		}
		return cancelled;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Strike> it = STRIKES.iterator(); it.hasNext(); ) {
			Strike strike = it.next();
			ServerWorld world = server.getWorld(strike.dimension);
			if (world == null) {
				it.remove();
				continue;
			}
			strike.age++;
			if (strike.age == StrikeTimeline.IMPACT) {
				ImpactBuilder impact = new ImpactBuilder(world, strike.target, strike.radius);
				strike.impact = impact;
				impact.start();
				ModNetworking.broadcast(world, new StrikeImpactPayload(strike.id, strike.target, impact.radius(),
						impact.terrain() ? impact.zoneDiameter() : 0, impact.spireHeight()));
				ShootingStar.LOGGER.info("Strike #{} impact at {}", strike.id, strike.target.toShortString());
			} else if (strike.impact != null && !strike.carved) {
				strike.carved = strike.impact.step();
			}
			if (strike.finished()) {
				it.remove();
			}
		}
	}
}
