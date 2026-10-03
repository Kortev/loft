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
	private static boolean cullingOverride;
	private static boolean savedCulling;

	private ImpactEffects() {
	}

	public static void clear() {
		AFTERMATHS.clear();
		if (cullingOverride) {
			MinecraftClient.getInstance().chunkCullingEnabled = savedCulling;
			cullingOverride = false;
		}
	}

	public static void trigger(MinecraftClient client, ClientStrike strike) {
		if (client.player == null) {
			return;
		}
		double distance = client.player.getPos().distanceTo(strike.center);
		if (strike.cinematic()) {
			// The shooter's camera is right there: the hit at once, the rumble when the shock wave reaches the camera.
			double from = strike.witness != null ? strike.witness.distanceTo(strike.center) : strike.radius * 1.3;
			int arrival = strike.scene != null ? (int) strike.scene.arrival(from) : 30;
			ClientStrikes.master(ModSounds.STRIKE_IMPACT, 1.0F, 1.0F);
			ClientStrikes.master(ModSounds.STRIKE_RUMBLE, 0.9F, 1.0F, arrival);
		} else {
			// Loud enough to carry about 640 blocks; it reaches you at the speed of sound.
			int delay = (int) (distance / SOUND_SPEED);
			ClientStrikes.at(client, strike.center, ModSounds.STRIKE_IMPACT, SoundCategory.WEATHER, 40.0F, 1.0F, delay);
			ClientStrikes.at(client, strike.center, ModSounds.STRIKE_RUMBLE, SoundCategory.WEATHER, 48.0F, 0.9F, delay + 4);
		}
		if (distance < PARTICLE_RANGE) {
			AFTERMATHS.add(new Aftermath(strike.center, strike.radius));
		}
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		boolean rebuilding = false;
		for (Iterator<Aftermath> it = AFTERMATHS.iterator(); it.hasNext(); ) {
			Aftermath a = it.next();
			if (++a.age > EMBER_TICKS) {
				it.remove();
				continue;
			}
			if (a.age < REBUILD_TICKS) {
				rebuilding = true;
				int wave = Math.max(18, Math.round(a.radius * 0.75F));
				if (a.age == wave + 5 || a.age == 100 || a.age == 200 || a.age == REBUILD_TICKS - 20) {
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
		// Section occlusion culling decides which sections are drawn (and rebuilt) from what each one
		// looked like when it was last built. Ground that was buried a moment ago and is now open to the
		// sky can be judged hidden and never rebuilt, leaving holes that show the sky through the world,
		// so culling is off while the crater settles.
		if (rebuilding != cullingOverride) {
			if (rebuilding) {
				savedCulling = client.chunkCullingEnabled;
				client.chunkCullingEnabled = false;
			} else {
				client.chunkCullingEnabled = savedCulling;
			}
			cullingOverride = rebuilding;
			client.worldRenderer.scheduleTerrainUpdate();
		}
	}

	/** Marks every section the carving can have touched for a rebuild. */
	private static void rebuild(MinecraftClient client, Aftermath a) {
		if (client.world == null) {
			return;
		}
		int reach = MathHelper.ceil(a.radius * 1.5) + 2;
		int cx = MathHelper.floor(a.center.x);
		int cy = MathHelper.floor(a.center.y);
		int cz = MathHelper.floor(a.center.z);
		int minY = Math.max(client.world.getBottomSectionCoord(), ChunkSectionPos.getSectionCoord(cy - a.radius - 8));
		int maxY = Math.min(client.world.getTopSectionCoord() - 1, ChunkSectionPos.getSectionCoord(cy + CUT_HEIGHT + 2));
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
