package io.github.kortev.gojolander;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** The Domain's particles (drawn by the client mod). */
public final class GojoParticles {
	/** A faint mote of the void's pale light, drifting up off one it broke and fading, all through their daze. */
	public static final SimpleParticleType MOTE = Registry.register(Registries.PARTICLE_TYPE, Gojolander.id("mote"), FabricParticleTypes.simple());

	private GojoParticles() {
	}

	static void init() {
	}
}
