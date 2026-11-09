package io.github.kortev.chainsawman.form;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;

/** The game rules of teams and the heart ({@link Fallen}, {@link Buddies}, {@link Hearts}, {@link Legend}). */
public final class TeamRules {
	/** Everyone who isn't a hybrid goes down instead of dying: a teammate has 30 s to pick them up. */
	public static final GameRules.Key<GameRules.BooleanRule> EVERYONE_GOES_DOWN = GameRuleRegistry.register("everyoneGoesDown",
			GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
	/** In-game days a hybrid can stay away before their heart goes back to the world (0: never). */
	public static final GameRules.Key<GameRules.IntRule> HEART_RETURN_DAYS = GameRuleRegistry.register("heartReturnDays",
			GameRules.Category.MISC, GameRuleFactory.createIntRule(0, 0));
	/** Whoever downed a hybrid can tear the heart out of them (hold use for 5 s). */
	public static final GameRules.Key<GameRules.BooleanRule> HEART_THEFT = GameRuleRegistry.register("heartTheft",
			GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(false));
	/** How many Chainsaw Man hearts a world has (a den for each). */
	public static final GameRules.Key<GameRules.IntRule> CHAINSAW_HEARTS = GameRuleRegistry.register("chainsawHearts",
			GameRules.Category.MISC, GameRuleFactory.createIntRule(1, 1));
	/** Chainsaw Man pulling the cord is heard, and shown on everyone's screen for a minute. */
	public static final GameRules.Key<GameRules.BooleanRule> LEGEND_ROAR = GameRuleRegistry.register("legendRoar",
			GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

	private TeamRules() {
	}

	public static void init() {
	}

	/** Whether the everyone-goes-down rule is on where {@code player} is. */
	public static boolean goesDown(ServerPlayerEntity player) {
		return player.getServerWorld().getGameRules().getBoolean(EVERYONE_GOES_DOWN);
	}
}
