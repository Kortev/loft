package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.gap.ClientGaps;
import io.github.kortev.shootingstar.client.gap.GapCamera;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The screen overlays that come from where the player's body is (the block their head is in, water, fire) are not
 * drawn while the event's camera is somewhere else; and walking through the unseen world on the floor of nothing, its
 * blocks are never pressed against the screen.
 */
@Mixin(InGameOverlayRenderer.class)
public abstract class VoidOverlayMixin {
	@Inject(method = "renderOverlays", at = @At("HEAD"), cancellable = true)
	private static void shootingstar$camerasAway(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
		if (GapCamera.away(1.0F)) {
			ci.cancel();
		}
	}

	@Inject(method = "getInWallBlockState", at = @At("HEAD"), cancellable = true)
	private static void shootingstar$notInAWall(PlayerEntity player, CallbackInfoReturnable<BlockState> cir) {
		if (ClientGaps.floating()) {
			cir.setReturnValue(null);
		}
	}
}
