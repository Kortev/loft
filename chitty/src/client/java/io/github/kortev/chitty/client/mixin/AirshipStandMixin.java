package io.github.kortev.chitty.client.mixin;

import io.github.kortev.chitty.airship.AirshipEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The airship's gondola is standing room: everyone aboard her is drawn standing, not sitting as riders are. */
@Mixin(LivingEntityRenderer.class)
public abstract class AirshipStandMixin {
	@Shadow
	protected EntityModel<?> model;

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/model/EntityModel;riding:Z", opcode = Opcodes.PUTFIELD,
					shift = At.Shift.AFTER), require = 0)
	private void chitty$standAboard(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers,
			int light, CallbackInfo ci) {
		if (entity.getVehicle() instanceof AirshipEntity) {
			model.riding = false;
		}
	}
}
