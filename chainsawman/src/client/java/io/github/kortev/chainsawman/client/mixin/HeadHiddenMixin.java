package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientForms;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In the form, the player's own head and hat layer are not drawn: the chainsaw head (FormFeature) is drawn in their
 * place. Nor is your own while your merge's camera is in your own eyes ({@code MergeCinematic.ownEyes}). The renderer shows them again before the next frame's pose, so nothing is left hidden once the form is gone.
 */
@Mixin(PlayerEntityModel.class)
public abstract class HeadHiddenMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void chainsawman$hideHead(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		if (entity instanceof PlayerEntity player && (ClientForms.on(player)
				|| player == net.minecraft.client.MinecraftClient.getInstance().player && io.github.kortev.chainsawman.client.MergeCinematic.ownEyes())) {
			PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
			model.head.visible = false;
			model.hat.visible = false;
		}
	}
}
