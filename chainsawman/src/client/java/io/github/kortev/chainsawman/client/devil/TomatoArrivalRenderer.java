package io.github.kortev.chainsawman.client.devil;

import io.github.kortev.chainsawman.client.FleshMesh;
import io.github.kortev.chainsawman.devil.TomatoArrivalEntity;
import java.util.List;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A Tomato Devil coming up ({@link TomatoArrivalEntity}): its body sunk in the ground with only the crown showing, its eyes opening one
 * after another at {@link TomatoArrivalEntity#EYES}, every one on the nearest player and shining in the dark (a pale glow round each, so
 * they read from 15 blocks at night); from {@link TomatoArrivalEntity#BULGE} the body swells and pushes up out of the earth, quivering,
 * till it bursts into the devil.
 */
public class TomatoArrivalRenderer extends EntityRenderer<TomatoArrivalEntity> {
	/** The body's size and its middle's height while only the eyes show, and at the burst. */
	static final float R0 = 0.5F;
	static final float R1 = 0.95F;
	static final float Y0 = -0.2F;
	static final float Y1 = 1.05F;
	/** How much bigger than the devil's own its eyes are drawn (seen from far, in the dark). */
	static final float EYE_GROW = 1.6F;

	public TomatoArrivalRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public Identifier getTexture(TomatoArrivalEntity entity) {
		return TomatoDevilRenderer.GASH;
	}

	@Override
	public boolean shouldRender(TomatoArrivalEntity entity, Frustum frustum, double x, double y, double z) {
		return entity.squaredDistanceTo(x, y, z) < 96.0 * 96.0;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	@Override
	public void render(TomatoArrivalEntity e, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(e.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float t = e.elapsed(delta);
		if (t < TomatoArrivalEntity.EYES - 2) {
			return;
		}
		float now = world.getTime() + delta;
		// The swell: slow at first, faster toward the burst.
		float s = MathHelper.clamp((t - TomatoArrivalEntity.BULGE) / (TomatoArrivalEntity.BURST - TomatoArrivalEntity.BULGE), 0.0F, 1.0F);
		float grow = s * s * (0.4F + 0.6F * s) + 0.25F * s;
		grow = Math.min(1.0F, grow);
		float r = MathHelper.lerp(grow, R0, R1) * (1.0F + 0.05F * s * MathHelper.sin(now * (1.0F + 2.5F * s)));
		float cy = MathHelper.lerp(grow, Y0, Y1);
		float jx = 0.05F * s * MathHelper.sin(now * 3.7F);
		float jz = 0.05F * s * MathHelper.cos(now * 4.3F);
		float turn = -e.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		Matrix4f bodyM = new Matrix4f().translate(jx, cy, jz).rotateY(turn).scale(r);
		Matrix4f bodyView = new Matrix4f(base).mul(bodyM);

		int sky = LightmapTextureManager.getSkyLightCoordinates(light);
		int block = LightmapTextureManager.getBlockLightCoordinates(light);
		float lit = Math.max(block, sky * (1.0F - world.getAmbientDarkness() / 15.0F)) / 15.0F;
		FleshMesh.Look look = new FleshMesh.Look();
		float k = 0.9F * (0.36F + 0.64F * lit);
		look.r = k;
		look.g = k;
		look.b = k;
		look.rim = 0.35F;
		look.hurt = 0.3F * s * (0.5F + 0.5F * MathHelper.sin(now * (0.4F + 1.2F * s)));
		FleshMesh.file("tomato_body").draw(bodyView, now, look, null);

		// The eyes, all on the nearest player.
		FleshMesh parts = FleshMesh.file("tomato_parts");
		FleshMesh.Part eye = parts.byName.get("eye");
		FleshMesh.Part pupil = parts.byName.get("pupil");
		FleshMesh.Part lid = parts.byName.get("lid");
		if (eye == null) {
			return;
		}
		PlayerEntity near = world.getClosestPlayer(e, 48.0);
		Vector3f target = null;
		if (near != null) {
			Vec3d ep = near.getLerpedPos(delta).add(0.0, near.getStandingEyeHeight(), 0.0).subtract(e.getLerpedPos(delta));
			target = new Matrix4f(bodyM).invert().transformPosition(new Vector3f((float) ep.x, (float) ep.y, (float) ep.z));
		}
		FleshMesh.Look shine = new FleshMesh.Look();
		shine.r = Math.max(k, 0.86F);
		shine.g = Math.max(k, 0.84F);
		shine.b = Math.max(k, 0.76F);
		shine.rim = 0.35F;
		VertexConsumer glow = provider.getBuffer(RenderLayer.getLightning());
		List<TomatoDevilRenderer.Eye> list = TomatoDevilRenderer.eyes();
		float grown = EYE_GROW * (1.0F - 0.35F * grow);
		for (int i = 0; i < list.size(); i++) {
			TomatoDevilRenderer.Eye ey = list.get(i);
			// Opening one after another, a few ticks apart.
			float open = ease((t - TomatoArrivalEntity.EYES - (i * 7 % 13) * 0.9F) / 6.0F);
			// Below the ground: never seen, not drawn.
			Vector3f world0 = bodyM.transformPosition(new Vector3f(ey.c()));
			if (world0.y < -0.15F) {
				continue;
			}
			Vector3f dir = target == null ? new Vector3f(ey.n()) : new Vector3f(target).sub(ey.c()).normalize();
			float cos = dir.dot(ey.n());
			if (cos < 0.4F) {
				Vector3f perp = new Vector3f(dir).sub(new Vector3f(ey.n()).mul(cos));
				if (perp.lengthSquared() < 1.0E-6F) {
					perp.set(0, 1, 0);
				}
				perp.normalize();
				dir = new Vector3f(ey.n()).mul(0.4F).add(perp.mul(0.917F)).normalize();
			}
			Vector3f zc = dir;
			Vector3f xc = new Vector3f(0, 1, 0).cross(zc);
			if (xc.lengthSquared() < 1.0E-4F) {
				xc.set(1, 0, 0);
			}
			xc.normalize();
			Vector3f yc = new Vector3f(zc).cross(xc);
			float sc = ey.r() / TomatoDevilRenderer.EYE_R * grown;
			Matrix4f m = new Matrix4f(bodyView).translate(ey.c()).mul(new Matrix4f(xc.x, xc.y, xc.z, 0, yc.x, yc.y, yc.z, 0, zc.x, zc.y, zc.z, 0, 0, 0, 0, 1))
					.scale(sc);
			if (open > 0.02F) {
				parts.drawPart(eye, m, shine, base);
				if (pupil != null) {
					float ps = 0.45F + 0.05F * MathHelper.sin(now * 0.3F + i);
					parts.drawPart(pupil, new Matrix4f(m).scale(ps, ps, 1.0F), look, base);
				}
			}
			if (lid != null) {
				Vector3f nz = new Vector3f(ey.n());
				Vector3f nx = new Vector3f(0, 1, 0).cross(nz);
				if (nx.lengthSquared() < 1.0E-4F) {
					nx.set(1, 0, 0);
				}
				nx.normalize();
				Vector3f ny = new Vector3f(nz).cross(nx).normalize();
				Matrix4f sock = new Matrix4f(bodyView).translate(ey.c()).mul(new Matrix4f(nx.x, nx.y, nx.z, 0, ny.x, ny.y, ny.z, 0, nz.x, nz.y, nz.z, 0, 0, 0, 0, 1)).scale(sc);
				float a = TomatoDevilRenderer.LID_OPEN * open;
				parts.drawPart(lid, new Matrix4f(sock).rotateX(a), look, base);
				parts.drawPart(lid, new Matrix4f(sock).rotateZ(MathHelper.PI).rotateX(a), look, base);
			}
			if (open > 0.02F) {
				// The eyeshine: a pale glow round the eye, facing the camera.
				Vector3f c = new Vector3f(ey.c()).add(new Vector3f(ey.n()).mul(0.1F));
				Vector3f wc = bodyM.transformPosition(c);
				halo(glow, matrices, wc, 0.32F * sc * r, 0.34F * open * (1.0F - 0.6F * s));
			}
		}
		if (s > 0.0F && world.random.nextFloat() < 0.5F) {
			world.addParticle(ParticleTypes.CRIMSON_SPORE, e.getX() + (world.random.nextDouble() - 0.5) * 2.0 * r * 1.3, e.getY() + 0.1,
					e.getZ() + (world.random.nextDouble() - 0.5) * 2.0 * r * 1.3, 0.0, 0.03, 0.0);
		}
	}

	private void halo(VertexConsumer vc, MatrixStack matrices, Vector3f at, float size, float alpha) {
		matrices.push();
		matrices.translate(at.x, at.y, at.z);
		matrices.multiply(this.dispatcher.getRotation());
		Matrix4f h = matrices.peek().getPositionMatrix();
		int segs = 10;
		for (int i = 0; i < segs; i++) {
			float a0 = i * MathHelper.TAU / segs;
			float a1 = (i + 1) * MathHelper.TAU / segs;
			vc.vertex(h, 0.0F, 0.0F, 0.0F).color(1.0F, 0.95F, 0.8F, alpha);
			vc.vertex(h, MathHelper.cos(a0) * size, MathHelper.sin(a0) * size, 0.0F).color(1.0F, 0.85F, 0.6F, 0.0F);
			vc.vertex(h, MathHelper.cos(a1) * size, MathHelper.sin(a1) * size, 0.0F).color(1.0F, 0.85F, 0.6F, 0.0F);
			vc.vertex(h, 0.0F, 0.0F, 0.0F).color(1.0F, 0.95F, 0.8F, alpha);
		}
		matrices.pop();
	}
}
