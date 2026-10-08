package io.github.kortev.shootingstar.client.thunder;

import io.github.kortev.shootingstar.client.ClientConfig;
import io.github.kortev.shootingstar.network.ThunderArcsPayload;
import io.github.kortev.shootingstar.network.ThunderCancelPayload;
import io.github.kortev.shootingstar.network.ThunderLockPayload;
import io.github.kortev.shootingstar.network.ThunderStrokePayload;
import io.github.kortev.shootingstar.registry.ModSounds;
import io.github.kortev.shootingstar.thunder.ThunderTimeline;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side Mjölnir strikes: what each one is doing, its sound cues on the timeline, the arcs, and who owns the screen
 * while the shooter's feed and camera shots play.
 */
public final class ClientThunders {
	/** Speed of sound in blocks per tick, give or take. */
	private static final double SOUND_SPEED = 17.0;
	private static final Map<Integer, ClientThunder> THUNDERS = new LinkedHashMap<>();
	private static final Random RANDOM = Random.create();
	private static boolean hudOverride;
	private static boolean savedHudHidden;
	@Nullable
	private static ClientWorld lastWorld;

	private ClientThunders() {
	}

	public static Collection<ClientThunder> all() {
		return THUNDERS.values();
	}

	/** The local player's strike while its feed or camera shots are running. */
	@Nullable
	public static ClientThunder cinematic() {
		for (ClientThunder thunder : THUNDERS.values()) {
			if (thunder.cinematic() && thunder.age < ThunderTimeline.CAMERA_END) {
				return thunder;
			}
		}
		return null;
	}

	/** The local player's strike, if they have one under way. */
	@Nullable
	public static ClientThunder mine() {
		for (ClientThunder thunder : THUNDERS.values()) {
			if (thunder.mine) {
				return thunder;
			}
		}
		return null;
	}

	public static boolean feedShowing(double t) {
		return t >= ThunderTimeline.FEED && t < ThunderTimeline.INBOUND;
	}

	public static boolean feedActive(@Nullable ClientThunder thunder, double t) {
		return thunder != null && thunder.cinematic() && ClientConfig.feed && feedShowing(t);
	}

	/** True while the shooter's feed covers their whole screen this frame, so the world behind it need not be drawn. */
	public static boolean feedCovers(float tickDelta) {
		ClientThunder thunder = cinematic();
		return thunder != null && feedActive(thunder, thunder.time(tickDelta));
	}

	public static boolean shotActive(@Nullable ClientThunder thunder, double t) {
		return thunder != null && thunder.cinematic() && ClientConfig.cameraShots
				&& (t >= ThunderTimeline.RISE && t < ThunderTimeline.FEED || t >= ThunderTimeline.INBOUND && t < ThunderTimeline.CAMERA_END);
	}

	/** True while a camera shot flies where the world's culling has never looked from (a tick of margin either side). */
	public static boolean flying() {
		ClientThunder thunder = cinematic();
		return thunder != null && (shotActive(thunder, thunder.age - 2) || shotActive(thunder, thunder.age + 2));
	}

	/** Whether {@code player} is holding Mjölnir up to call the storm, for how others see them. */
	public static boolean raising(PlayerEntity player) {
		for (ClientThunder thunder : THUNDERS.values()) {
			if (thunder.shooter.equals(player.getUuid()) && thunder.age < ThunderTimeline.RISE + 10) {
				return true;
			}
		}
		return false;
	}

	// --- packets ---------------------------------------------------------------------------

	public static void onLock(ThunderLockPayload payload, MinecraftClient client) {
		if (client.world == null) {
			return;
		}
		boolean mine = client.player != null && client.player.getUuid().equals(payload.shooter());
		ClientThunder thunder = new ClientThunder(payload.strikeId(), payload.target(), payload.shooter(), mine, payload.age(),
				payload.radius(), payload.seed(), client.world.getTopY());
		thunder.feedSkipped = !ClientConfig.feed || payload.age() > ThunderTimeline.RISE;
		THUNDERS.put(thunder.id, thunder);
		ThunderRender.prepare(client, thunder);
		if (payload.age() == 0 && mine) {
			master(ModSounds.MJOLNIR_RAISE, 1.0F, 1.0F, 0);
		}
	}

