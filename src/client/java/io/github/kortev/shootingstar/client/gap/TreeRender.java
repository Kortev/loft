package io.github.kortev.shootingstar.client.gap;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gfx.Mesh;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import io.github.kortev.shootingstar.gap.GapTimeline;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Yggdrasil in the void, for the rebuild: not a tree so much as what one means here, the axis the nine worlds hang
 * on, all of it light (tools/gen_yggdrasil.py, drawn by ss_tree the way the galaxies are). It grows up out of the dark
 * where the shooter is looking, its long root running out over the floor of the void to their feet; then light
 * gathers at its foot and goes out through the whole of it, down that root to the shooter, and the world is put back
 * from there. Drawn into a picture of its own, with a far plane of its own, which the grade pass adds over the void.
 */
final class TreeRender {
	private static final Target TARGET = new Target(false, true);
	/** The floor of the void, in the tree's own units below the foot of its axis: where its roots run. */
	private static final double FLOOR = 0.15;
	private static Mesh tree;

	private TreeRender() {
	}

	/** Where the foot of its axis stands (world): so that its roots run over the ground the shooter stands on. */
	static Vec3d foot(ClientGap gap) {
		return gap.odinFeet.add(0.0, 60.0 - 1.5 + FLOOR * GapTimeline.ODIN_HEIGHT, 0.0);
	}

	/** How much it is there: all through the rebuild, going as the world comes back. */
	static double there(double r) {
		return GapCamera.ease((r - GapTimeline.REBUILD_ODIN + 20.0) / 40.0) * (1.0 - GapCamera.ease((r - GapTimeline.REBUILD_DONE + 20.0) / 90.0));
	}

	/** How far it has grown: from the roots up, through the time the shooter has to look at it. */
	static double grown(double r) {
		return GapCamera.ease((r - GapTimeline.REBUILD_ODIN + 20.0) / (GapTimeline.REBUILD_ARM - GapTimeline.REBUILD_ODIN + 40.0));
	}

	/** Where the wave of light sent through it is, in its units along it from the foot: out to the shooter at the sweep. */
	static double wave(double r) {
		double gather = GapTimeline.REBUILD_SWEEP - 26.0;
		return r < gather ? -1.0 : (r - gather) / 26.0 * (GapTimeline.ODIN_DISTANCE / GapTimeline.ODIN_HEIGHT);
	}

	/** Draws it into its own picture; true if there was anything to draw. */
	static boolean draw(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, int w, int h) {
		if (gap.rebuildAt < 0 || gap.odinFeet == null || gap.odinFacing == null) {
			return false;
		}
		double r = t - gap.rebuildAt;
		float there = (float) there(r);
		if (there <= 0.0F) {
			return false;
		}
		if (tree == null) {
			tree = Mesh.tree();
		}
		// The same lens as the world's, but seeing as far as it is.
		Matrix4f lens = new Matrix4f(proj);
		float near = 2.0F;
		float far = 8000.0F;
		lens.m22(-(far + near) / (far - near));
		lens.m32(-2.0F * far * near / (far - near));
		// Its long root runs along its +Z: turned to run to the shooter.
		Vec3d toward = gap.odinFacing.multiply(-1.0);
		float yaw = (float) Math.atan2(toward.x, toward.z);
		Vector3f foot = GapRender.rel(foot(gap), cam);
		Matrix4f model = new Matrix4f().translation(foot).rotateY(yaw).scale((float) GapTimeline.ODIN_HEIGHT);

		TARGET.begin(w, h, 0.0F, 0.0F, 0.0F, 0.0F);
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ZERO,
				GlStateManager.DstFactor.ONE);
		Shaders.set(Shaders.tree, "ScreenSize", (float) w, (float) h);
		Shaders.set(Shaders.tree, "Grow", (float) grown(r));
		Shaders.set(Shaders.tree, "Time", (float) t);
		Shaders.set(Shaders.tree, "Wave", (float) wave(r));
		// Brighter as the light gathers in it for the sweep.
		float gather = (float) GapCamera.ease((r - GapTimeline.REBUILD_ARM) / (GapTimeline.REBUILD_SWEEP - GapTimeline.REBUILD_ARM));
		float after = (float) Math.exp(-Math.max(0.0, r - GapTimeline.REBUILD_SWEEP) / 40.0);
		Shaders.set(Shaders.tree, "Bright", there * (1.0F + 0.8F * gather * (r < GapTimeline.REBUILD_SWEEP ? 1.0F : after)));
		tree.draw(Shaders.tree, new Matrix4f(view).mul(model), lens);
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		return true;
	}

	static int color() {
		return TARGET.color();
	}
}
