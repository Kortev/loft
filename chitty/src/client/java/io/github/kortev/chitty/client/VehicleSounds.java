package io.github.kortev.chitty.client;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
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
	private static final Map<Entity, List<SoundInstance>> PLAYING = new WeakHashMap<>();

	private VehicleSounds() {
	}

	/** On each of her client ticks: her sounds, made by `make`, kept going. */
	static void keep(Entity vehicle, Supplier<List<? extends SoundInstance>> make) {
		SoundManager manager = MinecraftClient.getInstance().getSoundManager();
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
