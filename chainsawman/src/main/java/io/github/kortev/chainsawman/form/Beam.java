package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.BeamEntity;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.mixin.LivingEntityAccessor;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

/**
 * The ult: Beam, the shark fiend, in his big shark form (from the Reze arc, where he carries Chainsaw Man on his back, a
 * shark on four long thin legs who swims through the ground and through buildings as if they were water). In the form and
 * able to fight, his key calls him up for {@link Blood#BEAM} blood: he bursts up out of the ground under you, thrashing for
 * joy, turns his head back to look at you and screeches, and you stand on his back, holding his chain for reins. He lasts
 * {@link #LASTS} ticks; one Beam to a player.
 *
 * <p>While you ride him your keys are his: the forward key sends him the way you look (turning to it at most {@link #TURN}
 * degrees a tick, up to {@link #SPEED} blocks a tick, reached in a quick surge; back stops him, the strafe keys nudge him
 * aside). <em>Moving, he swims in the ground</em>: he sinks {@link #SINK} blocks so only his back, spikes and the rider show,
 * follows the ground's height as it rises and falls (smoothly, not in steps), and tears through what is in his way
 * ({@link #plow}: the blocks break with their sound and some fly out as short-lived debris; not the very hard ones; with
 * {@code mobGriefing} off he swims under the walls instead). He porpoises, a gentle rise and dip, and at full speed
 * now and then arcs out of the ground and back in. Stopped, he rises out onto his crab legs. The attack key bites ({@link
 * #bite}: he lunges up out of the ground, his jaw snaps on everything in a cone in front of his mouth, for {@link
 * #BITE_DAMAGE} of <em>your</em> damage, so what he kills is your kill, and flings what survives), held it still grinds with
 * your saws; the use key charges ({@link #charge}: a dash of {@link #CHARGE_BLOCKS} blocks that bites through everything in
 * the way once); the jump key breaches ({@link #leap}: out of the ground {@link #LEAP_UP} blocks up and {@link #LEAP_LONG}
 * forward and back in, a crash of spray on everything within {@link #LEAP_REACH} blocks of where he goes in for {@link
 * #LEAP_DAMAGE}); sneaking gets you off. The keys reach the server through the game's own input for a rider; the entity is
 * {@link BeamEntity}, drawn by the client's {@code BeamRenderer}.
 *
 * <p>It ends when the time is up, when you get off, or if you leave the form or go down: he dives, sinking into the ground
 * over {@link #DIVE_TICKS} ticks, and you are left standing, unhurt by the drop. While you ride, the lunge and the chain are
 * not yours (the keys are his instead).
 */
public final class Beam {
	/** How long he stays (ticks): 20 seconds. */
	public static final int LASTS = 20 * 20;
	/** His top speed (blocks a tick: 24 a second), how fast he picks it up (a surge) and brakes (blocks a tick, each tick). */
	public static final double SPEED = 1.2;
	private static final double ACCELERATE = 0.24;
	private static final double BRAKE = 0.16;
	/** How fast the strafe keys move him sideways (blocks a tick), and how fast he turns to where you look (degrees a tick). */
	private static final double STRAFE = 0.3;
	public static final float TURN = 11.0F;
	/** How deep he swims (blocks below the ground: only his back, spikes and the rider show), and how fast he sinks or rises (a part of it a tick). */
	public static final double SINK = 2.7;
	private static final double SWIM_RATE = 0.1;
	/** Slower than this he is not swimming but standing on his legs (blocks a tick); faster than {@link #SWIM_FROM}, or with a key down, he sinks. */
	private static final double STAND_BELOW = 0.05;
	private static final double SWIM_FROM = 0.12;
	/** How high a step of the ground he follows up (blocks); how far down he looks for it. */
	private static final int CLIMB = 2;
	private static final int FALL_TO = 8;
	/** Porpoising: how far he rises and dips as he swims at full speed (blocks), how fast (radians a tick at rest and extra at full speed). */
	private static final double BOB = 0.36;
	private static final double WAVE_REST = 0.07;
	private static final double WAVE_FAST = 0.3;
	/** At full speed he arcs out of the ground now and then: how long it takes (ticks), how high (blocks), the least time between (ticks) and a spread on it. */
	private static final int ARC_TICKS = 18;
	private static final double ARC_UP = 1.7;
	private static final int ARC_EVERY = 36;
	private static final int ARC_SPREAD = 30;
	/**
	 * The bite (the attack key): the least time between bites (ticks; a click this much early still counts, for lag), how
	 * hard it is, how far in front of his mouth it reaches (blocks) and how wide (the cosine of its half angle); what is
	 * nearer the mouth than {@link #BITE_CLOSE} is bitten whichever way it lies. The mouth is this far ahead of his middle
	 * and this high above where his feet would be on the ground (blocks). What survives is flung: this much up and this much
	 * forward (blocks a tick of speed). The lunge up out of the ground that goes with it takes this long (ticks).
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
	private static final int LUNGE_TICKS = 10;
	/** The charge (the use key): how long it takes (ticks), how far it goes (blocks), what each thing in the way takes, the time between charges (ticks) and how near his mouth a thing must be to be caught. */
	public static final int CHARGE_TICKS = 8;
	public static final double CHARGE_BLOCKS = 12.0;
	public static final float CHARGE_DAMAGE = 12.0F;
	public static final int CHARGE_EVERY = 30;
	private static final double CHARGE_REACH = 2.2;
	/** The leap, a breach (the jump key): no more often than this (ticks); how high and far it goes (blocks, from the level he swims at); the crash's reach (blocks) and bite. */
	public static final int LEAP_EVERY = 40;
	public static final double LEAP_UP = 5.2;
	public static final double LEAP_LONG = 14.0;
	public static final double LEAP_REACH = 3.2;
	public static final float LEAP_DAMAGE = 16.0F;
	/** What makes that leap: the pull of the ground (blocks a tick, each tick), the speed he leaves it at going up, and going forward; about this many ticks in the air. */
	private static final double LEAP_GRAVITY = 0.16;
	private static final double LEAP_LIFT = Math.sqrt(2.0 * LEAP_GRAVITY * LEAP_UP);
	public static final int LEAP_TICKS = (int) Math.round(2.0 * LEAP_LIFT / LEAP_GRAVITY);
	private static final double LEAP_FORWARD = LEAP_LONG / LEAP_TICKS;
	/** The dive: how long he takes to sink out of sight (ticks) and how deep he goes (blocks). */
	public static final int DIVE_TICKS = 15;
	private static final double DIVE_DEPTH = 3.4;
	/**
	 * Bursting up out of the ground (the summoning): for this many ticks he comes up from this far below the ground, flies
	 * out of it, lands on his legs and thrashes for joy, his head turned back to look at you; he screeches at {@link
	 * #SCREECH_AT}. Your keys are his from then.
	 */
	public static final int POP_TICKS = 26;
	private static final double POP_DEPTH = 3.0;
	private static final double POP_G = 0.122;
	private static final double POP_V = POP_G * 8.0;
	private static final int POP_LANDS = 12;
	private static final int SCREECH_AT = 7;
	/** How long a rider is spared a fall after getting off (ticks): a dive from the air. */
	private static final int SAFE_FOR = 60;
	/** How long the action bar shows the riding keys: sent as he arrives, and again this many ticks in. */
	private static final int KEYS_AGAIN = 50;
	/**
	 * Tearing through what is in his way: the blocks of the lane he swims in, from this far behind his middle to this far
	 * ahead of it, this far each side of it (blocks), as high as the rider; the most blocks he breaks in a tick, and the
	 * most of those that fly out as debris (and the most debris about at once, and how long a piece lasts, in ticks). What
	 * is harder than this to break he swims under.
	 */
	private static final double PLOW_BEHIND = 2.0;
	private static final double PLOW_AHEAD = 5.2;
	private static final double PLOW_WIDE = 1.75;
	private static final int PLOW_CAP = 36;
	private static final int DEBRIS_PER_TICK = 4;
	private static final int DEBRIS_MAX = 36;
	public static final int DEBRIS_LASTS = 24;
	private static final float TOO_HARD = 20.0F;

