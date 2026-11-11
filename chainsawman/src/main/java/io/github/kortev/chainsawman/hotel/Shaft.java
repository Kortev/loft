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
 * <p>The rule of the arena: <b>no damage without a tell, and no pain but from what attacks you</b>. Every attack shows itself at least
 * {@link #MIN_TELL} ticks before it lands, starts at least 4 blocks from whoever it is aimed at, and can be stepped out of. Every attack is a
 * limb: an arm slammed down a lane ({@link ShaftArmEntity#slam}), a head lunging ({@link ShaftHeadEntity}), half the room clenching shut
 * ({@link #squeeze}, whose thrust arm is left behind), a hand sweeping the floor ({@link ShaftArmEntity#grab}). One that misses is left stuck
 * {@link #STUCK} ticks, and then it can be torn off for good ({@link #TEAR}: two full sword blows, one saw slash); a stump stays. Cutting the
 * walls does nothing ({@link #cutBlock}). Enough limbs torn ({@link #NEED}) and it screams and its core is forced out of an end wall for a
 * short window ({@link #WINDOW}); a third of the core each window, three rounds, the long walls moving in each round. It grows with fear: a
 * torn limb grows back when nobody has torn one for a while, or someone is hit twice in a row ({@link #REGROW_IDLE}). After the third window
 * it breaks: it begs and holds its core out (the ending as it was, {@link EternityDevilEntity#arenaTo}).
 *
 * <p>Nothing inside it is drawn as a block: the walls are {@link ArenaFleshBlock}s nothing draws, and its flesh model is drawn over them.
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
	public static final float ARENA_SCALE = 33.0F;
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
	/** Rest after an attack resolves (ticks) in each round; from the second round attacks come in pairs (the second this soon after the first resolves). */
	public static final int[] REST = {26, 22, 14};
	public static final int PAIR_GAP = 6;
	/** While a limb lies stuck and nothing else is coming, nothing new starts for this long: the time to tear it off. */
	public static final int STUCK_REST = 26;
	private static final int START_GAP = 10;
	/** Creeping flesh: ticks between patches (plus a random part). Not in the arena any more (every attack there is a limb). */
	public static final int PATCH_GAP = 170;

	// ---- The limbs, the core, the rounds.
	/** A limb that missed is stuck this long (ticks), and this much cutting tears it off: two full iron sword blows, one saw slash. */
	public static final int STUCK = 50;
	public static final float TEAR = 12.0F;
	/** Limbs to tear off in each round (one player; one more for each other player). */
	public static final int[] NEED = {4, 5, 6};
	/** The core's window: the far wall bulging and splitting (its tell), then the core out on its stalk, chest high. */
	public static final int WINDOW_TELL = 20;
	public static final int WINDOW = 140;
	/** A third of the core: what each window takes to empty (one player: about five sword blows, two seconds of saws), scaled up for more. */
	public static final float WINDOW_HP = 30.0F;
	public static final float WINDOW_MORE = 0.6F;
	/** A window let go by: this many more limbs bring it back. */
	public static final int AGAIN = 2;
	/** It grows with fear: a torn limb grows back when nobody has torn one this long, or someone is hit twice within {@link #FEAR_HITS}; at most one each {@link #REGROW_GAP}. */
	public static final int REGROW_IDLE = 200;
	public static final int FEAR_HITS = 120;
	public static final int REGROW_GAP = 200;
	/** After a hit nothing new starts on that player this long. */
	public static final int SAFE_AFTER_HIT = 20;
	/** The long walls move in a block each from the second round, and again in the third, over this long (ticks). */
	public static final int WALL_MOVE = 30;
	/** Each round is a third of the arena's share of the bar: the limbs this much of it, the core the rest. */
	public static final float ROUND_SHARE = (1.0F - PAIN_TILT) / 3.0F;
	public static final float LIMB_PART = 0.35F;
	/** What a stump is of: an arm (a slam, a grab, a squeeze's thrust) or a head. */
	public static final byte LIMB_ARM = 0;
	public static final byte LIMB_HEAD = 1;
	/** The walls a stump can be in: the low-x end, the high-x end, the low-z long wall, the high-z long wall, the floor. */
	public static final int W_X0 = 0;
	public static final int W_X1 = 1;
	public static final int W_Z0 = 2;
	public static final int W_Z1 = 3;
	public static final int W_FLOOR = 4;
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
		/** The round (0 to 2), limbs torn in it, how many it needs now (more after a window let go by), and how many it needed at first. */
		public int round;
		public int torn;
		public int need;
		public int baseNeed;
		/** The core's window: the tick its tell began (-1 none), what is left of this round's third, what it was, and which end wall (0 low x). */
		public long windowAt = -1;
		public float windowHp;
		public float windowMax;
		public int coreSide;
		public UUID windowCore;
		/** The last limb torn, the last one grown back, the last laugh; someone was hit twice in a row (it grows a limb back). */
		public long lastTear;
		public long lastRegrow = -100000;
		public long laughAt = -1;
		public boolean fearHit;
		/** How far the long walls have moved in (blocks each), from where, and when it began (-1 not moving). */
		public int inset;
		public int wallFrom;
		public long wallAt = -1;
		/** Second rounds on: the second of a pair still to come. */
		public int pairLeft;
		/** Since when a limb has lain stuck with nothing else coming. */
		public long stuckSince;
		/** When each player was last hit. */
		public final Map<UUID, Long> hitAt = new HashMap<>();
		/** The stumps of torn limbs, in the walls and floor. */
		public final List<Stump> stumps = new ArrayList<>();
		/** The rounds have started (the first limb fight is set up as the arena is entered). */
		public boolean rounds;

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
			round = 0;
			torn = 0;
			need = 0;
			baseNeed = 0;
			windowAt = -1;
			windowCore = null;
			lastRegrow = -100000;
			laughAt = -1;
			fearHit = false;
			inset = 0;
			wallFrom = 0;
			wallAt = -1;
			pairLeft = 0;
			hitAt.clear();
			stumps.clear();
			rounds = false;
		}
	}

	/**
	 * A torn limb's stump: in wall {@code wall} ({@link #W_X0}...), at {@code u} along it (local z for the end walls, x for the long ones; for
	 * the floor its local x) and {@code v} (its height over the floor; for the floor its local z); what it was, in which round, when.
	 */
	public record Stump(int wall, double u, double v, byte kind, int round, long at) {
	}

	// ---- The design.

	/** The devil's inside: walls nothing draws (its flesh model is drawn over them). */
	static BlockState flesh() {
		return ChainsawMan.ARENA_FLESH.getDefaultState();
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

	/** Every block of the shaft, by local place: the arena, the mouth, the shaft's four walls with the corridor's own blocks, the alcoves. */
	public static Map<HotelPlan.P, BlockState> design() {
		HotelPlan plan = HotelPlan.get();
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState lamp = Blocks.REDSTONE_LAMP.getDefaultState().with(RedstoneLampBlock.LIT, true);
		BlockState cream = plan.at(1, Arena.Y0 + 1, 4);
		BlockState light = Blocks.LIGHT.getDefaultState();
		Map<HotelPlan.P, BlockState> m = new LinkedHashMap<>();
		// The arena: a shell two thick (the floor three) that nothing draws, its flesh model over it, and its inside.
		BlockState shell = flesh();
		for (int x = AX0 - 2; x <= AX1 + 2; x++) {
			for (int z = AZ0 - 2; z <= AZ1 + 2; z++) {
				for (int y = FLOOR - 3; y <= FLOOR + HIGH + 1; y++) {
					put(m, x, y, z, shell);
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
		// The throat: the mouth's 3 x 3 straight down through the arena's ceiling, lit.
		for (int x = IX0; x <= IX1; x++) {
			for (int z = IZ0; z <= IZ1; z++) {
				for (int y = FLOOR + HIGH; y <= BASE - 1; y++) {
					put(m, x, y, z, air);
				}
			}
		}
		put(m, 19, FLOOR + HIGH + 1, 6, light.with(net.minecraft.block.LightBlock.LEVEL_15, 15));
		// The mouth's hollow: a shell nothing draws (its flesh, the mass of fused arms, drawn over it), the mouth the shaft's 3 x 3 in it.
		for (int x = CX0 - 1; x <= CX1 + 1; x++) {
			for (int z = CZ0 - 1; z <= CZ1 + 1; z++) {
				for (int y = BASE - 1; y <= BASE + 5; y++) {
					boolean inside = x >= CX0 && x <= CX1 && z >= CZ0 && z <= CZ1 && y >= BASE && y <= BASE + 4;
					boolean mouth = x >= IX0 && x <= IX1 && z >= IZ0 && z <= IZ1 && (y == BASE - 1 || y == BASE + 5);
					put(m, x, y, z, inside || mouth ? air : shell);
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
		Vec3d a = HotelBuilder.at(h.origin, h.rot, AX0 - 3.0, FLOOR - 4.0, AZ0 - 3.0);
		Vec3d b = HotelBuilder.at(h.origin, h.rot, AX1 + 4.0, TOP + 3.0, AZ1 + 4.0);
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
		return HotelBuilder.at(h.origin, h.rot, 30.5, Arena.Y0, 6.5);
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
		for (ShaftCoreEntity e : world.getEntitiesByType(ChainsawMan.SHAFT_CORE, box, ShaftCoreEntity::window)) {
			e.discard();
		}
		// The long walls moved in stood where the arena was empty: empty again (what was there before is put back next).
		for (int x = AX0; x <= AX1; x++) {
			for (int z = AZ0; z <= AZ1; z++) {
				for (int y = FLOOR; y < FLOOR + HIGH; y++) {
					BlockPos at = HotelBuilder.world(h.origin, h.rot, x, y, z);
					if (world.getBlockState(at).isOf(ChainsawMan.ARENA_FLESH)) {
						world.setBlockState(at, Blocks.AIR.getDefaultState(), FLAGS);
					}
				}
			}
		}
		st.inset = 0;
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
		Vec3d mid = HotelBuilder.at(h.origin, h.rot, MID_X, FLOOR + 3.0, MID_Z);
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
		h.run.shaft.windowAt = -1;
		calm(world, worldBox(h));
		for (FleshPatchEntity e : world.getEntitiesByType(ChainsawMan.FLESH_PATCH, worldBox(h), x -> true)) {
			e.discard();
		}
		for (ShaftCoreEntity e : world.getEntitiesByType(ChainsawMan.SHAFT_CORE, worldBox(h), ShaftCoreEntity::window)) {
			e.drawBack();
		}
		sync(h, devil);
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
		Vec3d mid = HotelBuilder.at(h.origin, h.rot, 24.0, Arena.Y0 + 1.0, 6.5);
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
		Vec3d slide = HotelBuilder.dir(h.rot, 1.0, 0.0, 0.0);
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
			Vec3d at = HotelBuilder.at(h.origin, h.rot, 20.0, Arena.Y0 + 1.5, 6.5);
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
		st.rounds = false;
		st.stumps.clear();
		st.inset = 0;
		st.wallAt = -1;
		st.windowAt = -1;
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

	/** Which round of the arena's fight it is (0, 1, 2), as the server last told (on either side). */
	public static int stage(EternityDevilEntity devil) {
		return MathHelper.clamp(devil.arena().getInt("round"), 0, 2);
	}

	/** The low long wall's inner face (local z), where it is now moved to; and the high one's. */
	public static double z0(State st) {
		return AZ0 + st.inset;
	}

	public static double z1(State st) {
		return AZ1 + 1 - st.inset;
	}

	/** Where the bar stands for the fight so far: a third of the arena's share a round, the limbs part of it, the core the rest. */
	public static float progress(State st) {
		float limbs = st.baseNeed <= 0 ? 0.0F : Math.min(1.0F, st.torn / (float) st.baseNeed);
		float core = st.windowMax <= 0.0F ? 0.0F : 1.0F - Math.max(0.0F, st.windowHp) / st.windowMax;
		return PAIN_TILT + ROUND_SHARE * (Math.min(st.round, 3) + LIMB_PART * limbs + (1.0F - LIMB_PART) * core);
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
			if (lp.y > FLOOR + 0.3 && p.getVelocity().y < -0.9) {
				// Down the shaft and through the throat the fall is held to a steady speed (a soft landing, and time to see the mouth coming).
				p.setVelocity(p.getVelocity().x * 0.6, -0.9, p.getVelocity().z * 0.6);
				p.velocityModified = true;
			}
			if (inMouth(lp) && !overMouth(lp)) {
				// On the fused arms round the mouth: they pass you in.
				Vec3d to = HotelBuilder.dir(h.rot, MID_X - lp.x, 0.0, MID_Z - lp.z);
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
		if (!st.rounds) {
			st.rounds = true;
			startRound(world, h, devil, 0, Math.max(1, arena.size()), now);
		}
		wallsTick(world, h, arena, now);
		if (st.windowAt >= 0) {
			windowTick(world, h, devil, arena, now);
		} else {
			regrowTick(world, h, devil, now);
			director(world, h, devil, arena, now);
		}
		squeezeTick(world, h, devil, now);
	}

	// ---- The rounds: limbs torn, the core's window, the walls moving in, limbs growing back.

	/** Round {@code round} begins ({@code players}: how many are fighting): limbs to tear, a third of the core, the long walls in a block more. */
	public static void startRound(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, int round, int players, long now) {
		State st = h.run.shaft;
		st.round = round;
		st.torn = 0;
		st.baseNeed = NEED[Math.min(round, NEED.length - 1)] + Math.max(0, players - 1);
		st.need = st.baseNeed;
		st.windowMax = WINDOW_HP * (1.0F + WINDOW_MORE * Math.max(0, players - 1));
		st.windowHp = st.windowMax;
		st.windowAt = -1;
		st.lastTear = now;
		st.pairLeft = round >= 1 ? 1 : 0;
		if (round > 0) {
			st.wallFrom = st.inset;
			st.inset = Math.min(2, round);
			st.wallAt = now;
			Vec3d mid = at(h.origin, h.rot, MID_X, FLOOR + 3.0, MID_Z);
			world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.DEVIL_GROW, SoundCategory.HOSTILE, 4.0F, 0.45F);
			world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 4.0F, 0.6F);
		}
		devil.arenaTo(progress(st), EternityDevilEntity.P_ARM);
		sync(h, devil);
	}

	/** The long walls closing in (their model and the blocks behind it): anyone in their way is pushed toward the middle, and they stand. */
	private static void wallsTick(ServerWorld world, HomeBlockEntity h, List<ServerPlayerEntity> arena, long now) {
		State st = h.run.shaft;
		if (st.wallAt < 0) {
			return;
		}
		long t = now - st.wallAt;
		double k = MathHelper.clamp(t / (double) WALL_MOVE, 0.0, 1.0);
		double in = st.wallFrom + (st.inset - st.wallFrom) * k;
		for (ServerPlayerEntity p : arena) {
			Vec3d lp = local(h, p);
			double push = lp.z < AZ0 + in + 0.45 ? 1.0 : lp.z > AZ1 + 1 - in - 0.45 ? -1.0 : 0.0;
			if (push != 0.0) {
				Vec3d v = HotelBuilder.dir(h.rot, 0.0, 0.0, push * 0.25);
				p.setVelocity(p.getVelocity().x + v.x, p.getVelocity().y, p.getVelocity().z + v.z);
				p.velocityModified = true;
			}
		}
		if (t >= WALL_MOVE) {
			st.wallAt = -1;
			placeWalls(world, h);
		}
	}

	/** The blocks behind the long walls where they now stand (nothing draws them: the walls' model does), and nobody left inside them. */
	static void placeWalls(ServerWorld world, HomeBlockEntity h) {
		State st = h.run.shaft;
		for (int k = 0; k < st.inset; k++) {
			for (int z : new int[] {AZ0 + k, AZ1 - k}) {
				for (int x = AX0; x <= AX1; x++) {
					for (int y = FLOOR; y < FLOOR + HIGH; y++) {
						world.setBlockState(HotelBuilder.world(h.origin, h.rot, x, y, z), flesh(), FLAGS);
					}
				}
			}
		}
		for (ServerPlayerEntity p : world.getPlayers()) {
			Vec3d lp = local(h, p);
			if (inArena(lp) && (lp.z < z0(st) + 0.3 || lp.z > z1(st) - 0.3)) {
				Vec3d to = at(h.origin, h.rot, lp.x, Math.max(lp.y, FLOOR), MathHelper.clamp(lp.z, z0(st) + 0.6, z1(st) - 0.6));
				p.requestTeleport(to.x, to.y, to.z);
			}
		}
	}

	/** Which wall (or the floor) a local point is at. */
	static int wallOf(State st, Vec3d lp) {
		double[] d = {Math.abs(lp.x - AX0), Math.abs(lp.x - (AX1 + 1)), Math.abs(lp.z - z0(st)), Math.abs(lp.z - z1(st))};
		int best = W_FLOOR;
		double least = 0.8;
		for (int i = 0; i < 4; i++) {
			if (d[i] < least) {
				least = d[i];
				best = i;
			}
		}
		return best;
	}

	/** A limb was torn off for good at {@code root} (world): a stump stays, it screams, the bar moves; enough of them and its core is forced out. */
	public static void torn(ServerWorld world, BlockPos origin, Vec3d root, Vec3d dir, byte kind, @org.jetbrains.annotations.Nullable net.minecraft.entity.Entity by) {
		HomeBlockEntity h = Hotel.at(world, origin);
		if (h == null || !h.run.shaft.built || !(h.run.devil != null && world.getEntity(h.run.devil) instanceof EternityDevilEntity devil) || devil.begStarted) {
			return;
		}
		State st = h.run.shaft;
		long now = world.getTime();
		Vec3d lp = localOf(origin, h.rot, root);
		int wall = wallOf(st, lp);
		double u = wall == W_X0 || wall == W_X1 ? lp.z : lp.x;
		double v = wall == W_FLOOR ? lp.z : Math.max(0.0, lp.y - FLOOR);
		st.stumps.add(new Stump(wall, u, v, kind, st.round, now));
		st.torn++;
		st.lastTear = now;
		if (by != null) {
			Hotel.fought(world, devil, by);
		}
		world.playSound(null, root.x, root.y + 1.0, root.z, ChainsawSounds.DEVIL_HURT, SoundCategory.HOSTILE, 3.0F, 0.55F);
		world.playSound(null, root.x, root.y + 1.0, root.z, ChainsawSounds.HOTEL_CUT, SoundCategory.HOSTILE, 2.0F, 0.6F);
		world.spawnParticles(BLOOD, root.x, root.y + 0.6, root.z, 40, 0.4, 0.4, 0.4, 0.2);
		world.spawnParticles(GORE, root.x, root.y + 0.6, root.z, 16, 0.3, 0.3, 0.3, 0.3);
		devil.arenaTo(progress(st), kind == LIMB_HEAD ? EternityDevilEntity.P_HEAD : EternityDevilEntity.P_ARM);
		if (st.windowAt < 0 && st.torn >= st.need) {
			openWindow(world, h, devil, now);
		}
		sync(h, devil);
	}

	/** Enough limbs torn: it screams, the room convulses, the far end wall bulges and splits, and its core is forced out on a stalk. */
	public static void openWindow(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, long now) {
		State st = h.run.shaft;
		st.windowAt = now;
		// (Out of the end wall farther from the players: they have to cross to it.)
		double mean = 0.0;
		int n = 0;
		for (ServerPlayerEntity p : world.getPlayers()) {
			Vec3d lp = local(h, p);
			if (inArena(lp)) {
				mean += lp.x;
				n++;
			}
		}
		st.coreSide = n == 0 ? 1 : mean / n < MID_X ? 1 : 0;
		Vec3d wall = at(h.origin, h.rot, st.coreSide == 0 ? AX0 + 0.5 : AX1 + 0.5, FLOOR + 2.0, MID_Z);
		Vec3d mid = at(h.origin, h.rot, MID_X, FLOOR + 3.0, MID_Z);
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.DEVIL_SCREAM, SoundCategory.HOSTILE, 5.0F, 0.9F);
		world.playSound(null, wall.x, wall.y, wall.z, ChainsawSounds.DEVIL_GROW, SoundCategory.HOSTILE, 4.0F, 0.6F);
		world.playSound(null, wall.x, wall.y, wall.z, ChainsawSounds.HOTEL_HEARTBEAT, SoundCategory.HOSTILE, 4.0F, 0.8F);
		sync(h, devil);
	}

	/** Where a window's core is (local): out of its end wall on the stalk, chest high, in the middle of the wall. */
	public static Vec3d corePlace(int side) {
		return new Vec3d(side == 0 ? AX0 + 1.7 : AX1 + 1 - 1.7, FLOOR + 0.55, MID_Z);
	}

	/** The window: the core out after its tell, drawn back when its time is up (two more limbs bring it back). */
	private static void windowTick(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, List<ServerPlayerEntity> arena, long now) {
		State st = h.run.shaft;
		long t = now - st.windowAt;
		if (t == WINDOW_TELL) {
			ShaftCoreEntity core = new ShaftCoreEntity(ChainsawMan.SHAFT_CORE, world);
			Vec3d at = at(h.origin, h.rot, corePlace(st.coreSide).x, corePlace(st.coreSide).y, corePlace(st.coreSide).z);
			// (It faces out of its wall, into the room.)
			float yaw = HotelBuilder.alongYaw(h.rot) + (st.coreSide == 0 ? 0.0F : 180.0F);
			core.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
			core.devil = devil.getUuid();
			core.setWindow(h.origin);
			world.spawnEntity(core);
			core.markBorn();
			st.windowCore = core.getUuid();
			world.playSound(null, at.x, at.y + 1.0, at.z, ChainsawSounds.DEVIL_BITE, SoundCategory.HOSTILE, 3.0F, 0.5F);
			world.spawnParticles(BLOOD, at.x, at.y + 1.0, at.z, 50, 0.6, 0.6, 0.6, 0.25);
		}
		if (t >= WINDOW_TELL + WINDOW) {
			closeWindow(world, h, devil, now);
		}
	}

	/** The window let go by: the core drawn back in; two more limbs bring it out again. */
	private static void closeWindow(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, long now) {
		State st = h.run.shaft;
		backCore(world, st);
		st.windowAt = -1;
		st.need = st.torn + AGAIN;
		st.lastTear = now;
		st.nextAttack = Math.max(st.nextAttack, now + 20);
		Vec3d mid = at(h.origin, h.rot, MID_X, FLOOR + 3.0, MID_Z);
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.DEVIL_LAUGH, SoundCategory.HOSTILE, 3.0F, 1.0F);
		st.laughAt = now;
		sync(h, devil);
	}

	private static void backCore(ServerWorld world, State st) {
		if (st.windowCore != null && world.getEntity(st.windowCore) instanceof ShaftCoreEntity c) {
			c.drawBack();
		}
		st.windowCore = null;
	}

	/** A blow on a window's core: a third of the core a window; emptied, the round is won (the third one breaks it: it begs). */
	static boolean coreHit(ServerWorld world, ShaftCoreEntity core, net.minecraft.entity.damage.DamageSource source, float amount) {
		HomeBlockEntity h = Hotel.at(world, core.origin);
		if (h == null || !(h.run.devil != null && world.getEntity(h.run.devil) instanceof EternityDevilEntity devil) || devil.begStarted) {
			return false;
		}
		State st = h.run.shaft;
		if (st.windowAt < 0 || st.windowHp <= 0.0F) {
			return false;
		}
		long now = world.getTime();
		Hotel.fought(h, source);
		st.windowHp -= amount;
		ServerPlayerEntity by = source.getAttacker() instanceof ServerPlayerEntity p ? p : null;
		Vec3d hit = core.getPos().add(0.0, 0.8, 0.0);
		wound(world, devil, hit, Direction.UP, by, true);
		if (st.windowHp > 0.0F) {
			devil.arenaTo(progress(st), EternityDevilEntity.P_MOUTH);
			sync(h, devil);
			return true;
		}
		// Emptied: the core drawn back, the round won.
		st.windowHp = 0.0F;
		backCore(world, st);
		st.windowAt = -1;
		if (st.round >= 2) {
			// The third: it breaks. It begs, and holds its core out (the ending).
			st.round = 3;
			sync(h, devil);
			devil.arenaTo(1.0F, EternityDevilEntity.P_MOUTH);
			return true;
		}
		int players = 0;
		for (ServerPlayerEntity p : world.getPlayers()) {
			players += inArena(local(h, p)) ? 1 : 0;
		}
		Vec3d mid = at(h.origin, h.rot, MID_X, FLOOR + 3.0, MID_Z);
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.DEVIL_SCREAM, SoundCategory.HOSTILE, 5.0F, 0.7F);
		startRound(world, h, devil, st.round + 1, Math.max(1, players), now);
		st.nextAttack = Math.max(st.nextAttack, now + WALL_MOVE + 20);
		return true;
	}

	/** It grows with fear: nobody has torn a limb for a while, or someone was hit twice in a row: it laughs, and a torn limb grows back. */
	private static void regrowTick(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, long now) {
		State st = h.run.shaft;
		if (st.torn <= 0) {
			st.fearHit = false;
			return;
		}
		if (now - st.lastRegrow < REGROW_GAP || now - st.lastTear < REGROW_IDLE && !st.fearHit) {
			return;
		}
		st.fearHit = false;
		st.lastRegrow = now;
		st.torn--;
		for (int i = st.stumps.size() - 1; i >= 0; i--) {
			if (st.stumps.get(i).round() == st.round) {
				st.stumps.remove(i);
				break;
			}
		}
		st.laughAt = now;
		Vec3d mid = at(h.origin, h.rot, MID_X, FLOOR + 3.0, MID_Z);
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.DEVIL_LAUGH, SoundCategory.HOSTILE, 4.0F, 1.0F);
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.DEVIL_REGEN, SoundCategory.HOSTILE, 3.0F, 0.7F);
		Hotel.speak(world, h.run.inside, "message.chainsawman.devil.laugh", 50, 1);
		devil.arenaTo(progress(st), EternityDevilEntity.P_ARM);
		sync(h, devil);
	}

	/** A player hit by an attack: nothing new starts on them for a moment; hit twice in a row, its fear grows (a limb grows back). */
	public static void hurt(ServerWorld world, BlockPos origin, ServerPlayerEntity p) {
		HomeBlockEntity h = Hotel.at(world, origin);
		if (h == null) {
			return;
		}
		State st = h.run.shaft;
		long now = world.getTime();
		Long last = st.hitAt.put(p.getUuid(), now);
		if (last != null && now - last <= FEAR_HITS && now - last > 2) {
			st.fearHit = true;
		}
	}

	/** Tells the clients what they draw of the fight: the round, the window, the walls, the stumps, the laugh. */
	static void sync(HomeBlockEntity h, EternityDevilEntity devil) {
		State st = h.run.shaft;
		net.minecraft.nbt.NbtCompound tag = new net.minecraft.nbt.NbtCompound();
		tag.putInt("round", st.round);
		tag.putInt("torn", st.torn);
		tag.putInt("need", st.need);
		tag.putLong("windowAt", st.windowAt);
		tag.putFloat("window", st.windowMax <= 0.0F ? 0.0F : st.windowHp / st.windowMax);
		tag.putInt("coreSide", st.coreSide);
		tag.putInt("inset", st.inset);
		tag.putInt("wallFrom", st.wallFrom);
		tag.putLong("wallAt", st.wallAt);
		tag.putLong("laughAt", st.laughAt);
		net.minecraft.nbt.NbtList list = new net.minecraft.nbt.NbtList();
		for (Stump s : st.stumps) {
			net.minecraft.nbt.NbtCompound c = new net.minecraft.nbt.NbtCompound();
			c.putInt("w", s.wall());
			c.putDouble("u", s.u());
			c.putDouble("v", s.v());
			c.putByte("k", s.kind());
			c.putLong("at", s.at());
			list.add(c);
		}
		tag.put("stumps", list);
		devil.setArena(tag);
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

	/**
	 * The attacks: one tell at a time on each player (never a second before the first has landed or missed, nothing new on anyone just hit),
	 * as many out as there are players; a rest after each resolves, and from the second round the next of a pair comes at once.
	 */
	private static void director(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, List<ServerPlayerEntity> arena, long now) {
		State st = h.run.shaft;
		Box box = worldBox(h);
		int round = Math.min(st.round, REST.length - 1);
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
			if (st.round >= 1 && st.pairLeft > 0) {
				st.pairLeft--;
				st.nextAttack = Math.max(st.nextAttack, now + PAIR_GAP);
			} else {
				st.nextAttack = Math.max(st.nextAttack, now + REST[round]);
				st.pairLeft = st.round >= 1 ? 1 : 0;
			}
		}
		st.lastActive = active;
		if (active == 0 && stuckOut(world, box)) {
			// A limb lies stuck: time to tear it off before anything new starts (a pair waits too).
			st.nextAttack = Math.max(st.nextAttack, now + 1);
			if (now - st.stuckSince < STUCK_REST) {
				return;
			}
		} else {
			st.stuckSince = now;
		}
		int max = Math.min(4, arena.size());
		if (arena.isEmpty() || now < st.nextAttack || active >= max || st.wallAt >= 0) {
			return;
		}
		List<ServerPlayerEntity> free = new ArrayList<>();
		for (ServerPlayerEntity p : arena) {
			Long hit = st.hitAt.get(p.getUuid());
			if (!aimed.contains(p.getUuid()) && !io.github.kortev.chainsawman.form.Downed.is(p) && !heldByArm(world, box, p) && (hit == null || now - hit >= SAFE_AFTER_HIT)) {
				free.add(p);
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
				case SLAM -> !slamOut || arena.size() > 1;
				case LUNGE -> heads < 2;
				// (The squeeze takes in half the room: only when nothing else is coming at anyone.)
				case SQUEEZE -> active == 0 && !squeeze && st.squeezeHalf < 0;
				default -> !grabOut && !squeeze;
			};
			if (ok && launch(world, h, devil, kind, target, now)) {
				st.nextAttack = now + START_GAP;
				st.lastActive = active + 1;
				return;
			}
		}
	}

	/** Whether any limb lies stuck now (it can be torn off). */
	private static boolean stuckOut(ServerWorld world, Box box) {
		return !world.getEntitiesByType(ChainsawMan.SHAFT_ARM, box, ShaftArmEntity::stuck).isEmpty()
				|| !world.getEntitiesByType(ChainsawMan.SHAFT_HEAD, box, ShaftHeadEntity::stuck).isEmpty();
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
		double z0 = z0(h.run.shaft);
		double z1 = z1(h.run.shaft);
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
			sz = MathHelper.clamp(lp.z, z0 + 1.5, z1 - 1.5);
			len = MathHelper.clamp(Math.abs(lp.x - sx) + 3.0, 7.0, AX1 + 1 - AX0);
		} else {
			boolean fromLow = lp.z - z0 > z1 - lp.z;
			sz = fromLow ? z0 : z1;
			dirZ = fromLow ? 1.0 : -1.0;
			dirX = 0.0;
			sx = MathHelper.clamp(lp.x, AX0 + 1.5, AX1 + 1 - 1.5);
			len = MathHelper.clamp(Math.abs(lp.z - sz) + 3.0, Math.min(7.0, z1 - z0), z1 - z0);
		}
		Vec3d at = at(h.origin, h.rot, sx, FLOOR, sz);
		Vec3d dir = HotelBuilder.dir(h.rot, dirX, 0.0, dirZ);
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
			if (hx < AX0 + 1.0 || hx > AX1 || hz < z0(h.run.shaft) + 1.0 || hz > z1(h.run.shaft) - 1.0) {
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
		boolean low = lp.z - z0(h.run.shaft) < z1(h.run.shaft) - lp.z;
		double wz = low ? z0(h.run.shaft) : z1(h.run.shaft);
		double side = lp.x + 4.0 <= AX1 - 0.5 && (lp.x - 4.0 < AX0 + 1.5 || world.random.nextBoolean()) ? 4.0 : -4.0;
		double px = MathHelper.clamp(lp.x + side, AX0 + 1.5, AX1 - 0.5);
		Vec3d at = at(h.origin, h.rot, px, FLOOR, wz);
		Vec3d in = HotelBuilder.dir(h.rot, 0.0, 0.0, low ? 1.0 : -1.0);
		// It sweeps from the side away from them, across, toward them and on.
		Vec3d toward = HotelBuilder.dir(h.rot, -Math.signum(side), 0.0, 0.0);
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
			boolean caught = false;
			for (ServerPlayerEntity p : world.getPlayers()) {
				Vec3d lp = local(h, p);
				if (!p.isAlive() || p.isSpectator() || p.isCreative() || !inArena(lp) || !inSqueeze(st.squeezeHalf, lp)) {
					continue;
				}
				p.damage(devil.getDamageSources().mobAttack(devil), SQUEEZE_DAMAGE);
				Vec3d push = HotelBuilder.dir(h.rot, st.squeezeHalf == 0 ? 1.1 : -1.1, 0.0, 0.0);
				p.setVelocity(push.x, 0.35, push.z);
				p.velocityModified = true;
				hurt(world, h.origin, p);
				caught = true;
			}
			if (!caught) {
				// It caught nobody: the arm it thrust out of the end wall is left stuck on the floor, to be torn off.
				Vec3d from = at(h.origin, h.rot, st.squeezeHalf == 0 ? AX0 : AX1 + 1, FLOOR, MID_Z);
				Vec3d dir = HotelBuilder.dir(h.rot, st.squeezeHalf == 0 ? 1.0 : -1.0, 0.0, 0.0);
				ShaftArmEntity arm = new ShaftArmEntity(ChainsawMan.SHAFT_ARM, world);
				arm.lying(h.origin, h.rot, from, dir, SQUEEZE_REACH - 0.6);
				world.spawnEntity(arm);
			}
		}
		if (t > SQUEEZE_TELL + SQUEEZE_SHUT + SQUEEZE_SOFT) {
			st.squeezeHalf = -1;
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

	/** Whether the block at the local cell is the arena's flesh: its walls, its ceiling (not its floor). */
	public static boolean cuttable(int x, int y, int z) {
		return y >= FLOOR && y <= FLOOR + HIGH && x >= AX0 - 1 && x <= AX1 + 1 && z >= AZ0 - 1 && z <= AZ1 + 1;
	}

	/**
	 * A blow on a block of the arena ({@code pos}) by {@code by}: cutting its walls does nothing (pain comes only from tearing off what attacks
	 * you, and from its core). True if it was the arena's (the blow is used up on it).
	 */
	public static boolean cutBlock(ServerWorld world, ServerPlayerEntity by, BlockPos pos, Direction face, Vec3d hit, float amount) {
		HomeBlockEntity h = arenaAt(world, pos);
		if (h == null) {
			return false;
		}
		BlockPos lb = BlockPos.ofFloored(localOf(h.origin, h.rot, Vec3d.ofCenter(pos)));
		return cuttable(lb.getX(), lb.getY(), lb.getZ()) && !world.getBlockState(pos).isAir();
	}

	/** A slash into the arena's walls straight ahead of {@code player}: it does nothing (0: nothing was cut). */
	public static int cutAhead(ServerPlayerEntity player, double reach, float damage) {
		return 0;
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

	/** A limb cut ({@code at}: where; {@code tearing}: it is stuck, and being torn at): whoever cut it fought, and the wound shows. No pain: tearing it off is. */
	static void limbCut(ServerWorld world, BlockPos origin, Vec3d at, net.minecraft.entity.damage.DamageSource source, boolean tearing) {
		HomeBlockEntity h = Hotel.at(world, origin);
		Hotel.fought(h, source);
		if (h == null || !(h.run.devil != null && world.getEntity(h.run.devil) instanceof EternityDevilEntity devil)) {
			return;
		}
		ServerPlayerEntity by = source.getAttacker() instanceof ServerPlayerEntity p ? p : null;
		Direction face = by == null ? Direction.UP : Direction.getFacing(by.getEyePos().subtract(at).x, by.getEyePos().subtract(at).y, by.getEyePos().subtract(at).z);
		wound(world, devil, at, face, by, tearing);
	}

	/** A blow on a limb that is not stuck: it flinches, and that is all. */
	static void flinch(ServerWorld world, BlockPos origin, Vec3d at, net.minecraft.entity.damage.DamageSource source) {
		HomeBlockEntity h = Hotel.at(world, origin);
		Hotel.fought(h, source);
		world.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.05);
		world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_MUD_HIT, SoundCategory.HOSTILE, 1.2F, 0.7F + world.random.nextFloat() * 0.2F);
	}
}