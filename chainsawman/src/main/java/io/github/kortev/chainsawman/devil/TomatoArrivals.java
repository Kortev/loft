package io.github.kortev.chainsawman.devil;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.hotel.HomeBlockEntity;
import io.github.kortev.chainsawman.hotel.HotelBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * How Tomato Devils turn up (like vanilla's phantoms and patrols, a spawner of its own: the Tomato is not in the monsters' spawn list). At
 * night in the overworld, from the world's second night on, with mob spawning on and not in peace, each survival or adventure player under
 * the open sky gets a roll every {@link #ROLL_EVERY} ticks, so that a night brings one about {@link #NIGHT_CHANCE} of the time. After one,
 * everyone within {@link #QUIET_NEAR} blocks of it gets none that night or the next {@link #QUIET_NIGHTS} ({@link #QUIET_UNTIL}, saved on the
 * player). It comes out of the ground {@link #NEAR_MIN}-{@link #NEAR_MAX} blocks off, in front of them where it can
 * ({@link TomatoArrivalEntity}), never within {@link #CLEAR} blocks of another Tomato, its seeds or another arrival (so players together share
 * one), never at someone in a lair, riding, or asleep. Whoever is within {@link #HEAR} blocks hears the music stop and the air drop out.
 */
public final class TomatoArrivals {
	/** The chance that a night brings a player an arrival (if they spend it under the sky). */
	public static final double NIGHT_CHANCE = 0.4;
	/** How often a player is rolled for (ticks). */
	public static final int ROLL_EVERY = 200;
	/** The night by the clock ({@link TomatoDevilEntity#night}): 13000 to 23000. */
	static final int NIGHT_TICKS = 10000;
	/** One roll's chance: {@code 1 - (1 - NIGHT_CHANCE)^(1 / rolls a night)}. */
	public static final double ROLL_CHANCE = 1.0 - Math.pow(1.0 - NIGHT_CHANCE, 1.0 / (NIGHT_TICKS / (double) ROLL_EVERY));
	public static final double NEAR_MIN = 14.0;
	public static final double NEAR_MAX = 18.0;
	public static final double CLEAR = 128.0;
	public static final double HEAR = 32.0;
	/** A sleeper this near the spot stops it. */
	public static final double SLEEPER = 24.0;

	/** After an arrival, how many more nights everyone near it gets none (the night it came is quiet too: at most one a night). */
	public static final int QUIET_NIGHTS = 3;
	/** How near an arrival a player must be to share its quiet spell. */
	public static final double QUIET_NEAR = 128.0;
	/** The last night (the world's day number) of a player's quiet spell: no arrival comes at them up to and including it. Saved on them. */
	public static final AttachmentType<Long> QUIET_UNTIL = AttachmentRegistry.<Long>builder().persistent(Codec.LONG).copyOnDeath()
			.buildAndRegister(ChainsawMan.id("tomato_quiet_until"));

	private TomatoArrivals() {
	}

	/** The night number of the world's clock now (night N runs from dusk of day N to dawn of day N + 1). */
	public static long night(World world) {
		return Math.floorDiv(world.getTimeOfDay() - 13000L, 24000L);
	}

	/** Whether {@code player} is in a quiet spell tonight. */
	public static boolean quiet(ServerPlayerEntity player) {
		Long until = player.getAttached(QUIET_UNTIL);
		return until != null && night(player.getWorld()) <= until;
	}

	static void init() {
		ServerTickEvents.END_WORLD_TICK.register(TomatoArrivals::tick);
	}

	private static void tick(ServerWorld world) {
		if (world.getTime() % ROLL_EVERY != 0 || !nightFor(world)) {
			return;
		}
		Random r = world.getRandom();
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (r.nextDouble() < ROLL_CHANCE) {
				arrive(p);
			}
		}
	}

	/** Whether the world brings arrivals now: the overworld, a night by the clock from the second on, mob spawning on, not in peace. */
	public static boolean nightFor(ServerWorld world) {
		return world.getRegistryKey() == World.OVERWORLD && world.getTimeOfDay() >= 24000L && TomatoDevilEntity.night(world)
				&& world.getDifficulty() != Difficulty.PEACEFUL && world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);
	}

	/** An arrival at {@code player}, if everything allows one now (the roll already won: the tests and the film call it to force one). The arrival begun, or null. */
	@Nullable
	public static TomatoArrivalEntity arrive(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		if (whyNot(player) != null) {
			return null;
		}
		long night = night(world);
		Vec3d spot = spot(world, player);
		if (spot == null || crowded(world, spot)) {
			return null;
		}
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.isSleeping() && p.squaredDistanceTo(spot) < SLEEPER * SLEEPER) {
				return null;
			}
		}
		TomatoArrivalEntity a = Devils.TOMATO_ARRIVAL.create(world);
		if (a == null) {
			return null;
		}
		float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - spot.x), player.getZ() - spot.z));
		a.refreshPositionAndAngles(spot.x, spot.y, spot.z, yaw, 0.0F);
		world.spawnEntity(a);
		for (ServerPlayerEntity p : world.getPlayers()) {
			// Everyone near it gets a quiet spell: tonight and the next QUIET_NIGHTS nights.
			if (p.squaredDistanceTo(spot) < QUIET_NEAR * QUIET_NEAR) {
				p.setAttached(QUIET_UNTIL, night + QUIET_NIGHTS);
			}
			// The music stops and the air drops out, for everyone near.
			if (p.squaredDistanceTo(player) < HEAR * HEAR) {
				p.networkHandler.sendPacket(new StopSoundS2CPacket(null, SoundCategory.MUSIC));
				p.playSoundToPlayer(Devils.Sounds.HUSH, SoundCategory.HOSTILE, 1.0F, 1.0F);
			}
		}
		return a;
	}

	/** Why no arrival can come at {@code player} now (before the spot is looked for), or null if one can (the tests' messages). */
	@Nullable
	public static String whyNot(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		if (!nightFor(world)) {
			return "not a night for it";
		}
		if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
			return "not a living survival player";
		}
		if (player.hasVehicle() || player.isSleeping()) {
			return "riding or asleep";
		}
		if (quiet(player)) {
			return "a quiet spell";
		}
		if (!underSky(world, player.getBlockPos())) {
			return "not under the sky";
		}
		if (inLair(world, player.getBlockPos())) {
			return "in a lair";
		}
		return null;
	}

	/** Why the last spot search for {@code player} would fail, or null (the tests' messages). */
	@Nullable
	public static String whyNoSpot(ServerPlayerEntity player) {
		Vec3d spot = spot(player.getServerWorld(), player);
		if (spot == null) {
			return "no spot";
		}
		return crowded(player.getServerWorld(), spot) ? "crowded at " + spot : null;
	}

	/** Nothing over {@code pos} up to the sky (leaves don't count). */
	public static boolean underSky(World world, BlockPos pos) {
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) <= pos.getY();
	}

	/** Whether {@code pos} is in a lair (a hotel). */
	public static boolean inLair(ServerWorld world, BlockPos pos) {
		for (HomeBlockEntity h : HomeBlockEntity.LOADED) {
			if (h.getWorld() == world && HotelBuilder.box(h.origin, h.rot).contains(pos)) {
				return true;
			}
		}
		return false;
	}

	/** Another Tomato, its seeds or another arrival within {@link #CLEAR} blocks of {@code at}. */
	public static boolean crowded(ServerWorld world, Vec3d at) {
		Box box = new Box(at, at).expand(CLEAR);
		return !world.getEntitiesByClass(Entity.class, box,
				e -> e.isAlive() && (e instanceof TomatoDevilEntity || e instanceof TomatoSeedEntity || e instanceof TomatoArrivalEntity)
						&& e.squaredDistanceTo(at) < CLEAR * CLEAR).isEmpty();
	}

	/**
	 * Where it comes up: {@link #NEAR_MIN}-{@link #NEAR_MAX} blocks from {@code player} on solid ground under the sky, room for the devil, not
	 * far above or below them; in front of them (within 50 degrees of their look) if any such spot is, else any side. Null if none.
	 */
	@Nullable
	public static Vec3d spot(ServerWorld world, ServerPlayerEntity player) {
		Random r = world.getRandom();
		float look = player.getYaw();
		for (int i = 0; i < 48; i++) {
			float yaw = i < 24 ? look + (r.nextFloat() - 0.5F) * 100.0F : r.nextFloat() * 360.0F;
			double d = NEAR_MIN + r.nextDouble() * (NEAR_MAX - NEAR_MIN);
			float rad = yaw * MathHelper.RADIANS_PER_DEGREE;
			int x = MathHelper.floor(player.getX() - MathHelper.sin(rad) * d);
			int z = MathHelper.floor(player.getZ() + MathHelper.cos(rad) * d);
			if (!world.isChunkLoaded(x >> 4, z >> 4)) {
				continue;
			}
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (Math.abs(y - player.getY()) > 8.0) {
				continue;
			}
			BlockPos feet = new BlockPos(x, y, z);
			BlockState under = world.getBlockState(feet.down());
			if (!under.isSolidBlock(world, feet.down()) || !under.getFluidState().isEmpty() || under.isOf(net.minecraft.block.Blocks.BEDROCK)) {
				continue;
			}
			Vec3d at = new Vec3d(x + 0.5, y, z + 0.5);
			Box room = Devils.TOMATO_DEVIL.getDimensions().getBoxAt(at);
			if (!world.isSpaceEmpty(room) || world.containsFluid(room)) {
				continue;
			}
			return at;
		}
		return null;
	}
}
