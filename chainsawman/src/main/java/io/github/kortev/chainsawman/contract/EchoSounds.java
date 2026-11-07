package io.github.kortev.chainsawman.contract;

import io.github.kortev.chainsawman.ChainsawSounds;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/** Echo's sounds, played from the owner for everyone round about (the drone and the whispers are the client's, ClientEcho). */
final class EchoSounds {
	private EchoSounds() {
	}

	private static void play(ServerWorld world, ServerPlayerEntity owner, SoundEvent sound, float volume) {
		world.playSound(null, owner.getX(), owner.getY() + 1.0, owner.getZ(), sound, SoundCategory.PLAYERS, volume, 1.0F);
	}

	static void burst(ServerWorld world, ServerPlayerEntity owner) {
		play(world, owner, ChainsawSounds.ECHO_BURST, 1.6F);
	}

	static void gather(ServerWorld world, ServerPlayerEntity owner) {
		play(world, owner, ChainsawSounds.ECHO_GATHER, 1.3F);
	}

	static void streakIn(ServerWorld world, ServerPlayerEntity owner) {
		play(world, owner, ChainsawSounds.ECHO_STREAK, 1.5F);
	}

	static void finale(ServerWorld world, ServerPlayerEntity owner) {
		play(world, owner, ChainsawSounds.ECHO_FINALE, 2.0F);
	}
}
