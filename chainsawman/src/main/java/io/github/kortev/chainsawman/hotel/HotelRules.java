package io.github.kortev.chainsawman.hotel;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;

/** The two game rules the devils' homes read. */
public final class HotelRules {
	/** With this on, a devil's contract belongs to one player per world: once someone holds it, it is not offered again until they lose it. */
	public static final GameRules.Key<GameRules.BooleanRule> ONE_CONTRACT_PER_DEVIL = GameRuleRegistry.register("oneContractPerDevil",
			GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));
	/** In-game days after it was killed (or went off with a deal) that a hotel gets its devil back. */
	public static final GameRules.Key<GameRules.IntRule> DEVIL_RESPAWN_DAYS = GameRuleRegistry.register("devilRespawnDays",
			GameRules.Category.MISC, GameRuleFactory.createIntRule(3, 0));

	private HotelRules() {
	}

	public static void init() {
	}
}
