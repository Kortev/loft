package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.TntEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;

/**
 * With -Dshootingstar.selftest=film-kortev, film-woomerd or film-gerald: films one episode of the Genesis Key series from
 * one player's eyes, with that player's HUD, chat and hands. Three players are on the server: Kortev, Woomerd (his
 * enemy, the mean one) and gerald_2009 (a random who joins at the worst moment). The client is whichever one the mode
 * names; the other two are players the server makes for the scene (as the game tests do) and moves itself. Everything
 * happens at the same tick in all three films, so the three can be cut together:
 *
 * <p>Woomerd blows up Kortev's cabin with TNT and runs back to his base; gerald_2009 joins; Kortev takes out the
 * Genesis Key and turns it on the base. The block comes down on Woomerd (swallowed by Kortev); he comes back at the
 * spawn beside gerald_2009 as the black spreads; all three end up in the void; Kortev turns the cracked key, the world
 * comes back, and where the base was is a hole to the void.
 *
 * <p>Ticks: {@code s} counts from the start of the recording; once the key turns, the event's own age; then ticks
 * since the cracked key was turned ({@code r}), and since everyone was sent home ({@code h}).
 */
public class FilmSelfTest implements ClientModInitializer {
	enum Role {
		KORTEV("Kortev"), WOOMERD("Woomerd"), GERALD("gerald_2009");

		final String name;

		Role(String name) {
			this.name = name;
		}
	}

	/** When the key turns, and the cracked key is first tried, as the scene's ticks and the event's age. */
	private static final int KEY_TURN = 360;
	private static final int RELEASE_FROM = 840;
	/** Ticks after the cracked key is turned that everyone has been sent home (the rebuild's end, and a little more). */
	private static final int HOME = GapTimeline.REBUILD_END + 20;
	/** How long the recording goes on once everyone is home. */
	private static final int AFTER = 240;
	/** Woomerd's run back to his base. */
	private static final int RUN_FROM = 106;
	private static final int RUN_TO = 228;
	private static final int[] TNT_AT = {52, 60, 68};
	private static final int IGNITE = 100;
	private static final int GERALD_JOINS = 200;
	private static final int KEY_OUT = 284;

	private record Line(int at, Role who, String text) {
	}

	/** The chat, before the key turns (by s), during the event (by its age), the rebuild (by r) and after (by h). */
	private static final List<Line> BEFORE = List.of(
			new Line(15, Role.WOOMERD, "nice house kortev"),
			new Line(40, Role.KORTEV, "thanks?"),
			new Line(64, Role.KORTEV, "wait"),
			new Line(78, Role.KORTEV, "what are you doing"),
			new Line(92, Role.WOOMERD, "nothing :)"),
			new Line(104, Role.KORTEV, "NO"),
			new Line(116, Role.WOOMERD, "lol"),
			new Line(190, Role.WOOMERD, "OOPS"),
			new Line(212, Role.WOOMERD, "skill issue"),
			new Line(234, Role.GERALD, "hi guys"),
			new Line(252, Role.GERALD, "what was that boom"),
			new Line(268, Role.KORTEV, "ok."),
			new Line(298, Role.WOOMERD, "whats that"),
			new Line(318, Role.KORTEV, "nice base woomerd"),
			new Line(340, Role.WOOMERD, "ur not gonna do anything lmao"));
	private static final List<Line> DURING = List.of(
			new Line(45, Role.WOOMERD, "lol what was that"),
			new Line(110, Role.WOOMERD, "nothing happened"),
			new Line(170, Role.GERALD, "uh"),
			new Line(205, Role.GERALD, "what did he do"),
			new Line(260, Role.WOOMERD, "its broken lol"),
			new Line(300, Role.KORTEV, "wait for it"),
			new Line(345, Role.GERALD, "LOOK UP"),
			new Line(380, Role.WOOMERD, "wait"),
			new Line(410, Role.WOOMERD, "WAIT WAIT WAIT"),
			new Line(470, Role.GERALD, "WOOMERD???"),
			new Line(525, Role.WOOMERD, "BRO"),
			new Line(560, Role.GERALD, "RUN"),
			new Line(600, Role.WOOMERD, "WHAT IS THAT"),
			new Line(680, Role.WOOMERD, "where are we"),
			new Line(710, Role.GERALD, "where is everything"),
			new Line(745, Role.GERALD, "i literally just joined"),
			new Line(780, Role.WOOMERD, "KORTEV WHAT DID YOU DO"),
			new Line(815, Role.KORTEV, "relax"));
	private static final List<Line> REBUILD = List.of(
			new Line(90, Role.GERALD, "woah"),
			new Line(220, Role.WOOMERD, "is that a tree"),
			new Line(360, Role.GERALD, "its coming back"));
	private static final List<Line> AFTERWARDS = List.of(
			new Line(30, Role.WOOMERD, "wait"),
			new Line(70, Role.WOOMERD, "WHERE IS MY BASE"),
			new Line(110, Role.KORTEV, "what base"),
			new Line(150, Role.GERALD, "gg"));

