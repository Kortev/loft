package io.github.kortev.shootingstar.gap;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.item.GenesisKeyItem;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
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
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
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
 * other universe comes down, and everyone held safe in the black until the shooter uses the key again.
 * <p>
 * It plays out for the whole server at once, like a live event: anyone still standing in the zone when the black
 * reaches them is erased with it; then everyone else, in whatever world they were, is carried to the rim of the hole,
 * in a group round the shooter, to wait in the black together on one floor of nothing ({@link VoidFloor}), out of
 * reach of anything left in the world, and to watch it come back; and when it is all back, each is carried home.
 * One at a time: a second key does not turn while one is running.
 * <p>
 * Where everyone came from is saved with the world ({@link GapState}), so whatever happens to the server, nobody is
 * left at the rim: if it goes down in the middle of an event, the hole is finished and everyone sent home at once, or,
 * after a crash, as soon as it is back.
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
	/**
	 * Where everyone is gathered: the shooter at the front, past the edge of the hole on their side, and everyone else in
	 * rows beside and behind them, a few blocks apart.
	 */
	private static final double GATHER_OUT = 10.0;
	private static final double GATHER_APART = 3.0;
	private static final double GATHER_BACK = 3.0;
	private static final int PER_ROW = 9;

	private static final List<Gap> GAPS = new ArrayList<>();
	/**
	 * Released events whose world is coming back: their unseen floor stays until everyone's rebuild has shown the hole
	 * (GapTimeline.REBUILD_DONE), so nobody walks into it before they can see it; and everyone gathered for them is
	 * carried home when the rebuild is over (GapTimeline.REBUILD_END).
	 */
	private static final List<Gap> RETURNING = new ArrayList<>();
	/** Players about to be carried somewhere, once the light has held them for a moment. */
	private static final List<Warp> WARPS = new ArrayList<>();
	@Nullable
	private static GapState state;
	private static int nextId = 1;
	private static long ticks;

	private GapManager() {
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
		/** The world's spawn has been seen to (moved out of the hole if it was in it). */
		boolean spawnMoved;
		/** Everyone walking on the floor of nothing for it. */
		final Set<UUID> floors = new HashSet<>();
		/** Everyone the black has erased (each only once, so whoever comes back into the zone is not erased again). */
		final Set<UUID> erasedPlayers = new HashSet<>();
		/** Which way round the hole the shooter is (radians); how high everyone's floor is; who stands where. */
		double side;
		double floor = Double.NaN;
		final Map<UUID, Integer> places = new HashMap<>();

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

		GapState.Event record() {
			return new GapState.Event(dimension, target, radius, terrain, shooter, taken);
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GapManager::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(GapManager::recover);
		ServerLifecycleEvents.SERVER_STOPPING.register(GapManager::endNow);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RETURNING.clear();
			GAPS.clear();
			WARPS.clear();
			state = null;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			introduce(player);
			mendKey(player);
			Gap gap = holding();
			if (gap != null) {
				// In while it is still going on: gathered with everyone (home is still where they first came from).
				gather(gap, player);
			} else {
				GapState.Home home = state(server).homes.remove(player.getUuid());
				if (home != null) {
					// Back after it is all over (or after the server went down in the middle of it): home.
					state(server).markDirty();
					ServerWorld world = server.getWorld(home.world());
					if (world != null) {
						Vec3d to = safe(world, null, home.pos());
						player.teleport(world, to.x, to.y, to.z, home.yaw(), home.pitch());
						player.fallDistance = 0.0F;
					}
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			for (Gap gap : all()) {
				gap.floors.remove(handler.getPlayer().getUuid());
			}
		});
		// Carried into another world (to the rim, or home): told about any event going on there.
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> introduce(player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			Gap gap = holding();
			if (gap != null && !alive) {
				// Erased, and back: straight back to the rest of them at the rim, and afterwards home to where they came back.
				gap.floors.remove(newPlayer.getUuid());
				gather(gap, newPlayer);
			} else if (!alive) {
				// Never over a bottomless hole, whatever spawn point was in it.
				overGround(newPlayer);
			}
		});
		// Someone whose rebuild is over sooner (they hurried it) can go home now.
		ServerPlayNetworking.registerGlobalReceiver(GapSettlePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			for (Gap gap : List.copyOf(RETURNING)) {
				if (gap.floors.contains(player.getUuid())) {
					sendHome(gap, player);
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

	private static GapState state(MinecraftServer server) {
		if (state == null) {
			state = GapState.get(server);
		}
		return state;
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
		// Remembered with the world, so a hole cut short by a crash is finished when the server comes back.
		state(world.getServer()).event = gap.record();
		state(world.getServer()).markDirty();
		ModNetworking.broadcast(world, gap.payload());
		ShootingStar.LOGGER.info("Ginnungagap #{} on {} in {}", gap.id, target.toShortString(), world.getRegistryKey().getValue());
		ModCriteria.fire(shooter, ModCriteria.GAP_OPEN);
		return gap;
	}

	/** True while an event is going on anywhere on the server, from the key turning until everyone is home. */
	public static boolean running() {
		return !GAPS.isEmpty() || !RETURNING.isEmpty();
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
			if (gap.floors.contains(player.getUuid())) {
				return true;
			}
		}
		for (Gap gap : RETURNING) {
			if (gap.floors.contains(player.getUuid())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The event whose world is gone, everyone on the server being held in the black for it; or null. (Anyone who comes in
	 * once it is coming back is simply left where they are, or sent home.)
	 */
	@Nullable
	private static Gap holding() {
		for (Gap gap : GAPS) {
			if (gap.taken && !gap.released) {
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

	/** Lets reality back in, by command or with the key gone too long: the shooter's cracked key is put right. */
	public static void release(Gap gap, MinecraftServer server) {
		release(gap, server, false);
	}

	/**
	 * Lets reality back in: the world is there again, and everyone in it is told, so they see it rebuilt. {@code byKey}:
	 * the cracked key was turned for it (and shatters itself); otherwise it is put right here.
	 */
	public static void release(Gap gap, MinecraftServer server, boolean byKey) {
		if (gap.released) {
			return;
		}
		gap.released = true;
		if (!byKey) {
			mendKey(server, gap.shooter, gap.taken);
		}
		ServerWorld world = server.getWorld(gap.dimension);
		if (world == null) {
			return;
		}
		if (gap.terrain && gap.erasure != null) {
			// Let back in early (by command, or the key gone too long): the hole is finished first, all at once (the light
			// settles over the next few ticks, and its chunks are sent then).
			gap.erasure.finishBlocks();
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
		for (Warp warp : List.copyOf(WARPS)) {
			if (ticks >= warp.at()) {
				WARPS.remove(warp);
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
			if (gap.erasure != null && !gap.erasure.done()) {
				// Released early: the light still to settle and the hole to be sent.
				gap.erasure.step(Double.MAX_VALUE);
			}
			if (gap.age == GapTimeline.REBUILD_DONE + 40 && gap.erasure != null) {
				unlid(world, gap);
			}
			if (gap.age == GapTimeline.REBUILD_END) {
				// It is all back: everyone still at the rim is carried home.
				for (ServerPlayerEntity player : List.copyOf(server.getPlayerManager().getPlayerList())) {
					if (gap.floors.contains(player.getUuid())) {
						sendHome(gap, player);
					}
				}
			}
			// Kept a moment longer, for the last of them to land.
			if (gap.age >= GapTimeline.REBUILD_END + WARP_DELAY + 4) {
				gap.floors.clear();
				state(server).event = null;
				state(server).markDirty();
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
			// Two deaths, for anyone but the shooter: the universe in the block bursting out of the ground swallows whoever
			// it reaches as it swells to its full size; then, once it has fallen back in on itself, the black coming out over
			// the zone erases whoever is left standing in it.
			if (world.getGameRules().getBoolean(ModGameRules.GAP_LETHAL)) {
				if (gap.age >= GapTimeline.CONTACT && gap.age <= GapTimeline.COLLAPSE) {
					swallow(world, gap, shooter);
				}
				if (gap.age >= GapTimeline.ERASURE && gap.age < GapTimeline.NOTHING && gap.terrain) {
					eraseIn(world, gap, shooter);
				}
			}
			// Once the black has everything, the world is gone: everyone on the server, wherever they are, is carried to the
			// rim of the hole, round the shooter, under the black, to be in it together until the key is turned again.
			if (gap.age == GapTimeline.NOTHING) {
				take(server, world, gap, shooter);
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

	/** The black has it all: the world is gone, and everyone on the server is gathered at the rim on one floor. */
	private static void take(MinecraftServer server, ServerWorld world, Gap gap, @Nullable ServerPlayerEntity shooter) {
		gap.taken = true;
		state(server).event = gap.record();
		state(server).markDirty();
		Vec3d from = shooter != null && shooter.getWorld() == world ? shooter.getPos() : Vec3d.ofCenter(gap.target).add(1, 0, 0);
		gap.side = Math.atan2(from.z - gap.target.getZ() - 0.5, from.x - gap.target.getX() - 0.5);
		// One floor for everyone: level with the roots of the tree that will grow out of the hole (its long root runs out
		// to the shooter's feet on it), or the ground where the shooter will stand, if that is higher.
		Vec3d front = place(world, gap, 0);
		gap.floor = Math.max(gap.target.getY() + 2.0, groundAround(world, MathHelper.floor(front.x), MathHelper.floor(front.z)));
		if (shooter != null) {
			gather(gap, shooter);
		}
		for (ServerPlayerEntity player : List.copyOf(server.getPlayerManager().getPlayerList())) {
			if (player.isAlive() && !player.isSpectator() && !gap.places.containsKey(player.getUuid())) {
				gather(gap, player);
			}
			ModCriteria.fire(player, ModCriteria.GAP_VOID);
		}
	}

	/** Sends {@code player} the events going on in the world they are in now (on joining, or arriving from another). */
	private static void introduce(ServerPlayerEntity player) {
		for (Gap gap : GAPS) {
			boolean mine = gap.shooter.equals(player.getUuid());
			// Anyone coming into a world that is gone is in the black with everyone else.
			if (gap.dimension == player.getWorld().getRegistryKey() && !gap.released && (gap.age < GapTimeline.END || mine || gap.taken)) {
				ModNetworking.send(player, gap.payload());
			}
		}
	}

	// --- the people -------------------------------------------------------------------------

	/**
	 * Everyone (but the shooter, and anyone in creative or spectator) the universe bursting out of the block reaches as it
	 * swells is swallowed by it: the cube of it, a quarter sunk in the ground, as big as it is now; and, as it stops
	 * swelling and starts to fall back in, as big as it ever got.
	 */
	private static void swallow(ServerWorld world, Gap gap, @Nullable ServerPlayerEntity shooter) {
		double half = gap.age >= GapTimeline.COLLAPSE ? GapTimeline.blastHalf(gap.radius) : GapTimeline.burstHalf(gap.radius, gap.age);
		Vec3d middle = new Vec3d(gap.target.getX() + 0.5, gap.target.getY() + 1.0 + half * 0.25, gap.target.getZ() + 0.5);
		Box cube = new Box(middle.subtract(half, half, half), middle.add(half, half, half));
		for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
			if (spared(gap, player) || !player.getBoundingBox().intersects(cube)) {
				continue;
			}
			gap.erasedPlayers.add(player.getUuid());
			boolean by = shooter != null && player.shouldDamagePlayer(shooter);
			player.damage(ModDamageTypes.swallowed(world, by ? shooter : null), Float.MAX_VALUE);
			ShootingStar.LOGGER.info("Ginnungagap #{}: {} was swallowed", gap.id, player.getName().getString());
		}
	}

	/** Not killed by it: the shooter, anyone in creative or spectator, anyone already dead or killed by it once. */
	private static boolean spared(Gap gap, ServerPlayerEntity player) {
		return player.getUuid().equals(gap.shooter) || player.isCreative() || player.isSpectator() || !player.isAlive()
				|| gap.erasedPlayers.contains(player.getUuid());
	}

	/**
	 * Everyone (but the shooter, and anyone who cannot be hurt) still in the zone when the black reaches where they
	 * stand is erased with it: they see it coming, and then they are gone, whatever they carried with them. A further
	 * reach than the burst's: the whole of the hole.
	 */
	private static void eraseIn(ServerWorld world, Gap gap, @Nullable ServerPlayerEntity shooter) {
		double front = GapTimeline.eraseFront(gap.age);
		for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
			if (spared(gap, player) || horizontal(player.getPos(), gap.target) > gap.radius + 2) {
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
	 * Takes {@code player}, wherever they are, to the rim of the hole with everyone else: held where they stand for a
	 * moment, then carried to their place in the group round the shooter, facing the hole, on everyone's floor. Where they
	 * came from is remembered, the first time, to send them back to.
	 */
	private static void gather(Gap gap, ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		ServerWorld world = server.getWorld(gap.dimension);
		if (world == null || Double.isNaN(gap.floor)) {
			return;
		}
		UUID id = player.getUuid();
		GapState saved = state(server);
		if (!saved.homes.containsKey(id)) {
			saved.homes.put(id, new GapState.Home(player.getWorld().getRegistryKey(), player.getPos(), player.getYaw(), player.getPitch()));
			saved.markDirty();
		}
		Integer index = gap.places.get(id);
		if (index == null) {
			index = gap.places.size();
			gap.places.put(id, index);
		}
		hold(gap, player, player.getY());
		Vec3d place = place(world, gap, index);
		Vec3d middle = Vec3d.ofCenter(gap.target);
		float yaw = (float) (MathHelper.atan2(middle.z - place.z, middle.x - place.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		warp(gap, player, world, place, yaw, 10.0F, gap.floor);
	}

	/**
	 * Sends {@code player} home from the rim, to somewhere safe near where they came from, in whatever world that was;
	 * or, if they are about there already and on the ground, simply lets them go where they stand.
	 */
	private static void sendHome(Gap gap, ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		GapState saved = state(server);
		GapState.Home home = saved.homes.remove(player.getUuid());
		saved.markDirty();
		ServerWorld to = home == null ? null : server.getWorld(home.world());
		if (to == null) {
			// Nowhere to go back to: the nearest safe ground to where they are.
			to = player.getServerWorld();
			warp(gap, player, to, safe(to, gap, player.getPos()), player.getYaw(), player.getPitch(), Double.NaN);
			return;
		}
		Vec3d spot = safe(to, to.getRegistryKey() == gap.dimension ? gap : null, home.pos());
		Vec3d now = player.getPos();
		if (to == player.getWorld() && Math.hypot(spot.x - now.x, spot.z - now.z) < 8.0 && Math.abs(spot.y - now.y) < 4.0
				&& standable(to, player.getBlockPos())) {
			hold(gap, player, Double.NaN);
			return;
		}
		warp(gap, player, to, spot, home.yaw(), home.pitch(), Double.NaN);
	}

	/** Puts {@code player} on a floor of nothing {@code floor} high (NaN: off it), here and on their client. */
	private static void hold(Gap gap, ServerPlayerEntity player, double floor) {
		if (Double.isNaN(floor)) {
			gap.floors.remove(player.getUuid());
		} else {
			gap.floors.add(player.getUuid());
		}
		player.fallDistance = 0.0F;
		ModNetworking.send(player, new GapFloorPayload(floor));
	}

	/**
	 * The {@code index}th place in the gathering: the shooter's own first, at the front on their side of the hole; then
	 * out to either side of them in turn, a few blocks apart, a row at a time, each row a step further back from the edge
	 * and set between the places of the one in front. All on everyone's floor.
	 */
	private static Vec3d place(ServerWorld world, Gap gap, int index) {
		int row = index / PER_ROW;
		int slot = index % PER_ROW;
		double ring = gap.radius + GATHER_OUT + row * GATHER_BACK;
		double along = ((slot + 1) / 2) * (slot % 2 == 1 ? 1 : -1) + (row % 2 == 1 ? 0.5 : 0.0);
		double a = gap.side + along * GATHER_APART / ring;
		int x = MathHelper.floor(gap.target.getX() + 0.5 + Math.cos(a) * ring);
		int z = MathHelper.floor(gap.target.getZ() + 0.5 + Math.sin(a) * ring);
		world.getChunk(x >> 4, z >> 4);
		double y = Double.isNaN(gap.floor) ? world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) : gap.floor;
		return new Vec3d(x + 0.5, y, z + 0.5);
	}

	/** The ground round (x, z): the middle of the heights within a few blocks, so a lone spike or tree does not count. */
	private static double groundAround(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		int[] heights = new int[25];
		int n = 0;
		for (int dx = -4; dx <= 4; dx += 2) {
			for (int dz = -4; dz <= 4; dz += 2) {
				heights[n++] = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
			}
		}
		Arrays.sort(heights);
		return Math.max(heights[12], world.getBottomY() + 1);
	}

	/**
	 * Carries {@code player} to {@code to} in {@code world}: everyone where they are is told now, so they see them held
	 * in the light and go (over the sky, if it is in the same world), and, if they are going to another world, everyone
	 * there sees them arrive. The move is made a moment later. Their floor of nothing is then {@code floor} high (NaN: none).
	 */
	private static void warp(Gap gap, ServerPlayerEntity player, ServerWorld world, Vec3d to, float yaw, float pitch, double floor) {
		ServerWorld from = player.getServerWorld();
		boolean across = from != world;
		ModNetworking.broadcast(from, new GapWarpPayload(player.getUuid(), player.getPos(), across ? player.getPos() : to, WARP_DELAY));
		if (across) {
			ModNetworking.broadcast(world, new GapWarpPayload(player.getUuid(), to, to, WARP_DELAY));
		}
		WARPS.removeIf(w -> w.player().equals(player.getUuid()));
		WARPS.add(new Warp(gap, player.getUuid(), world.getRegistryKey(), to, yaw, pitch, floor, ticks + WARP_DELAY));
	}

	private static void arrive(MinecraftServer server, Warp warp) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(warp.player());
		ServerWorld world = server.getWorld(warp.world());
		if (player == null || world == null || !player.isAlive()) {
			return;
		}
		// Off whatever they ride, and out of bed: it is only them that is carried.
		player.stopRiding();
		if (player.isSleeping()) {
			player.wakeUp(true, true);
		}
		// Their floor first, so they are on it (or off it) the moment they land, rather than for a moment on the one they
		// left, at whatever height that was.
		hold(warp.gap(), player, warp.floor());
		player.teleport(world, warp.to().x, warp.to().y, warp.to().z, warp.yaw(), warp.pitch());
		player.fallDistance = 0.0F;
	}

	/**
	 * Somewhere {@code player} can stand at or near {@code pos}, always on solid ground with room over it and nothing
	 * that burns or drowns: over the hole, its rim; else the nearest such place in the column, up or down; else the
	 * nearest ground round about (an old hole, a lake of lava, the sky over the void); else the world's spawn.
	 */
	private static Vec3d safe(ServerWorld world, @Nullable Gap gap, Vec3d pos) {
		if (gap != null && horizontal(pos, gap.target) <= gap.radius + 8) {
			pos = rim(world, gap, pos);
		}
		BlockPos ground = groundNear(world, BlockPos.ofFloored(pos));
		if (ground == null) {
			ground = groundNear(world, world.getSpawnPos());
		}
		return ground == null ? Vec3d.ofBottomCenter(world.getSpawnPos()) : Vec3d.ofBottomCenter(ground);
	}

	/**
	 * The nearest place to stand round {@code at}: in its own column first, near its height, then further and further
	 * out, a couple of hundred blocks at most. In each column, near the height asked for, then (where the sky is open)
	 * on top of it.
	 */
	@Nullable
	private static BlockPos groundNear(ServerWorld world, BlockPos at) {
		BlockPos here = standableIn(world, at.getX(), at.getZ(), at.getY());
		if (here != null) {
			return here;
		}
		for (int r = 3; r <= 256; r += r < 24 ? 3 : 8) {
			int around = Math.max(8, Math.min(48, r * 2));
			for (int i = 0; i < around; i++) {
				double a = i * Math.PI * 2.0 / around;
				BlockPos found = standableIn(world, at.getX() + MathHelper.floor(Math.cos(a) * r), at.getZ() + MathHelper.floor(Math.sin(a) * r),
						at.getY());
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** A place to stand in the column (x, z): within a few dozen blocks of {@code y}, nearest first; else on top of it. */
	@Nullable
	private static BlockPos standableIn(ServerWorld world, int x, int z, int y) {
		world.getChunk(x >> 4, z >> 4);
		for (int dy = 0; dy <= 32; dy++) {
			for (int sign : dy == 0 ? new int[] {1} : new int[] {1, -1}) {
				BlockPos p = new BlockPos(x, y + sign * dy, z);
				if (standable(world, p)) {
					return p;
				}
			}
		}
		if (!world.getDimension().hasCeiling()) {
			BlockPos top = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (standable(world, top)) {
				return top;
			}
		}
		return null;
	}

	/** Someone who has just respawned over nothing (a spawn point in a hole) is set down on the nearest ground instead. */
	private static void overGround(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		if (world.getDimension().hasCeiling()) {
			return;
		}
		BlockPos at = player.getBlockPos();
		if (world.getTopY(Heightmap.Type.MOTION_BLOCKING, at.getX(), at.getZ()) > world.getBottomY()) {
			return;
		}
		BlockPos ground = groundNear(world, at.withY(world.getSeaLevel()));
		if (ground != null) {
			player.teleport(world, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, player.getYaw(), player.getPitch());
			player.fallDistance = 0.0F;
		}
	}

	/** Room for a player at {@code feet}, on something solid, and nothing there that burns. */
	private static boolean standable(ServerWorld world, BlockPos feet) {
		if (feet.getY() <= world.getBottomY() || feet.getY() >= world.getTopY() - 2) {
			return false;
		}
		BlockState below = world.getBlockState(feet.down());
		BlockState in = world.getBlockState(feet);
		return in.getCollisionShape(world, feet).isEmpty() && world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()
				&& !below.getCollisionShape(world, feet.down()).isEmpty() && !below.isIn(BlockTags.FIRE) && !below.isIn(BlockTags.CAMPFIRES)
				&& !below.isOf(Blocks.LAVA) && !below.isOf(Blocks.MAGMA_BLOCK) && !below.isOf(Blocks.CACTUS) && !below.isOf(Blocks.BARRIER)
				&& !in.isIn(BlockTags.FIRE) && !in.isOf(Blocks.COBWEB) && !in.isOf(Blocks.SWEET_BERRY_BUSH) && !in.isOf(Blocks.POWDER_SNOW)
				&& world.getFluidState(feet).isEmpty() && world.getFluidState(feet.up()).isEmpty();
	}

	// --- the key -------------------------------------------------------------------------

	/**
	 * Puts right the cracked key of an event that did not end with it: shattered if the world was taken (it is back
	 * without it), whole again if the world never was. Now if they are here, or when they are next seen.
	 */
	private static void mendKey(MinecraftServer server, UUID shooter, boolean shatter) {
		if (shooter.equals(Util.NIL_UUID)) {
			return;
		}
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(shooter);
		if (player == null) {
			state(server).keys.put(shooter, shatter);
			state(server).markDirty();
			return;
		}
		mendKey(player, shatter);
	}

	/** Puts right this player's cracked key, if one is owed them. */
	private static void mendKey(ServerPlayerEntity player) {
		GapState saved = state(player.getServer());
		Boolean shatter = saved.keys.remove(player.getUuid());
		if (shatter != null) {
			saved.markDirty();
			mendKey(player, shatter);
		}
	}

	private static void mendKey(ServerPlayerEntity player, boolean shatter) {
		PlayerInventory inventory = player.getInventory();
		boolean any = false;
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.isOf(ModItems.GENESIS_KEY) && GenesisKeyItem.cracked(stack)) {
				any = true;
				if (shatter) {
					inventory.setStack(i, ItemStack.EMPTY);
				} else {
					GenesisKeyItem.mend(stack);
				}
			}
		}
		if (any) {
			player.sendMessage(Text.translatable(shatter ? "message.shootingstar.gap.shattered" : "message.shootingstar.gap.mended")
					.formatted(Formatting.AQUA), false);
		}
	}

	// --- the server going down and coming back ---------------------------------------------

	/**
	 * Ends any event now, as the server does when it goes down in the middle of one, before anything is saved. The hole is
	 * finished (its light worked out afresh the next time it is loaded), the floor over it taken away, everyone held sent
	 * straight home and the shooter's key put right.
	 */
	public static void endNow(MinecraftServer server) {
		for (Gap gap : all()) {
			ServerWorld world = server.getWorld(gap.dimension);
			if (world != null && gap.taken && gap.terrain) {
				if (gap.erasure == null) {
					gap.erasure = new Erasure(world, gap.target, gap.radius, false);
				}
				if (!gap.erasure.done()) {
					gap.erasure.finishOffline();
					moveSpawn(server, world, gap.target, gap.radius);
				}
				gap.erasure.unlid();
			}
			if (!gap.released) {
				mendKey(server, gap.shooter, gap.taken);
			}
			gap.floors.clear();
		}
		WARPS.clear();
		GapState saved = state(server);
		for (ServerPlayerEntity player : List.copyOf(server.getPlayerManager().getPlayerList())) {
			GapState.Home home = saved.homes.remove(player.getUuid());
			ServerWorld world = home == null ? null : server.getWorld(home.world());
			if (world != null) {
				Vec3d to = safe(world, null, home.pos());
				player.teleport(world, to.x, to.y, to.z, home.yaw(), home.pitch());
				player.fallDistance = 0.0F;
			}
		}
		saved.event = null;
		saved.markDirty();
		GAPS.clear();
		RETURNING.clear();
	}

	/**
	 * The server is back after going down without warning in the middle of an event: its hole, cut short, is finished,
	 * the floor it laid taken out with it, and the shooter's key put right when they are next seen. Everyone it gathered
	 * is sent home when they come back (JOIN).
	 */
	private static void recover(MinecraftServer server) {
		state = GapState.get(server);
		GapState.Event event = state.event;
		if (event == null) {
			return;
		}
		ServerWorld world = server.getWorld(event.dimension());
		if (world != null && event.taken() && event.terrain()) {
			Erasure erasure = new Erasure(world, event.target(), event.radius(), true);
			erasure.finishOffline();
			moveSpawn(server, world, event.target(), event.radius());
			ShootingStar.LOGGER.info("A Ginnungagap event was cut short: its hole at {} is finished ({} blocks)", event.target().toShortString(),
					erasure.erased());
		}
		if (!event.shooter().equals(Util.NIL_UUID)) {
			state.keys.put(event.shooter(), event.taken());
		}
		state.event = null;
		state.markDirty();
	}

	/**
	 * If the world's spawn was in the hole, it is moved out to the rim, on its side, before anyone can come back into the
	 * world over nothing; and anyone here whose own spawn point was in it goes back to the world's.
	 */
	private static void moveSpawn(MinecraftServer server, ServerWorld world, BlockPos target, int radius) {
		Gap at = new Gap(0, world.getRegistryKey(), target, Util.NIL_UUID, radius, true, target, false);
		if (world == server.getOverworld() && horizontal(Vec3d.ofCenter(world.getSpawnPos()), target) <= radius + 8) {
			Vec3d rim = rim(world, at, Vec3d.ofCenter(world.getSpawnPos()));
			world.setSpawnPos(BlockPos.ofFloored(rim), world.getSpawnAngle());
			ShootingStar.LOGGER.info("The world's spawn was in the Ginnungagap's hole: moved to its rim at {}", BlockPos.ofFloored(rim).toShortString());
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			BlockPos spawn = player.getSpawnPointPosition();
			if (spawn != null && player.getSpawnPointDimension() == world.getRegistryKey()
					&& horizontal(Vec3d.ofCenter(spawn), target) <= radius + 8) {
				player.setSpawnPoint(World.OVERWORLD, null, 0.0F, false, false);
			}
		}
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
				gap.erasure = new Erasure(world, gap.target, gap.radius, false);
			}
			// Everyone is gone by now, so it goes straight out to the edge, a budget's worth a tick.
			gap.erased = gap.erasure.step(Double.MAX_VALUE);
			if (!gap.spawnMoved && gap.erasure.carved()) {
				// As soon as there is nothing under it: nobody comes back into the world over the hole.
				gap.spawnMoved = true;
				moveSpawn(world.getServer(), world, gap.target, gap.radius);
			}
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

	/** Sets the player down outside the hole, on its rim on the side they were on (on ground, not over a fissure). */
	private static void toRim(ServerWorld world, Gap gap, ServerPlayerEntity player) {
		Vec3d to = safe(world, gap, player.getPos());
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
