package io.github.kortev.chitty.client;

import io.github.kortev.chitty.carriage.Carriage;
import io.github.kortev.chitty.carriage.CarriageEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;

/**
 * The carriage's wheels as she goes, following her: iron tyres grinding over the ground and the creak and rattle of
 * her springs and her cage, louder and quicker the faster she goes, nothing standing.
 */
public class CarriageSound extends MovingSoundInstance {
	private final CarriageEntity carriage;

	public CarriageSound(CarriageEntity carriage) {
		super(Carriage.ROLL, SoundCategory.NEUTRAL, SoundInstance.createRandom());
		this.carriage = carriage;
		this.repeat = true;
		this.repeatDelay = 0;
		this.volume = 0.0F;
		this.x = carriage.getX();
		this.y = carriage.getY();
		this.z = carriage.getZ();
	}

	@Override
	public boolean canPlay() {
		return !carriage.isSilent();
	}

	@Override
	public boolean shouldAlwaysPlay() {
		return true;
	}

	@Override
	public void tick() {
		if (carriage.isRemoved()) {
			setDone();
			return;
		}
		x = carriage.getX();
		y = carriage.getY() + 0.5;
		z = carriage.getZ();
		float speed = (float) MathHelper.clamp(carriage.getSpeed() / 0.42, 0.0, 1.0);
		float want = carriage.isOnGround() ? MathHelper.sqrt(speed) * 0.9F : 0.0F;
		pitch = 0.75F + 0.45F * speed;
		volume = MathHelper.lerp(0.2F, volume, want);
	}
}
