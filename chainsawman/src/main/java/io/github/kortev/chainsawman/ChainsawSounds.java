package io.github.kortev.chainsawman;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Chainsaw Man's sounds, all made by {@code tools/gen_chainsaw_sounds.py}; mono, placed on whoever makes them. */
public final class ChainsawSounds {
	/** The cord ripped out of the chest in one long pull, its ratchet rattling, and snapping back in. */
	public static final SoundEvent CORD = register("form.cord");
	/** The engine catching off the pull and screaming up as the saws burst out, then dropping to its idle. */
	public static final SoundEvent START = register("form.start");
	/** The engine idling while the form is on: a two-stroke putting along, the chains ticking round. */
	public static final SoundEvent IDLE = register("form.idle");
	/** The engine coughing, stumbling and dying as he turns back. */
	public static final SoundEvent STOP = register("form.stop");

	private ChainsawSounds() {
	}

	static void init() {
	}

	private static SoundEvent register(String name) {
		Identifier id = ChainsawMan.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}
}
