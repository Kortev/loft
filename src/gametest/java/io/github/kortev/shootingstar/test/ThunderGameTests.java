package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.block.ChargedFulguriteBlock;
import io.github.kortev.shootingstar.item.MjolnirItem;
import io.github.kortev.shootingstar.registry.ModBlocks;
import io.github.kortev.shootingstar.registry.ModDamageTypes;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.thunder.Lichtenberg;
import io.github.kortev.shootingstar.thunder.ThunderBuilder;
import io.github.kortev.shootingstar.thunder.ThunderManager;
import io.github.kortev.shootingstar.thunder.ThunderTimeline;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/** Mjölnir: the hammer's refusals, the scar's figure, the fulgurite, and whole strikes on flat ground. */
public class ThunderGameTests implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "e_thunder", tickLimit = 40)
	public void refusalsAndFigure(TestContext context) {
		StringBuilder problems = new StringBuilder();
		Vec3d eye = new Vec3d(0.5, 70.0, 0.5);
		double min = ThunderTimeline.minRange(64);
		expectKey(problems, MjolnirItem.refusal(null, eye, min), "message.shootingstar.mjolnir.no_target", "no target");
		expectKey(problems, MjolnirItem.refusal(new BlockPos(20, 64, 0), eye, min), "message.shootingstar.mjolnir.too_close",
				"a target 20 blocks off");
		if (MjolnirItem.refusal(new BlockPos(200, 64, 0), eye, min) != null) {
			problems.append("refused a target 200 blocks off; ");
		}
		if (min != ThunderTimeline.scarRadius(64)) {
			problems.append("the closest target ").append(min).append(" is not the edge of the scar; ");
		}

		// The same seed grows the same scar, on the server and on every client.
		Lichtenberg a = Lichtenberg.grow(1234L, 64);
		Lichtenberg b = Lichtenberg.grow(1234L, 64);
		if (!a.segments().equals(b.segments())) {
			problems.append("the same seed grew two different scars; ");
		}
		if (a.segments().size() < 200) {
			problems.append("the scar has only ").append(a.segments().size()).append(" segments; ");
		}
		List<Lichtenberg.Cell> cells = a.cells(ThunderTimeline.coreRadius(64) + 0.5);
		int far = 0;
		for (Lichtenberg.Cell cell : cells) {
			double d = Math.sqrt(cell.dx() * cell.dx() + cell.dz() * cell.dz());
			if (d > ThunderTimeline.scarRadius(64) + 4) {
				problems.append("a scar cell at ").append(d).append(" is past the edge; ");
				break;
			}
			far = Math.max(far, (int) d);
		}
		if (far < 64) {
			problems.append("the scar only reaches ").append(far).append(" blocks out; ");
		}
		for (int i = 1; i < cells.size(); i++) {
			if (cells.get(i).arrival() < cells.get(i - 1).arrival()) {
				problems.append("scar cells out of order; ");
				break;
			}
		}

		// The leader only ever moves down, and is on the ground by the stroke.
		double last = 0.0;
		for (int t = ThunderTimeline.INBOUND; t <= ThunderTimeline.STROKE; t++) {
			double reach = ThunderTimeline.leaderReach(t);
			if (reach < last) {
				problems.append("the leader went back up at ").append(t).append("; ");
				break;
			}
			last = reach;
		}
		if (last != 1.0) {
			problems.append("the leader had only reached ").append(last).append(" by the stroke; ");
		}
		ShootingStar.LOGGER.info("[gametest] refusalsAndFigure: {}", problems.length() == 0 ? "ok" : problems);
		context.assertTrue(problems.length() == 0, problems.toString());
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "e_thunder", tickLimit = 40)
	public void fulguriteDischarges(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockPos base = context.getAbsolutePos(new BlockPos(1, 1, 1));
		BlockPos cooling = base.add(0, 0, 3);
		world.setBlockState(cooling, ModBlocks.CHARGED_FULGURITE.getDefaultState().with(ChargedFulguriteBlock.CHARGE, 1));
		for (int i = 0; i < 400 && world.getBlockState(cooling).isOf(ModBlocks.CHARGED_FULGURITE); i++) {
			world.getBlockState(cooling).randomTick(world, cooling, world.getRandom());
		}
		context.assertTrue(world.getBlockState(cooling).isOf(ModBlocks.FULGURITE), "charged fulgurite never lost its charge");

		BlockPos hot = base.add(3, 0, 3);
		world.setBlockState(hot, ModBlocks.CHARGED_FULGURITE.getDefaultState());
		world.setBlockState(hot.east(), Blocks.WATER.getDefaultState());
		context.assertTrue(world.getBlockState(hot).isOf(ModBlocks.FULGURITE), "water did not earth the charged fulgurite");

		ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
		zombie.setAiDisabled(true);
		context.assertTrue(zombie.damage(ModDamageTypes.thunderstruck(world, null), 1000.0F), "Mjölnir's damage was refused");
		context.assertTrue(!zombie.isAlive(), "zombie survived 1000 thunderstruck damage");
		context.complete();
	}

	/**
	 * A whole strike through the real timeline on flat grass over stone, radius 20: a crater 8 across fused to fulgurite,
	 * the bolt standing in it, the scar out to 30, the tree charred, the shed burned, the zone's zombies dead, and the
	 * creatures in the ring arced and a creeper charged; bedrock untouched.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "f_thunder", tickLimit = ThunderTimeline.STROKE + 120)
	public void fullStroke(TestContext context) {
		ServerWorld world = context.getWorld();
		world.getGameRules().get(ModGameRules.MJOLNIR_RADIUS).set(20, world.getServer());
		BlockPos center = context.getAbsolutePos(new BlockPos(4, 3, 48));
		for (BlockPos pos : BlockPos.iterate(center.add(-33, -9, -33), center.add(33, 0, 33))) {
			world.setBlockState(pos, pos.getY() == center.getY() ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.STONE.getDefaultState());
		}
		BlockPos bedrock = center.add(3, -2, 0);
		world.setBlockState(bedrock, Blocks.BEDROCK.getDefaultState());
		// A tree in the zone and a wooden shed beside it.
		for (int y = 1; y <= 4; y++) {
			world.setBlockState(center.add(12, y, 0), Blocks.OAK_LOG.getDefaultState());
		}
		for (BlockPos pos : BlockPos.iterate(center.add(11, 5, -1), center.add(13, 5, 1))) {
			world.setBlockState(pos, Blocks.OAK_LEAVES.getDefaultState());
		}
		for (BlockPos pos : BlockPos.iterate(center.add(14, 1, -6), center.add(15, 2, -5))) {
			world.setBlockState(pos, Blocks.OAK_PLANKS.getDefaultState());
		}
		// Relative positions: the centre is relative (4, 3, 48).
		ZombieEntity near = zombie(context, new BlockPos(6, 4, 48));
		ZombieEntity mid = zombie(context, new BlockPos(-11, 4, 48));
		CreeperEntity creeper = context.spawnEntity(EntityType.CREEPER, new BlockPos(30, 4, 48));
		creeper.setAiDisabled(true);
		ZombieEntity outer = context.spawnEntity(EntityType.HUSK, new BlockPos(4, 4, 74));
		outer.setAiDisabled(true);

		ThunderManager.launch(world, center, null);
		// The creeper is looked at once its arc has landed (a few ticks after the stroke) and before the scar can reach
		// it (26 blocks of path at 2.5 a tick): if an arm of the scar then runs under it, the charged fulgurite may still
		// shock it to death, which is the scar's doing, not the arc's.
		StringBuilder arced = new StringBuilder();
		context.waitAndRun(ThunderTimeline.STROKE + 8, () -> {
			if (!creeper.isAlive()) {
				arced.append("the creeper in the ring died instead of being arced and charged; ");
			} else if (!creeper.shouldRenderOverlay()) {
				arced.append("the creeper in the ring was not charged (").append(creeper.getHealth()).append(" hp); ");
			}
		});
		context.waitAndRun(ThunderTimeline.STROKE + 60, () -> {
			StringBuilder problems = new StringBuilder(arced);
			if (!world.getBlockState(bedrock).isOf(Blocks.BEDROCK)) {
				problems.append("the bedrock in the crater was broken; ");
			}
			// The crater: the bowl blown out above its floor (counted over a ring of it, so the odd shard thrown back in or
			// the foot of the bolt does not matter), its floor fused.
			int open = 0;
			int bowl = 0;
			for (BlockPos pos : BlockPos.iterate(center.add(-7, -1, -7), center.add(7, -1, 7))) {
				int dx = pos.getX() - center.getX();
				int dz = pos.getZ() - center.getZ();
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d >= 4.5 && d <= 6.5) {
					bowl++;
					open += world.getBlockState(pos).isAir() ? 1 : 0;
				}
			}
			if (open < bowl * 0.8) {
				problems.append("crater not blown out: ").append(open).append(" of ").append(bowl).append(" open; ");
			}
			if (!fused(world, center.add(6, -5, 0), center.add(6, -1, 0))) {
				problems.append("no fused floor under the crater at +6; ");
			}
			// The petrified bolt standing in it.
			if (!fused(world, center.add(-8, 10, -8), center.add(8, 10, 8))) {
				problems.append("no petrified bolt 10 over the crater; ");
			}
			// The scar out across the ring.
			int scar = 0;
			for (BlockPos pos : BlockPos.iterate(center.add(-30, -5, -30), center.add(30, 0, 30))) {
				int dx = pos.getX() - center.getX();
				int dz = pos.getZ() - center.getZ();
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 9 && d <= 30) {
					BlockState state = world.getBlockState(pos);
					if (state.isOf(ModBlocks.CHARGED_FULGURITE) || state.isOf(ModBlocks.FULGURITE)) {
						scar++;
					}
				}
			}
			if (scar < 40) {
				problems.append("only ").append(scar).append(" blocks of scar burned into the ground; ");
			}
			// The heat: grass burned off, the tree charred and stripped, the shed gone.
			if (world.getBlockState(center.add(-10, 0, 10)).isOf(Blocks.GRASS_BLOCK)) {
				problems.append("grass still growing 14 from the strike; ");
			}
			int logs = 0;
			int charred = 0;
			int leaves = 0;
			for (BlockPos pos : BlockPos.iterate(center.add(11, 1, -1), center.add(13, 5, 1))) {
				BlockState state = world.getBlockState(pos);
				logs += state.isOf(Blocks.OAK_LOG) ? 1 : 0;
				charred += state.isOf(ModBlocks.CHARRED_LOG) ? 1 : 0;
				leaves += state.isOf(Blocks.OAK_LEAVES) ? 1 : 0;
			}
			if (logs > 0 || leaves > 0 || charred == 0) {
				problems.append("tree: ").append(logs).append(" logs, ").append(leaves).append(" leaves, ").append(charred)
						.append(" charred").append(survivors(world, center, center.add(11, 1, -1), center.add(13, 5, 1),
								Blocks.OAK_LEAVES)).append("; ");
			}
			int planks = 0;
			for (BlockPos pos : BlockPos.iterate(center.add(14, 1, -6), center.add(15, 2, -5))) {
				planks += world.getBlockState(pos).isOf(Blocks.OAK_PLANKS) ? 1 : 0;
			}
			if (planks > 0) {
				problems.append(planks).append(" planks of the shed survived")
						.append(survivors(world, center, center.add(14, 1, -6), center.add(15, 2, -5), Blocks.OAK_PLANKS)).append("; ");
			}
			if (near.isAlive()) {
				problems.append("zombie by the strike survived with ").append(near.getHealth()).append(" hp; ");
			}
			if (mid.isAlive()) {
				problems.append("zombie in the zone survived with ").append(mid.getHealth()).append(" hp; ");
			}
			if (outer.isAlive() && outer.getHealth() >= outer.getMaxHealth()) {
				problems.append("the husk in the ring was never arced; ");
			}
			ShootingStar.LOGGER.info("[gametest] fullStroke: scar {} blocks; {}", scar, problems.length() == 0 ? "ok" : problems);
			context.assertTrue(problems.length() == 0, problems.toString());
			context.complete();
		});
	}

	/** With mjolnirTerrainDamage off the stroke still kills, but no block moves. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_thunderheld", tickLimit = 40)
	public void terrainHeld(TestContext context) {
		ServerWorld world = context.getWorld();
		world.getGameRules().get(ModGameRules.MJOLNIR_TERRAIN).set(false, world.getServer());
		try {
			BlockPos center = context.getAbsolutePos(new BlockPos(4, 3, 40));
			for (BlockPos pos : BlockPos.iterate(center.add(-20, -3, -20), center.add(20, 0, 20))) {
				world.setBlockState(pos, Blocks.STONE.getDefaultState());
			}
			world.setBlockState(center.add(5, 1, 0), Blocks.OAK_LOG.getDefaultState());
			ZombieEntity zombie = zombie(context, new BlockPos(6, 4, 40));
			ThunderBuilder builder = new ThunderBuilder(world, center, 12, 99L, null, Util.NIL_UUID);
			builder.start();
			for (int i = 0; i < 200 && !builder.step(); i++) {
				// Run the whole stroke at once.
			}
			StringBuilder problems = new StringBuilder();
			if (!world.getBlockState(center).isOf(Blocks.STONE)) {
				problems.append("the struck block changed: ").append(world.getBlockState(center)).append("; ");
			}
			if (!world.getBlockState(center.add(5, 1, 0)).isOf(Blocks.OAK_LOG)) {
				problems.append("the log was charred; ");
			}
			if (builder.boltHeight() != 0) {
				problems.append("a bolt was left standing with terrain held; ");
			}
			if (zombie.isAlive()) {
				problems.append("the zombie survived; ");
			}
			context.assertTrue(problems.length() == 0, problems.toString());
		} finally {
			world.getGameRules().get(ModGameRules.MJOLNIR_TERRAIN).set(true, world.getServer());
		}
		context.complete();
	}

	/** Where each block of {@code kind} left in the box is, and what the heightmaps say about its column. */
	private static String survivors(ServerWorld world, BlockPos center, BlockPos from, BlockPos to, Block kind) {
		StringBuilder out = new StringBuilder();
		for (BlockPos pos : BlockPos.iterate(from, to)) {
			if (world.getBlockState(pos).isOf(kind)) {
				out.append(" [").append(pos.subtract(center).toShortString()).append(" surface ")
						.append(world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ()) - center.getY())
						.append(" blocking ")
						.append(world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ()) - center.getY())
						.append(" below ").append(world.getBlockState(pos.down())).append("]");
			}
		}
		return out.toString();
	}

	/**
	 * The petrified bolt goes up as soon as the crater is open, and its forks can reach out over ground the heat has not
	 * got to yet: what is under them must burn all the same. Fulgurite hung over a leaf and (high up) over a log stands in
	 * for the forks.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_thunderbolt", tickLimit = 40)
	public void burnsUnderTheBolt(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockPos center = context.getAbsolutePos(new BlockPos(4, 3, 40));
		for (BlockPos pos : BlockPos.iterate(center.add(-25, -3, -25), center.add(25, 0, 25))) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
		BlockPos leaf = center.add(11, 3, 0);
		world.setBlockState(leaf, Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
		world.setBlockState(center.add(11, 20, 0), ModBlocks.FULGURITE.getDefaultState());
		BlockPos log = center.add(13, 1, 2);
		world.setBlockState(log, Blocks.OAK_LOG.getDefaultState());
		world.setBlockState(center.add(13, 150, 2), ModBlocks.CHARGED_FULGURITE.getDefaultState());
		ThunderBuilder builder = new ThunderBuilder(world, center, 16, 7L, null, Util.NIL_UUID);
		builder.start();
		for (int i = 0; i < 200 && !builder.step(); i++) {
			// Run the whole stroke at once.
		}
		StringBuilder problems = new StringBuilder();
		if (world.getBlockState(leaf).isOf(Blocks.OAK_LEAVES)) {
			problems.append("the leaf under the fulgurite did not burn; ");
		}
		if (!world.getBlockState(log).isOf(ModBlocks.CHARRED_LOG)) {
			problems.append("the log under the high fulgurite is ").append(world.getBlockState(log)).append("; ");
		}
		context.assertTrue(problems.length() == 0, problems.toString());
		context.complete();
	}

	private static boolean fused(ServerWorld world, BlockPos from, BlockPos to) {
		for (BlockPos pos : BlockPos.iterate(from, to)) {
			BlockState state = world.getBlockState(pos);
			if (state.isOf(ModBlocks.CHARGED_FULGURITE) || state.isOf(ModBlocks.FULGURITE)) {
				return true;
			}
		}
		return false;
	}

	private static void expectKey(StringBuilder problems, Text text, String key, String what) {
		if (text == null || !(text.getContent() instanceof TranslatableTextContent content) || !content.getKey().equals(key)) {
			problems.append(what).append(" gave ").append(text).append(" instead of ").append(key).append("; ");
		}
	}

	private static ZombieEntity zombie(TestContext context, BlockPos relative) {
		ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, relative);
		zombie.setAiDisabled(true);
		return zombie;
	}
}
