package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Vector3f;

/**
 * The ult: blood frenzy. In the form, called up with its key: for {@link #LASTS} ticks you go berserk, the saws doing
 * {@link #DAMAGE} times their damage and every hit healing {@link #HEAL} times as much, and nothing slows you (Slowness
 * comes off, cobwebs and berry bushes don't hold you); the screen goes red and the engine screams. No cutscene: just a
 * roar and a red pulse as it kicks in. Then {@link #EVERY} ticks before it can come again.
 */
public final class Frenzy {
	public static final int LASTS = 15 * 20;
	public static final int EVERY = 5 * 60 * 20;
	public static final float DAMAGE = 2.0F;
	public static final float HEAL = 4.0F;

	/** When each player's frenzy ends (world time), and when they last called one up. */
	private static final Map<UUID, Long> UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> CALLED = new ConcurrentHashMap<>();
	private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(0.9F, 0.05F, 0.05F), 1.6F);

	/** Who is in a frenzy as far as each client has been told; the server's own on the server. */
	public static Predicate<PlayerEntity> client = p -> false;

	private Frenzy() {
	}

	/** Whether {@code player} is in a frenzy: on either side. */
	public static boolean is(PlayerEntity player) {
		return player.getWorld().isClient ? client.test(player) : UNTIL.containsKey(player.getUuid());
	}

	/** How many times its damage the saws do for {@code player} now. */
	public static float damage(PlayerEntity player) {
		return is(player) ? DAMAGE : 1.0F;
	}

	/** How many times as much a hit heals {@code player} now. */
	public static float heal(PlayerEntity player) {
		return is(player) ? HEAL : 1.0F;
	}

	/** The frenzy called up by {@code player}: whether it came. */
	public static boolean start(ServerPlayerEntity player) {
		if (!Form.fighting(player) || is(player)) {
			return false;
		}
		long now = player.getServerWorld().getTime();
		Long last = CALLED.get(player.getUuid());
		if (last != null && now - last < EVERY) {
			long left = EVERY - (now - last);
			player.sendMessage(Text.translatable("message.chainsawman.frenzy_wait", left / 1200, String.format("%02d", left / 20 % 60))
					.formatted(Formatting.GRAY), true);
			return false;
		}
		CALLED.put(player.getUuid(), now);
		UNTIL.put(player.getUuid(), now + LASTS);
		player.removeStatusEffect(StatusEffects.SLOWNESS);
		Form.play(player, ChainsawSounds.FRENZY, 1.6F);
		Saw.around(player, RED, 1.2, 60, 0.6, 0.2);
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.FRENZY, LASTS));
		return true;
	}

	/** The frenzy over for {@code player}, if they were in one. */
	public static void end(ServerPlayerEntity player) {
		if (UNTIL.remove(player.getUuid()) != null) {
			Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.FRENZY, 0));
		}
	}

	static void tick(MinecraftServer server) {
		long now = server.getOverworld().getTime();
		for (Map.Entry<UUID, Long> e : Map.copyOf(UNTIL).entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
			if (player == null) {
				UNTIL.remove(e.getKey());
			} else if (now >= e.getValue() || !Form.fighting(player)) {
				end(player);
			} else {
				player.removeStatusEffect(StatusEffects.SLOWNESS);
			}
		}
		for (Map.Entry<UUID, Long> e : Map.copyOf(CALLED).entrySet()) {
			if (now - e.getValue() == EVERY) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
				if (player != null) {
					player.sendMessage(Text.translatable("message.chainsawman.frenzy_ready").formatted(Formatting.RED), true);
				}
			}
		}
	}

	/** Lets {@code player} call it up again at once (for the tests). */
	public static void ready(PlayerEntity player) {
		CALLED.remove(player.getUuid());
	}

	static void forget(UUID id) {
		UNTIL.remove(id);
	}

	static void clear() {
		UNTIL.clear();
		CALLED.clear();
	}
}
