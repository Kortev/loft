package io.github.kortev.chitty.client;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.entity.Entity;

/**
 * The sounds each vehicle keeps up while she is about (Chitty's engine and wind, the airship's engine, the carriage's
 * wheels), started when she is first seen; and started again if anything stopped them: the sound settings turned down
 * to nothing and up again, a new sound device, the sounds reloaded (F3+T), or a hush that stops every sound.
 */
final class VehicleSounds {
	/** How often (ticks) each vehicle's sounds are looked at, to start them again if they have stopped. */
	private static final int CHECK = 20;
	/**
	 * Each vehicle's sounds, by the vehicle itself (not by its network id: one that went out of range and came back is
	 * another), until it is gone.
	 */
	private static final Map<Entity, List<SoundInstance>> PLAYING = new IdentityHashMap<>();

	private VehicleSounds() {
	}

	/** On each of her client ticks: her sounds, made by `make`, kept going. */
	static void keep(Entity vehicle, Supplier<List<? extends SoundInstance>> make) {
		SoundManager manager = MinecraftClient.getInstance().getSoundManager();
		if (vehicle.age % CHECK == 0) {
			// Vehicles gone (out of range, or their world left) are forgotten; their sounds stop of themselves.
			PLAYING.keySet().removeIf(other -> other.isRemoved() || other.getWorld() != vehicle.getWorld());
		}
		List<SoundInstance> sounds = PLAYING.get(vehicle);
		if (sounds != null && (vehicle.age % CHECK != 0 || sounds.stream().allMatch(manager::isPlaying))) {
			return;
		}
		if (sounds != null) {
			sounds.forEach(manager::stop);
		}
		sounds = List.copyOf(make.get());
		PLAYING.put(vehicle, sounds);
		sounds.forEach(manager::play);
	}
}
