package io.github.kortev.chainsawman.client.devil;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * One Tomato Devil as this client draws it: its eight hands' steps, the way it faces, how far it has rolled, and the cuts on it.
 *
 * <p>The walk is a gait of alternating fours: the arms are two groups (every other arm round the body), and one group swings while the
 * other stands. A hand that stands is planted in the world and does not move until its group's turn; a group lifts when its hands have
 * fallen far enough behind where they would rest (more when it walks faster), and comes down ahead of the rest by as much, so each hand
 * stands as long as the other group swings. While a group swings the body sags a little onto the four that hold it.
 */
final class TomatoBody {
	/** The arms: where round the body (from the front, clockwise seen from above, degrees), and their two groups. */
	static final int ARMS = 8;
	static final float[] AZ = new float[ARMS];

	static {
		for (int i = 0; i < ARMS; i++) {
			AZ[i] = 22.5F + 45.0F * i;
		}
	}

	/** How far out from its middle a hand rests (blocks). */
	static final double HAND_R = 2.05;
	/** How long a swing takes walking, and standing about (ticks). */
	static final float SWING = 6.0F;
	static final float SWING_SLOW = 8.0F;
	/** How high a hand is lifted at the top of its swing. */
	static final double LIFT = 0.55;

	static final class Foot {
		Vec3d plant;
		/** The way its fingers point (radians, as atan2(x, z)). */
		double yaw;
		boolean swinging;
		Vec3d from;
		Vec3d to;
		double fromYaw;
		double toYaw;
		float start;
		float dur;
		/** Where it was last drawn (the world), and how. */
		Vec3d drawn;
		double drawnYaw;
		Vec3d groundAt;
		double groundY;
		/** When it last came down (its fingers press into the ground one after another from then). */
		float landAt = -100.0F;
	}

	/** A cut: where on the body (its own frame), the surface's normal, its turn and length, when it was made. */
	record Wound(float x, float y, float z, float nx, float ny, float nz, float angle, float length, float at) {
	}

	final Foot[] feet = new Foot[ARMS];
	final List<Wound> wounds = new ArrayList<>();
	boolean ready;
	float yaw;
	float lastNow = Float.NaN;
	Vec3d lastPos;
	Vec3d vel = Vec3d.ZERO;
	int lastGroup = 1;
	double rolled;
	int lastState = -1;
	int lastHurt;
	/** Each eye's next blink (ticks), set as it goes. */
	float[] blinkAt = new float[0];
	/** The body's sag onto the four that stand (blocks down). */
	float sag;

	TomatoBody() {
		for (int i = 0; i < ARMS; i++) {
			feet[i] = new Foot();
		}
	}

	static int group(int arm) {
		return arm % 2;
	}

	/** Where arm {@code i}'s hand rests (the world), the body at {@code pos} facing {@code yawDeg}, ahead by {@code lead}. */
	static Vec3d home(Vec3d pos, float yawDeg, int i, Vec3d lead) {
		double a = Math.toRadians(-yawDeg) + Math.toRadians(AZ[i]);
		// body frame: +Z forward; turned by -yaw about Y: (sin a, cos a) of the arm's azimuth in the world
		double x = Math.sin(a) * HAND_R;
		double z = Math.cos(a) * HAND_R;
		return new Vec3d(pos.x + x + lead.x, pos.y, pos.z + z + lead.z);
	}

