package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * The Genesis Key turned on {@code target}. {@code age} is non-zero when syncing an event already playing;
 * {@code swapSpot} is the tree (or patch of ground) that will trade places whole with its mirror.
 */
public record GapLockPayload(int gapId, BlockPos target, UUID shooter, int age, int radius, boolean terrain, BlockPos swapSpot,
		boolean tree) implements CustomPayload {
	public static final CustomPayload.Id<GapLockPayload> ID = new CustomPayload.Id<>(ShootingStar.id("gap_lock"));
	public static final PacketCodec<RegistryByteBuf, GapLockPayload> CODEC = PacketCodec.of(GapLockPayload::write, GapLockPayload::read);

	private void write(RegistryByteBuf buf) {
		buf.writeVarInt(gapId);
		buf.writeBlockPos(target);
		buf.writeUuid(shooter);
		buf.writeVarInt(age);
		buf.writeVarInt(radius);
		buf.writeBoolean(terrain);
		buf.writeBlockPos(swapSpot);
		buf.writeBoolean(tree);
	}

	private static GapLockPayload read(RegistryByteBuf buf) {
		return new GapLockPayload(buf.readVarInt(), buf.readBlockPos(), buf.readUuid(), buf.readVarInt(), buf.readVarInt(),
				buf.readBoolean(), buf.readBlockPos(), buf.readBoolean());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
