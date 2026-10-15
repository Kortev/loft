package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.FilmSkins;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In the film, the players wear their own skins (FilmSkins), offline as it runs. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class FilmSkinMixin {
	@Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
	private void shootingstarTest$filmSkin(CallbackInfoReturnable<SkinTextures> cir) {
		SkinTextures skin = FilmSkins.get(((AbstractClientPlayerEntity) (Object) this).getGameProfile().getName());
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}
}
