package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** A living thing (by entity id) is locked in an endless moment ({@code on}) or let go: sent to everyone, and to a player joining. */
public record ForeverLockPayload(int target, boolean on) implements CustomPayload {
	public static final CustomPayload.Id<ForeverLockPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("forever_lock"));
	public static final PacketCodec<RegistryByteBuf, ForeverLockPayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, ForeverLockPayload::target,
			PacketCodecs.BOOL, ForeverLockPayload::on, ForeverLockPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
