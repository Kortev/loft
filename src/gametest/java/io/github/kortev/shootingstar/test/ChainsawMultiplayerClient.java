package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.MergeTimeline;
import io.github.kortev.chainsawman.client.ChainsawManClient;
import io.github.kortev.chainsawman.client.ClientCombat;
import io.github.kortev.chainsawman.client.ClientForms;
import io.github.kortev.chainsawman.client.MergeCinematic;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.player.PlayerEntity;

/**
 * With -Dshootingstar.selftest=mp-shooter, mp-victim or mp-netherling and -Dshootingstar.mptest.scenario=chainsaw: one
 * of the three clients of the chainsaw's multiplayer test (see {@link ChainsawMultiplayerServer}). Shooter walks
 * forwards all through the merge, pulls the cord at the end of it, slashes, kicks, fires the chain and fires it again;
 * once up again pulls the cord, turns back with it, pulls it once more and leaves in the form. Netherling holds use,
 * sneaking, on them while they're down, to give them blood. Everyone photographs it all from where they are, and checks
 * what their own game shows of it.
 */
public class ChainsawMultiplayerClient implements ClientModInitializer {
	/** Ticks into the merge to photograph it at. */
	private static final int[] MERGE_SHOTS = {12, 100, 160, 200, 260, 340, 470, 505, 530, 552, 580, 600, MergeTimeline.END + 20};
	private static String role;
	private static int ticks;
	private static PlayerEntity man;
	private static boolean wasOn;
	private static boolean wasDown;
	private static boolean wasWaiting;
	private static int ons;
	private static int onAt = -1;
	private static int offAt = -1;
	private static int downAt = -1;
	private static int upAt = -1;
	private static int waitAt = -1;
	private static int goneAt = -1;
	private static final Set<String> TAKEN = new HashSet<>();

