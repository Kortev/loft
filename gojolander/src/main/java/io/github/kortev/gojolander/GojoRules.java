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
			"unlimitedVoidRadius", GameRules.Category.MISC, GameRuleFactory.createIntRule(16, 8, 48));

	/** How long the void holds whoever it caught, in seconds, from the world going until the barrier shatters. */
	public static final GameRules.Key<GameRules.IntRule> VOID_HOLD = GameRuleRegistry.register(
			"unlimitedVoidHoldSeconds", GameRules.Category.MISC, GameRuleFactory.createIntRule(5, 1, 30));

	/** How long after casting it before he can cast it again, in seconds. */
	public static final GameRules.Key<GameRules.IntRule> VOID_COOLDOWN = GameRuleRegistry.register(
			"unlimitedVoidCooldownSeconds", GameRules.Category.MISC, GameRuleFactory.createIntRule(300, 0, 3600));

	private GojoRules() {
	}

	static void init() {
	}
}
