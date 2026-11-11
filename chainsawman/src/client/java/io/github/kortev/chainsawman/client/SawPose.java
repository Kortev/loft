package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.form.SwingRhythm;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * The body fighting in the form, as everyone sees it, in place of the game's punch. The combo ({@link ClientCombat#SWING}
 * ticks a slash; which arm comes from the server, so everyone sees the same one): a sweep is a real swing of the whole arm,
 * raised high and out behind its shoulder, then brought down across in front in a wide arc and carried on past, the torso
 * winding away and then twisting hard into it, the other arm swung back for balance and the leg under the cut stepping in;
 * the left the same way back; then the big cut, both arms up overhead and brought down together hard, the saws crossing low,
 * the body bending into it and the head's saw nodding down with them. The arc tilts up or down with where you look. After a
 * swing the arms don't snap back: they carry on through ({@link #RECOVER}), and the next swing starts from where they are.
 * The lunge: bent forward headfirst, the head's saw leading, arms swept back. Grinding (or cutting through a block): both arms
 * held out in front, shaking with the engine. The chain out: the right arm held out along it. And the rest of the time his own
 * stance, not a player's: standing, arms a little forward and out, saws low; running, leaning in with the arms swept back,
 * saws trailing. In a frenzy the arms hang wider and the head comes forward.
 *
 * <p>Angles in radians, as in {@link CordPull}: an arm out in front is pitched -π/2; a raised arm swings out from the
 * body with a positive yaw on the right.
 */
public final class SawPose {
	private static final float FORWARD = -MathHelper.HALF_PI;
	/** How long the arms take to come back to rest after a swing if no other comes (ticks). */
	public static final float RECOVER = 7.0F;

	/** One arm's angles (pitch, yaw, roll), as for the right arm: the left takes them mirrored (its yaw and roll the other way). */
	private record Arm(float pitch, float yaw, float roll) {
		Arm lerp(float k, Arm to) {
			return new Arm(MathHelper.lerp(k, pitch, to.pitch), MathHelper.lerp(k, yaw, to.yaw), MathHelper.lerp(k, roll, to.roll));
		}
	}

	/** A sweep's arm: raised high and out behind its shoulder, through the middle in front, across and down, carried on past. */
	private static final Arm WOUND = new Arm(-2.8F, 1.0F, 0.6F);
	private static final Arm THROUGH = new Arm(-1.6F, -0.1F, 0.05F);
	private static final Arm ACROSS = new Arm(-0.9F, -1.05F, -0.35F);
	private static final Arm CARRIED = new Arm(-0.65F, -1.2F, -0.3F);
	/** The other arm meanwhile, swung back for balance. */
	private static final Arm BALANCE = new Arm(0.6F, 0.15F, 0.4F);
	/** The big cut's arms: up overhead and back, then down hard in front, the saws crossing low, and held there. */
	private static final Arm OVERHEAD = new Arm(-3.1F, 0.3F, 0.4F);
	private static final Arm CHOPPED = new Arm(-0.55F, -0.45F, -0.12F);
	private static final Arm CHOP_CARRIED = new Arm(-0.35F, -0.52F, -0.1F);
	/** The torso's twist in a sweep: wound away (the swinging shoulder back), then hard into it; held a little after. */
	private static final float TWIST_AWAY = 0.42F;
	private static final float TWIST_INTO = -0.62F;
	private static final float TWIST_HELD = 0.8F;
	/** How far the leg under a cut steps in (radians), and how far the body leans into it. */
	private static final float STEP = 0.42F;
	private static final float LEAN = 0.2F;
	/** When a sweep winds up, cuts through the middle, and is across (shares of the swing). */
	private static final float WIND = 0.22F;
	private static final float MIDDLE = 0.36F;
	private static final float CUT = 0.5F;
	/** When the big cut is overhead, and down. */
	private static final float RAISED = 0.3F;
	private static final float DOWN = 0.48F;

	private SawPose() {
	}

