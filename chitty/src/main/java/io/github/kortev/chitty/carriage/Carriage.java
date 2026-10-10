package io.github.kortev.chitty.carriage;

import io.github.kortev.shootingstar.ShootingStar;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/**
 * The Child Catcher's carriage: the carriage and its hitboxes, the item that puts it down, its sounds and the driver's
 * messages; and the rules for whoever is in its cage. Set up from Chitty's initializer.
 */
public final class Carriage {
	public static final EntityType<CarriageEntity> ENTITY = Registry.register(Registries.ENTITY_TYPE, ShootingStar.id("carriage"),
			EntityType.Builder.<CarriageEntity>create(CarriageEntity::new, SpawnGroup.MISC)
					.dimensions(1.9F, 3.4F)
					.maxTrackingRange(10)
					.trackingTickInterval(1)
					.build("carriage"));
	public static final EntityType<CarriagePartEntity> PART = Registry.register(Registries.ENTITY_TYPE, ShootingStar.id("carriage_part"),
			EntityType.Builder.<CarriagePartEntity>create(CarriagePartEntity::new, SpawnGroup.MISC)
					.dimensions(1.6F, 3.4F)
					.disableSaving()
					.disableSummon()
					.makeFireImmune()
					.maxTrackingRange(10)
					.trackingTickInterval(1)
					.build("carriage_part"));

	public static final Item ITEM = Registry.register(Registries.ITEM, ShootingStar.id("carriage"),
			new CarriageItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

	public static final SoundEvent ROLL = sound("carriage.roll");
	public static final SoundEvent WHIP = sound("carriage.whip");
	public static final SoundEvent DISGUISE_ON = sound("carriage.disguise_on");
	public static final SoundEvent DISGUISE_OFF = sound("carriage.disguise_off");

	private static SoundEvent sound(String name) {
		Identifier id = ShootingStar.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	private Carriage() {
	}

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ITEM));
		PayloadTypeRegistry.playC2S().register(CarriageActionPayload.ID, CarriageActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CarriageActionPayload.ID, (payload, context) -> {
			if (context.player().getVehicle() instanceof CarriageEntity carriage) {
				carriage.act(context.player(), payload.action());
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// Nobody in her cage is smothered in what she drives under.
			if (source.isOf(DamageTypes.IN_WALL) && entity.getVehicle() instanceof CarriageEntity) {
				return false;
			}
			// Nobody in her cage, door shut, can hurt anyone outside it through the bars (her driver up on the box
			// included): only each other.
			Entity attacker = source.getAttacker();
			return !(attacker != null && attacker.getVehicle() instanceof CarriageEntity carriage && carriage.inCage(attacker)
					&& !carriage.isDoorOpen() && !(entity.getVehicle() == carriage && carriage.inCage(entity)));
		});
		// Hitting something that stands at a carriage's open door shoves it in.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient || player.isSpectator() || !(entity instanceof LivingEntity target)) {
				return ActionResult.PASS;
			}
			CarriageEntity carriage = CarriageEntity.doorNear(target);
			return carriage != null && carriage.shove(player, target) ? ActionResult.SUCCESS : ActionResult.PASS;
		});
	}
}
