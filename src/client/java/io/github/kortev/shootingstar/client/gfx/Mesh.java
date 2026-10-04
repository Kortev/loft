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
