package io.github.kortev.shootingstar.client.gap;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gfx.Fx;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import io.github.kortev.shootingstar.client.gfx.Universe;
import io.github.kortev.shootingstar.gap.GapTimeline;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Draws Ginnungagap events into the world: the bridge of light standing over the target and the block of another
 * universe coming down it; the burst when it lands, the universe in it swelling out of the ground as a block, with
 * smaller blocks of it flung out and its stars streaming away; its fall back in on itself; then one full-screen
 * pass for the bad signal, the burst's light and shock front, the impact frames, the erasure and the black, and
 * finally the shooter again, the one thing left.
 */
public final class GapRender {
	private static final Target DEPTH = new Target(true, false);
	private static final Target COPY = new Target(false, false);
	private static final Fx BATCH = new Fx();
	private static final int WHITE = 0xFFFFFF;
	private static final int VIOLET = 0xA070FF;
	private static final int PALE = 0xD8C8FF;
	/** How high the bridge is drawn from. */
	private static final double BRIDGE_TOP = 600.0;
	/** When the bridge has reached the ground (the feed shows it reaching down). */
	private static final int BRIDGE = GapTimeline.SEND + 7;
	/** Blocks of that universe flung out of the burst, and stars streaming out of it. */
	private static final int FLUNG = 60;
	private static final int STARS = 520;
	/** Specks of the world lifting off as the black runs over it, out to this far from the target. */
	private static final int FLAKES = 1800;
	private static final int MOTES = 2400;
	private static final double FLAKE_REACH = 260.0;
	private static final Vector3f WHITE_LIGHT = new Vector3f(1.0F, 1.0F, 1.0F);
	/** The size of the picture being drawn, for the galaxies' sizes on screen. */
	private static int screenW = 1;
	private static int screenH = 1;

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
		screenW = w;
		screenH = h;
		Vector3f right = new Vector3f(view.m00(), view.m10(), view.m20());
		Vector3f up = new Vector3f(view.m01(), view.m11(), view.m21());
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		float time = (float) (world.getTime() + tickDelta);

		ClientGap mine = ClientGaps.mine();
		main.beginWrite(true);
		for (ClientGap gap : ClientGaps.all()) {
			double t = gap.time(tickDelta);
			if (!gap.mine) {
				TreeRender.drawSpectated(gap, t, cam, view, proj, w, h);
				main.beginWrite(true);
			}
			if (gap.mine ? gap.ended : t > GapTimeline.END + 40) {
				continue;
			}
			lock(gap, t, tickDelta, cam, view, proj, right, up);
			bridge(gap, t, cam, view, proj, right, up);
			block(gap, t, cam, view, proj, right, up, time);
			burst(gap, t, cam, view, proj, right, up, time);
		}

