package io.github.kortev.shootingstar.registry;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
	public static final SoundEvent UPLINK_LOCK = register("uplink.lock");
	public static final SoundEvent UPLINK_DENIED = register("uplink.denied");
	public static final SoundEvent FEED_ZOOM = register("feed.zoom");
	public static final SoundEvent FEED_RELAY = register("feed.relay");
	public static final SoundEvent FEED_WAKE = register("feed.wake");
	public static final SoundEvent FEED_LOAD = register("feed.load");
	public static final SoundEvent FEED_LAP = register("feed.lap");
	public static final SoundEvent FEED_RELEASE = register("feed.release");
	public static final SoundEvent FEED_REENTRY = register("feed.reentry");
	public static final SoundEvent STRIKE_INBOUND = register("strike.inbound");
	public static final SoundEvent STRIKE_IMPACT = register("strike.impact");
	public static final SoundEvent STRIKE_RUMBLE = register("strike.rumble");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = ShootingStar.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void init() {
	}
}
