package io.github.kortev.shootingstar.thunder;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.network.ModNetworking;
import io.github.kortev.shootingstar.network.ThunderArcsPayload;
import io.github.kortev.shootingstar.network.ThunderCancelPayload;
import io.github.kortev.shootingstar.network.ThunderLockPayload;
import io.github.kortev.shootingstar.network.ThunderStrokePayload;
import io.github.kortev.shootingstar.registry.ModCriteria;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.strike.Targeting;
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

/**
 * Every Mjölnir strike under way on the server, run against {@link ThunderTimeline}: raised, the storm gathering over the
 * target for eighteen seconds while the shooter watches the feed, then the stroke, when {@link ThunderBuilder} breaks the
 * ground and the arcs jump. A strike lasts well under a minute, so like Gungnir's it is not saved: a server that stops
 * mid-strike simply forgets it.
 */
public final class ThunderManager {
	/** Keeps the zone loaded and ticking until the scar is burned and the crater is open. */
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("shootingstar_thunder",
			Comparator.comparingLong(ChunkPos::toLong), ThunderTimeline.END + 200);

	private static final List<Strike> STRIKES = new ArrayList<>();
	private static int nextId = 1;

	private ThunderManager() {
	}

	public static final class Strike {
		final int id;
		final RegistryKey<World> dimension;
		final BlockPos target;
		final UUID shooter;
		/** Strike radius, fixed when the hammer is raised so a game rule changed mid-strike cannot desync clients. */
		final int radius;
		final long seed;
		int age;
		@Nullable
		ThunderBuilder builder;
		boolean done;

		Strike(int id, RegistryKey<World> dimension, BlockPos target, UUID shooter, int radius, long seed) {
			this.id = id;
			this.dimension = dimension;
			this.target = target;
			this.shooter = shooter;
			this.radius = radius;
			this.seed = seed;
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

		public int radius() {
			return radius;
		}

		public boolean finished() {
			return age >= ThunderTimeline.END && (builder == null || done);
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ThunderManager::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STRIKES.clear());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			for (Strike strike : STRIKES) {
				if (strike.dimension == player.getWorld().getRegistryKey() && strike.age < ThunderTimeline.STROKE) {
					ModNetworking.send(player, new ThunderLockPayload(strike.id, strike.target, strike.shooter, strike.age,
							strike.radius, strike.seed));
				}
			}
		});
	}

	/** Raises Mjölnir at {@code hit}. The shooter may be null for strikes called in by command. */
	public static Strike launch(ServerWorld world, BlockPos hit, @Nullable ServerPlayerEntity shooter) {
		BlockPos target = Targeting.settle(world, hit);
		int radius = world.getGameRules().getInt(ModGameRules.MJOLNIR_RADIUS);
		long seed = world.getRandom().nextLong();
		Strike strike = new Strike(nextId++, world.getRegistryKey(), target, shooter != null ? shooter.getUuid() : Util.NIL_UUID,
				radius, seed);
		STRIKES.add(strike);

		// Load out to the edge of the scar and the arcs beyond it, with two chunks of margin so the edge still ticks and
		// every change there reaches the players watching.
		ChunkPos chunk = new ChunkPos(target);
		int reach = ThunderTimeline.scarRadius(radius) + 16;
		world.getChunkManager().addTicket(TICKET, chunk, MathHelper.clamp(MathHelper.ceil(reach / 16.0) + 2, 1, 18), chunk);

		ModNetworking.broadcast(world, new ThunderLockPayload(strike.id, target, strike.shooter, 0, radius, seed));
		ModCriteria.fire(shooter, ModCriteria.MJOLNIR_RAISE);
		ShootingStar.LOGGER.info("Mjölnir raised #{} at {} in {}", strike.id, target.toShortString(), world.getRegistryKey().getValue());
		return strike;
	}

	/** True while the player's last strike has not come down yet. */
	public static boolean isBusy(UUID shooter) {
		for (Strike strike : STRIKES) {
			if (strike.shooter.equals(shooter) && strike.age < ThunderTimeline.STROKE) {
				return true;
			}
		}
		return false;
	}

	public static List<Strike> active() {
		return List.copyOf(STRIKES);
	}

	/** Calls off every strike whose bolt has not come down yet. Returns how many were called off. */
	public static int cancelAll(MinecraftServer server) {
		int cancelled = 0;
		for (Iterator<Strike> it = STRIKES.iterator(); it.hasNext(); ) {
			Strike strike = it.next();
			if (strike.builder != null) {
				continue;
			}
			ServerWorld world = server.getWorld(strike.dimension);
			if (world != null) {
				ModNetworking.broadcast(world, new ThunderCancelPayload(strike.id));
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
			if (strike.age == ThunderTimeline.STROKE) {
				stroke(server, world, strike);
			} else if (strike.builder != null && !strike.done) {
				strike.done = strike.builder.step();
			}
			if (strike.finished()) {
				it.remove();
			}
		}
	}

	private static void stroke(MinecraftServer server, ServerWorld world, Strike strike) {
		ServerPlayerEntity caller = server.getPlayerManager().getPlayer(strike.shooter);
		ThunderBuilder builder = new ThunderBuilder(world, strike.target, strike.radius, strike.seed,
				caller != null && caller.getWorld() == world ? caller : null, strike.shooter);
		strike.builder = builder;
		builder.start();
		ModNetworking.broadcast(world, new ThunderStrokePayload(strike.id, strike.target, strike.radius, strike.seed,
				builder.terrain(), builder.boltHeight()));
		List<Float> arcs = builder.arcData();
		if (!arcs.isEmpty()) {
			ModNetworking.broadcast(world, new ThunderArcsPayload(strike.id, arcs));
		}
		ModCriteria.fire(caller, ModCriteria.MJOLNIR_STROKE);
		ShootingStar.LOGGER.info("Mjölnir #{} stroke at {}", strike.id, strike.target.toShortString());
	}
}
