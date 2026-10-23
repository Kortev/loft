package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.FormCinematic;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands your camera to the shots of your own transformation while it plays ({@link FormCinematic}), and shakes it. */
@Mixin(Camera.class)
public abstract class FormCameraMixin {
	@Shadow
	private boolean thirdPerson;

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	protected abstract void setPos(double x, double y, double z);

	@Inject(method = "update", at = @At("TAIL"))
	private void chainsawman$shot(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta,
			CallbackInfo ci) {
		FormCinematic.Shot shot = FormCinematic.shot(tickDelta);
		Camera camera = (Camera) (Object) this;
		if (shot != null) {
			setRotation(shot.yaw(), shot.pitch());
			setPos(shot.x(), shot.y(), shot.z());
			// Your body shows once the camera is out of your head; and all through your merge, lying where your standing eyes
			// would not be (in its own eyes, your head alone isn't drawn).
			if (focusedEntity != null && (focusedEntity.getCameraPosVec(tickDelta).squaredDistanceTo(shot.x(), shot.y(), shot.z()) > 0.5
					|| io.github.kortev.chainsawman.client.MergeCinematic.filming(tickDelta))) {
				this.thirdPerson = true;
			}
		}
		if (shot == null && !this.thirdPerson) {
			// Riding Beam in your own eyes: raised and drawn back, so his head is ahead of you and not just his back under you.
			Vec3d eyes = io.github.kortev.chainsawman.client.BeamClient.eyes(tickDelta);
			if (eyes != null) {
				Vec3d at = camera.getPos();
				setPos(at.x + eyes.x, at.y + eyes.y, at.z + eyes.z);
				setRotation(camera.getYaw(), camera.getPitch() + io.github.kortev.chainsawman.client.BeamClient.eyeDown);
			}
		}
		double shake = FormCinematic.shake(tickDelta);
		if (shake > 0.001) {
			double t = (focusedEntity != null ? focusedEntity.age : 0) + tickDelta;
			Vec3d at = camera.getPos();
			Vec3d jitter = FormCinematic.jitter(shake, t);
			setPos(at.x + jitter.x, at.y + jitter.y, at.z + jitter.z);
		}
	}
}
