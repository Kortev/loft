package io.github.kortev.shootingstar.test.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The film's player clicking as a player does ({@code FilmHuman}): a left or right click on whatever is under the
 * crosshair, through the game's own handling of the mouse buttons.
 */
@Mixin(MinecraftClient.class)
public interface FilmClickInvoker {
	@Invoker("doAttack")
	boolean shootingstar$leftClick();

	@Invoker("doItemUse")
	void shootingstar$rightClick();
}
