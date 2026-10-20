package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import org.joml.Vector3f;

/**
 * Losing arms in the fight: in the form, every {@link #EVERY} health you lose takes an arm off (the right, then the left),
 * its saw with it. With one arm left the saws do three quarters of their damage, with none half (the head's saw alone).
 * Blood grows them back, as Denji's do: every {@link #REGROW} blood you drink by cutting (hits, bites, kills) brings one
 * back. Out of the form, or back up from being down, you have both again.
 */
public final class Limbs {
	public static final float EVERY = 12.0F;
	/** How much blood drunk by cutting grows an arm back. */
	public static final int REGROW = 25;
	private static final Map<UUID, Integer> DRUNK = new ConcurrentHashMap<>();
	/** Which arms are gone: bits, {@link #RIGHT} and {@link #LEFT}. */
	public static final int RIGHT = 1;
	public static final int LEFT = 2;

	private static final Map<UUID, Integer> LOST = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> TAKEN = new ConcurrentHashMap<>();
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 1.5F);

	/** Which arms each client has been told are gone; the server's own on the server. */
	public static Predicate<PlayerEntity> clientRight = p -> false;
	public static Predicate<PlayerEntity> clientLeft = p -> false;

	private Limbs() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayerEntity player && Form.fighting(player) && taken > 0.0F) {
				float total = TAKEN.getOrDefault(player.getUuid(), 0.0F) + taken;
				while (total >= EVERY && lost(player) != (RIGHT | LEFT)) {
					total -= EVERY;
					lose(player);
				}
				TAKEN.put(player.getUuid(), total);
			}
		});
	}

	/** Which arms {@code player} has lost (bits). */
	public static int lost(ServerPlayerEntity player) {
		return LOST.getOrDefault(player.getUuid(), 0);
	}

	public static boolean has(PlayerEntity player, int arm) {
		if (player.getWorld().isClient) {
			return !(arm == RIGHT ? clientRight : clientLeft).test(player);
		}
		return (LOST.getOrDefault(player.getUuid(), 0) & arm) == 0;
	}

	/** How much of their damage the saws do with the arms {@code player} has left. */
	public static float damage(ServerPlayerEntity player) {
		int arms = (has(player, RIGHT) ? 1 : 0) + (has(player, LEFT) ? 1 : 0);
		return arms == 2 ? 1.0F : arms == 1 ? 0.75F : 0.5F;
	}

	/** An arm off: the right if it's still there, else the left. */
	public static void lose(ServerPlayerEntity player) {
		int lost = lost(player);
		int arm = (lost & RIGHT) == 0 ? RIGHT : LEFT;
		set(player, lost | arm);
		Form.play(player, ChainsawSounds.HIT, 1.3F);
		player.getServerWorld().spawnParticles(BLOOD, player.getX(), player.getY() + 1.3, player.getZ(), 40, 0.4, 0.3, 0.4, 0.15);
	}

	/** Blood drunk by cutting: with an arm gone, every {@link #REGROW} of it grows one back. */
	public static void drank(ServerPlayerEntity player, int blood) {
		if (lost(player) == 0) {
			DRUNK.remove(player.getUuid());
			return;
		}
		int drunk = DRUNK.getOrDefault(player.getUuid(), 0) + blood;
		while (drunk >= REGROW && lost(player) != 0) {
			drunk -= REGROW;
			regrow(player);
		}
		DRUNK.put(player.getUuid(), drunk);
	}

	/** An arm back, grown out of blood: the left first (the last lost). */
	public static void regrow(ServerPlayerEntity player) {
		int lost = lost(player);
		if (lost != 0) {
			set(player, (lost & LEFT) != 0 ? lost & ~LEFT : lost & ~RIGHT);
			Form.play(player, ChainsawSounds.REVIVE, 0.6F);
			player.getServerWorld().spawnParticles(BLOOD, player.getX(), player.getY() + 1.3, player.getZ(), 20, 0.35, 0.25, 0.35, 0.05);
		}
	}

	/** Both arms back, and the count of damage started over. */
	public static void whole(ServerPlayerEntity player) {
		TAKEN.remove(player.getUuid());
		DRUNK.remove(player.getUuid());
		if (lost(player) != 0) {
			set(player, 0);
		}
	}

	private static void set(ServerPlayerEntity player, int lost) {
		if (lost == 0) {
			LOST.remove(player.getUuid());
		} else {
			LOST.put(player.getUuid(), lost);
		}
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.LIMBS, lost));
	}

	static void forget(UUID id) {
		LOST.remove(id);
		TAKEN.remove(id);
	}

	static void clear() {
		LOST.clear();
		TAKEN.clear();
	}
}