	/**
	 * The ambush (phase 2; the attack key while he swims, with a mob ahead that is too far to bite): the least time between ambushes
	 * (ticks, from the eruption), how far ahead of his mouth a mob can be marked (blocks) and how near to the way you look (the
	 * cosine of the half angle). He dips {@link #AMBUSH_DIP} blocks deeper and rushes under it at {@link #AMBUSH_SPEED} (turning up to
	 * {@link #AMBUSH_TURN} degrees a tick), at least {@link #AMBUSH_MIN} ticks and at most {@link #AMBUSH_MAX}, until his head is
	 * {@link #AMBUSH_UNDER} blocks short of its middle; then he erupts: {@link #AMBUSH_DAMAGE} to everything within {@link
	 * #AMBUSH_RADIUS} of it, the mob thrown {@link #AMBUSH_LAUNCH} blocks a tick straight up (the others less), a ring of {@link
	 * #RING_PIECES} blocks of the ground thrown up, and he rises {@link #AMBUSH_LIFT} (a speed, blocks a tick) over the hole and
	 * comes down on {@link #AMBUSH_G}.
	 */
	public static final int AMBUSH_EVERY = 30;
	public static final double AMBUSH_REACH = 8.5;
	private static final double AMBUSH_CONE = 0.7;
	private static final double AMBUSH_DIP = 1.3;
	private static final double AMBUSH_SPEED = 1.5;
	private static final float AMBUSH_TURN = 45.0F;
	private static final int AMBUSH_MIN = 3;
	private static final int AMBUSH_MAX = 14;
	private static final double AMBUSH_UNDER = 3.0;
	public static final float AMBUSH_DAMAGE = 24.0F;
	public static final double AMBUSH_LAUNCH = 2.1;
	private static final double AMBUSH_RADIUS = 2.6;
	private static final double AMBUSH_LIFT = 1.15;
	private static final double AMBUSH_G = 0.16;
	private static final int RING_PIECES = 16;
	private static final int RING_LASTS = 30;
	/**
	 * The grab (the bite on a mob no bigger than a Ravager): his jaws hold it, he shakes it for {@link BeamEntity#GRAB_TICKS} ticks (it
	 * takes {@link #SHAKE_DAMAGE} at the end of each swing of his head, six in all) and flings it the way you look at {@link
	 * #FLING_SPEED} blocks a tick for {@link #FLING_DAMAGE}. Bigger than this it is only bitten.
	 */
	public static final float GRAB_WIDTH = 2.0F;
	public static final float GRAB_HEIGHT = 2.4F;
	public static final float SHAKE_DAMAGE = 3.0F;
	public static final float FLING_DAMAGE = 6.0F;
	public static final double FLING_SPEED = 2.1;
	private static final double GRAB_RISE = 0.85;
	private static final int[] SHAKES_AT = {2, 5, 9, 12, 16, 19};
	/**
	 * The rider launch (the chain key while you ride): you are thrown {@link #LAUNCH_SPEED} blocks a tick the way you look for {@link
	 * #LAUNCH_TICKS} ticks (about twenty blocks), cutting everything in a wide path for the saws' finisher damage, and Beam swims to
	 * wherever you land and takes you back up if he gets to you within {@link #FETCH_TICKS} ticks. No more often than every {@link
	 * #LAUNCH_EVERY} ticks.
	 */
	public static final int LAUNCH_EVERY = 60;
	public static final double LAUNCH_SPEED = 2.3;
	public static final int LAUNCH_TICKS = 9;
	private static final double LAUNCH_WIDE = 1.4;
	public static final int FETCH_TICKS = 100;
	private static final double FETCH_SPEED = 1.5;
	private static final double FETCH_RANGE = 70.0;
	private static final double PICK_UP = 2.4;
	/** How hard a hit lands on his rider's screen (levels 1 to 4) and which extras come with it: the impact frames and lettering (told to the client, {@link FormEventPayload#BEAM_HIT}). */
	private static final int FLASH = FormEventPayload.BEAM_FLASH;
	private static final int CHOP = FormEventPayload.BEAM_CHOP;

