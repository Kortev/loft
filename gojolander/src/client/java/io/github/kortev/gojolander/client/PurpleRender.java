package io.github.kortev.gojolander.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.gojolander.purple.PurpleTimeline;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import io.github.kortev.shootingstar.client.gfx.Timings;
import io.github.kortev.shootingstar.client.gfx.WorldFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Draws Hollow Purple over the world in one full-screen pass each ({@code gl_purple}): Blue and Red at his hands, the
 * shell of light as they meet, the violet sphere and the streak behind it as it flies, and its collapse in light. The
 * pass is told where each body is (relative to the camera), how big and how strong, and does the rest: the light it
 * casts on the world, the picture bent round its edge, the depth kept so a hill in front hides it.
 */
public final class PurpleRender {
	private static final Target DEPTH = new Target(true, false);
	private static final Target COPY = new Target(false, false);
	/** How far off one is drawn at all. */
	private static final double DRAWN = 600.0;
	/** The orbs' radius at full, in blocks. */
	private static final double ORB = 0.5;

	public static ShaderProgram purple;

	private PurpleRender() {
	}

	/** Over the finished world (shootingstar's WorldPasses, as the Domain's pass). */
	public static void render(WorldFrame frame) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (purple == null || ClientPurples.all().isEmpty() || world == null || client.player == null) {
			return;
		}
		Camera camera = frame.camera();
		float tickDelta = frame.tickDelta();
		Vec3d cam = camera.getPos();
		Matrix4f view = new Matrix4f(frame.positionMatrix());
		Matrix4f proj = new Matrix4f(frame.projectionMatrix());
		Matrix4f viewProj = new Matrix4f(proj).mul(view);
		Framebuffer main = client.getFramebuffer();
		int w = main.textureWidth;
		int h = main.textureHeight;
		float time = (float) (world.getTime() % 24000L) / 20.0F + tickDelta / 20.0F;
		float zoom = proj.m11();
		for (ClientPurple p : ClientPurples.all()) {
			double t = p.time(tickDelta);
			if (p.cancelled || t >= p.end() || cam.distanceTo(p.eye) > DRAWN + p.range) {
				continue;
			}
			PurplePose.Anchor at = PurplePose.anchor(client, p, tickDelta);
			Vec3d merge = PurplePose.meet(p, at);
			double blue = PurpleTimeline.grow(t, PurpleTimeline.BLUE, 14.0) * (1.0 - PurpleTimeline.grow(t, PurpleTimeline.MERGE, 2.0));
			double red = PurpleTimeline.grow(t, PurpleTimeline.RED, 12.0) * (1.0 - PurpleTimeline.grow(t, PurpleTimeline.MERGE, 2.0));
			double swell = t >= PurpleTimeline.MERGE ? Math.min(1.0, (t - PurpleTimeline.MERGE) / 3.0) : 0.0;
			double collapse = p.collapse(t);
			double sphereAmt = swell * (1.0 - collapse * 0.6);
			double sphereR = p.sphereRadius(t);
			Vec3d sphere = p.sphereAt(t, merge);
			double pulse = 1.0 + 0.07 * Math.sin(t * 2.3);
			double orbR = ORB * (0.55 + 0.45 * pulse);
			Vec3d bluePos = PurplePose.hand(p, true, t, at);
			Vec3d redPos = PurplePose.hand(p, false, t, at);
			double sinceMerge = t >= PurpleTimeline.MERGE ? (t - PurpleTimeline.MERGE) / 20.0 : -1.0;
			if (blue <= 0.0 && red <= 0.0 && sphereAmt <= 0.0 && sinceMerge < 0.0) {
				continue;
			}
			double flying = t >= PurpleTimeline.RELEASE ? Math.min(1.0, (t - PurpleTimeline.RELEASE) / 3.0) * (1.0 - collapse) : 0.0;

			Timings.begin("purple.pass");
			DEPTH.ensure(w, h);
			DEPTH.copyDepthFrom(main);
			COPY.ensure(w, h);
			COPY.copyColorFrom(main);
			main.beginWrite(true);
			Post.begin();
			RenderSystem.enableDepthTest();
			RenderSystem.depthFunc(GL11.GL_ALWAYS);
			RenderSystem.depthMask(true);
			RenderSystem.setShaderTexture(0, COPY.color());
			RenderSystem.setShaderTexture(1, DEPTH.depth());
			Shaders.set(purple, "InvViewProj", new Matrix4f(viewProj).invert());
			Shaders.set(purple, "ScreenSize", (float) w, (float) h);
			Shaders.set(purple, "Time", time);
			body(purple, "Sphere", "SphereUv", sphere.subtract(cam), sphereR, viewProj, zoom);
			body(purple, "Blue", "BlueUv", bluePos.subtract(cam), orbR * (0.3 + 0.7 * blue), viewProj, zoom);
			body(purple, "Red", "RedUv", redPos.subtract(cam), orbR * (0.3 + 0.7 * red), viewProj, zoom);
			Shaders.set(purple, "Amt", (float) blue, (float) red, (float) sphereAmt, (float) collapse);
			Vec3d m = merge.subtract(cam);
			Shaders.set(purple, "Merge", (float) m.x, (float) m.y, (float) m.z, (float) (sinceMerge < 1.6 ? sinceMerge : -1.0));
			Shaders.set(purple, "Fly", (float) p.dir.x, (float) p.dir.y, (float) p.dir.z, (float) flying);
			Post.quad(purple);
			Timings.end();
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
			RenderSystem.setShaderTexture(0, 0);
			RenderSystem.setShaderTexture(1, 0);
		}
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		main.beginWrite(true);
	}

	/** A body's place (relative to the camera), radius, and where and how big it is on the screen. */
	private static void body(ShaderProgram program, String name, String uvName, Vec3d rel, double radius, Matrix4f viewProj, float zoom) {
		Shaders.set(program, name, (float) rel.x, (float) rel.y, (float) rel.z, (float) Math.max(radius, 1.0E-3));
		Vector4f clip = viewProj.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F));
		if (clip.w <= 0.01F || radius <= 1.0E-3) {
			Shaders.set(program, uvName, 0.0F, 0.0F, 0.0F, 0.0F);
			return;
		}
		float u = clip.x / clip.w * 0.5F + 0.5F;
		float v = clip.y / clip.w * 0.5F + 0.5F;
		float size = (float) (radius * zoom / clip.w * 0.5);
		Shaders.set(program, uvName, u, v, MathHelper.clamp(size, 0.0F, 4.0F), 1.0F);
	}
}
