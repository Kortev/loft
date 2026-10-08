package io.github.kortev.shootingstar.thunder;

import io.github.kortev.shootingstar.block.ChargedFulguriteBlock;
import io.github.kortev.shootingstar.registry.ModBlocks;
import io.github.kortev.shootingstar.registry.ModCriteria;
import io.github.kortev.shootingstar.registry.ModDamageTypes;
import io.github.kortev.shootingstar.registry.ModGameRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * What Mjölnir's stroke does to the world, from the tick it lands. The channel blasts a crater out of the ground where
 * it hits and fuses its bowl to glowing glass; the heat runs out over the ground faster than anyone can run, burning the
 * leaves off every tree, the trees to black trunks and anything wooden to nothing, and killing everything inside the
 * strike radius; the current burns a Lichtenberg scar out through the earth along its branches, trenches lined with
 * charged fulgurite; arcs jump off the bolt to everything standing at the edge of the zone and on from them to their
 * neighbours; and once the crater is open the bolt is left standing in it, petrified.
 *
 * <p>Like Gungnir's crater it is carved a front at a time with a budget of blocks per tick, and leaves unbreakable blocks
 * (bedrock, barriers, command blocks) alone.
 */
public final class ThunderBuilder {
	/** How high above the strike point the crater is blown clear. */
	private static final int MAX_CUT = 120;
	private static final int BLOCK_BUDGET = 60_000;
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
	/** How far an arc will jump from one creature it has struck to the next. */
	private static final double CHAIN_REACH = 14.0;
	/** How many times an arc jumps on before it is spent. */
	private static final int CHAIN_DEPTH = 3;
	/** The most creatures the bolt arcs to directly; the chains can reach more. */
	private static final int MAX_ARCS = 32;
	/**
	 * How far down a column is searched for the ground: past the petrified bolt, which can reach out over the zone well
	 * above it, and whatever stands on the ground.
	 */
	private static final int SCAN_DEPTH = 220;
	/** One arc through this many creatures earns Chain Lightning. */
	public static final int CHAIN_GOAL = 5;

	private record Column(int dx, int dz, double dist) {
	}

	/** An arc: where it jumps from and to, how many ticks after the stroke it lands, and what it does there. */
	private record Arc(Vec3d from, Vec3d to, int delay, LivingEntity target, float damage, int depth) {
	}

	private final ServerWorld world;
	private final BlockPos center;
	private final int radius;
	private final int core;
	private final int coreDepth;
	private final int scar;
	private final boolean terrain;
	private final boolean petrify;
	private final int boltHeight;
	private final long seed;
	@Nullable
	private final ServerPlayerEntity shooter;
	private final UUID shooterId;
	private final DamageSource strike;
	private final Random random;
	private final List<Column> columns = new ArrayList<>();
	private final List<Lichtenberg.Cell> cells;
	private final Set<UUID> struck = new HashSet<>();
	private final List<Arc> arcs = new ArrayList<>();
	private final BlockPos.Mutable cursorPos = new BlockPos.Mutable();
	private int columnCursor;
	private int cellCursor;
	private int arcCursor;
	private int tick;
	private double lastFront;
	private int budget;
	private boolean boltBuilt;

