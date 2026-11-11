package io.github.kortev.chainsawman.safety;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** What Public Safety tells a client (its licence, a payday) and what the client asks (the licence, by its key). */
public final class SafetyPayloads {
	private SafetyPayloads() {
	}

	/** One player's licence: yen, devils hunted (entity type id to count), the contracts held (ids); {@code open}: show the card now. */
	public record Licence(long yen, Map<String, Integer> hunted, List<String> contracts, boolean open) implements CustomPayload {
		public static final CustomPayload.Id<Licence> ID = new CustomPayload.Id<>(ChainsawMan.id("licence"));
		public static final PacketCodec<RegistryByteBuf, Licence> CODEC = PacketCodec.of((p, buf) -> {
			buf.writeVarLong(p.yen);
			buf.writeVarInt(p.hunted.size());
			for (Map.Entry<String, Integer> e : p.hunted.entrySet()) {
				buf.writeString(e.getKey());
				buf.writeVarInt(e.getValue());
			}
			buf.writeVarInt(p.contracts.size());
			for (String c : p.contracts) {
				buf.writeString(c);
			}
			buf.writeBoolean(p.open);
		}, buf -> {
			long yen = buf.readVarLong();
			Map<String, Integer> hunted = new LinkedHashMap<>();
			for (int i = buf.readVarInt(); i > 0; i--) {
				hunted.put(buf.readString(), buf.readVarInt());
			}
			List<String> contracts = new ArrayList<>();
			for (int i = buf.readVarInt(); i > 0; i--) {
				contracts.add(buf.readString());
			}
			return new Licence(yen, hunted, contracts, buf.readBoolean());
		});

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** One line of a payday: an item's translation key, how many, and what they fetched. */
	public record Line(String key, int count, long yen) {
	}

	/** A payday at the phone: the yen before and after, what was sold, and the first report's bonus (0 if not this time). */
	public record Payday(long from, long to, List<Line> lines, long bonus) implements CustomPayload {
		public static final CustomPayload.Id<Payday> ID = new CustomPayload.Id<>(ChainsawMan.id("payday"));
		public static final PacketCodec<RegistryByteBuf, Payday> CODEC = PacketCodec.of((p, buf) -> {
			buf.writeVarLong(p.from);
			buf.writeVarLong(p.to);
			buf.writeVarInt(p.lines.size());
			for (Line l : p.lines) {
				buf.writeString(l.key());
				buf.writeVarInt(l.count());
				buf.writeVarLong(l.yen());
			}
			buf.writeVarLong(p.bonus);
		}, buf -> {
			long from = buf.readVarLong();
			long to = buf.readVarLong();
			List<Line> lines = new ArrayList<>();
			for (int i = buf.readVarInt(); i > 0; i--) {
				lines.add(new Line(buf.readString(), buf.readVarInt(), buf.readVarLong()));
			}
			return new Payday(from, to, lines, buf.readVarLong());
		});

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** The licence key: show me my card. */
	public record Ask() implements CustomPayload {
		public static final CustomPayload.Id<Ask> ID = new CustomPayload.Id<>(ChainsawMan.id("licence_ask"));
		public static final PacketCodec<RegistryByteBuf, Ask> CODEC = PacketCodec.unit(new Ask());

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
