package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.block.MoltenCrustBlock;
import io.github.kortev.shootingstar.registry.ModBlocks;
import io.github.kortev.shootingstar.registry.ModDamageTypes;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.strike.StrikeManager;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import io.github.kortev.shootingstar.strike.Targeting;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class StrikeGameTests implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "a_blocks", tickLimit = 40)
	public void targetingAndCrust(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockPos base = context.getAbsolutePos(new BlockPos(1, 1, 1));

		// Stay inside the 8x8x8 test box: cast straight down onto a marker block.
		BlockPos marker = base.add(1, 1, 1);
		world.setBlockState(marker, Blocks.STONE.getDefaultState());
		Vec3d eye = Vec3d.ofCenter(base.add(1, 5, 1));
		BlockPos hit = Targeting.findTarget(world, eye, new Vec3d(0, -1, 0), 64);
		context.assertTrue(marker.equals(hit), "raycast hit " + hit + " instead of " + marker);
		context.assertTrue(Targeting.findTarget(world, eye, new Vec3d(0, 1, 0), 1) == null, "a short ray upward hit something");

		BlockPos cooling = base.add(0, 0, 3);
		world.setBlockState(cooling, ModBlocks.MOLTEN_CRUST.getDefaultState().with(MoltenCrustBlock.HEAT, 1));
		for (int i = 0; i < 400 && world.getBlockState(cooling).isOf(ModBlocks.MOLTEN_CRUST); i++) {
			world.getBlockState(cooling).randomTick(world, cooling, world.getRandom());
		}
		context.assertTrue(world.getBlockState(cooling).isOf(ModBlocks.FUSED_CRUST), "molten crust never cooled");

		BlockPos hot = base.add(3, 0, 3);
		world.setBlockState(hot, ModBlocks.MOLTEN_CRUST.getDefaultState());
		world.setBlockState(hot.east(), Blocks.WATER.getDefaultState());
		context.assertTrue(world.getBlockState(hot).isOf(ModBlocks.FUSED_CRUST), "water did not quench the crust");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "a_blocks", tickLimit = 40)
	public void kineticDamageKills(TestContext context) {
		ServerWorld world = context.getWorld();
		ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
		zombie.setAiDisabled(true);
		boolean applied = zombie.damage(ModDamageTypes.kineticStrike(world), 1000.0F);
		context.assertTrue(applied, "kinetic strike damage was refused");
		context.assertTrue(!zombie.isAlive(), "zombie survived 1000 kinetic damage with " + zombie.getHealth() + " hp");
		context.complete();
	}

	/** Runs a whole strike through the real timeline on flat stone and checks what is left. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "b_strike", tickLimit = StrikeTimeline.IMPACT + 120)
	public void fullStrike(TestContext context) {
		ServerWorld world = context.getWorld();
		// Radius 12: bowl out to 5, plain planed ground from 5 to 9, the debris lip beyond, scorched ring to 18.
		world.getGameRules().get(ModGameRules.CRATER_RADIUS).set(12, world.getServer());
		// Well clear of the test box so the crater cannot touch the test's own structure.
		BlockPos center = context.getAbsolutePos(new BlockPos(4, 3, 48));
		for (BlockPos pos : BlockPos.iterate(center.add(-20, -6, -20), center.add(20, 0, 20))) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
		for (BlockPos pos : BlockPos.iterate(center.add(3, 1, 3), center.add(5, 4, 5))) {
			world.setBlockState(pos, Blocks.OAK_PLANKS.getDefaultState());
		}
		// Relative positions (center is relative (4, 3, 48)). TestContext.getRelativePos mirrors unrotated tests,
		// so it cannot be used to convert back from absolute positions.
		ZombieEntity near = zombie(context, new BlockPos(2, 4, 48));
		ZombieEntity mid = zombie(context, new BlockPos(-2, 4, 48));
		ZombieEntity outer = zombie(context, new BlockPos(19, 4, 48));

		StrikeManager.launch(world, center, null);
		context.waitAndRun(StrikeTimeline.IMPACT + 30, () -> {
			// Collect every problem so one run reports all of them.
			StringBuilder problems = new StringBuilder();
			int top = world.getTopY() - 1;
			for (int y : new int[] {world.getBottomY() + 2, center.getY(), center.getY() + 100, top}) {
				BlockState state = world.getBlockState(new BlockPos(center.getX(), y, center.getZ()));
				if (!state.isOf(ModBlocks.GUNGNIR_HULL) && !state.isOf(ModBlocks.GUNGNIR_COIL) && !state.isOf(Blocks.BEDROCK)) {
					problems.append("spire missing at y=").append(y).append(": ").append(state).append("; ");
				}
			}
			BlockState crust = world.getBlockState(center.add(7, 0, 0));
			if (!crust.isOf(ModBlocks.MOLTEN_CRUST) && !crust.isOf(ModBlocks.FUSED_CRUST)) {
				problems.append("expected crust at +7 but found ").append(crust).append("; ");
			}
			int planks = 0;
			for (BlockPos pos : BlockPos.iterate(center.add(3, 1, 3), center.add(5, 4, 5))) {
				planks += world.getBlockState(pos).isOf(Blocks.OAK_PLANKS) ? 1 : 0;
			}
			if (planks > 0) {
				problems.append(planks).append(" blocks of the house survived; ");
			}
			if (world.getBlockState(center.add(3, 0, 0)).isOf(Blocks.STONE)) {
				problems.append("bowl not dug; ");
			}
			if (near.isAlive()) {
				problems.append("zombie at the impact point survived with ").append(near.getHealth()).append(" hp; ");
			}
			if (mid.isAlive()) {
				problems.append("zombie in the planed zone survived with ").append(mid.getHealth()).append(" hp; ");
			}
			if (!outer.isAlive() || outer.getHealth() >= outer.getMaxHealth() || !outer.isOnFire()) {
				problems.append("zombie in the scorched ring should be hurt and burning but has ").append(outer.getHealth())
						.append(" hp, alive=").append(outer.isAlive()).append(", burning=").append(outer.isOnFire()).append("; ");
			}
			ShootingStar.LOGGER.info("[gametest] fullStrike: {}", problems.length() == 0 ? "ok" : problems);
			context.assertTrue(problems.length() == 0, problems.toString());
			context.complete();
		});
	}

	private static ZombieEntity zombie(TestContext context, BlockPos relative) {
		ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, relative);
		zombie.setAiDisabled(true);
		return zombie;
	}
}
