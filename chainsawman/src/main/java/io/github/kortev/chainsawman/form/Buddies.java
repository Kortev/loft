package io.github.kortev.chainsawman.form;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

/**
 * Buddies (Public Safety's hunters work in pairs): crouch and right-click another player with an empty hand to ask them to be your
 * buddy; they do the same back to accept. One buddy at a time; {@code /chainsaw buddy} shows it, {@code /chainsaw buddy end} ends it.
 * Saved with both players. <b>Near</b> your buddy ({@link #NEAR} blocks) you take {@link #REDUCTION} less damage
 * ({@code BuddyDamageMixin}), and you pick each other up in half the time ({@link Fallen#liftTime}). When your buddy is down you are
 * shown an arrow to them. Later the Public Safety office will pair buddies: {@link #pair} is the hook (it does what accepting does).
 */
public final class Buddies {
	/** How close "near" is (blocks), and the share of damage a near buddy takes off. */
	public static final double NEAR = 16.0;
	public static final float REDUCTION = 0.10F;
	/** How long a request stands (ticks). */
	private static final int ASKED = 20 * 60;

	/** Your buddy's id (a UUID as text), saved with you. */
	public static final AttachmentType<String> BUDDY = AttachmentRegistry.<String>builder().persistent(Codec.STRING).copyOnDeath()
			.buildAndRegister(ChainsawMan.id("buddy"));
	/** Their name, to show when they are away. */
	public static final AttachmentType<String> NAME = AttachmentRegistry.<String>builder().persistent(Codec.STRING).copyOnDeath()
			.buildAndRegister(ChainsawMan.id("buddy_name"));

	/** Who asked whom, and when (world time). */
	private record Ask(UUID target, long at) {
	}

	private static final Map<UUID, Ask> ASKS = new ConcurrentHashMap<>();

	private Buddies() {
	}

