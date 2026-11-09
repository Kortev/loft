package io.github.kortev.chainsawman.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * More teeth on the saws as the heart levels up: a row of extra steel spikes standing out of both edges of each bar, between the chain's
 * own cutters (level 2: a spike every 1.8 pixels, 1.6 long; level 3: every 1.3, 2.4 long, so the edge is a row of fangs). They are cut from
 * the bar's own shape (its width and thickness, read from the mesh) and the chain's own steel swatch, in the bar's space, so they grow with the
 * bar through the transformation and need no change to the model.
 */
final class SawTeeth {
	/** What a bar is: where its width and thickness run, how wide and thick it is, how far it reaches. */
	private record Bar(Vector3f width, Vector3f thick, float halfWidth, float halfThick, float reach, float u, float v) {
	}

	private static final Map<String, Bar> BARS = new HashMap<>();

	private SawTeeth() {
	}

	static void forget() {
		BARS.clear();
	}

	/** Draws the teeth of {@code level} (2 or 3) on {@code bar} with the stack as the bar itself is drawn (its bone, grown). */
	static void draw(FormMesh mesh, FormMesh.Part bar, int level, MatrixStack.Entry entry, VertexConsumer out, int light, int overlay) {
		if (level < 2 || bar.growRoot == null || bar.growAxis == null) {
			return;
		}
		Bar b = BARS.computeIfAbsent(bar.name, k -> measure(mesh, bar));
		float step = level >= 3 ? 1.3F : 1.8F;
		float tall = level >= 3 ? 2.4F : 1.6F;
		Vector3f a = new Vector3f(bar.growAxis).normalize();
		Matrix4f m = entry.getPositionMatrix();
		Vector3f[] corner = new Vector3f[4];
		for (int i = 0; i < 4; i++) {
			corner[i] = new Vector3f();
		}
		Vector3f apex = new Vector3f();
		int n = 0;
		for (float t = 1.2F; t < b.reach - 0.4F; t += step, n++) {
			for (int side = -1; side <= 1; side += 2) {
				// A little longer on every other one, so the row is not a comb.
				float h = tall * (n % 2 == 0 ? 1.0F : 0.7F);
				Vector3f edge = new Vector3f(bar.growRoot).fma(t, a).fma(side * b.halfWidth, b.width);
				float half = 0.5F;
				float th = Math.max(0.4F, b.halfThick * 0.8F);
				corner[0].set(edge).fma(-half, a).fma(-th, b.thick);
				corner[1].set(edge).fma(half, a).fma(-th, b.thick);
				corner[2].set(edge).fma(half, a).fma(th, b.thick);
				corner[3].set(edge).fma(-half, a).fma(th, b.thick);
				apex.set(edge).fma(side * h, b.width).fma(0.3F, a);
				for (int f = 0; f < 4; f++) {
					Vector3f p0 = corner[f];
					Vector3f p1 = corner[(f + 1) % 4];
					Vector3f nrm = new Vector3f(p1).sub(p0).cross(new Vector3f(apex).sub(p0)).normalize();
					vertex(m, entry, out, p0, nrm, b, light, overlay);
					vertex(m, entry, out, p1, nrm, b, light, overlay);
					vertex(m, entry, out, apex, nrm, b, light, overlay);
					vertex(m, entry, out, apex, nrm, b, light, overlay);
				}
			}
		}
	}

	private static void vertex(Matrix4f m, MatrixStack.Entry entry, VertexConsumer out, Vector3f p, Vector3f nrm, Bar b, int light, int overlay) {
		Vector3f w = m.transformPosition(p.x / 16.0F, p.y / 16.0F, p.z / 16.0F, new Vector3f());
		Vector3f n = entry.transformNormal(nrm.x, nrm.y, nrm.z, new Vector3f());
		out.vertex(w.x, w.y, w.z, 0xFFFFFFFF, b.u, b.v, overlay, light, n.x, n.y, n.z);
	}

	/** Reads the bar's shape from its mesh: the longer of its two cross-section axes is its width. */
	private static Bar measure(FormMesh mesh, FormMesh.Part bar) {
		Vector3f a = new Vector3f(bar.growAxis).normalize();
		float[] extent = new float[3];
		float reach = 0.0F;
		int count = bar.glow.length;
		for (int i = 0; i < count; i++) {
			Vector3f d = new Vector3f(bar.pos[i * 3] - bar.growRoot.x, bar.pos[i * 3 + 1] - bar.growRoot.y, bar.pos[i * 3 + 2] - bar.growRoot.z);
			float t = d.dot(a);
			reach = Math.max(reach, t);
			d.fma(-t, a);
			extent[0] = Math.max(extent[0], Math.abs(d.x));
			extent[1] = Math.max(extent[1], Math.abs(d.y));
			extent[2] = Math.max(extent[2], Math.abs(d.z));
		}
		// The axis the bar runs along is not part of its cross-section.
		int along = Math.abs(a.x) >= Math.abs(a.y) && Math.abs(a.x) >= Math.abs(a.z) ? 0 : Math.abs(a.y) >= Math.abs(a.z) ? 1 : 2;
		extent[along] = -1.0F;
		int wide = -1;
		int thin = -1;
		for (int i = 0; i < 3; i++) {
			if (i == along) {
				continue;
			}
			if (wide < 0 || extent[i] > extent[wide]) {
				thin = wide;
				wide = i;
			} else {
				thin = i;
			}
		}
		Vector3f width = axis(wide);
		Vector3f thick = axis(thin);
		String prefix = bar.name.substring(0, bar.name.length() - "_bar".length());
		FormMesh.Part chain = mesh.parts.get(prefix + "_chain0");
		float u = chain != null && chain.glow.length > 0 ? chain.uvAt(0)[0] : bar.uvAt(0)[0];
		float v = chain != null && chain.glow.length > 0 ? chain.uvAt(0)[1] : bar.uvAt(0)[1];
		return new Bar(width, thick, Math.max(0.6F, extent[wide]), Math.max(0.3F, extent[thin]), reach, u, v);
	}

	private static Vector3f axis(int i) {
		return new Vector3f(i == 0 ? 1.0F : 0.0F, i == 1 ? 1.0F : 0.0F, i == 2 ? 1.0F : 0.0F);
	}
}
