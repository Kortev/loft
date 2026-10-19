package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientForms;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the form, no helmet is drawn: it would sit over the chainsaw head, or through it. It still protects. */
@Mixin(ArmorFeatureRenderer.class)
public abstract class HelmetHiddenMixin {
	@Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
	private void chainsawman$noHelmet(MatrixStack matrices, VertexConsumerProvider consumers, LivingEntity entity, EquipmentSlot slot, int light,
			BipedEntityModel<?> model, CallbackInfo ci) {
		if (slot == EquipmentSlot.HEAD && entity instanceof PlayerEntity player && ClientForms.on(player)) {
			ci.cancel();
		}
	}
}
