package io.github.kortev.chainsawman.form;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

/**
 * Everyone goes down instead of dying (game rule {@code everyoneGoesDown}, on by default). A player who is not a hybrid and takes a
 * hit that would kill them falls instead: on the ground, crawling slowly, unable to attack or use anything, with {@link #TIMER} ticks
 * (30 s) before they die as usual. Each hit they take while down takes {@link #HIT_COST} off it. A teammate crouching beside them and
 * holding use for {@link #LIFT} ticks ({@link #LIFT_BUDDY} if it is their buddy, near) gets them up with {@link #UP_HEALTH} health, no
 * cost to the helper; letting go early cancels. They can give up and die at once ({@link #giveUp}). The void and {@code /kill} still kill
 * outright. A hybrid keeps its own rules ({@link Downed}).
 */
public final class Fallen {
	/** How long they have (ticks), what each hit costs them, and how close together two hits count as two. */
	public static final int TIMER = 30 * 20;
	public static final int HIT_COST = 40;
	public static final int HIT_GAP = 10;
	/** How long a pick-up takes (ticks): alone, and by a buddy who is near. */
	public static final int LIFT = 60;
	public static final int LIFT_BUDDY = 30;
	/** What they get up with: four hearts. */
	public static final float UP_HEALTH = 8.0F;
	private static final double REACH = 3.0;
	/** How long between a helper's use and the next before it stops counting. */
	private static final int GAP = 6;

	/** The ticks they have left, saved with the player while they are down (so leaving does not get you out of it). */
	public static final AttachmentType<Integer> LEFT = AttachmentRegistry.<Integer>builder().persistent(Codec.INT)
			.buildAndRegister(ChainsawMan.id("fallen"));
	private static final Identifier CRAWL = ChainsawMan.id("fallen_crawl");

	private static final Set<UUID> FALLEN = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Long> LAST_HIT = new ConcurrentHashMap<>();

	/** Someone lifting them: who, how far they have got (ticks), and when they last held use on them. */
	private record Lift(UUID by, int done, long last) {
	}

	private static final Map<UUID, Lift> LIFTING = new ConcurrentHashMap<>();

	/** Who is down as far as each client has been told. */
	public static Predicate<PlayerEntity> client = p -> false;

	private Fallen() {
	}

	/** Whether {@code player} is down in this way (a human, not a hybrid): on either side. */
	public static boolean is(PlayerEntity player) {
		return player.getWorld().isClient ? client.test(player) : FALLEN.contains(player.getUuid());
	}

	/** Whether someone is lifting {@code down} now. */
	public static boolean lifted(ServerPlayerEntity down) {
		return LIFTING.containsKey(down.getUuid());
	}

