package io.github.kortev.chainsawman;

import io.github.kortev.chainsawman.form.Beam;
import io.github.kortev.chainsawman.form.Felling;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Frenzy;
import io.github.kortev.chainsawman.form.Grapple;
import io.github.kortev.chainsawman.form.Saw;
import io.github.kortev.chainsawman.network.FormActionPayload;
import io.github.kortev.chainsawman.network.FormEventPayload;
import io.github.kortev.chainsawman.network.FormPayload;
import io.github.kortev.chainsawman.network.FormPullPayload;
import io.github.kortev.chainsawman.network.PullCordPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.world.Heightmap;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chainsaw Man, needing only Fabric API: Pochita, carried, and the cord on the chest that turns
 * whoever carries him into Chainsaw Man ({@link Form}): the saws to fight with ({@link Saw}), the chain ({@link Grapple}),
 * Beam the shark, the ult ({@link Beam}; the blood frenzy, {@link Frenzy}, is kept dormant), and never dying while Pochita is with you ({@link io.github.kortev.chainsawman.form.Downed}).
 */
public final class ChainsawMan implements ModInitializer {
	public static final String MOD_ID = "chainsawman";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Pochita picked up: a chainsaw in your hands, and carried when you die, your heart. */
	public static final Item POCHITA = Registry.register(Registries.ITEM, id("pochita"),
			new PochitaItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof().component(net.minecraft.component.DataComponentTypes.TOOL, PochitaItem.tool())));
	/** Pochita himself, the animal: one in a world, found fainted, tamed with your blood. */
	public static final EntityType<PochitaEntity> POCHITA_ENTITY = Registry.register(Registries.ENTITY_TYPE, id("pochita"),
			EntityType.Builder.create(PochitaEntity::new, SpawnGroup.CREATURE).dimensions(0.6F, 0.7F).maxTrackingRange(10).build("pochita"));
	/** Beam, the shark fiend: summoned by the ult, never found or saved. */
	public static final EntityType<BeamEntity> BEAM_ENTITY = Registry.register(Registries.ENTITY_TYPE, id("beam"),
			EntityType.Builder.<BeamEntity>create(BeamEntity::new, SpawnGroup.MISC).dimensions(BeamEntity.WIDTH, BeamEntity.HEIGHT).maxTrackingRange(10)
					.trackingTickInterval(1).disableSummon().disableSaving().makeFireImmune().build("beam"));
	/** The Eternity Devil's hold on a locked target (Forever, the Eternity contract's power) and one moment of that target: never saved or summoned. */
	public static final EntityType<io.github.kortev.chainsawman.contract.ForeverHubEntity> FOREVER_HUB = Registry.register(Registries.ENTITY_TYPE, id("forever_hub"),
			EntityType.Builder.<io.github.kortev.chainsawman.contract.ForeverHubEntity>create(io.github.kortev.chainsawman.contract.ForeverHubEntity::new, SpawnGroup.MISC)
					.dimensions(0.4F, 0.4F).maxTrackingRange(8).trackingTickInterval(20).alwaysUpdateVelocity(false).disableSummon().disableSaving().makeFireImmune().build("forever_hub"));
	public static final EntityType<io.github.kortev.chainsawman.contract.ForeverCubeEntity> FOREVER_CUBE = Registry.register(Registries.ENTITY_TYPE, id("forever_cube"),
			EntityType.Builder.<io.github.kortev.chainsawman.contract.ForeverCubeEntity>create(io.github.kortev.chainsawman.contract.ForeverCubeEntity::new, SpawnGroup.MISC)
					.dimensions(0.4F, 0.4F).maxTrackingRange(10).trackingTickInterval(20).alwaysUpdateVelocity(false).disableSummon().disableSaving().makeFireImmune().build("forever_cube"));
	public static final EntityType<io.github.kortev.chainsawman.contract.ForeverCopyEntity> FOREVER_COPY = Registry.register(Registries.ENTITY_TYPE, id("forever_copy"),
			EntityType.Builder.<io.github.kortev.chainsawman.contract.ForeverCopyEntity>create(io.github.kortev.chainsawman.contract.ForeverCopyEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F).maxTrackingRange(8).trackingTickInterval(20).alwaysUpdateVelocity(false).disableSummon().disableSaving().makeFireImmune().build("forever_copy"));
	/** The Eternity Devil's home, buried in its hotel, and what it runs. */
	public static final net.minecraft.block.Block HOME_BLOCK = Registry.register(Registries.BLOCK, id("eternity_home"),
			new io.github.kortev.chainsawman.hotel.HomeBlock(io.github.kortev.chainsawman.hotel.HomeBlock.settings()));
	public static final net.minecraft.block.entity.BlockEntityType<io.github.kortev.chainsawman.hotel.HomeBlockEntity> HOME_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, id("eternity_home"),
			net.minecraft.block.entity.BlockEntityType.Builder.create(io.github.kortev.chainsawman.hotel.HomeBlockEntity::new, HOME_BLOCK).build(null));
	/** The Eternity Devil grown out of its hotel's walls (the boss), and flesh creeping across the floor from it. */
	public static final EntityType<io.github.kortev.chainsawman.hotel.EternityDevilEntity> ETERNITY_DEVIL = Registry.register(Registries.ENTITY_TYPE, id("eternity_devil"),
			EntityType.Builder.<io.github.kortev.chainsawman.hotel.EternityDevilEntity>create(io.github.kortev.chainsawman.hotel.EternityDevilEntity::new, SpawnGroup.MONSTER)
					.dimensions(io.github.kortev.chainsawman.hotel.EternityDevilEntity.WIDTH, io.github.kortev.chainsawman.hotel.EternityDevilEntity.HEIGHT)
					// (Its eyes are low in the mass, in the corridor's air: the top of its box is in the ceiling, and those looking along the corridor must be able to see it to hit it.)
					.eyeHeight(1.5F).maxTrackingRange(8)
					.trackingTickInterval(2).disableSummon().disableSaving().makeFireImmune().build("eternity_devil"));
	/** Flesh growing at the corners of the loop floor with the laps: only drawn. */
	public static final EntityType<io.github.kortev.chainsawman.hotel.FleshGrowthEntity> FLESH_GROWTH = Registry.register(Registries.ENTITY_TYPE, id("flesh_growth"),
			EntityType.Builder.<io.github.kortev.chainsawman.hotel.FleshGrowthEntity>create(io.github.kortev.chainsawman.hotel.FleshGrowthEntity::new, SpawnGroup.MISC)
					.dimensions(0.5F, 0.5F).maxTrackingRange(6).trackingTickInterval(20).alwaysUpdateVelocity(false).disableSummon().disableSaving().makeFireImmune().build("flesh_growth"));
	public static final EntityType<io.github.kortev.chainsawman.hotel.FleshPatchEntity> FLESH_PATCH = Registry.register(Registries.ENTITY_TYPE, id("flesh_patch"),
			EntityType.Builder.<io.github.kortev.chainsawman.hotel.FleshPatchEntity>create(io.github.kortev.chainsawman.hotel.FleshPatchEntity::new, SpawnGroup.MISC)
					.dimensions(2.2F, 0.4F).maxTrackingRange(8).trackingTickInterval(20).alwaysUpdateVelocity(false).disableSummon().disableSaving().makeFireImmune().build("flesh_patch"));
	/** One of the missing squad's hunters, alive in the hotel's loop. */
	public static final EntityType<io.github.kortev.chainsawman.hotel.SquadMemberEntity> SQUAD_MEMBER = Registry.register(Registries.ENTITY_TYPE, id("squad_member"),
			EntityType.Builder.<io.github.kortev.chainsawman.hotel.SquadMemberEntity>create(io.github.kortev.chainsawman.hotel.SquadMemberEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F).maxTrackingRange(10).disableSummon().disableSaving().build("squad_member"));
	/** What the Eternity Devil drops: its flesh (a lot of hunger, and sickness) and its blood (a bottle: refills Chainsaw Man's blood bar). */
	public static final Item ETERNITY_FLESH = Registry.register(Registries.ITEM, id("eternity_flesh"),
			new Item(new Item.Settings().rarity(Rarity.UNCOMMON).food(new net.minecraft.component.type.FoodComponent.Builder().nutrition(10).saturationModifier(0.8F).alwaysEdible()
					.statusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.NAUSEA, 300, 0), 1.0F).build())));
	public static final Item ETERNITY_BLOOD = Registry.register(Registries.ITEM, id("eternity_blood"),
			new io.github.kortev.chainsawman.hotel.EternityBloodItem(new Item.Settings().rarity(Rarity.UNCOMMON).maxCount(16)));
	public static final Item POCHITA_SPAWN_EGG = Registry.register(Registries.ITEM, id("pochita_spawn_egg"),
			new SpawnEggItem(POCHITA_ENTITY, 0xEC8026, 0x9A9EA6, new Item.Settings()));

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ChainsawSounds.init();
		Registry.register(Registries.STRUCTURE_TYPE, id("eternity_hotel"), io.github.kortev.chainsawman.hotel.HotelStructure.TYPE);
		Registry.register(Registries.STRUCTURE_PIECE, id("eternity_hotel"), io.github.kortev.chainsawman.hotel.HotelStructure.PIECE);
		io.github.kortev.chainsawman.hotel.HotelRules.init();
		io.github.kortev.chainsawman.form.TeamRules.init();
		io.github.kortev.chainsawman.hotel.Hotel.init();
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(POCHITA));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(POCHITA_SPAWN_EGG));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
			entries.add(ETERNITY_FLESH);
			entries.add(ETERNITY_BLOOD);
		});
		FabricDefaultAttributeRegistry.register(SQUAD_MEMBER, io.github.kortev.chainsawman.hotel.SquadMemberEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ETERNITY_DEVIL, io.github.kortev.chainsawman.hotel.EternityDevilEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(POCHITA_ENTITY, PochitaEntity.createAttributes());
		SpawnRestriction.register(POCHITA_ENTITY, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				AnimalEntity::isValidNaturalSpawn);
		// One in a world, waiting where he was put.
		Owner.init();
		PochitaPlace.init();
		PochitaItem.init();
		ChainsawCommand.init();
		PayloadTypeRegistry.playS2C().register(FormPayload.ID, FormPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FormPullPayload.ID, FormPullPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FormEventPayload.ID, FormEventPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(io.github.kortev.chainsawman.network.ForeverLockPayload.ID, io.github.kortev.chainsawman.network.ForeverLockPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(io.github.kortev.chainsawman.network.ContractPayload.ID, io.github.kortev.chainsawman.network.ContractPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(io.github.kortev.chainsawman.network.UseContractPayload.ID, io.github.kortev.chainsawman.network.UseContractPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.UseContractPayload.ID, (payload, context) -> io.github.kortev.chainsawman.contract.Forever.use(context.player()));
		PayloadTypeRegistry.playS2C().register(io.github.kortev.chainsawman.network.ForeverCatchUpPayload.ID, io.github.kortev.chainsawman.network.ForeverCatchUpPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(io.github.kortev.chainsawman.network.SwitchContractPayload.ID, io.github.kortev.chainsawman.network.SwitchContractPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.SwitchContractPayload.ID, (payload, context) -> io.github.kortev.chainsawman.contract.Contracts.cycle(context.player()));
		PayloadTypeRegistry.playS2C().register(io.github.kortev.chainsawman.network.BondPayload.ID, io.github.kortev.chainsawman.network.BondPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(io.github.kortev.chainsawman.network.NotYetPayload.ID, io.github.kortev.chainsawman.network.NotYetPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(io.github.kortev.chainsawman.network.NotYetKeyPayload.ID, io.github.kortev.chainsawman.network.NotYetKeyPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.NotYetKeyPayload.ID, (payload, context) -> io.github.kortev.chainsawman.contract.Forever.stepOut(context.player()));
		PayloadTypeRegistry.playS2C().register(io.github.kortev.chainsawman.network.HotelPayloads.Drop.ID, io.github.kortev.chainsawman.network.HotelPayloads.Drop.CODEC);
		PayloadTypeRegistry.playC2S().register(io.github.kortev.chainsawman.network.HotelPayloads.DropAnswer.ID, io.github.kortev.chainsawman.network.HotelPayloads.DropAnswer.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.HotelPayloads.DropAnswer.ID,
				(payload, context) -> io.github.kortev.chainsawman.hotel.Hotel.drop(context.player(), payload.slot()));
		PayloadTypeRegistry.playS2C().register(io.github.kortev.chainsawman.network.HotelPayloads.Fog.ID, io.github.kortev.chainsawman.network.HotelPayloads.Fog.CODEC);
		PayloadTypeRegistry.playS2C().register(io.github.kortev.chainsawman.network.HotelPayloads.Speech.ID, io.github.kortev.chainsawman.network.HotelPayloads.Speech.CODEC);
		PayloadTypeRegistry.playS2C().register(io.github.kortev.chainsawman.network.HotelPayloads.Offer.ID, io.github.kortev.chainsawman.network.HotelPayloads.Offer.CODEC);
		PayloadTypeRegistry.playS2C().register(io.github.kortev.chainsawman.network.HotelPayloads.Devil.ID, io.github.kortev.chainsawman.network.HotelPayloads.Devil.CODEC);
		PayloadTypeRegistry.playC2S().register(io.github.kortev.chainsawman.network.HotelPayloads.Answer.ID, io.github.kortev.chainsawman.network.HotelPayloads.Answer.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.HotelPayloads.Answer.ID,
				(payload, context) -> io.github.kortev.chainsawman.hotel.Hotel.answer(context.player(), payload.accept()));
		PayloadTypeRegistry.playC2S().register(PullCordPayload.ID, PullCordPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(io.github.kortev.chainsawman.network.TeamActionPayload.ID, io.github.kortev.chainsawman.network.TeamActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.TeamActionPayload.ID, (payload, context) -> {
			switch (payload.action()) {
				case io.github.kortev.chainsawman.network.TeamActionPayload.GIVE_UP -> io.github.kortev.chainsawman.form.Fallen.giveUp(context.player());
				case io.github.kortev.chainsawman.network.TeamActionPayload.STRUGGLE -> io.github.kortev.chainsawman.form.Hearts.struggle(context.player());
				default -> {
				}
			}
		});
		PayloadTypeRegistry.playC2S().register(FormActionPayload.ID, FormActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PullCordPayload.ID, (payload, context) -> Form.pullCord(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(FormActionPayload.ID, (payload, context) -> {
			switch (payload.action()) {
				case FormActionPayload.SLASH -> Saw.slash(context.player(), payload.target());
				case FormActionPayload.GRIND -> Saw.grind(context.player(), payload.on());
				case FormActionPayload.CHAIN -> {
					if (Beam.riding(context.player())) {
						// While you ride him the chain key throws you.
						Beam.launch(context.player());
					} else {
						Grapple.fire(context.player());
					}
				}
				case FormActionPayload.FRENZY -> Beam.summon(context.player());
				case FormActionPayload.LUNGE -> Saw.lunge(context.player());
				case FormActionPayload.RELEASE -> Grapple.release(context.player());
				case FormActionPayload.KICK -> Saw.kick(context.player());
				case FormActionPayload.SPRINT -> Beam.sprint(context.player(), payload.on());
				default -> {
				}
			}
		});
		Form.init();
		io.github.kortev.chainsawman.contract.Contracts.init();
		Felling.init();
	}
}
