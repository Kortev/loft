package io.github.kortev.shootingstar.client.chitty;

import io.github.kortev.shootingstar.chitty.Chitty;
import io.github.kortev.shootingstar.chitty.ChittyEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;

/**
 * One of Chitty's running sounds, following her. The engine is three loops recorded at three speeds (ticking over,
 * pulling, working hard): each plays near its own revs and they crossfade as hers rise and fall, so none is stretched
 * far from how it was made. The fourth is the wing propellers and the wind, as the wings open and the faster she flies;
 * the fifth the rush of air past her, swelling with her speed through the air (flying or falling).
 */
public class ChittySound extends MovingSoundInstance {
	/** The revs each engine loop was made at (tools/gen_chitty_sounds.py). */
	public static final float IDLE_RPM = 440.0F;
	public static final float LOW_RPM = 1100.0F;
	public static final float HIGH_RPM = 2200.0F;

	public enum Layer {
		IDLE, LOW, HIGH, FLIGHT, WIND;

		SoundEvent event() {
			return switch (this) {
				case IDLE -> Chitty.ENGINE_IDLE;
				case LOW -> Chitty.ENGINE_LOW;
				case HIGH -> Chitty.ENGINE_HIGH;
				case FLIGHT -> Chitty.FLIGHT;
				case WIND -> Chitty.WIND;
			};
		}
	}

	private final ChittyEntity car;
	private final Layer layer;
	/** The revs as this sound has them, easing towards the car's: every layer eases alike, so they stay together. */
	private float rpm;

	public ChittySound(ChittyEntity car, Layer layer) {
		super(layer.event(), SoundCategory.NEUTRAL, SoundInstance.createRandom());
		this.car = car;
		this.layer = layer;
		this.repeat = true;
		this.repeatDelay = 0;
		this.volume = 0.0F;
		this.rpm = IDLE_RPM;
		this.pitch = pitchAt(layer, rpm, car);
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
		rpm = easeRpm(rpm, car);
		volume = MathHelper.lerp(0.3F, volume, volumeAt(layer, rpm, car));
		pitch = pitchAt(layer, rpm, car);
	}

	/** The revs she wants now: ticking over standing, rising with speed and the throttle, higher in the air. */
	public static float targetRpm(ChittyEntity car) {
		if (!car.isEngineRunning()) {
			return IDLE_RPM;
		}
		float speed = (float) Math.abs(car.getSpeed());
		float pushing = car.getThrottle() != 0 ? 1.0F : 0.0F;
		if (car.isFlying()) {
			return 1500.0F + 900.0F * Math.min(1.0F, speed / 1.3F) + 250.0F * pushing;
		}
		if (car.getFloatOpen(1.0F) > 0.5F) {
			return 600.0F + 1300.0F * Math.min(1.0F, speed / 0.42F) + 300.0F * pushing;
		}
		return IDLE_RPM + 1850.0F * Math.min(1.0F, speed / 0.75F) + 380.0F * pushing;
	}

	/** One tick of the revs easing towards where she wants them: quicker up than down, as an engine does. */
	public static float easeRpm(float rpm, ChittyEntity car) {
		float target = targetRpm(car);
		return rpm + (target - rpm) * (target > rpm ? 0.18F : 0.1F);
	}

	/** How loud a layer wants to be at these revs (the sound eases towards it). */
	public static float volumeAt(Layer layer, float rpm, ChittyEntity car) {
		if (layer == Layer.FLIGHT) {
			double speed = car.getSpeed();
			return car.getWingOpen(1.0F) * (0.3F + 0.7F * (float) Math.min(1.0, speed / 1.1));
		}
		if (layer == Layer.WIND) {
			// Nothing at a walk, swelling past a gallop; mostly a thing of the air, not the road.
			float rush = MathHelper.clamp(((float) car.getAirSpeed() - 0.3F) / 1.0F, 0.0F, 1.0F);
			return rush * (float) Math.sqrt(rush) * (car.isOnGround() || car.isFloating() ? 0.3F : 1.0F);
		}
		if (!car.isEngineRunning()) {
			return 0.0F;
		}
		float weight = switch (layer) {
			case IDLE -> MathHelper.clamp((900.0F - rpm) / 400.0F, 0.0F, 1.0F);
			case LOW -> MathHelper.clamp(Math.min((rpm - 600.0F) / 400.0F, (1900.0F - rpm) / 600.0F), 0.0F, 1.0F);
			default -> MathHelper.clamp((rpm - 1400.0F) / 600.0F, 0.0F, 1.0F);
		};
		float level = 0.6F + 0.35F * MathHelper.clamp((rpm - IDLE_RPM) / 1700.0F, 0.0F, 1.0F)
				+ (car.getThrottle() > 0 ? 0.08F : 0.0F);
		// Equal power across a crossfade.
		return (float) Math.sqrt(weight) * level;
	}

	/** A layer's pitch at these revs: how far they are from the revs it was made at. */
	public static float pitchAt(Layer layer, float rpm, ChittyEntity car) {
		if (layer == Layer.FLIGHT) {
			return 0.85F + 0.3F * (float) Math.min(1.0, car.getSpeed() / 1.3);
		}
		if (layer == Layer.WIND) {
			return 0.8F + 0.45F * (float) Math.min(1.0, car.getAirSpeed() / 1.6);
		}
		float made = layer == Layer.IDLE ? IDLE_RPM : layer == Layer.LOW ? LOW_RPM : HIGH_RPM;
		return MathHelper.clamp(rpm / made, 0.5F, 2.0F);
	}
}
