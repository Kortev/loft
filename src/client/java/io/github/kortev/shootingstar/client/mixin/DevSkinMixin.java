package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.DevSkins;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In the dev game, players wear their own skins from run/skins ({@link DevSkins}); otherwise nothing changes. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class DevSkinMixin {
	@Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
	private void shootingstar$devSkin(CallbackInfoReturnable<SkinTextures> cir) {
		SkinTextures skin = DevSkins.get(((AbstractClientPlayerEntity) (Object) this).getGameProfile().getName());
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}
}
