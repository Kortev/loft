package io.github.kortev.shootingstar.registry;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.block.MoltenCrustBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
	/** Hull plating of the SS-03 round. What is left standing after a strike is mostly this. */
	public static final Block GUNGNIR_HULL = register("gungnir_hull", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.BLACK)
			.instrument(NoteBlockInstrument.BASEDRUM)
			.requiresTool()
			.strength(50.0F, 1200.0F)
			.sounds(BlockSoundGroup.NETHERITE)), true);

	/** The glowing coil bands that ring the round. */
	public static final Block GUNGNIR_COIL = register("gungnir_coil", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.ORANGE)
			.instrument(NoteBlockInstrument.BASEDRUM)
			.requiresTool()
			.strength(50.0F, 1200.0F)
			.luminance(state -> 12)
			.emissiveLighting((state, world, pos) -> true)
			.sounds(BlockSoundGroup.NETHERITE)), true);

	/** Ground planed by the impact. Cools through three heat stages into fused crust. */
	public static final Block MOLTEN_CRUST = register("molten_crust", new MoltenCrustBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.ORANGE)
			.instrument(NoteBlockInstrument.BASEDRUM)
			.requiresTool()
			.strength(2.0F, 6.0F)
			.ticksRandomly()
			.luminance(MoltenCrustBlock::luminance)
			.emissiveLighting((state, world, pos) -> true)
			.allowsSpawning((state, world, pos, type) -> type.isFireImmune())
			.sounds(BlockSoundGroup.BASALT)), false);

	public static final Block FUSED_CRUST = register("fused_crust", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.BLACK)
			.instrument(NoteBlockInstrument.BASEDRUM)
			.requiresTool()
			.strength(2.5F, 6.0F)
			.luminance(state -> 3)
			.sounds(BlockSoundGroup.BASALT)), true);

	private ModBlocks() {
	}

	private static Block register(String name, Block block, boolean withItem) {
		Registry.register(Registries.BLOCK, ShootingStar.id(name), block);
		if (withItem) {
			Registry.register(Registries.ITEM, ShootingStar.id(name), new BlockItem(block, new Item.Settings()));
		}
		return block;
	}

	public static void init() {
	}
}
