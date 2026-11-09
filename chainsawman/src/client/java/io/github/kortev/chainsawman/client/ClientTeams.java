package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.form.Fallen;
import io.github.kortev.chainsawman.network.FormEventPayload;
import io.github.kortev.chainsawman.network.TeamActionPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * What the client knows of teams and hearts, as the server tells it: who is down (a human, {@link Fallen}) and how long they have, who
 * is lifting them or tearing a hybrid's heart out, who your buddy is and where they lie when they are down, and the legend's marker.
 * The pose, the screen and the arrows are drawn from this ({@code FallenPoseMixin}, {@link TeamsHud}).
 */
public final class ClientTeams {
	/** Lifting (taking hold), and how it ended. */
	public static final int HOLDING = 0;
	public static final int LET_GO = 1;
	public static final int DONE = 2;
	/** What is being done: a pick-up, or a heart torn out. */
	public static final int PICKUP = 0;
	public static final int HEART = 1;
	/** How long an ended lift is kept (ticks). */
	private static final int KEEP = 12;

	/** Someone at a person on the ground: the helper (entity id), when they took hold (world time), how long it takes, what, and how it stands. */
	public record Lift(int helper, long from, int need, int kind, long ended, int how) {
	}

	/** Who is down: when their time runs out (world time), and where they lie. */
	private static final Map<UUID, Long> ENDS = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3d> LIES = new ConcurrentHashMap<>();
	private static final Map<UUID, Lift> LIFTS = new ConcurrentHashMap<>();

	/** Your buddy, if you have one; and where they are down, till when. */
	private static volatile UUID buddy;
	private static volatile Vec3d buddyAt;
	private static volatile long buddyUntil = -1;
	private static volatile int buddyLeft = -1;
	/** The legend: where he pulled the cord, and till when. */
	private static volatile Vec3d legendAt;
	private static volatile long legendUntil = -1;

	/** How long the give-up key has been held (ticks), and whether jump was down last tick (for the struggle). */
	private static int giving;
	private static boolean jumped;
	/** How long the give-up key must be held (ticks). */
	public static final int GIVE_UP_TICKS = 40;