		Grade grade = grade(mine, tickDelta, cam, view, proj);
		if (grade != null) {
			ClientGap rebuilding = ClientGaps.rebuilding();
			boolean tree = rebuilding != null && TreeRender.draw(rebuilding, rebuilding.time(tickDelta), cam, view, proj, w, h);
			if (!tree) {
				grade.treeState.x = 0.0F;
			}
			DEPTH.ensure(w, h);
			DEPTH.copyDepthFrom(main);
			COPY.ensure(w, h);
			COPY.copyColorFrom(main);
			main.beginWrite(true);
			Post.begin();
			// The pass writes the depth back as it found it, so it needs the depth test on to write at all.
			RenderSystem.enableDepthTest();
			RenderSystem.depthFunc(GL11.GL_ALWAYS);
			RenderSystem.depthMask(true);
			RenderSystem.setShaderTexture(0, COPY.color());
			RenderSystem.setShaderTexture(1, DEPTH.depth());
			RenderSystem.setShaderTexture(2, tree ? TreeRender.color() : 0);
			grade.apply(proj, view, w, h, time);
			Post.quad(Shaders.gap);
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
			RenderSystem.setShaderTexture(0, 0);
			RenderSystem.setShaderTexture(1, 0);
			RenderSystem.setShaderTexture(2, 0);
			for (ClientGap gap : ClientGaps.all()) {
				flakes(gap, gap.time(tickDelta), cam, view, proj, right, up);
			}
			// The one thing the erasure does not take: people. Everyone near is drawn again over the black (this player
			// only when the camera is outside them), so in the nothing they can still see each other.
			if (grade.front >= 0.0F || grade.black > 0.0F) {
				for (AbstractClientPlayerEntity player : world.getPlayers()) {
					boolean self = player == client.player;
					if ((self ? context.camera().isThirdPerson() : player.squaredDistanceTo(cam) < 128.0 * 128.0) && !player.isSpectator()) {
						redrawPlayer(client, player, cam, view, tickDelta);
					}
				}
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

	// --- the bridge and the block coming down it ------------------------------------------

	/** Height of the block's centre over the point of contact: high above the world until the inbound shot, then down. */
	static double blockHeight(double t) {
		if (t < GapTimeline.INBOUND) {
			return GapCamera.blockHeight(GapTimeline.INBOUND) + (GapTimeline.INBOUND - t) * 45.0 + GapCamera.BLOCK;
		}
		return GapCamera.blockHeight(t) + GapCamera.BLOCK;
	}

	/** A shaft of light from the block (or the top of the sky) down to the target, a rainbow at its edges, a star at its foot. */
	private static void bridge(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up) {
		if (t < BRIDGE - 3 || t > GapTimeline.CONTACT + 6) {
			return;
		}
		double reach = GapCamera.ease((t - BRIDGE + 3) / 3.0);
		float fade = (float) (t > GapTimeline.CONTACT ? 1.0 - (t - GapTimeline.CONTACT) / 6.0 : 1.0);
		double top = Math.min(BRIDGE_TOP, blockHeight(t));
		Vec3d a = gap.contact.add(0, top, 0);
		Vec3d b = gap.contact.add(0, top * (1.0 - reach), 0);
		Vector3f ra = rel(a, cam);
		Vector3f rb = rel(b, cam);
		Vector3f eye = new Vector3f();
		float pulse = 1.0F + 0.15F * (float) Math.sin(t * 1.7);
		float width = 2.2F * pulse * (1.0F + 0.8F * (float) Math.exp(-Math.max(0.0, t - BRIDGE) / 6.0));
		BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
		BATCH.beam(ra, rb, eye, width, Fx.fade(WHITE, fade), Fx.fade(PALE, fade));
		BATCH.beam(ra, rb, eye, width * 3.0F, Fx.fade(VIOLET, 0.55F * fade), Fx.fade(VIOLET, 0.45F * fade));
		BATCH.end(true, 1.6F);
		// The colours split out either side of it.
		Vector3f mid = new Vector3f(ra).add(rb).mul(0.5F);
		Vector3f side = new Vector3f(rb).sub(ra).cross(mid).normalize().mul(width * 1.35F);
		BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
		BATCH.beam(new Vector3f(ra).add(side), new Vector3f(rb).add(side), eye, width * 0.4F, Fx.argb(1.0F, 0.3F, 0.55F, 0.4F * fade),
				Fx.argb(1.0F, 0.35F, 0.45F, 0.3F * fade));
		BATCH.beam(new Vector3f(ra).sub(side), new Vector3f(rb).sub(side), eye, width * 0.4F, Fx.argb(0.2F, 0.9F, 1.0F, 0.4F * fade),
				Fx.argb(0.3F, 1.0F, 0.6F, 0.3F * fade));
		BATCH.end(true, 1.2F);
		if (reach >= 1.0) {
			BATCH.begin(Fx.SPIKES, 0.0F, view, proj, right, up);
			BATCH.sprite(rel(gap.contact.add(0, 0.5, 0), cam), 9.0F * pulse, (float) (t * 0.02), Fx.fade(WHITE, fade));
			BATCH.end(true, 1.6F);
			BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
			BATCH.sprite(rel(gap.contact.add(0, 1.0, 0), cam), 16.0F * pulse, 0.0F, Fx.fade(VIOLET, 0.6F * fade));
			BATCH.end(true, 1.3F);
		}
	}

	/** The block of the other universe, tumbling down the bridge, a halo round it and its wake left glowing above it. */
	private static void block(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up, float time) {
		double height = blockHeight(t);
		if (t >= GapTimeline.CONTACT || height > BRIDGE_TOP + 40.0) {
			return;
		}
		Vec3d c = gap.contact.add(0, height, 0);
		Vector3f rc = rel(c, cam);
		float s = (float) GapCamera.BLOCK;
		Matrix4f model = new Matrix4f().translation(rc).rotateY((float) (t * 0.05)).rotateX((float) (t * 0.031)).rotateZ(0.3F).scale(s);
		// Dark glass, so the web inside stands out against the daytime sky like a hole into space.
		universe(model, view, proj, Universe.FULL, 0, 1.3F, 0.88F, 1.8F, 0.2F);
		Vector3f eye = new Vector3f();
		BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
		BATCH.sprite(rc, s * 3.2F, 0.0F, Fx.fade(VIOLET, 0.55F));
		BATCH.sprite(rc, s * 1.6F, 0.0F, Fx.fade(WHITE, 0.4F));
		BATCH.end(true, 1.5F);
		// The air it tears through, glowing behind it.
		double wake = Math.min(120.0, height);
		BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
		BATCH.beam(rc, rel(c.add(0, wake, 0), cam), eye, s * 1.1F, Fx.fade(PALE, 0.7F), Fx.fade(VIOLET, 0.0F));
		BATCH.end(true, 1.4F);
	}

	// --- the burst ---------------------------------------------------------------------

	/** Where the burst sits: the block swelling out of the point of contact, a quarter of it sunk into the ground. */
	static Vec3d burstCenter(ClientGap gap, double t) {
		return gap.contact.add(0, GapCamera.burstHalf(gap, t) * 0.25, 0);
	}

	private static void burst(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up, float time) {
		if (t < GapTimeline.CONTACT || t >= GapTimeline.ERASURE + 2) {
			return;
		}
		double e = t - GapTimeline.CONTACT;
		double h = GapCamera.burstHalf(gap, t);
		Vec3d bc = burstCenter(gap, t);
		Vector3f rc = rel(bc, cam);
		float heat = (float) Math.pow(Math.max(0.0, 1.0 - e / 20.0), 1.5);
		if (h > 0.3) {
			Matrix4f model = new Matrix4f().translation(rc).scale((float) h);
			universe(model, view, proj, Universe.FULL, 0, 1.35F, 0.8F, 3.0F, heat);
		}
		// The universe bursting out of its block: its galaxies flying out over the land and past the camera, ten times as
		// far as the block is wide, then all drawn back in as it falls in on itself.
		double outward = GapCamera.ease((t - GapTimeline.CONTACT - 6.0) / (GapTimeline.COLLAPSE - GapTimeline.CONTACT - 6.0));
		double back = MathHelper.clamp((t - GapTimeline.COLLAPSE) / (GapTimeline.ERASURE - GapTimeline.COLLAPSE), 0.0, 1.0);
		double reach = GapCamera.blastHalf(gap) * (1.0 + 11.0 * Math.pow(outward, 1.5)) * Math.pow(1.0 - back, 2.0);
		if (outward > 0.0 && reach > 0.5) {
			Matrix4f flying = new Matrix4f().translation(rc).rotateY((float) (t * 0.004)).scale((float) reach);
			float light = (float) (1.4 * (1.0 - 0.5 * outward) * (1.0 - back) + 2.0 * back * (1.0 - back));
			Universe.draw(new Matrix4f(view).mul(flying), proj, screenW, screenH, Universe.FULL, 0, light, 0.5F, WHITE_LIGHT, 0.0F, 0.0F,
					0xFFFFFF, 0.0F);
		}
		flung(gap, t, cam, view, proj, time);
		column(gap, t, e, h, bc, cam, view, proj, right, up);
		shockWall(gap, t, e, cam, view, proj, right, up);

		// Its light, and the stars streaming out of it (or, at the end, back into it).
		float collapse = (float) MathHelper.clamp((t - GapTimeline.COLLAPSE) / (GapTimeline.ERASURE - GapTimeline.COLLAPSE), 0.0, 1.0);
		BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
		float glow = (float) (0.35 + 0.65 * Math.exp(-e / 8.0)) * (1.0F - collapse * 0.5F);
		BATCH.sprite(rc, (float) (h * 2.4 + 6.0), 0.0F, Fx.fade(VIOLET, 0.5F * glow));
		BATCH.sprite(rc, (float) (h * 1.2 + 3.0), 0.0F, Fx.fade(WHITE, 0.45F * glow + 0.5F * heat));
		if (collapse > 0.0F) {
			BATCH.sprite(rc, 4.0F + 30.0F * collapse * collapse, 0.0F, Fx.fade(WHITE, collapse));
		}
		BATCH.end(true, 1.6F);
		BATCH.begin(Fx.STREAK, 0.0F, view, proj, right, up);
		double full = GapCamera.blastHalf(gap);
		for (int i = 0; i < STARS; i++) {
			double born = noise(gap.id, i) * 70.0;
			double age = e - born;
			if (age < 0.0 || collapse >= 1.0F) {
				continue;
			}
			double life = 14.0 + noise(gap.id, i + 900) * 16.0;
			Vec3d dir = new Vec3d(noise(gap.id, i + 300) - 0.5, noise(gap.id, i + 600) * 0.9 - 0.15, noise(gap.id, i + 1200) - 0.5).normalize();
			double speed = 2.5 + noise(gap.id, i + 1500) * 6.0;
			double r;
			float a;
			if (collapse > 0.0F) {
				// Drawn back in.
				r = full * 3.0 * (1.0 - collapse) * (0.4 + noise(gap.id, i + 1800));
				a = (float) Math.sin(Math.PI * collapse) * 0.9F;
			} else {
				if (age > life) {
					continue;
				}
				r = h * 0.9 + speed * age;
				a = (float) (1.0 - age / life);
			}
			Vector3f p = rel(bc.add(dir.multiply(r)), cam);
			int color = switch (i % 5) {
				case 0 -> 0xBFA0FF;
				case 1 -> 0x9FE8FF;
				case 2 -> 0xFFD8A0;
				default -> 0xFFFFFF;
			};
			BATCH.stretched(p, new Vector3f((float) dir.x, (float) dir.y, (float) dir.z), (float) (speed * 2.6), 0.45F + 0.25F * (i % 3),
					Fx.fade(color, a));
		}
		BATCH.end(true, 2.6F);
	}

	/**
	 * The universe in the block blasting out of its top: a shaft of its light thrown up into the sky, widest and
	 * brightest at the start, pulsing, then drawn back down into the block as it falls in.
	 */
	private static void column(ClientGap gap, double t, double e, double h, Vec3d bc, Vec3d cam, Matrix4f view, Matrix4f proj,
			Vector3f right, Vector3f up) {
		float collapse = (float) MathHelper.clamp((t - GapTimeline.COLLAPSE) / (GapTimeline.ERASURE - GapTimeline.COLLAPSE), 0.0, 1.0);
		float rise = (float) GapCamera.ease(e / 10.0);
		float k = (float) (0.55 + 0.45 * Math.exp(-e / 14.0)) * (1.0F - collapse);
		if (k <= 0.01F || rise <= 0.0F) {
			return;
		}
		float pulse = 1.0F + 0.12F * (float) Math.sin(t * 2.3) + 0.06F * (float) Math.sin(t * 5.1);
		Vec3d top = bc.add(0, h, 0);
		Vector3f a = rel(top, cam);
		Vector3f b = rel(top.add(0, 520.0 * rise * (1.0F - collapse), 0), cam);
		Vector3f eye = new Vector3f();
		float w = (float) h * 0.55F * pulse;
		BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
		BATCH.beam(a, b, eye, w * 0.45F, Fx.fade(WHITE, k), Fx.fade(PALE, k * 0.8F));
		BATCH.beam(a, b, eye, w, Fx.fade(VIOLET, 0.7F * k), Fx.fade(VIOLET, 0.4F * k));
		BATCH.beam(a, b, eye, w * 2.2F, Fx.argb(0.35F, 0.2F, 0.9F, 0.35F * k), Fx.argb(0.3F, 0.15F, 0.8F, 0.15F * k));
		BATCH.end(true, 2.2F);
		// Where it leaves the block, a blinding crown.
		BATCH.begin(Fx.SPIKES, 0.0F, view, proj, right, up);
		BATCH.sprite(a, (float) h * 0.9F * pulse, (float) (t * 0.01), Fx.fade(WHITE, k));
		BATCH.end(true, 2.0F);
	}

	/** The burst's front as a square of light standing on the ground, racing out over everything. */
	private static void shockWall(ClientGap gap, double t, double e, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right,
			Vector3f up) {
		if (t >= GapTimeline.COLLAPSE) {
			return;
		}
		double half = shock(gap, e);
		float fade = (float) (Math.exp(-e / 60.0) * (1.0 - GapCamera.ease((t - GapTimeline.COLLAPSE + 20) / 20.0)));
		if (fade <= 0.02F) {
			return;
		}
		float height = (float) (6.0 + half * 0.07);
		Vec3d c = gap.contact.add(0, height - 2.0, 0);
		BATCH.begin(Fx.WALL, 0.0F, view, proj, right, up);
		Vector3f vert = new Vector3f(0, height, 0);
		for (int side = 0; side < 4; side++) {
			double ax = side == 0 ? 1 : side == 1 ? -1 : 0;
			double az = side == 2 ? 1 : side == 3 ? -1 : 0;
			// Each wall a little longer than the side, so the corners meet.
			Vec3d mid = c.add(ax * half, 0, az * half);
			Vector3f along = new Vector3f((float) Math.abs(az), 0, (float) Math.abs(ax)).mul((float) (half * 1.04));
			BATCH.flat(rel(mid, cam), along, vert, Fx.fade(PALE, fade));
		}
		BATCH.end(true, 1.8F);
	}

	/** How far the square front has run out from the point of contact, {@code e} ticks after it. */
	static double shock(ClientGap gap, double e) {
		return 4.0 + 2.6 * gap.radius * (1.0 - Math.exp(-e / 45.0));
	}

	/** Smaller blocks of the universe thrown out of the burst's faces, tumbling up and out, flaring white as they go. */
	private static void flung(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, float time) {
		for (int i = 0; i < FLUNG; i++) {
			double born = GapTimeline.CONTACT + 3.0 + i * 1.5 + noise(gap.id, i + 40) * 6.0;
			double age = t - born;
			double life = 40.0 + noise(gap.id, i + 70) * 30.0;
			if (age < 0.0 || age > life || t >= GapTimeline.COLLAPSE + 4) {
				continue;
			}
			double h = GapCamera.burstHalf(gap, born);
			Vec3d dir = new Vec3d(noise(gap.id, i + 100) - 0.5, 0.35 + noise(gap.id, i + 130) * 0.9, noise(gap.id, i + 160) - 0.5).normalize();
			double speed = 1.4 + noise(gap.id, i + 190) * 2.0;
			Vec3d start = burstCenter(gap, born).add(dir.multiply(h * 0.95));
			Vec3d pos = start.add(dir.multiply(speed * age)).add(0, -0.018 * age * age, 0);
			double fade = age / life;
			float size = (float) ((2.4 + noise(gap.id, i + 220) * 4.8) * (1.0 - Math.pow(fade, 3.0)));
			if (size < 0.05F) {
				continue;
			}
			float flare = (float) MathHelper.clamp((fade - 0.8) / 0.2, 0.0, 1.0);
			Vector3f axis = new Vector3f((float) noise(gap.id, i + 250) - 0.5F, (float) noise(gap.id, i + 280) - 0.5F, 0.3F).normalize();
			Matrix4f model = new Matrix4f().translation(rel(pos, cam)).rotate((float) (age * (0.08 + 0.1 * noise(gap.id, i + 310))), axis)
					.scale(size);
			universe(model, view, proj, Universe.TINY, 1 + i % 47, 1.3F, 0.85F, 2.2F, flare);
		}
	}

	/**
	 * The world flaking away as the black runs over it: specks of light lifting off the ground just behind its front
	 * and rising, spinning, as they go out. Drawn over the black, since they are what is left of what it took.
	 */
	private static void flakes(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up) {
		ClientWorld world = MinecraftClient.getInstance().world;
		if (world != null && gap.rebuildAt >= 0) {
			double r = gap.rebuild((float) (t - gap.age));
			motes(gap, world, r, cam, view, proj, right, up);
			landing(gap, r, cam, view, proj, right, up);
			return;
		}
		if (world == null || t < GapTimeline.ERASURE || t > GapTimeline.NOTHING + 40) {
			return;
		}
		// The front runs out as a diamond (it counts blocks along x and z, see eraseFront), corner to corner round it.
		double[] cx = {1.0, 0.0, -1.0, 0.0, 1.0};
		double[] cz = {0.0, 1.0, 0.0, -1.0, 0.0};
		BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
		for (int i = 0; i < FLAKES; i++) {
			double r = FLAKE_REACH * Math.sqrt(noise(gap.id, i + 3000));
			// When the front gets that far out: eraseFront turned round.
			double born = GapTimeline.ERASURE + 140.0 * Math.pow(r / 1400.0, 2.0 / 3.0);
			double life = 18.0 + noise(gap.id, i + 3300) * 20.0;
			double age = t - born;
			if (age < 0.0 || age > life) {
				continue;
			}
			double u = noise(gap.id, i + 3600) * 4.0;
			int side = Math.min(3, (int) u);
			double f = u - side;
			double x = gap.target.getX() + 0.5 + r * (cx[side] + (cx[side + 1] - cx[side]) * f);
			double z = gap.target.getZ() + 0.5 + r * (cz[side] + (cz[side + 1] - cz[side]) * f);
			int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, MathHelper.floor(x), MathHelper.floor(z));
			if (top <= world.getBottomY()) {
				continue;
			}
			double rise = age * (0.06 + 0.12 * noise(gap.id, i + 3900)) + 0.004 * age * age;
			double k = age / life;
			float alpha = (float) (Math.min(1.0, age / 2.0) * (1.0 - k * k));
			float size = (float) ((0.25 + 0.45 * noise(gap.id, i + 4200)) * (1.0 - 0.5 * k));
			double spin = age * 0.3 + i;
			Vector3f a = new Vector3f((float) Math.cos(spin), (float) (0.5 * Math.sin(spin * 0.7)), (float) Math.sin(spin)).normalize().mul(size);
			Vector3f b = new Vector3f(0.0F, 1.0F, 0.0F).cross(a).normalize().mul(size);
			int color = i % 3 == 0 ? 0x9FE8FF : i % 3 == 1 ? PALE : WHITE;
			BATCH.flat(rel(new Vec3d(x, top + rise, z), cam), a, b, Fx.fade(color, alpha));
		}
		BATCH.end(true, 2.2F);
	}

	/**
	 * The world coming back: specks of light raining down out of the void onto the ground just ahead of the rebuild's
	 * front, faster as they near it, each landing in a flare as its block is put back.
	 */
	private static void motes(ClientGap gap, ClientWorld world, double r, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up) {
		if (r < GapTimeline.REBUILD_SWEEP - 40 || r > GapTimeline.REBUILD_DONE + 10) {
			return;
		}
		BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
		for (int i = 0; i < MOTES; i++) {
			// Thick near the middle, where the camera is, and out to the edge of what can be seen.
			double rim = Math.min(rebuildReach(gap) * 0.5, gap.radius);
			double reach = rim + (rebuildReach(gap) * 0.9 - rim) * Math.pow(noise(gap.id, i + 5000), 1.4);
			double fall = 26.0 + noise(gap.id, i + 5300) * 30.0;
			double until = rebuilt(gap, reach) - r;
			if (until > fall || until < -6.0) {
				continue;
			}
			// The front is a ring (the grade pass): round it.
			double a = noise(gap.id, i + 5600) * Math.PI * 2.0;
			double x = gap.target.getX() + 0.5 + reach * Math.cos(a);
			double z = gap.target.getZ() + 0.5 + reach * Math.sin(a);
			int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, MathHelper.floor(x), MathHelper.floor(z));
			if (top <= world.getBottomY()) {
				continue;
			}
			float alpha;
			float size;
			double y;
			int color;
			if (until > 0.0) {
				// Falling, faster and faster, a streak of light.
				double k = 1.0 - until / fall;
				y = top + 0.5 + until * 0.4 + 0.035 * until * until;
				alpha = (float) Math.min(1.0, k * 4.0);
				size = (float) (0.18 + 0.2 * noise(gap.id, i + 5900));
				color = i % 2 == 0 ? PALE : WHITE;
			} else {
				// Landed: a flare spreading over the new block, gone in a moment.
				double k = -until / 6.0;
				y = top + 0.5;
				alpha = (float) (1.0 - k);
				size = (float) (0.6 + 1.6 * k);
				color = WHITE;
			}
			Vec3d at = new Vec3d(x, y, z);
			// Not up against the lens, where one would be a streak across the whole picture.
			alpha *= (float) GapCamera.ease((at.distanceTo(cam) - 3.0) / 9.0);
			if (alpha <= 0.01F) {
				continue;
			}
			Vector3f sa = new Vector3f(right).mul(size);
			Vector3f sb = new Vector3f(up).mul(size * (until > 0.0 ? 3.0F : 1.0F));
			BATCH.flat(rel(at, cam), sa, sb, Fx.fade(color, alpha));
		}
		BATCH.end(true, 2.4F);
	}