	private static final BlockStateParticleEffect DIRT = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState());

	/** Whose Beam is out (until he is gone). */
	private static final Map<UUID, BeamEntity> BEAMS = new ConcurrentHashMap<>();
	/** Who got off him in the air, and until when (world time): no fall damage for them. */
	private static final Map<UUID, Long> SAFE = new ConcurrentHashMap<>();

	/** A piece of a wall thrown out as he tears through it, and until when (world time). */
	private record Debris(FallingBlockEntity block, long until) {
	}

	/** What a run of Beam is doing: the server's, kept with his entity. */
	public static final class Ride {
		/** His speed now (blocks a tick). */
		double speed;
		/** The height of the ground he is on (blocks; NaN until known), smoothed; how it slopes under him (rise over run, smoothed). */
		double surf = Double.NaN;
		double slope;
		/** How far he is sunk into swimming (0 standing, 1 swimming), his porpoise's phase, how far he is pushed lower under walls he cannot break (0 to 1). */
		double swim;
		double wave;
		double under;
		/** The arc out of the ground in progress (ticks in, or -1) and the world time the next may begin. */
		int arc = -1;
		long nextArc;
		/** The leap in progress (ticks in, or -1), how far above his swimming level he is, how fast he is rising (blocks a tick), whether it has crashed down yet, and when the last began. */
		int leap = -1;
		double lift;
		double liftV;
		boolean crashed;
		long lastLeap = -1000L;
		/** The lunge of a bite in progress (ticks in, or -1). */
		int lunge = -1;
		/** The charge in progress (ticks in, or -1), the way it goes, what it has bitten already, and when the last one began. */
		int charge = -1;
		Vec3d chargeWay = Vec3d.ZERO;
		final Set<UUID> bitten = new HashSet<>();
		long lastCharge = -1000L;
		/** When he last bit, and where his feet were when he began to dive. */
		long lastBite = -1000L;
		double diveFrom;
		/** Whether the last tick had him out of the ground. */
		boolean out;
		/** The ambush in progress (-1 none, 0 rushing under the prey, 1 erupting), its prey, ticks into it, how deep he has dug in (eased), his lift over the hole and its speed, whether it has crashed, and when the last erupted. */
		int amb = -1;
		UUID prey;
		int ambTicks;
		double dip;
		double ambLift;
		double ambV;
		boolean ambCrashed;
		long lastAmbush = -1000L;
		/** What he holds in his jaws (or null), ticks into the thrash, and how far he has risen to hold it up (0 to 1). */
		UUID held;
		int grabTicks;
		double rise;
		/** The rider thrown: the rider is off him and he is swimming to fetch them; the flight (ticks in, or -1), its way, where the rider is on it, what it has cut, whether they had gravity off before, since when, and when the last began. */
		boolean loose;
		int launch = -1;
		Vec3d launchWay = Vec3d.ZERO;
		Vec3d launchPos = Vec3d.ZERO;
		final Set<UUID> sliced = new HashSet<>();
		boolean hadNoGravity;
		long looseSince;
		long lastLaunch = -1000L;
		/** How many hits he has told his rider of, and the hardest (for the tests). */
		int hits;
		int hardest;
		/** The debris about. */
		final List<Debris> debris = new ArrayList<>();

		/** The height of the ground he is on, or {@code fallback} if it is not known yet. */
		public double surface(double fallback) {
			return Double.isNaN(surf) ? fallback : surf;
		}

		/** For the tests: whether he rushes (0) or erupts (1) or neither (-1), what he holds, whether his rider is thrown, the flight's tick, and the hits told to the rider and the hardest. */
		public int ambush() {
			return amb;
		}

		public boolean holding() {
			return held != null;
		}

		public boolean loose() {
			return loose;
		}

		public int flying() {
			return launch;
		}

		public int hits() {
			return hits;
		}

		public int hardest() {
			return hardest;
		}
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
		// A kill from his back pleases him: a happy chomp and a wiggle.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity killer && killer != entity && mount(killer) != null) {
				BeamEntity beam = mount(killer);
				beam.pleased();
				beam.getWorld().playSound(null, beam.getX(), beam.getY() + 2.0, beam.getZ(), ChainsawSounds.BEAM_BITE, SoundCategory.PLAYERS, 0.9F, 1.5F);
			}
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
		beam.refreshPositionAndAngles(at.x, at.y - POP_DEPTH, at.z, player.getYaw(), 0.0F);
		beam.setOwner(player.getUuid());
		beam.ride().surf = at.y;
		beam.setSwimming(1.0F);
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
				Text.keybind("key.sneak"), Text.keybind("key.chainsawman.chain")), true);
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
			// (A tenth up, not a hundredth: a dirt path or a farm is a sixteenth lower than the grass beside it, and he is as wide as that.)
			Box box = size.getBoxAt(at.x, at.y + 0.1, at.z);
			if (!world.getBlockCollisions(null, box).iterator().hasNext()) {
				return at;
			}
		}
		return null;
	}

	/** Everything Beam does in a tick, on the server: his summoning, his swim, his leaps and charges, his dive. */
	public static void tick(BeamEntity beam) {
		if (!(beam.getWorld() instanceof ServerWorld world)) {
			return;
		}
		Ride ride = beam.ride();
		sweep(ride, world);
		if (beam.diving()) {
			diveTick(beam, world, ride);
			return;
		}
		if (ride.loose) {
			looseTick(beam, world, ride);
			return;
		}
		ServerPlayerEntity rider = beam.rider();
		if (!stays(beam, rider)) {
			dive(beam, world, rider);
			return;
		}
		if (beam.age == KEYS_AGAIN) {
			showKeys(rider);
		}
		if (beam.age < POP_TICKS) {
			popTick(beam, world, ride, rider);
			return;
		}
		long now = world.getTime();
		// While he charges or rushes under a mob he goes straight; otherwise he turns to the way you look.
		if (ride.charge < 0 && ride.amb < 0) {
			float yaw = beam.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(rider.getYaw() - beam.getYaw()), -TURN, TURN);
			beam.setYaw(yaw);
		}
		Vec3d step = ride.amb == 0 ? rushStep(beam, world, ride, rider) : null;
		Vec3d ahead = beam.heading();
		Vec3d left = new Vec3d(ahead.z, 0.0, -ahead.x);
		if (ride.charge < 0 && ride.leap < 0 && ride.amb < 0 && ((LivingEntityAccessor) rider).chainsawman$jumping()) {
			leap(rider);
		}
		double wanted = rider.forwardSpeed > 0.0F ? SPEED * Math.min(1.0F, rider.forwardSpeed) : 0.0;
		boolean key = wanted > 0.0 || rider.sidewaysSpeed != 0.0F;
		if (step != null) {
			key = true;
		} else if (ride.amb == 1) {
			// Over the hole he drifts on a little the way he faces.
			ride.speed *= 0.8;
			step = ahead.multiply(Math.max(ride.speed, 0.2));
			key = true;
		} else if (ride.leap >= 0) {
			// In the air of a breach he carries on at least as fast as he was going.
			ride.speed = Math.max(ride.speed, LEAP_FORWARD);
			step = ahead.multiply(ride.speed);
		} else if (ride.charge >= 0) {
			ride.speed = CHARGE_BLOCKS / CHARGE_TICKS;
			step = ride.chargeWay.multiply(ride.speed);
		} else {
			ride.speed += MathHelper.clamp(wanted - ride.speed, -BRAKE, ACCELERATE);
			step = ahead.multiply(ride.speed).add(left.multiply(rider.sidewaysSpeed * STRAFE));
		}
		travel(beam, world, ride, step, key);
		if (ride.charge >= 0) {
			chargeBites(beam, world, ride, rider);
		}
		if (ride.leap >= 0) {
			leapTick(beam, world, ride, rider);
		}
		if (ride.amb >= 0) {
			ambushTick(beam, world, ride, rider);
		}
		if (ride.held != null) {
			grabTick(beam, world, ride, rider);
		}
		pose(beam, world, ride, now);
		wake(beam, world, ride);
		rider.fallDistance = 0.0F;
	}

	/**
	 * Whether the run goes on: the rider is still on his back and in the form and able; the time is not up and they are not
	 * sneaking (those two wait till he is out of the air again).
	 */
	private static boolean stays(BeamEntity beam, ServerPlayerEntity rider) {
		if (rider == null || !rider.isAlive() || rider.isSpectator() || !Form.fighting(rider)) {
			return false;
		}
		return beam.airborne() || !rider.isSneaking() && beam.age < LASTS;
	}

	// The summoning.

	/** How far above the ground his feet are {@code t} ticks into the summoning: up from below it, out of it, down onto his legs, and a small hop of joy. */
	private static double popHeight(int t) {
		if (t < POP_LANDS) {
			return Math.max(-POP_DEPTH, -POP_DEPTH + POP_V * t - POP_G * t * t / 2.0);
		}
		if (t < POP_LANDS + 7) {
			return 0.45 * Math.sin(Math.PI * (t - POP_LANDS) / 7.0);
		}
		return 0.0;
	}

	/** One tick of the summoning: he comes up through the ground, flies out of it, comes down on his legs and thrashes about (the clients show the rest). */
	private static void popTick(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity rider) {
		int t = beam.age;
		double h = popHeight(t);
		double next = popHeight(t + 1);
		beam.setPosition(beam.getX(), ride.surf + h, beam.getZ());
		beam.setPitch((float) MathHelper.clamp(-(next - h) * 22.0, -28.0, 28.0));
		beam.setSwimming(t < POP_LANDS - 5 ? 1.0F : (float) Math.max(0.0, 1.0 - (t - (POP_LANDS - 5)) / 6.0));
		beam.setAirborne(h > 0.5);
		if (t == 4) {
			// Through the ground: the second burst.
			burst(world, beam.getX(), ride.surf, beam.getZ(), 70, 1.4, 0.45);
			ring(world, new Vec3d(beam.getX(), ride.surf, beam.getZ()), 2.4);
		}
		if (t == SCREECH_AT) {
			world.playSound(null, beam.getX(), beam.getY() + 2.5, beam.getZ(), ChainsawSounds.BEAM_SCREECH, SoundCategory.PLAYERS, 1.8F, 1.0F);
		}
		if (t == POP_LANDS) {
			world.playSound(null, beam.getX(), ride.surf, beam.getZ(), ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 0.9F, 0.75F);
			ring(world, new Vec3d(beam.getX(), ride.surf, beam.getZ()), 2.8);
		}
		if (t == POP_LANDS + 7) {
			ring(world, new Vec3d(beam.getX(), ride.surf, beam.getZ()), 2.0);
		}
		ride.swim = 0.0;
		rider.fallDistance = 0.0F;
	}

	// Swimming.

	/** Whether the block is ground to him: something he would stand on, or water (he swims along the top of a lake). */
	private static boolean ground(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return !state.getCollisionShape(world, pos).isEmpty() || !state.getFluidState().isEmpty();
	}

	/** The height of the top of the ground at the column of {@code x}, {@code z} that he could stand on near {@code around}, or NaN if the column is blocked (a wall) or not loaded. */
	private static double surfaceAt(ServerWorld world, double x, double z, double around) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
			return Double.NaN;
		}
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int mid = MathHelper.floor(around);
		int bottom = Math.max(world.getBottomY() + 1, mid - FALL_TO);
		double found = scan(world, pos, bx, bz, mid + CLIMB, bottom);
		if (!Double.isNaN(found)) {
			return found;
		}
		// Nothing in reach: if the air is open where he is (the ground has dropped away), look all the way down.
		if (!ground(world, pos.set(bx, mid, bz)) && !ground(world, pos.set(bx, mid + 1, bz))) {
			return scan(world, pos, bx, bz, bottom, Math.max(world.getBottomY() + 1, bottom - 48));
		}
		return Double.NaN;
	}

	private static double scan(ServerWorld world, BlockPos.Mutable pos, int bx, int bz, int from, int to) {
		for (int y = from; y >= to; y--) {
			BlockState below = world.getBlockState(pos.set(bx, y - 1, bz));
			if (below.getCollisionShape(world, pos).isEmpty() && below.getFluidState().isEmpty()) {
				continue;
			}
			double top = below.getCollisionShape(world, pos).isEmpty() ? 0.9 : Math.min(1.0, below.getCollisionShape(world, pos).getMax(Direction.Axis.Y));
			if (ground(world, pos.set(bx, y, bz)) || ground(world, pos.set(bx, y + 1, bz))) {
				continue;
			}
			return y - 1 + top;
		}
		return Double.NaN;
	}

	/**
	 * One tick of his travel by {@code step} (blocks, flat): he tears through what is in the lane ({@link #plow}), the ground's
	 * height is followed, and he sinks into swimming when he moves or rises on to his legs when he stops. He walks into no
	 * wall: whatever he cannot break he swims under.
	 */
	private static void travel(BeamEntity beam, ServerWorld world, Ride ride, Vec3d step, boolean keyDown) {
		double x = beam.getX() + step.x;
		double z = beam.getZ() + step.z;
		if (!world.isChunkLoaded(MathHelper.floor(x) >> 4, MathHelper.floor(z) >> 4)) {
			// Past the edge of what is loaded: he stops, rather than load it.
			ride.speed = 0.0;
			x = beam.getX();
			z = beam.getZ();
			step = Vec3d.ZERO;
		}
		Vec3d ahead = beam.heading();
		double there = beam.getY();
		if (Double.isNaN(ride.surf)) {
			ride.surf = beam.getY();
		}
		boolean blocked = false;
		if (step.lengthSquared() > 1.0E-6) {
			blocked = plow(beam, world, ride, x, z, ahead);
		}
		// The ground under his middle, ahead of him and behind him, averaged and followed smoothly.
		double s0 = surfaceAt(world, x, z, ride.surf);
		double s1 = surfaceAt(world, x + ahead.x * 3.0, z + ahead.z * 3.0, ride.surf);
		double s2 = surfaceAt(world, x - ahead.x * 3.0, z - ahead.z * 3.0, ride.surf);
		double sum = 0.0;
		double weight = 0.0;
		if (!Double.isNaN(s0)) {
			sum += s0 * 0.5;
			weight += 0.5;
		}
		if (!Double.isNaN(s1)) {
			sum += s1 * 0.25;
			weight += 0.25;
		}
		if (!Double.isNaN(s2)) {
			sum += s2 * 0.25;
			weight += 0.25;
		}
		// (Over the hole he has just blown in the ground he keeps the height he left it at.)
		if (weight > 0.0 && ride.amb != 1) {
			double target = sum / weight;
			ride.surf += MathHelper.clamp((target - ride.surf) * 0.3, -0.7, 0.5);
		}
		double rise = !Double.isNaN(s1) && !Double.isNaN(s2) ? (s1 - s2) / 6.0 : ride.slope;
		ride.slope += (rise - ride.slope) * 0.25;
		// Swimming or standing.
		boolean moving = ride.speed > SWIM_FROM || keyDown || ride.charge >= 0;
		boolean still = ride.speed < STAND_BELOW && !keyDown && ride.charge < 0;
		if (ride.leap < 0) {
			if (moving) {
				ride.swim = Math.min(1.0, ride.swim + SWIM_RATE);
			} else if (still) {
				ride.swim = Math.max(0.0, ride.swim - SWIM_RATE);
			}
		}
		ride.under += ((blocked ? 1.0 : 0.0) - ride.under) * 0.2;
		beam.setPosition(x, there, z);
	}

	/**
	 * What he does to the lane he swims in, ahead of the move to {@code x}, {@code z}: every block of it that is solid (from the
	 * ground up to the height of the rider) and not too hard is broken, nearest first, no more than {@link #PLOW_CAP} in a
	 * tick; a few fly out as debris. With {@code mobGriefing} off nothing is broken. Whether something in his way is left
	 * (unbroken: he swims lower, under it).
	 */
	private static boolean plow(BeamEntity beam, ServerWorld world, Ride ride, double x, double z, Vec3d ahead) {
		boolean grief = world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
		Vec3d left = new Vec3d(ahead.z, 0.0, -ahead.x);
		int floor = MathHelper.ceil(ride.surf - 0.02);
		// The rider's head is how high the lane reaches; in a breach, his back and the rider with it.
		int tall = Math.max(3, (int) Math.ceil(beam.getY() + BeamEntity.BACK + 1.9 - floor));
		List<BlockPos> hit = new ArrayList<>();
		boolean left_ = false;
		int reach = (int) Math.ceil(PLOW_AHEAD + 1.0);
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int bx = MathHelper.floor(x) + dx;
				int bz = MathHelper.floor(z) + dz;
				double rx = bx + 0.5 - x;
				double rz = bz + 0.5 - z;
				double d = rx * ahead.x + rz * ahead.z;
				double a = rx * left.x + rz * left.z;
				if (d < -PLOW_BEHIND || d > PLOW_AHEAD || Math.abs(a) > PLOW_WIDE) {
					continue;
				}
				if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
					continue;
				}
				// The ground of a column he can climb (a step, a hill, a low wall) is ground to him, not in his way: what is above it is.
				double ground = surfaceAt(world, bx + 0.5, bz + 0.5, ride.surf);
				int from = Double.isNaN(ground) ? floor : Math.max(floor, MathHelper.ceil(ground - 0.02));
				// His snout and the front of him are low; the rider's height is where his back is.
				int height = d > 3.4 ? 2 : tall;
				for (int y = from; y < floor + height; y++) {
					BlockState state = world.getBlockState(pos.set(bx, y, bz));
					if (state.isAir() || state.getCollisionShape(world, pos).isEmpty()) {
						continue;
					}
					if (grief && breakable(world, pos, state)) {
						hit.add(pos.toImmutable());
					} else if (d > -1.5 && d < 3.5 && y < floor + 3) {
						left_ = true;
					}
				}
			}
		}
		if (!hit.isEmpty()) {
			Vec3d at = new Vec3d(x, ride.surf, z);
			hit.sort((p, q) -> Double.compare(p.toCenterPos().squaredDistanceTo(at), q.toCenterPos().squaredDistanceTo(at)));
			int debris = 0;
			Vec3d out = ahead.multiply(0.55);
			for (int i = 0; i < hit.size() && i < PLOW_CAP; i++) {
				BlockPos p = hit.get(i);
				BlockState state = world.getBlockState(p);
				if (debris < DEBRIS_PER_TICK && ride.debris.size() < DEBRIS_MAX && world.random.nextInt(3) == 0 && !state.hasBlockEntity()
						&& state.isFullCube(world, p) && !(state.getBlock() instanceof net.minecraft.block.FallingBlock)) {
					debris++;
					world.syncWorldEvent(2001, p, Block.getRawIdFromState(state));
					FallingBlockEntity piece = FallingBlockEntity.spawnFromBlock(world, p, state);
					piece.dropItem = false;
					piece.setDestroyedOnLanding();
					double side = (world.random.nextDouble() - 0.5) * 1.1;
					piece.setVelocity(out.x + left.x * side, 0.45 + world.random.nextDouble() * 0.45, out.z + left.z * side);
					piece.velocityModified = true;
					ride.debris.add(new Debris(piece, world.getTime() + DEBRIS_LASTS));
				} else {
					world.breakBlock(p, false, beam);
				}
			}
			if (hit.size() > PLOW_CAP) {
				left_ = true;
			}
		}
		return left_;
	}

	/** Whether he can break it: not bedrock or obsidian and the like, nothing that cannot be broken at all. */
	private static boolean breakable(ServerWorld world, BlockPos pos, BlockState state) {
		float hardness = state.getHardness(world, pos);
		return hardness >= 0.0F && hardness < TOO_HARD;
	}

	/** The pieces of wall that have lived their time (or landed) are gone. */
	private static void sweep(Ride ride, ServerWorld world) {
		if (ride.debris.isEmpty()) {
			return;
		}
		long now = world.getTime();
		ride.debris.removeIf(d -> {
			if (d.block().isRemoved()) {
				return true;
			}
			if (now >= d.until()) {
				d.block().discard();
				return true;
			}
			return false;
		});
	}

	/** Every piece of wall about gone at once. */
	private static void sweepAll(Ride ride) {
		for (Debris d : ride.debris) {
			d.block().discard();
		}
		ride.debris.clear();
	}

	/**
	 * Puts him where his swim, his porpoise, his arc, his lunge and his leap say, tilted along the slope of the ground and the
	 * rise of his porpoise, and tells the clients how far he is sunk.
	 */
	private static void pose(BeamEntity beam, ServerWorld world, Ride ride, long now) {
		double frac = MathHelper.clamp(ride.speed / SPEED, 0.0, 1.0);
		double sunk = ride.swim * ride.swim * (3.0 - 2.0 * ride.swim);
		ride.wave += WAVE_REST + WAVE_FAST * frac;
		double bob = BOB * Math.sin(ride.wave) * sunk * frac;
		double bobSlope = BOB * Math.cos(ride.wave) * (WAVE_REST + WAVE_FAST * frac) * sunk * frac;
		// At full speed, now and then, an arc out of the ground and back in.
		double arcUp = 0.0;
		double arcSlope = 0.0;
		if (ride.arc < 0 && ride.leap < 0 && ride.charge < 0 && ride.amb < 0 && ride.held == null && sunk > 0.99 && frac > 0.85 && now >= ride.nextArc) {
			if (ride.nextArc == 0L) {
				ride.nextArc = now + 25;
			} else {
				ride.arc = 0;
				ride.nextArc = now + ARC_EVERY + world.random.nextInt(ARC_SPREAD);
			}
		}
		if (ride.arc >= 0) {
			double k = (ride.arc + 1.0) / ARC_TICKS;
			arcUp = ARC_UP * Math.sin(Math.PI * k);
			arcSlope = ARC_UP * Math.PI / ARC_TICKS * Math.cos(Math.PI * k);
			ride.arc++;
			if (ride.arc >= ARC_TICKS) {
				ride.arc = -1;
			}
		}
		// The lunge of a bite, and the rise of a charge: up out of the ground to where his jaws reach.
		double lunge = 0.0;
		if (ride.lunge >= 0) {
			lunge = Math.sin(Math.PI * (ride.lunge + 1.0) / LUNGE_TICKS) * 0.95;
			ride.lunge++;
			if (ride.lunge >= LUNGE_TICKS) {
				ride.lunge = -1;
			}
		}
		if (ride.charge >= 0) {
			lunge = Math.max(lunge, Math.sin(Math.PI * Math.min(1.0, (ride.charge + 1.0) / CHARGE_TICKS)) * 0.7);
		}
		// Holding something up in his jaws he rises out of the ground to do it.
		ride.rise += ((ride.held != null ? 1.0 : 0.0) - ride.rise) * 0.4;
		lunge = Math.max(lunge, GRAB_RISE * ride.rise);
		// Digging in to rush under a mob, or bursting up out of the hole he made.
		ride.dip += ((ride.amb == 0 ? AMBUSH_DIP : 0.0) - ride.dip) * (ride.amb == 0 ? 0.5 : 0.6);
		double depth = SINK * sunk * (1.0 - lunge) + 0.9 * ride.under * sunk + ride.dip * sunk;
		double leapUp = ride.leap >= 0 ? ride.lift : ride.amb == 1 ? ride.ambLift : 0.0;
		double y = ride.surf - depth + bob + arcUp + leapUp;
		beam.setPosition(beam.getX(), y, beam.getZ());
		// Tilted: up the slope of the ground, with the rise of his porpoise and his arc, and in a breach along the way he flies.
		double slope = Math.toDegrees(Math.atan(ride.slope));
		double tilt = -slope - Math.toDegrees(Math.atan2(bobSlope + arcSlope, Math.max(0.4, ride.speed)));
		if (ride.leap >= 0) {
			tilt = -Math.toDegrees(Math.atan2(ride.liftV, Math.max(0.3, ride.speed))) * 0.85;
		} else if (ride.amb == 1) {
			// Nose up out of the hole, and over and down into it.
			tilt = -Math.toDegrees(Math.atan2(ride.ambV, 0.5)) * 0.75;
		} else if (ride.amb == 0) {
			tilt = 12.0;
		}
		beam.setPitch(beam.getPitch() + (float) (MathHelper.clamp(tilt, -48.0, 48.0) - beam.getPitch()) * 0.45F);
		beam.setSwimming((float) ride.swim);
		double above = y - ride.surf;
		beam.setAirborne(above > 0.4 || ride.leap >= 0 || ride.amb == 1);
		// Out of the ground: a spray where the head comes out, and again where he goes back in.
		boolean out = above > -0.5;
		if (out != ride.out && sunk > 0.5 && ride.leap < 0 && ride.amb < 0) {
			double x = beam.getX() + beam.heading().x * (out ? 3.0 : -1.0);
			double z = beam.getZ() + beam.heading().z * (out ? 3.0 : -1.0);
			burst(world, x, ride.surf, z, out ? 40 : 55, 1.0, 0.4);
			world.playSound(null, x, ride.surf, z, ChainsawSounds.BEAM_DIVE, SoundCategory.PLAYERS, 0.8F, out ? 1.5F : 1.3F);
		}
		ride.out = out;
	}

	/**
	 * The wake: as he swims, the ground's own blocks spray up in a V behind his head and along his fin, with dirt kicked up
	 * and a cloud of its colour over him.
	 */
	private static void wake(BeamEntity beam, ServerWorld world, Ride ride) {
		double frac = MathHelper.clamp(ride.speed / SPEED, 0.0, 1.0);
		double sunk = ride.swim;
		if (sunk < 0.4 || frac < 0.1 || ride.leap >= 0 || ride.amb == 1 || Double.isNaN(ride.surf)) {
			return;
		}
		double amount = sunk * frac;
		Vec3d ahead = beam.heading();
		Vec3d left = new Vec3d(ahead.z, 0.0, -ahead.x);
		double cx = beam.getX();
		double cz = beam.getZ();
		double y = ride.surf + 0.1;
		ParticleEffect dirt = spray(world, cx, ride.surf, cz);
		var random = world.random;
		// The V: two sheets of ground thrown up and out from either side of his head, curling back.
		int bits = 2 + (int) Math.round(7.0 * amount);
		for (int side = -1; side <= 1; side += 2) {
			for (int i = 0; i < bits; i++) {
				double along = 4.0 - random.nextDouble() * 1.5;
				double out = 0.4 + random.nextDouble() * 0.9;
				double px = cx + ahead.x * along + left.x * side * out;
				double pz = cz + ahead.z * along + left.z * side * out;
				double vx = left.x * side * (0.14 + 0.2 * frac) - ahead.x * (0.12 + 0.16 * frac);
				double vz = left.z * side * (0.14 + 0.2 * frac) - ahead.z * (0.12 + 0.16 * frac);
				world.spawnParticles(dirt, px, y, pz, 0, vx + (random.nextDouble() - 0.5) * 0.08, 0.34 + random.nextDouble() * 0.36,
						vz + (random.nextDouble() - 0.5) * 0.08, 1.0);
			}
			// Along his flank: the ground heaved up beside his back.
			for (int i = 0; i < 2 + (int) Math.round(2.0 * amount); i++) {
				double along = 2.2 - random.nextDouble() * 5.0;
				double out = 1.2 + random.nextDouble() * 0.7;
				world.spawnParticles(dirt, cx + ahead.x * along + left.x * side * out, y, cz + ahead.z * along + left.z * side * out, 0,
						left.x * side * (0.08 + 0.1 * random.nextDouble()), 0.24 + random.nextDouble() * 0.3, left.z * side * (0.08 + 0.1 * random.nextDouble()), 1.0);
			}
		}
		// The fin: a spray along his back, and the churn behind his tail.
		for (int i = 0; i < 3; i++) {
			double along = 0.8 - random.nextDouble() * 2.8;
			double px = cx + ahead.x * along + (random.nextDouble() - 0.5) * 0.8 * left.x;
			double pz = cz + ahead.z * along + (random.nextDouble() - 0.5) * 0.8 * left.z;
			world.spawnParticles(dirt, px, y, pz, 0, left.x * (random.nextDouble() - 0.5) * 0.14, 0.26 + random.nextDouble() * 0.2,
					left.z * (random.nextDouble() - 0.5) * 0.14, 1.0);
		}
		world.spawnParticles(dirt, cx - ahead.x * 4.3, y, cz - ahead.z * 4.3, 4 + (int) (6 * amount), 0.9, 0.1, 0.9, 0.2);
		// Clouds of the ground's own colour thrown up at his head and left hanging behind his tail.
		BlockPos under = BlockPos.ofFloored(cx, ride.surf - 0.5, cz);
		int rgb = world.getBlockState(under).getMapColor(world, under).color;
		DustParticleEffect cloud = new DustParticleEffect(new Vector3f(((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F), 3.4F);
		world.spawnParticles(cloud, cx + ahead.x * 3.6, y + 0.4, cz + ahead.z * 3.6, 2 + (int) (4 * amount), 1.1, 0.3, 1.1, 0.03);
		world.spawnParticles(cloud, cx - ahead.x * 3.6, y + 0.3, cz - ahead.z * 3.6, 1 + (int) (3 * amount), 0.8, 0.2, 0.8, 0.02);
	}

	// The bite.

	/** The place of his mouth: ahead of his middle, at the height of the gape (as if he stood on the ground: swimming, he lunges up to it). */
	public static Vec3d mouth(BeamEntity beam) {
		double feet = Math.max(beam.getY(), beam.ride().surface(beam.getY()));
		return new Vec3d(beam.getX(), feet, beam.getZ()).add(beam.heading().multiply(MOUTH)).add(0.0, MOUTH_HEIGHT, 0.0);
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
	 * His bite (the attack key, from his back): he lunges up out of the ground and his jaw snaps shut on everything in a cone
	 * in front of his mouth, for {@link #BITE_DAMAGE} of the rider's damage, in a spray of blood. A mob no bigger than a Ravager that
	 * he catches is held and thrashed ({@link #grab}); the rest are flung up and forward. Swimming, with a mob too far ahead to
	 * bite, it is the ambush instead ({@link #ambush}). No more often than every {@link #BITE_EVERY} ticks. Whether it came.
	 */
	public static boolean bite(ServerPlayerEntity player) {
		BeamEntity beam = mount(player);
		if (beam == null || beam.age < POP_TICKS) {
			return false;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		Ride ride = beam.ride();
		if (now - ride.lastBite < BITE_EVERY - LAG_SLACK || ride.held != null || ride.amb >= 0) {
			return false;
		}
		Vec3d mouth = mouth(beam);
		List<LivingEntity> victims = within(beam, world, player, mouth, BITE_REACH, true);
		if (victims.isEmpty() && ride.swim > 0.5 && ride.leap < 0 && ride.charge < 0 && now - ride.lastAmbush >= AMBUSH_EVERY) {
			LivingEntity prey = ambushPrey(beam, world, player);
			if (prey != null) {
				ambush(beam, world, ride, player, prey);
				return true;
			}
		}
		ride.lastBite = now;
		ride.lunge = 0;
		beam.bit();
		hurt(world, player, victims, BITE_DAMAGE);
		Vec3d ahead = beam.heading();
		LivingEntity caught = null;
		for (LivingEntity victim : victims) {
			if (victim.isAlive() && grabbable(victim) && (caught == null || victim.squaredDistanceTo(mouth) < caught.squaredDistanceTo(mouth))) {
				caught = victim;
			}
		}
		for (LivingEntity victim : victims) {
			if (victim.isAlive() && victim != caught) {
				fling(victim, ahead.x * FLING_AHEAD, FLING_UP, ahead.z * FLING_AHEAD);
			}
		}
		if (caught != null) {
			grab(beam, ride, caught);
		}
		world.playSound(null, mouth.x, mouth.y, mouth.z, ChainsawSounds.BEAM_BITE, SoundCategory.PLAYERS, victims.isEmpty() ? 0.9F : 1.3F,
				0.9F + world.random.nextFloat() * 0.2F);
		if (!victims.isEmpty()) {
			hit(ride, player, caught != null ? 2 : 1, 0, mouth);
		}
		return true;
	}

	/** Whether his jaws can hold it: a mob (not a player) no bigger than a Ravager. */
	public static boolean grabbable(LivingEntity target) {
		return !(target instanceof PlayerEntity) && target.getWidth() <= GRAB_WIDTH && target.getHeight() <= GRAB_HEIGHT && !target.hasPassengers();
	}

	/** {@code rider} told that a hit of {@code level} (1 to 4) landed at {@code at}, with the {@code extras} ({@link #FLASH}, {@link #CHOP}): their screen shakes and holds still a frame or more. */
	private static void hit(Ride ride, ServerPlayerEntity rider, int level, int extras, Vec3d at) {
		ride.hits++;
		ride.hardest = Math.max(ride.hardest, level);
		if (rider != null && ServerPlayNetworking.canSend(rider, FormEventPayload.ID)) {
			ServerPlayNetworking.send(rider, new FormEventPayload(rider.getUuid(), FormEventPayload.BEAM_HIT, level | extras, at.x, at.y, at.z));
		}
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

	// The ambush.

	/**
	 * The mob he rushes under: the nearest to the way you look among those too far for a bite (up to {@link #AMBUSH_REACH} blocks
	 * from his mouth, within about 45 degrees of your look), standing on the ground (not flying). Null if there is none.
	 */
	private static LivingEntity ambushPrey(BeamEntity beam, ServerWorld world, ServerPlayerEntity rider) {
		Vec3d mouth = mouth(beam);
		Vec3d look = rider.getRotationVec(1.0F);
		Vec3d flat = new Vec3d(look.x, 0.0, look.z);
		flat = flat.lengthSquared() < 1.0E-4 ? beam.heading() : flat.normalize();
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : within(beam, world, rider, mouth, AMBUSH_REACH, false)) {
			Vec3d to = e.getPos().subtract(mouth).multiply(1.0, 0.0, 1.0);
			double distance = to.length();
			if (distance < BITE_REACH - 0.6 || distance > AMBUSH_REACH) {
				continue;
			}
			double cos = to.normalize().dotProduct(flat);
			if (cos < AMBUSH_CONE) {
				continue;
			}
			double ground = surfaceAt(world, e.getX(), e.getZ(), e.getY());
			if (Double.isNaN(ground) || Math.abs(e.getY() - ground) > 1.3) {
				continue;
			}
			double score = (1.0 - cos) * 10.0 + distance * 0.2;
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}

	/** The ambush begun: he dips deeper and rushes under {@code prey}. */
	private static void ambush(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity rider, LivingEntity prey) {
		long now = world.getTime();
		ride.amb = 0;
		ride.prey = prey.getUuid();
		ride.ambTicks = 0;
		ride.ambCrashed = false;
		ride.lastAmbush = now;
		ride.lastBite = now;
		ride.lunge = -1;
		ride.arc = -1;
		world.playSound(null, beam.getX(), ride.surface(beam.getY()), beam.getZ(), ChainsawSounds.BEAM_DIVE, SoundCategory.PLAYERS, 1.2F, 1.6F);
		burst(world, beam.getX(), ride.surface(beam.getY()), beam.getZ(), 30, 1.0, 0.35);
	}

	/** One tick of the rush: turned to the prey and sent at it, the way it moves this tick; he erupts when his head is under it. Null if the ambush is over (the prey gone or too far). */
	private static Vec3d rushStep(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity rider) {
		ride.ambTicks++;
		LivingEntity prey = ride.prey != null && world.getEntity(ride.prey) instanceof LivingEntity l && l.isAlive() ? l : null;
		if (prey == null || ride.ambTicks > AMBUSH_MAX) {
			ride.amb = -1;
			ride.prey = null;
			return null;
		}
		Vec3d to = prey.getPos().subtract(beam.getPos()).multiply(1.0, 0.0, 1.0);
		double distance = to.length();
		if (distance > 1.0E-3) {
			float want = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
			beam.setYaw(beam.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(want - beam.getYaw()), -AMBUSH_TURN, AMBUSH_TURN));
		}
		double gap = distance - AMBUSH_UNDER;
		ride.speed = AMBUSH_SPEED;
		if (gap <= AMBUSH_SPEED * 0.4 && ride.ambTicks >= AMBUSH_MIN) {
			erupt(beam, world, ride, rider, prey);
			return beam.heading().multiply(0.3);
		}
		return beam.heading().multiply(Math.min(AMBUSH_SPEED, Math.max(gap, 0.3)));
	}

	/**
	 * He bursts up out of the ground under {@code prey}: it and everything near it hurt and thrown sky-high, a ring of the ground
	 * thrown up round the hole, and he rises over it on the same burst.
	 */
	private static void erupt(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity rider, LivingEntity prey) {
		long now = world.getTime();
		Vec3d at = prey.getPos();
		double ground = Double.isNaN(surfaceAt(world, at.x, at.z, at.y)) ? at.y : surfaceAt(world, at.x, at.z, at.y);
		ride.amb = 1;
		ride.ambTicks = 0;
		ride.ambLift = 0.0;
		ride.ambV = AMBUSH_LIFT;
		ride.ambCrashed = false;
		ride.lastAmbush = now;
		ride.lastBite = now;
		ride.prey = null;
		beam.ambushed();
		beam.bit();
		Vec3d ahead = beam.heading();
		List<LivingEntity> victims = within(beam, world, rider, at.add(0.0, 0.4, 0.0), AMBUSH_RADIUS, false);
		if (!victims.contains(prey)) {
			victims.add(prey);
		}
		hurt(world, rider, victims, AMBUSH_DAMAGE);
		for (LivingEntity victim : victims) {
			if (victim.isAlive()) {
				double weight = victim == prey ? 1.0 : 0.75;
				fling(victim, ahead.x * 0.3, AMBUSH_LAUNCH * weight, ahead.z * 0.3);
			}
			Saw.bleed(world, victim, 50);
		}
		ringDebris(world, ride, new Vec3d(at.x, ground, at.z));
		ring(world, new Vec3d(at.x, ground, at.z), 3.6);
		burst(world, at.x, ground, at.z, 160, 1.8, 0.7);
		world.playSound(null, at.x, ground, at.z, ChainsawSounds.BEAM_SLAM, SoundCategory.PLAYERS, 2.2F, 1.0F);
		world.playSound(null, at.x, ground, at.z, ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 1.6F, 0.8F);
		world.playSound(null, at.x, ground + 1.0, at.z, ChainsawSounds.BEAM_BITE, SoundCategory.PLAYERS, 1.5F, 0.75F);
		hit(ride, rider, 4, FLASH, at.add(0.0, 1.0, 0.0));
	}

	/** One tick of the ambush after he has moved: over the hole he flies up and comes down, crashing in and diving on. */
	private static void ambushTick(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity rider) {
		if (ride.amb != 1) {
			return;
		}
		ride.ambTicks++;
		ride.ambLift += ride.ambV;
		ride.ambV -= AMBUSH_G;
		double y = ride.surf - SINK * ride.swim * ride.swim * (3.0 - 2.0 * ride.swim) + ride.ambLift;
		if (!ride.ambCrashed && ride.ambV < 0.0 && y < ride.surf + 0.2) {
			ride.ambCrashed = true;
			land(beam, world, rider);
		}
		if (ride.ambLift <= 0.0 && ride.ambV < 0.0 || ride.ambTicks > 80) {
			ride.amb = -1;
			ride.ambLift = 0.0;
			if (!ride.ambCrashed) {
				ride.ambCrashed = true;
				land(beam, world, rider);
			}
		}
	}

	/**
	 * A ring of the ground's blocks thrown up round {@code at} as flying debris (where {@code mobGriefing} lets them be torn up; else
	 * only the ground's particles, which {@code ring} throws), each landing and gone in a moment.
	 */
	private static void ringDebris(ServerWorld world, Ride ride, Vec3d at) {
		if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
			return;
		}
		var random = world.random;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int i = 0; i < RING_PIECES; i++) {
			double angle = Math.PI * 2.0 * (i + random.nextDouble() * 0.7) / RING_PIECES;
			double radius = 1.5 + random.nextDouble() * 1.7;
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			int bx = MathHelper.floor(at.x + dx * radius);
			int bz = MathHelper.floor(at.z + dz * radius);
			if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
				continue;
			}
			BlockPos found = null;
			for (int y = MathHelper.floor(at.y) + 1; y >= MathHelper.floor(at.y) - 2; y--) {
				pos.set(bx, y, bz);
				if (!world.getBlockState(pos).isAir() && world.getBlockState(pos.up()).isAir()) {
					found = pos.toImmutable();
					break;
				}
			}
			if (found == null) {
				continue;
			}
			BlockState state = world.getBlockState(found);
			if (!breakable(world, found, state) || state.hasBlockEntity() || !state.isFullCube(world, found) || state.getBlock() instanceof net.minecraft.block.FallingBlock) {
				continue;
			}
			world.syncWorldEvent(2001, found, Block.getRawIdFromState(state));
			FallingBlockEntity piece = FallingBlockEntity.spawnFromBlock(world, found, state);
			piece.dropItem = false;
			piece.setDestroyedOnLanding();
			double out = 0.18 + random.nextDouble() * 0.32;
			piece.setVelocity(dx * out, 0.55 + random.nextDouble() * 0.55, dz * out);
			piece.velocityModified = true;
			ride.debris.add(new Debris(piece, world.getTime() + RING_LASTS));
		}
	}

	// The grab.

	/** His jaws hold {@code prey}: it rides at his mouth (a passenger, swung with his head) for the thrash. */
	private static void grab(BeamEntity beam, Ride ride, LivingEntity prey) {
		prey.startRiding(beam, true);
		ride.held = prey.getUuid();
		ride.grabTicks = 0;
		beam.grabbed();
		beam.bit();
	}

	/**
	 * One tick of the thrash: at the end of each swing of his head what he holds takes {@link #SHAKE_DAMAGE} in a spray of blood;
	 * when the {@link BeamEntity#GRAB_TICKS} are up he flings it the way you look.
	 */
	private static void grabTick(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity rider) {
		LivingEntity prey = world.getEntity(ride.held) instanceof LivingEntity l && l.isAlive() && l.getVehicle() == beam ? l : null;
		if (prey == null) {
			releaseHeld(beam, ride);
			return;
		}
		int t = (int) beam.sinceGrab(0.0F);
		for (int k = ride.grabTicks; k <= t; k++) {
			for (int at : SHAKES_AT) {
				if (k == at && prey.isAlive()) {
					hurt(world, rider, List.of(prey), SHAKE_DAMAGE);
					Saw.bleed(world, prey, 36);
					Vec3d mid = prey.getBoundingBox().getCenter();
					world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.BEAM_BITE, SoundCategory.PLAYERS, 1.1F, 0.65F + world.random.nextFloat() * 0.2F);
					world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.CRUNCH, SoundCategory.PLAYERS, 0.9F, 0.8F + world.random.nextFloat() * 0.3F);
					hit(ride, rider, 2, 0, mid);
				}
			}
		}
		ride.grabTicks = t + 1;
		if (!prey.isAlive()) {
			releaseHeld(beam, ride);
			return;
		}
		if (t >= BeamEntity.GRAB_TICKS) {
			throwPrey(beam, world, ride, rider, prey);
		}
	}

	/** Lets go of what he holds (if he holds anything). */
	private static void releaseHeld(BeamEntity beam, Ride ride) {
		if (ride.held != null && beam.getWorld() instanceof ServerWorld world && world.getEntity(ride.held) instanceof LivingEntity held && held.getVehicle() == beam) {
			held.stopRiding();
		}
		ride.held = null;
		ride.grabTicks = 0;
	}

	/** The end of the thrash: he throws it the way {@code rider} looks, far, for {@link #FLING_DAMAGE}. */
	private static void throwPrey(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity rider, LivingEntity prey) {
		releaseHeld(beam, ride);
		ride.lastBite = world.getTime();
		beam.bit();
		hurt(world, rider, List.of(prey), FLING_DAMAGE);
		Vec3d look = rider.getRotationVec(1.0F);
		Vec3d way = new Vec3d(look.x, look.y * 0.7 + 0.16, look.z).normalize().multiply(FLING_SPEED);
		if (prey.isAlive()) {
			fling(prey, way.x, way.y, way.z);
		}
		Vec3d mid = prey.getBoundingBox().getCenter();
		Saw.bleed(world, prey, 60);
		Saw.impact(rider, prey);
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.BEAM_BITE, SoundCategory.PLAYERS, 1.6F, 0.6F);
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.BEAM_LAUNCH, SoundCategory.PLAYERS, 1.4F, 1.2F);
		hit(ride, rider, 3, FLASH, mid);
	}

	// The rider launch.

	/**
	 * The chain key while you ride: he flicks his head and throws you like a missile the way you look ({@link #LAUNCH_SPEED} blocks a
	 * tick for {@link #LAUNCH_TICKS} ticks, saws out), and swims to wherever you land. Whether it came.
	 */
	public static boolean launch(ServerPlayerEntity player) {
		BeamEntity beam = mount(player);
		if (beam == null || beam.age < POP_TICKS) {
			return false;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		Ride ride = beam.ride();
		if (now - ride.lastLaunch < LAUNCH_EVERY || ride.held != null || ride.amb >= 0 || ride.charge >= 0 || ride.leap >= 0) {
			return false;
		}
		Vec3d look = player.getRotationVec(1.0F);
		Vec3d way = new Vec3d(look.x, look.y * 0.75, look.z).normalize();
		ride.lastLaunch = now;
		ride.loose = true;
		ride.looseSince = now;
		ride.launch = 0;
		ride.launchWay = way;
		ride.launchPos = player.getPos();
		ride.sliced.clear();
		ride.hadNoGravity = player.hasNoGravity();
		beam.launched();
		beam.bit();
		player.stopRiding();
		player.setNoGravity(true);
		player.setVelocity(way.multiply(LAUNCH_SPEED));
		player.velocityModified = true;
		player.fallDistance = 0.0F;
		SAFE.put(player.getUuid(), now + SAFE_FOR + LAUNCH_TICKS);
		world.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), ChainsawSounds.BEAM_LAUNCH, SoundCategory.PLAYERS, 1.8F, 1.0F);
		world.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), ChainsawSounds.SLASH, SoundCategory.PLAYERS, 1.2F, 0.8F);
		burst(world, beam.getX(), ride.surface(beam.getY()), beam.getZ(), 40, 1.0, 0.4);
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.SWING, 3));
		return true;
	}

	/**
	 * One tick of Beam with his rider off him: the rider's flight goes on (cutting what it hits), and he swims to them; once they are down
	 * and he is under them he takes them back up. If it takes too long, or they are too far, or they have left the form, he dives.
	 */
	private static void looseTick(BeamEntity beam, ServerWorld world, Ride ride) {
		long now = world.getTime();
		ServerPlayerEntity owner = beam.owner() == null ? null : world.getServer().getPlayerManager().getPlayer(beam.owner());
		boolean gone = owner == null || !owner.isAlive() || owner.isSpectator() || !Form.fighting(owner) || beam.age >= LASTS || owner.getWorld() != world
				|| now - ride.looseSince > FETCH_TICKS || owner.getPos().distanceTo(beam.getPos()) > FETCH_RANGE;
		if (gone) {
			if (owner != null) {
				endFlight(ride, owner);
			}
			ride.loose = false;
			dive(beam, world, null);
			return;
		}
		if (ride.launch >= 0) {
			flightTick(beam, world, ride, owner);
		}
		// Swimming to them.
		Vec3d to = owner.getPos().subtract(beam.getPos()).multiply(1.0, 0.0, 1.0);
		double distance = to.length();
		if (distance > 1.0E-3) {
			float want = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
			beam.setYaw(beam.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(want - beam.getYaw()), -TURN * 2.0F, TURN * 2.0F));
		}
		boolean near = distance < 3.0;
		ride.speed += MathHelper.clamp((near ? Math.min(FETCH_SPEED, distance * 0.5) : FETCH_SPEED) - ride.speed, -BRAKE * 2.0, ACCELERATE * 2.0);
		// (Close in he goes straight at them, however he faces, so he cannot circle.)
		Vec3d step = distance < 10.0 && distance > 1.0E-3 ? to.multiply(Math.min(ride.speed, distance) / distance)
				: beam.heading().multiply(Math.min(ride.speed, Math.max(distance, 0.2)));
		travel(beam, world, ride, step, true);
		pose(beam, world, ride, now);
		wake(beam, world, ride);
		if (ride.launch < 0 && distance <= PICK_UP && owner.getY() - ride.surf > -2.0 && owner.getY() - ride.surf < 4.0 && !owner.hasVehicle()) {
			pickUp(beam, world, ride, owner);
		}
	}

	/**
	 * The flight of the thrown rider: along the way they were sent at {@link #LAUNCH_SPEED}, the saws cutting everything in a wide
	 * path (once each, for the finisher's damage), stopped by a wall; over after {@link #LAUNCH_TICKS}. The path is followed here as well
	 * as by the rider's own game, so the cut does not depend on lag.
	 */
	private static void flightTick(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity owner) {
		ride.launch++;
		Vec3d from = ride.launchPos;
		Vec3d to = from.add(ride.launchWay.multiply(LAUNCH_SPEED));
		boolean stopped = false;
		BlockHitResult wall = world.raycast(new RaycastContext(from.add(0.0, 0.9, 0.0), to.add(0.0, 0.9, 0.0), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, owner));
		if (wall.getType() != HitResult.Type.MISS) {
			to = wall.getPos().subtract(0.0, 0.9, 0.0).subtract(ride.launchWay.multiply(0.4));
			stopped = true;
		}
		Box path = new Box(from, to).expand(LAUNCH_WIDE, 0.0, LAUNCH_WIDE).stretch(0.0, 1.8, 0.0).expand(0.0, 0.5, 0.0);
		List<LivingEntity> cut = new ArrayList<>();
		for (Entity e : world.getOtherEntities(owner, path, e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity)
				&& !ride.sliced.contains(e.getUuid()))) {
			if (e instanceof ServerPlayerEntity other && Downed.is(other)) {
				continue;
			}
			cut.add((LivingEntity) e);
		}
		float damage = Saw.FINISHER_DAMAGE * Frenzy.damage(owner) * Limbs.damage(owner);
		for (LivingEntity target : cut) {
			ride.sliced.add(target.getUuid());
			Saw.breakGuard(target);
			Saw.impact(owner, target);
			target.timeUntilRegen = 0;
			target.damage(owner.getDamageSources().playerAttack(owner), damage);
			Saw.bleed(world, target, 50);
			if (target.isAlive()) {
				fling(target, ride.launchWay.x * 1.0, 0.5, ride.launchWay.z * 1.0);
			}
			Vec3d mid = target.getBoundingBox().getCenter();
			hit(ride, owner, 3, FLASH | CHOP, mid);
		}
		if (!cut.isEmpty()) {
			Form.play(owner, ChainsawSounds.HIT, 1.0F);
			Form.broadcast(owner.server, new FormEventPayload(owner.getUuid(), FormEventPayload.HIT, cut.size()));
		}
		ride.launchPos = to;
		owner.setVelocity(ride.launchWay.multiply(LAUNCH_SPEED));
		owner.velocityModified = true;
		owner.fallDistance = 0.0F;
		if (ride.launch >= LAUNCH_TICKS || stopped) {
			endFlight(ride, owner);
		}
	}

	/** The flight over: gravity is theirs again, and a little of the speed. */
	private static void endFlight(Ride ride, ServerPlayerEntity owner) {
		if (ride.launch < 0) {
			return;
		}
		ride.launch = -1;
		owner.setNoGravity(ride.hadNoGravity);
		owner.setVelocity(ride.launchWay.multiply(0.5));
		owner.velocityModified = true;
		owner.fallDistance = 0.0F;
	}

	/** He is under them: he comes up through the ground with them on his back again. */
	private static void pickUp(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity owner) {
		Vec3d ahead = beam.heading();
		beam.setPosition(owner.getX() - ahead.x * BeamEntity.SEAT_AHEAD, owner.getY() - BeamEntity.BACK + 0.05, owner.getZ() - ahead.z * BeamEntity.SEAT_AHEAD);
		ride.loose = false;
		ride.speed *= 0.5;
		owner.setVelocity(Vec3d.ZERO);
		owner.velocityModified = true;
		owner.startRiding(beam, true);
		owner.fallDistance = 0.0F;
		SAFE.put(owner.getUuid(), world.getTime() + SAFE_FOR);
		double surf = ride.surface(beam.getY());
		world.playSound(null, owner.getX(), surf, owner.getZ(), ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 1.4F, 1.1F);
		burst(world, owner.getX(), surf, owner.getZ(), 90, 1.3, 0.5);
		ring(world, new Vec3d(owner.getX(), surf, owner.getZ()), 2.4);
		hit(ride, owner, 2, 0, owner.getPos());
	}

	// The charge.

	/**
	 * The charge (the use key, from his back): he dashes straight ahead, {@link #CHARGE_BLOCKS} blocks in {@link
	 * #CHARGE_TICKS} ticks, biting through everything in the way once each for {@link #CHARGE_DAMAGE} and flinging it aside.
	 * No more often than every {@link #CHARGE_EVERY} ticks, and not in the air. Whether it came.
	 */
	public static boolean charge(ServerPlayerEntity player) {
		BeamEntity beam = mount(player);
		if (beam == null || beam.age < POP_TICKS) {
			return false;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		Ride ride = beam.ride();
		if (now - ride.lastCharge < CHARGE_EVERY || ride.leap >= 0 || ride.charge >= 0 || ride.amb >= 0) {
			return false;
		}
		ride.lastCharge = now;
		ride.charge = 0;
		ride.chargeWay = beam.heading();
		ride.bitten.clear();
		beam.charged();
		world.playSound(null, beam.getX(), beam.getY(), beam.getZ(), ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 1.2F, 1.35F);
		burst(world, beam.getX(), ride.surface(beam.getY()), beam.getZ(), 40, 0.8, 0.35);
		return true;
	}

	/** One tick of the charge, after he has gone on along the way he began: whatever he runs into that he has not bitten yet is bitten, and flung aside. */
	private static void chargeBites(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity rider) {
		ride.charge++;
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
			hit(ride, rider, 2, 0, mouth);
		}
		if (ride.charge >= CHARGE_TICKS) {
			ride.charge = -1;
			ride.speed = SPEED;
		}
	}

	// The leap, a breach.

	/** Begins his breach: out of the ground, up and over and back in, forward the way he faces (at most every {@link #LEAP_EVERY} ticks). Whether it began. */
	public static boolean leap(ServerPlayerEntity player) {
		BeamEntity beam = mount(player);
		if (beam == null || beam.age < POP_TICKS) {
			return false;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		Ride ride = beam.ride();
		if (now - ride.lastLeap < LEAP_EVERY || ride.leap >= 0 || ride.charge >= 0 || ride.amb >= 0) {
			return false;
		}
		ride.leap = 0;
		ride.arc = -1;
		ride.lift = 0.0;
		ride.liftV = LEAP_LIFT;
		ride.crashed = false;
		ride.lastLeap = now;
		ride.nextArc = Math.max(ride.nextArc, now + LEAP_TICKS + 30);
		beam.leapt();
		Vec3d ahead = beam.heading();
		double y = ride.surface(beam.getY());
		world.playSound(null, beam.getX(), y, beam.getZ(), ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 1.3F, 1.15F);
		burst(world, beam.getX() + ahead.x * 2.0, y, beam.getZ() + ahead.z * 2.0, 70, 1.3, 0.5);
		return true;
	}

	/**
	 * One tick of the breach, after he has gone on: up and over and down under the pull of the ground; where he goes back in
	 * there is a crash of spray and a bite on everything round it, and a moment later the breach is over.
	 */
	private static void leapTick(BeamEntity beam, ServerWorld world, Ride ride, ServerPlayerEntity rider) {
		ride.leap++;
		ride.lift += ride.liftV;
		ride.liftV -= LEAP_GRAVITY;
		double level = ride.surf - SINK * ride.swim * ride.swim * (3.0 - 2.0 * ride.swim) + 0.0;
		double y = level + ride.lift;
		if (!ride.crashed && ride.liftV < 0.0 && y < ride.surf + 0.2) {
			ride.crashed = true;
			land(beam, world, rider);
		}
		if (ride.lift <= 0.0 && ride.liftV < 0.0 || ride.leap > LEAP_TICKS * 3) {
			ride.leap = -1;
			ride.lift = 0.0;
			if (!ride.crashed) {
				ride.crashed = true;
				land(beam, world, rider);
			}
		}
	}

	/** Back in: a big spray where he goes in, a ring of the ground thrown up, and a bite of {@link #LEAP_DAMAGE} on everything round where he landed, thrown back. */
	private static void land(BeamEntity beam, ServerWorld world, ServerPlayerEntity rider) {
		Ride ride = beam.ride();
		Vec3d at = new Vec3d(beam.getX(), ride.surface(beam.getY()), beam.getZ());
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
		beam.landed();
		hit(ride, rider, victims.isEmpty() ? 2 : 3, 0, at);
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.BEAM_BURST, SoundCategory.PLAYERS, 1.6F, 0.7F);
		world.playSound(null, at.x, at.y, at.z, ChainsawSounds.BEAM_DIVE, SoundCategory.PLAYERS, 1.4F, 0.9F);
		ring(world, at, LEAP_REACH * 0.9);
		burst(world, at.x, at.y, at.z, 130, 1.6, 0.6);
		Vec3d ahead = beam.heading();
		burst(world, at.x + ahead.x * 3.0, at.y, at.z + ahead.z * 3.0, 90, 1.4, 0.55);
	}

	// Going down.

	/** The dive begun: he and the rider part, and he sinks into the ground. */
	public static void dive(BeamEntity beam, ServerWorld world, ServerPlayerEntity rider) {
		if (beam.diving()) {
			return;
		}
		beam.dived();
		Ride ride = beam.ride();
		releaseHeld(beam, ride);
		if (ride.launch >= 0 && rider != null) {
			endFlight(ride, rider);
		}
		ride.loose = false;
		ride.leap = -1;
		ride.charge = -1;
		ride.amb = -1;
		ride.diveFrom = beam.getY();
		beam.setAirborne(false);
		beam.setSwimming(1.0F);
		beam.removeAllPassengers();
		if (rider != null) {
			rider.fallDistance = 0.0F;
			SAFE.put(rider.getUuid(), world.getTime() + SAFE_FOR);
		}
		double surf = ride.surface(ride.diveFrom);
		world.playSound(null, beam.getX(), surf, beam.getZ(), ChainsawSounds.BEAM_DIVE, SoundCategory.PLAYERS, 1.4F, 1.0F);
		burst(world, beam.getX(), surf, beam.getZ(), 50, 1.0, 0.3);
	}

	/** Beam gone, at once: no dive (for the tests, and a world closing). */
	public static void end(ServerPlayerEntity player) {
		BeamEntity beam = BEAMS.remove(player.getUuid());
		if (beam != null) {
			releaseHeld(beam, beam.ride());
			endFlight(beam.ride(), player);
			sweepAll(beam.ride());
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
			burst(world, beam.getX(), ride.surface(ride.diveFrom), beam.getZ(), 14, 1.0, 0.25);
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
		sweepAll(beam.ride());
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
			sweepAll(beam.ride());
			beam.discard();
		}
		SAFE.remove(id);
	}

	static void clear() {
		BEAMS.clear();
		SAFE.clear();
	}
}
