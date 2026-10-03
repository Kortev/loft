package io.github.kortev.shootingstar.client.render;

import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.registry.ModSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/** Impact sound, the initial blast of particles, and the smoke column that lingers afterwards. */
public final class ImpactEffects {
	/** Speed of sound in blocks per tick, give or take. */
	private static final double SOUND_SPEED = 17.0;
	private static final double PARTICLE_RANGE = 480.0;
	private static final int COLUMN_TICKS = 700;

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

	private ImpactEffects() {
	}

	public static void clear() {
		AFTERMATHS.clear();
	}

	public static void trigger(MinecraftClient client, ClientStrike strike) {
		if (client.player == null) {
			return;
		}
		double distance = client.player.getPos().distanceTo(strike.center);
		int delay = strike.cinematic() ? 0 : (int) (distance / SOUND_SPEED);
		ClientStrikes.at(client, strike.center, ModSounds.STRIKE_IMPACT, SoundCategory.WEATHER, 4.0F, 1.0F, delay);
		if (distance > 96.0) {
			ClientStrikes.at(client, strike.center, ModSounds.STRIKE_RUMBLE, SoundCategory.WEATHER, 6.0F, 1.0F, delay + 4);
		}
		if (distance < PARTICLE_RANGE) {
			burst(client.particleManager, strike);
			AFTERMATHS.add(new Aftermath(strike.center, strike.radius));
		}
	}

	private static void burst(ParticleManager particles, ClientStrike strike) {
		Vec3d c = strike.center;
		for (int i = 0; i < 8; i++) {
			add(particles, ParticleTypes.EXPLOSION_EMITTER, c.x + gauss(3), c.y + RANDOM.nextDouble() * 4, c.z + gauss(3), 0, 0, 0);
		}
		// Fireball thrown up and out of the bowl.
		for (int i = 0; i < 260; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double speed = 0.3 + RANDOM.nextDouble() * 1.4;
			double up = 0.4 + RANDOM.nextDouble() * 1.6;
			ParticleEffect type = i % 3 == 0 ? ParticleTypes.LAVA : i % 3 == 1 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE;
			add(particles, type, c.x + gauss(2), c.y + 1, c.z + gauss(2), Math.cos(angle) * speed, up, Math.sin(angle) * speed);
		}
		// Shockwave: a ring of dust racing outward.
		for (int i = 0; i < 420; i++) {
			double angle = Math.PI * 2 * i / 420.0 + RANDOM.nextDouble() * 0.02;
			double speed = 1.1 + RANDOM.nextDouble() * 0.6;
			double r = 3.0 + RANDOM.nextDouble() * 2.0;
			add(particles, i % 2 == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.CLOUD,
					c.x + Math.cos(angle) * r, c.y + 0.5 + RANDOM.nextDouble() * 2.5, c.z + Math.sin(angle) * r,
					Math.cos(angle) * speed, 0.02 + RANDOM.nextDouble() * 0.08, Math.sin(angle) * speed);
		}
		// A flash of sparks straight up the spire.
		for (int i = 0; i < 120; i++) {
			add(particles, ParticleTypes.FIREWORK, c.x + gauss(1.5), c.y + RANDOM.nextDouble() * 6, c.z + gauss(1.5),
					gauss(0.3), 0.8 + RANDOM.nextDouble() * 2.5, gauss(0.3));
		}
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		ParticleManager particles = client.particleManager;
		for (Iterator<Aftermath> it = AFTERMATHS.iterator(); it.hasNext(); ) {
			Aftermath a = it.next();
			if (++a.age > COLUMN_TICKS) {
				it.remove();
				continue;
			}
			if (client.player.getPos().squaredDistanceTo(a.center) > PARTICLE_RANGE * PARTICLE_RANGE) {
				continue;
			}
			float fade = 1.0F - a.age / (float) COLUMN_TICKS;
			Vec3d c = a.center;
			// Smoke column from the pit around the spire.
			int smoke = a.age < 200 ? 4 : RANDOM.nextFloat() < fade * 2 ? 1 : 0;
			for (int i = 0; i < smoke; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double r = 3.0 + RANDOM.nextDouble() * 3.0;
				add(particles, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x + Math.cos(angle) * r, c.y + RANDOM.nextDouble() * 2,
						c.z + Math.sin(angle) * r, gauss(0.01), 0.07 + RANDOM.nextDouble() * 0.08, gauss(0.01));
			}
			// Embers spitting out of the bowl.
			if (RANDOM.nextFloat() < fade) {
				add(particles, ParticleTypes.LAVA, c.x + gauss(a.radius * 0.25), c.y, c.z + gauss(a.radius * 0.25), 0, 0, 0);
			}
			// Dust wall rolling outward from the rim for the first few seconds.
			if (a.age < 120) {
				for (int i = 0; i < 6; i++) {
					double angle = RANDOM.nextDouble() * Math.PI * 2;
					double r = a.radius * (0.9 + a.age / 120.0 * 0.9);
					add(particles, ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x + Math.cos(angle) * r, c.y + RANDOM.nextDouble() * 6,
							c.z + Math.sin(angle) * r, Math.cos(angle) * 0.08, 0.015, Math.sin(angle) * 0.08);
				}
			}
		}
	}

	private static void add(ParticleManager particles, ParticleEffect type, double x, double y, double z, double vx,
			double vy, double vz) {
		particles.addParticle(type, x, y, z, vx, vy, vz);
	}

	private static double gauss(double scale) {
		return RANDOM.nextGaussian() * scale;
	}
}
