package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.client.DomainCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the caster's camera to the shot of him casting his Domain ({@link DomainCamera}) while it runs. */
@Mixin(Camera.class)
public abstract class DomainCameraMixin {
	@Shadow
	private boolean thirdPerson;

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	protected abstract void setPos(double x, double y, double z);

	@Inject(method = "update", at = @At("TAIL"))
	private void gojolander$shot(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta,
			CallbackInfo ci) {
		DomainCamera.Shot shot = DomainCamera.current(tickDelta);
		if (shot != null) {
			setRotation(shot.yaw(), shot.pitch());
			setPos(shot.x(), shot.y(), shot.z());
			// His body shows once the camera is out of his head.
			if (focusedEntity != null && focusedEntity.getCameraPosVec(tickDelta).squaredDistanceTo(shot.x(), shot.y(), shot.z()) > 0.5) {
				this.thirdPerson = true;
			}
		}
	}
}
