package io.github.kortev.chainsawman.devil;

import io.github.kortev.chainsawman.ChainsawMan;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.world.Heightmap;

/**
 * The wild devils of the world (the {@code devil/} lane): what they are, what they drop and how they sound. For now the Tomato Devil, the
 * weak first devil: a big red tomato covered in eyes walking on eight human arms ({@link TomatoDevilEntity}), the seeds it bursts into
 * when killed ({@link TomatoSeedEntity}: burn them, or it comes back), and the seeds it spits ({@link TomatoSpitEntity}).
 */
public final class Devils {
	public static final EntityType<TomatoDevilEntity> TOMATO_DEVIL = Registry.register(Registries.ENTITY_TYPE, ChainsawMan.id("tomato_devil"),
			EntityType.Builder.create(TomatoDevilEntity::new, SpawnGroup.MONSTER).dimensions(TomatoDevilEntity.WIDTH, TomatoDevilEntity.HEIGHT)
					.eyeHeight(2.5F).maxTrackingRange(10).trackingTickInterval(2).build("tomato_devil"));
	public static final EntityType<TomatoSeedEntity> TOMATO_SEED = Registry.register(Registries.ENTITY_TYPE, ChainsawMan.id("tomato_seed"),
			EntityType.Builder.<TomatoSeedEntity>create(TomatoSeedEntity::new, SpawnGroup.MISC).dimensions(0.6F, 0.4F).maxTrackingRange(8)
					.trackingTickInterval(2).build("tomato_seed"));
	public static final EntityType<TomatoSpitEntity> TOMATO_SPIT = Registry.register(Registries.ENTITY_TYPE, ChainsawMan.id("tomato_spit"),
			EntityType.Builder.<TomatoSpitEntity>create(TomatoSpitEntity::new, SpawnGroup.MISC).dimensions(0.3F, 0.3F).maxTrackingRange(8)
					.trackingTickInterval(1).disableSaving().disableSummon().build("tomato_spit"));

	/** What is left of a Tomato Devil after it bursts: its body, worth something to someone. */
	public static final Item TOMATO_CARCASS = Registry.register(Registries.ITEM, ChainsawMan.id("tomato_carcass"),
			new Item(new Item.Settings().rarity(Rarity.UNCOMMON).maxCount(16)));
	public static final Item TOMATO_DEVIL_SPAWN_EGG = Registry.register(Registries.ITEM, ChainsawMan.id("tomato_devil_spawn_egg"),
			new SpawnEggItem(TOMATO_DEVIL, 0xC2231A, 0xC9A690, new Item.Settings()));

	// ---- The Leech Devil (LeechDevilEntity): the devil, the arm or tongue a blade can catch, the sucker it leaves, its spawn egg.
	public static final EntityType<LeechDevilEntity> LEECH_DEVIL = Registry.register(Registries.ENTITY_TYPE, ChainsawMan.id("leech_devil"),
			EntityType.Builder.create(LeechDevilEntity::new, SpawnGroup.MONSTER).dimensions(LeechDevilEntity.WIDTH, LeechDevilEntity.HEIGHT)
					.eyeHeight(1.6F).maxTrackingRange(10).trackingTickInterval(2).build("leech_devil"));
	public static final EntityType<LeechPartEntity> LEECH_PART = Registry.register(Registries.ENTITY_TYPE, ChainsawMan.id("leech_part"),
			EntityType.Builder.<LeechPartEntity>create(LeechPartEntity::new, SpawnGroup.MISC).dimensions(1.3F, 1.3F).maxTrackingRange(8)
					.trackingTickInterval(1).disableSaving().disableSummon().build("leech_part"));
	/** A Leech Devil's sucker mouth: a devil's part, worth something to someone. */
	public static final Item LEECH_SUCKER = Registry.register(Registries.ITEM, ChainsawMan.id("leech_sucker"),
			new Item(new Item.Settings().rarity(Rarity.UNCOMMON).maxCount(16)));
	public static final Item LEECH_DEVIL_SPAWN_EGG = Registry.register(Registries.ITEM, ChainsawMan.id("leech_devil_spawn_egg"),
			new SpawnEggItem(LEECH_DEVIL, 0x1D2115, 0x9A846E, new Item.Settings()));

	/** The Tomato's sounds, synthesised by {@code tools/gen_chainsaw_sounds.py}. */
	public static final class Sounds {
		/** One hand coming down: a wet slap of a palm on the ground. */
		public static final SoundEvent STEP = register("tomato.step");
		/** A gurgle deep in it: what it sounds like about, and as its mouth opens. */
		public static final SoundEvent GURGLE = register("tomato.gurgle");
		/** The slap landing: four palms slamming down, a wet crack. */
		public static final SoundEvent SLAP = register("tomato.slap");
		/** Tucking in and rocking back: a strained creak of skin and a breath in. */
		public static final SoundEvent TUCK = register("tomato.tuck");
		/** Rolling: a heavy wet rumble going over the ground. */
		public static final SoundEvent ROLL = register("tomato.roll");
		/** Into a wall: a big soft thud and a squelch. */
		public static final SoundEvent THUD = register("tomato.thud");
		/** The seeds spat: a wet hawk and pops. */
		public static final SoundEvent SPIT = register("tomato.spit");
		/** Cut: a wet squelch and a juicy spray. */
		public static final SoundEvent HURT = register("tomato.hurt");
		/** Bursting: a deep pop, juice and pulp everywhere. */
		public static final SoundEvent BURST = register("tomato.burst");
		/** A seed burning: a sharp hiss and crackle. */
		public static final SoundEvent HISS = register("tomato.hiss");
		/** A seed swelling: a slow wet creaking and bubbling. */
		public static final SoundEvent SWELL = register("tomato.swell");
		/** Its breathing, quiet and wet, through parted lips (what it sounds like about). */
		public static final SoundEvent BREATH = register("tomato.breath");
		/** A soft click of its teeth. */
		public static final SoundEvent CLICK = register("tomato.click");
		/** A seed catching fire: a thin shriek as it chars. */
		public static final SoundEvent SHRIEK = register("tomato.shriek");
		/** A blade or a fist into a seed: a squelch, no more. */
		public static final SoundEvent SQUELCH = register("tomato.squelch");
		/** Pochita chewing up a seed: teeth whirring, a wet crunch. */
		public static final SoundEvent CHEW = register("pochita.chew");
		/** Pochita smelling a devil: two short barks with an engine's rasp in them. */
		public static final SoundEvent BARK = register("pochita.bark");

		private Sounds() {
		}

		private static SoundEvent register(String name) {
			Identifier id = ChainsawMan.id(name);
			return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
		}

		static void init() {
		}
	}

	private Devils() {
	}

	/** Called once from the mod's init. */
	public static void init() {
		Sounds.init();
		FabricDefaultAttributeRegistry.register(TOMATO_DEVIL, TomatoDevilEntity.createAttributes());
		SpawnRestriction.register(TOMATO_DEVIL, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, TomatoDevilEntity::canSpawn);
		// Rare: a monster's chance out of a whole night's (zombies are 95 of about 515), then the night, the sky and the distance rule.
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, TOMATO_DEVIL, 2, 1, 1);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(TOMATO_DEVIL_SPAWN_EGG));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(TOMATO_CARCASS));
		Sniff.init();
		// The Leech Devil: no spawning in the world yet (only /summon, its egg and the film).
		LeechSounds.init();
		FabricDefaultAttributeRegistry.register(LEECH_DEVIL, LeechDevilEntity.createAttributes());
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(LEECH_DEVIL_SPAWN_EGG));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(LEECH_SUCKER));
	}
}