	/**
	 * The lock of light the Genesis Key goes into, seen from outside: rings closing in out of nothing in front of the
	 * shooter, flaring as the key turns home, and a column of light going up out of it into the sky, which the camera
	 * follows up. The shooter's own first-person view draws its own (KeyTurn) until the camera leaves them.
	 */
	private static void lock(ClientGap gap, double t, float tickDelta, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right,
			Vector3f up) {
		if (t < 6.0 || t > GapTimeline.RISE + 40 || gap.mine && t < GapTimeline.RISE) {
			return;
		}
		ClientWorld world = MinecraftClient.getInstance().world;
		PlayerEntity shooter = world == null ? null : world.getPlayerByUuid(gap.shooter);
		if (shooter == null) {
			return;
		}
		Vec3d look = shooter.getRotationVec(tickDelta);
		Vec3d centre = shooter.getCameraPosVec(tickDelta).add(look.multiply(0.95)).add(0.0, -0.3, 0.0);
		Vector3f c = rel(centre, cam);
		Vector3f n = new Vector3f((float) look.x, (float) look.y, (float) look.z);
		Vector3f u = new Vector3f(n).cross(0.0F, 1.0F, 0.0F);
		if (u.lengthSquared() < 1.0E-4F) {
			u.set(1.0F, 0.0F, 0.0F);
		}
		u.normalize();
		Vector3f v = new Vector3f(u).cross(n).normalize();
		double form = GapCamera.ease((t - 8.0) / 14.0);
		double flare = t < 37.0 ? 0.0 : GapCamera.ease((t - 37.0) / 8.0);
		float click = (float) (Math.exp(-Math.max(0.0, t - 30.0) / 2.0) * (t >= 30.0 ? 1.0 : 0.0)
				+ Math.exp(-Math.max(0.0, t - 37.0) / 3.0) * (t >= 37.0 ? 1.5 : 0.0));
		float alpha = (float) (form * (1.0 - flare));
		BATCH.begin(Fx.RING, 0.1F, view, proj, right, up);
		for (int k = 0; k < 3; k++) {
			float size = (float) ((0.32 - 0.08 * k) * (1.0 + 2.5 * (1.0 - form)) * (1.0 + 4.0 * flare));
			BATCH.flat(c, new Vector3f(u).mul(size), new Vector3f(v).mul(size), Fx.fade(k == 2 ? WHITE : PALE, alpha * (0.6F + 0.4F * click)));
		}
		BATCH.end(true, 2.4F);
		BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
		BATCH.sprite(c, 0.25F + 0.4F * click, 0.0F, Fx.fade(WHITE, (float) form * Math.min(1.0F, 0.3F + 0.5F * click)));
		BATCH.end(true, 2.4F);
		if (t >= 37.0) {
			// Up out of the lock into the sky as it turns home: fast, then fading as the camera goes up after it.
			double e = t - 37.0;
			double reach = 400.0 * GapCamera.ease(e / 6.0);
			float fade = (float) (1.0 - GapCamera.ease((e - 10.0) / 30.0));
			Vec3d top = centre.add(0.0, reach, 0.0);
			BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
			BATCH.beam(c, rel(top, cam), new Vector3f(), (float) (0.18 + 0.5 * Math.exp(-e / 4.0)), Fx.fade(WHITE, fade),
					Fx.fade(VIOLET, 0.0F));
			BATCH.end(true, 2.6F);
		}
	}

