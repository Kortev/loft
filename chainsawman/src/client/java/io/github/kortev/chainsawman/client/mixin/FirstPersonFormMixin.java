package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientForms;
import io.github.kortev.chainsawman.client.FirstPersonSaws;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In the form your hands are saws: in first person both arms are in front of you, slashing and grinding as you fight
 * ({@link FirstPersonSaws}), in place of the hand and what it holds. Using something (eating, a shield) shows it as usual.
 */
@Mixin(HeldItemRenderer.class)
public abstract class FirstPersonFormMixin {
	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At("HEAD"), cancellable = true)
	private void chainsawman$saws(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate consumers, ClientPlayerEntity player, int light,
			CallbackInfo ci) {
		if (ClientForms.on(player) && ClientForms.pullAge(player, tickDelta) < 0.0F && !player.isUsingItem()) {
			FirstPersonSaws.render((HeldItemRenderer) (Object) this, tickDelta, matrices, consumers, player, light);
			ci.cancel();
		}
	}
}
