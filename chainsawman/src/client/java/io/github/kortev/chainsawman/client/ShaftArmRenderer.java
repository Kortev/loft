package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.ShaftArmEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One of the Eternity Devil's arms ({@link ShaftArmEntity}), from the shaft mesh's grab parts (a root, copies of the tiling segment, the
 * long-fingered hand), pale against the dark flesh, in its three ways:
 * <ul>
 * <li>rising up the shaft: the segments stacked up the column, the hand at the tip;</li>
 * <li>the slam: the lane lit orange on the floor (its edges and a seam down its middle, beating faster to the slam) and the arm's back
 * breaking the floor along it; then up and down along the lane; lying there after, and if it missed, glowing bands where to cut;</li>
 * <li>the grab: the hand rising open at the wall, its palm lit, the reach of its sweep lit on the floor; then the arm sweeping low; closed
 * on whoever it caught; lying open after a miss, glowing.</li>
 * </ul>
 */
public class ShaftArmRenderer extends EntityRenderer<ShaftArmEntity> {
	/** The grab parts' rest geometry (shaft mesh): the root's base and the height of its top (the segment's and the hand's foot). */
	private static final float ROOT_BASE = 0.7F;
	private static final float ROOT_TOP = 2.9F;
	/** The segment's rest length. */
	private static final float SEG = 0.5F;