	/**
	 * The light the tree sends down its long root landing at the shooter's feet: a ring of it bursting out over the
	 * ground from them, a column of glow standing up round them, and sparks lifting off, as the world starts to come back.
	 */
	private static void landing(ClientGap gap, double r, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up) {
		double e = r - GapTimeline.REBUILD_SWEEP;
		if (gap.rebuildFrom == null || e < -4.0 || e > 60.0) {
			return;
		}
		Vec3d feet = gap.rebuildFrom.add(0.0, 0.1, 0.0);
		Vector3f at = rel(feet, cam);
		float arrive = (float) Math.exp(-Math.abs(e) / 6.0);
		BATCH.begin(Fx.RING, 0.08F, view, proj, right, up);
		for (int k = 0; k < 3; k++) {
			double age = e - k * 5.0;
			if (age < 0.0 || age > 45.0) {
				continue;
			}
			float size = (float) (1.5 + age * (1.6 + 0.5 * k));
			float alpha = (float) Math.pow(1.0 - age / 45.0, 1.5);
			// Gone before it gets out as far as the lens, rather than an arc of light sweeping across the whole picture.
			double lens = Math.hypot(cam.x - feet.x, cam.z - feet.z);
			alpha *= (float) (1.0 - GapCamera.ease((size - lens + 6.0) / 5.0));
			BATCH.flat(at, new Vector3f(size, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, size), Fx.fade(k == 0 ? WHITE : PALE, alpha));
		}
		BATCH.end(false, 2.6F);
		BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
		// The column: soft glows stacked up through them, brightest as it lands.
		for (int i = 0; i < 6; i++) {
			Vector3f p = rel(feet.add(0.0, 0.5 + i * 0.7, 0.0), cam);
			BATCH.sprite(p, 1.6F + i * 0.2F, 0.0F, Fx.fade(i % 2 == 0 ? PALE : WHITE, 0.5F * arrive * (1.0F - i / 7.0F)));
		}
		// Sparks lifting off round them.
		for (int i = 0; i < 60; i++) {
			double born = noise(gap.id, i + 7000) * 20.0;
			double age = e - born;
			if (age < 0.0 || age > 30.0) {
				continue;
			}
			double a = noise(gap.id, i + 7100) * Math.PI * 2.0;
			double out = 0.6 + age * (0.05 + 0.08 * noise(gap.id, i + 7200));
			Vec3d p = feet.add(Math.cos(a) * out, age * (0.08 + 0.1 * noise(gap.id, i + 7300)), Math.sin(a) * out);
			BATCH.sprite(rel(p, cam), 0.12F, 0.0F, Fx.fade(i % 3 == 0 ? VIOLET : PALE, (float) (1.0 - age / 30.0)));
		}
		BATCH.end(true, 2.2F);
	}

