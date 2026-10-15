package io.github.kortev.chitty.client;

import io.github.kortev.chitty.airship.Airship;
import io.github.kortev.chitty.airship.AirshipEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;

/**
 * One of the airship's running sounds, following her: the engine in the stern of the gondola with her two propellers
 * beating the air, ticking over while she is piloted and pulling with the throttle; and the wind in her rigging as she
 * goes.
 */
public class AirshipSound extends MovingSoundInstance {
	public enum Layer { ENGINE, WIND }

	private final AirshipEntity ship;
	private final Layer layer;

	public AirshipSound(AirshipEntity ship, Layer layer) {
		super(layer == Layer.ENGINE ? Airship.ENGINE : Airship.WIND, SoundCategory.NEUTRAL, SoundInstance.createRandom());
		this.ship = ship;
		this.layer = layer;
		this.repeat = true;
		this.repeatDelay = 0;
		this.volume = 0.0F;
		this.x = ship.getX();
		this.y = ship.getY();
		this.z = ship.getZ();
	}

	@Override
	public boolean canPlay() {
		return !ship.isSilent();
	}

	@Override
	public boolean shouldAlwaysPlay() {
		return true;
	}

	@Override
	public void tick() {
		if (ship.isRemoved()) {
			setDone();
			return;
		}
		x = ship.getX();
		y = ship.getY() + 1.6;
		z = ship.getZ();
		float speed = (float) MathHelper.clamp(ship.getSpeed() / 0.42, 0.0, 1.0);
		float want;
		if (layer == Layer.ENGINE) {
			boolean running = ship.isEngineRunning();
			float pull = ship.getThrottle() != 0 ? 1.0F : 0.0F;
			want = running ? 0.45F + 0.3F * pull + 0.25F * speed : 0.0F;
			pitch = 0.8F + 0.25F * pull + 0.2F * speed;
		} else {
			want = speed * speed * 0.8F;
			pitch = 0.85F + 0.3F * speed;
		}
		volume = MathHelper.lerp(0.1F, volume, want);
	}
}
