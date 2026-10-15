package io.github.kortev.shootingstar.test;

import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The film's own player, played the way a person plays: with a mouse and the keys. {@link FilmSelfTest} says each tick
 * what they mean to do (look at this, go there, sprint, crouch, stand about); this turns it into what a hand on a mouse
 * and fingers on WASD would do.
 *
 * <p>The mouse: a look somewhere new is a flick, fast in the middle and easing in and out (a minimum-jerk curve), a
 * little long or short and a little off to the side, put right by a smaller flick or two; something moving is followed a
 * moment behind; and the hand is never still (a slow tremor, and the eyes wander about whatever they are on).
 *
 * <p>The keys: going somewhere is W (or whichever keys take them there from where they are looking), sprinting with
 * the odd jump in it; and standing about is never standing still: a strafe, a step, a hop, a crouch now and then.
 *
 * <p>Everything random is drawn from one seeded generator, so the same take plays the same way every time.
 */
final class FilmHuman {
	enum Pace { SNEAK, WALK, SPRINT }

	private final Random random;

	// The mouse: where the view is (before the tremor), the flick under way and the pause after it.
	private boolean started;
	private double yaw;
	private double pitch;
	private float lastYaw;
	private float lastPitch;
	private double fromYaw;
	private double fromPitch;
	private double toYaw;
	private double toPitch;
	private int flickAt = -1;
	private int flickLength;
	private int dwell;
	private double tremorYaw;
	private double tremorPitch;
	// Where the eyes are drawn this tick, and how far they wander round it.
	@Nullable
	private Vec3d gaze;
	private double gazeYaw = Double.NaN;
	private double gazePitch;
	private double wander = 3.0;
	private double offYaw;
	private double offPitch;
	private int nextWander;
	/** How quickly a small difference is followed (a share of it a tick). */
	private double follow = 0.3;

	// The feet.
	@Nullable
	private Vec3d goal;
	private Pace pace = Pace.WALK;
	private double stopWithin = 0.6;
	private boolean hop;
	private int nextHop;
	private boolean back;
	private boolean sneak;
	private boolean spam;
	private boolean jumpOnce;
	private double fidget;
	private int nextFidget;
	private int fidgetUntil = -1;
	private int fidgetKind;

	FilmHuman(long seed) {
		random = new Random(seed);
	}

	// --- what they mean to do (set every tick by the script) --------------------------------

	/** Look at {@code at} (null: keep looking where they are). */
	FilmHuman look(@Nullable Vec3d at) {
		gaze = at;
		gazeYaw = Double.NaN;
		return this;
	}

	/** Look this way. */
	FilmHuman look(double yaw, double pitch) {
		gaze = null;
		gazeYaw = yaw;
		gazePitch = pitch;
		return this;
	}

	/** How far (degrees) the eyes wander round what they look at: a few when taking it in, more when idle. */
	FilmHuman wander(double degrees) {
		wander = degrees;
		return this;
	}

	/** How closely they follow: 0.3 normally, less when lazy, more when locked on. */
	FilmHuman follow(double share) {
		follow = share;
		return this;
	}

	/** Go to {@code to} at {@code pace} (null: stay), stopping within {@code within}; sprinting, with a jump now and then if {@code hop}. */
	FilmHuman go(@Nullable Vec3d to, Pace pace, double within, boolean hop) {
		goal = to;
		this.pace = pace;
		stopWithin = within;
		this.hop = hop;
		return this;
	}

	FilmHuman stay() {
		goal = null;
		return this;
	}

	/** Hold S (walking backwards, as when bridging). */
	FilmHuman back(boolean held) {
		back = held;
		return this;
	}

	/** Hold the sneak key. */
	FilmHuman sneak(boolean held) {
		sneak = held;
		return this;
	}

	/** Crouch up and down as fast as a finger can (the taunt). */
	FilmHuman spam(boolean on) {
		spam = on;
		return this;
	}

