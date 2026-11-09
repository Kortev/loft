package io.github.kortev.chainsawman.mixin;

import com.mojang.datafixers.util.Either;
import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Eternity's lasting price: it keeps your nights. A holder of its contract cannot sleep in a bed (the bed still sets where they respawn, as it
 * does by day), so the time since they last rested keeps growing and phantoms come for them as in vanilla.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class EternityBedMixin {
	@Inject(method = "trySleep", at = @At("HEAD"), cancellable = true)
	private void chainsawman$eternityKeepsYourNights(BlockPos pos, CallbackInfoReturnable<Either<PlayerEntity.SleepFailureReason, Unit>> cir) {
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
		if (Contracts.has(player, Contract.ETERNITY)) {
			player.setSpawnPoint(player.getWorld().getRegistryKey(), pos, player.getYaw(), false, true);
			player.sendMessage(Text.translatable("chainsawman.forever.nights"), true);
			cir.setReturnValue(Either.left(PlayerEntity.SleepFailureReason.OTHER_PROBLEM));
		}
	}
}