	@Override
	public void onInitializeClient() {
		String mode = System.getProperty("shootingstar.selftest", "");
		if (!mode.startsWith("mp-") || !ChainsawMultiplayerServer.scenario()) {
			return;
		}
		role = mode.substring(3);
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(10 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[mptest] {} timed out", role);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-mptest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ClientTickEvents.END_CLIENT_TICK.register(ChainsawMultiplayerClient::tick);
	}

	private static void tick(MinecraftClient client) {
		TestWindow.keepToItself(client);
		if (client.player == null || client.world == null) {
			return;
		}
		ticks++;
		boolean shooter = "shooter".equals(role);
		PlayerEntity found = null;
		for (PlayerEntity p : client.world.getPlayers()) {
			if (ChainsawMultiplayerServer.MAN.equals(p.getGameProfile().getName())) {
				found = p;
			}
		}
		if (found == null) {
			if (man != null && ons == 3 && goneAt < 0) {
				goneAt = ticks;
				check(!ClientForms.on(man), "Shooter's form is gone with them");
			}
			if (goneAt >= 0 && ticks == goneAt + 80) {
				ShootingStar.LOGGER.info("[mptest] {} leaving", role);
				client.scheduleStop();
			}
			return;
		}
		man = found;
		merge(client, shooter);
		boolean on = ClientForms.on(man);
		if (on && !wasOn) {
			ons++;
			onAt = ticks;
			ShootingStar.LOGGER.info("[mptest] {} sees Shooter in the form ({})", role, ons);
		}
		if (!on && wasOn) {
			offAt = ticks;
		}
		wasOn = on;
		boolean down = Downed.is(man);
		if (down && !wasDown) {
			downAt = ticks;
			check(true, "sees Shooter go down");
		}
		if (!down && wasDown) {
			upAt = ticks;
			check(true, "sees Shooter get up");
		}
		wasDown = down;
		if (onAt >= 0) {
			int in = ticks - onAt;
			for (int at : new int[] {5, 30, 62, 95, 125, 175}) {
				if (in == at) {
					shot(client, "form" + ons + "_" + at);
				}
			}
		}
		if (ons == 1 && onAt >= 0) {
			int in = ticks - onAt;
			if (shooter && (in == 60 || in == 75 || in == 90)) {
				press(client.options.attackKey, "slash");
			}
			if (shooter && in == 112) {
				press(ChainsawManClient.KICK, "kick");
			}
			if (shooter && (in == 165 || in == 205)) {
				press(ChainsawManClient.CHAIN, in == 165 ? "chain" : "chain again, to let go");
			}
			if ("victim".equals(role) && in == 180) {
				check(ClientCombat.chain(man) != null, "sees Shooter's chain out");
			}
		}
		if (downAt >= 0) {
			int in = ticks - downAt;
			if (in == 20 || in == 70 || upAt < 0 && in == 140) {
				shot(client, "down_" + in);
			}
			// Netherling, by them from 10 ticks after the server puts them there: sneaking, holding use on them.
			boolean feeding = "netherling".equals(role) && down && in >= 40;
			if ("netherling".equals(role) && (feeding || in == 40 || upAt >= 0 && ticks == upAt + 1)) {
				client.options.sneakKey.setPressed(feeding);
				client.options.useKey.setPressed(feeding);
			}
		}
		if (upAt >= 0 && (ticks == upAt + 10 || ticks == upAt + 30)) {
			shot(client, "up_" + (ticks - upAt));
		}
		if (shooter && upAt >= 0 && ticks == upAt + 60 && ons == 1) {
			press(ChainsawManClient.PULL_CORD, "back into the form");
		}
		if (shooter && ons == 2 && onAt >= 0 && ticks == onAt + 70) {
			press(ChainsawManClient.PULL_CORD, "to turn back");
		}
		if (ons == 2 && ClientForms.turnAge(man, 0.0F) >= 0.0F && TAKEN.add("turning-seen")) {
			check(true, "sees Shooter turn back");
			shot(client, "turning");
		}
		if (shooter && ons == 2 && offAt > onAt && ticks == offAt + 40) {
			press(ChainsawManClient.PULL_CORD, "into the form the third time");
		}
		if (shooter && ons == 3 && ticks == onAt + 70) {
			ShootingStar.LOGGER.info("[mptest] shooter leaves in the form");
			client.scheduleStop();
		}
	}

	/** The merge: Shooter walking all through it, and pulling the cord at the end; everyone photographing it. */
	private static void merge(MinecraftClient client, boolean shooter) {
		float age = MergeCinematic.age(man, 0.0F);
		if (age < 0.0F) {
			return;
		}
		int t = (int) age;
		for (int at : MERGE_SHOTS) {
			if (t == at) {
				shot(client, "merge_" + at);
			}
		}
		if (t == 40) {
			check(MergeCinematic.merging(man), "sees Shooter's merge");
		}
		if (t == 200) {
			float dream = MergeCinematic.dream(0.0F);
			check(shooter ? dream > 0.9F : dream < 0.01F, shooter ? "is in the dream" : "is not pulled into Shooter's dream (" + dream + ")");
		}
		if (shooter) {
			client.options.forwardKey.setPressed(t >= 30 && t < MergeTimeline.LEAP);
			boolean waiting = MergeCinematic.waiting(man);
			if (waiting && !wasWaiting) {
				waitAt = ticks;
			}
			wasWaiting = waiting;
			if (waitAt >= 0 && ticks == waitAt + 30) {
				press(ChainsawManClient.PULL_CORD, "at the end of the merge");
			}
		}
	}

	/** A key pressed as a player would (so it goes the way the key does, not round it). */
	private static void press(KeyBinding key, String why) {
		ShootingStar.LOGGER.info("[mptest] {} presses {} ({})", role, key.getTranslationKey(), why);
		KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey()));
	}

	private static void check(boolean ok, String what) {
		if (ok) {
			ShootingStar.LOGGER.info("[mptest] PASS {} {}", role, what);
		} else {
			ShootingStar.LOGGER.error("[mptest] FAIL {} {}", role, what);
		}
	}

	private static void shot(MinecraftClient client, String name) {
		if (!TAKEN.add(name)) {
			return;
		}
		String file = String.format(Locale.ROOT, "%s_%02d_%s.png", role, TAKEN.size(), name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, file, client.getFramebuffer(), message -> {
		});
	}
}
