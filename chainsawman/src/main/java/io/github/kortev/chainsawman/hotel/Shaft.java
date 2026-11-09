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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;

/**
 * The shaft: when the Eternity Devil's fear is full (it grows with the players' own: time, and every time one is hurt or downed) the floor
 * turns 90 degrees. The corridor of floor 8, stood on its end, is built as real blocks in a reserved space above the hotel's roof (the
 * carpet one wall, the lamps' ceiling the opposite, the doors' walls the other two, the doors opened into alcoves that are the ledges the
 * players stand on), {@link #DEPTH} deep, and the devil is at the bottom of it: a chamber of flesh whose floor is the mass (walkable) with
 * the mouth's pit in it. Every block it takes is kept (in the home's own data, {@link HomeBlockEntity#shaft}) and put back exactly when
 * the fight is over, like {@link Arena}.
 *
 * <p>Its attacks (small, quick, hard to avoid; never a big telegraphed slam): arms ({@link ShaftArmEntity}) rise up the shaft and take a
 * player off a ledge down onto the mass, heads ({@link ShaftHeadEntity}) bite whoever stands on the mass, flesh creeps on it
 * ({@link FleshPatchEntity}). The mouth swallows whoever goes into the pit: held, in the dark, taking damage, until they cut their way out
 * ({@link #cutHit}), which is a big dose of pain to the devil.
 *
 * <p>Local coordinates are the hotel's own (x along the corridor, z across, y up); the shaft is a column over the corridor's middle, so
 * a corridor's x is a height: x 36 (its far end, the devil's) the bottom.
 */
public final class Shaft {
	/** The walking level of the mass (the pit's rim): the floor's top blocks are at BASE - 1. Above the hotel's roof dressing. */
	public static final int BASE = HotelPlan.SY + 6;
	/** The shaft's height, as long as the corridor: the corridor's cell x is at BASE + DEPTH - x. */
	public static final int DEPTH = 36;
	/** The highest local y anyone may be at in it. */
	public static final int TOP = BASE + DEPTH + 3;
	/** The shaft's inside (3 x 3 over the mass's west end) and its outer walls. */
	public static final int IX0 = 18;
	public static final int IX1 = 20;
	public static final int IZ0 = 5;
	public static final int IZ1 = 7;
	/** The mass's floor, wider than the shaft (3 blocks of it all round the pit): inside x 15-23, z 2-10, y BASE to BASE + 4. */
	public static final int CX0 = 15;
	public static final int CX1 = 23;
	public static final int CZ0 = 2;
	public static final int CZ1 = 10;
	/** The pit's middle cell: the pit is the shaft's own 3 x 3, straight under it, and 4 deep. */
	public static final int PIT_X = 19;
	public static final int PIT_Z = 6;
	public static final int PIT_DEPTH = 4;
	/** How far above the mass players start (blocks): near enough that looking down the devil is big. */
	public static final int NEAR_MIN = 10;
	public static final int NEAR_MAX = 16;
	/** Where the devil stands: the east end of the chamber's floor, at the pit's bottom (its box is the floor and the pit). */
	public static final double MASS_X = 24.0;
	public static final double MASS_Z = 6.5;
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