	private enum Stage { WAIT_WORLD, SETUP, SETTLE, RUN, DONE, FINISHED }

	private static Role me;
	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;

	// Shared between the client and the integrated server (one JVM).
	@Nullable
	private static volatile FilmScene scene;
	private static volatile boolean ready;
	private static volatile boolean running;
	private static volatile int s = -1;
	private static volatile int age = -1;
	private static volatile int released = -1;
	private static volatile boolean finished;

	// Server thread only.
	private static final Set<String> SAID = new HashSet<>();
	private static int deadAt = -1;
	private static boolean geraldIn;

	// Client thread only.
	private static final Set<String> SHOT = new HashSet<>();
	private static final Set<String> ACTED = new HashSet<>();
	private static int deadFor;
	private static int lastTry = -1000;

	public static boolean active() {
		return me != null;
	}

	@Override
	public void onInitializeClient() {
		String mode = System.getProperty("shootingstar.selftest", "");
		if (!mode.startsWith("film-")) {
			return;
		}
		me = Role.valueOf(mode.substring(5).toUpperCase(Locale.ROOT));
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(115 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[film] timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-film-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Capture.serverTickEnd();
			if (running) {
				s++;
				serverTick(server);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(FilmSelfTest::clientTick);
	}

	// =============================================================================================
	// Client
	// =============================================================================================

	private static void clientTick(MinecraftClient client) {
		ticks++;
		TestWindow.keepToItself(client);
		IntegratedServer server = client.getServer();
		switch (stage) {
			case WAIT_WORLD -> {
				if (client.world != null && client.player != null && server != null) {
					ShootingStar.LOGGER.info("[film] joined as {}", client.player.getGameProfile().getName());
					stage = Stage.SETUP;
					ticks = 0;
				}
			}
			case SETUP -> {
				if (ticks < 40) {
					return;
				}
				client.options.getFov().setValue(80);
				client.options.getGuiScale().setValue(0);
				client.options.getAutoJump().setValue(true);
				client.options.setPerspective(Perspective.FIRST_PERSON);
				client.options.hudHidden = false;
				client.onResolutionChanged();
				server.execute(() -> setUp(server));
				stage = Stage.SETTLE;
				ticks = 0;
			}
			case SETTLE -> {
				// Long enough for the scene's chunks to be built round the new place, in software.
				if (ready && ticks >= 900) {
					ShootingStar.LOGGER.info("[film] rolling");
					shot(client, "00_ready");
					Capture.start(client, client.runDirectory.toPath().resolve("capture"));
					running = true;
					stage = Stage.RUN;
					ticks = 0;
				}
			}
			case RUN -> {
				act(client);
				if (finished || ticks > 4200) {
					ShootingStar.LOGGER.info("[film] cut ({})", finished ? "finished" : "too long");
					Capture.stop();
					stage = Stage.DONE;
				}
			}
			case DONE -> {
				ShootingStar.LOGGER.info("[film] done");
				stage = Stage.FINISHED;
				client.scheduleStop();
			}
			case FINISHED -> {
			}
		}
	}

	/** What the client's own player does this tick: where they look, walk, what they hold and use. */
	private static void act(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		FilmScene sc = scene;
		if (player == null || sc == null || s < 0) {
			return;
		}
		int now = s;
		stills(client, now);
		// Swallowed: back at the spawn a few seconds later, as anyone would.
		if (player.isDead() || client.currentScreen instanceof DeathScreen) {
			if (++deadFor == 70) {
				player.requestRespawn();
				client.setScreen(null);
				deadFor = 0;
			}
			return;
		}
		deadFor = 0;
		Function<Role, Vec3d> eyes = role -> {
			if (client.world == null) {
				return null;
			}
			for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
				if (other.getGameProfile().getName().equals(role.name)) {
					return other.getEyePos();
				}
			}
			return null;
		};
		// The shooter's head is the event's while it has the camera.
		boolean held = me == Role.KORTEV && age >= 0 && age < GapTimeline.RETURN && released < 0
				|| me == Role.KORTEV && released >= 0 && now - released < HOME;
		if (!held) {
			Vec3d at = look(me, now, age, eyes);
			if (at != null) {
				turnTowards(player, player.getEyePos(), at);
			}
		}
		if (me == Role.WOOMERD) {
			for (int at : TNT_AT) {
				if (once(now, at, "tnt" + at)) {
					player.swingHand(Hand.MAIN_HAND);
				}
			}
			if (once(now, IGNITE - 12, "flint")) {
				player.getInventory().selectedSlot = 1;
			}
			if (once(now, IGNITE, "ignite")) {
				player.swingHand(Hand.MAIN_HAND);
			}
			if (once(now, RUN_FROM, "sword")) {
				player.getInventory().selectedSlot = 2;
			}
			boolean run = now >= RUN_FROM && age < 0 && player.getPos().distanceTo(sc.woomerdAtBase) > 0.7;
			client.options.forwardKey.setPressed(run);
			client.options.sprintKey.setPressed(run);
			if (run) {
				player.setSprinting(true);
			}
		}
		if (me == Role.KORTEV) {
			if (once(now, KEY_OUT, "key out")) {
				player.getInventory().selectedSlot = 0;
			}
			if (once(now, KEY_TURN, "key")) {
				aimAt(player, player.getEyePos(), Vec3d.ofCenter(sc.target));
				ShootingStar.LOGGER.info("[film] Kortev turns the key at {}", now);
				client.interactionManager.interactItem(player, Hand.MAIN_HAND);
			}
			// The cracked key, turned in the black until it lets the world back.
			if (released < 0 && age >= RELEASE_FROM && ticks - lastTry >= 20 && player.getMainHandStack().isOf(ModItems.GENESIS_KEY)) {
				lastTry = ticks;
				ShootingStar.LOGGER.info("[film] Kortev turns the cracked key at age {}", age);
				client.interactionManager.interactItem(player, Hand.MAIN_HAND);
			}
		}
	}

	/** True the first tick at or after {@code at}. */
	private static boolean once(int now, int at, String what) {
		return now >= at && ACTED.add(what);
	}

	/** Stills for looking the film over without the video. */
	private static void stills(MinecraftClient client, int now) {
		for (int at : new int[] {0, 40, 70, 100, 140, 185, 230, 300, 355}) {
			if (now >= at && age < 0) {
				shot(client, String.format(Locale.ROOT, "s%03d", at));
			}
		}
		if (age >= 0 && released < 0) {
			for (int at : new int[] {20, 45, 120, 340, 430, 450, 470, 520, 580, 660, 760, 860}) {
				if (age >= at) {
					shot(client, String.format(Locale.ROOT, "a%03d", at));
				}
			}
		}
		if (released >= 0) {
			int r = now - released;
			for (int at : new int[] {60, 200, 400, 600}) {
				if (r >= at && r < HOME) {
					shot(client, String.format(Locale.ROOT, "r%03d", at));
				}
			}
			for (int at : new int[] {20, 80, 160}) {
				if (r >= HOME + at) {
					shot(client, String.format(Locale.ROOT, "h%03d", at));
				}
			}
		}
	}

	private static void shot(MinecraftClient client, String name) {
		String file = me.name().toLowerCase(Locale.ROOT) + "_" + name + ".png";
		if (!SHOT.add(file)) {
			return;
		}
		ShootingStar.LOGGER.info("[film] screenshot {}", file);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, file, client.getFramebuffer(), message -> {
		});
	}

