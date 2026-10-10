package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.registry.ModSounds;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;

/** The rebuild's score, which can be faded out when the shooter hurries the rebuild on. */
final class RebuildSound extends MovingSoundInstance {
	private boolean fading;

	RebuildSound() {
		super(ModSounds.GAP_REBUILD, SoundCategory.MASTER, SoundInstance.createRandom());
		relative = true;
		attenuationType = AttenuationType.NONE;
		repeat = false;
		volume = 1.0F;
	}

	/** Out over a second. */
	void fade() {
		fading = true;
	}

	@Override
	public void tick() {
		if (fading) {
			volume = Math.max(0.0F, volume - 0.05F);
			if (volume <= 0.0F) {
				setDone();
			}
		}
	}
}