	public static void onStroke(ThunderStrokePayload payload, MinecraftClient client) {
		if (client.world == null) {
			return;
		}
		ClientThunder thunder = THUNDERS.get(payload.strikeId());
		if (thunder == null) {
			thunder = new ClientThunder(payload.strikeId(), payload.target(), Util.NIL_UUID, false, ThunderTimeline.STROKE - 1,
					payload.radius(), payload.seed(), client.world.getTopY());
			thunder.feedSkipped = true;
			THUNDERS.put(thunder.id, thunder);
			ThunderRender.prepare(client, thunder);
		}
		thunder.struck = true;
		// This tick's update moves the strike on to the stroke itself, so the first frame shows the bolt.
		thunder.age = Math.max(thunder.age, ThunderTimeline.STROKE - 1);
		thunder.radius = payload.radius();
		thunder.terrain = payload.terrain();
		thunder.boltHeight = payload.boltHeight();
		ThunderRender.struck(client, thunder);
		ThunderDust.onStroke(client.world, thunder);
		strokeSounds(client, thunder);
	}

	public static void onArcs(ThunderArcsPayload payload, MinecraftClient client) {
		ClientThunder thunder = THUNDERS.get(payload.strikeId());
		List<Float> d = payload.arcs();
		if (thunder == null || client.player == null) {
			return;
		}
		Vec3d ear = client.gameRenderer.getCamera().getPos();
		for (int i = 0; i + 6 < d.size(); i += 7) {
			Vec3d from = new Vec3d(d.get(i), d.get(i + 1), d.get(i + 2));
			Vec3d to = new Vec3d(d.get(i + 3), d.get(i + 4), d.get(i + 5));
			int delay = Math.round(d.get(i + 6));
			thunder.arcs.add(new ClientThunder.Arc(from, to, delay, thunder.seed * 31 + i));
			int lag = (int) (ear.distanceTo(to) / SOUND_SPEED);
			at(client, to, ModSounds.THUNDER_ARC, SoundCategory.WEATHER, 4.0F, 0.85F + RANDOM.nextFloat() * 0.3F, delay + lag);
		}
	}

	public static void onCancel(ThunderCancelPayload payload) {
		ClientThunder thunder = THUNDERS.remove(payload.strikeId());
		if (thunder != null) {
			MinecraftClient client = MinecraftClient.getInstance();
			thunder.feedSounds.forEach(client.getSoundManager()::stop);
		}
	}

	public static void skipFeed() {
		MinecraftClient client = MinecraftClient.getInstance();
		for (ClientThunder thunder : THUNDERS.values()) {
			if (thunder.mine) {
				thunder.feedSkipped = true;
				thunder.feedSounds.forEach(client.getSoundManager()::stop);
				thunder.feedSounds.clear();
			}
		}
	}

	public static void clear(MinecraftClient client) {
		THUNDERS.clear();
		ThunderDust.clear();
		restoreHud(client);
	}

	// --- ticking ---------------------------------------------------------------------------

	public static void tick(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			if (!THUNDERS.isEmpty() || hudOverride) {
				clear(client);
			}
			return;
		}
		if (client.world != lastWorld) {
			lastWorld = client.world;
			clear(client);
		}
		if (client.isPaused()) {
			return;
		}
		for (Iterator<ClientThunder> it = THUNDERS.values().iterator(); it.hasNext(); ) {
			ClientThunder thunder = it.next();
			int before = thunder.age;
			if (!thunder.struck && thunder.age >= ThunderTimeline.STROKE - 1) {
				// Hold just short of the stroke until the server confirms it.
				if (++thunder.holdTicks > 100) {
					it.remove();
				}
				continue;
			}
			thunder.age++;
			cues(client, thunder, before, thunder.age);
			particles(client, thunder);
			if (thunder.age > ThunderTimeline.END) {
				it.remove();
			}
		}
		ThunderDust.tick(client.world);

		ClientThunder cinematic = cinematic();
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

