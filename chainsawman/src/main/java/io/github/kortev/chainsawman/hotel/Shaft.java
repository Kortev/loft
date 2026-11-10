package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.network.HotelPayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

/**
 * The shaft: when the Eternity Devil's fear is full (it grows with the players' own: time, and every time one is hurt or downed), or it has
 * been cut far enough, the floor turns 90 degrees. The corridor of floor 8, stood on its end, is built as real blocks in a reserved space above
 * the hotel's roof (the carpet one wall, the lamps' ceiling the opposite, the doors' walls the other two, the doors opened into alcoves),
 * {@link #DEPTH} deep, and everyone falls down it. Its bottom is the devil's mouth (lips and teeth in a floor of fused arms), and through the
 * mouth they drop, unhurt, into the devil itself: a small arena of flesh ({@link #AX0} to {@link #AX1} by {@link #AZ0} to {@link #AZ1},
 * {@link #HIGH} high, lit from inside the flesh and by the mouth overhead). Every block it takes is kept (in the home's own data,
 * {@link HomeBlockEntity#shaft}) and put back exactly when the fight is over, like {@link Arena}.
 *
 * <p>The rule of the arena: <b>no damage without a tell</b>. Every attack shows itself at least {@link #MIN_TELL} ticks before it lands, starts
 * at least 4 blocks from whoever it is aimed at, and can be stepped out of; a miss leaves the part that missed lying stunned and worth half as
 * much again to cut. The attacks: an arm slammed down a lane ({@link ShaftArmEntity#slam}), a head lunging ({@link ShaftHeadEntity}), half the
 * room clenching shut ({@link #squeeze}), a hand sweeping the floor that holds whoever it catches until the arm is cut
 * ({@link ShaftArmEntity#grab}), and flesh creeping on the floor ({@link FleshPatchEntity}). The walls and the growths can be cut at any time
 * ({@link #cutBlock}): there is always something to hit. It can't be killed here (its heart is not on this floor): the pain bar fills until it
 * gives up ({@link EternityDevilEntity#arenaPain}).
 *
 * <p>Local coordinates are the hotel's own (x along the corridor, z across, y up); the shaft is a column over the corridor's middle, so
 * a corridor's x is a height: x 36 (its far end, the devil's) the bottom.
 */
public final class Shaft {
	/**
	 * The arena's walking level (its floor's top blocks are at FLOOR - 1), above the hotel's roof (its floor's lowest blocks clear of the torn
	 * sky arena's shell, which is put back as the floor turns), and how high it is inside.
	 */
	public static final int FLOOR = HotelPlan.SY + 6;
	public static final int HIGH = 7;
	/** The arena's inside: 17 x 12, under the mouth. */
	public static final int AX0 = 11;
	public static final int AX1 = 27;
	public static final int AZ0 = 1;
	public static final int AZ1 = 12;
	/** Its middle (under the mouth), where the core is pushed up at the end. */
	public static final double MID_X = 19.5;
	public static final double MID_Z = 6.5;
	/** The mouth's rim: the top of the floor of fused arms the mouth is in (the shaft's foot). */
	public static final int BASE = FLOOR + HIGH + 3;
	/** The shaft's height, as long as the corridor: the corridor's cell x is at BASE + DEPTH - x. */
	public static final int DEPTH = 36;
	/** The highest local y anyone may be at in it. */
	public static final int TOP = BASE + DEPTH + 3;
	/** The shaft's inside (3 x 3, straight over the mouth). */
	public static final int IX0 = 18;
	public static final int IX1 = 20;
	public static final int IZ0 = 5;
	public static final int IZ1 = 7;
	/** The mouth's hollow round the shaft's foot: inside x 16-22, z 3-9, y BASE to BASE + 4; the mouth itself is the shaft's own 3 x 3 in its floor. */
	public static final int CX0 = 16;
	public static final int CX1 = 22;
	public static final int CZ0 = 3;
	public static final int CZ1 = 9;
	/** Where everyone is put as the floor turns: this far above the mouth, falling. */
	public static final int FALL_FROM = 24;
	/** Where the devil itself is (local): sunk in the arena's floor, out of reach. What you cut is its flesh: the walls, the growths, its parts. */
	public static final double DEVIL_Y = FLOOR - 3.0;
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

	// ---- The numbers.
	/** Fear (0 to 1): fills with time ({@link #FEAR_TICK}) and each time a player is hurt ({@link #FEAR_HURT}) or downed ({@link #FEAR_DOWN}); the floor turns at 1, no sooner than {@link #TILT_MIN} ticks into the fight. */
	public static final float FEAR_TICK = 1.0F / 1200.0F;
	public static final float FEAR_HURT = 0.06F;
	public static final float FEAR_DOWN = 0.2F;
	public static final int TILT_MIN = 800;
	/**
	 * Pain turns the floor too: cut it this far ({@link EternityDevilEntity#pain}) in the corridor, no sooner than {@link #PAIN_TILT_MIN} ticks
	 * into the fight, and it turns the floor. The corridor is the short opening; the arena holds the rest of the bar.
	 */
	public static final float PAIN_TILT = 0.4F;
	public static final int PAIN_TILT_MIN = 160;
	/** The turn takes this long (ticks) from the camera starting to roll to the players being in the shaft. */
	public static final int TILT_TICKS = 44;
	/**
	 * How much more the arena's share of the bar takes than the corridor's (the same cut moves the bar this many times less once you are inside
	 * it): what the arena's 60% of the bar needs, raised so that the saws take minutes, not seconds. Blows are not weakened, the bar is longer.
	 */
	public static final float ARENA_SCALE = 15.0F;
	/** The shortest tell any attack has (ticks): nothing lands sooner than this after it shows. */
	public static final int MIN_TELL = 12;
	/** A part that has just missed, and the walls a squeeze leaves soft: cuts there are worth this much more. */
	public static final float MISS_BONUS = 1.5F;
	/** The squeeze: how long its walls bulge before they clench, the clench, how long they stay soft, and what it does. */
	public static final int SQUEEZE_TELL = 34;
	public static final int SQUEEZE_SHUT = 8;
	public static final int SQUEEZE_SOFT = 40;
	public static final float SQUEEZE_DAMAGE = 6.0F;
	/** The clenching half reaches this far from its end wall: x below 17.5 (or above 21.5) is caught; the middle band and the other half are safe. */
	public static final double SQUEEZE_REACH = 6.5;
	/** Rest after an attack resolves (ticks) at each stage of the bar, and the least gap between two attacks starting. */
	private static final int[] REST = {22, 16, 9};
	private static final int[] START_GAP = {10, 18, 12};
	/** Creeping flesh: ticks between patches (plus a random part). */
	public static final int PATCH_GAP = 170;
	/** A player standing on a ledge this long is taken in by an arm. */
	public static final int LEDGE_TICKS = 16;

	/** The attacks, in the order it tries them (each skipped while one of its kind is out). */
	public static final byte SLAM = 0;
	public static final byte LUNGE = 1;
	public static final byte SQUEEZE = 2;
	public static final byte GRAB = 3;
	private static final byte[] ORDER = {SLAM, LUNGE, SQUEEZE, LUNGE, GRAB, SLAM, LUNGE, SQUEEZE, SLAM, GRAB, LUNGE, SLAM};

	private Shaft() {
	}

