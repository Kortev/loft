package io.github.kortev.shootingstar.test;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * The Zombie Devil, for the first reel (episode 1), as the anime has it: an undead upper torso, its arms gone at the
 * shoulders, its ribcage open at the bottom and its guts hanging out of it like tentacles, swaying (what it moves
 * with); an oversized pink brain bulging out of its neck where its head should be; and a zombie's head growing out of
 * the right side of its chest. Boxes and pixels like Pochita, painted by {@code tools/reel_textures.py} for exactly
 * these boxes. It floats. Only the reel draws it ({@link ReelSelfTest}). Pixels: y down from the base of its neck, its
 * front toward -z.
 */
final class ReelZombieDevil {
	static final Identifier TEXTURE = Identifier.of("shootingstar-gametest", "textures/entity/zombie_devil.png");
	/** How many strands of guts, and how many pieces to each. */
	private static final int STRANDS = 7;
	private static final int PIECES = 3;
	/** How far below its neck its lowest gut reaches, and how far above its brain does (pixels). */
	static final float BELOW = 38.5F;
	static final float ABOVE = 17.0F;

	private static ModelPart root;
	private static ModelPart[][] guts;

	private ReelZombieDevil() {
	}

	private static ModelPart get() {
		if (root == null) {
			root = data().createModel();
			// The head out of its chest, big as a man's.
			ModelPart head = root.getChild("chest_head");
			head.xScale = 1.35F;
			head.yScale = 1.35F;
			head.zScale = 1.35F;
			guts = new ModelPart[STRANDS][PIECES];
			for (int i = 0; i < STRANDS; i++) {
				ModelPart piece = root.getChild("gut" + i);
				for (int j = 0; j < PIECES; j++) {
					guts[i][j] = piece;
					if (j + 1 < PIECES) {
						piece = piece.getChild("gut" + i + "_" + (j + 1));
					}
				}
			}
		}
		return root;
	}

	private static TexturedModelData data() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("torso", box(0, 0, -9.0F, 0.0F, -6.0F, 18, 16, 12), ModelTransform.NONE);
		root.addChild("ribs", box(0, 28, -8.0F, 16.0F, -5.0F, 16, 5, 10), ModelTransform.NONE);
		// The arms gone: raw stumps at its shoulders.
		root.addChild("stump_right", box(0, 43, -12.0F, 1.0F, -4.0F, 3, 6, 8), ModelTransform.NONE);
		root.addChild("stump_left", box(0, 43, 9.0F, 1.0F, -4.0F, 3, 6, 8), ModelTransform.NONE);
		root.addChild("neck", box(104, 26, -3.0F, -3.0F, -3.0F, 6, 3, 6), ModelTransform.NONE);
		// The brain where its head should be, bulging up out of its neck.
		root.addChild("brain", box(60, 0, -8.0F, -15.0F, -7.0F, 16, 12, 14), ModelTransform.NONE);
		root.addChild("brain_top", box(60, 26, -6.0F, -17.0F, -5.0F, 12, 2, 10), ModelTransform.NONE);
		// Its lobes bulging out over its sides, front and back, and down over its neck: not a box but a lump.
		root.addChild("lobe_right", box(0, 64, -10.0F, -13.0F, -5.0F, 2, 8, 10), ModelTransform.NONE);
		root.addChild("lobe_left", box(0, 64, 8.0F, -13.0F, -5.0F, 2, 8, 10), ModelTransform.NONE);
		root.addChild("lobe_front", box(24, 64, -6.0F, -13.0F, -9.0F, 12, 7, 2), ModelTransform.NONE);
		root.addChild("lobe_back", box(24, 64, -6.0F, -14.0F, 7.0F, 12, 9, 2), ModelTransform.NONE);
		root.addChild("lobe_under", box(52, 64, -5.0F, -4.0F, -5.0F, 10, 2, 10), ModelTransform.NONE);
		// A zombie's head out of the right side of its chest (its right: -x), turned a little, its face out front.
		root.addChild("chest_head", box(22, 43, -4.0F, -4.0F, -6.0F, 8, 8, 6), ModelTransform.of(-5.0F, 6.5F, -7.5F, 0.15F, 0.3F, -0.35F));
		// The guts: strands of pieces hanging out of the open ribs, each piece hinged on the one above.
		float[][] at = {{-6.0F, -3.0F}, {-2.0F, -4.0F}, {3.0F, -3.0F}, {6.0F, -1.0F}, {-4.0F, 2.0F}, {1.0F, 3.0F}, {5.0F, 3.0F}};
		int[] u = {50, 58, 66};
		for (int i = 0; i < STRANDS; i++) {
			ModelPartData piece = root.addChild("gut" + i, box(u[i % 3], 43, -1.0F, 0.0F, -1.0F, 2, 6, 2),
					ModelTransform.pivot(at[i][0], 20.5F, at[i][1]));
			for (int j = 1; j < PIECES; j++) {
				piece = piece.addChild("gut" + i + "_" + j, box(u[(i + j) % 3], 43, -1.0F, 0.0F, -1.0F, 2, 6, 2), ModelTransform.pivot(0.0F, 5.6F, 0.0F));
			}
		}
		return TexturedModelData.of(data, 128, 128);
	}

	private static ModelPartBuilder box(int u, int v, float x, float y, float z, int w, int h, int d) {
		return ModelPartBuilder.create().uv(u, v).cuboid(x, y, z, w, h, d);
	}

	/**
	 * Draws it with the base of its neck at {@code at}, facing {@code yaw}, {@code scale} times its pixels' size, at
	 * {@code t} ticks (its guts sway with it, it bobs as it floats), lit by {@code light}.
	 */
	static void render(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d at, Vec3d cam, float yaw, float scale, float t, int light) {
		ModelPart model = get();
		for (int i = 0; i < STRANDS; i++) {
			for (int j = 0; j < PIECES; j++) {
				guts[i][j].pitch = MathHelper.sin(t * 0.11F + i * 1.3F + j * 0.8F) * (0.16F + 0.1F * j);
				guts[i][j].roll = MathHelper.cos(t * 0.09F + i * 2.1F + j * 0.5F) * (0.12F + 0.08F * j);
			}
		}
		matrices.push();
		matrices.translate(at.x - cam.x, at.y - cam.y + MathHelper.sin(t * 0.07F) * 0.06, at.z - cam.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
		// Swaying a little as it hangs there.
		matrices.multiply(RotationAxis.POSITIVE_Z.rotation(MathHelper.sin(t * 0.05F) * 0.04F));
		matrices.scale(-scale, -scale, scale);
		model.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
	}
}
