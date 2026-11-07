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
 * from the one who called it. It costs {@link #PRICE} levels of experience (free in creative).
 */
public final class Forever {
	/** The price in experience levels (kortev has not confirmed it: change it here), free in creative. */
	public static final int PRICE = 3;
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
	/** How far up and down a copy may look for ground (blocks). */
	public static final int VERTICAL = 4;

	private record Hit(UUID attacker, float amount) {
	}

	private record Snap(Vec3d pos, float yaw) {
	}

	/** One lock. */
	private static final class Lock {
		final UUID owner;
		final ServerWorld world;
		final LivingEntity target;
		final ForeverHubEntity hub;
		final List<ForeverCopyEntity> copies = new ArrayList<>();
		final List<Hit> hits = new ArrayList<>();
		final long start;
		final int lasts;
		final Vec3d at;
		final float yaw;
		final float pitch;
		final float headYaw;
		final float bodyYaw;
		final Vec3d velocity;
		final boolean wasNoAi;
		final boolean wasNoGravity;
		int phase = ForeverHubEntity.HELD;
		long phaseAt;

		Lock(ServerPlayerEntity owner, LivingEntity target, ForeverHubEntity hub, int lasts) {
			this.owner = owner.getUuid();
			this.world = owner.getServerWorld();
			this.target = target;
			this.hub = hub;
			this.start = world.getTime();
			this.lasts = lasts;
			this.at = target.getPos();
			this.yaw = target.getYaw();
			this.pitch = target.getPitch();
			this.headYaw = target.getHeadYaw();
			this.bodyYaw = target.getBodyYaw();
			this.velocity = target.getVelocity();
			this.wasNoAi = target instanceof MobEntity mob && mob.isAiDisabled();
			this.wasNoGravity = target.hasNoGravity();
		}
	}

	/** The locks, by target; and by owner. */
	private static final Map<UUID, Lock> LOCKS = new HashMap<>();
	private static final Map<UUID, Lock> BY_OWNER = new HashMap<>();
	/** When (server ticks) each owner may use it again, and each target may be locked again. */
	private static final Map<UUID, Long> COOL = new HashMap<>();
	private static final Map<UUID, Long> IMMUNE = new HashMap<>();
	/** Where each living thing near a contract holder has been, newest first, for the past copies. */
	private static final Map<UUID, ArrayDeque<Snap>> HISTORY_OF = new HashMap<>();
	/** Locks that are ending are kept here a moment (the flesh sinks). */
	private static final List<Lock> SINKING = new ArrayList<>();
	/** When (world time) a lock is to end earlier than its length, for the tests. */
	private static final Map<UUID, Long> endAt = new HashMap<>();

	private Forever() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Forever::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Forever::allowDamage);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			for (Lock l : LOCKS.values()) {
				if (ServerPlayNetworking.canSend(handler.player, ForeverLockPayload.ID)) {
					ServerPlayNetworking.send(handler.player, new ForeverLockPayload(l.target.getId(), true));
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Lock l = LOCKS.get(handler.player.getUuid());
			if (l != null) {
				cleanUp(l);
			}
			HISTORY_OF.remove(handler.player.getUuid());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOCKS.clear();
			BY_OWNER.clear();
			COOL.clear();
			IMMUNE.clear();
			HISTORY_OF.clear();
			SINKING.clear();
			ForeverFlags.SERVER.clear();
		});
		// A locked player does nothing: no use of items, blocks or things, no digging (their attacks are stopped in ForeverAttackMixin).
		UseItemCallback.EVENT.register((player, world, hand) -> locked(player) ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> locked(player) ? ActionResult.FAIL : ActionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> locked(player) ? ActionResult.FAIL : ActionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> locked(player) ? ActionResult.FAIL : ActionResult.PASS);
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
		if (!player.isAlive() || Downed.is(player) || BY_OWNER.containsKey(player.getUuid()) || locked(player)) {
			return;
		}
		long now = player.server.getTicks();
		Long until = COOL.get(player.getUuid());
		if (until != null && now < until) {
			player.sendMessage(Text.translatable("chainsawman.forever.cooldown", (until - now + 19) / 20), true);
			return;
		}
		LivingEntity target = look(player);
		if (target == null) {
			player.sendMessage(Text.translatable("chainsawman.forever.no_target"), true);
			return;
		}
		if (LOCKS.containsKey(target.getUuid())) {
			player.sendMessage(Text.translatable("chainsawman.forever.already"), true);
			return;
		}
		Long safe = IMMUNE.get(target.getUuid());
		if (safe != null && now < safe) {
			player.sendMessage(Text.translatable("chainsawman.forever.settling"), true);
			return;
		}
		if (!player.isCreative() && player.experienceLevel < PRICE) {
			player.sendMessage(Text.translatable("chainsawman.forever.price", PRICE), true);
			return;
		}
		if (!player.isCreative()) {
			player.addExperienceLevels(-PRICE);
		}
		begin(player, target);
	}

	public static boolean active(ServerPlayerEntity owner) {
		return BY_OWNER.containsKey(owner.getUuid());
	}

	public static boolean locked(Entity entity) {
		return entity != null && LOCKS.containsKey(entity.getUuid()) && LOCKS.get(entity.getUuid()).phase != ForeverHubEntity.SINKING;
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
		IMMUNE.clear();
	}

	/** Ticks until the moment ends, for the tests: moves the start so it ends {@code ticks} from now. */
	public static void endsIn(Entity target, int ticks) {
		Lock l = target == null ? null : LOCKS.get(target.getUuid());
		if (l != null) {
			endAt.put(target.getUuid(), l.world.getTime() + ticks);
		}
	}

	/** Whether {@code lasts} is a boss's moment. */
	public static boolean boss(LivingEntity target) {
		return target.getMaxHealth() >= 100.0F || target instanceof WitherEntity || target instanceof EnderDragonEntity || target instanceof WardenEntity;
	}

	/** The living thing {@code player} looks at within {@link #RANGE} blocks, with nothing solid between; null for none. */
	private static LivingEntity look(ServerPlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d dir = player.getRotationVector(player.getPitch(), player.getYaw());
		Vec3d end = eye.add(dir.multiply(RANGE));
		Box box = player.getBoundingBox().stretch(dir.multiply(RANGE)).expand(1.0);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && e != player, RANGE * RANGE);
		if (hit == null || !(hit.getEntity() instanceof LivingEntity living)) {
			return null;
		}
		HitResult block = player.getServerWorld().raycast(new RaycastContext(eye, hit.getPos(), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		return block.getType() == HitResult.Type.MISS ? living : null;
	}

	// ---- The lock.

	private static void begin(ServerPlayerEntity owner, LivingEntity target) {
		ServerWorld world = owner.getServerWorld();
		int lasts = boss(target) ? BOSS_LASTS : LASTS;
		ForeverHubEntity hub = ChainsawMan.FOREVER_HUB.create(world);
		hub.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ(), target.getYaw(), 0.0F);
		hub.begin(target.getId(), (int) world.getTime(), lasts, world.getRandom().nextInt());
		world.spawnEntity(hub);
		Lock lock = new Lock(owner, target, hub, lasts);
		makeCopies(lock);
		LOCKS.put(target.getUuid(), lock);
		BY_OWNER.put(owner.getUuid(), lock);
		// Freeze it: where it is, as it is.
		ForeverFlags.SERVER.add(target.getId());
		target.setNoGravity(true);
		if (target instanceof MobEntity mob) {
			mob.setAiDisabled(true);
			mob.getNavigation().stop();
		}
		target.setVelocity(Vec3d.ZERO);
		broadcast(world.getServer(), new ForeverLockPayload(target.getId(), true));
		play(world, target, ChainsawSounds.FOREVER_LOCK, 2.2F);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, target.getX(), target.getY() + 0.2, target.getZ(), 24, 1.6, 0.1, 1.6, 0.01);
	}

	private static void play(ServerWorld world, Entity at, SoundEvent sound, float volume) {
		world.playSound(null, at.getX(), at.getY() + at.getHeight() * 0.5, at.getZ(), sound, SoundCategory.PLAYERS, volume, 1.0F);
	}

	private static void broadcast(MinecraftServer server, ForeverLockPayload payload) {
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
		Vec3d[] pastAt = along(past, arcsPast, behindDir);
		Vec3d[] futureAt = along(future, arcsFuture, futureDir);
		for (int i = 0; i < SIDE; i++) {
			addCopy(lock, pastAt[i], ground, hover, width, height, -(i + 1), stride - 0.9F * (i + 1), speed);
			addCopy(lock, futureAt[i], ground, hover, width, height, i + 1, stride + 0.9F * (i + 1), speed);
		}
	}

	/** Points along the polyline {@code poly} at the arc lengths {@code arcs}, carried on straight along {@code dir} past its end. */
	private static Vec3d[] along(List<Vec3d> poly, double[] arcs, Vec3d dir) {
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

	private static void addCopy(Lock lock, Vec3d at, double refGround, double hover, float width, float height, int slot, float stride, float speed) {
		ServerWorld world = lock.world;
		Vec3d spot = standable(world, at.x, at.z, refGround);
		if (spot == null) {
			return;
		}
		double y = spot.y + hover;
		if (!fits(world, spot.x, y, spot.z, width, height)) {
			y = spot.y;
			if (hover > 0.0 && !fits(world, spot.x, y, spot.z, width, height) || !fits(world, spot.x, y, spot.z, width, height)) {
				return;
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
		Lock l = LOCKS.get(entity.getUuid());
		if (l == null || l.phase == ForeverHubEntity.SINKING || source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
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
				if (l.phase == ForeverHubEntity.SINKING || !(source.getAttacker() instanceof PlayerEntity attacker) || amount <= 0.0F) {
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
		if (LOCKS.isEmpty() && SINKING.isEmpty()) {
			return;
		}
		for (Lock l : List.copyOf(LOCKS.values())) {
			tick(server, l);
		}
		SINKING.removeIf(l -> {
			if (l.world.getTime() - l.phaseAt >= SINK || l.hub.isRemoved()) {
				l.hub.discard();
				return true;
			}
			return false;
		});
	}

	private static void tick(MinecraftServer server, Lock l) {
		long now = l.world.getTime();
		LivingEntity target = l.target;
		if (target.isRemoved() || !target.isAlive() || target.getWorld() != l.world) {
			cleanUp(l);
			return;
		}
		if (l.phase != ForeverHubEntity.SINKING) {
			pin(l);
		}
		if (l.phase == ForeverHubEntity.HELD) {
			Long forced = endAt.get(target.getUuid());
			if (now - l.start >= l.lasts || forced != null && now >= forced) {
				endAt.remove(target.getUuid());
				startRelease(l, now);
			}
		} else if (l.phase == ForeverHubEntity.ENDING && now - l.phaseAt >= SNAP) {
			blow(server, l, now);
		}
	}

	/** Holds the target where it froze (the mixins stop its own movement; this puts right whatever else moved it). */
	private static void pin(Lock l) {
		LivingEntity t = l.target;
		t.setVelocity(Vec3d.ZERO);
		t.setNoGravity(true);
		if (t instanceof ServerPlayerEntity player) {
			if (player.getPos().squaredDistanceTo(l.at) > 0.04 && player.networkHandler != null) {
				player.networkHandler.requestTeleport(l.at.x, l.at.y, l.at.z, l.yaw, l.pitch);
			}
		} else if (t.getPos().squaredDistanceTo(l.at) > 1.0E-4) {
			t.refreshPositionAndAngles(l.at.x, l.at.y, l.at.z, l.yaw, l.pitch);
			t.setHeadYaw(l.headYaw);
			t.setBodyYaw(l.bodyYaw);
		}
	}

	private static void startRelease(Lock l, long now) {
		l.phase = ForeverHubEntity.ENDING;
		l.phaseAt = now;
		l.hub.phase(ForeverHubEntity.ENDING, (int) now);
		for (ForeverCopyEntity c : l.copies) {
			c.snap((int) now);
		}
		stopSounds(l);
		play(l.world, l.target, ChainsawSounds.FOREVER_RELEASE, 2.6F);
	}

	private static void stopSounds(Lock l) {
		StopSoundS2CPacket stop = new StopSoundS2CPacket(ChainsawSounds.FOREVER_LOCK.getId(), SoundCategory.PLAYERS);
		for (ServerPlayerEntity p : l.world.getServer().getPlayerManager().getPlayerList()) {
			p.networkHandler.sendPacket(stop);
		}
	}

	/** The line is in: let the target go and every stored hit goes off at once. */
	private static void blow(MinecraftServer server, Lock l, long now) {
		LivingEntity t = l.target;
		ServerWorld world = l.world;
		unfreeze(l);
		for (ForeverCopyEntity c : l.copies) {
			c.discard();
		}
		l.copies.clear();
		l.phase = ForeverHubEntity.SINKING;
		l.phaseAt = now;
		l.hub.phase(ForeverHubEntity.SINKING, (int) now);
		LOCKS.remove(t.getUuid());
		BY_OWNER.remove(l.owner);
		SINKING.add(l);
		long ticks = server.getTicks();
		COOL.put(l.owner, ticks + COOLDOWN);
		IMMUNE.put(t.getUuid(), ticks + IMMUNITY);
		ServerPlayerEntity owner = server.getPlayerManager().getPlayer(l.owner);
		float total = 0.0F;
		Map<UUID, Float> by = new HashMap<>();
		for (Hit h : l.hits) {
			total += h.amount;
			by.merge(h.attacker, h.amount, Float::sum);
		}
		Vec3d heart = t.getPos().add(0.0, t.getHeight() * 0.5, 0.0);
		burst(world, heart, l.hits.size());
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
			float amount = Math.min(CAP, total * MULT);
			t.timeUntilRegen = 0;
			t.damage(source, amount);
			// Flung away from whoever called it, harder the more hits there were.
			Vec3d from = owner != null ? owner.getPos() : l.at.add(0.0, 0.0, 1.0);
			double strength = Math.min(2.6, 0.7 + 0.2 * l.hits.size());
			t.takeKnockback(strength, from.x - t.getX(), from.z - t.getZ());
			t.velocityModified = true;
		}
	}

	private static void burst(ServerWorld world, Vec3d at, int hits) {
		world.spawnParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		world.spawnParticles(new DustParticleEffect(new Vector3f(0.75F, 0.05F, 0.08F), 2.0F), at.x, at.y, at.z, 40 + hits * 6, 0.9, 0.7, 0.9, 0.1);
		world.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 0.8, 0.7, 0.8, 0.6);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 24, 0.7, 0.6, 0.7, 0.08);
		world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
	}

	/** Gives back what the freeze took: gravity, the AI, the velocity it had. */
	private static void unfreeze(Lock l) {
		LivingEntity t = l.target;
		ForeverFlags.SERVER.remove(t.getId());
		broadcast(l.world.getServer(), new ForeverLockPayload(t.getId(), false));
		t.setNoGravity(l.wasNoGravity);
		if (t instanceof MobEntity mob) {
			mob.setAiDisabled(l.wasNoAi);
		}
		t.setVelocity(l.velocity);
		t.velocityModified = true;
	}

	/** Ends a lock at once with nothing going off (the target died, left, or the world went): the line gone, the flesh sinking. */
	private static void cleanUp(Lock l) {
		unfreeze(l);
		stopSounds(l);
		for (ForeverCopyEntity c : l.copies) {
			c.discard();
		}
		l.copies.clear();
		LOCKS.remove(l.target.getUuid());
		BY_OWNER.remove(l.owner);
		endAt.remove(l.target.getUuid());
		if (l.phase != ForeverHubEntity.SINKING) {
			l.phase = ForeverHubEntity.SINKING;
			l.phaseAt = l.world.getTime();
			l.hub.phase(ForeverHubEntity.SINKING, (int) l.phaseAt);
			SINKING.add(l);
		}
		long ticks = l.world.getServer().getTicks();
		COOL.put(l.owner, ticks + COOLDOWN);
		IMMUNE.put(l.target.getUuid(), ticks + IMMUNITY);
	}

	// ---- Where things have been.

	/** Notes where every living thing near a contract holder is, so the past copies can be placed where it really was. */
	private static void recordHistory(MinecraftServer server) {
		boolean any = false;
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayerEntity holder : server.getPlayerManager().getPlayerList()) {
			if (Contracts.get(holder) != Contract.ETERNITY) {
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
