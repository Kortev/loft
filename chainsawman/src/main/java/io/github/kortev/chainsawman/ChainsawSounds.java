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
	/** A slash: the engine revving up hard as the saw whips through the air. */
	public static final SoundEvent SLASH = register("form.slash");
	/** The saw biting into something: a wet rip under the engine bogging down. */
	public static final SoundEvent HIT = register("form.hit");
	/** The saw biting hard into someone (a player, a finisher): a thump, bone cracking, the engine bogging down. */
	public static final SoundEvent CRUNCH = register("form.crunch");
	/** Grinding, a loop: the engine screaming under load, the chain chewing. */
	public static final SoundEvent GRIND = register("form.grind");
	/** The chain fired out of the arm: links rattling out fast, and the hook biting. */
	public static final SoundEvent CHAIN = register("form.chain");
	/** The frenzy kicking in: the engine shrieking up into a roar. */
	public static final SoundEvent FRENZY = register("form.frenzy");
	/** The frenzy's engine, a loop: screaming flat out. */
	public static final SoundEvent FRENZY_LOOP = register("form.frenzy_loop");
	/** Down: a wet collapse, the engine spluttering out. */
	public static final SoundEvent DOWN = register("form.down");
	/** Back up: a gulp of blood and the heart catching. */
	public static final SoundEvent REVIVE = register("form.revive");
	/** Pochita becoming your heart, cut to {@code MergeTimeline}: ringing silence, his steps, the sink, the heart starting. */
	public static final SoundEvent MERGE = register("form.merge");
	/** Pochita yours (and brought round): two happy yips and his engine's purr. */
	public static final SoundEvent TAME = register("pochita.tame");
	/**
	 * Pochita's five lines in the merge, his own voice: each cut from a clip kortev chooses (as the Domain's are), dropped
	 * in as sounds/form_merge_voice_N.ogg. Until then each is a moment of silence; the words show under the picture.
	 */
	public static final SoundEvent[] MERGE_VOICES = {register("form.merge.voice.1"), register("form.merge.voice.2"),
			register("form.merge.voice.3"), register("form.merge.voice.4"), register("form.merge.voice.5")};

	/** Beam bursting up out of the ground (and leaping, and crashing down when pitched low): stone and deepslate cracking, a deep boom, a heavy throw of earth, rubble raining back. */
	public static final SoundEvent BEAM_BURST = register("beam.burst");
	/** One footfall of Beam: a huge animal's foot slamming the earth, a heavy thud with dirt and gravel scattering; short, as it comes about three times a second. */
	public static final SoundEvent BEAM_STEP = register("beam.step");
	/** Beam swimming through the ground: a deep, thick rumble of earth and a dropped growl with dirt ripping past, played again every few ticks to run into one smooth roar. */
	public static final SoundEvent BEAM_SWIM = register("beam.swim");
	/** Beam's bite: a big wet chomp, jaws slamming, flesh and crunching bone, a low thump; tight enough to come several times a second. */
	public static final SoundEvent BEAM_BITE = register("beam.bite");
	/** Beam diving (or surfacing, pitched up): the earth closing over him, a sinking rumble of mud and dirt with debris trickling. */
	public static final SoundEvent BEAM_DIVE = register("beam.dive");
	/** Beam's screech of joy when he is called up: a big breath and an unhinged animal roar and cackle, with a gravel throat under it. */
	public static final SoundEvent BEAM_SCREECH = register("beam.screech");
	/** Beam's teeth chattering when he stands about: hard, bony clacks with a little wet under them, quiet and short. */
	public static final SoundEvent BEAM_CHATTER = register("beam.chatter");
	/** Beam bursting up under a mob: the ambush's slam, the loudest he makes, an earth-shaking boom with a long rain of stone. */
	public static final SoundEvent BEAM_SLAM = register("beam.slam");
	/** Beam throwing his rider, and flinging what he held: the snap of his head, a wet crack, a whip of air and a low thump. */
	public static final SoundEvent BEAM_LAUNCH = register("beam.launch");

	private ChainsawSounds() {
	}

	static void init() {
	}

	private static SoundEvent register(String name) {
		Identifier id = ChainsawMan.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}
}
