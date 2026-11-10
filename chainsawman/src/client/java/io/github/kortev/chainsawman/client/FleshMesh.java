package io.github.kortev.chainsawman.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

/**
 * One of the Eternity Devil's baked meshes ({@code tools/eternity_devil_3d.py}: {@code eternity_*_mesh.bin} and {@code eternity_*_parts.json}),
 * kept on the GPU. Every part that does not move is merged into one vertex buffer built once; every animated part (arms, heads, jaws, lids,
 * ropes...) has its own small buffer and is drawn with a transform of its own, so a frame costs one draw call a part and no vertices are
 * re-sent. The animation maths is the kit's {@code anim_transform} (tools/eternity_3d_kit.py), in ticks.
 *
 * <p>Drawing is queued from the entity renderers (which only say where the model is: the model-view matrix at that moment) and done once
 * after the entities, by {@link #flush}, with the {@code csm_flesh} shader: the paint is the vertex colour (alpha = glows on its own), and the
 * shader adds the game's own light floor, the orange-red rim on edges and the red glow from the pit.
 */
public final class FleshMesh {
	/** The shader, registered by {@link ChainsawManClient}. */
	static ShaderProgram program;

	/** How a part is posed when something other than the clock decides (a jaw open by a bite): return false to hide it. */
	interface Hook {
		boolean pose(Part p, Matrix4f m, Matrix4f base);
	}

	/** What the shader is told for one drawing. */
	static final class Look {
		float r = 1.0F;
		float g = 1.0F;
		float b = 1.0F;
		float hurt;
		float rim = 0.8F;
		/** The pit's glow, in the model's own frame: the point, how far it reaches, and its colour. */
		Vector3f glowAt;
		float glowRange = 2.0F;
		float glowR;
		float glowG;
		float glowB;
		/** Keep only the side of the plane through the model's origin that this (model-space) direction points to. */
		Vector3f clip;

		/** The world's light at the model (a packed light coordinate) lifts a floor of grey-brown: never darker than this. */
		Look light(int packed, float floor, float gain) {
			float lit = Math.max(LightmapTextureManager.getBlockLightCoordinates(packed), LightmapTextureManager.getSkyLightCoordinates(packed)) / 15.0F;
			float k = gain * (floor + (1.0F - floor) * lit);
			r = k;
			g = k;
			b = k;
			return this;
		}
	}

	static final class Anim {
		String kind;
		Vector3f axis = new Vector3f(0, 1, 0);
		Vector3f axis2;
		float amp;
		float period = 60.0F;
		float phase;
		float base;
	}

	static final class Part {
		int index;
		String name;
		String kind;
		Vector3f pivot = new Vector3f();
		Anim anim;
		VertexBuffer buffer;
		int quads;
		JsonObject json;
	}

	// ---- Cost.
	private static long cpuNs;
	private static long cpuWorst;
	private static int frames;
	private static int draws;
	private static int quadsDrawn;
	private static long gpuNs;
	private static int gpuSamples;
	private static int query;
	private static boolean queryBusy;

	/** The cost since the last call: frames, CPU ms in {@link #flush} (average and worst), GPU ms (timer query), draw calls and quads a frame. */
	public static String costReport() {
		String s = String.format("%d frames, CPU %.3f ms avg %.3f ms worst, GPU %.3f ms avg (%d samples), %d draw calls and %d quads a frame", frames,
				frames == 0 ? 0.0 : cpuNs / 1.0e6 / frames, cpuWorst / 1.0e6, gpuSamples == 0 ? 0.0 : gpuNs / 1.0e6 / gpuSamples, gpuSamples,
				frames == 0 ? 0 : draws / frames, frames == 0 ? 0 : quadsDrawn / frames);
		cpuNs = 0;
		cpuWorst = 0;
		frames = 0;
		draws = 0;
		quadsDrawn = 0;
		gpuNs = 0;
		gpuSamples = 0;
		return s;
	}

	// ---- The queue: what the renderers asked for this frame.
	private static final List<Runnable> QUEUE = new ArrayList<>();

	static void queue(Runnable r) {
		QUEUE.add(r);
	}

