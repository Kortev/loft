package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.EternityDevilEntity;
import io.github.kortev.chainsawman.network.HotelPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Eternity Devil as drawn, the way Minecraft's boss mods draw theirs: out of cuboids and one painted texture atlas
 * ({@code eternity_devil.png}, made by {@code tools/eternity_devil_atlas.py}, 16 px to the block, nearest-filtered). It has no face. It is a
 * heaving mass of flesh that fills the corridor's end and spills out of the walls, the ceiling and the floor, built from some hundreds of
 * boxes of every size, layered and turned a few degrees each so the outline is ragged although every piece is square: eyes scattered all over
 * it (cuboid balls with a pixel iris, two cuboid lids, each blinking at its own time and looking its own way, most following whoever is near),
 * and mouths of uneven human teeth (cuboid teeth in gums, over a dark throat and a tongue) gaping wherever they like. The heart is a cuboid heart
 * with pale vessels behind a ring of flesh that comes away as the flesh is cut. It is drawn in its own frame: +z toward the players, +y up,
 * the floor at y 0, the corridor 3 wide (x -1.5 to 1.5) and 4 high. Everything is worked out from a seed, so every client sees the same devil.
 */
public class EternityDevilRenderer extends EntityRenderer<EternityDevilEntity> {
	private static final Identifier ATLAS = Identifier.of("chainsawman", "textures/entity/eternity_devil.png");
	/** Flesh pixels to the block: chunkier than the 16 of the eyes and teeth, so the mass holds together at a distance. */
	private static final double TEXEL = 10.0;
	private static final float AW = 256.0F;
	private static final float AH = 384.0F;

	/** A rectangle of the atlas, in pixels. */
	record Tile(int x, int y, int w, int h) {
	}

	private static final Tile[] TOP = new Tile[4];
	private static final Tile[] SIDE = new Tile[4];
	private static final Tile[] BOTTOM = new Tile[4];
	private static final Tile[] IRIS = new Tile[8];
	private static final Tile[] SCLERA = new Tile[8];
	private static final Tile[] TOOTH = new Tile[8];
	private static final Tile[] TOOTH_SIDE = new Tile[8];
	private static final Tile[] GUM = new Tile[3];
	private static final Tile THROAT = new Tile(96, 288, 32, 32);
	private static final Tile TONGUE = new Tile(128, 288, 32, 16);
	private static final Tile HEART = new Tile(128, 192, 64, 64);
	private static final Tile[] VESSEL = {new Tile(192, 192, 16, 16), new Tile(208, 192, 16, 16)};

	static {
		for (int f = 0; f < 4; f++) {
			TOP[f] = new Tile(64 * f, 0, 64, 64);
			SIDE[f] = new Tile(64 * f, 64, 64, 64);
			BOTTOM[f] = new Tile(64 * f, 128, 64, 64);
		}
		for (int v = 0; v < 8; v++) {
			IRIS[v] = new Tile(16 * v, 192, 16, 16);
			SCLERA[v] = new Tile(16 * v, 208, 16, 16);
			TOOTH[v] = new Tile(8 * v, 256, 8, 16);
			TOOTH_SIDE[v] = new Tile(8 * v, 272, 8, 16);
		}
		for (int v = 0; v < 3; v++) {
			GUM[v] = new Tile(32 * v, 288, 32, 16);
		}
	}

	private static final int PALE = 0;
	private static final int RED = 1;
	private static final int DEEP = 2;
	private static final int SICK = 3;

	private static final int FRONT = 0;
	private static final int MASS = 1;
	private static final int SPILL = 2;
	private static final int DOOR = 3;

	/** What stays when a devil is drawn: its pieces, eyes, mouths and tendrils (by seed). */
	private static final Map<Integer, Layout> LAYOUTS = new HashMap<>();
	/** How far open each devil's heart is (0 hidden to 1 open), and a pulse when the heart was hit. */
	private static final Map<Integer, float[]> STATE = new HashMap<>();

	/** One box of flesh: its middle, its size, its turn (degrees), its flesh, and when it grows in. */
	static final class Piece {
		double x;
		double y;
		double z;
		double sx;
		double sy;
		double sz;
		float rx;
		float ry;
		float rz;
		int fam;
		int seed;
		int kind;
		float rank;
		float phase;
		float delay;
		/** A door of the heart's cover slides this way (a unit direction in the wall's plane) as it opens. */
		double ox;
		double oy;
	}

	static final class Eye {
		Vec3d pos;
		Vec3d normal;
		double size;
		int variant;
		float openBase;
		float blinkPeriod;
		float blinkPhase;
		float track;
		float phase;
		float rank;
		boolean cuttable;
		float delay;
		float gx;
		float gy;
	}

	static final class Tooth {
		float x;
		float len;
		float wid;
		float depth;
		float roll;
		float pitch;
		int variant;
	}

	static final class Mouth {
		Vec3d pos;
		Vec3d normal;
		float roll;
		double width;
		float base;
		float speed;
		float chatter;
		float phase;
		int gum;
		int seed;
		float delay;
		Tooth[] upper;
		Tooth[] lower;
	}

	/** A strand of flesh: where it starts, where it lies or hangs, and how it sways. */
	static final class Strand {
		Vec3d from;
		Vec3d to;
		double thick;
		float phase;
		int seg;
		boolean hang;
		Eye stalk;
		float delay;
	}

	static final class Layout {
		final List<Piece> pieces = new ArrayList<>();
		final List<Eye> eyes = new ArrayList<>();
		final List<Mouth> mouths = new ArrayList<>();
		final List<Strand> strands = new ArrayList<>();
	}

	public EternityDevilRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(EternityDevilEntity entity) {
		return ATLAS;
	}

