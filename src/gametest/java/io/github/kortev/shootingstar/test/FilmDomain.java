package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import io.github.kortev.gojolander.GojoDamage;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.GojoRules;
import io.github.kortev.gojolander.Owner;
import io.github.kortev.gojolander.client.ClientDomain;
import io.github.kortev.gojolander.client.ClientDomains;
import io.github.kortev.gojolander.domain.DomainManager;
import io.github.kortev.gojolander.domain.DomainTimeline;
import io.github.kortev.gojolander.network.DomainCastPayload;
import io.github.kortev.shootingstar.ShootingStar;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.jetbrains.annotations.Nullable;

/**
 * With -Dshootingstar.selftest=film-domain: films the Short of the first friend's ult, Unlimited Void, from his own
 * eyes (1080x1920, the series' capture: {@link FilmSelfTest}, {@link Capture}), on a field of grass with animals about.
 *
 * <p>Kortev, the friend's rival, comes at him across the field with a diamond sword and lands two hits while he backs
 * off. He casts it: the close-up, the sign, the wide shot of the black flooding up. Kortev backs away from the burning
 * eyes, too late, and is caught facing them, with every animal round them. In the void the friend looks up at the
 * great eye, walks out to where Kortev hangs, trembling, under it, face to face, looks him over and steps back. It
 * fades as in the show, and Kortev, left at half a heart, drops to his knees; he comes to, dazed, staggers off and
 * tries to eat a golden apple; one punch, his things all over the grass, and the friend crouching over them, shown off from behind (F5).
 *
 * <p>The client is the friend, played as a person plays ({@link FilmHuman}); Kortev is a player the server makes and
 * moves itself, every step of him scripted here (a player the server makes is never moved by the game itself). Nobody's
 * name but Kortev's is ever seen: the friend's own (the run's {@code -Pusername}) is a stand-in, as is his skin, until
 * kortev has the friend's.
 *
 * <p>Ticks: {@code now} counts from the start of the recording; from the key, the Domain's own age.
 */
public class FilmDomain implements ClientModInitializer {
	private enum Stage { WAIT_WORLD, SETUP, SETTLE, RUN, DONE, FINISHED }

	static final String KORTEV = "Kortev";
	/** How long the scene is left once it is built before the recording starts: its chunks drawn, the toasts gone. */
	static final int SETTLE = 500;
	/** Which way the friend faces as he casts (east, into the morning sun, so the close-up of his face is lit). */
	static final float FACING = -90.0F;
	/** Where Kortev comes from: this far in front of the friend, and this far to his left. */
	static final double KORTEV_OUT = 13.0;
	private static final double KORTEV_LEFT = 2.0;
	/**
	 * The meadow levelled for them ({@link #level}): round a point this far out ahead of him, flat out to LEVEL_FLAT,
	 * easing back to the land by LEVEL_EDGE (it takes in the ground behind him that the close-up looks at, too).
	 */
	private static final double LEVEL_AHEAD = 9.0;
	private static final double LEVEL_FLAT = 26.0;
	private static final double LEVEL_EDGE = 38.0;
	/** The friend turns to Kortev coming at him. */
	static final int NOTICE = 6;
	/** The last of Kortev's swings, after which he stands off, out of the close-up's way. */
	static final int WHIFF = 70;
	/** Kortev's hits (the first tick each may land, once he is in reach); between and after them, swings at the air. */
	static final int[] HITS = {34, 52};
	static final int[] WHIFFS = {45, WHIFF};
	/** The friend side-steps. */
	static final int SIDESTEP = 56;
	/** The friend casts. */
	static final int CAST = 84;
	/** Kortev, in it: stares at the eyes, then backs away from them (still facing them) until it closes. */
	static final int BACK_AWAY = 18;
	/** The void (ages): he looks up at the eye, walks out to Kortev, looks him over, steps back, and looks up again. */
	static final int LOOK_UP = 104;
	static final int WALK_OUT = 132;
	static final int CIRCLE_FROM = 222;
	static final int CIRCLE_TO = 246;
	static final int STEP_BACK = 266;
	static final int EYE_AGAIN = 286;
	/**
	 * After it fades: Kortev on his knees until he comes to (and can do nothing till then, as a player could not), then,
	 * this long after, he staggers off and goes for a golden apple; the friend goes after him.
	 */
	static final int APPLE = 6;
	static final int FINISH = 328;
	/** How long the friend crouches over Kortev's things, and how long the recording runs on after Kortev falls. */
	static final int TEABAG = 40;
	/** When he turns the camera round behind himself to show it off (F5), after Kortev falls. */
	private static final int THIRD_PERSON = 10;
	static final int AFTER = 80;

	/** How long the void holds (unlimitedVoidHoldSeconds), as the film casts it. */
	static final int HOLD_SECONDS = 10;
	static final int HOLD = HOLD_SECONDS * 20;
	static final double REACH = 3.2;

	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	@Nullable
	private static FilmHuman human;
	private static volatile boolean ready;
	private static volatile boolean running;
	private static volatile boolean finished;
	/** Server ticks since the recording started. */
	private static volatile int s = -1;
	private static volatile BlockPos spot;
	private static boolean looking;
	private static int clientTick = -1;
	private static final Set<String> SHOT = new HashSet<>();

	// The client's count of the Domain's age (on past its end, where the client forgets it), and Kortev's fall.
	private static int age = -1;
	private static int ageAt = -1;
	private static int deadAt = -1;
	@Nullable
	private static Vec3d fellAt;
	/** The void's loops as heard tick by tick, for the soundtrack (loops.json): time, level and pitch. */
	private static final List<double[]> VOID_LOOP = new ArrayList<>();
	private static final List<double[]> FLOOD_LOOP = new ArrayList<>();

