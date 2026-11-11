package io.github.kortev.chainsawman.client.devil;

import io.github.kortev.chainsawman.client.FleshMesh;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import java.util.List;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What a grown Tomato Devil grows ({@link io.github.kortev.chainsawman.devil.Growth}): a new shape each step, each one showing in its
 * outline from 10 blocks and creepier than the last, all drawn from the Tomato's own meshes.
 * <ol>
 * <li>two eyes push up out of its top on short stalks; its top splits (a seam, {@link TomatoGrowthLook});</li>
 * <li>its mouth is wider and taller, a second row of lips and teeth round the first ({@link TomatoDevilRenderer}'s lips); a wet lobe swells
 * out of its right side;</li>
 * <li>a ninth and a tenth arm push out of its flanks, small, twitching, grasping at nothing;</li>
 * <li>they are full size and walk with the rest; two more eyes on stalks; the lobe has split open, eyes inside it;</li>
 * <li>lopsided and overgrown: a second lobe on its left with a mouth of its own, open; vine stems trailing from its top; small eyes crowding
 * in between the others; six eyes on stalks.</li>
 * </ol>
 */
final class TomatoGrowthShape {
	private TomatoGrowthShape() {
	}

	/** The eyes on stalks: where they root (a direction on the body), and the growth they come at. */
	private static final float[][] STALKS = {
			{0.38F, 0.9F, 0.3F, 1}, {-0.42F, 0.88F, 0.18F, 1},
			{0.72F, 0.62F, -0.32F, 4}, {-0.66F, 0.7F, -0.38F, 4},
			{0.08F, 0.95F, -0.5F, 5}, {0.6F, 0.78F, 0.45F, 5}};
	/** The small eyes crowding in at 5: a direction on the body, and the eye's radius. */
	private static final float[][] CROWD = {
			{0.98F, 0.28F, 0.12F, 0.15F}, {-0.98F, 0.22F, 0.02F, 0.16F}, {0.52F, -0.5F, 0.7F, 0.14F}, {-0.5F, -0.52F, 0.7F, 0.15F},
			{0.25F, 0.72F, -0.65F, 0.16F}, {0.88F, -0.12F, -0.5F, 0.15F}, {-0.2F, 0.4F, 0.92F, 0.13F}, {0.95F, 0.6F, 0.35F, 0.13F}};

	/** The looks for eyes drawn here. */
	static final class Eyes {
		FleshMesh.Look shine;
		FleshMesh.Look iris;
		FleshMesh.Look rim;
		FleshMesh.Look skin;
		int g;
	}

	static void draw(TomatoDevilEntity e, TomatoBody b, ClientWorld world, FleshMesh body, FleshMesh parts, Matrix4f base, Matrix4f bodyM, Matrix4f bodyView,
			FleshMesh.Look look, FleshMesh.Look skin, FleshMesh.Look arms, int g, float gs, float now, Vec3d pos, float delta, boolean dazed) {
		if (g <= 0) {
			return;
		}
		// What its eyes look at: the nearest player's eyes, in the body's frame.
		Vector3f target = null;
		PlayerEntity near = world.getClosestPlayer(e, 32.0);
		if (near != null && !dazed) {
			Vec3d ep = near.getLerpedPos(delta).add(0.0, near.getStandingEyeHeight(), 0.0).subtract(pos).multiply(1.0 / gs);
			target = new Matrix4f(bodyM).invert().transformPosition(new Vector3f((float) ep.x, (float) ep.y, (float) ep.z));
		}
		Eyes L = new Eyes();
		L.shine = new FleshMesh.Look();
		L.shine.r = Math.max(look.r, 0.62F);
		L.shine.g = Math.max(look.g, 0.6F);
		L.shine.b = Math.max(look.b, 0.56F);
		L.shine.rim = look.rim;
		L.shine.hurt = look.hurt;
		L.iris = TomatoGrowthLook.iris(look, g);
		L.rim = TomatoGrowthLook.rim(look, g, now);
		L.skin = skin;
		L.g = g;
		float beat = TomatoGrowthLook.beat(now);
		int id = e.getId();

		// 1, 4, 5: eyes on stalks.
		for (int k = 0; k < STALKS.length; k++) {
			float[] s = STALKS[k];
			if (g < (int) s[3]) {
				continue;
			}
			int age = g - (int) s[3];
			Vector3f dir = new Vector3f(s[0], s[1], s[2]).normalize();
			Vector3f root = TomatoGrowthLook.onSkin(dir, 0.94F);
			float len = 0.55F + 0.12F * age + 0.05F * (k % 2);
			Vector3f sway = new Vector3f(MathHelper.sin(now * 0.07F + k * 1.9F + id), 0.0F, MathHelper.cos(now * 0.055F + k * 2.3F)).mul(0.18F);
			Vector3f way = new Vector3f(dir).add(0.0F, 0.9F, 0.0F).normalize().add(sway).normalize();
			Vector3f mid = new Vector3f(root).add(new Vector3f(way).mul(len * 0.5F)).add(new Vector3f(sway).mul(0.3F));
			Vector3f tip = new Vector3f(root).add(new Vector3f(way).mul(len));
			FleshMesh.Part fore = parts.byName.get("fore");
			bone(parts, fore, TomatoDevilRenderer.FORE, base, bodyM.transformPosition(new Vector3f(root)), bodyM.transformPosition(new Vector3f(mid)), 0.42F, arms);
			bone(parts, fore, TomatoDevilRenderer.FORE, base, bodyM.transformPosition(new Vector3f(mid)), bodyM.transformPosition(new Vector3f(tip)), 0.36F, arms);
			eye(parts, base, bodyView, tip, way, 0.21F + 0.02F * age, target, L, now, k * 7 + id, false);
		}

		// 5: small eyes crowding in.
		if (g >= 5) {
			for (int k = 0; k < CROWD.length; k++) {
				float[] c = CROWD[k];
				Vector3f n = new Vector3f(c[0], c[1], c[2]).normalize();
				eye(parts, base, bodyView, TomatoGrowthLook.onSkin(n, 0.97F), n, c[3], target, L, now, k * 13 + id + 5, false);
			}
		}

		// 2+: the wet lobe on its right (its mouth turned down into it, out of sight); at 4 split open, eyes inside.
		if (g >= 2) {
			float sc = (g == 2 ? 0.42F : g == 3 ? 0.5F : 0.56F) * (1.0F + (g >= 4 ? 0.035F * beat : 0.0F));
			Matrix4f local = new Matrix4f().translate(1.12F, -0.18F, -0.12F).rotateY(1.75F).rotateX(1.25F).scale(sc);
			lobe(body, parts, base, bodyView, local, skin, L, target, g >= 4 ? (g >= 5 ? 15 : 7) : 0, -1.0F, now, id);
		}
		// 5: the second lobe on its left, its own mouth open and breathing.
		if (g >= 5) {
			float sc = 0.6F * (1.0F + 0.03F * beat);
			Matrix4f local = new Matrix4f().translate(-1.18F, 0.12F, 0.18F).rotateY(-1.25F).rotateX(-0.15F).scale(sc);
			float open = 0.45F + 0.25F * (0.5F + 0.5F * MathHelper.sin(now * 0.11F + id));
			lobe(body, parts, base, bodyView, local, skin, L, target, 9, open, now, id + 3);
		}

		// 3: two small arms push out of its flanks, twitching; 4+: full size, walking with the rest.
		if (g == 3) {
			for (int side = -1; side <= 1; side += 2) {
				Vector3f sh = TomatoGrowthLook.onSkin(new Vector3f(side * 0.92F, 0.3F, 0.38F), 0.92F);
				float tw = MathHelper.sin(now * 0.9F + side) * MathHelper.sin(now * 0.23F + side * 2.0F);
				Vector3f wr = new Vector3f(sh).add(side * 0.85F, 0.25F + 0.25F * tw, 0.45F + 0.2F * MathHelper.sin(now * 0.31F + side));
				Vector3f pole = new Vector3f(side, 1.0F, -0.3F);
				Vector3f fing = new Vector3f(side * 0.6F, 0.4F, 0.8F).normalize();
				arm(parts, base, bodyM.transformPosition(sh), bodyM.transformPosition(wr), bodyM.transformDirection(pole), bodyM.transformDirection(fing),
						bodyM.transformDirection(new Vector3f(0, 1, 0)), 0.55F, 0.5F + 0.45F * Math.abs(tw), side > 0, arms);
			}
		} else if (g >= 4) {
			int[][] pairs = {{1, 2, 1}, {5, 6, -1}};
			for (int[] pr : pairs) {
				int side = pr[2];
				float az = side > 0 ? 90.0F : 270.0F;
				float r = az * MathHelper.RADIANS_PER_DEGREE;
				Vector3f o = new Vector3f(MathHelper.sin(r), 0.0F, MathHelper.cos(r));
				Vector3f sh = bodyM.transformPosition(new Vector3f(o.x * TomatoDevilRenderer.SHOULDER_R, TomatoDevilRenderer.SHOULDER_Y, o.z * TomatoDevilRenderer.SHOULDER_R));
				double[] h0 = b.walkHand(pr[0], now);
				double[] h1 = b.walkHand(pr[1], now);
				// Between its neighbours' hands and further out: it steps when they do.
				double hx = (h0[0] + h1[0]) * 0.5;
				double hy = Math.max(h0[1], h1[1]);
				double hz = (h0[2] + h1[2]) * 0.5;
				Vector3f outW = new Vector3f((float) (hx - pos.x), 0.0F, (float) (hz - pos.z));
				float l = outW.length();
				if (l > 1.0E-3F) {
					outW.mul(0.7F / l);
				}
				Vector3f wr = new Vector3f((float) ((hx - pos.x) / gs) + outW.x, (float) ((hy - pos.y) / gs + TomatoDevilRenderer.WRIST_UP), (float) ((hz - pos.z) / gs) + outW.z);
				Vector3f flat = new Vector3f(wr.x - sh.x, 0.0F, wr.z - sh.z);
				if (flat.lengthSquared() < 1.0E-4F) {
					flat.set(o);
				}
				flat.normalize();
				arm(parts, base, sh, wr, new Vector3f(flat).add(0.0F, 1.1F, 0.0F), flat, new Vector3f(0, 1, 0), 1.0F, 0.1F, side > 0, arms);
			}
		}

		// 5: vine stems trailing from its top, swaying, over its sides.
		if (g >= 5) {
			FleshMesh.Look vine = new FleshMesh.Look();
			vine.r = look.r * 0.42F;
			vine.g = look.g * 0.62F;
			vine.b = look.b * 0.3F;
			vine.rim = 0.2F;
			FleshMesh.Part upper = parts.byName.get("upper");
			for (int k = 0; k < 5; k++) {
				float a = k * MathHelper.TAU / 5.0F + 0.4F;
				Vector3f out = new Vector3f(MathHelper.cos(a), 0.0F, MathHelper.sin(a));
				Vector3f at = TomatoGrowthLook.onSkin(new Vector3f(out.x * 0.3F, 1.0F, out.z * 0.3F), 0.96F);
				Vector3f prev = bodyM.transformPosition(new Vector3f(at));
				int segs = 7;
				for (int j = 0; j < segs; j++) {
					float u = j / (float) segs;
					Vector3f d = new Vector3f(out).mul(0.55F + 0.5F * u).add(0.0F, 1.0F - 1.9F * u, 0.0F)
							.add(0.22F * MathHelper.sin(now * 0.05F + k * 1.7F + j * 0.6F), 0.0F, 0.22F * MathHelper.cos(now * 0.043F + k * 2.1F + j * 0.5F)).normalize();
					at.add(d.mul(0.34F));
					Vector3f next = bodyM.transformPosition(new Vector3f(at));
					bone(parts, upper, TomatoDevilRenderer.UPPER, base, prev, next, 0.3F - 0.025F * j, vine);
					prev = next;
				}
			}
		}
	}

	/** One of its eyes drawn here: {@code c} and {@code n} in the frame {@code frameView} draws, looking at {@code target} (that frame) or about. */
	static void eye(FleshMesh parts, Matrix4f base, Matrix4f frameView, Vector3f c, Vector3f n, float r, Vector3f target, Eyes L, float now, int seed, boolean rimmed) {
		FleshMesh.Part eye = parts.byName.get("eye");
		FleshMesh.Part lid = parts.byName.get("lid");
		FleshMesh.Part pupil = parts.byName.get("pupil");
		if (eye == null) {
			return;
		}
		n = new Vector3f(n).normalize();
		Vector3f dir = target != null ? new Vector3f(target).sub(c).normalize()
				: new Vector3f(n).add(0.35F * MathHelper.sin(now * 0.05F + seed), 0.3F * MathHelper.cos(now * 0.041F + seed * 1.3F), 0.0F).normalize();
		float cos = dir.dot(n);
		if (cos < 0.5F) {
			Vector3f perp = new Vector3f(dir).sub(new Vector3f(n).mul(cos));
			if (perp.lengthSquared() < 1.0E-6F) {
				perp.set(0, 1, 0);
			}
			perp.normalize();
			dir = new Vector3f(n).mul(0.5F).add(perp.mul(0.866F)).normalize();
		}
		float s = r / TomatoDevilRenderer.EYE_R;
		Matrix4f m = new Matrix4f(frameView).translate(c).mul(basis(dir)).scale(s);
		parts.drawPart(eye, m, L.shine, base);
		if (pupil != null) {
			parts.drawPart(pupil, new Matrix4f(m).scale(0.55F, 0.55F, 1.0F), L.iris, base);
		}
		if (lid != null) {
			Matrix4f sock = new Matrix4f(frameView).translate(c).mul(basis(n)).scale(s);
			if (L.rim != null && rimmed) {
				float rs = TomatoGrowthLook.rimSize(L.g);
				Matrix4f rm = new Matrix4f(sock).translate(0.0F, 0.0F, -0.45F * TomatoDevilRenderer.EYE_R).scale(rs, rs, 0.8F);
				parts.drawPart(lid, rm, L.rim, base);
				parts.drawPart(lid, new Matrix4f(rm).rotateZ(MathHelper.PI), L.rim, base);
			}
			// a blink now and then, out of step with the rest
			float bl = MathHelper.sin(now * 0.037F + seed * 2.7F);
			float a = bl > 0.985F ? 0.0F : TomatoDevilRenderer.LID_OPEN;
			parts.drawPart(lid, new Matrix4f(sock).rotateX(a), L.skin, base);
			parts.drawPart(lid, new Matrix4f(sock).rotateZ(MathHelper.PI).rotateX(a), L.skin, base);
		}
	}

	/** A lobe: the body's own flesh again, small, at {@code local} in the body's frame; {@code eyes} of its eye places open; its lips, open {@code open} (none if negative). */
	private static void lobe(FleshMesh body, FleshMesh parts, Matrix4f base, Matrix4f bodyView, Matrix4f local, FleshMesh.Look skin, Eyes L, Vector3f target, int eyes,
			float open, float now, int seed) {
		Matrix4f m = new Matrix4f(bodyView).mul(local);
		body.drawPart(null, m, skin, base);
		if (open >= 0.0F) {
			float a = 1.25F * open;
			for (String name : new String[] {"lip_l", "lip_r"}) {
				FleshMesh.Part part = body.byName.get(name);
				if (part != null) {
					float sgn = name.equals("lip_l") ? -1.0F : 1.0F;
					body.drawPart(part, new Matrix4f(m).translate(part.pivot).rotateY(sgn * a).translate(-part.pivot.x, -part.pivot.y, -part.pivot.z), skin, base);
				}
			}
		}
		if (eyes > 0) {
			Vector3f t = target == null ? null : new Matrix4f(local).invert().transformPosition(new Vector3f(target));
			List<TomatoDevilRenderer.Eye> list = TomatoDevilRenderer.eyes();
			for (int k = 0; k < Math.min(eyes, list.size()); k++) {
				TomatoDevilRenderer.Eye ey = list.get(k);
				eye(parts, base, m, ey.c(), ey.n(), ey.r(), t, L, now, seed * 31 + k, true);
			}
		}
	}

	/** A frame whose +Z is {@code z}. */
	private static Matrix4f basis(Vector3f z) {
		Vector3f x = new Vector3f(0, 1, 0).cross(z);
		if (x.lengthSquared() < 1.0E-4F) {
			x.set(1, 0, 0);
		}
		x.normalize();
		Vector3f y = new Vector3f(z).cross(x).normalize();
		return new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1);
	}

	/** One bone ({@code part}, {@code partLen} long along its +Y) stretched from {@code a} to {@code b} (the entity's frame), {@code thick} times as thick as an arm's. */
	private static void bone(FleshMesh parts, FleshMesh.Part part, float partLen, Matrix4f base, Vector3f a, Vector3f b, float thick, FleshMesh.Look look) {
		if (part == null) {
			return;
		}
		Vector3f y = new Vector3f(b).sub(a);
		float len = y.length();
		if (len < 1.0E-4F) {
			return;
		}
		y.div(len);
		Vector3f z = Math.abs(y.y) < 0.9F ? new Vector3f(0, 1, 0).cross(y) : new Vector3f(1, 0, 0).cross(y);
		z.normalize();
		Vector3f x = new Vector3f(y).cross(z).normalize();
		float t = thick * TomatoDevilRenderer.ARM_THICK;
		Matrix4f m = new Matrix4f(base).translate(a).mul(new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1)).scale(t, len / partLen, t);
		parts.drawPart(part, m, look, base);
	}

	/** An extra arm: shoulder to wrist (the entity's frame) by a two-bone solve, the elbow toward {@code pole}; the hand's fingers along {@code fingers}. */
	private static void arm(FleshMesh parts, Matrix4f base, Vector3f shoulder, Vector3f wrist, Vector3f pole, Vector3f fingers, Vector3f back, float sc, float curl,
			boolean right, FleshMesh.Look look) {
		float upper = TomatoDevilRenderer.UPPER * sc;
		float fore = TomatoDevilRenderer.FORE * sc;
		Vector3f d = new Vector3f(wrist).sub(shoulder);
		float len = d.length();
		float reach = (upper + fore) * 0.995F;
		if (len > reach) {
			d.mul(reach / len);
			wrist = new Vector3f(shoulder).add(d);
			len = reach;
		}
		len = Math.max(len, 0.2F * sc);
		Vector3f u = new Vector3f(d).normalize();
		float cosA = (upper * upper + len * len - fore * fore) / (2.0F * upper * len);
		float a = (float) Math.acos(MathHelper.clamp(cosA, -1.0F, 1.0F));
		Vector3f pr = new Vector3f(pole).sub(new Vector3f(u).mul(pole.dot(u)));
		if (pr.lengthSquared() < 1.0E-5F) {
			pr.set(0, 1, 0);
		}
		pr.normalize();
		Vector3f elbow = new Vector3f(shoulder).add(new Vector3f(u).mul(MathHelper.cos(a) * upper)).add(new Vector3f(pr).mul(MathHelper.sin(a) * upper));
		bone(parts, parts.byName.get("upper"), TomatoDevilRenderer.UPPER, base, shoulder, elbow, sc, look);
		bone(parts, parts.byName.get("fore"), TomatoDevilRenderer.FORE, base, elbow, wrist, sc, look);
		Vector3f z = new Vector3f(fingers).sub(new Vector3f(back).mul(fingers.dot(back)));
		if (z.lengthSquared() < 1.0E-5F) {
			z.set(1, 0, 0);
		}
		z.normalize();
		Vector3f y = new Vector3f(back).normalize();
		Vector3f x = new Vector3f(y).cross(z).normalize();
		y = new Vector3f(z).cross(x).normalize();
		Matrix4f hand = new Matrix4f(base).translate(wrist).mul(new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1))
				.scale(TomatoDevilRenderer.HAND_SCALE * sc);
		FleshMesh.Part hp = parts.byName.get(right ? "hand_r" : "hand_l");
		if (hp == null) {
			return;
		}
		parts.drawPart(hp, hand, look, base);
		for (int j = 0; j < 4; j++) {
			FleshMesh.Part fp = parts.byName.get((right ? "finger_r" : "finger_l") + j);
			if (fp != null) {
				parts.drawPart(fp, new Matrix4f(hand).translate(fp.pivot).rotateX(curl + 0.08F * j).translate(-fp.pivot.x, -fp.pivot.y, -fp.pivot.z), look, base);
			}
		}
	}
}
