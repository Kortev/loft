package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.List;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

/**
 * The chainsaw head, in the game's own way (as Pochita is): boxes, painted pixel by pixel
 * ({@code tools/gen_chainsaw_textures.py}, {@code form_head}, lays the texture out for exactly these boxes; change one,
 * change the other). The orange visor over the front, its top bevelled in from the sides and sloping down step by step
 * to the back, the ridge running down the slope; its prow narrowing toward the mouth; the eye's vents round the back
 * of its sides; under it an open mouth of long teeth, the orange jaw stepping down to a point; the black engine behind,
 * its top edge bevelled, a socket on each side, fins across its back, the D handle and the pole; the neck bound in
 * cords. The saw stays the mesh's. kortev chose these over the mesh's head (2026-10-20), a little less boxy than first
 * proposed: the slope and the bevels. {@link #on} is off only to compare with the mesh's ({@code /chainsawhead mesh}).
 * Pixels in the head bone's space: y down from the neck, the face toward -z.
 */
public final class FormHeadModel {
	public static final Identifier TEXTURE = ChainsawMan.id("textures/entity/form_head.png");
	/** Its eye's lit pixels, drawn over it as a spider's eyes are. */
	public static final Identifier GLOW = ChainsawMan.id("textures/entity/form_head_glow.png");
	/** Where the eye's vents are on each side (pixels): where the flare at the roar goes. */
	public static final List<Vector3f> EYES = List.of(new Vector3f(-5.05F, -7.3F, -0.2F), new Vector3f(5.05F, -7.3F, -0.2F));

	/** How far up the saw comes out of the boxes than out of the mesh head (pixels): out of the visor's middle. */
	public static final float BAR_LIFT = -1.6F;

	/** Whether the boxes are drawn in place of the mesh head (off only to compare). */
	public static boolean on = true;
	private static ModelPart root;

	private FormHeadModel() {
	}

	public static ModelPart get() {
		if (root == null) {
			root = data().createModel();
		}
		return root;
	}

	private static TexturedModelData data() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		// The engine, its top edge bevelled in.
		root.addChild("engine", box(0, 0, -4.5F, -8.0F, -1.0F, 9, 7, 6), ModelTransform.NONE);
		root.addChild("engine_cap", box(0, 44, -3.5F, -9.0F, -1.0F, 7, 1, 5), ModelTransform.NONE);
		root.addChild("engine_top", box(38, 40, -3.5F, -10.0F, 1.0F, 7, 1, 3), ModelTransform.NONE);
		// The visor, and its top: in from the sides a pixel and stopping short of the back, so the head slopes down from
		// the brow to the engine in steps; the ridge down the middle of it, stepping down with it.
		root.addChild("visor", box(0, 14, -5.0F, -10.0F, -5.0F, 10, 4, 6), ModelTransform.NONE);
		root.addChild("visor_top", box(32, 44, -4.0F, -11.0F, -5.0F, 8, 1, 5), ModelTransform.NONE);
		root.addChild("ridge", box(32, 0, -1.0F, -12.0F, -5.0F, 2, 1, 4), ModelTransform.NONE);
		root.addChild("ridge_tail", box(24, 44, -1.0F, -11.0F, 0.0F, 2, 1, 2), ModelTransform.NONE);
		// The prow, narrowing to the mouth.
		root.addChild("prow", box(50, 0, -3.0F, -10.0F, -6.0F, 6, 3, 1), ModelTransform.NONE);
		root.addChild("prow_tip", box(0, 50, -2.0F, -7.0F, -6.0F, 4, 1, 1), ModelTransform.NONE);
		root.addChild("mouth", box(0, 26, -4.0F, -6.0F, -5.0F, 8, 5, 5), ModelTransform.NONE);
		root.addChild("jaw", box(0, 36, -4.0F, -1.0F, -5.0F, 8, 1, 4), ModelTransform.NONE);
		root.addChild("chin", box(26, 36, -2.0F, 0.0F, -5.0F, 4, 1, 2), ModelTransform.NONE);
		root.addChild("chin_point", box(38, 36, -1.0F, 1.0F, -5.0F, 2, 1, 1), ModelTransform.NONE);
		root.addChild("socket_left", box(32, 14, 4.5F, -8.0F, 1.0F, 1, 5, 4), ModelTransform.NONE);
		root.addChild("socket_right", box(32, 14, -5.5F, -8.0F, 1.0F, 1, 5, 4), ModelTransform.NONE);
		root.addChild("handle_top", box(44, 14, -0.5F, -9.0F, 5.0F, 1, 1, 3), ModelTransform.NONE);
		root.addChild("handle_bottom", box(44, 14, -0.5F, -4.0F, 5.0F, 1, 1, 3), ModelTransform.NONE);
		root.addChild("handle_back", box(52, 14, -0.5F, -9.0F, 8.0F, 1, 6, 1), ModelTransform.NONE);
		root.addChild("pole", box(56, 14, -0.5F, -13.0F, 2.0F, 1, 3, 1), ModelTransform.NONE);
		root.addChild("neck", box(26, 26, -3.0F, -1.0F, -1.0F, 6, 1, 5), ModelTransform.NONE);
		return TexturedModelData.of(data, 64, 64);
	}

	private static ModelPartBuilder box(int u, int v, float x, float y, float z, int w, int h, int d) {
		return ModelPartBuilder.create().uv(u, v).cuboid(x, y, z, w, h, d);
	}
}
