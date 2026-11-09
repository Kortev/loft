package io.github.kortev.chainsawman.contract;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.network.ForeverLockPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

/**
 * Forever, the Eternity contract's power (kortev's design): press "Use contract" while looking at a living thing and it is locked in
 * an endless moment. It freezes exactly where it was (mid-air too, its AI paused, its animation stopped); every moment of it shows at
 * once, a line of copies of it back along where it came from and on to where it was going ({@link ForeverCopyEntity}); and the
 * Eternity Devil's flesh, eyes and mouths come up out of the ground round it to hold the moment ({@link ForeverHubEntity}, drawn
 * by the clients). Every copy is the target: a hit on the target or on any copy is not applied but stored. When the moment ends
 * ({@link #LASTS} ticks, {@link #BOSS_LASTS} for a boss) the line snaps into the target and every stored hit goes off at once:
 * one hit of the total times {@link #MULT} (at most {@link #CAP}), credited to whoever stored the most, flinging the target away
 * from the one who called it. After the release time catches up with the one who called it: their hunger drops by {@link #HUNGER} (free in
 * creative) and a use is refused at 0 hunger. Holding the contract also keeps you from sleeping ({@code EternityBedMixin}) and from the sleep vote.
 */
public final class Forever {
	/** The price of each use (kortev has not confirmed it: change it here): after the release the hunger drops by this much (a drumstick and a half), free in creative. */
	public static final int HUNGER = 3;
	/** How long the grey drain stays on the one who paid, after the release (ticks). */
	public static final int CATCH_UP = 22;
	/** How long the moment lasts (3 s), and for a boss (1.5 s). */
	public static final int LASTS = 60;
	public static final int BOSS_LASTS = 30;
	/** The cooldown from the release (30 s), and how long after a release the target cannot be locked again (3 s). */
	public static final int COOLDOWN = 600;
	public static final int IMMUNITY = 60;
	/** How far the target may be (blocks). */
	public static final double RANGE = 24.0;
	/** Copies on each side, how far apart (blocks), how many ticks of where a thing has been are kept, and how near a contract holder. */
	public static final int SIDE = 4;
	public static final double SPACING_MIN = 0.9;
	public static final double SPACING_MAX = 1.2;
	public static final int HISTORY = 40;
	public static final double HOLDER_RANGE = 32.0;
	/** The stored hits go off as the total times this, at most {@link #CAP} (20 hearts). */
	public static final float MULT = 1.5F;
	public static final float CAP = 40.0F;
	/** How long the line takes to snap in (ticks), and the flesh to sink back. */
	public static final int SNAP = 4;
	public static final int SINK = 10;
	/** The cube of frozen time: half its side (9 blocks across), and how many things it may hold at once. */
	public static final double CUBE_HALF = 4.5;
	public static final int CAP_LOCKS = 8;
	/** How far up and down a copy may look for ground (blocks). */
	public static final int VERTICAL = 4;

	// ---- The bond (see Bond): what each step changes. Bond 1 is everything above.
	/** Bond 2: the cube is 13 blocks across, the moment lasts 4 s (a boss's 2 s), and the lines run 14 render-only copies each way (about 32 blocks). */
	public static final double CUBE_HALF_BOND2 = 6.5;
	public static final int LASTS_BOND2 = 80;
	public static final int BOSS_LASTS_BOND2 = 40;
	public static final int FAR_COPIES = 11;
	public static final int FAR_COPIES_BOND2 = 14;
	/** Bond 3: the release burst is bigger (the stored hits go off at this multiple, up to the cap, knocked further). */
	public static final float MULT_BOND3 = 2.0F;
	public static final float CAP_BOND3 = 60.0F;
	/** "Not yet" (bond 3): how long you are out of time (1.5 s). */
	public static final int NOT_YET_LASTS = 30;

	/** Half the side of the cube at bond {@code bond}. */
	public static double cubeHalf(int bond) {
		return bond >= 2 ? CUBE_HALF_BOND2 : CUBE_HALF;
	}

	/** How long (ticks) a moment lasts at bond {@code bond}, for a boss or not. */
	public static int lasts(int bond, boolean boss) {
		return bond >= 2 ? (boss ? BOSS_LASTS_BOND2 : LASTS_BOND2) : (boss ? BOSS_LASTS : LASTS);
	}

	/** How many render-only copies each way a line carries on with at bond {@code bond}. */
	public static int farCopies(int bond) {
		return bond >= 2 ? FAR_COPIES_BOND2 : FAR_COPIES;
	}

	private record Hit(UUID attacker, float amount) {
	}

	private record Snap(Vec3d pos, float yaw) {
	}

	/** One cube of frozen time, called by one player: the things held in it, and its phase (the locks end together). */
	private static final class Cube {
		final UUID owner;
		final ServerWorld world;
		final Vec3d centre;
		final long start;
		/** The owner's bond with the contract when it was called: what this cube is (size, length, burst). */
		final int bond;
		int lasts;
		final ForeverCubeEntity entity;
		final List<Lock> locks = new ArrayList<>();
		/** Every hub it made (kept for the sinking after its locks are gone). */
		final List<ForeverHubEntity> hubs = new ArrayList<>();
		int phase = ForeverHubEntity.HELD;
		long phaseAt;
		Long forced;

		Cube(ServerPlayerEntity owner, Vec3d centre, int lasts, ForeverCubeEntity entity, int bond) {
			this.bond = bond;
			this.owner = owner.getUuid();
			this.world = owner.getServerWorld();
			this.centre = centre;
			this.start = world.getTime();
			this.lasts = lasts;
			this.entity = entity;
		}
	}

	/** One thing held in a cube. */
	private static final class Lock {
		final Cube cube;
		final ServerWorld world;
		final LivingEntity target;
		final ForeverHubEntity hub;
		final List<ForeverCopyEntity> copies = new ArrayList<>();
		final List<Hit> hits = new ArrayList<>();
		final Vec3d at;
		final float yaw;
		final float pitch;
		final float headYaw;
		final float bodyYaw;
		final Vec3d velocity;
		final float limbPos;
		final float limbSpeed;

