package io.github.kortev.gojolander;

import io.github.kortev.gojolander.domain.DomainManager;
import io.github.kortev.gojolander.network.DomainCastPayload;
import io.github.kortev.gojolander.network.DomainCaughtPayload;
import io.github.kortev.gojolander.network.DomainEndPayload;
import io.github.kortev.gojolander.network.DomainLockPayload;
import io.github.kortev.gojolander.network.SixEyesPayload;
import io.github.kortev.gojolander.sight.SixEyes;
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
 * Gojolander: the first friend's own mod on the SMP, beside kortev's The Shooting Star. He plays someone who is the
 * strongest there is, and the rival kortev answers to. For now his one thing is the blindfold: the Six Eyes it lets him
 * see with ({@link SixEyes}), and his ult, cast with it on: Domain Expansion, Unlimited Void ({@link DomainManager}).
 *
 * <p>It is a mod of its own, in its own jar and its own namespace, built beside The Shooting Star and needing it: its
 * things are under that mod's rule that only their owner crafts them (here, {@link Owner}: only him), and its
 * advancements use that mod's event trigger.
 */
public final class Gojolander implements ModInitializer {
	public static final String MOD_ID = "gojolander";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** The blindfold: worn over the eyes, in place of a helmet, it is what he casts his Domain with. */
	public static final Item BLINDFOLD = Registry.register(Registries.ITEM, id("blindfold"),
			new BlindfoldItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		GojoSounds.init();
		GojoParticles.init();
		GojoRules.init();
		Owner.init();
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(BLINDFOLD));
		PayloadTypeRegistry.playS2C().register(DomainLockPayload.ID, DomainLockPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DomainCaughtPayload.ID, DomainCaughtPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DomainEndPayload.ID, DomainEndPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SixEyesPayload.ID, SixEyesPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DomainCastPayload.ID, DomainCastPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DomainCastPayload.ID, (payload, context) -> DomainManager.cast(context.player()));
		DomainManager.init();
		SixEyes.init();
		GojoCommand.init();
	}
}
