package io.github.kortev.shootingstar.client.gfx;

import io.github.kortev.shootingstar.ShootingStar;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.Resource;
import org.joml.Matrix4f;

/** Geometry kept on the GPU: meshes exported from Blender (tools/models.py) and procedural shapes. */
public final class Mesh {
	private final VertexBuffer buffer;

	private Mesh(VertexBuffer buffer) {
		this.buffer = buffer;
	}

	private static Mesh upload(BufferBuilder builder) {
		VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
		vb.bind();
		vb.upload(builder.end());
		VertexBuffer.unbind();
		return new Mesh(vb);
	}

	/** Draws with the program's uniforms already set; ModelViewMat = {@code modelView}, ProjMat = {@code projection}. */
	public void draw(ShaderProgram program, Matrix4f modelView, Matrix4f projection) {
		buffer.bind();
		buffer.draw(modelView, projection, program);
		VertexBuffer.unbind();
	}

	/** Loads assets/shootingstar/meshes/{name}.ssm (positions, uv, occlusion/glow/material colour, normals). */
	public static Mesh load(String name) {
		try {
			Resource resource = MinecraftClient.getInstance().getResourceManager()
					.getResource(ShootingStar.id("meshes/" + name + ".ssm")).orElseThrow(() -> new IOException("missing mesh " + name));
			byte[] bytes;
			try (InputStream in = resource.getInputStream()) {
				bytes = in.readAllBytes();
			}
			ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
			if (data.get() != 'S' || data.get() != 'S' || data.get() != 'M' || data.get() != '1') {
				throw new IOException("bad mesh header in " + name);
			}
			int vertices = data.getInt();
			int indices = data.getInt();
			int base = data.position();
			int stride = 28;
			BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
			int indexBase = base + vertices * stride;
			for (int i = 0; i < indices; i++) {
				int v = data.getInt(indexBase + i * 4);
				int p = base + v * stride;
				b.vertex(data.getFloat(p), data.getFloat(p + 4), data.getFloat(p + 8))
						.texture(data.getFloat(p + 12), data.getFloat(p + 16))
						.color(data.get(p + 20) & 255, data.get(p + 21) & 255, data.get(p + 22) & 255, data.get(p + 23) & 255)
						.normal(data.get(p + 24) / 127.0F, data.get(p + 25) / 127.0F, data.get(p + 26) / 127.0F);
			}
			return upload(b);
		} catch (IOException e) {
			ShootingStar.LOGGER.error("Could not load mesh {}", name, e);
			return sphere(8, 4);
		}
	}

	/**
	 * Unit sphere with equirectangular UVs: u = 0 at longitude -180, v = 0 at the north pole. Longitude 0 faces +Z
	 * and east is +X, so a place at (lat, lon) lies along {@link #direction}.
	 */
	public static Mesh sphere(int lonSegments, int latSegments) {
		return sphere(lonSegments, latSegments, 0);
	}

	/** As {@link #sphere(int, int)}, tagged with an {@code ss_mesh} material id in the vertex colour's blue channel. */
	public static Mesh sphere(int lonSegments, int latSegments, int material) {
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
		for (int j = 0; j < latSegments; j++) {
			for (int i = 0; i < lonSegments; i++) {
				sphereVertex(b, (float) i / lonSegments, (float) j / latSegments, material);
				sphereVertex(b, (float) i / lonSegments, (float) (j + 1) / latSegments, material);
				sphereVertex(b, (float) (i + 1) / lonSegments, (float) (j + 1) / latSegments, material);
				sphereVertex(b, (float) (i + 1) / lonSegments, (float) j / latSegments, material);
			}
		}
		return upload(b);
	}

	private static void sphereVertex(BufferBuilder b, float u, float v, int material) {
		double lon = u * Math.PI * 2 - Math.PI;
		double lat = Math.PI / 2 - v * Math.PI;
		float x = (float) (Math.cos(lat) * Math.sin(lon));
		float y = (float) Math.sin(lat);
		float z = (float) (Math.cos(lat) * Math.cos(lon));
		b.vertex(x, y, z).texture(u, v).color(255, 0, material, 255).normal(x, y, z);
	}

	/** Direction of a place on a {@link #sphere} given in degrees. */
	public static org.joml.Vector3f direction(double latDeg, double lonDeg) {
		double lat = Math.toRadians(latDeg);
		double lon = Math.toRadians(lonDeg);
		return new org.joml.Vector3f((float) (Math.cos(lat) * Math.sin(lon)), (float) Math.sin(lat),
				(float) (Math.cos(lat) * Math.cos(lon)));
	}

