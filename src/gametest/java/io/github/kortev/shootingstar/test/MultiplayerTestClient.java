package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.client.gap.ClientGap;
import io.github.kortev.shootingstar.client.gap.ClientGaps;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.registry.ModItems;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Hand;

/**
 * With -Dshootingstar.selftest=mp-shooter, mp-victim or mp-netherling: one of the three clients of the multiplayer
 * test (see {@link MultiplayerTestServer}). The shooter turns the key once the server has handed it to them, and turns
 * it again in the black; the victim respawns when the burst has swallowed them; everyone photographs the event from
 * where they are, at the same moments, and the gathering and the rebuild from behind themselves, so the others show;
 * and once each is home they leave.
 */
public class MultiplayerTestClient implements ClientModInitializer {
	private static String role;
	private static int ticks;
	private static int holding;
	private static boolean fired;
	private static int deadFor;
	private static boolean wasFloating;
	private static int homeAt = -1;
	private static int lastAge = -1;
	private static double lastRebuild = -1.0;
	private static final Set<String> TAKEN = new HashSet<>();

	@Override
	public void onInitializeClient() {
		String mode = System.getProperty("shootingstar.selftest", "");
		if (!mode.startsWith("mp-")) {
			return;
		}
		role = mode.substring(3);
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(14 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[mptest] {} timed out", role);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-mptest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ClientTickEvents.END_CLIENT_TICK.register(MultiplayerTestClient::tick);
	}

	private static void tick(MinecraftClient client) {
		TestWindow.keepToItself(client);
		if (client.player == null || client.world == null) {
			return;
		}
		ticks++;
		// Swallowed: back in a moment, as anyone would.
		if (client.player.isDead() || client.currentScreen instanceof DeathScreen) {
			if (++deadFor == 30) {
				shot(client, "dead");
			}
			if (deadFor > 50) {
				client.player.requestRespawn();
				client.setScreen(null);
				deadFor = 0;
			}
			return;
		}
		if ("shooter".equals(role)) {
			if (!fired && client.player.getMainHandStack().isOf(ModItems.GENESIS_KEY) && ++holding == 500) {
				ShootingStar.LOGGER.info("[mptest] shooter turns the key");
				client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				fired = true;
			}
			ClientGap mine = ClientGaps.mine();
			// Turned again once it will let the world back, and again, as anyone would, until it does.
			if (mine != null && mine.rebuildAt < 0 && mine.age >= GapTimeline.END + 40 && (mine.age - GapTimeline.END) % 40 == 0) {
				ShootingStar.LOGGER.info("[mptest] shooter turns the key to let reality back in");
				client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
			}
		}
		ClientGap gap = ClientGaps.all().isEmpty() ? null : ClientGaps.all().iterator().next();
		if (gap != null) {
			int age = gap.age;
			for (int mark : new int[] {GapTimeline.INBOUND + 10, GapTimeline.CONTACT + 4, GapTimeline.BLAST + 25, GapTimeline.ERASURE + 40,
					GapTimeline.NOTHING + 40, GapTimeline.NOTHING + 140}) {
				// Only as the moment passes (not all at once for someone who comes in partway through).
				if (lastAge < mark && age >= mark && age - mark < 3) {
					if (mark == GapTimeline.NOTHING + 40 && !"shooter".equals(role)) {
						// From behind themselves, so the rest of the gathering shows.
						client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
					}
					shot(client, "age" + mark);
				}
			}
			lastAge = age;
			double r = gap.rebuild(1.0F);
			for (int mark : new int[] {150, 330, 520}) {
				if (lastRebuild < mark && r >= mark) {
					shot(client, "rebuild" + mark);
				}
			}
			lastRebuild = r;
		}
		boolean floating = ClientGaps.floating();
		if (wasFloating && !floating && homeAt < 0) {
			homeAt = ticks;
			client.options.setPerspective(Perspective.FIRST_PERSON);
		}
		wasFloating = floating;
		if (homeAt >= 0 && ticks == homeAt + 60) {
			shot(client, "home");
			ShootingStar.LOGGER.info("[mptest] {} home in {} at {}", role, client.world.getRegistryKey().getValue(),
					String.format(Locale.ROOT, "%.1f %.1f %.1f", client.player.getX(), client.player.getY(), client.player.getZ()));
		}
		if (homeAt >= 0 && ticks == homeAt + 120) {
			ShootingStar.LOGGER.info("[mptest] {} leaving", role);
			client.scheduleStop();
		}
	}

	private static void shot(MinecraftClient client, String name) {
		if (!TAKEN.add(name)) {
			return;
		}
		String file = String.format(Locale.ROOT, "%s_%02d_%s.png", role, TAKEN.size(), name);
		ShootingStar.LOGGER.info("[mptest] {} screenshot {} at {} {} {} floating {}", role, file,
				String.format(Locale.ROOT, "%.1f", client.player.getX()), String.format(Locale.ROOT, "%.2f", client.player.getY()),
				String.format(Locale.ROOT, "%.1f", client.player.getZ()), ClientGaps.floating());
		ScreenshotRecorder.saveScreenshot(client.runDirectory, file, client.getFramebuffer(), message -> {
		});
	}
}
