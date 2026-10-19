package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.form.Downed;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Down ({@link Downed}), you lie on the ground: the crawling pose, low, on the server and on every client alike. */
@Mixin(PlayerEntity.class)
public abstract class DownedPoseMixin {
	@Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
	private void chainsawman$lieDown(CallbackInfo ci) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		if (Downed.is(player)) {
			player.setPose(EntityPose.SWIMMING);
			ci.cancel();
		}
	}
}
