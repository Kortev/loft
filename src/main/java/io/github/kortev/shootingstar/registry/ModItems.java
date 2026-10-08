package io.github.kortev.shootingstar.registry;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.item.GenesisKeyItem;
import io.github.kortev.shootingstar.item.MjolnirItem;
import io.github.kortev.shootingstar.item.UplinkItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
	public static final Item GUNGNIR_UPLINK = Registry.register(Registries.ITEM, ShootingStar.id("gungnir_uplink"),
			new UplinkItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));
	public static final Item GENESIS_KEY = Registry.register(Registries.ITEM, ShootingStar.id("genesis_key"),
			new GenesisKeyItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));
	public static final Item MJOLNIR = Registry.register(Registries.ITEM, ShootingStar.id("mjolnir"),
			new MjolnirItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

	private ModItems() {
	}

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(GUNGNIR_UPLINK);
			entries.add(GENESIS_KEY);
			entries.add(MJOLNIR);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
			entries.add(ModBlocks.GUNGNIR_HULL);
			entries.add(ModBlocks.GUNGNIR_COIL);
			entries.add(ModBlocks.FUSED_CRUST);
			entries.add(ModBlocks.FULGURITE);
			entries.add(ModBlocks.CHARRED_LOG);
			entries.add(ModBlocks.MIRROR_GRASS);
			entries.add(ModBlocks.MIRROR_STONE);
			entries.add(ModBlocks.MIRROR_LOG);
			entries.add(ModBlocks.MIRROR_LEAVES);
		});
	}
}
