package io.github.kortev.gojolander;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * The Domain's sounds, all made by {@code tools/gen_gojolander_sounds.py}. Those heard from inside it (and the caster's
 * own) are stereo, played as they are heard rather than placed; the ".world" ones are what everyone outside hears, mono
 * so they can be placed where it is.
 */
public final class GojoSounds {
	/** The hand sign: a held breath and a low swell, and the ring hissing out over the ground. */
	public static final SoundEvent SIGN = register("domain.sign");
	public static final SoundEvent SIGN_WORLD = register("domain.sign.world");
	/** The barrier closing overhead: the air drawn in, then a deep, glassy shut. */
	public static final SoundEvent CLOSE = register("domain.close");
	public static final SoundEvent CLOSE_WORLD = register("domain.close.world");
	/** The void opening out of the eye, after the silence: a vast chord blooming out of nothing. */
	public static final SoundEvent REVEAL = register("domain.reveal");
	/** The void: an endless shimmering hum, as wide as the sky. */
	public static final SoundEvent VOID = register("domain.void");
	/** What those it caught hear: everything there is, at once. */
	public static final SoundEvent FLOOD = register("domain.flood");
	/** Let go of, the void fading away: its hum sinking and thinning into air, the world's quiet coming back. */
	public static final SoundEvent FADE = register("domain.fade");
	public static final SoundEvent FADE_WORLD = register("domain.fade.world");
	/** Cut short, the barrier shattering and the world rushing back. */
	public static final SoundEvent COLLAPSE = register("domain.collapse");
	public static final SoundEvent COLLAPSE_WORLD = register("domain.collapse.world");
	/** Not now: a soft dead click. */
	public static final SoundEvent DENIED = register("domain.denied");

	private GojoSounds() {
	}

	static void init() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Gojolander.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}
}