	public ThunderBuilder(ServerWorld world, BlockPos center, int radius, long seed, @Nullable ServerPlayerEntity shooter,
			UUID shooterId) {
		this.world = world;
		this.center = center;
		this.radius = radius;
		this.core = ThunderTimeline.coreRadius(radius);
		this.coreDepth = Math.max(3, Math.round(core * 0.5F));
		this.scar = ThunderTimeline.scarRadius(radius);
		this.terrain = world.getGameRules().getBoolean(ModGameRules.MJOLNIR_TERRAIN);
		this.petrify = terrain && world.getGameRules().getBoolean(ModGameRules.MJOLNIR_BOLT);
		this.seed = seed;
		this.shooter = shooter;
		this.shooterId = shooterId;
		this.strike = ModDamageTypes.thunderstruck(world, shooter);
		this.random = new Random(seed ^ 0x9E3779B97F4A7C15L);
		// As tall as the zone is wide, short of the cloud base it came down from, and never through the build limit.
		int floor = floorY(0);
		int room = Math.max(0, world.getTopY() - 2 - floor);
		int wanted = Math.min(Math.round(radius * 1.5F), ThunderTimeline.cloudBase(center.getY(), world.getTopY()) - floor - 8);
		this.boltHeight = petrify ? Math.min(room, Math.max(Math.min(24, room), wanted)) : 0;

		for (int dx = -scar; dx <= scar; dx++) {
			for (int dz = -scar; dz <= scar; dz++) {
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist <= scar) {
					columns.add(new Column(dx, dz, dist));
				}
			}
		}
		columns.sort(Comparator.comparingDouble(Column::dist));
		this.cells = terrain ? Lichtenberg.grow(seed, radius).cells(core + 0.5) : List.of();
	}

	public int radius() {
		return radius;
	}

	public boolean terrain() {
		return terrain;
	}

	/** Height of the petrified bolt over the crater floor, or 0 when none is left standing. */
	public int boltHeight() {
		return boltHeight;
	}

	/**
	 * The arcs, for the clients to draw: seven numbers each, the point it jumps from, the point it lands on, and the
	 * ticks after the stroke when it does.
	 */
	public List<Float> arcData() {
		List<Float> data = new ArrayList<>(arcs.size() * 7);
		for (Arc arc : arcs) {
			data.add((float) arc.from().x);
			data.add((float) arc.from().y);
			data.add((float) arc.from().z);
			data.add((float) arc.to().x);
			data.add((float) arc.to().y);
			data.add((float) arc.to().z);
			data.add((float) arc.delay());
		}
		return data;
	}

	/** Runs on the stroke's tick: everything under the channel dies, and the arcs are chosen. */
	public void start() {
		hitCore();
		planArcs();
		lastFront = core;
	}

	/** Moves everything on by a tick. True once every block is done and every arc has landed. */
	public boolean step() {
		tick++;
		budget = BLOCK_BUDGET;
		double front = Math.min(scar, tick * ThunderTimeline.BURN_SPEED);
		if (terrain) {
			while (columnCursor < columns.size() && columns.get(columnCursor).dist() <= front && budget > 0) {
				burn(columns.get(columnCursor++));
			}
			if (!boltBuilt && (columnCursor >= columns.size() || columns.get(columnCursor).dist() > core)) {
				boltBuilt = true;
				if (petrify && boltHeight > 0) {
					buildBolt();
				}
			}
			double scarFront = ThunderTimeline.scarFront(tick);
			while (cellCursor < cells.size() && cells.get(cellCursor).arrival() <= scarFront && budget > 0) {
				channel(cells.get(cellCursor++));
			}
		}
		if (front > lastFront) {
			groundCurrent(lastFront, front);
			lastFront = front;
		}
		while (arcCursor < arcs.size() && arcs.get(arcCursor).delay() <= tick) {
			land(arcs.get(arcCursor++));
		}
		if (tick == 2 && terrain) {
			spawnShards();
		}
		boolean blocksDone = !terrain || columnCursor >= columns.size() && cellCursor >= cells.size();
		return blocksDone && front >= scar && arcCursor >= arcs.size();
	}

	// --- the crater --------------------------------------------------------------------------------

	private int floorY(double dist) {
		if (dist < core) {
			double t = dist / core;
			return center.getY() - (int) Math.round(coreDepth * Math.sqrt(1.0 - t * t));
		}
		return center.getY();
	}

	private void burn(Column column) {
		int x = center.getX() + column.dx();
		int z = center.getZ() + column.dz();
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return;
		}
		if (column.dist() <= core) {
			blast(x, z, column.dist());
		} else {
			scorch(x, z, column.dist());
		}
	}

	/** Inside the channel's footprint: everything above the bowl is gone and the bowl is fused to glass. */
	private void blast(int x, int z, double dist) {
		int floor = floorY(dist);
		int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		int ceiling = Math.min(surface, floor + MAX_CUT);
		for (int y = ceiling; y > floor; y--) {
			vaporize(x, y, z);
		}
		// Boil off water, plants and trunks down to solid ground.
		int y = Math.min(floor, surface);
		int bottom = world.getBottomY();
		for (int guard = 0; guard < 32 && y > bottom; guard++) {
			BlockState state = world.getBlockState(cursorPos.set(x, y, z));
			boolean soft = state.isAir() || !state.getFluidState().isEmpty() || state.isIn(BlockTags.LEAVES)
					|| state.isIn(BlockTags.LOGS) || state.getCollisionShape(world, cursorPos).isEmpty();
			if (!soft) {
				break;
			}
			if (!state.isAir()) {
				vaporize(x, y, z);
			}
			y--;
		}
		double t = dist / core;
		set(x, y, z, charged(t < 0.45 ? 3 : t < 0.8 ? 2 : 1));
		set(x, y - 1, z, ModBlocks.FULGURITE.getDefaultState());
		if (t > 0.86) {
			// A lip of fused shards round the edge of the bowl.
			int lip = (int) Math.round((1.0 - Math.abs(t - 0.96) / 0.1) * (1.0 + 1.5 * hash(x, z)));
			for (int k = 1; k <= lip; k++) {
				set(x, y + k, z, hash(x * 7 + k, z) < 0.7 ? ModBlocks.FULGURITE.getDefaultState() : Blocks.TINTED_GLASS.getDefaultState());
			}
		}
	}

	/**
	 * Out from the crater: the heat of the stroke. Inside the strike radius every leaf burns away, every trunk is charred
	 * black and anything wooden burns to nothing; out in the ring beyond it less and less of that, down to scattered
	 * scorching at the edge of the scar.
	 */
	private void scorch(int x, int z, double dist) {
		boolean zone = dist <= radius;
		// 1 at the crater's rim, falling to a half at the edge of the zone and nothing at the edge of the scar.
		double heat = zone ? 1.0 - 0.5 * (dist - core) / Math.max(1.0, radius - core)
				: 0.5 * (1.0 - (dist - radius) / Math.max(1.0, scar - radius));
		int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		int bottom = Math.max(world.getBottomY(), top - SCAN_DEPTH);
		for (int y = top; y > bottom; y--) {
			BlockState state = world.getBlockState(cursorPos.set(x, y, z));
			if (state.isAir() || fulgurite(state)) {
				// The petrified bolt may already reach out over this column: the ground is under it.
				continue;
			}
			if (state.isIn(BlockTags.LEAVES)) {
				if (zone || random.nextDouble() < 0.2 + heat * 1.4) {
					vaporize(x, y, z);
				}
				continue;
			}
			if (state.isIn(BlockTags.LOGS)) {
				if (zone || random.nextDouble() < heat * 1.6) {
					set(x, y, z, charred(state));
				}
				continue;
			}
			if (state.isOf(ModBlocks.CHARRED_LOG)) {
				continue;
			}
			if (!state.getFluidState().isEmpty()) {
				if (state.isIn(BlockTags.ICE)) {
					set(x, y, z, Blocks.WATER.getDefaultState());
				}
				break;
			}
			if (state.hasBlockEntity()) {
				// Chests and the like ride it out.
				break;
			}
			if (state.isReplaceable() || state.isIn(BlockTags.FLOWERS) || state.getCollisionShape(world, cursorPos).isEmpty()) {
				if (zone || state.isReplaceable() && random.nextDouble() < 0.3 + heat) {
					vaporize(x, y, z);
				}
				continue;
			}
			if (state.isIn(ConventionalBlockTags.GLASS_BLOCKS) || state.isIn(ConventionalBlockTags.GLASS_PANES)) {
				if (heat > 0.2 && !state.isOf(Blocks.TINTED_GLASS)) {
					vaporize(x, y, z);
					continue;
				}
				break;
			}
			if (state.isBurnable()) {
				// Anything wooden: gone to ash inside the zone, here and there in the ring (where what is left shelters
				// what is under it).
				if (zone || random.nextDouble() < heat * 0.7) {
					vaporize(x, y, z);
					continue;
				}
				break;
			}
			ground(x, y, z, state, zone, heat);
			break;
		}
	}

	/** The ground itself: grass burns to bare earth, sand fuses to glass, snow and ice go. */
	private void ground(int x, int y, int z, BlockState top, boolean zone, double heat) {
		BlockState scorched = null;
		if (top.isOf(Blocks.GRASS_BLOCK) || top.isOf(Blocks.DIRT) || top.isOf(Blocks.PODZOL) || top.isOf(Blocks.MYCELIUM)
				|| top.isOf(Blocks.DIRT_PATH) || top.isOf(Blocks.FARMLAND) || top.isOf(Blocks.ROOTED_DIRT)
				|| top.isOf(Blocks.MOSS_BLOCK)) {
			if (zone || random.nextDouble() < heat * 1.5) {
				scorched = heat > 0.8 && random.nextDouble() < 0.25 ? Blocks.TUFF.getDefaultState() : Blocks.COARSE_DIRT.getDefaultState();
			}
		} else if (top.isOf(Blocks.SAND)) {
			scorched = zone || random.nextDouble() < heat * 1.4 ? Blocks.GLASS.getDefaultState() : null;
		} else if (top.isOf(Blocks.RED_SAND)) {
			scorched = zone || random.nextDouble() < heat * 1.4 ? Blocks.ORANGE_STAINED_GLASS.getDefaultState() : null;
		} else if (top.isOf(Blocks.SNOW_BLOCK) || top.isOf(Blocks.POWDER_SNOW)) {
			scorched = zone ? Blocks.AIR.getDefaultState() : null;
		}
		if (scorched != null) {
			set(x, y, z, scorched);
		}
		BlockPos above = new BlockPos(x, y + 1, z);
		double fire = zone ? 0.04 + 0.1 * heat : 0.06 * heat;
		if (random.nextDouble() < fire && world.isAir(above) && !world.getBlockState(cursorPos.set(x, y, z)).isAir()) {
			set(x, y + 1, z, AbstractFireBlock.getState(world, above));
		}
	}

	/**
	 * One cell of the scar: the current's channel through the ground. Wide channels are burned out into trenches up to
	 * four deep; every one is lined with charged fulgurite, brightest where the channel is widest.
	 */
	private void channel(Lichtenberg.Cell cell) {
		int x = center.getX() + cell.dx();
		int z = center.getZ() + cell.dz();
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return;
		}
		int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		int bottom = Math.max(world.getBottomY(), y - SCAN_DEPTH);
		for (; y > bottom; y--) {
			BlockState state = world.getBlockState(cursorPos.set(x, y, z));
			if (state.isAir() || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS) || state.isOf(ModBlocks.CHARRED_LOG)
					|| fulgurite(state)) {
				continue;
			}
			if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
				return;
			}
			if (state.getCollisionShape(world, cursorPos).isEmpty()) {
				vaporize(x, y, z);
				continue;
			}
			break;
		}
		if (y <= bottom) {
			return;
		}
		int depth = cell.width() >= 1.4F ? MathHelper.clamp(Math.round(cell.width() * 0.8F * (0.35F + 0.65F * cell.centrality())), 1, 4) : 0;
		for (int k = 0; k < depth; k++) {
			BlockState state = world.getBlockState(cursorPos.set(x, y, z));
			if (state.hasBlockEntity() || state.getHardness(world, cursorPos) < 0.0F) {
				break;
			}
			vaporize(x, y, z);
			y--;
		}
		int charge = cell.width() > 2.4F ? 3 : cell.width() > 1.2F ? 2 : 1;
		set(x, y, z, charged(charge));
		if (depth > 0) {
			set(x, y - 1, z, ModBlocks.FULGURITE.getDefaultState());
		}
	}

	// --- the petrified bolt ------------------------------------------------------------------------

	/**
	 * The bolt left standing in its crater as a jagged column of fulgurite, kinked every few blocks the way the channel
	 * was, forking near the top, and still charged at its heart.
	 */
	private void buildBolt() {
		Random r = new Random(seed ^ 0x5DEECE66DL);
		int floor = floorY(0);
		double top = floor + boltHeight;
		List<Vec3d> channel = new ArrayList<>();
		double x = center.getX() + 0.5;
		double z = center.getZ() + 0.5;
		double y = floor;
		channel.add(new Vec3d(x, y, z));
		while (y < top) {
			y = Math.min(top, y + 6.0 + r.nextDouble() * 8.0);
			x = MathHelper.clamp(x + r.nextGaussian() * 2.6, center.getX() - 6.5, center.getX() + 7.5);
			z = MathHelper.clamp(z + r.nextGaussian() * 2.6, center.getZ() - 6.5, center.getZ() + 7.5);
			channel.add(new Vec3d(x, y, z));
		}
		int n = channel.size();
		for (int i = 0; i + 1 < n; i++) {
			double a = 2.4 - 1.7 * i / (n - 1.0);
			double b = 2.4 - 1.7 * (i + 1) / (n - 1.0);
			capsule(channel.get(i), channel.get(i + 1), a, b, true);
		}
		// Forks off the upper kinks, reaching out and up and thinning to a point.
		int forks = n > 3 ? 2 + r.nextInt(3) : 0;
		for (int f = 0; f < forks; f++) {
			int at = n / 2 + r.nextInt(Math.max(1, n - 1 - n / 2));
			Vec3d from = channel.get(Math.min(at, n - 1));
			double angle = r.nextDouble() * Math.PI * 2.0;
			Vec3d dir = new Vec3d(Math.cos(angle), 0.6 + 0.6 * r.nextDouble(), Math.sin(angle)).normalize();
			double length = 8.0 + r.nextDouble() * Math.min(18.0, boltHeight * 0.25);
			Vec3d p = from;
			for (int k = 0; k < 3; k++) {
				Vec3d next = p.add(dir.multiply(length / 3.0)).add(r.nextGaussian() * 1.2, r.nextGaussian() * 0.8, r.nextGaussian() * 1.2);
				capsule(p, next, 1.2 - 0.2 * k, 1.0 - 0.2 * k, false);
				p = next;
			}
		}
	}

	/** Fills the blocks within a tapering radius of the line a..b with fulgurite, charged along the heart of thick parts. */
	private void capsule(Vec3d a, Vec3d b, double ra, double rb, boolean heart) {
		ra = Math.max(0.75, ra);
		rb = Math.max(0.75, rb);
		double reach = Math.max(ra, rb) + 1.0;
		Vec3d d = b.subtract(a);
		double len2 = Math.max(d.lengthSquared(), 1.0E-6);
		int minX = MathHelper.floor(Math.min(a.x, b.x) - reach);
		int maxX = MathHelper.floor(Math.max(a.x, b.x) + reach);
		int minY = MathHelper.floor(Math.min(a.y, b.y) - reach);
		int maxY = MathHelper.floor(Math.max(a.y, b.y) + reach);
		int minZ = MathHelper.floor(Math.min(a.z, b.z) - reach);
		int maxZ = MathHelper.floor(Math.max(a.z, b.z) + reach);
		for (int bx = minX; bx <= maxX; bx++) {
			for (int by = minY; by <= maxY; by++) {
				for (int bz = minZ; bz <= maxZ; bz++) {
					Vec3d p = new Vec3d(bx + 0.5, by + 0.5, bz + 0.5);
					double t = MathHelper.clamp(p.subtract(a).dotProduct(d) / len2, 0.0, 1.0);
					double dist = p.distanceTo(a.add(d.multiply(t)));
					double rad = MathHelper.lerp(t, ra, rb);
					if (dist <= rad) {
						set(bx, by, bz, heart && rad > 1.3 && dist < rad * 0.45 ? charged(3) : ModBlocks.FULGURITE.getDefaultState());
					}
				}
			}
		}
	}

	// --- creatures ---------------------------------------------------------------------------------

	/** Everything under the channel itself, at any height up to the cloud base, dies on the stroke's tick. */
	private void hitCore() {
		double cx = center.getX() + 0.5;
		double cz = center.getZ() + 0.5;
		Box box = new Box(cx - core, center.getY() - 24, cz - core, cx + core, world.getTopY() + 64, cz + core);
		for (Entity entity : world.getOtherEntities(null, box, EntityPredicates.EXCEPT_SPECTATOR.and(Entity::isAlive))) {
			double dx = entity.getX() - cx;
			double dz = entity.getZ() - cz;
			if (dx * dx + dz * dz <= (double) core * core && struck.add(entity.getUuid())) {
				kill(entity);
			}
		}
	}

	/** The current through the ground, out to the edge of the strike radius: nothing standing on it survives. */
	private void groundCurrent(double inner, double outer) {
		if (inner >= radius) {
			return;
		}
		outer = Math.min(outer, radius);
		double cx = center.getX() + 0.5;
		double cz = center.getZ() + 0.5;
		Box box = new Box(cx - outer, center.getY() - 24, cz - outer, cx + outer, center.getY() + MAX_CUT, cz + outer);
		for (Entity entity : world.getOtherEntities(null, box, EntityPredicates.EXCEPT_SPECTATOR.and(Entity::isAlive))) {
			double dx = entity.getX() - cx;
			double dz = entity.getZ() - cz;
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist >= inner && dist <= outer && struck.add(entity.getUuid())) {
				kill(entity);
			}
		}
	}

	private void kill(Entity entity) {
		if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity || entity instanceof FallingBlockEntity) {
			entity.discard();
			return;
		}
		entity.damage(strike, 1000.0F);
		entity.setOnFireFor(8.0F);
	}

	/**
	 * The arcs: a side flash off the channel to every creature in the ring between the strike radius and the edge of the
	 * scar, nearest first, each weaker the farther out it lands; and from each one hit, a jump on to the nearest creature
	 * not yet hit within reach, and from that one on again, until the arc is spent. Whoever raised the hammer is never
	 * arced. Chosen on the stroke's tick, landing over the ticks after it.
	 */
	private void planArcs() {
		double cx = center.getX() + 0.5;
		double cz = center.getZ() + 0.5;
		double reach = scar + CHAIN_REACH * CHAIN_DEPTH;
		Box box = new Box(cx - reach, center.getY() - 48, cz - reach, cx + reach, center.getY() + MAX_CUT, cz + reach);
		List<LivingEntity> all = world.getEntitiesByClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(shooterId));
		List<LivingEntity> ring = new ArrayList<>();
		for (LivingEntity e : all) {
			double dist = flat(e, cx, cz);
			if (dist > radius && dist <= scar) {
				ring.add(e);
			}
		}
		ring.sort(Comparator.comparingDouble(e -> flat(e, cx, cz)));
		Set<UUID> planned = new HashSet<>(struck);
		List<Arc> open = new ArrayList<>();
		for (LivingEntity e : ring) {
			if (open.size() >= MAX_ARCS) {
				break;
			}
			double dist = flat(e, cx, cz);
			double ring01 = 1.0 - (dist - radius) / Math.max(1.0, scar - radius);
			// It leaves the channel at a height that keeps the arc from running along the ground.
			Vec3d from = new Vec3d(cx, center.getY() + Math.min(40.0, 6.0 + dist * 0.35), cz);
			int delay = 1 + (int) Math.round((1.0 - ring01) * 6.0);
			planned.add(e.getUuid());
			open.add(new Arc(from, middle(e), delay, e, (float) (4.0 + 14.0 * ring01 * ring01), 0));
		}
		// Breadth first, so each jump goes to whoever is nearest the arc that reached them.
		for (int i = 0; i < open.size(); i++) {
			Arc arc = open.get(i);
			if (arc.depth() >= CHAIN_DEPTH) {
				continue;
			}
			LivingEntity next = null;
			double best = CHAIN_REACH * CHAIN_REACH;
			for (LivingEntity e : all) {
				if (planned.contains(e.getUuid()) || flat(e, cx, cz) <= radius) {
					continue;
				}
				double d2 = e.squaredDistanceTo(arc.target());
				if (d2 < best) {
					best = d2;
					next = e;
				}
			}
			if (next != null) {
				planned.add(next.getUuid());
				open.add(new Arc(arc.to(), middle(next), arc.delay() + 3, next, (float) (8.0 * Math.pow(0.7, arc.depth())),
						arc.depth() + 1));
			}
		}
		open.sort(Comparator.comparingInt(Arc::delay));
		arcs.addAll(open);
		if (arcs.size() >= CHAIN_GOAL) {
			ModCriteria.fire(shooter, ModCriteria.MJOLNIR_CHAIN);
		}
	}

	private void land(Arc arc) {
		LivingEntity target = arc.target();
		if (!target.isAlive() || target.getWorld() != world) {
			return;
		}
		struck.add(target.getUuid());
		target.damage(ModDamageTypes.arced(world, shooter), arc.damage());
		target.setOnFireFor(4.0F);
		if (target.isAlive()) {
			// The arc is lightning, so it does what lightning does: charges creepers, turns pigs, villagers and the rest.
			boolean wasCharged = target instanceof CreeperEntity creeper && creeper.shouldRenderOverlay();
			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
			if (bolt != null) {
				bolt.setCosmetic(true);
				bolt.setPosition(target.getX(), target.getY(), target.getZ());
				target.onStruckByLightning(world, bolt);
			}
			if (!wasCharged && target instanceof CreeperEntity creeper && creeper.shouldRenderOverlay()) {
				ModCriteria.fire(shooter, ModCriteria.MJOLNIR_CHARGED);
			}
		}
		Vec3d push = arc.to().subtract(arc.from()).multiply(1.0, 0.0, 1.0);
		if (push.lengthSquared() > 1.0E-4) {
			push = push.normalize().multiply(0.6);
			target.addVelocity(push.x, 0.35, push.z);
			target.velocityModified = true;
		}
	}

	private static double flat(Entity e, double cx, double cz) {
		double dx = e.getX() - cx;
		double dz = e.getZ() - cz;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static Vec3d middle(Entity e) {
		return e.getPos().add(0.0, e.getHeight() * 0.5, 0.0);
	}

	// --- shards ------------------------------------------------------------------------------------

	/** Fused glass thrown out of the crater as it is blasted open. */
	private void spawnShards() {
		int floor = floorY(0) + 2;
		int count = MathHelper.clamp(radius * 2, 30, 200);
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double offset = 3.0 + random.nextDouble() * (core - 3.0);
			BlockPos pos = BlockPos.ofFloored(center.getX() + 0.5 + Math.cos(angle) * offset, floor + random.nextInt(3),
					center.getZ() + 0.5 + Math.sin(angle) * offset);
			if (!world.isAir(pos)) {
				continue;
			}
			BlockState shard = random.nextDouble() < 0.6 ? ModBlocks.FULGURITE.getDefaultState() : Blocks.TINTED_GLASS.getDefaultState();
			FallingBlockEntity block = FallingBlockEntity.spawnFromBlock(world, pos, shard);
			block.dropItem = false;
			block.setHurtEntities(2.0F, 20);
			double reachOut = Math.sqrt(radius / 28.0);
			double speed = (0.5 + random.nextDouble() * 0.9) * reachOut;
			block.setVelocity(Math.cos(angle) * speed, (0.8 + random.nextDouble() * 0.7) * reachOut, Math.sin(angle) * speed);
			block.velocityModified = true;
			struck.add(block.getUuid());
		}
	}

	// --- blocks ------------------------------------------------------------------------------------

	/** The bolt's own glass, which the scans look straight through. */
	private static boolean fulgurite(BlockState state) {
		return state.isOf(ModBlocks.FULGURITE) || state.isOf(ModBlocks.CHARGED_FULGURITE);
	}

	private static BlockState charged(int charge) {
		return ModBlocks.CHARGED_FULGURITE.getDefaultState().with(ChargedFulguriteBlock.CHARGE, charge);
	}

	private static BlockState charred(BlockState log) {
		BlockState state = ModBlocks.CHARRED_LOG.getDefaultState();
		return log.contains(PillarBlock.AXIS) ? state.with(PillarBlock.AXIS, log.get(PillarBlock.AXIS)) : state;
	}

	private void vaporize(int x, int y, int z) {
		set(x, y, z, Blocks.AIR.getDefaultState());
	}

	private void set(int x, int y, int z, BlockState state) {
		cursorPos.set(x, y, z);
		if (world.isOutOfHeightLimit(cursorPos)) {
			return;
		}
		BlockState old = world.getBlockState(cursorPos);
		if (old == state || old.getHardness(world, cursorPos) < 0.0F) {
			// Leave bedrock, barriers, command blocks and other unbreakables alone.
			return;
		}
		if (old.hasBlockEntity()) {
			world.removeBlockEntity(cursorPos);
		}
		world.setBlockState(cursorPos, state, FLAGS);
		budget--;
	}

	/** A steady pseudo-random number in [0, 1) for a column, so the crater's lip has the same shape every time. */
	private static double hash(int x, int z) {
		long h = x * 341873128712L + z * 132897987541L;
		h = (h ^ (h >>> 13)) * 0x5bd1e995L;
		h ^= h >>> 15;
		return (h & 0xFFFF) / 65536.0;
	}
}