	@Override
	public boolean shouldRender(EternityDevilEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	// ---- The layout.

	private static Layout layout(int seed) {
		return LAYOUTS.computeIfAbsent(seed, EternityDevilRenderer::makeLayout);
	}

	private static Piece piece(Random rnd, int kind, double x, double y, double z, double sx, double sy, double sz, int fam, double turn) {
		Piece p = new Piece();
		p.kind = kind;
		p.x = x;
		p.y = y;
		p.z = z;
		p.sx = sx;
		p.sy = sy;
		p.sz = sz;
		p.fam = fam;
		p.rx = (float) ((rnd.nextDouble() - 0.5) * 2.0 * turn);
		p.ry = (float) ((rnd.nextDouble() - 0.5) * 2.0 * turn);
		p.rz = (float) ((rnd.nextDouble() - 0.5) * 2.0 * turn);
		p.seed = rnd.nextInt();
		p.rank = rnd.nextFloat();
		p.phase = rnd.nextFloat() * 6.28F;
		return p;
	}

	/** Near the heart (in the wall's plane): its cover goes here, and nothing else. */
	private static boolean heartZone(double x, double y, double r) {
		return Math.hypot(x, y - HEART_Y) < r;
	}

	private static final double HEART_Y = 1.3;
	private static final double HEART_Z = 1.45;

	private static Layout makeLayout(int seed) {
		Random rnd = new Random(seed * 7919L + 17L);
		Layout l = new Layout();
		// 1. The dark bulk behind everything, so nothing is seen through the mass.
		for (int i = 0; i < 10; i++) {
			Piece b = piece(rnd, MASS, -1.4 + (i % 5) * 0.72 + rnd.nextDouble() * 0.3, 0.2 + (i / 5) * 1.6 + rnd.nextDouble() * 1.2, -0.9 + rnd.nextDouble() * 0.9,
					1.5 + rnd.nextDouble() * 0.8, 1.5 + rnd.nextDouble() * 0.8, 1.4 + rnd.nextDouble() * 0.6, DEEP, 12.0);
			b.delay = 0.0F;
			l.pieces.add(b);
		}
		// 2. The middle layer: bigger lumps across the wall, a little forward in the middle, leaving the heart's hollow.
		for (double gx = -1.75; gx <= 1.76; gx += 0.55) {
			for (double gy = -0.1; gy <= 3.2; gy += 0.55) {
				double x = gx + (rnd.nextDouble() - 0.5) * 0.3;
				double y = gy + (rnd.nextDouble() - 0.5) * 0.3;
				if (heartZone(x, y, 1.15)) {
					continue;
				}
				double bulge = 0.55 * Math.exp(-(x * x / 1.4 + (y - 2.0) * (y - 2.0) / 3.0));
				Piece p = piece(rnd, MASS, x, y, 0.35 + rnd.nextDouble() * 0.35 + bulge * 0.6, 0.8 + rnd.nextDouble() * 0.5, 0.8 + rnd.nextDouble() * 0.5, 1.0 + rnd.nextDouble() * 0.5,
						rnd.nextInt(3) == 0 ? DEEP : RED, 16.0);
				p.delay = (float) (rnd.nextDouble() * 0.3);
				l.pieces.add(p);
			}
		}
		// A dark backdrop to the heart's hollow.
		{
			Piece p = piece(rnd, MASS, 0.0, HEART_Y, 0.55, 2.2, 2.2, 0.6, DEEP, 0.0);
			l.pieces.add(p);
		}
		// 3. The front: lumps of every size, rows of them pushing out of the ones round them.
		List<Piece> front = new ArrayList<>();
		for (double gx = -1.75; gx <= 1.76; gx += 0.37) {
			for (double gy = -0.1; gy <= 3.2; gy += 0.37) {
				double x = gx + (rnd.nextDouble() - 0.5) * 0.3;
				double y = gy + (rnd.nextDouble() - 0.5) * 0.3;
				if (heartZone(x, y, 1.05)) {
					continue;
				}
				double bulge = 0.55 * Math.exp(-(x * x / 1.3 + (y - 1.9) * (y - 1.9) / 2.4));
				double edge = Math.max(Math.abs(x) - 1.1, 0.0) * 0.55;
				double big = rnd.nextDouble();
				// Big forms are rare; most are small, so the surface is busy; the big ones stand a little proud.
				double s = big < 0.07 ? 1.05 : big < 0.3 ? 0.72 : 0.52;
				double z = 1.0 + rnd.nextDouble() * 0.45 + bulge - edge + (s > 0.6 ? 0.2 : 0.0);
				int fam = pick(rnd, x, y);
				Piece p = piece(rnd, FRONT, x, y, z, s * (0.7 + rnd.nextDouble() * 0.5), s * (0.65 + rnd.nextDouble() * 0.5), 0.5 + s * 0.6, fam, s > 0.6 ? 28.0 : 12.0);
				p.delay = (float) (0.1 + rnd.nextDouble() * 0.35);
				l.pieces.add(p);
				front.add(p);
			}
		}
		// 4. The heart's cover: a ring of flesh boxes that slide out of the way, and a plug of lumps over it.
		for (int i = 0; i < 9; i++) {
			double a = i * Math.PI * 2.0 / 9.0 + rnd.nextDouble() * 0.3;
			double r = 0.42 + rnd.nextDouble() * 0.2;
			Piece p = piece(rnd, DOOR, Math.cos(a) * r, HEART_Y + Math.sin(a) * r * 1.1, 1.65 + rnd.nextDouble() * 0.35, 0.7 + rnd.nextDouble() * 0.35, 0.7 + rnd.nextDouble() * 0.35,
					0.7, pickDoor(rnd), 22.0);
			p.ox = Math.cos(a);
			p.oy = Math.sin(a);
			p.delay = (float) (0.2 + rnd.nextDouble() * 0.2);
			l.pieces.add(p);
		}
		// 5. Knots and warts on the front, folds across it.
		int fronts = front.size();
		for (int i = 0; i < fronts; i++) {
			Piece b = front.get(i);
			int n = rnd.nextInt(3);
			for (int k = 0; k < n; k++) {
				double s = 0.12 + rnd.nextDouble() * 0.2;
				Piece w = piece(rnd, FRONT, b.x + (rnd.nextDouble() - 0.5) * b.sx * 0.9, b.y + (rnd.nextDouble() - 0.4) * b.sy * 0.9, b.z + b.sz * 0.42 + rnd.nextDouble() * 0.1, s,
						s * (0.8 + rnd.nextDouble() * 0.6), s * 0.9, rnd.nextInt(3) == 0 ? PALE : b.fam, 40.0);
				w.rank = b.rank;
				w.delay = b.delay + 0.1F;
				l.pieces.add(w);
			}
		}
		for (int i = 0; i < 24; i++) {
			double x = -1.5 + rnd.nextDouble() * 3.0;
			double y = 0.2 + rnd.nextDouble() * 2.7;
			if (heartZone(x, y, 1.0)) {
				continue;
			}
			Piece f = piece(rnd, FRONT, x, y, 1.75 + rnd.nextDouble() * 0.5, 0.08 + rnd.nextDouble() * 0.08, 0.07 + rnd.nextDouble() * 0.08, 0.6 + rnd.nextDouble() * 0.9,
					rnd.nextInt(3) == 0 ? PALE : DEEP, 0.0);
			f.rz = (float) (rnd.nextDouble() * 180.0);
			f.rx = (float) (rnd.nextDouble() - 0.5) * 30.0F;
			f.delay = 0.35F + rnd.nextFloat() * 0.2F;
			l.pieces.add(f);
		}
		// 6. Mouths first (they carve the front), then the rest of the spill.
		addMouths(l, new Random(seed * 31L + 3));
		// 7. The spill along the floor, the ceiling and the walls.
		spill(l, new Random(seed * 31L + 2));
		// 8. Eyes: everywhere.
		addEyes(l, new Random(seed * 31L + 4), front);
		// 9. Strands lying on the floor and hanging from the ceiling, ending in eyes.
		addStrands(l, new Random(seed * 31L + 5));
		// Carve: the front and middle lumps over a mouth are taken away (its throat is behind).
		l.pieces.removeIf(p -> (p.kind == FRONT || p.kind == MASS) && p.z > 0.2 && coversMouth(l, p));
		return l;
	}

	private static boolean coversMouth(Layout l, Piece p) {
		for (Mouth m : l.mouths) {
			if (Math.abs(m.normal.z) < 0.6) {
				continue;
			}
			double dx = p.x - m.pos.x;
			double dy = p.y - m.pos.y;
			double ca = Math.cos(Math.toRadians(m.roll));
			double sa = Math.sin(Math.toRadians(m.roll));
			double u = dx * ca + dy * sa;
			double w = -dx * sa + dy * ca;
			if (Math.abs(u) < m.width * 0.52 + p.sx * 0.15 && Math.abs(w) < m.width * 0.34 + p.sy * 0.15 && p.z > m.pos.z - 0.6 && p.z < m.pos.z + 0.9) {
				return true;
			}
		}
		return false;
	}

	private static int pick(Random rnd, double x, double y) {
		double r = rnd.nextDouble();
		boolean crease = y < 0.55 || y > 2.6 || Math.abs(x) > 1.25;
		if (crease) {
			return r < 0.55 ? DEEP : r < 0.9 ? RED : PALE;
		}
		return r < 0.34 ? PALE : r < 0.84 ? RED : r < 0.9 ? SICK : DEEP;
	}

	private static int pickDoor(Random rnd) {
		return rnd.nextInt(3) == 0 ? PALE : rnd.nextInt(2) == 0 ? RED : SICK;
	}

	private static void spill(Layout l, Random rnd) {
		// A carpet on the floor: wide flat slabs overlapping, running out down the corridor, thinning as they go.
		for (int i = 0; i < 16; i++) {
			double z = 1.8 + i * 0.34 + rnd.nextDouble() * 0.3;
			double fade = Math.max(0.35, 1.0 - (z - 1.8) / 6.5);
			double x = (rnd.nextDouble() - 0.5) * 2.6;
			Piece p = piece(rnd, SPILL, x, 0.05 + rnd.nextDouble() * 0.04, z, (0.8 + rnd.nextDouble() * 0.9) * fade + 0.3, 0.12 + rnd.nextDouble() * 0.1, 0.7 + rnd.nextDouble() * 0.8,
					rnd.nextInt(4) == 0 ? SICK : rnd.nextBoolean() ? DEEP : RED, 5.0);
			p.rx *= 0.3F;
			p.rz *= 0.3F;
			p.delay = (float) (0.2 + (z - 1.8) * 0.08);
			l.pieces.add(p);
		}
		// Low lumps on the carpet, mostly by the walls.
		for (int i = 0; i < 7; i++) {
			double z = 1.9 + i * 0.7 + rnd.nextDouble() * 0.3;
			double fade = Math.max(0.35, 1.0 - (z - 1.8) / 6.0);
			double side = rnd.nextBoolean() ? -1.0 : 1.0;
			double x = side * (1.0 + rnd.nextDouble() * 0.45);
			double sy = (0.15 + rnd.nextDouble() * 0.2) * fade;
			Piece p = piece(rnd, SPILL, x, sy * 0.4, z, (0.4 + rnd.nextDouble() * 0.5) * fade + 0.15, sy, 0.4 + rnd.nextDouble() * 0.5, rnd.nextInt(3) == 0 ? PALE : RED, 16.0);
			p.delay = (float) (0.25 + (z - 1.8) * 0.08);
			l.pieces.add(p);
		}
		// The ceiling: slabs sagging out of it, lumps hanging, and drips of flesh at their tips.
		for (int i = 0; i < 8; i++) {
			double z = 1.4 + i * 0.8 + rnd.nextDouble() * 0.5;
			double fade = Math.max(0.5, 1.0 - (z - 1.4) / 8.0);
			Piece p = piece(rnd, SPILL, (rnd.nextDouble() - 0.5) * 2.4, 2.95 - 0.15 * fade, z, (0.9 + rnd.nextDouble() * 0.8) * fade, (0.3 + rnd.nextDouble() * 0.35) * fade, 0.8 + rnd.nextDouble() * 0.7,
					rnd.nextBoolean() ? DEEP : RED, 6.0);
			p.delay = (float) (0.2 + (z - 1.4) * 0.07);
			l.pieces.add(p);
		}
		for (int i = 0; i < 16; i++) {
			double z = 1.5 + i * 0.34 + rnd.nextDouble() * 0.4;
			double fade = Math.max(0.4, 1.0 - (z - 1.5) / 6.5);
			double x = (rnd.nextDouble() - 0.5) * 3.0;
			double h = (0.25 + rnd.nextDouble() * 0.6) * fade;
			double w = (0.2 + rnd.nextDouble() * 0.4) * fade + 0.1;
			Piece p = piece(rnd, SPILL, x, 2.85 - h / 2, z, w, h, w * (0.8 + rnd.nextDouble() * 0.6), rnd.nextInt(3) == 0 ? SICK : rnd.nextInt(2) == 0 ? RED : PALE, 12.0);
			p.delay = (float) (0.2 + (z - 1.5) * 0.08);
			l.pieces.add(p);
			if (h > 0.4) {
				Piece t = piece(rnd, SPILL, x + (rnd.nextDouble() - 0.5) * 0.1, 2.85 - h - 0.1, z, w * 0.5, 0.25 + rnd.nextDouble() * 0.2, w * 0.5, p.fam, 10.0);
				t.delay = p.delay + 0.04F;
				l.pieces.add(t);
			}
		}
		// The walls: big swellings pushing out of both, the corridor narrowing as they come, some stacked on others.
		for (int s = -1; s <= 1; s += 2) {
			for (int i = 0; i < 12; i++) {
				double z = 1.2 + i * 0.5 + rnd.nextDouble() * 0.4;
				double fade = Math.max(0.45, 1.0 - (z - 1.0) / 8.0);
				double out = (0.25 + rnd.nextDouble() * 0.4) * fade + 0.1;
				double y = 0.25 + rnd.nextDouble() * 2.5;
				double sz = (0.6 + rnd.nextDouble() * 0.9) * fade + 0.2;
				double sy = (0.5 + rnd.nextDouble() * 0.9) * fade + 0.15;
				Piece p = piece(rnd, SPILL, s * (1.55 - out / 2.0), y, z, out + 0.1, sy, sz, rnd.nextInt(3) == 0 ? SICK : rnd.nextInt(2) == 0 ? RED : PALE, 14.0);
				p.delay = (float) (0.2 + (z - 0.9) * 0.07);
				l.pieces.add(p);
				if (rnd.nextInt(2) == 0) {
					double o2 = out * 0.7;
					Piece q = piece(rnd, SPILL, s * (1.55 - out - o2 / 2.0 + 0.08), y + (rnd.nextDouble() - 0.5) * sy, z + (rnd.nextDouble() - 0.5) * sz * 0.6, o2, sy * 0.55, sz * 0.5,
							rnd.nextBoolean() ? PALE : DEEP, 22.0);
					q.delay = p.delay + 0.05F;
					l.pieces.add(q);
				}
			}
		}
	}

	private static Mouth mouth(Random rnd, double x, double y, double z, Vec3d normal, float roll, double width, float base, int seed) {
		Mouth m = new Mouth();
		m.pos = new Vec3d(x, y, z);
		m.normal = normal.normalize();
		m.roll = roll;
		m.width = width;
		m.base = base;
		m.speed = 0.04F + rnd.nextFloat() * 0.05F;
		m.chatter = rnd.nextFloat() < 0.5F ? 0.0F : 0.5F + rnd.nextFloat() * 0.6F;
		m.phase = rnd.nextFloat() * 6.28F;
		m.gum = rnd.nextInt(3);
		m.seed = seed;
		m.delay = 0.35F + rnd.nextFloat() * 0.2F;
		m.upper = teeth(rnd, width, true);
		m.lower = teeth(rnd, width, false);
		return m;
	}

	private static Tooth[] teeth(Random rnd, double width, boolean upper) {
		int n = MathHelper.clamp((int) Math.round(width * 9.0), 6, 14);
		List<Tooth> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (rnd.nextFloat() < 0.1F) {
				continue;
			}
			Tooth t = new Tooth();
			float f = (i + 0.5F) / n * 2.0F - 1.0F;
			float a = Math.abs(f);
			t.x = (float) ((f * 0.46 + (rnd.nextFloat() - 0.5F) * 0.03) * width);
			float cell = (float) (0.92 * width / n);
			if (a < 0.3F) {
				t.wid = cell * (0.95F + rnd.nextFloat() * 0.25F);
				t.len = (float) (width * (upper ? 0.2 : 0.16) * (0.8 + rnd.nextFloat() * 0.5));
			} else if (a < 0.6F) {
				t.wid = cell * (0.65F + rnd.nextFloat() * 0.25F);
				t.len = (float) (width * 0.24 * (0.8 + rnd.nextFloat() * 0.6));
			} else {
				t.wid = cell * (1.05F + rnd.nextFloat() * 0.3F);
				t.len = (float) (width * 0.12 * (0.8 + rnd.nextFloat() * 0.5));
			}
			if (rnd.nextFloat() < 0.12F) {
				t.len *= 1.5F;
			}
			t.depth = (float) (width * (0.07 + rnd.nextDouble() * 0.04));
			t.roll = (rnd.nextFloat() - 0.5F) * 34.0F;
			t.pitch = (rnd.nextFloat() - 0.5F) * 20.0F;
			t.variant = rnd.nextInt(8);
			out.add(t);
		}
		return out.toArray(new Tooth[0]);
	}

