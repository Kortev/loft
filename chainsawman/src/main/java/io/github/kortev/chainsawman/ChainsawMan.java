package io.github.kortev.chainsawman;

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
 * whoever carries him into Chainsaw Man ({@link Form}): the saws to fight with ({@link Saw}), the chain ({@link Grapple}),
 * the frenzy ({@link Frenzy}), and never dying while Pochita is with you ({@link io.github.kortev.chainsawman.form.Downed}). Its things are kortev's alone to craft, under The Shooting
 * Star's owner-only rule.
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
		ChainsawSounds.init();
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(POCHITA));
		PayloadTypeRegistry.playS2C().register(FormPayload.ID, FormPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FormPullPayload.ID, FormPullPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FormEventPayload.ID, FormEventPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PullCordPayload.ID, PullCordPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FormActionPayload.ID, FormActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PullCordPayload.ID, (payload, context) -> Form.pullCord(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(FormActionPayload.ID, (payload, context) -> {
			switch (payload.action()) {
				case FormActionPayload.SLASH -> Saw.slash(context.player());
				case FormActionPayload.GRIND -> Saw.grind(context.player(), payload.on());
				case FormActionPayload.CHAIN -> Grapple.fire(context.player());
				case FormActionPayload.FRENZY -> Frenzy.start(context.player());
				default -> {
				}
			}
		});
		Form.init();
	}
}
