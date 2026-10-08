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
 * head to the sky, and lights up; it trembles harder and its runes burn brighter as the charge builds in it; the call
 * leaves it with a kick, and it hums on in the air until the camera goes up after the bolt, leaving it below.
 */
public final class HammerRaise {
	/** Ticks into the rise that the hammer is still drawn, falling away out of the picture as the camera leaves. */
	private static final int LEAVING = 3;

	private HammerRaise() {
	}

	/**
	 * Whether the hammer is held up in first person while a camera shot has put the HUD away (the call, followed up into
	 * the sky), so the hand is drawn anyway.
	 */
	public static boolean held() {
		ClientThunder thunder = ClientThunders.mine();
		return thunder != null && thunder.age < ThunderTimeline.RISE + LEAVING && ClientThunders.hidingHud();
	}

	/** Draws the hammer itself; false leaves the hand to vanilla. */
	public static boolean render(HeldItemRenderer renderer, AbstractClientPlayerEntity player, float tickDelta, ItemStack item,
			MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		ClientThunder thunder = ClientThunders.mine();
		if (thunder == null || !item.isOf(ModItems.MJOLNIR)) {
			return false;
		}
		double t = thunder.time(tickDelta);
		if (t >= ThunderTimeline.RISE + LEAVING) {
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
		// As the camera lifts away out of the shooter's head, the hammer they hold falls away down out of the picture.
		double leave = ease((t - (ThunderTimeline.RISE - 2.0)) / (LEAVING + 2.0));
		float y = (float) (MathHelper.lerp(up, -0.52, 0.12) + Math.sin(t * 9.1 + 1.0) * shake - kick - 1.4 * leave * leave);
		float z = (float) (MathHelper.lerp(up, -0.72, -0.82) + kick * 0.6);
		matrices.push();
		matrices.translate(x, y, z);
		// Head to the sky, tipped a little forward and in, so the face of the hammer shows.
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) MathHelper.lerp(up, -30.0, 12.0 + kick * 120.0)));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) MathHelper.lerp(up, -10.0, 14.0)));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) MathHelper.lerp(up, 20.0, -35.0)));
		// The model lies on the diagonal, the way a tool sits in a slot: stand it up. The item renderer centres it.
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0F));
		matrices.scale(0.62F, 0.62F, 0.62F);
		HammerGlow.boost(glow(t));
		renderer.renderItem(player, item, ModelTransformationMode.NONE, false, matrices, consumers,
				t > 6.0 ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light);
		HammerGlow.reset();
		matrices.pop();
		return true;
	}

	/**
	 * How brightly the runes of the hammer held up burn at {@code t} ({@link HammerGlow}'s scale): brighter as the charge
	 * builds, blazing at the call, humming on bright and unsteady while the storm gathers, blazing again as the stroke
	 * comes down, then burning down to their own glow.
	 */
	public static float glow(double t) {
		double span = HammerGlow.BLAZING - HammerGlow.STEADY;
		if (t < ThunderTimeline.CALL) {
			return (float) (HammerGlow.STEADY + span * ease((t - 6.0) / 10.0));
		}
		if (t < ThunderTimeline.STROKE) {
			double hum = 0.45 + 0.08 * Math.sin(t * 1.7) + 0.05 * Math.sin(t * 4.3);
			return (float) (HammerGlow.STEADY + span * Math.max(hum, Math.exp(-(t - ThunderTimeline.CALL) / 6.0)));
		}
		return (float) (HammerGlow.STEADY + span * Math.exp(-(t - ThunderTimeline.STROKE) / 8.0));
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
