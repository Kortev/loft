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
 * The Eternity Devil as drawn: the mass of flesh from kortev's drawings filling the corridor's end wall to wall, floor to ceiling (about 5
 * blocks wide and 4 high), grown out of the walls: a heap of lumps with many eyes (white, a dark red iris, they all turn to the one looking),
 * a big gaping mouth of uneven human teeth in the middle, a shut mouth grinding at each side and smaller ones about it, flesh creeping along
 * the floor and the ceiling, and tendrils lying on the carpet. Mouths rise from the floor and the walls to bite where something stands, a
 * tendril arches up and slams down the corridor (both from {@link ClientHotel#events}), and when enough of it is cut away the lumps
 * open and the heart shows, two lobes with eyes and teeth, beating. It is drawn in its own frame: +z toward the players, +y up, the floor
 * at y 0. Everything is worked out from a seed, so every client sees the same devil.
 */
public class EternityDevilRenderer extends EntityRenderer<EternityDevilEntity> {
	private static final Identifier FLESH = ForeverHubRenderer.FLESH;
	private static final Identifier EYE = ForeverHubRenderer.EYE;
	private static final Identifier MOUTH = ForeverHubRenderer.MOUTH;

	/** What stays when a devil is drawn: its lumps, eyes and mouths, tendrils and the flesh on the floor and ceiling (by seed). */
	private static final Map<Integer, Layout> LAYOUTS = new HashMap<>();
	/** How far open each devil's heart is (0 hidden to 1 open), and a pulse when the heart was hit. */
	private static final Map<Integer, float[]> STATE = new HashMap<>();

	static final class Lump {
		double x;
		double y;
		double z;
		double rx;
		double ry;
		double rz;
		int seed;
		/** Pointing along +z (the front of the mass), up (the ceiling's, hanging), or the floor's. */
		int kind;
	}

	static final class Layout {
		final List<Lump> lumps = new ArrayList<>();
		final List<ForeverLayout.Eye> eyes = new ArrayList<>();
		final List<ForeverLayout.Mouth> mouths = new ArrayList<>();
		final List<double[]> tendrils = new ArrayList<>();
	}

	public EternityDevilRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(EternityDevilEntity entity) {
		return FLESH;
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

	private static Layout makeLayout(int seed) {
		Random rnd = new Random(seed * 7919L + 17L);
		Layout l = new Layout();
		// The dark bulk behind everything, so nothing is seen through the mass.
		for (int i = 0; i < 7; i++) {
			Lump b = new Lump();
			b.x = -2.0 + i * 0.67 + rnd.nextDouble() * 0.2;
			b.y = 0.6 + rnd.nextDouble() * 2.8;
			b.z = -0.6 + rnd.nextDouble() * 0.5;
			b.rx = 1.1;
			b.ry = 1.4;
			b.rz = 1.4;
			b.seed = rnd.nextInt();
			l.lumps.add(b);
		}
		// The front: rows of lumps across the wall, each pushing out of the ones round it. The mouth's patch (the middle, low) is left clear.
		double[] rows = {0.35, 1.2, 2.1, 3.0, 3.75};
		for (int r = 0; r < rows.length; r++) {
			int n = r == 0 || r == 4 ? 5 : 6;
			for (int i = 0; i < n; i++) {
				Lump p = new Lump();
				p.x = -2.1 + 4.2 * (i + 0.5 + 0.3 * (r % 2) - 0.15) / n + (rnd.nextDouble() - 0.5) * 0.3;
				p.y = rows[r] + (rnd.nextDouble() - 0.5) * 0.35;
				p.z = 0.9 + rnd.nextDouble() * 0.7;
				p.rx = 0.62 + rnd.nextDouble() * 0.38;
				p.ry = 0.5 + rnd.nextDouble() * 0.38;
				p.rz = 0.75 + rnd.nextDouble() * 0.35;
				p.seed = rnd.nextInt();
				boolean mouth = Math.abs(p.x) < 1.75 && p.y > 0.75 && p.y < 2.0;
				if (mouth) {
					continue;
				}
				l.lumps.add(p);
			}
		}
		// A brow of lumps over the mouth and a swelling under it, as in the drawing.
		for (int i = 0; i < 5; i++) {
			Lump p = new Lump();
			p.x = -1.3 + i * 0.65 + (rnd.nextDouble() - 0.5) * 0.2;
			p.y = 2.15 + rnd.nextDouble() * 0.35;
			p.z = 1.2 + rnd.nextDouble() * 0.5;
			p.rx = 0.5 + rnd.nextDouble() * 0.2;
			p.ry = 0.42 + rnd.nextDouble() * 0.2;
			p.rz = 0.7;
			p.seed = rnd.nextInt();
			l.lumps.add(p);
		}
		// Flesh creeping along the floor and hanging along the ceiling at the sides.
		for (int i = 0; i < 12; i++) {
			Lump f = new Lump();
			f.kind = i % 3 == 2 ? 2 : 1;
			f.x = (i % 2 == 0 ? -1 : 1) * (1.0 + rnd.nextDouble() * 0.45);
			f.z = 2.3 + i * 0.42 + rnd.nextDouble() * 0.3;
			f.y = f.kind == 1 ? 0.0 : 3.97;
			f.rx = 0.45 + rnd.nextDouble() * 0.3;
			f.ry = 0.14 + rnd.nextDouble() * 0.1;
			f.rz = 0.55 + rnd.nextDouble() * 0.4;
			f.seed = rnd.nextInt();
			l.lumps.add(f);
		}
		// Eyes: the big one over the mouth, a ring of others on the lumps of the front, the way they are in the drawing: all sizes.
		List<Lump> front = new ArrayList<>();
		for (Lump p : l.lumps) {
			if (p.kind == 0 && p.z > 0.8) {
				front.add(p);
			}
		}
		int index = 0;
		ForeverLayout.Eye big = new ForeverLayout.Eye();
		big.pos = new Vec3d(0.0, 2.45, 2.05);
		big.normal = new Vec3d(0.0, 0.1, 1.0).normalize();
		big.radius = 0.42;
		big.index = index++;
		l.eyes.add(big);
		for (int i = 0; i < 17; i++) {
			Lump p = front.get(rnd.nextInt(front.size()));
			ForeverLayout.Eye e = new ForeverLayout.Eye();
			Vec3d dir = new Vec3d((rnd.nextDouble() - 0.5) * 0.9, (rnd.nextDouble() - 0.3) * 0.7, 1.0).normalize();
			e.pos = new Vec3d(p.x + dir.x * p.rx * 0.88, p.y + dir.y * p.ry * 0.9, p.z + dir.z * p.rz * 0.95);
			e.normal = dir;
			e.radius = 0.13 + rnd.nextDouble() * rnd.nextDouble() * 0.3;
			e.index = index++;
			l.eyes.add(e);
		}
		// Mouths: the big one in the middle, a shut one each side, a few small ones.
		l.mouths.add(mouth(0.0, 1.35, 2.0, new Vec3d(0.0, 0.05, 1.0), 3.0, 11));
		l.mouths.add(mouth(-2.05, 1.3, 1.65, new Vec3d(-0.5, 0.0, 1.0), 1.15, 23));
		l.mouths.add(mouth(2.05, 1.3, 1.65, new Vec3d(0.5, 0.0, 1.0), 1.15, 37));
		l.mouths.add(mouth(0.5, 0.35, 2.05, new Vec3d(0.1, 0.15, 1.0), 0.95, 41));
		l.mouths.add(mouth(-1.5, 3.3, 1.6, new Vec3d(-0.2, -0.1, 1.0), 0.7, 53));
		l.mouths.add(mouth(1.7, 3.0, 1.55, new Vec3d(0.3, -0.1, 1.0), 0.6, 59));
		l.mouths.add(mouth(-0.2, 3.6, 1.55, new Vec3d(0.0, -0.1, 1.0), 0.5, 61));
		// Tendrils lying on the carpet: start at the mass, run out along the floor.
		for (int i = 0; i < 5; i++) {
			l.tendrils.add(new double[] {-1.1 + i * 0.55 + (rnd.nextDouble() - 0.5) * 0.2, 2.1, 3.6 + rnd.nextDouble() * 3.0, rnd.nextDouble() * 6.0});
		}
		return l;
	}

	private static ForeverLayout.Mouth mouth(double x, double y, double z, Vec3d normal, double width, int seed) {
		ForeverLayout.Mouth m = new ForeverLayout.Mouth();
		m.pos = new Vec3d(x, y, z);
		m.normal = normal.normalize();
		m.width = width;
		m.seed = seed;
		return m;
	}

	// ---- Drawing.

	@Override
	public void render(EternityDevilEntity devil, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(devil.getWorld() instanceof ClientWorld world)) {
			return;
		}
		Layout layout = layout(devil.born());
		float now = world.getTime() + delta;
		float grown = ease((devil.age + delta) / EternityDevilEntity.GROW_TICKS);
		float frac = MathHelper.clamp(devil.getHealth() / devil.getMaxHealth(), 0.0F, 1.0F);
		boolean dead = devil.isDead();
		float death = dead ? ease((devil.deathTime + delta) / EternityDevilEntity.DEATH_TICKS) : 0.0F;
		float[] state = STATE.computeIfAbsent(devil.getId(), k -> new float[] {0.0F, -100.0F, -100.0F});
		// The heart opens as the flesh is cut away (and shuts as it grows back).
		float wantOpen = devil.heartOut() ? 1.0F : 0.0F;
		state[0] += MathHelper.clamp(wantOpen - state[0], -0.03F, 0.045F);
		java.util.List<ClientHotel.Event> events = ClientHotel.events(devil.getId());
		for (ClientHotel.Event e : events) {
			if (e.kind() == HotelPayloads.Devil.HEART && e.ticks() > 0) {
				state[1] = Math.max(state[1], e.start());
			} else if (e.kind() == HotelPayloads.Devil.REGEN) {
				state[2] = Math.max(state[2], e.start());
			}
		}
		float open = state[0];
		float hit = MathHelper.clamp(1.0F - (now - state[1]) / 8.0F, 0.0F, 1.0F);
		float regen = MathHelper.clamp(1.0F - (now - state[2]) / 14.0F, 0.0F, 1.0F);
		float flinch = devil.hurtTime > 0 ? devil.hurtTime / 10.0F : 0.0F;
		int lit = ForeverGeo.lit(light, 12, 9);

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-devil.facing()));
		// Bulk: a little smaller for what is cut away, a swell when it regrows, a shudder when hit, sagging when it dies.
		float bulk = (0.92F + 0.08F * frac) * (1.0F + 0.035F * regen * MathHelper.sin(now * 0.9F)) * (1.0F - 0.03F * flinch);
		float sag = 1.0F - 0.55F * death;
		matrices.scale(bulk, bulk * sag, bulk);
		Vec3d gaze = gaze(devil, delta);

		// ---- The lumps.
		ForeverGeo.Pen flesh = new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH)), matrices.peek(), lit);
		float cut = 1.0F - frac;
		for (Lump p : layout.lumps) {
			// Flesh cut away: the more it is hurt the more of the front is gone (it grows back as it regenerates).
			if (p.kind == 0 && p.z > 0.8 && !dead && ((p.seed >>> 3) % 100) / 100.0F < cut * 0.5F) {
				continue;
			}
			double dx = 0.0;
			double dy = 0.0;
			double dz = 0.0;
			// Grown out of the walls: sunk into them and spread out, coming up.
			double sink = (1.0 - grown) * 2.6;
			double mx = p.x * (0.6 + 0.4 * grown) + dx;
			double breathe = 0.04 * Math.sin(now * 0.07 + p.seed % 13);
			double sz = 0.45 + 0.55 * grown;
			matrices.push();
			if (p.kind == 0) {
				matrices.translate(mx, p.y + dy, p.z + dz - sink + breathe);
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
				ForeverGeo.blob(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH)), matrices.peek(), lit), 0.0, 0.0, 0.0, p.rx * sz, p.rz * sz, p.ry * sz,
						9, 14, 0.5, p.seed, 0.28, 2.0F, 1.0F);
			} else if (p.kind == 1) {
				matrices.translate(p.x * grown, p.y, p.z * (0.4 + 0.6 * grown) - sink * 0.4);
				ForeverGeo.blob(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH)), matrices.peek(), lit), 0.0, 0.0, 0.0, p.rx, p.ry * (0.4 + 0.6 * grown), p.rz,
						7, 12, 0.25, p.seed, 0.3, 2.0F, 1.0F);
			} else {
				matrices.translate(p.x * grown, p.y, p.z * (0.4 + 0.6 * grown) - sink * 0.4);
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));
				ForeverGeo.blob(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH)), matrices.peek(), lit), 0.0, 0.0, 0.0, p.rx, p.ry * (0.4 + 0.6 * grown), p.rz,
						7, 12, 0.25, p.seed, 0.3, 2.0F, 1.0F);
			}
			matrices.pop();
		}
		// ---- The heart, deep in the mass, showing through the opened lumps.
		if (open > 0.02F && !dead) {
			heart(devil, matrices, provider, lit, now, open, hit, frac, gaze, grown);
		}
		// ---- Eyes: all on the one that looks.
		for (ForeverLayout.Eye e : layout.eyes) {
			if (!dead && e.index > 0 && (e.index * 37 % 100) / 100.0F < cut * 0.4F) {
				continue;
			}
			double slide = (1.0 - grown) * 2.6;
			double wake = grown < 0.5F ? 0.0 : ease((grown - 0.5F) * 2.0F - (float) e.index * 0.02F);
			float lidOpen = (float) wake * (dead ? 1.0F - death : 1.0F);
			if (lidOpen <= 0.02F) {
				continue;
			}
			// A cut-away brow hides the eyes the heart's opening has pushed out of place: they follow the lumps.
			Vec3d pos = new Vec3d(e.pos.x * (0.6 + 0.4 * grown), e.pos.y, e.pos.z - slide);
			eye(matrices, provider, lit, pos, e.normal, e.radius * (0.7 + 0.3 * grown), lidOpen, gaze, open);
		}
		// ---- Mouths: the big one gapes and breathes, the side ones are shut and grinding, the small ones snap.
		for (int i = 0; i < layout.mouths.size(); i++) {
			ForeverLayout.Mouth m = layout.mouths.get(i);
			float gape;
			if (i == 0) {
				gape = 0.55F + 0.08F * MathHelper.sin(now * 0.12F) + 0.25F * flinch + 0.3F * open;
			} else if (i == 1 || i == 2) {
				gape = 0.03F + 0.025F * Math.abs(MathHelper.sin(now * 0.45F + i * 2.0F));
			} else {
				gape = 0.1F + 0.1F * Math.abs(MathHelper.sin(now * 0.2F + i * 1.3F));
			}
			if (dead) {
				gape = Math.min(1.0F, gape + 0.5F * death);
			}
			double slide = (1.0 - grown) * 2.6;
			matrices.push();
			matrices.translate(m.pos.x * (0.6 + 0.4 * grown), m.pos.y, m.pos.z - slide + 0.04);
			matrices.multiply(ForeverHubRenderer.frame(m.normal));
			ForeverHubRenderer.mouth(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(MOUTH)), matrices.peek(), lit), m, gape * grown, 1.0F);
			matrices.pop();
		}
		// ---- Tendrils on the floor, writhing.
		ForeverGeo.Pen rope = new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH)), matrices.peek(), lit);
		for (double[] t : layout.tendrils) {
			List<Vec3d> pts = new ArrayList<>();
			int n = 22;
			double len = t[2] * grown;
			for (int i = 0; i < n; i++) {
				double k = i / (double) (n - 1);
				double z = 1.9 + k * len;
				double x = t[0] + Math.sin(now * 0.05 + t[3] + k * 5.0) * 0.28 * k + Math.sin(k * 7.0 + t[3]) * 0.1;
				double y = 0.12 + 0.1 * Math.sin(k * 9.0 + t[3]) + 0.12 * k * Math.max(0.0, Math.sin(now * 0.09 + t[3] + k * 3.0));
				pts.add(new Vec3d(MathHelper.clamp(x, -1.3, 1.3), y, z));
			}
			ForeverGeo.rope(rope, pts, 0.17, 0.06, 5.0F);
		}
		// ---- Attacks.
		for (ClientHotel.Event e : events) {
			attack(devil, e, matrices, provider, lit, now, flesh);
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

	private static void eye(MatrixStack matrices, VertexConsumerProvider provider, int lit, Vec3d pos, Vec3d normal, double radius, float open, Vec3d gaze, float cavity) {
		Vec3d look = gaze.subtract(pos);
		Vec3d dir = look.lengthSquared() < 1.0E-6 ? normal : look.normalize();
		if (dir.dotProduct(normal) < 0.15) {
			dir = dir.multiply(0.5).add(normal.multiply(0.7)).normalize();
		}
		Quaternionf toward = new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, (float) dir.x, (float) dir.y, (float) dir.z);
		Quaternionf socket = ForeverHubRenderer.frame(normal);
		float lid = open * 1.25F;
		matrices.push();
		matrices.translate(pos.x, pos.y, pos.z);
		matrices.push();
		matrices.multiply(toward);
		ForeverGeo.ball(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(EYE)), matrices.peek(), LightmapTextureManager.MAX_LIGHT_COORDINATE), radius, 9, 16);
		matrices.pop();
		matrices.push();
		matrices.multiply(socket);
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_X.rotation(-lid));
		ForeverGeo.lid(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH)), matrices.peek(), lit), radius * 1.12, true, 4, 12);
		matrices.pop();
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_X.rotation(lid * 0.8F));
		ForeverGeo.lid(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH)), matrices.peek(), lit), radius * 1.12, false, 4, 12);
		matrices.pop();
		matrices.pop();
		matrices.pop();
	}

	/** The heart: two deep red lobes with eyes and uneven teeth (the drawing's), pulsing, in a dark cavity. */
	private static void heart(EternityDevilEntity devil, MatrixStack matrices, VertexConsumerProvider provider, int lit, float now, float open, float hit, float frac, Vec3d gaze,
			float grown) {
		float rate = 0.32F + 0.5F * (1.0F - frac);
		float beat = 1.0F + 0.09F * MathHelper.sin(now * rate) + 0.07F * Math.max(0.0F, MathHelper.sin(now * rate * 2.0F + 1.0F)) + 0.25F * hit;
		double s = open * beat;
		int bright = LightmapTextureManager.pack(13, 8);
		matrices.push();
		matrices.translate(0.0, 1.15, 1.7);
		for (int side = -1; side <= 1; side += 2) {
			matrices.push();
			matrices.translate(side * 0.5 * open, 0.0, -0.1);
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 12.0F));
			ForeverGeo.Pen lobe = new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH)), matrices.peek(), bright);
			lobe.tint(1.0F, 0.45F + 0.3F * hit, 0.42F + 0.3F * hit, 1.0F);
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
			ForeverGeo.blob(lobe, 0.0, 0.0, 0.0, 0.58 * s, 0.7 * s, 0.62 * s, 8, 12, 0.6, 73 + side, 0.18, 2.0F, 1.0F);
			matrices.pop();
			// An eye on each lobe's upper front, and a row of uneven teeth below.
			Vec3d at = new Vec3d(0.0, 0.2 * s, 0.7 * s);
			eye(matrices, provider, bright, at, new Vec3d(0.0, 0.15, 1.0).normalize(), 0.2 * s, open, gaze.subtract(side * 0.5, 1.75, -0.2), open);
			ForeverLayout.Mouth m = new ForeverLayout.Mouth();
			m.width = 0.8;
			m.seed = 5 + side;
			matrices.push();
			matrices.translate(0.0, -0.3 * s, 0.72 * s);
			ForeverHubRenderer.mouth(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(MOUTH)), matrices.peek(), bright), m, 0.12F + 0.15F * hit, (float) s);
			matrices.pop();
			matrices.pop();
		}
		matrices.pop();
	}

	// ---- Attacks.

	/** One attack's picture: where it is in its time (0 to 1 over the warning), in this devil's frame. */
	private void attack(EternityDevilEntity devil, ClientHotel.Event e, MatrixStack matrices, VertexConsumerProvider provider, int lit, float now, ForeverGeo.Pen flesh) {
		if (e.kind() != HotelPayloads.Devil.BITE && e.kind() != HotelPayloads.Devil.TENDRIL) {
			return;
		}
		float p = (now - e.start()) / Math.max(1, e.ticks());
		if (p < 0.0F || p > 1.35F) {
			return;
		}
		Vec3d rel = new Vec3d(e.x(), e.y(), e.z()).subtract(devil.getPos());
		Vector3f v = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z).rotateY((float) Math.toRadians(devil.facing()));
		if (e.kind() == HotelPayloads.Devil.BITE) {
			bite(matrices, provider, lit, v.x, v.z, p, e.start());
		} else {
			tendril(matrices, provider, lit, v.x, v.z, p, flesh, e.start());
		}
	}

	/** A mouth rising from the floor, or out of the nearer wall if the point is by one, hanging open, and snapping shut at the end of the warning. */
	private static void bite(MatrixStack matrices, VertexConsumerProvider provider, int lit, double x, double z, float p, long seed) {
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
		matrices.push();
		matrices.translate(px, py, z);
		matrices.multiply(ForeverHubRenderer.frame(normal));
		// The heap of flesh the mouth is in.
		ForeverGeo.blob(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH)), matrices.peek(), lit), 0.0, 0.0, -0.1, 0.95 * up, 0.9 * up, 0.5 * up, 8, 12, 0.3,
				(int) seed, 0.3, 2.0F, 1.0F);
		ForeverLayout.Mouth m = new ForeverLayout.Mouth();
		m.width = 1.5;
		m.seed = (int) (seed * 3 % 97);
		matrices.translate(0.0, 0.0, 0.42 * up);
		float opening = up * (1.0F - shut) * 0.85F;
		ForeverHubRenderer.mouth(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(MOUTH)), matrices.peek(), lit), m, opening, 1.0F);
		matrices.pop();
	}

	/** A tendril: it rears up out of the mass, hangs, and comes down on the point (x, z), then draws back. */
	private static void tendril(MatrixStack matrices, VertexConsumerProvider provider, int lit, double x, double z, float p, ForeverGeo.Pen unused, long seed) {
		float lift = ease(p / 0.6F);
		float slam = MathHelper.clamp((p - 0.62F) / 0.12F, 0.0F, 1.0F);
		float back = p > 0.95F ? ease((p - 0.95F) / 0.35F) : 0.0F;
		Vec3d from = new Vec3d(x * 0.25, 1.4, 1.7);
		double tipY = MathHelper.lerp(slam, 3.2 * lift + 0.6, 0.1);
		Vec3d tip = new Vec3d(MathHelper.lerp(0.15 + 0.85 * lift, from.x, x), tipY, MathHelper.lerp(0.15 + 0.85 * lift, from.z, z));
		Vec3d end = from.lerp(tip, 1.0 - back);
		List<Vec3d> pts = new ArrayList<>();
		int n = 26;
		double arch = (1.0 - slam) * 1.4 * lift + 0.15;
		for (int i = 0; i < n; i++) {
			double k = i / (double) (n - 1);
			Vec3d c = from.lerp(end, k);
			pts.add(c.add(0.0, Math.sin(Math.PI * k) * arch, 0.0).add(Math.sin(k * 6.0 + seed) * 0.06, 0.0, 0.0));
		}
		ForeverGeo.rope(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH)), matrices.peek(), lit), pts, 0.32, 0.12, 6.0F);
	}
}