	/** Poses {@code player} in the form: a slash, grinding, the chain, or standing ready. */
	public static void pose(BipedEntityModel<?> model, PlayerEntity player, float tickDelta) {
		if (player.getVehicle() instanceof io.github.kortev.chainsawman.BeamEntity) {
			ride(model, player, tickDelta);
			return;
		}
		float swing = ClientCombat.swingAge(player, tickDelta);
		float since = ClientCombat.sinceSwing(player, tickDelta);
		ClientCombat.Swing which = ClientCombat.swing(player);
		ClientCombat.Chain chain = ClientCombat.chain(player);
		boolean grinding = ClientCombat.grinding(player) || player.handSwinging;
		if (swing >= 0.0F && which != null && which.step() == 4) {
			kick(model, swing / ClientCombat.SWING);
		} else if (swing >= 0.0F && which != null && which.step() == 3) {
			lunge(model, swing / ClientCombat.SWING);
		} else if (swing >= 0.0F && which != null) {
			slash(model, player, which.step(), swing / ClientCombat.SWING, SwingFx.prev(player));
		} else if (grinding) {
			grind(model, player.age + tickDelta);
		} else if (since >= 0.0F && which != null && which.step() <= 2) {
			// Just after a swing: the arms carrying on through, back to rest.
			recover(model, player, which.step(), ease((since - ClientCombat.SWING) / RECOVER));
		} else {
			stance(model, player);
		}
		if (ClientCombat.frenzy(player)) {
			model.rightArm.roll += 0.2F;
			model.leftArm.roll -= 0.2F;
			model.head.pitch += 0.15F;
		}
		if (chain != null) {
			model.rightArm.pitch = FORWARD + model.head.pitch;
			model.rightArm.yaw = model.head.yaw;
			model.rightArm.roll = 0.0F;
		}
	}

	/**
	 * On Beam's back: standing, knees bent and feet apart, leaning in, both arms out in front holding the reins (his chain),
	 * or held out grinding with the saws when the attack key is held.
	 */
	private static void ride(BipedEntityModel<?> model, PlayerEntity player, float tickDelta) {
		if (ClientCombat.grinding(player) || player.handSwinging) {
			grind(model, player.age + tickDelta);
		} else {
			model.rightArm.pitch = FORWARD + 0.5F;
			model.leftArm.pitch = FORWARD + 0.5F;
			model.rightArm.yaw = -0.18F;
			model.leftArm.yaw = 0.18F;
			model.rightArm.roll = 0.0F;
			model.leftArm.roll = 0.0F;
			model.body.pitch = 0.25F;
			model.head.pitch -= 0.1F;
		}
		model.rightLeg.pitch = -0.5F;
		model.leftLeg.pitch = -0.5F;
		model.rightLeg.roll = 0.28F;
		model.leftLeg.roll = -0.28F;
		model.rightLeg.yaw = 0.0F;
		model.leftLeg.yaw = 0.0F;
	}

	/** The kick: the right leg up and out in front, fast, the arms out for balance, leaning back into it; and down. */
	private static void kick(BipedEntityModel<?> model, float k) {
		float up = MathHelper.sin(MathHelper.clamp(k / 0.7F, 0.0F, 1.0F) * MathHelper.PI);
		model.rightLeg.pitch = -1.75F * up;
		model.leftLeg.pitch = 0.15F * up;
		model.rightArm.pitch = -0.4F * up;
		model.leftArm.pitch = -0.4F * up;
		model.rightArm.roll = 0.6F * up;
		model.leftArm.roll = -0.6F * up;
		model.rightArm.yaw = 0.0F;
		model.leftArm.yaw = 0.0F;
		model.body.pitch = -0.2F * up;
		model.head.pitch -= 0.15F * up;
	}

