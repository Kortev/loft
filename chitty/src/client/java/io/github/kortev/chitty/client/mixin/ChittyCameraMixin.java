package io.github.kortev.chitty.client.mixin;

import io.github.kortev.chitty.ChittyEntity;
import io.github.kortev.chitty.airship.AirshipEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Chitty is five blocks long: in third person, someone riding in her is watched from twice as far back; the airship is
 * far bigger, and is watched from three times as far.
 */
@Mixin(Camera.class)
public abstract class ChittyCameraMixin {
	@Shadow
	private Entity focusedEntity;

	@ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"),
			require = 0)
	private float chitty$chittyDistance(float distance) {
		if (focusedEntity == null) {
			return distance;
		}
		return focusedEntity.getVehicle() instanceof ChittyEntity ? distance * 2.0F
				: focusedEntity.getVehicle() instanceof AirshipEntity ? distance * 3.0F : distance;
	}
}
