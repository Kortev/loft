package io.github.kortev.shootingstar.client.gap;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gfx.Fx;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import io.github.kortev.shootingstar.gap.GapTimeline;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Draws Ginnungagap events into the world: the tear and the universe seen through it, the part of that universe
 * that has come through, the lock, the blocks trading places, the contact, then one full-screen pass for the
 * glitch, the impact frames, the erasure and the black, and finally the shooter again, the one thing left.
 */
public final class GapRender {
	private static final Target DEPTH = new Target(true, false);
	private static final Target COPY = new Target(false, false);
	private static final Target PORTAL = new Target(true, false);
	private static final Fx BATCH = new Fx();
	private static final int TEAR_POINTS = 48;
	private static final float[] OTHER_SKY = {0x15 / 255.0F, 0x0A / 255.0F, 0x26 / 255.0F};
	private static final int WHITE = 0xFFFFFF;
	private static final int CYAN = 0xA8F8FF;
	private static final int PURPLE = 0xC77DFF;
	private static final int RED = 0xFF3B30;

	private GapRender() {
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = context.world();
		if (!Shaders.ready() || world == null || client.player == null || ClientGaps.all().isEmpty()) {
			return;
		}
		float tickDelta = context.tickCounter().getTickDelta(false);
		Vec3d cam = context.camera().getPos();
		Matrix4f view = new Matrix4f(context.positionMatrix());
		Matrix4f proj = new Matrix4f(context.projectionMatrix());
		Framebuffer main = client.getFramebuffer();
		int w = main.textureWidth;
		int h = main.textureHeight;
		Vector3f right = new Vector3f(view.m00(), view.m10(), view.m20());
		Vector3f up = new Vector3f(view.m01(), view.m11(), view.m21());
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		ClientGap mine = ClientGaps.mine();
		for (ClientGap gap : ClientGaps.all()) {
			double t = gap.time(tickDelta);
			boolean shown = t >= GapTimeline.TEAR && (gap.mine ? t < GapTimeline.NOTHING : t < GapTimeline.NOTHING + 20);
			if (shown && gap.mirror != null) {
				drawTear(gap, t, cam, view, proj, main, w, h, right, up);
				main.beginWrite(true);
				drawMirror(gap, t, cam, view, proj, 1);
			}
			main.beginWrite(true);
			drawMarks(world, gap, t, tickDelta, cam, view, proj, right, up);
		}

		Grade grade = grade(mine, tickDelta, cam, view, proj, w, h);
		if (grade != null) {
			DEPTH.ensure(w, h);
			DEPTH.copyDepthFrom(main);
			COPY.ensure(w, h);
			COPY.copyColorFrom(main);
			main.beginWrite(true);
			Post.begin();
			RenderSystem.setShaderTexture(0, COPY.color());
			RenderSystem.setShaderTexture(1, DEPTH.depth());
			grade.apply(proj, view, w, h, (float) (world.getTime() + tickDelta));
			Post.quad(Shaders.gap);
			RenderSystem.setShaderTexture(0, 0);
			RenderSystem.setShaderTexture(1, 0);
			// The one thing the erasure does not take: draw the shooter again over the black.
			if (mine != null && (grade.front >= 0.0F || grade.black > 0.0F) && context.camera().isThirdPerson()) {
				redrawShooter(client, client.player, cam, view, tickDelta);
			}
		}

		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		main.beginWrite(true);
	}

	// --- the mirror universe -------------------------------------------------------------

	private static boolean loggedContact;

