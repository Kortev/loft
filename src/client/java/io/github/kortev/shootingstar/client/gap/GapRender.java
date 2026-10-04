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
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Draws Ginnungagap events into the world: the shard of the other universe falling out of the broken sky, the
 * blocks trading places, the contact, then one full-screen pass for the glitch, the sky shattering, the
 * impact frames, the erasure and the black, and finally the shooter again, the one thing left.
 */
public final class GapRender {
	private static final Target DEPTH = new Target(true, false);
	private static final Target COPY = new Target(false, false);
	private static final Fx BATCH = new Fx();
	private static final int WHITE = 0xFFFFFF;
	private static final int CYAN = 0xA8F8FF;
	private static final int PURPLE = 0xC77DFF;

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
		float time = (float) (world.getTime() + tickDelta);
		// Until the impact frames, the shard and the marks go on after the full-screen pass, so the broken sky can
		// never paint over them. From then on they go under it, to be inked, erased and blacked out with the rest.
		for (ClientGap gap : ClientGaps.all()) {
			double t = gap.time(tickDelta);
			if (t >= GapTimeline.FRAMES) {
				draw(world, gap, t, tickDelta, cam, view, proj, right, up, main, time, false);
			}
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
		for (ClientGap gap : ClientGaps.all()) {
			double t = gap.time(tickDelta);
			if (t < GapTimeline.FRAMES) {
				draw(world, gap, t, tickDelta, cam, view, proj, right, up, main, time, true);
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

	private static void draw(ClientWorld world, ClientGap gap, double t, float tickDelta, Vec3d cam, Matrix4f view, Matrix4f proj,
			Vector3f right, Vector3f up, Framebuffer main, float time, boolean ridges) {
		main.beginWrite(true);
		if (shown(gap, t)) {
			drawShard(gap, t, cam, view, proj, time);
			if (ridges) {
				drawRidges(gap, t, cam, view, proj, right, up);
			}
		}
		drawMarks(world, gap, t, tickDelta, cam, view, proj, right, up);
	}

	private static boolean shown(ClientGap gap, double t) {
		return t >= GapTimeline.TEAR && (gap.mine ? t < GapTimeline.NOTHING : t < GapTimeline.NOTHING + 20);
	}

	// --- the shard: a piece of the other universe, falling out of the broken sky -------------

	private static final double[] RING_H = {0, 8, 24, 48, 76, 104, 126, 140, 150};
	private static final double[] RING_R = {0, 4.6, 12, 19, 23.5, 20, 13, 3.5, 0};
	private static final int SIDES = 7;
	/** Smaller pieces breaking off round the big one: angle, distance out, height over the big one's tip, size. */
	private static final double[][] CHIPS = {
		{0.4, 46, 70, 0.32}, {1.5, 62, 120, 0.22}, {2.6, 38, 160, 0.18}, {3.3, 70, 40, 0.26}, {4.2, 52, 200, 0.2},
		{5.1, 80, 95, 0.28}, {5.8, 34, 230, 0.15}
	};

	private static void drawShard(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, float time) {
		Vec3d axis = new Vec3d(0, 1, 0).add(gap.along.multiply(0.16)).add(gap.across.multiply(-0.07)).normalize();
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
		shard(b, gap.shardTip(t), axis, 1.0, gap.id * 31, t * 0.004, cam);
		for (int i = 0; i < CHIPS.length; i++) {
			double[] c = CHIPS[i];
			// They fall behind the big one, slower, and never quite land.
			double fall = Math.max(c[2] * 0.35, GapTimeline.shardTip(t) + c[2]);
			Vec3d at = gap.contact.add(Math.cos(c[0]) * c[1], fall, Math.sin(c[0]) * c[1]);
			Vec3d lean = new Vec3d(Math.cos(c[0] * 3.1), 2.2, Math.sin(c[0] * 2.3)).normalize();
			shard(b, at, lean, c[3], gap.id * 31 + i + 1, t * (0.01 + 0.004 * i), cam);
		}
		Shaders.set(Shaders.shard, "Time", time);
		Shaders.set(Shaders.shard, "Spin", (float) Math.sin(gap.id * 1.7) * 0.2F, 0.0F, (float) Math.cos(gap.id * 1.7) * 0.2F);
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.disableCull();
		RenderSystem.disableBlend();
		Post.draw(b, Shaders.shard, view, proj);
	}

	/** One faceted, slightly twisted crystal, tip first; every triangle its own flat facet. */
	private static void shard(BufferBuilder b, Vec3d tip, Vec3d axis, double scale, int seed, double spin, Vec3d cam) {
		Vec3d u = axis.crossProduct(new Vec3d(0.31, 0.12, 0.94)).normalize();
		Vec3d v = axis.crossProduct(u);
		Vec3d[][] ring = new Vec3d[RING_H.length][SIDES];
		for (int i = 0; i < RING_H.length; i++) {
			for (int j = 0; j < SIDES; j++) {
				double a = Math.PI * 2 * j / SIDES + i * 0.21 + spin + (noise(seed, i * 16 + j) - 0.5) * 0.5;
				double r = RING_R[i] * scale * (0.78 + 0.44 * noise(seed + 7, i * 16 + j));
				double h = RING_H[i] * scale + (i == 0 || i == RING_H.length - 1 ? 0.0 : (noise(seed + 3, i * 16 + j) - 0.5) * 6.0 * scale);
				ring[i][j] = tip.add(axis.multiply(h)).add(u.multiply(Math.cos(a) * r)).add(v.multiply(Math.sin(a) * r));
			}
		}
		for (int i = 0; i + 1 < RING_H.length; i++) {
			for (int j = 0; j < SIDES; j++) {
				int k = (j + 1) % SIDES;
				if (i > 0) {
					facet(b, ring[i][j], ring[i + 1][j], ring[i][k], cam);
				}
				if (i + 2 < RING_H.length) {
					facet(b, ring[i][k], ring[i + 1][j], ring[i + 1][k], cam);
				}
			}
		}
	}

	private static void facet(BufferBuilder b, Vec3d p0, Vec3d p1, Vec3d p2, Vec3d cam) {
		Vec3d n = p1.subtract(p0).crossProduct(p2.subtract(p0)).normalize();
		float nx = (float) n.x;
		float ny = (float) n.y;
		float nz = (float) n.z;
		// The texture coordinates are barycentric, so the shader can find the facet's edges.
		b.vertex((float) (p0.x - cam.x), (float) (p0.y - cam.y), (float) (p0.z - cam.z)).texture(1, 0).color(-1).normal(nx, ny, nz);
		b.vertex((float) (p1.x - cam.x), (float) (p1.y - cam.y), (float) (p1.z - cam.z)).texture(0, 1).color(-1).normal(nx, ny, nz);
		b.vertex((float) (p2.x - cam.x), (float) (p2.y - cam.y), (float) (p2.z - cam.z)).texture(0, 0).color(-1).normal(nx, ny, nz);
	}

	/** The big shard's ridges, glowing out past its silhouette. */
	private static void drawRidges(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up) {
		Vec3d axis = new Vec3d(0, 1, 0).add(gap.along.multiply(0.16)).add(gap.across.multiply(-0.07)).normalize();
		Vec3d tip = gap.shardTip(t);
		Vec3d u = axis.crossProduct(new Vec3d(0.31, 0.12, 0.94)).normalize();
		Vec3d v = axis.crossProduct(u);
		int seed = gap.id * 31;
		double spin = t * 0.004;
		Vector3f eye = new Vector3f();
		BATCH.begin(Fx.LINE, 0.0F, view, proj, right, up);
		for (int j = 0; j < SIDES; j++) {
			Vec3d prev = null;
			for (int i = 0; i < RING_H.length; i++) {
				double a = Math.PI * 2 * j / SIDES + i * 0.21 + spin + (noise(seed, i * 16 + j) - 0.5) * 0.5;
				double r = RING_R[i] * (0.78 + 0.44 * noise(seed + 7, i * 16 + j));
				double h = RING_H[i] + (i == 0 || i == RING_H.length - 1 ? 0.0 : (noise(seed + 3, i * 16 + j) - 0.5) * 6.0);
				Vec3d p = tip.add(axis.multiply(h)).add(u.multiply(Math.cos(a) * r)).add(v.multiply(Math.sin(a) * r));
				if (prev != null) {
					Vector3f pa = rel(prev, cam);
					Vector3f pb = rel(p, cam);
					float dist = new Vector3f(pa).add(pb).mul(0.5F).length();
					BATCH.beam(pa, pb, eye, Math.max(0.04F, dist * 0.0022F), Fx.fade(WHITE, 0.55F), Fx.fade(WHITE, 0.55F));
					BATCH.beam(pa, pb, eye, Math.max(0.25F, dist * 0.014F), Fx.fade(CYAN, 0.26F), Fx.fade(CYAN, 0.26F));
				}
				prev = p;
			}
		}
		BATCH.end(true, 1.4F);
		// Dust of their universe streaming off it as it falls, left behind above it.
		BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
		for (int i = 0; i < 110; i++) {
			double life = (t * 0.9 + noise(seed, 800 + i) * 40.0) % 40.0;
			double h = noise(seed, 500 + i) * 140.0 + life * 1.8;
			double a = noise(seed, 600 + i) * Math.PI * 2;
			double r = 5.0 + noise(seed, 700 + i) * 20.0 + life * 0.35;
			Vec3d p = tip.add(axis.multiply(h)).add(u.multiply(Math.cos(a) * r)).add(v.multiply(Math.sin(a) * r));
			float fade = (float) Math.sin(Math.PI * life / 40.0);
			BATCH.sprite(rel(p, cam), 0.5F + 0.9F * (float) noise(seed, 900 + i), 0.0F, Fx.fade(i % 3 == 0 ? PURPLE : CYAN, 0.5F * fade));
		}
		BATCH.end(true, 1.3F);
	}

	// --- marks: the lock, the swaps, the contact ------------------------------------------

	private static void drawMarks(ClientWorld world, ClientGap gap, double t, float tickDelta, Vec3d cam, Matrix4f view, Matrix4f proj,
			Vector3f right, Vector3f up) {
		Vector3f eye = new Vector3f();
		boolean any = false;
		BATCH.begin(Fx.LINE, 0.0F, view, proj, right, up);
		// Blocks trading places: a hard white line from each up into the other universe, gone in a third of a second.
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
			Vec3d b = gap.swappedTo(s.pos());
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
			Vec3d b = gap.swappedTo(s.pos());
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
		float shatter;
		Vector3f shatterFrom = new Vector3f(0.0F, 1.0F, 0.0F);
		float lockOpen;
		float lockCracks;
		/** Height of the clouds over the camera, while they should break with the sky. */
		float cloudY = 10000.0F;

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
			Shaders.set(Shaders.gap, "Shatter", shatter);
			Shaders.set(Shaders.gap, "ShatterFrom", shatterFrom);
			Shaders.set(Shaders.gap, "CosmosTime", time);
			Shaders.set(Shaders.gap, "Lock", KeyTurn.LOCK_X, KeyTurn.LOCK_Y, lockOpen, lockCracks);
			Shaders.set(Shaders.gap, "CloudY", cloudY);
		}
	}

	private static Grade grade(ClientGap mine, float tickDelta, Vec3d cam, Matrix4f view, Matrix4f proj, int w, int h) {
		Grade g = new Grade();
		boolean on = false;
		for (ClientGap gap : ClientGaps.all()) {
			double t = gap.time(tickDelta);
			boolean live = gap.mine ? !gap.ended : t < GapTimeline.END + 40;
			if (t >= GapTimeline.ERASURE && live) {
				g.front = (float) GapTimeline.eraseFront(t);
				g.clamp = gap.mine ? 0.0F : gap.radius;
				g.offset = cam.subtract(gap.target.getX(), gap.target.getY(), gap.target.getZ());
				on = true;
			}
			// The sky breaks open from straight over the target, wherever it is seen from.
			if (t >= GapTimeline.TEAR && live) {
				g.shatter = Math.max(g.shatter, (float) GapTimeline.shatter(t));
				g.shatterFrom = rel(gap.contact.add(0, 150, 0), cam).normalize();
				float clouds = MinecraftClient.getInstance().world.getDimensionEffects().getCloudsHeight();
				if (!Float.isNaN(clouds)) {
					g.cloudY = (float) (clouds - cam.y);
				}
				on = true;
			}
		}
		if (mine != null) {
			double t = mine.time(tickDelta);
			if (t < GapTimeline.TURNED + 3) {
				g.glitch = (float) (0.15 * GapCamera.ease((t - 30) / 6.0) + 0.85 * Math.pow(MathHelper.clamp((t - 42) / 18.0, 0.0, 1.0), 2));
				g.flash = (float) (t < GapTimeline.TURNED ? Math.pow(MathHelper.clamp((t - 54) / 6.0, 0.0, 1.0), 2)
						: 1.0 - (t - GapTimeline.TURNED) / 3.0);
				g.seed = (float) Math.floor(t * 1.5);
				// The lock's light only where the shooter is looking through their own eyes, at the key.
				if (MinecraftClient.getInstance().options.getPerspective().isFirstPerson()) {
					g.lockOpen = KeyTurn.open(t);
					g.lockCracks = KeyTurn.cracks(t);
				}
				on = true;
			}
			if (t >= GapCamera.CUT_EYES && t < GapCamera.CUT_EYES + 4) {
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
