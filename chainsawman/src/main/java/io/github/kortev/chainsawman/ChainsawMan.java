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
import io.github.kortev.shootingstar.OwnerOnly;
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
 * kortev's own powers on the SMP, beside The Shooting Star: Pochita, carried, and the cord on the chest that turns
 * whoever carries him into Chainsaw Man ({@link Form}): the saws to fight with ({@link Saw}), the chain ({@link Grapple}),
 * Beam the shark, the ult ({@link Beam}; the blood frenzy, {@link Frenzy}, is kept dormant), and never dying while Pochita is with you ({@link io.github.kortev.chainsawman.form.Downed}). Its things are kortev's alone to craft, under The Shooting
 * Star's owner-only rule.
 */
public final class ChainsawMan implements ModInitializer {
	public static final String MOD_ID = "chainsawman";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** Whose these powers are. */
	public static final String OWNER = "kortev";

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
	public static final Item POCHITA_SPAWN_EGG = Registry.register(Registries.ITEM, id("pochita_spawn_egg"),
			new SpawnEggItem(POCHITA_ENTITY, 0xEC8026, 0x9A9EA6, new Item.Settings()));

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		OwnerOnly.register(MOD_ID, () -> OWNER);
		ChainsawSounds.init();
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(POCHITA));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(POCHITA_SPAWN_EGG));
		FabricDefaultAttributeRegistry.register(POCHITA_ENTITY, PochitaEntity.createAttributes());
		SpawnRestriction.register(POCHITA_ENTITY, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				AnimalEntity::isValidNaturalSpawn);
		// One in a world, waiting where he was put.
		PochitaPlace.init();
		ChainsawCommand.init();
		PayloadTypeRegistry.playS2C().register(FormPayload.ID, FormPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FormPullPayload.ID, FormPullPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FormEventPayload.ID, FormEventPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PullCordPayload.ID, PullCordPayload.CODEC);
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
		Felling.init();
	}
}