	private static void drawMirror(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, int clip) {
		MirrorWorld m = gap.mirror;
		if (clip == 1 && !loggedContact && t >= GapTimeline.CONTACT) {
			loggedContact = true;
			io.github.kortev.shootingstar.ShootingStar.LOGGER.info("Ginnungagap #{} at contact: camera {}, lift {}, mirror placed at y {}, came through below {}",
					gap.id, cam, gap.lift(t), m.surface + gap.lift(t), gap.surface + GapTimeline.through(t));
		}
		float ox = (float) (m.originX - cam.x);
		float oy = (float) (m.surface + gap.lift(t) - cam.y);
		float oz = (float) (m.originZ - cam.z);
		Shaders.set(Shaders.mirror, "Offset", ox, oy, oz);
		Shaders.set(Shaders.mirror, "ClipY", (float) (gap.surface + GapTimeline.through(t) - cam.y));
		Shaders.setInt(Shaders.mirror, "ClipMode", clip);
		Shaders.set(Shaders.mirror, "Radius", (float) MirrorWorld.RADIUS);
		Shaders.set(Shaders.mirror, "Glow", (float) (0.35 + 0.65 * GapCamera.ease((t - GapTimeline.CLOSING) / 110.0)));
		Shaders.set(Shaders.mirror, "Fade", 1.0F);
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.disableCull();
		RenderSystem.disableBlend();
		m.draw(Shaders.mirror, view, proj);
		// What traded places: our blocks, still in our colours, hanging in theirs.
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
		int n = 0;
		for (ClientGap.Swap s : gap.swaps) {
			if (t < s.age()) {
				continue;
			}
			float x = s.pos().getX() - m.originX;
			float y = s.pos().getY() - m.surface;
			float z = s.pos().getZ() - m.originZ;
			for (int f = 0; f < 6; f++) {
				MirrorWorld.face(b, x, y, z, 1.0F, f, MirrorWorld.shade(s.color(), f == 0 ? 1.0F : f == 1 ? 0.5F : 0.78F), 0.01F);
			}
			n++;
		}
		if (n > 0) {
			Post.draw(b, Shaders.mirror, view, proj);
		} else {
			b.endNullable();
		}
	}

	/** The tear in the sky over the target, and through it the other universe's sky, its black sun and its ground. */
	private static void drawTear(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, Framebuffer main, int w, int h,
			Vector3f right, Vector3f up) {
		Vec3d[] outline = outline(gap, t, right, up);
		Vec3d centre = new Vec3d(gap.contact.x, gap.tearY(), gap.contact.z);

		PORTAL.begin(w, h, OTHER_SKY[0], OTHER_SKY[1], OTHER_SKY[2], 1.0F);
		BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
		java.util.Random r = new java.util.Random(gap.id * 977L);
		for (int i = 0; i < 260; i++) {
			double az = r.nextDouble() * Math.PI * 2;
			double el = Math.asin(0.15 + 0.85 * r.nextDouble());
			Vec3d dir = new Vec3d(Math.cos(az) * Math.cos(el), Math.sin(el), Math.sin(az) * Math.cos(el));
			BATCH.sprite(rel(centre.add(dir.multiply(420)), cam), 0.6F + 1.6F * (float) Math.pow(r.nextDouble(), 4),
					0.0F, Fx.fade(0xE9DDFF, 0.4F + 0.6F * r.nextFloat()));
		}
		BATCH.end(false);
		// Their sun is black, with a hard white rim.
		Vec3d sun = centre.add(gap.along.multiply(70)).add(gap.across.multiply(-30)).add(0, 230, 0);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		billboard(b, rel(sun, cam), right, up, 21.0F, 0xFFFFFFFF);
		billboard(b, rel(sun, cam).add(new Vector3f(rel(sun, cam)).normalize().mul(-0.5F)), right, up, 18.5F, 0xFF000000);
		RenderSystem.disableDepthTest();
		RenderSystem.disableBlend();
		RenderSystem.disableCull();
		Post.draw(b, GameRenderer.getPositionColorProgram(), view, proj);
		drawMirror(gap, t, cam, view, proj, 0);

		// The hole itself, in our sky: a fan of triangles showing whatever the portal saw. It writes no depth, so
		// what has already come through draws over it.
		main.beginWrite(true);
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.disableBlend();
		RenderSystem.setShaderTexture(0, PORTAL.color());
		Shaders.set(Shaders.portal, "ScreenSize", (float) w, (float) h);
		BufferBuilder fan = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION);
		Vector3f c = rel(centre, cam);
		for (int i = 0; i < outline.length; i++) {
			Vector3f a = rel(outline[i], cam);
			Vector3f d = rel(outline[(i + 1) % outline.length], cam);
			fan.vertex(c.x, c.y, c.z);
			fan.vertex(a.x, a.y, a.z);
			fan.vertex(d.x, d.y, d.z);
		}
		Post.draw(fan, Shaders.portal, view, proj);
		RenderSystem.setShaderTexture(0, 0);
		RenderSystem.depthMask(true);

