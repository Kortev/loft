package io.github.kortev.gojolander.domain;

import io.github.kortev.gojolander.BlindfoldItem;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.GojoRules;
import io.github.kortev.gojolander.GojoSounds;
import io.github.kortev.gojolander.GojoState;
import io.github.kortev.gojolander.Owner;
import io.github.kortev.gojolander.network.DomainCaughtPayload;
import io.github.kortev.gojolander.network.DomainEndPayload;
import io.github.kortev.gojolander.network.DomainLockPayload;
import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.network.ModNetworking;
import io.github.kortev.shootingstar.registry.ModCriteria;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Domain Expansion, Unlimited Void, on the server: who may cast it and when, who it catches, holding them still, the
 * barrier that nobody crosses while it stands, and letting them go.
 *
 * <p>Cast by the blindfold's owner ({@link #cast}), a Domain is a sphere of {@code unlimitedVoidRadius} round where he
 * stood. Through the hand sign nothing is decided: anyone can get out of the ring. When the barrier closes
 * ({@link DomainTimeline#CLOSE}) every living thing inside but him is caught (not players in creative or spectator,
 * nor bosses), and held exactly where it was, in the air or not, until the barrier shatters: it neither moves nor falls
 * nor is knocked about, mobs stop thinking, and players can neither act nor, their clients told, move or look round
 * (the server puts them back if they do). Hurting them is left to him: the Domain does no harm of its own. While it
 * stands nothing crosses its surface either way: whoever tries is put back where they were, and anything fired at it
 * or out of it stops dead against it. When it shatters the caught go free, dazed for a while.
 *
 * <p>A Domain ends early, shattering at once, if its caster dies, leaves the world or the game, and never runs while
 * the Genesis Key has the world gone. The clients make all of what is seen and heard from the payloads.
 */
public final class DomainManager {
	/** Advancement events ({@code shootingstar:event}): cast and closed, a player caught in it, a crowd caught at once. */
	public static final String CAST = "unlimited_void";
	public static final String CAUGHT_PLAYER = "unlimited_void_player";
	public static final String CROWD = "unlimited_void_crowd";
	public static final int CROWD_SIZE = 10;
	/** How far either side of the barrier its watch reaches: whatever moves faster than this a tick is not seen crossing. */
	private static final double SHELL = 6.0;
	/** How far a held player may drift (squared, in blocks) before the server puts them back. */
	private static final double DRIFT = 0.04;

	private static final List<Domain> ACTIVE = new ArrayList<>();
	/** Everything held still by a Domain, for {@link DomainHold}. */
	private static final Set<UUID> HELD = new HashSet<>();
	private static int nextId = 1;

	/** Where something caught was when the barrier closed, and its network id for the clients. */
	record Hold(int netId, Vec3d pos, float yaw, float pitch) {
	}

	/** Which side of the barrier something near it was on last tick, and where. */
	record Side(boolean inside, Vec3d pos) {
	}

	/** One Domain, from the hand sign until the last of its barrier is gone. */
	public static final class Domain {
		public final int id;
		public final ServerWorld world;
		@Nullable
		public final UUID caster;
		public final Vec3d center;
		public final double radius;
		/** How many ticks its void holds, from the game rule as it was cast. */
		public final int hold;
		int age;
		final Map<UUID, Hold> caught = new LinkedHashMap<>();
		final Map<UUID, Side> sides = new HashMap<>();
		boolean released;

		Domain(int id, ServerWorld world, @Nullable UUID caster, Vec3d center, double radius, int hold) {
			this.id = id;
			this.world = world;
			this.caster = caster;
			this.center = center;
			this.radius = radius;
			this.hold = hold;
		}

		public int age() {
			return age;
		}

		/** Whether the barrier stands now: closed and not yet shattered. */
		public boolean standing() {
			return age >= DomainTimeline.CLOSE && age < DomainTimeline.collapse(hold);
		}

		/** Who it caught (whether or not they are still held). */
		public Set<UUID> caught() {
			return Collections.unmodifiableSet(caught.keySet());
		}

		/** Whether a point is inside its sphere. */
		public boolean contains(Vec3d point) {
			return point.squaredDistanceTo(center) < radius * radius;
		}

		DomainLockPayload lock() {
			return new DomainLockPayload(id, caster != null ? caster : DomainLockPayload.NOBODY, center, (float) radius, hold, age);
		}

		DomainCaughtPayload caughtPayload() {
			return new DomainCaughtPayload(id, caught.values().stream().map(Hold::netId).toList());
		}
	}

	private DomainManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(DomainManager::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> endAll(false));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> introduce(handler.getPlayer()));
		// Held, nobody does anything: no hitting, no using, no breaking.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> !world.isClient() && isHeld(player)
				? ActionResult.FAIL : ActionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> !world.isClient() && isHeld(player)
				? ActionResult.FAIL : ActionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> !world.isClient() && isHeld(player)
				? ActionResult.FAIL : ActionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> !world.isClient() && isHeld(player)
				? ActionResult.FAIL : ActionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) -> !world.isClient() && isHeld(player)
				? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> world.isClient() || !isHeld(player));
	}

	/** The Domains running now. */
	public static List<Domain> active() {
		return Collections.unmodifiableList(ACTIVE);
	}

	/** Whether a Domain holds {@code entity} still now (server side). */
	public static boolean isHeld(Entity entity) {
		return !HELD.isEmpty() && HELD.contains(entity.getUuid());
	}

	/** Whether {@code player} is in a Domain already: casting one, or caught in one. */
	public static boolean busy(PlayerEntity player) {
		for (Domain domain : ACTIVE) {
			if (player.getUuid().equals(domain.caster) || domain.caught.containsKey(player.getUuid())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The Domain key, pressed by {@code player}: casts it round them if they are the owner, wearing the blindfold and
	 * ready, and tells them why not otherwise. Returns whether it was cast.
	 */
	public static boolean cast(ServerPlayerEntity player) {
		if (player.isSpectator() || !player.isAlive() || !BlindfoldItem.wearing(player)) {
			return false;
		}
		if (!Owner.is(player)) {
			deny(player, Text.translatable("message.gojolander.not_yours").formatted(Formatting.GRAY));
			return false;
		}
		if (GapManager.running()) {
			deny(player, Text.translatable("message.gojolander.domain.gap").formatted(Formatting.DARK_PURPLE));
			return false;
		}
		if (busy(player)) {
			deny(player, Text.translatable("message.gojolander.domain.busy").formatted(Formatting.AQUA));
			return false;
		}
		MinecraftServer server = player.getServer();
		GojoState state = GojoState.get(server);
		long now = server.getOverworld().getTime();
		long ready = state.readyAt(player.getUuid());
		if (ready > now) {
			deny(player, Text.translatable("message.gojolander.domain.cooldown", clock(ready - now)).formatted(Formatting.AQUA));
			return false;
		}
		launch(player.getServerWorld(), player.getPos(), player);
		int cooldown = player.getServerWorld().getGameRules().getInt(GojoRules.VOID_COOLDOWN) * 20;
		if (cooldown > 0) {
			state.setReadyAt(player.getUuid(), now + cooldown);
			player.getItemCooldownManager().set(Gojolander.BLINDFOLD, cooldown);
		}
		return true;
	}

	/** Casts a Domain at {@code center}, by {@code caster} (or nobody, from a command), with the world's rules as they are. */
	public static Domain launch(ServerWorld world, Vec3d center, @Nullable ServerPlayerEntity caster) {
		int radius = world.getGameRules().getInt(GojoRules.VOID_RADIUS);
		int hold = world.getGameRules().getInt(GojoRules.VOID_HOLD) * 20;
		Domain domain = new Domain(nextId++, world, caster != null ? caster.getUuid() : null, center, radius, hold);
		ACTIVE.add(domain);
		ModNetworking.broadcast(world, domain.lock());
		return domain;
	}

	/** Makes {@code caster}'s Domain ready again at once (operators: {@code /gojolander ready}). */
	public static void ready(ServerPlayerEntity caster) {
		GojoState.get(caster.getServer()).setReadyAt(caster.getUuid(), 0L);
		caster.getItemCooldownManager().remove(Gojolander.BLINDFOLD);
	}

	/** Ends every Domain now (operators: {@code /gojolander collapse}); returns how many there were. */
	public static int collapseAll() {
		int count = ACTIVE.size();
		for (Domain domain : new ArrayList<>(ACTIVE)) {
			endEarly(domain);
		}
		return count;
	}

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		Iterator<Domain> it = ACTIVE.iterator();
		List<Domain> early = new ArrayList<>();
		while (it.hasNext()) {
			Domain domain = it.next();
			domain.age++;
			if (domain.age >= DomainTimeline.end(domain.hold)) {
				release(domain, true);
				it.remove();
				continue;
			}
			if (!domain.released && (casterGone(server, domain) || GapManager.running())) {
				early.add(domain);
				continue;
			}
			if (domain.age == DomainTimeline.CLOSE) {
				close(domain, server);
			}
			if (domain.standing()) {
				hold(domain);
				barrier(domain);
			}
			if (domain.age == DomainTimeline.collapse(domain.hold)) {
				release(domain, true);
			}
		}
		early.forEach(DomainManager::endEarly);
	}

	private static boolean casterGone(MinecraftServer server, Domain domain) {
		if (domain.caster == null) {
			return false;
		}
		ServerPlayerEntity caster = server.getPlayerManager().getPlayer(domain.caster);
		return caster == null || !caster.isAlive() || caster.getServerWorld() != domain.world;
	}

	/** The barrier has closed: everything living inside it but its caster is caught. */
	private static void close(Domain domain, MinecraftServer server) {
		double r = domain.radius;
		Box box = Box.of(domain.center, r * 2.0, r * 2.0, r * 2.0);
		int players = 0;
		for (LivingEntity entity : domain.world.getEntitiesByClass(LivingEntity.class, box, e -> catchable(domain, e))) {
			if (!domain.contains(entity.getBoundingBox().getCenter())) {
				continue;
			}
			domain.caught.put(entity.getUuid(), new Hold(entity.getId(), entity.getPos(), entity.getYaw(), entity.getPitch()));
			HELD.add(entity.getUuid());
			entity.setVelocity(Vec3d.ZERO);
			entity.velocityModified = true;
			if (entity instanceof PlayerEntity) {
				players++;
			}
		}
		ModNetworking.broadcast(domain.world, domain.caughtPayload());
		ServerPlayerEntity caster = domain.caster != null ? server.getPlayerManager().getPlayer(domain.caster) : null;
		ModCriteria.fire(caster, CAST);
		if (players > 0) {
			ModCriteria.fire(caster, CAUGHT_PLAYER);
		}
		if (domain.caught.size() >= CROWD_SIZE) {
			ModCriteria.fire(caster, CROWD);
		}
		Gojolander.LOGGER.debug("Unlimited Void {} caught {}", domain.id, domain.caught.size());
	}

	private static boolean catchable(Domain domain, LivingEntity entity) {
		if (!entity.isAlive() || entity.getUuid().equals(domain.caster) || entity.getType().isIn(ConventionalEntityTypeTags.BOSSES)
				|| HELD.contains(entity.getUuid())) {
			return false;
		}
		return !(entity instanceof PlayerEntity player) || !player.isSpectator() && !player.isCreative();
	}

	/** Holds everything caught exactly where it was: no drift, no fall, no knockback (the movement mixin does the rest). */
	private static void hold(Domain domain) {
		for (Map.Entry<UUID, Hold> entry : domain.caught.entrySet()) {
			Entity entity = domain.world.getEntity(entry.getKey());
			if (entity == null || !entity.isAlive()) {
				continue;
			}
			Hold hold = entry.getValue();
			entity.setVelocity(Vec3d.ZERO);
			entity.fallDistance = 0.0F;
			if (entity.hasVehicle()) {
				continue;
			}
			if (entity instanceof ServerPlayerEntity player) {
				if (player.getPos().squaredDistanceTo(hold.pos()) > DRIFT) {
					player.networkHandler.requestTeleport(hold.pos().x, hold.pos().y, hold.pos().z, player.getYaw(), player.getPitch());
				}
			} else if (entity.getPos().squaredDistanceTo(hold.pos()) > 1.0E-6) {
				entity.requestTeleport(hold.pos().x, hold.pos().y, hold.pos().z);
			}
		}
	}

	/**
	 * Nothing crosses the barrier while it stands. Everything near it (but what it holds, what is dropped and what
	 * players in creative or spectator do) is watched: whatever was on one side last tick and is on the other now is put
	 * back where it was, stopped dead, or if it was fired, stops against it and is gone.
	 */
	private static void barrier(Domain domain) {
		double reach = domain.radius + SHELL;
		Box box = Box.of(domain.center, reach * 2.0, reach * 2.0, reach * 2.0);
		double inner = Math.max(0.0, domain.radius - SHELL);
		for (Entity entity : domain.world.getOtherEntities(null, box)) {
			if (domain.caught.containsKey(entity.getUuid()) || entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity
					|| entity.hasVehicle() || entity instanceof PlayerEntity player && (player.isSpectator() || player.isCreative())
					|| entity.getType().isIn(ConventionalEntityTypeTags.BOSSES)) {
				continue;
			}
			Vec3d at = entity.getBoundingBox().getCenter();
			double d2 = at.squaredDistanceTo(domain.center);
			if (d2 < inner * inner || d2 > reach * reach) {
				domain.sides.remove(entity.getUuid());
				if (d2 < inner * inner) {
					domain.sides.put(entity.getUuid(), new Side(true, entity.getPos()));
				}
				continue;
			}
			boolean inside = d2 < domain.radius * domain.radius;
			Side was = domain.sides.get(entity.getUuid());
			if (was != null && was.inside() != inside) {
				if (entity instanceof ProjectileEntity) {
					domain.world.spawnParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.02);
					entity.discard();
					continue;
				}
				Vec3d back = was.pos();
				entity.setVelocity(Vec3d.ZERO);
				entity.velocityModified = true;
				if (entity instanceof ServerPlayerEntity player) {
					player.networkHandler.requestTeleport(back.x, back.y, back.z, player.getYaw(), player.getPitch());
				} else {
					entity.requestTeleport(back.x, back.y, back.z);
				}
				continue;
			}
			domain.sides.put(entity.getUuid(), new Side(inside, entity.getPos()));
		}
	}

	/** Lets go of everything it caught; {@code dazed}, they come out of it slow and with the light going in and out. */
	private static void release(Domain domain, boolean dazed) {
		if (domain.released) {
			return;
		}
		domain.released = true;
		for (UUID id : domain.caught.keySet()) {
			HELD.remove(id);
			if (domain.world.getEntity(id) instanceof LivingEntity entity && entity.isAlive()) {
				entity.setVelocity(Vec3d.ZERO);
				entity.velocityModified = true;
				entity.fallDistance = 0.0F;
				if (dazed) {
					entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, DomainTimeline.DAZE, 1, false, true));
					if (entity instanceof PlayerEntity) {
						entity.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, DomainTimeline.DAZE, 0, false, true));
					}
				}
			}
		}
		domain.sides.clear();
	}

	/**
	 * Ends a Domain before its time. Not yet closed, it is called off and nothing comes of it; closed, it shatters now,
	 * letting go of whoever it caught, and the clients play the shattering from here.
	 */
	private static void endEarly(Domain domain) {
		if (!ACTIVE.contains(domain)) {
			return;
		}
		if (domain.age < DomainTimeline.CLOSE) {
			ACTIVE.remove(domain);
			ModNetworking.broadcast(domain.world, new DomainEndPayload(domain.id, domain.age, true));
			return;
		}
		release(domain, true);
		if (domain.age < DomainTimeline.collapse(domain.hold)) {
			domain.age = DomainTimeline.collapse(domain.hold);
			ModNetworking.broadcast(domain.world, new DomainEndPayload(domain.id, domain.age, false));
		}
	}

	/** The server going down: everyone let go at once, nothing dazed, nothing left over. */
	private static void endAll(boolean dazed) {
		for (Domain domain : ACTIVE) {
			release(domain, dazed);
		}
		ACTIVE.clear();
		HELD.clear();
	}

	/** Someone joining: the Domains under way where they are, and how long their own is still cooling for. */
	private static void introduce(ServerPlayerEntity player) {
		for (Domain domain : ACTIVE) {
			if (domain.world == player.getServerWorld()) {
				ModNetworking.send(player, domain.lock());
				if (domain.age >= DomainTimeline.CLOSE) {
					ModNetworking.send(player, domain.caughtPayload());
				}
			}
		}
		long left = GojoState.get(player.getServer()).readyAt(player.getUuid()) - player.getServer().getOverworld().getTime();
		if (left > 0L) {
			player.getItemCooldownManager().set(Gojolander.BLINDFOLD, (int) Math.min(Integer.MAX_VALUE, left));
		}
	}

	private static void deny(ServerPlayerEntity player, Text message) {
		player.sendMessage(message, true);
		player.playSoundToPlayer(GojoSounds.DENIED, SoundCategory.PLAYERS, 0.8F, 1.0F);
	}

	/** Ticks as minutes and seconds, rounded up: {@code 4:05}. */
	static String clock(long ticks) {
		long seconds = (ticks + 19L) / 20L;
		return String.format("%d:%02d", seconds / 60L, seconds % 60L);
	}
}