	private static void addMouths(Layout l, Random rnd) {
		// On the front: a big one low and left, one tilted high on the right, a sideways one, a small one, a vertical one low right.
		l.mouths.add(mouth(rnd, -0.7, 2.35, 1.95, new Vec3d(0.12, -0.05, 1.0), 8.0F, 1.5, 0.75F, 11));
		l.mouths.add(mouth(rnd, 1.15, 0.55, 1.95, new Vec3d(-0.25, 0.1, 1.0), -70.0F, 1.1, 0.6F, 23));
		l.mouths.add(mouth(rnd, -1.1, 0.6, 1.9, new Vec3d(0.25, 0.1, 1.0), 95.0F, 1.0, 0.55F, 37));
		l.mouths.add(mouth(rnd, 1.1, 2.3, 1.8, new Vec3d(-0.2, -0.1, 1.0), -28.0F, 0.95, 0.65F, 41));
		l.mouths.add(mouth(rnd, -1.25, 1.5, 2.0, new Vec3d(0.3, 0.0, 1.0), -65.0F, 0.65, 0.4F, 43));
		l.mouths.add(mouth(rnd, 0.35, 0.3, 2.3, new Vec3d(0.0, 0.2, 1.0), 12.0F, 0.6, 0.35F, 47));
		// Out of the walls, the ceiling and the floor.
		l.mouths.add(mouth(rnd, -1.38, 1.9, 3.1, new Vec3d(1.0, 0.0, 0.25), 80.0F, 0.95, 0.4F, 53));
		l.mouths.add(mouth(rnd, 1.38, 2.6, 4.3, new Vec3d(-1.0, 0.0, 0.2), -20.0F, 0.8, 0.35F, 59));
		l.mouths.add(mouth(rnd, -0.2, 2.95, 3.0, new Vec3d(0.0, -1.0, 0.1), 0.0F, 0.9, 0.5F, 61));
		l.mouths.add(mouth(rnd, 0.55, 0.25, 3.5, new Vec3d(0.0, 1.0, 0.1), 10.0F, 0.7, 0.4F, 67));
	}

