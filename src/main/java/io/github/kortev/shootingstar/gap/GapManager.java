package io.github.kortev.shootingstar.gap;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.network.GapEndPayload;
import io.github.kortev.shootingstar.network.GapFloorPayload;
import io.github.kortev.shootingstar.network.GapLockPayload;
import io.github.kortev.shootingstar.network.GapSettlePayload;
import io.github.kortev.shootingstar.network.GapWarpPayload;
import io.github.kortev.shootingstar.network.ModNetworking;
import io.github.kortev.shootingstar.registry.ModBlocks;
import io.github.kortev.shootingstar.registry.ModCriteria;
import io.github.kortev.shootingstar.registry.ModDamageTypes;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.strike.Targeting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Runs Ω-00 Ginnungagap events on the server: the erasure of everything in the zone after the block of the
 * other universe comes down, and everyone in the world held safe in the black until the shooter uses the key again.
 * <p>
 * It plays out for the whole world at once, like a live event: anyone still standing in the zone when the black
 * reaches them is erased with it; then everyone else, wherever they were, is carried to the rim of the hole, round the
 * shooter, to wait in the black together on a floor of nothing ({@link VoidFloor}), out of reach of anything left in
 * the world, and to watch it come back; and when it is all back, each is carried home again.
 */
public final class GapManager {
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("shootingstar_gap",
			Comparator.comparingLong(ChunkPos::toLong), GapTimeline.END + 200);
	/**
	 * Only the key lets the world back. But if whoever has it is gone (logged off) this long, it comes back on its own,
	 * so nobody is left in the void for good.
	 */
	private static final int ABSENT_LIMIT = 20 * 60 * 5;
	/** How long a player is held in the light before they are carried off (so everyone sees them go). */
	private static final int WARP_DELAY = 10;
	/** How far out from the middle of the hole everyone is gathered, past its edge, and how far apart. */
	private static final double GATHER_OUT = 10.0;
	private static final double GATHER_APART = 4.0;

	private static final List<Gap> GAPS = new ArrayList<>();
	/**
	 * Released events whose world is coming back: their unseen floor stays until everyone's rebuild has shown the hole
	 * (GapTimeline.REBUILD_DONE), so nobody walks into it before they can see it; and everyone gathered for them is
	 * carried home when the rebuild is over (GapTimeline.REBUILD_END).
	 */
	private static final List<Gap> RETURNING = new ArrayList<>();
	/** Players about to be carried somewhere, once the light has held them for a moment. */
	private static final List<Warp> WARPS = new ArrayList<>();
	/** Anyone who logged off while gathered, and where they are to be sent when they come back. */
	private static final Map<UUID, Home> AWAY = new HashMap<>();
	private static int nextId = 1;
	private static long ticks;

	private GapManager() {
	}

	/** Where a gathered player came from, and which way they faced. */
	private record Home(RegistryKey<World> world, Vec3d pos, float yaw, float pitch) {
	}

	private record Warp(Gap gap, UUID player, RegistryKey<World> world, Vec3d to, float yaw, float pitch, double floor, long at) {
	}

	public static final class Gap {
		final int id;
		final RegistryKey<World> dimension;
		final BlockPos target;
		final UUID shooter;
		final int radius;
		final boolean terrain;
		/** Halfway between the shooter and the target (kept for the network format; nothing watches it now). */
		final BlockPos swapSpot;
		final boolean tree;
		final Random random;
		int age;
		@Nullable
		Erasure erasure;
		boolean erased;
		boolean released;
		/** Everyone in the world has been taken into the void. */
		boolean taken;
		/** How long the shooter has been gone (logged off) while everyone waits in the void. */
		int absent;
		/** Everyone gathered at the rim for it, and where each is to be sent home to. */
		final Map<UUID, Home> homes = new HashMap<>();
		/** Everyone walking on the floor of nothing, and how high it is for each. */
		final Map<UUID, Double> floors = new HashMap<>();
		/** Everyone the black has erased (each only once, so whoever comes back into the zone is not erased again). */
		final Set<UUID> erasedPlayers = new HashSet<>();
		/** Which way round the hole the shooter is (radians), where the gathering starts; and how many have places. */
		double side;
		int places;

