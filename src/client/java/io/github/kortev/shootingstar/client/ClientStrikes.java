package io.github.kortev.shootingstar.client;

import io.github.kortev.shootingstar.client.feed.Feed;
import io.github.kortev.shootingstar.client.render.ImpactEffects;
import io.github.kortev.shootingstar.network.StrikeCancelPayload;
import io.github.kortev.shootingstar.network.StrikeImpactPayload;
import io.github.kortev.shootingstar.network.StrikeLockPayload;
import io.github.kortev.shootingstar.registry.ModSounds;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/** Client-side strikes, their sound cues, and who owns the screen while they play out. */
public final class ClientStrikes {
	private static final Map<Integer, ClientStrike> STRIKES = new LinkedHashMap<>();
	private static boolean hudOverride;
	private static boolean savedHudHidden;

	private ClientStrikes() {
	}

	public static Collection<ClientStrike> all() {
		return STRIKES.values();
	}

	/** The local player's strike while its feed or camera shots are running. */
	@Nullable
	public static ClientStrike cinematic() {
		for (ClientStrike strike : STRIKES.values()) {
			if (strike.cinematic() && strike.age < StrikeTimeline.WIDE_END) {
				return strike;
			}
		}
		return null;
	}

	public static boolean feedActive(@Nullable ClientStrike strike, double t) {
		return strike != null && strike.cinematic() && ClientConfig.feed && Feed.showing(t);
	}

	public static boolean shotActive(@Nullable ClientStrike strike, double t) {
		return strike != null && strike.cinematic() && ClientConfig.cameraShots
				&& (t >= StrikeTimeline.RISE && t < StrikeTimeline.ORBIT || t >= StrikeTimeline.INBOUND && t < StrikeTimeline.WIDE_END);
	}

	/** True while the local player has a strike in flight (for the uplink's status card). */
	@Nullable
	public static ClientStrike mine() {
		for (ClientStrike strike : STRIKES.values()) {
			if (strike.mine) {
				return strike;
			}
		}
		return null;
	}

	// --- packets ---------------------------------------------------------------------------

	public static void onLock(StrikeLockPayload payload, MinecraftClient client) {
		boolean mine = client.player != null && client.player.getUuid().equals(payload.shooter());
		ClientStrike strike = new ClientStrike(payload.strikeId(), payload.target(), payload.shooter(), mine, payload.age());
		strike.feedSkipped = !ClientConfig.feed || payload.age() > StrikeTimeline.RISE;
		STRIKES.put(strike.id, strike);
		if (payload.age() == 0) {
			if (mine) {
				master(ModSounds.UPLINK_LOCK, 1.0F, 1.0F);
			}
			at(client, strike.center, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 4.0F, 0.6F, 0);
		}
	}

	public static void onImpact(StrikeImpactPayload payload, MinecraftClient client) {
		ClientStrike strike = STRIKES.get(payload.strikeId());
		if (strike == null) {
			strike = new ClientStrike(payload.strikeId(), payload.impact(), Util.NIL_UUID, false, StrikeTimeline.IMPACT);
			strike.feedSkipped = true;
			STRIKES.put(strike.id, strike);
		}
		strike.impacted = true;
		strike.age = Math.max(strike.age, StrikeTimeline.IMPACT);
		strike.impactAge = strike.age;
		strike.radius = payload.radius();
		strike.zoneDiameter = payload.zoneDiameter();
		strike.spireHeight = payload.spireHeight();
		ImpactEffects.trigger(client, strike);
	}

	public static void onCancel(StrikeCancelPayload payload) {
		STRIKES.remove(payload.strikeId());
	}

	public static void skipFeed() {
		for (ClientStrike strike : STRIKES.values()) {
			if (strike.mine) {
				strike.feedSkipped = true;
			}
		}
	}

	public static void clear(MinecraftClient client) {
		STRIKES.clear();
		ImpactEffects.clear();
		restoreHud(client);
	}

	// --- ticking ---------------------------------------------------------------------------

