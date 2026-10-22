package io.github.kortev.gojolander.client;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

/** A sound heard as it is, all round (not placed anywhere), going round and round until it is stopped: the void, the flood. */
final class LoopSound extends AbstractSoundInstance {
	LoopSound(SoundEvent sound, float volume) {
		super(sound, SoundCategory.MASTER, Random.create());
		this.volume = volume;
		this.repeat = true;
		this.repeatDelay = 0;
		this.relative = true;
		this.attenuationType = SoundInstance.AttenuationType.NONE;
	}
}