	/**
	 * A slash {@code k} through (0 to 1), step {@code step} of the combo; {@code prev}, the step before it in the same flurry (or -1),
	 * whose arm is still carrying through as this one starts.
	 */
	private static void slash(BipedEntityModel<?> model, PlayerEntity player, int step, float k, int prev) {
		// The rest under it, which the arms leave and come back to.
		stance(model, player);
		float look = MathHelper.clamp(model.head.pitch, -1.1F, 1.1F);
		Arm restRight = of(model.rightArm, 1.0F);
		Arm restLeft = of(model.leftArm, -1.0F);
		if (SwingRhythm.big(step)) {
			// Both up overhead (from wherever the last swing left them), then down together hard, crossing low.
			Arm a = k < RAISED ? end(prev, prev == 0, restRight).lerp(ease(k / RAISED), OVERHEAD) : chop(k, look);
			Arm b = k < RAISED ? end(prev, prev == 1, restLeft).lerp(ease(k / RAISED), OVERHEAD) : chop(k, look);
			set(model.rightArm, a, 1.0F);
			set(model.leftArm, b, -1.0F);
			float up = ease(k / RAISED);
			float down = ease((k - RAISED) / (DOWN - RAISED));
			model.body.yaw = 0.0F;
			model.body.pitch = MathHelper.lerp(down, -0.18F * up, 0.45F);
			// The head's saw back with the arms, then nodding down hard with them.
			model.head.pitch += MathHelper.lerp(down, -0.3F * up, 0.6F);
			model.rightLeg.pitch -= STEP * down;
			model.leftLeg.pitch += 0.2F * down;
			return;
		}
		float side = SwingRhythm.side(step);
		boolean right = side > 0.0F;
		ModelPart swinging = right ? model.rightArm : model.leftArm;
		ModelPart other = right ? model.leftArm : model.rightArm;
		Arm rest = right ? restRight : restLeft;
		Arm otherRest = right ? restLeft : restRight;
		// This arm winds up from where it was (swung back for balance in the last sweep, or down from the big cut, or at rest).
		Arm a = k < WIND ? end(prev, step == prev, rest).lerp(ease(k / WIND), WOUND) : sweep(k, look);
		// The other swings back for balance, from where it was (carrying through the last cut, if it made it).
		Arm from = end(prev, prev == 2 || prev == (right ? 1 : 0), otherRest);
		Arm b = from.lerp(ease(k / 0.35F), BALANCE).lerp(ease((k - 0.6F) / 0.4F) * 0.5F, otherRest);
		// The torso: wound away (from where the last sweep left it: twisted into that one is wound away for this), then into the cut.
		float from0 = prev >= 0 && prev != 2 && prev != step ? -TWIST_INTO * TWIST_HELD : 0.0F;
		float twist;
		if (k < WIND) {
			twist = MathHelper.lerp(ease(k / WIND), from0, TWIST_AWAY);
		} else if (k < CUT) {
			twist = MathHelper.lerp(ease((k - WIND) / (CUT - WIND)), TWIST_AWAY, TWIST_INTO);
		} else {
			twist = MathHelper.lerp(ease((k - CUT) / (1.0F - CUT)), TWIST_INTO, TWIST_INTO * TWIST_HELD);
		}
		turn(model, twist * side);
		set(swinging, a, side);
		set(other, b, -side);
		swinging.yaw += model.body.yaw;
		other.yaw += model.body.yaw;
		// Into it: the leg on the other side stepping in under the cut, the body leaning over it.
		float into = ease((k - WIND) / (CUT - WIND)) * (1.0F - 0.4F * ease((k - CUT) / (1.0F - CUT)));
		model.body.pitch = LEAN * into;
		(right ? model.leftLeg : model.rightLeg).pitch -= STEP * into;
		(right ? model.rightLeg : model.leftLeg).pitch += 0.2F * into;
	}

	/** After a swing of {@code step}: the arms carrying on from where it left them back to rest, {@code r} of the way (0 to 1). */
	private static void recover(BipedEntityModel<?> model, PlayerEntity player, int step, float r) {
		stance(model, player);
		float look = MathHelper.clamp(model.head.pitch, -1.1F, 1.1F);
		Arm restRight = of(model.rightArm, 1.0F);
		Arm restLeft = of(model.leftArm, -1.0F);
		if (SwingRhythm.big(step)) {
			set(model.rightArm, chop(1.0F, look).lerp(r, restRight), 1.0F);
			set(model.leftArm, chop(1.0F, look).lerp(r, restLeft), -1.0F);
			model.body.pitch = 0.45F * (1.0F - r);
			model.head.pitch += 0.6F * (1.0F - r);
			return;
		}
		float side = SwingRhythm.side(step);
		boolean right = side > 0.0F;
		turn(model, TWIST_INTO * TWIST_HELD * (1.0F - r) * side);
		ModelPart swinging = right ? model.rightArm : model.leftArm;
		ModelPart other = right ? model.leftArm : model.rightArm;
		set(swinging, sweep(1.0F, look).lerp(r, right ? restRight : restLeft), side);
		set(other, BALANCE.lerp(0.5F, right ? restLeft : restRight).lerp(r, right ? restLeft : restRight), -side);
		swinging.yaw += model.body.yaw;
		other.yaw += model.body.yaw;
		model.body.pitch = LEAN * 0.6F * (1.0F - r);
	}

	/**
	 * Where an arm was left by the swing before ({@code prev}, or -1 for none): carrying through if it made that swing ({@code swung}:
	 * the cut's end; the big cut's both arms), else swung back for balance; at {@code rest} if there was none.
	 */
	private static Arm end(int prev, boolean swung, Arm rest) {
		if (prev < 0) {
			return rest;
		}
		if (SwingRhythm.big(prev)) {
			return CHOP_CARRIED;
		}
		return swung ? CARRIED : BALANCE.lerp(0.5F, rest);
	}