	/** Jump once, now (if on the ground). */
	FilmHuman jump() {
		jumpOnce = true;
		return this;
	}

	/** Standing about: how restless (0: quite still, 1: a step or hop every couple of seconds, 2: can't keep still). */
	FilmHuman fidget(double level) {
		fidget = level;
		return this;
	}

	/** True once within {@code within} of {@code at}, across the ground. */
	static boolean near(ClientPlayerEntity player, Vec3d at, double within) {
		return Math.hypot(at.x - player.getX(), at.z - player.getZ()) <= within;
	}

	/** True while the view is still moving towards what it was told to look at. */
	boolean turning() {
		return flickAt >= 0 || dwell > 0;
	}

	/** How far (degrees) the view is from {@code at}. */
	double off(ClientPlayerEntity player, Vec3d at) {
		double[] a = angles(player.getEyePos(), at);
		return Math.hypot(MathHelper.wrapDegrees(a[0] - player.getYaw()), a[1] - player.getPitch());
	}

	/** Lets go of every key (for while the game has the player, or a screen is open). */
	static void release(MinecraftClient client) {
		GameOptions o = client.options;
		o.forwardKey.setPressed(false);
		o.backKey.setPressed(false);
		o.leftKey.setPressed(false);
		o.rightKey.setPressed(false);
		o.jumpKey.setPressed(false);
		o.sneakKey.setPressed(false);
		o.sprintKey.setPressed(false);
	}

	// --- doing it ---------------------------------------------------------------------------

	/** Moves the mouse and presses the keys for this tick. */
	void tick(MinecraftClient client, ClientPlayerEntity player, int now) {
		mouse(player, now);
		feet(client, player, now);
	}

	/** Only the mouse (when the feet are not theirs: carried, held, or in a screen). */
	void mouseOnly(ClientPlayerEntity player, int now) {
		mouse(player, now);
	}

	private void mouse(ClientPlayerEntity player, int now) {
		// Turned by something else since last tick (carried somewhere, the event giving the view back): start from there.
		if (!started || Math.abs(MathHelper.wrapDegrees(player.getYaw() - lastYaw)) > 0.5 || Math.abs(player.getPitch() - lastPitch) > 0.5) {
			started = true;
			yaw = player.getYaw();
			pitch = player.getPitch();
			flickAt = -1;
			dwell = 0;
		}
		if (now >= nextWander) {
			offYaw = random.nextGaussian() * wander;
			offPitch = random.nextGaussian() * wander * 0.4;
			nextWander = now + 25 + random.nextInt(70);
		}
		double wantYaw;
		double wantPitch;
		if (gaze != null) {
			double[] a = angles(player.getEyePos(), gaze);
			wantYaw = a[0];
			wantPitch = a[1];
		} else if (!Double.isNaN(gazeYaw)) {
			wantYaw = gazeYaw;
			wantPitch = gazePitch;
		} else {
			wantYaw = yaw;
			wantPitch = pitch;
		}
		wantYaw += offYaw;
		wantPitch = MathHelper.clamp(wantPitch + offPitch, -89.0, 89.0);
		if (flickAt >= 0) {
			double t = (now - flickAt + 1) / (double) flickLength;
			double k = t >= 1.0 ? 1.0 : t * t * t * (10.0 - 15.0 * t + 6.0 * t * t);
			yaw = fromYaw + (toYaw - fromYaw) * k;
			pitch = fromPitch + (toPitch - fromPitch) * k;
			if (t >= 1.0) {
				flickAt = -1;
				dwell = 1 + random.nextInt(3);
			}
		} else if (dwell > 0) {
			dwell--;
		} else {
			double dy = MathHelper.wrapDegrees(wantYaw - yaw);
			double dp = wantPitch - pitch;
			double amp = Math.hypot(dy, dp);
			if (amp > 2.5) {
				// A flick: a little past or short of it and a little to one side, as a hand throws a mouse.
				double along = 1.03 + random.nextGaussian() * 0.06;
				double aside = random.nextGaussian() * 0.025 * amp;
				double ux = dy / amp;
				double uy = dp / amp;
				fromYaw = yaw;
				fromPitch = pitch;
				toYaw = yaw + dy * along - uy * aside;
				toPitch = MathHelper.clamp(pitch + dp * along + ux * aside, -89.5, 89.5);
				flickLength = Math.max(2, (int) Math.round(2.0 + 0.95 * Math.log1p(amp) + random.nextGaussian() * 0.5));
				flickAt = now;
			} else {
				yaw += dy * follow;
				pitch += dp * follow;
			}
		}
		// The hand's tremor: slow, small, never quite still.
		tremorYaw += -tremorYaw * 0.2 + random.nextGaussian() * 0.11;
		tremorPitch += -tremorPitch * 0.2 + random.nextGaussian() * 0.07;
		lastYaw = (float) (yaw + tremorYaw);
		lastPitch = (float) MathHelper.clamp(pitch + tremorPitch, -90.0, 90.0);
		player.setYaw(lastYaw);
		player.setPitch(lastPitch);
	}

