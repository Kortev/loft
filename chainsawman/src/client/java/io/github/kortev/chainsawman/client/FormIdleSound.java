package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawSounds;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;

/** The engine idling on someone in the form: going round and round where they are, until the form is off. */
final class FormIdleSound extends MovingSoundInstance {
	private final AbstractClientPlayerEntity player;

	FormIdleSound(AbstractClientPlayerEntity player) {
		super(ChainsawSounds.IDLE, SoundCategory.PLAYERS, Random.create());
		this.player = player;
		this.repeat = true;
		this.repeatDelay = 0;
		this.volume = 0.7F;
		this.attenuationType = SoundInstance.AttenuationType.LINEAR;
		follow();
	}

	@Override
	public void tick() {
		if (player.isRemoved() || !ClientForms.on(player)) {
			setDone();
			return;
		}
		follow();
	}

	private void follow() {
		x = player.getX();
		y = player.getY() + 1.4;
		z = player.getZ();
	}
}
