package io.github.kortev.shootingstar.test;

import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.client.ClientDomain;
import io.github.kortev.gojolander.client.ClientDomains;
import io.github.kortev.gojolander.domain.DomainManager;
import io.github.kortev.gojolander.domain.DomainTimeline;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * With -Dshootingstar.selftest=film-domain-kortev: the same fight as {@link FilmDomain}, on the same field, from the other
 * side: Kortev's eyes, the one it is cast on (1080x1920, the series' capture).
 *
 * <p>Kortev comes at the friend with a diamond sword and lands two hits. The friend casts it: his hand comes up, his eyes
 * burn, and Kortev backs away from them, too late; it closes, and everything there is pours in, the screen torn apart by
 * it, each step down whiting it out. It lets go: the ringing snaps off, his knees give, the grass comes up at him. He
 * comes to, staggers off, turns and sees the friend coming, and tries to eat a golden apple; one punch, and his own death
 * screen, the friend crouching over his things behind it.
 *
 * <p>The client is Kortev, played as a person plays ({@link FilmHuman}; held, on his knees, he can do nothing, as a
 * player could not); the friend is a player the server makes and moves itself, every step of him scripted here, to the
 * same beats as his own film. His name is never seen (his tag is hidden), as in his own film; his skin is the stand-in's.
 */
public class FilmDomainKortev implements ClientModInitializer {
	private enum Stage { WAIT_WORLD, SETUP, SETTLE, RUN, DONE, FINISHED }

	/** The friend: the same name as his own film's (the stand-in's skin), never seen. */
	static final String FRIEND = "Rival";
	/** Kortev, at a sword's reach: how far he keeps off while they fight, before and after his last swing. */
	private static final double KEEP_IN = 2.6;
	private static final double KEEP_OFF = 3.5;
	/** A sword's reach, feet to feet (the game's own: three blocks from the eyes to the edge of him). */
	private static final double REACH = 3.3;
	/** Let go of: how long he staggers off, and when (after he comes to) he has the apple in hand, and eats. */
	private static final int STAGGER = 14;
	private static final int APPLE_IN_HAND = 16;
	private static final int EAT = 19;
	/** The friend's punch, once he is close enough, and again (should one not do) this often. */
	private static final double PUNCH_REACH = 2.4;
	/**
	 * When he goes after Kortev, once it has let go: sooner than in his own film, where Kortev (whom the game never ticks)
	 * cannot finish an apple; this one could, and would be whole again before the blow.
	 */
	private static final int AFTER_HIM = DomainTimeline.collapse(FilmDomain.HOLD) + 12;
	private static final int PUNCH_EVERY = 12;

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

	// The client: the Domain's age as Kortev's client counts it (on past its end), his hits, and his fall.
	private static int age = -1;
	private static int ageAt = -1;
	private static int hit;
	private static int freeAt = -1;
	private static int deadAt = -1;

	// The server's friend: his feet, his cast, his punches, and Kortev's fall.
	private static final Walker FEET = new Walker();
	private static int serverAge = -1;
	private static boolean domainSeen;
	private static float lastHealth = -1.0F;
	@Nullable
	private static Vec3d castFacing;
	private static int nextPunch;
	private static int kDeadAt = -1;
	@Nullable
	private static Vec3d fellAt;
	/** Where the friend crouches over Kortev's things: in front of his eyes, where his death screen looks. */
	@Nullable
	private static Vec3d over;
	private static final Random RANDOM = Random.create(47L);

	public static boolean active() {
		return human != null;
	}

	@Override
	public void onInitializeClient() {
		if (!"film-domain-kortev".equals(System.getProperty("shootingstar.selftest", ""))) {
			return;
		}
		human = new FilmHuman(2077L);
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(40 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[film] timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-film-domain-kortev-watchdog");
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
		ClientTickEvents.END_CLIENT_TICK.register(FilmDomainKortev::clientTick);
	}

	// =============================================================================================
	// Client: Kortev
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
				client.getToastManager().clear();
				if (!ready) {
					ticks = 0;
				} else if (ticks >= FilmDomain.SETTLE) {
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
					FilmDomain.writeLoops(client);
					stage = Stage.DONE;
				}
			}
			case DONE -> {
				ShootingStar.LOGGER.info("[film] done");
				stage = Stage.FINISHED;
				if (server != null) {
					server.execute(() -> {
						ServerPlayerEntity friend = server.getPlayerManager().getPlayer(FRIEND);
						if (friend != null) {
							server.getPlayerManager().remove(friend);
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
				}, "shootingstar-film-domain-kortev-exit");
				out.setDaemon(true);
				out.start();
				client.scheduleStop();
			}
			case FINISHED -> {
			}
		}
	}

