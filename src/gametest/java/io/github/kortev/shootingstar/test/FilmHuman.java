package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.test.mixin.FilmClickInvoker;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The film's own player, played the way a person plays: with a mouse and the keys. {@link FilmSelfTest} says each tick
 * what they mean to do (look at this, look about at these, go there, crouch, click); this turns it into what a hand on
 * a mouse and fingers on WASD would do.
 *
 * <p>The mouse is still unless it is moving somewhere: a hand at rest on a mouse leaves the view where it is. When what
 * they mean to look at is far enough off, the hand moves after a moment's reaction: one quick stroke, fast in the middle
 * and easing in and out (a minimum-jerk curve), on a slightly curved path, usually falling a little short, then a
 * smaller corrective stroke if it is still off. Something moving can be followed smoothly instead. Looking about is a
 * glance from one thing to another, held a while on each.
 *
 * <p>The keys: going somewhere is W (or whichever keys take them there from where they are looking), sprinting with the
 * odd jump in it, a jump when walked into a step; standing about is now and then a step to one side, a hop or a crouch,
 * each a whole movement rather than a twitch.
 *
 * <p>Clicks are the game's own mouse buttons, on whatever is under the crosshair.
 *
 * <p>Everything random is drawn from one seeded generator, so the same take plays the same way every time.
 */
final class FilmHuman {
	enum Pace { SNEAK, WALK, SPRINT }

	private final Random random;
	private int clock;

	// The mouse: where the view is, the stroke under way, and the pauses round strokes.
	private boolean started;
	private double yaw;
	private double pitch;
	private float lastYaw;
	private float lastPitch;
	// The look a tick ago and now, for the frames in between, and the look a frame was last given.
	private double fromTickYaw;
	private double fromTickPitch;
	private float frameYaw = Float.NaN;
	private float framePitch;
	private int strokeAt = -1;
	private int strokeLength;
	private double fromYaw;
	private double fromPitch;
	private double toYaw;
	private double toPitch;
	private double bulge;
	private int restUntil;
	private int reactAt;
	@Nullable
	private double[] lastWant;

	// What they mean to look at.
	@Nullable
	private Vec3d gaze;
	private double gazeYaw = Double.NaN;
	private double gazePitch;
	private double slack = 2.5;
	private boolean track;
	@Nullable
	private String glanceKey;
	private int glanceIndex = -1;
	private int glanceUntil = -1;

	// The feet and the right mouse button.
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
	private boolean useHeld;
	/** A or D held this tick (-1, 1; 0 neither), as when keeping to the middle of a bridge. */
	private int strafe;
	private double idle;
	private int nextIdle = 40;
	private int idleUntil = -1;
	private int idleKind;
	private int stuckFor;
	private int sidestepUntil = -1;
	private boolean sidestepLeft;

	FilmHuman(long seed) {
		random = new Random(seed);
	}

	// --- what they mean to do (said every tick by the script) --------------------------------

	/** The tick it is: first, every tick. Also lets go of anything held only while asked for. */
	FilmHuman at(int now) {
		clock = now;
		useHeld = false;
		strafe = 0;
		track = false;
		return this;
	}

	/** Look at {@code at} (null: leave the view where it is). */
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

	/**
	 * Look about among {@code points}, holding each {@code minHold} to {@code maxHold} ticks before glancing to another.
	 * {@code key} names the set: a new one starts again from its first point.
	 */
	FilmHuman glance(String key, int minHold, int maxHold, Vec3d... points) {
		if (!key.equals(glanceKey)) {
			glanceKey = key;
			glanceIndex = 0;
			glanceUntil = clock + minHold + random.nextInt(Math.max(1, maxHold - minHold + 1));
		} else if (clock >= glanceUntil && points.length > 1) {
			int next = random.nextInt(points.length - 1);
			glanceIndex = next >= glanceIndex ? next + 1 : next;
			glanceUntil = clock + minHold + random.nextInt(Math.max(1, maxHold - minHold + 1));
		}
		return look(points[Math.min(glanceIndex, points.length - 1)]);
	}

	/** How far (degrees) the view may be off what they look at before the hand moves to put it right. */
	FilmHuman slack(double degrees) {
		this.slack = degrees;
		return this;
	}

	/** Follow what they look at smoothly (something moving), rather than in strokes. */
	FilmHuman track() {
		track = true;
		return this;
	}

	/** Go to {@code to} at {@code pace} (null: stay), stopping within {@code within}; sprinting, a jump now and then if {@code hop}. */
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

