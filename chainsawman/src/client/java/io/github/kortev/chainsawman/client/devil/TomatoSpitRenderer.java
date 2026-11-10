package io.github.kortev.chainsawman.client.devil;

import io.github.kortev.chainsawman.client.FleshMesh;
import io.github.kortev.chainsawman.devil.TomatoSpitEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A spat seed in flight: the seed pointing the way it flies, trailing juice. */
public class TomatoSpitRenderer extends EntityRenderer<TomatoSpitEntity> {
	private static final DustParticleEffect TRAIL = new DustParticleEffect(new Vector3f(0.85F, 0.12F, 0.05F), 0.9F);

	public TomatoSpitRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(TomatoSpitEntity entity) {
		return TomatoDevilRenderer.GASH;
	}

	@Override
	public void render(TomatoSpitEntity e, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		FleshMesh parts = FleshMesh.file("tomato_parts");
		FleshMesh.Part seed = parts.byName.get("seed");
		if (seed == null) {
			return;
		}
		Vec3d v = e.getVelocity();
		if (v.lengthSquared() < 1.0E-6) {
			v = new Vec3d(0, 0, 1);
		}
		Vector3f z = new Vector3f((float) v.x, (float) v.y, (float) v.z).normalize();
		Vector3f x = new Vector3f(0, 1, 0).cross(z);
		if (x.lengthSquared() < 1.0E-4F) {
			x = new Vector3f(1, 0, 0);
		}
		x.normalize();
		Vector3f y = new Vector3f(z).cross(x);
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		float spin = (e.age + delta) * 0.8F;
		Matrix4f m = new Matrix4f(base).translate(0, 0.15F, 0).mul(new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1)).rotateZ(spin).scale(1.8F);
		FleshMesh.Look look = new FleshMesh.Look();
		look.rim = 0.4F;
		parts.drawPart(seed, m, look, base);
		if (e.getWorld().random.nextFloat() < 0.5F) {
			e.getWorld().addParticle(TRAIL, e.getX(), e.getY() + 0.15, e.getZ(), 0.0, 0.0, 0.0);
		}
	}
}