	/** A universe in its block (or a piece of one), {@code model} placing the block's cube from -1 to 1 in the world. */
	private static void universe(Matrix4f model, Matrix4f view, Matrix4f proj, int detail, int turn, float brightness, float dark,
			float edge, float heat) {
		Universe.draw(new Matrix4f(view).mul(model), proj, screenW, screenH, detail, turn, brightness, 1.0F, WHITE_LIGHT, dark, edge, 0xE6DCFF,
				heat);
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
		float remake;
		float clamp;
		Vec3d offset = Vec3d.ZERO;
		Vector4f burst = new Vector4f();
		Vector3f burstLight = new Vector3f();
		float shock = -1.0F;
		float skyMix;
		/** The foot of Yggdrasil (relative to the target) and its height; how much it is there. */
		Vector4f tree = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
		Vector4f treeState = new Vector4f();
		/** The underside of the clouds, relative to the target block. */
		float cloudY = 10000.0F;
		/** How far out the rebuild's ring goes. */
		float reach = 600.0F;

		void apply(Matrix4f proj, Matrix4f view, int w, int h, float time) {
			Matrix4f inv = new Matrix4f(proj).mul(view).invert();
			Shaders.set(Shaders.gap, "InvViewProj", inv);
			Shaders.set(Shaders.gap, "ScreenSize", (float) w, (float) h);
			Shaders.set(Shaders.gap, "Time", time);
			Shaders.set(Shaders.gap, "CamOffset", (float) offset.x, (float) offset.y, (float) offset.z);
			Shaders.set(Shaders.gap, "Front", front);
			Shaders.set(Shaders.gap, "Remake", remake);
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
			Shaders.set(Shaders.gap, "Burst", burst.x, burst.y, burst.z, burst.w);
			Shaders.set(Shaders.gap, "BurstLight", burstLight);
			Shaders.set(Shaders.gap, "Shock", shock);
			Shaders.set(Shaders.gap, "SkyMix", skyMix);
			Shaders.set(Shaders.gap, "Tree", tree.x, tree.y, tree.z, tree.w);
			Shaders.set(Shaders.gap, "TreeState", treeState.x, treeState.y, treeState.z, treeState.w);
			Shaders.set(Shaders.gap, "CloudY", cloudY);
			Shaders.set(Shaders.gap, "Reach", reach);
		}
	}