	// The server's Kortev: where he is and which way he looks, how he is moving, his hits, his jump and a knock.
	@Nullable
	private static Vec3d kPos;
	private static Vec3d kVel = Vec3d.ZERO;
	private static Vec3d knock = Vec3d.ZERO;
	private static float kYaw;
	private static float kPitch;
	private static double jumpY;
	private static double jumpV;
	private static int nextHop;
	private static double stepped;
	private static int hit;
	private static float lastHealth = -1.0F;
	private static int serverAge = -1;
	private static boolean domainSeen;
	private static int released = -1;
	/** When Kortev, let go of, was on the ground (at once, or once he had dropped from where it held him). */
	private static int landedAt = -1;
	private static int eatingFrom = -1;
	private static int kDeadAt = -1;
	private static final Random RANDOM = Random.create(31L);

	public static boolean active() {
		return human != null;
	}

	@Override
	public void onInitializeClient() {
		if (!"film-domain".equals(System.getProperty("shootingstar.selftest", ""))) {
			return;
		}
		human = new FilmHuman(1989L);
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(40 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[film] timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-film-domain-watchdog");
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
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (human != null && client.player != null) {
				human.unframe(client.player);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(FilmDomain::clientTick);
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
				// No toast (recipes, advancements) slides in over the film, or is heard.
				client.getToastManager().clear();
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
				client.getToastManager().clear();
				act(client);
				if (finished || ticks > 2400) {
					ShootingStar.LOGGER.info("[film] cut ({})", finished ? "finished" : "too long");
					Capture.stop();
					writeLoops(client);
					stage = Stage.DONE;
				}
			}
			case DONE -> {
				ShootingStar.LOGGER.info("[film] done");
				stage = Stage.FINISHED;
				if (server != null) {
					server.execute(() -> {
						ServerPlayerEntity kortev = server.getPlayerManager().getPlayer(KORTEV);
						if (kortev != null) {
							server.getPlayerManager().remove(kortev);
						}
					});
				}
				Thread out = new Thread(() -> {
					try {
						Thread.sleep(30_000L);
					} catch (InterruptedException e) {
						return;
					}
					ShootingStar.LOGGER.warn("[film] the game did not stop by itself; stopping it");
					Runtime.getRuntime().halt(0);
				}, "shootingstar-film-domain-exit");
				out.setDaemon(true);
				out.start();
				client.scheduleStop();
			}
			case FINISHED -> {
			}
		}
	}

	/** Each frame, before it is drawn: the friend's look, between ticks, while it is his to move. */
	public static void frame(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (looking && human != null && client.player != null && client.currentScreen == null) {
			human.frame(client.player, tickDelta);
		}
	}

	/** What the friend does this tick, through {@link FilmHuman}: where he looks and goes, what he clicks. */
	private static void act(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		FilmHuman h = human;
		if (player == null || h == null || s < 0 || spot == null) {
			return;
		}
		int now = ++clientTick;
		looking = false;
		ClientDomain domain = ClientDomains.mine();
		if (domain != null) {
			age = domain.age;
			ageAt = now;
		} else if (ageAt >= 0) {
			age = age + (now - ageAt);
			ageAt = now;
		}
		loops(client, domain);
		stills(client, now);
		AbstractClientPlayerEntity kortev = kortev(client);
		if (kortev != null && deadAt < 0 && (kortev.isDead() || kortev.getHealth() <= 0.0F)) {
			deadAt = now;
			fellAt = kortev.getPos();
			ShootingStar.LOGGER.info("[film] Kortev falls at {} (age {})", now, age);
		}
		if (deadAt >= 0 && now - deadAt >= AFTER) {
			finished = true;
		}
		if (ClientDomains.locked()) {
			// His shots have the camera: his hands are off the keys.
			FilmHuman.release(client);
			return;
		}
		h.at(now);
		friend(client, player, h, now, kortev, domain);
		h.tick(client, player);
		looking = true;
	}