	/**
	 * A ribbon around the Y axis at radius 1, {@code width} wide, for {@code ss_glow} mode 4: a flat
	 * band in the XZ plane crossed with an upright one, so it reads from any angle including edge-on.
	 * u runs 0..1 around from +X towards -Z, v runs -1..1 across.
	 */
	public static Mesh ribbon(int segments, float width) {
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		for (int upright = 0; upright < 2; upright++) {
			for (int i = 0; i < segments; i++) {
				float u0 = (float) i / segments;
				float u1 = (float) (i + 1) / segments;
				ribbonVertex(b, u0, -1, width, upright == 1);
				ribbonVertex(b, u0, 1, width, upright == 1);
				ribbonVertex(b, u1, 1, width, upright == 1);
				ribbonVertex(b, u1, -1, width, upright == 1);
			}
		}
		return upload(b);
	}

	private static void ribbonVertex(BufferBuilder b, float u, float v, float width, boolean upright) {
		double a = u * Math.PI * 2;
		float r = upright ? 1.0F : 1.0F + v * width * 0.5F;
		float y = upright ? v * width * 0.5F : 0.0F;
		b.vertex((float) (Math.cos(a) * r), y, (float) (-Math.sin(a) * r)).texture(u, v).color(255, 255, 255, 255);
	}

	/** The star catalogue (assets/shootingstar/textures/feed/stars.bin) as one quad per star. */
	public static Mesh stars() {
		try {
			Resource resource = MinecraftClient.getInstance().getResourceManager()
					.getResource(ShootingStar.id("textures/feed/stars.bin")).orElseThrow(() -> new IOException("missing star catalogue"));
			byte[] bytes;
			try (InputStream in = resource.getInputStream()) {
				bytes = in.readAllBytes();
			}
			ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
			int count = data.getInt();
			BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
			for (int i = 0; i < count; i++) {
				float x = data.getFloat();
				float y = data.getFloat();
				float z = data.getFloat();
				int r = data.get() & 255;
				int g = data.get() & 255;
				int bl = data.get() & 255;
				int mag = data.get() & 255;
				b.vertex(x, y, z).texture(-1, -1).color(r, g, bl, mag);
				b.vertex(x, y, z).texture(1, -1).color(r, g, bl, mag);
				b.vertex(x, y, z).texture(1, 1).color(r, g, bl, mag);
				b.vertex(x, y, z).texture(-1, 1).color(r, g, bl, mag);
			}
			return upload(b);
		} catch (IOException e) {
			ShootingStar.LOGGER.error("Could not load the star catalogue", e);
			BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
			for (int i = 0; i < 4; i++) {
				b.vertex(0, 0, 1).texture(0, 0).color(0, 0, 0, 255);
			}
			return upload(b);
		}
	}

	/**
	 * The universe in Ginnungagap's block (assets/shootingstar/textures/feed/universe.bin, tools/gen_universe.py), for
	 * {@code ss_galaxy}: a mesh of the first {@code galaxies[i]} of its galaxies for each i, then one of the first
	 * {@code glows[i]} glows of its web for each i. The galaxies are shuffled, so the first few thousand are a fair
	 * sample of the whole; the first of all, the big spiral the feed starts beside, is left out of every sample but the
	 * first. Each is a quad, its four corners at its centre, spread out on screen by the shader.
	 */
	public static Mesh[] universe(int[] galaxies, int[] glows) {
		Mesh[] meshes = new Mesh[galaxies.length + glows.length];
		try {
			Resource resource = MinecraftClient.getInstance().getResourceManager()
					.getResource(ShootingStar.id("textures/feed/universe.bin")).orElseThrow(() -> new IOException("missing universe"));
			byte[] bytes;
			try (InputStream in = resource.getInputStream()) {
				bytes = in.readAllBytes();
			}
			ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
			int galaxyCount = data.getInt(0);
			int glowCount = data.getInt(4);
			for (int m = 0; m < galaxies.length; m++) {
				BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
				for (int i = m == 0 ? 0 : 1; i < Math.min(galaxies[m], galaxyCount); i++) {
					int at = 8 + i * 24;
					// The radius (in 1e-5) and, above it, the type (0 spiral, 1 elliptical) and shape go in as UV2.
					int radius = data.getShort(at + 16) & 0xFFFF;
					int kind = (data.get(at + 15) & 255) * 256 + (data.get(at + 18) & 255);
					galaxy(b, data.getFloat(at), data.getFloat(at + 4), data.getFloat(at + 8), data.get(at + 19) & 255, data.get(at + 20) & 255,
							data.get(at + 21) & 255, data.get(at + 22) & 255, radius, kind, data.get(at + 12) / 127.0F, data.get(at + 13) / 127.0F,
							data.get(at + 14) / 127.0F);
				}
				meshes[m] = upload(b);
			}
			int glowStart = 8 + galaxyCount * 24;
			for (int m = 0; m < glows.length; m++) {
				BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
				for (int i = 0; i < Math.min(glows[m], glowCount); i++) {
					int at = glowStart + i * 20;
					galaxy(b, data.getFloat(at), data.getFloat(at + 4), data.getFloat(at + 8), data.get(at + 14) & 255, data.get(at + 15) & 255,
							data.get(at + 16) & 255, data.get(at + 17) & 255, data.getShort(at + 12) & 0xFFFF, 0, 0.0F, 1.0F, 0.0F);
				}
				meshes[galaxies.length + m] = upload(b);
			}
		} catch (IOException e) {
			ShootingStar.LOGGER.error("Could not load the universe", e);
			for (int m = 0; m < meshes.length; m++) {
				BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
				galaxy(b, 0.0F, 0.0F, 0.0F, 0, 0, 0, 0, 0, 0, 0.0F, 1.0F, 0.0F);
				meshes[m] = upload(b);
			}
		}
		return meshes;
	}

