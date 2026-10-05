package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.registry.ModItems;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * The Genesis Key in first person as the event starts, in time with its sound: it comes up into the middle of
 * the view and wakes, is pushed into a lock in the empty air and turns with the click and the clunk, and the
 * camera leaves.
 */
public final class KeyTurn {
	private KeyTurn() {
	}

	/** Draws the key itself; false leaves the hand to vanilla. */
	public static boolean render(HeldItemRenderer renderer, AbstractClientPlayerEntity player, float tickDelta, ItemStack item,
			MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		ClientGap gap = ClientGaps.mine();
		if (gap == null || !item.isOf(ModItems.GENESIS_KEY)) {
			return false;
		}
		double t = gap.time(tickDelta);
		if (t >= GapTimeline.RISE) {
			return false;
		}
		double up = ease(t / 16.0);
		double thrust = ease((t - 22.0) / 7.0);
		double turn = ease((t - 30.0) / 7.0);
		// The clunk at the end of the turn knocks it back a hair, and it hums.
		double knock = Math.exp(-Math.max(0.0, t - 37.0) / 2.0) * (t >= 37.0 ? 0.02 : 0.0);
		double shake = MathHelper.clamp((t - 37.0) / 5.0, 0.0, 1.0) * 0.004;
		float x = (float) (MathHelper.lerp(up, 0.42, 0.0) + Math.sin(t * 9.7) * shake);
		float y = (float) (MathHelper.lerp(up, -0.55, -0.1) + Math.sin(t * 7.3 + 1.0) * shake);
		float z = (float) (MathHelper.lerp(up, -0.7, -0.62) - 0.22 * thrust + knock);
		matrices.push();
		matrices.translate(x, y, z);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) MathHelper.lerp(up, -20.0, -55.0)));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (90.0 * turn + 12.0 * (1.0 - up))));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0F));
		// No centring here: the item renderer centres the model itself.
		matrices.scale(0.5F, 0.5F, 0.5F);
		renderer.renderItem(player, item, ModelTransformationMode.NONE, false, matrices, consumers,
				t > 10.0 ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light);
		matrices.pop();
		return true;
	}

	private static double ease(double x) {
		return GapCamera.ease(x);
	}
}
