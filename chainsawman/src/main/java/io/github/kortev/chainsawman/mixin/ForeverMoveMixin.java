package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.contract.ForeverFlags;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A locked player's movement (and turning) is not taken from the client: the server holds them where the moment froze them. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ForeverMoveMixin {
	@Shadow
	public ServerPlayerEntity player;

	@Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
	private void chainsawman$held(PlayerMoveC2SPacket packet, CallbackInfo ci) {
		if (ForeverFlags.SERVER.contains(player.getId())) {
			ci.cancel();
		}
	}
}
