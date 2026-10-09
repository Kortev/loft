package io.github.kortev.chitty.airship;

import io.github.kortev.chitty.ChittyControls;
import io.github.kortev.shootingstar.ShootingStar;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/**
 * Baron Bomburst's Vulgarian airship: the airship and its hitboxes, the holder on its grapple, its bombs, the items
 * that put it down and load its rack, its sounds and the crew's messages. Set up from Chitty's initializer.
 */
public final class Airship {
	public static final EntityType<AirshipEntity> ENTITY = Registry.register(Registries.ENTITY_TYPE, ShootingStar.id("airship"),
			EntityType.Builder.<AirshipEntity>create(AirshipEntity::new, SpawnGroup.MISC)
					.dimensions(2.0F, 1.5F)
					.maxTrackingRange(16)
					.trackingTickInterval(1)
					.build("airship"));
	public static final EntityType<AirshipPartEntity> PART = Registry.register(Registries.ENTITY_TYPE, ShootingStar.id("airship_part"),
			EntityType.Builder.<AirshipPartEntity>create(AirshipPartEntity::new, SpawnGroup.MISC)
					.dimensions(2.0F, 2.0F)
					.disableSaving()
					.disableSummon()
					.makeFireImmune()
					.maxTrackingRange(16)
					.trackingTickInterval(1)
					.build("airship_part"));
	public static final EntityType<AirshipHookEntity> HOOK = Registry.register(Registries.ENTITY_TYPE, ShootingStar.id("airship_hook"),
			EntityType.Builder.<AirshipHookEntity>create(AirshipHookEntity::new, SpawnGroup.MISC)
					.dimensions(0.8F, 1.2F)
					.disableSaving()
					.disableSummon()
					.makeFireImmune()
					.maxTrackingRange(16)
					.trackingTickInterval(1)
					.build("airship_hook"));
	public static final EntityType<AirshipBombEntity> BOMB_ENTITY = Registry.register(Registries.ENTITY_TYPE,
			ShootingStar.id("airship_bomb"),
			EntityType.Builder.<AirshipBombEntity>create(AirshipBombEntity::new, SpawnGroup.MISC)
					.dimensions(0.35F, 0.55F)
					.disableSummon()
					.maxTrackingRange(16)
					.trackingTickInterval(1)
					.build("airship_bomb"));

	public static final Item ITEM = Registry.register(Registries.ITEM, ShootingStar.id("airship"),
			new AirshipItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));
	public static final Item BOMB = Registry.register(Registries.ITEM, ShootingStar.id("airship_bomb"),
			new Item(new Item.Settings().maxCount(16)));

	public static final SoundEvent ENGINE = sound("airship.engine");
	public static final SoundEvent WIND = sound("airship.wind");
	public static final SoundEvent CREAK = sound("airship.creak");
	public static final SoundEvent WINCH = sound("airship.winch");
	public static final SoundEvent GRAB = sound("airship.grab");
	public static final SoundEvent LADDER = sound("airship.ladder");
	public static final SoundEvent BOMB_DROP = sound("airship.bomb_drop");
	public static final SoundEvent LOAD = sound("airship.load");

	private static SoundEvent sound(String name) {
		Identifier id = ShootingStar.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	private Airship() {
	}

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(ITEM);
			entries.add(BOMB);
		});
		PayloadTypeRegistry.playC2S().register(AirshipInputPayload.ID, AirshipInputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AirshipActionPayload.ID, AirshipActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AirshipWalkPayload.ID, AirshipWalkPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AirshipInputPayload.ID, (payload, context) -> {
			if (context.player().getVehicle() instanceof AirshipEntity ship) {
				ship.applyInput(context.player(), ChittyControls.unpack(payload.controls()));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(AirshipActionPayload.ID, (payload, context) -> {
			if (context.player().getVehicle() instanceof AirshipEntity ship) {
				ship.act(context.player(), payload.action());
			} else if (payload.action() == AirshipEntity.ACTION_THROW) {
				// On the ground with an airship's grapple in hand, the grapple key throws it.
				AirshipEntity held = AirshipEntity.grappleHeldBy(context.player());
				if (held != null) {
					held.throwGrapple(context.player());
				}
			}
		});
		// Holding an airship's grapple on the ground, using it on something hooks it on (and nothing else happens).
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			AirshipEntity ship = AirshipEntity.grappleHeldBy(player);
			if (ship == null || entity instanceof AirshipHookEntity) {
				return ActionResult.PASS;
			}
			if (world.isClient) {
				return ActionResult.SUCCESS;
			}
			return ship.hookOnto(player, entity) ? ActionResult.SUCCESS : ActionResult.FAIL;
		});
		ServerPlayNetworking.registerGlobalReceiver(AirshipWalkPayload.ID, (payload, context) -> {
			if (context.player().getVehicle() instanceof AirshipEntity ship && Float.isFinite(payload.x())
					&& Float.isFinite(payload.z())) {
				ship.walk(context.player(), payload.x(), payload.z());
			}
		});
	}
}