	private static void addEyes(Layout l, Random rnd, List<Piece> front) {
		List<Piece> cand = new ArrayList<>();
		for (Piece p : front) {
			if (p.kind == FRONT && p.sx > 0.35) {
				cand.add(p);
			}
		}
		// Huge ones, not above one another, not level, none over a mouth; the rest all sizes, on the front's outermost surface.
		double[][] big = {{1.25, 1.5, 0.85}, {-0.45, 2.7, 0.6}, {-1.3, 1.0, 0.7}, {1.3, 2.45, 0.5}, {-0.9, 0.3, 0.45}, {0.8, 0.3, 0.4}};
		for (double[] b : big) {
			Piece host = nearest(cand, b[0], b[1]);
			Vec3d n = new Vec3d(-b[0] * 0.15, 0.05, 1.0);
			addEye(l, rnd, host, b[2], b[0], b[1], surfaceZ(l, b[0], b[1]) + 0.12, n);
		}
		int placed = 0;
		for (int i = 0; i < 900 && placed < 95; i++) {
			double x = -1.6 + rnd.nextDouble() * 3.2;
			double y = 0.15 + rnd.nextDouble() * 2.8;
			double size = 0.18 + rnd.nextDouble() * rnd.nextDouble() * 0.5;
			if (heartZone(x, y, 1.3) || nearMouth(l, x, y, size)) {
				continue;
			}
			boolean clear = true;
			for (Eye o : l.eyes) {
				if (Math.hypot(o.pos.x - x, o.pos.y - y) < (o.size + size) * 0.55) {
					clear = false;
					break;
				}
			}
			if (!clear) {
				continue;
			}
			Piece host = nearest(cand, x, y);
			Vec3d n = new Vec3d((rnd.nextDouble() - 0.5) * 0.8 - x * 0.15, (rnd.nextDouble() - 0.3) * 0.7, 1.0).normalize();
			addEye(l, rnd, host, size, x, y, surfaceZ(l, x, y) + 0.1, n);
			placed++;
		}
		// On the walls, ceiling and floor spill.
		int added = 0;
		for (Piece p : new ArrayList<>(l.pieces)) {
			if (p.kind != SPILL || added >= 14 || rnd.nextInt(5) != 0) {
				continue;
			}
			double size = 0.12 + rnd.nextDouble() * 0.3;
			Vec3d n;
			double x = p.x;
			double y = p.y;
			double z = p.z;
			if (Math.abs(p.x) > 1.0) {
				n = new Vec3d(-Math.signum(p.x), 0.1, 0.3).normalize();
				x = p.x - Math.signum(p.x) * p.sx * 0.45;
				z = p.z + (rnd.nextDouble() - 0.5) * p.sz * 0.4;
			} else if (p.y > 2.3) {
				n = new Vec3d(0.0, -1.0, 0.45).normalize();
				y = p.y - p.sy * 0.42;
			} else {
				n = new Vec3d(0.0, 1.0, 0.3).normalize();
				y = p.y + p.sy * 0.46;
			}
			Eye e = addEye(l, rnd, p, size, x, y, z, n);
			e.cuttable = false;
			added++;
		}
	}

	/** The z of the front's outermost surface at (x, y). */
	private static double surfaceZ(Layout l, double x, double y) {
		double z = 1.3;
		for (Piece p : l.pieces) {
			if ((p.kind == FRONT || p.kind == DOOR) && Math.abs(x - p.x) < p.sx * 0.4 && Math.abs(y - p.y) < p.sy * 0.4) {
				z = Math.max(z, p.z + p.sz * 0.38);
			}
		}
		return z;
	}

	private static boolean nearMouth(Layout l, double x, double y, double size) {
		for (Mouth m : l.mouths) {
			if (Math.abs(m.normal.z) > 0.6 && Math.hypot(x - m.pos.x, y - m.pos.y) < m.width * 0.62 + size * 0.5) {
				return true;
			}
		}
		return false;
	}

	private static boolean overlapsMouth(Layout l, Piece p) {
		for (Mouth m : l.mouths) {
			if (Math.abs(m.normal.z) > 0.6 && Math.hypot(p.x - m.pos.x, p.y - m.pos.y) < m.width * 0.75) {
				return true;
			}
		}
		return false;
	}

