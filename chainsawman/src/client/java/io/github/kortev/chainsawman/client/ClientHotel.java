package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.network.HotelPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * What this client has been told about the Eternity Devil's hotel: the loop's fog (blocks, 0 off), eased in and out; the devil's words
 * (shown by {@link HotelHud}); the contract on offer ({@link ContractScreen}); and what the devil's body is doing ({@link #events}), for
 * {@link EternityDevilRenderer}.
 */
public final class ClientHotel {
	private static float fogTarget;
	private static float fogNow;
	private static float fogPrev;

	/** One thing the devil says, from the world tick it began: translation key, how long it stays, and its style (0 plain, 1 begging, 2 the offer's last words). */
	public record Line(String key, int style, long start, int ticks) {
	}

	/** One thing the devil's body is doing: what ({@link HotelPayloads.Devil} kinds), where (world), from which world tick and for how long. */
	public record Event(byte kind, double x, double y, double z, long start, int ticks) {
	}

	/** The floor turning: the world tick the roll began and how long it takes (-1 when not); the shaft arriving (a flash); swallowed. */
	private static long tiltAt = -1;
	private static int tiltTicks = 44;
	private static long flashAt = -1000;
	private static boolean swallowed;

	private static Line line;
	private static final Map<Integer, List<Event>> EVENTS = new HashMap<>();

	private ClientHotel() {
	}

	public static void fog(float end) {
		fogTarget = end;
	}

	/** One tick: the fog's closing in or opening out. */
	public static void tick() {
		fogPrev = fogNow;
		float to = fogTarget > 0.0F ? 1.0F : 0.0F;
		// (It closes in about half a second and lifts over about a second, as when the fight starts.)
		fogNow += MathHelper.clamp(to - fogNow, -0.05F, 0.1F);
	}

	/** How closed in the corridor is, 0 to 1. */
	public static float fogAmount(float delta) {
		return MathHelper.lerp(delta, fogPrev, fogNow);
	}

	/** The distance the fog ends at when fully closed. */
	public static float fogEnd() {
		return fogTarget > 0.0F ? fogTarget : 11.0F;
	}

	private static long now() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world != null ? client.world.getTime() : 0L;
	}

	public static void speech(HotelPayloads.Speech payload) {
		line = new Line(payload.key(), payload.style(), now(), payload.ticks());
	}

	/** The words on screen now, or null. */
	public static Line line() {
		if (line != null && now() - line.start() > line.ticks()) {
			line = null;
		}
		return line;
	}

	/** The contract screen opens ({@code ticks} to answer) or closes (0). */
	public static void offer(int ticks, boolean hybrid) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ticks > 0) {
			client.setScreen(new ContractScreen(ticks, hybrid));
		} else if (client.currentScreen instanceof ContractScreen || client.currentScreen instanceof DropScreen) {
			client.setScreen(null);
		}
	}

	/** How far the view is rolled now (radians): a quarter turn over the tilt's two seconds, none otherwise. */
	public static float roll(float delta) {
		if (tiltAt < 0) {
			return 0.0F;
		}
		float t = (now() + delta - tiltAt) / tiltTicks;
		if (t > 1.0F + 60.0F / tiltTicks) {
			// (The shaft never came: the world is as it was.)
			return 0.0F;
		}
		t = MathHelper.clamp(t, 0.0F, 1.0F);
		return (float) (Math.PI / 2.0) * t * t * (3.0F - 2.0F * t);
	}

	/** How hard the screen shakes, 0 to 1: building through the turn, and a last shudder as the shaft arrives. */
	public static float shake(float delta) {
		float now = now() + delta;
		if (tiltAt >= 0) {
			float t = MathHelper.clamp((now - tiltAt) / tiltTicks, 0.0F, 1.0F);
			return 0.25F + 0.75F * t;
		}
		float since = now - flashAt;
		return since >= 0.0F && since < 24.0F ? 1.0F - since / 24.0F : 0.0F;
	}

	/** The red-black flash as the shaft arrives, 1 down to 0 over about a second and a half. */
	public static float flash(float delta) {
		float since = now() + delta - flashAt;
		return since >= 0.0F && since < 30.0F ? 1.0F - since / 30.0F : 0.0F;
	}

	/** Whether the mouth has swallowed this player. */
	public static boolean swallowed() {
		return swallowed;
	}

	public static void devil(HotelPayloads.Devil payload) {
		long now = now();
		if (payload.kind() == HotelPayloads.Devil.TILT) {
			tiltAt = now;
			tiltTicks = Math.max(1, payload.ticks());
			return;
		}
		if (payload.kind() == HotelPayloads.Devil.SHAFT) {
			tiltAt = -1;
			if (payload.ticks() > 0) {
				flashAt = now;
			}
			return;
		}
		if (payload.kind() == HotelPayloads.Devil.SWALLOW) {
			swallowed = payload.ticks() > 0;
			return;
		}
		List<Event> list = EVENTS.computeIfAbsent(payload.devil(), k -> new ArrayList<>());
		list.removeIf(e -> now - e.start() > e.ticks() + 40);
		list.add(new Event(payload.kind(), payload.x(), payload.y(), payload.z(), now, payload.ticks()));
	}

	/** What the devil with this entity id is doing now (those that have not finished). */
	public static List<Event> events(int devil) {
		List<Event> list = EVENTS.get(devil);
		if (list == null) {
			return List.of();
		}
		long now = now();
		list.removeIf(e -> now - e.start() > e.ticks() + 40);
		return list;
	}

	public static void clear() {
		fogTarget = 0.0F;
		fogNow = 0.0F;
		fogPrev = 0.0F;
		line = null;
		tiltAt = -1;
		flashAt = -1000;
		swallowed = false;
		EVENTS.clear();
	}
}
