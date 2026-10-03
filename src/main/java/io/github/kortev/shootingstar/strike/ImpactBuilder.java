package io.github.kortev.shootingstar.strike;

import io.github.kortev.shootingstar.block.MoltenCrustBlock;
import io.github.kortev.shootingstar.registry.ModBlocks;
import io.github.kortev.shootingstar.registry.ModDamageTypes;
import io.github.kortev.shootingstar.registry.ModGameRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * Plans and carves one impact: the spire goes up on the impact tick, then a shockwave front walks
 * outward (about three quarters of a tick per block of radius), blasting a deep bowl around the
 * spire, planing the zone flat into molten crust, throwing up a rim, scorching the ring beyond it
 * and blasting entities as it passes them.
 */
public final class ImpactBuilder {
	/** Squared radius of the spire's round cross-section (21 blocks per layer). */
	public static final int SPIRE_R2 = 6;
	private static final int FIN_HEIGHT = 30;
	private static final int FIN_SPAN = 8;
	private static final int BAND_SPACING = 24;
	private static final int MAX_CUT = 140;
	private static final int BLOCK_BUDGET = 60_000;
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private record Column(int dx, int dz, double dist) {
	}

	private final ServerWorld world;
	private final BlockPos center;
	private final int radius;
	private final int bowlRadius;
	private final int bowlDepth;
	private final int scorchRadius;
	private final int waveTicks;
	private final boolean terrain;
	private final boolean spire;
	private final DamageSource damage;
	private final Random random;
	private final List<Column> columns = new ArrayList<>();
	private final Set<UUID> blasted = new HashSet<>();
	private final Set<UUID> ejecta = new HashSet<>();
	private final BlockPos.Mutable cursorPos = new BlockPos.Mutable();
	private int cursor;
	private int tick;
	private double lastFront;
	private int budget;

