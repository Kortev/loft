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
			"gungnirCraterRadius", GameRules.Category.MISC, GameRuleFactory.createIntRule(64, 8, 160));

	/** Whether the spent round is left standing as a spire through the whole world height. */
	public static final GameRules.Key<GameRules.BooleanRule> SPIRE = GameRuleRegistry.register(
			"gungnirSpire", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

	/** Radius in blocks of the zone a Ginnungagap event erases, build limit to bedrock. */
	public static final GameRules.Key<GameRules.IntRule> GAP_RADIUS = GameRuleRegistry.register(
			"ginnungagapRadius", GameRules.Category.MISC, GameRuleFactory.createIntRule(96, 16, 256));

	/** When false, the erasure still takes entities but leaves every block where it is (and nothing trades places). */
	public static final GameRules.Key<GameRules.BooleanRule> GAP_TERRAIN = GameRuleRegistry.register(
			"ginnungagapTerrainDamage", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

	/**
	 * Whether anyone (but the shooter, or anyone in creative or spectator) still standing in the zone when the black
	 * reaches them is erased with it. Everyone else is held safe in the void and carried home afterwards.
	 */
	public static final GameRules.Key<GameRules.BooleanRule> GAP_LETHAL = GameRuleRegistry.register(
			"ginnungagapLethal", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

	private ModGameRules() {
	}

	public static void init() {
	}
}
