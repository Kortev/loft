package io.github.kortev.gojolander.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;

/**
 * The Six Eyes on the client: how far this player sees through walls (the server says), and whether a given entity is
 * seen so: anything living but an armour stand, within that far, drawn outlined through what stands between, in the
 * cold blue of his eyes ({@code SixEyesMixin}, {@code SixEyesColorMixin}).
 */
public final class SixEyesClient {
	/** The blue they see in. */
	public static final int BLUE = 0x7FD8FF;
	private static int range;

	private SixEyesClient() {
	}

	public static void set(int blocks) {
		range = blocks;
	}

	public static void clear() {
		range = 0;
	}

	/** Whether this player sees {@code entity} through walls with the Six Eyes now. */
	public static boolean sees(Entity entity) {
		if (range <= 0 || !(entity instanceof LivingEntity) || entity instanceof ArmorStandEntity) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player != null && entity != client.player && entity.squaredDistanceTo(client.player) < (double) range * range;
	}
}
