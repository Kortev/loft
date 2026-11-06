package io.github.kortev.gojolander.purple;

import io.github.kortev.gojolander.BlindfoldItem;
import io.github.kortev.gojolander.GojoDamage;
import io.github.kortev.gojolander.GojoRules;
import io.github.kortev.gojolander.GojoState;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.Owner;
import io.github.kortev.gojolander.domain.DomainManager;
import io.github.kortev.gojolander.network.PurpleEndPayload;
import io.github.kortev.gojolander.network.PurpleLaunchPayload;
import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.network.ModNetworking;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Hollow Technique: Purple, on the server: who may cast it and when, and what it does. Blue and Red are brought together
 * into one sphere of imaginary mass ({@link PurpleTimeline}); released, it flies straight along where its caster looked
 * ({@link PurpleTimeline#SPEED} blocks a tick) for {@code hollowPurpleRange} blocks and <b>erases everything in its way</b>.
 *
 * <p>What it passes through:
 * <ul>
 *   <li>Blocks vanish: no drops, no neighbour updates, nothing in a chest or a furnace left behind, a clean round tunnel
 *   or trench of {@code hollowPurpleRadius} through hills, forests, houses and water. Not what cannot be broken
 *   (bedrock, the end portal frame, barriers, command blocks: anything with negative hardness). With
 *   {@code hollowPurpleErases} off it hurts and leaves the world alone. The carving is done a slice a tick, behind the
 *   sphere, its slice bounded by a per-tick budget, so it cannot lag however big the radius.</li>
 *   <li>Everything living in it dies, players too (but not those in creative or spectator, and not him). <b>Bosses</b> (the
 *   {@code c:bosses} tag) are not killed: they take {@link #BOSS_DAMAGE} once per cast, which is a heavy blow (half a Wither),
 *   not the end of them. Whatever else is in it (dropped items, experience, arrows, paintings) is simply erased.</li>
 *   <li>Plants and torches on the rim of the cut, which lose what they stood on without anything telling them so,
 *   are taken too, so nothing is left hanging off it.</li>
 * </ul>
 *
 * <p>Fits the mod's other rules: not cast while a Genesis Key event is going on, and not while he is in his Domain (or the
 * Domain in a Purple); the cooldown shows on the blindfold, like the Domain's.
 */
public final class PurpleManager {
	/** Heavy damage a boss takes, once per cast. */
	public static final float BOSS_DAMAGE = 150.0F;
	/** How far in front of his eyes its surface forms, as it is released: clear of him and his feet. */
	private static final double GAP = 1.5;
	/** How many blocks it examines at most each tick (the slice behind the sphere is cut shorter to fit). */
	private static final int BUDGET = 60000;
	/** How far outside its radius plants and the like, left with nothing under them, are cleaned off. */
	private static final double RIM = 1.6;
	/** Block flags: tell clients, but no neighbour or shape updates (so nothing drops or flows), no drops. */
	private static final int ERASE = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

	private static final List<Purple> ACTIVE = new ArrayList<>();
	private static int nextId = 1;

	/** One Hollow Purple, from the key until the last of its light is gone. */
	public static final class Purple {
		public final int id;
		public final ServerWorld world;
		@Nullable
		public final UUID caster;
		/** Where he cast it from (his eyes), the way it flies and where it is released (its middle at that moment). */
		public final Vec3d eye;
		public final Vec3d dir;
		public final Vec3d form;
		public final double radius;
		public final double range;
		public final boolean erases;
		int age;
		/** How far along its path the carving has got (blocks from {@link #form}). */
		double carved;
		/** Counts, for the tests and the log. */
		public int erased;
		public int killed;
		final Set<UUID> hurt = new HashSet<>();

		Purple(int id, ServerWorld world, @Nullable UUID caster, Vec3d eye, Vec3d dir, double radius, double range, boolean erases) {
			this.id = id;
			this.world = world;
			this.caster = caster;
			this.eye = eye;
			this.dir = dir;
			this.radius = radius;
			this.range = range;
			this.erases = erases;
			this.form = eye.add(dir.multiply(radius + GAP));
		}

		public int age() {
			return age;
		}

		/** Whether it has been released: flying, or collapsed already. */
		public boolean released() {
			return age >= PurpleTimeline.RELEASE;
		}

		/** How far it has carved so far. */
		public double carved() {
			return carved;
		}

		/** Where its middle is now. */
		public Vec3d at() {
			return form.add(dir.multiply(PurpleTimeline.front(age, range)));
		}

		PurpleLaunchPayload launch() {
			return new PurpleLaunchPayload(id, caster != null ? caster : PurpleLaunchPayload.NOBODY, eye, dir, (float) radius, (float) range, age);
		}
	}

	private PurpleManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PurpleManager::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE.clear());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			for (Purple purple : ACTIVE) {
				if (purple.world == handler.getPlayer().getServerWorld()) {
					ModNetworking.send(handler.getPlayer(), purple.launch());
				}
			}
		});
	}

	/** The Purples running now. */
	public static List<Purple> active() {
		return Collections.unmodifiableList(ACTIVE);
	}

	/** Whether {@code player} has one going now (casting it, or its sphere still flying). */
	public static boolean busy(PlayerEntity player) {
		for (Purple purple : ACTIVE) {
			if (player.getUuid().equals(purple.caster)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The Hollow Purple key, pressed by {@code player}: casts it from their eyes along their look if they are the owner,
	 * wearing the blindfold, not in the middle of a Domain or a Purple, and ready; tells them why not otherwise.
	 * Returns whether it was cast.
	 */
	public static boolean cast(ServerPlayerEntity player) {
		Gojolander.LOGGER.debug("Hollow Purple key from {}", player.getGameProfile().getName());
		if (player.isSpectator() || !player.isAlive() || !BlindfoldItem.wearing(player)) {
			return false;
		}
		if (!Owner.is(player)) {
			DomainManager.deny(player, Text.translatable("message.gojolander.not_yours").formatted(Formatting.GRAY));
			return false;
		}
		if (GapManager.running()) {
			DomainManager.deny(player, Text.translatable("message.gojolander.purple.gap").formatted(Formatting.DARK_PURPLE));
			return false;
		}
		if (busy(player) || DomainManager.busy(player)) {
			DomainManager.deny(player, Text.translatable("message.gojolander.purple.busy").formatted(Formatting.LIGHT_PURPLE));
			return false;
		}
		MinecraftServer server = player.getServer();
		GojoState state = GojoState.get(server);
		long now = server.getOverworld().getTime();
		long ready = state.purpleReadyAt(player.getUuid());
		if (ready > now) {
			DomainManager.deny(player,
					Text.translatable("message.gojolander.purple.cooldown", DomainManager.clock(ready - now)).formatted(Formatting.LIGHT_PURPLE));
			return false;
		}
		ServerWorld world = player.getServerWorld();
		launch(world, player, player.getEyePos(), player.getRotationVec(1.0F));
		int cooldown = world.getGameRules().getInt(GojoRules.PURPLE_COOLDOWN) * 20;
		if (cooldown > 0) {
			state.setPurpleReadyAt(player.getUuid(), now + cooldown);
			// One overlay on the blindfold for both powers: whichever is further from ready.
			long domain = Math.max(0L, state.readyAt(player.getUuid()) - now);
			player.getItemCooldownManager().set(Gojolander.BLINDFOLD, (int) Math.max(cooldown, domain));
		}
		return true;
	}

	/** Casts one from {@code eye} along {@code dir} (a command, or a test), by {@code caster} or nobody, with the world's rules as they are. */
	public static Purple launch(ServerWorld world, @Nullable ServerPlayerEntity caster, Vec3d eye, Vec3d dir) {
		int radius = world.getGameRules().getInt(GojoRules.PURPLE_RADIUS);
		int range = world.getGameRules().getInt(GojoRules.PURPLE_RANGE);
		boolean erases = world.getGameRules().getBoolean(GojoRules.PURPLE_ERASES);
		Purple purple = new Purple(nextId++, world, caster != null ? caster.getUuid() : null, eye, dir.normalize(), radius, range, erases);
		ACTIVE.add(purple);
		ModNetworking.broadcast(world, purple.launch());
		return purple;
	}

	/** Makes {@code caster}'s Purple ready again at once (operators: {@code /gojolander ready}). */
	public static void ready(ServerPlayerEntity caster) {
		GojoState state = GojoState.get(caster.getServer());
		state.setPurpleReadyAt(caster.getUuid(), 0L);
		if (state.readyAt(caster.getUuid()) <= caster.getServer().getOverworld().getTime()) {
			caster.getItemCooldownManager().remove(Gojolander.BLINDFOLD);
		}
	}

	/** Ends every Purple now (operators and tests); returns how many there were. A released sphere simply stops where it is. */
	public static int cancelAll() {
		int count = ACTIVE.size();
		for (Purple purple : ACTIVE) {
			if (!purple.released()) {
				ModNetworking.broadcast(purple.world, new PurpleEndPayload(purple.id));
			}
		}
		ACTIVE.clear();
		return count;
	}

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		Iterator<Purple> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Purple purple = it.next();
			purple.age++;
			if (!purple.released()) {
				if (casterGone(server, purple) || GapManager.running()) {
					ModNetworking.broadcast(purple.world, new PurpleEndPayload(purple.id));
					it.remove();
				}
				continue;
			}
			if (purple.age >= PurpleTimeline.end(purple.range)) {
				it.remove();
				continue;
			}
			double front = PurpleTimeline.front(purple.age, purple.range);
			if (front > purple.carved || purple.age == PurpleTimeline.RELEASE) {
				double width = 2.0 * purple.radius + 2.0 * RIM + 1.0;
				double slice = Math.max(0.5, BUDGET / (width * width) - width);
				double to = Math.min(front, purple.carved + slice);
				sweep(purple, purple.carved, to);
				purple.carved = to;
			}
		}
	}

	private static boolean casterGone(MinecraftServer server, Purple purple) {
		if (purple.caster == null) {
			return false;
		}
		ServerPlayerEntity caster = server.getPlayerManager().getPlayer(purple.caster);
		return caster == null || !caster.isAlive() || caster.getServerWorld() != purple.world;
	}

	/** Takes everything the sphere passes through going from {@code from} to {@code to} blocks along its path. */
	private static void sweep(Purple purple, double from, double to) {
		Vec3d a = purple.form.add(purple.dir.multiply(from));
		Vec3d b = purple.form.add(purple.dir.multiply(to));
		double length = to - from;
		double r = purple.radius;
		double reach = r + RIM;
		if (purple.erases) {
			carve(purple, a, length, r, reach);
		}
		kill(purple, a, b, r);
	}

	private static void carve(Purple purple, Vec3d a, double length, double r, double reach) {
		ServerWorld world = purple.world;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		Vec3d b = a.add(purple.dir.multiply(length));
		int x0 = MathHelper.floor(Math.min(a.x, b.x) - reach);
		int x1 = MathHelper.floor(Math.max(a.x, b.x) + reach);
		int y0 = Math.max(world.getBottomY(), MathHelper.floor(Math.min(a.y, b.y) - reach));
		int y1 = Math.min(world.getTopY() - 1, MathHelper.floor(Math.max(a.y, b.y) + reach));
		int z0 = MathHelper.floor(Math.min(a.z, b.z) - reach);
		int z1 = MathHelper.floor(Math.max(a.z, b.z) + reach);
		double inner = r * r;
		double outer = reach * reach;
		Vec3d dir = purple.dir;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (!world.isChunkLoaded(x >> 4, z >> 4)) {
					continue;
				}
				for (int y = y0; y <= y1; y++) {
					double cx = x + 0.5 - a.x;
					double cy = y + 0.5 - a.y;
					double cz = z + 0.5 - a.z;
					double along = length > 1.0E-6 ? MathHelper.clamp(cx * dir.x + cy * dir.y + cz * dir.z, 0.0, length) : 0.0;
					double dx = cx - dir.x * along;
					double dy = cy - dir.y * along;
					double dz = cz - dir.z * along;
					double d2 = dx * dx + dy * dy + dz * dz;
					if (d2 > outer) {
						continue;
					}
					pos.set(x, y, z);
					BlockState state = world.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					if (d2 <= inner ? state.getHardness(world, pos) < 0.0F : !fragile(world, pos, state)) {
						continue;
					}
					erase(world, pos);
					purple.erased++;
				}
			}
		}
	}

	/** Something that cannot stand on nothing and is not worth a block: a plant, a torch, a carpet of snow. */
	private static boolean fragile(ServerWorld world, BlockPos pos, BlockState state) {
		return state.getFluidState().isEmpty() && state.getCollisionShape(world, pos).isEmpty() && state.getHardness(world, pos) >= 0.0F
				&& !state.isOf(Blocks.NETHER_PORTAL) && !state.isOf(Blocks.END_PORTAL) && !state.isOf(Blocks.END_GATEWAY);
	}

	private static void erase(ServerWorld world, BlockPos pos) {
		BlockEntity entity = world.getBlockEntity(pos);
		if (entity instanceof Inventory inventory) {
			// Whatever was in a chest or a furnace goes with it: nothing drops.
			inventory.clear();
		}
		world.setBlockState(pos, Blocks.AIR.getDefaultState(), ERASE);
	}

	/** Everything living in the swept capsule dies (bosses: heavy damage, once); everything else in it is erased. */
	private static void kill(Purple purple, Vec3d a, Vec3d b, double r) {
		ServerWorld world = purple.world;
		Box box = new Box(a, b).expand(r + 2.0);
		Vec3d ab = b.subtract(a);
		double len2 = ab.lengthSquared();
		for (Entity entity : world.getOtherEntities(null, box)) {
			if (entity.getUuid().equals(purple.caster) || !entity.isAlive()) {
				continue;
			}
			if (entity instanceof PlayerEntity player && (player.isSpectator() || player.isCreative())) {
				continue;
			}
			Box body = entity.getBoundingBox();
			Vec3d c = body.getCenter();
			double t = len2 > 1.0E-9 ? MathHelper.clamp(c.subtract(a).dotProduct(ab) / len2, 0.0, 1.0) : 0.0;
			Vec3d q = a.add(ab.multiply(t));
			double slack = 0.5 * Math.max(body.getLengthX(), Math.max(body.getLengthY(), body.getLengthZ()));
			if (c.squaredDistanceTo(q) > (r + slack) * (r + slack)) {
				continue;
			}
			if (!(entity instanceof LivingEntity living)) {
				entity.discard();
				continue;
			}
			burst(world, c, Math.max(8, (int) (body.getAverageSideLength() * 14.0)));
			if (living.getType().isIn(ConventionalEntityTypeTags.BOSSES)) {
				if (purple.hurt.add(living.getUuid())) {
					living.damage(GojoDamage.hollowPurple(world), BOSS_DAMAGE);
				}
				continue;
			}
			living.damage(GojoDamage.hollowPurple(world), Float.MAX_VALUE);
			if (living.isAlive()) {
				living.kill();
			}
			purple.killed++;
		}
	}

	/** A puff of violet dust where something was erased. */
	private static void burst(ServerWorld world, Vec3d at, int count) {
		world.spawnParticles(new DustParticleEffect(new Vector3f(0.62F, 0.18F, 1.0F), 1.6F), at.x, at.y, at.z, count, 0.4, 0.5, 0.4, 0.05);
	}
}
