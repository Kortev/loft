package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.block.MoltenCrustBlock;
import io.github.kortev.shootingstar.registry.ModBlocks;
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

		BlockPos wall = base.add(10, 2, 0);
		world.setBlockState(wall, Blocks.STONE.getDefaultState());
		Vec3d eye = Vec3d.ofCenter(base.add(0, 2, 0));
		BlockPos hit = Targeting.findTarget(world, eye, new Vec3d(1, 0, 0), 64);
		context.assertTrue(wall.equals(hit), "raycast hit " + hit + " instead of " + wall);
		context.assertTrue(Targeting.findTarget(world, eye, new Vec3d(0, 1, 0), 64) == null, "a ray into the sky hit something");

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

	/** Runs a whole strike through the real timeline on flat stone and checks what is left. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "b_strike", tickLimit = StrikeTimeline.IMPACT + 120)
	public void fullStrike(TestContext context) {
		ServerWorld world = context.getWorld();
		world.getGameRules().get(ModGameRules.CRATER_RADIUS).set(8, world.getServer());
		BlockPos center = context.getAbsolutePos(new BlockPos(4, 3, 4));
		for (BlockPos pos : BlockPos.iterate(center.add(-14, -6, -14), center.add(14, 0, 14))) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
		for (BlockPos pos : BlockPos.iterate(center.add(3, 1, 3), center.add(5, 4, 5))) {
			world.setBlockState(pos, Blocks.OAK_PLANKS.getDefaultState());
		}
		ZombieEntity near = context.spawnEntity(EntityType.ZOMBIE, context.getRelativePos(center.add(-2, 1, 0)));
		ZombieEntity mid = context.spawnEntity(EntityType.ZOMBIE, context.getRelativePos(center.add(-6, 1, 0)));
		near.setAiDisabled(true);
		mid.setAiDisabled(true);

		StrikeManager.launch(world, center, null);
		context.waitAndRun(StrikeTimeline.IMPACT + 30, () -> {
			int top = world.getTopY() - 1;
			for (int y : new int[] {world.getBottomY() + 2, center.getY(), center.getY() + 100, top}) {
				BlockState state = world.getBlockState(new BlockPos(center.getX(), y, center.getZ()));
				context.assertTrue(state.isOf(ModBlocks.GUNGNIR_HULL) || state.isOf(ModBlocks.GUNGNIR_COIL)
						|| state.isOf(Blocks.BEDROCK), "spire missing at y=" + y + ": " + state);
			}
			BlockPos planed = center.add(6, 0, 0);
			BlockState crust = world.getBlockState(planed);
			context.assertTrue(crust.isOf(ModBlocks.MOLTEN_CRUST), "expected molten crust at " + planed + ", found " + crust);
			context.assertTrue(world.getBlockState(planed.up()).isAir(), "planed zone was not cleared");
			context.assertTrue(world.getBlockState(center.add(4, 2, 4)).isAir(), "the house survived the impact");
			context.assertTrue(world.getBlockState(center.add(3, 0, 0)).isAir(), "the bowl was not dug");
			context.assertTrue(!near.isAlive(), "a zombie at the impact point survived");
			context.assertTrue(!mid.isAlive(), "a zombie in the planed zone survived");
			context.complete();
		});
	}
}