		Gap(int id, RegistryKey<World> dimension, BlockPos target, UUID shooter, int radius, boolean terrain, BlockPos swapSpot,
				boolean tree) {
			this.id = id;
			this.dimension = dimension;
			this.target = target;
			this.shooter = shooter;
			this.radius = radius;
			this.terrain = terrain;
			this.swapSpot = swapSpot;
			this.tree = tree;
			this.random = Random.create(target.asLong() ^ id);
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

		/** Whether what was in the zone has all gone (the crater is carved), so the world can come back round it. */
		public boolean unmade() {
			return !terrain || erased;
		}

		GapLockPayload payload() {
			return new GapLockPayload(id, target, shooter, age, radius, terrain, swapSpot, tree);
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GapManager::tick);
		// Anything still floored over is opened up as the server stops, before the worlds are saved, so no barrier is left
		// standing in a save.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Gap gap : RETURNING) {
				if (gap.erasure != null) {
					gap.erasure.unlid();
				}
			}
			for (Gap gap : GAPS) {
				if (gap.erasure != null) {
					gap.erasure.unlid();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RETURNING.clear();
			GAPS.clear();
			WARPS.clear();
			AWAY.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			Home away = AWAY.remove(player.getUuid());
			for (Gap gap : GAPS) {
				boolean mine = gap.shooter.equals(player.getUuid());
				// Anyone coming into a world that is gone is in the black with everyone else.
				if (gap.dimension == player.getWorld().getRegistryKey() && !gap.released && (gap.age < GapTimeline.END || mine || gap.taken)) {
					ModNetworking.send(player, gap.payload());
				}
			}
			Gap gap = holding(player.getServerWorld());
			if (gap != null) {
				// Back while it is still going on: gathered again (home is still where they first came from).
				if (away != null) {
					gap.homes.put(player.getUuid(), away);
				}
				gather(player.getServerWorld(), gap, player);
			} else if (away != null) {
				// Back after it is all over: home.
				ServerWorld world = server.getWorld(away.world());
				if (world != null) {
					Vec3d to = safe(world, null, away.pos());
					player.teleport(world, to.x, to.y, to.z, away.yaw(), away.pitch());
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			for (Gap gap : all()) {
				Home home = gap.homes.remove(player.getUuid());
				gap.floors.remove(player.getUuid());
				if (home != null) {
					AWAY.put(player.getUuid(), home);
				}
			}
		});
		// Erased, and back: straight back to the rest of them at the rim, and afterwards home to where they came back.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			Gap gap = holding(newPlayer.getServerWorld());
			if (gap != null && !alive) {
				gap.homes.remove(newPlayer.getUuid());
				gap.floors.remove(newPlayer.getUuid());
				gather(newPlayer.getServerWorld(), gap, newPlayer);
			}
		});
		// Someone whose rebuild is over sooner (they hurried it) can go home now.
		ServerPlayNetworking.registerGlobalReceiver(GapSettlePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			for (Gap gap : RETURNING) {
				if (gap.homes.containsKey(player.getUuid())) {
					sendHome(player.getServerWorld(), gap, player);
				}
			}
		});
		// Nothing touches the shooter while their event plays: they cannot move, and they are the one thing left. Nor
		// anyone, once their world is gone, until they are home again.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayerEntity player
				&& (shielded(player) || gone(player.getWorld()) || held(player))));
		// And nothing is there to touch: no breaking, placing, using or hitting anything in a world that is gone, or while
		// held at the rim watching it come back. Only the key, which brings it back.
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> cut(player, world) ? ActionResult.FAIL : ActionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> cut(player, world) && !holdingKey(player, hand) ? ActionResult.FAIL
				: ActionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> cut(player, world) ? ActionResult.FAIL : ActionResult.PASS);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> cut(player, world) ? ActionResult.FAIL : ActionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) -> cut(player, world) && !holdingKey(player, hand)
				? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !cut(player, world));
	}

	/** Whether {@code player} can touch nothing: their world is gone, or they are held at the rim while it comes back. */
	private static boolean cut(PlayerEntity player, World world) {
		return gone(world) || player instanceof ServerPlayerEntity serverPlayer && held(serverPlayer);
	}

	/** Turns the key on {@code hit}. The shooter may be null for events called in by command. */
	public static Gap launch(ServerWorld world, BlockPos hit, @Nullable ServerPlayerEntity shooter) {
		BlockPos target = Targeting.settle(world, hit);
		int radius = world.getGameRules().getInt(ModGameRules.GAP_RADIUS);
		boolean terrain = world.getGameRules().getBoolean(ModGameRules.GAP_TERRAIN);
		BlockPos from = shooter != null ? shooter.getBlockPos() : target.add(48, 0, 0);
		BlockPos spot = ground(world, (target.getX() + from.getX()) / 2, (target.getZ() + from.getZ()) / 2);
		Gap gap = new Gap(nextId++, world.getRegistryKey(), target, shooter != null ? shooter.getUuid() : Util.NIL_UUID, radius,
				terrain, spot, false);
		GAPS.add(gap);
		ChunkPos chunk = new ChunkPos(target);
		world.getChunkManager().addTicket(TICKET, chunk, MathHelper.clamp(MathHelper.ceil(radius / 16.0) + 2, 1, 32), chunk);
		ModNetworking.broadcast(world, gap.payload());
		ShootingStar.LOGGER.info("Ginnungagap #{} on {} in {}", gap.id, target.toShortString(), world.getRegistryKey().getValue());
		ModCriteria.fire(shooter, ModCriteria.GAP_OPEN);
		return gap;
	}

	/** True while the player's last event is still playing or holding them in the black. */
	public static boolean isBusy(UUID shooter) {
		for (Gap gap : GAPS) {
			if (gap.shooter.equals(shooter) && !gap.released) {
				return true;
			}
		}
		return false;
	}

	/** The event that has taken this world, which the cracked key turned in it ends; or null. */
	@Nullable
	public static Gap goneWith(World world) {
		for (Gap gap : GAPS) {
			if (gap.taken && !gap.released && gap.dimension == world.getRegistryKey()) {
				return gap;
			}
		}
		return null;
	}

	/** True while this world is gone: everything in it black, everyone in it holding still in nothing. */
	public static boolean gone(World world) {
		return !world.isClient() && goneWith(world) != null;
	}

	/**
	 * True while {@code player} is held by an event: gathered at the rim, on the floor of nothing, out of reach of
	 * everything, from the black until they are carried home.
	 */
	public static boolean held(ServerPlayerEntity player) {
		for (Gap gap : GAPS) {
			if (gap.floors.containsKey(player.getUuid())) {
				return true;
			}
		}
		for (Gap gap : RETURNING) {
			if (gap.floors.containsKey(player.getUuid())) {
				return true;
			}
		}
		return false;
	}

	/** The event in this world everyone is being held for (gone, or coming back), or null. */
	@Nullable
	private static Gap holding(ServerWorld world) {
		Gap gone = goneWith(world);
		if (gone != null) {
			return gone;
		}
		for (Gap gap : RETURNING) {
			if (gap.dimension == world.getRegistryKey() && gap.age < GapTimeline.REBUILD_END) {
				return gap;
			}
		}
		return null;
	}

	private static boolean holdingKey(PlayerEntity player, Hand hand) {
		return player.getStackInHand(hand).isOf(ModItems.GENESIS_KEY);
	}

	public static List<Gap> active() {
		return List.copyOf(GAPS);
	}

	private static List<Gap> all() {
		List<Gap> all = new ArrayList<>(GAPS);
		all.addAll(RETURNING);
		return all;
	}

	private static boolean shielded(ServerPlayerEntity player) {
		for (Gap gap : GAPS) {
			if (!gap.released && gap.shooter.equals(player.getUuid())) {
				return true;
			}
		}
		return false;
	}

	/** Lets reality back in: the world is there again, and everyone in it is told, so they see it rebuilt. */
	public static void release(Gap gap, MinecraftServer server) {
		if (gap.released) {
			return;
		}
		gap.released = true;
		ServerWorld world = server.getWorld(gap.dimension);
		if (world == null) {
			return;
		}
		if (gap.terrain && gap.erasure != null) {
			// Let back in early (by command, or the key gone too long): the hole is finished first, all at once.
			while (!gap.erasure.done()) {
				gap.erasure.step(Double.MAX_VALUE);
			}
			// Anyone not held on the floor who is out over where the hole is, on the ground that was, is set down on its
			// rim before that goes.
			for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
				if (!held(player) && horizontal(player.getPos(), gap.target) <= gap.radius + 8) {
					toRim(world, gap, player);
				}
			}
		}
		// The ground the black was walked on stays until the rebuild has shown everyone the hole; everyone gathered goes
		// home when it is over.
		gap.age = 0;
		RETURNING.add(gap);
		ModNetworking.broadcast(world, new GapEndPayload(gap.id));
		ShootingStar.LOGGER.info("Ginnungagap #{} released", gap.id);
	}

	/** Calls off every event still in its sequence. Returns how many were stopped. */
	public static int cancelAll(MinecraftServer server) {
		int n = 0;
		for (Gap gap : List.copyOf(GAPS)) {
			if (!gap.released) {
				release(gap, server);
				n++;
			}
		}
		return n;
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		for (Iterator<Warp> it = WARPS.iterator(); it.hasNext(); ) {
			Warp warp = it.next();
			if (ticks >= warp.at()) {
				it.remove();
				arrive(server, warp);
			}
		}
		for (Iterator<Gap> it = RETURNING.iterator(); it.hasNext(); ) {
			Gap gap = it.next();
			ServerWorld world = server.getWorld(gap.dimension);
			if (world == null) {
				it.remove();
				continue;
			}
			gap.age++;
			if (gap.age == GapTimeline.REBUILD_DONE + 40 && gap.erasure != null) {
				unlid(world, gap);
			}
			if (gap.age >= GapTimeline.REBUILD_END) {
				// It is all back: everyone still at the rim is carried home.
				for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
					if (gap.homes.containsKey(player.getUuid())) {
						sendHome(world, gap, player);
					}
				}
				it.remove();
			}
		}
		for (Iterator<Gap> it = GAPS.iterator(); it.hasNext(); ) {
			Gap gap = it.next();
			ServerWorld world = server.getWorld(gap.dimension);
			if (world == null || gap.released) {
				it.remove();
				continue;
			}
			gap.age++;
			ServerPlayerEntity shooter = server.getPlayerManager().getPlayer(gap.shooter);
			// As the black comes over the zone, anyone still standing in it is erased with it (but the shooter).
			if (gap.age >= GapTimeline.ERASURE && gap.age < GapTimeline.NOTHING && gap.terrain
					&& world.getGameRules().getBoolean(ModGameRules.GAP_LETHAL)) {
				eraseIn(world, gap, shooter);
			}
			// Once the black has everything, the world is gone: everyone in it, wherever they are, is carried to the rim of
			// the hole, round the shooter, under the black, to be in it together until the key is turned again.
			if (gap.age == GapTimeline.NOTHING) {
				gap.taken = true;
				Vec3d from = shooter != null && shooter.getWorld() == world ? shooter.getPos() : Vec3d.ofCenter(gap.target).add(1, 0, 0);
				gap.side = Math.atan2(from.z - gap.target.getZ() - 0.5, from.x - gap.target.getX() - 0.5);
				if (shooter != null && shooter.getWorld() == world) {
					gather(world, gap, shooter);
				}
				for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
					if (player.isAlive()) {
						gather(world, gap, player);
					}
					ModCriteria.fire(player, ModCriteria.GAP_VOID);
				}
			}
			// The black spreading is only seen; nothing is touched until then. Then the hole is taken out, without telling
			// anyone block by block (see Erasure), and what was in it goes.
			if (gap.taken) {
				erase(world, gap);
			}
			if (gap.age >= GapTimeline.END) {
				// Called in by command, with no key to turn: it comes back by itself once it is over.
				if (gap.shooter.equals(Util.NIL_UUID)) {
					release(gap, server);
				} else {
					gap.absent = shooter == null ? gap.absent + 1 : 0;
					if (gap.absent >= ABSENT_LIMIT) {
						ShootingStar.LOGGER.info("Ginnungagap #{}: the key has been gone too long, letting the world back", gap.id);
						release(gap, server);
					}
				}
			}
			if (gap.released) {
				it.remove();
			}
		}
	}

	// --- the people -------------------------------------------------------------------------

	/**
	 * Everyone (but the shooter, and anyone who cannot be hurt) still in the zone when the black reaches where they
	 * stand is erased with it: they see it coming, and then they are gone, whatever they carried with them.
	 */
	private static void eraseIn(ServerWorld world, Gap gap, @Nullable ServerPlayerEntity shooter) {
		double front = GapTimeline.eraseFront(gap.age);
		for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
			if (player.getUuid().equals(gap.shooter) || player.isCreative() || player.isSpectator() || !player.isAlive()
					|| gap.erasedPlayers.contains(player.getUuid()) || horizontal(player.getPos(), gap.target) > gap.radius + 2) {
				continue;
			}
			// The same measure the black is drawn with (ss_gap): blocks along x and z, height for half.
			double reach = Math.abs(player.getX() - gap.target.getX() - 0.5) + Math.abs(player.getZ() - gap.target.getZ() - 0.5)
					+ 0.5 * Math.abs(player.getY() - gap.target.getY());
			if (reach <= front) {
				gap.erasedPlayers.add(player.getUuid());
				// Put down to the shooter where their rules let one player hurt another; where they do not (pvp off, the same
				// team), the erasure takes them all the same, only not by anyone's hand (the gamerule is what turns it off).
				boolean by = shooter != null && player.shouldDamagePlayer(shooter);
				player.damage(ModDamageTypes.erased(world, by ? shooter : null), Float.MAX_VALUE);
				ShootingStar.LOGGER.info("Ginnungagap #{} erased {}", gap.id, player.getName().getString());
			}
		}
	}

	/**
	 * Takes {@code player} to the rim of the hole with everyone else: they are held where they are on a floor of
	 * nothing, then carried in a moment to the next place round the rim from the shooter, facing the hole.
	 */
	private static void gather(ServerWorld world, Gap gap, ServerPlayerEntity player) {
		UUID id = player.getUuid();
		if (gap.homes.containsKey(id)) {
			return;
		}
		gap.homes.put(id, new Home(world.getRegistryKey(), player.getPos(), player.getYaw(), player.getPitch()));
		hold(gap, player, player.getY());
		Vec3d place = place(world, gap, gap.places++);
		Vec3d middle = Vec3d.ofCenter(gap.target);
		float yaw = (float) (MathHelper.atan2(middle.z - place.z, middle.x - place.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		warp(world, gap, player, place, yaw, 10.0F, place.y);
	}

	/** Sends {@code player} home from the rim (to somewhere safe near where they came from), off the floor of nothing. */
	private static void sendHome(ServerWorld world, Gap gap, ServerPlayerEntity player) {
		Home home = gap.homes.remove(player.getUuid());
		if (home == null) {
			return;
		}
		ServerWorld to = player.getServer().getWorld(home.world());
		if (to == null || to != world) {
			hold(gap, player, Double.NaN);
			return;
		}
		warp(world, gap, player, safe(world, gap, home.pos()), home.yaw(), home.pitch(), Double.NaN);
	}

	/** Puts {@code player} on a floor of nothing {@code floor} high (NaN: off it), here and on their client. */
	private static void hold(Gap gap, ServerPlayerEntity player, double floor) {
		if (Double.isNaN(floor)) {
			gap.floors.remove(player.getUuid());
		} else {
			gap.floors.put(player.getUuid(), floor);
		}
		player.fallDistance = 0.0F;
		ModNetworking.send(player, new GapFloorPayload(floor));
	}

	/**
	 * The {@code index}th place at the rim: the shooter's own first, on their side of the hole, then out to either side
	 * of it in turn, a few blocks apart, so everyone stands together along the edge.
	 */
	private static Vec3d place(ServerWorld world, Gap gap, int index) {
		double ring = gap.radius + GATHER_OUT;
		int k = (index + 1) / 2;
		double a = gap.side + (index % 2 == 1 ? 1 : -1) * k * GATHER_APART / ring;
		int x = MathHelper.floor(gap.target.getX() + 0.5 + Math.cos(a) * ring);
		int z = MathHelper.floor(gap.target.getZ() + 0.5 + Math.sin(a) * ring);
		world.getChunk(x >> 4, z >> 4);
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		return new Vec3d(x + 0.5, y, z + 0.5);
	}

	/**
	 * Carries {@code player} to {@code to}: everyone is told now, so they see them held in the light where they stand,
	 * and the move is made a moment later. Their floor of nothing is then {@code floor} high (NaN: none).
	 */
	private static void warp(ServerWorld world, Gap gap, ServerPlayerEntity player, Vec3d to, float yaw, float pitch, double floor) {
		ModNetworking.broadcast(world, new GapWarpPayload(player.getUuid(), player.getPos(), to, WARP_DELAY));
		WARPS.removeIf(w -> w.player().equals(player.getUuid()));
		WARPS.add(new Warp(gap, player.getUuid(), world.getRegistryKey(), to, yaw, pitch, floor, ticks + WARP_DELAY));
	}

	private static void arrive(MinecraftServer server, Warp warp) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(warp.player());
		ServerWorld world = server.getWorld(warp.world());
		if (player == null || world == null || player.getWorld() != world || !player.isAlive()) {
			return;
		}
		// Off whatever they ride, and out of bed: it is only them that is carried.
		player.stopRiding();
		if (player.isSleeping()) {
			player.wakeUp(true, true);
		}
		player.teleport(world, warp.to().x, warp.to().y, warp.to().z, warp.yaw(), warp.pitch());
		hold(warp.gap(), player, warp.floor());
	}

	/**
	 * Somewhere {@code player} can stand at or near {@code pos}: where it is, if that is still ground with room over it;
	 * else the nearest such place in its column, up or down; else the top of it. Over the hole, its rim.
	 */
	private static Vec3d safe(ServerWorld world, @Nullable Gap gap, Vec3d pos) {
		if (gap != null && horizontal(pos, gap.target) <= gap.radius + 8) {
			return rim(world, gap, pos);
		}
		BlockPos at = BlockPos.ofFloored(pos);
		world.getChunk(at.getX() >> 4, at.getZ() >> 4);
		for (int dy = 0; dy <= 32; dy++) {
			for (int sign : new int[] {1, -1}) {
				BlockPos p = at.up(sign * dy);
				if (standable(world, p)) {
					return new Vec3d(pos.x, p.getY(), pos.z);
				}
				if (dy == 0) {
					break;
				}
			}
		}
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		return new Vec3d(pos.x, Math.max(top, world.getBottomY() + 1), pos.z);
	}

	/** Room for a player at {@code feet}, on something solid, and nothing there that burns. */
	private static boolean standable(ServerWorld world, BlockPos feet) {
		if (feet.getY() <= world.getBottomY() || feet.getY() >= world.getTopY() - 2) {
			return false;
		}
		BlockState below = world.getBlockState(feet.down());
		return world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
				&& world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()
				&& !below.getCollisionShape(world, feet.down()).isEmpty() && !below.isIn(BlockTags.FIRE) && !below.isOf(Blocks.LAVA)
				&& !below.isOf(Blocks.MAGMA_BLOCK) && !below.isOf(Blocks.BARRIER) && world.getFluidState(feet).isEmpty();
	}

	// --- the mirror blocks ------------------------------------------------------------

	/** What a block becomes when it trades places with its twin in the mirror universe, or null if it cannot. */
	@Nullable
	public static BlockState mirrorOf(World world, BlockPos pos, BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty() || state.getHardness(world, pos) < 0.0F
				|| state.isIn(BlockTags.WITHER_IMMUNE)) {
			return null;
		}
		if (state.isIn(BlockTags.LOGS)) {
			BlockState log = ModBlocks.MIRROR_LOG.getDefaultState();
			return state.contains(Properties.AXIS) ? log.with(Properties.AXIS, state.get(Properties.AXIS)) : log;
		}
		if (state.isIn(BlockTags.LEAVES)) {
			return ModBlocks.MIRROR_LEAVES.getDefaultState();
		}
		if (!state.isFullCube(world, pos)) {
			return null;
		}
		if (state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND) || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.SNOW_BLOCK)) {
			return ModBlocks.MIRROR_GRASS.getDefaultState();
		}
		return ModBlocks.MIRROR_STONE.getDefaultState();
	}

	// --- the erasure ---------------------------------------------------------------------

	private static void erase(ServerWorld world, Gap gap) {
		if (gap.terrain && !gap.erased) {
			if (gap.erasure == null) {
				gap.erasure = new Erasure(world, gap.target, gap.radius);
			}
			// Everyone is gone by now, so it goes straight out to the edge, a budget's worth a tick.
			gap.erased = gap.erasure.step(Double.MAX_VALUE);
			if (gap.erased) {
				ShootingStar.LOGGER.info("Ginnungagap #{} erased {} blocks", gap.id, gap.erasure.erased());
			}
		}
		if (gap.age % 2 != 0 || gap.age > GapTimeline.NOTHING + 40) {
			return;
		}
		double reach = Math.min(gap.radius, GapTimeline.eraseReach(gap.age, gap.radius));
		Box box = new Box(gap.target).expand(reach, world.getHeight(), reach);
		for (Entity entity : world.getOtherEntities(null, box, e -> !e.getUuid().equals(gap.shooter)
				&& horizontal(e.getPos(), gap.target) <= reach)) {
			// Players are not erased here: the void takes them, with everyone else, when the black has it all.
			if (!(entity instanceof ServerPlayerEntity)) {
				entity.discard();
			}
		}
	}

	// --- helpers --------------------------------------------------------------------------

	/**
	 * Takes the unseen floor away now the hole can be seen. Anyone still standing on it who is not held on a floor of
	 * their own is first set down on solid ground: on the rim, if they are out over the hole; beside the crack, if they
	 * are over one of the fissures.
	 */
	private static void unlid(ServerWorld world, Gap gap) {
		for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
			if (held(player)) {
				continue;
			}
			if (horizontal(player.getPos(), gap.target) <= gap.radius + 8) {
				toRim(world, gap, player);
				continue;
			}
			BlockPos feet = player.getBlockPos();
			if (onLid(gap, feet)) {
				BlockPos safe = besideLid(world, gap, feet);
				if (safe != null) {
					player.teleport(world, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYaw(), player.getPitch());
					player.fallDistance = 0.0F;
				}
			}
		}
		gap.erasure.unlid();
	}

	private static boolean onLid(Gap gap, BlockPos feet) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = -2; dy <= 0; dy++) {
					if (gap.erasure.lid(feet.add(dx, dy, dz).asLong())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The nearest ground beside the fissure the player is standing over. */
	@Nullable
	private static BlockPos besideLid(ServerWorld world, Gap gap, BlockPos feet) {
		for (int r = 2; r <= 8; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = feet.getX() + dx;
					int z = feet.getZ() + dz;
					int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
					BlockPos ground = new BlockPos(x, y - 1, z);
					if (y > world.getBottomY() && !gap.erasure.lid(ground.asLong()) && !onLid(gap, new BlockPos(x, y, z))) {
						return new BlockPos(x, y, z);
					}
				}
			}
		}
		return null;
	}

	/** Sets the player down outside the hole, on its rim on the side they were on. */
	private static void toRim(ServerWorld world, Gap gap, ServerPlayerEntity player) {
		Vec3d to = rim(world, gap, player.getPos());
		Vec3d middle = Vec3d.ofCenter(gap.target);
		// Turned to face back across the hole, rather than at whatever hillside happens to be in front of them.
		float yaw = (float) (MathHelper.atan2(middle.z - to.z, middle.x - to.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		player.teleport(world, to.x, to.y, to.z, yaw, 10.0F);
		player.fallDistance = 0.0F;
	}

	/** The ground on the rim of the hole on the side {@code from} is on. */
	private static Vec3d rim(ServerWorld world, Gap gap, Vec3d from) {
		Vec3d away = new Vec3d(from.x - gap.target.getX() - 0.5, 0, from.z - gap.target.getZ() - 0.5);
		away = away.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : away.normalize();
		int x = MathHelper.floor(gap.target.getX() + 0.5 + away.x * (gap.radius + GATHER_OUT));
		int z = MathHelper.floor(gap.target.getZ() + 0.5 + away.z * (gap.radius + GATHER_OUT));
		world.getChunk(x >> 4, z >> 4);
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		return new Vec3d(x + 0.5, Math.max(y, world.getBottomY() + 1), z + 0.5);
	}

	private static BlockPos ground(World world, int x, int z) {
		return new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
	}

	private static double horizontal(Vec3d pos, BlockPos target) {
		return Math.hypot(pos.x - target.getX() - 0.5, pos.z - target.getZ() - 0.5);
	}
}
