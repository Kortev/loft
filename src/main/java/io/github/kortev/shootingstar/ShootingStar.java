package io.github.kortev.shootingstar;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShootingStar implements ModInitializer {
	public static final String MOD_ID = "shootingstar";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("SS-03 Gungnir online");
	}
}
