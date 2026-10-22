package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.advancement.AdvancementEntry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * With -Dshootingstar.selftest=film-kortev, film-woomerd or film-gerald: films one episode of the Genesis Key series from
 * one player's eyes, with that player's HUD and hands. Three players are on the server: Kortev, Woomerd (Kortev's enemy,
 * the mean one) and gerald_2009 (a random who has just joined). The client is whichever one the mode names, played as a
 * person plays ({@link FilmHuman}); the other two are players the server makes for the scene, each doing what they did
 * in their own last take ({@link FilmMoves}). Nobody talks: the chat has only what the game says itself. Everything
 * happens at the same tick in all three films, so they can be cut together:
 *
 * <p>Kortev is getting the wheat in when Woomerd's TNT takes the side off the cabin. Woomerd, a safe way off, crouches
 * at them and runs home; Kortev goes straight to the chest by the farm, takes out the Genesis Key and turns it on
 * Woomerd's base. The block comes down on Woomerd (dancing about in front of the base), who comes back at the spawn just
 * as the black comes for them. gerald_2009, bridging over a lava sea in the Nether all this while, slips off the bridge,
 * and is caught falling and carried into the void with the other two. Kortev turns the cracked key, the world comes
 * back, and where the base was is a hole to the void; gerald, back on the bridge, steps off it into the lava anyway.
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

	/** The size of a Short, which the game is drawn at while filming, whatever the window's size. */
	public static final int WIDTH = 1080;
	public static final int HEIGHT = 1920;
	/**
	 * The erased zone's radius (ginnungagapRadius): the base and the ground round it go, Kortev's cabin stays, so
	 * everyone comes home to a hole where the base was. At the default 96 the cabin went too, and the hole was too big to
	 * read as anything but the sea.
	 */
	static final int RADIUS = 20;
	/** How long the scene is left once it is built before the recording starts: its chunks drawn, the toasts gone. */
	private static final int SETTLE = 600;

	// Before the key (s).
	private static final int GERALD_JOINS = 24;
	/** Woomerd: the TNT against the cabin's east wall, the flint and steel, and the getaway (to safety, then home). */
	private static final int[] TNT_AT = {46, 54, 62};
	private static final int FLINT = 70;
	private static final int IGNITE = 78;
	private static final int RUN_AWAY = 82;
	/** When the TNT goes up (its fuse is 80 ticks). */
	private static final int BOOM = IGNITE + 80;
	private static final int TAUNT_FROM = 166;
	private static final int TAUNT_TO = 198;
	private static final int RUN_HOME = 200;
	/** Kortev: off to the chest, and the key turned. */
	private static final int CHEST_GO = 192;
	private static final int KEY_TURN = 256;
	// The event (its age).
	/** gerald_2009 comes through the Nether portal at the spawn (in the Nether since joining), just before the cabin goes up. */
	private static final int ARRIVE = 146;
	/** gerald opens the inventory (the event's age; being new, while the sky does what it does), and shuts it. */
	private static final int GERALD_INV = 112;
	private static final int GERALD_INV_SHUT = 296;
	/** Woomerd, in the chest at the base, shuts it. */
	private static final int CHEST_SHUT = 300;
	/** The fewest ticks between two blocks of the pile by the base going down. */
	private static final int PILE_GAP = 5;
	/** How much of the pile goes down before the chest (the rest after it). */
	private static final int PILE_FIRST = 10;
	/** Everyone has been carried into the void by now: where each is then is where their moves there are measured from. */
	private static final int ANCHOR = GapTimeline.NOTHING + 14;
	/** Kortev turns the cracked key (and again every second until the world comes back). */
	private static final int RELEASE_FROM = 960;
	/** Ticks after the cracked key is turned that everyone has been sent home (the rebuild's end, and a little more). */
	private static final int HOME = GapTimeline.REBUILD_END + 20;
	/** How long the recording goes on once everyone is home. */
	private static final int AFTER = 360;
	/** How long someone killed lies dead before coming back. */
	private static final int RESPAWN_DELAY = 60;

	private enum Stage { WAIT_WORLD, SETUP, SETTLE, RUN, DONE, FINISHED }

	private static Role me;
	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;

	// Shared between the client and the integrated server (one JVM).
	@Nullable
	private static volatile FilmScene scene;
	@Nullable
	private static volatile FilmNether nether;
	private static volatile boolean ready;
	private static volatile boolean running;
	private static volatile int s = -1;
	private static volatile int age = -1;
	private static volatile int released = -1;
	private static volatile boolean finished;

	// Server thread only.
	private static final Map<Role, FilmMoves> TRACKS = new EnumMap<>(Role.class);
	private static final Map<Role, Vec3d> ANCHORS = new EnumMap<>(Role.class);
	private static int deadAt = -1;
	private static boolean geraldIn;

	// Client thread only.
	private static final Set<String> SHOT = new HashSet<>();
	private static final Set<String> ACTED = new HashSet<>();
	private static final FilmMoves RECORD = new FilmMoves();
	@Nullable
	private static FilmHuman human;
	private static int deadFor;
	private static int lastTry = -1000;
	/** The client's own tick in the scene: one a tick, every tick (the server's, seen from here, skip and stall). */
	private static int clientTick = -1;
	/** The film's player's look is theirs this tick (not the event's, a screen's or a death's): turned every frame. */
	private static boolean looking;
	private static boolean swung;
	// Kortev: the wheat being got in (which stalk, which step of it, since when), the chest.
	private static int job;
	private static int jobStep;
	private static int jobAt;
	private static int openedAt = -1;
	private static int closedAt = -1;
	// gerald_2009: where they make for to go in at the edge of the hole, at the end.
	@Nullable
	private static Vec3d edge;
	// Inside a screen (gerald's inventory, Woomerd's chest): ticks in it, the tick the business at the end began.
	private static int invTicks;
	// Woomerd's pile: the tick the last block of it went down (client), and the next block to put down (server, in the
	// others' takes, as Woomerd's recorded clicks come).
	private static int lastPile = -100;
	private static int pileNext;
	// Woomerd's pearl home: the look that throws it, the tick it went and where from.
	@Nullable
	private static float[] pearlAim;
	private static int pearlAt = -1;
	@Nullable
	private static Vec3d pearlFrom;
	private static int refillAt = -1;

	public static boolean active() {
		return me != null;
	}

	/** True in a film self test, from before the window opens (the mixins that size the frame ask before this loads). */
	public static boolean filming() {
		return System.getProperty("shootingstar.selftest", "").startsWith("film-");
	}

	@Override
	public void onInitializeClient() {
		String mode = System.getProperty("shootingstar.selftest", "");
		if (!mode.startsWith("film-")) {
			return;
		}
		String who = mode.substring(5).toUpperCase(Locale.ROOT);
		if (Arrays.stream(Role.values()).noneMatch(role -> role.name().equals(who))) {
			// Another film, not the series' (FilmDomain).
			return;
		}
		me = Role.valueOf(who);
		human = new FilmHuman(me.ordinal() * 7919L + 17L);
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
		FilmCursor.register();
		ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Capture.serverTickEnd();
			if (running) {
				s++;
				serverTick(server);
			}
		});
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (human != null && client.player != null) {
				human.unframe(client.player);
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
				client.options.getBobView().setValue(true);
				client.options.setPerspective(Perspective.FIRST_PERSON);
				client.options.hudHidden = false;
				client.onResolutionChanged();
				server.execute(() -> setUp(server));
				stage = Stage.SETTLE;
				ticks = 0;
			}
			case SETTLE -> {
				// Counted from when the scene is built (finding the places can take a minute): long enough for its chunks
				// to be drawn round the new place, in software, and for the toasts of everyone's kit to go.
				if (!ready) {
					ticks = 0;
				} else if (ticks >= SETTLE) {
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
				if (finished || ticks > 4800) {
					ShootingStar.LOGGER.info("[film] cut ({})", finished ? "finished" : "too long");
					Capture.stop();
					RECORD.save(me);
					stage = Stage.DONE;
				}
			}
			case DONE -> {
				ShootingStar.LOGGER.info("[film] done");
				stage = Stage.FINISHED;
				if (server != null) {
					// The server's own players go first: left on, they held the server up on its way down for good.
					server.execute(() -> {
						for (ServerPlayerEntity p : List.copyOf(server.getPlayerManager().getPlayerList())) {
							if (!p.getGameProfile().getName().equals(me.name)) {
								server.getPlayerManager().remove(p);
							}
						}
					});
				}
				// Everything the film needs is written by now: if the game has not gone in half a minute, it goes anyway.
				Thread out = new Thread(() -> {
					try {
						Thread.sleep(30_000L);
					} catch (InterruptedException e) {
						return;
					}
					ShootingStar.LOGGER.warn("[film] the game did not stop by itself; stopping it");
					Runtime.getRuntime().halt(0);
				}, "shootingstar-film-exit");
				out.setDaemon(true);
				out.start();
				client.scheduleStop();
			}
			case FINISHED -> {
			}
		}
	}

	/** Each frame, before it is drawn: the film's player's look, between ticks, while it is theirs to move. */
	public static void frame(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (looking && human != null && client.player != null && client.currentScreen == null) {
			human.frame(client.player, tickDelta);
		}
	}

	/** What the client's own player does this tick, through {@link FilmHuman}: where they look and go, what they use. */
	private static void act(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		FilmScene sc = scene;
		FilmHuman h = human;
		if (player == null || sc == null || h == null || s < 0) {
			return;
		}
		int now = ++clientTick;
		swung = false;
		looking = false;
		stills(client, now);
		int r = released >= 0 ? now - released : -1;
		if (player.isDead() || client.currentScreen instanceof DeathScreen) {
			FilmHuman.release(client);
			// Swallowed: back at the spawn a few seconds later, as anyone would. (gerald, in the lava at the end, stays dead.)
			if (++deadFor == RESPAWN_DELAY && r < 0) {
				player.requestRespawn();
				client.setScreen(null);
				deadFor = 0;
			}
		} else {
			deadFor = 0;
			// The shooter's view is the event's while it has the camera.
			boolean held = me == Role.KORTEV && (age >= 0 && age < GapTimeline.RETURN && released < 0 || r >= 0 && r < HOME);
			if (held) {
				FilmHuman.release(client);
			} else if (client.currentScreen instanceof HandledScreen<?> screen) {
				FilmHuman.release(client);
				switch (me) {
					case KORTEV -> {
						if (screen instanceof GenericContainerScreen box) {
							chest(client, player, box, now);
						}
					}
					case WOOMERD -> {
						if (screen instanceof GenericContainerScreen box) {
							woomerdChest(client, player, box);
						}
					}
					case GERALD -> geraldInventory(client, player, screen);
				}
			} else {
				h.at(now);
				switch (me) {
					case KORTEV -> kortev(client, player, h, sc, now, r);
					case WOOMERD -> woomerd(client, player, h, sc, now, r);
					case GERALD -> gerald(client, player, h, sc, now, r);
				}
				h.tick(client, player);
				looking = true;
			}
		}
		record(client, player, now, r);
	}

	private static void record(MinecraftClient client, ClientPlayerEntity player, int now, int r) {
		Identifier item = Registries.ITEM.getId(player.getMainHandStack().getItem());
		RECORD.add(new FilmMoves.Move(now, released >= 0 ? -1 : age, r, player.getWorld().getRegistryKey().getValue().toString(), player.getX(),
				player.getY(), player.getZ(), player.getYaw(), player.getPitch(), player.isSneaking(), player.isSprinting(), swung,
				player.isAlive() && !player.isDead(), player.getInventory().selectedSlot, item.toString()));
	}

	private static void swing(ClientPlayerEntity player) {
		player.swingHand(Hand.MAIN_HAND);
		swung = true;
	}

	/** True the first tick it is asked at or after {@code at}. */
	private static boolean once(int now, int at, String what) {
		return now >= at && ACTED.add(what);
	}

	@Nullable
	private static Vec3d eyes(MinecraftClient client, Role role) {
		if (client.world == null) {
			return null;
		}
		for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
			if (other.getGameProfile().getName().equals(role.name)) {
				return other.getEyePos();
			}
		}
		return null;
	}

	private static Vec3d orElse(@Nullable Vec3d v, Vec3d fallback) {
		return v != null ? v : fallback;
	}

	private static Vec3d eye(Vec3d feet) {
		return feet.add(0.0, 1.62, 0.0);
	}

	// --- Kortev ------------------------------------------------------------------------------

	/** The wheat Kortev gets in, nearest first along the path down the farm's east side: the ripe columns. */
	private static BlockPos crop(FilmScene sc, int i) {
		int row = i / 2;
		return sc.farm.add(i % 2 == 0 ? 3 : 2, 1, -3 + row);
	}

	private static void kortev(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now, int r) {
		Vec3d target = Vec3d.ofCenter(sc.target);
		Vec3d woomerd = orElse(eyes(client, Role.WOOMERD), eye(sc.woomerdSafe));
		Vec3d cabin = Vec3d.ofCenter(sc.house).add(0.0, 2.5, 0.0);
		h.slack(2.5).spam(false).sneak(false).back(false).idle(0.0).stay();
		if (age < 0) {
			if (now < BOOM) {
				farming(client, player, h, sc, now);
			} else if (now < BOOM + 6) {
				// It goes up behind them: round they spin, a step back.
				h.look(cabin.add(4.0, -0.5, 0.0)).slack(5.0).back(now < BOOM + 4);
			} else if (now < BOOM + 22) {
				// What is left of the wall.
				h.glance("boom", 6, 10, cabin.add(3.5, -1.5, 0.0), cabin.add(2.5, 0.5, 1.5), cabin.add(3.5, -2.0, -1.0)).slack(2.0);
			} else if (now < CHEST_GO) {
				// And there is Woomerd, crouching at them: a step their way.
				h.look(woomerd).track().slack(1.5).go(sc.kortev.add(1.2, 0.0, 0.6), FilmHuman.Pace.WALK, 0.3, false);
			} else if (closedAt < 0) {
				toChest(client, player, h, sc, now);
			} else {
				// The key out, and on the base.
				if (once(now, closedAt + 3, "key out")) {
					for (int k = 0; k < 9; k++) {
						if (player.getInventory().getStack(k).isOf(ModItems.GENESIS_KEY)) {
							player.getInventory().selectedSlot = k;
						}
					}
				}
				h.look(target.add(0.0, 0.6, 0.0)).slack(0.6);
				if (once(now, KEY_TURN, "key")) {
					aimAt(player, player.getEyePos(), target);
					ShootingStar.LOGGER.info("[film] Kortev turns the key at {}", now);
					FilmHuman.rightClick(client);
					swung = true;
				}
			}
			return;
		}
		if (released < 0) {
			kortevVoid(client, player, h, target);
			return;
		}
		if (r >= HOME) {
			kortevHome(client, player, h, sc, r - HOME, target);
		}
	}

	/** Getting the wheat in: to each ripe stalk along the path, a left click on it, a right click on its soil (seed back in). */
	private static void farming(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now) {
		BlockPos crop = crop(sc, job);
		BlockPos soil = crop.down();
		Vec3d stand = new Vec3d(sc.kortev.x, sc.kortev.y, crop.getZ() + 0.5 + (job % 2 == 0 ? -0.2 : 0.2));
		int t = now - jobAt;
		h.go(stand, FilmHuman.Pace.WALK, 0.35, false).slack(1.2);
		switch (jobStep) {
			case 0 -> {
				h.look(Vec3d.ofCenter(crop).add(0.0, -0.2, 0.0));
				if (t >= 4 && FilmHuman.on(client, crop, null)) {
					FilmHuman.leftClick(client);
					swung = true;
					jobStep = 1;
					jobAt = now;
				} else if (t > 40) {
					nextJob(now);
				}
			}
			case 1 -> {
				h.look(Vec3d.ofCenter(soil).add(0.0, 0.44, 0.0));
				if (t >= 3 && FilmHuman.on(client, soil, Direction.UP)) {
					FilmHuman.rightClick(client);
					swung = true;
					jobStep = 2;
					jobAt = now;
				} else if (t > 30) {
					nextJob(now);
				}
			}
			default -> {
				if (t >= 3) {
					nextJob(now);
				}
			}
		}
	}

	private static void nextJob(int now) {
		job = Math.min(job + 1, 13);
		jobStep = 0;
		jobAt = now;
	}

	/** Over to the chest, and a right click on it once the crosshair is on it. */
	private static void toChest(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now) {
		h.look(Vec3d.ofCenter(sc.chest).add(0.0, 0.2, 0.0)).slack(1.0).go(sc.atChest, FilmHuman.Pace.WALK, 0.3, false);
		if (openedAt >= 0 || ACTED.contains("open")) {
			return;
		}
		if (FilmHuman.near(player, sc.atChest, 0.7) && FilmHuman.on(client, sc.chest, null)) {
			ACTED.add("open");
			FilmHuman.rightClick(client);
			swung = true;
			ShootingStar.LOGGER.info("[film] Kortev opens the chest at {}", now);
		} else if (now >= CHEST_GO + 40) {
			ACTED.add("open");
			ShootingStar.LOGGER.warn("[film] Kortev's crosshair never found the chest: opened it anyway");
			client.interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(sc.chest), Direction.WEST, sc.chest, false));
		}
	}

	/** In the chest: the cursor to the key (its name comes up), the key into the first hotbar slot, and shut. */
	private static void chest(MinecraftClient client, ClientPlayerEntity player, GenericContainerScreen screen, int now) {
		GenericContainerScreenHandler handler = screen.getScreenHandler();
		if (openedAt < 0) {
			openedAt = now;
			FilmCursor.show(true);
			FilmCursor.place(screen.width / 2.0 + 6.0, screen.height / 2.0 + 14.0);
		}
		int t = now - openedAt;
		int key = 13;
		int hotbar = handler.getRows() * 9 + 27;
		if (t == 2) {
			FilmCursor.moveTo(slotX(screen, handler.getSlot(key)) + 2.0, slotY(screen, handler.getSlot(key)) + 3.0, 6.0);
		}
		if (t == 14) {
			client.interactionManager.clickSlot(handler.syncId, key, 0, SlotActionType.PICKUP, player);
		}
		if (t == 16) {
			FilmCursor.moveTo(slotX(screen, handler.getSlot(hotbar)) + 1.0, slotY(screen, handler.getSlot(hotbar)) + 2.0, 5.0);
		}
		if (t == 23) {
			client.interactionManager.clickSlot(handler.syncId, hotbar, 0, SlotActionType.PICKUP, player);
		}
		if (t == 25) {
			FilmCursor.moveTo(slotX(screen, handler.getSlot(hotbar)) + 14.0, slotY(screen, handler.getSlot(hotbar)) - 10.0, 4.0);
		}
		if (t >= 29) {
			FilmCursor.show(false);
			player.closeHandledScreen();
			closedAt = now;
			ShootingStar.LOGGER.info("[film] Kortev has the key at {}", now);
		}
	}

	private static double slotX(HandledScreen<?> screen, Slot slot) {
		return (screen.width - 176) / 2.0 + slot.x + 8.0;
	}

	private static double slotY(HandledScreen<?> screen, Slot slot) {
		int height = screen instanceof GenericContainerScreen box ? 114 + box.getScreenHandler().getRows() * 18 : 166;
		return (screen.height - height) / 2.0 + slot.y + 8.0;
	}

	/** The pointer on to {@code slot}, a little off its middle, over {@code ticks}. */
	private static void pointAt(HandledScreen<?> screen, int slot, double ticks) {
		Slot at = screen.getScreenHandler().getSlot(slot);
		FilmCursor.moveTo(slotX(screen, at) + 1.5, slotY(screen, at) + 2.5, ticks);
	}

	/** In the void, once the view is theirs again: taking it in, a few steps on nothing, Woomerd, gerald, the hole, the key. */
	private static void kortevVoid(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, Vec3d target) {
		if (age < GapTimeline.RETURN) {
			return;
		}
		Vec3d woomerd = orElse(eyes(client, Role.WOOMERD), target);
		Vec3d gerald = orElse(eyes(client, Role.GERALD), woomerd);
		Vec3d hole = target.add(0.0, -4.0, 0.0);
		Vec3d eye = player.getEyePos();
		double toHole = FilmHuman.angles(eye, hole)[0];
		int a = age;
		if (a < 744) {
			h.glance("void", 10, 18, far(eye, toHole + 100.0, -4.0), far(eye, toHole - 70.0, 40.0), woomerd).slack(3.0);
		} else if (a < 800) {
			// Woomerd at them, punching (nothing can be hurt here).
			h.look(woomerd).track().slack(1.2).idle(0.6);
		} else if (a < 852) {
			// Back at them.
			h.look(woomerd.add(0.0, -0.3, 0.0)).track().slack(1.0).go(woomerd.add(0.0, -1.62, 0.0), FilmHuman.Pace.WALK, 2.0, false);
			if ((a - 800) % 6 == 0 && FilmHuman.off(player, woomerd) < 12.0) {
				FilmHuman.leftClick(client);
				swung = true;
			}
		} else if (a < 905) {
			h.look(gerald).track().slack(1.5).idle(0.8);
			if (a == 884) {
				h.jump();
			}
		} else {
			h.look(hole).slack(1.0);
		}
		if (released < 0 && age >= RELEASE_FROM && ticks - lastTry >= 20 && player.getMainHandStack().isOf(ModItems.GENESIS_KEY)) {
			lastTry = ticks;
			ShootingStar.LOGGER.info("[film] Kortev turns the cracked key at age {}", age);
			FilmHuman.rightClick(client);
			swung = true;
		}
	}

	/**
	 * Home: over to the edge of the hole where the base was, on the side Woomerd comes running up to it from (the spawn's),
	 * a few blocks along from them; a look down it; then at Woomerd beside them, and the crouch back at them.
	 */
	private static void kortevHome(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int t, Vec3d target) {
		Vec3d woomerd = orElse(eyes(client, Role.WOOMERD), target);
		Vec3d middle = Vec3d.ofBottomCenter(sc.target);
		Vec3d out = new Vec3d(sc.respawn.x - middle.x, 0.0, sc.respawn.z - middle.z).normalize().rotateY(-0.2F);
		Vec3d spot = new Vec3d(middle.x + out.x * 28.0, player.getY(), middle.z + out.z * 28.0);
		if (t < 24) {
			h.look(target.add(0.0, -6.0, 0.0)).slack(4.0);
		} else if (t < 165 && !ACTED.contains("kortev at rim")) {
			if (FilmHuman.near(player, spot, 1.0) || !safeAhead(player)) {
				ACTED.add("kortev at rim");
			}
			h.look(t < 100 ? eye(spot) : target.add(0.0, -8.0, 0.0)).slack(3.0).go(spot,
					player.squaredDistanceTo(spot) > 100.0 ? FilmHuman.Pace.SPRINT : FilmHuman.Pace.WALK, 1.0, false);
		} else if (t < 190) {
			h.look(target.add(0.0, -16.0, 0.0)).slack(2.0);
		} else if (t < 240) {
			h.look(woomerd).track().slack(1.0).spam(t > 196);
		} else {
			h.look(woomerd).track().slack(1.5).idle(0.8);
		}
		if (!safeAhead(player)) {
			h.stay();
		}
	}

	/** A point well off from {@code eye}, this way. */
	private static Vec3d far(Vec3d eye, double yaw, double pitch) {
		return eye.add(Vec3d.fromPolar((float) pitch, (float) yaw).multiply(40.0));
	}

	/** The place {@code from} blocks out from the hole's middle on the player's side, to go and look down from. */
	private static Vec3d edge(ClientPlayerEntity player, FilmScene sc, double from) {
		Vec3d middle = Vec3d.ofBottomCenter(sc.target);
		Vec3d out = new Vec3d(player.getX() - middle.x, 0.0, player.getZ() - middle.z);
		out = out.lengthSquared() < 1.0E-4 ? new Vec3d(-1.0, 0.0, 0.0) : out.normalize();
		return new Vec3d(middle.x + out.x * from, player.getY(), middle.z + out.z * from);
	}

	/** Ground a step or two ahead: no fissure or hole there to walk into (a dip in the land is fine). */
	private static boolean safeAhead(ClientPlayerEntity player) {
		Vec3d v = player.getVelocity();
		if (v.horizontalLengthSquared() < 1.0E-4 || player.getWorld() == null) {
			return true;
		}
		Vec3d ahead = player.getPos().add(v.normalize().multiply(1.8));
		int top = player.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(ahead.x), MathHelper.floor(ahead.z));
		return top >= player.getY() - 10.0;
	}

	// --- Woomerd -----------------------------------------------------------------------------

	private static void woomerd(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now, int r) {
		Vec3d kortev = orElse(eyes(client, Role.KORTEV), eye(sc.atChest));
		Vec3d target = Vec3d.ofCenter(sc.target);
		Vec3d wall = new Vec3d(sc.house.getX() + 4.5, sc.house.getY() + 1.0, sc.house.getZ() + 0.5);
		h.spam(false).sneak(false).back(false).idle(0.0).slack(2.5).stay();
		if (age < 0) {
			woomerdBefore(client, player, h, sc, now, kortev, wall);
			return;
		}
		if (released < 0 && age < GapTimeline.NOTHING) {
			if (!ACTED.contains("respawned") && age < GapTimeline.CONTACT + 20) {
				woomerdBase(client, player, h, sc, now, kortev, target);
			} else {
				ACTED.add("respawned");
				woomerdFlee(h, player, target);
			}
			return;
		}
		if (released < 0) {
			woomerdVoid(client, player, h, kortev);
			return;
		}
		if (r < HOME) {
			rebuild(h, player, target, r);
			return;
		}
		woomerdHome(h, player, sc, r - HOME, kortev, target);
	}

	/** Where Woomerd stands to put down the {@code i}th TNT: square in front of it, a couple of blocks back. */
	private static Vec3d tntSpot(FilmScene sc, int i) {
		BlockPos p = sc.tnt[i];
		return new Vec3d(p.getX() + 2.8, sc.woomerdAtHouse.y, p.getZ() + 0.5);
	}

	/** The first TNT not yet put down (3: all of them). */
	private static int nextTnt() {
		for (int k = 0; k < TNT_AT.length; k++) {
			if (!ACTED.contains("tnt" + k)) {
				return k;
			}
		}
		return TNT_AT.length;
	}

	/**
	 * Before the key: up to the cabin, crouching the last of the way; the TNT down a block at a time, each with a right
	 * click on the ground in front of the wall; lit with a right click on it; away to safety to watch; the crouch at
	 * Kortev; home to the base.
	 */
	private static void woomerdBefore(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now, Vec3d kortev,
			Vec3d wall) {
		int i = nextTnt();
		if (now < TNT_AT[0] - 20) {
			Vec3d spot = tntSpot(sc, 0);
			boolean close = FilmHuman.near(player, spot, 1.2);
			h.look(now > 12 && now < 22 ? Vec3d.ofCenter(sc.farm).add(0.0, 1.5, 0.0) : wall).slack(3.0)
					.go(spot, close ? FilmHuman.Pace.SNEAK : FilmHuman.Pace.WALK, 0.25, false);
		} else if (i < TNT_AT.length) {
			BlockPos soil = sc.tnt[i].down();
			h.look(Vec3d.ofCenter(soil).add(0.0, 0.5, 0.0)).slack(0.6).go(tntSpot(sc, i), FilmHuman.Pace.WALK, 0.2, false);
			if (now >= TNT_AT[i]) {
				if (FilmHuman.on(client, soil, Direction.UP)) {
					ACTED.add("tnt" + i);
					FilmHuman.rightClick(client);
					swung = true;
				} else if (now >= TNT_AT[i] + 6) {
					ACTED.add("tnt" + i);
					ShootingStar.LOGGER.warn("[film] Woomerd's crosshair was not on TNT spot {} in time: the server puts it there", i);
				}
			}
		} else if (!ACTED.contains("ignite")) {
			if (once(now, FLINT, "flint")) {
				player.getInventory().selectedSlot = 1;
			}
			h.look(Vec3d.ofCenter(sc.tnt[1]).add(0.0, 0.1, 0.0)).slack(0.6).go(tntSpot(sc, 1), FilmHuman.Pace.WALK, 0.2, false);
			if (now >= IGNITE && !ACTED.contains("ignite")) {
				if (FilmHuman.on(client, sc.tnt[1], null)) {
					ACTED.add("ignite");
					FilmHuman.rightClick(client);
					swung = true;
				} else if (now >= IGNITE + 6) {
					ACTED.add("ignite");
					ShootingStar.LOGGER.warn("[film] Woomerd's crosshair was not on the TNT to light it: the server lights it");
				}
			}
		} else if (now < TAUNT_FROM) {
			if (once(now, RUN_AWAY, "sword")) {
				player.getInventory().selectedSlot = 2;
			}
			boolean there = FilmHuman.near(player, sc.woomerdSafe, 0.9) || ACTED.contains("safe");
			if (there) {
				ACTED.add("safe");
				if (now < BOOM) {
					h.glance("watch", 10, 24, wall.add(0.0, 1.0, 0.0), wall.add(-1.0, 3.5, 0.5), wall.add(0.0, 0.5, 1.5)).slack(2.5).idle(1.2);
				} else {
					h.look(wall.add(-1.0, 1.0, 0.0)).slack(3.0);
				}
			} else {
				h.look(eye(sc.woomerdSafe)).slack(4.0).go(sc.woomerdSafe, FilmHuman.Pace.SPRINT, 0.9, true);
			}
		} else if (now < TAUNT_TO) {
			h.look(kortev).track().slack(1.0).spam(true);
			if (now == TAUNT_FROM + 20) {
				h.jump();
			}
		} else if (!ACTED.contains("home")) {
			woomerdPearl(client, player, h, sc, now);
		} else {
			// Home before the key: straight on with the pile.
			player.getInventory().selectedSlot = 4;
			pile(client, player, h, sc, woomerdSpot(sc), PILE_FIRST, kortev);
		}
	}

	/** Where Woomerd stands to build the pile, by the W. */
	private static Vec3d woomerdSpot(FilmScene sc) {
		return sc.woomerdAtBase.add(0.0, 0.0, -1.1);
	}

	/** Where Woomerd's pearl is meant to come down: just short of where they build the pile, so they are straight at it. */
	private static Vec3d pearlTo(FilmScene sc) {
		Vec3d spot = woomerdSpot(sc);
		Vec3d d = spot.subtract(sc.woomerdSafe).multiply(1.0, 0.0, 1.0);
		return spot.subtract(d.normalize().multiply(1.5));
	}

	/**
	 * Home the quick way: from the lookout, a pearl down the way to the base (the pearl in hand, the look that throws it
	 * there, a right click), watched as it goes, and there they are, a few blocks short. If no throw from here would get
	 * it near, the whole way at a run.
	 */
	private static void woomerdPearl(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now) {
		Vec3d to = pearlTo(sc);
		if (pearlAt < 0 && pearlAim == null && !ACTED.contains("no pearl")) {
			pearlAim = pearlAim(player, to);
			if (pearlAim == null) {
				ACTED.add("no pearl");
				ShootingStar.LOGGER.warn("[film] Woomerd has no throw that gets a pearl to {}: running home", to);
			} else {
				ShootingStar.LOGGER.info("[film] Woomerd throws for {} at yaw {} pitch {}", to, pearlAim[0], pearlAim[1]);
			}
		}
		if (ACTED.contains("no pearl") || pearlAt >= 0 && now > pearlAt + 70) {
			if (FilmHuman.near(player, sc.woomerdAtBase, 0.8)) {
				ACTED.add("home");
			}
			h.look(eye(sc.woomerdAtBase)).slack(4.0).go(sc.woomerdAtBase, FilmHuman.Pace.SPRINT, 0.8, true);
			return;
		}
		if (pearlAt < 0) {
			player.getInventory().selectedSlot = 5;
			h.look(pearlAim[0], pearlAim[1]).slack(0.3);
			double off = Math.hypot(MathHelper.wrapDegrees(player.getYaw() - pearlAim[0]), player.getPitch() - pearlAim[1]);
			if (now >= TAUNT_TO + 6 && off < 0.5 && player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
				FilmHuman.rightClick(client);
				swung = true;
				pearlAt = now;
				pearlFrom = player.getPos();
				ShootingStar.LOGGER.info("[film] Woomerd throws the pearl at {}", now);
			}
			return;
		}
		// In the air: watching where it goes, then round the way the pile will be from there as it comes down (landing
		// face to the W otherwise, a wall to look at); then, there.
		if (now - pearlAt < 3) {
			h.look(to.add(0.0, 1.0, 0.0)).slack(3.0);
		} else {
			Vec3d top = Vec3d.ofBottomCenter(sc.pile.get(0)).add(0.0, -0.04, 0.0);
			double[] way = FilmHuman.angles(eye(woomerdSpot(sc)), top);
			h.look(way[0], way[1]).slack(3.0);
		}
		if (player.getPos().distanceTo(pearlFrom) > 6.0) {
			ACTED.add("home");
			ShootingStar.LOGGER.info("[film] Woomerd lands at {} at {}", player.getPos(), now);
		}
	}

	/**
	 * The look that throws a pearl from where they stand to come down nearest {@code to}: straight at it, and the
	 * flattest pitch that gets it within a block (or the nearest, within two), found by flying the pearl as the game
	 * does. Null if none will.
	 */
	@Nullable
	private static float[] pearlAim(ClientPlayerEntity player, Vec3d to) {
		Vec3d from = new Vec3d(player.getX(), player.getEyeY() - 0.1, player.getZ());
		float yaw = (float) FilmHuman.angles(player.getEyePos(), to)[0];
		float[] best = null;
		double bestOff = 2.0;
		for (float pitch = 10.0F; pitch >= -45.0F; pitch -= 0.25F) {
			Vec3d at = pearlLands(player, from, yaw, pitch);
			if (at == null) {
				continue;
			}
			double off = Math.hypot(at.x - to.x, at.z - to.z);
			if (off < 1.0) {
				return new float[] {yaw, pitch};
			}
			if (off < bestOff) {
				bestOff = off;
				best = new float[] {yaw, pitch};
			}
		}
		return best;
	}

	/**
	 * Where a pearl thrown standing still from {@code from} with this look comes down, stepped as the game steps one
	 * (1.5 a tick off the hand, 0.99 of it kept each tick, 0.03 a tick lost to falling) against the blocks; null if it is
	 * still up after 4 seconds.
	 */
	@Nullable
	private static Vec3d pearlLands(ClientPlayerEntity player, Vec3d from, float yaw, float pitch) {
		float r = MathHelper.RADIANS_PER_DEGREE;
		Vec3d v = new Vec3d(-MathHelper.sin(yaw * r) * MathHelper.cos(pitch * r), -MathHelper.sin(pitch * r),
				MathHelper.cos(yaw * r) * MathHelper.cos(pitch * r)).normalize().multiply(1.5);
		Vec3d pos = from;
		for (int t = 0; t < 80; t++) {
			Vec3d next = pos.add(v);
			BlockHitResult hit = player.getWorld().raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE, player));
			if (hit.getType() != HitResult.Type.MISS) {
				return hit.getPos();
			}
			pos = next;
			v = v.multiply(0.99).add(0.0, -0.03, 0.0);
		}
		return null;
	}

	/**
	 * The event, at the base: on with the pile, whatever the light from Kortev is; then into the chest by the W; out
	 * again to the last of the pile, to something in the sky, and the block coming down on them.
	 */
	private static void woomerdBase(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now, Vec3d kortev,
			Vec3d target) {
		int a = age;
		Vec3d spot = woomerdSpot(sc);
		// Not home yet when the key turned: still sprinting there.
		if (!ACTED.contains("home") && a < 124) {
			if (FilmHuman.near(player, sc.woomerdAtBase, 0.9)) {
				ACTED.add("home");
			} else {
				h.look(eye(sc.woomerdAtBase)).slack(4.0).go(sc.woomerdAtBase, FilmHuman.Pace.SPRINT, 0.8, true);
				return;
			}
		}
		boolean first = a < 150 && !piled(client, sc, PILE_FIRST);
		boolean building = first || a >= CHEST_SHUT + 12 && a < 360;
		// The cobblestone in hand to build with, the sword otherwise.
		if (building) {
			player.getInventory().selectedSlot = 4;
		} else if (a >= 52 && player.getInventory().selectedSlot == 4) {
			player.getInventory().selectedSlot = 2;
		}
		if (first) {
			// On with it, the light from Kortev or not (a look over at it would take the hand away too long).
			pile(client, player, h, sc, spot, PILE_FIRST, kortev);
		} else if (!ACTED.contains("base chest")) {
			h.look(Vec3d.ofCenter(sc.baseChest).add(0.0, 0.2, 0.0)).slack(1.0).go(sc.woomerdAtBase, FilmHuman.Pace.WALK, 0.3, false);
			if (FilmHuman.on(client, sc.baseChest, null)) {
				ACTED.add("base chest");
				FilmHuman.rightClick(client);
				swung = true;
			} else if (a > 190) {
				ACTED.add("base chest");
				ShootingStar.LOGGER.warn("[film] Woomerd's crosshair never found the chest: opened it anyway");
				client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
						new BlockHitResult(Vec3d.ofCenter(sc.baseChest), Direction.WEST, sc.baseChest, false));
			}
		} else if (a < CHEST_SHUT + 12) {
			// Out of it: back to the pile.
			h.look(Vec3d.ofCenter(sc.pile.get(3))).slack(3.0).go(spot, FilmHuman.Pace.WALK, 0.3, false);
		} else if (a < 360) {
			pile(client, player, h, sc, spot, sc.pile.size(), kortev);
		} else if (a < GapTimeline.INBOUND) {
			// Something in the sky over them: up at it, round at Kortev, up again, backing about under it, a hop.
			h.glance("sky", 14, 26, target.add(0.0, 60.0, 0.0), kortev, target.add(8.0, 45.0, -6.0)).slack(2.0)
					.go(sc.woomerdAtBase.add(-1.5 + 1.5 * Math.sin(a / 19.0), 0.0, 1.2 * Math.cos(a / 23.0)), FilmHuman.Pace.WALK, 0.5, false);
			if (a % 37 == 0) {
				h.jump();
			}
		} else {
			// It comes down: they back off a step, watching it.
			double fall = MathHelper.clamp((a - GapTimeline.INBOUND) / 24.0, 0.0, 1.0);
			h.look(target.add(0.0, MathHelper.lerp(fall, 60.0, 4.0), 0.0)).track().slack(1.0)
					.go(sc.woomerdAtBase.add(1.5, 0.0, 1.2), FilmHuman.Pace.WALK, 0.4, false);
		}
	}

	/**
	 * Building the pile: from {@code spot}, the first of its blocks (up to {@code upTo}) not down yet, looked at (the top of
	 * the block it goes on) and put down with a right click as soon as the crosshair is on it. With all of them down, a
	 * look at Kortev.
	 */
	private static void pile(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, Vec3d spot, int upTo,
			Vec3d kortev) {
		for (int i = 0; i < upTo; i++) {
			BlockPos p = sc.pile.get(i);
			BlockState there = client.world.getBlockState(p);
			if (!there.isAir() && !there.isReplaceable()) {
				continue;
			}
			BlockPos under = p.down();
			h.look(new Vec3d(p.getX() + 0.5, p.getY() - 0.04, p.getZ() + 0.5)).slack(0.6).go(spot, FilmHuman.Pace.WALK, 0.3, false);
			if (clientTick - lastPile >= PILE_GAP && FilmHuman.near(player, spot, 0.6) && FilmHuman.on(client, under, Direction.UP)) {
				FilmHuman.rightClick(client);
				swung = true;
				lastPile = clientTick;
			}
			return;
		}
		h.look(kortev).track().slack(1.5).idle(0.8);
	}

	/** The first {@code n} blocks of the pile are down. */
	private static boolean piled(MinecraftClient client, FilmScene sc, int n) {
		for (int i = 0; i < n; i++) {
			BlockState there = client.world.getBlockState(sc.pile.get(i));
			if (there.isAir() || there.isReplaceable()) {
				return false;
			}
		}
		return true;
	}

	/** Back at the spawn, the base gone in a flash, and then the black coming for them: they run, one look back. */
	private static void woomerdFlee(FilmHuman h, ClientPlayerEntity player, Vec3d target) {
		int a = age;
		if (a < GapTimeline.ERASURE + 4) {
			h.look(target.add(0.0, 10.0, 0.0)).slack(3.0);
		} else {
			Vec3d away = new Vec3d(player.getX() - target.x, 0.0, player.getZ() - target.z).normalize();
			Vec3d to = player.getPos().add(away.multiply(30.0));
			boolean back = a < GapTimeline.ERASURE + 10 || a > GapTimeline.ERASURE + 40 && a < GapTimeline.ERASURE + 52;
			h.look(back ? target : eye(to)).slack(4.0).go(to, FilmHuman.Pace.SPRINT, 1.0, true);
		}
	}

	/** In the void: where are we, then up to Kortev, hitting them (nothing touches anyone here), the crouch, gerald. */
	private static void woomerdVoid(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, Vec3d kortev) {
		int a = age;
		if (a < ANCHOR) {
			return;
		}
		Vec3d eye = player.getEyePos();
		Vec3d gerald = orElse(eyes(client, Role.GERALD), kortev);
		double toKortev = FilmHuman.angles(eye, kortev)[0];
		if (a < 690) {
			h.glance("lost", 10, 22, far(eye, toKortev + 140.0, 0.0), far(eye, toKortev - 120.0, 20.0), far(eye, toKortev + 60.0, 60.0), kortev)
					.slack(4.0);
			if (a == 652 || a == 670) {
				h.jump();
			}
		} else if (a < 730) {
			h.look(kortev).track().slack(1.5).go(kortev.add(0.0, -1.62, 0.0), FilmHuman.Pace.WALK, 1.8, false);
		} else if (a < 800) {
			h.look(kortev.add(0.0, -0.4, 0.0)).track().slack(1.0).go(kortev.add(0.0, -1.62, 0.0), FilmHuman.Pace.WALK, 1.6, false);
			if ((a - 730) % 7 == 0) {
				FilmHuman.leftClick(client);
				swung = true;
			}
		} else if (a < 830) {
			h.look(kortev).track().slack(1.0).spam(true);
		} else {
			// Off to Kortev's side, so the hole is in sight past them when the world comes back.
			Vec3d feet = kortev.add(0.0, -1.62, 0.0);
			Vec3d side = beside(feet, target(), 3.5, true);
			h.look(a < 880 ? gerald : kortev).track().slack(2.0).go(side, FilmHuman.Pace.WALK, 0.6, false);
		}
	}

	/** The key's target (the middle of the hole). */
	private static Vec3d target() {
		return Vec3d.ofCenter(scene.target);
	}

	/** {@code by} blocks to one side of {@code of}, across the line from it to {@code toward}. */
	private static Vec3d beside(Vec3d of, Vec3d toward, double by, boolean left) {
		Vec3d d = new Vec3d(toward.x - of.x, 0.0, toward.z - of.z);
		d = d.lengthSquared() < 1.0E-4 ? new Vec3d(1.0, 0.0, 0.0) : d.normalize();
		Vec3d side = left ? new Vec3d(-d.z, 0.0, d.x) : new Vec3d(d.z, 0.0, -d.x);
		return of.add(side.multiply(by));
	}

	/**
	 * Watching the world come back: up the tree as it grows, then about at the land coming back along its roots, a long
	 * look at each, then down at where the base was.
	 */
	private static void rebuild(FilmHuman h, ClientPlayerEntity player, Vec3d target, int r) {
		Vec3d eye = player.getEyePos();
		double toward = FilmHuman.angles(eye, target)[0];
		if (r < 150) {
			h.look(target.add(0.0, Math.min(45.0, 8.0 + r * 0.25), 0.0)).track().slack(1.5);
		} else if (r < 440) {
			h.glance("rebuild", 40, 80, far(eye, toward + 70.0, 6.0), target.add(0.0, 30.0, 0.0), far(eye, toward - 75.0, 4.0),
					far(eye, toward + 150.0, 10.0)).slack(3.0).idle(0.9);
		} else {
			h.look(target.add(0.0, -8.0, 0.0)).slack(2.5).idle(0.9);
		}
	}

	/** Home: running over to where the base was; looking down into the hole; and there is Kortev, crouching at them. */
	private static void woomerdHome(FilmHuman h, ClientPlayerEntity player, FilmScene sc, int t, Vec3d kortev, Vec3d target) {
		if (t < 20) {
			h.look(target).slack(4.0);
		} else if (!ACTED.contains("at rim")) {
			Vec3d rim = edge(player, sc, 27.0);
			if (FilmHuman.near(player, rim, 1.0) || !safeAhead(player)) {
				ACTED.add("at rim");
			}
			h.look(eye(rim)).slack(3.0).go(rim, player.squaredDistanceTo(rim) > 64.0 ? FilmHuman.Pace.SPRINT : FilmHuman.Pace.WALK, 1.0, true);
		} else if (t < 185) {
			h.look(target.add(0.0, -18.0, 0.0)).slack(2.5);
		} else {
			h.look(kortev).track().slack(1.0);
		}
	}

	// --- gerald_2009 -------------------------------------------------------------------------

	/**
	 * gerald_2009, new to the server: through the portal at the spawn just as the cabin goes up; up towards it all, Woomerd
	 * running past; Kortev's light; a look in the inventory (being new); the block coming down from a safe way off, and
	 * Woomerd with it; away from the black; the void, the world coming back; and then, home, over to see the new hole, and
	 * in.
	 */
	private static void gerald(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now, int r) {
		h.spam(false).idle(0.0).slack(2.0).stay().sneak(false).back(false);
		if (now <= ARRIVE) {
			// In the Nether since joining: not in the film.
			return;
		}
		if (once(now, ARRIVE + 1, "portal sound")) {
			client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_PORTAL_TRAVEL, 1.0F, 0.3F));
		}
		Vec3d eye = player.getEyePos();
		Vec3d middle = Vec3d.ofBottomCenter(sc.target);
		Vec3d target = Vec3d.ofCenter(sc.target);
		Vec3d cabin = Vec3d.ofCenter(sc.house).add(0.0, 2.5, 0.0);
		Vec3d kortev = orElse(eyes(client, Role.KORTEV), eye(sc.atChest));
		Vec3d woomerd = orElse(eyes(client, Role.WOOMERD), eye(sc.woomerdAtBase));
		// Where they go: a first look at it all, a little nearer to watch, back from the burst, away from the black.
		Vec3d watch = middle.add(-12.0, 0.0, 28.0);
		Vec3d nearer = middle.add(-9.0, 0.0, 24.0);
		Vec3d back = middle.add(-11.0, 0.0, 28.5);
		Vec3d away = middle.add(-17.0, 0.0, 43.0);
		if (age < 0) {
			if (now < BOOM) {
				// Out of the portal into a new world, a look about it.
				h.look(far(eye, 180.0, 3.0)).slack(3.0).go(sc.portalOut.add(0.0, 0.0, -3.5), FilmHuman.Pace.WALK, 0.4, false);
			} else if (now < BOOM + 34) {
				// What was that.
				h.glance("boom", 10, 16, cabin, cabin.add(3.0, 3.0, 0.0), cabin.add(1.0, -1.0, 2.0)).slack(2.0);
			} else {
				// Up towards it; Woomerd sprinting off home past them.
				boolean runner = now > RUN_HOME - 6 && now < RUN_HOME + 70;
				h.look(runner ? woomerd : cabin).track().slack(2.0).go(watch, FilmHuman.Pace.WALK, 0.6, false);
			}
			return;
		}
		if (released < 0 && age < GapTimeline.NOTHING) {
			int a = age;
			// The light going up from Kortev, followed only so far up: past that it is blank sky to look at.
			Vec3d beam = kortev.add(0.0, Math.min(28.0, Math.max(0.0, (a - 37) * 6.0)), 0.0);
			// Hungry, being new: two of the bread while the light goes up, and the third out of the inventory (all of which
			// is too far off to hear anything of, but the eating).
			if (a >= 34 && a < GapTimeline.SEND && player.getInventory().selectedSlot != 3) {
				player.getInventory().selectedSlot = 3;
			}
			int eaten = player.getMainHandStack().isOf(Items.BREAD) ? 7 - player.getMainHandStack().getCount() : 0;
			if ((a >= 40 && a < GERALD_INV && eaten < 2) || (a >= GERALD_INV_SHUT && a < GapTimeline.SEND + 2 && eaten < 3)) {
				h.use();
			}
			if (a < 37) {
				h.look(kortev).slack(1.5).idle(0.6);
			} else if (a < 84) {
				h.look(beam).track().slack(2.0);
			} else if (a < GERALD_INV) {
				h.glance("after beam", 10, 16, woomerd, eye(sc.woomerdAtBase).add(0.0, 2.0, 0.0)).slack(2.0).idle(0.8);
			} else if (!ACTED.contains("inventory")) {
				// E, to see what they have, being new.
				ACTED.add("inventory");
				client.setScreen(new InventoryScreen(player));
			} else if (a < GapTimeline.SEND + 8) {
				// The fort and Woomerd, a bit nearer to see; nothing in the sky yet.
				h.glance("fort", 12, 22, woomerd, Vec3d.ofCenter(sc.base).add(0.0, 3.0, 0.0)).slack(2.0).go(nearer, FilmHuman.Pace.WALK, 0.6, false);
			} else if (a < GapTimeline.INBOUND) {
				// The light reaching down over the base: up at it, the fort still in sight below.
				h.glance("sky", 18, 30, target.add(0.0, 22.0, 0.0), woomerd, target.add(4.0, 28.0, -3.0)).slack(2.0)
						.go(nearer, FilmHuman.Pace.WALK, 0.6, false);
			} else if (a < GapTimeline.CONTACT + 4) {
				double fall = MathHelper.clamp((a - GapTimeline.INBOUND) / 24.0, 0.0, 1.0);
				h.look(target.add(0.0, MathHelper.lerp(fall, 34.0, 4.0), 0.0)).track().slack(1.0);
			} else if (a < GapTimeline.ERASURE) {
				// The burst, Woomerd with it: a few steps back from it, watching.
				h.look(target.add(0.0, 12.0, 0.0)).track().slack(2.0).back(!FilmHuman.near(player, back, 0.8));
			} else {
				// The black coming for them: a look, and run, looking on ahead the way they run (not at where they run to,
				// which is the ground at their feet once they are nearly there).
				boolean look = a < GapTimeline.ERASURE + 8;
				Vec3d ahead = away.add(away.subtract(middle).normalize().multiply(20.0));
				h.look(look ? target : new Vec3d(ahead.x, eye.y, ahead.z)).slack(4.0).go(look ? null : away, FilmHuman.Pace.SPRINT, 1.0, true);
			}
			return;
		}
		if (released < 0) {
			geraldVoid(client, player, h);
			return;
		}
		if (r < HOME) {
			rebuild(h, player, target, r);
			return;
		}
		// Home: where am I; over to see the new hole; and in.
		int t = r - HOME;
		if (t < 14) {
			h.glance("home", 6, 10, target.add(0.0, -6.0, 0.0), far(eye, 120.0, 0.0), target).slack(3.0);
			return;
		}
		if (edge == null) {
			edge = toTheEdge(client, player, middle);
			ShootingStar.LOGGER.info("[film] gerald_2009 makes for the edge, at {}", edge);
		}
		// A sprint over (the last few blocks walked), to see it before anyone else does.
		h.look(new Vec3d(edge.x, player.getY() - 6.0, edge.z)).slack(2.5).go(edge, FilmHuman.Pace.SPRINT, 0.1, false);
	}

	/** In the void: where are we, then up to the other two, and about with them. */
	private static void geraldVoid(MinecraftClient client, ClientPlayerEntity player, FilmHuman h) {
		Vec3d eye = player.getEyePos();
		Vec3d kortev = orElse(eyes(client, Role.KORTEV), eye.add(4.0, 0.0, 0.0));
		Vec3d woomerd = orElse(eyes(client, Role.WOOMERD), kortev);
		int a = age;
		if (a < ANCHOR) {
			return;
		}
		double toKortev = FilmHuman.angles(eye, kortev)[0];
		if (a < 690) {
			h.glance("where", 9, 20, far(eye, toKortev + 150.0, 5.0), far(eye, toKortev - 100.0, -10.0), far(eye, toKortev + 40.0, 70.0), kortev,
					woomerd).slack(4.0);
		} else if (a < 730) {
			// Down at the floor of nothing; a hop on it.
			h.look(far(eye, toKortev + 10.0, 72.0)).slack(4.0);
			if (a == 700 || a == 714) {
				h.jump();
			}
		} else if (a < 820) {
			Vec3d side = beside(kortev.add(0.0, -1.62, 0.0), target(), 4.5, false);
			h.look(a < 780 ? kortev : woomerd).track().slack(1.5).go(side, FilmHuman.Pace.WALK, 0.8, false);
		} else {
			h.glance("them", 25, 50, woomerd, kortev).track().slack(2.0).idle(0.6);
		}
	}

	/**
	 * Where to walk to go in at the edge of the hole: a straight way there from where they stand (towards the hole's middle,
	 * or as near that as there is one) over ground with no crack in it (one would only drop them a few blocks), out to a
	 * little past the edge. With no such way, straight at the middle.
	 */
	private static Vec3d toTheEdge(MinecraftClient client, ClientPlayerEntity player, Vec3d middle) {
		double y = player.getY();
		double bearing = Math.atan2(middle.z - player.getZ(), middle.x - player.getX());
		for (int k = 0; k <= 12; k++) {
			double angle = bearing + Math.toRadians((k % 2 == 1 ? 1 : -1) * ((k + 1) / 2) * 6.0);
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			for (double d = 0.5; d <= 40.0; d += 0.5) {
				double x = player.getX() + dx * d;
				double z = player.getZ() + dz * d;
				int top = client.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
				if (top < y - 20.0) {
					return new Vec3d(player.getX() + dx * (d + 3.0), y, player.getZ() + dz * (d + 3.0));
				}
				if (Math.abs(top - y) > 1.5) {
					break;
				}
			}
		}
		return middle;
	}

	/** In the inventory, being new: the pointer from one thing to the next, held on each (its name comes up), and shut. */
	private static void geraldInventory(MinecraftClient client, ClientPlayerEntity player, HandledScreen<?> screen) {
		int t = invTicks++;
		if (t == 0) {
			FilmCursor.show(true);
			FilmCursor.place(screen.width / 2.0 - 10.0, screen.height / 2.0 - 30.0);
		}
		int[] slots = {39, 40, 38, 9, 36};
		for (int k = 0; k < slots.length; k++) {
			if (t == 4 + k * 26) {
				pointAt(screen, slots[k], 7.0);
			}
		}
		if (age >= GERALD_INV_SHUT) {
			FilmCursor.show(false);
			player.closeHandledScreen();
			ShootingStar.LOGGER.info("[film] gerald_2009 shuts the inventory at age {}", age);
		}
	}

	/**
	 * Woomerd in the chest at the base: the pointer to the golden apples, held over them, and at the end the apples and
	 * the TNT shift-clicked out, and shut.
	 */
	private static void woomerdChest(MinecraftClient client, ClientPlayerEntity player, GenericContainerScreen screen) {
		int t = invTicks++;
		int sync = screen.getScreenHandler().syncId;
		if (t == 0) {
			FilmCursor.show(true);
			FilmCursor.place(screen.width / 2.0 + 4.0, screen.height / 2.0 + 20.0);
		}
		if (t == 2) {
			pointAt(screen, 4, 7.0);
		}
		if (age < CHEST_SHUT - 16) {
			return;
		}
		if (refillAt < 0) {
			refillAt = t;
		}
		int u = t - refillAt;
		if (u == 0) {
			client.interactionManager.clickSlot(sync, 4, 0, SlotActionType.QUICK_MOVE, player);
		}
		if (u == 2) {
			pointAt(screen, 2, 5.0);
		}
		if (u == 8) {
			client.interactionManager.clickSlot(sync, 2, 0, SlotActionType.QUICK_MOVE, player);
		}
		if (u >= 12) {
			FilmCursor.show(false);
			player.closeHandledScreen();
			ShootingStar.LOGGER.info("[film] Woomerd shuts the chest at age {}", age);
		}
	}

	// --- stills ------------------------------------------------------------------------------

	/** Stills for looking the film over without the video. */
	private static void stills(MinecraftClient client, int now) {
		for (int at : new int[] {0, 40, 70, 120, 162, 180, 215, 245}) {
			if (now >= at && age < 0) {
				shot(client, String.format(Locale.ROOT, "s%03d", at));
			}
		}
		if (age >= 0 && released < 0) {
			for (int at : new int[] {20, 120, 340, 420, 436, 470, 500, 540, 612, 640, 700, 760, 820, 900}) {
				if (age >= at) {
					shot(client, String.format(Locale.ROOT, "a%03d", at));
				}
			}
		}
		if (released >= 0) {
			int r = now - released;
			for (int at : new int[] {60, 300, 600}) {
				if (r >= at && r < HOME) {
					shot(client, String.format(Locale.ROOT, "r%03d", at));
				}
			}
			for (int at : new int[] {20, 90, 150, 200, 240}) {
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
		ScreenshotRecorder.saveScreenshot(client.runDirectory, file, client.getFramebuffer(), message -> {
		});
	}

	private static void aimAt(ClientPlayerEntity player, Vec3d eye, Vec3d at) {
		double[] want = FilmHuman.angles(eye, at);
		player.setYaw((float) want[0]);
		player.setPitch((float) want[1]);
	}

	// =============================================================================================
	// Server
	// =============================================================================================

	private static void setUp(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		// The game's respawn at the world spawn faces south whatever the spawn's own angle, into the portal just there
		// (all Woomerd saw of coming back): turned north instead, over it all, as they come back.
		ServerPlayerEvents.AFTER_RESPAWN.register((before, after, alive) -> after.networkHandler.requestTeleport(after.getX(), after.getY(),
				after.getZ(), 180.0F, 0.0F));
		GameRules rules = world.getGameRules();
		rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		rules.get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		rules.get(GameRules.DO_MOB_SPAWNING).set(false, server);
		rules.get(GameRules.SPAWN_RADIUS).set(0, server);
		// Everyone's kit earns its advancements now, unannounced; from the first frame on they are announced (the key's).
		rules.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, server);
		rules.get(ModGameRules.GAP_RADIUS).set(RADIUS, server);
		world.setTimeOfDay(2500);
		world.setWeather(24000, 0, false, false);
		FilmScene sc = FilmScene.build(world);
		scene = sc;
		ServerWorld hell = server.getWorld(World.NETHER);
		FilmNether set = hell != null ? FilmNether.build(hell) : null;
		nether = set;
		for (Role role : Role.values()) {
			if (role != me) {
				FilmMoves track = FilmMoves.load(role);
				if (track != null) {
					TRACKS.put(role, track);
				}
			}
		}
		for (Role role : Role.values()) {
			if (role == Role.GERALD && me != Role.GERALD) {
				continue;
			}
			ServerPlayerEntity player = role == me ? server.getPlayerManager().getPlayerList().get(0) : join(server, world, role);
			dress(player, role);
			place(server, player, role);
		}
		shoo(world);
		ready = true;
		ShootingStar.LOGGER.info("[film] set up as {} (playing back {})", me, TRACKS.keySet());
	}

	/** No animals or villagers about the scene (a village nearby walks its villagers into the farm, and in front of shots). */
	private static void shoo(ServerWorld world) {
		FilmScene sc = scene;
		Box around = new Box(sc.house).expand(110.0, 60.0, 110.0).offset(20.0, 0.0, 20.0);
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, around, mob -> true)) {
			mob.discard();
		}
	}

	/** Where each one starts: Kortev at the farm, facing it; Woomerd creeping up from the east; gerald in the Nether. */
	private static void place(MinecraftServer server, ServerPlayerEntity player, Role role) {
		FilmScene sc = scene;
		FilmNether set = nether;
		ServerWorld world = server.getOverworld();
		switch (role) {
			case KORTEV -> put(world, player, sc.kortev, Vec3d.ofCenter(crop(sc, 0)));
			case WOOMERD -> put(world, player, sc.woomerdStart, eye(sc.woomerdAtHouse));
			case GERALD -> {
				ServerWorld hell = server.getWorld(World.NETHER);
				if (set != null && hell != null) {
					Vec3d at = set.start();
					player.teleport(hell, at.x, at.y, at.z, 90.0F, 78.0F);
					player.setSneaking(true);
				}
			}
		}
	}

	/** A player the server makes and moves itself, connected as the game tests' are. */
	private static ServerPlayerEntity join(MinecraftServer server, ServerWorld world, Role role) {
		GameProfile profile = new GameProfile(Uuids.getOfflinePlayerUuid(role.name), role.name);
		ConnectedClientData data = new ConnectedClientData(profile, 0, FilmSkins.allLayers(), false);
		ServerPlayerEntity player = new ServerPlayerEntity(server, world, profile, data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerManager().onPlayerConnect(connection, player, data);
		ShootingStar.LOGGER.info("[film] {} joined", role.name);
		return player;
	}

	/**
	 * What each of them has on them: Kortev a survivor's kit and seed (the key is in the chest, the first hotbar slot free
	 * for it); Woomerd TNT and netherite; gerald cobblestone to bridge with.
	 */
	private static void dress(ServerPlayerEntity player, Role role) {
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		player.setHealth(player.getMaxHealth());
		player.getHungerManager().setFoodLevel(20);
		switch (role) {
			case KORTEV -> {
				player.getInventory().setStack(1, new ItemStack(Items.WHEAT_SEEDS, 23));
				player.getInventory().setStack(2, new ItemStack(Items.DIAMOND_SWORD));
				player.getInventory().setStack(3, new ItemStack(Items.DIAMOND_PICKAXE));
				player.getInventory().setStack(4, new ItemStack(Items.IRON_AXE));
				player.getInventory().setStack(5, new ItemStack(Items.COOKED_BEEF, 23));
				player.getInventory().setStack(6, new ItemStack(Items.OAK_LOG, 48));
				player.getInventory().setStack(7, new ItemStack(Items.TORCH, 31));
				player.getInventory().setStack(8, new ItemStack(Items.WATER_BUCKET));
				// Wheat already in the bag, so what is harvested stacks there and not in the first hotbar slot (the key's).
				player.getInventory().setStack(9, new ItemStack(Items.WHEAT, 19));
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
				player.getInventory().setStack(0, new ItemStack(Items.COBBLESTONE, 14));
				player.getInventory().setStack(9, new ItemStack(Items.COBBLESTONE, 64));
				player.getInventory().setStack(10, new ItemStack(Items.COBBLESTONE, 37));
				player.getInventory().setStack(2, new ItemStack(Items.STONE_PICKAXE));
				player.getInventory().setStack(3, new ItemStack(Items.BREAD, 7));
				player.getInventory().setStack(4, new ItemStack(Items.TORCH, 12));
				// Hungry from the Nether: room for three of the bread.
				player.getHungerManager().setFoodLevel(5);
				player.setExperienceLevel(3);
				select(player, Role.GERALD, 0);
			}
		}
		// What they would long since have: every recipe, and the advancements the film's goings-on would otherwise earn
		// on screen (seed planted, the key picked up), so no toast or line in the chat gets in the way of it.
		MinecraftServer server = player.getServer();
		player.unlockRecipes(server.getRecipeManager().values());
		for (String id : new String[] {"minecraft:husbandry/plant_seed", "shootingstar:root", "shootingstar:genesis/key"}) {
			AdvancementEntry advancement = server.getAdvancementLoader().get(Identifier.of(id));
			if (advancement != null) {
				for (String criterion : advancement.value().criteria().keySet()) {
					player.getAdvancementTracker().grantCriterion(advancement, criterion);
				}
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
		double[] a = at == null ? new double[] {player.getYaw(), player.getPitch()}
				: FilmHuman.angles(feet.add(0.0, player.getStandingEyeHeight(), 0.0), at);
		player.teleport(world, feet.x, feet.y, feet.z, (float) a[0], (float) a[1]);
		player.setHeadYaw((float) a[0]);
		player.networkHandler.syncWithPlayerPosition();
	}

	@Nullable
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
		if (now == 0) {
			world.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(true, server);
		}
		if (now % 40 == 0) {
			shoo(world);
		}
		if (released >= 0 && now - released >= HOME + AFTER) {
			finished = true;
		}

		// Woomerd's TNT against the cabin's east wall, lit. Filmed as Woomerd, their own clicks put it down and light it,
		// and this only makes up for a click that missed (a few ticks later, once it has had its chance).
		boolean own = me == Role.WOOMERD;
		for (int i = 0; i < TNT_AT.length; i++) {
			if (now == TNT_AT[i] + (own ? 8 : 0) && !world.getBlockState(sc.tnt[i]).isOf(Blocks.TNT)) {
				BlockPos p = sc.tnt[i];
				world.setBlockState(p, Blocks.TNT.getDefaultState(), 3);
				world.playSound(null, p, SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1.0F, 0.8F);
				if (woomerd != null) {
					woomerd.getInventory().getStack(0).decrement(1);
				}
			}
		}
		if (now == IGNITE + (own ? 8 : 0)) {
			boolean lit = false;
			for (BlockPos p : sc.tnt) {
				if (!world.getBlockState(p).isOf(Blocks.TNT)) {
					// Lit already (Woomerd's flint and steel).
					lit = true;
					continue;
				}
				world.setBlockState(p, Blocks.AIR.getDefaultState(), 11);
				TntEntity tnt = new TntEntity(world, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, woomerd);
				world.spawnEntity(tnt);
				world.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0F, 1.0F);
			}
			if (!lit) {
				world.playSound(null, sc.tnt[1], SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
			}
		}

		// gerald_2009 comes through the portal at the spawn, from the Nether.
		if (now == ARRIVE) {
			ServerPlayerEntity gerald = player(server, Role.GERALD);
			if (gerald != null) {
				gerald.teleport(world, sc.portalOut.x, sc.portalOut.y, sc.portalOut.z, 180.0F, 2.0F);
				gerald.setSneaking(false);
				ShootingStar.LOGGER.info("[film] gerald_2009 comes through the portal at {}", sc.portalOut);
			}
		}

		// gerald_2009 joins (in the Nether): what their kit and the Nether earn them, unannounced.
		if (now == GERALD_JOINS + 4) {
			world.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(true, server);
		}
		if (now == GERALD_JOINS && !geraldIn) {
			geraldIn = true;
			world.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, server);
			if (me == Role.GERALD) {
				server.getPlayerManager().broadcast(Text.translatable("multiplayer.player.joined", Role.GERALD.name).formatted(Formatting.YELLOW), false);
			} else {
				ServerPlayerEntity gerald = join(server, world, Role.GERALD);
				dress(gerald, Role.GERALD);
				place(server, gerald, Role.GERALD);
			}
		}

		// Kortev's key, for the server's Kortev (the client's turns its own).
		if (kortev != null && me != Role.KORTEV) {
			if (now == KEY_TURN) {
				if (!kortev.getMainHandStack().isOf(ModItems.GENESIS_KEY)) {
					kortev.getInventory().setStack(0, new ItemStack(ModItems.GENESIS_KEY));
					select(kortev, Role.KORTEV, 0);
				}
				aim(kortev, Vec3d.ofCenter(sc.target));
				kortev.swingHand(Hand.MAIN_HAND, true);
				kortev.getMainHandStack().use(world, kortev, Hand.MAIN_HAND);
				ShootingStar.LOGGER.info("[film] Kortev turns the key at {}", now);
			}
			if (released < 0 && age >= RELEASE_FROM && (age - RELEASE_FROM) % 20 == 0 && kortev.getMainHandStack().isOf(ModItems.GENESIS_KEY)) {
				kortev.swingHand(Hand.MAIN_HAND, true);
				kortev.getMainHandStack().use(world, kortev, Hand.MAIN_HAND);
				ShootingStar.LOGGER.info("[film] Kortev turns the cracked key at age {}", age);
			}
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
			} else if (now - deadAt == RESPAWN_DELAY) {
				ServerPlayerEntity back = server.getPlayerManager().respawnPlayer(woomerd, false, Entity.RemovalReason.KILLED);
				// As the game's own respawn does: the connection is the new player's now. Left on the dead one, every move
				// made after this (gathered into the void, sent home) moved the dead one, and Woomerd stayed at the spawn.
				back.networkHandler.player = back;
				back.networkHandler.syncWithPlayerPosition();
				ShootingStar.LOGGER.info("[film] Woomerd respawned at {}", back.getBlockPos().toShortString());
				deadAt = -1;
			}
		}

		// The server's own players: as they moved in their own takes.
		for (Role role : Role.values()) {
			if (role == me) {
				continue;
			}
			ServerPlayerEntity p = player(server, role);
			if (p != null && p.isAlive()) {
				replay(server, role, p, now);
			}
		}
		if (released < 0 && age >= 0 && age % 80 == 0) {
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				ShootingStar.LOGGER.info("[film] age {}: {} is at {} in {}", age, p.getGameProfile().getName(), p.getBlockPos().toShortString(),
						p.getWorld().getRegistryKey().getValue());
			}
		}
	}

	/**
	 * The server's copy of {@code role} doing what they did at this tick in their own take: going where they went (until
	 * the black takes them; in the void, the same moves from where they arrive this time; once home, where they went),
	 * looking, crouching, sprinting, swinging and holding what they did. With no take of theirs yet, they stand in their
	 * places.
	 */
	private static void replay(MinecraftServer server, Role role, ServerPlayerEntity p, int now) {
		FilmMoves track = TRACKS.get(role);
		FilmMoves.Move m = track == null ? null : track.at(now);
		int r = released >= 0 ? now - released : -1;
		if (m == null) {
			standIn(server, role, p, now, r);
			return;
		}
		if (!m.alive()) {
			// Dead in their own take after it was all over (gerald, in the hole): dead here too.
			if (r >= HOME) {
				p.damage(p.getServerWorld().getDamageSources().outOfWorld(), Float.MAX_VALUE);
			}
			return;
		}
		Vec3d pos = null;
		if (released < 0 && age < GapTimeline.NOTHING) {
			pos = m.pos();
		} else if (released < 0 || r < HOME) {
			if (age >= ANCHOR || released >= 0) {
				Vec3d here = ANCHORS.computeIfAbsent(role, k -> p.getPos());
				FilmMoves.Move there = track.atAge(ANCHOR);
				if (there != null) {
					pos = here.add(m.pos().subtract(there.pos()));
				}
			}
		} else if (r >= HOME + 2) {
			pos = m.pos();
		}
		if (pos != null && p.getWorld().getRegistryKey().getValue().toString().equals(m.dim())) {
			p.refreshPositionAndAngles(pos.x, pos.y, pos.z, m.yaw(), m.pitch());
		} else {
			p.setYaw(m.yaw());
			p.setPitch(m.pitch());
		}
		p.setHeadYaw(m.yaw());
		p.setSneaking(m.sneak());
		p.setPose(m.sneak() ? EntityPose.CROUCHING : EntityPose.STANDING);
		p.setSprinting(m.sprint());
		if (m.swing()) {
			p.swingHand(Hand.MAIN_HAND, true);
			// Woomerd's clicks with cobblestone at the base put the pile there down, block by block, as in their take.
			FilmScene sc = scene;
			if (role == Role.WOOMERD && m.item().equals("minecraft:ender_pearl") && released < 0) {
				// Their pearl home: one like it thrown here (no one's, so it only flies and breaks; they get there by
				// their moves, as it comes down).
				ServerWorld world = p.getServerWorld();
				EnderPearlEntity pearl = new EnderPearlEntity(EntityType.ENDER_PEARL, world);
				pearl.setPosition(p.getX(), p.getEyeY() - 0.1, p.getZ());
				pearl.setItem(new ItemStack(Items.ENDER_PEARL));
				pearl.setVelocity(p, m.pitch(), m.yaw(), 0.0F, 1.5F, 0.0F);
				world.spawnEntity(pearl);
				world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDER_PEARL_THROW, SoundCategory.NEUTRAL, 0.5F, 0.45F);
			}
			if (role == Role.WOOMERD && m.item().equals("minecraft:cobblestone") && released < 0 && now >= RUN_HOME && pileNext < sc.pile.size()) {
				BlockPos block = sc.pile.get(pileNext++);
				ServerWorld world = p.getServerWorld();
				world.setBlockState(block, Blocks.COBBLESTONE.getDefaultState(), 3);
				world.playSound(null, block, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0F, 0.8F);
			}
		}
		Item item = Registries.ITEM.get(Identifier.of(m.item()));
		if (p.getInventory().selectedSlot != m.slot() || !p.getMainHandStack().isOf(item)) {
			if (!p.getInventory().getStack(m.slot()).isOf(item)) {
				p.getInventory().setStack(m.slot(), item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, item.getMaxCount() > 1 ? 16 : 1));
			}
			select(p, role, m.slot());
		}
	}

	/** No take of {@code role} yet: they stand where they would be, looking about where they would look. */
	private static void standIn(MinecraftServer server, Role role, ServerPlayerEntity p, int now, int r) {
		FilmScene sc = scene;
		if (released >= 0 && r < HOME || released < 0 && age >= GapTimeline.NOTHING) {
			return;
		}
		ServerWorld world = server.getOverworld();
		Vec3d feet = switch (role) {
			case KORTEV -> now < CHEST_GO ? sc.kortev : sc.atChest;
			case WOOMERD -> now < IGNITE ? sc.woomerdAtHouse : now < RUN_HOME ? sc.woomerdSafe : sc.woomerdAtBase;
			case GERALD -> null;
		};
		if (feet != null && p.getWorld() == world && r < 0) {
			p.refreshPositionAndAngles(feet.x, feet.y, feet.z, p.getYaw(), p.getPitch());
			aim(p, role == Role.KORTEV ? Vec3d.ofCenter(sc.target) : eye(sc.atChest));
		}
	}

	private static void aim(ServerPlayerEntity p, Vec3d at) {
		double[] a = FilmHuman.angles(p.getEyePos(), at);
		p.setYaw((float) a[0]);
		p.setPitch((float) a[1]);
		p.setHeadYaw((float) a[0]);
	}

	private static boolean hasKey(ServerPlayerEntity player) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			if (player.getInventory().getStack(i).isOf(ModItems.GENESIS_KEY)) {
				return true;
			}
		}
		return false;
	}
}
