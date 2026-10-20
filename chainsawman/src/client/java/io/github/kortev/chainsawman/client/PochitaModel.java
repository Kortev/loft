package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.PochitaEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * Pochita in Minecraft's own way of making animals: boxes, painted pixel by pixel ({@code tools/gen_chainsaw_textures.py},
 * {@code pochita_entity}, lays the texture out for exactly these boxes and their texture corners; change one, change the
 * other). From kortev's stills: a long bean of a body, stepped at its edges, its head the fuller front end; a little
 * muzzle; four stubby legs; the chainsaw's bar straight out of his forehead, a little up, teeth along it; the big D handle
 * leaning far back off the top of his head; the small handle under his rear; the thin tail curling up to its triangle.
 * Pixels in the model's space: y down, his feet at 24, his front toward -z.
 */
public class PochitaModel extends SinglePartEntityModel<PochitaEntity> {
	private final ModelPart root;
	private final ModelPart tail;
	/** His lower jaw, hinged at its back under his snout: a little open at rest, wide as he talks. */
	private final ModelPart jaw;
	/** How far open his jaw is at rest, and how much further his words open it (radians). */
	private static final float JAW_REST = 0.22F;
	private static final float JAW_TALK = 0.6F;
	private final ModelPart[] legs;

	public PochitaModel(ModelPart root) {
		this.root = root;
		this.tail = root.getChild("tail");
		this.jaw = root.getChild("jaw");
		this.legs = new ModelPart[] {root.getChild("leg0"), root.getChild("leg1"), root.getChild("leg2"), root.getChild("leg3")};
	}

	public static TexturedModelData texturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		// The bean: the head (front), the body behind it, a step on top of each and at the rump to round it off.
		root.addChild("head", box(0, 0, -4.5F, 11.0F, -8.0F, 9, 10, 6), ModelTransform.NONE);
		root.addChild("body", box(0, 16, -4.0F, 12.0F, -2.0F, 8, 9, 9), ModelTransform.NONE);
		root.addChild("head_top", box(30, 0, -3.5F, 10.0F, -7.0F, 7, 1, 4), ModelTransform.NONE);
		root.addChild("body_top", box(30, 5, -3.0F, 11.0F, -1.0F, 6, 1, 6), ModelTransform.NONE);
		root.addChild("rump", box(34, 16, -3.0F, 13.0F, 7.0F, 6, 7, 1), ModelTransform.NONE);
		// The snout, its nose and fang; under it the lower jaw, hinged at its back (inside his mouth, painted dark on the
		// front of his head, shows as it drops).
		root.addChild("snout", box(48, 16, -2.5F, 16.0F, -9.5F, 5, 2, 2), ModelTransform.NONE);
		root.addChild("jaw", box(0, 54, -2.5F, 0.0F, -2.0F, 5, 2, 2), ModelTransform.of(0.0F, 18.0F, -7.5F, JAW_REST, 0.0F, 0.0F));
		// The small handle under his rear, between his back legs.
		root.addChild("under_handle", box(34, 38, -1.0F, 21.0F, 2.0F, 2, 2, 4), ModelTransform.NONE);
		// Stubby legs.
		float[][] at = {{-2.5F, -5.0F}, {2.5F, -5.0F}, {-2.5F, 4.5F}, {2.5F, 4.5F}};
		for (int i = 0; i < 4; i++) {
			root.addChild("leg" + i, box(34, 24, -1.5F, 0.0F, -1.5F, 3, 3, 3), ModelTransform.pivot(at[i][0], 21.0F, at[i][1]));
		}
		// The bar, out of his forehead and a little up; its teeth along the top and the bottom, and at its end.
		ModelPartData bar = root.addChild("bar", box(0, 34, -0.5F, -2.5F, -13.0F, 1, 5, 13),
				ModelTransform.of(0.0F, 13.5F, -5.0F, -0.18F, 0.0F, 0.0F));
		for (int i = 0; i < 4; i++) {
			float z = -5.5F - i * 2.0F;
			bar.addChild("tooth_top" + i, box(46, 24, -0.5F, -3.5F, z, 1, 1, 1), ModelTransform.NONE);
			bar.addChild("tooth_bottom" + i, box(46, 24, -0.5F, 2.5F, z, 1, 1, 1), ModelTransform.NONE);
		}
		bar.addChild("tooth_end", box(46, 24, -0.5F, -0.5F, -14.0F, 1, 1, 1), ModelTransform.NONE);
		// The big D handle, leaning far back off the top of his head.
		ModelPartData handle = root.addChild("handle", ModelPartBuilder.create(), ModelTransform.of(0.0F, 11.5F, -3.0F, -1.0F, 0.0F, 0.0F));
		handle.addChild("post_left", box(28, 34, -4.5F, -6.0F, -1.0F, 1, 6, 2), ModelTransform.NONE);
		handle.addChild("post_right", box(28, 34, 3.5F, -6.0F, -1.0F, 1, 6, 2), ModelTransform.NONE);
		handle.addChild("grip", box(34, 34, -4.5F, -7.0F, -1.0F, 9, 1, 2), ModelTransform.NONE);
		// The tail: back out of his rump, up, and the starter's triangle at its end.
		ModelPartData tail = root.addChild("tail", box(46, 38, -0.5F, -0.5F, 0.0F, 1, 1, 3), ModelTransform.pivot(0.0F, 14.5F, 7.5F));
		ModelPartData up = tail.addChild("tail_up", box(54, 38, -0.5F, -4.0F, 2.0F, 1, 4, 1), ModelTransform.NONE);
		up.addChild("tail_grip", box(28, 44, 0.0F, -8.0F, -1.0F, 0, 4, 7), ModelTransform.NONE);
		return TexturedModelData.of(data, 64, 64);
	}

	private static ModelPartBuilder box(int u, int v, float x, float y, float z, int w, int h, int d) {
		return ModelPartBuilder.create().uv(u, v).cuboid(x, y, z, w, h, d);
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(PochitaEntity pochita, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		animate(limbAngle, limbDistance, animationProgress, pochita.isInSittingPose());
	}

	/** Poses him without an entity (the merge draws him itself): walking that far and fast, his tail wagging, sitting. */
	public void animate(float limbAngle, float limbDistance, float animationProgress, boolean sitting) {
		jaw.pitch = JAW_REST;
		tail.yaw = MathHelper.sin(animationProgress * 0.4F) * 0.35F;
		for (int i = 0; i < 4; i++) {
			float swing = MathHelper.cos(limbAngle * 0.9F + (i == 1 || i == 2 ? MathHelper.PI : 0.0F)) * 1.1F * limbDistance;
			// Sitting (the renderer tips him up at the back): the back legs folded forward under him.
			legs[i].pitch = sitting ? (i < 2 ? 0.0F : -1.2F) : swing;
		}
	}

	/** His mouth open {@code open} of the way (0 to 1), as he talks: his jaw dropping on its hinge. After {@link #animate}. */
	public void talk(float open) {
		jaw.pitch = JAW_REST + JAW_TALK * MathHelper.clamp(open, 0.0F, 1.0F);
	}
}
