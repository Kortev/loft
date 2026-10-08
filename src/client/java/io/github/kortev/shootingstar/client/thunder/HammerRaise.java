package io.github.kortev.shootingstar.client.thunder;

import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.thunder.ThunderTimeline;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Mjölnir in first person as the storm is called, in time with its sound: the hammer swings up over the shooter's head,
 * head to the sky, and lights up; it trembles harder as the charge builds in it; the call leaves it with a kick, and it
 * hums on in the air until the camera goes up after the bolt.
 */
public final class HammerRaise {
	private HammerRaise() {
	}

	/** Draws the hammer itself; false leaves the hand to vanilla. */
	public static boolean render(HeldItemRenderer renderer, AbstractClientPlayerEntity player, float tickDelta, ItemStack item,
			MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		ClientThunder thunder = ClientThunders.mine();
		if (thunder == null || !item.isOf(ModItems.MJOLNIR)) {
			return false;
		}
		double t = thunder.time(tickDelta);
		if (t >= ThunderTimeline.RISE) {
			return false;
		}
		double up = ease(t / 10.0);
		// The charge: a tremble that builds to the call, and fades after it.
		double charge = t < ThunderTimeline.CALL ? ease((t - 6.0) / 10.0) : Math.exp(-(t - ThunderTimeline.CALL) / 6.0);
		double shake = 0.012 * charge;
		// The call leaves it with a kick down and back, settling over a few ticks.
		double since = t - ThunderTimeline.CALL;
		double kick = since >= 0 ? Math.exp(-since / 2.5) * Math.sin(Math.min(since, 6.0) * 1.3) * 0.06 : 0.0;
		float x = (float) (MathHelper.lerp(up, 0.56, 0.18) + Math.sin(t * 11.3) * shake);
		float y = (float) (MathHelper.lerp(up, -0.52, 0.12) + Math.sin(t * 9.1 + 1.0) * shake - kick);
		float z = (float) (MathHelper.lerp(up, -0.72, -0.82) + kick * 0.6);
		matrices.push();
		matrices.translate(x, y, z);
		// Head to the sky, tipped a little forward and in, so the face of the hammer shows.
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) MathHelper.lerp(up, -30.0, 12.0 + kick * 120.0)));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) MathHelper.lerp(up, -10.0, 14.0)));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) MathHelper.lerp(up, 20.0, -35.0)));
		matrices.scale(0.62F, 0.62F, 0.62F);
		renderer.renderItem(player, item, ModelTransformationMode.NONE, false, matrices, consumers,
				t > 6.0 ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light);
		matrices.pop();
		return true;
	}

	/** Where the hammer's head is in the world while it is held up to call the storm: over the right shoulder. */
	public static Vec3d hammerHead(PlayerEntity player, float tickDelta) {
		Vec3d eye = player.getCameraPosVec(tickDelta);
		float yaw = player.getYaw(tickDelta) * MathHelper.RADIANS_PER_DEGREE;
		Vec3d right = new Vec3d(-MathHelper.cos(yaw), 0.0, -MathHelper.sin(yaw));
		Vec3d forward = new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw));
		return eye.add(0.0, 0.95, 0.0).add(right.multiply(0.3)).add(forward.multiply(0.25));
	}

	private static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