	private static Piece nearest(List<Piece> cand, double x, double y) {
		Piece best = null;
		double bd = 1.0E9;
		for (Piece p : cand) {
			double d = Math.hypot(p.x - x, p.y - y);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	private static Eye addEye(Layout l, Random rnd, Piece host, double size, double x, double y, double z, Vec3d normal) {
		Eye e = new Eye();
		e.pos = new Vec3d(x, y, z);
		e.normal = normal.normalize();
		e.size = size;
		e.variant = rnd.nextInt(8);
		e.openBase = 0.5F + rnd.nextFloat() * 0.5F;
		e.blinkPeriod = 70.0F + rnd.nextFloat() * 160.0F;
		e.blinkPhase = rnd.nextFloat() * 300.0F;
		e.track = rnd.nextFloat() < 0.7F ? 0.9F : 0.1F;
		e.phase = rnd.nextFloat() * 20.0F;
		e.rank = host != null ? host.rank : rnd.nextFloat();
		e.cuttable = host != null && host.kind == FRONT;
		e.delay = host != null ? host.delay + 0.12F : 0.4F;
		e.gx = (rnd.nextFloat() - 0.5F) * 2.0F;
		e.gy = (rnd.nextFloat() - 0.5F) * 2.0F;
		l.eyes.add(e);
		return e;
	}

	private static void addStrands(Layout l, Random rnd) {
		// Lying on the floor, out of the mass, winding down the corridor.
		for (int i = 0; i < 6; i++) {
			Strand s = new Strand();
			s.from = new Vec3d(-1.2 + i * 0.5 + (rnd.nextDouble() - 0.5) * 0.2, 0.3, 1.8);
			s.to = new Vec3d(s.from.x + (rnd.nextDouble() - 0.5) * 1.2, 0.12, 4.0 + rnd.nextDouble() * 3.5);
			s.thick = 0.15 + rnd.nextDouble() * 0.08;
			s.phase = rnd.nextFloat() * 6.28F;
			s.seg = 22;
			s.delay = 0.4F;
			l.strands.add(s);
		}
		// Hanging from the ceiling, ending in an eye each.
		for (int i = 0; i < 5; i++) {
			Strand s = new Strand();
			s.hang = true;
			double z = 1.8 + i * 1.0 + rnd.nextDouble() * 0.6;
			double x = (i % 2 == 0 ? -1.0 : 1.0) * (0.75 + rnd.nextDouble() * 0.55);
			s.from = new Vec3d(x, 3.0, z);
			s.to = new Vec3d(x + (rnd.nextDouble() - 0.5) * 0.4, 3.0 - (0.7 + rnd.nextDouble() * 0.7), z + (rnd.nextDouble() - 0.5) * 0.4);
			s.thick = 0.08 + rnd.nextDouble() * 0.04;
			s.phase = rnd.nextFloat() * 6.28F;
			s.seg = 9;
			s.delay = 0.45F + i * 0.05F;
			Eye e = new Eye();
			e.pos = s.to;
			e.normal = new Vec3d(0.0, -0.2, 1.0).normalize();
			e.size = 0.26 + rnd.nextDouble() * 0.3;
			e.variant = rnd.nextInt(8);
			e.openBase = 0.6F + rnd.nextFloat() * 0.4F;
			e.blinkPeriod = 70.0F + rnd.nextFloat() * 160.0F;
			e.blinkPhase = rnd.nextFloat() * 300.0F;
			e.track = 0.9F;
			e.phase = rnd.nextFloat() * 20.0F;
			e.delay = s.delay;
			s.stalk = e;
			l.strands.add(s);
		}
	}

	// ---- The drawing of boxes.

	private static void tile(ForeverGeo.Pen p, double[] a, double[] b, double[] c, double[] d, double nx, double ny, double nz, Tile t, double fa, double fb, boolean fixed, int seed, int face) {
		float u0;
		float v0;
		float u1;
		float v1;
		if (fixed) {
			u0 = t.x() + 0.02F;
			v0 = t.y() + 0.02F;
			u1 = t.x() + t.w() - 0.02F;
			v1 = t.y() + t.h() - 0.02F;
		} else {
			int w = MathHelper.clamp((int) Math.round(fa * TEXEL), 3, t.w());
			int h = MathHelper.clamp((int) Math.round(fb * TEXEL), 3, t.h());
			int hash = (seed * 31 + face * 17) & 0x7FFFFFFF;
			int ox = t.w() > w ? hash % (t.w() - w + 1) : 0;
			int oy = t.h() > h ? (hash / 64) % (t.h() - h + 1) : 0;
			u0 = t.x() + ox + 0.02F;
			v0 = t.y() + oy + 0.02F;
			u1 = t.x() + ox + w - 0.02F;
			v1 = t.y() + oy + h - 0.02F;
		}
		u0 /= AW;
		u1 /= AW;
		v0 /= AH;
		v1 /= AH;
		p.v(a[0], a[1], a[2], u0, v1, nx, ny, nz);
		p.v(b[0], b[1], b[2], u1, v1, nx, ny, nz);
		p.v(c[0], c[1], c[2], u1, v0, nx, ny, nz);
		p.v(d[0], d[1], d[2], u0, v0, nx, ny, nz);
	}

	/**
	 * A box about the origin, half-sizes {@code hx, hy, hz}: {@code front} on its two z faces, {@code side} on its x faces, {@code top} and
	 * {@code bottom}. {@code fixed} puts the whole of each tile on the face (teeth, eyes); otherwise a piece of the tile the size of the face.
	 */
	private static void cuboid(ForeverGeo.Pen p, double hx, double hy, double hz, Tile front, Tile side, Tile top, Tile bottom, boolean fixed, int seed) {
		if (hx < 0.004 || hy < 0.004 || hz < 0.004) {
			return;
		}
		double[] a = {-hx, -hy, hz};
		double[] b = {hx, -hy, hz};
		double[] c = {hx, hy, hz};
		double[] d = {-hx, hy, hz};
		tile(p, a, b, c, d, 0, 0, 1, front, 2 * hx, 2 * hy, fixed, seed, 0);
		a = new double[] {hx, -hy, -hz};
		b = new double[] {-hx, -hy, -hz};
		c = new double[] {-hx, hy, -hz};
		d = new double[] {hx, hy, -hz};
		tile(p, a, b, c, d, 0, 0, -1, front, 2 * hx, 2 * hy, fixed, seed, 1);
		a = new double[] {hx, -hy, hz};
		b = new double[] {hx, -hy, -hz};
		c = new double[] {hx, hy, -hz};
		d = new double[] {hx, hy, hz};
		tile(p, a, b, c, d, 1, 0, 0, side, 2 * hz, 2 * hy, fixed, seed, 2);
		a = new double[] {-hx, -hy, -hz};
		b = new double[] {-hx, -hy, hz};
		c = new double[] {-hx, hy, hz};
		d = new double[] {-hx, hy, -hz};
		tile(p, a, b, c, d, -1, 0, 0, side, 2 * hz, 2 * hy, fixed, seed, 3);
		a = new double[] {-hx, hy, hz};
		b = new double[] {hx, hy, hz};
		c = new double[] {hx, hy, -hz};
		d = new double[] {-hx, hy, -hz};
		tile(p, a, b, c, d, 0, 1, 0, top, 2 * hx, 2 * hz, fixed, seed, 4);
		a = new double[] {-hx, -hy, -hz};
		b = new double[] {hx, -hy, -hz};
		c = new double[] {hx, -hy, hz};
		d = new double[] {-hx, -hy, hz};
		tile(p, a, b, c, d, 0, -1, 0, bottom, 2 * hx, 2 * hz, fixed, seed, 5);
	}

	private static void flesh(ForeverGeo.Pen p, double hx, double hy, double hz, int fam, int seed) {
		cuboid(p, hx, hy, hz, SIDE[fam], SIDE[fam], TOP[fam], BOTTOM[fam], false, seed);
	}

	private static void turn(MatrixStack m, float rx, float ry, float rz) {
		m.multiply(new Quaternionf().rotationZYX((float) Math.toRadians(rz), (float) Math.toRadians(ry), (float) Math.toRadians(rx)));
	}

	// ---- Drawing.

	private static final class Ctx {
		MatrixStack matrices;
		VertexConsumer vc;
		int lit;
		int bright;
		float now;
		float grown;
		float death;
		boolean dead;
		float cut;
		float open;
		float hit;
		float flinch;
		Vec3d gaze;

		ForeverGeo.Pen pen() {
			return new ForeverGeo.Pen(vc, matrices.peek(), lit);
		}

		ForeverGeo.Pen pen(int light) {
			return new ForeverGeo.Pen(vc, matrices.peek(), light);
		}
	}

	@Override
	public void render(EternityDevilEntity devil, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(devil.getWorld() instanceof ClientWorld world)) {
			return;
		}
		Layout layout = layout(devil.born());
		Ctx c = new Ctx();
		c.matrices = matrices;
		c.vc = provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ATLAS));
		c.now = world.getTime() + delta;
		c.grown = ease((devil.age + delta) / EternityDevilEntity.GROW_TICKS);
		float frac = MathHelper.clamp(devil.getHealth() / devil.getMaxHealth(), 0.0F, 1.0F);
		c.dead = devil.isDead();
		c.death = c.dead ? ease((devil.deathTime + delta) / EternityDevilEntity.DEATH_TICKS) : 0.0F;
		float[] state = STATE.computeIfAbsent(devil.getId(), k -> new float[] {0.0F, -100.0F, -100.0F});
		float wantOpen = devil.heartOut() ? 1.0F : 0.0F;
		state[0] += MathHelper.clamp(wantOpen - state[0], -0.03F, 0.045F);
		List<ClientHotel.Event> events = ClientHotel.events(devil.getId());
		for (ClientHotel.Event e : events) {
			if (e.kind() == HotelPayloads.Devil.HEART && e.ticks() > 0) {
				state[1] = Math.max(state[1], e.start());
			} else if (e.kind() == HotelPayloads.Devil.REGEN) {
				state[2] = Math.max(state[2], e.start());
			}
		}
		c.open = state[0];
		c.hit = MathHelper.clamp(1.0F - (c.now - state[1]) / 8.0F, 0.0F, 1.0F);
		float regen = MathHelper.clamp(1.0F - (c.now - state[2]) / 14.0F, 0.0F, 1.0F);
		c.flinch = devil.hurtTime > 0 ? devil.hurtTime / 10.0F : 0.0F;
		c.cut = 1.0F - frac;
		c.lit = ForeverGeo.lit(light, 13, 11);
		c.bright = LightmapTextureManager.pack(14, 11);

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-devil.facing()));
		float bulk = (0.95F + 0.05F * frac) * (1.0F + 0.035F * regen * MathHelper.sin(c.now * 0.9F)) * (1.0F - 0.03F * c.flinch);
		float sag = 1.0F - 0.55F * c.death;
		matrices.scale(bulk, bulk * sag, bulk);
		c.gaze = gaze(devil, delta);

		for (Piece p : layout.pieces) {
			drawPiece(c, p);
		}
		if (c.open > 0.02F && !c.dead) {
			heart(c);
		}
		for (Mouth m : layout.mouths) {
			drawMouth(c, m);
		}
		for (Eye e : layout.eyes) {
			if (e.cuttable && !c.dead && e.rank < c.cut * 0.5F) {
				continue;
			}
			drawEye(c, e, 1.0F);
		}
		for (Strand s : layout.strands) {
			drawStrand(c, s);
		}
		for (ClientHotel.Event e : events) {
			attack(devil, e, c);
		}
		matrices.pop();
	}

	/** The eye of the one looking, in this devil's frame. */
	private static Vec3d gaze(EternityDevilEntity devil, float delta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return new Vec3d(0.0, 1.7, 8.0);
		}
		Vec3d eye = client.player.getCameraPosVec(delta).subtract(devil.getLerpedPos(delta));
		Vector3f v = new Vector3f((float) eye.x, (float) eye.y, (float) eye.z).rotateY((float) Math.toRadians(devil.facing()));
		return new Vec3d(v.x, v.y, v.z);
	}

	private static void drawPiece(Ctx c, Piece p) {
		if (c.dead ? false : p.kind == FRONT && p.rank < c.cut * 0.5F) {
			return;
		}
		float g = ease(c.grown * 1.55F - p.delay * 0.9F);
		if (g <= 0.01F) {
			return;
		}
		float breath = c.dead ? 1.0F : 1.0F + 0.04F * MathHelper.sin(c.now * 0.06F + p.phase) + 0.015F * MathHelper.sin(c.now * 0.17F + p.phase * 2.0F);
		double x = p.x;
		double y = p.y;
		double z = p.z;
		float tx = 0.0F;
		float s = g * breath;
		if (p.kind == DOOR) {
			float o = c.open;
			x += p.ox * o * 1.05;
			y += p.oy * o * 1.05;
			z -= 0.1 * o;
			tx = o * 30.0F * (float) p.ox;
			s *= 1.0F - 0.1F * o;
		}
		// Grown out of the wall: starts sunk in the back and spread out.
		double zz = MathHelper.lerp(g, -1.0, z) + 0.035 * MathHelper.sin(c.now * 0.06F + p.phase);
		double xx = x * (0.7 + 0.3 * g);
		c.matrices.push();
		c.matrices.translate(xx, y, zz);
		turn(c.matrices, p.rx + tx, p.ry, p.rz);
		// Shaded by depth and by how near the floor it is: the crevices go dark, what stands proud stays bright.
		float k = p.kind == SPILL ? 0.9F : MathHelper.clamp(0.5F + 0.42F * (float) z, 0.62F, 1.0F);
		if (p.y < 0.5) {
			k *= 0.82F;
		}
		flesh(c.pen().tint(k, k * 0.96F, k * 0.96F, 1.0F), p.sx * 0.5 * s, p.sy * 0.5 * s, p.sz * 0.5 * s, p.fam, p.seed);
		c.matrices.pop();
	}

	// ---- Eyes.

	private static void drawEye(Ctx c, Eye e, float extraOpen) {
		float wake = c.grown < 0.4F ? 0.0F : ease((c.grown - 0.4F) / 0.4F - e.delay * 0.3F);
		float blink;
		float t = (c.now + e.blinkPhase) % e.blinkPeriod;
		blink = t < 7.0F ? 1.0F - (float) Math.sin(t / 7.0F * Math.PI) : 1.0F;
		float open = e.openBase * blink * wake * extraOpen * (c.dead ? 1.0F - c.death : 1.0F);
		double sz = e.size * (0.6 + 0.4 * wake) * (1.0 + 0.02 * MathHelper.sin(c.now * 0.07F + e.phase));
		if (open <= 0.01F && wake <= 0.05F) {
			return;
		}
		double slide = (1.0 - ease(c.grown * 1.55F - e.delay * 0.9F)) * 1.0;
		Quaternionf q = new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, (float) e.normal.x, (float) e.normal.y, (float) e.normal.z);
		Quaternionf inv = new Quaternionf(q).conjugate();
		// Where it looks, in its own frame: at whoever is near (most of them), or about, and never away from the front.
		Vec3d to = c.gaze.subtract(e.pos);
		Vector3f lv = new Vector3f((float) to.x, (float) to.y, (float) to.z);
		lv.normalize();
		inv.transform(lv);
		float wx = MathHelper.sin(c.now * 0.021F + e.phase) * 0.8F + e.gx * 0.5F;
		float wy = MathHelper.sin(c.now * 0.017F + e.phase * 1.7F) * 0.55F + e.gy * 0.35F;
		Vector3f wander = new Vector3f(wx, wy, 1.0F).normalize();
		lv.mul(e.track).add(wander.mul(1.0F - e.track)).normalize();
		if (lv.z < 0.5F) {
			lv.x *= 0.5F / Math.max(0.05F, lv.z) * 0.6F;
			lv.y *= 0.5F / Math.max(0.05F, lv.z) * 0.6F;
			lv.z = 0.5F;
			lv.normalize();
		}
		Quaternionf look = new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, lv.x, lv.y, lv.z);
		c.matrices.push();
		c.matrices.translate(e.pos.x * (0.75 + 0.25 * ease(c.grown * 1.55F - e.delay * 0.9F)), e.pos.y, e.pos.z + (-slide));
		c.matrices.multiply(q);
		// The ball: two boxes, one at 45 degrees (an octagon), and the plate with the iris on it, turned to look.
		c.matrices.push();
		c.matrices.multiply(look);
		int light = c.bright;
		double r = sz * 0.5;
		for (int k = 0; k < 2; k++) {
			c.matrices.push();
			if (k == 1) {
				turn(c.matrices, 0.0F, 0.0F, 45.0F);
			}
			Tile body = SCLERA[e.variant];
			cuboid(c.pen(light), r * 0.78, r * 0.78, r * 0.78, body, body, body, body, true, e.variant);
			c.matrices.pop();
		}
		c.matrices.push();
		c.matrices.translate(0.0, 0.0, r * 0.76);
		cuboid(c.pen(light), r * 0.97, r * 0.97, r * 0.05, IRIS[e.variant], IRIS[e.variant], IRIS[e.variant], IRIS[e.variant], true, e.variant);
		c.matrices.pop();
		c.matrices.pop();
		// Lids: two thin slabs across the front that close from the top and bottom edges toward the middle; the corners of the socket.
		float cover = 1.0F - MathHelper.clamp(open, 0.0F, 1.0F);
		Tile lid = TOP[e.variant % 2 == 0 ? RED : PALE];
		for (int s = -1; s <= 1; s += 2) {
			double edge = s > 0 ? r * (0.98 - 1.0 * cover) : -r * (0.98 - 0.8 * cover);
			double hh = r * (s > 0 ? 0.3 : 0.2);
			c.matrices.push();
			c.matrices.translate(0.0, edge + s * hh, r * 0.88);
			turn(c.matrices, 0.0F, 0.0F, (e.variant - 3.5F) * 2.0F);
			cuboid(c.pen(), r * 1.0, hh, r * 0.1, lid, lid, lid, SIDE[RED], false, e.variant * 7 + s);
			c.matrices.pop();
		}
		for (int s = -1; s <= 1; s += 2) {
			c.matrices.push();
			c.matrices.translate(s * r * 1.08, 0.0, r * 0.2);
			flesh(c.pen(), r * 0.25, r * 0.7, r * 0.5, RED, e.variant * 5 + s);
			c.matrices.pop();
		}
		c.matrices.pop();
	}

	// ---- Mouths.

	private static void drawMouth(Ctx c, Mouth m) {
		float g = ease(c.grown * 1.55F - m.delay * 0.9F);
		if (g <= 0.02F) {
			return;
		}
		float gape = m.base + 0.12F * MathHelper.sin(c.now * m.speed + m.phase) + 0.1F * MathHelper.sin(c.now * m.speed * 2.3F + m.phase * 2.0F) + 0.2F * c.flinch + 0.25F * c.open;
		if (m.chatter > 0.0F) {
			gape += m.chatter * 0.2F * Math.max(0.0F, MathHelper.sin(c.now * 0.7F + m.phase * 3.0F)) * Math.max(0.0F, MathHelper.sin(c.now * 0.05F + m.phase));
		}
		if (c.dead) {
			gape = Math.min(1.0F, gape + 0.5F * c.death);
		}
		c.matrices.push();
		double slide = (1.0 - g) * 1.0;
		c.matrices.translate(m.pos.x * (0.75 + 0.25 * g), m.pos.y, m.pos.z - slide);
		c.matrices.multiply(new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, (float) m.normal.x, (float) m.normal.y, (float) m.normal.z));
		c.matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(m.roll));
		mouthModel(c, m, MathHelper.clamp(gape, 0.02F, 1.0F), (float) g, c.lit);
		c.matrices.pop();
	}

	/** One mouth in its own frame: +z out of the flesh, x its width, y the way it opens. */
	private static void mouthModel(Ctx c, Mouth m, float gape, float g, int light) {
		double w = m.width * (0.6 + 0.4 * g);
		double hh = 0.3 * w;
		double h = 0.015 * w + gape * hh;
		// The throat and its back: dark, with a tongue lying in it.
		c.matrices.push();
		c.matrices.translate(0.0, 0.0, -0.22 * w);
		cuboid(c.pen(light), 0.52 * w, hh * 1.25 + 0.04 * w, 0.2 * w, THROAT, THROAT, THROAT, THROAT, true, 1);
		c.matrices.pop();
		// The raw rim round it: ragged boxes that stand off the flesh.
		Random r = new Random(m.seed);
		for (int i = 0; i < 6; i++) {
			c.matrices.push();
			double a = i / 6.0 * Math.PI * 2.0 + 0.4;
			double rx = Math.cos(a) * w * 0.58;
			double ry = Math.sin(a) * (h + 0.2 * w) * 1.0;
			c.matrices.translate(rx, ry, -0.04 * w);
			turn(c.matrices, 0.0F, 0.0F, (float) Math.toDegrees(a) + (float) (r.nextDouble() - 0.5) * 30.0F);
			flesh(c.pen(light), 0.12 * w, 0.2 * w * (0.7 + r.nextDouble() * 0.6), 0.14 * w, i % 2 == 0 ? RED : DEEP, m.seed + i);
			c.matrices.pop();
		}
		for (int s = -1; s <= 1; s += 2) {
			c.matrices.push();
			c.matrices.translate(0.0, s * (h + 0.22 * w), -0.03 * w);
			flesh(c.pen(light), 0.66 * w, 0.14 * w, 0.14 * w, s > 0 ? PALE : SICK, m.seed * 3 + s);
			c.matrices.pop();
		}
		// The jaws.
		for (int s = -1; s <= 1; s += 2) {
			Tooth[] ts = s > 0 ? m.upper : m.lower;
			c.matrices.push();
			c.matrices.translate(0.0, s * h, 0.0);
			if (s < 0) {
				c.matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-gape * 6.0F));
			}
			// The gum.
			c.matrices.push();
			c.matrices.translate(0.0, s * 0.07 * w, 0.02 * w);
			cuboid(c.pen(light), 0.5 * w, 0.07 * w, 0.1 * w, GUM[m.gum], GUM[m.gum], GUM[m.gum], GUM[m.gum], true, 2);
			c.matrices.pop();
			for (Tooth t : ts) {
				c.matrices.push();
				c.matrices.translate(t.x * (float) (w / m.width), -s * 0.0, 0.035 * w);
				c.matrices.translate(0.0, -s * (t.len * 0.5 * w / m.width - 0.01 * w), 0.0);
				turn(c.matrices, t.pitch, 0.0F, t.roll);
				double k = w / m.width;
				cuboid(c.pen(light), t.wid * 0.5 * k, t.len * 0.5 * k, t.depth * 0.5 * k, TOOTH[t.variant], TOOTH_SIDE[t.variant], TOOTH_SIDE[t.variant], TOOTH_SIDE[t.variant], true, 3);
				c.matrices.pop();
			}
			if (s < 0 && gape > 0.12F) {
				c.matrices.push();
				c.matrices.translate(0.0, 0.03 * w + 0.01 * w * MathHelper.sin(c.now * 0.09F + m.phase), -0.05 * w);
				turn(c.matrices, 0.0F, 0.0F, 6.0F * MathHelper.sin(c.now * 0.05F + m.phase));
				cuboid(c.pen(light), 0.3 * w, 0.05 * w, 0.2 * w, TONGUE, TONGUE, TONGUE, TONGUE, true, 4);
				c.matrices.pop();
			}
			c.matrices.pop();
		}
	}

	// ---- Strands.

	private void drawStrand(Ctx c, Strand s) {
		float g = ease(c.grown * 1.55F - s.delay * 0.9F);
		if (g <= 0.02F) {
			return;
		}
		List<Vec3d> pts = new ArrayList<>();
		for (int i = 0; i <= s.seg; i++) {
			double k = i / (double) s.seg;
			Vec3d p = s.from.lerp(s.to, k * g);
			double sway = s.hang ? 0.22 * k : 0.28 * k;
			double ph = c.now * (s.hang ? 0.045 : 0.05) + s.phase + k * 4.5;
			if (s.hang) {
				p = p.add(Math.sin(ph) * sway, 0.0, Math.sin(ph * 0.8 + 1.0) * sway);
			} else {
				p = p.add(Math.sin(ph) * sway + Math.sin(k * 7.0 + s.phase) * 0.12, 0.1 * Math.sin(k * 9.0 + s.phase) + 0.1 * k * Math.max(0.0, Math.sin(c.now * 0.09F + s.phase + k * 3.0)), 0.0);
			}
			pts.add(new Vec3d(MathHelper.clamp(p.x, -1.4, 1.4), p.y, p.z));
		}
		chain(c, pts, s.thick, s.thick * 0.55, (int) (s.phase * 100.0F));
		if (s.stalk != null) {
			Vec3d end = pts.get(pts.size() - 1);
			Eye e = s.stalk;
			e.pos = end.add(0.0, -e.size * 0.35, e.size * 0.25);
			drawEye(c, e, 1.0F);
		}
	}

	/** A chain of boxes along {@code pts}, thick at the start and thin at the end, with a knot every third. */
	private static void chain(Ctx c, List<Vec3d> pts, double r0, double r1, int seed) {
		int n = pts.size();
		for (int i = 0; i < n - 1; i++) {
			Vec3d a = pts.get(i);
			Vec3d b = pts.get(i + 1);
			Vec3d d = b.subtract(a);
			double len = d.length();
			if (len < 1.0E-4) {
				continue;
			}
			double k = i / (double) (n - 1);
			double r = MathHelper.lerp(k, r0, r1) * (i % 3 == 0 ? 1.35 : 1.0);
			Vec3d mid = a.add(b).multiply(0.5);
			c.matrices.push();
			c.matrices.translate(mid.x, mid.y, mid.z);
			Vec3d u = d.multiply(1.0 / len);
			c.matrices.multiply(new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, (float) u.x, (float) u.y, (float) u.z));
			flesh(c.pen(), r, r, len * 0.58, (seed + i) % 3 == 0 ? PALE : RED, seed + i);
			c.matrices.pop();
		}
	}

	// ---- The heart.

	/** The heart: a cuboid heart in a hollow, dark red with pale vessels, beating; its cover (the door pieces) has slid away. */
	private static void heart(Ctx c) {
		float rate = 0.32F + 0.4F * c.cut;
		float beat = 1.0F + 0.07F * MathHelper.sin(c.now * rate) + 0.08F * Math.max(0.0F, MathHelper.sin(c.now * rate * 2.0F + 1.0F)) + 0.2F * c.hit;
		double s = c.open * beat;
		c.matrices.push();
		c.matrices.translate(0.0, HEART_Y, HEART_Z + 0.25);
		turn(c.matrices, 0.0F, 0.0F, -10.0F);
		c.matrices.scale((float) (s * 1.25), (float) (s * 1.25), (float) (s * 1.25));
		int light = LightmapTextureManager.pack(15, 12);
		// {x, y, z, hx, hy, hz, rz}
		double[][] boxes = {
				{0.0, 0.0, 0.0, 0.5, 0.55, 0.42, -8.0},
				{-0.34, 0.6, 0.0, 0.26, 0.3, 0.26, 20.0},
				{0.3, 0.62, -0.02, 0.28, 0.32, 0.28, -15.0},
				{0.2, -0.1, 0.28, 0.3, 0.36, 0.2, 10.0},
				{-0.12, -0.6, 0.02, 0.3, 0.3, 0.28, 28.0},
				{-0.3, -0.9, 0.02, 0.15, 0.2, 0.15, 34.0},
		};
		for (double[] b : boxes) {
			c.matrices.push();
			c.matrices.translate(b[0], b[1], b[2]);
			turn(c.matrices, 0.0F, 0.0F, (float) b[6]);
			cuboid(c.pen(light), b[3], b[4], b[5], HEART, HEART, HEART, HEART, false, (int) (b[0] * 100));
			c.matrices.pop();
		}
		// Vessels: up out of the top into the mass, and into the sides.
		double[][] vessels = {{0.08, 1.0, 0.0, 0.16, 0.5, 0, 4.0}, {-0.3, 1.0, 0.1, 0.13, 0.45, 1, -16.0}, {0.55, 0.95, 0.05, 0.12, 0.4, 1, -38.0}, {-0.7, 0.85, 0.0, 0.12, 0.38, 0, 42.0},
				{0.6, 0.35, 0.1, 0.1, 0.34, 0, -70.0}, {-0.62, 0.3, 0.1, 0.1, 0.3, 1, 75.0}, {0.2, 1.35, 0.0, 0.1, 0.3, 0, 20.0}};
		for (double[] v : vessels) {
			c.matrices.push();
			c.matrices.translate(v[0], v[1], v[2]);
			turn(c.matrices, 0.0F, 0.0F, (float) v[6]);
			Tile t = VESSEL[(int) v[5]];
			cuboid(c.pen(light), v[3], v[4], v[3], t, t, t, t, true, 1);
			c.matrices.pop();
		}
		c.matrices.pop();
	}

	// ---- Attacks.

	/** One attack's picture: where it is in its time (0 to 1 over the warning), in this devil's frame. */
	private void attack(EternityDevilEntity devil, ClientHotel.Event e, Ctx c) {
		if (e.kind() != HotelPayloads.Devil.BITE && e.kind() != HotelPayloads.Devil.TENDRIL) {
			return;
		}
		float p = (c.now - e.start()) / Math.max(1, e.ticks());
		if (p < 0.0F || p > 1.35F) {
			return;
		}
		Vec3d rel = new Vec3d(e.x(), e.y(), e.z()).subtract(devil.getPos());
		Vector3f v = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z).rotateY((float) Math.toRadians(devil.facing()));
		if (e.kind() == HotelPayloads.Devil.BITE) {
			bite(c, v.x, v.z, p, e.start());
		} else {
			tendril(c, v.x, v.z, p, e.start());
		}
	}

	/** A mouth rising from the floor, or out of the nearer wall if the point is by one, hanging open, and snapping shut at the end of the warning. */
	private static void bite(Ctx c, double x, double z, float p, long seed) {
		boolean wall = Math.abs(x) > 0.85;
		float rise = ease(p / 0.55F);
		float shut = MathHelper.clamp((p - 0.82F) / 0.12F, 0.0F, 1.0F);
		float sink = p > 1.0F ? ease((p - 1.0F) / 0.3F) : 0.0F;
		float up = rise * (1.0F - sink);
		if (up <= 0.02F) {
			return;
		}
		double side = Math.signum(x);
		Vec3d normal = wall ? new Vec3d(-side, 0.0, 0.0) : new Vec3d(0.0, 1.0, 0.0);
		double px = wall ? side * 1.5 : x;
		double py = wall ? 1.0 : 0.0;
		c.matrices.push();
		c.matrices.translate(px, py, z);
		c.matrices.multiply(new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, (float) normal.x, (float) normal.y, (float) normal.z));
		c.matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(wall ? 90.0F : 0.0F));
		// The heap of flesh the mouth is in.
		Random rnd = new Random(seed);
		for (int i = 0; i < 6; i++) {
			c.matrices.push();
			c.matrices.translate((rnd.nextDouble() - 0.5) * 1.5 * up, (rnd.nextDouble() - 0.5) * 1.1 * up, -0.1 * up);
			turn(c.matrices, (rnd.nextFloat() - 0.5F) * 40.0F, (rnd.nextFloat() - 0.5F) * 40.0F, (rnd.nextFloat() - 0.5F) * 40.0F);
			flesh(c.pen(), (0.3 + rnd.nextDouble() * 0.3) * up, (0.3 + rnd.nextDouble() * 0.3) * up, (0.25 + rnd.nextDouble() * 0.25) * up, i % 3 == 0 ? PALE : RED, (int) seed + i);
			c.matrices.pop();
		}
		Mouth m = new Mouth();
		m.width = 1.5;
		m.seed = (int) (seed * 3 % 97);
		Random tr = new Random(m.seed);
		m.upper = teeth(tr, 1.5, true);
		m.lower = teeth(tr, 1.5, false);
		m.gum = 1;
		m.phase = 1.0F;
		c.matrices.translate(0.0, 0.0, 0.4 * up);
		float opening = up * (1.0F - shut) * 0.9F;
		mouthModel(c, m, Math.max(0.03F, opening), up, c.lit);
		c.matrices.pop();
	}

	/** A tendril: it rears up out of the mass, hangs, and comes down on the point (x, z), then draws back. */
	private static void tendril(Ctx c, double x, double z, float p, long seed) {
		float lift = ease(p / 0.6F);
		float slam = MathHelper.clamp((p - 0.62F) / 0.12F, 0.0F, 1.0F);
		float back = p > 0.95F ? ease((p - 0.95F) / 0.35F) : 0.0F;
		Vec3d from = new Vec3d(x * 0.25, 1.4, 1.7);
		double tipY = MathHelper.lerp(slam, 3.2 * lift + 0.6, 0.1);
		Vec3d tip = new Vec3d(MathHelper.lerp(0.15 + 0.85 * lift, from.x, x), tipY, MathHelper.lerp(0.15 + 0.85 * lift, from.z, z));
		Vec3d end = from.lerp(tip, 1.0 - back);
		List<Vec3d> pts = new ArrayList<>();
		int n = 20;
		double arch = (1.0 - slam) * 1.4 * lift + 0.15;
		for (int i = 0; i < n; i++) {
			double k = i / (double) (n - 1);
			Vec3d cc = from.lerp(end, k);
			pts.add(cc.add(0.0, Math.sin(Math.PI * k) * arch, 0.0).add(Math.sin(k * 6.0 + seed) * 0.06, 0.0, 0.0));
		}
		chain(c, pts, 0.3, 0.12, (int) seed);
	}
}
