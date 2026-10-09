package io.github.kortev.chitty.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;

/**
 * A wisp of the shimmer over Chitty's hot bonnet and the end of her pipe (ChittyEntity's bonnetHeat): a faint wavering
 * streak of the air that rises a little way, swaying, brightens and fades. Many together make the air over her waver.
 */
final class ChittyHeatParticle extends SpriteBillboardParticle {
	/** How clear it is at its most visible. */
	private static final float CLEAR = 0.3F;
	private final float sway;

	private ChittyHeatParticle(ClientWorld world, double x, double y, double z, SpriteProvider sprites) {
		super(world, x, y, z);
		setSprite(sprites);
		maxAge = 12 + random.nextInt(10);
		scale = 0.16F + random.nextFloat() * 0.12F;
		alpha = 0.0F;
		velocityX = 0.0;
		velocityY = 0.025 + random.nextFloat() * 0.02;
		velocityZ = 0.0;
		gravityStrength = 0.0F;
		collidesWithWorld = false;
		sway = random.nextFloat() * (float) (Math.PI * 2.0);
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public void tick() {
		super.tick();
		float t = (float) age / maxAge;
		alpha = CLEAR * MathHelper.sin(t * (float) Math.PI);
		velocityX = MathHelper.sin(sway + age * 0.9F) * 0.008;
		velocityZ = MathHelper.cos(sway + age * 0.7F) * 0.008;
	}

	static final class Factory implements ParticleFactory<SimpleParticleType> {
		private final SpriteProvider sprites;

		Factory(SpriteProvider sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy,
				double vz) {
			return new ChittyHeatParticle(world, x, y, z, sprites);
		}
	}
}