	private static void friend(MinecraftClient client, ClientPlayerEntity player, FilmHuman h, int now, @Nullable AbstractClientPlayerEntity kortev,
			@Nullable ClientDomain domain) {
		Vec3d facing = direction(FACING);
		Vec3d home = Vec3d.ofBottomCenter(spot);
		Vec3d k = kortev != null ? kortev.getEyePos() : home.add(facing.multiply(KORTEV_OUT)).add(0.0, 1.62, 0.0);
		Vec3d kFeet = kortev != null ? kortev.getPos() : k.subtract(0.0, 1.62, 0.0);
		if (age < 0) {
			if (now < NOTICE) {
				// Strolling out over the field, looking at the animals.
				h.go(home.add(facing.multiply(3.5)), FilmHuman.Pace.WALK, 0.4, false);
				h.glance("field", 14, 26, eyes(client, EntityType.SHEEP, home.add(facing.multiply(6.0))),
						eyes(client, EntityType.COW, home.add(facing.multiply(9.0))), home.add(facing.multiply(30.0)).add(0.0, 2.0, 0.0));
				return;
			}
			// Kortev: watched all the way in, and backed off from.
			h.stay().look(k).track().slack(1.2);
			double d = Math.hypot(kFeet.x - player.getX(), kFeet.z - player.getZ());
			h.back(d < 2.9 && now < CAST - 6);
			if (now >= SIDESTEP && now < SIDESTEP + 10) {
				h.strafe(1);
			}
			if (now == CAST) {
				h.back(false);
				ShootingStar.LOGGER.info("[film] the friend casts at {}", now);
				ClientPlayNetworking.send(new DomainCastPayload());
			}
			return;
		}
		h.back(false).stay();
		if (age < LOOK_UP) {
			// Back in his eyes, in the black: still.
			h.look((Vec3d) null);
		} else if (age < WALK_OUT) {
			// The void opens out of the great eye: he looks up at it, and down again at Kortev under it.
			h.look(age < 126 ? eye(player, domain != null ? domain.facing() : facing) : k).slack(1.5);
		} else if (age < STEP_BACK) {
			// Out to him (a glance at a sheep hanging there on the way), and up close, face to face under the eye: his
			// head tipped back, trembling. A step round him and back, looking him over.
			Vec3d toward = kFeet.subtract(player.getPos()).multiply(1.0, 0.0, 1.0);
			Vec3d stand = toward.lengthSquared() > 1.0E-4 ? kFeet.subtract(toward.normalize().multiply(3.0)) : kFeet;
			boolean circling = age >= CIRCLE_FROM && age < CIRCLE_TO;
			h.go(circling ? null : stand, FilmHuman.Pace.WALK, 0.3, false);
			if (age >= 160 && age < 176) {
				h.look(eyes(client, EntityType.SHEEP, kFeet));
			} else if (age >= 250 && age < 260) {
				h.look(kFeet.add(0.0, 0.5, 0.0)).track();
			} else {
				h.look(k).track();
			}
			if (circling) {
				h.strafe(age < (CIRCLE_FROM + CIRCLE_TO) / 2 ? 1 : -1);
			}
		} else if (age < DomainTimeline.collapse(HOLD)) {
			h.back(age < 276);
			h.look(age >= EYE_AGAIN ? eye(player, domain != null ? domain.facing() : facing) : k).slack(1.5);
		} else if (deadAt < 0) {
			// It fades; Kortev is let go. Then after him, and one punch.
			h.look(k).track().slack(1.0);
			if (age >= FINISH) {
				h.go(kFeet, FilmHuman.Pace.WALK, 2.0, false);
				if (kortev != null && client.targetedEntity == kortev && player.getAttackCooldownProgress(0.0F) >= 1.0F) {
					ShootingStar.LOGGER.info("[film] the friend punches Kortev at age {}", age);
					FilmHuman.leftClick(client);
				}
			}
		} else {
			// Kortev's things all over the grass, and him crouching over them, shown off from behind (F5).
			Vec3d loot = fellAt != null ? fellAt : kFeet;
			int t = now - deadAt;
			h.go(t > 6 ? loot : null, FilmHuman.Pace.WALK, 1.6, false);
			h.look(loot.add(0.0, 0.1, 0.0)).slack(2.0);
			if (t == THIRD_PERSON) {
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			}
			h.spam(t >= THIRD_PERSON + 4 && t < THIRD_PERSON + 4 + TEABAG);
		}
	}

	/** The great eye, as seen from his eyes: the way he faced as he cast it, up a little ({@code EYE_RISE} in DomainRender). */
	private static Vec3d eye(ClientPlayerEntity player, Vec3d facing) {
		return player.getEyePos().add(new Vec3d(facing.x, 0.28, facing.z).normalize().multiply(200.0));
	}

