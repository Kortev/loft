package io.github.kortev.gojolander;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * The Domain's sounds, all made by {@code tools/gen_gojolander_sounds.py} but his voice. Those heard from inside it (and the caster's
 * own) are stereo, played as they are heard rather than placed; the ".world" ones are what everyone outside hears, mono
 * so they can be placed where it is.
 */
public final class GojoSounds {
	/**
	 * His own voice, as in the show: "Ryōiki Tenkai" (Domain Expansion) as his hand comes up into the sign, and
	 * "Muryōkūsho" (Unlimited Void) as the void opens. Cut from a clip kortev chose (build/voice/source), not made by
	 * the generator.
	 */
	public static final SoundEvent VOICE_EXPANSION = register("domain.voice.expansion");
	public static final SoundEvent VOICE_VOID = register("domain.voice.void");
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
	/**
	 * A step down, the flood surging: for the caught, a dull blow inside the head and a ringing that swells with it;
	 * for whoever is by, a muffled throb from them.
	 */
	public static final SoundEvent PULSE = register("domain.pulse");
	public static final SoundEvent PULSE_WORLD = register("domain.pulse.world");
	/**
	 * Let go of at its end, broken: for the caught, the ringing in their head snapping off into a deep, dull thud and
	 * then almost nothing; for whoever is by, the ring cutting off and their knees hitting the ground.
	 */
	public static final SoundEvent BREAK = register("domain.break");
	public static final SoundEvent BREAK_WORLD = register("domain.break.world");
	/** One slow, muffled beat of their own heart, heard by one it broke over and over through their daze. */
	public static final SoundEvent HEARTBEAT = register("domain.heartbeat");
	/** Let go of, the void fading away: its hum sinking and thinning into air, the world's quiet coming back. */
	public static final SoundEvent FADE = register("domain.fade");
	public static final SoundEvent FADE_WORLD = register("domain.fade.world");
	/** Cut short, the barrier shattering and the world rushing back. */
	public static final SoundEvent COLLAPSE = register("domain.collapse");
	public static final SoundEvent COLLAPSE_WORLD = register("domain.collapse.world");
	/** Not now: a soft dead click. */
	public static final SoundEvent DENIED = register("domain.denied");

	/**
	 * Hollow Purple. Heard by the caster (and whoever the shots are for) as they are, stereo; the ".world" ones, mono,
	 * for everyone else, from where it happens: Blue sucking in, Red bursting out, the merge (hum, flash, crack), the
	 * release boom, the roar as it carves, and its collapse.
	 */
	public static final SoundEvent PURPLE_BLUE = register("purple.blue");
	public static final SoundEvent PURPLE_BLUE_WORLD = register("purple.blue.world");
	public static final SoundEvent PURPLE_RED = register("purple.red");
	public static final SoundEvent PURPLE_RED_WORLD = register("purple.red.world");
	public static final SoundEvent PURPLE_MERGE = register("purple.merge");
	public static final SoundEvent PURPLE_MERGE_WORLD = register("purple.merge.world");
	public static final SoundEvent PURPLE_RELEASE = register("purple.release");
	public static final SoundEvent PURPLE_RELEASE_WORLD = register("purple.release.world");
	public static final SoundEvent PURPLE_ROAR = register("purple.roar");
	public static final SoundEvent PURPLE_ROAR_WORLD = register("purple.roar.world");
	public static final SoundEvent PURPLE_FADE = register("purple.fade");
	public static final SoundEvent PURPLE_FADE_WORLD = register("purple.fade.world");
	/**
	 * A slot for his voice line ("Hollow Technique: Purple"), played as the orbs meet. Nothing is in it: kortev supplies
	 * the clip as sounds/purple_voice.ogg and adds a purple.voice entry to sounds.json; until then it is not played.
	 */
	public static final SoundEvent PURPLE_VOICE = register("purple.voice");

	private GojoSounds() {
	}

	static void init() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Gojolander.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}
}