	/** Each frame, before it is drawn: Kortev's look, between ticks, while it is his to move. */
	public static void frame(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (looking && human != null && client.player != null && client.currentScreen == null) {
			human.frame(client.player, tickDelta);
		}
	}

	/** What Kortev does this tick, through {@link FilmHuman}: where he looks and goes, what he clicks. */
	private static void act(MinecraftClient client) {
		ClientPlayerEntity me = client.player;
		FilmHuman h = human;
		if (me == null || h == null || s < 0 || spot == null) {
			return;
		}
		int now = ++clientTick;
		looking = false;
		ClientDomain domain = null;
		for (ClientDomain d : ClientDomains.all()) {
			domain = d;
		}
		if (domain != null) {
			age = domain.age;
			ageAt = now;
		} else if (ageAt >= 0) {
			age = age + (now - ageAt);
			ageAt = now;
		}
		FilmDomain.loops(client, domain);
		stills(client, now);
		if (deadAt < 0 && (me.isDead() || me.getHealth() <= 0.0F)) {
			deadAt = now;
			ShootingStar.LOGGER.info("[film] Kortev falls at {} (age {})", now, age);
		}
		if (deadAt >= 0) {
			// His death screen, the friend crouching over his things behind it.
			FilmHuman.release(client);
			if (now - deadAt >= FilmDomain.AFTER) {
				finished = true;
			}
			return;
		}
		if (ClientDomains.locked()) {
			// Held, or on his knees: he can do nothing.
			FilmHuman.release(client);
			return;
		}
		h.at(now);
		kortev(client, me, h, now, friend(client));
		h.tick(client, me);
		looking = true;
	}

	private static void kortev(MinecraftClient client, ClientPlayerEntity me, FilmHuman h, int now, @Nullable AbstractClientPlayerEntity friend) {
		Vec3d home = Vec3d.ofBottomCenter(spot);
		Vec3d fEyes = friend != null ? friend.getEyePos() : home.add(0.0, 1.62, 0.0);
		Vec3d fFeet = friend != null ? friend.getPos() : home;
		double d = Math.hypot(fFeet.x - me.getX(), fFeet.z - me.getZ());
		if (age < 0) {
			// The fight: in at a sprint, two hits, and keeping at a sword's reach, circling, as he backs off.
			h.look(fEyes).track().slack(d < 3.6 ? 0.4 : 1.0);
			int[] hits = FilmDomain.HITS;
			boolean swinging = hit < hits.length && now >= hits[hit] && now < FilmDomain.CAST - 8;
			double keep = now > FilmDomain.WHIFF ? KEEP_OFF : KEEP_IN;
			if (swinging) {
				// In for the blow.
				h.back(false).go(fFeet, FilmHuman.Pace.SPRINT, 2.2, false);
			} else if (d > keep + 1.4) {
				h.back(false).go(fFeet, FilmHuman.Pace.SPRINT, keep, d > 7.0);
			} else if (d < keep - 0.3) {
				h.stay().back(true);
			} else {
				h.stay().back(false).strafe((now / 26) % 2 == 0 ? 1 : -1);
			}
			// A swing that lands: he is in reach, facing him, his sword ready (the game's own attack, as a click on him).
			if (swinging && friend != null && me.distanceTo(friend) < REACH
					&& FilmHuman.off(me, fEyes.subtract(0.0, 0.5, 0.0)) < 14.0 && me.getAttackCooldownProgress(0.0F) >= 0.9F) {
				ShootingStar.LOGGER.info("[film] Kortev hits the friend at {}", now);
				client.interactionManager.attackEntity(me, friend);
				me.swingHand(Hand.MAIN_HAND);
				hit++;
			}
			if (now == FilmDomain.WHIFFS[0] || now == FilmDomain.WHIFFS[1]) {
				// A swing at the air as the friend backs off.
				FilmHuman.leftClick(client);
			}
			return;
		}
		if (age < DomainTimeline.CLOSE) {
			// The eyes: he stares, then backs away from them, a hop back now and then, too late.
			h.stay().look(fEyes).track().slack(1.0);
			h.back(age >= FilmDomain.BACK_AWAY);
			if (age > 40 && age % 14 == 0) {
				h.jump();
			}
			return;
		}
		if (age < DomainTimeline.collapse(FilmDomain.HOLD)) {
			// Caught (the game has him held: a tick may pass before his client is told).
			h.stay().back(false);
			return;
		}
		ClientDomain last = null;
		for (ClientDomain d2 : ClientDomains.all()) {
			last = d2;
		}
		if (freeAt < 0 && last != null && last.falling.contains(me.getId())) {
			// Let go of in the air (caught mid-hop): he drops, and goes down onto his knees as he lands.
			h.stay().back(false);
			return;
		}
		// Let go of, and come to: he staggers off, head down; turns and sees the friend coming, and goes for an apple.
		if (freeAt < 0) {
			freeAt = now;
			ShootingStar.LOGGER.info("[film] Kortev comes to at {} (age {})", now, age);
		}
		int t = now - freeAt;
		Vec3d away = me.getPos().subtract(fFeet).multiply(1.0, 0.0, 1.0);
		away = away.lengthSquared() > 1.0E-4 ? away.normalize() : FilmDomain.direction(FilmDomain.FACING);
		h.back(false);
		if (t < STAGGER) {
			h.go(me.getPos().add(away.multiply(3.0)), FilmHuman.Pace.WALK, 0.2, false);
			h.look(me.getEyePos().add(away.multiply(5.0)).add(0.0, -2.0, 0.0)).slack(4.0);
			return;
		}
		h.stay().look(fEyes).track().slack(2.0);
		if (t == APPLE_IN_HAND) {
			me.getInventory().selectedSlot = 1;
		}
		if (t >= EAT) {
			h.use();
		}
	}

