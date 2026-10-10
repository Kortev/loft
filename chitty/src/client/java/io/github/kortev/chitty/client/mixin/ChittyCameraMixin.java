package io.github.kortev.chitty.client.mixin;

import io.github.kortev.chitty.ChittyEntity;
import io.github.kortev.chitty.airship.AirshipEntity;
import io.github.kortev.chitty.carriage.CarriageEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The third-person view from the film's vehicles. Chitty is five blocks long: someone riding in her is watched from
 * twice as far back; the Child Catcher's carriage, with its horse, is longer still. The airship is 34 blocks long and 13 high: riding her, the camera turns about the middle of her
 * (between the gondola and the envelope) rather than about the rider, and stands far enough back to see all of her.
 */
@Mixin(Camera.class)
public abstract class ChittyCameraMixin {
	@Shadow
	private Entity focusedEntity;

	@Shadow
	protected abstract void setPos(double x, double y, double z);

	@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"),
			require = 0)
	private void chitty$airshipPivot(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView, float tickDelta,
			CallbackInfo ci) {
		if (focused != null && focused.getVehicle() instanceof AirshipEntity ship) {
			Vec3d centre = ship.viewCentre(tickDelta);
			setPos(centre.x, centre.y, centre.z);
		}
	}

	@ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"),
			require = 0)
	private float chitty$chittyDistance(float distance) {
		if (focusedEntity == null) {
			return distance;
		}
		return focusedEntity.getVehicle() instanceof ChittyEntity ? distance * 2.0F
				: focusedEntity.getVehicle() instanceof CarriageEntity ? distance * 2.2F
				: focusedEntity.getVehicle() instanceof AirshipEntity ? AirshipEntity.VIEW_DISTANCE : distance;
	}
}
