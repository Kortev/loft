package io.github.kortev.chainsawman.contract;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.network.EchoPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.random.Random;

/**
 * Echo, the Eternity contract's power: press "Use contract" and you burst into a crowd of yourself, every one of them you from a
 * different moment in eternity, ringing your target (the living thing you look at, else yourself). Kortev's design.
 *
 * <p>The copies ({@link EchoEntity}) stand on real ground round the target, at different heights where the ground is uneven. For
 * {@link #LASTS} ticks they mirror what you do, each after its own delay so a move ripples round the ring: your movement
 * measured against the target (in, out, round) is given to each from its own place, your jumps are jumped, your swings are
 * swung, and a copy within reach of the target hits it for a little ({@link #HIT}), the target taking an echo hit at most every
 * {@link #HIT_GAP} ticks however many swing. Past copies lag ({@link #PAST_DELAY}), future copies are the quickest
 * ({@link #FUTURE_DELAY}). Mobs that were after you switch to copies now and then; nothing a copy suffers touches you. Then the
 * finale: they all turn to the target, hold a beat ({@link #HOLD}), and streak back into you at once, the target taking one
 * combined hit. If the target dies first the crowd collapses back early with the same finale and no hit.
 *
 * <p>All of this is the server's; the clients draw the copies, the burst, the disguise and the atmosphere ({@code EchoPayload}).
 */
public final class Echo {
	/** How many copies, how long it lasts (15 s), the cooldown from its end (45 s), how far a target may be (blocks). */
	public static final int COPIES = 14;
	public static final int LASTS = 300;
	public static final int COOLDOWN = 900;
	public static final double RANGE = 24.0;
	/** The ring round the target: nearest and furthest (blocks), and how far up and down a copy may look for ground. */
	public static final double RING_MIN = 4.0;
	public static final double RING_MAX = 6.0;
	public static final int VERTICAL = 4;
	/** The delays (ticks, inclusive): past copies lag, future copies are quickest. */
	public static final int[] PAST_DELAY = {6, 14};
	public static final int[] FUTURE_DELAY = {0, 3};
	/** A copy's reach of the target (blocks), what an echo hit does, and the fewest ticks between two hits on one target. */
	public static final double REACH = 3.5;
	public static final float HIT = 1.5F;
	public static final int HIT_GAP = 5;
	/** The finale: how long they hold, turned to the target, before they streak in (ticks), the combined hit (2 + 0.5 a copy, at most 10). */
	public static final int HOLD = 10;
	public static final float FINALE_BASE = 2.0F;
	public static final float FINALE_EACH = 0.5F;
	public static final float FINALE_MAX = 10.0F;
	/** The ring never closes tighter than this round the target (blocks). */
	private static final double CLOSEST = 1.5;
	/** How often (ticks) a mob after you may switch, and how likely: one in this many. */
	private static final int SWITCH_EVERY = 10;
	private static final int SWITCH_ODDS = 4;

	/** One tick of what the owner did: where, how they stood against the target, and whether they jumped or swung. */
	private record Frame(Vec3d pos, double radius, double angle, boolean jump, boolean swing) {
	}

	/** One copy and what it mirrors: its place in the ring (radius and angle from the target) and its delay. */
	private static final class Copy {
		final EchoEntity entity;
		final int delay;
		final double radius;
		final double angle;
		int stuck;

		Copy(EchoEntity entity, int delay, double radius, double angle) {
			this.entity = entity;
			this.delay = delay;
			this.radius = radius;
			this.angle = angle;
		}
	}

	private static final class State {
		final UUID owner;
		final ServerWorld world;
		/** The target (the owner when there is none: the crowd rings yourself). */
		final LivingEntity target;
		final boolean self;
		long start;
		final int kind;
		final List<Copy> copies = new ArrayList<>();
		final List<Frame> frames = new ArrayList<>();
		final Map<Integer, Long> mobs = new HashMap<>();
		Frame first;
		boolean swung;
		boolean wasOnGround = true;
		double lastY;
		/** 0 mirroring, 1 holding for the finale, 2 streaking back; when this phase began; whether the finale hits. */
		int phase;
		long phaseAt;
		boolean hit = true;

