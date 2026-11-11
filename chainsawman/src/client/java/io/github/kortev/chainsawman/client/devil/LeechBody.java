package io.github.kortev.chainsawman.client.devil;

import io.github.kortev.chainsawman.devil.LeechDevilEntity;
import io.github.kortev.chainsawman.devil.LeechSounds;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * One Leech Devil as this client draws it: its four sucker feet's steps, the way it faces, the way its eyeless face turns after you (by
 * sound: late), and the sucker it lost when an arm was cut loose.
 *
 * <p>The walk is a trot: the legs go in diagonal pairs (front left with back right, front right with back left), one pair swinging while
 * the other stands. A standing foot is planted in the world (its sucker shut on the ground) until its pair's turn; a pair lifts when its
 * feet have fallen far enough behind where they would rest, and comes down as far ahead.
 */
final class LeechBody {
	static final int LEGS = 4;
	/** Where each foot rests (the body's frame: +X its left, +Z its front), from tools/leech_devil_3d.py FEET. */
	static final double[][] FEET = {{1.25, 1.40}, {-1.25, 1.40}, {1.35, -1.85}, {-1.35, -1.85}};
	static final float SWING = 5.0F;
	static final float SWING_SLOW = 8.0F;
	static final double LIFT = 0.5;

	static final class Foot {
		Vec3d plant;
		boolean swinging;
		Vec3d from;
		Vec3d to;
		float start;
		float dur;
		Vec3d drawn;
		Vec3d groundAt;
		double groundY;
		float landAt = -100.0F;
	}

	final Foot[] feet = new Foot[LEGS];
	boolean ready;
	float yaw;
	float lastNow = Float.NaN;
	Vec3d lastPos;
	Vec3d vel = Vec3d.ZERO;
	int lastGroup = 1;
	int lastState = -1;
	/** The face's turn from the body (degrees) and where it is turning to (heard, so a little late). */
	float headYaw;
	float headPitch;
	float headWant;
	float headPitchWant;
	float heardAt = -100.0F;
	/** Where the arm last drank (the world), so the sucker that tears off falls from there. */
	Vec3d lastStuckAt;
	/** The sucker torn off: where it fell from and when (NaN: none). */
	Vec3d dropFrom;
	float dropAt = Float.NaN;
	double dropGround;
	/** The next time it tastes the air with its tongue. */
	float tasteAt = 60.0F;

	LeechBody() {
		for (int i = 0; i < LEGS; i++) {
			feet[i] = new Foot();
		}
	}

	static int group(int leg) {
		return leg == 0 || leg == 3 ? 0 : 1;
	}

	/** A point of the body's frame on the ground, turned by {@code yawDeg}, from {@code pos}. */
	static Vec3d onGround(Vec3d pos, float yawDeg, double x, double z, Vec3d lead) {
		double r = Math.toRadians(yawDeg);
		double c = Math.cos(r);
		double s = Math.sin(r);
		return new Vec3d(pos.x + x * c - z * s + lead.x, pos.y, pos.z + x * s + z * c + lead.z);
	}

	static Vec3d home(Vec3d pos, float yawDeg, int i, Vec3d lead) {
		return onGround(pos, yawDeg, FEET[i][0], FEET[i][1], lead);
	}

	/** The ground under a point: the top of what is there within a couple of blocks up or down. */
	static double ground(ClientWorld world, Vec3d at, double near) {
		if (world == null || !Double.isFinite(at.x) || !Double.isFinite(at.z) || !Double.isFinite(near)) {
			return near;
		}
		net.minecraft.util.hit.BlockHitResult hit = world.raycast(new net.minecraft.world.RaycastContext(new Vec3d(at.x, near + 2.2, at.z), new Vec3d(at.x, near - 3.0, at.z),
				net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, net.minecraft.block.ShapeContext.absent()));
		return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? near : hit.getPos().y;
	}

	private double groundCached(ClientWorld world, Foot f, Vec3d at, double near) {
		if (f.groundAt == null || f.groundAt.squaredDistanceTo(at.x, f.groundAt.y, at.z) > 0.04) {
			f.groundAt = at;
			f.groundY = ground(world, at, near);
		}
		return f.groundY;
	}