	// ---- The numbers.
	/** Fear (0 to 1): fills with time ({@link #FEAR_TICK}) and each time a player is hurt ({@link #FEAR_HURT}) or downed ({@link #FEAR_DOWN}); the floor turns at 1, no sooner than {@link #TILT_MIN} ticks into the fight. */
	public static final float FEAR_TICK = 1.0F / 1200.0F;
	public static final float FEAR_HURT = 0.06F;
	public static final float FEAR_DOWN = 0.2F;
	public static final int TILT_MIN = 800;
	/** The turn takes this long (ticks) from the camera starting to roll to the players being in the shaft. */
	public static final int TILT_TICKS = 44;
	/** Ticks between attacks (plus a random part), and how many of each may be out. */
	public static final int HEAD_GAP = 24;
	public static final int ARM_GAP = 60;
	public static final int PATCH_GAP = 120;
	public static final int MAX_HEADS = 4;
	/** Swallowed: damage every 10 ticks, the damage dealt in the mouth that cuts you out (saws 13 a blow: three blows; a sword about 7: six), and the pain the devil takes as you burst out. */
	public static final float SWALLOW_DAMAGE = 1.0F;
	public static final float CUT_NEED = 36.0F;
	public static final float PAIN_BURST = 30.0F;
	/** Hits from someone standing on the mass do this much more pain (dropping down is the strong, risky play). */
	public static final float MASS_BONUS = 1.5F;

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
		public final Map<UUID, Held> swallowed = new HashMap<>();
		/** Who has just burst out of the mouth, until when (they are still over the pit for a moment, and are not swallowed again). */
		public final Map<UUID, Long> immune = new HashMap<>();
		/** The shaft's blocks still to be put down (they go in over the turn, a few hundred a tick), and how many are. */
		public List<Map.Entry<HotelPlan.P, BlockState>> queue;
		public int placed;
		public long nextArm;
		public long nextHead;
		public long nextPatch;