	/**
	 * The rebuild: Yggdrasil growing up out of the void, and then the world put back block by block, out from where the
	 * shooter stands, until it is all there again.
	 */
	private static void rebuild(Grade g, ClientGap gap, double r) {
		double front = rebuildFront(gap, r);
		g.front = (float) front;
		g.remake = front >= 0.0 ? 1.0F : 0.0F;
		g.reach = (float) rebuildReach(gap);
		if (gap.rebuildFrom == null) {
			return;
		}
		Vec3d feet = TreeRender.foot(gap).subtract(gap.target.getX(), gap.target.getY(), gap.target.getZ());
		g.tree.set((float) feet.x, (float) feet.y, (float) feet.z, (float) TreeRender.width(gap));
		g.treeState.set((float) TreeRender.there(r), (float) skyBack(gap, r), 0.0F, 0.0F);
		// Its light on the world as it comes back round it: violet and cold, flaring as the light goes out through it.
		double height = TreeRender.height(gap);
		Vec3d glow = TreeRender.foot(gap).add(0.0, height * 0.3, 0.0).subtract(gap.target.getX(), gap.target.getY(), gap.target.getZ());
		double flare = Math.exp(-Math.abs(r - GapTimeline.REBUILD_SWEEP) / 30.0);
		g.burst.set((float) glow.x, (float) glow.y, (float) glow.z, (float) (height * 0.9));
		g.burstLight.set(0.42F, 0.38F, 0.95F).mul((float) (TreeRender.there(r) * TreeRender.grown(r) * (0.35 + 0.9 * flare)));
	}