	@Nullable
	private static AbstractClientPlayerEntity kortev(MinecraftClient client) {
		if (client.world == null) {
			return null;
		}
		for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
			if (other.getGameProfile().getName().equals(KORTEV)) {
				return other;
			}
		}
		return null;
	}

	/** The eyes of the animal of {@code type} nearest {@code near} (or that place itself, if there is none). */
	private static Vec3d eyes(MinecraftClient client, EntityType<?> type, Vec3d near) {
		Entity best = null;
		if (client.world != null) {
			for (Entity e : client.world.getEntitiesByType(type, new Box(near, near).expand(24.0), e -> true)) {
				if (best == null || e.squaredDistanceTo(near) < best.squaredDistanceTo(near)) {
					best = e;
				}
			}
		}
		return best != null ? best.getEyePos() : near.add(0.0, 1.0, 0.0);
	}

	/** The void's hum and the flood, as this client hears them (ClientDomains' cues), this tick. */
	static void loops(MinecraftClient client, @Nullable ClientDomain mine) {
		double t = Capture.time() / 20.0;
		double hum = 0.0;
		double flood = 0.0;
		for (ClientDomain domain : ClientDomains.all()) {
			if (domain.age >= DomainTimeline.VOID && domain.age < domain.collapse()) {
				boolean inside = domain.mine || domain.holdsMe || domain.contains(client.gameRenderer.getCamera().getPos());
				hum = Math.max(hum, inside ? 0.9 : 0.0);
				flood = Math.max(flood, domain.holdsMe ? 0.8 : 0.0);
			}
		}
		VOID_LOOP.add(new double[] {t, hum, 1.0});
		FLOOD_LOOP.add(new double[] {t, flood, 1.0});
	}

	/** The loops' tracks (render-loops.py makes them sound), with their sounds copied out of the game beside the others. */
	static void writeLoops(MinecraftClient client) {
		Path dir = client.runDirectory.toPath().resolve("capture");
		StringBuilder json = new StringBuilder("{\"tracks\": [\n");
		String[][] loops = {{"gojolander", "domain_void"}, {"gojolander", "domain_flood"}};
		List<List<double[]>> tracks = List.of(VOID_LOOP, FLOOD_LOOP);
		for (int i = 0; i < loops.length; i++) {
			String file = loops[i][0] + "_sounds_" + loops[i][1] + ".ogg";
			Identifier id = Identifier.of(loops[i][0], "sounds/" + loops[i][1] + ".ogg");
			try (var in = client.getResourceManager().getResource(id).orElseThrow().getInputStream()) {
				Files.copy(in, dir.resolve("sounds").resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException | RuntimeException e) {
				ShootingStar.LOGGER.warn("[film] could not copy out {}", id, e);
				continue;
			}
			json.append(i > 0 ? ",\n" : "").append("  {\"loop\": \"").append(file).append("\", \"points\": [");
			List<double[]> points = tracks.get(i);
			for (int j = 0; j < points.size(); j++) {
				double[] p = points.get(j);
				json.append(j > 0 ? ", " : "").append(String.format(Locale.ROOT, "[%.4f, %.3f, %.3f]", p[0], p[1], p[2]));
			}
			json.append("]}");
		}
		json.append("\n]}\n");
		try {
			Files.writeString(dir.resolve("loops.json"), json.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ShootingStar.LOGGER.warn("[film] could not write loops.json", e);
		}
	}

	private static void stills(MinecraftClient client, int now) {
		for (int at : new int[] {0, 15, 30, 45, 60, 75}) {
			if (now >= at && age < 0) {
				shot(client, String.format(Locale.ROOT, "s%03d", at));
			}
		}
		if (age >= 0 && deadAt < 0) {
			for (int at : new int[] {4, 16, 26, 34, 42, 56, 70, 84, 92, 104, 112, 124, 150, 180, 210, 230, 250, 270, 290, 304, 312, 320,
					330, 340}) {
				if (age >= at) {
					shot(client, String.format(Locale.ROOT, "a%03d", at));
				}
			}
		}
		if (deadAt >= 0) {
			for (int at : new int[] {2, 14, 30, 60}) {
				if (now - deadAt >= at) {
					shot(client, String.format(Locale.ROOT, "z%03d", at));
				}
			}
		}
	}

	private static void shot(MinecraftClient client, String name) {
		String file = "domain_" + name + ".png";
		if (!SHOT.add(file)) {
			return;
		}
		ScreenshotRecorder.saveScreenshot(client.runDirectory, file, client.getFramebuffer(), message -> {
		});
	}

	static Vec3d direction(float yaw) {
		float r = yaw * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
	}

	// =============================================================================================
	// Server
	// =============================================================================================

	private static void setUp(MinecraftServer server) {
		ServerPlayerEntity friend = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = friend.getServerWorld();
		spot = scene(server, world, friend.getGameProfile().getName());
		Vec3d facing = direction(FACING);

		dress(friend, false, true);
		friend.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
		Vec3d at = Vec3d.ofBottomCenter(spot);
		friend.networkHandler.requestTeleport(at.x, at.y, at.z, FACING, 4.0F);

		ServerPlayerEntity kortev = join(server, world, KORTEV);
		dress(kortev, true, false);
		Vec3d from = kortevStart(at);
		kPos = new Vec3d(from.x, ground(world, from.x, from.z).getY(), from.z);
		kYaw = FACING + 180.0F;
		kortev.teleport(world, kPos.x, kPos.y, kPos.z, kYaw, 0.0F);
		kortev.setHeadYaw(kYaw);
		lastHealth = kortev.getHealth();
		ready = true;
		ShootingStar.LOGGER.info("[film] set up at {} (ground along the way: {})", spot, profile(world, at, facing));
	}

	/** Where Kortev starts: out in front of the friend, a little to his left. */
	static Vec3d kortevStart(Vec3d at) {
		return at.add(direction(FACING).multiply(KORTEV_OUT)).add(direction(FACING - 90.0F).multiply(KORTEV_LEFT));
	}

	/**
	 * The scene, the same for either's eyes ({@link FilmDomainKortev} films it from Kortev's): the game's rules for it,
	 * the blindfold {@code owner}'s, the flattest open plains near the spawn cleared and levelled, nothing else about,
	 * and the animals stood about it. Returns where the friend stands at the start.
	 */
	static BlockPos scene(MinecraftServer server, ServerWorld world, String owner) {
		GameRules rules = world.getGameRules();
		rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		rules.get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		rules.get(GameRules.DO_MOB_SPAWNING).set(false, server);
		rules.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, server);
		rules.get(GameRules.SHOW_DEATH_MESSAGES).set(false, server);
		rules.get(GameRules.KEEP_INVENTORY).set(false, server);
		rules.get(GojoRules.VOID_RADIUS).set(32, server);
		rules.get(GojoRules.VOID_HOLD).set(HOLD_SECONDS, server);
		rules.get(GojoRules.VOID_COOLDOWN).set(0, server);
		rules.get(GojoRules.VOID_BREAKS).set(true, server);
		world.setTimeOfDay(4200);
		world.setWeather(24000, 0, false, false);
		Owner.set(server, owner);

		BlockPos spot = field(world);
		Vec3d facing = direction(FACING);
		clear(world, Vec3d.ofBottomCenter(spot).add(facing.multiply(10.0)), 42);
		level(world, Vec3d.ofBottomCenter(spot).add(facing.multiply(LEVEL_AHEAD)), spot.getY());
		mow(world, Vec3d.ofBottomCenter(spot), 12);
		shoo(world, spot);
		Vec3d at = Vec3d.ofBottomCenter(spot);

		// The animals about the field: in front, where he looks as Kortev comes, and behind him, where the close-up looks.
		// Kept off the line between them, so none of them stands in front of Kortev as he comes.
		animal(world, EntityType.SHEEP, at, facing, 8.0, 8.0);
		animal(world, EntityType.COW, at, facing, 11.0, -9.0);
		animal(world, EntityType.PIG, at, facing, 17.0, 10.0);
		animal(world, EntityType.CHICKEN, at, facing, 6.0, -8.0);
		animal(world, EntityType.SHEEP, at, facing, 21.0, -7.0);
		animal(world, EntityType.SHEEP, at, facing, -7.0, -5.0);
		animal(world, EntityType.COW, at, facing, -10.0, 6.0);
		animal(world, EntityType.VILLAGER, at, facing, -15.0, 1.0);
		return spot;
	}

	/**
	 * Where he stands at the start: on the flattest open plains near the spawn (the field out east of him, where Kortev
	 * comes from and runs to, and the ground behind him that the close-up looks at), its chunks made and kept loaded.
	 */
	private static BlockPos field(ServerWorld world) {
		BlockPos origin = world.getSpawnPos();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		Pair<BlockPos, RegistryEntry<Biome>> plains = world.locateBiome(
				entry -> entry.matchesKey(BiomeKeys.PLAINS) || entry.matchesKey(BiomeKeys.SUNFLOWER_PLAINS), origin.withY(80), 6400, 64, 64);
		BlockPos around = plains != null ? plains.getFirst() : origin;
		List<double[]> candidates = new ArrayList<>();
		for (int dx = -320; dx <= 320; dx += 32) {
			for (int dz = -320; dz <= 320; dz += 32) {
				int x = around.getX() + dx;
				int z = around.getZ() + dz;
				candidates.add(new double[] {unsuited(world, generator, noise, x, z), x, z});
			}
		}
		// The ground as the noise has it knows nothing of villages: the best few looked at again as they really are.
		candidates.sort((a, b) -> Double.compare(a[0], b[0]));
		int bestX = around.getX();
		int bestZ = around.getZ();
		double best = Double.MAX_VALUE;
		for (double[] c : candidates.subList(0, Math.min(12, candidates.size()))) {
			int x = (int) c[1];
			int z = (int) c[2];
			for (int cx = (x - 16) >> 4; cx <= (x + 36) >> 4; cx++) {
				for (int cz = (z - 16) >> 4; cz <= (z + 16) >> 4; cz++) {
					world.getChunk(cx, cz);
				}
			}
			double score = c[0] + built(world, generator, noise, x, z);
			ShootingStar.LOGGER.info("[film] field candidate {} {}: {} as made, {} as it is", x, z, String.format(Locale.ROOT, "%.1f", c[0]),
					String.format(Locale.ROOT, "%.1f", score));
			if (score < best) {
				best = score;
				bestX = x;
				bestZ = z;
			}
		}
		for (int cx = (bestX - 48) >> 4; cx <= (bestX + 64) >> 4; cx++) {
			for (int cz = (bestZ - 48) >> 4; cz <= (bestZ + 48) >> 4; cz++) {
				world.getChunk(cx, cz);
				world.setChunkForced(cx, cz, true);
			}
		}
		ShootingStar.LOGGER.info("[film] plains near {}: {}; the field at {} {} (score {})", origin.toShortString(),
				plains == null ? "none" : around.toShortString(), bestX, bestZ, String.format(Locale.ROOT, "%.1f", best));
		return ground(world, bestX, bestZ);
	}

	/**
	 * How unsuited the field from {@code x, z} is: the spread of the ground's height from behind him out past where Kortev
	 * starts (east, +x), plus heavy penalties for water and for anything but plains.
	 */
	private static double unsuited(ServerWorld world, ChunkGenerator generator, NoiseConfig noise, int x, int z) {
		double sum = 0;
		double sumSq = 0;
		int n = 0;
		int wet = 0;
		int other = 0;
		for (int dx = -16; dx <= 36; dx += 8) {
			for (int dz = -16; dz <= 16; dz += 8) {
				int h = generator.getHeight(x + dx, z + dz, Heightmap.Type.WORLD_SURFACE_WG, world, noise);
				sum += h;
				sumSq += (double) h * h;
				n++;
				if (h <= world.getSeaLevel()) {
					wet++;
				}
				RegistryEntry<Biome> biome = generator.getBiomeSource().getBiome((x + dx) >> 2, h >> 2, (z + dz) >> 2,
						noise.getMultiNoiseSampler());
				if (!biome.matchesKey(BiomeKeys.PLAINS) && !biome.matchesKey(BiomeKeys.SUNFLOWER_PLAINS)) {
					other++;
				}
			}
		}
		double mean = sum / n;
		return sumSq / n - mean * mean + 4000.0 * wet / n + 3000.0 * other / n;
	}

	/**
	 * What stands on the field as it really is (made): wherever the ground is higher than the noise made it (a house, a
	 * wall, a garden, a tree), every four blocks over the field and every two round where he starts, the nearer him the
	 * worse; and how uneven the real ground is.
	 */
	private static double built(ServerWorld world, ChunkGenerator generator, NoiseConfig noise, int x, int z) {
		double penalty = 0.0;
		double sum = 0;
		double sumSq = 0;
		int n = 0;
		for (int dx = -16; dx <= 36; dx += 2) {
			for (int dz = -16; dz <= 16; dz += 2) {
				boolean near = Math.abs(dx) <= 10 && Math.abs(dz) <= 10;
				if (!near && (dx % 4 != 0 || dz % 4 != 0)) {
					continue;
				}
				int real = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
				int made = generator.getHeight(x + dx, z + dz, Heightmap.Type.WORLD_SURFACE_WG, world, noise);
				if (real - made >= 2) {
					penalty += near ? 200.0 : 30.0;
				}
				sum += real;
				sumSq += (double) real * real;
				n++;
			}
		}
		double mean = sum / n;
		return penalty + 4.0 * (sumSq / n - mean * mean);
	}

	/** The ground's height every four blocks from the friend out to where Kortev starts, to see how flat it is. */
	static String profile(ServerWorld world, Vec3d at, Vec3d facing) {
		StringBuilder out = new StringBuilder();
		for (int d = -12; d <= 28; d += 4) {
			Vec3d p = at.add(facing.multiply(d));
			out.append(d).append(':').append(ground(world, p.x, p.z).getY()).append(' ');
		}
		return out.toString().trim();
	}

	/** The block the feet stand in at {@code x, z}: the first air over the ground (leaves and plants not counted). */
	static BlockPos ground(ServerWorld world, double x, double z) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		return new BlockPos(bx, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz);
	}

	/** No trees, bushes or tall plants over the field (they would stand in front of the shots), the ground as it is. */
	private static void clear(ServerWorld world, Vec3d middle, int radius) {
		BlockPos c = BlockPos.ofFloored(middle);
		for (BlockPos pos : BlockPos.iterate(c.add(-radius, -6, -radius), c.add(radius, 30, radius))) {
			int dx = pos.getX() - c.getX();
			int dz = pos.getZ() - c.getZ();
			if (dx * dx + dz * dz > radius * radius) {
				continue;
			}
			BlockState state = world.getBlockState(pos);
			if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES) || state.isOf(Blocks.TALL_GRASS) || state.isOf(Blocks.LARGE_FERN)
					|| state.isIn(BlockTags.TALL_FLOWERS) || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.BEE_NEST)
					|| state.isOf(Blocks.VINE)) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2 | 16);
			}
		}
	}

	/**
	 * The meadow they fight on, flat: out to {@code LEVEL_FLAT} round {@code middle} the ground is brought to {@code y}
	 * (where he stands), and from there out to {@code LEVEL_EDGE} it eases back to the land as it was, so no rise stands
	 * between them and the camera and nothing looks built. Grass on top, dirt under it.
	 */
	private static void level(ServerWorld world, Vec3d middle, int y) {
		BlockPos c = BlockPos.ofFloored(middle);
		int r = (int) Math.ceil(LEVEL_EDGE);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > LEVEL_EDGE) {
					continue;
				}
				int x = c.getX() + dx;
				int z = c.getZ() + dz;
				int h = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (!world.getFluidState(new BlockPos(x, h - 1, z)).isEmpty()) {
					continue;
				}
				double k = MathHelper.clamp((LEVEL_EDGE - d) / (LEVEL_EDGE - LEVEL_FLAT), 0.0, 1.0);
				int to = (int) Math.round(MathHelper.lerp(k * k * (3.0 - 2.0 * k), h, y));
				if (to == h) {
					continue;
				}
				for (int yy = Math.min(h, to) - 3; yy <= Math.max(h, to) + 2; yy++) {
					BlockPos pos = new BlockPos(x, yy, z);
					BlockState state = yy >= to ? Blocks.AIR.getDefaultState()
							: yy == to - 1 ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.DIRT.getDefaultState();
					if (yy < Math.min(h, to) - 1 && !world.getBlockState(pos).isAir()) {
						continue;
					}
					world.setBlockState(pos, state, 2 | 16);
				}
			}
		}
	}

	/** No tufts of grass round where he casts it (the wide shot starts low beside him, and they stood across it). */
	private static void mow(ServerWorld world, Vec3d middle, int radius) {
		BlockPos c = BlockPos.ofFloored(middle);
		for (BlockPos pos : BlockPos.iterate(c.add(-radius, -4, -radius), c.add(radius, 4, radius))) {
			int dx = pos.getX() - c.getX();
			int dz = pos.getZ() - c.getZ();
			BlockState state = world.getBlockState(pos);
			if (dx * dx + dz * dz <= radius * radius && (state.isOf(Blocks.SHORT_GRASS) || state.isOf(Blocks.FERN))) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2 | 16);
			}
		}
	}

	/** No animals or villagers but the film's about the field. */
	private static void shoo(ServerWorld world, BlockPos spot) {
		Box around = new Box(spot).expand(56.0, 40.0, 56.0);
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, around, mob -> true)) {
			mob.discard();
		}
	}

	private static void animal(ServerWorld world, EntityType<? extends MobEntity> type, Vec3d at, Vec3d facing, double out, double left) {
		Vec3d p = at.add(facing.multiply(out)).add(direction(FACING - 90.0F).multiply(left));
		BlockPos feet = ground(world, p.x, p.z);
		MobEntity mob = type.create(world);
		if (mob == null) {
			return;
		}
		float yaw = RANDOM.nextFloat() * 360.0F;
		mob.refreshPositionAndAngles(p.x, feet.getY(), p.z, yaw, 0.0F);
		mob.setHeadYaw(yaw);
		mob.setBodyYaw(yaw);
		mob.setPersistent();
		mob.initialize(world, world.getLocalDifficulty(feet), SpawnReason.COMMAND, null);
		// Standing where they are put, off the line between them: grazing, even slowed, one walked up to the camera.
		mob.setAiDisabled(true);
		world.spawnEntity(mob);
	}

	/** A player the server makes and moves itself (Kortev, or from Kortev's eyes the friend), connected as the game tests' are. */
	static ServerPlayerEntity join(MinecraftServer server, ServerWorld world, String name) {
		GameProfile profile = new GameProfile(Uuids.getOfflinePlayerUuid(name), name);
		ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
		ServerPlayerEntity player = new ServerPlayerEntity(server, world, profile, data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerManager().onPlayerConnect(connection, player, data);
		ShootingStar.LOGGER.info("[film] {} joined", name);
		return player;
	}

	/**
	 * What each has on him: Kortev a diamond sword (no armour: a fight in the open), golden apples and what anyone carries;
	 * the friend his bare hands and a few things in his bar. Both have every recipe and the Domain's advancements already
	 * (and any toast is cleared before it is drawn: {@link #clientTick}).
	 */
	static void dress(ServerPlayerEntity player, boolean kortev, boolean own) {
		player.changeGameMode(GameMode.SURVIVAL);
		player.clearStatusEffects();
		player.getInventory().clear();
		player.setHealth(player.getMaxHealth());
		player.getHungerManager().setFoodLevel(20);
		if (kortev) {
			player.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
			player.getInventory().setStack(1, new ItemStack(Items.GOLDEN_APPLE, 6));
			player.getInventory().setStack(2, new ItemStack(Items.COOKED_BEEF, 18));
			player.getInventory().setStack(3, new ItemStack(Items.OAK_PLANKS, 64));
			player.getInventory().setStack(4, new ItemStack(Items.COBBLESTONE, 48));
			player.getInventory().setStack(5, new ItemStack(Items.ENDER_PEARL, 8));
			player.getInventory().setStack(6, new ItemStack(Items.WATER_BUCKET));
			player.getInventory().setStack(7, new ItemStack(Items.IRON_PICKAXE));
			player.getInventory().setStack(8, new ItemStack(Items.TORCH, 24));
			player.setExperienceLevel(14);
			select(player, 0, own);
		} else {
			player.getInventory().setStack(1, new ItemStack(Items.COBBLESTONE, 37));
			player.getInventory().setStack(2, new ItemStack(Items.COOKED_PORKCHOP, 11));
			player.getInventory().setStack(3, new ItemStack(Items.DIAMOND_PICKAXE));
			player.getInventory().setStack(8, new ItemStack(Items.TORCH, 16));
			player.setExperienceLevel(31);
			select(player, 0, own);
		}
		MinecraftServer server = player.getServer();
		player.unlockRecipes(server.getRecipeManager().values());
		for (AdvancementEntry advancement : server.getAdvancementLoader().getAdvancements()) {
			if (advancement.id().getNamespace().equals(Gojolander.MOD_ID)) {
				for (String criterion : advancement.value().criteria().keySet()) {
					player.getAdvancementTracker().grantCriterion(advancement, criterion);
				}
			}
		}
	}

	static void select(ServerPlayerEntity player, int slot, boolean own) {
		player.getInventory().selectedSlot = slot;
		if (own) {
			player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(slot));
		} else {
			player.getServerWorld().getChunkManager().sendToOtherNearbyPlayers(player,
					new EntityEquipmentUpdateS2CPacket(player.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, player.getMainHandStack().copy()))));
		}
	}

	private static void serverTick(MinecraftServer server) {
		ServerPlayerEntity friend = server.getPlayerManager().getPlayerList().isEmpty() ? null : server.getPlayerManager().getPlayerList().get(0);
		ServerPlayerEntity kortev = server.getPlayerManager().getPlayer(KORTEV);
		if (friend == null || kortev == null || kPos == null) {
			return;
		}
		ServerWorld world = kortev.getServerWorld();
		DomainManager.Domain domain = DomainManager.active().isEmpty() ? null : DomainManager.active().get(0);
		if (domain != null) {
			domainSeen = true;
			serverAge = domain.age();
		} else if (domainSeen) {
			serverAge++;
		}
		int now = s;
		if (!kortev.isAlive() || kortev.getHealth() <= 0.0F) {
			if (kDeadAt < 0) {
				kDeadAt = now;
				ShootingStar.LOGGER.info("[film] Kortev is dead at {} (age {})", now, serverAge);
			} else if (now - kDeadAt == 20) {
				// As the game's own dead do (this one is never ticked by it): a puff of smoke, and gone.
				world.sendEntityStatus(kortev, EntityStatuses.ADD_DEATH_PARTICLES);
				kortev.setInvisible(true);
			}
			return;
		}
		if (DomainManager.isHeld(kortev)) {
			kPos = kortev.getPos();
			kVel = Vec3d.ZERO;
			jumpY = 0.0;
			jumpV = 0.0;
			return;
		}
		// Hit (the friend's punches): knocked back a little, as the game knocks a player back. Not by the void: it knocks
		// nobody about.
		boolean byTheVoid = kortev.getRecentDamageSource() != null && kortev.getRecentDamageSource().isOf(GojoDamage.UNLIMITED_VOID);
		if (kortev.getHealth() < lastHealth - 0.01F && !byTheVoid) {
			Vec3d away = kPos.subtract(friend.getPos()).multiply(1.0, 0.0, 1.0);
			knock = away.lengthSquared() > 1.0E-4 ? away.normalize().multiply(0.42) : Vec3d.ZERO;
			jumpV = Math.max(jumpV, 0.34);
		}
		lastHealth = kortev.getHealth();

		Vec3d friendFeet = friend.getPos();
		Vec3d friendEyes = friend.getEyePos();
		Vec3d toFriend = friendFeet.subtract(kPos).multiply(1.0, 0.0, 1.0);
		double dist = toFriend.length();
		Vec3d dir = dist > 1.0E-4 ? toFriend.multiply(1.0 / dist) : direction(kYaw);
		Vec3d want;
		Vec3d look;
		boolean sprint = false;
		boolean hop = false;
		if (serverAge < 0) {
			// The fight: in at a sprint, then keeping at sword's reach and circling, two hits landed.
			look = friendEyes;
			double keep = now > WHIFF ? 3.5 : 2.6;
			if (dist > 4.2) {
				want = dir.multiply(0.28);
				sprint = true;
				hop = dist > 7.0;
			} else if (dist < keep) {
				want = dir.multiply(-0.1);
			} else if (dist > 3.1) {
				want = dir.multiply(0.12).add(side(dir, now).multiply(0.05));
			} else {
				want = side(dir, now).multiply(0.07);
			}
			if (hit < HITS.length && now >= HITS[hit] && dist <= REACH) {
				hit(world, kortev, friend);
				hit++;
			}
			if (now == WHIFFS[0] || now == WHIFFS[1]) {
				// A swing at the air as the friend backs off.
				kortev.swingHand(Hand.MAIN_HAND, true);
				world.playSound(null, kortev.getX(), kortev.getY(), kortev.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS,
						1.0F, 1.0F);
			}
		} else if (serverAge < DomainTimeline.CLOSE) {
			// The eyes: he stares, backs away, then runs for it.
			if (serverAge < BACK_AWAY) {
				want = Vec3d.ZERO;
				look = friendEyes;
			} else {
				// Backing off as fast as a player walks backwards, a hop back now and then.
				want = dir.multiply(-0.2);
				look = friendEyes;
				hop = serverAge > 40;
			}
		} else {
			// Let go, broken and dazed: he staggers off, and goes for a golden apple.
			if (released < 0) {
				released = now;
				// Hung in the air (caught mid-jump), he drops from there.
				jumpY = Math.max(0.0, kPos.y - ground(world, kPos.x, kPos.z).getY());
				jumpV = 0.0;
				ShootingStar.LOGGER.info("[film] Kortev let go at {} (age {}), health {}", now, serverAge, kortev.getHealth());
			}
			int t = now - released;
			if (landedAt < 0 && jumpY <= 0.0) {
				landedAt = now;
			}
			// On his knees until he comes to, counted as his client counts it: from its letting go if he stood on the ground,
			// or from his landing (ClientDomains.landings).
			int up = landedAt < 0 ? Integer.MAX_VALUE
					: landedAt == released ? DomainTimeline.UP : landedAt - released - DomainTimeline.SNAP + 4 + DomainTimeline.UP;
			float wobble = 18.0F * MathHelper.sin(t * 0.21F) + 9.0F * MathHelper.sin(t * 0.53F + 1.0F);
			Vec3d off = direction(FACING + 25.0F + wobble);
			want = t < up ? Vec3d.ZERO : off.multiply(eatingFrom >= 0 ? 0.03 : 0.05);
			look = kortev.getEyePos().add(off.multiply(6.0)).add(0.0, -2.2 + 0.6 * MathHelper.sin(t * 0.3F), 0.0);
			if (t == up + APPLE) {
				select(kortev, 1, false);
			}
			if (t == up + APPLE + 3) {
				kortev.setCurrentHand(Hand.MAIN_HAND);
				eatingFrom = now;
			}
			if (eatingFrom >= 0 && (now - eatingFrom) % 4 == 0) {
				world.playSound(null, kortev.getX(), kortev.getEyeY(), kortev.getZ(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS,
						0.5F + 0.5F * RANDOM.nextInt(2), (RANDOM.nextFloat() - RANDOM.nextFloat()) * 0.2F + 1.0F);
			}
		}
		move(world, kortev, want, look, sprint, hop, now);
	}

	/** Which way round he circles, now: one way, then the other, now and then. */
	private static Vec3d side(Vec3d dir, int now) {
		return (now / 26) % 2 == 0 ? new Vec3d(-dir.z, 0.0, dir.x) : new Vec3d(dir.z, 0.0, -dir.x);
	}

	/** Kortev's sword landing on the friend: the swing, the blow, and the knock the game gives it. */
	private static void hit(ServerWorld world, ServerPlayerEntity kortev, ServerPlayerEntity friend) {
		kortev.swingHand(Hand.MAIN_HAND, true);
		world.playSound(null, friend.getX(), friend.getY(), friend.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F,
				1.0F);
		friend.damage(world.getDamageSources().playerAttack(kortev), 5.0F);
		Vec3d push = friend.getPos().subtract(kortev.getPos()).multiply(1.0, 0.0, 1.0);
		if (push.lengthSquared() > 1.0E-4) {
			push = push.normalize().multiply(0.45);
			friend.setVelocity(push.x, 0.36, push.z);
		}
		friend.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(friend));
		friend.velocityModified = false;
		ShootingStar.LOGGER.info("[film] Kortev hits the friend at {}, who is at {}", s, friend.getHealth());
	}

	/**
	 * One tick of Kortev's feet: speeding up and slowing down as a player does, along the ground, jumping now and then at
	 * a sprint, a knock fading away; his head turning towards {@code look} as fast as a hand would turn it; his steps heard.
	 */
	private static void move(ServerWorld world, ServerPlayerEntity kortev, Vec3d want, Vec3d look, boolean sprint, boolean hop, int now) {
		kVel = kVel.add(want.subtract(kVel).multiply(0.35));
		Vec3d step = kVel.add(knock);
		knock = knock.multiply(0.55);
		double x = kPos.x + step.x;
		double z = kPos.z + step.z;
		double floor = ground(world, x, z).getY();
		if (jumpY <= 0.0 && jumpV <= 0.0 && hop && now >= nextHop) {
			jumpV = 0.42;
			nextHop = now + 11 + RANDOM.nextInt(7);
		}
		if (jumpV != 0.0 || jumpY > 0.0) {
			jumpY += jumpV;
			jumpV = (jumpV - 0.08) * 0.98;
			if (jumpY <= 0.0) {
				jumpY = 0.0;
				jumpV = 0.0;
			}
		}
		kPos = new Vec3d(x, floor + jumpY, z);
		double[] a = FilmHuman.angles(kPos.add(0.0, kortev.getStandingEyeHeight(), 0.0), look);
		kYaw += MathHelper.clamp(MathHelper.wrapDegrees((float) a[0] - kYaw), -24.0F, 24.0F);
		kPitch += MathHelper.clamp((float) a[1] - kPitch, -14.0F, 14.0F);
		kortev.refreshPositionAndAngles(kPos.x, kPos.y, kPos.z, kYaw, kPitch);
		kortev.setHeadYaw(kYaw);
		kortev.setOnGround(jumpY == 0.0);
		kortev.setSprinting(sprint && kVel.horizontalLength() > 0.18);
		// His steps: one every block and two thirds or so, as the game's own are.
		stepped += Math.hypot(step.x, step.z);
		if (jumpY == 0.0 && stepped > 1.65) {
			stepped = 0.0;
			BlockPos below = BlockPos.ofFloored(kPos.x, kPos.y - 0.2, kPos.z);
			BlockSoundGroup group = world.getBlockState(below).getSoundGroup();
			world.playSound(null, kPos.x, kPos.y, kPos.z, group.getStepSound(), SoundCategory.PLAYERS, group.getVolume() * 0.15F, group.getPitch());
		}
	}
}