		Lock(Cube cube, LivingEntity target, ForeverHubEntity hub) {
			this.cube = cube;
			this.world = cube.world;
			this.target = target;
			this.hub = hub;
			this.at = target.getPos();
			this.yaw = target.getYaw();
			this.pitch = target.getPitch();
			this.headYaw = target.getHeadYaw();
			this.bodyYaw = target.getBodyYaw();
			this.velocity = target.getVelocity();
			this.limbPos = target.limbAnimator.getPos();
			this.limbSpeed = target.limbAnimator.getSpeed();
		}

		ForeverLockPayload on() {
			return new ForeverLockPayload(target.getId(), true, at.x, at.y, at.z, yaw, pitch, headYaw, bodyYaw, limbPos, limbSpeed);
		}
	}

	/** The locks, by target; the cubes, by owner; cubes whose flesh is sinking back. */
	private static final Map<UUID, Lock> LOCKS = new HashMap<>();
	private static final Map<UUID, Cube> CUBES = new HashMap<>();
	private static final List<Cube> SINKING = new ArrayList<>();
	/** Hubs of locks that were cleaned up early, and when (world time) they may go. */
	private static final Map<ForeverHubEntity, Long> LOOSE = new HashMap<>();
	/** When (server ticks) each owner may use it again, and each target may be locked again. */
	private static final Map<UUID, Long> COOL = new HashMap<>();
	private static final Map<UUID, Long> IMMUNE = new HashMap<>();
	/** Who has stepped out of time ("Not yet", bond 3), until what server tick. */
	private static final Map<UUID, Long> NOT_YET = new HashMap<>();
	/** Where each living thing near a contract holder has been, newest first, for the past copies. */
	private static final Map<UUID, ArrayDeque<Snap>> HISTORY_OF = new HashMap<>();

