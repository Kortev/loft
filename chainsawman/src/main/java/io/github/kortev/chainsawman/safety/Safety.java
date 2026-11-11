package io.github.kortev.chainsawman.safety;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.form.HeartLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;

/**
 * Public Safety (the {@code safety/} lane): a notice board by every village's bell ({@link NoticeBoards}) with a poster that hands out a
 * hunter's licence and a green public phone that buys devil parts for yen; the licence card (its key, J, shows it); and, per player, saved
 * on them and kept through death, their yen and the devils they have hunted (counted when they deal the killing blow, licence or not).
 * Yen buys nothing yet.
 */
public final class Safety {
	/** Tomato Devil carcass: what Public Safety pays for one. */
	public static final long TOMATO_PRICE = 3000L;
	/** The first part a player ever sells adds this, once. */
	public static final long FIRST_BONUS = 10000L;

	public static final Block POSTER = Registry.register(Registries.BLOCK, ChainsawMan.id("poster"), new BoardBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.WHITE).strength(1.0F).sounds(BlockSoundGroup.WOOD).noCollision().nonOpaque().burnable(), true));
	public static final Block PHONE = Registry.register(Registries.BLOCK, ChainsawMan.id("phone"), new BoardBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.GREEN).strength(1.5F).sounds(BlockSoundGroup.METAL).instrument(NoteBlockInstrument.BELL).nonOpaque(), false));
	public static final Item POSTER_ITEM = Registry.register(Registries.ITEM, ChainsawMan.id("poster"), new BlockItem(POSTER, new Item.Settings()));
	public static final Item PHONE_ITEM = Registry.register(Registries.ITEM, ChainsawMan.id("phone"), new BlockItem(PHONE, new Item.Settings()));
	/** The hunter's licence: one, and only a card (what is on it is the player's). */
	public static final Item LICENCE = Registry.register(Registries.ITEM, ChainsawMan.id("licence"),
			new LicenceItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	/** Yen, kept on the player. */
	public static final AttachmentType<Long> YEN = AttachmentRegistry.<Long>builder().persistent(Codec.LONG).copyOnDeath()
			.buildAndRegister(ChainsawMan.id("yen"));
	/** Devils hunted: entity type id to how many. */
	public static final AttachmentType<Map<String, Integer>> HUNTED = AttachmentRegistry.<Map<String, Integer>>builder()
			.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath().buildAndRegister(ChainsawMan.id("devils_hunted"));
	/** Whether the first report's bonus has been paid. */
	public static final AttachmentType<Boolean> REPORTED = AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).copyOnDeath()
			.buildAndRegister(ChainsawMan.id("first_report"));

	/** Sounds, synthesised by {@code tools/gen_safety_sounds.py}. */
	public static final class Sounds {
		/** Paper: the poster torn off, the licence card out. */
		public static final SoundEvent PAPER = register("safety.paper");
		/** The phone: the handset up, the ringing at the other end, an answer, a voice murmuring through the line. */
		public static final SoundEvent RING = register("safety.ring");
		/** Paid: an envelope and coins and a bell. */
		public static final SoundEvent CASH = register("safety.cash");
		/** The yen counting up: one click. */
		public static final SoundEvent TICK = register("safety.tick");
		/** Nothing to report: the handset up, a dial tone, down again. */
		public static final SoundEvent DIAL = register("safety.dial");

		private Sounds() {
		}

		private static SoundEvent register(String name) {
			Identifier id = ChainsawMan.id(name);
			return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
		}

		static void init() {
		}
	}

	private Safety() {
	}

	/** Called once from the mod's init. */
	public static void init() {
		Sounds.init();
		PayloadTypeRegistry.playS2C().register(SafetyPayloads.Licence.ID, SafetyPayloads.Licence.CODEC);
		PayloadTypeRegistry.playS2C().register(SafetyPayloads.Payday.ID, SafetyPayloads.Payday.CODEC);
		PayloadTypeRegistry.playC2S().register(SafetyPayloads.Ask.ID, SafetyPayloads.Ask.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SafetyPayloads.Ask.ID, (payload, context) -> show(context.player()));
		ServerLivingEntityEvents.AFTER_DEATH.register(Safety::died);
		ServerTickEvents.END_WORLD_TICK.register(NoticeBoards::tick);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(LICENCE));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.add(POSTER_ITEM);
			entries.add(PHONE_ITEM);
		});
	}

	// ------------------------------------------------------------------ the player's record

	public static long yen(ServerPlayerEntity p) {
		return p.getAttachedOrElse(YEN, 0L);
	}

	public static Map<String, Integer> hunted(ServerPlayerEntity p) {
		return p.getAttachedOrElse(HUNTED, Map.of());
	}

	public static int hunted(ServerPlayerEntity p, Identifier type) {
		return hunted(p).getOrDefault(type.toString(), 0);
	}

	/** What Public Safety pays for one of {@code stack}'s item, or 0 if it is no devil's part. */
	public static long price(ItemStack stack) {
		return stack.isOf(io.github.kortev.chainsawman.devil.Devils.TOMATO_CARCASS) ? TOMATO_PRICE : 0L;
	}

	/** Whether {@code p} has a licence on them. */
	public static boolean holdsLicence(ServerPlayerEntity p) {
		for (int i = 0; i < p.getInventory().size(); i++) {
			if (p.getInventory().getStack(i).isOf(LICENCE)) {
				return true;
			}
		}
		return false;
	}

	/** A devil died: the player who dealt the killing blow (or, if it was not a blow, the one who last hit it) has one more of its kind. */
	private static void died(LivingEntity entity, DamageSource source) {
		if (!entity.getType().isIn(HeartLevel.DEVILS)) {
			return;
		}
		Entity by = source.getAttacker();
		if (!(by instanceof ServerPlayerEntity)) {
			by = entity.getPrimeAdversary();
		}
		if (by instanceof ServerPlayerEntity p && p != entity) {
			count(p, Registries.ENTITY_TYPE.getId(entity.getType()));
		}
	}

	/** One more devil of {@code type} hunted by {@code p}. */
	public static void count(ServerPlayerEntity p, Identifier type) {
		Map<String, Integer> map = new HashMap<>(hunted(p));
		map.merge(type.toString(), 1, Integer::sum);
		p.setAttached(HUNTED, map);
	}

	// ------------------------------------------------------------------ the board

	/** The poster: a licence if they have none; the paper's sound either way. */
	public static void poster(ServerPlayerEntity p, BlockPos at) {
		p.getServerWorld().playSound(null, at, Sounds.PAPER, SoundCategory.BLOCKS, 1.0F, 1.0F);
		licence(p);
	}

	/** A licence for {@code p} if they hold none, with the line that says so; whether one was given. */
	public static boolean licence(ServerPlayerEntity p) {
		if (holdsLicence(p)) {
			return false;
		}
		p.getInventory().offerOrDrop(new ItemStack(LICENCE));
		p.sendMessage(Text.translatable("safety.chainsawman.hired"), true);
		return true;
	}

	/**
	 * The phone: a licence first if they have none; then every devil part on them is sold (the first ever sale adds {@link #FIRST_BONUS}):
	 * the phone rings and the client counts the yen up. With nothing to sell, a dial tone and "Nothing to report."
	 */
	public static void phone(ServerPlayerEntity p, BlockPos at) {
		boolean gave = false;
		if (!holdsLicence(p)) {
			p.getServerWorld().playSound(null, at, Sounds.PAPER, SoundCategory.BLOCKS, 1.0F, 1.0F);
			gave = licence(p);
		}
		Map<Item, int[]> sold = new LinkedHashMap<>();
		long total = 0L;
		for (int i = 0; i < p.getInventory().size(); i++) {
			ItemStack stack = p.getInventory().getStack(i);
			long price = price(stack);
			if (price > 0 && !stack.isEmpty()) {
				sold.computeIfAbsent(stack.getItem(), k -> new int[1])[0] += stack.getCount();
				total += price * stack.getCount();
				p.getInventory().setStack(i, ItemStack.EMPTY);
			}
		}
		if (sold.isEmpty()) {
			p.getServerWorld().playSound(null, at, Sounds.DIAL, SoundCategory.BLOCKS, 1.0F, 1.0F);
			if (!gave) {
				p.sendMessage(Text.translatable("safety.chainsawman.nothing"), true);
			}
			return;
		}
		List<SafetyPayloads.Line> lines = new ArrayList<>();
		for (Map.Entry<Item, int[]> e : sold.entrySet()) {
			ItemStack one = new ItemStack(e.getKey());
			lines.add(new SafetyPayloads.Line(one.getItem().getTranslationKey(), e.getValue()[0], price(one) * e.getValue()[0]));
		}
		long bonus = p.getAttachedOrElse(REPORTED, false) ? 0L : FIRST_BONUS;
		p.setAttached(REPORTED, true);
		long from = yen(p);
		long to = from + total + bonus;
		p.setAttached(YEN, to);
		p.getServerWorld().playSound(null, at, Sounds.RING, SoundCategory.BLOCKS, 1.0F, 1.0F);
		if (ServerPlayNetworking.canSend(p, SafetyPayloads.Payday.ID)) {
			ServerPlayNetworking.send(p, new SafetyPayloads.Payday(from, to, lines, bonus));
		}
		tell(p, false);
	}

	/** The licence card for {@code p}: what it shows, and shown now. */
	public static void show(ServerPlayerEntity p) {
		tell(p, true);
	}

	private static void tell(ServerPlayerEntity p, boolean open) {
		if (ServerPlayNetworking.canSend(p, SafetyPayloads.Licence.ID)) {
			ServerPlayNetworking.send(p, new SafetyPayloads.Licence(yen(p), new LinkedHashMap<>(hunted(p)), new ArrayList<>(Contracts.ids(p)), open));
		}
	}
}
