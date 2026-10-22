package io.github.kortev.shootingstar.test.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where the game takes the mouse to be, set by the film's own pointer ({@code FilmCursor}). */
@Mixin(Mouse.class)
public interface FilmMouseAccessor {
	@Accessor("x")
	void shootingstar$setX(double x);

	@Accessor("y")
	void shootingstar$setY(double y);
}