	/** What is going on in one hotel's shaft right now (not saved). */
	public static final class State {
		public float fear;
		/** The tick the turn began (-1 when not turning). */
		public long tiltAt = -1;
		/** The shaft is built and the fight is in it. */
		public boolean built;
		public long enteredAt;
		public final Map<UUID, Float> hp = new HashMap<>();
		public final Set<UUID> down = new HashSet<>();
		/** The shaft's blocks still to be put down (they go in over the turn, a few hundred a tick), and how many are. */
		public List<Map.Entry<HotelPlan.P, BlockState>> queue;
		public int placed;
		/** When the next attack may start, and the next creeping flesh (Long.MAX_VALUE: never, for the tests). */
		public long nextAttack;
		public long nextPatch;
		/** Attacks out last tick (when it drops, one has resolved: a rest before the next), and where the order is. */
		public int lastActive;
		public int order;
		/** The squeeze: which half (0 the low-x end, 1 the high-x end, -1 none), when it began, and at whom it was aimed. */
		public int squeezeHalf = -1;
		public long squeezeAt;
		public UUID squeezeTarget;
		public boolean squeezeLanded;
		/** How long each player has stood on a ledge. */
		public final Map<UUID, Integer> onLedge = new HashMap<>();
		/** Who has landed in the arena (for the tests: fell in unhurt). */
		public final Set<UUID> landed = new HashSet<>();
		/** The last tick the devil cried out at a cut. */
		public long criedAt;

		public void reset() {
			fear = 0.0F;
			tiltAt = -1;
			built = false;
			hp.clear();
			down.clear();
			queue = null;
			placed = 0;
			squeezeHalf = -1;
			squeezeTarget = null;
			onLedge.clear();
			landed.clear();
			lastActive = 0;
			order = 0;
		}
	}

	// ---- The design.

	/** The mouth's floor: dark grey-brown (mud, packed mud, brown and grey terracotta), a few veins of black. */
	static BlockState flesh(int x, int y, int z) {
		int h = hash(x, y, z);
		Block b = h < 40 ? Blocks.MUD : h < 100 ? Blocks.PACKED_MUD : h < 150 ? Blocks.BROWN_TERRACOTTA : h < 238 ? Blocks.GRAY_TERRACOTTA : Blocks.BLACKSTONE;
		return b.getDefaultState();
	}

	/** The arena's walls and ceiling: dark red flesh (so the pale heads and arms read against it), with darker streaks. */
	static BlockState wallFlesh(int x, int y, int z) {
		int h = hash(x, y, z);
		Block b = h < 170 ? Blocks.NETHER_WART_BLOCK : h < 238 ? Blocks.RED_TERRACOTTA : Blocks.BROWN_TERRACOTTA;
		return b.getDefaultState();
	}

	/** The arena's floor: browner than the walls, so you can tell where the floor ends. */
	static BlockState floorFlesh(int x, int y, int z) {
		int h = hash(x, y, z);
		Block b = h < 150 ? Blocks.RED_TERRACOTTA : h < 215 ? Blocks.BROWN_TERRACOTTA : Blocks.NETHER_WART_BLOCK;
		return b.getDefaultState();
	}

	private static int hash(int x, int y, int z) {
		return ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 255;
	}

	private static void put(Map<HotelPlan.P, BlockState> m, int x, int y, int z, BlockState s) {
		m.put(new HotelPlan.P(x, y, z), s);
	}

	private static BlockState wool(BlockState carpet) {
		Identifier id = Registries.BLOCK.getId(carpet.getBlock());
		Block w = Registries.BLOCK.get(Identifier.of(id.getNamespace(), id.getPath().replace("_carpet", "_wool")));
		return (w == Blocks.AIR ? Blocks.RED_WOOL : w).getDefaultState();
	}

	/** A ledge: the alcove's height (the local y a player stands at) and the wall it is in (z 4 the front side, z 8 the back side). */
	public record Ledge(int y, int wallZ) {
	}

	/** The ledges, lowest first: one for each room door of the loop floor that has room above the mouth. */
	public static List<Ledge> ledges() {
		List<Ledge> out = new ArrayList<>();
		for (HotelPlan.Door d : HotelPlan.get().doors) {
			if (d.kind() == HotelPlan.DoorKind.ROOM && d.y() == Arena.Y0 && (d.z() == 4 || d.z() == 8)) {
				int y = BASE + DEPTH - d.x();
				if (y >= BASE + 8 && y <= BASE + DEPTH) {
					out.add(new Ledge(y, d.z()));
				}
			}
		}
		out.sort(Comparator.comparingInt(Ledge::y));
		return out;
	}

	/** The growths on the arena's floor (against its walls), each two blocks high: lumps of flesh with a glowing boil on top, cut at any time. */
	public static final int[][] GROWTHS = {{AX0, AZ0}, {AX1, AZ0}, {AX0, AZ1}, {AX1, AZ1}, {15, AZ1}, {23, AZ0}, {AX0, 6}, {AX1, 7}};
	/** The boils in the walls (local x, y, z of the block): glowing lumps that light the flesh round them. */
	private static final int[][] BOILS = {{AX0 - 1, FLOOR + 3, 3}, {AX0 - 1, FLOOR + 4, 10}, {AX1 + 1, FLOOR + 3, 4}, {AX1 + 1, FLOOR + 4, 9}, {14, FLOOR + 3, AZ0 - 1},
			{20, FLOOR + 4, AZ0 - 1}, {25, FLOOR + 3, AZ0 - 1}, {13, FLOOR + 4, AZ1 + 1}, {18, FLOOR + 3, AZ1 + 1}, {24, FLOOR + 4, AZ1 + 1}};

