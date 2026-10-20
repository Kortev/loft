package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.form.FormTimeline;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Who is in the form, as the server has told us; who is going through the transformation and since when; and the
 * engine idling on each of them.
 */
public final class ClientForms {
	private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();
	/** Who pulled the cord: the world time they pulled it at and which way they faced. */
	private static final Map<UUID, Pull> PULLED = new ConcurrentHashMap<>();
	private static final Map<UUID, FormIdleSound> IDLING = new ConcurrentHashMap<>();
	/** Who is turning back: the world time it started. Who has had their splash. */
	private static final Map<UUID, Long> TURNED = new ConcurrentHashMap<>();
	private static final Set<UUID> SPLASHED = ConcurrentHashMap.newKeySet();
	private static final net.minecraft.particle.BlockStateParticleEffect GORE = new net.minecraft.particle.BlockStateParticleEffect(
			net.minecraft.particle.ParticleTypes.BLOCK, net.minecraft.block.Blocks.REDSTONE_BLOCK.getDefaultState());
	private static final net.minecraft.particle.DustParticleEffect BLOOD = new net.minecraft.particle.DustParticleEffect(
			new org.joml.Vector3f(0.62F, 0.02F, 0.04F), 1.3F);

	/** A pull of the cord: when, and which way its puller faced (the camera's shots are set round that). */
	public record Pull(long at, float yaw) {
	}

	private ClientForms() {
	}

	public static boolean on(PlayerEntity player) {
		return ON.contains(player.getUuid());
	}

	/** Puts {@code player} in or out of the form here (as the server says; or a test's stand-in, which the server never sees). */
	public static void set(UUID player, boolean on) {
		if (on) {
			ON.add(player);
		} else {
			ON.remove(player);
		}
	}

	/** {@code player} pulled the cord at {@code worldTime} facing {@code yaw} (as the server says; or a test's stand-in). */
	public static void pulled(UUID player, long worldTime, float yaw) {
		PULLED.put(player, new Pull(worldTime, yaw));
	}

	/** {@code player} began turning back at {@code worldTime} (as the server says). */
	public static void turned(UUID player, long worldTime) {
		TURNED.put(player, worldTime);
		SPLASHED.remove(player);
	}

	/** How many ticks into turning back {@code player} is, while it plays out; otherwise -1. */
	public static float turnAge(PlayerEntity player, float tickDelta) {
		Long at = TURNED.get(player.getUuid());
		if (at == null) {
			return -1.0F;
		}
		float age = player.getWorld().getTime() - at + tickDelta;
		return age >= 0.0F && age <= FormTimeline.BACK_END ? age : -1.0F;
	}

	@Nullable
	public static Pull pull(PlayerEntity player) {
		return PULLED.get(player.getUuid());
	}

	/** How many ticks ago {@code player} pulled the cord, while the transformation is playing out; otherwise -1. */
	public static float pullAge(PlayerEntity player, float tickDelta) {
		Pull pull = PULLED.get(player.getUuid());
		if (pull == null) {
			return -1.0F;
		}
		float age = player.getWorld().getTime() - pull.at() + tickDelta;
		return age >= 0.0F && age <= FormTimeline.END ? age : -1.0F;
	}

	/**
	 * How far {@code player}'s saws have grown out (0 to 1): the head's, or the arms'. Whoever is in the form and not
	 * transforming has them out in full.
	 */
	public static float grown(PlayerEntity player, float tickDelta, boolean arms) {
		float back = turnAge(player, tickDelta);
		if (back >= 0.0F) {
			// Going back in: slow to start, then sucked in.
			float from = arms ? FormTimeline.BACK_ARMS : FormTimeline.BACK_HEAD;
			float to = arms ? FormTimeline.BACK_ARMS_IN : FormTimeline.BACK_HEAD_IN;
			float k = Math.max(0.0F, Math.min(1.0F, (back - from) / (to - from)));
			return 1.0F - k * k;
		}
		float t = pullAge(player, tickDelta);
		if (t < 0.0F) {
			return 1.0F;
		}
		float from = arms ? FormTimeline.ARMS : FormTimeline.BURST;
		float to = arms ? FormTimeline.ARMS_GROWN : FormTimeline.HEAD_GROWN;
		float k = Math.max(0.0F, Math.min(1.0F, (t - from) / (to - from)));
		// Out fast, then easing into place.
		return 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
	}

	/** Every tick: the engine idling on whoever is in the form here, and not on anyone else; old pulls forgotten. */
	static void tick(MinecraftClient client) {
		if (client.world == null) {
			return;
		}
		long now = client.world.getTime();
		PULLED.values().removeIf(pull -> now - pull.at() > FormTimeline.END + 20);
		TURNED.values().removeIf(at -> now - at > FormTimeline.BACK_END + 20);
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (turnAge(player, 0.0F) >= FormTimeline.BACK_SPLASH && SPLASHED.add(player.getUuid())) {
				// The chainsaw head comes away in a splash of blood: from all round the head (not inside it, where none
				// of it would show), gore flying.
				net.minecraft.util.math.Vec3d head = player.getEyePos().add(0.0, 0.1, 0.0);
				for (int i = 0; i < 40; i++) {
					net.minecraft.util.math.Vec3d out = new net.minecraft.util.math.Vec3d(Math.random() - 0.5, Math.random() * 0.8 - 0.2,
							Math.random() - 0.5).normalize();
					net.minecraft.util.math.Vec3d at = head.add(out.multiply(0.4));
					if (i % 2 == 0) {
						client.world.addParticle(GORE, at.x, at.y, at.z, out.x * 0.25, 0.15 + out.y * 0.2, out.z * 0.25);
					} else {
						client.world.addParticle(BLOOD, at.x, at.y, at.z, out.x, out.y, out.z);
					}
				}
			}
			// Idling once the transformation (whose own sound runs into the idle) is over.
			if (ON.contains(player.getUuid()) && !IDLING.containsKey(player.getUuid()) && pullAge(player, 0.0F) < 0.0F) {
				FormIdleSound sound = new FormIdleSound(player);
				IDLING.put(player.getUuid(), sound);
				client.getSoundManager().play(sound);
			}
		}
		IDLING.values().removeIf(FormIdleSound::isDone);
	}

	static void clear() {
		ClientCombat.clear();
		ON.clear();
		PULLED.clear();
		IDLING.clear();
		TURNED.clear();
		SPLASHED.clear();
	}
}
