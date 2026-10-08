package io.github.kortev.shootingstar.registry;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class ModDamageTypes {
	public static final RegistryKey<DamageType> KINETIC_STRIKE =
			RegistryKey.of(RegistryKeys.DAMAGE_TYPE, ShootingStar.id("kinetic_strike"));

	public static final RegistryKey<DamageType> ERASED = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, ShootingStar.id("erased"));
	/** Swallowed by the universe in the block as it bursts out of the ground. */
	public static final RegistryKey<DamageType> SWALLOWED = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, ShootingStar.id("swallowed"));

	/** Mjölnir's stroke: the bolt itself and the current through the ground under it. */
	public static final RegistryKey<DamageType> THUNDERSTRUCK = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, ShootingStar.id("thunderstruck"));
	/** An arc that jumped off Mjölnir's bolt, or off someone it had already hit. */
	public static final RegistryKey<DamageType> ARCED = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, ShootingStar.id("arced"));

	private ModDamageTypes() {
	}

	public static DamageSource erased(World world) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ERASED));
	}

	/** Erased by whoever turned the key ({@code by}, if they are about). */
	public static DamageSource erased(World world, @Nullable Entity by) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ERASED), by);
	}

	/** Swallowed by the universe that {@code by} brought down (if they are about). */
	public static DamageSource swallowed(World world, @Nullable Entity by) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SWALLOWED), by);
	}

	public static DamageSource kineticStrike(World world) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(KINETIC_STRIKE));
	}

	/** Struck down by the bolt whoever raised Mjölnir ({@code by}, if they are about) called down. */
	public static DamageSource thunderstruck(World world, @Nullable Entity by) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(THUNDERSTRUCK), by);
	}

	/** Hit by an arc off the bolt {@code by} called down. */
	public static DamageSource arced(World world, @Nullable Entity by) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ARCED), by);
	}
}
