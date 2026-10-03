package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** With -Dshootingstar.genworld=true the dedicated server stops as soon as its world exists. */
public class WorldGenHelper implements ModInitializer {
	@Override
	public void onInitialize() {
		if (!Boolean.getBoolean("shootingstar.genworld")) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ShootingStar.LOGGER.info("[selftest] world generated, stopping the server");
			server.stop(false);
		});
	}
}
