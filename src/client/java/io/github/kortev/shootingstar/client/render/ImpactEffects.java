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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * The impact's sound (the boom reaches you at the speed of sound) and the embers that keep spitting
 * out of the bowl afterwards. The blast itself is drawn by {@code WorldFx}.
 */
public final class ImpactEffects {
	/** Speed of sound in blocks per tick, give or take. */
	private static final double SOUND_SPEED = 17.0;
	private static final double PARTICLE_RANGE = 480.0;
	private static final int EMBER_TICKS = 700;

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
		ClientStrikes.at(client, strike.center, ModSounds.STRIKE_IMPACT, SoundCategory.WEATHER, 4.0F + strike.radius / 16.0F,
				1.0F, delay);
		ClientStrikes.at(client, strike.center, ModSounds.STRIKE_RUMBLE, SoundCategory.WEATHER, 6.0F + strike.radius / 10.0F,
				0.9F, delay + 4);
		if (distance < PARTICLE_RANGE) {
			AFTERMATHS.add(new Aftermath(strike.center, strike.radius));
		}
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		for (Iterator<Aftermath> it = AFTERMATHS.iterator(); it.hasNext(); ) {
			Aftermath a = it.next();
			if (++a.age > EMBER_TICKS) {
				it.remove();
				continue;
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
}
