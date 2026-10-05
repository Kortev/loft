package io.github.kortev.shootingstar.client.gap;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gfx.Fx;
import io.github.kortev.shootingstar.client.gfx.Mesh;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import io.github.kortev.shootingstar.gap.GapTimeline;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Odin in the void, for the rebuild: blocky, built to Minecraft's grid of pixels like a mob (see tools/models.py),
 * a giant out where the shooter is looking, rising out of the dark with his ravens round him. Drawn into a picture of
 * his own, with a far plane of his own (he stands past where the world's ends), which the grade pass lays over the
 * void and the sky.
 */
final class OdinRender {
	private static final Target TARGET = new Target(true, true);
	private static final Fx BATCH = new Fx();
	/** The model's height in its own pixels, to the crown of the hat. */
	private static final float PIXELS = 40.0F;
	/** His right shoulder, in the model's pixels. */
	private static final Vector3f SHOULDER = new Vector3f(-6.0F, 23.0F, 0.0F);
	private static final Vector3f EYE = new Vector3f(2.0F, 28.5F, 4.4F);
	private static final Vector3f TIP = new Vector3f(6.0F, 47.5F, 4.0F);
	private static Mesh body;
	private static Mesh arm;
	private static Mesh raven;

	private OdinRender() {
	}

	/** Where he stands now (feet, world), {@code r} ticks into the rebuild: risen out of the void below. */
	static Vec3d feet(ClientGap gap, double r) {
		double rise = GapCamera.ease((r - GapTimeline.REBUILD_ODIN) / 90.0);
		return gap.odinFeet.add(0.0, -GapTimeline.ODIN_HEIGHT * 0.6 * (1.0 - rise), 0.0);
	}

	/** How much he is there: coming out of the dark, going as the world comes back. */
	static double there(double r) {
		return GapCamera.ease((r - GapTimeline.REBUILD_ODIN) / 60.0) * (1.0 - GapCamera.ease((r - GapTimeline.REBUILD_DONE + 20.0) / 80.0));
	}

	/** The right arm, in radians forward from hanging: up over his head, swept down to point at the shooter, lowered. */
	private static float armAngle(double r) {
		double up = GapCamera.ease((r - GapTimeline.REBUILD_ARM) / (GapTimeline.REBUILD_SWEEP - GapTimeline.REBUILD_ARM));
		double sweep = GapCamera.ease((r - GapTimeline.REBUILD_SWEEP) / 9.0);
		double lower = GapCamera.ease((r - GapTimeline.REBUILD_SWEEP - 140.0) / 80.0);
		double degrees = r < GapTimeline.REBUILD_SWEEP ? 165.0 * up : MathHelper.lerp(lower, MathHelper.lerp(sweep, 165.0, 72.0), 8.0);
		return (float) Math.toRadians(degrees);
	}

	/** Draws him into his own picture; true if there was anything to draw. */
	static boolean draw(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, int w, int h, Vector3f right, Vector3f up) {
		if (gap.rebuildAt < 0 || gap.odinFeet == null || gap.odinFacing == null) {
			return false;
		}
		double r = t - gap.rebuildAt;
		float there = (float) there(r);
		if (there <= 0.0F) {
			return false;
		}
		if (body == null) {
			body = Mesh.load("odin");
			arm = Mesh.load("odin_arm");
			raven = Mesh.load("raven");
		}
		// The same lens as the world's, but seeing as far as he is.
		Matrix4f lens = new Matrix4f(proj);
		float near = 2.0F;
		float far = 6000.0F;
		lens.m22(-(far + near) / (far - near));
		lens.m32(-2.0F * far * near / (far - near));

		Vec3d toward = gap.odinFacing.multiply(-1.0);
		float yaw = (float) Math.atan2(toward.x, toward.z);
		float scale = (float) (GapTimeline.ODIN_HEIGHT / PIXELS);
		Vector3f feet = GapRender.rel(feet(gap, r), cam);
		Matrix4f model = new Matrix4f().translation(feet).rotateY(yaw).scale(scale);

		TARGET.begin(w, h, 0.0F, 0.0F, 0.0F, 0.0F);
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		RenderSystem.disableCull();

		// Lit cold from above and in front, rimmed in violet from the void behind him; the stars in his cloak twinkle.
		Vector3f key = view.transformDirection(new Vector3f((float) toward.x * 0.7F, 1.0F, (float) toward.z * 0.7F).normalize());
		Vector3f fill = view.transformDirection(new Vector3f((float) -toward.x, -0.3F, (float) -toward.z).normalize());
		Shaders.set(Shaders.mesh, "LightDir", key);
		Shaders.set(Shaders.mesh, "LightColor", 0.75F * there, 0.8F * there, 0.95F * there);
		Shaders.set(Shaders.mesh, "AmbientColor", 0.05F * there, 0.05F * there, 0.08F * there);
		Shaders.set(Shaders.mesh, "RimColor", 0.55F * there, 0.35F * there, 1.0F * there);
		Shaders.set(Shaders.mesh, "FillDir", fill);
		Shaders.set(Shaders.mesh, "FillColor", 0.3F * there, 0.18F * there, 0.6F * there);
		Shaders.set(Shaders.mesh, "PointPos", 0.0F, 0.0F, 0.0F);
		Shaders.set(Shaders.mesh, "PointColor", 0.0F, 0.0F, 0.0F);
		Shaders.set(Shaders.mesh, "GlowColor", 1.0F, 0.85F, 0.55F);
		Shaders.set(Shaders.mesh, "Heat", 0.0F);
		Shaders.set(Shaders.mesh, "Fade", 1.0F);
		Shaders.set(Shaders.mesh, "Sweep", 2.0F);
		Shaders.set(Shaders.mesh, "Phase", (float) (t * 0.05));

		float eye = (float) GapCamera.ease((r - GapTimeline.REBUILD_EYE) / 12.0);
		Shaders.set(Shaders.mesh, "GlowStrength", (0.3F + 2.8F * eye) * there);
		body.draw(Shaders.mesh, new Matrix4f(view).mul(model), lens);

		float swing = armAngle(r);
		float hand = (float) (r < GapTimeline.REBUILD_SWEEP ? 0.6 * GapCamera.ease((r - GapTimeline.REBUILD_ARM) / 60.0)
				: 0.6 + 2.4 * Math.exp(-(r - GapTimeline.REBUILD_SWEEP) / 30.0));
		Matrix4f armModel = new Matrix4f(model).translate(SHOULDER).rotateX(-swing);
		Shaders.set(Shaders.mesh, "GlowStrength", (0.3F + 3.0F * hand) * there);
		arm.draw(Shaders.mesh, new Matrix4f(view).mul(armModel), lens);

		// Huginn and Muninn, wheeling round his head; out wide over the world as it comes back.
		Shaders.set(Shaders.mesh, "GlowStrength", 2.0F * there);
		float out = (float) GapCamera.ease((r - GapTimeline.REBUILD_SWEEP) / 120.0);
		for (int i = 0; i < 2; i++) {
			double a = r * 0.018 + i * Math.PI + i * 0.6;
			float radius = 17.0F + 30.0F * out + 4.0F * i;
			float height = 34.0F + 3.0F * (float) Math.sin(r * 0.05 + i * 2.0) - 8.0F * out + 5.0F * i;
			Matrix4f bird = new Matrix4f(model).translate((float) Math.cos(a) * radius, height, (float) Math.sin(a) * radius)
					// Flying round the circle: facing along it, banked into the turn.
					.rotateY((float) (-a)).rotateZ(-0.35F).scale(0.9F);
			raven.draw(Shaders.mesh, new Matrix4f(view).mul(bird), lens);
		}
		Shaders.set(Shaders.mesh, "Phase", 0.0F);

		// His eye, the spear's point and the hand that remakes it all flare; a faint light behind his head.
		BATCH.begin(Fx.BLOB, 0.0F, view, lens, right, up);
		Vector3f eyeAt = model.transformPosition(new Vector3f(EYE));
		BATCH.sprite(eyeAt, scale * 6.0F * eye, 0.0F, Fx.argb(1.0F, 0.85F, 0.5F, 0.9F * eye * there));
		BATCH.sprite(model.transformPosition(new Vector3f(TIP)), scale * 9.0F, 0.0F, Fx.argb(0.8F, 0.75F, 1.0F, 0.7F * there));
		Vector3f handAt = armModel.transformPosition(new Vector3f(0.0F, -12.5F, 0.0F));
		BATCH.sprite(handAt, scale * (5.0F + 10.0F * hand), 0.0F, Fx.argb(1.0F, 0.9F, 0.7F, Math.min(1.0F, 0.5F * hand) * there));
		BATCH.sprite(model.transformPosition(new Vector3f(0.0F, 30.0F, -6.0F)), scale * 34.0F, 0.0F, Fx.argb(0.5F, 0.35F, 1.0F, 0.18F * there));
		BATCH.end(true, 2.0F);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.depthMask(true);
		return true;
	}

	static int color() {
		return TARGET.color();
	}
}
