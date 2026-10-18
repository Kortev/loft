package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.client.ClientDomain;
import io.github.kortev.gojolander.client.ClientDomains;
import io.github.kortev.gojolander.domain.DomainTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Whatever a Domain holds trembles where it hangs once the flood pours in: everything there is, all at once, is too much
 * to stand still under. Only drawn so (it stays exactly where it is).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class HeldTrembleMixin {
	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"))
	private void gojolander$tremble(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
			int light, CallbackInfo ci) {
		ClientDomain held = ClientDomains.holdingEntity(entity);
		if (held == null) {
			return;
		}
		double t = held.time(MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true));
		float k = (float) MathHelper.clamp((t - DomainTimeline.VOID) / 6.0, 0.0, 1.0) * 0.022F;
		if (k > 0.0F) {
			int id = entity.getId();
			matrices.translate(MathHelper.sin((float) t * 9.1F + id) * k, MathHelper.sin((float) t * 7.3F + id * 2.0F) * k * 0.6F,
					MathHelper.sin((float) t * 8.3F + id * 3.0F) * k);
		}
	}
}
