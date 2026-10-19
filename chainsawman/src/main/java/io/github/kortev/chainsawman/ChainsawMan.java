package io.github.kortev.chainsawman;

import io.github.kortev.shootingstar.OwnerOnly;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * kortev's own powers on the SMP, beside The Shooting Star: Pochita, carried, and the cord on the chest that turns
 * whoever carries him into Chainsaw Man. Its things are kortev's alone to craft, under The Shooting Star's owner-only
 * rule.
 */
public final class ChainsawMan implements ModInitializer {
	public static final String MOD_ID = "chainsawman";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** Whose these powers are. */
	public static final String OWNER = "kortev";

	/** Pochita: carried anywhere in the inventory, he is the heart the cord starts. */
	public static final Item POCHITA = Registry.register(Registries.ITEM, id("pochita"),
			new PochitaItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		OwnerOnly.register(MOD_ID, () -> OWNER);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(POCHITA));
	}
}