	/** Turns the client's player a step towards {@code at}, easing in, as a person turns. */
	private static void turnTowards(ClientPlayerEntity player, Vec3d eye, Vec3d at) {
		float[] want = angles(eye, at);
		float dy = MathHelper.wrapDegrees(want[0] - player.getYaw());
		float dp = want[1] - player.getPitch();
		player.setYaw(player.getYaw() + MathHelper.clamp(dy * 0.3F, -9.0F, 9.0F));
		player.setPitch(player.getPitch() + MathHelper.clamp(dp * 0.3F, -6.0F, 6.0F));
	}

	private static void aimAt(ClientPlayerEntity player, Vec3d eye, Vec3d at) {
		float[] want = angles(eye, at);
		player.setYaw(want[0]);
		player.setPitch(want[1]);
	}

	private static float[] angles(Vec3d eye, Vec3d at) {
		Vec3d d = at.subtract(eye);
		float yaw = (float) (MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * MathHelper.DEGREES_PER_RADIAN);
		return new float[] {yaw, MathHelper.clamp(pitch, -89.0F, 89.0F)};
	}

	// =============================================================================================
	// Who looks where, when (the same for the client's player and the server's)
	// =============================================================================================

	@Nullable
	private static Vec3d look(Role who, int now, int eventAge, Function<Role, Vec3d> eyes) {
		FilmScene sc = scene;
		if (sc == null) {
			return null;
		}
		Vec3d target = Vec3d.ofCenter(sc.target);
		Vec3d wall = new Vec3d(sc.house.getX() + 4.5, sc.house.getY() + 0.5, sc.house.getZ() + 0.5);
		Vec3d middle = Vec3d.ofCenter(sc.house).lerp(Vec3d.ofCenter(sc.base), 0.5).add(0.0, 2.0, 0.0);
		Vec3d kortev = orElse(eyes.apply(Role.KORTEV), sc.kortev.add(0.0, 1.62, 0.0));
		Vec3d woomerd = orElse(eyes.apply(Role.WOOMERD), sc.woomerdAtBase.add(0.0, 1.62, 0.0));
		int r = released >= 0 ? now - released : -1;
		if (r >= HOME) {
			// Home: everyone looks at where the base was; Kortev, then, at Woomerd.
			return who == Role.KORTEV && r - HOME > 100 ? woomerd : target.add(0.0, -12.0, 0.0);
		}
		if (r >= 0) {
			// The tree growing out of the hole.
			return target.add(0.0, 45.0, 0.0);
		}
		if (eventAge >= GapTimeline.NOTHING) {
			// In the void: at the one who did it; Kortev, at Woomerd and then at the hole.
			if (who == Role.KORTEV) {
				return eventAge < 790 ? woomerd : target.add(0.0, -6.0, 0.0);
			}
			return kortev;
		}
		if (eventAge >= 0) {
			Vec3d beam = kortev.add(0.0, Math.min(60.0, (eventAge - 37) * 6.0), 0.0);
			double fall = MathHelper.clamp((eventAge - 424) / 24.0, 0.0, 1.0);
			return switch (who) {
				case KORTEV -> target;
				case WOOMERD -> eventAge < 37 ? kortev : eventAge < 100 ? beam : eventAge < 330 ? kortev
						: eventAge < 448 ? target.add(0.0, MathHelper.lerp(fall, 60.0, 6.0), 0.0)
						: target.add(0.0, eventAge < 548 ? 20.0 : 6.0, 0.0);
				case GERALD -> eventAge < 37 ? middle : eventAge < 100 ? beam : eventAge < 330 ? middle
						: eventAge < 448 ? target.add(0.0, MathHelper.lerp(fall, 50.0, 6.0), 0.0)
						: target.add(0.0, eventAge < 548 ? 20.0 : 4.0, 0.0);
			};
		}
		return switch (who) {
			case KORTEV -> now < TNT_AT[0] ? woomerd : now < IGNITE ? wall : now < 172 ? woomerd : now < 200 ? wall.add(0.0, 1.0, 0.0)
					: now < 330 ? woomerd : target;
			case WOOMERD -> now < RUN_FROM ? sc.tnt[Math.min(2, Math.max(0, (now - TNT_AT[0]) / 8))].toCenterPos()
					: now < RUN_TO ? sc.woomerdAtBase.add(0.0, 1.62, 0.0) : kortev;
			case GERALD -> now < 260 ? Vec3d.ofCenter(sc.house).add(0.0, 2.0, 0.0) : now < 320 ? middle : kortev;
		};
	}

