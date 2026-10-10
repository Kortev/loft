package io.github.kortev.chitty.client;

import io.github.kortev.chitty.Chitty;
import io.github.kortev.chitty.ChittyEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;

/**
 * One of Chitty's running sounds, following her. The engine is three loops recorded at three speeds (ticking over,
 * pulling, working hard): each plays near its own revs and they crossfade as hers rise and fall, so none is stretched
 * far from how it was made. The fourth is the wing propellers and the wind, as the wings open and the faster she flies;
 * the fifth the rush of air past her, swelling with her speed through the air (flying or falling); the sixth her tyres
 * squealing as they spin or slide on paving. Her revs are the car's own (ChittyEntity.getRpm), so the needle on her
 * rev counter and her body's shaking keep time with what is heard.
 */
public class ChittySound extends MovingSoundInstance {
	/** The revs each engine loop was made at (tools/gen_chitty_sounds.py). */
	public static final float IDLE_RPM = ChittyEntity.IDLE_RPM;
	public static final float LOW_RPM = 1100.0F;
	public static final float HIGH_RPM = 2200.0F;

	public enum Layer {
		IDLE, LOW, HIGH, FLIGHT, WIND, SKID;

		SoundEvent event() {
			return switch (this) {
				case IDLE -> Chitty.ENGINE_IDLE;
				case LOW -> Chitty.ENGINE_LOW;
				case HIGH -> Chitty.ENGINE_HIGH;
				case FLIGHT -> Chitty.FLIGHT;
				case WIND -> Chitty.WIND;
				case SKID -> Chitty.SKID;
			};
		}
	}

	private final ChittyEntity car;
	private final Layer layer;

	public ChittySound(ChittyEntity car, Layer layer) {
		super(layer.event(), SoundCategory.NEUTRAL, SoundInstance.createRandom());
		this.car = car;
		this.layer = layer;
		this.repeat = true;
		this.repeatDelay = 0;
		this.volume = 0.0F;
		this.pitch = pitchAt(layer, car.getRpm(), car);
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
		float rpm = car.getRpm();
		volume = MathHelper.lerp(0.3F, volume, volumeAt(layer, rpm, car));
		pitch = pitchAt(layer, rpm, car);
	}

	/** How loud a layer wants to be at these revs (the sound eases towards it). */
	public static float volumeAt(Layer layer, float rpm, ChittyEntity car) {
		if (layer == Layer.FLIGHT) {
			double speed = car.getSpeed();
			return car.getWingOpen(1.0F) * (0.3F + 0.7F * (float) Math.min(1.0, speed / 1.1));
		}
		if (layer == Layer.SKID) {
			return car.getSqueal();
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
				+ (car.getThrottle() > 0 || car.isRevving() ? 0.1F : 0.0F);
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
		if (layer == Layer.SKID) {
			return 0.9F + 0.2F * car.getSqueal();
		}
		float made = layer == Layer.IDLE ? IDLE_RPM : layer == Layer.LOW ? LOW_RPM : HIGH_RPM;
		return MathHelper.clamp(rpm / made, 0.5F, 2.0F);
	}
}