	/**
	 * How far out the world has been built back, {@code r} ticks into the rebuild: from the rim of the hole (there is
	 * nothing to put back inside it), easing out, slowest near the hole where it can best be seen, out past the fog and
	 * easing to a stop as it gets there (by when the grade pass has faded out the last of the dark, so nothing changes
	 * when it is done); then done (-1). The middle of the hole before the sweep (0), so it is all dark.
	 */
	public static double rebuildFront(ClientGap gap, double r) {
		double x = (r - GapTimeline.REBUILD_SWEEP) / (GapTimeline.REBUILD_DONE - GapTimeline.REBUILD_SWEEP);
		if (x >= 1.0) {
			return -1.0;
		}
		if (x <= 0.0) {
			return 0.0;
		}
		double reach = rebuildReach(gap);
		double rim = Math.min(reach * 0.5, gap.radius);
		return rim + (reach - rim) * GapCamera.ease(x);
	}

	/**
	 * How far out from the middle of the hole the ring goes before the world is simply back: just past the edge of
	 * what can be seen from the rim at this player's view distance, so it is in sight all the way out (rather than
	 * gone into the fog in its first few seconds), and the rest of the world comes back with the sky.
	 */
	public static double rebuildReach(ClientGap gap) {
		double view = MinecraftClient.getInstance().options.getClampedViewDistance() * 16.0;
		return MathHelper.clamp(view + gap.radius + 48.0, gap.radius + 120.0, 600.0);
	}

	/** How much of the sky's light is back during a rebuild, 0 to 1, as the grade pass brings the sky back. */
	public static float skyLight(float tickDelta) {
		ClientGap gap = ClientGaps.rebuilding();
		return gap == null ? 1.0F : (float) skyBack(gap, gap.rebuild(tickDelta));
	}

