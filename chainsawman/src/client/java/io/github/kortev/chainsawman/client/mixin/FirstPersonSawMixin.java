package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientForms;
import io.github.kortev.chainsawman.client.FormFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In first person, your own arm in front of you has its saw too: drawn on the arm the game has just drawn, as the form
 * draws it on the arm in third person.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class FirstPersonSawMixin {
	@Inject(method = "renderArm", at = @At("TAIL"))
	private void chainsawman$saw(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		if (!ClientForms.on(player)) {
			return;
		}
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) (Object) this;
		String bone = arm == renderer.getModel().rightArm ? "right_arm" : "left_arm";
		float time = player.age + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
		FormFeature.draw(matrices, consumers, light, OverlayTexture.DEFAULT_UV, player, time, bone, arm);
	}
}
