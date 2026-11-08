package io.github.kortev.chainsawman.hotel;

import com.mojang.serialization.MapCodec;
import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Eternity Devil's home: an invisible block buried in the hotel's foundation under the lobby, whose block entity runs the hotel
 * ({@link Hotel}): day and night, the loop, the deal, the fight, and the devil coming back. Unbreakable; nothing is drawn.
 */
public class HomeBlock extends BlockWithEntity {
	public static final MapCodec<HomeBlock> CODEC = createCodec(HomeBlock::new);

	public HomeBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	public static AbstractBlock.Settings settings() {
		return AbstractBlock.Settings.create().strength(-1.0F, 3600000.0F).dropsNothing().nonOpaque();
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new HomeBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ChainsawMan.HOME_ENTITY, (w, p, s, be) -> Hotel.tick((net.minecraft.server.world.ServerWorld) w, be));
	}
}
