package io.github.kortev.chainsawman.form;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.PochitaItem;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.joml.Vector3f;

/**
 * With Pochita carried you never die. What would have killed you leaves you down instead: on the ground, your arms and
 * legs gone, unable to move, fight or use anything, and nothing more can hurt you. Someone has to give you blood: crouch
 * beside you and hold use on you for {@link #FEED} ticks, and {@link #GIVEN} of their own health comes into you and you
 * are back up. If nobody comes, after {@link #ALONE} ticks you come back by yourself. (Out of the world, or killed by a
 * command, you still die.) Being down is saved with you, so leaving does not get you out of it.
 */
public final class Downed {
	/** How long you lie there if nobody gives you blood (ticks): ten minutes. */
	public static final int ALONE = 10 * 60 * 20;
	/** How long someone has to hold use on you to give you blood (ticks), and how much they give (health points). */
	public static final int FEED = 60;
	public static final float GIVEN = 6.0F;
	/** How far from you they may be (blocks), and how long between their use and the next before it stops counting. */
	private static final double FEED_REACH = 3.0;
	private static final int FEED_GAP = 6;

	/** How many ticks you have left to lie there, saved with you while you are down. */
	public static final AttachmentType<Integer> LEFT = AttachmentRegistry.<Integer>builder().persistent(Codec.INT)
			.buildAndRegister(ChainsawMan.id("downed"));
	private static final Identifier STILL = ChainsawMan.id("downed_still");

	private static final Set<UUID> DOWN = ConcurrentHashMap.newKeySet();

	/** Someone giving you blood: who, how far they have got (ticks), and when they last held use on you. */
	private record Feed(UUID by, int done, long last) {
	}

