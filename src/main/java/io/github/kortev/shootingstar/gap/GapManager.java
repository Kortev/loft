package io.github.kortev.shootingstar.gap;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.network.GapEndPayload;
import io.github.kortev.shootingstar.network.GapLockPayload;
import io.github.kortev.shootingstar.network.ModNetworking;
import io.github.kortev.shootingstar.registry.ModBlocks;
import io.github.kortev.shootingstar.registry.ModCriteria;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.strike.Targeting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
 * other universe comes down, and the shooter held safe in the black until they use the key again.
 */
public final class GapManager {
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("shootingstar_gap",
			Comparator.comparingLong(ChunkPos::toLong), GapTimeline.END + 200);
	/**
	 * Only the key lets the world back. But if whoever has it is gone (logged off) this long, it comes back on its own,
	 * so nobody is left in the void for good.
	 */
	private static final int ABSENT_LIMIT = 20 * 60 * 5;

	private static final List<Gap> GAPS = new ArrayList<>();
	private static int nextId = 1;

	private GapManager() {
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
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GAPS.clear());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			for (Gap gap : GAPS) {
				boolean mine = gap.shooter.equals(player.getUuid());
				// Anyone coming into a world that is gone is in the black with everyone else.
				if (gap.dimension == player.getWorld().getRegistryKey() && !gap.released && (gap.age < GapTimeline.END || mine || gap.taken)) {
					ModNetworking.send(player, gap.payload());
				}
			}
		});
		// Nothing touches the shooter while their event plays: they cannot move, and they are the one thing left. Nor
		// anyone, once their world is gone.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayerEntity player
				&& (shielded(player) || gone(player.getWorld()))));
		// And nothing is there to touch: no breaking, placing, using or hitting anything in a world that is gone. Only
		// the key, which brings it back.
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> gone(world) ? ActionResult.FAIL : ActionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> gone(world) && !holdingKey(player, hand) ? ActionResult.FAIL
				: ActionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> gone(world) ? ActionResult.FAIL : ActionResult.PASS);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> gone(world) ? ActionResult.FAIL : ActionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) -> gone(world) && !holdingKey(player, hand)
				? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !gone(world));
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

	private static boolean holdingKey(PlayerEntity player, Hand hand) {
		return player.getStackInHand(hand).isOf(ModItems.GENESIS_KEY);
	}

	public static List<Gap> active() {
		return List.copyOf(GAPS);
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
			// Anyone who walked out over where the hole is, on the ground that was, is set down on its rim before that goes.
			for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
				if (horizontal(player.getPos(), gap.target) <= gap.radius + 8) {
					toRim(world, gap, player);
				}
			}
			// The ground the black was walked on goes; the hole is open.
			gap.erasure.unlid();
		}
		ModNetworking.broadcast(world, new GapEndPayload(gap.id));
		ShootingStar.LOGGER.info("Ginnungagap #{} released", gap.id);
	}

	/** Calls off every event still in its sequence. Returns how many were stopped. */
	public static int cancelAll(MinecraftServer server) {
		int n = 0;
		for (Gap gap : GAPS) {
			if (!gap.released) {
				release(gap, server);
				n++;
			}
		}
		return n;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Gap> it = GAPS.iterator(); it.hasNext(); ) {
			Gap gap = it.next();
			ServerWorld world = server.getWorld(gap.dimension);
			if (world == null || gap.released) {
				it.remove();
				continue;
			}
			gap.age++;
			ServerPlayerEntity shooter = server.getPlayerManager().getPlayer(gap.shooter);
			// Once the black has everything, the world is gone: everyone in it is in nothing until the key is turned again.
			// Whoever was standing where the hole will be is set down on its rim first, under the black, unseen.
			if (gap.age == GapTimeline.NOTHING) {
				gap.taken = true;
				for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
					if (gap.terrain && horizontal(player.getPos(), gap.target) <= gap.radius + 8) {
						toRim(world, gap, player);
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
			// Players are not erased: the void takes them, with everyone else, when the black has it all.
			if (!(entity instanceof ServerPlayerEntity)) {
				entity.discard();
			}
		}
	}

	// --- helpers --------------------------------------------------------------------------

	/** Sets the player down outside the hole, on its rim on the side they were on. */
	private static void toRim(ServerWorld world, Gap gap, ServerPlayerEntity player) {
		Vec3d away = new Vec3d(player.getX() - gap.target.getX() - 0.5, 0, player.getZ() - gap.target.getZ() - 0.5);
		away = away.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : away.normalize();
		int x = MathHelper.floor(gap.target.getX() + 0.5 + away.x * (gap.radius + 10));
		int z = MathHelper.floor(gap.target.getZ() + 0.5 + away.z * (gap.radius + 10));
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y > world.getBottomY()) {
			player.teleport(world, x + 0.5, y, z + 0.5, player.getYaw(), player.getPitch());
			player.fallDistance = 0.0F;
		}
	}

	private static BlockPos ground(World world, int x, int z) {
		return new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
	}

	private static double horizontal(Vec3d pos, BlockPos target) {
		return Math.hypot(pos.x - target.getX() - 0.5, pos.z - target.getZ() - 0.5);
	}
}
