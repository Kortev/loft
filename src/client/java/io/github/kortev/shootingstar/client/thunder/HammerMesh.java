package io.github.kortev.shootingstar.client.thunder;

import io.github.kortev.shootingstar.ShootingStar;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Mjölnir as built in Blender and baked for the game (tools/mjolnir_model.py --game): its head, bands, collar, wound
 * leather, pommel, ring and wrist loop as one mesh of quads, read from assets/shootingstar/meshes/mjolnir.hbm the first
 * time it is drawn (and again after resources reload) and drawn quad by quad by {@link HammerRenderer}.
 *
 * <p>The file is little endian:
 * <ul>
 * <li>the four bytes {@code HBM1};</li>
 * <li>int32: how many quads there are;</li>
 * <li>int32: how many of them, at the front, carry the glowing interlace and runes (only these are drawn a second time,
 * into the glow layer);</li>
 * <li>then four vertices a quad, 24 bytes each: float32 x, y, z in the item's pixels (sixteen to a block, as a JSON
 * item model's elements are, with the hammer laid over on the diagonal, head top right, where the old cuboid model
 * lay); float32 u, v on the baked textures (v runs down); int8 nx, ny, nz, the normal times 127; and one byte of
 * padding.</li>
 * </ul>
 * Triangles repeat their last corner. Positions are turned into blocks as they are read, as the builtin item renderer
 * draws a block from 0 to 1.
 */
public final class HammerMesh {
	private static final String FILE = "meshes/mjolnir.hbm";
	private static final float PIXEL = 1.0F / 16.0F;

	/** Each vertex's position in blocks, three floats apiece. */
	private final float[] position;
	/** Each vertex's place on the textures, two floats apiece. */
	private final float[] uv;
	/** Each vertex's normal, three floats apiece. */
	private final float[] normal;
	/** How many quads there are. */
	public final int quads;
	/** How many quads at the front carry the runes' glow. */
	public final int glowing;

	private HammerMesh(int quads, int glowing) {
		this.quads = quads;
		this.glowing = glowing;
		this.position = new float[quads * 4 * 3];
		this.uv = new float[quads * 4 * 2];
		this.normal = new float[quads * 4 * 3];
	}

	@Nullable
	private static HammerMesh loaded;
	private static boolean failed;

	/** The mesh, read on first use (and again after resources reload); null if it could not be read. */
	@Nullable
	public static HammerMesh get() {
		if (loaded == null && !failed) {
			try {
				loaded = load();
			} catch (IOException | RuntimeException e) {
				failed = true;
				ShootingStar.LOGGER.error("Could not load Mjölnir's mesh", e);
			}
		}
		return loaded;
	}

	/** Forgets the mesh so the next draw reads it again, from whichever resource pack now provides it. */
	public static void reload() {
		loaded = null;
		failed = false;
	}

	private static HammerMesh load() throws IOException {
		Resource resource = MinecraftClient.getInstance().getResourceManager()
				.getResource(ShootingStar.id(FILE)).orElseThrow(() -> new IOException("missing " + FILE));
		byte[] bytes;
		try (InputStream in = resource.getInputStream()) {
			bytes = in.readAllBytes();
		}
		ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		if (data.get() != 'H' || data.get() != 'B' || data.get() != 'M' || data.get() != '1') {
			throw new IOException("not a hammer mesh");
		}
		int quads = data.getInt();
		int glowing = data.getInt();
		if (quads < 0 || glowing < 0 || glowing > quads || data.remaining() != quads * 4 * 24) {
			throw new IOException("hammer mesh of the wrong size");
		}
		HammerMesh mesh = new HammerMesh(quads, glowing);
		for (int i = 0; i < quads * 4; i++) {
			mesh.position[i * 3] = data.getFloat() * PIXEL;
			mesh.position[i * 3 + 1] = data.getFloat() * PIXEL;
			mesh.position[i * 3 + 2] = data.getFloat() * PIXEL;
			mesh.uv[i * 2] = data.getFloat();
			mesh.uv[i * 2 + 1] = data.getFloat();
			mesh.normal[i * 3] = data.get() / 127.0F;
			mesh.normal[i * 3 + 1] = data.get() / 127.0F;
			mesh.normal[i * 3 + 2] = data.get() / 127.0F;
			data.get();
		}
		return mesh;
	}

	/**
	 * Draws the first {@code count} quads (all of them, or just the glowing ones) through the stack's matrices, in one
	 * colour (ARGB, multiplying the texture), at one light.
	 */
	public void draw(MatrixStack.Entry entry, VertexConsumer out, int count, int color, int light, int overlay) {
		Matrix4f matrix = entry.getPositionMatrix();
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		int vertices = Math.min(count, quads) * 4;
		for (int i = 0; i < vertices; i++) {
			matrix.transformPosition(position[i * 3], position[i * 3 + 1], position[i * 3 + 2], p);
			entry.transformNormal(normal[i * 3], normal[i * 3 + 1], normal[i * 3 + 2], n);
			out.vertex(p.x, p.y, p.z, color, uv[i * 2], uv[i * 2 + 1], overlay, light, n.x, n.y, n.z);
		}
	}
}
