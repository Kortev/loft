package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.PochitaPlace;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * The rules of the heart. Chainsaw Man's is the only one now, but nothing here is Chainsaw Man's alone: any hybrid's heart will follow
 * the same rules.
 * <ul>
 * <li><b>Giving it up</b> ({@code /chainsaw giveup}, with a confirm, {@link #giveUp}): the hybrid is human again and the heart goes back to
 * the world, as a new den at least {@link PochitaPlace#FAR_BACK} blocks from the old one ({@link PochitaPlace#returnHeart}).</li>
 * <li><b>Absence</b> (game rule {@code heartReturnDays}, 0 = off): a hybrid who has not been in the game for that many in-game days has
 * their heart go back the same way; they find themselves human, with a line, when they next join.</li>
 * <li><b>Stealing</b> (game rule {@code heartTheft}, off by default): whoever dealt the blow that downed a hybrid holds use on them for
 * {@link #THEFT} ticks (5 s) and tears the heart out. The hybrid can struggle (each press of jump takes {@link #STRUGGLE} ticks off the
 * hold), a hit on the thief cancels it, and the heart cannot change hands again for a day ({@link #SETTLE}). The thief becomes the
 * hybrid; the old one is human at once, with their contract slots free (and, down, goes down as a human: {@link Fallen}). Mobs never
 * steal: only a player can hold use.</li>
 * <li><b>{@code chainsawHearts}</b>: how many hearts (dens) the world has.</li>
 * </ul>
 * Who holds a heart, and when they were last in the game, is kept in {@link TeamState}.
 */
public final class Hearts {
	/** An in-game day (ticks). */
	public static final int DAY = 24000;
	/** How long tearing the heart out takes (ticks), and what each struggle takes off it. */
	public static final int THEFT = 100;
	public static final int STRUGGLE = 2;
	/** How long a stolen heart stays with its new holder at the least (ticks): a day. */
	public static final int SETTLE = DAY;
	private static final double REACH = 3.0;
	private static final int GAP = 6;
	/** How long {@code /chainsaw giveup} waits for its confirm (ticks). */
	private static final int CONFIRM = 600;

	/** The thief, how far they have got (ticks), and when they last held use. */
	private record Theft(UUID by, int done, long last) {
	}

	private static final Map<UUID, Theft> THEFTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> STRUGGLED = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> CONFIRMING = new ConcurrentHashMap<>();

	private Hearts() {
	}

	static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			TeamState state = TeamState.of(server);
			if (state.returned.remove(player.getUuid())) {
				state.markDirty();
				took(player, true);
			} else if (Heart.has(player) && absent(server, state, player.getUuid())) {
				// (Away longer than the rule allows, though nobody was in the game to see it.)
				giveUpQuietly(player, true);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.player;
			cancel(player);
			THEFTS.entrySet().removeIf(e -> e.getValue().by().equals(player.getUuid()));
			CONFIRMING.remove(player.getUuid());
			if (Heart.has(player)) {
				TeamState state = TeamState.of(server);
				state.seen.put(player.getUuid(), server.getOverworld().getTime());
				state.markDirty();
			}
		});
		// A hit on someone tearing a heart out stops it.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity thief && source.getAttacker() != null && amount > 0.0F && !THEFTS.isEmpty()) {
				for (Map.Entry<UUID, Theft> e : Map.copyOf(THEFTS).entrySet()) {
					if (e.getValue().by().equals(thief.getUuid())) {
						ServerPlayerEntity victim = thief.server.getPlayerManager().getPlayer(e.getKey());
						if (victim != null) {
							cancel(victim);
							thief.sendMessage(Text.translatable("message.chainsawman.theft.hit").formatted(Formatting.RED), true);
						}
					}
				}
			}
			return true;
		});
		ServerTickEvents.END_SERVER_TICK.register(Hearts::tick);
	}

	/** Whether the heart of {@code id} has been away longer than the rule allows. */
	private static boolean absent(MinecraftServer server, TeamState state, UUID id) {
		int days = server.getOverworld().getGameRules().getInt(TeamRules.HEART_RETURN_DAYS);
		Long seen = state.seen.get(id);
		return days > 0 && seen != null && server.getOverworld().getTime() - seen >= (long) days * DAY;
	}

	/** {@code player} is a hybrid the world knows of: their den taken, their time kept. */
	private static void note(MinecraftServer server, TeamState state, ServerPlayerEntity player) {
		if (state.seen.put(player.getUuid(), server.getOverworld().getTime()) == null) {
			PochitaPlace.taken(server, player.getPos());
		}
		state.markDirty();
	}

	/**
	 * Every so often (and for the tests, whenever asked): who holds a heart and when they were last seen; hearts whose holder has been
	 * away too long go back; the world has as many dens as {@code chainsawHearts}.
	 */
	public static void update(MinecraftServer server) {
		TeamState state = TeamState.of(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (Heart.merged(player)) {
				note(server, state, player);
			} else if (state.seen.remove(player.getUuid()) != null) {
				state.markDirty();
			}
		}
		if (server.getOverworld().getGameRules().getInt(TeamRules.HEART_RETURN_DAYS) > 0) {
			for (UUID id : java.util.Set.copyOf(state.seen.keySet())) {
				if (server.getPlayerManager().getPlayer(id) == null && absent(server, state, id)) {
					state.seen.remove(id);
					state.returned.add(id);
					state.markDirty();
					PochitaPlace.returnHeart(server);
				}
			}
		}
		PochitaPlace.ensure(server, server.getOverworld().getGameRules().getInt(TeamRules.CHAINSAW_HEARTS));
	}

	private static void tick(MinecraftServer server) {
		long now = server.getOverworld().getTime();
		if (now % 40 == 0) {
			update(server);
		}
		if (!CONFIRMING.isEmpty()) {
			CONFIRMING.entrySet().removeIf(e -> now - e.getValue() > CONFIRM);
		}
		for (Map.Entry<UUID, Theft> e : Map.copyOf(THEFTS).entrySet()) {
			ServerPlayerEntity victim = server.getPlayerManager().getPlayer(e.getKey());
			Theft theft = e.getValue();
			ServerPlayerEntity by = server.getPlayerManager().getPlayer(theft.by());
			if (victim == null || by == null || !valid(theft, by, victim, now)) {
				THEFTS.remove(e.getKey());
				if (victim != null) {
					Form.broadcast(server, new FormEventPayload(victim.getUuid(), FormEventPayload.LIFT, 0, THEFT, 1.0, 0.0));
				}
				continue;
			}
			int done = theft.done() + 1;
			THEFTS.put(e.getKey(), new Theft(theft.by(), done, theft.last()));
			if (done % 20 == 0 && done < THEFT) {
				victim.sendMessage(Text.translatable("message.chainsawman.theft.you", Text.keybind("key.jump")).formatted(Formatting.DARK_RED), true);
				victim.getServerWorld().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.7F, 0.5F);
			}
			if (done % 10 == 0 && done < THEFT) {
				by.sendMessage(Text.translatable("message.chainsawman.theft.tearing", done * 100 / THEFT).formatted(Formatting.DARK_RED), true);
			}
			if (done >= THEFT) {
				steal(by, victim);
			}
		}
	}

	private static boolean valid(Theft theft, ServerPlayerEntity by, ServerPlayerEntity victim, long now) {
		return by.isAlive() && by.getServerWorld() == victim.getServerWorld() && now - theft.last() <= GAP && by.isSneaking()
				&& by.squaredDistanceTo(victim) <= REACH * REACH && Downed.is(victim) && Heart.has(victim) && !Heart.has(by) && !Fallen.is(by)
				&& !Downed.is(by);
	}

	/** Whether the heartTheft rule is on where {@code player} is. */
	public static boolean theftOn(ServerPlayerEntity player) {
		return player.getServerWorld().getGameRules().getBoolean(TeamRules.HEART_THEFT);
	}

	/**
	 * {@code by}, crouched, holds use on {@code down}, a downed hybrid: if they are the one who downed them and the rule is on, a tick of
	 * tearing the heart out, and true (the use is theirs). Otherwise false: it is the friend's cord pull.
	 */
	public static boolean theftHold(ServerPlayerEntity by, ServerPlayerEntity down) {
		if (!theftOn(by) || !Heart.has(down) || Heart.has(by) || Fallen.is(by) || !by.getUuid().equals(Downed.downedBy(down))) {
			return false;
		}
		if (by.getServerWorld() != down.getServerWorld() || by.squaredDistanceTo(down) > REACH * REACH) {
			return true;
		}
		MinecraftServer server = by.server;
		long now = server.getOverworld().getTime();
		Long stolen = TeamState.of(server).stolen.get(down.getUuid());
		if (stolen != null && now - stolen < SETTLE) {
			by.sendMessage(Text.translatable("message.chainsawman.theft.settling").formatted(Formatting.GRAY), true);
			return true;
		}
		Theft theft = THEFTS.get(down.getUuid());
		if (theft == null || !theft.by().equals(by.getUuid()) || now - theft.last() > GAP) {
			theft = new Theft(by.getUuid(), 0, now);
			Form.broadcast(server, new FormEventPayload(down.getUuid(), FormEventPayload.LIFT, by.getId(), THEFT, 1.0, 0.0));
		}
		THEFTS.put(down.getUuid(), new Theft(by.getUuid(), theft.done(), now));
		return true;
	}

	/** Whether a heart is being torn out of {@code down} now. */
	public static boolean tearing(ServerPlayerEntity down) {
		return THEFTS.containsKey(down.getUuid());
	}

	/** How far (ticks) the heart of {@code down} has been torn, or -1. */
	public static int torn(ServerPlayerEntity down) {
		Theft theft = THEFTS.get(down.getUuid());
		return theft == null ? -1 : theft.done();
	}

	/** {@code down}, a hybrid being robbed, struggles (a press of jump): the hold slips back {@link #STRUGGLE} ticks. At most one press a tick counts. */
	public static void struggle(ServerPlayerEntity down) {
		Theft theft = THEFTS.get(down.getUuid());
		long now = down.getServerWorld().getTime();
		if (theft == null || STRUGGLED.getOrDefault(down.getUuid(), -1L) == now) {
			return;
		}
		STRUGGLED.put(down.getUuid(), now);
		THEFTS.put(down.getUuid(), new Theft(theft.by(), Math.max(0, theft.done() - STRUGGLE), theft.last()));
	}

	/** The heart torn out is stopped, whoever the thief. */
	private static void cancel(ServerPlayerEntity victim) {
		if (THEFTS.remove(victim.getUuid()) != null) {
			Form.broadcast(victim.server, new FormEventPayload(victim.getUuid(), FormEventPayload.LIFT, 0, THEFT, 1.0, 0.0));
		}
		STRUGGLED.remove(victim.getUuid());
	}

	/** {@code by} has torn {@code victim}'s heart out: it is theirs now. */
	public static void steal(ServerPlayerEntity by, ServerPlayerEntity victim) {
		MinecraftServer server = by.server;
		TeamState state = TeamState.of(server);
		long now = server.getOverworld().getTime();
		THEFTS.remove(victim.getUuid());
		STRUGGLED.remove(victim.getUuid());
		int blood = Blood.get(victim);
		int heartXp = HeartLevel.xp(victim);
		Downed.release(victim);
		strip(victim);
		Heart.give(by);
		// The heart's levels go with it.
		HeartLevel.set(by, heartXp);
		Blood.set(by, Math.max(blood, Blood.WELL));
		state.seen.remove(victim.getUuid());
		state.seen.put(by.getUuid(), now);
		state.stolen.remove(victim.getUuid());
		state.stolen.put(by.getUuid(), now);
		state.markDirty();
		Form.broadcast(server, new FormEventPayload(victim.getUuid(), FormEventPayload.LIFT, -1, THEFT, 1.0, 0.0));
		Saw.around(victim, new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.6F, 0.0F, 0.03F), 1.6F), 0.4, 80, 0.7, 0.2);
		victim.getServerWorld().playSound(null, victim.getX(), victim.getY(), victim.getZ(), ChainsawSounds.CRUNCH, SoundCategory.PLAYERS, 1.2F, 0.7F);
		// The old hybrid is human, and where they lie they go down as one.
		if (TeamRules.goesDown(victim)) {
			Fallen.fall(victim);
		} else {
			victim.setHealth(Fallen.UP_HEALTH);
		}
		victim.sendMessage(Text.translatable("message.chainsawman.theft.lost", by.getDisplayName()).formatted(Formatting.DARK_RED), false);
		by.sendMessage(Text.translatable("message.chainsawman.theft.took").formatted(Formatting.GOLD), false);
	}

	/** {@code player} is a hybrid no more: the form ended, the heart gone from them, Beam and the frenzy with it. Their contract slots are free. */
	static void strip(ServerPlayerEntity player) {
		Beam.end(player);
		Frenzy.end(player);
		Form.end(player);
		Heart.take(player);
	}

	/** {@code /chainsaw giveup}: the question, and the way to say yes. */
	public static void askGiveUp(ServerPlayerEntity player) {
		if (!Heart.has(player)) {
			player.sendMessage(Text.translatable("message.chainsawman.giveup.none").formatted(Formatting.GRAY), false);
			return;
		}
		CONFIRMING.put(player.getUuid(), player.server.getOverworld().getTime());
		player.sendMessage(Text.translatable("message.chainsawman.giveup.ask").formatted(Formatting.GOLD), false);
		player.sendMessage(Text.translatable("message.chainsawman.giveup.confirm").setStyle(Style.EMPTY.withColor(Formatting.RED).withUnderline(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chainsaw giveup confirm"))), false);
	}

	/** {@code /chainsaw giveup confirm}: only after the question. */
	public static boolean confirmGiveUp(ServerPlayerEntity player) {
		Long asked = CONFIRMING.remove(player.getUuid());
		if (asked == null || player.server.getOverworld().getTime() - asked > CONFIRM) {
			player.sendMessage(Text.translatable("message.chainsawman.giveup.first").formatted(Formatting.GRAY), false);
			return false;
		}
		return giveUp(player);
	}

	/** {@code player} gives the heart up: human again, the heart back in the world as a new den far away. */
	public static boolean giveUp(ServerPlayerEntity player) {
		if (!Heart.has(player)) {
			return false;
		}
		giveUpQuietly(player, false);
		return true;
	}

	private static void giveUpQuietly(ServerPlayerEntity player, boolean away) {
		MinecraftServer server = player.server;
		TeamState state = TeamState.of(server);
		// (A heart the world has not counted yet: its den is taken first, so the new one replaces it.)
		note(server, state, player);
		cancel(player);
		if (Downed.is(player)) {
			Downed.release(player);
			player.setHealth(Fallen.UP_HEALTH);
		}
		strip(player);
		state.seen.remove(player.getUuid());
		state.stolen.remove(player.getUuid());
		state.markDirty();
		PochitaPlace.returnHeart(server);
		took(player, away);
	}

	/** {@code player} is told their heart is gone (given up, or while away). */
	private static void took(ServerPlayerEntity player, boolean away) {
		if (Heart.has(player)) {
			strip(player);
		}
		player.sendMessage(Text.translatable(away ? "message.chainsawman.giveup.away" : "message.chainsawman.giveup.done").formatted(Formatting.GOLD), false);
	}

	static void clear() {
		THEFTS.clear();
		STRUGGLED.clear();
		CONFIRMING.clear();
	}

	/** When the heart {@code holder} holds was stolen (world time), or null: for the tests. */
	@Nullable
	public static Long stolenAt(MinecraftServer server, UUID holder) {
		return TeamState.of(server).stolen.get(holder);
	}
}
