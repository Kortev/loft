package io.github.kortev.gojolander;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;

/**
 * What Unlimited Void does to whoever it holds: not a wound, everything there is, all at once, more than a mind can
 * take. It goes through armour, enchantments, Resistance and shields alike, and past the moment's grace after a hit,
 * so it takes exactly what it is given; it neither knocks nor moves them, and makes no sound of its own (the Domain's
 * clients play its own). Its tags are in {@code data/minecraft/tags/damage_type}.
 */
public final class GojoDamage {
	public static final RegistryKey<DamageType> UNLIMITED_VOID = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Gojolander.id("unlimited_void"));

	private GojoDamage() {
	}

	public static DamageSource unlimitedVoid(World world) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(UNLIMITED_VOID));
	}
}