	@Nullable
	private static AbstractClientPlayerEntity friend(MinecraftClient client) {
		if (client.world == null) {
			return null;
		}
		for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
			if (other.getGameProfile().getName().equals(FRIEND)) {
				return other;
			}
		}
		return null;
	}

	private static void stills(MinecraftClient client, int now) {
		for (int at : new int[] {0, 15, 30, 45, 60, 75}) {
			if (now >= at && age < 0) {
				shot(client, String.format(Locale.ROOT, "s%03d", at));
			}
		}
		if (age >= 0 && deadAt < 0) {
			for (int at : new int[] {4, 16, 30, 42, 70, 86, 92, 104, 124, 141, 150, 181, 230, 262, 290, 302, 305, 308, 312, 320, 330, 340,
					350}) {
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
		String file = "domain-kortev_" + name + ".png";
		if (!SHOT.add(file)) {
			return;
		}
		ScreenshotRecorder.saveScreenshot(client.runDirectory, file, client.getFramebuffer(), message -> {
		});
	}

	// =============================================================================================
	// Server: the friend
	// =============================================================================================

	private static void setUp(MinecraftServer server) {
		ServerPlayerEntity kortev = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = kortev.getServerWorld();
		spot = FilmDomain.scene(server, world, FRIEND);
		Vec3d at = Vec3d.ofBottomCenter(spot);

		ServerPlayerEntity friend = FilmDomain.join(server, world, FRIEND);
		FilmDomain.dress(friend, false, false);
		friend.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
		FEET.place(world, friend, at, FilmDomain.FACING);
		lastHealth = friend.getHealth();
		// His name is never seen.
		Scoreboard board = server.getScoreboard();
		Team team = board.getTeam("film_unnamed");
		if (team == null) {
			team = board.addTeam("film_unnamed");
		}
		team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
		board.addScoreHolderToTeam(FRIEND, team);

		FilmDomain.dress(kortev, true, true);
		// A little hungry, as after a fight: a player's own body heals with a full belly, and the film's other Kortev (whom
		// the game never ticks) does not, so neither does this one.
		kortev.getHungerManager().setFoodLevel(17);
		kortev.getHungerManager().setSaturationLevel(0.0F);
		Vec3d from = FilmDomain.kortevStart(at);
		kortev.networkHandler.requestTeleport(from.x, FilmDomain.ground(world, from.x, from.z).getY(), from.z, FilmDomain.FACING + 180.0F, 0.0F);
		ready = true;
		ShootingStar.LOGGER.info("[film] set up at {} (ground along the way: {})", spot,
				FilmDomain.profile(world, at, FilmDomain.direction(FilmDomain.FACING)));
	}

	private static void serverTick(MinecraftServer server) {
		ServerPlayerEntity kortev = server.getPlayerManager().getPlayer(FilmDomain.KORTEV);
		ServerPlayerEntity friend = server.getPlayerManager().getPlayer(FRIEND);
		if (friend == null || kortev == null || FEET.pos == null) {
			return;
		}
		ServerWorld world = friend.getServerWorld();
		DomainManager.Domain domain = DomainManager.active().isEmpty() ? null : DomainManager.active().get(0);
		if (domain != null) {
			domainSeen = true;
			serverAge = domain.age();
		} else if (domainSeen) {
			serverAge++;
		}
		int now = s;
		Vec3d kFeet = kortev.getPos();
		Vec3d kEyes = kortev.getEyePos();
		// Hit (Kortev's sword): knocked back a little, as the game knocks a player back.
		if (friend.getHealth() < lastHealth - 0.01F) {
			Vec3d away = FEET.pos.subtract(kFeet).multiply(1.0, 0.0, 1.0);
			FEET.knock = away.lengthSquared() > 1.0E-4 ? away.normalize().multiply(0.42) : Vec3d.ZERO;
			FEET.jumpV = Math.max(FEET.jumpV, 0.34);
		}
		lastHealth = friend.getHealth();
		Vec3d toK = kFeet.subtract(FEET.pos).multiply(1.0, 0.0, 1.0);
		double dist = toK.length();
		Vec3d dir = dist > 1.0E-4 ? toK.multiply(1.0 / dist) : FilmDomain.direction(FEET.yaw);
		Vec3d side = (now / 26) % 2 == 0 ? new Vec3d(-dir.z, 0.0, dir.x) : new Vec3d(dir.z, 0.0, -dir.x);
		Vec3d want = Vec3d.ZERO;
		Vec3d look = kEyes;

		if (!kortev.isAlive() || kortev.getHealth() <= 0.0F) {
			// Kortev's things all over the grass, and him crouching over them.
			if (kDeadAt < 0) {
				kDeadAt = now;
				fellAt = kFeet;
				Vec3d facing = Vec3d.fromPolar(0.0F, kortev.getYaw()).multiply(1.0, 0.0, 1.0).normalize();
				over = fellAt.add(facing.multiply(2.6));
				ShootingStar.LOGGER.info("[film] Kortev is dead at {} (age {})", now, serverAge);
			}
			int t = now - kDeadAt;
			Vec3d toOver = over.subtract(FEET.pos).multiply(1.0, 0.0, 1.0);
			if (t > 6 && toOver.length() > 0.25) {
				want = toOver.normalize().multiply(Math.min(0.1, toOver.length()));
			}
			look = fellAt;
			boolean down = t >= 14 && t < 14 + FilmDomain.TEABAG && (t / 2) % 2 == 0;
			friend.setSneaking(down);
			friend.setPose(down ? EntityPose.CROUCHING : EntityPose.STANDING);
		} else if (serverAge < 0) {
			if (now >= FilmDomain.NOTICE && dist < 2.9 && now < FilmDomain.CAST - 6) {
				want = dir.multiply(-0.1);
			}
			if (now >= FilmDomain.SIDESTEP && now < FilmDomain.SIDESTEP + 10) {
				want = want.add(side.multiply(0.12));
			}
			if (now == FilmDomain.CAST) {
				castFacing = dir;
				ShootingStar.LOGGER.info("[film] the friend casts at {}: {}", now, DomainManager.cast(friend));
			}
		} else if (serverAge < FilmDomain.LOOK_UP) {
			// The sign, his eyes on Kortev; in the black, still.
		} else if (serverAge < FilmDomain.WALK_OUT) {
			look = serverAge < 126 ? eye(friend) : kEyes;
		} else if (serverAge < FilmDomain.STEP_BACK) {
			// Out to him, face to face under the eye, a step round him and back, looking him over.
			boolean circling = serverAge >= FilmDomain.CIRCLE_FROM && serverAge < FilmDomain.CIRCLE_TO;
			if (circling) {
				want = (serverAge < (FilmDomain.CIRCLE_FROM + FilmDomain.CIRCLE_TO) / 2 ? side : side.multiply(-1.0)).multiply(0.07);
			} else if (dist > 3.2) {
				want = dir.multiply(0.1);
			}
			look = serverAge >= 250 && serverAge < 260 ? kFeet.add(0.0, 0.5, 0.0) : kEyes;
		} else if (serverAge < DomainTimeline.collapse(FilmDomain.HOLD)) {
			if (serverAge < 276) {
				want = dir.multiply(-0.08);
			}
			look = serverAge >= FilmDomain.EYE_AGAIN ? eye(friend) : kEyes;
		} else if (serverAge >= AFTER_HIM) {
			// It fades; Kortev is let go. Then after him, and one punch.
			if (dist > PUNCH_REACH) {
				want = dir.multiply(0.11);
			} else if (now >= nextPunch) {
				punch(world, friend, kortev);
				nextPunch = now + PUNCH_EVERY;
			}
		}
		FEET.move(world, friend, want, look, now);
	}

	/** The great eye, as he sees it: the way he faced as he cast it, up a little ({@code EYE_RISE} in DomainRender). */
	private static Vec3d eye(ServerPlayerEntity friend) {
		Vec3d f = castFacing != null ? castFacing : FilmDomain.direction(FilmDomain.FACING);
		return friend.getEyePos().add(new Vec3d(f.x, 0.28, f.z).normalize().multiply(200.0));
	}

	/** The friend's fist landing on Kortev: the swing, the blow (the game knocks him back), and the sound of it. */
	private static void punch(ServerWorld world, ServerPlayerEntity friend, ServerPlayerEntity kortev) {
		friend.swingHand(Hand.MAIN_HAND, true);
		world.playSound(null, kortev.getX(), kortev.getY(), kortev.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F,
				1.0F);
		kortev.damage(world.getDamageSources().playerAttack(friend), 2.0F);
		if (kortev.isAlive()) {
			// One punch, as kortev wants it: through his iron a fist leaves him a sliver of his half a heart.
			kortev.damage(world.getDamageSources().playerAttack(friend), Float.MAX_VALUE);
		}
		ShootingStar.LOGGER.info("[film] the friend punches Kortev at age {}, who is at {}", serverAge, kortev.getHealth());
	}

	/**
	 * The feet of a player the server makes and moves itself (as {@code FilmDomain.move} moves Kortev): speeding up and
	 * slowing down as a player does, along the ground, a knock fading away; the head turning towards what it looks at as
	 * fast as a hand would turn it; the steps heard.
	 */
	static final class Walker {
		@Nullable
		Vec3d pos;
		Vec3d vel = Vec3d.ZERO;
		Vec3d knock = Vec3d.ZERO;
		float yaw;
		float pitch;
		double jumpY;
		double jumpV;
		double stepped;

		void place(ServerWorld world, ServerPlayerEntity player, Vec3d at, float yaw) {
			pos = new Vec3d(at.x, FilmDomain.ground(world, at.x, at.z).getY(), at.z);
			this.yaw = yaw;
			player.teleport(world, pos.x, pos.y, pos.z, yaw, 0.0F);
			player.setHeadYaw(yaw);
			player.setBodyYaw(yaw);
		}

		void move(ServerWorld world, ServerPlayerEntity player, Vec3d want, Vec3d look, int now) {
			vel = vel.add(want.subtract(vel).multiply(0.35));
			Vec3d step = vel.add(knock);
			knock = knock.multiply(0.55);
			double x = pos.x + step.x;
			double z = pos.z + step.z;
			double floor = FilmDomain.ground(world, x, z).getY();
			if (jumpV != 0.0 || jumpY > 0.0) {
				jumpY += jumpV;
				jumpV = (jumpV - 0.08) * 0.98;
				if (jumpY <= 0.0) {
					jumpY = 0.0;
					jumpV = 0.0;
				}
			}
			pos = new Vec3d(x, floor + jumpY, z);
			double[] a = FilmHuman.angles(pos.add(0.0, player.getStandingEyeHeight(), 0.0), look);
			yaw += MathHelper.clamp(MathHelper.wrapDegrees((float) a[0] - yaw), -20.0F, 20.0F);
			pitch += MathHelper.clamp((float) a[1] - pitch, -12.0F, 12.0F);
			player.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, pitch);
			player.setHeadYaw(yaw);
			player.setOnGround(jumpY == 0.0);
			stepped += Math.hypot(step.x, step.z);
			if (jumpY == 0.0 && stepped > 1.65) {
				stepped = 0.0;
				BlockPos below = BlockPos.ofFloored(pos.x, pos.y - 0.2, pos.z);
				BlockSoundGroup group = world.getBlockState(below).getSoundGroup();
				world.playSound(null, pos.x, pos.y, pos.z, group.getStepSound(), SoundCategory.PLAYERS, group.getVolume() * 0.15F, group.getPitch());
			}
		}
	}
}
