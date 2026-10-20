package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.client.mixin.HeldItemRendererInvoker;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Your two saw arms in front of you in the form, as you see them ({@link SawPose} is how everyone else sees you): low
 * on either side at rest; a slash sweeps one across the view from its own side, the third brings both down from above;
 * grinding holds both out and in, shaking; the chain throws the right one forward.
 */
public final class FirstPersonSaws {
	/** Where each arm swings from in a sweep or a chop, in the view's space (blocks), mirrored for the left. */
	private static final float PIVOT_X = 0.55F;
	private static final float PIVOT_Y = -0.45F;
	private static final float PIVOT_Z = 0.1F;

	private FirstPersonSaws() {
	}

	public static void render(HeldItemRenderer renderer, float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate consumers,
			ClientPlayerEntity player, int light) {
		HeldItemRendererInvoker arms = (HeldItemRendererInvoker) renderer;
		float swing = ClientCombat.swingAge(player, tickDelta);
		ClientCombat.Swing which = ClientCombat.swing(player);
		int step = swing >= 0.0F && which != null ? which.step() : -1;
		float k = swing / ClientCombat.SWING;
		boolean grind = ClientCombat.grinding(player) || player.handSwinging && step < 0;
		// (The kick's arms go wide, as the lunge's go back.)
		boolean lunge = step == 3 || step == 4;
		ClientCombat.Chain chain = ClientCombat.chain(player);
		float chainOut = chain == null ? 0.0F
				: chain.held() ? 1.0F : 1.0F - MathHelper.clamp((player.getWorld().getTime() - chain.at() + tickDelta - 6.0F) / 4.0F, 0.0F, 1.0F);
		float time = player.age + tickDelta;
		for (Arm arm : new Arm[] {Arm.RIGHT, Arm.LEFT}) {
			if (!io.github.kortev.chainsawman.form.Limbs.has(player, arm == Arm.RIGHT ? io.github.kortev.chainsawman.form.Limbs.RIGHT
					: io.github.kortev.chainsawman.form.Limbs.LEFT)) {
				// Lost: its stub, where the hand was.
				Stumps.firstPerson(matrices, consumers, light, player, arm, tickDelta);
				continue;
			}
			float side = arm == Arm.RIGHT ? 1.0F : -1.0F;
			matrices.push();
			// At rest, a little lower and wider than a hand would be, the saws reaching up into the view.
			matrices.translate(side * 0.06F, -0.08F, 0.0F);
			if (grind) {
				float shake = MathHelper.sin(time * 9.0F) * 0.012F + MathHelper.sin(time * 23.0F) * 0.008F;
				matrices.translate(-side * 0.16F + shake, 0.12F + shake, -0.12F);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 12.0F));
			}
			if (lunge) {
				// Both arms swept back and down out of the way: the head leads.
				float back = MathHelper.sin(MathHelper.clamp(k, 0.0F, 1.0F) * MathHelper.PI);
				matrices.translate(side * 0.18F * back, -0.35F * back, 0.15F * back);
			} else if (step == 2) {
				// Both raised up out of view, then brought down hard through the middle.
				float up = ease(k / 0.3F);
				float down = ease((k - 0.3F) / 0.25F);
				float back = ease((k - 0.6F) / 0.4F);
				float angle = MathHelper.lerp(down, 55.0F * up, -12.0F) * (1.0F - back);
				pivot(matrices, side, () -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle)));
				matrices.translate(-side * 0.12F * down * (1.0F - back), 0.0F, -0.1F * down * (1.0F - back));
			} else if (step >= 0 && (step == 0) == (arm == Arm.RIGHT)) {
				// This arm sweeps across from its own side.
				float out = ease(k / 0.2F);
				float across = ease((k - 0.2F) / 0.3F);
				float back = ease((k - 0.5F) / 0.5F);
				float angle = MathHelper.lerp(across, -25.0F * out, 95.0F) * (1.0F - back);
				float lift = 0.18F * out * (1.0F - back);
				matrices.translate(0.0F, lift, -0.1F * out * (1.0F - back));
				pivot(matrices, side, () -> {
					matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * angle));
					matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -20.0F * out * (1.0F - back)));
				});
			} else if (step >= 0) {
				// The other arm drawn back out of the way.
				float away = MathHelper.sin(MathHelper.clamp(k, 0.0F, 1.0F) * MathHelper.PI);
				matrices.translate(side * 0.1F * away, -0.12F * away, 0.05F * away);
			}
			if (arm == Arm.RIGHT && chainOut > 0.0F) {
				matrices.translate(-0.12F * chainOut, 0.14F * chainOut, -0.25F * chainOut);
			}
			arms.chainsawman$renderArm(matrices, consumers, light, 0.0F, 0.0F, arm);
			matrices.pop();
		}
		SawTrails.renderView(consumers, player, tickDelta);
		consumers.draw();
	}

	/** Turns what {@code turn} does round the arm's shoulder in the view. */
	private static void pivot(MatrixStack matrices, float side, Runnable turn) {
		matrices.translate(side * PIVOT_X, PIVOT_Y, PIVOT_Z);
		turn.run();
		matrices.translate(-side * PIVOT_X, -PIVOT_Y, -PIVOT_Z);
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
