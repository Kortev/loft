package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.BeamEntity;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.mixin.LivingEntityAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;

/**
 * The ult: Beam, the shark fiend, in his big shark form (from the Reze arc, where he carries Chainsaw Man on his back,
 * swimming through the ground and the streets as if they were water). In the form and able to fight, his key calls him up
 * for {@link Blood#BEAM} blood: he bursts up out of the ground under you in a spray of the ground's own blocks and you
 * are on his back. He lasts {@link #LASTS} ticks; one Beam to a player.
 *
 * <p>He swims through the ground, only his back and his fin above the surface: he goes the way you look (turning to it
 * at most {@link #TURN} degrees a tick), the forward key drives him on to {@link #SPEED} blocks a tick (the back key
 * brings him to a stop, the strafe keys nudge him aside), and he follows the ground's surface, smoothed, so he rises
 * over a step or a wall and sinks again as if he went through it. Every {@link #BITE_EVERY} ticks he bites whatever
 * living thing is within {@link #BITE_REACH} blocks of his mouth, for {@link #BITE_DAMAGE}, with <em>your</em> damage, so
 * what he kills is your kill (the saws' blood, healing and torn-apart bodies all follow). The jump key makes him leap
 * ({@link #LEAP_UP} blocks up, {@link #LEAP_LONG} forward, no more often than every {@link #LEAP_EVERY} ticks), crashing
 * down on whatever is within {@link #LEAP_REACH} blocks of where he lands for {@link #LEAP_DAMAGE}.
 *
 * <p>It ends when the time is up, when you sneak to get off, or if you leave the form or go down: he dives, sinking out
 * of sight over {@link #DIVE_TICKS} ticks, and you are left standing on the surface, unhurt by the drop. While you ride
 * the lunge and the chain are not yours to use (the ground goes by under you faster than either could aim). The
 * keys reach the server through the game's own input for a rider; the entity is {@link BeamEntity}, and drawn by the
 * client's {@code BeamRenderer}.
 */
public final class Beam {
	/** How long he stays (ticks): 20 seconds. */
	public static final int LASTS = 20 * 20;
	/** His top speed (blocks a tick: a fast horse's pace), how fast he picks it up and brakes (blocks a tick, each tick). */
	public static final double SPEED = 0.55;
	private static final double ACCELERATE = 0.05;
	private static final double BRAKE = 0.08;
	/** How fast the strafe keys move him sideways (blocks a tick), and how fast he turns to where you look (degrees a tick). */
	private static final double STRAFE = 0.22;
	public static final float TURN = 14.0F;
	/** The bite: how often (ticks), how hard, and how far from his mouth it reaches (blocks); the mouth is this far ahead of his middle. */
	public static final int BITE_EVERY = 8;
	public static final float BITE_DAMAGE = 12.0F;
	public static final double BITE_REACH = 2.2;
	public static final double MOUTH = 1.9;
	/** The leap: no more often than this (ticks), how long it takes, how high and far, the crash's reach (blocks) and bite. */
	public static final int LEAP_EVERY = 40;
	public static final int LEAP_TICKS = 14;
	public static final double LEAP_UP = 2.5;
	public static final double LEAP_LONG = 6.0;
	public static final double LEAP_REACH = 3.0;
	public static final float LEAP_DAMAGE = 14.0F;
	/** The dive: how long he takes to sink out of sight (ticks) and how deep he goes (blocks). */
	public static final int DIVE_TICKS = 15;
	private static final double DIVE_DEPTH = 1.6;
	/** Bursting up out of the ground: for this many ticks he and you are thrown up this high (blocks). */
	public static final int POP_TICKS = 10;
	private static final double POP = 0.8;
	/** Following the ground: how much of the way to a higher surface he rises in a tick, how much of a lower he sinks, and the most; he never gets deeper than this under it (blocks). */
	private static final double RISE = 0.35;
	private static final double FALL = 0.3;
	private static final double MOST_SINK = 0.8;
	private static final double DEEPEST = 1.0;
	/** How fast his pitch settles back level after a leap (the fraction a tick). */
	private static final float LEVEL = 0.35F;

