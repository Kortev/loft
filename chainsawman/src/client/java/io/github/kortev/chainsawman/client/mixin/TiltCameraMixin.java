package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientHotel;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When the Eternity Devil turns the floor ({@link ClientHotel#roll}) the view rolls a quarter turn, and it shakes as the shaft arrives. */
@Mixin(value = Camera.class, priority = 2000)
public abstract class TiltCameraMixin {
	@Shadow
	protected abstract void setPos(double x, double y, double z);

	@Inject(method = "update", at = @At("TAIL"))
	private void chainsawman$tilt(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
		Camera camera = (Camera) (Object) this;
		float roll = ClientHotel.roll(tickDelta);
		if (roll != 0.0F) {
			camera.getRotation().rotateZ(roll);
		}
		float shake = ClientHotel.shake(tickDelta);
		if (shake > 0.001F) {
			double t = (focusedEntity != null ? focusedEntity.age : 0) + tickDelta;
			Vec3d at = camera.getPos();
			double k = 0.09 * shake;
			setPos(at.x + Math.sin(t * 2.7) * k, at.y + Math.sin(t * 3.9 + 1.0) * k, at.z + Math.cos(t * 3.1 + 2.0) * k);
		}
	}
}