	/** Where a sweep's arm is {@code k} through the swing (0 to 1); {@code look}: the head's pitch, the arc tilted with it. */
	private static Arm sweep(float k, float look) {
		Arm a;
		if (k < MIDDLE) {
			a = WOUND.lerp(ease((k - WIND) / (MIDDLE - WIND)), THROUGH);
		} else if (k < CUT) {
			a = THROUGH.lerp(ease((k - MIDDLE) / (CUT - MIDDLE)), ACROSS);
		} else {
			a = ACROSS.lerp(ease((k - CUT) / (1.0F - CUT)), CARRIED);
		}
		// Up or down the way you look: the cut goes where you aim it.
		return new Arm(a.pitch() + look * 0.8F * MathHelper.clamp((k - WIND * 0.5F) / 0.3F, 0.0F, 1.0F), a.yaw(), a.roll());
	}

	/** Where the big cut's arms are {@code k} through it, after they are overhead (as for the right). */
	private static Arm chop(float k, float look) {
		Arm a = k < DOWN ? OVERHEAD.lerp(ease((k - RAISED) / (DOWN - RAISED)), CHOPPED) : CHOPPED.lerp(ease((k - DOWN) / (1.0F - DOWN)), CHOP_CARRIED);
		return new Arm(a.pitch() + look * 0.7F * MathHelper.clamp((k - RAISED) / (DOWN - RAISED), 0.0F, 1.0F), a.yaw(), a.roll());
	}

	/** The torso twisted round by {@code yaw}, the shoulders going round with it. */
	private static void turn(BipedEntityModel<?> model, float yaw) {
		model.body.yaw = yaw;
		model.rightArm.pivotZ = MathHelper.sin(yaw) * 5.0F;
		model.rightArm.pivotX = -MathHelper.cos(yaw) * 5.0F;
		model.leftArm.pivotZ = -MathHelper.sin(yaw) * 5.0F;
		model.leftArm.pivotX = MathHelper.cos(yaw) * 5.0F;
	}

	private static void set(ModelPart part, Arm a, float side) {
		part.pitch = a.pitch();
		part.yaw = a.yaw() * side;
		part.roll = a.roll() * side;
	}

	private static Arm of(ModelPart part, float side) {
		return new Arm(part.pitch, part.yaw * side, part.roll * side);
	}

	/** Standing or walking: arms a little forward and out, swinging less than a player's; running: leaning in, arms back. */
	private static void stance(BipedEntityModel<?> model, PlayerEntity player) {
		if (player.isSprinting()) {
			float swing = model.rightArm.pitch * 0.25F;
			model.rightArm.pitch = 0.85F + swing;
			model.leftArm.pitch = 0.85F - swing;
			model.rightArm.roll = 0.3F;
			model.leftArm.roll = -0.3F;
			model.body.pitch = 0.3F;
			model.head.pitch -= 0.25F;
			return;
		}
		model.rightArm.pitch = model.rightArm.pitch * 0.4F - 0.3F;
		model.leftArm.pitch = model.leftArm.pitch * 0.4F - 0.3F;
		model.rightArm.roll += 0.18F;
		model.leftArm.roll -= 0.18F;
	}

	/** Headfirst: bent forward, the head's saw leading, the arms swept back. */
	private static void lunge(BipedEntityModel<?> model, float k) {
		float in = ease(k / 0.2F) * (1.0F - ease((k - 0.7F) / 0.3F));
		model.body.pitch = 0.5F * in;
		model.head.pitch += 0.25F * in;
		model.rightArm.pitch = 1.1F * in;
		model.leftArm.pitch = 1.1F * in;
		model.rightArm.roll = 0.25F * in;
		model.leftArm.roll = -0.25F * in;
	}

	/** Both arms out in front, a little in, shaking with the engine. */
	private static void grind(BipedEntityModel<?> model, float time) {
		float shake = MathHelper.sin(time * 9.0F) * 0.04F + MathHelper.sin(time * 23.0F) * 0.025F;
		model.rightArm.pitch = FORWARD + 0.15F + shake;
		model.leftArm.pitch = FORWARD + 0.15F - shake;
		model.rightArm.yaw = -0.3F + shake;
		model.leftArm.yaw = 0.3F - shake;
		model.rightArm.roll = 0.0F;
		model.leftArm.roll = 0.0F;
		model.body.pitch = 0.12F;
		model.head.pitch += 0.15F;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