		public void reset() {
			fear = 0.0F;
			tiltAt = -1;
			built = false;
			hp.clear();
			down.clear();
			swallowed.clear();
			immune.clear();
			queue = null;
			placed = 0;
		}
	}

	/** Someone in the mouth: the damage they have dealt toward cutting out. */
	public static final class Held {
		public float cut;
		public long since;
	}

	// ---- The design.

	/** The mass: dark grey-brown (mud, packed mud, brown and grey terracotta), a few veins of dark red. Never bright red. */
	static BlockState flesh(int x, int y, int z) {
		int h = ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 255;
		Block b = h < 40 ? Blocks.MUD : h < 100 ? Blocks.PACKED_MUD : h < 150 ? Blocks.BROWN_TERRACOTTA : h < 238 ? Blocks.GRAY_TERRACOTTA : Blocks.NETHER_WART_BLOCK;
		return b.getDefaultState();
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

	/** The ledges, lowest first: one for each room door of the loop floor that has room above the chamber. */
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

	/**
	 * The ledges players start on: {@link #NEAR_MIN} to {@link #NEAR_MAX} blocks above the mass, so looking down it is big (the lowest ledges
	 * when there are none as low as that).
	 */
	public static List<Ledge> lowLedges() {
		List<Ledge> all = ledges();
		List<Ledge> near = new ArrayList<>();
		for (Ledge l : all) {
			if (l.y() - BASE >= NEAR_MIN && l.y() - BASE <= NEAR_MAX) {
				near.add(l);
			}
		}
		return near.isEmpty() ? all : near;
	}

	/** Every block of the shaft, by local place: the chamber, the pit, the shaft's four walls with the corridor's own blocks, the alcoves. */
	public static Map<HotelPlan.P, BlockState> design() {
		HotelPlan plan = HotelPlan.get();
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState lamp = Blocks.REDSTONE_LAMP.getDefaultState().with(RedstoneLampBlock.LIT, true);
		BlockState glow = Blocks.SHROOMLIGHT.getDefaultState();
		BlockState cream = plan.at(1, Arena.Y0 + 1, 4);
		Map<HotelPlan.P, BlockState> m = new LinkedHashMap<>();
		// The chamber: the mass, a shell of dark grey-brown flesh with the floor of it six thick, and its inside.
		BlockState light = Blocks.LIGHT.getDefaultState();
		for (int x = CX0 - 1; x <= CX1 + 1; x++) {
			for (int z = CZ0 - 1; z <= CZ1 + 1; z++) {
				for (int y = BASE - 6; y <= BASE + 5; y++) {
					put(m, x, y, z, flesh(x, y, z));
				}
			}
		}
		for (int x = CX0; x <= CX1; x++) {
			for (int z = CZ0; z <= CZ1; z++) {
				for (int y = BASE; y <= BASE + 4; y++) {
					put(m, x, y, z, air);
				}
			}
		}
		// A dim warm light in the chamber (invisible: the lamps above do the rest).
		for (int[] g : new int[][] {{CX0 + 1, BASE + 3, CZ0 + 1}, {CX0 + 1, BASE + 3, CZ1 - 1}, {CX1 - 1, BASE + 3, CZ0 + 1}, {CX1 - 1, BASE + 3, CZ1 - 1}}) {
			put(m, g[0], g[1], g[2], light.with(net.minecraft.block.LightBlock.LEVEL_15, 9));
		}
		// The pit: the mouth, straight under the shaft, 3 x 3 and four deep, lined with dark red and faintly lit from the bottom.
		for (int x = PIT_X - 2; x <= PIT_X + 2; x++) {
			for (int z = PIT_Z - 2; z <= PIT_Z + 2; z++) {
				boolean rim = x == PIT_X - 2 || x == PIT_X + 2 || z == PIT_Z - 2 || z == PIT_Z + 2;
				for (int y = BASE - PIT_DEPTH - 1; y <= BASE - 1; y++) {
					if (rim) {
						put(m, x, y, z, (x + z + y) % 3 == 0 ? Blocks.NETHER_WART_BLOCK.getDefaultState() : Blocks.RED_TERRACOTTA.getDefaultState());
					} else if (y == BASE - PIT_DEPTH - 1) {
						put(m, x, y, z, Blocks.NETHER_WART_BLOCK.getDefaultState());
					} else {
						put(m, x, y, z, air);
					}
				}
			}
		}
		put(m, PIT_X, BASE - PIT_DEPTH, PIT_Z, light.with(net.minecraft.block.LightBlock.LEVEL_15, 7));
		// The way up out of the chamber: the shaft's inside meets its roof.
		for (int x = IX0; x <= IX1; x++) {
			for (int z = IZ0; z <= IZ1; z++) {
				put(m, x, BASE + 5, z, air);
			}
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
		// The top.
		for (int x = IX0 - 1; x <= IX1 + 1; x++) {
			for (int z = IZ0 - 1; z <= IZ1 + 1; z++) {
				put(m, x, BASE + DEPTH, z, Blocks.SMOOTH_QUARTZ.getDefaultState());
			}
		}
		// The alcoves: each room's door opened into a ledge, three deep, three wide, three high, a lamp in its roof.
		BlockState floor = cream;
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
					put(m, x, l.y() - 1, l.wallZ() + dz * k, floor);
				}
			}
			put(m, 19, l.y() + 3, l.wallZ() + dz * 2, lamp);
		}
		return m;
	}

	// ---- Building it, and putting it back.

	/** The shaft's block box, local: what it holds and what is moved out of it when it goes. */
	private static Box worldBox(HomeBlockEntity h) {
		Vec3d a = HotelBuilder.world(h.origin, h.rot, CX0 - 2.0, BASE - 7.0, -1.0);
		Vec3d b = HotelBuilder.world(h.origin, h.rot, CX1 + 3.0, TOP + 3.0, 14.0);
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

	/** Puts down up to {@code count} more of the queued blocks (lowest first, so the mass is there first). */
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
		st.swallowed.clear();
		st.queue = null;
		if (h.shaft == null) {
			return;
		}
		// Out first: the players, what was dropped, and the mass's own creatures.
		Box box = worldBox(h);
		Vec3d to = out(h);
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (box.contains(p.getPos())) {
				p.stopRiding();
				p.networkHandler.requestTeleport(to.x, to.y, to.z, HotelBuilder.alongYaw(h.rot) + 180.0F, 0.0F);
				p.setVelocity(Vec3d.ZERO);
				p.fallDistance = 0.0F;
				send(p, -1, HotelPayloads.Devil.SWALLOW, 0);
			}
		}
		for (ItemEntity item : world.getEntitiesByType(EntityType.ITEM, box, e -> true)) {
			item.refreshPositionAndAngles(to.x + (world.random.nextDouble() - 0.5), to.y + 0.3, to.z + (world.random.nextDouble() - 0.5), 0.0F, 0.0F);
			item.setVelocity(Vec3d.ZERO);
		}
		for (SquadMemberEntity m : world.getEntitiesByType(ChainsawMan.SQUAD_MEMBER, box, e -> true)) {
			m.refreshPositionAndAngles(to.x, to.y, to.z, 0.0F, 0.0F);
		}
		for (ShaftArmEntity e : world.getEntitiesByType(ChainsawMan.SHAFT_ARM, box, x -> true)) {
			e.discard();
		}
		for (ShaftHeadEntity e : world.getEntitiesByType(ChainsawMan.SHAFT_HEAD, box, x -> true)) {
			e.discard();
		}
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
		Vec3d mid = HotelBuilder.world(h.origin, h.rot, 19.5, BASE + 12.0, 6.5);
		world.spawnParticles(ParticleTypes.POOF, mid.x, mid.y, mid.z, 30, 2.0, 8.0, 2.0, 0.02);
		world.playSound(null, to.x, to.y, to.z, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 3.0F, 0.6F);
	}

	// ---- Messages to the clients.

	static void send(ServerPlayerEntity p, int devil, byte kind, int ticks) {
		if (ServerPlayNetworking.canSend(p, HotelPayloads.Devil.ID)) {
			ServerPlayNetworking.send(p, new HotelPayloads.Devil(devil, kind, 0.0F, 0.0F, 0.0F, ticks));
		}
	}

	private static Vec3d local(HomeBlockEntity h, ServerPlayerEntity p) {
		return HotelBuilder.local(h.origin, h.rot, p.getPos());
	}

	private static HomeBlockEntity home(ServerWorld world, EternityDevilEntity devil) {
		return world.getBlockEntity(devil.homePos) instanceof HomeBlockEntity h ? h : null;
	}

	// ---- The fight.

	/** One tick of the shaft fight for the hotel: fear and the turn before it, the attacks and the mouth after. {@code players}: those inside, alive. */
	static void tick(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, List<ServerPlayerEntity> players, long now) {
		State st = h.run.shaft;
		if (st.built) {
			shaftTick(world, h, devil, players, now);
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
		if (st.fear >= 1.0F && now - r.fightAt >= TILT_MIN && !players.isEmpty()) {
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

	/** The two seconds of the turn: everyone slides toward the devil's end, then the shaft is there and so are they. */
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

	/** The shaft is built, the players are put in its doorways, looking down it, and the devil is at its foot. */
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
		st.nextArm = now + 50;
		st.nextHead = now + 30;
		st.nextPatch = now + 90;
		st.swallowed.clear();
		// The flesh creeping in the corridor goes with it, and the sky arena is shut.
		Box corridor = new Box(HotelBuilder.world(h.origin, h.rot, 0.0, Arena.Y0 - 2.0, 0.0), HotelBuilder.world(h.origin, h.rot, HotelPlan.SX, Arena.Y1 + 3.0, HotelPlan.SZ));
		for (FleshPatchEntity patch : world.getEntitiesByType(ChainsawMan.FLESH_PATCH, corridor, e -> true)) {
			patch.discard();
		}
		List<Ledge> ledges = lowLedges();
		int n = ledges.size();
		int i = 0;
		for (ServerPlayerEntity p : players) {
			place(p, h, ledges.get(i % n));
			i++;
		}
		for (SquadMemberEntity m : Squad.members(world, h)) {
			Ledge l = ledges.get(i % n);
			Vec3d at = HotelBuilder.world(h.origin, h.rot, 19.5, l.y(), l.wallZ() + (l.wallZ() == 4 ? -1.5 : 1.5));
			m.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
			i++;
		}
		Arena.mend(world, h);
		devil.enterShaft(HotelBuilder.world(h.origin, h.rot, MASS_X, BASE - PIT_DEPTH, MASS_Z));
		for (ServerPlayerEntity p : players) {
			send(p, devil.getId(), HotelPayloads.Devil.SHAFT, 1);
		}
		Vec3d mid = HotelBuilder.world(h.origin, h.rot, 19.5, BASE + 4.0, 6.5);
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.DEVIL_GROW, SoundCategory.HOSTILE, 4.0F, 0.6F);
	}

	/** Where along z someone stands on a ledge: at the lip of the doorway, over the shaft. */
	public static double edgeZ(Ledge l) {
		return l.wallZ() == 4 ? 4.95 : 8.05;
	}

	/** {@code p} in a ledge's doorway, looking across and down the shaft. */
	public static void place(ServerPlayerEntity p, HomeBlockEntity h, Ledge l) {
		Vec3d at = HotelBuilder.world(h.origin, h.rot, 19.5, l.y(), edgeZ(l));
		float yaw = HotelBuilder.alongYaw(h.rot) + (l.wallZ() == 4 ? 90.0F : -90.0F);
		p.stopRiding();
		p.networkHandler.requestTeleport(at.x, at.y, at.z, yaw, 55.0F);
		p.setVelocity(Vec3d.ZERO);
		p.velocityModified = true;
		p.fallDistance = 0.0F;
	}

	/** In the pit (the mouth): over its cells and below the rim. */
	private static boolean inPit(Vec3d lp) {
		return lp.x >= PIT_X - 1 && lp.x < PIT_X + 2 && lp.z >= PIT_Z - 1 && lp.z < PIT_Z + 2 && lp.y < BASE - 0.15 && lp.y > BASE - PIT_DEPTH - 2;
	}

	/** On the mass: in the chamber, down on its floor and not in the pit. */
	private static boolean onMass(Vec3d lp) {
		return lp.x >= CX0 && lp.x < CX1 + 1 && lp.z >= CZ0 && lp.z < CZ1 + 1 && lp.y >= BASE - 0.6 && lp.y < BASE + 3.0;
	}

	/** Whether a player stands on the mass (for the bonus their blows have). */
	static boolean onMass(EternityDevilEntity devil, ServerPlayerEntity p) {
		return onMass(HotelBuilder.local(devil.origin, devil.rot, p.getPos()));
	}

	/** Who is swallowed, in the mouth now. */
	public static boolean swallowed(HomeBlockEntity h, ServerPlayerEntity p) {
		return h.run.shaft.swallowed.containsKey(p.getUuid());
	}

	private static void shaftTick(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, List<ServerPlayerEntity> players, long now) {
		State st = h.run.shaft;
		List<ServerPlayerEntity> mass = new ArrayList<>();
		List<ServerPlayerEntity> ledge = new ArrayList<>();
		for (ServerPlayerEntity p : players) {
			Vec3d lp = local(h, p);
			// (The mass is soft, and the ledges are close: nobody dies of the fall; it is the mass that hurts.)
			p.fallDistance = 0.0F;
			if (p.isCreative() || p.isSpectator()) {
				continue;
			}
			boolean pit = inPit(lp);
			Held held = st.swallowed.get(p.getUuid());
			if (pit && held == null && !devil.begStarted && now >= st.immune.getOrDefault(p.getUuid(), 0L)) {
				swallow(world, h, devil, p, now);
			} else if (held != null && (!pit || devil.begStarted)) {
				st.swallowed.remove(p.getUuid());
				send(p, devil.getId(), HotelPayloads.Devil.SWALLOW, 0);
			}
			if (held != null && pit) {
				p.setVelocity(0.0, Math.min(0.0, p.getVelocity().y), 0.0);
				if ((now - held.since) % 10 == 9 && !io.github.kortev.chainsawman.form.Downed.is(p)) {
					p.damage(devil.getDamageSources().generic(), SWALLOW_DAMAGE);
				}
			} else if (onMass(lp)) {
				mass.add(p);
			} else if (lp.y >= BASE + 4.0) {
				ledge.add(p);
			}
		}
		if (!devil.fightable() || devil.broken || devil.begStarted) {
			return;
		}
		Box box = worldBox(h);
		if (now >= st.nextHead && !mass.isEmpty()) {
			st.nextHead = now + HEAD_GAP + world.random.nextInt(14);
			if (world.getEntitiesByType(ChainsawMan.SHAFT_HEAD, box, e -> true).size() < MAX_HEADS) {
				ServerPlayerEntity target = mass.get(world.random.nextInt(mass.size()));
				// Under their feet, or a step off: small, quick, hard to see coming.
				Vec3d lp = local(h, target);
				double hx = Math.max(CX0 + 1.0, Math.min(CX1 - 0.5, lp.x + (world.random.nextDouble() - 0.5) * 2.4));
				double hz = Math.max(CZ0 + 1.0, Math.min(CZ1 - 0.5, lp.z + (world.random.nextDouble() - 0.5) * 2.4));
				if (!inPit(new Vec3d(hx, BASE - 1.0, hz))) {
					ShaftHeadEntity head = new ShaftHeadEntity(ChainsawMan.SHAFT_HEAD, world);
					head.start(h.origin, h.rot, HotelBuilder.world(h.origin, h.rot, hx, BASE, hz));
					world.spawnEntity(head);
				}
			}
		}
		if (now >= st.nextArm && !ledge.isEmpty()) {
			st.nextArm = now + ARM_GAP + world.random.nextInt(35);
			if (world.getEntitiesByType(ChainsawMan.SHAFT_ARM, box, e -> true).size() < 2 + ledge.size() / 2) {
				// (The arm rises up the middle of the shaft; the hand is wide enough to take someone in a doorway.)
				ServerPlayerEntity target = ledge.get(world.random.nextInt(ledge.size()));
				Vec3d lp = local(h, target);
				double ax = Math.max(IX0 + 0.6, Math.min(IX1 + 0.4, lp.x));
				double az = IZ0 + 1.5 + (lp.z < IZ0 ? -0.6 : lp.z > IZ1 + 1 ? 0.6 : 0.0);
				ShaftArmEntity arm = new ShaftArmEntity(ChainsawMan.SHAFT_ARM, world);
				arm.start(h.origin, h.rot, HotelBuilder.world(h.origin, h.rot, ax, BASE, az), h.origin.getY() + BASE + DEPTH - 1.5);
				world.spawnEntity(arm);
				world.playSound(null, arm.getX(), arm.getY() + 2.0, arm.getZ(), ChainsawSounds.DEVIL_SLAM, SoundCategory.HOSTILE, 1.2F, 0.7F);
			}
		}
		if (now >= st.nextPatch && !mass.isEmpty()) {
			st.nextPatch = now + PATCH_GAP + world.random.nextInt(40);
			ServerPlayerEntity target = mass.get(world.random.nextInt(mass.size()));
			Vec3d lp = local(h, target);
			for (int k = 0; k < 3; k++) {
				double px = Math.max(CX0 + 1.0, Math.min(CX1 - 0.5, lp.x + (world.random.nextDouble() - 0.5) * 4.0));
				double pz = Math.max(CZ0 + 1.0, Math.min(CZ1 - 0.5, lp.z + (world.random.nextDouble() - 0.5) * 4.0));
				if (inPit(new Vec3d(px, BASE - 1.0, pz))) {
					continue;
				}
				Vec3d at = HotelBuilder.world(h.origin, h.rot, px, BASE, pz);
				FleshPatchEntity patch = new FleshPatchEntity(ChainsawMan.FLESH_PATCH, world);
				patch.refreshPositionAndAngles(at.x, at.y, at.z, world.random.nextFloat() * 360.0F, 0.0F);
				patch.setLife(FleshPatchEntity.LIFE, 10 * k);
				world.spawnEntity(patch);
			}
		}
	}

	// ---- The mouth.

	private static void swallow(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, ServerPlayerEntity p, long now) {
		Held held = new Held();
		held.since = now;
		h.run.shaft.swallowed.put(p.getUuid(), held);
		p.setVelocity(Vec3d.ZERO);
		p.velocityModified = true;
		p.fallDistance = 0.0F;
		world.playSound(null, p.getX(), p.getY(), p.getZ(), ChainsawSounds.DEVIL_BITE, SoundCategory.HOSTILE, 2.0F, 0.6F);
		send(p, devil.getId(), HotelPayloads.Devil.SWALLOW, 1);
		p.sendMessage(Text.translatable("message.chainsawman.devil.swallowed"), true);
	}

	/**
	 * A blow struck from inside the mouth: it counts toward cutting out (the saws in three or four, any other weapon in more), and when
	 * enough has been dealt the player bursts out. False if {@code by} is not swallowed (the blow is an ordinary one).
	 */
	public static boolean cutHit(ServerWorld world, EternityDevilEntity devil, ServerPlayerEntity by, float amount) {
		HomeBlockEntity h = home(world, devil);
		if (h == null) {
			return false;
		}
		Held held = h.run.shaft.swallowed.get(by.getUuid());
		if (held == null) {
			return false;
		}
		held.cut += Math.max(1.0F, amount);
		Vec3d at = HotelBuilder.world(h.origin, h.rot, PIT_X + 0.5, BASE - 2.0, PIT_Z + 0.5);
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.HOTEL_CUT, SoundCategory.HOSTILE, 1.5F, 0.8F + held.cut / CUT_NEED * 0.6F);
		world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.RED_TERRACOTTA.getDefaultState()), at.x, at.y, at.z, 10, 0.6, 0.6, 0.6, 0.1);
		if (held.cut >= CUT_NEED) {
			burst(world, h, devil, by, true);
		}
		return true;
	}

	/** Out of the mouth: popped up onto the mass, and (if you cut your way out) the devil takes a big dose of pain. */
	private static void burst(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil, ServerPlayerEntity p, boolean pain) {
		h.run.shaft.swallowed.remove(p.getUuid());
		h.run.shaft.immune.put(p.getUuid(), world.getTime() + 30L);
		send(p, devil.getId(), HotelPayloads.Devil.SWALLOW, 0);
		Vec3d at = HotelBuilder.world(h.origin, h.rot, PIT_X + 0.5, BASE - 1.2, PIT_Z + 0.5);
		p.networkHandler.requestTeleport(at.x, at.y, at.z, p.getYaw(), p.getPitch());
		// Launched up out of the pit and toward the shaft's foot.
		Vec3d away = HotelBuilder.world(BlockPos.ORIGIN, h.rot, -0.22, 0.0, (world.random.nextDouble() - 0.5) * 0.2);
		p.setVelocity(away.x, 0.95, away.z);
		p.velocityModified = true;
		p.fallDistance = 0.0F;
		world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.RED_TERRACOTTA.getDefaultState()), at.x, at.y + 1.0, at.z, 60, 0.9, 1.0, 0.9, 0.25);
		world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1.0, at.z, 2, 0.5, 0.5, 0.5, 0.0);
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.DEVIL_HURT, SoundCategory.HOSTILE, 3.0F, 0.7F);
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.DEVIL_SLAM, SoundCategory.HOSTILE, 2.0F, 1.2F);
		if (pain) {
			devil.addPain(PAIN_BURST);
		}
	}

	/** Everyone in the mouth is let out (the devil begs: it has no hold on anyone). */
	public static void freeAll(ServerWorld world, EternityDevilEntity devil) {
		HomeBlockEntity h = home(world, devil);
		if (h == null || h.run.shaft.swallowed.isEmpty()) {
			return;
		}
		for (UUID id : new ArrayList<>(h.run.shaft.swallowed.keySet())) {
			ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(id);
			if (p != null) {
				burst(world, h, devil, p, false);
			} else {
				h.run.shaft.swallowed.remove(id);
			}
		}
	}
}
