package io.github.kortev.shootingstar.client.thunder;

import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.thunder.ThunderTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
		// As the camera lifts away out of the shooter's head, the hammer they hold falls away down out of the picture.
		double leave = ease((t - (ThunderTimeline.RISE - 2.0)) / (LEAVING + 2.0));
		// Held high: head to the sky, tipped a little forward and in, so the face of the hammer shows. The model lies on
		// the diagonal, the way a tool sits in a slot: stood up.
		Vector3f high = new Vector3f((float) (0.18 + Math.sin(t * 11.3) * shake),
				(float) (0.12 + Math.sin(t * 9.1 + 1.0) * shake - kick - 1.4 * leave * leave), (float) (-0.82 + kick * 0.6));
		Quaternionf highTurn = new Quaternionf().rotateX((float) Math.toRadians(12.0 + kick * 120.0))
				.rotateZ((float) Math.toRadians(14.0)).rotateY((float) Math.toRadians(-35.0)).rotateZ((float) Math.toRadians(45.0));
		// It swings up from just where the game draws it in the hand (the item model's first-person place and turn), so
		// the raise starts without a jump.
		Transformation held = MinecraftClient.getInstance().getItemRenderer().getModel(item, player.getWorld(), player, 0)
				.getTransformation().getTransformation(ModelTransformationMode.FIRST_PERSON_RIGHT_HAND);
		Vector3f low = new Vector3f(0.56F, -0.52F, -0.72F).add(held.translation);
		Quaternionf lowTurn = new Quaternionf().rotationXYZ((float) Math.toRadians(held.rotation.x()),
				(float) Math.toRadians(held.rotation.y()), (float) Math.toRadians(held.rotation.z()));
		Vector3f size = new Vector3f(held.scale).lerp(new Vector3f(0.62F), (float) up);
		matrices.push();
		Vector3f at = new Vector3f(low).lerp(high, (float) up);
		matrices.translate(at.x, at.y, at.z);
		matrices.multiply(new Quaternionf(lowTurn).slerp(highTurn, (float) up));
		matrices.scale(size.x, size.y, size.z);
		// Lit by the world in the hand, and by its own light as it charges.
		int block = LightmapTextureManager.getBlockLightCoordinates(light);
		int lit = LightmapTextureManager.pack(Math.round(MathHelper.lerp((float) ease((t - 3.0) / 6.0), block, 15.0F)),
				LightmapTextureManager.getSkyLightCoordinates(light));
		HammerGlow.boost(glow(t));
		renderer.renderItem(player, item, ModelTransformationMode.NONE, false, matrices, consumers, lit);
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
