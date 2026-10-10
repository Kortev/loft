package io.github.kortev.chitty;

import io.github.kortev.chitty.airship.Airship;
import io.github.kortev.chitty.carriage.Carriage;
import io.github.kortev.shootingstar.ShootingStar;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chitty Chitty Bang Bang: the car, the item that puts her down, her sounds and the driver's messages; and the film's
 * other vehicles, each set up from here (the Vulgarian airship: {@link Airship}; the Child Catcher's carriage:
 * {@link Carriage}).
 *
 * <p>She is a mod of her own, in her own jar, built beside The Shooting Star and needing it: her things keep the
 * {@code shootingstar} names they were made with (so cars already put down and items already made survive the move),
 * which also puts them under its rule that only kortev crafts them, and her advancements use its event trigger.
 */
public final class Chitty implements ModInitializer {
	public static final String MOD_ID = "chitty";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final EntityType<ChittyEntity> ENTITY = Registry.register(Registries.ENTITY_TYPE, ShootingStar.id("chitty"),
			EntityType.Builder.<ChittyEntity>create(ChittyEntity::new, SpawnGroup.MISC)
					.dimensions(2.0F, 1.6F)
					// Her paint may blister, but she does not burn up in a fire or in lava (nor does her item).
					.makeFireImmune()
					.maxTrackingRange(10)
					.trackingTickInterval(1)
					.build("chitty"));
	/** The hitboxes she keeps along her length, beyond her own (ChittyPartEntity), each sizing itself. */
	public static final EntityType<ChittyPartEntity> PART = Registry.register(Registries.ENTITY_TYPE,
			ShootingStar.id("chitty_part"),
			EntityType.Builder.<ChittyPartEntity>create(ChittyPartEntity::new, SpawnGroup.MISC)
					.dimensions(1.6F, 1.6F)
					.disableSaving()
					.disableSummon()
					.makeFireImmune()
					.maxTrackingRange(10)
					.trackingTickInterval(1)
					.build("chitty_part"));

	public static final Item ITEM = Registry.register(Registries.ITEM, ShootingStar.id("chitty"),
			new ChittyItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE).fireproof()));

	public static final SoundEvent ENGINE_IDLE = sound("chitty.engine_idle");
	public static final SoundEvent ENGINE_LOW = sound("chitty.engine_low");
	public static final SoundEvent ENGINE_HIGH = sound("chitty.engine_high");
	public static final SoundEvent FLIGHT = sound("chitty.flight");
	public static final SoundEvent WIND = sound("chitty.wind");
	public static final SoundEvent START = sound("chitty.start");
	public static final SoundEvent START_FAIL = sound("chitty.start_fail");
	public static final SoundEvent SKID = sound("chitty.skid");
	public static final SoundEvent BANG = sound("chitty.bang");
	public static final SoundEvent HORN = sound("chitty.horn");
	public static final SoundEvent WINGS_OUT = sound("chitty.wings_out");
	public static final SoundEvent WINGS_IN = sound("chitty.wings_in");
	public static final SoundEvent FLOATS = sound("chitty.floats");
	public static final SoundEvent FLOATS_DOWN = sound("chitty.floats_down");
	public static final SoundEvent EJECT = sound("chitty.eject");
	public static final SoundEvent CRASH = sound("chitty.crash");

	/** The shimmer of hot air over her bonnet and the end of her pipe, once she has run a while. */
	public static final SimpleParticleType HEAT = Registry.register(Registries.PARTICLE_TYPE, ShootingStar.id("chitty_heat"),
			FabricParticleTypes.simple());

	private static SoundEvent sound(String name) {
		Identifier id = ShootingStar.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	@Override
	public void onInitialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ITEM));
		Airship.init();
		Carriage.init();
		SoftLanding.init();
		Reboard.init();
		PayloadTypeRegistry.playC2S().register(ChittyInputPayload.ID, ChittyInputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ChittyHornPayload.ID, ChittyHornPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ChittyEjectPayload.ID, ChittyEjectPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ChittyInputPayload.ID, (payload, context) -> {
			if (context.player().getVehicle() instanceof ChittyEntity car) {
				car.applyInput(context.player(), ChittyControls.unpack(payload.controls()), payload.state());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ChittyEjectPayload.ID, (payload, context) -> {
			if (context.player().getVehicle() instanceof ChittyEntity car && car.getControllingPassenger() == context.player()) {
				car.ejectBackSeat();
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ChittyHornPayload.ID, (payload, context) -> {
			if (context.player().getVehicle() instanceof ChittyEntity car) {
				car.honk(context.player());
			}
		});
	}
}
