package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.BeamEntity;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.mixin.LivingEntityAccessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;

/**
 * The ult: Beam, the shark fiend, in his big shark form (from the Reze arc, where he carries Chainsaw Man on his back, a
 * shark standing and running on four long thin legs). In the form and able to fight, his key calls him up for {@link
 * Blood#BEAM} blood: he bursts up out of the ground under you in a spray of the ground's own blocks and you stand on his
 * back, holding his chain for reins. He lasts {@link #LASTS} ticks; one Beam to a player.
 *
 * <p>While you ride him your keys are his: the forward key walks him the way you look (turning to it at most {@link #TURN}
 * degrees a tick, up to {@link #SPEED} blocks a tick; back stops him, the strafe keys nudge him aside), and he steps up what
 * is in his way, a block or two, and stops at a wall. The attack key bites ({@link #bite}: his jaw snaps on everything in a
 * cone in front of his mouth, for {@link #BITE_DAMAGE} of <em>your</em> damage, so what he kills is your kill, and flings
 * what survives), held it still grinds with your saws; the use key charges ({@link #charge}: a dash of {@link
 * #CHARGE_BLOCKS} blocks that bites through everything in the way once); the jump key leaps ({@link #leap}: {@link
 * #LEAP_UP} blocks up, {@link #LEAP_LONG} forward, crashing down on everything within {@link #LEAP_REACH} blocks of where he
 * lands for {@link #LEAP_DAMAGE}); sneaking gets you off. The keys reach the server through the game's own input for a
 * rider; the entity is {@link BeamEntity}, drawn by the client's {@code BeamRenderer}.
 *
 * <p>It ends when the time is up, when you get off, or if you leave the form or go down: he dives, sinking into the ground
 * over {@link #DIVE_TICKS} ticks, and you are left standing, unhurt by the drop. While you ride, the lunge and the chain are
 * not yours (the keys are his instead).
 */
public final class Beam {
	/** How long he stays (ticks): 20 seconds. */
	public static final int LASTS = 20 * 20;
	/** His top speed (blocks a tick), how fast he picks it up and brakes (blocks a tick, each tick). */
	public static final double SPEED = 0.6;
	private static final double ACCELERATE = 0.08;
	private static final double BRAKE = 0.12;
	/** How fast the strafe keys move him sideways (blocks a tick), and how fast he turns to where you look (degrees a tick). */
	private static final double STRAFE = 0.25;
	public static final float TURN = 12.0F;
	/**
	 * The bite (the attack key): the least time between bites (ticks; a click this much early still counts, for lag), how
	 * hard it is, how far in front of his mouth it reaches (blocks) and how wide (the cosine of its half angle); what is
	 * nearer the mouth than {@link #BITE_CLOSE} is bitten whichever way it lies. The mouth is this far ahead of his middle
	 * and this high above his feet (blocks). What survives is flung: this much up and this much forward (blocks a tick of speed).
	 */
	public static final int BITE_EVERY = 12;
	private static final int LAG_SLACK = 2;
	public static final float BITE_DAMAGE = 16.0F;
	public static final double BITE_REACH = 3.5;
	private static final double BITE_CONE = 0.55;
	private static final double BITE_CLOSE = 1.2;
	public static final double MOUTH = 2.2;
	public static final double MOUTH_HEIGHT = 1.4;
	public static final double FLING_UP = 1.2;
	public static final double FLING_AHEAD = 1.5;
	/** The charge (the use key): how long it takes (ticks), how far it goes (blocks), what each thing in the way takes, the time between charges (ticks) and how near his mouth a thing must be to be caught. */
	public static final int CHARGE_TICKS = 8;
	public static final double CHARGE_BLOCKS = 9.0;
	public static final float CHARGE_DAMAGE = 12.0F;
	public static final int CHARGE_EVERY = 30;
	private static final double CHARGE_REACH = 2.2;
	/** The leap (the jump key): no more often than this (ticks); how high and far it goes (blocks); the crash's reach (blocks) and bite. */
	public static final int LEAP_EVERY = 40;
	public static final double LEAP_UP = 4.0;
	public static final double LEAP_LONG = 8.0;
	public static final double LEAP_REACH = 3.0;
	public static final float LEAP_DAMAGE = 16.0F;
	/** What makes that leap: the pull of the ground (blocks a tick, each tick), the speed he leaves it at going up, and going forward; about this many ticks in the air. */
	private static final double LEAP_GRAVITY = 0.16;
	private static final double LEAP_LIFT = Math.sqrt(2.0 * LEAP_GRAVITY * LEAP_UP);
	private static final double LEAP_FORWARD = LEAP_LONG / (2.0 * LEAP_LIFT / LEAP_GRAVITY);
	public static final int LEAP_TICKS = (int) Math.round(2.0 * LEAP_LIFT / LEAP_GRAVITY);
	/** The dive: how long he takes to sink out of sight (ticks) and how deep he goes (blocks). */
	public static final int DIVE_TICKS = 15;
	private static final double DIVE_DEPTH = 3.4;
	/** Bursting up out of the ground: for this many ticks he rises into place, from this far down (blocks). */
	public static final int POP_TICKS = 10;
	private static final double SINK = 2.4;
	/** On his feet: how hard the ground pulls him down (blocks a tick, each tick), and the most he falls in a tick. */
	private static final double PULL = 0.12;
	private static final double FALL = 1.2;
	/** How long a rider is spared a fall after getting off (ticks): a dive from the air. */
	private static final int SAFE_FOR = 60;
	/** How long the action bar shows the riding keys: sent as he arrives, and again this many ticks in. */
	private static final int KEYS_AGAIN = 50;

