package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
		public final String bone;
		final float[] pos;
		final float[] uv;
		final byte[] normal;
		final boolean[] glow;

		Part(String bone, int vertices) {
			this.bone = bone;
			this.pos = new float[vertices * 3];
			this.uv = new float[vertices * 2];
			this.normal = new byte[vertices * 3];
			this.glow = new boolean[vertices];
		}

		/** Draws the part with the stack already moved to its bone; the eye at full light. */
		public void draw(MatrixStack.Entry entry, VertexConsumer out, int light, int overlay) {
			Matrix4f m = entry.getPositionMatrix();
			Vector3f p = new Vector3f();
			Vector3f n = new Vector3f();
			int count = glow.length;
			for (int i = 0; i < count; i++) {
				m.transformPosition(pos[i * 3] / 16.0F, pos[i * 3 + 1] / 16.0F, pos[i * 3 + 2] / 16.0F, p);
				entry.transformNormal(normal[i * 3] / 127.0F, normal[i * 3 + 1] / 127.0F, normal[i * 3 + 2] / 127.0F, n);
				out.vertex(p.x, p.y, p.z, 0xFFFFFFFF, uv[i * 2], uv[i * 2 + 1], overlay,
						glow[i] ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light, n.x, n.y, n.z);
			}
		}
	}

	public final Map<String, Part> parts;

	private FormMesh(Map<String, Part> parts) {
		this.parts = parts;
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
		if (data.get() != 'C' || data.get() != 'S' || data.get() != 'M' || data.get() != '1') {
			throw new IOException("bad header in form.csm");
		}
		int count = data.getInt();
		Map<String, Part> parts = new LinkedHashMap<>();
		for (int k = 0; k < count; k++) {
			String name = string(data);
			String bone = string(data);
			int quads = data.getInt();
			Part part = new Part(bone, quads * 4);
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
