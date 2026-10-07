package io.github.kortev.shootingstar.chitty;

import io.github.kortev.shootingstar.ShootingStar;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/** Chitty Chitty Bang Bang: the car, the item that puts her down, her sounds and the driver's messages. */
public final class Chitty {
	public static final EntityType<ChittyEntity> ENTITY = Registry.register(Registries.ENTITY_TYPE, ShootingStar.id("chitty"),
			EntityType.Builder.<ChittyEntity>create(ChittyEntity::new, SpawnGroup.MISC)
					.dimensions(2.0F, 1.6F)
					.maxTrackingRange(10)
					.trackingTickInterval(1)
					.build("chitty"));
	/** The hamper's own hitbox, which she keeps on her stern (ChittyHamperEntity). */
	public static final EntityType<ChittyHamperEntity> HAMPER = Registry.register(Registries.ENTITY_TYPE,
			ShootingStar.id("chitty_hamper"),
			EntityType.Builder.<ChittyHamperEntity>create(ChittyHamperEntity::new, SpawnGroup.MISC)
					.dimensions(0.8F, 0.5F)
					.disableSaving()
					.disableSummon()
					.makeFireImmune()
					.maxTrackingRange(10)
					.trackingTickInterval(1)
					.build("chitty_hamper"));

	public static final Item ITEM = Registry.register(Registries.ITEM, ShootingStar.id("chitty"),
			new ChittyItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	public static final SoundEvent ENGINE_IDLE = sound("chitty.engine_idle");
	public static final SoundEvent ENGINE_LOW = sound("chitty.engine_low");
	public static final SoundEvent ENGINE_HIGH = sound("chitty.engine_high");
	public static final SoundEvent FLIGHT = sound("chitty.flight");
	public static final SoundEvent START = sound("chitty.start");
	public static final SoundEvent BANG = sound("chitty.bang");
	public static final SoundEvent HORN = sound("chitty.horn");
	public static final SoundEvent WINGS_OUT = sound("chitty.wings_out");
	public static final SoundEvent WINGS_IN = sound("chitty.wings_in");
	public static final SoundEvent FLOATS = sound("chitty.floats");
	public static final SoundEvent FLOATS_DOWN = sound("chitty.floats_down");
	public static final SoundEvent EJECT = sound("chitty.eject");
	public static final SoundEvent CRASH = sound("chitty.crash");

	private Chitty() {
	}

	private static SoundEvent sound(String name) {
		Identifier id = ShootingStar.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ITEM));
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