	private static final Map<UUID, Feed> FED = new ConcurrentHashMap<>();
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.5F, 0.0F, 0.03F), 1.4F);

	/** Who is down as far as each client has been told; the server's own set on the server. */
	public static Predicate<PlayerEntity> client = p -> false;

	private Downed() {
	}

	/** Whether {@code player} is down: on either side. */
	public static boolean is(PlayerEntity player) {
		return player.getWorld().isClient ? client.test(player) : DOWN.contains(player.getUuid());
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity player && !lethal(source) && (PochitaItem.carried(player) || is(player))) {
				down(player);
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayerEntity player && is(player) && !lethal(source)));
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> is(player) ? ActionResult.FAIL : ActionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> is(player) ? ActionResult.FAIL : ActionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> is(player) ? ActionResult.FAIL : ActionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) -> is(player) ? TypedActionResult.fail(ItemStack.EMPTY) : TypedActionResult.pass(ItemStack.EMPTY));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (is(player)) {
				return ActionResult.FAIL;
			}
			if (!world.isClient && entity instanceof ServerPlayerEntity down && is(down) && player.isSneaking() && hand == Hand.MAIN_HAND) {
				feeding((ServerPlayerEntity) player, down);
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			Integer left = player.getAttached(LEFT);
			if (left != null && left > 0) {
				DOWN.add(player.getUuid());
				hold(player, true);
				Form.broadcast(server, new FormEventPayload(player.getUuid(), FormEventPayload.DOWNED, 1));
			}
			for (UUID id : DOWN) {
				if (!id.equals(player.getUuid()) && ServerPlayNetworking.canSend(player, FormEventPayload.ID)) {
					ServerPlayNetworking.send(player, new FormEventPayload(id, FormEventPayload.DOWNED, 1));
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			DOWN.remove(handler.player.getUuid());
			FED.remove(handler.player.getUuid());
		});
	}

	/** What kills you all the same: the void, and the kill command. */
	private static boolean lethal(DamageSource source) {
		return source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL);
	}

	/** Down: what would have killed {@code player} has not. */
	public static void down(ServerPlayerEntity player) {
		player.setHealth(1.0F);
		if (!DOWN.add(player.getUuid())) {
			return;
		}
		player.setAttached(LEFT, ALONE);
		Form.end(player);
		Frenzy.end(player);
		Saw.forget(player.getUuid());
		player.extinguish();
		player.clearStatusEffects();
		hold(player, true);
		Form.play(player, ChainsawSounds.DOWN, 1.0F);
		Saw.around(player, BLOOD, 0.3, 60, 0.5, 0.1);
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.DOWNED, 1));
	}

	/** Back up, with {@link #GIVEN} health: given blood, or after lying there long enough. */
	public static void up(ServerPlayerEntity player) {
		if (!DOWN.remove(player.getUuid())) {
			return;
		}
		FED.remove(player.getUuid());
		player.removeAttached(LEFT);
		hold(player, false);
		player.setHealth(GIVEN);
		Form.play(player, ChainsawSounds.REVIVE, 1.0F);
		player.sendMessage(Text.translatable("message.chainsawman.revived").formatted(Formatting.RED), true);
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.DOWNED, 0));
	}

	/** {@code by} holding use on {@code down}: another tick or so of giving them blood. */
	public static void feeding(ServerPlayerEntity by, ServerPlayerEntity down) {
		if (by.squaredDistanceTo(down) > FEED_REACH * FEED_REACH) {
			return;
		}
		long now = by.getServerWorld().getTime();
		Feed feed = FED.get(down.getUuid());
		if (feed == null || !feed.by().equals(by.getUuid()) || now - feed.last() > FEED_GAP) {
			feed = new Feed(by.getUuid(), 0, now);
		}
		FED.put(down.getUuid(), new Feed(by.getUuid(), feed.done(), now));
	}

	/** Stops (or lets go) {@code player}'s legs: they cannot walk or jump while down. */
	private static void hold(ServerPlayerEntity player, boolean on) {
		for (EntityAttributeInstance attribute : new EntityAttributeInstance[] {player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED),
				player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH)}) {
			if (attribute == null) {
				continue;
			}
			attribute.removeModifier(STILL);
			if (on) {
				attribute.addTemporaryModifier(new EntityAttributeModifier(STILL, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}

	static void tick(MinecraftServer server) {
		long now = server.getOverworld().getTime();
		for (UUID id : Set.copyOf(DOWN)) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player == null) {
				DOWN.remove(id);
				continue;
			}
			player.setHealth(Math.max(player.getHealth(), 1.0F));
			player.setSprinting(false);
			int left = player.getAttachedOrElse(LEFT, ALONE) - 1;
			if (left <= 0) {
				up(player);
				continue;
			}
			player.setAttached(LEFT, left);
			Feed feed = FED.get(id);
			ServerPlayerEntity by = feed != null ? server.getPlayerManager().getPlayer(feed.by()) : null;
			if (feed != null && (by == null || now - feed.last() > FEED_GAP || !by.isSneaking() || by.squaredDistanceTo(player) > FEED_REACH * FEED_REACH)) {
				FED.remove(id);
				feed = null;
			}
			if (feed != null) {
				int done = feed.done() + 1;
				FED.put(id, new Feed(feed.by(), done, feed.last()));
				if (done % 10 == 0) {
					by.sendMessage(Text.translatable("message.chainsawman.feeding", done * 100 / FEED).formatted(Formatting.DARK_RED), true);
				}
				if (done >= FEED) {
					by.damage(by.getDamageSources().magic(), GIVEN);
					up(player);
					continue;
				}
			}
			if (now % 20 == 0) {
				player.sendMessage(Text.translatable("message.chainsawman.downed", left / 1200, String.format("%02d", left / 20 % 60))
						.formatted(Formatting.DARK_RED), true);
				Saw.around(player, BLOOD, 0.15, 6, 0.4, 0.0);
			}
		}
	}

	/** Who is down now (for the tests). */
	public static Set<UUID> all() {
		return Set.copyOf(DOWN);
	}

	static void clear() {
		DOWN.clear();
		FED.clear();
	}
}