	private static final BlockStateParticleEffect DIRT = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState());

	/** Whose Beam is out (until he is gone). */
	private static final Map<UUID, BeamEntity> BEAMS = new ConcurrentHashMap<>();
	/** Who got off him in the air, and until when (world time): no fall damage for them. */
	private static final Map<UUID, Long> SAFE = new ConcurrentHashMap<>();

	/** What a run of Beam is doing: the server's, kept with his entity. */
	public static final class Ride {
		/** His speed now (blocks a tick), and how fast the ground is pulling him down. */
		double speed;
		double fall;
		/** Where the ground was where he burst up (his feet's height when he is up). */
		double popFrom;
		/** The leap in progress (ticks in, or -1), and when the last one began. */
		int leap = -1;
		long lastLeap = -1000L;
		/** The charge in progress (ticks in, or -1), the way it goes, what it has bitten already, and when the last one began. */
		int charge = -1;
		Vec3d chargeWay = Vec3d.ZERO;
		final Set<UUID> bitten = new HashSet<>();
		long lastCharge = -1000L;
		/** When he last bit, and where his feet were when he began to dive. */
		long lastBite = -1000L;
		double diveFrom;
	}

	private Beam() {
	}

	public static void init() {
		// Pressed into a wall or a roof, a rider's head may be in a block a moment: that is not a harm to them. Nor is the
		// fall of getting off him in the air.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (source.isOf(DamageTypes.IN_WALL) && entity.getVehicle() instanceof BeamEntity) {
				return false;
			}
			return !(source.isOf(DamageTypes.FALL) && entity instanceof ServerPlayerEntity player && SAFE.getOrDefault(player.getUuid(), 0L) > player.getServerWorld().getTime());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUuid()));
	}

	/**
	 * Beam called up by {@code player}: whether he came. Costs {@link Blood#BEAM}; only in the form and able to fight, and
	 * where there is room for him (a clear space about two blocks wide and tall, on the ground, within a few blocks of
	 * you).
	 */
	public static boolean summon(ServerPlayerEntity player) {
		if (!Form.fighting(player) || active(player) != null || player.hasVehicle()) {
			return false;
		}
		ServerWorld world = player.getServerWorld();
		double ground = groundUnder(world, player);
		Vec3d at = room(world, player, ground);
		if (at == null) {
			player.sendMessage(Text.translatable("chainsawman.beam.no_room"), true);
			return false;
		}
		if (!Blood.spend(player, Blood.BEAM)) {
			return false;
		}
		BeamEntity beam = new BeamEntity(ChainsawMan.BEAM_ENTITY, world);
		beam.refreshPositionAndAngles(at.x, at.y - SINK, at.z, player.getYaw(), 0.0F);
		beam.setOwner(player.getUuid());
		beam.ride().popFrom = at.y;
		BEAMS.put(player.getUuid(), beam);
		world.spawnEntity(beam);
		player.startRiding(beam, true);
		player.fallDistance = 0.0F;
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 1.6F, 1.0F);
		burst(world, at.x, at.y, at.z, 90, 1.1, 0.5);
		showKeys(player);
		return true;
	}

	/** The line on the action bar that says what your keys do now (with your own keys' names). */
	private static void showKeys(ServerPlayerEntity player) {
		player.sendMessage(Text.translatable("chainsawman.beam.keys", Text.keybind("key.attack"), Text.keybind("key.use"), Text.keybind("key.jump"),
				Text.keybind("key.sneak")), true);
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

	/** The Beam {@code player} is riding, if he is not on his way down; otherwise null. */
	private static BeamEntity mount(ServerPlayerEntity player) {
		return player.getVehicle() instanceof BeamEntity beam && beam.isAlive() && !beam.diving() && player.getUuid().equals(beam.owner()) ? beam : null;
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

	/** Where there is room for him: where {@code player} stands, or the nearest place round it that is clear (his size), or null. */
	private static Vec3d room(ServerWorld world, ServerPlayerEntity player, double ground) {
		EntityDimensions size = ChainsawMan.BEAM_ENTITY.getDimensions();
		int[][] near = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}};
		for (int[] d : near) {
			Vec3d at = new Vec3d(player.getX() + d[0], ground, player.getZ() + d[1]);
			Box box = size.getBoxAt(at.x, at.y + 0.01, at.z);
			if (!world.getBlockCollisions(null, box).iterator().hasNext()) {
				return at;
			}
		}
		return null;
	}

	/** Everything Beam does in a tick, on the server: his walk, his leaps and charges, his dive. */
	public static void tick(BeamEntity beam) {
		if (!(beam.getWorld() instanceof ServerWorld world)) {
			return;
		}
		Ride ride = beam.ride();
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
		if (beam.age == KEYS_AGAIN) {
			showKeys(rider);
		}
		if (beam.age < POP_TICKS) {
			// Rising out of the ground into place.
			double k = (beam.age + 1.0) / POP_TICKS;
			beam.setPosition(beam.getX(), ride.popFrom - SINK * (1.0 - k) * (1.0 - k), beam.getZ());
			beam.setAirborne(false);
			rider.fallDistance = 0.0F;
			return;
		}
		// While he charges he goes straight; otherwise he turns to the way you look.
		if (ride.charge < 0) {
			float yaw = beam.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(rider.getYaw() - beam.getYaw()), -TURN, TURN);
			beam.setYaw(yaw);
		}
		Vec3d ahead = beam.heading();
		Vec3d left = new Vec3d(ahead.z, 0.0, -ahead.x);
		if (ride.charge < 0 && ride.leap < 0 && ((LivingEntityAccessor) rider).chainsawman$jumping() && beam.isOnGround()) {
			leap(rider);
		}
		if (ride.leap >= 0) {
			leapTick(beam, world, ride, rider, ahead);
		} else if (ride.charge >= 0) {
			chargeTick(beam, world, ride, rider);
		} else {
			double wanted = rider.forwardSpeed > 0.0F ? SPEED * Math.min(1.0F, rider.forwardSpeed) : 0.0;
			ride.speed += MathHelper.clamp(wanted - ride.speed, -BRAKE, ACCELERATE);
			Vec3d step = ahead.multiply(ride.speed).add(left.multiply(rider.sidewaysSpeed * STRAFE));
			walk(beam, world, ride, step);
			beam.setPitch(beam.getPitch() * 0.6F);
		}
		rider.fallDistance = 0.0F;
	}

	/**
	 * Whether the run goes on: the rider is still on his back and in the form and able; the time is not up and they are not
	 * sneaking (those two wait till he is on the ground again).
	 */
	private static boolean stays(BeamEntity beam, ServerPlayerEntity rider) {
		if (rider == null || !rider.isAlive() || rider.isSpectator() || !Form.fighting(rider)) {
			return false;
		}
		return beam.airborne() || !rider.isSneaking() && beam.age < LASTS;
	}

	/** One step of {@code step} (blocks, flat) along the ground: the game's own collision stops him at a wall and lifts him over what he can step on, and the ground pulls him down. */
	private static void walk(BeamEntity beam, ServerWorld world, Ride ride, Vec3d step) {
		double x = beam.getX() + step.x;
		double z = beam.getZ() + step.z;
		if (!world.isChunkLoaded(MathHelper.floor(x) >> 4, MathHelper.floor(z) >> 4)) {
			// Past the edge of what is loaded: he stops, rather than load it.
			ride.speed = 0.0;
			step = Vec3d.ZERO;
		}
		ride.fall = beam.isOnGround() ? -0.1 : Math.max(ride.fall - PULL, -FALL);
		Vec3d from = beam.getPos();
		beam.move(MovementType.SELF, new Vec3d(step.x, ride.fall, step.z));
		if (beam.isOnGround()) {
			ride.fall = -0.1;
		}
		beam.setAirborne(!beam.isOnGround());
		// Against a wall his speed is what he got: he does not wind up against it.
		double moved = Math.hypot(beam.getX() - from.x, beam.getZ() - from.z);
		if (moved < Math.hypot(step.x, step.z) * 0.5) {
			ride.speed = Math.min(ride.speed, moved);
		}
	}

	// The bite.

	/** The place of his mouth: ahead of his middle, at the height of the gape. */
	public static Vec3d mouth(BeamEntity beam) {
		return beam.getPos().add(beam.heading().multiply(MOUTH)).add(0.0, MOUTH_HEIGHT, 0.0);
	}

	/**
	 * What Beam can bite or crash on: the living things within {@code reach} of {@code from} (the nearest point of each),
	 * not the rider, not armour stands, spectators, or anyone downed. With {@code cone}, only what lies in front of {@code
	 * from} within {@link #BITE_CONE} of the way he faces (or very close to it).
	 */
	private static List<LivingEntity> within(BeamEntity beam, ServerWorld world, ServerPlayerEntity rider, Vec3d from, double reach, boolean cone) {
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
			double far = near.distanceTo(from);
			if (far > reach) {
				continue;
			}
			if (cone && far > BITE_CLOSE && near.subtract(from).multiply(1.0, 0.0, 1.0).normalize().dotProduct(ahead) < BITE_CONE) {
				continue;
			}
			out.add(living);
		}
		return out;
	}

	/**
	 * His bite (the attack key, from his back): his jaw snaps shut on everything in a cone in front of his mouth, for {@link
	 * #BITE_DAMAGE} of the rider's damage, in a spray of blood; what survives is flung up and forward. No more often than
	 * every {@link #BITE_EVERY} ticks. Whether it came.
	 */
	public static boolean bite(ServerPlayerEntity player) {
		BeamEntity beam = mount(player);
		if (beam == null || beam.age < POP_TICKS) {
			return false;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		Ride ride = beam.ride();
		if (now - ride.lastBite < BITE_EVERY - LAG_SLACK) {
			return false;
		}
		ride.lastBite = now;
		beam.bit();
		Vec3d mouth = mouth(beam);
		List<LivingEntity> victims = within(beam, world, player, mouth, BITE_REACH, true);
		hurt(world, player, victims, BITE_DAMAGE);
		Vec3d ahead = beam.heading();
		for (LivingEntity victim : victims) {
			if (victim.isAlive()) {
				fling(victim, ahead.x * FLING_AHEAD, FLING_UP, ahead.z * FLING_AHEAD);
			}
		}
		world.playSound(null, mouth.x, mouth.y, mouth.z, ChainsawSounds.BEAM_BITE, SoundCategory.PLAYERS, victims.isEmpty() ? 0.9F : 1.3F,
				0.9F + world.random.nextFloat() * 0.2F);
		return true;
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

	/** {@code target} thrown: at this velocity (blocks a tick). */
	private static void fling(LivingEntity target, double x, double y, double z) {
		target.setVelocity(x, y, z);
		target.velocityModified = true;
	}

	// The charge.

	/**
	 * The charge (the use key, from his back): he dashes straight ahead, {@link #CHARGE_BLOCKS} blocks in {@link
	 * #CHARGE_TICKS} ticks, biting through everything in the way once each for {@link #CHARGE_DAMAGE} and flinging it aside.
	 * No more often than every {@link #CHARGE_EVERY} ticks, and only on the ground. Whether it came.
	 */
	public static boolean charge(ServerPlayerEntity player) {
		BeamEntity beam = mount(player);
		if (beam == null || beam.age < POP_TICKS) {
			return false;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		Ride ride = beam.ride();
		if (now - ride.lastCharge < CHARGE_EVERY || ride.leap >= 0 || ride.charge >= 0 || !beam.isOnGround()) {
			return false;
		}
		ride.lastCharge = now;
		ride.charge = 0;
		ride.chargeWay = beam.heading();
		ride.bitten.clear();
		beam.charged();
		world.playSound(null, beam.getX(), beam.getY(), beam.getZ(), ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 1.2F, 1.35F);
		burst(world, beam.getX(), beam.getY(), beam.getZ(), 40, 0.8, 0.35);
		return true;
	}

	/** One tick of the charge: on, along the way he began; whatever he runs into that he has not bitten yet is bitten, and flung aside. */
	private static void chargeTick(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity rider) {
		ride.charge++;
		double each = CHARGE_BLOCKS / CHARGE_TICKS;
		ride.speed = each;
		walk(beam, world, ride, ride.chargeWay.multiply(each));
		Vec3d mouth = mouth(beam);
		Vec3d side = new Vec3d(ride.chargeWay.z, 0.0, -ride.chargeWay.x);
		boolean any = false;
		for (LivingEntity victim : within(beam, world, rider, mouth, CHARGE_REACH, false)) {
			if (!ride.bitten.add(victim.getUuid())) {
				continue;
			}
			any = true;
			hurt(world, rider, List.of(victim), CHARGE_DAMAGE);
			if (victim.isAlive()) {
				// Aside: whichever side of his way it is on, a little further on, a little up.
				double across = victim.getPos().subtract(beam.getPos()).dotProduct(side) >= 0.0 ? 1.0 : -1.0;
				fling(victim, side.x * across * 1.1 + ride.chargeWay.x * 0.5, 0.6, side.z * across * 1.1 + ride.chargeWay.z * 0.5);
			}
		}
		if (any) {
			beam.bit();
			world.playSound(null, mouth.x, mouth.y, mouth.z, ChainsawSounds.BEAM_BITE, SoundCategory.PLAYERS, 1.3F, 0.85F + world.random.nextFloat() * 0.2F);
		}
		if (ride.charge >= CHARGE_TICKS) {
			ride.charge = -1;
			ride.speed = SPEED;
		}
	}

	// The leap.

	/** Begins his leap: up and over, forward the way he faces (at most every {@link #LEAP_EVERY} ticks, from the ground). Whether it began. */
	public static boolean leap(ServerPlayerEntity player) {
		BeamEntity beam = mount(player);
		if (beam == null || beam.age < POP_TICKS) {
			return false;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		Ride ride = beam.ride();
		if (now - ride.lastLeap < LEAP_EVERY || ride.leap >= 0 || ride.charge >= 0 || !beam.isOnGround()) {
			return false;
		}
		ride.leap = 0;
		ride.fall = LEAP_LIFT;
		ride.lastLeap = now;
		beam.leapt();
		world.playSound(null, beam.getX(), beam.getY(), beam.getZ(), ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 1.3F, 1.15F);
		burst(world, beam.getX(), beam.getY(), beam.getZ(), 50, 0.9, 0.4);
		return true;
	}

	/** One tick of the leap: up and over and down under the pull of the ground, carried forward; when his feet are down again he crashes. */
	private static void leapTick(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity rider, Vec3d ahead) {
		ride.leap++;
		ride.fall -= LEAP_GRAVITY;
		Vec3d step = ahead.multiply(LEAP_FORWARD);
		double x = beam.getX() + step.x;
		double z = beam.getZ() + step.z;
		if (!world.isChunkLoaded(MathHelper.floor(x) >> 4, MathHelper.floor(z) >> 4)) {
			step = Vec3d.ZERO;
		}
		beam.move(MovementType.SELF, new Vec3d(step.x, ride.fall, step.z));
		if (beam.verticalCollision && ride.fall > 0.0) {
			// A roof: he stops rising.
			ride.fall = 0.0;
		}
		ride.speed = LEAP_FORWARD;
		// Nose up going up, down coming down.
		beam.setPitch((float) -Math.toDegrees(Math.atan2(ride.fall, LEAP_FORWARD)) * 0.5F);
		beam.setAirborne(!beam.isOnGround());
		if (beam.isOnGround() && ride.leap >= 3 || ride.leap > LEAP_TICKS * 3) {
			ride.leap = -1;
			ride.fall = -0.1;
			land(beam, world, rider);
		}
	}

	/** Down again: a ring of the ground thrown up, and a bite of {@link #LEAP_DAMAGE} on everything round where he landed, thrown back. */
	private static void land(BeamEntity beam, ServerWorld world, ServerPlayerEntity rider) {
		Vec3d at = beam.getPos();
		List<LivingEntity> victims = within(beam, world, rider, at.add(0.0, 0.3, 0.0), LEAP_REACH, false);
		hurt(world, rider, victims, LEAP_DAMAGE);
		for (LivingEntity victim : victims) {
			Vec3d push = victim.getPos().subtract(at).multiply(1.0, 0.0, 1.0);
			if (victim.isAlive() && push.lengthSquared() > 1.0E-4) {
				push = push.normalize().multiply(0.9);
				fling(victim, push.x, 0.5, push.z);
			}
		}
		if (!victims.isEmpty()) {
			beam.bit();
		}
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 1.6F, 0.7F);
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.BEAM_BITE, SoundCategory.PLAYERS, 1.2F, 0.7F);
		ring(world, at, LEAP_REACH * 0.85);
	}

	// Going down.

	/** The dive begun: he and the rider part, and he sinks into the ground. */
	public static void dive(BeamEntity beam, ServerWorld world, ServerPlayerEntity rider) {
		if (beam.diving()) {
			return;
		}
		beam.dived();
		Ride ride = beam.ride();
		ride.leap = -1;
		ride.charge = -1;
		ride.diveFrom = beam.getY();
		beam.setAirborne(false);
		beam.removeAllPassengers();
		if (rider != null) {
			rider.fallDistance = 0.0F;
			SAFE.put(rider.getUuid(), world.getTime() + SAFE_FOR);
		}
		world.playSound(null, beam.getX(), ride.diveFrom, beam.getZ(), ChainsawSounds.BEAM_DIVE, SoundCategory.PLAYERS, 1.4F, 1.0F);
		burst(world, beam.getX(), ride.diveFrom, beam.getZ(), 50, 1.0, 0.3);
	}

	/** Beam gone, at once: no dive (for the tests, and a world closing). */
	public static void end(ServerPlayerEntity player) {
		BeamEntity beam = BEAMS.remove(player.getUuid());
		if (beam != null) {
			beam.removeAllPassengers();
			beam.discard();
		}
	}

	/** One tick of the dive: he slows and sinks into the ground, throwing it up as he goes, and is gone. */
	private static void diveTick(BeamEntity beam, ServerWorld world, Ride ride) {
		float since = beam.sinceDive(0.0F);
		double k = Math.min(1.0, since / DIVE_TICKS);
		ride.speed *= 0.9;
		Vec3d step = beam.heading().multiply(ride.speed);
		beam.setPosition(beam.getX() + step.x, ride.diveFrom - DIVE_DEPTH * k * k, beam.getZ() + step.z);
		beam.setPitch(beam.getPitch() * 0.6F);
		if (beam.age % 3 == 0 && k < 1.0) {
			burst(world, beam.getX(), ride.diveFrom, beam.getZ(), 14, 1.0, 0.25);
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
		SAFE.remove(id);
	}

	static void clear() {
		BEAMS.clear();
		SAFE.clear();
	}
}
