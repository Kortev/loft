package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The form as built in Blender (tools/chainsaw_model.py --game): its parts, each following one bone of the player
 * model, read from assets/chainsawman/meshes/form.csm and drawn quad by quad into an entity layer, so it is lit and
 * fogged like the rest of the player.
 */
public final class FormMesh {
	/** A part: the bone it follows (head, body...) and its quads, four corners each, in pixels from the bone's pivot. */
	public static final class Part {
		public final String name;
		public final String bone;
		/** Which frame of its chain this is (a part named NAME_chainK is frame K of NAME's chain), or -1. */
		public final int chainFrame;
		/** For a saw (a bar or its chain), where it grows out from and the way it runs, in pixels from the bone's pivot. */
		@Nullable
		public final Vector3f growRoot;
		@Nullable
		public final Vector3f growAxis;
		final float[] pos;
		final float[] uv;
		final byte[] normal;
		final boolean[] glow;
		@Nullable
		private List<Vector3f> glowCentres;

		Part(String name, String bone, @Nullable Vector3f growRoot, @Nullable Vector3f growAxis, int vertices) {
			int chain = name.lastIndexOf("_chain");
			this.chainFrame = chain >= 0 ? Integer.parseInt(name.substring(chain + "_chain".length())) : -1;
			this.name = name;
			this.bone = bone;
			this.growRoot = growRoot;
			this.growAxis = growAxis;
			this.pos = new float[vertices * 3];
			this.uv = new float[vertices * 2];
			this.normal = new byte[vertices * 3];
			this.glow = new boolean[vertices];
		}

		/** For a saw, how far it runs out from its root along its axis (pixels). */
		public float reach() {
			if (growRoot == null) {
				return 0.0F;
			}
			float reach = 0.0F;
			for (int i = 0; i < glow.length; i++) {
				reach = Math.max(reach, (pos[i * 3] - growRoot.x) * growAxis.x + (pos[i * 3 + 1] - growRoot.y) * growAxis.y
						+ (pos[i * 3 + 2] - growRoot.z) * growAxis.z);
			}
			return reach;
		}

		/** Where its glowing vertices are on each side, left and right (pixels, from the bone's pivot): the form's eyes. */
		public List<Vector3f> glowCentres() {
			if (glowCentres == null) {
				List<Vector3f> centres = new ArrayList<>();
				for (int side = -1; side <= 1; side += 2) {
					Vector3f sum = new Vector3f();
					int n = 0;
					for (int i = 0; i < glow.length; i++) {
						if (glow[i] && Math.signum(pos[i * 3]) == side) {
							sum.add(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]);
							n++;
						}
					}
					if (n > 0) {
						centres.add(sum.div(n));
					}
				}
				glowCentres = centres;
			}
			return glowCentres;
		}

		/** Draws the part with the stack already moved to its bone; the eye at full light. */
		public void draw(MatrixStack.Entry entry, VertexConsumer out, int light, int overlay) {
			draw(entry, out, light, overlay, 1.0F);
		}

		/** Draws the part, the eye lit {@code eye} of the way (0 dark, still in its own light; 1 burning). */
		public void draw(MatrixStack.Entry entry, VertexConsumer out, int light, int overlay, float eye) {
			draw(entry, out, light, overlay, eye, 0xFFFFFFFF);
		}

		/** Draws the part, tinted {@code tint} (drenched in blood, say), the eye lit {@code eye} of the way. */
		public void draw(MatrixStack.Entry entry, VertexConsumer out, int light, int overlay, float eye, int tint) {
			Matrix4f m = entry.getPositionMatrix();
			Vector3f p = new Vector3f();
			Vector3f n = new Vector3f();
			int c = (int) (255 * (0.2F + 0.8F * eye));
			int lit = 0xFF000000 | c << 16 | c << 8 | c;
			int count = glow.length;
			for (int i = 0; i < count; i++) {
				m.transformPosition(pos[i * 3] / 16.0F, pos[i * 3 + 1] / 16.0F, pos[i * 3 + 2] / 16.0F, p);
				entry.transformNormal(normal[i * 3] / 127.0F, normal[i * 3 + 1] / 127.0F, normal[i * 3 + 2] / 127.0F, n);
				out.vertex(p.x, p.y, p.z, glow[i] ? lit : tint, uv[i * 2], uv[i * 2 + 1], overlay,
						glow[i] ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light, n.x, n.y, n.z);
			}
		}
	}

	public final Map<String, Part> parts;
	/** How many frames each chain is built in (shown in turn to run it round). */
	public final int chainFrames;

	/** Where in the texture the cord's colour is (the empty part named rope points at it), for the cord the game draws. */
	public float[] ropeUv() {
		Part rope = parts.get("rope");
		return rope != null ? new float[] {rope.uv[0], rope.uv[1]} : new float[] {0.0F, 0.0F};
	}

	private FormMesh(Map<String, Part> parts) {
		this.parts = parts;
		int frames = 1;
		for (Part part : parts.values()) {
			frames = Math.max(frames, part.chainFrame + 1);
		}
		this.chainFrames = frames;
	}

	private static FormMesh loaded;
	private static boolean failed;

	/** The form's mesh, loaded on first use; null if it could not be read. */
	@Nullable
	public static FormMesh get() {
		if (loaded == null && !failed) {
			try {
				loaded = load();
			} catch (IOException | RuntimeException e) {
				failed = true;
				ChainsawMan.LOGGER.error("Could not load the form's mesh", e);
			}
		}
		return loaded;
	}

	/** Forgets the mesh, to be read again (after resources reload). */
	public static void reset() {
		loaded = null;
		failed = false;
	}

	private static FormMesh load() throws IOException {
		Resource resource = MinecraftClient.getInstance().getResourceManager().getResource(ChainsawMan.id("meshes/form.csm"))
				.orElseThrow(() -> new IOException("missing meshes/form.csm"));
		byte[] bytes;
		try (InputStream in = resource.getInputStream()) {
			bytes = in.readAllBytes();
		}
		ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		if (data.get() != 'C' || data.get() != 'S' || data.get() != 'M' || data.get() != '2') {
			throw new IOException("bad header in form.csm");
		}
		int count = data.getInt();
		Map<String, Part> parts = new LinkedHashMap<>();
		for (int k = 0; k < count; k++) {
			String name = string(data);
			String bone = string(data);
			Vector3f root = null;
			Vector3f axis = null;
			if (data.get() != 0) {
				root = new Vector3f(data.getFloat(), data.getFloat(), data.getFloat());
				axis = new Vector3f(data.getFloat(), data.getFloat(), data.getFloat());
			}
			int quads = data.getInt();
			Part part = new Part(name, bone, root, axis, quads * 4);
			for (int i = 0; i < quads * 4; i++) {
				part.pos[i * 3] = data.getFloat();
				part.pos[i * 3 + 1] = data.getFloat();
				part.pos[i * 3 + 2] = data.getFloat();
				part.uv[i * 2] = data.getFloat();
				part.uv[i * 2 + 1] = data.getFloat();
				part.normal[i * 3] = data.get();
				part.normal[i * 3 + 1] = data.get();
				part.normal[i * 3 + 2] = data.get();
				part.glow[i] = (data.get() & 1) != 0;
			}
			parts.put(name, part);
		}
		return new FormMesh(parts);
	}

	private static String string(ByteBuffer data) {
		byte[] b = new byte[data.getShort()];
		data.get(b);
		return new String(b, StandardCharsets.UTF_8);
	}
}
