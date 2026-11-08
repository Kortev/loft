package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.Forever;
import io.github.kortev.chainsawman.contract.ForeverCopyEntity;
import io.github.kortev.chainsawman.contract.ForeverHubEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Draws the Eternity Devil where it holds a locked target ({@link ForeverHubEntity}): five to seven low lumps of meaty flesh pushing
 * up through jagged cracks in the ground round the target, eyes on about half of them (white, a square iris, lids) that open one after
 * another and all turn to stare at the target and never blink until the end, a mouth on every lump (a lens of uneven human teeth) that
 * grins wider with every stored hit and bites shut at the release, thick sinews winding up the legs of the target and of every moment of
 * it, the red slashes of the stored hits on the target, the release's burst, and the flesh sinking back into the ground after.
 */
public class ForeverHubRenderer extends EntityRenderer<ForeverHubEntity> {
	static final Identifier FLESH = Identifier.of("chainsawman", "textures/entity/forever_flesh.png");
	static final Identifier EYE = Identifier.of("chainsawman", "textures/entity/forever_eye.png");
	static final Identifier MOUTH = Identifier.of("chainsawman", "textures/entity/forever_mouth.png");
	static final Identifier CRACK = Identifier.of("chainsawman", "textures/entity/forever_crack.png");
	static final Identifier MARK = Identifier.of("chainsawman", "textures/entity/forever_mark.png");
	static final Identifier BURST = Identifier.of("chainsawman", "textures/entity/forever_burst.png");

	/** The ticks the flesh takes to rise. */
	private static final float RISE = 6.0F;
	/** How wide the eyes open (radians of lid). */
	private static final float LID_OPEN = 1.25F;

