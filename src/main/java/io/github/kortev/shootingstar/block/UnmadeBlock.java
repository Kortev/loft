package io.github.kortev.shootingstar.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * What is left at the edge of what Ginnungagap took: the world with its matter gone, pitch black, cracked through with
 * the other universe's light, which now and then comes loose from an open face and drifts off into the hole.
 */
public class UnmadeBlock extends Block {
	public UnmadeBlock(Settings settings) {
		super(settings);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(6) != 0) {
			return;
		}
		Direction face = Direction.random(random);
		BlockPos out = pos.offset(face);
		if (!world.getBlockState(out).isAir()) {
			return;
		}
		double x = pos.getX() + 0.5 + face.getOffsetX() * 0.55 + (random.nextDouble() - 0.5) * (face.getOffsetX() == 0 ? 0.9 : 0.0);
		double y = pos.getY() + 0.5 + face.getOffsetY() * 0.55 + (random.nextDouble() - 0.5) * (face.getOffsetY() == 0 ? 0.9 : 0.0);
		double z = pos.getZ() + 0.5 + face.getOffsetZ() * 0.55 + (random.nextDouble() - 0.5) * (face.getOffsetZ() == 0 ? 0.9 : 0.0);
		world.addParticle(random.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL, x, y, z,
				face.getOffsetX() * 0.02, face.getOffsetY() * 0.02 + 0.01, face.getOffsetZ() * 0.02);
	}
}
