package io.github.kortev.shootingstar.block;

import io.github.kortev.shootingstar.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;

/**
 * Ground the strike melted. Heat 3 is white-hot, heat 1 is a dull red glow; now and then a random tick
 * cools it one stage, and below heat 1 it sets into {@link ModBlocks#FUSED_CRUST}. Water quenches
 * it instantly.
 */
public class MoltenCrustBlock extends Block {
	public static final IntProperty HEAT = IntProperty.of("heat", 1, 3);

	public MoltenCrustBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(HEAT, 3));
	}

	public static int luminance(BlockState state) {
		return switch (state.get(HEAT)) {
			case 3 -> 15;
			case 2 -> 12;
			default -> 9;
		};
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(HEAT);
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		int heat = state.get(HEAT);
		// Slow: a bowl of molten rock keeps glowing for a quarter of an hour.
		if (random.nextInt(heat == 3 ? 4 : 5) != 0) {
			return;
		}
		if (heat > 1) {
			world.setBlockState(pos, state.with(HEAT, heat - 1), Block.NOTIFY_ALL);
		} else {
			world.setBlockState(pos, ModBlocks.FUSED_CRUST.getDefaultState(), Block.NOTIFY_ALL);
		}
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
			WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		FluidState fluid = neighborState.getFluidState();
		if (fluid.isIn(FluidTags.WATER)) {
			world.syncWorldEvent(WorldEvents.LAVA_EXTINGUISHED, pos, 0);
			return ModBlocks.FUSED_CRUST.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
		if (!entity.bypassesSteppingEffects() && entity instanceof LivingEntity && !entity.isFireImmune()) {
			entity.damage(world.getDamageSources().hotFloor(), state.get(HEAT));
		}
		super.onSteppedOn(world, pos, state, entity);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!world.getBlockState(pos.up()).isAir()) {
			return;
		}
		int heat = state.get(HEAT);
		double x = pos.getX() + random.nextDouble();
		double y = pos.getY() + 1.02;
		double z = pos.getZ() + random.nextDouble();
		if (random.nextInt(heat == 3 ? 4 : 10) == 0) {
			world.addParticle(heat == 3 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x, y, z, 0.0, 0.03, 0.0);
		}
		if (heat == 3 && random.nextInt(30) == 0) {
			world.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
		}
	}
}
