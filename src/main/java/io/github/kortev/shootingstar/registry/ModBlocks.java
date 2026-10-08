package io.github.kortev.shootingstar.registry;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.block.ChargedFulguriteBlock;
import io.github.kortev.shootingstar.block.MoltenCrustBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.PillarBlock;
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

	/**
	 * Petrified lightning: ground and air Mjölnir's bolt fused into dark glass. The scar's channels are lined with it,
	 * and the bolt itself is left standing in it.
	 */
	public static final Block FULGURITE = register("fulgurite", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.BLACK)
			.instrument(NoteBlockInstrument.HAT)
			.requiresTool()
			.strength(2.0F, 6.0F)
			.luminance(state -> 2)
			.sounds(BlockSoundGroup.GLASS)), true);

	/** Fulgurite still holding the bolt's charge. Bleeds it away through three stages into plain fulgurite. */
	public static final Block CHARGED_FULGURITE = register("charged_fulgurite", new ChargedFulguriteBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.LIGHT_BLUE)
			.instrument(NoteBlockInstrument.HAT)
			.requiresTool()
			.strength(2.0F, 6.0F)
			.ticksRandomly()
			.luminance(ChargedFulguriteBlock::luminance)
			.emissiveLighting((state, world, pos) -> true)
			.sounds(BlockSoundGroup.GLASS)), false);

	/** What is left of a tree Mjölnir's stroke passed through: a black trunk, burned through, that will not burn again. */
	public static final Block CHARRED_LOG = register("charred_log", new PillarBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.BLACK)
			.instrument(NoteBlockInstrument.BASS)
			.strength(1.5F)
			.sounds(BlockSoundGroup.WOOD)), true);

	// Matter from the mirror universe, left behind where blocks traded places with their twins.
	public static final Block MIRROR_GRASS = register("mirror_grass", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.DIAMOND_BLUE)
			.strength(0.6F)
			.luminance(state -> 4)
			.sounds(BlockSoundGroup.AMETHYST_BLOCK)), true);

	public static final Block MIRROR_STONE = register("mirror_stone", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.CYAN)
			.requiresTool()
			.strength(1.5F, 6.0F)
			.luminance(state -> 2)
			.sounds(BlockSoundGroup.AMETHYST_BLOCK)), true);

	public static final Block MIRROR_LOG = register("mirror_log", new PillarBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.CYAN)
			.strength(2.0F)
			.luminance(state -> 3)
			.sounds(BlockSoundGroup.AMETHYST_BLOCK)), true);

	public static final Block MIRROR_LEAVES = register("mirror_leaves", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.DIAMOND_BLUE)
			.strength(0.2F)
			.luminance(state -> 5)
			.sounds(BlockSoundGroup.AMETHYST_CLUSTER)), true);

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
