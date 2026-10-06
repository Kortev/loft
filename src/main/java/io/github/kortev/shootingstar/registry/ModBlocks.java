package io.github.kortev.shootingstar.registry;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.block.MoltenCrustBlock;
import io.github.kortev.shootingstar.block.VoidFloorBlock;
import io.github.kortev.shootingstar.block.YggdrasilSaplingBlock;
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

	/** The floor of the void: everyone the black takes stands on it. Black, unbreakable, never outlined. */
	public static final Block VOID_FLOOR = register("void_floor", new VoidFloorBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.BLACK)
			.strength(-1.0F, 3600000.0F)
			.dropsNothing()
			.allowsSpawning((state, world, pos, type) -> false)
			.sounds(BlockSoundGroup.GLASS)), true);

	/** Left in the middle of the crater when Yggdrasil draws back down into it. */
	public static final Block YGGDRASIL_SAPLING = register("yggdrasil_sapling", new YggdrasilSaplingBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.DIAMOND_BLUE)
			.noCollision()
			.breakInstantly()
			.nonOpaque()
			.luminance(state -> 13)
			.emissiveLighting((state, world, pos) -> true)
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