	private void feet(MinecraftClient client, ClientPlayerEntity player, int now) {
		boolean w = false;
		boolean a = false;
		boolean s = back;
		boolean d = false;
		boolean jump = false;
		boolean crouch = sneak || pace == Pace.SNEAK && goal != null;
		boolean sprint = false;
		if (goal != null) {
			double dx = goal.x - player.getX();
			double dz = goal.z - player.getZ();
			double dist = Math.hypot(dx, dz);
			if (dist > stopWithin) {
				float facing = player.getYaw();
				double want = MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN - 90.0;
				double rel = MathHelper.wrapDegrees(want - facing);
				w = Math.abs(rel) < 67.5;
				s = s || Math.abs(rel) > 112.5;
				d = rel > 22.5 && rel < 157.5;
				a = rel < -22.5 && rel > -157.5;
				// Momentum carries a sprinter on: off the sprint a little before getting there.
				sprint = pace == Pace.SPRINT && w && Math.abs(rel) < 35.0 && dist > 3.0;
				if (sprint && hop && player.isOnGround() && now >= nextHop) {
					jump = true;
					nextHop = now + 10 + random.nextInt(9);
				}
			}
		} else if (fidget > 0.0 && !back) {
			if (now >= nextFidget) {
				fidgetKind = random.nextInt(10);
				fidgetUntil = now + 2 + random.nextInt(fidgetKind < 6 ? 6 : 3);
				nextFidget = now + (int) ((30 + random.nextInt(70)) / fidget);
			}
			if (now < fidgetUntil) {
				switch (fidgetKind) {
					case 0, 1, 2 -> a = true;
					case 3, 4, 5 -> d = true;
					case 6 -> w = true;
					case 7 -> s = true;
					case 8 -> jump = player.isOnGround();
					default -> crouch = true;
				}
			}
		}
		if (spam) {
			crouch = (now / 2) % 2 == 0;
		}
		if (jumpOnce) {
			jump = jump || player.isOnGround();
			jumpOnce = false;
		}
		GameOptions o = client.options;
		o.forwardKey.setPressed(w);
		o.backKey.setPressed(s);
		o.leftKey.setPressed(a);
		o.rightKey.setPressed(d);
		o.jumpKey.setPressed(jump);
		o.sneakKey.setPressed(crouch);
		o.sprintKey.setPressed(sprint);
		if (sprint) {
			player.setSprinting(true);
		} else if (!w) {
			player.setSprinting(false);
		}
	}

	static double[] angles(Vec3d eye, Vec3d at) {
		Vec3d d = at.subtract(eye);
		double yaw = MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN - 90.0;
		double pitch = -(MathHelper.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * MathHelper.DEGREES_PER_RADIAN);
		return new double[] {yaw, MathHelper.clamp(pitch, -89.0, 89.0)};
	}
}
