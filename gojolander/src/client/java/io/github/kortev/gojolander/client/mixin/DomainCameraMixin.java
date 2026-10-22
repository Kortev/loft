package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.client.ClientDomain;
import io.github.kortev.gojolander.client.ClientDomains;
import io.github.kortev.gojolander.client.DomainCamera;
import io.github.kortev.gojolander.client.HandSign;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the caster's camera to the shot of him casting his Domain ({@link DomainCamera}) while it runs; and for one it
 * let go of broken, takes their eyes down with them onto their knees, head hung, and up again.
 */
@Mixin(Camera.class)
public abstract class DomainCameraMixin {
	/** How far their eyes drop to the ground with their head hung, in first person (degrees). */
	private static final float HUNG = 32.0F;

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
			return;
		}
		ClientDomain let = focusedEntity == MinecraftClient.getInstance().player ? ClientDomains.kneeling(focusedEntity) : null;
		float kneel = let != null ? let.kneel(focusedEntity.getId(), let.time(tickDelta)) : 0.0F;
		if (kneel > 0.0F) {
			Camera camera = (Camera) (Object) this;
			Vec3d at = camera.getPos();
			setPos(at.x, at.y - HandSign.DROP * kneel, at.z);
			if (!thirdPerson) {
				setRotation(camera.getYaw(), Math.min(90.0F, camera.getPitch() + HUNG * kneel));
			}
		}
	}
}
