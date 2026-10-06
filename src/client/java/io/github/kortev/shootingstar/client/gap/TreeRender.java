package io.github.kortev.shootingstar.client.gap;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gfx.Mesh;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import io.github.kortev.shootingstar.gap.GapTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Yggdrasil in the void, for the rebuild: not a tree so much as what one means here, the axis the nine worlds hang
 * on, all of it light (tools/gen_yggdrasil.py, drawn by ss_tree the way the galaxies are). It grows up out of the hole
 * the block left, its roots running out over where the ground was and its long root out to the shooter's feet; then
 * light gathers at its foot and goes out through the whole of it, and the world is put back out from its roots. Drawn into a picture of its own, with a far plane of its own, which the grade pass adds over the void.
 */
final class TreeRender {
	private static final Target TARGET = new Target(false, true);
	/** The floor of the void, in the tree's own units below the foot of its axis: where its roots run. */
	private static final double FLOOR = 0.15;
	private static Mesh tree;
	private static boolean low;

	private TreeRender() {
	}

	/** How tall it stands: out of all proportion to the hole it grows from. */
	static double height(ClientGap gap) {
		return Math.max(120.0, Math.min(420.0, gap.radius * 2.6));
	}

	/**
	 * How wide it is drawn, one of its units across: slender enough that the whole of its crown stands inside the hole,
	 * so the shooter, set down at the rim, sees all of it rather than standing in among its limbs.
	 */
	static double width(ClientGap gap) {
		return Math.min(height(gap), 0.8 * gap.radius / 0.62);
	}

	/** Where the foot of its axis stands (world): in the middle of the hole, its roots running out at the old ground's level. */
	static Vec3d foot(ClientGap gap) {
		return gap.contact.add(0.0, 1.0 + FLOOR * height(gap), 0.0);
	}

	/** The way out from the middle of the hole to where the shooter stands. */
	static Vec3d toward(ClientGap gap) {
		Vec3d d = new Vec3d(gap.rebuildFrom.x - gap.contact.x, 0.0, gap.rebuildFrom.z - gap.contact.z);
		return d.lengthSquared() < 1.0 ? new Vec3d(1.0, 0.0, 0.0) : d.normalize();
	}

	/** How far its long root has to run to reach the shooter, in its own units. */
	static double reach(ClientGap gap) {
		return Math.max(0.2, Math.hypot(gap.rebuildFrom.x - gap.contact.x, gap.rebuildFrom.z - gap.contact.z) / width(gap));
	}

	/** How much it is there: all through the rebuild, going as the world comes back. */
	static double there(double r) {
		return GapCamera.ease((r - GapTimeline.REBUILD_TREE + 20.0) / 40.0) * (1.0 - GapCamera.ease((r - GapTimeline.REBUILD_DONE + 10.0) / 100.0));
	}

	/**
	 * How far it has grown: from the roots up, through the time the shooter has to look at it; and at the end, as the
	 * world is all back, drawing back in the way it came, down into the hole.
	 */
	static double grown(double r) {
		double up = GapCamera.ease((r - GapTimeline.REBUILD_TREE + 20.0) / (GapTimeline.REBUILD_GATHER - GapTimeline.REBUILD_TREE + 40.0));
		return up * (1.0 - 0.9 * GapCamera.ease((r - GapTimeline.REBUILD_DONE + 40.0) / 100.0));
	}

	/** Where the wave of light sent through it is, in its units along it from the foot: out to the shooter at the sweep. */
	static double wave(ClientGap gap, double r) {
		double gather = GapTimeline.REBUILD_SWEEP - 26.0;
		return r < gather ? -1.0 : (r - gather) / 26.0 * reach(gap);
	}

	/** Draws it into its own picture for the shooter's rebuild; true if there was anything to draw. */
	static boolean draw(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, int w, int h) {
		if (gap.rebuildAt < 0 || gap.rebuildFrom == null) {
			return false;
		}
		double r = gap.rebuild((float) (t - gap.age));
		if (there(r) <= 0.0) {
			return false;
		}
		// The same lens as the world's, but seeing as far as it is.
		Matrix4f lens = new Matrix4f(proj);
		float near = 2.0F;
		float far = 8000.0F;
		lens.m22(-(far + near) / (far - near));
		lens.m32(-2.0F * far * near / (far - near));
		TARGET.begin(w, h, 0.0F, 0.0F, 0.0F, 0.0F);
		RenderSystem.disableDepthTest();
		render(gap, r, t, cam, view, lens, w, h);
		return true;
	}

	/** How long someone else's tree stands, seen from outside: the shooter's rebuild, played faster. */
	static final int SPECTATED = 390;
	private static final double SPECTATED_PACE = 1.6;

	/**
	 * Someone else's: seen from outside, in among the world, growing out of the hole their Ginnungagap left and gone
	 * again, its long root running out to whoever is watching. Drawn straight into the world, behind what stands in front.
	 */
	static void drawSpectated(ClientGap gap, double t, Vec3d cam, Matrix4f view, Matrix4f proj, int w, int h) {
		if (gap.spectateAt < 0 || gap.rebuildFrom == null) {
			return;
		}
		double r = (t - gap.spectateAt) * SPECTATED_PACE;
		if (there(r) <= 0.0) {
			return;
		}
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		render(gap, r, t, cam, view, proj, w, h);
	}

	private static void render(ClientGap gap, double r, double t, Vec3d cam, Matrix4f view, Matrix4f lens, int w, int h) {
		if (tree == null) {
			// Half the points on Fast graphics: the filaments go to dotted lines, but it is far lighter to draw.
			low = MinecraftClient.getInstance().options.getGraphicsMode().getValue() == GraphicsMode.FAST;
			tree = Mesh.tree(low ? 2 : 1);
		}
		float there = (float) there(r);
		// Its long root runs along its +Z: turned to run to the shooter.
		Vec3d toward = toward(gap);
		float yaw = (float) Math.atan2(toward.x, toward.z);
		Vector3f foot = GapRender.rel(foot(gap), cam);
		Matrix4f model = new Matrix4f().translation(foot).rotateY(yaw).scale((float) width(gap), (float) height(gap), (float) width(gap));
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ZERO,
				GlStateManager.DstFactor.ONE);
		Shaders.set(Shaders.tree, "ScreenSize", (float) w, (float) h);
		Shaders.set(Shaders.tree, "Grow", (float) grown(r));
		Shaders.set(Shaders.tree, "Time", (float) t);
		Shaders.set(Shaders.tree, "Wave", (float) wave(gap, r));
		Shaders.set(Shaders.tree, "Reach", (float) reach(gap));
		// Brighter as the light gathers in it for the sweep.
		float gather = (float) GapCamera.ease((r - GapTimeline.REBUILD_GATHER) / (GapTimeline.REBUILD_SWEEP - GapTimeline.REBUILD_GATHER));
		float after = (float) Math.exp(-Math.max(0.0, r - GapTimeline.REBUILD_SWEEP) / 40.0);
		float bright = there * (1.0F + 0.8F * gather * (r < GapTimeline.REBUILD_SWEEP ? 1.0F : after));
		// The game's grade is darker than the light it was drawn to look right under: brought up to match.
		Shaders.set(Shaders.tree, "Bright", bright * 1.8F * (low ? 1.5F : 1.0F));
		tree.draw(Shaders.tree, new Matrix4f(view).mul(model), lens);
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
	}

	static int color() {
		return TARGET.color();
	}
}
