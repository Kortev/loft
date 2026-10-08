package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.thunder.ClientThunders;
import io.github.kortev.shootingstar.client.thunder.HammerGlow;
import io.github.kortev.shootingstar.registry.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Seen from outside, the runes of the hammer held up to call Mjölnir's storm flare as they do for the shooter. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
	@Inject(method = "renderItem", at = @At("HEAD"))
	private void shootingstar$runesUp(LivingEntity entity, ItemStack stack, ModelTransformationMode transformationMode, Arm arm,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (entity instanceof PlayerEntity player && stack.isOf(ModItems.MJOLNIR)) {
			HammerGlow.boost(ClientThunders.raiseGlow(player, MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false)));
		}
	}

	@Inject(method = "renderItem", at = @At("RETURN"))
	private void shootingstar$runesDown(LivingEntity entity, ItemStack stack, ModelTransformationMode transformationMode, Arm arm,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		HammerGlow.reset();
	}
}
