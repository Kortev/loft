package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Blocks about to trade places with their twins in the mirror universe: {@code kind} 0 is a single block, 1 the
 * tree or patch the close-up watches. Sent just before the blocks change, so clients still see what they were.
 */
public record GapSwapPayload(int gapId, int kind, List<BlockPos> positions) implements CustomPayload {
	public static final CustomPayload.Id<GapSwapPayload> ID = new CustomPayload.Id<>(ShootingStar.id("gap_swap"));
	public static final PacketCodec<RegistryByteBuf, GapSwapPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, GapSwapPayload::gapId,
			PacketCodecs.VAR_INT, GapSwapPayload::kind,
			BlockPos.PACKET_CODEC.collect(PacketCodecs.toList()), GapSwapPayload::positions,
			GapSwapPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