		// Its torn edge, white hot.
		BATCH.begin(Fx.LINE, 0.0F, view, proj, right, up);
		Vector3f eye = new Vector3f();
		for (int i = 0; i < outline.length; i++) {
			Vector3f a = rel(outline[i], cam);
			Vector3f d = rel(outline[(i + 1) % outline.length], cam);
			// As thick on screen however near the edge passes the camera.
			float dist = new Vector3f(a).add(d).mul(0.5F).length();
			BATCH.beam(a, d, eye, Math.max(0.05F, dist * 0.0035F), Fx.fade(WHITE, 1.0F), Fx.fade(WHITE, 1.0F));
			BATCH.beam(a, d, eye, Math.max(0.15F, dist * 0.012F), Fx.fade(CYAN, 0.35F), Fx.fade(CYAN, 0.35F));
		}
		BATCH.end(true, 1.6F);
	}

	/**
	 * The tear's outline: a long, ragged rift across the sky over the target, sawtoothed on both lips and pointed
	 * at the ends. It always faces the camera, like a crack in the picture itself.
	 */
	static Vec3d[] outline(ClientGap gap, double t, Vector3f right, Vector3f up) {
		double length = GapTimeline.tearLength(t);
		double width = GapTimeline.tearWidth(t);
		Vec3d centre = new Vec3d(gap.contact.x, gap.tearY(), gap.contact.z);
		Vec3d across = new Vec3d(right.x, right.y, right.z).normalize();
		Vec3d along = new Vec3d(up.x, up.y, up.z).normalize();
		Vec3d[] pts = new Vec3d[TEAR_POINTS * 2];
		for (int side = 0; side < 2; side++) {
			for (int i = 0; i < TEAR_POINTS; i++) {
				double u = side == 0 ? -1.0 + 2.0 * i / TEAR_POINTS : 1.0 - 2.0 * i / TEAR_POINTS;
				double taper = Math.pow(Math.max(0.0, 1.0 - u * u), 0.55);
				double jag = (i % 2 == 0 ? 1.0 : 0.7) * (0.7 + 0.45 * noise(gap.id, side * 1000 + i));
				double v = (side == 0 ? 1.0 : -1.0) * width * taper * jag;
				pts[side * TEAR_POINTS + i] = centre.add(across.multiply(u * length)).add(along.multiply(v));
			}
		}
		return pts;
	}

	// --- marks: the lock, the swaps, the contact ------------------------------------------

	private static void drawMarks(ClientWorld world, ClientGap gap, double t, float tickDelta, Vec3d cam, Matrix4f view, Matrix4f proj,
			Vector3f right, Vector3f up) {
		Vector3f eye = new Vector3f();
		boolean any = false;
		BATCH.begin(Fx.LINE, 0.0F, view, proj, right, up);
		// The beam from the key to the target: dashes running out along it.
		double beam = GapCamera.ease((t - 84) / 8.0) * (1.0 - GapCamera.ease((t - 134) / 8.0));
		PlayerEntity shooter = world.getPlayerByUuid(gap.shooter);
		if (beam > 0.0 && shooter != null) {
			Vec3d hand = shooter.getLerpedPos(tickDelta).add(0, 1.35, 0).add(gap.along.multiply(0.55)).add(gap.across.multiply(0.4));
			Vec3d end = gap.contact.add(0, 0.1, 0);
			double reach = GapCamera.ease((t - 84) / 18.0);
			double length = hand.distanceTo(end) * reach;
			Vec3d dir = end.subtract(hand).normalize();
			for (double s = (t * 0.6) % 2.0 - 2.0; s < length; s += 2.0) {
				double a = Math.max(0.0, s);
				double d = Math.min(length, s + 1.2);
				if (d > a) {
					BATCH.beam(rel(hand.add(dir.multiply(a)), cam), rel(hand.add(dir.multiply(d)), cam), eye, 0.07F,
							Fx.fade(CYAN, (float) beam), Fx.fade(CYAN, (float) beam));
				}
			}
			any = true;
		}
		// Blocks trading places: a hard white line from each to its twin, gone in a third of a second.
		for (ClientGap.Swap s : gap.swaps) {
			double age = t - s.age();
			if (age < 0.0 || age > 7.0) {
				continue;
			}
			if (s.kind() == 1 && (s.pos().hashCode() & 3) != 0) {
				continue;
			}
			float k = (float) (1.0 - age / 7.0);
			Vec3d a = Vec3d.ofCenter(s.pos());
			Vec3d b = new Vec3d(a.x, gap.mirrorY(s.pos().getY(), t) + 0.5, a.z);
			BATCH.beam(rel(a, cam), rel(b, cam), eye, 0.08F + 0.25F * k, Fx.fade(WHITE, k), Fx.fade(WHITE, k));
			any = true;
		}
		BATCH.end(true, 1.5F);

		// Ender-purple specks along the swaps and bursting at both ends.
		BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
		for (ClientGap.Swap s : gap.swaps) {
			double age = t - s.age();
			if (age < 0.0 || age > 14.0 || (s.kind() == 1 && (s.pos().hashCode() & 3) != 0)) {
				continue;
			}
			float k = (float) (1.0 - age / 14.0);
			Vec3d a = Vec3d.ofCenter(s.pos());
			Vec3d b = new Vec3d(a.x, gap.mirrorY(s.pos().getY(), t) + 0.5, a.z);
			int seed = s.pos().hashCode();
			for (int i = 0; i < 14; i++) {
				double f = noise(seed, i);
				double spread = 0.4 + 1.6 * (age / 14.0);
				Vec3d p = a.lerp(b, f).add((noise(seed, i + 40) - 0.5) * spread, (noise(seed, i + 80) - 0.5) * spread,
						(noise(seed, i + 120) - 0.5) * spread);
				BATCH.sprite(rel(p, cam), 0.12F + 0.1F * k, 0.0F, Fx.fade(PURPLE, k * 0.9F));
			}
			any = true;
		}
		BATCH.end(true, 1.4F);

		// The lock: red brackets on the ground round the target.
		double lock = GapCamera.ease((t - 104) / 3.0) * (1.0 - GapCamera.ease((t - GapTimeline.CONTACT + 10) / 10.0));
		if (lock > 0.0) {
			BATCH.begin(Fx.LINE, 0.0F, view, proj, right, up);
			float half = (float) (2.6 - 0.6 * GapCamera.ease((t - 104) / 6.0));
			Vec3d c = gap.contact.add(0, 0.06, 0);
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					Vec3d corner = c.add(sx * half, 0, sz * half);
					BATCH.beam(rel(corner, cam), rel(corner.add(-sx * 1.1, 0, 0), cam), eye, 0.09F, Fx.fade(RED, (float) lock),
							Fx.fade(RED, (float) lock));
					BATCH.beam(rel(corner, cam), rel(corner.add(0, 0, -sz * 1.1), cam), eye, 0.09F, Fx.fade(RED, (float) lock),
							Fx.fade(RED, (float) lock));
				}
			}
			BATCH.end(true, 1.6F);
			any = true;
		}

		// Contact: a white seam where the two touch.
		if (t >= GapTimeline.CONTACT - 1 && t < GapTimeline.FRAMES + 2) {
			double k = GapCamera.ease((t - GapTimeline.CONTACT) / 14.0);
			BATCH.begin(Fx.LINE, 0.0F, view, proj, right, up);
			Vec3d c = gap.contact.add(0, 0.04, 0);
			double reach = 0.4 + 9.0 * k;
			BATCH.beam(rel(c.add(gap.along.multiply(-reach)), cam), rel(c.add(gap.along.multiply(reach)), cam), eye, 0.05F, 0xFFFFFFFF,
					0xFFFFFFFF);
			BATCH.beam(rel(c.add(gap.across.multiply(-reach * 0.6)), cam), rel(c.add(gap.across.multiply(reach * 0.6)), cam), eye, 0.05F,
					0xFFFFFFFF, 0xFFFFFFFF);
			BATCH.end(true, 3.0F);
			BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
			BATCH.sprite(rel(c.add(0, 0.2, 0), cam), (float) (0.5 + 2.5 * k), 0.0F, 0xFFFFFFFF);
			BATCH.end(true, 2.0F);
			any = true;
		}
		if (any) {
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableBlend();
			RenderSystem.depthMask(true);
		}
	}

	// --- the full-screen pass ------------------------------------------------------------

	private static final class Grade {
		int style;
		int extras;
		int panelOn;
		float[] panels = {0, 0, 0};
		float focusX = 0.5F;
		float focusY = 0.5F;
		float punch;
		float seed;
		float glitch;
		float flash;
		float black;
		float front = -1.0F;
		float clamp;
		Vec3d offset = Vec3d.ZERO;

		void apply(Matrix4f proj, Matrix4f view, int w, int h, float time) {
			Matrix4f inv = new Matrix4f(proj).mul(view).invert();
			Shaders.set(Shaders.gap, "InvViewProj", inv);
			Shaders.set(Shaders.gap, "ScreenSize", (float) w, (float) h);
			Shaders.set(Shaders.gap, "Time", time);
			Shaders.set(Shaders.gap, "CamOffset", (float) offset.x, (float) offset.y, (float) offset.z);
			Shaders.set(Shaders.gap, "Front", front);
			Shaders.set(Shaders.gap, "Clamp", clamp);
			Shaders.setInt(Shaders.gap, "Style", style);
			Shaders.setInt(Shaders.gap, "Extras", extras);
			Shaders.set(Shaders.gap, "Panels", panels[0], panels[1], panels[2]);
			Shaders.setInt(Shaders.gap, "PanelOn", panelOn);
			Shaders.set(Shaders.gap, "Focus", focusX, focusY);
			Shaders.set(Shaders.gap, "Punch", punch);
			Shaders.set(Shaders.gap, "Seed", seed);
			Shaders.set(Shaders.gap, "Glitch", glitch);
			Shaders.set(Shaders.gap, "Flash", flash);
			Shaders.set(Shaders.gap, "Black", black);
		}
	}

	private static Grade grade(ClientGap mine, float tickDelta, Vec3d cam, Matrix4f view, Matrix4f proj, int w, int h) {
		Grade g = new Grade();
		boolean on = false;
		for (ClientGap gap : ClientGaps.all()) {
			double t = gap.time(tickDelta);
			if (t >= GapTimeline.ERASURE && (gap.mine ? !gap.ended : t < GapTimeline.END + 40)) {
				g.front = (float) GapTimeline.eraseFront(t);
				g.clamp = gap.mine ? 0.0F : gap.radius;
				g.offset = cam.subtract(gap.target.getX(), gap.target.getY(), gap.target.getZ());
				on = true;
			}
		}
		if (mine != null) {
			double t = mine.time(tickDelta);
			if (t < GapTimeline.AIM + 3) {
				g.glitch = (float) (0.15 * GapCamera.ease((t - 30) / 6.0) + 0.85 * Math.pow(MathHelper.clamp((t - 42) / 18.0, 0.0, 1.0), 2));
				g.flash = (float) (t < GapTimeline.AIM ? Math.pow(MathHelper.clamp((t - 54) / 6.0, 0.0, 1.0), 2)
						: 1.0 - (t - GapTimeline.AIM) / 3.0);
				g.seed = (float) Math.floor(t * 1.5);
				on = true;
			}
			if (t >= 236 && t < 248) {
				g.glitch = 0.22F;
				g.seed = (float) Math.floor(t * 1.5);
				on = true;
			}
			for (ClientGap.Swap s : mine.swaps) {
				double age = t - s.age();
				if (age >= 0.0 && age < (s.kind() == 1 ? 5.0 : 2.0)) {
					g.glitch = Math.max(g.glitch, s.kind() == 1 ? (float) (0.7 * (1.0 - age / 5.0)) : 0.18F);
					g.seed = (float) Math.floor(t * 2.0);
					on = true;
				}
			}
			if (t >= GapTimeline.FRAMES && t < GapTimeline.ERASURE) {
				double e = t - GapTimeline.FRAMES;
				int index = GapFrames.index(e);
				GapFrames.Frame frame = GapFrames.at(e);
				g.style = frame.style();
				g.extras = frame.extras();
				if (frame.paneled()) {
					g.panelOn = 1;
					g.panels = new float[] {frame.panels()[0], frame.panels()[1], frame.panels()[2]};
				}
				g.seed = index * 3.7F;
				g.punch = (float) (0.09 * (1.0 - GapCamera.ease((e - frame.start()) / 3.0)));
				Vector4f p = new Vector4f((float) (mine.contact.x - cam.x), (float) (mine.contact.y + 1.0 - cam.y),
						(float) (mine.contact.z - cam.z), 1.0F);
				view.transform(p);
				proj.transform(p);
				if (p.w > 1.0E-3F) {
					g.focusX = MathHelper.clamp(p.x / p.w * 0.5F + 0.5F, 0.05F, 0.95F);
					g.focusY = MathHelper.clamp(p.y / p.w * 0.5F + 0.5F, 0.05F, 0.95F);
				}
				on = true;
			}
			if (t >= GapTimeline.NOTHING - 10) {
				g.black = (float) MathHelper.clamp((t - GapTimeline.NOTHING + 10) / 10.0, 0.0, 1.0);
				on = true;
			}
		}
		return on ? g : null;
	}

	/** Draws the local player again at full brightness, over whatever the pass did to the frame. */
	private static void redrawShooter(MinecraftClient client, ClientPlayerEntity player, Vec3d cam, Matrix4f view, float tickDelta) {
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.set(view);
		RenderSystem.applyModelViewMatrix();
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
		Vec3d p = player.getLerpedPos(tickDelta);
		client.getEntityRenderDispatcher().render(player, p.x - cam.x, p.y - cam.y, p.z - cam.z, player.getYaw(tickDelta), tickDelta,
				new MatrixStack(), consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		consumers.draw();
		modelView.popMatrix();
		RenderSystem.applyModelViewMatrix();
	}

	// --- helpers --------------------------------------------------------------------------

	private static void billboard(BufferBuilder b, Vector3f c, Vector3f right, Vector3f up, float size, int argb) {
		float rx = right.x * size;
		float ry = right.y * size;
		float rz = right.z * size;
		float ux = up.x * size;
		float uy = up.y * size;
		float uz = up.z * size;
		b.vertex(c.x - rx - ux, c.y - ry - uy, c.z - rz - uz).color(argb);
		b.vertex(c.x + rx - ux, c.y + ry - uy, c.z + rz - uz).color(argb);
		b.vertex(c.x + rx + ux, c.y + ry + uy, c.z + rz + uz).color(argb);
		b.vertex(c.x - rx + ux, c.y - ry + uy, c.z - rz + uz).color(argb);
	}

	static Vector3f rel(Vec3d p, Vec3d cam) {
		return new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
	}

	static double noise(int a, int b) {
		long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h >>> 11) * 0x1.0p-53;
	}
}
