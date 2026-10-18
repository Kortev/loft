package io.github.kortev.gojolander;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;

/**
 * The Domain's numbers, as game rules so they can be balanced on the SMP against everyone else's mods without a new
 * jar. Each is read once as a Domain is cast and kept for the whole of it.
 */
public final class GojoRules {
	/** How far the Domain reaches from where he casts it, in blocks: the ring on the ground and the sphere over it. */
	public static final GameRules.Key<GameRules.IntRule> VOID_RADIUS = GameRuleRegistry.register(
			"unlimitedVoidRadius", GameRules.Category.MISC, GameRuleFactory.createIntRule(32, 8, 64));

	/** How long the void holds whoever it caught, in seconds, from the void opening until the barrier shatters. */
	public static final GameRules.Key<GameRules.IntRule> VOID_HOLD = GameRuleRegistry.register(
			"unlimitedVoidHoldSeconds", GameRules.Category.MISC, GameRuleFactory.createIntRule(10, 1, 30));

	/** How long after casting it before he can cast it again, in seconds. */
	public static final GameRules.Key<GameRules.IntRule> VOID_COOLDOWN = GameRuleRegistry.register(
			"unlimitedVoidCooldownSeconds", GameRules.Category.MISC, GameRuleFactory.createIntRule(900, 0, 7200));

	/**
	 * Whether the void breaks whoever it held to the end: when it shatters they are left at half a heart, as well as
	 * dazed. Off, they are only dazed.
	 */
	public static final GameRules.Key<GameRules.BooleanRule> VOID_BREAKS = GameRuleRegistry.register(
			"unlimitedVoidBreaks", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

	/**
	 * How far the Six Eyes see through the blindfold (and through walls): every living thing within this many blocks of
	 * its owner shows to him. 0 turns them off.
	 */
	public static final GameRules.Key<GameRules.IntRule> SIX_EYES_RANGE = GameRuleRegistry.register(
			"sixEyesRange", GameRules.Category.MISC, GameRuleFactory.createIntRule(24, 0, 64));

	private GojoRules() {
	}

	static void init() {
	}
}