		State(ServerPlayerEntity owner, LivingEntity target, boolean self, int kind) {
			this.owner = owner.getUuid();
			this.world = owner.getServerWorld();
			this.target = target;
			this.self = self;
			this.start = owner.getServerWorld().getTime();
			this.kind = kind;
		}
	}

	private static final Map<UUID, State> ACTIVE = new HashMap<>();
	/** When each owner may use Echo again (world time). */
	private static final Map<UUID, Long> COOL = new HashMap<>();
	/** When each target last took an echo hit (world time), from any copy of any echo. */
	private static final Map<UUID, Long> HIT_AT = new HashMap<>();

	private Echo() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Echo::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			for (State s : ACTIVE.values()) {
				if (ServerPlayNetworking.canSend(handler.player, EchoPayload.ID)) {
					ServerPlayNetworking.send(handler.player, told(s));
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(handler.player, false));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			COOL.clear();
			HIT_AT.clear();
		});
	}

	// ---- Asking for it.

	/** The "Use contract" key. */
	public static void use(ServerPlayerEntity player) {
		Contract held = Contracts.get(player);
		if (held == Contract.NONE) {
			player.sendMessage(Text.translatable("chainsawman.echo.no_contract"), true);
			return;
		}
		if (held != Contract.ETERNITY) {
			return;
		}
		if (Form.in(player)) {
			player.sendMessage(Text.translatable("chainsawman.echo.human_only"), true);
			return;
		}
		if (!player.isAlive() || Downed.is(player) || ACTIVE.containsKey(player.getUuid())) {
			return;
		}
		long now = player.getServerWorld().getTime();
		Long until = COOL.get(player.getUuid());
		if (until != null && now < until) {
			player.sendMessage(Text.translatable("chainsawman.echo.cooldown", (until - now + 19) / 20), true);
			return;
		}
		begin(player);
	}

	public static boolean active(ServerPlayerEntity player) {
		return ACTIVE.containsKey(player.getUuid());
	}

	/** The copies {@code player} has now. */
	public static List<EchoEntity> copies(ServerPlayerEntity player) {
		State s = ACTIVE.get(player.getUuid());
		List<EchoEntity> out = new ArrayList<>();
		if (s != null) {
			for (Copy c : s.copies) {
				out.add(c.entity);
			}
		}
		return out;
	}

	/** What the target is (the owner, if the crowd rings them), or null with no echo going. */
	public static LivingEntity target(ServerPlayerEntity player) {
		State s = ACTIVE.get(player.getUuid());
		return s == null ? null : s.target;
	}

	/** Ticks left of the cooldown (0 when it may be used). */
	public static long cooldownLeft(ServerPlayerEntity player) {
		Long until = COOL.get(player.getUuid());
		return until == null ? 0 : Math.max(0, until - player.getServerWorld().getTime());
	}

	/** Whether {@code entity} is one of the copies of the echo {@code owner} has going. */
	public static boolean isCopyOf(Entity entity, ServerPlayerEntity owner) {
		return entity instanceof EchoEntity e && owner.getUuid().equals(e.owner());
	}

	/** Skips the cooldown (for the tests and for trying it out). */
	public static void ready(ServerPlayerEntity player) {
		COOL.remove(player.getUuid());
	}

	/** Ticks until the finale starts, for the tests: moves the start back so it comes {@code ticks} from now. */
	public static void finaleIn(ServerPlayerEntity player, int ticks) {
		State s = ACTIVE.get(player.getUuid());
		if (s != null) {
			s.start = s.world.getTime() - (LASTS - ticks);
		}
	}

	private static EchoPayload told(State s) {
		return new EchoPayload(s.owner, (byte) s.kind, s.self ? -1 : s.target.getId(), s.phase >= 1 ? (int) s.phaseAt : -1);
	}

	private static void broadcast(MinecraftServer server, EchoPayload payload) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (ServerPlayNetworking.canSend(p, EchoPayload.ID)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	/** A swing of {@code player}, told by the game ({@code EchoSwingMixin}). */
	public static void swung(ServerPlayerEntity player) {
		State s = ACTIVE.get(player.getUuid());
		if (s != null) {
			s.swung = true;
		}
	}

	// ---- The burst.

	private static void begin(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		LivingEntity target = look(player);
		boolean self = target == null;
		LivingEntity aim = self ? player : target;
		Random random = world.getRandom();
		State s = new State(player, aim, self, random.nextBoolean() ? EchoEntity.PAST : EchoEntity.FUTURE);
		// Half of them past and half future, mixed round the ring.
		List<Integer> kinds = new ArrayList<>();
		for (int i = 0; i < COPIES; i++) {
			kinds.add(i % 2 == 0 ? EchoEntity.PAST : EchoEntity.FUTURE);
		}
		Collections.shuffle(kinds, new java.util.Random(random.nextLong()));
		Vec3d center = aim.getPos();
		double base = random.nextDouble() * Math.PI * 2.0;
		List<Vec3d> taken = new ArrayList<>();
		taken.add(player.getPos());
		int made = 0;
		for (int i = 0; i < COPIES; i++) {
			double angle0 = base + Math.PI * 2.0 * i / COPIES + (random.nextDouble() - 0.5) * 0.28;
			double radius0 = RING_MIN + (RING_MAX - RING_MIN) * random.nextDouble();
			Vec3d spot = null;
			double radius = radius0;
			double angle = angle0;
			for (int attempt = 0; attempt < 10 && spot == null; attempt++) {
				if (attempt > 0) {
					radius = RING_MIN + (RING_MAX - RING_MIN) * random.nextDouble();
					angle = angle0 + (random.nextDouble() - 0.5) * 0.5;
				}
				Vec3d candidate = standable(world, center.x + Math.cos(angle) * radius, center.z + Math.sin(angle) * radius, center.y, random);
				if (candidate != null && apart(taken, candidate)) {
					spot = candidate;
				}
			}
			if (spot == null) {
				continue;
			}
			taken.add(spot);
			int kind = kinds.get(i);
			int[] range = kind == EchoEntity.PAST ? PAST_DELAY : FUTURE_DELAY;
			int delay = range[0] + random.nextInt(range[1] - range[0] + 1);
			EchoEntity e = ChainsawMan.ECHO_ENTITY.create(world);
			if (e == null) {
				continue;
			}
			e.refreshPositionAndAngles(spot.x, spot.y, spot.z, 0.0F, 0.0F);
			e.born(player.getUuid(), kind, player.getPos(), (int) world.getTime());
			e.face(center);
			world.spawnEntity(e);
			s.copies.add(new Copy(e, delay, Math.hypot(spot.x - center.x, spot.z - center.z), Math.atan2(spot.z - center.z, spot.x - center.x)));
			made++;
		}
		if (made == 0) {
			player.sendMessage(Text.translatable("chainsawman.echo.no_room"), true);
			return;
		}
		s.lastY = player.getY();
		s.wasOnGround = player.isOnGround();
		ACTIVE.put(player.getUuid(), s);
		// Mobs that were after you start to lose track.
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(32.0), m -> m.getTarget() == player)) {
			s.mobs.put(mob.getId(), world.getTime());
		}
		EchoEffects.burst(world, player, s.copies.stream().map(c -> c.entity).toList());
		broadcast(player.server, told(s));
	}

	private static boolean apart(List<Vec3d> taken, Vec3d spot) {
		for (Vec3d t : taken) {
			if (t.squaredDistanceTo(spot) < 1.0) {
				return false;
			}
		}
		return true;
	}

	/** The living thing {@code player} looks at within {@link #RANGE} blocks, with nothing solid between; null for none. */
	private static LivingEntity look(ServerPlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d dir = player.getRotationVector(player.getPitch(), player.getYaw());
		Vec3d end = eye.add(dir.multiply(RANGE));
		Box box = player.getBoundingBox().stretch(dir.multiply(RANGE)).expand(1.0);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && !(e instanceof EchoEntity) && !e.isSpectator() && e != player, RANGE * RANGE);
		if (hit == null || !(hit.getEntity() instanceof LivingEntity living)) {
			return null;
		}
		HitResult block = player.getServerWorld().raycast(new RaycastContext(eye, hit.getPos(), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		return block.getType() == HitResult.Type.MISS ? living : null;
	}

	/**
	 * Somewhere in the column of {@code (x, z)} within {@link #VERTICAL} blocks of {@code y} where a copy can stand: solid ground
	 * with a full top under it, two blocks of air over it, nothing that hurts. Where there is more than one (a ledge over the
	 * ground) it is often the one nearest {@code y} and sometimes another, so the ring ends up at different heights.
	 */
	public static Vec3d standable(ServerWorld world, double x, double z, double y, Random random) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		int centre = MathHelper.floor(y);
		List<Integer> found = new ArrayList<>();
		for (int dy = VERTICAL; dy >= -VERTICAL; dy--) {
			if (stands(world, bx, centre + dy, bz)) {
				found.add(centre + dy);
			}
		}
		if (found.isEmpty()) {
			return null;
		}
		int pick = found.get(0);
		int best = Integer.MAX_VALUE;
		for (int f : found) {
			if (Math.abs(f - centre) < best) {
				best = Math.abs(f - centre);
				pick = f;
			}
		}
		if (found.size() > 1 && random.nextFloat() < 0.45F) {
			pick = found.get(random.nextInt(found.size()));
		}
		return new Vec3d(bx + 0.5, pick, bz + 0.5);
	}

	/** Whether a copy can stand with its feet at block {@code y} of the column {@code (x, z)}. */
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

	// ---- Each tick.

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		for (State s : List.copyOf(ACTIVE.values())) {
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(s.owner);
			if (owner == null || !owner.isAlive() || owner.getServerWorld() != s.world || Form.in(owner) || Downed.is(owner)) {
				if (owner == null) {
					ACTIVE.remove(s.owner);
					discardAll(s);
					cooled(s.owner, s.world);
					broadcast(server, new EchoPayload(s.owner, (byte) -1, -1, -1));
				} else {
					end(owner, false);
				}
				continue;
			}
			tick(server, s, owner);
		}
	}

	private static void tick(MinecraftServer server, State s, ServerPlayerEntity owner) {
		ServerWorld world = s.world;
		long now = world.getTime();
		// What the owner did this tick.
		Vec3d at = s.target.getPos();
		double dx = owner.getX() - at.x;
		double dz = owner.getZ() - at.z;
		boolean jumped = s.wasOnGround && !owner.isOnGround() && owner.getY() > s.lastY + 0.1;
		s.wasOnGround = owner.isOnGround();
		s.lastY = owner.getY();
		Frame frame = new Frame(owner.getPos(), Math.hypot(dx, dz), Math.atan2(dz, dx), jumped, s.swung);
		s.swung = false;
		if (s.first == null) {
			s.first = frame;
		}
		s.frames.add(frame);
		if (s.frames.size() > 40) {
			s.frames.subList(0, s.frames.size() - 40).clear();
		}
		// The copies that are gone (they cannot be, but a command could) are dropped.
		s.copies.removeIf(c -> c.entity.isRemoved());
		if (s.phase == 0) {
			if (!s.self && !s.target.isAlive()) {
				finale(owner, s, now, false);
			} else if (now - s.start >= LASTS || s.copies.isEmpty()) {
				finale(owner, s, now, true);
			}
		}
		if (s.phase == 0) {
			mirror(s, owner, now);
			lose(s, owner, now);
		} else if (s.phase == 1) {
			holdTurned(s, owner);
			if (now - s.phaseAt >= HOLD) {
				for (Copy c : s.copies) {
					c.entity.goBack((int) now);
				}
				s.phase = 2;
				s.phaseAt = now;
				EchoEffects.streakIn(world, owner, s.copies.stream().map(c -> c.entity).toList());
			}
		} else {
			int sinceBack = (int) (now - s.phaseAt);
			Vec3d heart = owner.getPos().add(0.0, 0.9, 0.0);
			for (Copy c : s.copies) {
				c.entity.drive(c.entity.getX(), c.entity.getZ(), 0.0, false);
			}
			if (sinceBack >= EchoEntity.STREAK_IN) {
				int count = s.copies.size();
				if (s.hit && !s.self && s.target.isAlive()) {
					float amount = Math.min(FINALE_MAX, FINALE_BASE + FINALE_EACH * count);
					s.target.timeUntilRegen = 0;
					s.target.damage(owner.getDamageSources().playerAttack(owner), amount);
					HIT_AT.put(s.target.getUuid(), now);
				}
				EchoEffects.boom(world, owner, s.target, heart, count, s.hit && !s.self);
				finish(s, owner, true);
			}
		}
	}

	/** Each copy walks to where the ring has got to, jumps and swings as the owner did {@code delay} ticks ago. */
	private static void mirror(State s, ServerPlayerEntity owner, long now) {
		ServerWorld world = s.world;
		int size = s.frames.size();
		for (Copy c : s.copies) {
			EchoEntity e = c.entity;
			if (now - e.bornAt() < EchoEntity.STREAK_OUT) {
				e.drive(e.getX(), e.getZ(), 0.0, false);
				continue;
			}
			Frame f = s.frames.get(Math.max(0, size - 1 - c.delay));
			Vec3d wantAt;
			if (s.self) {
				// The ring follows you, each copy a little behind.
				double ox = c.radius * Math.cos(c.angle);
				double oz = c.radius * Math.sin(c.angle);
				wantAt = new Vec3d(f.pos.x + ox, e.getY(), f.pos.z + oz);
			} else {
				// Your move against the target (in, out, round), made again from its own place.
				double radius = MathHelper.clamp(c.radius + (f.radius - s.first.radius), CLOSEST, 14.0);
				double angle = c.angle + MathHelper.wrapDegrees((float) Math.toDegrees(f.angle - s.first.angle)) * Math.PI / 180.0;
				Vec3d centre = s.target.getPos();
				wantAt = new Vec3d(centre.x + Math.cos(angle) * radius, e.getY(), centre.z + Math.sin(angle) * radius);
			}
			e.drive(wantAt.x, wantAt.z, 0.6, f.jump);
			e.face(s.target.getPos());
			// A copy that has ended up far from its place (round a wall, over a drop) is put on the nearest ground to it.
			double off = Math.hypot(wantAt.x - e.getX(), wantAt.z - e.getZ());
			c.stuck = off > 5.0 && e.getVelocity().horizontalLengthSquared() < 0.002 ? c.stuck + 1 : 0;
			if (c.stuck > 30) {
				c.stuck = 0;
				Vec3d spot = standable(world, wantAt.x, wantAt.z, s.target.getY(), world.getRandom());
				if (spot != null) {
					e.refreshPositionAndAngles(spot.x, spot.y, spot.z, e.getYaw(), 0.0F);
					e.setVelocity(Vec3d.ZERO);
				}
			}
			if (f.swing) {
				e.swingHand(Hand.MAIN_HAND, true);
				strike(s, owner, e, now);
			}
		}
	}

	/** The copy swings at the target: within reach it hits for a little, unless the target took an echo hit a moment ago. */
	private static void strike(State s, ServerPlayerEntity owner, EchoEntity copy, long now) {
		if (s.self || !s.target.isAlive() || s.target == owner) {
			return;
		}
		if (s.target.getBoundingBox().squaredMagnitude(copy.getEyePos()) > REACH * REACH) {
			return;
		}
		Long last = HIT_AT.get(s.target.getUuid());
		if (last != null && now - last < HIT_GAP) {
			return;
		}
		HIT_AT.put(s.target.getUuid(), now);
		s.target.timeUntilRegen = 0;
		s.target.damage(owner.getDamageSources().playerAttack(owner), HIT);
		EchoEffects.hit(s.world, copy, s.target);
	}

	/** Mobs that were after the owner switch, now and then, to a random copy (and some back to the owner). */
	private static void lose(State s, ServerPlayerEntity owner, long now) {
		if (now % SWITCH_EVERY != 0 || s.copies.isEmpty()) {
			return;
		}
		ServerWorld world = s.world;
		// Mobs that start after the owner part-way count too.
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, owner.getBoundingBox().expand(32.0), m -> m.getTarget() == owner)) {
			s.mobs.putIfAbsent(mob.getId(), now);
		}
		for (Integer id : List.copyOf(s.mobs.keySet())) {
			if (!(world.getEntityById(id) instanceof MobEntity mob) || !mob.isAlive()) {
				s.mobs.remove(id);
				continue;
			}
			LivingEntity now0 = mob.getTarget();
			if (now0 != null && now0 != owner && !isCopyOf(now0, owner)) {
				// It has found someone else.
				s.mobs.remove(id);
				continue;
			}
			if (world.getRandom().nextInt(SWITCH_ODDS) == 0) {
				if (world.getRandom().nextInt(5) == 0) {
					mob.setTarget(owner);
				} else {
					mob.setTarget(s.copies.get(world.getRandom().nextInt(s.copies.size())).entity);
				}
			} else if (now0 == null) {
				mob.setTarget(world.getRandom().nextInt(3) == 0 ? owner : s.copies.get(world.getRandom().nextInt(s.copies.size())).entity);
			}
		}
	}

	private static void holdTurned(State s, ServerPlayerEntity owner) {
		for (Copy c : s.copies) {
			c.entity.drive(c.entity.getX(), c.entity.getZ(), 0.0, false);
			c.entity.face(s.self ? owner.getPos() : s.target.getPos());
		}
	}

	// ---- The end.

	/** The finale begins: all turn to the target (the owner, if it is gone), hold, then streak in. */
	private static void finale(ServerPlayerEntity owner, State s, long now, boolean hit) {
		s.phase = 1;
		s.phaseAt = now;
		s.hit = hit;
		EchoEffects.gather(s.world, owner, s.copies.stream().map(c -> c.entity).toList());
		broadcast(s.world.getServer(), told(s));
	}

	/** Ends {@code player}'s echo at once, if there is one: the copies gone, no finale and no hit. */
	static void end(ServerPlayerEntity player, boolean finale) {
		State s = ACTIVE.get(player.getUuid());
		if (s == null) {
			return;
		}
		if (finale && s.phase == 0) {
			finale(player, s, s.world.getTime(), true);
			return;
		}
		finish(s, player, false);
	}

	private static void finish(State s, ServerPlayerEntity owner, boolean boom) {
		ACTIVE.remove(s.owner);
		for (Copy c : s.copies) {
			EchoEffects.puff(s.world, c.entity);
		}
		discardAll(s);
		// Mobs that were after a copy are after the owner again.
		for (Integer id : s.mobs.keySet()) {
			if (s.world.getEntityById(id) instanceof MobEntity mob && mob.isAlive() && mob.getTarget() instanceof EchoEntity) {
				mob.setTarget(owner);
			}
		}
		cooled(s.owner, s.world);
		broadcast(s.world.getServer(), new EchoPayload(s.owner, (byte) -1, -1, -1));
	}

	private static void cooled(UUID owner, ServerWorld world) {
		COOL.put(owner, world.getTime() + COOLDOWN);
	}

	private static void discardAll(State s) {
		for (Copy c : s.copies) {
			c.entity.discard();
		}
		s.copies.clear();
	}
}