	private static void cues(MinecraftClient client, ClientThunder thunder, int from, int to) {
		// The sounds carry their own levels (tools/gen_sounds.py mixes them), so they all play at full volume.
		boolean feed = thunder.cinematic() && ClientConfig.feed;
		Vec3d ear = client.gameRenderer.getCamera().getPos();
		if (crossed(from, to, ThunderTimeline.CALL)) {
			AbstractClientPlayerEntity caller = findShooter(client, thunder);
			if (caller != null) {
				thunder.callFrom = HammerRaise.hammerHead(caller, 1.0F);
			}
			if (!thunder.mine && thunder.callFrom != null) {
				at(client, thunder.callFrom, ModSounds.MJOLNIR_CALL, SoundCategory.WEATHER, 10.0F, 1.0F,
						(int) (ear.distanceTo(thunder.callFrom) / SOUND_SPEED));
			}
		}
		if (thunder.cinematic() && ClientConfig.cameraShots && crossed(from, to, ThunderTimeline.RISE)) {
			master(ModSounds.THUNDER_RISE, 1.0F, 1.0F, 0);
		}
		if (crossed(from, to, ThunderTimeline.FEED)) {
			if (feed) {
				held(thunder, ModSounds.THUNDER_FEED);
			} else {
				// Out in the world, the storm winds up overhead.
				at(client, thunder.top(), ModSounds.THUNDER_STORM, SoundCategory.WEATHER, 16.0F, 1.0F, 0);
			}
		}
		if (feed) {
			if (crossed(from, to, ThunderTimeline.DRAW)) {
				held(thunder, ModSounds.THUNDER_DRAW);
			}
			if (crossed(from, to, ThunderTimeline.FORGE)) {
				held(thunder, ModSounds.THUNDER_FORGE);
			}
			if (crossed(from, to, ThunderTimeline.LEADER)) {
				held(thunder, ModSounds.THUNDER_LEADER);
			}
		}
		if (crossed(from, to, ThunderTimeline.INBOUND)) {
			if (thunder.cinematic()) {
				master(ModSounds.THUNDER_CHARGE_NEAR, 1.0F, 1.0F, 0);
			} else {
				at(client, thunder.center.add(0, 3, 0), ModSounds.THUNDER_CHARGE, SoundCategory.WEATHER, 8.0F, 1.0F, 0);
			}
		}
	}

	/** The crack, the thunder rolling off the hills, and the hiss and crackle after; late, the farther off you are. */
	private static void strokeSounds(MinecraftClient client, ClientThunder thunder) {
		if (client.player == null) {
			return;
		}
		if (thunder.cinematic()) {
			double from = thunder.witness != null ? thunder.witness.distanceTo(thunder.center) : thunder.radius * 1.25;
			int lag = (int) (from / SOUND_SPEED);
			master(ModSounds.THUNDER_STROKE_NEAR, 1.0F, 1.0F, lag);
			master(ModSounds.THUNDER_ROLL_NEAR, 1.0F, 1.0F, lag + 14);
			master(ModSounds.THUNDER_AFTERMATH_NEAR, 1.0F, 1.0F, 70);
			return;
		}
		Vec3d ear = client.gameRenderer.getCamera().getPos();
		double distance = ear.distanceTo(thunder.center);
		int lag = (int) (distance / SOUND_SPEED);
		at(client, thunder.center.add(0, 8, 0), ModSounds.THUNDER_STROKE, SoundCategory.WEATHER, 40.0F, 1.0F, lag);
		double reach = thunder.radius * 14.0 + 300.0;
		if (distance < reach) {
			float volume = (float) MathHelper.clamp(1.0 - distance / reach, 0.2, 1.0);
			around(client, ModSounds.THUNDER_ROLL, volume, lag + 10);
		}
		at(client, thunder.center, ModSounds.THUNDER_AFTERMATH, SoundCategory.WEATHER, 6.0F, 1.0F, lag + 50);
	}