	/** Who is down now (for the tests). */
	public static Set<UUID> all() {
		return Set.copyOf(FALLEN);
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity player && !lethal(source) && TeamRules.goesDown(player) && !is(player) && !Heart.has(player)
					&& !Downed.is(player) && !Merge.is(player)) {
				fall(player);
				return false;
			}
			return true;
		});
		// A hit on someone who is down does not hurt them: it shortens their time.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity player && is(player) && !lethal(source)) {
				if (amount > 0.0F) {
					hit(player);
				}
				return false;
			}
			return true;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> is(player) ? ActionResult.FAIL : ActionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> is(player) ? ActionResult.FAIL : ActionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> is(player) ? ActionResult.FAIL : ActionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) -> is(player) ? TypedActionResult.fail(ItemStack.EMPTY) : TypedActionResult.pass(ItemStack.EMPTY));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (is(player)) {
				return ActionResult.FAIL;
			}
			if (!world.isClient && entity instanceof ServerPlayerEntity down && is(down) && player.isSneaking() && hand == Hand.MAIN_HAND) {
				lifting((ServerPlayerEntity) player, down);
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		});
		// Dying (the void, the kill command, bleeding out) ends it.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity player) {
				reset(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> reset(player));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			Integer left = player.getAttached(LEFT);
			if (left != null && left > 0) {
				FALLEN.add(player.getUuid());
				crawl(player, true);
				Form.broadcast(server, new FormEventPayload(player.getUuid(), FormEventPayload.FALLEN, left, player.getX(), player.getY(), player.getZ()));
			}
			for (UUID id : FALLEN) {
				ServerPlayerEntity other = server.getPlayerManager().getPlayer(id);
				if (other != null && !id.equals(player.getUuid()) && ServerPlayNetworking.canSend(player, FormEventPayload.ID)) {
					ServerPlayNetworking.send(player, new FormEventPayload(id, FormEventPayload.FALLEN, other.getAttachedOrElse(LEFT, 0), other.getX(), other.getY(),
							other.getZ()));
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUuid();
			FALLEN.remove(id);
			LIFTING.remove(id);
			LAST_HIT.remove(id);
		});
	}

	/**
	 * What kills you all the same: the void, the kill command and the like (whatever bypasses invulnerability, as another mod's cosmic
	 * kills are tagged).
	 */
	private static boolean lethal(DamageSource source) {
		return source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL) || source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	private static void reset(ServerPlayerEntity player) {
		boolean was = FALLEN.remove(player.getUuid());
		LIFTING.remove(player.getUuid());
		LAST_HIT.remove(player.getUuid());
		player.removeAttached(LEFT);
		crawl(player, false);
		if (was && player.server != null) {
			Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.FALLEN, 0));
		}
	}

	/** {@code player}, who would have died, is down. */
	public static void fall(ServerPlayerEntity player) {
		player.setHealth(1.0F);
		if (!FALLEN.add(player.getUuid())) {
			return;
		}
		player.setAttached(LEFT, TIMER);
		player.stopRiding();
		player.stopUsingItem();
		player.setSprinting(false);
		player.extinguish();
		crawl(player, true);
		announce(player, TIMER);
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0F, 0.6F);
		player.sendMessage(Text.translatable("message.chainsawman.fallen.you", Text.keybind("key.chainsawman.give_up")).formatted(Formatting.RED), false);
		for (ServerPlayerEntity mate : Buddies.team(player)) {
			mate.sendMessage(Text.translatable("message.chainsawman.fallen.mate", player.getDisplayName(), TIMER / 20).formatted(Formatting.RED), true);
		}
	}

	private static void announce(ServerPlayerEntity player, int left) {
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.FALLEN, left, player.getX(), player.getY(), player.getZ()));
	}

	/** They are hit while down: {@link #HIT_COST} off their time (not twice within {@link #HIT_GAP} ticks). */
	private static void hit(ServerPlayerEntity player) {
		long now = player.getServerWorld().getTime();
		Long last = LAST_HIT.get(player.getUuid());
		if (last != null && now - last < HIT_GAP) {
			return;
		}
		LAST_HIT.put(player.getUuid(), now);
		int left = player.getAttachedOrElse(LEFT, TIMER) - HIT_COST;
		// (A hit that takes the last of it leaves one tick: the next tick of the world kills them, not this call in the middle of a hit.)
		left = Math.max(1, left);
		player.setAttached(LEFT, left);
		announce(player, left);
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.8F, 0.8F);
	}

	/** Their time ran out: they die as usual (drops, respawn). */
	private static void bleedOut(ServerPlayerEntity player) {
		reset(player);
		player.damage(player.getDamageSources().genericKill(), Float.MAX_VALUE);
	}

	/** They give up: dead at once. */
	public static boolean giveUp(ServerPlayerEntity player) {
		if (!is(player)) {
			return false;
		}
		bleedOut(player);
		return true;
	}

	/** Back up with {@link #UP_HEALTH} health, picked up by {@code by} (or by a command, or the heart changing hands: null). */
	public static void up(ServerPlayerEntity down, ServerPlayerEntity by) {
		if (!FALLEN.remove(down.getUuid())) {
			return;
		}
		LIFTING.remove(down.getUuid());
		LAST_HIT.remove(down.getUuid());
		down.removeAttached(LEFT);
		crawl(down, false);
		down.setHealth(UP_HEALTH);
		Form.broadcast(down.server, new FormEventPayload(down.getUuid(), FormEventPayload.FALLEN, 0));
		Form.broadcast(down.server, new FormEventPayload(down.getUuid(), FormEventPayload.LIFT, -1, LIFT, 0.0, 0.0));
		down.getServerWorld().playSound(null, down.getX(), down.getY(), down.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 1.0F, 0.8F);
		if (by != null) {
			down.sendMessage(Text.translatable("message.chainsawman.fallen.up", by.getDisplayName()).formatted(Formatting.GREEN), true);
			by.sendMessage(Text.translatable("message.chainsawman.fallen.helped", down.getDisplayName()).formatted(Formatting.GREEN), true);
		}
	}

	/** Slows {@code player} to a crawl and keeps them off their feet (or lets them go). */
	private static void crawl(ServerPlayerEntity player, boolean on) {
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		EntityAttributeInstance jump = player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
		if (speed != null) {
			speed.removeModifier(CRAWL);
			if (on) {
				speed.addTemporaryModifier(new EntityAttributeModifier(CRAWL, -0.8, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		if (jump != null) {
			jump.removeModifier(CRAWL);
			if (on) {
				jump.addTemporaryModifier(new EntityAttributeModifier(CRAWL, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}

	/** How long a pick-up of {@code down} by {@code by} takes (ticks): half as long by their buddy, near. */
	public static int liftTime(ServerPlayerEntity by, ServerPlayerEntity down) {
		return Buddies.near(by, down) ? LIFT_BUDDY : LIFT;
	}

	private static boolean valid(Lift lift, ServerPlayerEntity down, long now) {
		ServerPlayerEntity by = down.server.getPlayerManager().getPlayer(lift.by());
		return by != null && by.isAlive() && by.getServerWorld() == down.getServerWorld() && now - lift.last() <= GAP && by.isSneaking()
				&& by.squaredDistanceTo(down) <= REACH * REACH && !is(by);
	}

	/** {@code by} holding use on {@code down}, crouched: another tick or so of picking them up. */
	public static void lifting(ServerPlayerEntity by, ServerPlayerEntity down) {
		if (by.getServerWorld() != down.getServerWorld() || by.squaredDistanceTo(down) > REACH * REACH || !is(down)) {
			return;
		}
		long now = by.getServerWorld().getTime();
		Lift lift = LIFTING.get(down.getUuid());
		if (lift != null && !lift.by().equals(by.getUuid()) && valid(lift, down, now)) {
			// Someone else is at it: one at a time.
			return;
		}
		if (lift == null || !lift.by().equals(by.getUuid()) || now - lift.last() > GAP) {
			if (lift != null) {
				let(down);
			}
			lift = new Lift(by.getUuid(), 0, now);
			Form.broadcast(down.server, new FormEventPayload(down.getUuid(), FormEventPayload.LIFT, by.getId(), liftTime(by, down), 0.0, 0.0));
		}
		LIFTING.put(down.getUuid(), new Lift(by.getUuid(), lift.done(), now));
	}

	/** The helper let go before the end: it starts over. */
	private static void let(ServerPlayerEntity down) {
		Form.broadcast(down.server, new FormEventPayload(down.getUuid(), FormEventPayload.LIFT, 0));
	}

	/** How far from a mob a standing player pulls it off someone who is down (blocks). */
	public static final double PREFER = 16.0;

	/** Mobs after someone who is down turn on a standing player within {@link #PREFER} blocks of them; with nobody standing near, they keep at the one on the ground. */
	private static void preferStanding(ServerPlayerEntity down) {
		for (net.minecraft.entity.mob.MobEntity mob : down.getServerWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class,
				down.getBoundingBox().expand(32.0), m -> m.getTarget() == down)) {
			ServerPlayerEntity best = null;
			for (ServerPlayerEntity other : down.server.getPlayerManager().getPlayerList()) {
				if (other != down && other.isAlive() && other.getServerWorld() == mob.getWorld() && !is(other) && !Downed.is(other) && !other.isSpectator()
						&& !other.isCreative() && other.squaredDistanceTo(mob) <= PREFER * PREFER && (best == null || other.squaredDistanceTo(mob) < best.squaredDistanceTo(mob))) {
					best = other;
				}
			}
			if (best != null) {
				mob.setTarget(best);
			}
		}
	}

	static void tick(MinecraftServer server) {
		long now = server.getOverworld().getTime();
		for (UUID id : Set.copyOf(FALLEN)) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player == null || !player.isAlive()) {
				FALLEN.remove(id);
				LIFTING.remove(id);
				continue;
			}
			if (Heart.has(player) || Downed.is(player)) {
				// (A heart given them by a command while down.)
				up(player, null);
				continue;
			}
			player.setHealth(1.0F);
			player.setSprinting(false);
			if (now % 5 == 0) {
				preferStanding(player);
			}
			int left = player.getAttachedOrElse(LEFT, TIMER) - 1;
			if (left <= 0) {
				bleedOut(player);
				continue;
			}
			player.setAttached(LEFT, left);
			Lift lift = LIFTING.get(id);
			ServerPlayerEntity by = lift != null ? server.getPlayerManager().getPlayer(lift.by()) : null;
			if (lift != null && !valid(lift, player, now)) {
				LIFTING.remove(id);
				lift = null;
				let(player);
			}
			if (lift != null) {
				int done = lift.done() + 1;
				LIFTING.put(id, new Lift(lift.by(), done, lift.last()));
				int need = liftTime(by, player);
				if (done % 10 == 0 && done < need) {
					by.sendMessage(Text.translatable("message.chainsawman.fallen.lifting", done * 100 / need).formatted(Formatting.GREEN), true);
				}
				if (done >= need) {
					up(player, by);
					continue;
				}
			}
			if (now % 20 == 0) {
				announce(player, left);
				for (ServerPlayerEntity mate : Buddies.team(player)) {
					mate.sendMessage(Text.translatable("message.chainsawman.fallen.mate", player.getDisplayName(), (left + 19) / 20).formatted(Formatting.RED), true);
				}
			}
		}
	}

	static void clear() {
		FALLEN.clear();
		LIFTING.clear();
		LAST_HIT.clear();
	}
}