	/** Every block of the shaft, by local place: the arena, the mouth, the shaft's four walls with the corridor's own blocks, the alcoves. */
	public static Map<HotelPlan.P, BlockState> design() {
		HotelPlan plan = HotelPlan.get();
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState lamp = Blocks.REDSTONE_LAMP.getDefaultState().with(RedstoneLampBlock.LIT, true);
		BlockState cream = plan.at(1, Arena.Y0 + 1, 4);
		BlockState light = Blocks.LIGHT.getDefaultState();
		Map<HotelPlan.P, BlockState> m = new LinkedHashMap<>();
		// The arena: a shell of flesh two thick (the floor three), and its inside.
		for (int x = AX0 - 2; x <= AX1 + 2; x++) {
			for (int z = AZ0 - 2; z <= AZ1 + 2; z++) {
				for (int y = FLOOR - 3; y <= FLOOR + HIGH + 1; y++) {
					put(m, x, y, z, y < FLOOR ? floorFlesh(x, y, z) : wallFlesh(x, y, z));
				}
			}
		}
		for (int x = AX0; x <= AX1; x++) {
			for (int z = AZ0; z <= AZ1; z++) {
				for (int y = FLOOR; y < FLOOR + HIGH; y++) {
					put(m, x, y, z, air);
				}
			}
		}
		// Lit from inside: light (invisible) a block over the floor every three blocks, so every floor block has 12 or more; boils in the walls.
		for (int x = AX0 + 1; x <= AX1; x += 3) {
			for (int z = AZ0 + 1; z <= AZ1; z += 3) {
				put(m, x, FLOOR + 1, z, light.with(net.minecraft.block.LightBlock.LEVEL_15, 15));
			}
		}
		for (int[] b : BOILS) {
			put(m, b[0], b[1], b[2], Blocks.SHROOMLIGHT.getDefaultState());
		}
		for (int[] g : GROWTHS) {
			put(m, g[0], FLOOR, g[1], Blocks.NETHER_WART_BLOCK.getDefaultState());
			put(m, g[0], FLOOR + 1, g[1], Blocks.SHROOMLIGHT.getDefaultState());
		}
		// The throat: the mouth's 3 x 3 straight down through the arena's ceiling, lit.
		for (int x = IX0; x <= IX1; x++) {
			for (int z = IZ0; z <= IZ1; z++) {
				for (int y = FLOOR + HIGH; y <= BASE - 1; y++) {
					put(m, x, y, z, air);
				}
			}
		}
		put(m, 19, FLOOR + HIGH + 1, 6, light.with(net.minecraft.block.LightBlock.LEVEL_15, 15));
		// The mouth's hollow: a shell of the dark grey-brown flesh, its floor the fused arms (drawn over it), the mouth the shaft's 3 x 3 in it.
		for (int x = CX0 - 1; x <= CX1 + 1; x++) {
			for (int z = CZ0 - 1; z <= CZ1 + 1; z++) {
				for (int y = BASE - 1; y <= BASE + 5; y++) {
					boolean inside = x >= CX0 && x <= CX1 && z >= CZ0 && z <= CZ1 && y >= BASE && y <= BASE + 4;
					boolean mouth = x >= IX0 && x <= IX1 && z >= IZ0 && z <= IZ1 && (y == BASE - 1 || y == BASE + 5);
					put(m, x, y, z, inside || mouth ? air : flesh(x, y, z));
				}
			}
		}
		for (int[] g : new int[][] {{CX0, BASE + 3, CZ0}, {CX0, BASE + 3, CZ1}, {CX1, BASE + 3, CZ0}, {CX1, BASE + 3, CZ1}}) {
			put(m, g[0], g[1], g[2], light.with(net.minecraft.block.LightBlock.LEVEL_15, 13));
		}
		// The shaft: the corridor stood on its end. A corridor's cell x is at BASE + DEPTH - x; across is still z; its height (the floor slab,
		// the carpet row, two of air, the ceiling slab) runs from x 17 (the carpet's wall) to x 21 (the lamps').
		for (int cx = 1; cx <= 30; cx++) {
			int y = BASE + DEPTH - cx;
			for (int z = 4; z <= 8; z++) {
				for (int c = 0; c <= 4; c++) {
					BlockState s = plan.at(cx, Arena.Y0 - 1 + c, z);
					boolean inner = z >= 5 && z <= 7 && c >= 1 && c <= 3;
					if (inner) {
						s = air;
					} else if (c == 0 && z >= 5 && z <= 7) {
						// (The carpet, as a wall of wool of its colours: a carpet cannot be stood on its edge.)
						s = wool(plan.at(cx, Arena.Y0, z));
					} else if (s.isOf(Blocks.REDSTONE_LAMP)) {
						s = lamp;
					} else if (!s.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) {
						// (The doors' gaps, and the signs and chains.)
						s = air;
					}
					put(m, IX0 - 1 + c, y, z, s);
				}
			}
		}
		// Light down the whole shaft, so the fall is lit all the way (invisible light blocks in the shaft's air).
		for (int dy = 6; dy <= DEPTH - 2; dy += 4) {
			put(m, IX0, BASE + dy, IZ0, light.with(net.minecraft.block.LightBlock.LEVEL_15, 13));
			put(m, IX1, BASE + dy + 2, IZ1, light.with(net.minecraft.block.LightBlock.LEVEL_15, 13));
		}
		// The top.
		for (int x = IX0 - 1; x <= IX1 + 1; x++) {
			for (int z = IZ0 - 1; z <= IZ1 + 1; z++) {
				put(m, x, BASE + DEPTH, z, Blocks.SMOOTH_QUARTZ.getDefaultState());
			}
		}
		// The alcoves: each room's door opened into a ledge, three deep, three wide, three high, a lamp in its roof.
		for (Ledge l : ledges()) {
			int dz = l.wallZ() == 4 ? -1 : 1;
			for (int x = IX0 - 1; x <= IX1 + 1; x++) {
				for (int k = 1; k <= 4; k++) {
					for (int y = l.y() - 1; y <= l.y() + 3; y++) {
						put(m, x, y, l.wallZ() + dz * k, cream);
					}
				}
			}
			for (int x = IX0; x <= IX1; x++) {
				for (int k = 0; k <= 3; k++) {
					for (int y = l.y(); y <= l.y() + 2; y++) {
						put(m, x, y, l.wallZ() + dz * k, air);
					}
					put(m, x, l.y() - 1, l.wallZ() + dz * k, cream);
				}
			}
			put(m, 19, l.y() + 3, l.wallZ() + dz * 2, lamp);
		}
		return m;
	}

	// ---- Building it, and putting it back.

	/** The shaft's block box, local: what it holds and what is moved out of it when it goes. */
	private static Box worldBox(HomeBlockEntity h) {
		Vec3d a = HotelBuilder.world(h.origin, h.rot, AX0 - 3.0, FLOOR - 4.0, AZ0 - 3.0);
		Vec3d b = HotelBuilder.world(h.origin, h.rot, AX1 + 4.0, TOP + 3.0, AZ1 + 4.0);
		return new Box(a, b);
	}

	/** Whether the world point is in the shaft's space (the home's hotel has it built). */
	public static boolean contains(HomeBlockEntity h, BlockPos pos) {
		return h.shaft != null && worldBox(h).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	/**
	 * Gets it ready: every block it will replace is kept first (so a restart at any moment puts back the right thing: a block not yet
	 * replaced is its own old state), and the blocks to put down are queued for {@link #placeSome}. False if the world is not tall enough
	 * above the hotel for it.
	 */
	public static boolean beginBuild(ServerWorld world, HomeBlockEntity h) {
		if (h.shaft != null) {
			mend(world, h);
		}
		if (h.origin.getY() + TOP + 3 >= world.getTopY()) {
			return false;
		}
		Map<HotelPlan.P, BlockState> design = design();
		List<Arena.Cell> cells = new ArrayList<>();
		for (Map.Entry<HotelPlan.P, BlockState> e : design.entrySet()) {
			HotelPlan.P p = e.getKey();
			BlockPos at = HotelBuilder.world(h.origin, h.rot, p.x(), p.y(), p.z());
			BlockState old = world.getBlockState(at);
			if (old == e.getValue()) {
				continue;
			}
			BlockEntity be = world.getBlockEntity(at);
			cells.add(new Arena.Cell(p.x(), p.y(), p.z(), old, be == null ? null : be.createNbtWithIdentifyingData(world.getRegistryManager())));
		}
		h.shaft = Arena.write(cells);
		h.markDirty();
		State st = h.run.shaft;
		st.queue = new ArrayList<>(design.entrySet());
		st.placed = 0;
		return true;
	}

	/** Puts down up to {@code count} more of the queued blocks (lowest first, so the arena is there first). */
	public static void placeSome(ServerWorld world, HomeBlockEntity h, int count) {
		State st = h.run.shaft;
		if (st.queue == null) {
			return;
		}
		int end = (int) Math.min((long) st.queue.size(), (long) st.placed + count);
		for (; st.placed < end; st.placed++) {
			Map.Entry<HotelPlan.P, BlockState> e = st.queue.get(st.placed);
			HotelPlan.P p = e.getKey();
			BlockPos at = HotelBuilder.world(h.origin, h.rot, p.x(), p.y(), p.z());
			if (world.getBlockEntity(at) != null) {
				world.removeBlockEntity(at);
			}
			if (world.getBlockState(at) != e.getValue()) {
				world.setBlockState(at, e.getValue(), FLAGS);
			}
		}
		if (st.placed >= st.queue.size()) {
			st.queue = null;
		}
	}

	/** Where whoever is in the shaft when it goes is put: the corridor's east end, looking back along it. */
	private static Vec3d out(HomeBlockEntity h) {
		return HotelBuilder.world(h.origin, h.rot, 30.5, Arena.Y0, 6.5);
	}

	/** The shaft put back, block for block (anyone in it is put in the corridor, and what lies in it too). */
	public static void mend(ServerWorld world, HomeBlockEntity h) {
		State st = h.run.shaft;
		st.built = false;
		st.tiltAt = -1;
		st.squeezeHalf = -1;
		st.queue = null;
		if (h.shaft == null) {
			return;
		}
		// Out first: the players, what was dropped, and the devil's own creatures.
		Box box = worldBox(h);
		Vec3d to = out(h);
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (box.contains(p.getPos())) {
				p.stopRiding();
				p.networkHandler.requestTeleport(to.x, to.y, to.z, HotelBuilder.alongYaw(h.rot) + 180.0F, 0.0F);
				p.setVelocity(Vec3d.ZERO);
				p.fallDistance = 0.0F;
			}
		}
		for (ItemEntity item : world.getEntitiesByType(EntityType.ITEM, box, e -> true)) {
			item.refreshPositionAndAngles(to.x + (world.random.nextDouble() - 0.5), to.y + 0.3, to.z + (world.random.nextDouble() - 0.5), 0.0F, 0.0F);
			item.setVelocity(Vec3d.ZERO);
		}
		for (SquadMemberEntity m : world.getEntitiesByType(ChainsawMan.SQUAD_MEMBER, box, e -> true)) {
			m.refreshPositionAndAngles(to.x, to.y, to.z, 0.0F, 0.0F);
		}
		calm(world, box);
		for (FleshPatchEntity e : world.getEntitiesByType(ChainsawMan.FLESH_PATCH, box, x -> true)) {
			e.discard();
		}
		List<Arena.Cell> cells = Arena.read(h.shaft, world);
		for (Arena.Cell c : cells) {
			BlockPos at = HotelBuilder.world(h.origin, h.rot, c.x(), c.y(), c.z());
			if (world.getBlockState(at) != c.state()) {
				world.setBlockState(at, c.state(), FLAGS);
			}
			if (c.entity() != null) {
				BlockEntity be = BlockEntity.createFromNbt(at, c.state(), c.entity(), world.getRegistryManager());
				if (be != null) {
					world.getChunk(at).setBlockEntity(be);
					be.markDirty();
				}
			}
		}
		h.shaft = null;
		h.markDirty();
		Vec3d mid = HotelBuilder.world(h.origin, h.rot, MID_X, FLOOR + 3.0, MID_Z);
		world.spawnParticles(ParticleTypes.POOF, mid.x, mid.y, mid.z, 30, 4.0, 2.0, 3.0, 0.02);
		world.playSound(null, to.x, to.y, to.z, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 3.0F, 0.6F);
	}

