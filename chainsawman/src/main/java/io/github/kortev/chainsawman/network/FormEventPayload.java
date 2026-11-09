package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

/**
 * What a player in the form has done, told to everyone so they see it: a slash ({@code value} the step of the combo),
 * grinding on or off ({@code value} 1 or 0), the chain fired ({@code value} the entity it caught, -1 a block, -2
 * nothing; {@code x y z} where it went; held until CHAIN_END, unless it caught nothing), downed or back up ({@code value}
 * 1 or 0), the frenzy ({@code value} its ticks left, 0 when over), a hit landing ({@code value} how many it hit), a kill
 * by the saws ({@code value} the entity torn apart), your blood, arms lost.
 */
public record FormEventPayload(UUID player, byte kind, int value, double x, double y, double z) implements CustomPayload {
	public static final byte SWING = 0;
	public static final byte GRIND = 1;
	public static final byte CHAIN = 2;
	public static final byte DOWNED = 3;
	public static final byte FRENZY = 4;
	public static final byte HIT = 5;
	public static final byte CHAIN_END = 6;
	public static final byte KILL = 7;
	/** Your own blood (to you alone), 0 to 100. */
	public static final byte BLOOD = 8;
	/** Which arms are gone ({@code value} bits: 1 the right, 2 the left). */
	public static final byte LIMBS = 9;
	/** Pochita becoming your heart ({@code value} 1 as it starts, {@code x y z} where he comes from; 0 when it's done). */
	public static final byte MERGE = 10;
	/** Who has Pochita's heart ({@code value} 1): the cord shows on their chest. */
	public static final byte HEART = 11;
	/** Turning back: the saws going in, the form off at {@code FormTimeline.BACK_SPLASH}. */
	public static final byte TURN = 12;
	/** Faint from blood loss ({@code value} 1), or over it (0). */
	public static final byte FAINT = 13;
	/** A saw biting into a player (or a finisher into anything): {@code value} what was hit (its id), {@code x y z} where. */
	public static final byte IMPACT = 14;
	/**
	 * One of Beam's hits landing, to his rider alone: {@code value} how hard (1 to 4) with {@code BEAM_FLASH} added when it is
	 * one of the big ones that gets the anime's impact frames and its lettering, {@code x y z} where.
	 */
	public static final byte BEAM_HIT = 15;
	public static final int BEAM_FLASH = 8;
	public static final int BEAM_CHOP = 16;
	/**
	 * A friend pulling the cord of {@code player}, who is down: {@code value} the puller's entity id as they take hold, 0 when
	 * they let go (the cord slides back in), -1 when they yank it (the cord snaps back, the limbs grow, the form comes).
	 */
	public static final byte REVIVE = 16;
	/** A human gone down ({@code Fallen}): {@code value} ticks they have left (0: up, or dead), {@code x y z} where they lie. */
	public static final byte FALLEN = 17;
	/**
	 * A friend lifting a fallen human, or tearing a hybrid's heart out ({@code Hearts}): {@code player} the one on the ground, {@code value}
	 * the helper's entity id as they take hold, 0 when they let go, -1 when it is done; {@code x} how many ticks it takes, {@code y} 0 for a
	 * pick-up, 1 for the heart.
	 */
	public static final byte LIFT = 18;
	/** Your buddy ({@code player} their id, {@code value} 1), or none (0): to you alone. */
	public static final byte BUDDY = 19;
	/** Your buddy is down, to you alone: {@code x y z} where they lie, {@code value} the ticks they have left (-1 if they are a hybrid). Resent while they are. */
	public static final byte BUDDY_DOWN = 20;
	/** The legend ({@code player}, Chainsaw Man) pulled the cord at {@code x y z}: a marker for {@code value} ticks. */
	public static final byte LEGEND = 21;
	/** The heart's level of {@code player} ({@code value} 0 for no heart, else 1 to 3): everyone, the saws' teeth and the engine follow it. */
	public static final byte HEART_LEVEL = 22;
	/** Your heart's XP, to you alone. */
	public static final byte HEART_XP = 23;
	/** {@code player}'s heart just reached level {@code value} at {@code x y z}: the roar, the blood, the line on their screen. */
	public static final byte HEART_LEVEL_UP = 24;

	public static final CustomPayload.Id<FormEventPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("form_event"));
	public static final PacketCodec<RegistryByteBuf, FormEventPayload> CODEC = PacketCodec.tuple(Uuids.PACKET_CODEC, FormEventPayload::player,
			PacketCodecs.BYTE, FormEventPayload::kind, PacketCodecs.VAR_INT, FormEventPayload::value, PacketCodecs.DOUBLE, FormEventPayload::x,
			PacketCodecs.DOUBLE, FormEventPayload::y, PacketCodecs.DOUBLE, FormEventPayload::z, FormEventPayload::new);

	public FormEventPayload(UUID player, byte kind, int value) {
		this(player, kind, value, 0.0, 0.0, 0.0);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
