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
		volume = MathHelper.lerp(0.25F, volume, targetVolume(car, flight));
		pitch = MathHelper.clamp(MathHelper.lerp(0.2F, pitch, targetPitch(car, flight)), 0.5F, 2.0F);
	}

	/** How loud this sound wants to be now (the instance eases towards it). */
	public static float targetVolume(ChittyEntity car, boolean flight) {
		double speed = car.getSpeed();
		if (flight) {
			return car.getWingOpen(1.0F) * (0.25F + 0.75F * (float) Math.min(1.0, speed / 1.1));
		}
		return car.isEngineRunning() ? 0.55F + 0.35F * (float) Math.min(1.0, speed / 0.75) : 0.0F;
	}

	/** The pitch this sound wants now. */
	public static float targetPitch(ChittyEntity car, boolean flight) {
		double speed = car.getSpeed();
		if (flight) {
			return 0.7F + 0.6F * (float) Math.min(1.3, speed);
		}
		return 0.72F + 0.65F * (float) Math.min(1.2, speed / 0.75) + (car.getThrottle() > 0 ? 0.1F : 0.0F);
	}
}