	void reset(ClientWorld world, Vec3d pos, float yawDeg) {
		yaw = yawDeg;
		for (int i = 0; i < LEGS; i++) {
			Foot f = feet[i];
			Vec3d h = home(pos, yawDeg, i, Vec3d.ZERO);
			f.plant = new Vec3d(h.x, ground(world, h, pos.y), h.z);
			f.swinging = false;
			f.drawn = f.plant;
		}
		ready = true;
	}

	void plantWhereDrawn(ClientWorld world, Vec3d pos) {
		for (Foot f : feet) {
			if (f.drawn != null) {
				f.plant = new Vec3d(f.drawn.x, ground(world, f.drawn, pos.y), f.drawn.z);
			}
			f.swinging = false;
		}
	}

	void step(ClientWorld world, LeechDevilEntity e, Vec3d pos, float yawDeg, float now, boolean walking) {
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
		}
		lastNow = now;
		lastPos = pos;
		double speed = vel.length();
		float swing = speed > 0.04 ? SWING : SWING_SLOW;
		boolean anySwinging = false;
		for (int i = 0; i < LEGS; i++) {
			Foot f = feet[i];
			if (!f.swinging) {
				continue;
			}
			float s = (now - f.start) / f.dur;
			if (s >= 1.0F) {
				f.swinging = false;
				f.plant = f.to;
				f.landAt = now;
				land(world, f.plant, speed);
			} else {
				anySwinging = true;
			}
		}
		if (!walking || anySwinging) {
			return;
		}
		double[] err = new double[2];
		Vec3d lead = vel.multiply(swing * 1.5);
		for (int i = 0; i < LEGS; i++) {
			Vec3d h = home(pos, yawDeg, i, Vec3d.ZERO);
			Foot f = feet[i];
			err[group(i)] = Math.max(err[group(i)], Math.hypot(f.plant.x - h.x, f.plant.z - h.z));
		}
		double need = Math.max(0.3, speed * swing * 0.5);
		int g = err[1 - lastGroup] >= need * 0.7 ? 1 - lastGroup : (err[lastGroup] > err[1 - lastGroup] * 1.6 ? lastGroup : 1 - lastGroup);
		if (err[g] < need) {
			return;
		}
		lastGroup = g;
		for (int i = 0; i < LEGS; i++) {
			if (group(i) != g) {
				continue;
			}
			Foot f = feet[i];
			Vec3d h = home(pos, yawDeg, i, lead);
			f.from = f.plant;
			f.to = new Vec3d(h.x, groundCached(world, f, h, pos.y), h.z);
			f.start = now;
			f.dur = swing * (0.92F + 0.16F * ((i * 37) % 5) / 4.0F);
			f.swinging = true;
		}
	}

	private void land(ClientWorld world, Vec3d at, double speed) {
		world.playSound(at.x, at.y, at.z, LeechSounds.STEP, SoundCategory.HOSTILE, 0.4F + (float) Math.min(0.5, speed * 2.0), 0.8F + world.random.nextFloat() * 0.3F,
				false);
		BlockPos below = BlockPos.ofFloored(at.x, at.y - 0.05, at.z);
		var state = world.getBlockState(below);
		if (!state.isAir()) {
			for (int k = 0; k < 3; k++) {
				world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), at.x + (world.random.nextDouble() - 0.5) * 0.4, at.y + 0.05,
						at.z + (world.random.nextDouble() - 0.5) * 0.4, 0.0, 0.05, 0.0);
			}
		}
	}

	/** Where foot {@code i} is at {@code now} (the world) and how far through its swing (-1 standing). */
	double[] foot(int i, float now) {
		Foot f = feet[i];
		if (!f.swinging) {
			return new double[] {f.plant.x, f.plant.y, f.plant.z, -1.0};
		}
		float s = MathHelper.clamp((now - f.start) / f.dur, 0.0F, 1.0F);
		float e = s * s * (3.0F - 2.0F * s);
		double x = MathHelper.lerp(e, f.from.x, f.to.x);
		double z = MathHelper.lerp(e, f.from.z, f.to.z);
		double y = MathHelper.lerp(e, f.from.y, f.to.y) + LIFT * Math.sin(Math.PI * Math.pow(s, 0.8));
		return new double[] {x, y, z, s};
	}
}
