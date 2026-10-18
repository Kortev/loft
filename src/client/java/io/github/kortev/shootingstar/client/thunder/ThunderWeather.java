package io.github.kortev.shootingstar.client.thunder;

import io.github.kortev.shootingstar.thunder.ThunderTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * The weather under Mjölnir's storm, as this client sees it: the nearer the camera is to a gathering storm, the more of
 * vanilla's rain and thunder it lends the client's world (never the server's), so the sky turns slate, the fog closes
 * in, the daylight goes and rain falls (where the biome has any), all with vanilla's own drawing. Lightning in the storm
 * flashes the whole sky and the world's light the way vanilla's lightning does. Vanilla's clouds are hidden while a
 * storm is up: they cut through it, and through the stroke's impact frames.
 */
public final class ThunderWeather {
	/** How far past the storm's edge its weather still reaches, fading out. */
	private static final double REACH = 160.0;

	private ThunderWeather() {
	}

	/** How much storm weather the camera is under (0..1): rain, and as much again of thunder. */
	public static float storm(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.gameRenderer == null || ClientThunders.all().isEmpty()) {
			return 0.0F;
		}
		Vec3d cam = client.gameRenderer.getCamera().getPos();
		float storm = 0.0F;
		for (ClientThunder thunder : ClientThunders.all()) {
			double t = thunder.time(tickDelta);
			double density = ThunderRender.stormDensity(t);
			if (density < 0.01) {
				continue;
			}
			double edge = deck(t, thunder.radius);
			double d = Math.hypot(cam.x - thunder.center.x, cam.z - thunder.center.z);
			double under = 1.0 - ThunderTimeline.smooth((d - edge) / REACH);
			storm = Math.max(storm, (float) (density * under));
		}
		return storm;
	}

	/** The radius of the storm's widest deck at {@code t}: its outer cloud reaches this far from the target. */
	public static double deck(double t, int radius) {
		return ThunderTimeline.vortexRadius(t, radius) * 1.6;
	}

	/** True while any storm is up near enough to see, when vanilla's clouds are hidden. */
	public static boolean cloudless() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.gameRenderer == null) {
			return false;
		}
		Vec3d cam = client.gameRenderer.getCamera().getPos();
		for (ClientThunder thunder : ClientThunders.all()) {
			if (thunder.age >= ThunderTimeline.CALL && thunder.age < ThunderTimeline.END
					&& Math.hypot(cam.x - thunder.center.x, cam.z - thunder.center.z) < 1200.0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Once a tick: flashes the sky and the world's light for lightning in a storm the camera is under (the brighter and
	 * nearer, the more often), and for the call and the stroke with its restrikes, which anyone in sight of sees.
	 */
	public static void tick(MinecraftClient client) {
		if (client.world == null || client.gameRenderer == null) {
			return;
		}
		Vec3d cam = client.gameRenderer.getCamera().getPos();
		for (ClientThunder thunder : ClientThunders.all()) {
			int age = thunder.age;
			double d = Math.hypot(cam.x - thunder.center.x, cam.z - thunder.center.z);
			boolean sight = d < 1200.0;
			int ticks = 0;
			if (sight && age == ThunderTimeline.CALL) {
				ticks = 2;
			}
			if (sight && thunder.struck) {
				int e = age - ThunderTimeline.STROKE;
				if (e == 0) {
					ticks = 4;
				}
				for (int restrike : ThunderTimeline.RESTRIKES) {
					if (age == restrike) {
						ticks = 2;
					}
				}
			}
			// Lightning inside the storm, seen from under it: the moment a flash starts.
			double under = 1.0 - ThunderTimeline.smooth((d - deck(age, thunder.radius)) / REACH);
			float[] now = ThunderRender.stormFlash(thunder, age);
			float[] before = ThunderRender.stormFlash(thunder, age - 1);
			if (under > 0.3 && now[2] >= 0.99F && before[2] < 0.99F && ThunderRender.stormDensity(age) > 0.3) {
				ticks = Math.max(ticks, 1);
			}
			if (ticks > 0 && client.world.getLightningTicksLeft() < ticks) {
				client.world.setLightningTicksLeft(ticks);
			}
		}
	}
}
