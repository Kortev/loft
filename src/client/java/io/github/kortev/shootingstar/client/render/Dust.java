package io.github.kortev.shootingstar.client.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * What a strike leaves in the air: for a few minutes after the hit the light round the crater takes on a warm,
 * dusty tint, the far hills go hazy, and a little ash drifts down. Drawn through the world's fog
 * (see {@code BackgroundRendererMixin}) and vanilla ash particles.
 */
public final class Dust {
	private static final int RISE = 220;
	private static final int HOLD = 1800;
	private static final int GONE = 4800;
	/** The dust colour at full daylight. */
	public static final float RED = 0.47F;
	public static final float GREEN = 0.34F;
	public static final float BLUE = 0.23F;

	private static final class Cloud {
		final Vec3d center;
		final int radius;
		int age;

		Cloud(Vec3d center, int radius) {
			this.center = center;
			this.radius = radius;
		}
	}

	private static final List<Cloud> CLOUDS = new ArrayList<>();
	private static final Random RANDOM = Random.create();

	private Dust() {
	}

	public static void add(Vec3d center, int radius) {
		CLOUDS.add(new Cloud(center, radius));
	}

	public static void clear() {
		CLOUDS.clear();
	}

	/** How thick the dust is where the camera is, 0..1. */
	public static float amount(Vec3d cam) {
		float best = 0.0F;
		for (Cloud c : CLOUDS) {
			float time = smooth((c.age - 20) / (float) RISE) * (1.0F - smooth((c.age - HOLD) / (float) (GONE - HOLD)));
			double d = Math.sqrt((cam.x - c.center.x) * (cam.x - c.center.x) + (cam.z - c.center.z) * (cam.z - c.center.z));
			double near = c.radius * 2.0 + 100.0;
			double far = c.radius * 7.0 + 450.0;
			float place = 1.0F - smooth((float) ((d - near) / (far - near)));
			best = Math.max(best, time * place);
		}
		return best;
	}

	public static void tick(MinecraftClient client) {
		for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext(); ) {
			if (++it.next().age > GONE) {
				it.remove();
			}
		}
		if (client.world == null || client.player == null || client.isPaused()) {
			return;
		}
		Vec3d cam = client.gameRenderer.getCamera().getPos();
		float dust = amount(cam);
		// Ash drifting down round the camera.
		int flakes = (int) (dust * 8.0F) + (RANDOM.nextFloat() < dust * 8.0F % 1.0F ? 1 : 0);
		for (int i = 0; i < flakes; i++) {
			double x = cam.x + (RANDOM.nextDouble() - 0.5) * 40.0;
			double z = cam.z + (RANDOM.nextDouble() - 0.5) * 40.0;
			double y = cam.y + RANDOM.nextDouble() * 18.0 - 4.0;
			client.world.addParticle(RANDOM.nextFloat() < 0.8F ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH, x, y, z, 0, 0, 0);
		}
	}

	private static float smooth(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
