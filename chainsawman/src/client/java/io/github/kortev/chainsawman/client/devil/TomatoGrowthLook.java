package io.github.kortev.chainsawman.client.devil;

import io.github.kortev.chainsawman.client.FleshMesh;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * How a grown Tomato Devil ({@link io.github.kortev.chainsawman.devil.Growth}) looks worse each step, readable at a glance side by side:
 * more of its eyes open (young, some are still shut), bigger, the whites bloodshot and a raw red rim swelling round each; the skin darker
 * and splitting along wet seams that lengthen; from step 3 dark veins spread out from the oldest seams, and at 4 and 5 they pulse with a
 * slow heartbeat (the skin's red with them) and it drips.
 */
final class TomatoGrowthLook {
	private TomatoGrowthLook() {
	}

	static final Identifier VEINS = Identifier.of("chainsawman", "textures/entity/tomato_veins.png");
	/** How many of its 15 eyes are open at each growth (the biggest open first). */
	private static final int[] OPEN = {9, 10, 11, 13, 14, 15};
	/** The body as an ellipsoid (its own frame), for the seams laid on it. */
	private static final float RX = 1.3F;
	private static final float RY = 1.06F;

	/**
	 * The seams: where on the body (a direction in its own frame), the way they run (radians from level, round the skin), how long, and the
	 * growth they split open at. Mostly up and down, as a tomato's skin cracks; none across the mouth.
	 */
	private static final float[][] SEAMS = {
			{0.95F, 0.05F, 0.3F, 1.35F, 0.62F, 1}, {-0.95F, 0.1F, 0.25F, -1.4F, 0.6F, 1},
			{0.55F, -0.2F, 0.82F, 1.45F, 0.58F, 2}, {-0.55F, -0.2F, 0.82F, -1.5F, 0.58F, 2},
			{0.32F, 0.62F, 0.72F, 1.0F, 0.5F, 3}, {-0.32F, 0.62F, 0.72F, -1.1F, 0.5F, 3}, {0.0F, 0.98F, -0.2F, 0.15F, 0.6F, 3},
			{0.9F, 0.1F, -0.45F, -1.3F, 0.62F, 4}, {-0.9F, 0.05F, -0.45F, 1.25F, 0.62F, 4}, {0.0F, -0.35F, -0.95F, 1.5F, 0.66F, 4},
			{0.45F, 0.85F, -0.3F, 0.8F, 0.5F, 5}, {-0.45F, 0.85F, -0.3F, -0.9F, 0.5F, 5}};

	private static int[] rank;

	/** Whether eye {@code k} is open at growth {@code g}. */
	static boolean open(int k, int g) {
		List<TomatoDevilRenderer.Eye> eyes = TomatoDevilRenderer.eyes();
		if (rank == null || rank.length != eyes.size()) {
			List<Integer> order = new ArrayList<>();
			for (int i = 0; i < eyes.size(); i++) {
				order.add(i);
			}
			// (Biggest first; among equals, the front ones.)
			order.sort((a, b) -> {
				int c = Float.compare(eyes.get(b).r(), eyes.get(a).r());
				return c != 0 ? c : Float.compare(eyes.get(b).n().z, eyes.get(a).n().z);
			});
			int[] r = new int[eyes.size()];
			for (int i = 0; i < order.size(); i++) {
				r[order.get(i)] = i;
			}
			rank = r;
		}
		int n = Math.round(OPEN[MathHelper.clamp(g, 0, 5)] * eyes.size() / 15.0F);
		return k >= rank.length || rank[k] < n;
	}

	/** How much bigger its eyes are drawn. */
	static float eyeSize(int g) {
		return 1.0F + 0.045F * g;
	}

	/** The red rim round each eye: how much bigger than the ball. */
	static float rimSize(int g) {
		return 1.12F + 0.06F * g;
	}

	/** A slow heartbeat (one double thump in 30 ticks): 0 to about 1. */
	static float beat(float now) {
		float ph = (now / 30.0F) % 1.0F;
		float a = (ph - 0.05F) / 0.06F;
		float b = (ph - 0.25F) / 0.07F;
		return (float) (Math.exp(-a * a) + 0.6 * Math.exp(-b * b));
	}

	private static FleshMesh.Look copy(FleshMesh.Look l) {
		FleshMesh.Look c = new FleshMesh.Look();
		c.r = l.r;
		c.g = l.g;
		c.b = l.b;
		c.rim = l.rim;
		c.hurt = l.hurt;
		return c;
	}

	/** The body's skin: darker, deeper red each step; at 4 and 5 it flushes with each heartbeat; its edges catch more of the red rim. */
	static FleshMesh.Look skin(FleshMesh.Look look, int g, float now) {
		FleshMesh.Look s = copy(look);
		float pulse = g >= 4 ? 0.14F * (g - 3) * 0.5F * beat(now) : 0.0F;
		s.r *= (1.0F - 0.1F * g) * (1.0F + pulse);
		s.g *= 1.0F - 0.16F * g;
		s.b *= 1.0F - 0.12F * g;
		s.rim = look.rim + 0.07F * g;
		return s;
	}

	/** The arms: greyer and bruised each step. */
	static FleshMesh.Look arms(FleshMesh.Look look, int g) {
		FleshMesh.Look s = copy(look);
		s.r *= 1.0F - 0.06F * g;
		s.g *= 1.0F - 0.11F * g;
		s.b *= 1.0F - 0.06F * g;
		return s;
	}

	/** The eyeballs' whites go pink, then bloodshot. */
	static void bloodshot(FleshMesh.Look shine, int g) {
		shine.g *= 1.0F - 0.08F * g;
		shine.b *= 1.0F - 0.09F * g;
	}

	/** The irises (the pupil part's paint): from orange-brown to blood red. */
	static FleshMesh.Look iris(FleshMesh.Look look, int g) {
		FleshMesh.Look r = copy(look);
		r.r = look.r * (1.0F + 0.06F * g);
		r.g = look.g * (1.0F - 0.17F * g);
		r.b = look.b * (1.0F - 0.17F * g);
		return r;
	}

	/** The rims' raw red (none at growth 0), throbbing at 4 and 5. */
	static FleshMesh.Look rim(FleshMesh.Look look, int g, float now) {
		if (g <= 0) {
			return null;
		}
		FleshMesh.Look r = copy(look);
		float k = 0.75F + 0.1F * g + (g >= 4 ? 0.15F * beat(now) : 0.0F);
		r.r = look.r * k;
		r.g = look.g * 0.07F;
		r.b = look.b * 0.06F;
		r.rim = 0.1F;
		return r;
	}

	// ---- The seams and the veins (on the ellipsoid of the body, in its frame).

	/** The seams split open at growth {@code g}, then the veins round the older ones. */
	static void seams(int g, float now, MatrixStack matrices, VertexConsumerProvider provider, Matrix4f bodyM, int light) {
		if (g <= 0) {
			return;
		}
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f m = new Matrix4f(entry.getPositionMatrix()).mul(bodyM);
		Matrix3f nm = new Matrix3f(entry.getNormalMatrix()).mul(new Matrix3f(bodyM));
		float bt = beat(now);
		if (g >= 3) {
			// (Cut out, not blended: the body is drawn after the entities, so a blended vein would show the sky through it.)
			VertexConsumer vc = provider.getBuffer(RenderLayer.getEntityCutoutNoCull(VEINS));
			for (float[] s : SEAMS) {
				int age = g - (int) s[5];
				if (age < 2) {
					continue;
				}
				float alpha = MathHelper.clamp(0.45F + 0.18F * (g - 3) + (g >= 4 ? 0.3F * bt : 0.0F), 0.0F, 1.0F);
				float size = (0.95F + 0.18F * (age - 2)) * (g >= 4 ? 1.0F + 0.05F * bt : 1.0F);
				decal(vc, m, nm, s, s[3] + 0.6F, size, size, 1.07F, alpha, light, true);
			}
		}
		VertexConsumer vc = provider.getBuffer(RenderLayer.getEntityCutoutNoCull(TomatoDevilRenderer.GASH));
		for (float[] s : SEAMS) {
			int age = g - (int) s[5];
			if (age < 0) {
				continue;
			}
			float len = s[4] * (1.15F + 0.18F * age);
			float wid = 0.22F + 0.04F * age;
			decal(vc, m, nm, s, s[3], len, wid, 1.09F, 1.0F, light, false);
		}
	}

	/** Where a direction on the body lands on its skin (its own frame), lifted {@code lift} times off it. */
	private static Vector3f onSkin(Vector3f d, float lift) {
		float k = (float) Math.sqrt((d.x / RX) * (d.x / RX) + (d.y / RY) * (d.y / RY) + (d.z / RX) * (d.z / RX));
		return new Vector3f(d).div(k).mul(lift);
	}

	/** One decal of the texture laid over the skin round {@code s}'s direction: {@code len} along {@code angle}, {@code wid} across; a 4x4 grid bent to the body. */
	private static void decal(VertexConsumer vc, Matrix4f m, Matrix3f nm, float[] s, float angle, float len, float wid, float lift, float alpha, int light, boolean shade) {
		Vector3f c = new Vector3f(s[0], s[1], s[2]).normalize();
		Vector3f t = new Vector3f(0, 1, 0).cross(c);
		if (t.lengthSquared() < 1.0E-4F) {
			t.set(1, 0, 0);
		}
		t.normalize();
		Vector3f b = new Vector3f(c).cross(t).normalize();
		Vector3f along = new Vector3f(t).mul(MathHelper.cos(angle)).add(new Vector3f(b).mul(MathHelper.sin(angle)));
		Vector3f across = new Vector3f(c).cross(along).normalize();
		// (In the unit sphere's terms: the body is about 1.2 blocks round, so a block of decal is about 0.8 of a radian.)
		float sa = len / 1.2F;
		float sc = wid / 1.2F;
		int n = 4;
		Vector3f[][] p = new Vector3f[n + 1][n + 1];
		Vector3f[][] nn = new Vector3f[n + 1][n + 1];
		for (int i = 0; i <= n; i++) {
			for (int j = 0; j <= n; j++) {
				float u = i / (float) n - 0.5F;
				float v = j / (float) n - 0.5F;
				Vector3f d = new Vector3f(c).add(new Vector3f(along).mul(u * sa)).add(new Vector3f(across).mul(v * sc));
				Vector3f q = onSkin(d, lift);
				p[i][j] = q;
				nn[i][j] = nm.transform(new Vector3f(q.x / (RX * RX), q.y / (RY * RY), q.z / (RX * RX))).normalize();
			}
		}
		// (Cut out, the veins pulse in brightness: dull between beats, a raw purple-red on them.)
		int a = shade ? 255 : (int) (alpha * 255.0F);
		int k = shade ? (int) (110 + 145 * MathHelper.clamp(alpha, 0.0F, 1.0F)) : 255;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				vert(vc, m, p[i][j], nn[i][j], i / (float) n, 1.0F - j / (float) n, k, a, light);
				vert(vc, m, p[i + 1][j], nn[i + 1][j], (i + 1) / (float) n, 1.0F - j / (float) n, k, a, light);
				vert(vc, m, p[i + 1][j + 1], nn[i + 1][j + 1], (i + 1) / (float) n, 1.0F - (j + 1) / (float) n, k, a, light);
				vert(vc, m, p[i][j + 1], nn[i][j + 1], i / (float) n, 1.0F - (j + 1) / (float) n, k, a, light);
			}
		}
	}

	private static void vert(VertexConsumer vc, Matrix4f m, Vector3f p, Vector3f n, float u, float v, int k, int a, int light) {
		vc.vertex(m, p.x, p.y, p.z).color(k, k, k, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n.x, n.y, n.z);
	}

	// ---- The drips.

	private static final Map<TomatoDevilEntity, Long> LAST_DRIP = new WeakHashMap<>();
	private static final BlockStateParticleEffect DRIP = new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.RED_CONCRETE.getDefaultState());

	/** At 4 and 5, juice drips from its seams and its lips (a few a second; once a tick at most). */
	static void drip(TomatoDevilEntity e, ClientWorld world, int g, Matrix4f bodyM, Vec3d pos, float gs) {
		if (g < 4) {
			return;
		}
		long tick = world.getTime();
		Long last = LAST_DRIP.get(e);
		if (last != null && last == tick) {
			return;
		}
		LAST_DRIP.put(e, tick);
		int tries = g >= 5 ? 2 : 1;
		for (int k = 0; k < tries; k++) {
			if (world.random.nextFloat() > 0.22F) {
				continue;
			}
			Vector3f at;
			if (world.random.nextFloat() < 0.3F) {
				// from the lips, low on its face
				at = onSkin(new Vector3f((world.random.nextFloat() - 0.5F) * 0.3F, -0.55F, 1.0F), 1.05F);
			} else {
				float[] s = SEAMS[world.random.nextInt(SEAMS.length)];
				if (g < (int) s[5]) {
					continue;
				}
				at = onSkin(new Vector3f(s[0], s[1] - 0.15F, s[2]), 1.04F);
			}
			Vector3f w = bodyM.transformPosition(at).mul(gs);
			world.addParticle(DRIP, pos.x + w.x, pos.y + w.y, pos.z + w.z, 0.0, 0.0, 0.0);
		}
	}
}