	public static void tick(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			if (!STRIKES.isEmpty() || hudOverride) {
				clear(client);
			}
			return;
		}
		if (client.isPaused()) {
			return;
		}
		for (Iterator<ClientStrike> it = STRIKES.values().iterator(); it.hasNext(); ) {
			ClientStrike strike = it.next();
			int before = strike.age;
			if (!strike.impacted && strike.age >= StrikeTimeline.IMPACT - 1) {
				// Hold just short of impact until the server confirms it.
				if (++strike.holdTicks > 100) {
					it.remove();
				}
				continue;
			}
			strike.age++;
			cues(client, strike, before, strike.age);
			if (strike.age > StrikeTimeline.END) {
				it.remove();
			}
		}
		ImpactEffects.tick(client);

		ClientStrike cinematic = cinematic();
		boolean takeOver = cinematic != null && (feedActive(cinematic, cinematic.age) || shotActive(cinematic, cinematic.age));
		if (takeOver && !hudOverride) {
			savedHudHidden = client.options.hudHidden;
			client.options.hudHidden = true;
			hudOverride = true;
		} else if (!takeOver && hudOverride) {
			restoreHud(client);
		}
	}

	private static void restoreHud(MinecraftClient client) {
		if (hudOverride) {
			client.options.hudHidden = savedHudHidden;
			hudOverride = false;
		}
	}

	private static boolean crossed(int from, int to, int mark) {
		return from < mark && to >= mark;
	}

	private static void cues(MinecraftClient client, ClientStrike strike, int from, int to) {
		boolean feed = strike.cinematic() && ClientConfig.feed;
		if (feed) {
			if (crossed(from, to, StrikeTimeline.ORBIT)) {
				master(ModSounds.FEED_ZOOM, 1.0F, 0.9F);
			}
			if (crossed(from, to, StrikeTimeline.RELAY)) {
				master(ModSounds.FEED_RELAY, 1.0F, 1.0F);
			}
			if (crossed(from, to, StrikeTimeline.WAKE)) {
				master(ModSounds.FEED_WAKE, 1.0F, 1.0F);
			}
			if (crossed(from, to, StrikeTimeline.LOADING + 10)) {
				master(ModSounds.FEED_LOAD, 1.0F, 1.0F);
			}
			if (to >= StrikeTimeline.LAPS && to < StrikeTimeline.DEBRIS) {
				int lap = StrikeTimeline.lapNumber(StrikeTimeline.lapProgress(to));
				if (lap != strike.lastLap) {
					strike.lastLap = lap;
					master(ModSounds.FEED_LAP, 0.7F + lap * 0.14F, 0.9F);
				}
			}
			if (crossed(from, to, StrikeTimeline.DEBRIS)) {
				master(ModSounds.FEED_RELEASE, 1.0F, 1.0F);
			}
			if (crossed(from, to, StrikeTimeline.TERMINAL)) {
				master(ModSounds.FEED_REENTRY, 1.0F, 1.0F);
			}
		}
		if (crossed(from, to, StrikeTimeline.INBOUND)) {
			if (strike.cinematic()) {
				master(ModSounds.STRIKE_INBOUND, 1.0F, 1.0F);
			} else {
				at(client, strike.center.add(0, 40, 0), ModSounds.STRIKE_INBOUND, SoundCategory.WEATHER, 1.5F, 1.0F, 0);
			}
		}
	}

	// --- sound helpers ---------------------------------------------------------------------

	public static void master(SoundEvent sound, float pitch, float volume) {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
	}

	public static void at(MinecraftClient client, Vec3d pos, SoundEvent sound, SoundCategory category, float volume,
			float pitch, int delay) {
		PositionedSoundInstance instance = new PositionedSoundInstance(sound, category, volume, pitch, Random.create(),
				pos.x, pos.y, pos.z);
		if (delay > 0) {
			client.getSoundManager().play(instance, delay);
		} else {
			client.getSoundManager().play(instance);
		}
	}
}
