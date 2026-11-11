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

	/**
	 * To a player in the loop: the fog the corridor hides its ends in, as a distance in blocks (0 off), and the hotel's place and turn (so the
	 * client can draw what is in the corridor where the corridor repeats it, and keep what the loop moves from sliding across).
	 */
	public record Fog(float end, net.minecraft.util.math.BlockPos origin, int rot) implements CustomPayload {
		public static final CustomPayload.Id<Fog> ID = new CustomPayload.Id<>(ChainsawMan.id("hotel_fog"));
		public static final PacketCodec<RegistryByteBuf, Fog> CODEC = PacketCodec.tuple(PacketCodecs.FLOAT, Fog::end,
				net.minecraft.util.math.BlockPos.PACKET_CODEC, Fog::origin, PacketCodecs.VAR_INT, Fog::rot, Fog::new);

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

	/**
	 * The contract screen: to a player inside the hotel, with how many ticks they have to answer (0 closes it). {@code hybrid}: the player has
	 * Pochita's heart, which leaves no room for another devil, so Accept is greyed out.
	 */
	public record Offer(int ticks, boolean hybrid) implements CustomPayload {
		public static final CustomPayload.Id<Offer> ID = new CustomPayload.Id<>(ChainsawMan.id("contract_offer"));
		public static final PacketCodec<RegistryByteBuf, Offer> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, Offer::ticks, PacketCodecs.BOOL, Offer::hybrid, Offer::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Slots full: which contract does the player give up for the new one? The ids held (a screen of buttons), and how many ticks they have. */
	public record Drop(int ticks, java.util.List<String> held) implements CustomPayload {
		public static final CustomPayload.Id<Drop> ID = new CustomPayload.Id<>(ChainsawMan.id("contract_drop"));
		public static final PacketCodec<RegistryByteBuf, Drop> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, Drop::ticks,
				PacketCodecs.STRING.collect(PacketCodecs.toList()), Drop::held, Drop::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** The contract given up (its slot), or -1 for none (the new one is not taken). */
	public record DropAnswer(int slot) implements CustomPayload {
		public static final CustomPayload.Id<DropAnswer> ID = new CustomPayload.Id<>(ChainsawMan.id("contract_drop_answer"));
		public static final PacketCodec<RegistryByteBuf, DropAnswer> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, DropAnswer::slot, DropAnswer::new);

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
		public static final byte BEG = 6;
		/** To one player: the floor turns (ticks: how long), the shaft is there (ticks 1; 0: it did not come). */
		public static final byte TILT = 7;
		public static final byte SHAFT = 8;
		/** In the arena: a cut's lit wound at the point (ticks: the face it is on, the {@code Direction} id, plus 8 when the flesh was soft). */
		public static final byte WOUND = 10;
		/** In the arena: half the room squeezes (ticks: which half, 0 the low-x end, 1 the high-x end); it bulges for {@code Shaft.SQUEEZE_TELL} first. */
		public static final byte SQUEEZE = 11;
		public static final CustomPayload.Id<Devil> ID = new CustomPayload.Id<>(ChainsawMan.id("devil_event"));
		public static final PacketCodec<RegistryByteBuf, Devil> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, Devil::devil, PacketCodecs.BYTE, Devil::kind,
				PacketCodecs.FLOAT, Devil::x, PacketCodecs.FLOAT, Devil::y, PacketCodecs.FLOAT, Devil::z, PacketCodecs.VAR_INT, Devil::ticks, Devil::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