	/**
	 * Yggdrasil's points of light (tools/gen_yggdrasil.py), a quad each for ss_tree: colour and brightness; size and when
	 * it grows as UV2; how far along the tree it is, what it is and a seed as the normal.
	 */
	public static Mesh tree() {
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
		try {
			Resource resource = MinecraftClient.getInstance().getResourceManager()
					.getResource(ShootingStar.id("textures/feed/yggdrasil.bin")).orElseThrow(() -> new IOException("missing yggdrasil"));
			byte[] bytes;
			try (InputStream in = resource.getInputStream()) {
				bytes = in.readAllBytes();
			}
			ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
			int count = data.getInt(0);
			for (int i = 0; i < count; i++) {
				int at = 4 + i * 24;
				int size = data.getShort(at + 16) & 0xFFFF;
				int grow = (int) ((data.getShort(at + 18) & 0xFFFF) * (30000.0 / 65535.0));
				float along = (data.getShort(at + 20) & 0xFFFF) * 1.0E-4F / 2.5F * 2.0F - 1.0F;
				float kind = (data.get(at + 22) & 255) / 3.0F;
				float seed = (data.get(at + 23) & 255) / 255.0F * 2.0F - 1.0F;
				float x = data.getFloat(at);
				float y = data.getFloat(at + 4);
				float z = data.getFloat(at + 8);
				int r = data.get(at + 12) & 255;
				int g = data.get(at + 13) & 255;
				int bl = data.get(at + 14) & 255;
				int a = data.get(at + 15) & 255;
				for (int c = 0; c < 4; c++) {
					b.vertex(x, y, z).color(r, g, bl, a).texture(c == 0 || c == 3 ? -1 : 1, c < 2 ? -1 : 1).light(size, grow).normal(along, kind, seed);
				}
			}
		} catch (IOException e) {
			ShootingStar.LOGGER.error("Could not load Yggdrasil", e);
			for (int c = 0; c < 4; c++) {
				b.vertex(0, 0, 0).color(0, 0, 0, 0).texture(0, 0).light(0, 0).normal(0, 1, 0);
			}
		}
		return upload(b);
	}

	private static void galaxy(BufferBuilder b, float x, float y, float z, int r, int g, int bl, int a, int radius, int kind, float px,
			float py, float pz) {
		b.vertex(x, y, z).color(r, g, bl, a).texture(-1, -1).light(radius, kind).normal(px, py, pz);
		b.vertex(x, y, z).color(r, g, bl, a).texture(1, -1).light(radius, kind).normal(px, py, pz);
		b.vertex(x, y, z).color(r, g, bl, a).texture(1, 1).light(radius, kind).normal(px, py, pz);
		b.vertex(x, y, z).color(r, g, bl, a).texture(-1, 1).light(radius, kind).normal(px, py, pz);
	}

