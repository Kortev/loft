package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** With -Dshootingstar.genworld=true the dedicated server stops as soon as its world exists. */
public class WorldGenHelper implements ModInitializer {
	@Override
	public void onInitialize() {
		if (!Boolean.getBoolean("shootingstar.genworld")) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			// What the self test will stand in, to tell one seed's world from another's.
			ServerWorld world = server.getOverworld();
			BlockPos spawn = world.getSpawnPos();
			String biome = world.getBiome(spawn).getKey().map(key -> key.getValue().toString()).orElse("?");
			ShootingStar.LOGGER.info("[selftest] world generated, spawn {} in {}; stopping the server", spawn.toShortString(), biome);
			server.stop(false);
		});
	}
}