	/** How much of the sky is back, {@code r} ticks into the rebuild: as the grade pass brings it back (ss_gap). */
	static double skyBack(ClientGap gap, double r) {
		double front = rebuildFront(gap, r);
		if (front < 0.0) {
			return r < GapTimeline.REBUILD_SWEEP ? 0.0 : 1.0;
		}
		double reach = rebuildReach(gap);
		double x = MathHelper.clamp((front - 0.1 * reach) / (0.77 * reach), 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}

	/** When the rebuild's front gets out as far as {@code reach}: rebuildFront turned round. */
	private static double rebuilt(ClientGap gap, double reach) {
		double full = rebuildReach(gap);
		double rim = Math.min(full * 0.5, gap.radius);
		double k = MathHelper.clamp((reach - rim) / (full - rim), 0.0, 1.0);
		double lo = 0.0;
		double hi = 1.0;
		for (int i = 0; i < 16; i++) {
			double mid = (lo + hi) * 0.5;
			if (GapCamera.ease(mid) < k) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return GapTimeline.REBUILD_SWEEP + (GapTimeline.REBUILD_DONE - GapTimeline.REBUILD_SWEEP) * lo;
	}

	private static Grade grade(ClientGap mine, float tickDelta, Vec3d cam, Matrix4f view, Matrix4f proj) {
		Grade g = new Grade();
		boolean on = false;
		for (ClientGap gap : ClientGaps.all()) {
			double t = gap.time(tickDelta);
			// Everyone's, now the whole world goes: through the black, held in it, and out again with the rebuild.
			boolean live = !gap.ended && gap.spectateAt < 0;
			if (!live || t < GapTimeline.INBOUND) {
				continue;
			}
			g.offset = cam.subtract(gap.target.getX(), gap.target.getY(), gap.target.getZ());
			ClientWorld world = MinecraftClient.getInstance().world;
			float clouds = world == null ? Float.NaN : world.getDimensionEffects().getCloudsHeight();
			g.cloudY = Float.isNaN(clouds) ? 10000.0F : clouds - gap.target.getY() - 0.5F;
			on = true;
			if (t < GapTimeline.CONTACT) {
				// The block coming down lights the ground under it, more and more as it nears.
				double near = (t - GapTimeline.INBOUND) / (GapTimeline.CONTACT - GapTimeline.INBOUND);
				Vec3d bc = gap.contact.add(0, blockHeight(t), 0).subtract(gap.target.getX(), gap.target.getY(), gap.target.getZ());
				g.burst.set((float) bc.x, (float) bc.y, (float) bc.z, (float) (GapCamera.BLOCK * (4.0 + 6.0 * near)));
				g.burstLight.set(0.62F, 0.42F, 1.0F).mul((float) (0.3 + 1.6 * near * near));
				continue;
			}
			// The hit: two ticks of pure white before the first frame.
			if (t >= GapTimeline.CONTACT && t < GapTimeline.CONTACT + 2) {
				g.flash = 1.0F;
			}
			// The impact frames, for everyone: the shooter's own cut of them (GapCamera), or anyone else's own eyes.
			if (t >= GapTimeline.FRAMES && t < GapTimeline.BLAST) {
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
				Vec3d focus = gap.contact.add(0, GapCamera.burstHalf(gap, t) * 0.4, 0);
				Vector4f p = new Vector4f((float) (focus.x - cam.x), (float) (focus.y - cam.y), (float) (focus.z - cam.z), 1.0F);
				view.transform(p);
				proj.transform(p);
				if (p.w > 1.0E-3F) {
					g.focusX = MathHelper.clamp(p.x / p.w * 0.5F + 0.5F, 0.05F, 0.95F);
					g.focusY = MathHelper.clamp(p.y / p.w * 0.5F + 0.5F, 0.05F, 0.95F);
				}
				on = true;
			}
			if (t >= GapTimeline.ERASURE) {
				g.front = (float) GapTimeline.eraseFront(t);
				// The whole world, for everyone in it.
				g.clamp = 0.0F;
			}
			double r = gap.rebuild(tickDelta);
			if (r >= 0.0) {
				rebuild(g, gap, r);
			}
			if (t < GapTimeline.ERASURE + 4) {
				double e = t - GapTimeline.CONTACT;
				double h = GapCamera.burstHalf(gap, t);
				Vec3d bc = burstCenter(gap, t).subtract(gap.target.getX(), gap.target.getY(), gap.target.getZ());
				g.burst.set((float) bc.x, (float) bc.y, (float) bc.z, (float) Math.max(h, 4.0));
				float collapse = (float) MathHelper.clamp((t - GapTimeline.COLLAPSE) / (GapTimeline.ERASURE - GapTimeline.COLLAPSE), 0.0, 1.0);
				float strength = (float) (0.9 + 2.4 * Math.exp(-e / 6.0)) * (1.0F - 0.7F * collapse);
				// Flickering with the universe raging inside it.
				strength *= (float) (1.0 + 0.18 * Math.sin(t * 2.3) + 0.1 * Math.sin(t * 5.1 + 1.0));
				g.burstLight.set(0.62F, 0.42F, 1.0F).mul(strength);
				if (t < GapTimeline.COLLAPSE) {
					g.shock = (float) shock(gap, e);
				}
			}
			// The other universe's sky stays over ours until the black has it too.
			g.skyMix = (float) (0.85 * GapCamera.ease((t - GapTimeline.CONTACT - 10.0) / 50.0));
			if (t >= GapTimeline.NOTHING - 10) {
				// Black once the black has it all. Lifted as the rebuild begins: from then on the front alone holds the dark, so
				// the tree and the world coming back are seen.
				double black = MathHelper.clamp((t - GapTimeline.NOTHING + 10) / 10.0, 0.0, 1.0) * (r < 0.0 ? 1.0 : 1.0 - GapCamera.ease(r / 20.0));
				g.black = Math.max(g.black, (float) black);
			}
			if (r >= 0.0) {
				// And it goes as ours comes back, all of it gone before the rebuild is done.
				double front = rebuildFront(gap, r);
				g.skyMix *= (float) (front < 0.0 ? 0.0 : 1.0 - GapCamera.ease(front / (0.87 * rebuildReach(gap))));
			}
		}
		if (mine != null) {
			double t = mine.time(tickDelta);
			// The click and the clunk of the key turning, each a jolt of bad signal.
			if (t >= 28 && t < GapTimeline.RISE + 4) {
				g.glitch = (float) (0.45 * Math.exp(-Math.max(0.0, t - 30.0) / 2.0) * (t >= 30 ? 1 : 0)
						+ 0.6 * Math.exp(-Math.max(0.0, t - 37.0) / 2.5) * (t >= 37 ? 1 : 0));
				g.seed = (float) Math.floor(t * 1.5);
				on = true;
			}
			// Up into the clouds before the feed, and out of its whiteout after it.
			if (mine.skippedAt >= 0 && t < GapTimeline.INBOUND) {
				// Skipped: a quick fade back into the shooter's own eyes from wherever the camera was.
				double since = t - mine.skippedAt;
				if (since < 6.0) {
					g.flash = (float) (1.0 - GapCamera.ease(since / 6.0));
					on = true;
				}
			} else if (t >= GapTimeline.FEED - 8 && t < GapTimeline.FEED && !mine.feedSkipped) {
				g.flash = (float) Math.pow((t - GapTimeline.FEED + 8) / 8.0, 2.0);
				on = true;
			} else if (t >= GapTimeline.INBOUND && t < GapTimeline.INBOUND + 16 && !mine.feedSkipped) {
				// Out of the cloud deck's white into the sky over the target, slowly enough to see it clear.
				g.flash = (float) (1.0 - GapCamera.ease((t - GapTimeline.INBOUND) / 16.0));
				on = true;
			}
			// It falls in on itself: a last flash as the black opens.
			if (t >= GapTimeline.ERASURE - 2 && t < GapTimeline.ERASURE + 6) {
				g.flash = Math.max(g.flash, (float) (0.8 * Math.exp(-Math.abs(t - GapTimeline.ERASURE) / 1.8)));
				on = true;
			}
		}
		return on ? g : null;
	}

	/** Draws a player again at full brightness, over whatever the pass did to the frame. */
	private static void redrawPlayer(MinecraftClient client, AbstractClientPlayerEntity player, Vec3d cam, Matrix4f view, float tickDelta) {
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
