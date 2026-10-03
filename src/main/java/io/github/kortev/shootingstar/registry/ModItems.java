package io.github.kortev.shootingstar.registry;

import io.github.kortev.shootingstar.ShootingStar;
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

	private ModItems() {
	}

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(GUNGNIR_UPLINK));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
			entries.add(ModBlocks.GUNGNIR_HULL);
			entries.add(ModBlocks.GUNGNIR_COIL);
			entries.add(ModBlocks.FUSED_CRUST);
		});
	}
}
