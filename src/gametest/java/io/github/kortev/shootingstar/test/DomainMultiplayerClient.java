package io.github.kortev.shootingstar.test;

import io.github.kortev.gojolander.BlindfoldItem;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.client.ClientDomain;
import io.github.kortev.gojolander.client.ClientDomains;
import io.github.kortev.gojolander.client.GojolanderClient;
import io.github.kortev.gojolander.domain.DomainTimeline;
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

/**
 * With -Dshootingstar.selftest=mp-shooter, mp-victim or mp-netherling and -Dshootingstar.mptest.scenario=domain: one
 * of the three clients of the Domain's multiplayer test (see {@link DomainMultiplayerServer}). Shooter presses the
 * Domain key once the blindfold is on, again while it cools down, and once more when it is ready again, then leaves the
 * game in the middle of that one; Victim presses it too (it is not theirs), and walks forwards all through the void;
 * everyone photographs both Domains from where they are, at the same moments, and the others leave once the second is
 * over.
 */
public class DomainMultiplayerClient implements ClientModInitializer {
	private static String role;
	private static int ticks;
	private static int worn = -1;
	private static int pressed;
	private static int lastId = -1;
	private static int round;
	private static int lastAge = -1;
	private static int endedAt = -1;
	private static boolean seen;
	private static final Set<String> TAKEN = new HashSet<>();

	@Override
	public void onInitializeClient() {
		String mode = System.getProperty("shootingstar.selftest", "");
		if (!mode.startsWith("mp-") || !DomainMultiplayerServer.scenario()) {
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
		ClientTickEvents.END_CLIENT_TICK.register(DomainMultiplayerClient::tick);
	}

	private static void tick(MinecraftClient client) {
		TestWindow.keepToItself(client);
		if (client.player == null || client.world == null) {
			return;
		}
		ticks++;
		boolean wearing = BlindfoldItem.wearing(client.player);
		if (wearing && worn < 0) {
			worn = ticks;
		}
		ClientDomain domain = ClientDomains.all().isEmpty() ? null : ClientDomains.all().iterator().next();
		if (domain != null && domain.id != lastId) {
			lastId = domain.id;
			round++;
			lastAge = -1;
			endedAt = -1;
			seen = true;
			ShootingStar.LOGGER.info("[mptest] {} sees Domain {} (round {}), cast by them: {}", role, domain.id, round, domain.mine);
		}
		if ("shooter".equals(role)) {
			// The first, a while after the blindfold went on; again 40 ticks after it ended (it should be cooling down);
			// and once more when the server has made it ready again.
			if (pressed == 0 && worn >= 0 && ticks == worn + 160) {
				press(client, "first");
			} else if (pressed == 1 && round == 1 && endedAt >= 0 && ticks == endedAt + 40) {
				press(client, "while it cools down");
			} else if (pressed == 2 && !client.player.getItemCooldownManager().isCoolingDown(Gojolander.BLINDFOLD)) {
				press(client, "ready again");
			}
		}
		if ("victim".equals(role) && pressed == 0 && worn >= 0 && ticks == worn + 100) {
			press(client, "theirs is not theirs to cast");
		}
		if (domain != null) {
			int age = domain.age;
			int collapse = domain.collapse();
			int[] marks = round == 1
					? new int[] {DomainTimeline.HAND, DomainTimeline.RISE + 10, DomainTimeline.VOID + 30, collapse - 10, collapse + 12}
					: new int[] {DomainTimeline.VOID + 30, DomainTimeline.VOID + 52};
			for (int mark : marks) {
				if (lastAge < mark && age >= mark && age - mark < 3) {
					shot(client, "r" + round + "_age" + mark);
					ShootingStar.LOGGER.info("[mptest] {} at age {}: holds me {}, held here {}, caught {}, shattered {}", role, mark,
							domain.holdsMe, ClientDomains.heldHere(client.player), domain.caught.size(), domain.shattered);
				}
			}
			// Held, they try to walk away all through the void (the server checks they did not get anywhere).
			if ("victim".equals(role) && round == 1) {
				client.options.forwardKey.setPressed(age >= DomainTimeline.CLOSE + 10 && age < collapse - 2);
			}
			// The caster leaves in the middle of the second.
			if ("shooter".equals(role) && round == 2 && age == DomainTimeline.VOID + 40) {
				ShootingStar.LOGGER.info("[mptest] shooter leaves in the middle of the void");
				client.scheduleStop();
			}
			lastAge = age;
		} else if (seen && endedAt < 0) {
			endedAt = ticks;
			client.options.forwardKey.setPressed(false);
			ShootingStar.LOGGER.info("[mptest] {} sees round {} over", role, round);
		}
		if (endedAt >= 0 && ticks == endedAt + 30) {
			shot(client, "r" + round + "_after");
		}
		if (!"shooter".equals(role) && round == 2 && endedAt >= 0 && ticks == endedAt + 80) {
			ShootingStar.LOGGER.info("[mptest] {} leaving", role);
			client.scheduleStop();
		}
	}

	/** The Domain key, pressed as a player would (so it goes the way the key does, not round it). */
	private static void press(MinecraftClient client, String why) {
		pressed++;
		ShootingStar.LOGGER.info("[mptest] {} presses the Domain key ({})", role, why);
		KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(GojolanderClient.DOMAIN.getBoundKeyTranslationKey()));
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