	/** Sparks crawl over everything under the storm as the leader comes down; the crater spits sparks and smoke after. */
	private static void particles(MinecraftClient client, ClientThunder thunder) {
		if (client.world == null || client.player == null
				|| client.player.getPos().squaredDistanceTo(thunder.center) > 320.0 * 320.0) {
			return;
		}
		double t = thunder.age;
		if (t >= ThunderTimeline.INBOUND && t < ThunderTimeline.STROKE) {
			double p = (t - ThunderTimeline.INBOUND) / (ThunderTimeline.STROKE - ThunderTimeline.INBOUND);
			int sparks = (int) (2 + 10 * p);
			for (int i = 0; i < sparks; i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2.0;
				double r = Math.sqrt(RANDOM.nextDouble()) * thunder.radius;
				int x = MathHelper.floor(thunder.center.x + Math.cos(a) * r);
				int z = MathHelper.floor(thunder.center.z + Math.sin(a) * r);
				int y = client.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
				client.particleManager.addParticle(ParticleTypes.ELECTRIC_SPARK, x + RANDOM.nextDouble(), y + 0.1, z + RANDOM.nextDouble(),
						0.0, 0.12 + RANDOM.nextDouble() * 0.2, 0.0);
			}
		}
		double e = t - ThunderTimeline.STROKE;
		if (thunder.struck && e >= 0 && e < 160) {
			double fade = 1.0 - e / 160.0;
			int n = (int) (12 * fade * fade) + (RANDOM.nextFloat() < fade ? 1 : 0);
			double core = Math.max(4, thunder.radius * 0.42);
			for (int i = 0; i < n; i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2.0;
				double r = Math.sqrt(RANDOM.nextDouble()) * core;
				double x = thunder.center.x + Math.cos(a) * r;
				double z = thunder.center.z + Math.sin(a) * r;
				double y = thunder.center.y - core * 0.4 + RANDOM.nextDouble() * 3.0;
				client.particleManager.addParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.ELECTRIC_SPARK, x, y, z,
						(RANDOM.nextDouble() - 0.5) * 0.3, 0.2 + RANDOM.nextDouble() * 0.4, (RANDOM.nextDouble() - 0.5) * 0.3);
			}
		}
	}

	@Nullable
	static AbstractClientPlayerEntity findShooter(MinecraftClient client, ClientThunder thunder) {
		if (client.world == null) {
			return null;
		}
		PlayerEntity player = client.world.getPlayerByUuid(thunder.shooter);
		return player instanceof AbstractClientPlayerEntity p ? p : null;
	}

	// --- sound helpers ---------------------------------------------------------------------

	/** A non-positional feed sound that stops if the shooter skips the feed. */
	private static void held(ClientThunder thunder, SoundEvent sound) {
		SoundInstance instance = PositionedSoundInstance.master(sound, 1.0F, 1.0F);
		thunder.feedSounds.add(instance);
		MinecraftClient.getInstance().getSoundManager().play(instance);
	}

	private static void master(SoundEvent sound, float pitch, float volume, int delay) {
		SoundInstance instance = PositionedSoundInstance.master(sound, pitch, volume);
		if (delay > 0) {
			MinecraftClient.getInstance().getSoundManager().play(instance, delay);
		} else {
			MinecraftClient.getInstance().getSoundManager().play(instance);
		}
	}

	/** A sound all round the listener rather than at a point: thunder rolling in from everywhere. */
	private static void around(MinecraftClient client, SoundEvent sound, float volume, int delay) {
		SoundInstance instance = new PositionedSoundInstance(sound.getId(), SoundCategory.WEATHER, volume, 1.0F, Random.create(), false, 0,
				SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true);
		if (delay > 0) {
			client.getSoundManager().play(instance, delay);
		} else {
			client.getSoundManager().play(instance);
		}
	}

	private static void at(MinecraftClient client, Vec3d pos, SoundEvent sound, SoundCategory category, float volume, float pitch,
			int delay) {
		PositionedSoundInstance instance = new PositionedSoundInstance(sound, category, volume, pitch, Random.create(), pos.x, pos.y, pos.z);
		if (delay > 0) {
			client.getSoundManager().play(instance, delay);
		} else {
			client.getSoundManager().play(instance);
		}
	}
}