	private Forever() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Forever::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Forever::allowDamage);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			for (Lock l : LOCKS.values()) {
				if (ServerPlayNetworking.canSend(handler.player, ForeverLockPayload.ID)) {
					ServerPlayNetworking.send(handler.player, l.on());
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Lock l = LOCKS.get(handler.player.getUuid());
			if (l != null) {
				cleanLock(l);
			}
			HISTORY_OF.remove(handler.player.getUuid());
			NOT_YET.remove(handler.player.getUuid());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOCKS.clear();
			CUBES.clear();
			NOT_YET.clear();
			COOL.clear();
			IMMUNE.clear();
			HISTORY_OF.clear();
			SINKING.clear();
			LOOSE.clear();
			ForeverFlags.SERVER.clear();
		});
		// A locked player does nothing: no use of items, blocks or things, no digging (their attacks are stopped in ForeverAttackMixin).
		UseItemCallback.EVENT.register((player, world, hand) -> locked(player) || notYet(player) ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> locked(player) || notYet(player) ? ActionResult.FAIL : ActionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> locked(player) || notYet(player) ? ActionResult.FAIL : ActionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> locked(player) || notYet(player) ? ActionResult.FAIL : ActionResult.PASS);
	}

	// ---- Asking for it.

	/** The "Use contract" key. */
	public static void use(ServerPlayerEntity player) {
		Contract held = Contracts.get(player);
		if (held == Contract.NONE) {
			player.sendMessage(Text.translatable("chainsawman.forever.no_contract"), true);
			return;
		}
		if (held != Contract.ETERNITY) {
			return;
		}
		if (Form.in(player)) {
			player.sendMessage(Text.translatable("chainsawman.forever.human_only"), true);
			return;
		}
		if (!player.isAlive() || Downed.is(player) || CUBES.containsKey(player.getUuid()) || locked(player) || notYet(player)) {
			return;
		}
		if (!player.isCreative() && player.getHungerManager().getFoodLevel() <= 0) {
			// There is nothing left of you for it to eat.
			player.sendMessage(Text.translatable("chainsawman.forever.hungry"), true);
			return;
		}
		long now = player.server.getTicks();
		Long until = COOL.get(player.getUuid());
		if (until != null && now < until) {
			player.sendMessage(Text.translatable("chainsawman.forever.cooldown", (until - now + 19) / 20), true);
			return;
		}
		ServerWorld world = player.getServerWorld();
		LivingEntity looked = look(player);
		Vec3d centre;
		if (looked != null) {
			centre = looked.getPos().add(0.0, looked.getHeight() * 0.5, 0.0);
		} else {
			Vec3d eye = player.getEyePos();
			Vec3d end = eye.add(player.getRotationVector(player.getPitch(), player.getYaw()).multiply(RANGE));
			HitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
			if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
				player.sendMessage(Text.translatable("chainsawman.forever.no_target"), true);
				return;
			}
			centre = Vec3d.ofCenter(block.getBlockPos());
			// A power turned on a wall of a sealed hotel cuts it, as anything else would.
			io.github.kortev.chainsawman.hotel.HomeBlockEntity sealed = io.github.kortev.chainsawman.hotel.Hotel.sealedAt(world, block.getBlockPos());
			if (sealed != null) {
				io.github.kortev.chainsawman.hotel.Hunt.cut(world, sealed, block.getBlockPos(), player);
			}
		}
		// Everything living in the cube round it (bigger with the bond), but you and your own pets; the nearest eight.
		int bond = Bond.step(player, Contract.ETERNITY);
		double half = cubeHalf(bond);
		Box box = new Box(centre.x - half, centre.y - half, centre.z - half, centre.x + half, centre.y + half, centre.z + half);
		List<LivingEntity> found = world.getEntitiesByClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && e != player && !(e instanceof Tameable pet && player.getUuid().equals(pet.getOwnerUuid())));
		boolean busy = false;
		boolean settling = false;
		List<LivingEntity> targets = new ArrayList<>();
		found.sort((x, y) -> Double.compare(x.getPos().squaredDistanceTo(centre), y.getPos().squaredDistanceTo(centre)));
		for (LivingEntity e : found) {
			if (LOCKS.containsKey(e.getUuid())) {
				busy = true;
				continue;
			}
			Long safe = IMMUNE.get(e.getUuid());
			if (safe != null && now < safe) {
				settling = true;
				continue;
			}
			if (targets.size() < CAP_LOCKS) {
				targets.add(e);
			}
		}
		if (targets.isEmpty()) {
			String key = busy ? "already" : settling ? "settling" : "no_target";
			player.sendMessage(Text.translatable("chainsawman.forever." + key), true);
			return;
		}
		begin(player, centre, targets, bond);
	}

	// ---- Not yet (bond 3): out of time for a moment.

	/** Whether {@code entity} has stepped out of time: untouchable, and doing nothing but moving. */
	public static boolean notYet(Entity entity) {
		return entity != null && NOT_YET.containsKey(entity.getUuid());
	}

	/**
	 * "Not yet": the switch-contract key held, not tapped, with Forever selected, at bond 3. You step out of time for {@link #NOT_YET_LASTS}
	 * ticks (1.5 s): nothing can touch you (every hit is turned aside), you cannot attack or use things, you can still move, and the clients
	 * show you grey with copies of you trailing behind. When it ends you step back in where you are. It shares Forever's cooldown (set from
	 * the end) and costs the same price (taken at the end). Whether it began.
	 */
	public static boolean stepOut(ServerPlayerEntity player) {
		if (Contracts.get(player) != Contract.ETERNITY || Bond.step(player, Contract.ETERNITY) < 3) {
			return false;
		}
		if (Form.in(player)) {
			player.sendMessage(Text.translatable("chainsawman.forever.human_only"), true);
			return false;
		}
		if (!player.isAlive() || Downed.is(player) || CUBES.containsKey(player.getUuid()) || locked(player) || notYet(player)) {
			return false;
		}
		if (!player.isCreative() && player.getHungerManager().getFoodLevel() <= 0) {
			player.sendMessage(Text.translatable("chainsawman.forever.hungry"), true);
			return false;
		}
		long now = player.server.getTicks();
		Long until = COOL.get(player.getUuid());
		if (until != null && now < until) {
			player.sendMessage(Text.translatable("chainsawman.forever.cooldown", (until - now + 19) / 20), true);
			return false;
		}
		NOT_YET.put(player.getUuid(), now + NOT_YET_LASTS);
		tell(player.server, new io.github.kortev.chainsawman.network.NotYetPayload(player.getUuid(), NOT_YET_LASTS));
		player.getServerWorld().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), ChainsawSounds.NOT_YET, SoundCategory.PLAYERS, 1.8F, 1.0F);
		player.getServerWorld().spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.1, player.getZ(), 10, 0.5, 0.05, 0.5, 0.01);
		return true;
	}

	/** Ticks of "Not yet" left for {@code player} (0 when they are not out of time). */
	public static long notYetLeft(ServerPlayerEntity player) {
		Long until = NOT_YET.get(player.getUuid());
		return until == null ? 0 : Math.max(0, until - player.server.getTicks());
	}

	/** Ends {@code player}'s "Not yet" now (for the tests): they step back in, and the price is taken. */
	public static void endNotYet(ServerPlayerEntity player) {
		if (NOT_YET.remove(player.getUuid()) != null) {
			stepBack(player);
		}
	}

	private static void stepBack(ServerPlayerEntity player) {
		tell(player.server, new io.github.kortev.chainsawman.network.NotYetPayload(player.getUuid(), 0));
		ServerWorld world = player.getServerWorld();
		world.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), ChainsawSounds.STEP_OUT, SoundCategory.PLAYERS, 1.8F, 1.0F);
		world.spawnParticles(new DustParticleEffect(new Vector3f(0.75F, 0.05F, 0.08F), 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.6, 0.8, 0.6, 0.08);
		world.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.6, 0.8, 0.6, 0.4);
		catchUp(player);
		COOL.put(player.getUuid(), (long) player.server.getTicks() + COOLDOWN);
	}

	private static void tickNotYet(MinecraftServer server) {
		long now = server.getTicks();
		for (Map.Entry<UUID, Long> e : List.copyOf(NOT_YET.entrySet())) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
			if (player == null || !player.isAlive()) {
				NOT_YET.remove(e.getKey());
				if (player != null) {
					tell(server, new io.github.kortev.chainsawman.network.NotYetPayload(e.getKey(), 0));
				}
			} else if (now >= e.getValue()) {
				NOT_YET.remove(e.getKey());
				stepBack(player);
			}
		}
	}

	public static boolean active(ServerPlayerEntity owner) {
		return CUBES.containsKey(owner.getUuid());
	}

	public static boolean locked(Entity entity) {
		Lock l = entity == null ? null : LOCKS.get(entity.getUuid());
		return l != null && l.cube.phase != ForeverHubEntity.SINKING;
	}

	/** What {@code owner}'s cube holds now. */
	public static List<LivingEntity> lockedBy(ServerPlayerEntity owner) {
		Cube c = CUBES.get(owner.getUuid());
		List<LivingEntity> out = new ArrayList<>();
		if (c != null) {
			for (Lock l : c.locks) {
				out.add(l.target);
			}
		}
		return out;
	}

	/** The copies of the lock on {@code target}, none if it is not locked. */
	public static List<ForeverCopyEntity> copies(Entity target) {
		Lock l = target == null ? null : LOCKS.get(target.getUuid());
		return l == null ? List.of() : List.copyOf(l.copies);
	}

	/** How many hits are stored on {@code target}. */
	public static int stored(Entity target) {
		Lock l = target == null ? null : LOCKS.get(target.getUuid());
		return l == null ? 0 : l.hits.size();
	}

	/** The total of the hits stored on {@code target}. */
	public static float storedTotal(Entity target) {
		Lock l = target == null ? null : LOCKS.get(target.getUuid());
		float sum = 0.0F;
		if (l != null) {
			for (Hit h : l.hits) {
				sum += h.amount;
			}
		}
		return sum;
	}

	/** The hub of the lock on {@code target}, or null. */
	public static ForeverHubEntity hub(Entity target) {
		Lock l = target == null ? null : LOCKS.get(target.getUuid());
		return l == null ? null : l.hub;
	}

	/** Whether {@code entity} (a hub, a copy or a cube) still belongs to a lock that exists: the ones that do not remove themselves. */
	public static boolean holds(Entity entity) {
		for (Cube c : CUBES.values()) {
			if (c.entity == entity || c.hubs.contains(entity) || c.locks.stream().anyMatch(l -> l.copies.contains(entity))) {
				return true;
			}
		}
		for (Cube c : SINKING) {
			if (c.entity == entity || c.hubs.contains(entity)) {
				return true;
			}
		}
		return entity instanceof ForeverHubEntity hub && LOOSE.containsKey(hub);
	}

	/** Ticks left of the owner's cooldown (0 when it may be used). */
	public static long cooldownLeft(ServerPlayerEntity player) {
		Long until = COOL.get(player.getUuid());
		return until == null ? 0 : Math.max(0, until - player.server.getTicks());
	}

	/** Ticks left of the target's immunity to being locked again. */
	public static long immuneLeft(Entity target) {
		Long until = IMMUNE.get(target.getUuid());
		return until == null || target.getServer() == null ? 0 : Math.max(0, until - target.getServer().getTicks());
	}

	/** Skips the owner's cooldown and every target's immunity (for the tests and for trying it out). */
	public static void ready(ServerPlayerEntity player) {
		COOL.remove(player.getUuid());
		NOT_YET.remove(player.getUuid());
		IMMUNE.clear();
	}

	/** Ticks until the moment ends, for the tests: the cube holding {@code target} ends {@code ticks} from now. */
	public static void endsIn(Entity target, int ticks) {
		Lock l = target == null ? null : LOCKS.get(target.getUuid());
		if (l != null) {
			l.cube.forced = l.world.getTime() + ticks;
		}
	}

	/** Makes the moment holding {@code target} last {@code ticks} longer (for the tests and for filming). */
	public static void extend(Entity target, int ticks) {
		Lock l = target == null ? null : LOCKS.get(target.getUuid());
		if (l != null) {
			l.cube.lasts += ticks;
		}
	}

	/** Whether {@code lasts} is a boss's moment. */
	public static boolean boss(LivingEntity target) {
		if (target instanceof PlayerEntity) {
			return false;
		}
		return target.getMaxHealth() >= 100.0F || target instanceof WitherEntity || target instanceof EnderDragonEntity || target instanceof WardenEntity;
	}

	/** The living thing {@code player} looks at within {@link #RANGE} blocks, with nothing solid between; null for none. */
	private static LivingEntity look(ServerPlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d dir = player.getRotationVector(player.getPitch(), player.getYaw());
		Vec3d end = eye.add(dir.multiply(RANGE));
		Box box = player.getBoundingBox().stretch(dir.multiply(RANGE)).expand(1.0);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && e != player && !(e instanceof Tameable pet && player.getUuid().equals(pet.getOwnerUuid())), RANGE * RANGE);
		if (hit == null || !(hit.getEntity() instanceof LivingEntity living)) {
			return null;
		}
		HitResult block = player.getServerWorld().raycast(new RaycastContext(eye, hit.getPos(), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		return block.getType() == HitResult.Type.MISS ? living : null;
	}

	// ---- The cube.

	private static boolean begin(ServerPlayerEntity owner, Vec3d centre, List<LivingEntity> targets, int bond) {
		ServerWorld world = owner.getServerWorld();
		boolean anyBoss = false;
		for (LivingEntity t : targets) {
			if (boss(t)) {
				anyBoss = true;
			}
		}
		int lasts = lasts(bond, anyBoss);
		Cube cube = null;
		try {
			ForeverCubeEntity ce = ChainsawMan.FOREVER_CUBE.create(world);
			ce.refreshPositionAndAngles(centre.x, centre.y, centre.z, 0.0F, 0.0F);
			ce.begin(owner.getId(), (int) world.getTime(), lasts, bond);
			world.spawnEntity(ce);
			cube = new Cube(owner, centre, lasts, ce, bond);
			for (LivingEntity target : targets) {
				ForeverHubEntity hub = ChainsawMan.FOREVER_HUB.create(world);
				hub.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ(), target.getYaw(), 0.0F);
				hub.begin(target.getId(), owner.getId(), (int) world.getTime(), lasts, world.getRandom().nextInt(), bond);
				world.spawnEntity(hub);
				cube.hubs.add(hub);
				Lock lock = new Lock(cube, target, hub);
				cube.locks.add(lock);
				makeCopies(lock);
			}
		} catch (RuntimeException e) {
			ChainsawMan.LOGGER.error("Forever could not be made", e);
			if (cube != null) {
				for (Lock l : cube.locks) {
					l.copies.forEach(Entity::discard);
				}
				cube.hubs.forEach(Entity::discard);
				cube.entity.discard();
			}
			return false;
		}
		CUBES.put(owner.getUuid(), cube);
		for (Lock lock : cube.locks) {
			LOCKS.put(lock.target.getUuid(), lock);
			// Freeze it where it is, as it is: nothing is set on it (nothing to be saved with it); the mixins stop it moving.
			ForeverFlags.SERVER.add(lock.target.getId());
			lock.target.setVelocity(Vec3d.ZERO);
			if (lock.target instanceof MobEntity mob) {
				mob.getNavigation().stop();
			}
			broadcast(world.getServer(), lock.on());
		}
		world.playSound(null, centre.x, centre.y, centre.z, ChainsawSounds.FOREVER_LOCK, SoundCategory.PLAYERS, 2.2F, bond >= 2 ? 0.9F : 1.0F);
		world.spawnParticles(ParticleTypes.SMOKE, centre.x, centre.y - 1.5, centre.z, 14, 1.8, 0.05, 1.8, 0.01);
		return true;
	}

	private static void play(ServerWorld world, Entity at, SoundEvent sound, float volume) {
		world.playSound(null, at.getX(), at.getY() + at.getHeight() * 0.5, at.getZ(), sound, SoundCategory.PLAYERS, volume, 1.0F);
	}

	private static void tell(MinecraftServer server, net.minecraft.network.packet.CustomPayload payload) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (ServerPlayNetworking.canSend(p, payload.getId())) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	static void broadcast(MinecraftServer server, ForeverLockPayload payload) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (ServerPlayNetworking.canSend(p, ForeverLockPayload.ID)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	// ---- The line of copies.

	private static void makeCopies(Lock lock) {
		ServerWorld world = lock.world;
		LivingEntity target = lock.target;
		Vec3d p = lock.at;
		double facingYaw = Math.toRadians(lock.bodyYaw);
		Vec3d facing = new Vec3d(-Math.sin(facingYaw), 0.0, Math.cos(facingYaw));
		// The past: where it was, newest first; the future: where it was going.
		List<Vec3d> past = new ArrayList<>();
		past.add(p);
		ArrayDeque<Snap> history = HISTORY_OF.get(target.getUuid());
		if (history != null) {
			for (Snap s : history) {
				Vec3d last = past.get(past.size() - 1);
				if (s.pos.squaredDistanceTo(last) > 0.0004) {
					past.add(s.pos);
				}
			}
		}
		// Which way it was moving: from where it was a few ticks ago, else its velocity.
		Vec3d heading = Vec3d.ZERO;
		if (history != null && history.size() > 4) {
			Snap[] arr = history.toArray(new Snap[0]);
			Vec3d d = p.subtract(arr[Math.min(arr.length - 1, 5)].pos);
			heading = new Vec3d(d.x, 0.0, d.z);
		}
		if (heading.lengthSquared() < 0.01) {
			Vec3d v = lock.velocity;
			heading = new Vec3d(v.x, 0.0, v.z);
		}
		Vec3d ahead = heading.lengthSquared() > 0.0016 ? heading.normalize() : facing;
		List<Vec3d> future = new ArrayList<>();
		future.add(p);
		if (target instanceof MobEntity mob && mob.getNavigation().getCurrentPath() != null && !mob.getNavigation().getCurrentPath().isFinished()) {
			Path path = mob.getNavigation().getCurrentPath();
			for (int i = path.getCurrentNodeIndex(); i < path.getLength(); i++) {
				PathNode node = path.getNode(i);
				Vec3d at = new Vec3d(node.x + 0.5, node.y, node.z + 0.5);
				if (at.squaredDistanceTo(future.get(future.size() - 1)) > 0.04) {
					future.add(at);
				}
			}
		}
		Vec3d behindDir = past.size() >= 2 ? past.get(past.size() - 1).subtract(past.get(past.size() - 2)).multiply(1.0, 0.0, 1.0) : Vec3d.ZERO;
		behindDir = behindDir.lengthSquared() > 1.0E-6 ? behindDir.normalize() : (heading.lengthSquared() > 0.0016 ? ahead.negate() : facing.negate());
		Vec3d futureDir = future.size() >= 2 ? future.get(future.size() - 1).subtract(future.get(future.size() - 2)).multiply(1.0, 0.0, 1.0) : Vec3d.ZERO;
		futureDir = futureDir.lengthSquared() > 1.0E-6 ? futureDir.normalize() : ahead;
		// How high it is above the ground (a copy hangs at the same height over its own ground).
		double ground = groundBelow(world, p.x, p.y, p.z);
		double hover = p.y - ground > 0.15 ? Math.min(p.y - ground, 8.0) : 0.0;
		float width = target.getWidth();
		float height = target.getHeight();
		float speed = Math.max(0.4F, Math.min(1.0F, target.limbAnimator.getSpeed()));
		float stride = target.limbAnimator.getPos();
		net.minecraft.util.math.random.Random random = world.getRandom();
		double[] arcsPast = new double[SIDE];
		double[] arcsFuture = new double[SIDE];
		double sumP = 0.0;
		double sumF = 0.0;
		for (int i = 0; i < SIDE; i++) {
			sumP += SPACING_MIN + (SPACING_MAX - SPACING_MIN) * random.nextDouble();
			sumF += SPACING_MIN + (SPACING_MAX - SPACING_MIN) * random.nextDouble();
			arcsPast[i] = sumP;
			arcsFuture[i] = sumF;
		}
		boolean[] pastReal = new boolean[SIDE];
		boolean[] futureReal = new boolean[SIDE];
		Vec3d[] pastAt = along(past, arcsPast, behindDir, pastReal);
		Vec3d[] futureAt = along(future, arcsFuture, futureDir, futureReal);
		for (int i = 0; i < SIDE; i++) {
			// Where it really was (the history) is used as it was, height and all; the rest hangs at its height over the ground.
			addCopy(lock, pastAt[i], pastReal[i], ground, hover, width, height, -(i + 1), stride - 0.9F * (i + 1), speed);
			addCopy(lock, futureAt[i], false, ground, hover, width, height, i + 1, stride + 0.9F * (i + 1), speed);
		}
	}

	/** Points along the polyline {@code poly} at the arc lengths {@code arcs}, carried on straight along {@code dir} past its end. */
	private static Vec3d[] along(List<Vec3d> poly, double[] arcs, Vec3d dir, boolean[] within) {
		Vec3d[] out = new Vec3d[arcs.length];
		for (int k = 0; k < arcs.length; k++) {
			double left = arcs[k];
			Vec3d at = poly.get(0);
			boolean found = false;
			for (int i = 1; i < poly.size() && !found; i++) {
				Vec3d a = poly.get(i - 1);
				Vec3d b = poly.get(i);
				double len = a.distanceTo(b);
				if (left <= len) {
					at = a.lerp(b, left / Math.max(len, 1.0E-6));
					found = true;
				} else {
					left -= len;
					at = b;
				}
			}
			out[k] = found ? at : at.add(dir.multiply(left));
			within[k] = found;
		}
		return out;
	}

	/** The height of the highest solid thing under {@code (x, y, z)} within 16 blocks, or {@code y - 16} if there is none. */
	private static double groundBelow(ServerWorld world, double x, double y, double z) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		for (int by = MathHelper.floor(y + 0.01); by > MathHelper.floor(y) - 16; by--) {
			BlockPos pos = new BlockPos(bx, by, bz);
			VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
			if (!shape.isEmpty()) {
				double top = by + shape.getMax(Direction.Axis.Y);
				if (top <= y + 0.01) {
					return top;
				}
			}
		}
		return y - 16.0;
	}

	private static void addCopy(Lock lock, Vec3d at, boolean real, double refGround, double hover, float width, float height, int slot, float stride, float speed) {
		ServerWorld world = lock.world;
		double y;
		if (real && fits(world, at.x, at.y, at.z, width, height)) {
			// Where it really was: its own height, in the air if it was in the air.
			y = at.y;
		} else {
			Vec3d spot = standable(world, at.x, at.z, refGround);
			if (spot == null) {
				return;
			}
			y = spot.y + hover;
			if (!fits(world, spot.x, y, spot.z, width, height)) {
				y = spot.y;
				if (!fits(world, spot.x, y, spot.z, width, height)) {
					return;
				}
			}
		}
		ForeverCopyEntity copy = ChainsawMan.FOREVER_COPY.create(world);
		if (copy == null) {
			return;
		}
		copy.refreshPositionAndAngles(at.x, y, at.z, lock.bodyYaw, 0.0F);
		copy.setup(lock.hub.getId(), lock.target.getId(), slot, stride, speed, width, height);
		world.spawnEntity(copy);
		lock.copies.add(copy);
	}

	/** Whether a body {@code width} by {@code height} fits with its feet at {@code (x, y, z)}: no block in it. */
	public static boolean fits(ServerWorld world, double x, double y, double z, float width, float height) {
		Box box = new Box(x - width / 2.0 + 0.02, y + 0.001, z - width / 2.0 + 0.02, x + width / 2.0 - 0.02, y + height - 0.02, z + width / 2.0 - 0.02);
		return !world.getBlockCollisions(null, box).iterator().hasNext();
	}

	/**
	 * Somewhere in the column of {@code (x, z)} within {@link #VERTICAL} blocks of {@code y} where a body can stand: solid ground with
	 * a full top, two blocks of air over it, nothing that hurts; the one nearest {@code y}. Null if there is none.
	 */
	public static Vec3d standable(ServerWorld world, double x, double z, double y) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		int centre = MathHelper.floor(y + 0.5);
		int pick = Integer.MIN_VALUE;
		int best = Integer.MAX_VALUE;
		for (int dy = -VERTICAL; dy <= VERTICAL; dy++) {
			if (stands(world, bx, centre + dy, bz) && Math.abs(dy) < best) {
				best = Math.abs(dy);
				pick = centre + dy;
			}
		}
		return pick == Integer.MIN_VALUE ? null : new Vec3d(x, pick, z);
	}

	/** Whether something can stand with its feet at block {@code y} of the column {@code (x, z)}. */
	public static boolean stands(ServerWorld world, int x, int y, int z) {
		BlockPos below = new BlockPos(x, y - 1, z);
		BlockState ground = world.getBlockState(below);
		VoxelShape top = ground.getCollisionShape(world, below);
		if (top.isEmpty() || !Block.isFaceFullSquare(top, Direction.UP) || !ground.getFluidState().isEmpty() || hurts(ground)) {
			return false;
		}
		for (int i = 0; i < 2; i++) {
			BlockPos at = new BlockPos(x, y + i, z);
			BlockState state = world.getBlockState(at);
			if (!state.getCollisionShape(world, at).isEmpty() || !state.getFluidState().isEmpty() || hurts(state)) {
				return false;
			}
		}
		return true;
	}

	private static boolean hurts(BlockState state) {
		return state.isIn(BlockTags.FIRE) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.SWEET_BERRY_BUSH)
				|| state.isOf(Blocks.COBWEB) || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.WITHER_ROSE) || state.isOf(Blocks.CAMPFIRE)
				|| state.isOf(Blocks.SOUL_CAMPFIRE) || state.isOf(Blocks.LAVA);
	}

	// ---- Storing hits.

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!NOT_YET.isEmpty() && NOT_YET.containsKey(entity.getUuid()) && !source.isOf(DamageTypes.GENERIC_KILL) && !source.isOf(DamageTypes.OUT_OF_WORLD)) {
			// Out of time: nothing lands.
			return false;
		}
		Lock l = LOCKS.get(entity.getUuid());
		if (l == null || l.cube.phase == ForeverHubEntity.SINKING || source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
			return true;
		}
		if (!(source.getAttacker() instanceof PlayerEntity attacker) || amount <= 0.0F) {
			return true;
		}
		store(l, attacker, amount, shareOn(entity.getBoundingBox(), entity.getY(), entity.getHeight(), source, attacker));
		return false;
	}

	/** A hit on one of the copies: stored on the target as if it had been struck there. Returns true if it was stored. */
	static boolean storeOnCopy(ForeverCopyEntity copy, DamageSource source, float amount) {
		for (Lock l : LOCKS.values()) {
			if (l.copies.contains(copy)) {
				if (l.cube.phase == ForeverHubEntity.SINKING || !(source.getAttacker() instanceof PlayerEntity attacker) || amount <= 0.0F) {
					return false;
				}
				store(l, attacker, amount, shareOn(copy.getBoundingBox(), copy.getY(), copy.getHeight(), source, attacker));
				return true;
			}
		}
		return false;
	}

	/** Where up a body ({@code box}, {@code height} tall from {@code feet}) a hit landed, as a share of its height. */
	private static float shareOn(Box box, double feet, double height, DamageSource source, PlayerEntity attacker) {
		double y;
		Entity direct = source.getSource();
		if (direct != null && direct != attacker) {
			y = direct.getY() + direct.getHeight() * 0.5;
		} else {
			Vec3d eye = attacker.getEyePos();
			Vec3d dir = attacker.getRotationVector();
			Optional<Vec3d> hit = box.expand(0.05).raycast(eye, eye.add(dir.multiply(8.0)));
			y = hit.isPresent() ? hit.get().y : MathHelper.clamp(eye.y, feet + 0.2 * height, feet + 0.9 * height);
		}
		return (float) MathHelper.clamp((y - feet) / Math.max(0.1, height), 0.12, 0.95);
	}

	private static void store(Lock l, PlayerEntity attacker, float amount, float share) {
		l.hits.add(new Hit(attacker.getUuid(), amount));
		l.hub.mark(share);
		play(l.world, l.target, ChainsawSounds.FOREVER_HIT, 1.2F);
		if (attacker instanceof ServerPlayerEntity sp) {
			sp.sendMessage(Text.translatable("chainsawman.forever.stored", l.hits.size()), true);
		}
	}

	// ---- Each tick.

	private static void tick(MinecraftServer server) {
		recordHistory(server);
		long ticks = server.getTicks();
		if (ticks % 100 == 0) {
			IMMUNE.values().removeIf(until -> until <= ticks);
			COOL.values().removeIf(until -> until <= ticks);
		}
		if (!NOT_YET.isEmpty()) {
			tickNotYet(server);
		}
		if (CUBES.isEmpty() && SINKING.isEmpty() && LOOSE.isEmpty()) {
			return;
		}
		for (Cube c : List.copyOf(CUBES.values())) {
			tickCube(server, c);
		}
		SINKING.removeIf(c -> {
			if (c.world.getTime() - c.phaseAt >= SINK) {
				c.hubs.forEach(Entity::discard);
				c.entity.discard();
				return true;
			}
			return false;
		});
		LOOSE.entrySet().removeIf(e -> {
			if (e.getKey().isRemoved() || e.getKey().getWorld().getTime() >= e.getValue()) {
				e.getKey().discard();
				return true;
			}
			return false;
		});
	}

	private static void tickCube(MinecraftServer server, Cube c) {
		long now = c.world.getTime();
		for (Lock l : List.copyOf(c.locks)) {
			LivingEntity target = l.target;
			if (target.isRemoved() || !target.isAlive() || target.getWorld() != l.world) {
				cleanLock(l);
			}
		}
		if (c.locks.isEmpty()) {
			// Nothing left to hold: the cube ends with nothing going off.
			CUBES.remove(c.owner);
			c.phase = ForeverHubEntity.SINKING;
			c.phaseAt = now;
			c.entity.phase(ForeverHubEntity.SINKING, (int) now);
			for (ForeverHubEntity h : c.hubs) {
				h.phase(ForeverHubEntity.SINKING, (int) now);
			}
			SINKING.add(c);
			COOL.put(c.owner, (long) server.getTicks() + COOLDOWN);
			return;
		}
		for (Lock l : c.locks) {
			pin(l);
		}
		if (c.phase == ForeverHubEntity.HELD) {
			if (now - c.start >= c.lasts || c.forced != null && now >= c.forced) {
				startRelease(c, now);
			}
		} else if (c.phase == ForeverHubEntity.ENDING && now - c.phaseAt >= SNAP) {
			blow(server, c, now);
		}
	}

	/** Holds the target where it froze: the mixins stop its own movement, so this is only a check that nothing else has moved it. */
	private static void pin(Lock l) {
		LivingEntity t = l.target;
		if (t.getVelocity().lengthSquared() > 0.0) {
			t.setVelocity(Vec3d.ZERO);
		}
		if (t instanceof ServerPlayerEntity player) {
			if (player.getPos().squaredDistanceTo(l.at) > 0.25 && player.networkHandler != null) {
				player.networkHandler.requestTeleport(l.at.x, l.at.y, l.at.z, l.yaw, l.pitch);
			}
		} else if (t.getPos().squaredDistanceTo(l.at) > 0.0025) {
			t.refreshPositionAndAngles(l.at.x, l.at.y, l.at.z, l.yaw, l.pitch);
			t.setHeadYaw(l.headYaw);
			t.setBodyYaw(l.bodyYaw);
		}
	}

	private static void startRelease(Cube c, long now) {
		c.phase = ForeverHubEntity.ENDING;
		c.phaseAt = now;
		c.entity.phase(ForeverHubEntity.ENDING, (int) now);
		for (Lock l : c.locks) {
			l.hub.phase(ForeverHubEntity.ENDING, (int) now);
			for (ForeverCopyEntity copy : l.copies) {
				copy.snap((int) now);
			}
		}
		stopSounds(c);
		c.world.playSound(null, c.centre.x, c.centre.y, c.centre.z, ChainsawSounds.FOREVER_RELEASE, SoundCategory.PLAYERS, c.bond >= 3 ? 3.6F : 2.6F, c.bond >= 3 ? 0.82F : 1.0F);
	}

	/** Stops the lock's whispers for the players near this cube (not for another one's far off). */
	private static void stopSounds(Cube c) {
		StopSoundS2CPacket stop = new StopSoundS2CPacket(ChainsawSounds.FOREVER_LOCK.getId(), SoundCategory.PLAYERS);
		for (ServerPlayerEntity p : c.world.getServer().getPlayerManager().getPlayerList()) {
			if (p.getServerWorld() == c.world && p.squaredDistanceTo(c.centre) < 64.0 * 64.0) {
				p.networkHandler.sendPacket(stop);
			}
		}
	}

	/** The cube's moment ends: the line is in, every thing it held is let go and every stored hit goes off at once. */
	private static void blow(MinecraftServer server, Cube c, long now) {
		ServerWorld world = c.world;
		CUBES.remove(c.owner);
		c.phase = ForeverHubEntity.SINKING;
		c.phaseAt = now;
		c.entity.phase(ForeverHubEntity.SINKING, (int) now);
		SINKING.add(c);
		long ticks = server.getTicks();
		COOL.put(c.owner, ticks + COOLDOWN);
		ServerPlayerEntity owner = server.getPlayerManager().getPlayer(c.owner);
		if (owner != null) {
			catchUp(owner);
			// A use that locked something counts toward the bond (the moment, if it deepens, comes with the grey of the catch-up).
			Bond.record(owner, Contract.ETERNITY);
		}
		float mult = c.bond >= 3 ? MULT_BOND3 : MULT;
		float cap = c.bond >= 3 ? CAP_BOND3 : CAP;
		for (Lock l : List.copyOf(c.locks)) {
			LivingEntity t = l.target;
			unfreeze(l);
			for (ForeverCopyEntity copy : l.copies) {
				copy.discard();
			}
			l.copies.clear();
			l.hub.phase(ForeverHubEntity.SINKING, (int) now);
			LOCKS.remove(t.getUuid());
			IMMUNE.put(t.getUuid(), ticks + IMMUNITY);
			float total = 0.0F;
			Map<UUID, Float> by = new HashMap<>();
			for (Hit h : l.hits) {
				total += h.amount;
				by.merge(h.attacker, h.amount, Float::sum);
			}
			Vec3d heart = t.getPos().add(0.0, t.getHeight() * 0.5, 0.0);
			burst(world, heart, l.hits.size(), c.bond);
			if (!l.hits.isEmpty() && t.isAlive()) {
				UUID top = null;
				float most = -1.0F;
				for (Map.Entry<UUID, Float> e : by.entrySet()) {
					if (e.getValue() > most) {
						most = e.getValue();
						top = e.getKey();
					}
				}
				PlayerEntity credit = top == null ? null : world.getPlayerByUuid(top);
				DamageSource source = credit != null ? world.getDamageSources().playerAttack(credit) : world.getDamageSources().generic();
				float amount = Math.min(cap, total * mult);
				t.timeUntilRegen = 0;
				t.damage(source, amount);
				// Flung away from whoever called it, harder the more hits there were.
				Vec3d from = owner != null ? owner.getPos() : c.centre.add(0.0, 0.0, 1.0);
				double strength = Math.min(c.bond >= 3 ? 3.6 : 2.6, (0.7 + 0.2 * l.hits.size()) * (c.bond >= 3 ? 1.35 : 1.0));
				t.takeKnockback(strength, from.x - t.getX(), from.z - t.getZ());
				t.velocityModified = true;
			}
		}
		c.locks.clear();
	}

	/** The price of a use: time catches up with them. The moment they froze is taken from them (their hunger drops) and the world stays grey round them for a second. */
	public static void catchUp(ServerPlayerEntity owner) {
		if (!owner.isCreative()) {
			owner.getHungerManager().setFoodLevel(Math.max(0, owner.getHungerManager().getFoodLevel() - HUNGER));
			owner.getHungerManager().setSaturationLevel(0.0F);
		}
		if (ServerPlayNetworking.canSend(owner, io.github.kortev.chainsawman.network.ForeverCatchUpPayload.ID)) {
			ServerPlayNetworking.send(owner, new io.github.kortev.chainsawman.network.ForeverCatchUpPayload(CATCH_UP));
		}
	}

	private static void burst(ServerWorld world, Vec3d at, int hits, int bond) {
		// Bond 3: twice the burst, spreading wider.
		int more = bond >= 3 ? 2 : 1;
		double wide = bond >= 3 ? 1.5 : 1.0;
		world.spawnParticles(new DustParticleEffect(new Vector3f(0.75F, 0.05F, 0.08F), bond >= 3 ? 1.5F : 1.0F), at.x, at.y, at.z, (22 + hits * 2) * more, 0.8 * wide, 0.6 * wide, 0.8 * wide, 0.12);
		world.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30 * more, 0.8 * wide, 0.7 * wide, 0.8 * wide, 0.6);
		if (bond >= 3) {
			world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0.0);
		}
	}

	/** Gives back what the freeze took: the velocity it had (nothing else was set on it). */
	private static void unfreeze(Lock l) {
		LivingEntity t = l.target;
		ForeverFlags.SERVER.remove(t.getId());
		broadcast(l.world.getServer(), ForeverLockPayload.off(t.getId()));
		t.setVelocity(l.velocity);
		t.velocityModified = true;
	}

	/** Ends one lock at once with nothing going off (the target died, left or went): its line gone, its flesh sinking. */
	private static void cleanLock(Lock l) {
		unfreeze(l);
		for (ForeverCopyEntity c : l.copies) {
			c.discard();
		}
		l.copies.clear();
		LOCKS.remove(l.target.getUuid());
		l.cube.locks.remove(l);
		long now = l.world.getTime();
		l.hub.phase(ForeverHubEntity.SINKING, (int) now);
		LOOSE.put(l.hub, now + SINK);
		long ticks = l.world.getServer().getTicks();
		IMMUNE.put(l.target.getUuid(), ticks + IMMUNITY);
	}

	// ---- Where things have been.

	/** Notes where every living thing near a contract holder is, so the past copies can be placed where it really was. */
	private static void recordHistory(MinecraftServer server) {
		boolean any = false;
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayerEntity holder : server.getPlayerManager().getPlayerList()) {
			if (!Contracts.has(holder, Contract.ETERNITY)) {
				continue;
			}
			any = true;
			Box box = holder.getBoundingBox().expand(HOLDER_RANGE);
			for (LivingEntity e : holder.getServerWorld().getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
				if (e.squaredDistanceTo(holder) > HOLDER_RANGE * HOLDER_RANGE || !seen.add(e.getUuid())) {
					continue;
				}
				ArrayDeque<Snap> h = HISTORY_OF.computeIfAbsent(e.getUuid(), k -> new ArrayDeque<>());
				if (!locked(e)) {
					h.addFirst(new Snap(e.getPos(), e.getBodyYaw()));
					while (h.size() > HISTORY) {
						h.removeLast();
					}
				}
			}
		}
		if (!any) {
			HISTORY_OF.clear();
		} else {
			HISTORY_OF.keySet().removeIf(id -> !seen.contains(id));
		}
	}

	/** The ticks of history kept for {@code entity} (for the tests). */
	public static int historyLength(Entity entity) {
		ArrayDeque<Snap> h = HISTORY_OF.get(entity.getUuid());
		return h == null ? 0 : h.size();
	}
}
