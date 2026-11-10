package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.ShaftHeadEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A head lunging in the Eternity Devil's arena ({@link ShaftHeadEntity}): the lone head of the stages mesh ({@code eternity_head_*}, gaunt,
 * its jaw baked wide open), pale, on a neck of the arm mesh's segments out of the floor. It rises with its eyes lit on you, rears back with its
 * line lit orange on the floor (where it will go), lunges along it with its jaw snapping, and if it missed, hangs out at the end of its neck,
 * stunned, glowing at the neck where to cut.
 */
public class ShaftHeadRenderer extends EntityRenderer<ShaftHeadEntity> {
	/** The head mesh's jaw is baked open this many degrees; a positive angle closes it. */
	static final float JAW_OPEN = 52.0F;

	public ShaftHeadRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ShaftHeadEntity entity) {
		return Identifier.of("chainsawman", "textures/entity/eternity_devil.png");
	}

	@Override
	public boolean shouldRender(ShaftHeadEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	@Override
	public void render(ShaftHeadEntity head, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(head.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float t = head.age(delta);
		int out = head.outcome();
		Vec3d pos = head.getLerpedPos(delta);
		Vec3d h = head.headAt(t).subtract(pos);
		Vec3d d = head.dir();
		Vec3d up = new Vec3d(0.0, 1.0, 0.0);
		Glow g = new Glow(provider, matrices);
		// The line it will lunge along, lit on the floor from the moment it is fixed: brighter, and beating faster, to the strike.
		if (head.locked() && out == ShaftHeadEntity.PENDING) {
			float k = MathHelper.clamp((t - ShaftHeadEntity.UP) / ShaftHeadEntity.REAR, 0.0F, 1.0F);
			float beat = 0.5F + 0.5F * MathHelper.sin(t * (0.6F + 1.4F * k));
			float a = Math.min(1.0F, 0.45F + 0.55F * k) * (0.7F + 0.3F * beat);
			Vec3d y = new Vec3d(0.0, 0.05, 0.0);
			Vec3d end = y.add(d.multiply(ShaftHeadEntity.REACH + 0.7));
			Vec3d side = new Vec3d(d.z, 0.0, -d.x).multiply(ShaftHeadEntity.BITE_RADIUS);
			g.line(y, end, up, 0.14 + 0.16 * k, 1.0F, 0.7F, 0.25F, a);
			g.line(y.add(side), end.add(side), up, 0.1, 1.0F, 0.5F, 0.1F, a * 0.8F);
			g.line(y.subtract(side), end.subtract(side), up, 0.1, 1.0F, 0.5F, 0.1F, a * 0.8F);
			g.fade(y.add(side), y.subtract(side), end.subtract(side), end.add(side), 1.0F, 0.4F, 0.08F, 0.2F * k, 0.05F * k);
		}
		if (h.y < -0.95) {
			return;
		}
		// Which way it looks: at the nearest player as it rises, then along its line.
		double theta;
		if (head.locked()) {
			theta = Math.atan2(-d.z, d.x);
		} else {
			PlayerEntity near = world.getClosestPlayer(head, 16.0);
			theta = near == null ? 0.0 : Math.atan2(-(near.getZ() - head.getZ()), near.getX() - head.getX());
		}
		// The neck: out of the floor at the foot, up and along to the head.
		FleshMesh mesh = FleshMesh.get("shaft");
		FleshMesh.Part seg = mesh.byName.get("grab_segment");
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		boolean stunned = head.stunned();
		FleshMesh.Look look = new FleshMesh.Look().light(net.minecraft.client.render.LightmapTextureManager.pack(15, 15), 0.7F, 1.3F);
		look.rim = 0.6F;
		look.hurt = out == ShaftHeadEntity.CUT ? 0.6F : stunned ? 0.18F + 0.18F * MathHelper.sin(t * 0.5F) : 0.0F;
		if (seg != null) {
			Vec3d foot = new Vec3d(0.0, -0.6, 0.0);
			Vec3d knee = new Vec3d(0.0, Math.max(-0.6, h.y - 0.4), 0.0).add(d.multiply(Math.min(0.0, h.dotProduct(d)) * 0.5));
			Vec3d neckEnd = h.subtract(d.multiply(0.45)).add(0.0, -0.35, 0.0);
			Vec3d[] pts = {foot, knee, neckEnd};
			for (int i = 0; i < pts.length - 1; i++) {
				Vec3d a = pts[i];
				Vec3d b = pts[i + 1];
				Vec3d ab = b.subtract(a);
				double len = ab.length();
				if (len < 0.05) {
					continue;
				}
				Vec3d u = ab.multiply(1.0 / len);
				Quaternionf q = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) u.x, (float) u.y, (float) u.z);
				int n = Math.max(1, (int) Math.ceil(len / 0.5));
				float step = (float) (len / n);
				for (int k = 0; k < n; k++) {
					Vec3d p = a.add(u.multiply(k * step));
					mesh.drawPart(seg, new Matrix4f(base).translate((float) p.x, (float) p.y, (float) p.z).rotate(q).scale(1.35F, step / 0.5F, 1.35F), look, base);
				}
			}
			if (stunned) {
				// Stunned at the end of its neck: glowing rings where to cut.
				float a = 0.55F + 0.45F * MathHelper.sin(t * 0.6F);
				Vec3d nd = neckEnd.subtract(knee);
				for (int i = 1; i <= 3; i++) {
					Vec3d c = knee.add(nd.multiply(i / 4.0));
					g.arc(c, 0.55, 0.13, 0.0, Math.PI * 2.0, nd.crossProduct(up).normalize(), up, 1.0F, 0.75F, 0.3F, a);
				}
			}
		}
		// The head: its jaw open as it rears, snapping shut as it strikes.
		float strike = ShaftHeadEntity.STRIKE;
		float closed;
		if (t < strike) {
			closed = JAW_OPEN * (1.0F - ease((t - 4.0F) / 10.0F));
		} else if (t < strike + ShaftHeadEntity.LUNGE + 1) {
			closed = JAW_OPEN * ease((t - strike - ShaftHeadEntity.LUNGE + 1.5F) / 1.5F);
		} else {
			closed = JAW_OPEN * 0.5F;
		}
		Matrix4f model = new Matrix4f(base).translate((float) h.x, (float) (h.y - 1.45 * 0.95), (float) h.z).rotateY((float) theta).scale(0.95F);
		// Its eyes lit on you while it comes and rears (the tell), the face glowing.
		boolean telling = out == ShaftHeadEntity.PENDING && t < strike;
		look.glowAt = new Vector3f(0.45F, 1.55F, 0.0F);
		look.glowRange = telling ? 0.7F : 0.4F;
		look.glowR = telling ? 1.4F : 0.6F;
		look.glowG = telling ? 0.55F : 0.2F;
		look.glowB = 0.08F;
		FleshMesh.get("head").draw(model, world.getTime() + delta, look, jawHook(closed));
		if (telling) {
			// Two lit eyes, looking at you.
			Vec3d fwd = new Vec3d(Math.cos(theta), 0.0, -Math.sin(theta));
			Vec3d sideways = new Vec3d(fwd.z, 0.0, -fwd.x);
			float a = 0.7F + 0.3F * MathHelper.sin(t * 0.9F);
			for (int s = -1; s <= 1; s += 2) {
				Vec3d e = h.add(fwd.multiply(0.5)).add(sideways.multiply(0.2 * s)).add(0.0, 0.1, 0.0);
				g.arc(e, 0.06, 0.12, 0.0, Math.PI * 2.0, sideways, up, 1.0F, 0.85F, 0.4F, a);
				g.arc(e, 0.16, 0.12, 0.0, Math.PI * 2.0, sideways, up, 1.0F, 0.45F, 0.1F, a * 0.5F);
			}
		}
	}

	/** A hook that sets the head mesh's jaw to {@code closed} degrees (0 wide open, 52 shut). */
	static FleshMesh.Hook jawHook(float closed) {
		return (p, m, base) -> {
			if (p.kind.equals("jaw")) {
				m.set(base).translate(p.pivot).rotate((float) Math.toRadians(closed), p.anim.axis).translate(-p.pivot.x, -p.pivot.y, -p.pivot.z);
			}
			return true;
		};
	}
}
