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
		double shake = FormCinematic.shake(tickDelta);
		if (shake > 0.001) {
			double t = (focusedEntity != null ? focusedEntity.age : 0) + tickDelta;
			Vec3d at = camera.getPos();
			setPos(at.x + shake * (Math.sin(t * 7.3) + Math.sin(t * 13.1) * 0.5), at.y + shake * (Math.sin(t * 9.7 + 1.0) + Math.sin(t * 17.3) * 0.5),
					at.z + shake * (Math.sin(t * 8.1 + 2.0) + Math.sin(t * 11.9) * 0.5));
		}
	}
}
