package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.camera.CameraDirector;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the camera to the strike's cinematic shots while one is playing. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean thirdPerson;

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	protected abstract void setPos(double x, double y, double z);

	@Inject(method = "update", at = @At("TAIL"))
	private void shootingstar$applyShot(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
			float tickDelta, CallbackInfo ci) {
		CameraDirector.Shot shot = CameraDirector.current(tickDelta);
		if (shot != null) {
			setRotation(shot.yaw(), shot.pitch());
			setPos(shot.x(), shot.y(), shot.z());
			this.thirdPerson = true;
		}
	}
}