	private static Vec3d orElse(@Nullable Vec3d v, Vec3d fallback) {
		return v != null ? v : fallback;
	}

	// =============================================================================================
	// Server
	// =============================================================================================

	private static void setUp(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		GameRules rules = world.getGameRules();
		rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		rules.get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		rules.get(GameRules.DO_MOB_SPAWNING).set(false, server);
		rules.get(GameRules.SPAWN_RADIUS).set(0, server);
		rules.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(true, server);
		world.setTimeOfDay(2500);
		world.setWeather(24000, 0, false, false);
		FilmScene sc = FilmScene.build(world);
		scene = sc;
		for (Role role : Role.values()) {
			if (role == Role.GERALD && me != Role.GERALD) {
				continue;
			}
			ServerPlayerEntity player = role == me ? server.getPlayerManager().getPlayerList().get(0) : join(server, world, role);
			dress(player, role);
			put(world, player, start(sc, role), look(role, 0, -1, r -> null));
		}
		ready = true;
		ShootingStar.LOGGER.info("[film] set up as {}", me);
	}

	private static Vec3d start(FilmScene sc, Role role) {
		return switch (role) {
			case KORTEV -> sc.kortev;
			case WOOMERD -> sc.woomerdAtHouse;
			case GERALD -> sc.gerald;
		};
	}

