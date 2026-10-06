package io.github.kortev.shootingstar.client.chitty;

import io.github.kortev.shootingstar.chitty.Chitty;
import io.github.kortev.shootingstar.chitty.ChittyEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;

/**
 * One of Chitty's running sounds, following her: the engine (while anyone drives her, deeper ticking over and rising
 * with speed and throttle) or the wind and propeller in flight (as the wings open, louder and higher the faster she
 * flies).
 */
public class ChittySound extends MovingSoundInstance {
	private final ChittyEntity car;
	private final boolean flight;

	public ChittySound(ChittyEntity car, boolean flight) {
		super(flight ? Chitty.FLIGHT : Chitty.ENGINE, SoundCategory.NEUTRAL, SoundInstance.createRandom());
		this.car = car;
		this.flight = flight;
		this.repeat = true;
		this.repeatDelay = 0;
		this.volume = 0.0F;
		this.pitch = flight ? 0.8F : 0.75F;
		this.x = car.getX();
		this.y = car.getY();
		this.z = car.getZ();
	}

	@Override
	public boolean canPlay() {
		return !car.isSilent();
	}

	@Override
	public boolean shouldAlwaysPlay() {
		return true;
	}

	@Override
	public void tick() {
		if (car.isRemoved()) {
			setDone();
			return;
		}
		x = car.getX();
		y = car.getY() + 0.8;
		z = car.getZ();
		double speed = car.getSpeed();
		float targetVolume;
		float targetPitch;
		if (flight) {
			float open = car.getWingOpen(1.0F);
			targetVolume = open * (0.25F + 0.75F * (float) Math.min(1.0, speed / 1.1));
			targetPitch = 0.7F + 0.6F * (float) Math.min(1.3, speed);
		} else {
			boolean running = car.isEngineRunning();
			float load = car.getThrottle() > 0 ? 0.1F : 0.0F;
			targetVolume = running ? 0.55F + 0.35F * (float) Math.min(1.0, speed / 0.75) : 0.0F;
			targetPitch = 0.72F + 0.65F * (float) Math.min(1.2, speed / 0.75) + load;
		}
		volume = MathHelper.lerp(0.25F, volume, targetVolume);
		pitch = MathHelper.clamp(MathHelper.lerp(0.2F, pitch, targetPitch), 0.5F, 2.0F);
	}
}
