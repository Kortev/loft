package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.ShaftArmEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One of the Eternity Devil's grab arms ({@link ShaftArmEntity}), from the shaft mesh's grab parts: a root rising out of the mass, copies of
 * the tiling segment stacked up the column (stretched to fit exactly), and the long-fingered hand slid up to the tip. The arm's column is
 * the entity's own x and z. While the tip is still low the root sinks into the mass instead of standing short.
 */
public class ShaftArmRenderer extends EntityRenderer<ShaftArmEntity> {
	/** The grab parts' rest geometry (shaft mesh): the root's base and the height of its top. */
	private static final float ROOT_BASE = 0.7F;
	private static final float ROOT_TOP = 2.9F;

	public ShaftArmRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ShaftArmEntity entity) {
		return Identifier.of("chainsawman", "textures/entity/eternity_devil.png");
	}

	@Override
	public boolean shouldRender(ShaftArmEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(ShaftArmEntity arm, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(arm.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float now = world.getTime() + delta;
		FleshMesh mesh = FleshMesh.get("shaft");
		int k = Math.floorMod(arm.getId(), 4);
		FleshMesh.Part root = mesh.byName.get("grab_" + k + "_root");
		FleshMesh.Part hand = mesh.byName.get("grab_" + k + "_hand");
		FleshMesh.Part seg = mesh.byName.get("grab_segment");
		if (root == null || hand == null || seg == null) {
			return;
		}
		var top = hand.json.getAsJsonArray("root_top");
		Vector3f rt = new Vector3f(top.get(0).getAsFloat(), 0.0F, top.get(2).getAsFloat());
		float height = Math.max(0.3F, arm.tip() - (float) arm.getY());
		float rootH = ROOT_TOP - ROOT_BASE;
		float baseY = height >= rootH ? 0.0F : height - rootH;
		float reach = Math.max(0.0F, height - rootH);
		// The mesh's frame with the root's top at the column and its foot at baseY.
		Matrix4f model = new Matrix4f(matrices.peek().getPositionMatrix()).translate(-rt.x, baseY - ROOT_BASE, -rt.z);
		FleshMesh.Look look = new FleshMesh.Look().light(light, 0.7F, 1.15F);
		look.rim = 0.45F;
		look.glowAt = new Vector3f(rt.x, ROOT_BASE - 1.5F, rt.z);
		look.glowRange = 3.0F;
		look.glowR = 0.4F;
		look.glowG = 0.06F;
		look.glowB = 0.03F;
		Matrix4f m = new Matrix4f(model);
		FleshMesh.pose(root.anim, root.pivot, now, m);
		mesh.drawPart(root, m, look, model);
		int n = (int) Math.ceil(reach / 0.5F);
		if (n > 0) {
			float step = reach / n;
			for (int i = 0; i < n; i++) {
				Matrix4f s = new Matrix4f(model).translate(rt.x, ROOT_TOP + i * step, rt.z).scale(1.0F, step / 0.5F, 1.0F);
				mesh.drawPart(seg, s, look, model);
			}
		}
		Matrix4f h = new Matrix4f(model).translate(0.0F, reach, 0.0F);
		if (arm.holding()) {
			// It closes on whoever it holds: the hand tips over its wrist.
			h.translate(rt.x, ROOT_TOP, rt.z).rotateX(0.35F).translate(-rt.x, -ROOT_TOP, -rt.z);
		}
		mesh.drawPart(hand, h, look, model);
	}
}