	/** Its arms and heads go (they draw back: the fight is over, or it is begging and nothing crosses in front of its core). */
	private static void calm(ServerWorld world, Box box) {
		for (ShaftArmEntity e : world.getEntitiesByType(ChainsawMan.SHAFT_ARM, box, x -> true)) {
			e.discard();
		}
		for (ShaftHeadEntity e : world.getEntitiesByType(ChainsawMan.SHAFT_HEAD, box, x -> true)) {
			e.discard();
		}
	}

	/** At full pain it stops attacking: its parts draw back and any squeeze is over (anyone it held is let go). */
	public static void calm(ServerWorld world, EternityDevilEntity devil) {
		HomeBlockEntity h = home(world, devil);
		if (h == null || !h.run.shaft.built) {
			return;
		}
		h.run.shaft.squeezeHalf = -1;
		calm(world, worldBox(h));
		for (FleshPatchEntity e : world.getEntitiesByType(ChainsawMan.FLESH_PATCH, worldBox(h), x -> true)) {
			e.discard();
		}
	}

	// ---- Messages to the clients.

	static void send(ServerPlayerEntity p, int devil, byte kind, int ticks) {
		if (ServerPlayNetworking.canSend(p, HotelPayloads.Devil.ID)) {
			ServerPlayNetworking.send(p, new HotelPayloads.Devil(devil, kind, 0.0F, 0.0F, 0.0F, ticks));
		}
	}

	private static Vec3d local(HomeBlockEntity h, ServerPlayerEntity p) {
		return localOf(h.origin, h.rot, p.getPos());
	}

