package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** What the Eternity Devil's hotel tells the clients (and what they tell it back). */
public final class HotelPayloads {
	private HotelPayloads() {
	}

	/** To a player in the loop: the fog the corridor hides its ends in, as a distance in blocks (0 off). */
	public record Fog(float end) implements CustomPayload {
		public static final CustomPayload.Id<Fog> ID = new CustomPayload.Id<>(ChainsawMan.id("hotel_fog"));
		public static final PacketCodec<RegistryByteBuf, Fog> CODEC = PacketCodec.tuple(PacketCodecs.FLOAT, Fog::end, Fog::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** The devil speaks, in its crowd voice, words on screen: a translation key and how many ticks they stay ({@code style} 0 plain, 1 begging, 2 the offer's last words). */
	public record Speech(String key, int ticks, int style) implements CustomPayload {
		public static final CustomPayload.Id<Speech> ID = new CustomPayload.Id<>(ChainsawMan.id("devil_speech"));
		public static final PacketCodec<RegistryByteBuf, Speech> CODEC = PacketCodec.tuple(PacketCodecs.STRING, Speech::key,
				PacketCodecs.VAR_INT, Speech::ticks, PacketCodecs.VAR_INT, Speech::style, Speech::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** The contract screen: to a player in the loop, with how many ticks they have to answer (0 closes it). */
	public record Offer(int ticks) implements CustomPayload {
		public static final CustomPayload.Id<Offer> ID = new CustomPayload.Id<>(ChainsawMan.id("contract_offer"));
		public static final PacketCodec<RegistryByteBuf, Offer> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, Offer::ticks, Offer::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** The answer to the offer. */
	public record Answer(boolean accept) implements CustomPayload {
		public static final CustomPayload.Id<Answer> ID = new CustomPayload.Id<>(ChainsawMan.id("contract_answer"));
		public static final PacketCodec<RegistryByteBuf, Answer> CODEC = PacketCodec.tuple(PacketCodecs.BOOL, Answer::accept, Answer::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/**
	 * What the devil's body is doing, for those who see it: {@code kind} bite (a mouth rising at the point), tendril (a flesh arm slamming
	 * down the corridor to the point), creep (flesh spreading to the point), the heart showing or beating, the devil's death.
	 */
	public record Devil(int devil, byte kind, float x, float y, float z, int ticks) implements CustomPayload {
		public static final byte BITE = 0;
		public static final byte TENDRIL = 1;
		public static final byte CREEP = 2;
		public static final byte HEART = 3;
		public static final byte REGEN = 4;
		public static final byte DYING = 5;
		public static final CustomPayload.Id<Devil> ID = new CustomPayload.Id<>(ChainsawMan.id("devil_event"));
		public static final PacketCodec<RegistryByteBuf, Devil> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, Devil::devil, PacketCodecs.BYTE, Devil::kind,
				PacketCodecs.FLOAT, Devil::x, PacketCodecs.FLOAT, Devil::y, PacketCodecs.FLOAT, Devil::z, PacketCodecs.VAR_INT, Devil::ticks, Devil::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
