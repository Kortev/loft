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
 * Ground Mjölnir's bolt has just fused into glass, still holding its charge: charge 3 glows white-blue and spits
 * sparks, charge 1 a dim blue. Now and then a random tick bleeds a little of it away, and with none left it is plain
 * {@link ModBlocks#FULGURITE}. Water earths it at once. Standing on it gives you a shock.
 */
public class ChargedFulguriteBlock extends Block {
	public static final IntProperty CHARGE = IntProperty.of("charge", 1, 3);

	public ChargedFulguriteBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(CHARGE, 3));
	}

	public static int luminance(BlockState state) {
		return switch (state.get(CHARGE)) {
			case 3 -> 14;
			case 2 -> 11;
			default -> 8;
		};
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(CHARGE);
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		int charge = state.get(CHARGE);
		// Slow: the scar keeps glowing for a quarter of an hour, the way Gungnir's crater does.
		if (random.nextInt(charge == 3 ? 4 : 5) != 0) {
			return;
		}
		if (charge > 1) {
			world.setBlockState(pos, state.with(CHARGE, charge - 1), Block.NOTIFY_ALL);
		} else {
			world.setBlockState(pos, ModBlocks.FULGURITE.getDefaultState(), Block.NOTIFY_ALL);
		}
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
			WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		FluidState fluid = neighborState.getFluidState();
		if (fluid.isIn(FluidTags.WATER)) {
			world.syncWorldEvent(WorldEvents.LAVA_EXTINGUISHED, pos, 0);
			return ModBlocks.FULGURITE.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
		if (!entity.bypassesSteppingEffects() && entity instanceof LivingEntity) {
			entity.damage(world.getDamageSources().lightningBolt(), state.get(CHARGE));
		}
		super.onSteppedOn(world, pos, state, entity);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!world.getBlockState(pos.up()).isAir()) {
			return;
		}
		int charge = state.get(CHARGE);
		double x = pos.getX() + random.nextDouble();
		double y = pos.getY() + 1.02;
		double z = pos.getZ() + random.nextDouble();
		if (random.nextInt(charge == 3 ? 3 : charge == 2 ? 6 : 14) == 0) {
			world.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, (random.nextDouble() - 0.5) * 0.2, 0.08 + random.nextDouble() * 0.1,
					(random.nextDouble() - 0.5) * 0.2);
		}
		if (charge == 3 && random.nextInt(12) == 0) {
			world.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.02, 0.0);
		}
	}
}
