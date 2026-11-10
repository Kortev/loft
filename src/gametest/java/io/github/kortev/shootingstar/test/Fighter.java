package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.client.ClientCombat;
import io.github.kortev.chainsawman.client.ClientForms;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A scripted player that fights through the real inputs, for the self tests' played takes: it turns its head toward a point (smoothly, as a
 * hand on a mouse would), walks or runs to a place, strafes aside to dodge, jumps, and swings (in the Chainsaw Man form the saws' slash, the
 * attack key's own path through {@link ClientCombat#attack}; otherwise the game's own attack on what is under the crosshair, when the
 * weapon is charged). Call the setters during a tick and {@link #apply} once at its end.
 */
public final class Fighter {
	/** Where the head is told to look (degrees), NaN for nowhere. */
	public float yaw = Float.NaN;
	public float pitch;
	/** How fast the head may turn (degrees a tick). */
	public float turn = 18.0F;
	private boolean fwd;
	private boolean back;
	private boolean left;
	private boolean right;
	private boolean run;
	private boolean jump;
	private int dodge;
	private int dodgeDir;
	/** Swings that were sent (the form's slashes count when the saws can come: one every {@code Saw.SLASH_EVERY} ticks). */
	public int swings;
	private long lastSwing = -100;

	private static float yawTo(Vec3d from, Vec3d to) {
		Vec3d d = to.subtract(from);
		return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
	}

	private static float pitchTo(Vec3d from, Vec3d to) {
		Vec3d d = to.subtract(from);
		return (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
	}

	/** Look at a world point (turned toward it at {@link #turn} a tick from where the head is). */
	public void aim(MinecraftClient c, Vec3d at) {
		ClientPlayerEntity p = c.player;
		Vec3d eye = p.getEyePos();
		float wantYaw = yawTo(eye, at);
		float wantPitch = MathHelper.clamp(pitchTo(eye, at), -89.0F, 89.0F);
		float fromYaw = Float.isNaN(yaw) ? p.getYaw() : yaw;
		float fromPitch = Float.isNaN(yaw) ? p.getPitch() : pitch;
		yaw = fromYaw + MathHelper.clamp(MathHelper.wrapDegrees(wantYaw - fromYaw), -turn, turn);
		pitch = fromPitch + MathHelper.clamp(wantPitch - fromPitch, -turn, turn);
	}

	/** Let the head be (the server or the game turns it). */
	public void free() {
		yaw = Float.NaN;
	}

	/** Walk (or run) toward a world point, the head turned to it; true once within {@code within} blocks (across the ground). */
	public boolean moveTo(MinecraftClient c, Vec3d at, double within, boolean sprint) {
		Vec3d p = c.player.getPos();
		double d = Math.hypot(at.x - p.x, at.z - p.z);
		if (d <= within) {
			fwd = false;
			return true;
		}
		aim(c, new Vec3d(at.x, c.player.getEyeY(), at.z));
		fwd = true;
		run = sprint;
		return false;
	}

	/** Keep walking forward the way the head faces (or stop). */
	public void walk(boolean on, boolean sprint) {
		fwd = on;
		run = on && sprint;
	}

	/** Strafe aside for {@code ticks} ({@code dir} -1 left, 1 right): a dodge. */
	public void dodge(int dir, int ticks) {
		dodgeDir = dir;
		dodge = ticks;
	}

	public boolean dodging() {
		return dodge > 0;
	}

	public void jump() {
		jump = true;
	}

	/** Stop moving (the head keeps where it is told). */
	public void stop() {
		fwd = false;
		back = false;
		left = false;
		right = false;
		run = false;
	}

	/**
	 * One press of the attack key: in the form the saws' slash (they come when they can; a press early is held for the moment they can), and
	 * otherwise the game's own attack on the entity under the crosshair once the weapon is charged ({@code charged}: how full, 0 to 1).
	 */
	public boolean swing(MinecraftClient c, float charged) {
		ClientPlayerEntity p = c.player;
		long now = p.getWorld().getTime();
		if (ClientForms.on(p)) {
			if (now - lastSwing >= io.github.kortev.chainsawman.form.Saw.SLASH_EVERY) {
				lastSwing = now;
				swings++;
			}
			ClientCombat.attack(c);
			return true;
		}
		if (p.getAttackCooldownProgress(0.5F) < charged) {
			return false;
		}
		HitResult hit = c.crosshairTarget;
		if (hit instanceof EntityHitResult eh) {
			Entity target = eh.getEntity();
			c.interactionManager.attackEntity(p, target);
		}
		p.swingHand(Hand.MAIN_HAND);
		lastSwing = now;
		swings++;
		return true;
	}

	/** Puts the tick's head and keys on the player. */
	public void apply(MinecraftClient c) {
		ClientPlayerEntity p = c.player;
		if (p == null) {
			return;
		}
		if (!Float.isNaN(yaw)) {
			p.setYaw(yaw);
			p.setHeadYaw(yaw);
			p.setBodyYaw(yaw);
			p.setPitch(pitch);
		}
		boolean l = left || dodge > 0 && dodgeDir < 0;
		boolean r = right || dodge > 0 && dodgeDir > 0;
		if (dodge > 0) {
			dodge--;
		}
		c.options.forwardKey.setPressed(fwd);
		c.options.backKey.setPressed(back);
		c.options.leftKey.setPressed(l);
		c.options.rightKey.setPressed(r);
		c.options.sprintKey.setPressed(fwd && run);
		c.options.jumpKey.setPressed(jump);
		jump = false;
	}

	/** Every key let go (at the end of a take). */
	public void release(MinecraftClient c) {
		stop();
		dodge = 0;
		jump = false;
		apply(c);
	}
}
