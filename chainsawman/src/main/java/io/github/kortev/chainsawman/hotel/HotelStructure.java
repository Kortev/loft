package io.github.kortev.chainsawman.hotel;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

/**
 * The hotel in the world: a rare structure on flat dry ground (the plains, meadows, taiga and savanna that its biome tag lists, well apart),
 * drawn from {@link HotelPlan} one chunk at a time. It looks at nine points over its footprint and is not made on water or if they
 * differ by more than {@link #STEEP} blocks.
 */
public class HotelStructure extends Structure {
	public static final MapCodec<HotelStructure> CODEC = createCodec(HotelStructure::new);
	/** The most the ground may differ over the footprint. */
	public static final int STEEP = 4;

	public static final StructureType<HotelStructure> TYPE = () -> CODEC;
	public static final StructurePieceType PIECE = (StructurePieceType.Simple) HotelPiece::new;

	public HotelStructure(Config config) {
		super(config);
	}

	@Override
	protected Optional<StructurePosition> getStructurePosition(Context context) {
		ChunkPos chunk = context.chunkPos();
		int rot = context.random().nextInt(4);
		BlockPos centre = new BlockPos(chunk.getStartX() + 8, 0, chunk.getStartZ() + 8);
		// The origin is the local (0, 0) corner: the middle of the plan lands on the middle of the chunk.
		Vec3d off = HotelBuilder.dir(rot, HotelPlan.SX / 2.0, 0.0, HotelPlan.SZ / 2.0);
		int ox = centre.getX() - (int) Math.round(off.x);
		int oz = centre.getZ() - (int) Math.round(off.z);
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		int sum = 0;
		int n = 0;
		for (int sx = 0; sx < 3; sx++) {
			for (int sz = 0; sz < 3; sz++) {
				BlockPos p = HotelBuilder.world(new BlockPos(ox, 0, oz), rot, sx * (HotelPlan.SX - 1) / 2, 0, sz * (HotelPlan.SZ - 1) / 2);
				int top = context.chunkGenerator().getHeightInGround(p.getX(), p.getZ(), Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
				int floor = context.chunkGenerator().getHeightInGround(p.getX(), p.getZ(), Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig());
				if (top > floor) {
					return Optional.empty();
				}
				min = Math.min(min, floor);
				max = Math.max(max, floor);
				sum += floor;
				n++;
			}
		}
		if (max - min > STEEP) {
			return Optional.empty();
		}
		BlockPos origin = new BlockPos(ox, Math.round(sum / (float) n), oz);
		int turn = rot;
		return Optional.of(new StructurePosition(origin, collector -> collector.addPiece(new HotelPiece(origin, turn))));
	}

	@Override
	public StructureType<?> getType() {
		return TYPE;
	}

	/** The one piece: the whole hotel. */
	public static class HotelPiece extends StructurePiece {
		private final BlockPos origin;
		private final int rot;

		public HotelPiece(BlockPos origin, int rot) {
			super(PIECE, 0, HotelBuilder.box(origin, rot));
			this.origin = origin;
			this.rot = rot;
		}

		public HotelPiece(NbtCompound nbt) {
			super(PIECE, nbt);
			this.origin = new BlockPos(nbt.getInt("OX"), nbt.getInt("OY"), nbt.getInt("OZ"));
			this.rot = nbt.getInt("Rot");
		}

		@Override
		protected void writeNbt(StructureContext context, NbtCompound nbt) {
			nbt.putInt("OX", origin.getX());
			nbt.putInt("OY", origin.getY());
			nbt.putInt("OZ", origin.getZ());
			nbt.putInt("Rot", rot);
		}

		@Override
		public void generate(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator, Random random, BlockBox chunkBox,
				ChunkPos chunkPos, BlockPos pivot) {
			HotelBuilder.place(world, origin, rot, chunkBox);
		}
	}
}
