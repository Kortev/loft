package io.github.kortev.shootingstar.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.Optional;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * One advancement trigger for everything the weapons do: {@code shootingstar:event} with the name of what happened
 * (see the EVENT constants). A new weapon's advancements need only new names, fired where they happen.
 */
public final class ModCriteria {
	// Gungnir.
	public static final String GUNGNIR_LOCK = "gungnir_lock";
	public static final String GUNGNIR_IMPACT = "gungnir_impact";
	public static final String GUNGNIR_DANGER_CLOSE = "gungnir_danger_close";
	// The Genesis Key.
	public static final String GAP_OPEN = "gap_open";
	public static final String GAP_VOID = "gap_void";
	public static final String GAP_RESTORED = "gap_restored";

	public static final EventCriterion EVENT = Registry.register(Registries.CRITERION, ShootingStar.id("event"), new EventCriterion());

	private ModCriteria() {
	}

	public static void init() {
	}

	/** Fires {@code event} for {@code player}, if there is one (strikes and events called in by command have none). */
	public static void fire(ServerPlayerEntity player, String event) {
		if (player != null) {
			EVENT.trigger(player, event);
		}
	}

	public static final class EventCriterion extends AbstractCriterion<EventCriterion.Conditions> {
		@Override
		public Codec<Conditions> getConditionsCodec() {
			return Conditions.CODEC;
		}

		public void trigger(ServerPlayerEntity player, String event) {
			trigger(player, conditions -> conditions.event().equals(event));
		}

		public record Conditions(Optional<LootContextPredicate> player, String event) implements AbstractCriterion.Conditions {
			public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
					EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
					Codec.STRING.fieldOf("event").forGetter(Conditions::event)).apply(instance, Conditions::new));
		}
	}
}