	private static final java.util.Random RANDOM = new java.util.Random();
	private static final net.minecraft.particle.DustParticleEffect BLOOD = new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.6F, 0.0F, 0.03F), 1.3F);

	private ClientTeams() {
	}

	static void init() {
		Fallen.client = p -> ENDS.containsKey(p.getUuid());
	}

	/** Something the server said about teams. */
	static void event(ClientWorld world, FormEventPayload e, long now) {
		UUID id = e.player();
		switch (e.kind()) {
			case FormEventPayload.FALLEN -> {
				if (e.value() > 0) {
					ENDS.put(id, now + e.value());
					LIES.put(id, new Vec3d(e.x(), e.y(), e.z()));
				} else {
					ENDS.remove(id);
					LIES.remove(id);
				}
			}
			case FormEventPayload.LIFT -> {
				if (e.value() > 0) {
					LIFTS.put(id, new Lift(e.value(), now, (int) e.x(), (int) e.y(), 0, HOLDING));
				} else {
					Lift old = LIFTS.get(id);
					if (old != null) {
						LIFTS.put(id, new Lift(old.helper(), old.from(), old.need(), old.kind(), now, e.value() < 0 ? DONE : LET_GO));
					}
				}
			}
			case FormEventPayload.BUDDY -> {
				buddy = e.value() != 0 ? id : null;
				if (e.value() == 0) {
					buddyAt = null;
					buddyUntil = -1;
				}
			}
			case FormEventPayload.BUDDY_DOWN -> {
				buddyAt = new Vec3d(e.x(), e.y(), e.z());
				buddyLeft = e.value();
				buddyUntil = now + 30;
			}
			case FormEventPayload.LEGEND -> {
				legendAt = new Vec3d(e.x(), e.y(), e.z());
				legendUntil = now + e.value();
			}
			default -> {
			}
		}
	}

	/** Ticks left to {@code player}, who is down, or -1 if they are not (for this frame). */
	public static float left(PlayerEntity player, float tickDelta) {
		Long end = ENDS.get(player.getUuid());
		return end == null ? -1.0F : Math.max(0.0F, end - (player.getWorld().getTime() + tickDelta));
	}

	/** Whoever is at {@code down} now (or just was): the lift, or null. */
	@Nullable
	public static Lift on(PlayerEntity down) {
		Lift lift = LIFTS.get(down.getUuid());
		if (lift == null) {
			return null;
		}
		return lift.how() == HOLDING || down.getWorld().getTime() - lift.ended() < KEEP ? lift : null;
	}

	/** How far through ({@code 0} to {@code 1}) a lift is, for this frame. */
	public static float progress(PlayerEntity down, Lift lift, float tickDelta) {
		if (lift.how() == DONE) {
			return 1.0F;
		}
		if (lift.how() == LET_GO) {
			return 0.0F;
		}
		return Math.min(1.0F, (down.getWorld().getTime() - lift.from() + tickDelta) / Math.max(1, lift.need()));
	}

	/** What {@code helper} is lifting right now (the person on the ground), or null. */
	@Nullable
	public static PlayerEntity lifting(PlayerEntity helper) {
		ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null) {
			return null;
		}
		for (Map.Entry<UUID, Lift> e : LIFTS.entrySet()) {
			Lift lift = e.getValue();
			if (lift.helper() == helper.getId() && lift.how() == HOLDING) {
				return world.getPlayerByUuid(e.getKey());
			}
		}
		return null;
	}

	/** The lift {@code helper} is at (see {@link #lifting}). */
	@Nullable
	public static Lift liftBy(PlayerEntity helper) {
		for (Lift lift : LIFTS.values()) {
			if (lift.helper() == helper.getId() && lift.how() == HOLDING) {
				return lift;
			}
		}
		return null;
	}

	/** Where your buddy lies if they are down (and the server has said so lately), or null. */
	@Nullable
	public static Vec3d buddyDown(long now) {
		return buddyAt != null && now <= buddyUntil ? buddyAt : null;
	}

	/** The name of your buddy as the world has it, or null. */
	@Nullable
	public static PlayerEntity buddy() {
		ClientWorld world = MinecraftClient.getInstance().world;
		return world == null || buddy == null ? null : world.getPlayerByUuid(buddy);
	}

	public static boolean hasBuddy() {
		return buddy != null;
	}

	public static int buddyLeft() {
		return buddyLeft;
	}

	/** Where the legend pulled the cord while it still shows, or null. */
	@Nullable
	public static Vec3d legend(long now) {
		return legendAt != null && now <= legendUntil ? legendAt : null;
	}

	/** Ticks the legend's marker has left. */
	public static long legendLeft(long now) {
		return legendUntil - now;
	}

	/** How far the give-up key is held (0 to 1). */
	public static float giving() {
		return Math.min(1.0F, giving / (float) GIVE_UP_TICKS);
	}

	/** Every tick: the give-up key (held), and the struggle (jump pressed) against a hand at your heart. */
	static void tick(MinecraftClient client) {
		ClientPlayerEntity self = client.player;
		if (self == null || client.world == null) {
			giving = 0;
			return;
		}
		long now = client.world.getTime();
		// Old lifts forgotten.
		LIFTS.entrySet().removeIf(e -> e.getValue().how() != HOLDING && now - e.getValue().ended() > KEEP * 2L);
		if (Fallen.is(self) && ChainsawManClient.GIVE_UP != null && ChainsawManClient.GIVE_UP.isPressed()) {
			giving++;
			if (giving >= GIVE_UP_TICKS) {
				giving = 0;
				if (ClientPlayNetworking.canSend(TeamActionPayload.ID)) {
					ClientPlayNetworking.send(new TeamActionPayload(TeamActionPayload.GIVE_UP));
				}
			}
		} else {
			giving = 0;
		}
		Lift mine = on(self);
		boolean tearing = mine != null && mine.kind() == HEART && mine.how() == HOLDING;
		boolean down = io.github.kortev.chainsawman.form.Downed.is(self);
		boolean pressed = tearing && down && InputUtil.isKeyPressed(client.getWindow().getHandle(),
				InputUtil.fromTranslationKey(client.options.jumpKey.getBoundKeyTranslationKey()).getCode());
		if (pressed && !jumped && ClientPlayNetworking.canSend(TeamActionPayload.ID)) {
			ClientPlayNetworking.send(new TeamActionPayload(TeamActionPayload.STRUGGLE));
		}
		jumped = pressed;
		// A heart being torn out: blood from the chest, more as it comes; a burst when it is out.
		for (Map.Entry<UUID, Lift> e : LIFTS.entrySet()) {
			Lift lift = e.getValue();
			PlayerEntity down2 = client.world.getPlayerByUuid(e.getKey());
			if (lift.kind() != HEART || down2 == null || lift.how() == LET_GO) {
				continue;
			}
			float share = progress(down2, lift, 0.0F);
			int count = lift.how() == DONE ? (now - lift.ended() == 0 ? 40 : 0) : 1 + (int) (share * 5.0F);
			for (int i = 0; i < count; i++) {
				client.world.addParticle(BLOOD, down2.getX() + (RANDOM.nextDouble() - 0.5) * 0.3, down2.getY() + 0.3 + RANDOM.nextDouble() * 0.2,
						down2.getZ() + (RANDOM.nextDouble() - 0.5) * 0.3, (RANDOM.nextDouble() - 0.5) * 0.25, 0.05 + RANDOM.nextDouble() * 0.2,
						(RANDOM.nextDouble() - 0.5) * 0.25);
			}
		}
		if (buddyUntil >= 0 && now > buddyUntil) {
			buddyAt = null;
			buddyUntil = -1;
		}
	}

	public static void clear() {
		ENDS.clear();
		LIES.clear();
		LIFTS.clear();
		buddy = null;
		buddyAt = null;
		buddyUntil = -1;
		legendAt = null;
		legendUntil = -1;
		giving = 0;
	}
}
