package io.github.kortev.gojolander.network;

import io.github.kortev.gojolander.Gojolander;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

/**
 * A Domain cast at {@code center}: who cast it ({@link #NOBODY} for one an operator called), how far it reaches and how
 * many ticks its void holds. {@code age} is non-zero when telling someone of one already under way.
 */
public record DomainLockPayload(int domainId, UUID caster, Vec3d center, float radius, int hold, int age) implements CustomPayload {
	public static final UUID NOBODY = new UUID(0L, 0L);
	public static final CustomPayload.Id<DomainLockPayload> ID = new CustomPayload.Id<>(Gojolander.id("domain_lock"));
	public static final PacketCodec<RegistryByteBuf, DomainLockPayload> CODEC = PacketCodec.of(DomainLockPayload::write,
			DomainLockPayload::read);

	private void write(RegistryByteBuf buf) {
		buf.writeVarInt(domainId);
		buf.writeUuid(caster);
		buf.writeDouble(center.x);
		buf.writeDouble(center.y);
		buf.writeDouble(center.z);
		buf.writeFloat(radius);
		buf.writeVarInt(hold);
		buf.writeVarInt(age);
	}

	private static DomainLockPayload read(RegistryByteBuf buf) {
		return new DomainLockPayload(buf.readVarInt(), buf.readUuid(), new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
				buf.readFloat(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