	public ShaftArmRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ShaftArmEntity entity) {
		return Identifier.of("chainsawman", "textures/entity/eternity_devil.png");
	}

	@Override
	public boolean shouldRender(ShaftArmEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	private static FleshMesh.Look pale(int light, float hurt) {
		FleshMesh.Look look = new FleshMesh.Look().light(net.minecraft.client.render.LightmapTextureManager.pack(15, 15), 0.7F, 1.3F);
		look.rim = 0.6F;
		look.hurt = hurt;
		return look;
	}

	@Override
	public void render(ShaftArmEntity arm, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(arm.getWorld() instanceof ClientWorld world)) {
			return;
		}
		int mode = arm.mode();
		if (mode == ShaftArmEntity.SLAM) {
			slam(arm, world, delta, matrices, provider, light);
		} else if (mode == ShaftArmEntity.GRAB) {
			grab(arm, world, delta, matrices, provider, light);
		} else {
			rise(arm, world, delta, matrices, light);
		}
	}

	/** Copies of the segment from {@code a} to {@code b} (the entity's frame), {@code thick} times as thick, and the hand at {@code b} pointing on. */
	private static void chain(FleshMesh mesh, Matrix4f base, Vec3d a, Vec3d b, float thick, int k, boolean hand, boolean closed, FleshMesh.Look look) {
		FleshMesh.Part seg = mesh.byName.get("grab_segment");
		FleshMesh.Part h = mesh.byName.get("grab_" + k + "_hand");
		if (seg == null || h == null) {
			return;
		}
		Vec3d d = b.subtract(a);
		double len = d.length();
		if (len < 0.05) {
			return;
		}
		Vec3d u = d.multiply(1.0 / len);
		Quaternionf q = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) u.x, (float) u.y, (float) u.z);
		int n = Math.max(1, (int) Math.ceil(len / SEG));
		float step = (float) (len / n);
		for (int i = 0; i < n; i++) {
			Vec3d p = a.add(u.multiply(i * step));
			Matrix4f s = new Matrix4f(base).translate((float) p.x, (float) p.y, (float) p.z).rotate(q).scale(thick, step / SEG, thick);
			mesh.drawPart(seg, s, look, base);
		}
		if (hand) {
			var top = h.json.getAsJsonArray("root_top");
			Vector3f rt = new Vector3f(top.get(0).getAsFloat(), ROOT_TOP, top.get(2).getAsFloat());
			Matrix4f hm = new Matrix4f(base).translate((float) b.x, (float) b.y, (float) b.z).rotate(q).scale(thick * 1.25F);
			if (closed) {
				hm.rotateX(0.5F);
			}
			hm.translate(-rt.x, -rt.y, -rt.z);
			mesh.drawPart(h, hm, look, base);
		}
	}

	private void slam(ShaftArmEntity arm, ClientWorld world, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		float t = arm.age(delta);
		int out = arm.outcome();
		float since = out == ShaftArmEntity.PENDING ? -1.0F : (float) (world.getTime() - arm.outAt()) + delta;
		Vec3d d = arm.dir();
		Vec3d side = new Vec3d(d.z, 0.0, -d.x);
		Vec3d up = new Vec3d(0.0, 1.0, 0.0);
		double len = arm.len();
		Glow g = new Glow(provider, matrices);
		// The tell: the lane lit on the floor, brighter and beating faster as the slam comes.
		if (out == ShaftArmEntity.PENDING && t < ShaftArmEntity.SLAM_TELL + ShaftArmEntity.SLAM_FALL) {
			float k = MathHelper.clamp(t / ShaftArmEntity.SLAM_TELL, 0.0F, 1.0F);
			float beat = 0.5F + 0.5F * MathHelper.sin(t * (0.5F + 1.4F * k));
			float a = Math.min(1.0F, 0.35F + 0.65F * k) * (0.7F + 0.3F * beat);
			double half = ShaftArmEntity.LANE / 2.0;
			Vec3d y = new Vec3d(0.0, 0.04, 0.0);
			g.line(y.add(side.multiply(half)), y.add(side.multiply(half)).add(d.multiply(len)), up, 0.16, 1.0F, 0.55F, 0.1F, a);
			g.line(y.add(side.multiply(-half)), y.add(side.multiply(-half)).add(d.multiply(len)), up, 0.16, 1.0F, 0.55F, 0.1F, a);
			g.line(y, y.add(d.multiply(len)), up, 0.12 + 0.18 * k, 1.0F, 0.75F, 0.3F, Math.min(1.0F, a + 0.2F));
			g.fade(y.add(side.multiply(half)), y.add(side.multiply(-half)), y.add(side.multiply(-half)).add(d.multiply(len)), y.add(side.multiply(half)).add(d.multiply(len)),
					1.0F, 0.4F, 0.08F, 0.22F * k, 0.08F * k);
			// The wall it comes from: a lit seam up it.
			Vec3d w0 = side.multiply(-1.2).add(0.0, 0.05, 0.0).add(d.multiply(0.03));
			Vec3d w1 = side.multiply(1.2).add(0.0, 0.05, 0.0).add(d.multiply(0.03));
			g.fade(w0, w1, w1.add(0.0, 3.0 * k + 0.5, 0.0), w0.add(0.0, 3.0 * k + 0.5, 0.0), 1.0F, 0.5F, 0.1F, 0.7F * a, 0.0F);
		}
		// The arm: its back in the floor (the tell), up and slammed down, lying, then back into the wall (or sunk, cut).
		double lift;
		double reach = len;
		double sunk;
		if (out == ShaftArmEntity.PENDING && t < ShaftArmEntity.SLAM_TELL) {
			sunk = -0.75 + 0.45 * ease(t / 12.0F);
			lift = 0.0;
		} else if (out == ShaftArmEntity.PENDING || since >= 0.0F && since < 1.0F && out != ShaftArmEntity.CUT) {
			float s = t - ShaftArmEntity.SLAM_TELL;
			lift = s < 1.5F ? 1.8 * ease(s / 1.5F) : 1.8 * (1.0 - ease((s - 1.5F) / 1.5F));
			sunk = -0.3 + 0.55 * ease(s / 1.0F);
		} else if (out == ShaftArmEntity.CUT) {
			sunk = 0.25 - 1.3 * ease(since / ShaftArmEntity.BACK);
			lift = 0.0;
		} else {
			sunk = 0.25;
			lift = 0.0;
			if (since > ShaftArmEntity.LIE) {
				reach = len * (1.0 - ease((since - ShaftArmEntity.LIE) / ShaftArmEntity.BACK));
			}
		}
		if (reach > 0.2) {
			FleshMesh mesh = FleshMesh.get("shaft");
			Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
			boolean stunned = arm.stunned();
			FleshMesh.Look look = pale(light, out == ShaftArmEntity.CUT ? 0.6F : stunned ? 0.18F + 0.18F * MathHelper.sin(t * 0.5F) : 0.0F);
			int k = Math.floorMod(arm.getId(), 4);
			int n = 6;
			Vec3d prev = new Vec3d(0.0, sunk + 0.35, 0.0).add(d.multiply(-0.4));
			for (int i = 1; i <= n; i++) {
				double f = (double) i / n;
				Vec3d p = d.multiply(reach * f - 0.4 * (1.0 - f)).add(0.0, sunk + 0.35 + lift * Math.sin(Math.PI * Math.min(1.0, f * 1.1)), 0.0);
				chain(mesh, base, prev, p, 1.7F, k, i == n, false, look);
				prev = p;
			}
			if (stunned) {
				// Lying stunned: glowing bands where to cut, pulsing.
				float a = 0.55F + 0.45F * MathHelper.sin(t * 0.6F);
				for (int i = 1; i <= 4; i++) {
					Vec3d c = d.multiply(reach * i / 5.0).add(0.0, sunk + 0.35, 0.0);
					g.arc(c, 0.62, 0.14, 0.0, Math.PI * 2.0, side, up, 1.0F, 0.75F, 0.3F, a);
				}
				g.line(new Vec3d(0.0, sunk + 1.05, 0.0), d.multiply(reach).add(0.0, sunk + 1.05, 0.0), up, 0.14, 1.0F, 0.6F, 0.2F, a * 0.8F);
			}
		}
	}

	private void grab(ShaftArmEntity arm, ClientWorld world, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		float t = arm.age(delta);
		int out = arm.outcome();
		float since = out == ShaftArmEntity.PENDING ? -1.0F : (float) (world.getTime() - arm.outAt()) + delta;
		Vec3d in = arm.dir();
		Vec3d left = new Vec3d(in.z, 0.0, -in.x);
		Vec3d up = new Vec3d(0.0, 1.0, 0.0);
		Vec3d pos = arm.getLerpedPos(delta);
		Vec3d hand = arm.handAt(t).subtract(arm.getPos()).add(arm.getPos().subtract(pos));
		Glow g = new Glow(provider, matrices);
		// The tell: the reach of the sweep lit on the floor (the arc, and the half-disc faintly), the palm lit.
		if (out == ShaftArmEntity.PENDING && t < ShaftArmEntity.GRAB_TELL + ShaftArmEntity.SWEEP) {
			float k = MathHelper.clamp(t / ShaftArmEntity.GRAB_TELL, 0.0F, 1.0F);
			float beat = 0.5F + 0.5F * MathHelper.sin(t * (0.5F + 1.2F * k));
			double a0 = -Math.toRadians(ShaftArmEntity.SWEEP_ANGLE);
			double a1 = Math.toRadians(ShaftArmEntity.SWEEP_ANGLE);
			Vec3d c = new Vec3d(0.0, 0.04, 0.0);
			double r = ShaftArmEntity.REACH_GRAB + ShaftArmEntity.HAND * 0.5;
			g.arc(c, r, 0.18, a0, a1, in, left, 1.0F, 0.55F, 0.1F, (0.4F + 0.6F * k) * (0.7F + 0.3F * beat));
			for (int i = 0; i < 12; i++) {
				double t0 = a0 + (a1 - a0) * i / 12.0;
				double t1 = a0 + (a1 - a0) * (i + 1) / 12.0;
				Vec3d d0 = in.multiply(Math.cos(t0)).add(left.multiply(Math.sin(t0))).multiply(r);
				Vec3d d1 = in.multiply(Math.cos(t1)).add(left.multiply(Math.sin(t1))).multiply(r);
				g.fade(c.add(d0), c.add(d1), c.add(d1.multiply(0.05)), c.add(d0.multiply(0.05)), 1.0F, 0.4F, 0.08F, 0.16F * k, 0.04F * k);
			}
			if (t < ShaftArmEntity.GRAB_TELL) {
				g.arc(hand.add(in.multiply(0.5)), 0.6 + 0.25 * beat, 0.5, 0.0, Math.PI * 2.0, in.crossProduct(up).normalize(), up, 1.0F, 0.65F, 0.2F, 0.45F + 0.4F * beat);
			}
		}
		boolean gone = out == ShaftArmEntity.CUT && since > ShaftArmEntity.BACK
				|| out == ShaftArmEntity.MISSED && since > ShaftArmEntity.LIE + ShaftArmEntity.BACK;
		if (gone) {
			return;
		}
		double sink = 0.0;
		if (out == ShaftArmEntity.CUT) {
			sink = 1.5 * ease(since / ShaftArmEntity.BACK);
		} else if (out == ShaftArmEntity.MISSED && since > ShaftArmEntity.LIE) {
			sink = 1.5 * ease((since - ShaftArmEntity.LIE) / ShaftArmEntity.BACK);
		} else if (out == ShaftArmEntity.HIT && !arm.holding() && since > 0.0F) {
			sink = 1.5 * ease(since / 14.0F);
		}
		Vec3d foot = new Vec3d(0.0, 0.5 - sink, 0.0).add(pos.subtract(pos));
		Vec3d tip = hand.add(0.0, -sink, 0.0);
		FleshMesh mesh = FleshMesh.get("shaft");
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		boolean stunned = arm.stunned();
		FleshMesh.Look look = pale(light, out == ShaftArmEntity.CUT ? 0.6F : stunned ? 0.18F + 0.18F * MathHelper.sin(t * 0.5F) : 0.0F);
		int k = Math.floorMod(arm.getId(), 4);
		if (t < ShaftArmEntity.GRAB_TELL && out == ShaftArmEntity.PENDING) {
			foot = new Vec3d(0.0, -1.2, 0.0).add(in.multiply(0.6));
		}
		chain(mesh, base, foot, tip, 1.9F, k, true, arm.holding(), look);
		if (stunned) {
			float a = 0.55F + 0.45F * MathHelper.sin(t * 0.6F);
			Vec3d d = tip.subtract(foot);
			for (int i = 1; i <= 4; i++) {
				Vec3d c = foot.add(d.multiply(i / 5.0));
				g.arc(c, 0.7, 0.14, 0.0, Math.PI * 2.0, d.crossProduct(up).normalize(), up, 1.0F, 0.75F, 0.3F, a);
			}
		}
		if (arm.holding()) {
			// Its grip lit: cut here.
			float a = 0.6F + 0.4F * MathHelper.sin(t * 0.7F);
			Vec3d d = tip.subtract(foot).normalize();
			g.arc(tip.subtract(d.multiply(1.2)), 0.75, 0.16, 0.0, Math.PI * 2.0, d.crossProduct(up).normalize(), up, 1.0F, 0.7F, 0.25F, a);
		}
	}

	private void rise(ShaftArmEntity arm, ClientWorld world, float delta, MatrixStack matrices, int light) {
		float now = world.getTime() + delta;
		FleshMesh mesh = FleshMesh.get("shaft");
		int k = Math.floorMod(arm.getId(), 4);
		FleshMesh.Part root = mesh.byName.get("grab_" + k + "_root");
		FleshMesh.Part hand = mesh.byName.get("grab_" + k + "_hand");
		FleshMesh.Part seg = mesh.byName.get("grab_segment");
		if (root == null || hand == null || seg == null) {
			return;
		}
		var top = hand.json.getAsJsonArray("root_top");
		Vector3f rt = new Vector3f(top.get(0).getAsFloat(), 0.0F, top.get(2).getAsFloat());
		float height = Math.max(0.3F, arm.tip() - (float) arm.getY());
		float rootH = ROOT_TOP - ROOT_BASE;
		float baseY = height >= rootH ? 0.0F : height - rootH;
		float reach = Math.max(0.0F, height - rootH);
		// The mesh's frame with the root's top at the column and its foot at baseY.
		Matrix4f model = new Matrix4f(matrices.peek().getPositionMatrix()).translate(-rt.x, baseY - ROOT_BASE, -rt.z);
		FleshMesh.Look look = pale(light, 0.0F);
		look.glowAt = new Vector3f(rt.x, ROOT_BASE - 1.5F, rt.z);
		look.glowRange = 3.0F;
		look.glowR = 0.4F;
		look.glowG = 0.15F;
		look.glowB = 0.05F;
		Matrix4f m = new Matrix4f(model);
		FleshMesh.pose(root.anim, root.pivot, now, m);
		mesh.drawPart(root, m, look, model);
		int n = (int) Math.ceil(reach / SEG);
		if (n > 0) {
			float step = reach / n;
			for (int i = 0; i < n; i++) {
				Matrix4f s = new Matrix4f(model).translate(rt.x, ROOT_TOP + i * step, rt.z).scale(1.0F, step / SEG, 1.0F);
				mesh.drawPart(seg, s, look, model);
			}
		}
		Matrix4f h = new Matrix4f(model).translate(0.0F, reach, 0.0F);
		if (arm.holding()) {
			// It closes on whoever it holds: the hand tips over its wrist.
			h.translate(rt.x, ROOT_TOP, rt.z).rotateX(0.35F).translate(-rt.x, -ROOT_TOP, -rt.z);
		}
		mesh.drawPart(hand, h, look, model);
	}
}
