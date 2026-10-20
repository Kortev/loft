package io.github.kortev.chainsawman.form;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Blood, the form's fuel: a bar from 0 to {@link #MAX}, kept with the player. The saws fill it as they cut (a slash or
 * lunge that hits, a grinding bite, a kill most of all), and out of the form your body slowly makes more. Pulling the
 * cord costs {@link #TRANSFORM} (the engine won't start without it), the chain {@link #CHAIN}, the lunge {@link
 * #LUNGE}, the frenzy {@link #FRENZY}: blood in place of plain cooldowns.
 */
public final class Blood {
	public static final int MAX = 100;
	public static final int TRANSFORM = 25;
	public static final int CHAIN = 15;
	public static final int LUNGE = 10;
	public static final int FRENZY = 60;
	/** What cutting gives: a hit landing, a grinding bite, a kill. */
	public static final int HIT = 8;
	public static final int BITE = 3;
	public static final int KILL = 25;
	/** Out of the form, a point every so many ticks. */
	public static final int MAKES_EVERY = 40;

	public static final AttachmentType<Integer> LEVEL = AttachmentRegistry.<Integer>builder().persistent(Codec.INT)
			.buildAndRegister(ChainsawMan.id("blood"));

	/** What each player's client was last told, so it is told only of changes. */
	private static final Map<UUID, Integer> TOLD = new ConcurrentHashMap<>();

	private Blood() {
	}

	public static int get(ServerPlayerEntity player) {
		return player.getAttachedOrElse(LEVEL, MAX);
	}

	public static void set(ServerPlayerEntity player, int blood) {
		player.setAttached(LEVEL, Math.max(0, Math.min(MAX, blood)));
	}

	public static void add(ServerPlayerEntity player, int blood) {
		set(player, get(player) + blood);
	}

	/** Spends {@code blood} if {@code player} has it: whether they did. If not, they're told so. */
	public static boolean spend(ServerPlayerEntity player, int blood) {
		int has = get(player);
		if (has < blood) {
			player.sendMessage(Text.translatable("message.chainsawman.no_blood", blood, has).formatted(Formatting.DARK_RED), true);
			return false;
		}
		set(player, has - blood);
		return true;
	}

	static void tick(MinecraftServer server) {
		long now = server.getOverworld().getTime();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (now % MAKES_EVERY == 0 && !Form.on(player) && !Downed.is(player) && Heart.has(player)) {
				add(player, 1);
			}
			int blood = get(player);
			Integer told = TOLD.get(player.getUuid());
			if ((told == null || told != blood) && ServerPlayNetworking.canSend(player, FormEventPayload.ID)) {
				ServerPlayNetworking.send(player, new FormEventPayload(player.getUuid(), FormEventPayload.BLOOD, blood));
				TOLD.put(player.getUuid(), blood);
			}
		}
	}

	static void forget(UUID id) {
		TOLD.remove(id);
	}

	static void clear() {
		TOLD.clear();
	}
}