	/** The ground under a point: the top of what is there within a couple of blocks up or down. */
	static double ground(ClientWorld world, Vec3d at, double near) {
		if (world == null || !Double.isFinite(at.x) || !Double.isFinite(at.z) || !Double.isFinite(near)) {
			return near;
		}
		Vec3d from = new Vec3d(at.x, near + 2.2, at.z);
		Vec3d to = new Vec3d(at.x, near - 3.0, at.z);
		BlockHitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
				net.minecraft.block.ShapeContext.absent()));
		return hit.getType() == HitResult.Type.MISS ? near : hit.getPos().y;
	}

	private double groundCached(ClientWorld world, Foot f, Vec3d at, double near) {
		if (f.groundAt == null || f.groundAt.squaredDistanceTo(at.x, f.groundAt.y, at.z) > 0.04) {
			f.groundAt = at;
			f.groundY = ground(world, at, near);
		}
		return f.groundY;
	}

	/** Every hand planted where it rests (a new devil, or one that was out of sight). */
	void reset(ClientWorld world, Vec3d pos, float yawDeg) {
		yaw = yawDeg;
		for (int i = 0; i < ARMS; i++) {
			Foot f = feet[i];
			Vec3d h = home(pos, yawDeg, i, Vec3d.ZERO);
			f.plant = new Vec3d(h.x, ground(world, h, pos.y), h.z);
			f.yaw = Math.atan2(h.x - pos.x, h.z - pos.z);
			f.swinging = false;
			f.drawn = f.plant;
			f.drawnYaw = f.yaw;
		}
		ready = true;
	}

	/** Plants every hand where it was last drawn (coming back to the walk from a move that posed the arms). */
	void plantWhereDrawn(ClientWorld world, Vec3d pos) {
		for (Foot f : feet) {
			if (f.drawn != null) {
				f.plant = new Vec3d(f.drawn.x, ground(world, f.drawn, pos.y), f.drawn.z);
				f.yaw = f.drawnYaw;
			}
			f.swinging = false;
		}
	}

	/**
	 * Moves the steps on to {@code now} (ticks, with the frame's part): the body at {@code pos} facing {@code yawDeg}; {@code walking}
	 * false holds every hand where it is (a move).
	 */
	void step(ClientWorld world, TomatoDevilEntity e, Vec3d pos, float yawDeg, float now, boolean walking) {
		float dt = Float.isNaN(lastNow) ? 0.0F : now - lastNow;
		if (dt < 0.0F || dt > 10.0F || lastPos == null) {
			dt = 0.0F;
			lastPos = pos;
		}
		if (dt > 0.0F) {
			Vec3d v = pos.subtract(lastPos).multiply(1.0 / dt);
			v = new Vec3d(v.x, 0.0, v.z);
			double k = Math.min(1.0, dt * 0.35);
			vel = vel.add(v.subtract(vel).multiply(k));
			rolled += Math.hypot(pos.x - lastPos.x, pos.z - lastPos.z);
		}
		lastNow = now;
		lastPos = pos;
		double speed = vel.length();
		float swing = speed > 0.04 ? SWING : SWING_SLOW;
		// The swings under way.
		boolean anySwinging = false;
		float sagNow = 0.0F;
		for (int i = 0; i < ARMS; i++) {
			Foot f = feet[i];
			if (!f.swinging) {
				continue;
			}
			float s = (now - f.start) / f.dur;
			if (s >= 1.0F) {
				f.swinging = false;
				f.plant = f.to;
				f.yaw = f.toYaw;
				f.landAt = now;
				land(world, e, f.plant, speed);
			} else {
				anySwinging = true;
				sagNow = Math.max(sagNow, MathHelper.sin(s * MathHelper.PI));
			}
		}
		sag = sagNow;
		if (!walking || anySwinging) {
			return;
		}
		// Which group has fallen furthest behind where it would rest, and whether it is far enough to lift.
		double[] err = new double[2];
		Vec3d lead = vel.multiply(swing * 1.5);
		for (int i = 0; i < ARMS; i++) {
			Vec3d h = home(pos, yawDeg, i, Vec3d.ZERO);
			Foot f = feet[i];
			double d = Math.hypot(f.plant.x - h.x, f.plant.z - h.z);
			err[group(i)] = Math.max(err[group(i)], d);
		}
		double need = Math.max(0.28, speed * swing * 0.5);
		int g = err[1 - lastGroup] >= need * 0.7 ? 1 - lastGroup : (err[lastGroup] > err[1 - lastGroup] * 1.6 ? lastGroup : 1 - lastGroup);
		if (err[g] < need) {
			return;
		}
		lastGroup = g;
		for (int i = 0; i < ARMS; i++) {
			if (group(i) != g) {
				continue;
			}
			Foot f = feet[i];
			Vec3d h = home(pos, yawDeg, i, lead);
			double gy = groundCached(world, f, h, pos.y);
			f.from = f.plant;
			f.to = new Vec3d(h.x, gy, h.z);
			f.fromYaw = f.yaw;
			f.toYaw = Math.atan2(h.x - (pos.x + lead.x), h.z - (pos.z + lead.z));
			f.start = now;
			f.dur = swing * (0.92F + 0.16F * ((i * 37) % 5) / 4.0F);
			f.swinging = true;
		}
	}

	/** A hand coming down: its slap, a puff of what it lands on. */
	private void land(ClientWorld world, TomatoDevilEntity e, Vec3d at, double speed) {
		world.playSound(at.x, at.y, at.z, Devils.Sounds.STEP, SoundCategory.HOSTILE, 0.35F + (float) Math.min(0.4, speed * 2.0),
				0.85F + world.random.nextFloat() * 0.3F, false);
		BlockPos below = BlockPos.ofFloored(at.x, at.y - 0.05, at.z);
		var state = world.getBlockState(below);
		if (!state.isAir()) {
			for (int k = 0; k < 3; k++) {
				world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), at.x + (world.random.nextDouble() - 0.5) * 0.4, at.y + 0.05,
						at.z + (world.random.nextDouble() - 0.5) * 0.4, 0.0, 0.05, 0.0);
			}
		}
	}

	/** Where arm {@code i}'s wrist is in the walk at {@code now}, and the hand's yaw, pitch (its fingers dropping as it swings) and curl. */
	double[] walkHand(int i, float now) {
		Foot f = feet[i];
		if (!f.swinging) {
			return new double[] {f.plant.x, f.plant.y, f.plant.z, f.yaw, 0.0, 0.1};
		}
		float s = MathHelper.clamp((now - f.start) / f.dur, 0.0F, 1.0F);
		// Out fast, down gently: the hand leaves the ground and reaches, then settles flat.
		float e = s * s * (3.0F - 2.0F * s);
		double x = MathHelper.lerp(e, f.from.x, f.to.x);
		double z = MathHelper.lerp(e, f.from.z, f.to.z);
		double y = MathHelper.lerp(e, f.from.y, f.to.y) + LIFT * Math.sin(Math.PI * Math.pow(s, 0.8));
		double yaw = f.fromYaw + MathHelper.wrapDegrees((float) Math.toDegrees(f.toYaw - f.fromYaw)) * Math.PI / 180.0 * e;
		// The wrist lets the hand hang as it lifts (fingers down and curled), then opens it flat to land.
		double pitch = 1.1 * Math.sin(Math.PI * Math.min(1.0, s * 1.15));
		double curl = 0.1 + 0.9 * Math.sin(Math.PI * Math.min(1.0, s * 1.1));
		return new double[] {x, y, z, yaw, pitch, curl};
	}

	// ---- The hands' fingers: they land and press one after another; standing, a few hands feel about, tap and flex.

	/** Whether arm {@code i} is one that fidgets when it stands still. */
	static boolean fidgets(int i, int id) {
		return Math.floorMod(i * 5 + id, 3) == 0;
	}

	/** How far through a fidget arm {@code i} is at {@code now}: its kind (0 tap, 1 flex, 2 feel about) and the phase 0..1, or null. */
	static float[] fidget(int i, int id, float now) {
		int period = 70 + Math.floorMod(i * 37 + id * 11, 50);
		float c = (now + Math.floorMod(i * 131 + id * 7, period)) % period;
		float len = 26.0F;
		if (c > len) {
			return null;
		}
		int cycle = (int) Math.floor((now + Math.floorMod(i * 131 + id * 7, period)) / period);
		return new float[] {Math.floorMod(cycle * 7 + i, 3), c / len};
	}

	private static float bump(float x) {
		return x <= 0.0F || x >= 1.0F ? 0.0F : MathHelper.sin(x * MathHelper.PI);
	}

	/**
	 * Finger {@code j} (0 the index, by the thumb, to 3 the little one) of arm {@code i} at {@code now}: its curl (radians: up is
	 * negative, pressed into the ground) and how far the fingers spread (0..1). {@code still} when it stands about.
	 */
	float[] finger(int i, int j, float now, int id, boolean still) {
		Foot f = feet[i];
		if (f.swinging) {
			float s = MathHelper.clamp((now - f.start) / f.dur, 0.0F, 1.0F);
			float c = 0.1F + 0.9F * MathHelper.sin(MathHelper.PI * Math.min(1.0F, s * 1.1F));
			// Coming down, the fingers stay up and a little curled, waiting to touch.
			if (s > 0.55F) {
				c = Math.max(c, 0.45F - 0.1F * j);
			}
			return new float[] {c, 0.4F};
		}
		float dt = now - f.landAt - j * 1.4F;
		if (dt < 0.0F && now - f.landAt < 8.0F) {
			return new float[] {0.45F - 0.1F * j, 0.55F};
		}
		if (dt >= 0.0F && dt < 2.0F) {
			float k = dt / 2.0F;
			return new float[] {MathHelper.lerp(k * k, 0.45F - 0.1F * j, -0.2F), MathHelper.lerp(k, 0.55F, 1.0F)};
		}
		if (dt >= 2.0F && dt < 10.0F) {
			float k = (dt - 2.0F) / 8.0F;
			return new float[] {MathHelper.lerp(k, -0.2F, 0.04F), MathHelper.lerp(k, 1.0F, 0.6F)};
		}
		float curl = 0.04F;
		float spread = 0.6F;
		if (still && fidgets(i, id)) {
			float[] fg = fidget(i, id, now);
			if (fg != null) {
				float ph = fg[1];
				switch ((int) fg[0]) {
					case 0 -> curl -= 0.55F * bump((ph - j * 0.12F) * 3.0F) - 0.25F * bump((ph - 0.5F - j * 0.08F) * 3.0F);
					case 1 -> {
						curl += 1.1F * bump(ph * 1.2F) * (1.0F - j * 0.08F);
						spread -= 0.5F * bump(ph * 1.2F);
					}
					default -> curl += 0.35F * bump(ph) + 0.15F * MathHelper.sin(ph * 18.0F + j);
				}
			}
		}
		return new float[] {curl, spread};
	}

	/** A standing hand lifting off the ground as it feels about (blocks up, and to the side). */
	float[] feel(int i, float now, int id, boolean still) {
		if (!still || feet[i].swinging || !fidgets(i, id)) {
			return new float[] {0.0F, 0.0F};
		}
		float[] fg = fidget(i, id, now);
		if (fg == null) {
			return new float[] {0.0F, 0.0F};
		}
		float ph = fg[1];
		if ((int) fg[0] == 2) {
			return new float[] {0.22F * bump(ph), 0.25F * MathHelper.sin(ph * MathHelper.TAU)};
		}
		return new float[] {(int) fg[0] == 0 ? 0.06F * bump(ph) : 0.0F, 0.0F};
	}

	// ---- The eyes: each wanders on its own till it notices someone; then every one snaps to them.

	float[][] gaze = new float[0][];
	float[] nextLook = new float[0];
	float[][] lookTo = new float[0][];
	float lastEyes = Float.NaN;
	float pupil = 1.0F;

	/** Each eye's blink at {@code now}: 1 open, toward 0 shut. */
	float blink(int eye, float now, long seed) {
		if (blinkAt.length <= eye) {
			float[] b = new float[eye + 8];
			System.arraycopy(blinkAt, 0, b, 0, blinkAt.length);
			for (int k = blinkAt.length; k < b.length; k++) {
				b[k] = now + 10.0F + (float) Math.floorMod(seed * 31 + k * 977L, 120L);
			}
			blinkAt = b;
		}
		float d = now - blinkAt[eye];
		if (d > 6.0F) {
			blinkAt[eye] = now + 50.0F + (float) Math.floorMod((long) (now * 13) + eye * 7919L, 150L);
			return 1.0F;
		}
		if (d < 0.0F) {
			return 1.0F;
		}
		return 1.0F - MathHelper.sin(d / 6.0F * MathHelper.PI) * 0.92F;
	}
}
