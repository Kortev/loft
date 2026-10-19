package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * The chain from the arm: fired the way you look, up to {@link #REACH} blocks. Caught in something living, it yanks it
 * to you; caught in a block, it pulls you to it, and you land without hurting yourself. Missing, it rattles back.
 */
public final class Grapple {
	public static final double REACH = 24.0;
	/** Ticks between shots. */
	public static final int EVERY = 60;
	/** The hook's bite on what it catches. */
	public static final float DAMAGE = 2.0F;
	/** How far off your aim the chain still catches something (blocks): it is a chain, not a needle. */
	private static final double CATCH = 0.45;
	/** How long after a pull you are kept from fall damage (ticks). */
	private static final int SAFE = 50;

	private static final Map<UUID, Long> FIRED = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> SAFE_UNTIL = new ConcurrentHashMap<>();

	private Grapple() {
	}

	/** The chain fired by {@code player}; what it caught, or null if it missed or could not be fired yet. */
	public static HitResult fire(ServerPlayerEntity player) {
		if (!Form.fighting(player)) {
			return null;
		}
		long now = player.getServerWorld().getTime();
		Long last = FIRED.get(player.getUuid());
		if (last != null && now - last < EVERY) {
			return null;
		}
		FIRED.put(player.getUuid(), now);
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(REACH));
		BlockHitResult block = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		Vec3d reach = block.getType() == HitResult.Type.MISS ? end : block.getPos();
		EntityHitResult entity = caught(player, eye, reach);
		Form.play(player, ChainsawSounds.CHAIN, 1.0F);
		if (entity != null) {
			yank(player, (LivingEntity) entity.getEntity());
			Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.CHAIN, entity.getEntity().getId(),
					entity.getPos().x, entity.getPos().y, entity.getPos().z));
			return entity;
		}
		if (block.getType() != HitResult.Type.MISS) {
			pull(player, block.getPos());
			Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.CHAIN, -1, reach.x, reach.y, reach.z));
			return block;
		}
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.CHAIN, -2, end.x, end.y, end.z));
		return null;
	}

	/** The nearest living thing along the chain's way from {@code eye} to {@code reach}, within {@link #CATCH} of it. */
	private static EntityHitResult caught(ServerPlayerEntity player, Vec3d eye, Vec3d reach) {
		EntityHitResult best = null;
		double nearest = Double.MAX_VALUE;
		for (Entity e : player.getWorld().getOtherEntities(player, new Box(eye, reach).expand(CATCH + 1.0),
				e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && !(e instanceof ServerPlayerEntity p && Downed.is(p)))) {
			Optional<Vec3d> at = e.getBoundingBox().expand(CATCH).raycast(eye, reach);
			if (at.isPresent() && eye.squaredDistanceTo(at.get()) < nearest) {
				nearest = eye.squaredDistanceTo(at.get());
				best = new EntityHitResult(e, at.get());
			}
		}
		return best;
	}

	/** What the chain caught, flung to {@code player}. */
	private static void yank(ServerPlayerEntity player, LivingEntity target) {
		Vec3d to = player.getPos().subtract(target.getPos());
		double distance = to.length();
		Vec3d flung = to.normalize().multiply(Math.min(2.2, 0.45 + distance * 0.11)).add(0.0, 0.35 + distance * 0.015, 0.0);
		target.damage(player.getDamageSources().playerAttack(player), DAMAGE);
		target.setVelocity(flung);
		target.velocityModified = true;
		target.fallDistance = 0.0F;
		if (target instanceof ServerPlayerEntity other) {
			SAFE_UNTIL.put(other.getUuid(), player.getServerWorld().getTime() + SAFE);
		}
	}

	/** {@code player} pulled along the chain to {@code at}. */
	private static void pull(ServerPlayerEntity player, Vec3d at) {
		Vec3d to = at.subtract(player.getPos());
		double distance = to.length();
		Vec3d pulled = to.normalize().multiply(Math.min(2.4, 0.6 + distance * 0.1)).add(0.0, 0.42, 0.0);
		player.setVelocity(pulled);
		player.velocityModified = true;
		player.fallDistance = 0.0F;
		SAFE_UNTIL.put(player.getUuid(), player.getServerWorld().getTime() + SAFE);
	}

	static void tick(MinecraftServer server) {
		long now = server.getOverworld().getTime();
		SAFE_UNTIL.entrySet().removeIf(e -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
			if (player == null || now > e.getValue()) {
				return true;
			}
			player.fallDistance = 0.0F;
			return false;
		});
	}

	/** Lets {@code player} fire again at once (for the tests). */
	public static void ready(Entity player) {
		FIRED.remove(player.getUuid());
	}

	static void forget(UUID id) {
		FIRED.remove(id);
		SAFE_UNTIL.remove(id);
	}

	static void clear() {
		FIRED.clear();
		SAFE_UNTIL.clear();
	}
}
