package io.github.kortev.chainsawman.hotel;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * What the inside of the Eternity Devil stands on and is shut in by ({@link Shaft}): a block nothing draws, that cannot be broken or aimed
 * at (no outline, so blows pass through to the devil's limbs in front of it), that you collide with and that shuts out the light. What is
 * seen there is the room's flesh model ({@code ArenaRenderer}), drawn over these.
 */
public class ArenaFleshBlock extends Block {
	public ArenaFleshBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	public static AbstractBlock.Settings settings() {
		return AbstractBlock.Settings.create().strength(-1.0F, 3600000.0F).dropsNothing().sounds(BlockSoundGroup.MUD).allowsSpawning((s, w, p, t) -> false);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.fullCube();
	}

	@Override
	protected VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
		return VoxelShapes.fullCube();
	}

	@Override
	protected VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
		return VoxelShapes.fullCube();
	}
}