	/** A player the server makes and moves itself, connected as the game tests' are. */
	private static ServerPlayerEntity join(MinecraftServer server, ServerWorld world, Role role) {
		GameProfile profile = new GameProfile(Uuids.getOfflinePlayerUuid(role.name), role.name);
		ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
		ServerPlayerEntity player = new ServerPlayerEntity(server, world, profile, data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerManager().onPlayerConnect(connection, player, data);
		ShootingStar.LOGGER.info("[film] {} joined", role.name);
		return player;
	}

	/** What each of them has on them: Kortev a survivor's kit and the key; Woomerd TNT and netherite; gerald nothing. */
	private static void dress(ServerPlayerEntity player, Role role) {
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		player.setHealth(player.getMaxHealth());
		player.getHungerManager().setFoodLevel(20);
		switch (role) {
			case KORTEV -> {
				player.getInventory().setStack(0, new ItemStack(ModItems.GENESIS_KEY));
				player.getInventory().setStack(1, new ItemStack(Items.DIAMOND_SWORD));
				player.getInventory().setStack(2, new ItemStack(Items.DIAMOND_PICKAXE));
				player.getInventory().setStack(3, new ItemStack(Items.IRON_AXE));
				player.getInventory().setStack(4, new ItemStack(Items.COOKED_BEEF, 23));
				player.getInventory().setStack(5, new ItemStack(Items.OAK_LOG, 48));
				player.getInventory().setStack(6, new ItemStack(Items.TORCH, 31));
				player.getInventory().setStack(7, new ItemStack(Items.WATER_BUCKET));
				player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				player.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				player.equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
				player.setExperienceLevel(27);
				select(player, Role.KORTEV, 1);
			}
			case WOOMERD -> {
				player.getInventory().setStack(0, new ItemStack(Items.TNT, 3));
				player.getInventory().setStack(1, new ItemStack(Items.FLINT_AND_STEEL));
				player.getInventory().setStack(2, new ItemStack(Items.NETHERITE_SWORD));
				player.getInventory().setStack(3, new ItemStack(Items.GOLDEN_APPLE, 4));
				player.getInventory().setStack(4, new ItemStack(Items.COBBLESTONE, 64));
				player.getInventory().setStack(5, new ItemStack(Items.ENDER_PEARL, 7));
				player.getInventory().setStack(6, new ItemStack(Items.TNT, 12));
				player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
				player.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
				player.equipStack(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
				player.setExperienceLevel(41);
				select(player, Role.WOOMERD, 0);
			}
			case GERALD -> {
				player.setExperienceLevel(0);
				select(player, Role.GERALD, 0);
			}
		}
	}

	/** Puts a player in the hotbar slot, and (for the server's own players) shows the others what is in their hand. */
	private static void select(ServerPlayerEntity player, Role role, int slot) {
		player.getInventory().selectedSlot = slot;
		if (role == me) {
			player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(slot));
		} else {
			player.getServerWorld().getChunkManager().sendToOtherNearbyPlayers(player,
					new EntityEquipmentUpdateS2CPacket(player.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, player.getMainHandStack().copy()))));
		}
	}

	/** Stands a player at {@code feet}, looking at {@code at}. */
	private static void put(ServerWorld world, ServerPlayerEntity player, Vec3d feet, @Nullable Vec3d at) {
		float[] a = at == null ? new float[] {player.getYaw(), player.getPitch()} : angles(feet.add(0.0, player.getStandingEyeHeight(), 0.0), at);
		player.teleport(world, feet.x, feet.y, feet.z, a[0], a[1]);
		player.setHeadYaw(a[0]);
		player.networkHandler.syncWithPlayerPosition();
	}

	private static ServerPlayerEntity player(MinecraftServer server, Role role) {
		return server.getPlayerManager().getPlayer(role.name);
	}

	private static void serverTick(MinecraftServer server) {
		FilmScene sc = scene;
		ServerWorld world = server.getOverworld();
		int now = s;
		GapManager.Gap gap = GapManager.active().isEmpty() ? null : GapManager.active().get(0);
		if (gap != null && released < 0) {
			age = gap.age();
		}
		ServerPlayerEntity kortev = player(server, Role.KORTEV);
		ServerPlayerEntity woomerd = player(server, Role.WOOMERD);

		// The chat.
		if (age < 0) {
			say(server, BEFORE, now, "b");
		} else if (released < 0) {
			say(server, DURING, age, "d");
		}
		if (released >= 0) {
			int r = now - released;
			say(server, REBUILD, r, "r");
			if (r >= HOME) {
				say(server, AFTERWARDS, r - HOME, "h");
			}
			if (r >= HOME + AFTER) {
				finished = true;
			}
		}

		// Woomerd's TNT against the cabin's east wall, lit, and his run home.
		for (int i = 0; i < TNT_AT.length; i++) {
			if (now == TNT_AT[i]) {
				BlockPos p = sc.tnt[i];
				world.setBlockState(p, Blocks.TNT.getDefaultState(), 3);
				world.playSound(null, p, SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1.0F, 0.8F);
				if (woomerd != null) {
					ItemStack tnt = woomerd.getInventory().getStack(0);
					tnt.decrement(1);
					if (me != Role.WOOMERD) {
						woomerd.swingHand(Hand.MAIN_HAND, true);
						select(woomerd, Role.WOOMERD, 0);
					}
				}
			}
		}
		if (now == IGNITE - 12 && woomerd != null && me != Role.WOOMERD) {
			select(woomerd, Role.WOOMERD, 1);
		}
		if (now == IGNITE) {
			world.playSound(null, sc.tnt[1], SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
			for (BlockPos p : sc.tnt) {
				world.setBlockState(p, Blocks.AIR.getDefaultState(), 11);
				TntEntity tnt = new TntEntity(world, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, woomerd);
				world.spawnEntity(tnt);
				world.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0F, 1.0F);
			}
			if (woomerd != null && me != Role.WOOMERD) {
				woomerd.swingHand(Hand.MAIN_HAND, true);
			}
		}
		if (now == RUN_FROM && woomerd != null && me != Role.WOOMERD) {
			select(woomerd, Role.WOOMERD, 2);
		}

		// gerald_2009 joins.
		if (now == GERALD_JOINS && !geraldIn) {
			geraldIn = true;
			if (me == Role.GERALD) {
				server.getPlayerManager().broadcast(Text.translatable("multiplayer.player.joined", Role.GERALD.name).formatted(Formatting.YELLOW), false);
			} else {
				ServerPlayerEntity gerald = join(server, world, Role.GERALD);
				dress(gerald, Role.GERALD);
				put(world, gerald, sc.gerald, Vec3d.ofCenter(sc.house));
			}
		}

		// Kortev takes out the key and turns it on the base.
		if (now == KEY_OUT && kortev != null && me != Role.KORTEV) {
			select(kortev, Role.KORTEV, 0);
		}
		if (now == KEY_TURN && kortev != null && me != Role.KORTEV) {
			Vec3d eye = kortev.getEyePos();
			float[] a = angles(eye, Vec3d.ofCenter(sc.target));
			kortev.setYaw(a[0]);
			kortev.setPitch(a[1]);
			kortev.setHeadYaw(a[0]);
			kortev.swingHand(Hand.MAIN_HAND, true);
			kortev.getMainHandStack().use(world, kortev, Hand.MAIN_HAND);
			ShootingStar.LOGGER.info("[film] Kortev turns the key at {}", now);
		}
		if (released < 0 && age >= RELEASE_FROM && (age - RELEASE_FROM) % 20 == 0 && kortev != null && me != Role.KORTEV
				&& kortev.getMainHandStack().isOf(ModItems.GENESIS_KEY)) {
			kortev.swingHand(Hand.MAIN_HAND, true);
			kortev.getMainHandStack().use(world, kortev, Hand.MAIN_HAND);
			ShootingStar.LOGGER.info("[film] Kortev turns the cracked key at age {}", age);
		}
		// Let go: the cracked key has shattered.
		if (released < 0 && age >= RELEASE_FROM && kortev != null && !hasKey(kortev)) {
			released = now;
			ShootingStar.LOGGER.info("[film] the world comes back from {} (age {})", now, age);
		}

		// Woomerd swallowed, and back a few seconds later (the client's own does it from the death screen).
		if (woomerd != null && !woomerd.isAlive() && me != Role.WOOMERD) {
			if (deadAt < 0) {
				deadAt = now;
			} else if (now - deadAt == 70) {
				ServerPlayerEntity back = server.getPlayerManager().respawnPlayer(woomerd, false, Entity.RemovalReason.KILLED);
				ShootingStar.LOGGER.info("[film] Woomerd respawned at {}", back.getBlockPos().toShortString());
				deadAt = -1;
			}
		}

		// The server's own players: where they stand (until the event takes over) and where they look.
		Function<Role, Vec3d> eyes = role -> {
			ServerPlayerEntity p = player(server, role);
			return p == null ? null : p.getEyePos();
		};
		for (Role role : Role.values()) {
			if (role == me) {
				continue;
			}
			ServerPlayerEntity p = player(server, role);
			if (p == null || !p.isAlive()) {
				continue;
			}
			if (age < 0) {
				Vec3d feet = feet(world, sc, role, now);
				if (feet != null) {
					p.refreshPositionAndAngles(feet.x, feet.y, feet.z, p.getYaw(), p.getPitch());
					p.setSprinting(role == Role.WOOMERD && now >= RUN_FROM && now < RUN_TO);
				}
			}
			Vec3d at = look(role, now, age, eyes);
			if (at != null) {
				float[] want = angles(p.getEyePos(), at);
				float yaw = p.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(want[0] - p.getYaw()) * 0.3F, -9.0F, 9.0F);
				float pitch = p.getPitch() + MathHelper.clamp((want[1] - p.getPitch()) * 0.3F, -6.0F, 6.0F);
				p.setYaw(yaw);
				p.setPitch(pitch);
				p.setHeadYaw(yaw);
				p.setBodyYaw(yaw);
			}
		}
	}

	/** Where a server-moved player stands before the event: Woomerd runs from the cabin to his base; the others stay. */
	@Nullable
	private static Vec3d feet(ServerWorld world, FilmScene sc, Role role, int now) {
		return switch (role) {
			case KORTEV -> sc.kortev;
			case GERALD -> now >= GERALD_JOINS ? sc.gerald : null;
			case WOOMERD -> {
				double f = MathHelper.clamp((now - RUN_FROM) / (double) (RUN_TO - RUN_FROM), 0.0, 1.0);
				Vec3d p = sc.woomerdAtHouse.lerp(sc.woomerdAtBase, f);
				yield new Vec3d(p.x, FilmScene.ground(world, MathHelper.floor(p.x), MathHelper.floor(p.z)), p.z);
			}
		};
	}

	private static boolean hasKey(ServerPlayerEntity player) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			if (player.getInventory().getStack(i).isOf(ModItems.GENESIS_KEY)) {
				return true;
			}
		}
		return false;
	}

	/** Says the lines due by {@code now}, each once, as chat from whoever says them. */
	private static void say(MinecraftServer server, List<Line> lines, int now, String part) {
		for (Line line : lines) {
			if (now >= line.at() && SAID.add(part + line.at() + line.who())) {
				server.getPlayerManager().broadcast(Text.literal("<" + line.who().name + "> " + line.text()), false);
			}
		}
	}
}
