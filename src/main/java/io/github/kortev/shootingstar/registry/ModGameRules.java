package io.github.kortev.shootingstar.registry;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;

public final class ModGameRules {
	/** When false, strikes still hurt entities but leave terrain alone and raise no spire. */
	public static final GameRules.Key<GameRules.BooleanRule> TERRAIN_DAMAGE = GameRuleRegistry.register(
			"gungnirTerrainDamage", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

	/** Radius in blocks of the planed zone around the impact point. */
	public static final GameRules.Key<GameRules.IntRule> CRATER_RADIUS = GameRuleRegistry.register(
			"gungnirCraterRadius", GameRules.Category.MISC, GameRuleFactory.createIntRule(28, 8, 64));

	/** Whether the spent round is left standing as a spire through the whole world height. */
	public static final GameRules.Key<GameRules.BooleanRule> SPIRE = GameRuleRegistry.register(
			"gungnirSpire", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

	private ModGameRules() {
	}

	public static void init() {
	}
}