	/**
	 * A local point in the world, placed the same way the blocks are (a cell's local x to x + 1 is exactly its block, whichever way the hotel
	 * is turned): everything in the arena is put and drawn with this, so it lines up with its walls.
	 */
	public static Vec3d at(BlockPos origin, int rot, double x, double y, double z) {
		return switch (rot & 3) {
			case 0 -> new Vec3d(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
			case 1 -> new Vec3d(origin.getX() - z + 1.0, origin.getY() + y, origin.getZ() + x);
			case 2 -> new Vec3d(origin.getX() - x + 1.0, origin.getY() + y, origin.getZ() - z + 1.0);
			default -> new Vec3d(origin.getX() + z, origin.getY() + y, origin.getZ() - x + 1.0);
		};
	}

	/** The local point of a world point: the inverse of {@link #at}. */
	public static Vec3d localOf(BlockPos origin, int rot, Vec3d p) {
		double dx = p.x - origin.getX();
		double dz = p.z - origin.getZ();
		double y = p.y - origin.getY();
		return switch (rot & 3) {
			case 0 -> new Vec3d(dx, y, dz);
			case 1 -> new Vec3d(dz, y, 1.0 - dx);
			case 2 -> new Vec3d(1.0 - dx, y, 1.0 - dz);
			default -> new Vec3d(1.0 - dz, y, dx);
		};
	}

	static HomeBlockEntity home(ServerWorld world, EternityDevilEntity devil) {
		return world.getBlockEntity(devil.homePos) instanceof HomeBlockEntity h ? h : null;
	}

	// ---- The fight.

	/** One tick of the shaft fight for the hotel: fear and the turn before it, the arena after. {@code players}: those inside, alive. */
	static void tick(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, List<ServerPlayerEntity> players, long now) {
		State st = h.run.shaft;
		if (st.built) {
			arenaTick(world, h, devil, players, now);
		} else if (h.run.shaftOn) {
			wallTick(world, h, devil, players, now);
		}
	}

	/** Fear, in the corridor: it fills, and at full the floor turns. */
	private static void wallTick(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, List<ServerPlayerEntity> players, long now) {
		Hotel.Run r = h.run;
		State st = r.shaft;
		if (st.tiltAt >= 0) {
			tilting(world, h, devil, players, now);
			return;
		}
		if (devil.growing() || devil.begStarted || !devil.isAlive()) {
			return;
		}
		float fear = st.fear + FEAR_TICK;
		for (ServerPlayerEntity p : players) {
			Float last = st.hp.get(p.getUuid());
			if (last != null && p.getHealth() < last - 0.01F) {
				fear += FEAR_HURT;
			}
			st.hp.put(p.getUuid(), p.getHealth());
			boolean down = io.github.kortev.chainsawman.form.Downed.is(p);
			if (down && st.down.add(p.getUuid())) {
				fear += FEAR_DOWN;
			} else if (!down) {
				st.down.remove(p.getUuid());
			}
		}
		st.fear = Math.min(1.0F, fear);
		boolean hurtEnough = devil.pain() >= PAIN_TILT && now - r.fightAt >= PAIN_TILT_MIN;
		if ((st.fear >= 1.0F && now - r.fightAt >= TILT_MIN || hurtEnough) && !players.isEmpty()) {
			startTilt(world, h, devil, players, now);
		}
	}

	/** The floor begins to turn: the camera rolls, the hotel groans, the players slide down the corridor. */
	static void startTilt(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, List<ServerPlayerEntity> players, long now) {
		State st = h.run.shaft;
		if (!beginBuild(world, h)) {
			// (The world is not tall enough above the hotel: the fight goes on in the corridor, and the fear starts again.)
			st.fear = 0.5F;
			return;
		}
		st.tiltAt = now;
		st.fear = 1.0F;
		devil.tilting = true;
		Vec3d mid = HotelBuilder.world(h.origin, h.rot, 24.0, Arena.Y0 + 1.0, 6.5);
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.DEVIL_GROW, SoundCategory.HOSTILE, 5.0F, 0.45F);
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.HOTEL_LOOP_BREAK, SoundCategory.HOSTILE, 5.0F, 0.4F);
		world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 5.0F, 0.5F);
		for (ServerPlayerEntity p : players) {
			send(p, devil.getId(), HotelPayloads.Devil.TILT, TILT_TICKS);
		}
		Hotel.speak(world, h.run.inside, "message.chainsawman.devil.tilt", 70, 1);
	}

	/** The two seconds of the turn: everyone slides toward the devil's end, then the shaft is there and they fall down it. */
	private static void tilting(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, List<ServerPlayerEntity> players, long now) {
		State st = h.run.shaft;
		long t = now - st.tiltAt;
		Vec3d slide = HotelBuilder.world(BlockPos.ORIGIN, h.rot, 1.0, 0.0, 0.0);
		for (ServerPlayerEntity p : players) {
			float k = 0.012F + 0.05F * t / TILT_TICKS;
			p.addVelocity(slide.x * k, 0.0, slide.z * k);
			p.velocityModified = true;
		}
		// (The shaft goes up over the turn, out of sight above the roof.)
		if (st.queue != null) {
			placeSome(world, h, st.queue.size() / Math.max(1, TILT_TICKS - 6 - (int) t) + 1);
		}
		if (t % 6 == 0) {
			Vec3d at = HotelBuilder.world(h.origin, h.rot, 20.0, Arena.Y0 + 1.5, 6.5);
			world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 4, 6.0, 0.8, 1.2, 0.01);
		}
		if (t >= TILT_TICKS) {
			enter(world, h, devil, players, now);
		}
	}

	/** The shaft is built: everyone is falling down it toward the mouth, and the devil is the arena they will land in. */
	static void enter(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, List<ServerPlayerEntity> players, long now) {
		Hotel.Run r = h.run;
		State st = r.shaft;
		st.tiltAt = -1;
		devil.tilting = false;
		placeSome(world, h, Integer.MAX_VALUE);
		if (h.shaft == null) {
			st.fear = 0.5F;
			for (ServerPlayerEntity p : players) {
				send(p, devil.getId(), HotelPayloads.Devil.SHAFT, 0);
			}
			return;
		}
		r.mendAt = -1;
		st.built = true;
		st.enteredAt = now;
		// (The fall takes about two seconds; the first attack a second and a half after the landing.)
		st.nextAttack = now + 75;
		st.nextPatch = now + 200;
		st.lastActive = 0;
		st.squeezeHalf = -1;
		st.landed.clear();
		// The flesh creeping in the corridor goes with it, and the sky arena is shut.
		Box corridor = new Box(at(h.origin, h.rot, 0.0, Arena.Y0 - 2.0, 0.0), at(h.origin, h.rot, HotelPlan.SX, Arena.Y1 + 3.0, HotelPlan.SZ));
		for (FleshPatchEntity patch : world.getEntitiesByType(ChainsawMan.FLESH_PATCH, corridor, e -> true)) {
			patch.discard();
		}
		int i = 0;
		for (ServerPlayerEntity p : players) {
			drop(p, h, i++);
		}
		for (SquadMemberEntity m : Squad.members(world, h)) {
			Vec3d at = at(h.origin, h.rot, MID_X + 3.0 * ((i % 3) - 1), FLOOR, MID_Z + 2.5 * ((i / 3) % 2 == 0 ? 1 : -1));
			m.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
			i++;
		}
		Arena.mend(world, h);
		devil.enterShaft(at(h.origin, h.rot, MID_X, DEVIL_Y, MID_Z));
		for (ServerPlayerEntity p : players) {
			send(p, devil.getId(), HotelPayloads.Devil.SHAFT, 1);
		}
		Vec3d mid = at(h.origin, h.rot, MID_X, BASE + 2.0, MID_Z);
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.DEVIL_GROW, SoundCategory.HOSTILE, 4.0F, 0.6F);
	}

	/** {@code p} in the shaft, high over the mouth, falling, looking down it ({@code i}: which of them, so two are not in one place). */
	public static void drop(ServerPlayerEntity p, HomeBlockEntity h, int i) {
		double dx = ((i % 3) - 1) * 0.7;
		double dz = (((i / 3) % 3) - 1) * 0.7;
		Vec3d at = at(h.origin, h.rot, MID_X + dx, BASE + FALL_FROM, MID_Z + dz);
		p.stopRiding();
		p.networkHandler.requestTeleport(at.x, at.y, at.z, HotelBuilder.alongYaw(h.rot), 72.0F);
		p.setVelocity(0.0, -0.2, 0.0);
		p.velocityModified = true;
		p.fallDistance = 0.0F;
	}

	/** Where along z someone stands on a ledge: at the lip of the doorway, over the shaft. */
	public static double edgeZ(Ledge l) {
		return l.wallZ() == 4 ? 4.95 : 8.05;
	}

	/** {@code p} in a ledge's doorway, looking across and down the shaft. */
	public static void place(ServerPlayerEntity p, HomeBlockEntity h, Ledge l) {
		Vec3d at = at(h.origin, h.rot, 19.5, l.y(), edgeZ(l));
		float yaw = HotelBuilder.alongYaw(h.rot) + (l.wallZ() == 4 ? 90.0F : -90.0F);
		p.stopRiding();
		p.networkHandler.requestTeleport(at.x, at.y, at.z, yaw, 55.0F);
		p.setVelocity(Vec3d.ZERO);
		p.velocityModified = true;
		p.fallDistance = 0.0F;
	}

	/** In the arena (local): over its floor, below its ceiling. */
	public static boolean inArena(Vec3d lp) {
		return lp.x >= AX0 && lp.x < AX1 + 1 && lp.z >= AZ0 && lp.z < AZ1 + 1 && lp.y >= FLOOR - 0.6 && lp.y < FLOOR + HIGH;
	}

	/** In the mouth's hollow (over its floor of fused arms). */
	private static boolean inMouth(Vec3d lp) {
		return lp.x >= CX0 && lp.x < CX1 + 1 && lp.z >= CZ0 && lp.z < CZ1 + 1 && lp.y >= BASE - 0.6 && lp.y < BASE + 5.0;
	}

	/** Over the mouth (the shaft's own 3 x 3). */
	private static boolean overMouth(Vec3d lp) {
		return lp.x >= IX0 + 0.25 && lp.x < IX1 + 0.75 && lp.z >= IZ0 + 0.25 && lp.z < IZ1 + 0.75;
	}

	/** The arena's share of the bar, 0 to 1 (its pain past the turn). */
	public static float share(EternityDevilEntity devil) {
		return MathHelper.clamp((devil.pain() - PAIN_TILT) / (1.0F - PAIN_TILT), 0.0F, 1.0F);
	}

	/** How wild it is: 0 at first, 1 past a third of the arena's share of the bar (two attacks overlap), 2 past two thirds (shorter gaps, worse cries). */
	public static int stage(EternityDevilEntity devil) {
		float s = share(devil);
		return s < 1.0F / 3.0F ? 0 : s < 2.0F / 3.0F ? 1 : 2;
	}

	private static void arenaTick(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, List<ServerPlayerEntity> players, long now) {
		State st = h.run.shaft;
		List<ServerPlayerEntity> arena = new ArrayList<>();
		for (ServerPlayerEntity p : players) {
			Vec3d lp = local(h, p);
			// (The flesh is soft, and the fall is caught: nobody is hurt landing.)
			p.fallDistance = 0.0F;
			if (p.isCreative() || p.isSpectator()) {
				continue;
			}
			if (lp.y < BASE && lp.y > FLOOR + 0.3 && p.getVelocity().y < -0.9) {
				// Through the throat: the fall is slowed so the landing is soft.
				p.setVelocity(p.getVelocity().x * 0.6, -0.9, p.getVelocity().z * 0.6);
				p.velocityModified = true;
			}
			if (inMouth(lp) && !overMouth(lp)) {
				// On the fused arms round the mouth: they pass you in.
				Vec3d to = HotelBuilder.world(BlockPos.ORIGIN, h.rot, MID_X - lp.x, 0.0, MID_Z - lp.z);
				double len = Math.max(0.3, to.length());
				p.setVelocity(to.x / len * 0.32, Math.min(p.getVelocity().y, 0.05), to.z / len * 0.32);
				p.velocityModified = true;
			}
			if (lp.y >= BASE + 6.0 && p.isOnGround()) {
				// Caught on a ledge: an arm comes up the shaft and takes them in.
				int t = st.onLedge.merge(p.getUuid(), 1, Integer::sum);
				if (t == LEDGE_TICKS && devil.fightable()) {
					riseFor(world, h, p);
				}
			} else {
				st.onLedge.remove(p.getUuid());
			}
			if (inArena(lp)) {
				arena.add(p);
				st.landed.add(p.getUuid());
			}
		}
		if (!devil.fightable() || devil.broken || devil.begStarted) {
			return;
		}
		director(world, h, devil, arena, now);
		squeezeTick(world, h, devil, now);
		if (now >= st.nextPatch && !arena.isEmpty()) {
			st.nextPatch = now + PATCH_GAP + world.random.nextInt(60);
			creep(world, h, arena.get(world.random.nextInt(arena.size())));
		}
	}

	/** An arm up the shaft for whoever is on a ledge: it takes them down to the mouth. */
	private static void riseFor(ServerWorld world, HomeBlockEntity h, ServerPlayerEntity target) {
		Vec3d lp = local(h, target);
		double ax = Math.max(IX0 + 0.6, Math.min(IX1 + 0.4, lp.x));
		double az = IZ0 + 1.5 + (lp.z < IZ0 ? -0.6 : lp.z > IZ1 + 1 ? 0.6 : 0.0);
		ShaftArmEntity arm = new ShaftArmEntity(ChainsawMan.SHAFT_ARM, world);
		arm.start(h.origin, h.rot, at(h.origin, h.rot, ax, BASE, az), h.origin.getY() + BASE + DEPTH - 1.5);
		world.spawnEntity(arm);
		world.playSound(null, arm.getX(), arm.getY() + 2.0, arm.getZ(), ChainsawSounds.DEVIL_SLAM, SoundCategory.HOSTILE, 1.2F, 0.7F);
	}

	/** Whether the squeeze is out (from its first bulge to its clench). */
	private static boolean squeezing(State st, long now) {
		return st.squeezeHalf >= 0 && now - st.squeezeAt < SQUEEZE_TELL + SQUEEZE_SHUT;
	}

	/** The attacks: as many out at once as the stage and the number of players allow, each at a different player, a rest after each resolves. */
	private static void director(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, List<ServerPlayerEntity> arena, long now) {
		State st = h.run.shaft;
		Box box = worldBox(h);
		int stage = stage(devil);
		Set<UUID> aimed = new HashSet<>();
		int active = 0;
		boolean slamOut = false;
		boolean grabOut = false;
		for (ShaftArmEntity a : world.getEntitiesByType(ChainsawMan.SHAFT_ARM, box, e -> true)) {
			if (a.mode() != ShaftArmEntity.RISE && a.threatening()) {
				active++;
				slamOut |= a.mode() == ShaftArmEntity.SLAM;
				grabOut |= a.mode() == ShaftArmEntity.GRAB;
				if (a.target != null) {
					aimed.add(a.target);
				}
			}
		}
		int heads = 0;
		for (ShaftHeadEntity e : world.getEntitiesByType(ChainsawMan.SHAFT_HEAD, box, e -> true)) {
			if (e.threatening()) {
				active++;
				heads++;
				if (e.target != null) {
					aimed.add(e.target);
				}
			}
		}
		boolean squeeze = squeezing(st, now);
		if (squeeze) {
			active++;
			if (st.squeezeTarget != null) {
				aimed.add(st.squeezeTarget);
			}
		}
		if (active < st.lastActive) {
			st.nextAttack = Math.max(st.nextAttack, now + REST[stage]);
		}
		st.lastActive = active;
		int max = Math.min(4, (stage == 0 ? 1 : 2) + Math.max(0, arena.size() - 1));
		if (arena.isEmpty() || now < st.nextAttack || active >= max) {
			return;
		}
		List<ServerPlayerEntity> free = new ArrayList<>();
		for (ServerPlayerEntity p : arena) {
			if (!aimed.contains(p.getUuid()) && !io.github.kortev.chainsawman.form.Downed.is(p) && !heldByArm(world, box, p)) {
				free.add(p);
			}
		}
		if (free.isEmpty() && stage > 0) {
			// (Fewer players than attacks it may have out: past the first stage they come at the same one.)
			for (ServerPlayerEntity p : arena) {
				if (!io.github.kortev.chainsawman.form.Downed.is(p) && !heldByArm(world, box, p)) {
					free.add(p);
				}
			}
		}
		if (free.isEmpty()) {
			return;
		}
		ServerPlayerEntity target = free.get(world.random.nextInt(free.size()));
		for (int k = 0; k < ORDER.length; k++) {
			byte kind = ORDER[st.order % ORDER.length];
			st.order++;
			boolean ok = switch (kind) {
				case SLAM -> !slamOut || stage > 0 && arena.size() > 1;
				case LUNGE -> heads < 2;
				case SQUEEZE -> !squeeze && !grabOut;
				default -> !grabOut && !squeeze;
			};
			if (ok && launch(world, h, devil, kind, target, now)) {
				st.nextAttack = now + START_GAP[stage];
				st.lastActive = active + 1;
				return;
			}
		}
	}

	/** Whether {@code p} is held by a hand now. */
	private static boolean heldByArm(ServerWorld world, Box box, ServerPlayerEntity p) {
		for (ShaftArmEntity a : world.getEntitiesByType(ChainsawMan.SHAFT_ARM, box, e -> e.holding())) {
			if (p.getUuid().equals(a.heldId())) {
				return true;
			}
		}
		return false;
	}

	/** Starts one attack of {@code kind} at {@code target}: false if there is no room for it. */
	public static boolean launch(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, byte kind, ServerPlayerEntity target, long now) {
		Vec3d lp = local(h, target);
		switch (kind) {
			case SLAM -> {
				return slam(world, h, target, lp, world.random.nextFloat() < 0.6F);
			}
			case LUNGE -> {
				return lunge(world, h, target, lp);
			}
			case SQUEEZE -> {
				squeeze(world, h, devil, target, lp.x < MID_X ? 0 : 1, now);
				return true;
			}
			default -> {
				return grab(world, h, target, lp);
			}
		}
	}

	/** A slam down a lane through {@code lp}, from the wall farther from it ({@code alongX}: the lane runs along the room's length). */
	public static boolean slam(ServerWorld world, HomeBlockEntity h, ServerPlayerEntity target, Vec3d lp, boolean alongX) {
		double sx;
		double sz;
		double dirX;
		double dirZ;
		double len;
		if (alongX) {
			boolean fromLow = lp.x - AX0 > AX1 + 1 - lp.x;
			sx = fromLow ? AX0 : AX1 + 1;
			dirX = fromLow ? 1.0 : -1.0;
			dirZ = 0.0;
			sz = MathHelper.clamp(lp.z, AZ0 + 1.5, AZ1 + 1 - 1.5);
			len = MathHelper.clamp(Math.abs(lp.x - sx) + 3.0, 7.0, AX1 + 1 - AX0);
		} else {
			boolean fromLow = lp.z - AZ0 > AZ1 + 1 - lp.z;
			sz = fromLow ? AZ0 : AZ1 + 1;
			dirZ = fromLow ? 1.0 : -1.0;
			dirX = 0.0;
			sx = MathHelper.clamp(lp.x, AX0 + 1.5, AX1 + 1 - 1.5);
			len = MathHelper.clamp(Math.abs(lp.z - sz) + 3.0, 7.0, AZ1 + 1 - AZ0);
		}
		Vec3d at = at(h.origin, h.rot, sx, FLOOR, sz);
		Vec3d dir = HotelBuilder.world(BlockPos.ORIGIN, h.rot, dirX, 0.0, dirZ);
		ShaftArmEntity arm = new ShaftArmEntity(ChainsawMan.SHAFT_ARM, world);
		arm.slam(h.origin, h.rot, at, dir, len, target.getUuid());
		world.spawnEntity(arm);
		Vec3d mid = at.add(dir.multiply(len * 0.5));
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.DEVIL_REGEN, SoundCategory.HOSTILE, 2.2F, 0.6F);
		return true;
	}

	/** A head rising 5 blocks from {@code target} (wherever there is floor and nobody near), to lunge at them. */
	public static boolean lunge(ServerWorld world, HomeBlockEntity h, ServerPlayerEntity target, Vec3d lp) {
		double start = world.random.nextDouble() * Math.PI * 2.0;
		for (int k = 0; k < 16; k++) {
			double a = start + k * Math.PI / 8.0;
			double r = ShaftHeadEntity.FROM;
			double hx = lp.x + Math.cos(a) * r;
			double hz = lp.z + Math.sin(a) * r;
			if (hx < AX0 + 1.0 || hx > AX1 || hz < AZ0 + 1.0 || hz > AZ1) {
				continue;
			}
			boolean clear = true;
			for (ServerPlayerEntity other : world.getPlayers()) {
				Vec3d o = local(h, other);
				clear &= Math.hypot(o.x - hx, o.z - hz) >= 3.5 || Math.abs(o.y - FLOOR) > 4.0;
			}
			if (!clear) {
				continue;
			}
			ShaftHeadEntity head = new ShaftHeadEntity(ChainsawMan.SHAFT_HEAD, world);
			head.start(h.origin, h.rot, at(h.origin, h.rot, hx, FLOOR, hz), target.getUuid());
			world.spawnEntity(head);
			world.playSound(null, head.getX(), head.getY() + 1.0, head.getZ(), ChainsawSounds.DEVIL_GROW, SoundCategory.HOSTILE, 1.6F, 1.5F);
			return true;
		}
		return false;
	}

	/** A hand at the long wall nearer {@code target}, 4 blocks along from them, to sweep the floor on that side. */
	public static boolean grab(ServerWorld world, HomeBlockEntity h, ServerPlayerEntity target, Vec3d lp) {
		boolean low = lp.z - AZ0 < AZ1 + 1 - lp.z;
		double wz = low ? AZ0 : AZ1 + 1;
		double side = lp.x + 4.0 <= AX1 - 0.5 && (lp.x - 4.0 < AX0 + 1.5 || world.random.nextBoolean()) ? 4.0 : -4.0;
		double px = MathHelper.clamp(lp.x + side, AX0 + 1.5, AX1 - 0.5);
		Vec3d at = at(h.origin, h.rot, px, FLOOR, wz);
		Vec3d in = HotelBuilder.world(BlockPos.ORIGIN, h.rot, 0.0, 0.0, low ? 1.0 : -1.0);
		// It sweeps from the side away from them, across, toward them and on.
		Vec3d toward = HotelBuilder.world(BlockPos.ORIGIN, h.rot, -Math.signum(side), 0.0, 0.0);
		ShaftArmEntity arm = new ShaftArmEntity(ChainsawMan.SHAFT_ARM, world);
		arm.grab(h.origin, h.rot, at, in, toward, target.getUuid());
		world.spawnEntity(arm);
		world.playSound(null, at.x, at.y + 1.0, at.z, ChainsawSounds.DEVIL_REGEN, SoundCategory.HOSTILE, 2.2F, 0.45F);
		return true;
	}

	/** Half the room's walls bulge, their seams lit, then that half clenches shut. Stand in the other half. */
	public static void squeeze(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, ServerPlayerEntity target, int half, long now) {
		State st = h.run.shaft;
		st.squeezeHalf = half;
		st.squeezeAt = now;
		st.squeezeTarget = target == null ? null : target.getUuid();
		st.squeezeLanded = false;
		Vec3d at = at(h.origin, h.rot, half == 0 ? AX0 + 3.0 : AX1 - 2.0, FLOOR + 2.0, MID_Z);
		devil.announceAt(HotelPayloads.Devil.SQUEEZE, at, half);
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.DEVIL_GROW, SoundCategory.HOSTILE, 3.0F, 0.5F);
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.HOTEL_HEARTBEAT, SoundCategory.HOSTILE, 3.0F, 0.6F);
	}

	/** Whether the local point is where the clenching half closes. */
	public static boolean inSqueeze(int half, Vec3d lp) {
		return half == 0 ? lp.x < AX0 + SQUEEZE_REACH : lp.x > AX1 + 1 - SQUEEZE_REACH;
	}

	private static void squeezeTick(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, long now) {
		State st = h.run.shaft;
		if (st.squeezeHalf < 0) {
			return;
		}
		long t = now - st.squeezeAt;
		if (t == SQUEEZE_TELL + 2 && !st.squeezeLanded) {
			st.squeezeLanded = true;
			Vec3d at = at(h.origin, h.rot, st.squeezeHalf == 0 ? AX0 + 2.0 : AX1 - 1.0, FLOOR + 2.0, MID_Z);
			world.playSound(null, at.x, at.y, at.z, ChainsawSounds.DEVIL_SLAM, SoundCategory.HOSTILE, 3.0F, 0.5F);
			world.playSound(null, at.x, at.y, at.z, ChainsawSounds.DEVIL_BITE, SoundCategory.HOSTILE, 2.5F, 0.5F);
			for (ServerPlayerEntity p : world.getPlayers()) {
				Vec3d lp = local(h, p);
				if (!p.isAlive() || p.isSpectator() || p.isCreative() || !inArena(lp) || !inSqueeze(st.squeezeHalf, lp)) {
					continue;
				}
				p.damage(devil.getDamageSources().mobAttack(devil), SQUEEZE_DAMAGE);
				Vec3d push = HotelBuilder.world(BlockPos.ORIGIN, h.rot, st.squeezeHalf == 0 ? 1.1 : -1.1, 0.0, 0.0);
				p.setVelocity(push.x, 0.35, push.z);
				p.velocityModified = true;
			}
		}
		if (t > SQUEEZE_TELL + SQUEEZE_SHUT + SQUEEZE_SOFT) {
			st.squeezeHalf = -1;
		}
	}

	/** Whether the walls at the local point are soft (the half a squeeze has just clenched). */
	private static boolean soft(State st, long now, Vec3d lp) {
		return st.squeezeHalf >= 0 && now - st.squeezeAt >= SQUEEZE_TELL && (st.squeezeHalf == 0 ? lp.x < MID_X : lp.x > MID_X);
	}

	/** Flesh creeping toward a player: three patches round them, a few blocks off (they come up slowly, and bite only who stands on them). */
	private static void creep(ServerWorld world, HomeBlockEntity h, ServerPlayerEntity target) {
		Vec3d lp = local(h, target);
		for (int k = 0; k < 3; k++) {
			double a = world.random.nextDouble() * Math.PI * 2.0;
			double r = 2.5 + world.random.nextDouble() * 2.0;
			double px = MathHelper.clamp(lp.x + Math.cos(a) * r, AX0 + 1.0, AX1);
			double pz = MathHelper.clamp(lp.z + Math.sin(a) * r, AZ0 + 1.0, AZ1);
			Vec3d at = at(h.origin, h.rot, px, FLOOR, pz);
			FleshPatchEntity patch = new FleshPatchEntity(ChainsawMan.FLESH_PATCH, world);
			patch.refreshPositionAndAngles(at.x, at.y, at.z, world.random.nextFloat() * 360.0F, 0.0F);
			patch.setLife(FleshPatchEntity.LIFE, 10 * k);
			world.spawnEntity(patch);
		}
	}

	// ---- Cutting it.

	/** The hotel whose built shaft holds {@code pos}, if any. */
	static HomeBlockEntity arenaAt(ServerWorld world, BlockPos pos) {
		for (HomeBlockEntity h : HomeBlockEntity.LOADED) {
			if (h.getWorld() == world && h.run.shaft.built && contains(h, pos)) {
				return h;
			}
		}
		return null;
	}

	/** Whether the block at the local cell is the arena's flesh that can be cut: its walls, its ceiling, its growths (not its floor). */
	public static boolean cuttable(int x, int y, int z) {
		return y >= FLOOR && y <= FLOOR + HIGH && x >= AX0 - 1 && x <= AX1 + 1 && z >= AZ0 - 1 && z <= AZ1 + 1;
	}

	/**
	 * A blow on a block of the arena ({@code pos}, struck on its {@code face} at {@code hit}) by {@code by} for {@code amount}: if it is the
	 * devil's flesh, it is pain (half as much again where a squeeze left it soft), and it shows. True if it was the arena's (the blow is used).
	 */
	public static boolean cutBlock(ServerWorld world, ServerPlayerEntity by, BlockPos pos, Direction face, Vec3d hit, float amount) {
		HomeBlockEntity h = arenaAt(world, pos);
		if (h == null) {
			return false;
		}
		BlockPos lb = BlockPos.ofFloored(localOf(h.origin, h.rot, Vec3d.ofCenter(pos)));
		if (!cuttable(lb.getX(), lb.getY(), lb.getZ()) || world.getBlockState(pos).isAir()) {
			return false;
		}
		if (!(h.run.devil != null && world.getEntity(h.run.devil) instanceof EternityDevilEntity devil) || !devil.fightable() || devil.begStarted) {
			return true;
		}
		Vec3d lp = localOf(h.origin, h.rot, hit);
		boolean soft = soft(h.run.shaft, world.getTime(), lp);
		devil.arenaPain(amount * (soft ? MISS_BONUS : 1.0F), EternityDevilEntity.P_MASS, by);
		wound(world, devil, hit, face, by, soft);
		return true;
	}

	/** A slash's cut into the arena's walls or growths straight ahead of {@code player}, within {@code reach}: 1 if it cut, 0 if not. */
	public static int cutAhead(ServerPlayerEntity player, double reach, float damage) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(reach));
		BlockHitResult hit = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK || !(player.getWorld() instanceof ServerWorld world)) {
			return 0;
		}
		return cutBlock(world, player, hit.getBlockPos(), hit.getSide(), hit.getPos(), damage) ? 1 : 0;
	}

	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.7F, 0.03F, 0.05F), 1.4F);
	private static final BlockStateParticleEffect GORE = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());

	/**
	 * A cut shows: a lit wound where it landed (drawn by the clients for a few seconds), blood sprayed toward whoever cut, the flesh flinching
	 * and the devil crying out ({@code soft}: the wound is wider).
	 */
	public static void wound(ServerWorld world, EternityDevilEntity devil, Vec3d at, Direction face, ServerPlayerEntity by, boolean soft) {
		Vec3d toward = by == null ? new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ()) : by.getEyePos().subtract(at).normalize();
		Vec3d p = at.add(face.getOffsetX() * 0.1, face.getOffsetY() * 0.1, face.getOffsetZ() * 0.1);
		for (int k = 0; k < 16; k++) {
			Vec3d d = toward.add((world.random.nextDouble() - 0.5) * 0.9, (world.random.nextDouble() - 0.2) * 0.7, (world.random.nextDouble() - 0.5) * 0.9).normalize();
			world.spawnParticles(BLOOD, p.x, p.y, p.z, 0, d.x, d.y, d.z, 0.35 + world.random.nextDouble() * 0.35);
		}
		for (int k = 0; k < 6; k++) {
			Vec3d d = toward.add((world.random.nextDouble() - 0.5) * 0.6, world.random.nextDouble() * 0.5, (world.random.nextDouble() - 0.5) * 0.6).normalize();
			world.spawnParticles(GORE, p.x, p.y, p.z, 0, d.x, d.y, d.z, 0.4);
		}
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 4, 0.15, 0.15, 0.15, 0.2);
		devil.announceAt(HotelPayloads.Devil.WOUND, p, face.getId() + (soft ? 8 : 0));
		world.playSound(null, p.x, p.y, p.z, ChainsawSounds.HOTEL_CUT, SoundCategory.HOSTILE, 1.0F, 0.85F + world.random.nextFloat() * 0.3F);
		HomeBlockEntity h = home(world, devil);
		long now = world.getTime();
		if (h != null && now - h.run.shaft.criedAt >= 7) {
			h.run.shaft.criedAt = now;
			int stage = stage(devil);
			world.playSound(null, p.x, p.y, p.z, ChainsawSounds.DEVIL_HURT, SoundCategory.HOSTILE, 1.4F + 0.5F * stage, 1.05F - 0.15F * stage + world.random.nextFloat() * 0.1F);
		}
	}

	// ---- The devil's parts (its arms, heads and core): the saws cut them too (they are not living things, which is all a slash looked for).

	/**
	 * A slash's cut through the devil's parts in front of {@code player} ({@code aimed}: what was under the crosshair, or -1): each one
	 * within {@code reach} and ahead of the look is dealt {@code damage}. Returns how many were cut.
	 */
	public static int cutParts(ServerPlayerEntity player, double reach, double width, float damage, int aimed) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0F);
		int n = 0;
		for (net.minecraft.entity.Entity e : player.getWorld().getOtherEntities(player, player.getBoundingBox().expand(reach + 2.0),
				x -> (x instanceof ShaftArmEntity || x instanceof ShaftHeadEntity || x instanceof ShaftCoreEntity) && x.canHit())) {
			Box b = e.getBoundingBox();
			Vec3d near = new Vec3d(MathHelper.clamp(eye.x, b.minX, b.maxX), MathHelper.clamp(eye.y, b.minY, b.maxY), MathHelper.clamp(eye.z, b.minZ, b.maxZ));
			Vec3d to = near.subtract(eye);
			double d = to.length();
			boolean aimedAt = e.getId() == aimed && d <= reach + 1.0;
			if (!aimedAt && (d > reach || d >= 0.6 && to.normalize().dotProduct(look) < width && b.getCenter().subtract(eye).normalize().dotProduct(look) < width)) {
				continue;
			}
			if (e.damage(player.getDamageSources().playerAttack(player), damage)) {
				n++;
			}
		}
		return n;
	}

	/** A part of the devil cut ({@code part}: where; {@code missed}: it lies stunned after a miss, worth half as much again): pain, and the wound shows. */
	static void partCut(ServerWorld world, BlockPos origin, Vec3d at, net.minecraft.entity.damage.DamageSource source, float amount, boolean missed, int kind) {
		HomeBlockEntity h = Hotel.at(world, origin);
		Hotel.fought(h, source);
		if (h == null || !(h.run.devil != null && world.getEntity(h.run.devil) instanceof EternityDevilEntity devil)) {
			return;
		}
		ServerPlayerEntity by = source.getAttacker() instanceof ServerPlayerEntity p ? p : null;
		devil.arenaPain(amount * (missed ? MISS_BONUS : 1.0F), kind, source.getAttacker());
		Direction face = by == null ? Direction.UP : Direction.getFacing(by.getEyePos().subtract(at).x, by.getEyePos().subtract(at).y, by.getEyePos().subtract(at).z);
		wound(world, devil, at, face, by, missed);
	}
}
