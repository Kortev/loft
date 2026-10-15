package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.advancement.AdvancementEntry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.mob.MobEntity;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
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
	/** gerald_2009 slips, just before the black takes everyone. */
	private static final int SLIP = GapTimeline.NOTHING - 11;
	/** gerald stops bridging this long before the slip, at the edge, looking about. */
	private static final int HESITATE = 24;
	/** Everyone has been carried into the void by now: where each is then is where their moves there are measured from. */
	private static final int ANCHOR = GapTimeline.NOTHING + 14;
	/** Kortev turns the cracked key (and again every second until the world comes back). */
	private static final int RELEASE_FROM = 960;
	/** Ticks after the cracked key is turned that everyone has been sent home (the rebuild's end, and a little more). */
	private static final int HOME = GapTimeline.REBUILD_END + 20;
	/** How long the recording goes on once everyone is home. */
	private static final int AFTER = 260;
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
	private static int lastRecorded = -1;
	private static boolean swung;
	// Kortev: the wheat being got in, the chest.
	private static int job;
	private static int jobAt;
	private static int openedAt = -1;
	private static int closedAt = -1;
	// gerald_2009: the bridge.
	private static int bridgeX;
	private static int placed;
	private static int pauseUntil = -1;
	private static int nextPause = 7;
	private static int lastPlace = -100;

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
		me = Role.valueOf(mode.substring(5).toUpperCase(Locale.ROOT));
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

	/** What the client's own player does this tick, through {@link FilmHuman}: where they look and go, what they use. */
	private static void act(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		FilmScene sc = scene;
		FilmHuman h = human;
		if (player == null || sc == null || h == null || s < 0) {
			return;
		}
		int now = s;
		if (now == lastRecorded) {
			return;
		}
		swung = false;
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
			} else if (client.currentScreen instanceof GenericContainerScreen screen) {
				FilmHuman.release(client);
				chest(client, player, screen, now);
			} else {
				switch (me) {
					case KORTEV -> kortev(client, player, h, sc, now, r);
					case WOOMERD -> woomerd(client, player, h, sc, now, r);
					case GERALD -> gerald(client, player, h, sc, now, r);
				}
				h.tick(client, player, now);
			}
		}
		record(client, player, now, r);
	}

	private static void record(MinecraftClient client, ClientPlayerEntity player, int now, int r) {
		lastRecorded = now;
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
		if (age < 0) {
			h.wander(2.0).follow(0.3).fidget(0.0).spam(false).sneak(false).back(false);
			if (now < BOOM) {
				farming(client, player, h, sc, now);
			} else if (now < BOOM + 8) {
				// It goes up behind them: round they spin, a step back.
				h.look(cabin.add(4.0, 0.0, 0.0)).wander(1.0).stay().back(now < BOOM + 4);
			} else if (now < BOOM + 16) {
				h.look(cabin.add(3.0, -1.0, 0.0)).wander(2.5).stay();
			} else if (now < CHEST_GO) {
				// And there is Woomerd, crouching at them.
				h.look(woomerd).follow(0.35).wander(1.0).go(sc.kortev.add(1.2, 0.0, 0.6), FilmHuman.Pace.WALK, 0.3, false);
			} else if (closedAt < 0) {
				Vec3d box = Vec3d.ofCenter(sc.chest).add(0.0, 0.25, 0.0);
				h.look(box).wander(0.6).follow(0.45).go(sc.atChest, FilmHuman.Pace.WALK, 0.3, false);
				if (openedAt < 0 && (FilmHuman.near(player, sc.atChest, 0.5) && h.off(player, box) < 7.0 || now >= CHEST_GO + 30)) {
					if (ACTED.add("open")) {
						client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
								new BlockHitResult(Vec3d.ofCenter(sc.chest).add(-0.5, 0.0, 0.0), Direction.WEST, sc.chest, false));
						swing(player);
						ShootingStar.LOGGER.info("[film] Kortev opens the chest at {}", now);
					}
				}
			} else {
				// The key out, and on the base.
				if (once(now, closedAt + 3, "key out")) {
					player.getInventory().selectedSlot = 0;
				}
				h.look(target.add(0.0, 0.6, 0.0)).wander(0.4).follow(0.5).stay();
				if (once(now, KEY_TURN, "key")) {
					aimAt(player, player.getEyePos(), target);
					ShootingStar.LOGGER.info("[film] Kortev turns the key at {}", now);
					client.interactionManager.interactItem(player, Hand.MAIN_HAND);
					swing(player);
				}
			}
			return;
		}
		if (released < 0) {
			kortevVoid(client, player, h, sc, now, target);
			return;
		}
		if (r >= HOME) {
			kortevHome(client, player, h, sc, now, r - HOME, target);
		}
	}

	/** Getting the wheat in: to each ripe stalk along the path, break it, put seed back in. */
	private static void farming(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now) {
		BlockPos crop = crop(sc, job);
		BlockPos soil = crop.down();
		Vec3d stand = new Vec3d(sc.kortev.x, sc.kortev.y, crop.getZ() + 0.5 + (job % 2 == 0 ? -0.2 : 0.2));
		int t = now - jobAt;
		h.go(stand, FilmHuman.Pace.WALK, 0.35, false);
		if (t < 9) {
			h.look(Vec3d.ofCenter(crop).add(0.0, -0.2, 0.0)).wander(0.8).follow(0.4);
		} else if (t == 9) {
			client.interactionManager.attackBlock(crop, Direction.UP);
			swing(player);
		} else if (t < 15) {
			h.look(Vec3d.ofCenter(soil).add(0.0, 0.45, 0.0)).wander(0.6);
		} else if (t == 15) {
			client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
					new BlockHitResult(Vec3d.ofCenter(soil).add(0.0, 0.44, 0.0), Direction.UP, soil, false));
			swing(player);
		} else if (t >= 18) {
			job = Math.min(job + 1, 13);
			jobAt = now;
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
		if (t == 1) {
			FilmCursor.moveTo(slotX(screen, handler.getSlot(key)) + 2.0, slotY(screen, handler.getSlot(key)) + 3.0, 7.0);
		}
		if (t == 15) {
			client.interactionManager.clickSlot(handler.syncId, key, 0, SlotActionType.PICKUP, player);
		}
		if (t == 17) {
			FilmCursor.moveTo(slotX(screen, handler.getSlot(hotbar)) + 1.0, slotY(screen, handler.getSlot(hotbar)) + 2.0, 6.0);
		}
		if (t == 25) {
			client.interactionManager.clickSlot(handler.syncId, hotbar, 0, SlotActionType.PICKUP, player);
		}
		if (t == 27) {
			FilmCursor.moveTo(slotX(screen, handler.getSlot(hotbar)) + 14.0, slotY(screen, handler.getSlot(hotbar)) - 10.0, 4.0);
		}
		if (t >= 31) {
			FilmCursor.show(false);
			player.closeHandledScreen();
			closedAt = now;
			ShootingStar.LOGGER.info("[film] Kortev has the key at {}", now);
		}
	}

	private static double slotX(GenericContainerScreen screen, Slot slot) {
		return (screen.width - 176) / 2.0 + slot.x + 8.0;
	}

	private static double slotY(GenericContainerScreen screen, Slot slot) {
		return (screen.height - (114 + screen.getScreenHandler().getRows() * 18)) / 2.0 + slot.y + 8.0;
	}

	/** In the void, once the view is theirs again: taking it in, Woomerd at them, gerald, then the hole, and the key. */
	private static void kortevVoid(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now, Vec3d target) {
		if (age < GapTimeline.RETURN) {
			return;
		}
		Vec3d woomerd = orElse(eyes(client, Role.WOOMERD), target);
		Vec3d gerald = orElse(eyes(client, Role.GERALD), woomerd);
		Vec3d hole = target.add(0.0, -4.0, 0.0);
		double toHole = FilmHuman.angles(player.getEyePos(), hole)[0];
		int a = age;
		h.spam(false).sneak(false).back(false).fidget(0.0).follow(0.3);
		if (a < 760) {
			h.look(toHole + 70.0 * Math.sin((a - GapTimeline.RETURN) / 18.0), -8.0 + 6.0 * Math.sin((a - GapTimeline.RETURN) / 11.0)).wander(3.0).stay();
		} else if (a < 800) {
			// Down at their feet: rings where they step on nothing.
			Vec3d ahead = player.getPos().add(Vec3d.fromPolar(0.0F, (float) toHole).multiply(3.0));
			h.look(toHole - 20.0, 62.0).wander(2.0).go(ahead, FilmHuman.Pace.WALK, 0.4, false);
			if (a == 790) {
				h.jump();
			}
		} else if (a < 860) {
			h.look(woomerd).wander(1.0).fidget(0.6).stay();
		} else if (a < 900) {
			h.look(gerald).wander(1.5).stay();
		} else {
			h.look(hole).wander(0.8).follow(0.4).stay();
		}
		if (released < 0 && age >= RELEASE_FROM && ticks - lastTry >= 20 && player.getMainHandStack().isOf(ModItems.GENESIS_KEY)) {
			lastTry = ticks;
			ShootingStar.LOGGER.info("[film] Kortev turns the cracked key at age {}", age);
			client.interactionManager.interactItem(player, Hand.MAIN_HAND);
			swing(player);
		}
	}

	/**
	 * Home: over to the edge of the hole where the base was, on the side Woomerd comes running up to it from (the spawn's),
	 * a few blocks along from them; a look down it; then at Woomerd beside them, and the crouch back at them.
	 */
	private static void kortevHome(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now, int t, Vec3d target) {
		Vec3d woomerd = orElse(eyes(client, Role.WOOMERD), target);
		Vec3d middle = Vec3d.ofBottomCenter(sc.target);
		Vec3d out = new Vec3d(sc.respawn.x - middle.x, 0.0, sc.respawn.z - middle.z).normalize().rotateY(-0.2F);
		Vec3d spot = new Vec3d(middle.x + out.x * 28.0, player.getY(), middle.z + out.z * 28.0);
		h.sneak(false).back(false).spam(false).fidget(0.0).follow(0.3);
		if (t < 24) {
			h.look(target.add(0.0, -6.0, 0.0)).wander(4.0).stay();
		} else if (t < 165 && !ACTED.contains("kortev at rim")) {
			if (FilmHuman.near(player, spot, 1.0) || !safeAhead(player)) {
				ACTED.add("kortev at rim");
			}
			h.look(t < 100 ? eye(spot) : target.add(0.0, -8.0, 0.0)).wander(2.0).go(spot,
					player.squaredDistanceTo(spot) > 100.0 ? FilmHuman.Pace.SPRINT : FilmHuman.Pace.WALK, 1.0, false);
		} else if (t < 190) {
			h.look(target.add(0.0, -16.0, 0.0)).wander(3.0).stay();
		} else if (t < 240) {
			h.look(woomerd).wander(0.6).follow(0.4).stay().spam(t > 196);
		} else {
			h.look(woomerd).wander(1.5).fidget(0.8).stay();
		}
		if (!safeAhead(player)) {
			h.stay();
		}
	}

	/** The place {@code from} blocks out from the hole's middle on the player's side, to go and look down from. */
	private static Vec3d edge(ClientPlayerEntity player, FilmScene sc, double from) {
		Vec3d middle = Vec3d.ofBottomCenter(sc.target);
		Vec3d out = new Vec3d(player.getX() - middle.x, 0.0, player.getZ() - middle.z);
		out = out.lengthSquared() < 1.0E-4 ? new Vec3d(-1.0, 0.0, 0.0) : out.normalize();
		return new Vec3d(middle.x + out.x * from, player.getY(), middle.z + out.z * from);
	}

	/** Ground a step or two ahead (no fissure or hole there to walk into). */
	private static boolean safeAhead(ClientPlayerEntity player) {
		Vec3d v = player.getVelocity();
		if (v.horizontalLengthSquared() < 1.0E-4 || player.getWorld() == null) {
			return true;
		}
		Vec3d ahead = player.getPos().add(v.normalize().multiply(1.8));
		int top = player.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(ahead.x), MathHelper.floor(ahead.z));
		return top >= player.getY() - 3.0;
	}

	// --- Woomerd -----------------------------------------------------------------------------

	private static void woomerd(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now, int r) {
		Vec3d kortev = orElse(eyes(client, Role.KORTEV), eye(sc.atChest));
		Vec3d target = Vec3d.ofCenter(sc.target);
		Vec3d wall = new Vec3d(sc.house.getX() + 4.5, sc.house.getY() + 1.0, sc.house.getZ() + 0.5);
		h.spam(false).sneak(false).back(false).fidget(0.0).follow(0.3).wander(2.0);
		if (age < 0) {
			if (now < 44) {
				// Up to the cabin, crouching for the last few steps, a look round the corner for Kortev on the way.
				Vec3d look = now > 18 && now < 28 ? Vec3d.ofCenter(sc.farm).add(0.0, 1.5, 0.0) : wall;
				boolean close = FilmHuman.near(player, sc.woomerdAtHouse, 2.8);
				h.look(look).wander(2.5).go(sc.woomerdAtHouse, close ? FilmHuman.Pace.SNEAK : FilmHuman.Pace.WALK, 0.3, false);
			} else if (now < IGNITE + 2) {
				int i = MathHelper.clamp((now - TNT_AT[0] + 4) / 8, 0, 2);
				h.look(sc.tnt[i].toCenterPos()).wander(0.8).follow(0.45).go(sc.woomerdAtHouse, FilmHuman.Pace.WALK, 0.3, false);
				for (int at : TNT_AT) {
					if (once(now, at, "tnt" + at)) {
						swing(player);
					}
				}
				if (once(now, FLINT, "flint")) {
					player.getInventory().selectedSlot = 1;
				}
				if (once(now, IGNITE, "ignite")) {
					swing(player);
				}
			} else if (now < RUN_AWAY + 40) {
				if (once(now, RUN_AWAY, "sword")) {
					player.getInventory().selectedSlot = 2;
				}
				boolean there = FilmHuman.near(player, sc.woomerdSafe, 0.8) || ACTED.contains("safe");
				if (there) {
					ACTED.add("safe");
				}
				h.look(there ? wall : eye(sc.woomerdSafe)).wander(1.5).go(there ? null : sc.woomerdSafe, FilmHuman.Pace.SPRINT, 0.8, true);
			} else if (now < TAUNT_FROM) {
				h.look(now < BOOM ? wall : wall.add(0.0, 1.5, 0.0)).wander(2.0).fidget(1.2).stay();
			} else if (now < TAUNT_TO) {
				// At Kortev, crouching up and down, a hop for good measure.
				h.look(kortev).wander(1.0).follow(0.4).stay().spam(true);
				if (now == TAUNT_FROM + 20) {
					h.jump();
				}
			} else {
				boolean home = FilmHuman.near(player, sc.woomerdAtBase, 0.8) || ACTED.contains("home");
				if (home) {
					ACTED.add("home");
					celebrate(h, player, sc, now, kortev);
				} else {
					h.look(eye(sc.woomerdAtBase)).wander(2.0).go(sc.woomerdAtBase, FilmHuman.Pace.SPRINT, 0.8, true);
				}
			}
			return;
		}
		if (released < 0 && age < GapTimeline.NOTHING) {
			if (!ACTED.contains("respawned") && age < GapTimeline.CONTACT + 20) {
				woomerdBase(h, player, sc, now, kortev, target);
			} else {
				ACTED.add("respawned");
				woomerdFlee(h, player, sc, now, target);
			}
			return;
		}
		if (released < 0) {
			woomerdVoid(client, player, h, now, kortev);
			return;
		}
		if (r < HOME) {
			rebuild(h, player, target, r);
			return;
		}
		woomerdHome(h, player, sc, r - HOME, kortev, target);
	}

	/** At the base: hopping about, a spin, a crouch or two at the cabin; looking round at Kortev now and then. */
	private static void celebrate(FilmHuman h, ClientPlayerEntity player, FilmScene sc, int now, Vec3d kortev) {
		int t = now - RUN_HOME;
		Vec3d spot = sc.woomerdAtBase.add(Math.sin(t / 23.0) * 1.2, 0.0, Math.cos(t / 31.0) * 1.4);
		if ((t / 40) % 3 == 1) {
			h.look(kortev).wander(1.5).spam((t % 40) > 22).go(spot, FilmHuman.Pace.WALK, 0.5, false);
		} else {
			double yaw = player.getYaw() + 9.0;
			h.look(yaw, 4.0).wander(4.0).fidget(2.0).go(spot, FilmHuman.Pace.WALK, 0.5, false);
			if (t % 17 == 0) {
				h.jump();
			}
		}
	}

	/** The event, at the base: Kortev's light going up, the sky, then the block coming down on them. */
	private static void woomerdBase(FilmHuman h, ClientPlayerEntity player, FilmScene sc, int now, Vec3d kortev, Vec3d target) {
		int a = age;
		Vec3d beam = kortev.add(0.0, Math.min(60.0, Math.max(0.0, (a - 37) * 6.0)), 0.0);
		if (a < 40) {
			celebrate(h, player, sc, now, kortev);
		} else if (a < 70) {
			// The flash where Kortev is.
			h.look(kortev).wander(1.0).follow(0.4).stay().fidget(0.5);
		} else if (a < 92) {
			h.look(kortev.add(0.0, Math.min(22.0, (a - 70) * 1.5), 0.0)).wander(1.5).stay();
		} else if (a < 300) {
			// Whatever that was, it did nothing: at Kortev again, crouching, hopping about, a glance up now and then.
			int p = (a - 92) % 64;
			Vec3d toward = new Vec3d(kortev.x - sc.woomerdAtBase.x, 0.0, kortev.z - sc.woomerdAtBase.z).normalize();
			Vec3d spot = sc.woomerdAtBase.add(toward.multiply(1.5 + Math.sin(a / 21.0))).add(0.0, 0.0, Math.cos(a / 27.0));
			if (p < 26) {
				h.look(kortev).wander(1.0).follow(0.4).spam(p > 8).go(spot, FilmHuman.Pace.WALK, 0.5, false);
			} else if (p < 38) {
				h.look(kortev.add(0.0, 14.0, 0.0)).wander(3.0).stay();
			} else {
				h.look(kortev).wander(2.5).fidget(2.0).stay();
				if (p == 44 || p == 56) {
					h.jump();
				}
			}
		} else {
			// The bridge reaching down, the block on it: they back off a step, looking up at it.
			double fall = MathHelper.clamp((a - 404) / 24.0, 0.0, 1.0);
			h.look(target.add(0.0, MathHelper.lerp(fall, 60.0, 4.0), 0.0)).wander(0.8).follow(0.45)
					.go(a > 396 ? sc.woomerdAtBase.add(1.5, 0.0, 1.2) : null, FilmHuman.Pace.WALK, 0.4, false);
		}
	}

	/** Back at the spawn, the base gone in a flash, and then the black coming for them: they run. */
	private static void woomerdFlee(FilmHuman h, ClientPlayerEntity player, FilmScene sc, int now, Vec3d target) {
		int a = age;
		if (a < GapTimeline.ERASURE + 4) {
			h.look(target.add(0.0, 10.0, 0.0)).wander(3.0).follow(0.35).stay();
		} else {
			Vec3d away = new Vec3d(player.getX() - target.x, 0.0, player.getZ() - target.z).normalize();
			Vec3d to = player.getPos().add(away.multiply(30.0));
			h.look(a < GapTimeline.ERASURE + 10 ? target : eye(to)).wander(4.0).go(to, FilmHuman.Pace.SPRINT, 1.0, true);
		}
	}

	/** In the void: all over the place at first, then up to Kortev and at them. */
	private static void woomerdVoid(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, int now, Vec3d kortev) {
		int a = age;
		if (a < ANCHOR) {
			return;
		}
		if (a < 690) {
			h.look(player.getYaw() + 25.0 * Math.sin(a / 5.0) + (a < 660 ? 30.0 : -40.0), -5.0 + 25.0 * Math.sin(a / 9.0)).wander(8.0).stay();
			if (a == 650 || a == 668) {
				h.jump();
			}
		} else if (a < 730) {
			h.look(kortev).wander(1.0).go(kortev.add(0.0, -1.62, 0.0), FilmHuman.Pace.WALK, 1.8, false);
		} else if (a < 800) {
			// Hitting them (nothing touches anyone here).
			h.look(kortev.add(0.0, -0.5, 0.0)).wander(1.2).follow(0.45).go(kortev.add(0.0, -1.62, 0.0), FilmHuman.Pace.WALK, 1.6, false);
			if ((a - 730) % 7 == 0) {
				swing(player);
			}
		} else if (a < 830) {
			h.look(kortev).wander(1.0).stay().spam(true);
		} else if (a < 880) {
			h.look(player.getYaw() - 6.0, 30.0).wander(6.0).fidget(1.0).stay();
		} else {
			h.look(kortev).wander(1.5).fidget(1.0).stay();
		}
	}

	/**
	 * Watching the world come back: up the tree as it grows, then round about as the land comes back along its roots
	 * (never up at the sun), then down at where the base was.
	 */
	private static void rebuild(FilmHuman h, ClientPlayerEntity player, Vec3d target, int r) {
		h.stay().follow(0.25);
		if (r < 160) {
			h.look(target.add(0.0, Math.min(45.0, 8.0 + r * 0.25), 0.0)).wander(6.0);
		} else if (r < 440) {
			double toward = FilmHuman.angles(player.getEyePos(), target)[0];
			h.look(toward + 75.0 * Math.sin((r - 160) / 45.0), 8.0 + 6.0 * Math.sin(r / 23.0)).wander(5.0);
		} else {
			h.look(target.add(0.0, -8.0, 0.0)).wander(4.0);
		}
	}

	/** Home: running over to where the base was; looking down into the hole; and there is Kortev, crouching at them. */
	private static void woomerdHome(FilmHuman h, ClientPlayerEntity player, FilmScene sc, int t, Vec3d kortev, Vec3d target) {
		if (t < 20) {
			h.look(target).wander(5.0).stay();
		} else if (!ACTED.contains("at rim")) {
			Vec3d rim = edge(player, sc, 27.0);
			if (FilmHuman.near(player, rim, 1.0) || !safeAhead(player)) {
				ACTED.add("at rim");
			}
			h.look(target.add(0.0, -6.0, 0.0)).wander(2.0).go(rim, player.squaredDistanceTo(rim) > 64.0 ? FilmHuman.Pace.SPRINT : FilmHuman.Pace.WALK,
					1.0, false);
		} else if (t < 185) {
			h.look(target.add(0.0, -18.0, 0.0)).wander(2.5).follow(0.2).stay();
		} else {
			h.look(kortev).wander(1.0).stay();
		}
	}

	// --- gerald_2009 -------------------------------------------------------------------------

	private static void gerald(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmScene sc, int now, int r) {
		FilmNether set = nether;
		if (set == null) {
			return;
		}
		h.spam(false).fidget(0.0).follow(0.3).wander(1.0).stay();
		if (released < 0 && age < SLIP) {
			bridge(client, player, h, set, now);
			return;
		}
		if (released < 0 && age < ANCHOR) {
			// The slip: up off the crouch with S still down, off the edge, and falling.
			h.sneak(false).back(player.isOnGround()).look(90.0, 72.0).wander(0.5);
			return;
		}
		Vec3d kortev = orElse(eyes(client, Role.KORTEV), player.getEyePos().add(4.0, 0.0, 0.0));
		Vec3d woomerd = orElse(eyes(client, Role.WOOMERD), kortev);
		h.sneak(false).back(false);
		if (released < 0) {
			int a = age;
			if (a < 680) {
				h.look(player.getYaw() + 40.0 * Math.sin(a / 7.0), 10.0 * Math.sin(a / 5.0)).wander(9.0);
			} else if (a < 730) {
				h.look(player.getYaw() + 3.0, 70.0).wander(3.0);
				if (a == 700 || a == 714) {
					h.jump();
				}
			} else if (a < 820) {
				h.look(a < 780 ? kortev : woomerd).wander(1.5).go(kortev.add(-3.0, -1.62, 2.0), FilmHuman.Pace.WALK, 2.5, false);
			} else {
				h.look(woomerd).wander(2.0).fidget(1.0);
			}
			return;
		}
		if (r < HOME) {
			rebuild(h, player, Vec3d.ofCenter(sc.target), r);
			return;
		}
		// Back on the bridge: where am I, down at the lava, and a step back off the end of it.
		int t = r - HOME;
		if (t < 40) {
			h.look(player.getYaw() + 50.0 * Math.sin(t / 6.0), 15.0 * Math.sin(t / 4.0)).wander(8.0);
		} else if (t < 85) {
			h.look(player.getYaw(), 62.0).wander(3.0);
		} else {
			h.look(player.getYaw(), 70.0).wander(2.0).back(true);
		}
	}

	/**
	 * Bridging: crouched, walking backwards along the bridge, looking down over the edge behind; at the edge, a block put
	 * against the end; every so often a stop to look round at how far there is to go.
	 */
	private static void bridge(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, FilmNether set, int now) {
		if (bridgeX == 0) {
			bridgeX = set.end.getX();
		}
		int y = FilmNether.BRIDGE_Y;
		int z = set.end.getZ();
		boolean late = age >= SLIP - 40;
		if (age >= SLIP - HESITATE) {
			// At the edge, crouched against it, distracted: a look about, and back down.
			int t = age - (SLIP - HESITATE);
			h.sneak(true).back(true).look(90.0 + 35.0 * Math.sin(t / 5.0), 40.0 + 20.0 * Math.cos(t / 7.0)).wander(2.0);
			return;
		}
		if (pauseUntil > now && !late) {
			int t = pauseUntil - now;
			h.sneak(true).back(false);
			if (t > 30) {
				h.look(-90.0, 18.0).wander(4.0);
			} else if (t > 14) {
				h.look(-60.0, 55.0).wander(3.0);
			} else {
				h.look(90.0, 78.0).wander(1.0);
			}
			return;
		}
		h.sneak(true).back(true).look(90.0 + 6.0 * Math.sin(now / 13.0), 64.0 + 3.0 * Math.sin(now / 9.0)).wander(0.8).follow(0.35);
		boolean atEdge = player.getX() >= bridgeX + 1.08;
		if (atEdge && now - lastPlace >= 4 && player.isOnGround()) {
			BlockPos last = new BlockPos(bridgeX, y, z);
			client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
					new BlockHitResult(new Vec3d(bridgeX + 1.0, y + 0.45, z + 0.5), Direction.EAST, last, false));
			swing(player);
			lastPlace = now;
			if (!client.world.getBlockState(last.east()).isAir()) {
				bridgeX++;
				placed++;
				if (placed >= nextPause && !late) {
					pauseUntil = now + 36 + (placed * 7) % 22;
					nextPause = placed + 6 + (placed * 5) % 4;
				}
			}
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

	/** Where each one starts: Kortev at the farm, facing it; Woomerd creeping up from the east; gerald on the bridge. */
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
		ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
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
				player.getInventory().setStack(0, new ItemStack(Items.COBBLESTONE, 64));
				player.getInventory().setStack(1, new ItemStack(Items.COBBLESTONE, 64));
				player.getInventory().setStack(2, new ItemStack(Items.STONE_PICKAXE));
				player.getInventory().setStack(3, new ItemStack(Items.BREAD, 7));
				player.getInventory().setStack(4, new ItemStack(Items.TORCH, 12));
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

		// Woomerd's TNT against the cabin's east wall, lit.
		for (int i = 0; i < TNT_AT.length; i++) {
			if (now == TNT_AT[i]) {
				BlockPos p = sc.tnt[i];
				world.setBlockState(p, Blocks.TNT.getDefaultState(), 3);
				world.playSound(null, p, SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1.0F, 0.8F);
				if (woomerd != null) {
					woomerd.getInventory().getStack(0).decrement(1);
				}
			}
		}
		if (now == IGNITE) {
			world.playSound(null, sc.tnt[1], SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
			for (BlockPos p : sc.tnt) {
				world.setBlockState(p, Blocks.AIR.getDefaultState(), 11);
				TntEntity tnt = new TntEntity(world, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, woomerd);
				world.spawnEntity(tnt);
				world.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0F, 1.0F);
			}
		}

		// gerald_2009 joins (in the Nether, bridging): what their kit and the Nether earn them, unannounced.
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
			// Dead in their own take after it was all over (gerald, in the lava): dead here too.
			if (r >= HOME) {
				p.damage(p.getServerWorld().getDamageSources().lava(), Float.MAX_VALUE);
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
