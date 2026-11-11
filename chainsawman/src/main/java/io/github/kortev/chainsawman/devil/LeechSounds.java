package io.github.kortev.chainsawman.devil;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** The Leech Devil's sounds, synthesised by {@code tools/gen_leech_sounds.py}. */
public final class LeechSounds {
	/** A soft sing-song hum through a shut mouth while it circles you (three short phrases); cut off dead at each tell. */
	public static final SoundEvent HUM = register("leech.hum");
	/** The tongue's tell: a wet rising slurp as it coils back. */
	public static final SoundEvent TELL = register("leech.tell");
	/** The tongue lancing out: a whip of air and a wet crack. */
	public static final SoundEvent TONGUE = register("leech.tongue");
	/** The latch's tell: a sucker opening with a smack. */
	public static final SoundEvent SMACK = register("leech.smack");
	/** A tentacle whipping out. */
	public static final SoundEvent WHIP = register("leech.whip");
	/** One pulse of it drinking through the sucker. */
	public static final SoundEvent SUCK = register("leech.suck");
	/** The arm cut loose: a wet rip and the sucker popping off. */
	public static final SoundEvent TEAR = register("leech.tear");
	/** Hurt: a shrill, broken shriek. */
	public static final SoundEvent SHRIEK = register("leech.shriek");
	/** A front sucker pawing the ground before the trample. */
	public static final SoundEvent PAW = register("leech.paw");
	/** The trample's run. */
	public static final SoundEvent TRAMPLE = register("leech.trample");
	/** Skidding to a stop after the trample. */
	public static final SoundEvent SKID = register("leech.skid");
	/** A sucker foot coming down: a wet slap. */
	public static final SoundEvent STEP = register("leech.step");
	/** Dying: a long gurgling wheeze. */
	public static final SoundEvent DEATH = register("leech.death");
	/** The tongue tasting the air. */
	public static final SoundEvent TASTE = register("leech.taste");

	private LeechSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = ChainsawMan.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	static void init() {
	}
}
