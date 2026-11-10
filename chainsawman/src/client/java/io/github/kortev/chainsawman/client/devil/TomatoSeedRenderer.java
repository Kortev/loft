package io.github.kortev.chainsawman.client.devil;

import io.github.kortev.chainsawman.client.FleshMesh;
import io.github.kortev.chainsawman.devil.TomatoSeedEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * A Tomato Devil's seed in the ground: a pale seed in red jelly, half sunk, glowing faintly with a slow pulse and a soft light on the
 * ground round it. Swelling, it grows into a small red tomato coming up out of the earth, pulsing faster; withering, it shrinks and greys.
 */
public class TomatoSeedRenderer extends EntityRenderer<TomatoSeedEntity> {
	public TomatoSeedRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public Identifier getTexture(TomatoSeedEntity entity) {
		return TomatoDevilRenderer.GASH;
	}

	@Override
	public void render(TomatoSeedEntity e, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(e.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float now = world.getTime() + delta;
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		FleshMesh parts = FleshMesh.file("tomato_parts");
		FleshMesh.Part seed = parts.byName.get("seed");
		if (seed == null) {
			return;
		}
		int swell = e.swelling();
		int wither = e.withering();
		float pulse = 0.5F + 0.5F * MathHelper.sin(now * (swell >= 0 ? 0.6F : 0.12F) + e.getId());
		FleshMesh.Look look = new FleshMesh.Look();
		int sky = LightmapTextureManager.getSkyLightCoordinates(light);
		int block = LightmapTextureManager.getBlockLightCoordinates(light);
		float lit = Math.max(block, sky * (1.0F - world.getAmbientDarkness() / 15.0F)) / 15.0F;
		float k = 0.55F + 0.5F * lit + 0.25F * pulse;
		look.r = k;
		look.g = k;
		look.b = k;
		look.rim = 0.5F;
		float scale = 2.4F;
		if (wither >= 0) {
			float w = MathHelper.clamp((wither + delta) / TomatoSeedEntity.WITHER, 0.0F, 1.0F);
			scale *= 1.0F - 0.7F * w;
			look.r = k * (1.0F - 0.5F * w);
			look.g = k * (1.0F - 0.2F * w);
			look.b = k * (1.0F - 0.1F * w);
		}
		float spin = e.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		float sunk = e.landed() ? -0.06F : 0.1F;
		Matrix4f m = new Matrix4f(base).translate(0.0F, sunk + 0.08F, 0.0F).rotateY(spin).rotateX(e.landed() ? -0.35F : now * 0.4F).scale(scale);
		parts.drawPart(seed, m, look, base);
		if (swell >= 0) {
			// The tell: it swells into a little tomato coming up out of the earth, quivering.
			float s = MathHelper.clamp((swell + delta) / TomatoSeedEntity.SWELL, 0.0F, 1.0F);
			float r = 0.12F + 0.5F * s + 0.04F * MathHelper.sin(now * (0.6F + 1.2F * s));
			FleshMesh body = FleshMesh.file("tomato_body");
			Matrix4f bm = new Matrix4f(base).translate(0.03F * MathHelper.sin(now * 3.1F) * s, r * 1.06F * 0.7F, 0.0F).rotateY(spin).scale(r);
			FleshMesh.Look bl = new FleshMesh.Look();
			float bk = 0.5F + 0.6F * lit;
			bl.r = bk;
			bl.g = bk;
			bl.b = bk;
			bl.hurt = 0.4F * pulse * s;
			body.draw(bm, now, bl, null);
			if (world.random.nextFloat() < 0.15F + 0.4F * s) {
				world.addParticle(ParticleTypes.CRIMSON_SPORE, e.getX() + (world.random.nextDouble() - 0.5) * r * 2, e.getY() + 0.1,
						e.getZ() + (world.random.nextDouble() - 0.5) * r * 2, 0.0, 0.02, 0.0);
			}
		}
		// The faint glow on the ground round it.
		if (wither < 0) {
			VertexConsumer vc = provider.getBuffer(RenderLayer.getLightning());
			Matrix4f g = matrices.peek().getPositionMatrix();
			float a = (0.10F + 0.14F * pulse) * (swell >= 0 ? 1.6F : 1.0F);
			float rr = swell >= 0 ? 0.9F : 0.42F;
			int n = 16;
			for (int i = 0; i < n; i++) {
				float a0 = i * MathHelper.TAU / n;
				float a1 = (i + 1) * MathHelper.TAU / n;
				vc.vertex(g, 0.0F, 0.03F, 0.0F).color(1.0F, 0.45F, 0.15F, a);
				vc.vertex(g, MathHelper.cos(a0) * rr, 0.03F, MathHelper.sin(a0) * rr).color(1.0F, 0.3F, 0.08F, 0.0F);
				vc.vertex(g, MathHelper.cos(a1) * rr, 0.03F, MathHelper.sin(a1) * rr).color(1.0F, 0.3F, 0.08F, 0.0F);
				vc.vertex(g, 0.0F, 0.03F, 0.0F).color(1.0F, 0.45F, 0.15F, a);
				vc.vertex(g, 0.0F, 0.03F, 0.0F).color(1.0F, 0.45F, 0.15F, a);
				vc.vertex(g, MathHelper.cos(a1) * rr, 0.03F, MathHelper.sin(a1) * rr).color(1.0F, 0.3F, 0.08F, 0.0F);
				vc.vertex(g, MathHelper.cos(a0) * rr, 0.03F, MathHelper.sin(a0) * rr).color(1.0F, 0.3F, 0.08F, 0.0F);
				vc.vertex(g, 0.0F, 0.03F, 0.0F).color(1.0F, 0.45F, 0.15F, a);
			}
		}
	}
}