	private static final BlockStateParticleEffect DIRT = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState());

	/** Whose Beam is out (until he is gone). */
	private static final Map<UUID, BeamEntity> BEAMS = new ConcurrentHashMap<>();

	/** What a run of Beam is doing: the server's, kept with his entity. */
	public static final class Ride {
		/** His speed now (blocks a tick), and the surface he has settled to under him (smoothed). */
		double speed;
		double ground = Double.NaN;
		/** The leap in progress (ticks in, or -1), where it began and where it will land, and when the last one began. */
		int leap = -1;
		double leapFromX;
		double leapFromZ;
		double leapFromGround;
		double leapToX;
		double leapToZ;
		double leapToGround;
		long lastLeap = -1000L;
	}

	private Beam() {
	}

	public static void init() {
		// Pressed into a wall while he climbs it, or let down by the smoothing, a rider's head may be in the ground a moment:
		// that is not a harm to them.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(source.isOf(DamageTypes.IN_WALL) && entity.getVehicle() instanceof BeamEntity));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUuid()));
	}

	/** Beam called up by {@code player}: whether he came. Costs {@link Blood#BEAM}; only in the form and able to fight. */
	public static boolean summon(ServerPlayerEntity player) {
		if (!Form.fighting(player) || active(player) != null || player.hasVehicle()) {
			return false;
		}
		if (!Blood.spend(player, Blood.BEAM)) {
			return false;
		}
		ServerWorld world = player.getServerWorld();
		double ground = groundUnder(world, player);
		BeamEntity beam = new BeamEntity(ChainsawMan.BEAM_ENTITY, world);
		beam.refreshPositionAndAngles(player.getX(), ground, player.getZ(), player.getYaw(), 0.0F);
		beam.setOwner(player.getUuid());
		beam.ride().ground = ground;
		BEAMS.put(player.getUuid(), beam);
		world.spawnEntity(beam);
		player.startRiding(beam, true);
		player.fallDistance = 0.0F;
		world.playSound(null, beam.getX(), ground, beam.getZ(), ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 1.4F, 1.0F);
		burst(world, beam.getX(), ground, beam.getZ(), 70, 0.9, 0.45);
		return true;
	}

	/** {@code player}'s Beam, if he is out and not on his way down; otherwise null. */
	public static BeamEntity active(ServerPlayerEntity player) {
		BeamEntity beam = BEAMS.get(player.getUuid());
		return beam != null && beam.isAlive() && !beam.diving() ? beam : null;
	}

	/** Whether {@code player} is riding Beam. */
	public static boolean riding(Entity player) {
		return player.getVehicle() instanceof BeamEntity;
	}

	/** The surface under {@code player}, where he bursts up: their feet if they are on the ground, else the ground beneath them. */
	private static double groundUnder(ServerWorld world, ServerPlayerEntity player) {
		if (player.isOnGround()) {
			return player.getY();
		}
		Vec3d from = player.getPos().add(0.0, 0.1, 0.0);
		Vec3d to = from.add(0.0, -24.0, 0.0);
		var hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, player));
		if (hit.getType() != HitResult.Type.MISS) {
			return hit.getPos().y;
		}
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
	}

	/** Everything Beam does in a tick, on the server: his run, his bites and leaps, his dive. */
	public static void tick(BeamEntity beam) {
		if (!(beam.getWorld() instanceof ServerWorld world)) {
			return;
		}
		Ride ride = beam.ride();
		if (Double.isNaN(ride.ground)) {
			ride.ground = beam.getY();
		}
		if (beam.diving()) {
			diveTick(beam, world, ride);
			return;
		}
		ServerPlayerEntity rider = beam.rider();
		if (!stays(beam, rider)) {
			dive(beam, world, rider);
			return;
		}
		long now = world.getTime();
		// The way you look, and how hard you drive him.
		float yaw = beam.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(rider.getYaw() - beam.getYaw()), -TURN, TURN);
		beam.setYaw(yaw);
		Vec3d ahead = Vec3d.fromPolar(0.0F, yaw);
		Vec3d left = new Vec3d(ahead.z, 0.0, -ahead.x);
		if (ride.leap < 0 && ((LivingEntityAccessor) rider).chainsawman$jumping() && now - ride.lastLeap >= LEAP_EVERY) {
			startLeap(beam, world, ride, ahead, now);
		}
		if (ride.leap >= 0) {
			leapTick(beam, world, ride, rider, now);
		} else {
			double wanted = rider.forwardSpeed > 0.0F ? SPEED * Math.min(1.0F, rider.forwardSpeed) : 0.0;
			ride.speed += MathHelper.clamp(wanted - ride.speed, -BRAKE, ACCELERATE);
			Vec3d step = ahead.multiply(ride.speed).add(left.multiply(rider.sidewaysSpeed * STRAFE));
			double x = beam.getX() + step.x;
			double z = beam.getZ() + step.z;
			double top = surface(world, x, z);
			if (Double.isNaN(top)) {
				// Past the edge of what is loaded: he stops, rather than load it.
				ride.speed = 0.0;
				x = beam.getX();
				z = beam.getZ();
			} else {
				ride.ground = follow(ride.ground, top);
			}
			double pop = beam.age < POP_TICKS ? POP * Math.sin(Math.PI * beam.age / POP_TICKS) : 0.0;
			beam.setPosition(x, ride.ground + pop, z);
			beam.setPitch(beam.getPitch() * (1.0F - LEVEL));
			if (beam.age % BITE_EVERY == 0) {
				bite(beam, world, rider);
			}
		}
		rider.fallDistance = 0.0F;
	}

	/** Whether the run goes on: the rider is still on his back and in the form and able, and the time is not up. */
	private static boolean stays(BeamEntity beam, ServerPlayerEntity rider) {
		return rider != null && rider.isAlive() && !rider.isSpectator() && Form.fighting(rider) && !rider.isSneaking() && beam.age < LASTS;
	}

	/** The surface at the column of {@code x}, {@code z}: its top, or NaN if the ground there is not loaded. */
	private static double surface(ServerWorld world, double x, double z) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
			return Double.NaN;
		}
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
	}

	/** The surface he has settled to, {@code ground}, a tick on toward the new {@code top}: up quickly, down more slowly. */
	private static double follow(double ground, double top) {
		double next = top > ground ? ground + (top - ground) * RISE : ground + Math.max((top - ground) * FALL, -MOST_SINK);
		return Math.max(next, top - DEEPEST);
	}

	// The bite.

	/** The place of his mouth: ahead of his middle, at the height of his back. */
	public static Vec3d mouth(BeamEntity beam) {
		return beam.getPos().add(beam.heading().multiply(MOUTH)).add(0.0, BeamEntity.BACK * 0.5, 0.0);
	}

	/**
	 * What Beam can bite or crash on: the living things within {@code reach} of {@code from} (the nearest point of each),
	 * not the rider, not armour stands, spectators, or anyone downed. With {@code inFront}, only those whose nearest point
	 * is ahead of his middle.
	 */
	private static List<LivingEntity> within(BeamEntity beam, ServerWorld world, ServerPlayerEntity rider, Vec3d from, double reach, boolean inFront) {
		List<LivingEntity> out = new ArrayList<>();
		Vec3d ahead = beam.heading();
		for (Entity e : world.getOtherEntities(beam, new Box(from, from).expand(reach + 2.0),
				e -> e instanceof LivingEntity l && l.isAlive() && e != rider && !e.isSpectator() && !(e instanceof ArmorStandEntity) && !e.hasPassenger(beam))) {
			LivingEntity living = (LivingEntity) e;
			if (living instanceof ServerPlayerEntity other && Downed.is(other)) {
				continue;
			}
			Box b = living.getBoundingBox();
			Vec3d near = new Vec3d(MathHelper.clamp(from.x, b.minX, b.maxX), MathHelper.clamp(from.y, b.minY, b.maxY), MathHelper.clamp(from.z, b.minZ, b.maxZ));
			if (near.distanceTo(from) > reach) {
				continue;
			}
			if (inFront && near.subtract(beam.getPos()).dotProduct(ahead) < 0.3) {
				continue;
			}
			out.add(living);
		}
		return out;
	}

	/** His bite, if anything is in reach: it takes {@link #BITE_DAMAGE} of the rider's damage, in a spray of blood. */
	private static void bite(BeamEntity beam, ServerWorld world, ServerPlayerEntity rider) {
		Vec3d mouth = mouth(beam);
		List<LivingEntity> victims = within(beam, world, rider, mouth, BITE_REACH, true);
		if (victims.isEmpty()) {
			return;
		}
		hurt(world, rider, victims, BITE_DAMAGE);
		beam.bit();
		world.playSound(null, mouth.x, mouth.y, mouth.z, ChainsawSounds.BEAM_BITE, SoundCategory.PLAYERS, 1.2F, 0.9F + world.random.nextFloat() * 0.2F);
	}

	/** {@code damage} to each of {@code victims} from {@code rider}: as the player's own attack, so the kills are theirs. */
	private static void hurt(ServerWorld world, ServerPlayerEntity rider, List<LivingEntity> victims, float damage) {
		for (LivingEntity target : victims) {
			Saw.breakGuard(target);
			target.timeUntilRegen = 0;
			target.damage(rider.getDamageSources().playerAttack(rider), damage);
			Saw.bleed(world, target, 24);
		}
	}

	// The leap.

	private static void startLeap(BeamEntity beam, ServerWorld world, Ride ride, Vec3d ahead, long now) {
		ride.leap = 0;
		ride.leapFromX = beam.getX();
		ride.leapFromZ = beam.getZ();
		ride.leapFromGround = ride.ground;
		ride.leapToX = beam.getX() + ahead.x * LEAP_LONG;
		ride.leapToZ = beam.getZ() + ahead.z * LEAP_LONG;
		double land = surface(world, ride.leapToX, ride.leapToZ);
		ride.leapToGround = Double.isNaN(land) ? ride.ground : land;
		ride.lastLeap = now;
		beam.leapt();
		world.playSound(null, beam.getX(), ride.ground, beam.getZ(), ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 1.3F, 1.15F);
		burst(world, beam.getX(), ride.ground, beam.getZ(), 50, 0.8, 0.4);
	}

	/** One tick of the leap: along its arc (up and over, landing where it was aimed); at the end he crashes down. */
	private static void leapTick(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity rider, long now) {
		ride.leap++;
		double t = Math.min(1.0, (double) ride.leap / LEAP_TICKS);
		double x = MathHelper.lerp(t, ride.leapFromX, ride.leapToX);
		double z = MathHelper.lerp(t, ride.leapFromZ, ride.leapToZ);
		double base = MathHelper.lerp(t, ride.leapFromGround, ride.leapToGround);
		double top = surface(world, x, z);
		if (!Double.isNaN(top)) {
			base = Math.max(base, top);
		}
		ride.ground = base;
		double arc = 4.0 * LEAP_UP * t * (1.0 - t);
		beam.setPosition(x, base + arc, z);
		// Nose up going up, down coming down.
		double rise = (ride.leapToGround - ride.leapFromGround) + 4.0 * LEAP_UP * (1.0 - 2.0 * t);
		beam.setPitch((float) -Math.toDegrees(Math.atan2(rise, LEAP_LONG)));
		if (ride.leap >= LEAP_TICKS) {
			ride.leap = -1;
			land(beam, world, rider);
		}
	}

	/** Down again: a ring of the ground thrown up, and a bite of {@link #LEAP_DAMAGE} on everything round where he landed. */
	private static void land(BeamEntity beam, ServerWorld world, ServerPlayerEntity rider) {
		Vec3d at = new Vec3d(beam.getX(), beam.ride().ground, beam.getZ());
		List<LivingEntity> victims = within(beam, world, rider, at.add(0.0, 0.3, 0.0), LEAP_REACH, false);
		hurt(world, rider, victims, LEAP_DAMAGE);
		for (LivingEntity victim : victims) {
			Vec3d push = victim.getPos().subtract(at).multiply(1.0, 0.0, 1.0);
			if (push.lengthSquared() > 1.0E-4) {
				push = push.normalize().multiply(0.6);
				victim.addVelocity(push.x, 0.3, push.z);
				victim.velocityModified = true;
			}
		}
		if (!victims.isEmpty()) {
			beam.bit();
		}
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 1.5F, 0.7F);
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.BEAM_BITE, SoundCategory.PLAYERS, 1.2F, 0.7F);
		ring(world, at, LEAP_REACH * 0.85);
	}

	// Going down.

	/** The dive begun: he and the rider part, and he sinks. */
	public static void dive(BeamEntity beam, ServerWorld world, ServerPlayerEntity rider) {
		if (beam.diving()) {
			return;
		}
		beam.dived();
		beam.ride().leap = -1;
		beam.removeAllPassengers();
		if (rider != null) {
			rider.fallDistance = 0.0F;
		}
		world.playSound(null, beam.getX(), beam.ride().ground, beam.getZ(), ChainsawSounds.BEAM_DIVE, SoundCategory.PLAYERS, 1.3F, 1.0F);
		burst(world, beam.getX(), beam.ride().ground, beam.getZ(), 40, 0.9, 0.3);
	}

	/** Beam gone, at once: no dive (for the tests, and a world closing). */
	public static void end(ServerPlayerEntity player) {
		BeamEntity beam = BEAMS.remove(player.getUuid());
		if (beam != null) {
			beam.removeAllPassengers();
			beam.discard();
		}
	}

	/** One tick of the dive: he slows and sinks out of sight, throwing up the ground as he goes, and is gone. */
	private static void diveTick(BeamEntity beam, ServerWorld world, Ride ride) {
		float since = beam.sinceDive(0.0F);
		double k = Math.min(1.0, since / DIVE_TICKS);
		ride.speed *= 0.92;
		Vec3d step = beam.heading().multiply(ride.speed);
		beam.setPosition(beam.getX() + step.x, ride.ground - DIVE_DEPTH * k * k, beam.getZ() + step.z);
		beam.setPitch(beam.getPitch() * (1.0F - LEVEL));
		if (beam.age % 3 == 0 && k < 1.0) {
			burst(world, beam.getX(), ride.ground, beam.getZ(), 8, 0.7, 0.25);
		}
		if (since >= DIVE_TICKS) {
			beam.discard();
		}
	}

	/** Beam is gone from the world (dived, or unloaded). */
	public static void gone(BeamEntity beam) {
		UUID owner = beam.owner();
		if (owner != null) {
			BEAMS.remove(owner, beam);
		}
	}

	// The ground thrown up.

	/** What the ground at {@code x}, {@code y}, {@code z} throws up when it bursts: its own blocks (water, a splash). */
	private static ParticleEffect spray(ServerWorld world, double x, double y, double z) {
		for (int down = 1; down <= 3; down++) {
			BlockPos pos = BlockPos.ofFloored(x, y - down + 0.5, z);
			BlockState state = world.getBlockState(pos);
			if (!state.getFluidState().isEmpty()) {
				return ParticleTypes.SPLASH;
			}
			if (!state.isAir() && state.getRenderType() != net.minecraft.block.BlockRenderType.INVISIBLE) {
				return new BlockStateParticleEffect(ParticleTypes.BLOCK, state);
			}
		}
		return DIRT;
	}

	/** A spray of the ground's own blocks thrown up from {@code x}, {@code y}, {@code z}: {@code count} bits, spread {@code spread}. */
	private static void burst(ServerWorld world, double x, double y, double z, int count, double spread, double speed) {
		world.spawnParticles(spray(world, x, y, z), x, y + 0.2, z, count, spread, 0.2, spread, speed);
	}

	/** A ring of the ground thrown up, out from {@code at} to {@code radius} blocks. */
	private static void ring(ServerWorld world, Vec3d at, double radius) {
		ParticleEffect spray = spray(world, at.x, at.y, at.z);
		int bits = 28;
		for (int i = 0; i < bits; i++) {
			double angle = Math.PI * 2.0 * i / bits;
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			world.spawnParticles(spray, at.x + dx * radius * 0.5, at.y + 0.1, at.z + dz * radius * 0.5, 0, dx, 0.55, dz, 0.45);
		}
		world.spawnParticles(spray, at.x, at.y + 0.2, at.z, 40, radius * 0.4, 0.2, radius * 0.4, 0.3);
	}

	static void forget(UUID id) {
		BeamEntity beam = BEAMS.remove(id);
		if (beam != null && beam.isAlive()) {
			beam.discard();
		}
	}

	static void clear() {
		BEAMS.clear();
	}
}
