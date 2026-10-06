package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.gap.GapCamera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the event's camera is away from the shooter's eyes, nobody standing right where the lens passes is drawn: a
 * figure filling the picture from inside it is not a shot.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class LensMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void shootingstar$notAtTheLens(E entity, Frustum frustum, double x, double y, double z,
			CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof PlayerEntity && GapCamera.atLens(entity)) {
			cir.setReturnValue(false);
		}
	}
}
