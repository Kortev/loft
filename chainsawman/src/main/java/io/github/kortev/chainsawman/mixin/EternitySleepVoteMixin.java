package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.SleepManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Eternity's lasting price: a holder of its contract does not count toward the server's sleep vote (they cannot sleep, and must not hold the
 * others up): the vote counts everyone else, as it does for a spectator.
 */
@Mixin(SleepManager.class)
public abstract class EternitySleepVoteMixin {
	@Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;isSpectator()Z"))
	private boolean chainsawman$holdersDoNotVote(ServerPlayerEntity player) {
		return player.isSpectator() || Contracts.has(player, Contract.ETERNITY);
	}
}
