package io.github.kortev.chitty.client;

import io.github.kortev.chitty.Chitty;
import io.github.kortev.shootingstar.ShootingStar;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A vehicle as built in Blender (tools/chitty_model.py --game, tools/airship_model.py --game): the body in one piece
 * and every part that moves on its own pivot, read from assets/shootingstar/meshes/NAME.cbm and drawn quad by quad into
 * an entity layer, so it is lit and fogged like everything else. Coordinates are blocks with the vehicle at yaw 0: x to
 * its left, y up, z forward.
 */
public final class ChittyMesh {
	/**
	 * A part: where it hangs and how it rests, four numbers for its animation (a fan panel's open angle, dihedral,
	 * folded angle and how far it draws in folded; a mast's folded turn as a quaternion), and its quads (four corners
	 * each), some of which glow and some polished metal, shone as it is looked at (ChittyShine).
	 */
	public static final class Part {
		public final String name;
		public final Vector3f pivot;
		public final Quaternionf rest;
		public final float a;
		public final float b;
		public final float c;
		public final float d;
		final float[] pos;
		final float[] uv;
		final int[] color;
		final float[] normal;
		final boolean[] glow;
		final byte[] metal;

		Part(String name, Vector3f pivot, Quaternionf rest, float a, float b, float c, float d, int vertices) {
			this.name = name;
			this.pivot = pivot;
			this.rest = rest;
			this.a = a;
			this.b = b;
			this.c = c;
			this.d = d;
			this.pos = new float[vertices * 3];
			this.uv = new float[vertices * 2];
			this.color = new int[vertices];
			this.normal = new float[vertices * 3];
			this.glow = new boolean[vertices];
			this.metal = new byte[vertices];
		}

		/**
		 * Draws the part with the stack already moved to its pivot and posed; the lamps and eyes at full light, the
		 * polished metal in the colour it reflects from where it is seen (drawn evenly lit, as the baked parts are).
		 */
		public void draw(MatrixStack.Entry entry, VertexConsumer out, int light, int overlay, @Nullable ChittyShine shine) {
			Matrix4f m = entry.getPositionMatrix();
			Vector3f p = new Vector3f();
			Vector3f n = new Vector3f();
			int count = color.length;
			for (int i = 0; i < count; i++) {
				m.transformPosition(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], p);
				entry.transformNormal(normal[i * 3], normal[i * 3 + 1], normal[i * 3 + 2], n);
				int c = color[i];
				if (metal[i] != 0) {
					if (shine != null) {
						c = shine.shade(metal[i], p.x, p.y, p.z, n.x, n.y, n.z);
					}
					n.set(0.0F, 1.0F, 0.0F);
				}
				out.vertex(p.x, p.y, p.z, c, uv[i * 2], uv[i * 2 + 1], overlay,
						glow[i] ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light, n.x, n.y, n.z);
			}
		}
	}

	public final Map<String, Part> parts;
	public final Map<String, Vec3d> markers;

	private ChittyMesh(Map<String, Part> parts, Map<String, Vec3d> markers) {
		this.parts = parts;
		this.markers = markers;
	}

	private static final Map<String, ChittyMesh> LOADED = new HashMap<>();
	private static final Set<String> FAILED = new HashSet<>();

	/** Chitty's mesh. */
	@Nullable
	public static ChittyMesh get() {
		return get("chitty");
	}

	/** A vehicle's mesh, loaded on first use (and again after resources reload); null if it could not be read. */
	@Nullable
	public static ChittyMesh get(String name) {
		ChittyMesh mesh = LOADED.get(name);
		if (mesh == null && !FAILED.contains(name)) {
			try {
				mesh = load(name);
				LOADED.put(name, mesh);
			} catch (IOException | RuntimeException e) {
				FAILED.add(name);
				Chitty.LOGGER.error("Could not load the mesh meshes/{}.cbm", name, e);
			}
		}
		return mesh;
	}

	public static void reload() {
		LOADED.clear();
		FAILED.clear();
	}

	private static ChittyMesh load(String file) throws IOException {
		String path = "meshes/" + file + ".cbm";
		Resource resource = MinecraftClient.getInstance().getResourceManager()
				.getResource(ShootingStar.id(path)).orElseThrow(() -> new IOException("missing " + path));
		byte[] bytes;
		try (InputStream in = resource.getInputStream()) {
			bytes = in.readAllBytes();
		}
		ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		if (data.get() != 'C' || data.get() != 'B' || data.get() != 'M' || data.get() != '2') {
			throw new IOException("not a vehicle mesh");
		}
		int count = data.getInt();
		Map<String, Part> parts = new LinkedHashMap<>();
		for (int k = 0; k < count; k++) {
			String name = string(data);
			Vector3f pivot = new Vector3f(data.getFloat(), data.getFloat(), data.getFloat());
			Quaternionf rest = new Quaternionf(data.getFloat(), data.getFloat(), data.getFloat(), data.getFloat());
			float a = data.getFloat();
			float b = data.getFloat();
			float c = data.getFloat();
			float d = data.getFloat();
			int vertices = data.getInt() * 4;
			Part part = new Part(name, pivot, rest, a, b, c, d, vertices);
			for (int i = 0; i < vertices; i++) {
				part.pos[i * 3] = data.getFloat();
				part.pos[i * 3 + 1] = data.getFloat();
				part.pos[i * 3 + 2] = data.getFloat();
				part.uv[i * 2] = data.getFloat();
				part.uv[i * 2 + 1] = data.getFloat();
				int r = data.get() & 255;
				int g = data.get() & 255;
				int bl = data.get() & 255;
				int al = data.get() & 255;
				part.color[i] = al << 24 | r << 16 | g << 8 | bl;
				part.normal[i * 3] = data.get() / 127.0F;
				part.normal[i * 3 + 1] = data.get() / 127.0F;
				part.normal[i * 3 + 2] = data.get() / 127.0F;
				int flags = data.get();
				part.glow[i] = (flags & 1) != 0;
				part.metal[i] = (byte) (flags >> 1 & 7);
			}
			parts.put(name, part);
		}
		Map<String, Vec3d> markers = new LinkedHashMap<>();
		int markerCount = data.getInt();
		for (int k = 0; k < markerCount; k++) {
			String name = string(data);
			markers.put(name, new Vec3d(data.getFloat(), data.getFloat(), data.getFloat()));
		}
		return new ChittyMesh(parts, markers);
	}

	private static String string(ByteBuffer data) {
		byte[] b = new byte[data.getShort()];
		data.get(b);
		return new String(b, StandardCharsets.UTF_8);
	}
}