	/** {@code player}'s buddy's id, or null. */
	@Nullable
	public static UUID of(PlayerEntity player) {
		String id = player.getAttached(BUDDY);
		if (id == null || id.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** {@code player}'s buddy, if they are in the game. */
	@Nullable
	public static ServerPlayerEntity buddyOf(ServerPlayerEntity player) {
		UUID id = of(player);
		return id == null || player.server == null ? null : player.server.getPlayerManager().getPlayer(id);
	}

	/** Whether {@code a} and {@code b} are each other's buddies and within {@link #NEAR} blocks of each other (in one world). */
	public static boolean near(ServerPlayerEntity a, ServerPlayerEntity b) {
		if (a == b || !b.getUuid().equals(of(a)) || !a.getUuid().equals(of(b))) {
			return false;
		}
		return a.getServerWorld() == b.getServerWorld() && a.squaredDistanceTo(b) <= NEAR * NEAR;
	}

	/** {@code amount} of damage to {@code player}, less if their buddy is near. */
	public static float damage(ServerPlayerEntity player, float amount) {
		ServerPlayerEntity buddy = buddyOf(player);
		return buddy != null && buddy.isAlive() && near(player, buddy) ? amount * (1.0F - REDUCTION) : amount;
	}

	/** Who is told when {@code player} goes down: their buddy and the players of their scoreboard team, if any (those in the game). */
	public static List<ServerPlayerEntity> team(ServerPlayerEntity player) {
		List<ServerPlayerEntity> mates = new ArrayList<>();
		ServerPlayerEntity buddy = buddyOf(player);
		if (buddy != null && buddy != player) {
			mates.add(buddy);
		}
		if (player.getScoreboardTeam() != null && player.server != null) {
			for (ServerPlayerEntity other : player.server.getPlayerManager().getPlayerList()) {
				if (other != player && !mates.contains(other) && other.getScoreboardTeam() == player.getScoreboardTeam()) {
					mates.add(other);
				}
			}
		}
		return mates;
	}

	static void init() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (io.github.kortev.chainsawman.contract.Forever.locked(player) || io.github.kortev.chainsawman.contract.Forever.notYet(player)) {
				return ActionResult.FAIL;
			}
			if (!world.isClient && entity instanceof ServerPlayerEntity other && player instanceof ServerPlayerEntity me && player.isSneaking()
					&& hand == Hand.MAIN_HAND && me.getMainHandStack().isEmpty() && !Fallen.is(me) && !Fallen.is(other) && !Downed.is(me) && !Downed.is(other)) {
				ask(me, other);
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			TeamState state = TeamState.of(server);
			if (state.ended.remove(player.getUuid())) {
				// Their buddy ended it while they were away.
				player.removeAttached(BUDDY);
				player.removeAttached(NAME);
				state.markDirty();
				player.sendMessage(Text.translatable("message.chainsawman.buddy.ended_away").formatted(Formatting.GRAY), false);
			}
			told(player);
			// Their buddy is told they are back (the name may have changed).
			ServerPlayerEntity buddy = buddyOf(player);
			if (buddy != null) {
				told(buddy);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ASKS.remove(handler.player.getUuid()));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Buddies::tick);
	}

	/** {@code player} is told who their buddy is (or that they have none). */
	static void told(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, FormEventPayload.ID)) {
			return;
		}
		UUID id = of(player);
		ServerPlayNetworking.send(player, new FormEventPayload(id == null ? player.getUuid() : id, FormEventPayload.BUDDY, id == null ? 0 : 1));
	}

	/** {@code by}, crouched with an empty hand, right-clicks {@code other}: a request, or the answer to theirs. */
	public static void ask(ServerPlayerEntity by, ServerPlayerEntity other) {
		long now = by.getServerWorld().getTime();
		if (of(by) != null) {
			by.sendMessage(Text.translatable("message.chainsawman.buddy.have").formatted(Formatting.GRAY), true);
			return;
		}
		if (of(other) != null) {
			by.sendMessage(Text.translatable("message.chainsawman.buddy.theirs", other.getDisplayName()).formatted(Formatting.GRAY), true);
			return;
		}
		Ask theirs = ASKS.get(other.getUuid());
		if (theirs != null && theirs.target().equals(by.getUuid()) && now - theirs.at() <= ASKED) {
			ASKS.remove(other.getUuid());
			pair(by, other);
			return;
		}
		ASKS.put(by.getUuid(), new Ask(other.getUuid(), now));
		by.sendMessage(Text.translatable("message.chainsawman.buddy.asked", other.getDisplayName()).formatted(Formatting.GOLD), false);
		other.sendMessage(Text.translatable("message.chainsawman.buddy.asks", by.getDisplayName()).formatted(Formatting.GOLD), false);
	}

	/**
	 * Makes {@code a} and {@code b} buddies (each told, both saved): what accepting does, and what the Public Safety office will call to
	 * pair hunters. Anyone either had before is let go.
	 */
	public static void pair(ServerPlayerEntity a, ServerPlayerEntity b) {
		end(a);
		end(b);
		a.setAttached(BUDDY, b.getUuid().toString());
		a.setAttached(NAME, b.getGameProfile().getName());
		b.setAttached(BUDDY, a.getUuid().toString());
		b.setAttached(NAME, a.getGameProfile().getName());
		ASKS.remove(a.getUuid());
		ASKS.remove(b.getUuid());
		a.sendMessage(Text.translatable("message.chainsawman.buddy.now", b.getDisplayName()).formatted(Formatting.GREEN), false);
		b.sendMessage(Text.translatable("message.chainsawman.buddy.now", a.getDisplayName()).formatted(Formatting.GREEN), false);
		told(a);
		told(b);
	}

	/** {@code player}'s buddy ended: on both sides (a buddy who is away finds out when they are back). */
	public static void end(ServerPlayerEntity player) {
		UUID id = of(player);
		if (id == null) {
			return;
		}
		MinecraftServer server = player.server;
		player.removeAttached(BUDDY);
		player.removeAttached(NAME);
		ServerPlayerEntity buddy = server.getPlayerManager().getPlayer(id);
		if (buddy != null) {
			if (player.getUuid().equals(of(buddy))) {
				buddy.removeAttached(BUDDY);
				buddy.removeAttached(NAME);
				buddy.sendMessage(Text.translatable("message.chainsawman.buddy.ended_by", player.getDisplayName()).formatted(Formatting.GRAY), false);
			}
			told(buddy);
		} else {
			TeamState state = TeamState.of(server);
			state.ended.add(id);
			state.markDirty();
		}
		told(player);
		player.sendMessage(Text.translatable("message.chainsawman.buddy.ended").formatted(Formatting.GRAY), false);
	}

	/** Every second: a buddy who is down is shown to their buddy (the arrow), and old requests lapse. */
	private static void tick(MinecraftServer server) {
		long now = server.getOverworld().getTime();
		if (!ASKS.isEmpty()) {
			ASKS.entrySet().removeIf(e -> now - e.getValue().at() > ASKED);
		}
		if (now % 10 != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerPlayerEntity buddy = buddyOf(player);
			if (buddy == null || !ServerPlayNetworking.canSend(player, FormEventPayload.ID) || !player.getUuid().equals(of(buddy))) {
				continue;
			}
			boolean fallen = Fallen.is(buddy);
			if (fallen || Downed.is(buddy)) {
				int left = fallen ? buddy.getAttachedOrElse(Fallen.LEFT, 0) : -1;
				ServerPlayNetworking.send(player, new FormEventPayload(buddy.getUuid(), FormEventPayload.BUDDY_DOWN, left, buddy.getX(), buddy.getY(), buddy.getZ()));
			}
		}
	}

	static void clear() {
		ASKS.clear();
	}
}
