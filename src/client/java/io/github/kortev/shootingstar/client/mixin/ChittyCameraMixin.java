package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.chitty.ChittyEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Chitty is five blocks long: in third person, someone riding in her is watched from twice as far back. */
@Mixin(Camera.class)
public abstract class ChittyCameraMixin {
	@Shadow
	private Entity focusedEntity;

	@ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"),
			require = 0)
	private float shootingstar$chittyDistance(float distance) {
		return focusedEntity != null && focusedEntity.getVehicle() instanceof ChittyEntity ? distance * 2.0F : distance;
	}
}
