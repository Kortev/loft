package io.github.kortev.gojolander.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;

/**
 * A faint mote of the void's pale light ({@link io.github.kortev.gojolander.GojoParticles#MOTE}): a small soft dot,
 * cold blue-white and lit by itself, that fades in, drifts slowly up and away, and fades out.
 */
public final class MoteParticle extends SpriteBillboardParticle {
	private final float peak;

	MoteParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
		super(world, x, y, z, vx, vy, vz);
		velocityX = vx;
		velocityY = vy;
		velocityZ = vz;
		velocityMultiplier = 0.95F;
		gravityStrength = -0.004F;
		collidesWithWorld = false;
		maxAge = 36 + random.nextInt(24);
		scale = 0.08F + random.nextFloat() * 0.04F;
		red = 0.86F;
		green = 0.94F;
		blue = 1.0F;
		peak = 0.85F + random.nextFloat() * 0.15F;
		alpha = 0.0F;
		setSprite(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		float life = (float) age / maxAge;
		alpha = MathHelper.clamp(Math.min(life * 5.0F, (1.0F - life) * 1.8F), 0.0F, 1.0F) * peak;
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	protected int getBrightness(float tint) {
		return 0xF000F0;
	}

	public record Factory(SpriteProvider sprites) implements ParticleFactory<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
			return new MoteParticle(world, x, y, z, vx, vy, vz, sprites);
		}
	}
}