	public ImpactBuilder(ServerWorld world, BlockPos center, int radius) {
		this.world = world;
		this.center = center;
		this.radius = radius;
		this.bowlRadius = Math.max(4, Math.round(radius * 0.55F));
		this.bowlDepth = Math.max(3, Math.round(bowlRadius * 0.62F));
		this.scorchRadius = Math.round(radius * 1.5F);
		this.waveTicks = Math.max(18, Math.round(radius * 0.75F));
		this.terrain = world.getGameRules().getBoolean(ModGameRules.TERRAIN_DAMAGE);
		this.spire = terrain && world.getGameRules().getBoolean(ModGameRules.SPIRE);
		this.damage = ModDamageTypes.kineticStrike(world);
		this.random = Random.create(center.asLong() ^ world.getTime());

		for (int dx = -scorchRadius; dx <= scorchRadius; dx++) {
			for (int dz = -scorchRadius; dz <= scorchRadius; dz++) {
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist <= scorchRadius) {
					columns.add(new Column(dx, dz, dist));
				}
			}
		}
		columns.sort(Comparator.comparingDouble(Column::dist));
	}

	public int radius() {
		return radius;
	}

	public int scorchRadius() {
		return scorchRadius;
	}

	public int zoneDiameter() {
		return radius * 2;
	}

	/** Height of the spire, or 0 when no spire is raised. */
	public int spireHeight() {
		return spire ? world.getTopY() - world.getBottomY() : 0;
	}

	public boolean terrain() {
		return terrain;
	}

	/** Runs on the impact tick. */
	public void start() {
		if (spire) {
			buildSpire();
		}
		blastEntities(0, bowlRadius);
		lastFront = bowlRadius;
	}

	/** Advances the shockwave one tick. Returns true once everything is carved. */
	public boolean step() {
		tick++;
		budget = BLOCK_BUDGET;
		// The front races out and decelerates as it spreads, like a blast wave (radius ~ t^0.45).
		double front = scorchRadius * Math.pow(Math.min(1.0, (double) tick / waveTicks), 0.45);
		if (terrain) {
			while (cursor < columns.size() && columns.get(cursor).dist() <= front && budget > 0) {
				carve(columns.get(cursor++));
			}
		}
		if (front > lastFront) {
			blastEntities(lastFront, front);
			lastFront = front;
		}
		if (tick == 3 && terrain) {
			spawnEjecta();
		}
		return tick >= waveTicks && (!terrain || cursor >= columns.size());
	}

	// --- terrain -----------------------------------------------------------------------------

	private void carve(Column column) {
		int x = center.getX() + column.dx();
		int z = center.getZ() + column.dz();
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return;
		}
		if (spire && column.dx() * column.dx() + column.dz() * column.dz() <= SPIRE_R2) {
			return;
		}
		int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		if (column.dist() <= radius) {
			plane(x, z, column.dist(), surface);
		} else {
			scorch(x, z, column.dist(), surface);
		}
	}

	private int floorY(double dist) {
		if (dist < bowlRadius) {
			double t = dist / bowlRadius;
			return center.getY() - (int) Math.round(bowlDepth * Math.sqrt(1.0 - t * t));
		}
		return center.getY();
	}

	private void plane(int x, int z, double dist, int surface) {
		int floor = floorY(dist);
		int ceiling = Math.min(surface, floor + MAX_CUT);
		for (int y = ceiling; y > floor; y--) {
			vaporize(x, y, z);
		}

		// Boil off water, plants and tree trunks down to solid ground.
		int y = Math.min(floor, surface);
		int bottom = world.getBottomY();
		for (int guard = 0; guard < 40 && y > bottom; guard++) {
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

		double ring = dist / radius + noise(x, z) * 0.18;
		int heat = dist < bowlRadius ? 3 : ring < 0.66 ? 2 : 1;
		boolean pit = spire && dist * dist <= SPIRE_R2 + 7;
		if (pit) {
			set(x, y, z, Blocks.LAVA.getDefaultState());
			set(x, y - 1, z, crust(3));
		} else {
			set(x, y, z, crust(heat));
		}
		for (int k = 1; k <= 2; k++) {
			BlockState below = world.getBlockState(cursorPos.set(x, y - k - (pit ? 1 : 0), z));
			if (!below.isOf(Blocks.BEDROCK) && !below.isOf(ModBlocks.GUNGNIR_HULL)) {
				set(x, y - k - (pit ? 1 : 0), z, (k == 1 && heat == 3) ? Blocks.MAGMA_BLOCK.getDefaultState()
						: Blocks.BLACKSTONE.getDefaultState());
			}
		}

		// A lip of thrown debris around the edge of the planed zone.
		double lipWidth = 2.5 + radius * 0.08;
		if (dist > radius - lipWidth) {
			double lip = (1.0 - Math.abs(dist - (radius - 1)) / lipWidth) * (0.6 + 0.8 * noise(x * 3, z * 3));
			int height = (int) Math.round(lip * (2.2 + radius * 0.06));
			for (int k = 1; k <= height; k++) {
				set(x, y + k, z, debris());
			}
		}
	}

	private void scorch(int x, int z, double dist, int surface) {
		double heat = 1.0 - (dist - radius) / (double) (scorchRadius - radius);
		int y = surface;
		int bottom = world.getBottomY();
		// Strip leaves, plants, snow and glass from the top of the column.
		for (int guard = 0; guard < 28 && y > bottom; guard++) {
			BlockState state = world.getBlockState(cursorPos.set(x, y, z));
			if (state.isAir()) {
				y--;
				continue;
			}
			boolean strip;
			if (state.isIn(BlockTags.LEAVES)) {
				strip = random.nextFloat() < 0.35 + 0.65 * heat;
			} else if (!state.getFluidState().isEmpty()) {
				strip = false;
			} else {
				strip = state.isOf(Blocks.SNOW) || state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.REPLACEABLE)
						|| heat > 0.25 && (state.isIn(ConventionalBlockTags.GLASS_BLOCKS)
						|| state.isIn(ConventionalBlockTags.GLASS_PANES));
			}
			if (strip) {
				vaporize(x, y, z);
				y--;
				continue;
			}
			if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
				y--;
				continue;
			}
			break;
		}

		BlockState top = world.getBlockState(cursorPos.set(x, y, z));
		BlockState scorched = null;
		if (top.isOf(Blocks.GRASS_BLOCK) || top.isOf(Blocks.DIRT) || top.isOf(Blocks.PODZOL)
				|| top.isOf(Blocks.MYCELIUM) || top.isOf(Blocks.DIRT_PATH) || top.isOf(Blocks.FARMLAND)
				|| top.isOf(Blocks.ROOTED_DIRT) || top.isOf(Blocks.MOSS_BLOCK)) {
			scorched = heat > 0.55 && random.nextFloat() < heat ? Blocks.BLACKSTONE.getDefaultState()
					: Blocks.COARSE_DIRT.getDefaultState();
		} else if (top.isIn(BlockTags.SAND)) {
			scorched = random.nextFloat() < heat * 0.8 ? Blocks.GLASS.getDefaultState() : null;
		} else if (top.isOf(Blocks.SNOW_BLOCK) || top.isOf(Blocks.POWDER_SNOW)) {
			scorched = Blocks.AIR.getDefaultState();
		} else if (top.isIn(BlockTags.ICE)) {
			scorched = Blocks.WATER.getDefaultState();
		} else if (heat > 0.7 && top.isIn(BlockTags.BASE_STONE_OVERWORLD) && random.nextFloat() < heat * 0.6) {
			scorched = random.nextFloat() < 0.2 ? crust(1) : Blocks.BLACKSTONE.getDefaultState();
		}
		if (scorched != null) {
			set(x, y, z, scorched);
		}

		BlockPos above = new BlockPos(x, y + 1, z);
		if (random.nextFloat() < 0.12 * heat && world.isAir(above) && !world.getBlockState(cursorPos.set(x, y, z)).isAir()) {
			set(x, y + 1, z, AbstractFireBlock.getState(world, above));
		}
	}

	private BlockState crust(int heat) {
		return ModBlocks.MOLTEN_CRUST.getDefaultState().with(MoltenCrustBlock.HEAT, heat);
	}

	private BlockState debris() {
		float roll = random.nextFloat();
		if (roll < 0.40F) {
			return Blocks.BLACKSTONE.getDefaultState();
		} else if (roll < 0.65F) {
			return Blocks.BASALT.getDefaultState();
		} else if (roll < 0.80F) {
			return Blocks.MAGMA_BLOCK.getDefaultState();
		} else if (roll < 0.92F) {
			return Blocks.COBBLED_DEEPSLATE.getDefaultState();
		}
		return crust(1);
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
			// Vaporised along with everything in it; nothing drops.
			world.removeBlockEntity(cursorPos);
		}
		world.setBlockState(cursorPos, state, FLAGS);
		budget--;
	}

	// --- spire -------------------------------------------------------------------------------

	private void buildSpire() {
		int bottom = world.getBottomY();
		int top = world.getTopY() - 1;
		BlockState hull = ModBlocks.GUNGNIR_HULL.getDefaultState();
		BlockState coil = ModBlocks.GUNGNIR_COIL.getDefaultState();
		int cx = center.getX();
		int cz = center.getZ();
		for (int y = bottom; y <= top; y++) {
			int fromBottom = y - bottom;
			int fromTop = top - y;
			boolean band = fromBottom > 4 && (fromBottom % BAND_SPACING < 2 || fromTop < 2);
			BlockState state = y == bottom ? Blocks.BEDROCK.getDefaultState() : band ? coil : hull;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (dx * dx + dz * dz <= SPIRE_R2) {
						set(cx + dx, y, cz + dz, state);
					}
				}
			}
		}
		// Tail fins: swept leading edge, flat trailing edge at the top of the world.
		for (int i = 0; i < FIN_HEIGHT; i++) {
			int y = top - i;
			int span = i < 6 ? FIN_SPAN : FIN_SPAN - (int) Math.round((i - 6) * (FIN_SPAN - 2.0) / (FIN_HEIGHT - 6));
			for (int k = 3; k <= span; k++) {
				BlockState state = k == span || i == 0 ? coil : hull;
				set(cx + k, y, cz, state);
				set(cx - k, y, cz, state);
				set(cx, y, cz + k, state);
				set(cx, y, cz - k, state);
			}
		}
	}

	// --- entities ----------------------------------------------------------------------------

	private void blastEntities(double inner, double outer) {
		double cx = center.getX() + 0.5;
		double cy = center.getY() + 1.0;
		double cz = center.getZ() + 0.5;
		// The round planes everything up to MAX_CUT above the impact, so reach that high.
		Box box = new Box(cx - outer, cy - 24, cz - outer, cx + outer, cy + MAX_CUT, cz + outer);
		for (Entity entity : world.getOtherEntities(null, box, EntityPredicates.EXCEPT_SPECTATOR.and(Entity::isAlive))) {
			double dx = entity.getX() - cx;
			double dz = entity.getZ() - cz;
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist < inner || dist >= outer || ejecta.contains(entity.getUuid()) || !blasted.add(entity.getUuid())) {
				continue;
			}
			hit(entity, dx, dz, dist);
		}
	}

	private void hit(Entity entity, double dx, double dz, double dist) {
		if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity || entity instanceof FallingBlockEntity) {
			if (dist <= radius) {
				entity.discard();
			}
			return;
		}
		// Nothing in the planed zone survives. Out in the scorched ring the blast weakens with distance.
		double ring = dist <= radius ? 1.0 : 1.0 - (dist - radius) / Math.max(1.0, scorchRadius - radius);
		entity.damage(damage, dist <= radius ? 1000.0F : (float) (3.0 + 15.0 * ring * ring));
		entity.setOnFireFor((float) (3.0 + 7.0 * ring));
		double len = Math.max(dist, 0.001);
		double push = 0.6 + 2.0 * ring;
		entity.addVelocity(dx / len * push, 0.35 + 0.9 * ring, dz / len * push);
		entity.velocityModified = true;
	}

	private void spawnEjecta() {
		int floor = floorY(0) + 2;
		int count = MathHelper.clamp(radius * 3, 40, 260);
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double offset = 3.5 + random.nextDouble() * (bowlRadius - 3.5);
			BlockPos pos = BlockPos.ofFloored(center.getX() + 0.5 + Math.cos(angle) * offset, floor + random.nextInt(3),
					center.getZ() + 0.5 + Math.sin(angle) * offset);
			if (!world.isAir(pos)) {
				continue;
			}
			FallingBlockEntity block = FallingBlockEntity.spawnFromBlock(world, pos, debris());
			block.dropItem = false;
			block.setHurtEntities(2.0F, 20);
			double reach = Math.sqrt(radius / 28.0);
			double speed = (0.5 + random.nextDouble() * 0.9) * reach;
			block.setVelocity(Math.cos(angle) * speed, (0.9 + random.nextDouble() * 0.7) * reach, Math.sin(angle) * speed);
			block.velocityModified = true;
			ejecta.add(block.getUuid());
		}
	}

	// --- noise -------------------------------------------------------------------------------

	/** Smooth value noise in [-1, 1] on a 6-block lattice. */
	private static double noise(int x, int z) {
		double fx = x / 6.0;
		double fz = z / 6.0;
		int x0 = MathHelper.floor(fx);
		int z0 = MathHelper.floor(fz);
		double tx = smooth(fx - x0);
		double tz = smooth(fz - z0);
		double a = MathHelper.lerp(tx, hash(x0, z0), hash(x0 + 1, z0));
		double b = MathHelper.lerp(tx, hash(x0, z0 + 1), hash(x0 + 1, z0 + 1));
		return MathHelper.lerp(tz, a, b);
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double hash(int x, int z) {
		long h = x * 341873128712L + z * 132897987541L;
		h = (h ^ (h >>> 13)) * 0x5bd1e995L;
		h ^= h >>> 15;
		return ((h & 0xFFFF) / 32767.5) - 1.0;
	}
}
