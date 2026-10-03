package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.Capture;
import java.util.function.DoubleFunction;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The capture's own camera moves (the closing fly-over), applied after the mod's camera shots. */
@Mixin(value = Camera.class, priority = 1500)
public abstract class CaptureCameraMixin {
	@Shadow
	private boolean thirdPerson;

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	protected abstract void setPos(double x, double y, double z);

	@Inject(method = "update", at = @At("TAIL"))
	private void shootingstarTest$camera(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
			float tickDelta, CallbackInfo ci) {
		DoubleFunction<Capture.Pose> camera = Capture.camera;
		if (camera != null) {
			Capture.Pose pose = camera.apply(Capture.time());
			setRotation(pose.yaw(), pose.pitch());
			setPos(pose.x(), pose.y(), pose.z());
			this.thirdPerson = true;
		}
	}
}