	/** Hold the right mouse button this tick (as when bridging: a block goes wherever the crosshair finds a face). */
	FilmHuman use() {
		useHeld = true;
		return this;
	}

	/** Hold A ({@code -1}) or D ({@code 1}) this tick. */
	FilmHuman strafe(int side) {
		strafe = side;
		return this;
	}

	/** Standing about: how restless (0: still; 1: a step, hop or crouch every few seconds; 2: twice as often). */
	FilmHuman idle(double level) {
		idle = level;
		return this;
	}

	// --- what the player can tell -------------------------------------------------------------

	static boolean near(ClientPlayerEntity player, Vec3d at, double within) {
		return Math.hypot(at.x - player.getX(), at.z - player.getZ()) <= within;
	}

	/** How far (degrees) the view is from {@code at}. */
	static double off(ClientPlayerEntity player, Vec3d at) {
		double[] a = angles(player.getEyePos(), at);
		return Math.hypot(MathHelper.wrapDegrees(a[0] - player.getYaw()), a[1] - player.getPitch());
	}

	/** The crosshair is on block {@code pos} (on its {@code side}, if not null). */
	static boolean on(MinecraftClient client, BlockPos pos, @Nullable Direction side) {
		return client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)
				&& (side == null || hit.getSide() == side);
	}

	/** A left click: on whatever is under the crosshair. */
	static void leftClick(MinecraftClient client) {
		((FilmClickInvoker) client).shootingstar$leftClick();
	}

	/** A right click: on whatever is under the crosshair, or the item in hand used. */
	static void rightClick(MinecraftClient client) {
		((FilmClickInvoker) client).shootingstar$rightClick();
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
		o.useKey.setPressed(false);
	}

	static double[] angles(Vec3d eye, Vec3d at) {
		Vec3d d = at.subtract(eye);
		double yaw = MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN - 90.0;
		double pitch = -(MathHelper.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * MathHelper.DEGREES_PER_RADIAN);
		return new double[] {yaw, MathHelper.clamp(pitch, -89.0, 89.0)};
	}

	// --- doing it -----------------------------------------------------------------------------

	/** Moves the mouse and presses the keys for this tick. */
	void tick(MinecraftClient client, ClientPlayerEntity player) {
		mouse(player);
		feet(client, player);
	}

	private void mouse(ClientPlayerEntity player) {
		int now = clock;
		fromTickYaw = yaw;
		fromTickPitch = pitch;
		// Turned by something else since last tick (carried somewhere, the event giving the view back): start from there.
		if (!started || Math.abs(MathHelper.wrapDegrees(player.getYaw() - lastYaw)) > 0.5 || Math.abs(player.getPitch() - lastPitch) > 0.5) {
			started = true;
			yaw = player.getYaw();
			pitch = player.getPitch();
			fromTickYaw = yaw;
			fromTickPitch = pitch;
			strokeAt = -1;
			restUntil = now + 4;
			lastWant = null;
		}
		double[] want = null;
		if (gaze != null) {
			want = angles(player.getEyePos(), gaze);
		} else if (!Double.isNaN(gazeYaw)) {
			want = new double[] {gazeYaw, MathHelper.clamp(gazePitch, -89.0, 89.0)};
		}
		if (want != null) {
			// Something new to look at: a moment to see it before the hand moves.
			if (lastWant != null && Math.hypot(MathHelper.wrapDegrees(want[0] - lastWant[0]), want[1] - lastWant[1]) > 8.0) {
				reactAt = now + 2 + random.nextInt(4);
			}
			lastWant = want;
		}
		if (strokeAt >= 0) {
			double t = Math.min(1.0, (now - strokeAt + 1) / (double) strokeLength);
			double k = t * t * t * (10.0 - 15.0 * t + 6.0 * t * t);
			double dy = toYaw - fromYaw;
			double dp = toPitch - fromPitch;
			double len = Math.max(1.0E-6, Math.hypot(dy, dp));
			double side = bulge * Math.sin(Math.PI * t);
			yaw = fromYaw + dy * k - dp / len * side;
			pitch = fromPitch + dp * k + dy / len * side;
			if (t >= 1.0) {
				strokeAt = -1;
				restUntil = now + 1 + random.nextInt(2);
			}
		} else if (want != null && now >= restUntil && now >= reactAt) {
			double dy = MathHelper.wrapDegrees(want[0] - yaw);
			double dp = want[1] - pitch;
			double err = Math.hypot(dy, dp);
			if (track && err > 0.4 && err < 12.0) {
				// Following something moving: smoothly, a little behind it.
				yaw += dy * 0.3;
				pitch += dp * 0.3;
			} else if (err > slack) {
				// A stroke: the first, long one usually a little short; a correction about right.
				double gain = err > 3.0 * Math.max(slack, 1.0) ? 0.9 + random.nextGaussian() * 0.05 : 1.0 + random.nextGaussian() * 0.06;
				fromYaw = yaw;
				fromPitch = pitch;
				toYaw = yaw + dy * gain;
				toPitch = MathHelper.clamp(pitch + dp * gain, -89.5, 89.5);
				bulge = MathHelper.clamp(random.nextGaussian() * 0.06 * err, -0.15 * err, 0.15 * err);
				// A hand on a mouse in a recorded game: a third of a second for a few degrees, most of a second to turn round.
				strokeLength = Math.max(3, (int) Math.round(3.0 + 2.6 * (Math.log1p(err / 3.0) / Math.log(2.0)) + random.nextGaussian() * 0.5));
				strokeAt = now;
				double t = 1.0 / strokeLength;
				double k = t * t * t * (10.0 - 15.0 * t + 6.0 * t * t);
				yaw = fromYaw + (toYaw - fromYaw) * k;
				pitch = fromPitch + (toPitch - fromPitch) * k;
			}
		}
		pitch = MathHelper.clamp(pitch, -90.0, 90.0);
		lastYaw = (float) yaw;
		lastPitch = (float) pitch;
		player.setYaw(lastYaw);
		player.setPitch(lastPitch);
	}

	/**
	 * A frame {@code tickDelta} of the way from the last tick to the next: the look on its way from where it was a tick
	 * ago to where it is now, as a mouse moves it every frame (the game draws its own player's look as it is).
	 */
	void frame(ClientPlayerEntity player, float tickDelta) {
		if (!started) {
			return;
		}
		frameYaw = (float) (fromTickYaw + (yaw - fromTickYaw) * tickDelta);
		framePitch = (float) (fromTickPitch + (pitch - fromTickPitch) * tickDelta);
		player.setYaw(frameYaw);
		player.setPitch(framePitch);
	}

	/** At the start of a tick: the look the tick left, unless something else has turned the player since the last frame. */
	void unframe(ClientPlayerEntity player) {
		if (!Float.isNaN(frameYaw) && player.getYaw() == frameYaw && player.getPitch() == framePitch) {
			player.setYaw(lastYaw);
			player.setPitch(lastPitch);
		}
		frameYaw = Float.NaN;
	}

	private void feet(MinecraftClient client, ClientPlayerEntity player) {
		int now = clock;
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
				double want = MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN - 90.0;
				double rel = MathHelper.wrapDegrees(want - player.getYaw());
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
				// Walked into a step: up it; still stuck, round it.
				boolean slow = player.getVelocity().horizontalLengthSquared() < 0.0004;
				stuckFor = slow || player.horizontalCollision ? stuckFor + 1 : 0;
				if (stuckFor > 3 && player.isOnGround()) {
					jump = true;
				}
				if (stuckFor > 14 && sidestepUntil < now) {
					sidestepUntil = now + 8 + random.nextInt(6);
					sidestepLeft = random.nextBoolean();
					stuckFor = 0;
				}
				if (now < sidestepUntil) {
					a = sidestepLeft;
					d = !sidestepLeft;
				}
			} else {
				stuckFor = 0;
			}
		} else if (idle > 0.0 && !back && !sneak) {
			if (now >= nextIdle) {
				idleKind = random.nextInt(10);
				idleUntil = now + switch (idleKind) {
					case 0, 1, 2, 3 -> 4 + random.nextInt(6);
					case 4, 5 -> 5 + random.nextInt(7);
					case 6 -> 1;
					default -> 7;
				};
				nextIdle = now + (int) ((50 + random.nextInt(80)) / idle);
			}
			if (now < idleUntil) {
				switch (idleKind) {
					case 0, 1 -> a = true;
					case 2, 3 -> d = true;
					case 4 -> w = true;
					case 5 -> s = true;
					case 6 -> jump = player.isOnGround();
					case 7 -> {
						w = true;
						jump = player.isOnGround();
					}
					default -> crouch = (idleUntil - now) % 4 < 2;
				}
			}
		}
		if (strafe != 0) {
			a = strafe < 0;
			d = strafe > 0;
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
		o.useKey.setPressed(useHeld);
		if (sprint) {
			player.setSprinting(true);
		} else if (!w) {
			player.setSprinting(false);
		}
	}
}
