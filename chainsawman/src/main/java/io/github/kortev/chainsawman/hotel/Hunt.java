package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Finding the Eternity Devil in the walls. Once the loop has closed it hides at a random spot in the corridor's walls (a wall block of the
 * loop floor, the room behind it). It cannot hide perfectly, and it is alive:
 * <ul>
 * <li>its heartbeat, from the spot, louder the nearer you are (a sound at the spot, so the game's own distance does it);</li>
 * <li>eyes open in the wallpaper near it ({@link FleshGrowthEntity#EYE}) and shut when a player looks straight at one;</li>
 * <li>the wall at its spot breathes ({@link FleshGrowthEntity#BREATH}).</li>
 * </ul>
 * Cutting a wall block of the loop floor (any attack on it: a hand, a sword, an axe, a power, the saws) bleeds when the devil is within
 * {@link #BLEED_RANGE} blocks of it, and is a dull knock if not. It flees to another spot when someone lingers by it, and from the first cut
 * at a spot, at most {@link #MAX_MOVES} times in all; after {@link #CUTS_TO_TEAR} bleeding cuts at a spot it has not left it tears out of
 * the wall and the fight starts ({@link Hotel#startFight}), with the health the cuts left it.
 */
public final class Hunt {
	/** How near (blocks) the devil must be behind a cut for it to bleed. */
	public static final double BLEED_RANGE = 2.0;
	/** What a bleeding cut takes off its health, and how many it takes at one spot before it tears out. */
	public static final float BLEED_DAMAGE = 8.0F;
	public static final int CUTS_TO_TEAR = 3;
	/** It moves at most this many times. */
	public static final int MAX_MOVES = 2;
	/** A player this near (blocks) to its spot for {@link #LINGER} ticks makes it move. */
	public static final double CLOSE = 2.2;
	public static final int LINGER = 40;
	/** Eyes open within this distance of the spot, and stay shut this long (ticks) after being looked at. */
	public static final double EYE_RANGE = 5.5;
	public static final int EYE_SHUT = 30;
	/** Looking within this angle (cosine) of an eye shuts it. */
	public static final double LOOK_COS = Math.cos(Math.toRadians(9.0));
	/** The spots: wall blocks of the loop floor in the corridor's window, local. */
	public static final int SPOT_X0 = 15;
	public static final int SPOT_X1 = 23;
	public static final int WALL_Y0 = 29;
	public static final int WALL_Y1 = 31;

	/** The spot: a wall block, local (x, y, z with z 4 or 8). */
	public int sx;
	public int sy;
	public int sz;
	public int moves;
	/** Bleeding cuts at this spot since it came (or last moved). */
	public int cuts;
	/** The devil's health, which the cuts take from (it is a real entity only once it tears out). */
	public float health = EternityDevilEntity.MAX_HEALTH;
	public long lingerTicks;
	public long beatAt;
	public final List<UUID> eyes = new ArrayList<>();
	public final Map<UUID, Long> shutUntil = new HashMap<>();
	public UUID breath;
	/** Blocks turned red by a cut, and when they go back. */
	public final Map<BlockPos, Long> reds = new HashMap<>();
	/** Counters for the tests: how many cuts bled, how many were dull. */
	public int bled;
	public int dull;
	public int beats;

	private Hunt() {
	}

	/** The spot's centre in the world, on the corridor side of the wall. */
	public Vec3d face(HomeBlockEntity h) {
		return HotelBuilder.world(h.origin, h.rot, sx + 0.5, sy + 0.5, sz == 4 ? 5.0 : 8.0);
	}

	/** Where the heart beats: inside the wall, behind the spot. */
	public Vec3d inside(HomeBlockEntity h) {
		return HotelBuilder.world(h.origin, h.rot, sx + 0.5, sy + 0.5, sz == 4 ? 3.9 : 9.1);
	}

	/** The hunt begins: it hides, and the signs start. */
	static void start(ServerWorld world, HomeBlockEntity h, long now) {
		Hotel.Run r = h.run;
		end(world, h);
		Hunt u = new Hunt();
		r.hunt = u;
		int[] spot = pick(world, -100, 0);
		u.sx = spot[0];
		u.sy = spot[1];
		u.sz = spot[2];
		u.beatAt = now - 100;
		// The eyes (shut until the devil is near): a row of them along each wall.
		for (int side = 0; side < 2; side++) {
			for (int k = 0; k < 7; k++) {
				double x = 14.6 + 1.5 * k + world.random.nextDouble() * 0.5;
				double y = 30.0 + world.random.nextInt(2) * 0.9;
				u.eyes.add(grow(world, h, FleshGrowthEntity.EYE, x, y, side == 0, world.random.nextInt()).getUuid());
			}
		}
		u.breath = grow(world, h, FleshGrowthEntity.BREATH, u.sx + 0.5, u.sy + 0.5, u.sz == 4, 7).getUuid();
	}

	private static FleshGrowthEntity grow(ServerWorld world, HomeBlockEntity h, int kind, double x, double y, boolean north, int seed) {
		FleshGrowthEntity e = new FleshGrowthEntity(ChainsawMan.FLESH_GROWTH, world);
		Vec3d at = HotelBuilder.world(h.origin, h.rot, x, y, north ? 5.0 : 8.0);
		e.refreshPositionAndAngles(at.x, at.y, at.z, HotelBuilder.alongYaw(h.rot) + (north ? 90.0F : -90.0F), 0.0F);
		e.set(kind, seed);
		world.spawnEntity(e);
		return e;
	}

	/** A spot at least five blocks from {@code (ox, oz)} along the wall (or on the other wall, three along). */
	private static int[] pick(ServerWorld world, int ox, int oz) {
		for (int tries = 0; tries < 60; tries++) {
			int x = SPOT_X0 + world.random.nextInt(SPOT_X1 - SPOT_X0 + 1);
			int y = WALL_Y0 + world.random.nextInt(WALL_Y1 - WALL_Y0 + 1);
			int z = world.random.nextBoolean() ? 4 : 8;
			boolean far = Math.abs(x - ox) >= 5 || z != oz && Math.abs(x - ox) >= 3;
			if (far && HotelPlan.get().at(x, y, z).isOpaque() && !(HotelPlan.get().at(x, y, z).getBlock() instanceof DoorBlock)) {
				return new int[] {x, y, z};
			}
		}
		return new int[] {SPOT_X0 + 2, 30, 4};
	}

	/** The signs gone and the walls as they were. */
	static void end(ServerWorld world, HomeBlockEntity h) {
		Hunt u = h.run.hunt;
		h.run.hunt = null;
		if (u == null) {
			return;
		}
		for (UUID id : u.eyes) {
			Entity e = world.getEntity(id);
			if (e != null) {
				e.discard();
			}
		}
		if (u.breath != null && world.getEntity(u.breath) != null) {
			world.getEntity(u.breath).discard();
		}
		for (BlockPos p : u.reds.keySet()) {
			restore(world, h, p);
		}
	}

	private static void restore(ServerWorld world, HomeBlockEntity h, BlockPos at) {
		Vec3d lp = HotelBuilder.local(h.origin, h.rot, Vec3d.ofCenter(at));
		BlockState want = HotelPlan.get().at((int) Math.floor(lp.x), (int) Math.floor(lp.y), (int) Math.floor(lp.z)).rotate(HotelBuilder.turn(h.rot));
		if (world.getBlockState(at) != want) {
			world.setBlockState(at, want, net.minecraft.block.Block.NOTIFY_LISTENERS);
		}
	}

	/** One tick of the hunt, with the players inside. */
	static void tick(ServerWorld world, HomeBlockEntity h, List<ServerPlayerEntity> players, long now) {
		Hotel.Run r = h.run;
		Hunt u = r.hunt;
		if (u == null || r.fight) {
			return;
		}
		Vec3d face = u.face(h);
		double nearest = 1.0E9;
		boolean close = false;
		for (ServerPlayerEntity p : players) {
			double d = p.getEyePos().distanceTo(face);
			nearest = Math.min(nearest, d);
			if (Math.hypot(p.getX() - face.x, p.getZ() - face.z) <= CLOSE && Math.abs(p.getY() + 0.9 - face.y) <= 2.5) {
				close = true;
			}
		}
		// Its heartbeat: nearer, faster.
		int gap = nearest < 3.5 ? 16 : nearest < 7.0 ? 22 : 30;
		if (now - u.beatAt >= gap) {
			u.beatAt = now;
			u.beats++;
			Vec3d at = u.inside(h);
			world.playSound(null, at.x, at.y, at.z, ChainsawSounds.HOTEL_HEARTBEAT, SoundCategory.HOSTILE, 3.0F, nearest < 3.5 ? 1.08F : 1.0F);
		}
		// The eyes.
		if (now % 4 == 0) {
			for (UUID id : u.eyes) {
				if (world.getEntity(id) instanceof FleshGrowthEntity eye) {
					boolean open = eye.getPos().distanceTo(face) <= EYE_RANGE;
					Vec3d at = eye.getPos();
					for (ServerPlayerEntity p : players) {
						Vec3d to = at.subtract(p.getEyePos());
						double d = to.length();
						if (d > 0.1 && d < 28.0 && p.getRotationVec(1.0F).dotProduct(to.multiply(1.0 / d)) > LOOK_COS) {
							u.shutUntil.put(id, now + EYE_SHUT);
						}
					}
					if (u.shutUntil.getOrDefault(id, 0L) > now) {
						open = false;
					}
					if (eye.isOpen() != open) {
						eye.setOpen(open);
					}
				}
			}
		}
		// Blood on the wall fades.
		if (!u.reds.isEmpty()) {
			for (java.util.Iterator<Map.Entry<BlockPos, Long>> it = u.reds.entrySet().iterator(); it.hasNext();) {
				Map.Entry<BlockPos, Long> e = it.next();
				if (now >= e.getValue()) {
					restore(world, h, e.getKey());
					it.remove();
				}
			}
		}
		// Someone lingers by it: it slips away.
		u.lingerTicks = close ? u.lingerTicks + 1 : 0;
		if (u.lingerTicks >= LINGER && u.moves < MAX_MOVES) {
			move(world, h, u, now);
		}
	}

	/** It slips to another spot (the signs follow it). */
	static void move(ServerWorld world, HomeBlockEntity h, Hunt u, long now) {
		Vec3d from = u.inside(h);
		int[] spot = pick(world, u.sx, u.sz);
		u.relocate(world, h, spot[0], spot[1], spot[2]);
		u.moves++;
		u.cuts = 0;
		u.lingerTicks = 0;
		u.beatAt = now;
		world.playSound(null, from.x, from.y, from.z, ChainsawSounds.HOTEL_BREATH, SoundCategory.HOSTILE, 1.6F, 0.6F);
	}

	/** It is at this wall block now (local; z 4 or 8): the wall that breathes goes with it. Used when it slips away, and by the filming. */
	public void relocate(ServerWorld world, HomeBlockEntity h, int x, int y, int z) {
		this.sx = x;
		this.sy = y;
		this.sz = z;
		if (breath != null && world.getEntity(breath) instanceof FleshGrowthEntity b) {
			Vec3d at = HotelBuilder.world(h.origin, h.rot, x + 0.5, y + 0.5, z == 4 ? 5.0 : 8.0);
			b.refreshPositionAndAngles(at.x, at.y, at.z, HotelBuilder.alongYaw(h.rot) + (z == 4 ? 90.0F : -90.0F), 0.0F);
		}
	}

	/** The local block a world block of the hotel is, as (x, y, z). */
	private static int[] local(HomeBlockEntity h, BlockPos pos) {
		Vec3d lp = HotelBuilder.local(h.origin, h.rot, Vec3d.ofCenter(pos));
		return new int[] {(int) Math.floor(lp.x), (int) Math.floor(lp.y), (int) Math.floor(lp.z)};
	}

	/**
	 * A wall block of the loop floor is attacked (by {@code by}: a left click, or a power; null for none). Bleeds if the devil is within
	 * {@link #BLEED_RANGE} blocks behind it, a dull knock if not.
	 *
	 * @return whether it was a cut of the corridor's wall
	 */
	public static boolean cut(ServerWorld world, HomeBlockEntity h, BlockPos pos, ServerPlayerEntity by) {
		if (io.github.kortev.chainsawman.form.Fallen.is(by) || io.github.kortev.chainsawman.form.Downed.is(by)) {
			// Someone lying down cuts nothing.
			return false;
		}
		Hotel.Run r = h.run;
		int[] c = local(h, pos);
		BlockState state = world.getBlockState(pos);
		boolean wall = (c[2] == 4 || c[2] == 8) && c[0] >= 1 && c[0] <= HotelPlan.CORR_X1 && c[1] >= WALL_Y0 && c[1] <= WALL_Y1 && !state.isAir()
				&& !(state.getBlock() instanceof DoorBlock);
		if (!wall) {
			return false;
		}
		Hunt u = r.hunt;
		Vec3d at = HotelBuilder.world(h.origin, h.rot, c[0] + 0.5, c[1] + 0.5, c[2] == 4 ? 5.0 : 8.0);
		long now = world.getTime();
		if (u == null || r.fight) {
			dull(world, at, state);
			return true;
		}
		double dx = c[0] - u.sx;
		double dy = c[1] - u.sy;
		boolean near = c[2] == u.sz && Math.sqrt(dx * dx + dy * dy) <= BLEED_RANGE;
		if (!near) {
			u.dull++;
			dull(world, at, state);
			return true;
		}
		u.bled++;
		u.cuts++;
		u.health = Math.max(1.0F, u.health - BLEED_DAMAGE);
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.HOTEL_CUT, SoundCategory.HOSTILE, 1.6F, 0.9F + world.random.nextFloat() * 0.2F);
		world.spawnParticles(new DustParticleEffect(new Vector3f(0.55F, 0.02F, 0.04F), 1.5F), at.x, at.y, at.z, 24, 0.3, 0.3, 0.3, 0.02);
		world.spawnParticles(ParticleTypes.FALLING_LAVA, at.x, at.y + 0.3, at.z, 6, 0.3, 0.1, 0.3, 0.0);
		// The block goes red for a few seconds.
		BlockState red = (c[0] + c[1]) % 2 == 0 ? Blocks.RED_TERRACOTTA.getDefaultState() : Blocks.NETHER_WART_BLOCK.getDefaultState();
		world.setBlockState(pos, red, net.minecraft.block.Block.NOTIFY_LISTENERS);
		u.reds.put(pos.toImmutable(), now + 50);
		if (by != null) {
			by.sendMessage(Text.translatable("message.chainsawman.hotel.bleeds"), true);
		}
		if (u.cuts >= CUTS_TO_TEAR) {
			tearOut(world, h, u, now);
		} else if (u.moves < MAX_MOVES) {
			move(world, h, u, now);
		}
		return true;
	}

	private static void dull(ServerWorld world, Vec3d at, BlockState state) {
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.HOTEL_KNOCK, SoundCategory.BLOCKS, 1.0F, 0.9F + world.random.nextFloat() * 0.2F);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.05);
	}

	/** It tears out of the wall at its spot: the fight starts there. */
	private static void tearOut(ServerWorld world, HomeBlockEntity h, Hunt u, long now) {
		Vec3d at = u.inside(h);
		// It tears out of the wall at its spot, but the devil's own place is the corridor's end, where the arena opens (see Arena).
		double x = EternityDevilEntity.LOCAL_X;
		float health = u.health;
		for (BlockPos p : u.reds.keySet()) {
			restore(world, h, p);
		}
		u.reds.clear();
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.RED_TERRACOTTA.getDefaultState()), at.x, at.y, at.z, 80, 0.8, 0.8, 0.8, 0.2);
		end(world, h);
		Hotel.startFight(world, h, now, x, health);
	}
}
