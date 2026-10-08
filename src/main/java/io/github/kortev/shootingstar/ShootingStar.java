package io.github.kortev.shootingstar;

import io.github.kortev.shootingstar.command.GapCommand;
import io.github.kortev.shootingstar.command.GungnirCommand;
import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.network.ModNetworking;
import io.github.kortev.shootingstar.registry.ModBlocks;
import io.github.kortev.shootingstar.registry.ModCriteria;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.registry.ModSounds;
import io.github.kortev.shootingstar.strike.StrikeManager;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShootingStar implements ModInitializer {
	public static final String MOD_ID = "shootingstar";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModItems.init();
		ModSounds.init();
		ModGameRules.init();
		ModCriteria.init();
		ModNetworking.init();
		StrikeManager.init();
		GungnirCommand.init();
		GapManager.init();
		GapCommand.init();
		LOGGER.info("SS-03 Gungnir online");
	}
}
