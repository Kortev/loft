package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.chitty.ChittyEntity;
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
 * Riding in Chitty, the view banks with her as she turns in the air, as from the seat of an aeroplane (a little less
 * than she does, so the world does not tip too far). It rolls the view where the game tilts it when you are hurt.
 */
@Mixin(GameRenderer.class)
public abstract class ChittyViewMixin {
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
	private void shootingstar$bankWithChitty(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		Entity viewer = MinecraftClient.getInstance().getCameraEntity();
		if (viewer != null && viewer.getVehicle() instanceof ChittyEntity car) {
			float bank = car.getBank(tickDelta);
			if (bank != 0.0F) {
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(bank * 0.8F));
			}
		}
	}
}
