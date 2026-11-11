package io.github.kortev.chainsawman.safety;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * One of the notice board's two blocks, hung on the board behind it and facing out ({@link #FACING}): the poster (using it gives a licence)
 * or the phone (using it sells devil parts). Thin against the back of its block; the poster's paper is drawn wider than the block.
 */
public class BoardBlock extends HorizontalFacingBlock {
	public static final MapCodec<BoardBlock> POSTER_CODEC = createCodec(s -> new BoardBlock(s, true));
	public static final MapCodec<BoardBlock> PHONE_CODEC = createCodec(s -> new BoardBlock(s, false));

	private final boolean poster;

	public BoardBlock(Settings settings, boolean poster) {
		super(settings);
		this.poster = poster;
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
		return poster ? POSTER_CODEC : PHONE_CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	/** The box (in sixteenths) for a block facing north, its back against the south face; turned for the others. */
	private VoxelShape box(Direction facing) {
		double x0 = poster ? 0 : 3;
		double x1 = poster ? 16 : 13;
		double y0 = 0;
		double y1 = poster ? 16 : 14;
		double depth = poster ? 1 : 6;
		return switch (facing) {
			case SOUTH -> Block.createCuboidShape(16 - x1, y0, 0, 16 - x0, y1, depth);
			case EAST -> Block.createCuboidShape(0, y0, x0, depth, y1, x1);
			case WEST -> Block.createCuboidShape(16 - depth, y0, 16 - x1, 16, y1, 16 - x0);
			default -> Block.createCuboidShape(x0, y0, 16 - depth, x1, y1, 16);
		};
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return box(state.get(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return poster ? VoxelShapes.empty() : box(state.get(FACING));
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && player instanceof ServerPlayerEntity sp) {
			if (poster) {
				Safety.poster(sp, pos);
			} else {
				Safety.phone(sp, pos);
			}
		}
		return ActionResult.success(world.isClient);
	}
}