	/**
	 * Open tube of radius 1 along +Z from {@code z0} to {@code z1}, for the accelerator's barrel ({@code ss_bore}):
	 * rings packed close near {@code z0 + 30} where the camera rides, spreading out towards the far end.
	 */
	public static Mesh tube(int segments, int rings, float z0, float z1) {
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
		for (int j = 0; j < rings; j++) {
			float za = tubeZ(j, rings, z0, z1);
			float zb = tubeZ(j + 1, rings, z0, z1);
			for (int i = 0; i < segments; i++) {
				tubeVertex(b, i, segments, za);
				tubeVertex(b, i, segments, zb);
				tubeVertex(b, i + 1, segments, zb);
				tubeVertex(b, i + 1, segments, za);
			}
		}
		return upload(b);
	}

	private static float tubeZ(int j, int rings, float z0, float z1) {
		// Even spacing over the first sixty, then each ring further than the last.
		float t = (float) j / rings;
		float near = Math.min(60.0F, (z1 - z0) * 0.5F);
		return t < 0.5F ? z0 + near * t * 2.0F : z0 + near + (z1 - z0 - near) * (float) Math.pow((t - 0.5F) * 2.0F, 2.5);
	}

	private static void tubeVertex(BufferBuilder b, int i, int segments, float z) {
		double a = Math.PI * 2 * i / segments;
		float x = (float) Math.cos(a);
		float y = (float) Math.sin(a);
		b.vertex(x, y, z).texture((float) i / segments, z).color(255, 255, 255, 255).normal(-x, -y, 0.0F);
	}

	/** Cube from -1 to 1 with flat normals, for the glass of a universe's block ({@code ss_block}). */
	public static Mesh cube() {
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
		int[][] faces = {{1, 0, 0, 0, 1, 0}, {-1, 0, 0, 0, 1, 0}, {0, 1, 0, 0, 0, 1}, {0, -1, 0, 0, 0, 1}, {0, 0, 1, 0, 1, 0}, {0, 0, -1, 0, 1, 0}};
		for (int[] f : faces) {
			float nx = f[0];
			float ny = f[1];
			float nz = f[2];
			// a = up within the face, c = n x a.
			float ax = f[3];
			float ay = f[4];
			float az = f[5];
			float cx = ny * az - nz * ay;
			float cy = nz * ax - nx * az;
			float cz = nx * ay - ny * ax;
			float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
			for (float[] k : corners) {
				b.vertex(nx + cx * k[0] + ax * k[1], ny + cy * k[0] + ay * k[1], nz + cz * k[0] + az * k[1]).texture(0, 0)
						.color(255, 255, 255, 255).normal(nx, ny, nz);
			}
		}
		return upload(b);
	}

	/** Square from -1 to 1 in x and y at z = 0, facing +Z (the window of an open gate). */
	public static Mesh quad() {
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
		b.vertex(-1, -1, 0).texture(0, 0).color(255, 255, 255, 255).normal(0, 0, 1);
		b.vertex(1, -1, 0).texture(1, 0).color(255, 255, 255, 255).normal(0, 0, 1);
		b.vertex(1, 1, 0).texture(1, 1).color(255, 255, 255, 255).normal(0, 0, 1);
		b.vertex(-1, 1, 0).texture(0, 1).color(255, 255, 255, 255).normal(0, 0, 1);
		return upload(b);
	}

	/** Open-ended cone shell along +Z from the tip at z = 0 to radius 1 at z = -1, for the re-entry sheath. */
	public static Mesh cone(int segments, int rings) {
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
		for (int j = 0; j < rings; j++) {
			for (int i = 0; i < segments; i++) {
				coneVertex(b, i, j, segments, rings);
				coneVertex(b, i, j + 1, segments, rings);
				coneVertex(b, i + 1, j + 1, segments, rings);
				coneVertex(b, i + 1, j, segments, rings);
			}
		}
		return upload(b);
	}

	private static void coneVertex(BufferBuilder b, int i, int j, int segments, int rings) {
		double a = Math.PI * 2 * i / segments;
		float t = (float) j / rings;
		// A blunt bow-shock profile: wide quickly, then flaring slowly.
		float r = (float) Math.sqrt(t);
		float z = -t;
		float x = (float) Math.cos(a) * r;
		float y = (float) Math.sin(a) * r;
		float nx = (float) Math.cos(a);
		float ny = (float) Math.sin(a);
		float nz = 0.5F / Math.max(r, 0.05F);
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		b.vertex(x, y, z).texture((float) i / segments, t).color(255, 255, 255, 255).normal(nx / len, ny / len, nz / len);
	}
}