	public ForeverHubRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ForeverHubEntity entity) {
		return FLESH;
	}

	@Override
	public boolean shouldRender(ForeverHubEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	private static float back(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		float c = 1.9F;
		float y = x - 1.0F;
		return 1.0F + (c + 1.0F) * y * y * y + c * y * y;
	}

	@Override
	public void render(ForeverHubEntity hub, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(hub.getWorld() instanceof net.minecraft.client.world.ClientWorld world)) {
			return;
		}
		ForeverLayout layout = ClientForever.layout(hub);
		LivingEntity target = world.getEntityById(hub.targetId()) instanceof LivingEntity living ? living : null;
		float now = world.getTime() + delta;
		float t = now - hub.startedAt();
		int phase = hub.phase();
		float since = now - hub.phaseAt();
		float[] marks = hub.marks();
		int lit = ForeverGeo.lit(light, 7, 5);
		int n = layout.lumps.size();
		float[] rise = new float[n];
		for (int i = 0; i < n; i++) {
			ForeverLayout.Lump l = layout.lumps.get(i);
			if (phase == ForeverHubEntity.SINKING) {
				float sink = since / Forever.SINK;
				rise[i] = 1.0F - ease(sink * 1.35F - i * 0.07F);
			} else {
				rise[i] = back((t - (float) l.delay) / RISE);
				if (phase == ForeverHubEntity.ENDING && since < 2.0F) {
					rise[i] = 1.0F;
				}
			}
		}
		// The bite squashes the flesh for a moment, and it springs back.
		float squash = 1.0F;
		if (phase == ForeverHubEntity.ENDING) {
			squash = 1.0F - 0.2F * MathHelper.clamp(since / 1.5F, 0.0F, 1.0F);
		} else if (phase == ForeverHubEntity.SINKING) {
			squash = 0.8F + 0.2F * MathHelper.clamp(since / 2.5F, 0.0F, 1.0F);
		}
		// ---- Cracks.
		VertexConsumer crackVc = provider.getBuffer(RenderLayer.getEntityCutoutNoCull(CRACK));
		ForeverGeo.Pen crackPen = new ForeverGeo.Pen(crackVc, matrices.peek(), LightmapTextureManager.MAX_LIGHT_COORDINATE);
		for (ForeverLayout.Crack c : layout.cracks) {
			float g = MathHelper.clamp(rise[c.lump], 0.0F, 1.0F);
			float shown = g * (c.pts.size() - 1);
			for (int s = 0; s < c.pts.size() - 1; s++) {
				float part = MathHelper.clamp(shown - s, 0.0F, 1.0F);
				if (part <= 0.0F) {
					break;
				}
				Vec3d a = c.pts.get(s);
				Vec3d b = a.lerp(c.pts.get(s + 1), part);
				double w0 = 0.13 * (1.0 - s / (double) c.pts.size() * 0.6);
				double w1 = 0.13 * (1.0 - (s + part) / c.pts.size() * 0.6);
				ForeverGeo.strip(crackPen, a, b, w0, w1, 0.0F, 1.0F);
			}
		}
		// ---- The flesh itself: lumps, with sinews.
		VertexConsumer fleshVc = provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH));
		ForeverGeo.Pen flesh = new ForeverGeo.Pen(fleshVc, matrices.peek(), lit);
		for (int i = 0; i < n; i++) {
			float r = rise[i];
			if (r < 0.02F) {
				continue;
			}
			ForeverLayout.Lump l = layout.lumps.get(i);
			for (ForeverLayout.Part p : l.parts) {
				double sunk = (1.0 - r) * p.ry * 1.4;
				double sx = 0.5 + 0.5 * Math.min(r, 1.15);
				double sy = (0.3 + 0.7 * r) * squash;
				ForeverGeo.blob(flesh, p.x, p.y - sunk, p.z, p.rx * sx * (2.0 - squash), p.ry * sy, p.rz * sx * (2.0 - squash), 9, 16, 0.25, p.seed, 0.3, 2.0F, 1.0F);
			}
		}
		flesh.tint(1.0F, 1.0F, 1.0F, 1.0F);
		// Sinews up the legs of the target and of every moment of it.
		List<double[]> holds = new ArrayList<>();
		if (target != null) {
			holds.add(new double[] {0.0, 0.0, 0.0, target.getHeight(), target.getWidth(), 0.0});
		}
		for (ForeverCopyEntity c : ClientForever.copies(hub.getId())) {
			if (c.snapAt() >= 0) {
				continue;
			}
			Vec3d at = c.getLerpedPos(delta).subtract(hub.getLerpedPos(delta));
			holds.add(new double[] {at.x, at.y, at.z, c.getHeight(), c.getWidth(), Math.abs(c.slot())});
		}
		for (double[] hold : holds) {
			float growth;
			if (phase == ForeverHubEntity.HELD) {
				growth = MathHelper.clamp((t - 3.0F - (float) hold[5] * 0.8F) / 6.0F, 0.0F, 1.0F);
			} else if (phase == ForeverHubEntity.ENDING) {
				growth = 1.0F - MathHelper.clamp(since / 1.5F, 0.0F, 1.0F);
			} else {
				growth = 0.0F;
			}
			if (growth <= 0.0F) {
				continue;
			}
			Vec3d feet = new Vec3d(hold[0], hold[1], hold[2]);
			int which = nearest(layout, feet);
			ForeverLayout.Lump l = layout.lumps.get(which);
			ForeverLayout.Part main = l.parts.get(0);
			Vec3d flat = new Vec3d(feet.x - l.x, 0.0, feet.z - l.z);
			Vec3d dir = flat.lengthSquared() < 1.0E-6 ? new Vec3d(1.0, 0.0, 0.0) : flat.normalize();
			double sunk = (1.0 - Math.min(1.0, rise[which])) * main.ry * 1.4;
			Vec3d from = ForeverLayout.surface(main, dir, 0.5).add(0.0, -sunk, 0.0);
			for (int strand = 0; strand < (hold[5] == 0.0 ? 2 : 1); strand++) {
				sinew(flesh, from, feet, hold[3], hold[4], strand * Math.PI + (hold[5] * 1.3), growth, (int) (hold[5] * 7) + strand);
			}
		}
		// ---- Eyes: balls in sockets of flesh, lids opening one after another, all staring at the target.
		Vec3d gaze = new Vec3d(0.0, target != null ? target.getStandingEyeHeight() : 1.5, 0.0);
		for (ForeverLayout.Eye e : layout.eyes) {
			float r = rise[e.lump];
			if (r < 0.4F) {
				continue;
			}
			ForeverLayout.Lump l = layout.lumps.get(e.lump);
			ForeverLayout.Part main = l.parts.get(0);
			double sx = 0.5 + 0.5 * Math.min(r, 1.15);
			double sy = (0.3 + 0.7 * r) * squash;
			Vec3d c0 = new Vec3d(main.x, main.y, main.z);
			Vec3d c1 = new Vec3d(main.x, main.y - (1.0 - r) * main.ry * 1.4, main.z);
			Vec3d rel = e.pos.subtract(c0);
			Vec3d pos = c1.add(rel.x * sx * (2.0 - squash), rel.y * sy, rel.z * sx * (2.0 - squash));
			float open;
			if (phase == ForeverHubEntity.HELD) {
				open = ease((t - 3.0F - 1.3F * e.index) / 4.0F);
			} else if (phase == ForeverHubEntity.ENDING) {
				open = e.index == 0 ? 1.0F : 1.0F - MathHelper.clamp(since / 1.2F, 0.0F, 1.0F);
			} else {
				open = e.index == 0 ? 1.0F - MathHelper.clamp((since - 6.0F) / 2.0F, 0.0F, 1.0F) : 0.0F;
			}
			double radius = e.radius * Math.min(1.1, 0.6 + 0.4 * r);
			Vec3d look = gaze.subtract(pos);
			Vec3d nrm = e.normal;
			Vec3d dirLook = look.lengthSquared() < 1.0E-6 ? nrm : look.normalize();
			if (dirLook.dotProduct(nrm) < 0.15) {
				dirLook = dirLook.multiply(0.5).add(nrm.multiply(0.7)).normalize();
			}
			Quaternionf toward = new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, (float) dirLook.x, (float) dirLook.y, (float) dirLook.z);
			Quaternionf socket = frame(nrm);
			matrices.push();
			matrices.translate(pos.x, pos.y, pos.z);
			if (open > 0.02F) {
				matrices.push();
				matrices.multiply(toward);
				ForeverGeo.ball(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(EYE)), matrices.peek(), LightmapTextureManager.MAX_LIGHT_COORDINATE), radius, 10, 18);
				matrices.pop();
			}
			float lid = open * LID_OPEN;
			matrices.push();
			matrices.multiply(socket);
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_X.rotation(-lid));
			ForeverGeo.lid(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH)), matrices.peek(), lit), radius * 1.12, true, 4, 14);
			matrices.pop();
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_X.rotation(lid * 0.8F));
			ForeverGeo.lid(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH)), matrices.peek(), lit), radius * 1.12, false, 4, 14);
			matrices.pop();
			matrices.pop();
			matrices.pop();
		}
		// ---- Mouths: lenses of uneven human teeth, wider with each stored hit, biting shut at the release.
		VertexConsumer mouthVc = provider.getBuffer(RenderLayer.getEntityCutoutNoCull(MOUTH));
		for (ForeverLayout.Mouth m : layout.mouths) {
			float r = rise[m.lump];
			if (r < 0.5F) {
				continue;
			}
			ForeverLayout.Lump l = layout.lumps.get(m.lump);
			ForeverLayout.Part main = l.parts.get(0);
			double sx = 0.5 + 0.5 * Math.min(r, 1.15);
			double sy = (0.3 + 0.7 * r) * squash;
			Vec3d c0 = new Vec3d(main.x, main.y, main.z);
			Vec3d c1 = new Vec3d(main.x, main.y - (1.0 - r) * main.ry * 1.4, main.z);
			Vec3d rel = m.pos.subtract(c0);
			Vec3d pos = c1.add(rel.x * sx * (2.0 - squash), rel.y * sy, rel.z * sx * (2.0 - squash)).add(m.normal.multiply(0.02));
			float opening;
			if (phase == ForeverHubEntity.HELD) {
				opening = ease((t - 5.0F) / 12.0F) * Math.min(1.0F, 0.2F + 0.07F * marks.length);
				if (marks.length > 0) {
					opening *= 1.0F + 0.06F * MathHelper.sin(now * 0.9F + m.seed % 7);
				}
			} else if (phase == ForeverHubEntity.ENDING) {
				opening = Math.min(1.0F, 0.2F + 0.07F * marks.length) * (1.0F - MathHelper.clamp(since / 1.0F, 0.0F, 1.0F));
			} else {
				opening = 0.0F;
			}
			matrices.push();
			matrices.translate(pos.x, pos.y, pos.z);
			matrices.multiply(frame(m.normal));
			mouth(new ForeverGeo.Pen(mouthVc, matrices.peek(), lit), m, opening, (float) (0.6 + 0.4 * Math.min(1.0, r)));
			matrices.pop();
		}
		// ---- The line goes on: render-only copies, fainter and paler with distance, like a mirror tunnel.
		if (target != null && phase != ForeverHubEntity.SINKING && !layout.far.isEmpty()) {
			float grow = phase == ForeverHubEntity.HELD ? ease((t - 2.0F) / 8.0F) : 1.0F - MathHelper.clamp(since / 1.5F, 0.0F, 1.0F);
			Vec3d cam = dispatcher.camera.getPos();
			Vec3d hubAt = hub.getLerpedPos(delta);
			for (double[] f : layout.far) {
				double dx = hubAt.x + f[0] - cam.x;
				double dz = hubAt.z + f[2] - cam.z;
				if (dx * dx + dz * dz > 48.0 * 48.0) {
					continue;
				}
				float fade = (float) Math.pow(1.0 - f[3], 1.5) * 0.4F * grow;
				if (fade < 0.015F) {
					continue;
				}
				float[] tint = {1.2F, 1.4F, 1.2F, fade};
				float stride = target.limbAnimator.getPos() + (float) (f[4] * (4.0 + f[5]) * 0.9);
				ForeverCopyRenderer.drawTarget(target, dispatcher, target.getBodyYaw(), stride, 0.7F, false, tint, true, 1.0F, new Vec3d(f[0], f[1], f[2]), matrices, provider,
						ForeverGeo.lit(light, 15, 13), delta);
			}
		}
		// ---- The target: drained, with the slashes of its stored hits.
		if (target != null) {
			if (phase != ForeverHubEntity.SINKING) {
				MinecraftClient client = MinecraftClient.getInstance();
				boolean firstPerson = target == client.player && client.options.getPerspective().isFirstPerson();
				if (!firstPerson) {
					float wash = phase == ForeverHubEntity.HELD ? ease(t / 6.0F) : 1.0F - MathHelper.clamp(since / 2.0F, 0.0F, 1.0F);
					float[] tint = {0.75F, 0.9F, 0.7F, 0.34F * wash};
					ForeverCopyRenderer.drawTarget(target, dispatcher, 0.0F, 0.0F, 0.0F, true, tint, false, 1.012F, Vec3d.ZERO, matrices, provider,
							ForeverGeo.lit(light, 13, 11), delta);
				}
				marks(dispatcher, matrices, provider, marks, target.getHeight(), target.getWidth(), t, 1.0F);
			}
		}
		// ---- The release: the burst at the target's heart.
		if (phase == ForeverHubEntity.SINKING && since < 12.0F) {
			float k = since / 12.0F;
			double h = target != null ? target.getHeight() * 0.5 : 0.9;
			burst(provider, matrices, new Vec3d(0.0, h, 0.0), 0.5F + 1.5F * (1.0F - (1.0F - k) * (1.0F - k)), 1.0F - k * k, hub.getId() * 0.7F);
		}
	}

	// ---- Parts.

	private static int nearest(ForeverLayout layout, Vec3d at) {
		int best = 0;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < layout.lumps.size(); i++) {
			ForeverLayout.Lump l = layout.lumps.get(i);
			double d = (l.x - at.x) * (l.x - at.x) + (l.z - at.z) * (l.z - at.z);
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	/** A frame whose +z is {@code normal} and whose x is horizontal. */
	private static Quaternionf frame(Vec3d normal) {
		Vec3d z = normal.normalize();
		Vec3d up = Math.abs(z.y) > 0.97 ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(0.0, 1.0, 0.0);
		Vec3d x = up.crossProduct(z).normalize();
		Vec3d y = z.crossProduct(x);
		Matrix3f m = new Matrix3f((float) x.x, (float) x.y, (float) x.z, (float) y.x, (float) y.y, (float) y.z, (float) z.x, (float) z.y, (float) z.z);
		return new Quaternionf().setFromNormalized(m);
	}

	/** One rope of flesh from the lump at {@code from} along the ground to {@code feet} and winding up the legs of something {@code height} tall. */
	private static void sinew(ForeverGeo.Pen pen, Vec3d from, Vec3d feet, double height, double width, double phase, float growth, int seed) {
		int n = 46;
		double leg = Math.max(0.24, width * 0.42);
		Vec3d anchor = feet.add(Math.cos(phase) * leg, 0.04, Math.sin(phase) * leg);
		int used = Math.max(2, (int) Math.ceil(growth * n));
		List<Vec3d> pts = new ArrayList<>();
		double split = 0.38;
		Vec3d dir = anchor.subtract(from);
		Vec3d side = new Vec3d(-dir.z, 0.0, dir.x);
		side = side.lengthSquared() < 1.0E-6 ? new Vec3d(1.0, 0.0, 0.0) : side.normalize();
		for (int i = 0; i < Math.min(used, n); i++) {
			double s = i / (double) (n - 1);
			if (s < split) {
				double u = s / split;
				Vec3d p = from.lerp(anchor, u);
				p = p.add(0.0, 0.3 * Math.sin(Math.PI * u) * (1.0 - 0.4 * u), 0.0).add(side.multiply(Math.sin(u * 8.0 + seed) * 0.09));
				pts.add(p);
			} else {
				double v = (s - split) / (1.0 - split);
				double ang = phase + v * Math.PI * 3.2;
				double rr = leg * (1.0 + 0.18 * Math.sin(v * 13.0 + seed));
				pts.add(new Vec3d(feet.x + Math.cos(ang) * rr, feet.y + 0.04 + v * 0.88 * height, feet.z + Math.sin(ang) * rr));
			}
		}
		ForeverGeo.rope(pen, pts, 0.045, 0.032, 8.0F);
	}

	/** One mouth in its own frame (x along it, y up, z out of the flesh): lips, a dark throat, a tongue, and rows of uneven teeth. */
	private static void mouth(ForeverGeo.Pen pen, ForeverLayout.Mouth m, float opening, float size) {
		double w = m.width * size;
		double h = 0.035 + w * 0.5 * opening;
		int segs = 14;
		double[] xs = new double[segs + 1];
		double[] top = new double[segs + 1];
		double[] bot = new double[segs + 1];
		for (int i = 0; i <= segs; i++) {
			double tt = 2.0 * i / segs - 1.0;
			double f = Math.pow(Math.max(0.0, 1.0 - tt * tt), 0.75);
			xs[i] = tt * w / 2.0;
			top[i] = 0.3 * h * f;
			bot[i] = -0.7 * h * f;
		}
		float lip = cell(0);
		float lipShade = cell(1);
		float throat = cell(2);
		float tongue = cell(3);
		float tooth = cell(4);
		float toothShade = cell(5);
		float v = 0.5F;
		// The throat and tongue, set back.
		for (int i = 0; i < segs; i++) {
			pen.quad(new Vec3d(xs[i], bot[i], -0.07), new Vec3d(xs[i + 1], bot[i + 1], -0.07), new Vec3d(xs[i + 1], top[i + 1], -0.07), new Vec3d(xs[i], top[i], -0.07), throat, v, throat, v);
			if (h > 0.16) {
				double a0 = bot[i] + (top[i] - bot[i]) * 0.04;
				double a1 = bot[i + 1] + (top[i + 1] - bot[i + 1]) * 0.04;
				double b0 = bot[i] + (top[i] - bot[i]) * 0.4;
				double b1 = bot[i + 1] + (top[i + 1] - bot[i + 1]) * 0.4;
				pen.quad(new Vec3d(xs[i] * 0.8, a0, -0.05), new Vec3d(xs[i + 1] * 0.8, a1, -0.05), new Vec3d(xs[i + 1] * 0.8, b1, -0.05), new Vec3d(xs[i] * 0.8, b0, -0.05), tongue, v, tongue, v);
			}
		}
		// The lips, thick, over and under, and a darker ring round them.
		for (int i = 0; i < segs; i++) {
			double k0 = 1.0 + 0.05 * Math.abs(xs[i] / (w / 2.0));
			double k1 = 1.0 + 0.05 * Math.abs(xs[i + 1] / (w / 2.0));
			pen.quad(new Vec3d(xs[i], top[i], 0.0), new Vec3d(xs[i + 1], top[i + 1], 0.0), new Vec3d(xs[i + 1] * k1, top[i + 1] + 0.08, 0.05), new Vec3d(xs[i] * k0, top[i] + 0.08, 0.05), lip, v, lip, v);
			pen.quad(new Vec3d(xs[i] * k0, top[i] + 0.08, 0.05), new Vec3d(xs[i + 1] * k1, top[i + 1] + 0.08, 0.05), new Vec3d(xs[i + 1] * k1, top[i + 1] + 0.14, 0.0), new Vec3d(xs[i] * k0, top[i] + 0.14, 0.0), lipShade, v, lipShade, v);
			pen.quad(new Vec3d(xs[i + 1], bot[i + 1], 0.0), new Vec3d(xs[i], bot[i], 0.0), new Vec3d(xs[i] * k0, bot[i] - 0.1, 0.055), new Vec3d(xs[i + 1] * k1, bot[i + 1] - 0.1, 0.055), lip, v, lip, v);
			pen.quad(new Vec3d(xs[i + 1] * k1, bot[i + 1] - 0.1, 0.055), new Vec3d(xs[i] * k0, bot[i] - 0.1, 0.055), new Vec3d(xs[i] * k0, bot[i] - 0.17, 0.0), new Vec3d(xs[i + 1] * k1, bot[i + 1] - 0.17, 0.0), lipShade, v, lipShade, v);
		}
		// The teeth: uneven flat human ones, an upper and a lower row; shut, they meet edge to edge.
		Random rnd = new Random(m.seed);
		double x = -w / 2.0 + 0.03;
		int k = 0;
		while (x < w / 2.0 - 0.06) {
			double tw = 0.055 + rnd.nextDouble() * 0.05;
			double tc = MathHelper.clamp((x + tw / 2.0) / (w / 2.0), -1.0, 1.0);
			double f = Math.pow(Math.max(0.0, 1.0 - tc * tc), 0.75);
			double uy = 0.3 * h * f;
			double ly = -0.7 * h * f;
			double gap = uy - ly;
			boolean shut = gap < 0.075;
			double up = shut ? 0.075 : Math.min(0.04 + 0.06 * rnd.nextDouble(), gap * 0.46);
			double dn = shut ? 0.075 : Math.min(0.035 + 0.055 * rnd.nextDouble(), gap * 0.42);
			float col = k % 2 == 0 ? tooth : toothShade;
			double fwd = rnd.nextDouble() * 0.02;
			ForeverGeo.box(pen, x, uy - up, -0.015, x + tw, uy + 0.01, 0.03 + fwd, col, v);
			ForeverGeo.box(pen, x + 0.01, ly - 0.01, -0.015, x + tw + 0.005, ly + dn, 0.03 + rnd.nextDouble() * 0.02, col == tooth ? toothShade : tooth, v);
			x += tw + 0.012;
			k++;
		}
	}

	private static float cell(int i) {
		return (i * 4 + 2) / 32.0F;
	}

	/** The red slashes of the stored hits, hanging still on a body {@code height} tall: glowing, drawn facing the viewer, piling up. */
	static void marks(EntityRenderDispatcher dispatcher, MatrixStack matrices, VertexConsumerProvider provider, float[] marks, double height, double width, float sinceLock,
			float strength) {
		if (marks.length == 0) {
			return;
		}
		VertexConsumer vc = provider.getBuffer(RenderLayer.getEntityTranslucentEmissive(MARK));
		for (int i = Math.max(0, marks.length - 14); i < marks.length; i++) {
			int h = ((i + 3) * 0x9E3779B1 >>> 8) & 0xFFFF;
			float roll = (-0.75F + (h % 97) / 97.0F * 1.5F) + (i % 2 == 0 ? 0.2F : -0.2F);
			double lateral = ((h >> 4) % 100 / 100.0 - 0.5) * width * 0.7;
			double size = 0.3 + (h % 13) / 13.0 * 0.12;
			matrices.push();
			matrices.translate(0.0, marks[i] * height, 0.0);
			matrices.multiply(dispatcher.getRotation());
			matrices.translate(lateral, 0.0, width * 0.55 + 0.12 + i * 0.004);
			matrices.multiply(RotationAxis.POSITIVE_Z.rotation(roll));
			ForeverGeo.Pen pen = new ForeverGeo.Pen(vc, matrices.peek(), LightmapTextureManager.MAX_LIGHT_COORDINATE).tint(1.0F, 1.0F, 1.0F, MathHelper.clamp(strength, 0.0F, 1.0F));
			pen.quad(new Vec3d(-size, -size, 0.0), new Vec3d(size, -size, 0.0), new Vec3d(size, size, 0.0), new Vec3d(-size, size, 0.0), 0.0F, 0.0F, 1.0F, 1.0F);
			matrices.pop();
		}
	}

	/** A billboard burst of white, red and black spikes at {@code at}, {@code size} across, fading. */
	private void burst(VertexConsumerProvider provider, MatrixStack matrices, Vec3d at, float size, float alpha, float spin) {
		VertexConsumer vc = provider.getBuffer(RenderLayer.getEntityTranslucentEmissive(BURST));
		for (int layer = 0; layer < 2; layer++) {
			matrices.push();
			matrices.translate(at.x, at.y, at.z);
			matrices.multiply(dispatcher.getRotation());
			matrices.translate(0.0, 0.0, 1.6 + layer * 0.05);
			matrices.multiply(RotationAxis.POSITIVE_Z.rotation(spin + layer * 0.5F));
			double s = size * (layer == 0 ? 1.0 : 0.62);
			ForeverGeo.Pen pen = new ForeverGeo.Pen(vc, matrices.peek(), LightmapTextureManager.MAX_LIGHT_COORDINATE).tint(1.0F, 1.0F, 1.0F, MathHelper.clamp(alpha, 0.0F, 1.0F));
			pen.quad(new Vec3d(-s, -s, 0.0), new Vec3d(s, -s, 0.0), new Vec3d(s, s, 0.0), new Vec3d(-s, s, 0.0), 0.0F, 0.0F, 1.0F, 1.0F);
			matrices.pop();
		}
	}
}