	/** Draws everything queued (after the entities, with the frame's projection). */
	static void flush() {
		if (QUEUE.isEmpty() || program == null) {
			QUEUE.clear();
			return;
		}
		long t0 = System.nanoTime();
		boolean timing = false;
		try {
			if (query == 0) {
				query = GL15C.glGenQueries();
			}
			if (!queryBusy) {
				GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
				queryBusy = true;
				timing = true;
			}
		} catch (RuntimeException | Error e) {
			timing = false;
		}
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		RenderSystem.enableCull();
		int callsBefore = draws;
		for (Runnable r : QUEUE) {
			r.run();
		}
		QUEUE.clear();
		if (timing) {
			GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);
		} else if (queryBusy) {
			try {
				if (GL15C.glGetQueryObjecti(query, GL15C.GL_QUERY_RESULT_AVAILABLE) != 0) {
					gpuNs += GL33C.glGetQueryObjecti64(query, GL15C.GL_QUERY_RESULT);
					gpuSamples++;
					queryBusy = false;
				}
			} catch (RuntimeException | Error e) {
				queryBusy = false;
			}
		}
		long dt = System.nanoTime() - t0;
		cpuNs += dt;
		cpuWorst = Math.max(cpuWorst, dt);
		frames++;
	}

	// ---- The data.

	private static final Map<String, FleshMesh> CACHE = new HashMap<>();

	static FleshMesh get(String name) {
		return CACHE.computeIfAbsent(name, FleshMesh::new);
	}

	/** Frees the GPU buffers (a resource reload). */
	static void clear() {
		for (FleshMesh m : CACHE.values()) {
			for (Part p : m.parts) {
				if (p.buffer != null) {
					p.buffer.close();
				}
			}
			if (m.staticBuffer != null) {
				m.staticBuffer.close();
			}
		}
		CACHE.clear();
	}

	final List<Part> parts = new ArrayList<>();
	final Map<String, Part> byName = new HashMap<>();
	private VertexBuffer staticBuffer;
	private int staticQuads;

	private static InputStream open(String file) throws IOException {
		InputStream in = FleshMesh.class.getResourceAsStream("/assets/chainsawman/models/entity/" + file);
		if (in == null) {
			throw new IOException("missing " + file);
		}
		return in;
	}

	private static Vector3f vec(JsonArray a) {
		return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
	}

	private FleshMesh(String name) {
		try (InputStream pin = open("eternity_" + name + "_parts.json"); InputStream min = open("eternity_" + name + "_mesh.bin")) {
			JsonObject root = JsonParser.parseReader(new InputStreamReader(pin, StandardCharsets.UTF_8)).getAsJsonObject();
			float unit = root.has("posUnit") ? root.get("posUnit").getAsFloat() : 16.0F;
			for (JsonElement e : root.getAsJsonArray("parts")) {
				JsonObject o = e.getAsJsonObject();
				Part p = new Part();
				p.json = o;
				p.index = o.get("index").getAsInt();
				p.name = o.get("name").getAsString();
				p.kind = o.get("kind").getAsString();
				p.pivot = vec(o.getAsJsonArray("pivot"));
				if (o.has("anim") && !o.get("anim").isJsonNull()) {
					JsonObject a = o.getAsJsonObject("anim");
					Anim an = new Anim();
					an.kind = a.get("kind").getAsString();
					an.axis = vec(a.getAsJsonArray("axis")).normalize();
					if (a.has("axis2")) {
						an.axis2 = vec(a.getAsJsonArray("axis2")).normalize();
					}
					an.amp = a.has("amp") ? a.get("amp").getAsFloat() : 0.0F;
					an.period = a.has("period") ? a.get("period").getAsFloat() : 60.0F;
					an.phase = a.has("phase") ? a.get("phase").getAsFloat() : 0.0F;
					an.base = a.has("base") ? a.get("base").getAsFloat() : 0.0F;
					p.anim = an;
				}
				parts.add(p);
				byName.put(p.name, p);
			}
			ByteBuffer b = ByteBuffer.wrap(min.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
			int n = b.getInt();
			// One builder for the merged static parts, one for each animated part.
			Map<Integer, BufferBuilder> builders = new HashMap<>();
			Map<Integer, Integer> counts = new HashMap<>();
			List<net.minecraft.client.util.BufferAllocator> allocs = new ArrayList<>();
			float[][] c = new float[4][3];
			for (int i = 0; i < n; i++) {
				int dir = b.get() & 255;
				int pi = b.get() & 255;
				float x = b.getShort() / unit;
				float y = b.getShort() / unit;
				float z = b.getShort() / unit;
				float du = b.getShort() / unit;
				float dv = b.getShort() / unit;
				int r = b.get() & 255;
				int g = b.get() & 255;
				int bl = b.get() & 255;
				int em = b.get() & 255;
				Part part = parts.get(pi);
				boolean merged = part.anim == null && !part.kind.startsWith("grab_");
				BufferBuilder bb;
				int key = merged ? -1 : pi;
				bb = builders.get(key);
				if (bb == null) {
					net.minecraft.client.util.BufferAllocator alloc = new net.minecraft.client.util.BufferAllocator(1 << 16);
					allocs.add(alloc);
					bb = new BufferBuilder(alloc, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
					builders.put(key, bb);
				}
				counts.merge(key, 1, Integer::sum);
				int ax = dir >> 1;
				float[] u = {0, 0, 0};
				float[] v = {0, 0, 0};
				if (ax == 0) {
					u[2] = du;
					v[1] = dv;
				} else if (ax == 1) {
					u[0] = du;
					v[2] = dv;
				} else {
					u[0] = du;
					v[1] = dv;
				}
				int[][] cc = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
				for (int k = 0; k < 4; k++) {
					c[k][0] = x + u[0] * cc[k][0] + v[0] * cc[k][1];
					c[k][1] = y + u[1] * cc[k][0] + v[1] * cc[k][1];
					c[k][2] = z + u[2] * cc[k][0] + v[2] * cc[k][1];
				}
				float nx = ax == 0 ? (dir == 0 ? 1 : -1) : 0;
				float ny = ax == 1 ? (dir == 2 ? 1 : -1) : 0;
				float nz = ax == 2 ? (dir == 4 ? 1 : -1) : 0;
				// Wind it counter-clockwise seen from outside (the faces are culled from behind).
				float e1x = c[1][0] - c[0][0];
				float e1y = c[1][1] - c[0][1];
				float e1z = c[1][2] - c[0][2];
				float e2x = c[3][0] - c[0][0];
				float e2y = c[3][1] - c[0][1];
				float e2z = c[3][2] - c[0][2];
				float dot = (e1y * e2z - e1z * e2y) * nx + (e1z * e2x - e1x * e2z) * ny + (e1x * e2y - e1y * e2x) * nz;
				int[] order = dot >= 0 ? new int[] {0, 1, 2, 3} : new int[] {0, 3, 2, 1};
				for (int k : order) {
					bb.vertex(c[k][0], c[k][1], c[k][2]).texture(0.0F, 0.0F).color(r, g, bl, em).normal(nx, ny, nz);
				}
			}
			for (Map.Entry<Integer, BufferBuilder> e : builders.entrySet()) {
				VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
				vb.bind();
				vb.upload(e.getValue().end());
				VertexBuffer.unbind();
				if (e.getKey() < 0) {
					staticBuffer = vb;
					staticQuads = counts.get(-1);
				} else {
					Part p = parts.get(e.getKey());
					p.buffer = vb;
					p.quads = counts.get(e.getKey());
				}
			}
			for (net.minecraft.client.util.BufferAllocator a : allocs) {
				a.close();
			}
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	// ---- Drawing.

	/** The part's pose about its pivot at time {@code t} (ticks), multiplied onto {@code m}. */
	static void pose(Anim a, Vector3f pivot, float t, Matrix4f m) {
		float w = (float) (2.0 * Math.PI * (t / a.period + a.phase));
		Vector3f tr = new Vector3f();
		float sx = 1;
		float sy = 1;
		float sz = 1;
		float rad = (float) Math.PI / 180.0F;
		m.translate(pivot);
		switch (a.kind) {
			case "sway" -> {
				m.rotate(a.amp * (float) Math.sin(w) * rad, a.axis);
				if (a.axis2 != null) {
					m.rotate(0.6F * a.amp * (float) Math.cos(w * 0.8F + 1.0F) * rad, a.axis2);
				}
			}
			case "hinge" -> m.rotate((a.base + a.amp * (0.5F + 0.5F * (float) Math.sin(w))) * rad, a.axis);
			case "blink" -> {
				float f = (t / a.period + a.phase) % 1.0F;
				if (f < 0) {
					f += 1.0F;
				}
				float cl = f < 0.88F ? 0.0F : (float) Math.sin((f - 0.88F) / 0.12F * Math.PI);
				m.rotate((a.base + a.amp * cl) * rad, a.axis);
			}
			case "roll" -> {
				m.rotate(a.amp * (float) Math.sin(w) * rad, a.axis);
				if (a.axis2 != null) {
					m.rotate(0.7F * a.amp * (float) Math.cos(w * 0.7F + 0.5F) * rad, a.axis2);
				}
			}
			case "scale" -> {
				float k = 1.0F + a.amp * (float) Math.sin(w);
				sx = Math.abs(a.axis.x) > 0.5F ? 1.0F : k;
				sy = Math.abs(a.axis.y) > 0.5F ? 1.0F : k;
				sz = Math.abs(a.axis.z) > 0.5F ? 1.0F : k;
				m.scale(sx, sy, sz);
			}
			case "reach" -> m.translate(new Vector3f(a.axis).mul(a.amp * (0.5F + 0.5F * (float) Math.sin(w))));
			case "fall" -> {
				float f = (t / a.period + a.phase) % 1.0F;
				if (f < 0) {
					f += 1.0F;
				}
				m.translate(new Vector3f(a.axis).mul(a.amp * f));
			}
			default -> {
			}
		}
		m.translate(-pivot.x, -pivot.y, -pivot.z);
	}

	private static void setUniforms(Matrix4f base, Look look) {
		ShaderProgram p = program;
		set(p, "Tint", look.r, look.g, look.b, look.hurt);
		set(p, "RimAmt", look.rim);
		if (look.clip != null) {
			Vector3f n = base.transformDirection(new Vector3f(look.clip)).normalize();
			Vector3f o = base.transformPosition(new Vector3f());
			set(p, "Clip", n.x, n.y, n.z, -n.dot(o));
		} else {
			set(p, "Clip", 0, 0, 0, 1);
		}
		set(p, "GlowCol", look.glowR, look.glowG, look.glowB);
		if (look.glowAt != null) {
			Vector4f v = new Vector4f(look.glowAt, 1.0F).mul(base);
			set(p, "GlowPos", v.x, v.y, v.z, look.glowRange);
		} else {
			set(p, "GlowPos", 0, 0, 0, 1);
			set(p, "GlowCol", 0, 0, 0);
		}
	}

	private static void set(ShaderProgram p, String n, float a) {
		var u = p.getUniform(n);
		if (u != null) {
			u.set(a);
		}
	}

	private static void set(ShaderProgram p, String n, float a, float b, float c) {
		var u = p.getUniform(n);
		if (u != null) {
			u.set(a, b, c);
		}
	}

	private static void set(ShaderProgram p, String n, float a, float b, float c, float d) {
		var u = p.getUniform(n);
		if (u != null) {
			u.set(a, b, c, d);
		}
	}

	private static void drawBuffer(VertexBuffer vb, Matrix4f mv, Matrix4f proj, int quads) {
		vb.bind();
		vb.draw(mv, proj, program);
		VertexBuffer.unbind();
		draws++;
		quadsDrawn += quads;
	}

	/**
	 * Queues the whole model: {@code model} takes the model's frame to view space (the entity's matrix as it is now), {@code t} is in ticks.
	 * Parts of kind {@code grab_*} are left out (the arms draw those); {@code hook} (may be null) can pose or hide the animated parts.
	 */
	void draw(Matrix4f model, float t, Look look, Hook hook) {
		Matrix4f full = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(model);
		queue(() -> {
			Matrix4f proj = RenderSystem.getProjectionMatrix();
			setUniforms(full, look);
			if (staticBuffer != null) {
				drawBuffer(staticBuffer, full, proj, staticQuads);
			}
			for (Part p : parts) {
				if (p.buffer == null || p.kind.startsWith("grab_")) {
					continue;
				}
				Matrix4f m = new Matrix4f(full);
				pose(p.anim, p.pivot, t, m);
				if (hook != null && !hook.pose(p, m, full)) {
					continue;
				}
				drawBuffer(p.buffer, m, proj, p.quads);
			}
		});
	}

	/** Queues one part (or the merged static body when {@code part} is null) with a transform the caller worked out. */
	void drawPart(Part part, Matrix4f view, Look look, Matrix4f lookBase) {
		Matrix4f full = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(view);
		Matrix4f lb = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(lookBase);
		queue(() -> {
			setUniforms(lb, look);
			if (part == null) {
				if (staticBuffer != null) {
					drawBuffer(staticBuffer, full, RenderSystem.getProjectionMatrix(), staticQuads);
				}
			} else if (part.buffer != null) {
				drawBuffer(part.buffer, full, RenderSystem.getProjectionMatrix(), part.quads);
			} else if (part.kind.startsWith("grab_")) {
				// (An unanimated grab part is in the merged buffer? No: grab parts are never merged; they have buffers.)
			}
		});
	}
}
