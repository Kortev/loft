package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientLoop;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A thing the hotel's loop moves a period along its corridor is moved here before the game would slide it there ({@link ClientLoop}). */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class HotelLoopMoveMixin {
	@Inject(method = "onEntityPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V", shift = At.Shift.AFTER))
	private void chainsawman$loopMove(EntityPositionS2CPacket packet, CallbackInfo ci) {
		ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null) {
			return;
		}
		Entity e = world.getEntityById(packet.getEntityId());
		if (e != null) {
			ClientLoop.beforeMove(e, packet.getX(), packet.getY(), packet.getZ());
		}
	}
}
