package io.github.kortev.shootingstar.registry;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
	public static final SoundEvent UPLINK_LOCK = register("uplink.lock");
	public static final SoundEvent UPLINK_DENIED = register("uplink.denied");
	public static final SoundEvent CAMERA_RISE = register("camera.rise");
	public static final SoundEvent FEED_ZOOM = register("feed.zoom");
	public static final SoundEvent FEED_AMBIENCE = register("feed.ambience");
	public static final SoundEvent FEED_RELAY = register("feed.relay");
	public static final SoundEvent FEED_WAKE = register("feed.wake");
	public static final SoundEvent FEED_LOAD = register("feed.load");
	public static final SoundEvent FEED_LAP = register("feed.lap");
	public static final SoundEvent FEED_COILS = register("feed.coils");
	public static final SoundEvent FEED_RELEASE = register("feed.release");
	public static final SoundEvent FEED_REENTRY = register("feed.reentry");
	public static final SoundEvent FEED_STRIKE = register("feed.strike");
	public static final SoundEvent FEED_CRUISE = register("feed.cruise");
	public static final SoundEvent FEED_TRANSIT = register("feed.transit");
	public static final SoundEvent FEED_LOCATE = register("feed.locate");
	// World sounds are mono so they can be placed; the ".near" versions are the shooter's stereo close-ups.
	public static final SoundEvent STRIKE_INBOUND = register("strike.inbound");
	public static final SoundEvent STRIKE_INBOUND_NEAR = register("strike.inbound.near");
	public static final SoundEvent STRIKE_IMPACT = register("strike.impact");
	public static final SoundEvent STRIKE_IMPACT_NEAR = register("strike.impact.near");
	public static final SoundEvent STRIKE_RUMBLE = register("strike.rumble");
	public static final SoundEvent STRIKE_RUMBLE_NEAR = register("strike.rumble.near");
	public static final SoundEvent STRIKE_AFTERMATH = register("strike.aftermath");
	public static final SoundEvent STRIKE_AFTERMATH_NEAR = register("strike.aftermath.near");

	// Ω-00 Ginnungagap. The shooter's cues are stereo; the swap is mono so it can be placed in the world.
	public static final SoundEvent GAP_KEY = register("gap.key");
	public static final SoundEvent GAP_AMBIENCE = register("gap.ambience");
	public static final SoundEvent GAP_WAKE = register("gap.wake");
	public static final SoundEvent GAP_MAP = register("gap.map");
	public static final SoundEvent GAP_EXTRACT = register("gap.extract");
	public static final SoundEvent GAP_SEND = register("gap.send");
	public static final SoundEvent GAP_FALL = register("gap.fall");
	public static final SoundEvent GAP_INBOUND = register("gap.inbound");
	public static final SoundEvent GAP_BLAST = register("gap.blast");
	public static final SoundEvent GAP_LOCK = register("gap.lock");
	public static final SoundEvent GAP_TEAR = register("gap.tear");
	public static final SoundEvent GAP_DRONE = register("gap.drone");
	public static final SoundEvent GAP_SWAP = register("gap.swap");
	public static final SoundEvent GAP_CONTACT = register("gap.contact");
	public static final SoundEvent GAP_IMPACT = register("gap.impact");
	public static final SoundEvent GAP_ERASE = register("gap.erase");
	public static final SoundEvent GAP_VOID = register("gap.void");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = ShootingStar.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void init() {
	}
}
