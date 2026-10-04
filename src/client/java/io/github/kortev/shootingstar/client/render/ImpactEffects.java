package io.github.kortev.shootingstar.client.render;

import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.registry.ModSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * The impact's sound (the boom reaches you at the speed of sound), the embers that keep spitting out
 * of the bowl afterwards, and keeping the carved terrain fully drawn. The blast itself is drawn by
 * {@code WorldFx}.
 */
public final class ImpactEffects {
	/** Speed of sound in blocks per tick, give or take. */
	private static final double SOUND_SPEED = 17.0;
	private static final double PARTICLE_RANGE = 480.0;
	private static final int EMBER_TICKS = 700;
	/** How long after the hit the terrain around the crater is kept fully rebuilt. */
	private static final int REBUILD_TICKS = 360;
	/** The crater is carved up to this far above the impact (ImpactBuilder.MAX_CUT). */
	private static final int CUT_HEIGHT = 140;

	private static final class Aftermath {
		final Vec3d center;
		final int radius;
		int age;

		Aftermath(Vec3d center, int radius) {
			this.center = center;
			this.radius = radius;
		}
	}

	private static final List<Aftermath> AFTERMATHS = new ArrayList<>();
	private static final Random RANDOM = Random.create();
	private static boolean rebuilding;

	private ImpactEffects() {
	}

	public static void clear() {
		AFTERMATHS.clear();
		rebuilding = false;
	}

	/** True while a crater nearby is still settling and its terrain is being kept fully drawn. */
	public static boolean rebuilding() {
		return rebuilding;
	}

	public static void trigger(MinecraftClient client, ClientStrike strike) {
		if (client.player == null) {
			return;
		}
		double distance = client.player.getPos().distanceTo(strike.center);
		if (strike.cinematic()) {
			// The shooter's camera is right there: the flash, a beat of silence while the sound covers the
			// distance, the boom; then the shock wave when it reaches the camera, and the crater burning.
			double from = strike.witness != null ? strike.witness.distanceTo(strike.center) : strike.radius * 1.3;
			int arrival = strike.scene != null ? (int) strike.scene.arrival(from) : 30;
			ClientStrikes.master(ModSounds.STRIKE_IMPACT_NEAR, 1.0F, 1.0F, 3);
			ClientStrikes.master(ModSounds.STRIKE_RUMBLE_NEAR, 1.0F, 0.85F, arrival);
			ClientStrikes.master(ModSounds.STRIKE_AFTERMATH_NEAR, 1.0F, 0.55F, 90);
		} else {
			// Loud enough to carry about 640 blocks; it reaches you at the speed of sound.
			int delay = (int) (distance / SOUND_SPEED);
			ClientStrikes.at(client, strike.center, ModSounds.STRIKE_IMPACT, SoundCategory.WEATHER, 40.0F, 1.0F, delay);
			// The shock wave rolls over you from all round, weaker the farther it has come.
			double reach = strike.radius * 12.0 + 200.0;
			if (distance < reach) {
				int wave = strike.scene != null ? (int) strike.scene.arrival(distance) : delay + 4;
				float volume = (float) MathHelper.clamp(1.0 - distance / reach, 0.15, 1.0);
				ClientStrikes.around(client, ModSounds.STRIKE_RUMBLE, SoundCategory.WEATHER, volume, 1.0F, wave);
			}
			ClientStrikes.at(client, strike.center, ModSounds.STRIKE_AFTERMATH, SoundCategory.WEATHER, 6.0F, 1.0F, delay + 40);
		}
		if (distance < PARTICLE_RANGE) {
			AFTERMATHS.add(new Aftermath(strike.center, strike.radius));
		}
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		rebuilding = false;
		for (Iterator<Aftermath> it = AFTERMATHS.iterator(); it.hasNext(); ) {
			Aftermath a = it.next();
			if (++a.age > EMBER_TICKS) {
				it.remove();
				continue;
			}
			if (a.age < REBUILD_TICKS) {
				rebuilding = true;
				int wave = Math.max(18, Math.round(a.radius * 0.75F));
				if (a.age == wave + 5) {
					rebuild(client, a);
				}
			}
			if (client.player.getPos().squaredDistanceTo(a.center) > PARTICLE_RANGE * PARTICLE_RANGE) {
				continue;
			}
			float fade = 1.0F - a.age / (float) EMBER_TICKS;
			int embers = a.age < 120 ? 3 : RANDOM.nextFloat() < fade ? 1 : 0;
			for (int i = 0; i < embers; i++) {
				Vec3d c = a.center;
				double spread = a.radius * 0.3;
				client.particleManager.addParticle(ParticleTypes.LAVA, c.x + RANDOM.nextGaussian() * spread, c.y,
						c.z + RANDOM.nextGaussian() * spread, 0, 0, 0);
			}
		}
	}

	/**
	 * Once the shock wave has passed, marks the bowl's sections for a rebuild in case an update was missed. Only
	 * the bowl and the ground just above it: every extra section queued here waits in line with the whole world's
	 * mesh builds, and on a slow machine a long queue is what leaves the freshly opened ground unbuilt.
	 */
	private static void rebuild(MinecraftClient client, Aftermath a) {
		if (client.world == null) {
			return;
		}
		int reach = MathHelper.ceil(a.radius * 1.1) + 2;
		int cx = MathHelper.floor(a.center.x);
		int cy = MathHelper.floor(a.center.y);
		int cz = MathHelper.floor(a.center.z);
		int minY = Math.max(client.world.getBottomSectionCoord(), ChunkSectionPos.getSectionCoord(cy - a.radius / 2 - 8));
		int maxY = Math.min(client.world.getTopSectionCoord() - 1, ChunkSectionPos.getSectionCoord(cy + 24));
		int sections = MathHelper.ceil(reach / 16.0) + 1;
		int sx0 = ChunkSectionPos.getSectionCoord(cx);
		int sz0 = ChunkSectionPos.getSectionCoord(cz);
		for (int sx = sx0 - sections; sx <= sx0 + sections; sx++) {
			for (int sz = sz0 - sections; sz <= sz0 + sections; sz++) {
				double dx = (sx * 16 + 8) - a.center.x;
				double dz = (sz * 16 + 8) - a.center.z;
				if (dx * dx + dz * dz > (reach + 12.0) * (reach + 12.0)) {
					continue;
				}
				for (int sy = minY; sy <= maxY; sy++) {
					client.worldRenderer.scheduleBlockRender(sx, sy, sz);
				}
			}
		}
		client.worldRenderer.scheduleTerrainUpdate();
	}
}
