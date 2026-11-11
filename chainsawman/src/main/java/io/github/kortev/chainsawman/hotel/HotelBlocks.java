package io.github.kortev.chainsawman.hotel;

import com.mojang.serialization.MapCodec;
import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The hotel's own blocks (package 0.1g), all at the game's own sixteen pixels to the block, textures by {@code tools/gen_hotel_corridor.py}:
 * <ul>
 * <li>the stairwell's spine wall between its two flights, with the handrail of a flight fixed along it ({@link Spine});</li>
 * <li>the floor sign on each landing ({@link FloorSign}: it reads its floor, and 8 everywhere while the loop holds);</li>
 * <li>the wall clock at the loop corridor's ends, along it and in the lobby ({@link Clock}: the time of day, or 8:18 while the loop holds);</li>
 * <li>the wall torn open where the walls bleed toward the devil: the wallpaper ripped, the lath under it, the flesh pushing through.</li>
 * </ul>
 */
public final class HotelBlocks {
	private HotelBlocks() {
	}

	/** Which handrail a spine block carries (the rails are drawn longer than the block, along the flight). */
	public enum Rail implements StringIdentifiable {
		NONE("none"), LOW("low"), HIGH("high");

		private final String name;

		Rail(String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return name;
		}
	}

	/**
	 * The wall between the stairwell's two lanes (plastered like the corridor), its facing the plan's +x (the way the lower flight climbs). A
	 * {@code low} block carries the lower flight's rail on its +z face, a {@code high} one the upper flight's on its -z face.
	 */
	public static class Spine extends HorizontalFacingBlock {
		public static final MapCodec<Spine> CODEC = createCodec(Spine::new);
		public static final EnumProperty<Rail> RAIL = EnumProperty.of("rail", Rail.class);

		public Spine(Settings settings) {
			super(settings);
			setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.EAST).with(RAIL, Rail.NONE));
		}

		@Override
		protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
			return CODEC;
		}

		@Override
		protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
			builder.add(FACING, RAIL);
		}
	}

	/** A thin plate on a wall, facing out (its back against the block behind): the floor sign and the clock. */
	public abstract static class Plate extends HorizontalFacingBlock {
		private final double w;
		private final double h;
		private final double y0;

		protected Plate(Settings settings, double w, double h, double y0) {
			super(settings);
			this.w = w;
			this.h = h;
			this.y0 = y0;
		}

		@Override
		public BlockState getPlacementState(ItemPlacementContext ctx) {
			return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
		}

		@Override
		protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			double x0 = 8.0 - w / 2.0;
			double x1 = 8.0 + w / 2.0;
			double d = 1.0;
			return switch (state.get(FACING)) {
				case SOUTH -> Block.createCuboidShape(16 - x1, y0, 0, 16 - x0, y0 + h, d);
				case EAST -> Block.createCuboidShape(0, y0, x0, d, y0 + h, x1);
				case WEST -> Block.createCuboidShape(16 - d, y0, 16 - x1, 16, y0 + h, 16 - x0);
				default -> Block.createCuboidShape(x0, y0, 16 - d, x1, y0 + h, 16);
			};
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return net.minecraft.util.shape.VoxelShapes.empty();
		}
	}

	/** The floor sign: the floor it reads, 1 to 8. */
	public static class FloorSign extends Plate {
		public static final MapCodec<FloorSign> CODEC = createCodec(FloorSign::new);
		public static final IntProperty FLOOR = IntProperty.of("floor", 1, 8);

		public FloorSign(Settings settings) {
			super(settings, 10.0, 10.0, 3.0);
			setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(FLOOR, 1));
		}

		@Override
		protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
			return CODEC;
		}

		@Override
		protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
			builder.add(FACING, FLOOR);
		}
	}

	/** The wall clock: its face is the block's model, its hands are drawn by the client from the time (or 8:18 when it is {@link #STOPPED}). */
	public static class Clock extends Plate implements BlockEntityProvider {
		public static final MapCodec<Clock> CODEC = createCodec(Clock::new);
		public static final BooleanProperty STOPPED = BooleanProperty.of("stopped");

		public Clock(Settings settings) {
			super(settings, 12.0, 12.0, 2.0);
			setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(STOPPED, false));
		}

		@Override
		protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
			return CODEC;
		}

		@Override
		protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
			builder.add(FACING, STOPPED);
		}

		@Override
		public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
			return new ClockEntity(pos, state);
		}
	}

	/** Only there so the client can draw the hands. */
	public static class ClockEntity extends BlockEntity {
		public ClockEntity(BlockPos pos, BlockState state) {
			super(CLOCK_ENTITY, pos, state);
		}
	}

	/** Minutes past twelve on the clock's dial (0 to 719): 8:18 if it is stopped, else the world's time of day (tick 0 is six in the morning). */
	public static int reading(World world, BlockState state) {
		if (state.getBlock() instanceof Clock && state.get(Clock.STOPPED)) {
			return STOPPED_AT;
		}
		long t = Math.floorMod(world.getTimeOfDay(), 24000L);
		int minutes = (int) ((t * 60L / 1000L + 6 * 60) % (24 * 60));
		return minutes % (12 * 60);
	}

	/** 8:18, where the show's clocks stop inside the loop. */
	public static final int STOPPED_AT = 8 * 60 + 18;

	private static Block register(String name, Block block) {
		return Registry.register(Registries.BLOCK, ChainsawMan.id(name), block);
	}

	public static final Block SPINE = register("hotel_spine", new Spine(AbstractBlock.Settings.create().mapColor(MapColor.PALE_YELLOW).strength(0.8F)
			.sounds(BlockSoundGroup.STONE)));
	public static final Block FLOOR_SIGN = register("hotel_floor_sign", new FloorSign(AbstractBlock.Settings.create().mapColor(MapColor.GREEN).strength(0.5F)
			.sounds(BlockSoundGroup.METAL).noCollision().nonOpaque()));
	public static final Block CLOCK = register("hotel_clock", new Clock(AbstractBlock.Settings.create().mapColor(MapColor.BROWN).strength(0.5F)
			.sounds(BlockSoundGroup.WOOD).noCollision().nonOpaque()));
	public static final Block TORN_PAPER = register("hotel_torn_paper", new Block(AbstractBlock.Settings.create().mapColor(MapColor.PALE_YELLOW).strength(0.8F)
			.sounds(BlockSoundGroup.WOOD)));
	public static final Block TORN_LATH = register("hotel_torn_lath", new Block(AbstractBlock.Settings.create().mapColor(MapColor.BROWN).strength(0.8F)
			.sounds(BlockSoundGroup.WOOD)));
	public static final Block WALL_FLESH = register("hotel_wall_flesh", new Block(AbstractBlock.Settings.create().mapColor(MapColor.DULL_RED).strength(0.8F)
			.sounds(BlockSoundGroup.WART_BLOCK)));
	/** The lit exit sign over a stair door, set in the wall (a green plate with a running man on wallpaper, both faces). */
	public static final Block EXIT_SIGN = register("hotel_exit_sign", new Block(AbstractBlock.Settings.create().mapColor(MapColor.PALE_YELLOW).strength(0.8F)
			.sounds(BlockSoundGroup.STONE).luminance(s -> 6)));
	public static final BlockEntityType<ClockEntity> CLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, ChainsawMan.id("hotel_clock"),
			BlockEntityType.Builder.create(ClockEntity::new, CLOCK).build(null));

	/** Loads the class (the blocks register as it loads). */
	public static void init() {
	}
}
