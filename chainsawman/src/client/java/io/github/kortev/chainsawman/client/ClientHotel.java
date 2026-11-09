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
		fogNow += MathHelper.clamp(to - fogNow, -0.1F, 0.1F);
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

	public static void devil(HotelPayloads.Devil payload) {
		long now = now();
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
		EVENTS.clear();
	}
}
