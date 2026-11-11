package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.client.mixin.HeldItemRendererInvoker;
import io.github.kortev.chainsawman.form.SwingRhythm;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Your two saw arms in front of you in the form, as you see them ({@link SawPose} is how everyone else sees you): low on either side at
 * rest. A slash is a whole swing of one arm: raised up and out at its own side, then brought down across the lower part of the view to the
 * far side and carried on, low, out of the way (the target stays in sight above it), the arms taking turns; the other drops away meanwhile.
 * The big cut brings both up off the top of the view and down hard together, crossing low in the middle. After a swing the arm comes back
 * from where it was ({@link SawPose#RECOVER}) rather than snapping. The blade's smear ({@link SawTrails}) is laid along its arc. Grinding
 * holds both out and in, shaking; the chain throws the right one forward.
 */
public final class FirstPersonSaws {
	/** Where each arm turns round, in the view's space (blocks), mirrored for the left: its fist, low at the side, where the saw comes out. */
	private static final float PIVOT_X = 0.62F;
	private static final float PIVOT_Y = -0.66F;
	private static final float PIVOT_Z = -0.7F;

	/**
	 * An arm's place in the view (for the right; the left mirrored): moved by x, y, z (blocks), then turned round its fist by roll
	 * (in the view's plane: positive leans its top in toward the middle), yaw (positive turns it in) and pitch (positive tips its top
	 * back toward you, negative forward into the view), in degrees.
	 */
	private record Place(float x, float y, float z, float roll, float yaw, float pitch) {
		static final Place REST = new Place(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

		Place lerp(float k, Place to) {
			return new Place(MathHelper.lerp(k, x, to.x), MathHelper.lerp(k, y, to.y), MathHelper.lerp(k, z, to.z), MathHelper.lerp(k, roll, to.roll),
					MathHelper.lerp(k, yaw, to.yaw), MathHelper.lerp(k, pitch, to.pitch));
		}
	}

	/** A sweep: wound up high and out at its own side; through the lower middle; across and low on the far side; carried on, low. */
	private static final Place WOUND = new Place(-0.26F, 0.2F, 0.02F, -22.0F, -38.0F, 22.0F);
	private static final Place THROUGH = new Place(-0.4F, 0.04F, -0.04F, 18.0F, 22.0F, -8.0F);
	private static final Place ACROSS = new Place(-0.5F, -0.12F, -0.02F, 34.0F, 72.0F, -24.0F);
	private static final Place CARRIED = new Place(-0.5F, -0.32F, 0.04F, 40.0F, 88.0F, -30.0F);
	/** The other arm meanwhile, dropped away out of the view's way. */
	private static final Place AWAY = new Place(0.06F, -0.18F, 0.06F, -10.0F, 0.0F, 10.0F);
	/** The big cut: both up off the top of the view, then down hard together, crossing low in the middle; held low after. */
	private static final Place OVERHEAD = new Place(-0.3F, 0.5F, 0.08F, -8.0F, -10.0F, 42.0F);
	private static final Place CHOPPED = new Place(-0.42F, -0.06F, -0.08F, 14.0F, 26.0F, -36.0F);
	private static final Place CHOP_CARRIED = new Place(-0.42F, -0.26F, -0.02F, 16.0F, 30.0F, -46.0F);
	/** When a sweep winds up, cuts through the middle and is across; when the big cut is overhead and down (shares of the swing). */
	private static final float WIND = 0.28F;
	private static final float MIDDLE = 0.4F;
	private static final float CUT = 0.54F;
	private static final float RAISED = 0.3F;
	private static final float DOWN = 0.48F;

	private FirstPersonSaws() {
	}

	public static void render(HeldItemRenderer renderer, float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate consumers,
			ClientPlayerEntity player, int light) {
		HeldItemRendererInvoker arms = (HeldItemRendererInvoker) renderer;
		float swing = ClientCombat.swingAge(player, tickDelta);
		float since = ClientCombat.sinceSwing(player, tickDelta);
		ClientCombat.Swing which = ClientCombat.swing(player);
		int step = swing >= 0.0F && which != null ? which.step() : -1;
		// Just after a slash, which one it was (the arm comes back from where it left it).
		int after = step < 0 && since >= 0.0F && which != null && which.step() <= 2 ? which.step() : -1;
		float k = swing / ClientCombat.SWING;
		float back = after >= 0 ? ease((since - ClientCombat.SWING) / SawPose.RECOVER) : 0.0F;
		int prev = SwingFx.prev(player);
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
			int mine = arm == Arm.RIGHT ? 0 : 1;
			matrices.push();
			// At rest, a little lower and wider than a hand would be, the saws reaching up into the view.
			matrices.translate(side * 0.06F, -0.08F, 0.0F);
			if (grind && step < 0) {
				float shake = MathHelper.sin(time * 9.0F) * 0.012F + MathHelper.sin(time * 23.0F) * 0.008F;
				matrices.translate(-side * 0.16F + shake, 0.12F + shake, -0.12F);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 12.0F));
			}
			Place place = Place.REST;
			if (lunge) {
				// Both arms swept back and down out of the way: the head leads.
				float out = MathHelper.sin(MathHelper.clamp(k, 0.0F, 1.0F) * MathHelper.PI);
				matrices.translate(side * 0.18F * out, -0.35F * out, 0.15F * out);
			} else if (step >= 0 && SwingRhythm.big(step)) {
				place = k < RAISED ? end(prev, prev == mine).lerp(ease(k / RAISED), OVERHEAD)
						: k < DOWN ? OVERHEAD.lerp(ease((k - RAISED) / (DOWN - RAISED)), CHOPPED)
								: CHOPPED.lerp(ease((k - DOWN) / (1.0F - DOWN)), CHOP_CARRIED);
			} else if (step >= 0 && step == mine) {
				// This arm's swing, from its own side across.
				place = k < WIND ? end(prev, prev == mine).lerp(ease(k / WIND), WOUND)
						: k < MIDDLE ? WOUND.lerp(ease((k - WIND) / (MIDDLE - WIND)), THROUGH)
								: k < CUT ? THROUGH.lerp(ease((k - MIDDLE) / (CUT - MIDDLE)), ACROSS)
										: ACROSS.lerp(ease((k - CUT) / (1.0F - CUT)), CARRIED);
			} else if (step >= 0) {
				// The other arm's swing: this one drops away, from where the last left it.
				place = end(prev, prev == mine).lerp(ease(k / 0.35F), AWAY).lerp(ease((k - 0.6F) / 0.4F) * 0.6F, Place.REST);
			} else if (after >= 0) {
				Place left = SwingRhythm.big(after) ? CHOP_CARRIED : after == mine ? CARRIED : AWAY.lerp(0.6F, Place.REST);
				place = left.lerp(back, Place.REST);
			}
			if (place != Place.REST) {
				matrices.translate(side * place.x(), place.y(), place.z());
				matrices.translate(side * PIVOT_X, PIVOT_Y, PIVOT_Z);
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * place.roll()));
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * place.yaw()));
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(place.pitch()));
				matrices.translate(-side * PIVOT_X, -PIVOT_Y, -PIVOT_Z);
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

	/** Where an arm was left by the swing before ({@code prev}, or -1): carried through if it made it ({@code swung}), dropped away if not. */
	private static Place end(int prev, boolean swung) {
		if (prev < 0) {
			return Place.REST;
		}
		if (SwingRhythm.big(prev)) {
			return CHOP_CARRIED;
		}
		return swung ? CARRIED : AWAY.lerp(0.6F, Place.REST);
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
