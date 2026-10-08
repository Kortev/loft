package io.github.kortev.chitty.client.mixin;

import io.github.kortev.chitty.ChittyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Riding in Chitty, the view banks with her as she turns in the air, as from the seat of an aeroplane: exactly as far as
 * she does, so that she stays level in front of you and the world tips instead. It rolls the view where the game tilts
 * it when you are hurt. (Her pitch is followed by turning her passengers' heads with her, ChittyEntity, so that what
 * they aim at stays under the crosshair.)
 */
@Mixin(GameRenderer.class)
public abstract class ChittyViewMixin {
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
	private void chitty$bankWithChitty(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		Entity viewer = MinecraftClient.getInstance().getCameraEntity();
		if (viewer != null && viewer.getVehicle() instanceof ChittyEntity car) {
			float bank = car.getBank(tickDelta);
			if (bank != 0.0F) {
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(bank));
			}
		}
	}
}
