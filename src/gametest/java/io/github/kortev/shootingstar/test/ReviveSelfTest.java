package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.client.ReviveCord;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.FormTimeline;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.Saw;
import io.github.kortev.shootingstar.ShootingStar;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;

/**
 * With -Dshootingstar.selftest=revive: the reel for a viewer's comment ("if you get killed in hybrid form other players can pull
 * your cord to revive you"): a friend pulling your cord brings you back as Chainsaw Man. A snowy taiga at dusk, snow falling.
 * Chainsaw Man fights a horde of husks and strays (their own AI; nothing but their spawn spots is staged) and is really
 * overwhelmed: down, his limbs gone. His friend, a second player on the server, sprints in through the mobs, which turn on him;
 * crouches; and holds use on him: the same {@code UseEntityCallback} a real crouch + use fires every four ticks, so it is the
 * mod's own pull (Downed, ReviveCord): the cord stretches out of the chest, the engine coughs, three seconds; the yank, the
 * limbs regrow, the mod's own transformation (its own camera), and he tears through the horde. Staged: spawn spots, timing,
 * health (low at first so the horde can down him; full once he is up, as the horde is round him while the transformation plays),
 * the friend's route (he is moved along it; he is a real player otherwise), the cameras.
 * <pre>./gradlew runSelftest -Pselftest=revive -Pusername=Kortev -Pcapture=true "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"</pre>
 */
public class ReviveSelfTest implements ClientModInitializer {
	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	private static final String TITLE = "CHAINSAW MAN MOD";
	private static final String[] HOOK = {"Your friend can pull your", "cord to bring you back"};
	private static final String ASK = "What should I add next?";

	/** How the clip runs, in ticks since its start unless said otherwise. */
	private static final int HURT_BY = 44;
	private static final int FRIEND_RUNS = 50;
	/** Where the friend starts (blocks from the middle), how far from the body he kneels, and how fast he sprints (blocks a tick). */
	private static final double FRIEND_FROM = 22.0;
	private static final double FRIEND_KNEELS = 0.55;
	private static final double SPRINT = 0.3;

	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static BlockPos centre;
	private static Vec3d middle = Vec3d.ZERO;
	private static final float FACING = -90.0F;
	private static volatile int friendId = -1;
	private static volatile ServerPlayerEntity friend;
	/** The friend's script on the server: 0 waiting, 1 running to the body, 2 kneeling and pulling. */
	private static volatile int friendMode;
	private static volatile int friendRanFor;
	private static volatile Vec3d friendFrom = Vec3d.ZERO;
	private static volatile Vec3d friendTo = Vec3d.ZERO;
	private static final List<Integer> MOBS = new java.util.concurrent.CopyOnWriteArrayList<>();

	/** The first wave's ids. */
	private static final java.util.Set<Integer> NEAR = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static volatile int yankedAtTick = -1;
	private static int downAt = -1;
	private static int slowAt = Integer.MAX_VALUE;
	private static int gripAt = -1;
	private static int yankAt = -1;
	private static int askAt = Integer.MAX_VALUE;
	private static int slashAt = -100;
	private static int shots;
	private static float wantYaw = Float.NaN;
	private static float wantPitch;
	private static float headYaw = Float.NaN;
	private static float headPitch;
	private static float lens;

	/** After the transformation, how long he fights before the end, and how long the question is up. */
	private static final int RAMPAGE = 112;
	private static final int ASKED_FOR = 42;

	public static boolean active() {
		return "revive".equals(System.getProperty("shootingstar.selftest"));
	}

	public static float lens() {
		return active() && stage == Stage.RUN ? lens : 0.0F;
	}

	/** -Ptarget=bare: no mobs at all (to look at the pull itself); the player is put down by the test. */
	private static boolean bare() {
		return "bare".equals(System.getProperty("shootingstar.selftest.target", ""));
	}

	private static boolean capturing() {
		return "true".equals(System.getProperty("shootingstar.selftest.capture"));
	}

	@Override
	public void onInitializeClient() {
		if (!active()) {
			return;
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(25 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] revive timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-revive-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		if (capturing()) {
			ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
			ServerTickEvents.END_SERVER_TICK.register(server -> Capture.serverTickEnd());
		}
		ServerTickEvents.END_SERVER_TICK.register(ReviveSelfTest::friendTick);
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity == friend) {
				ShootingStar.LOGGER.info("[selftest] revive: friend hurt {} by {} at tick {}", amount, source.getName(), ticks);
			}
			return true;
		});
		ClientTickEvents.END_CLIENT_TICK.register(ReviveSelfTest::tick);
		HudRenderCallback.EVENT.register((context, counter) -> {
			if (stage == Stage.RUN) {
				lettering(context, ticks + counter.getTickDelta(true));
			}
		});
	}

	private static void tick(MinecraftClient client) {
		TestWindow.keepToItself(client);
		ticks++;
		IntegratedServer server = client.getServer();
		client.options.pauseOnLostFocus = false;
		if (stage == null) {
			return;
		}
		if (stage != Stage.WAIT_WORLD && client.currentScreen != null) {
			client.setScreen(null);
		}
		switch (stage) {
			case WAIT_WORLD -> {
				if (client.world != null && client.player != null && client.player.isAlive() && server != null && ticks > 60) {
					stage = Stage.SETUP;
				}
			}
			case SETUP -> {
				server.execute(() -> setup(server));
				stage = Stage.SETTLE;
				ticks = 0;
			}
			case SETTLE -> {
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				if (centre != null && ticks > 400) {
					stage = Stage.RUN;
					ticks = 0;
				}
			}
			case RUN -> {
				client.getToastManager().clear();
				run(client, server, ticks);
				head(client);
			}
			case DONE -> {
				Capture.stop();
				ShootingStar.LOGGER.info("[selftest] revive: done");
				stage = null;
				client.scheduleStop();
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ the clip

	private static void run(MinecraftClient client, IntegratedServer server, int f) {
		if (client.player == null || client.world == null) {
			return;
		}
		Entity self = client.player;
		if (f == 1) {
			client.options.hudHidden = false;
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			headYaw = client.player.getYaw();
			headPitch = client.player.getPitch();
			server.execute(() -> firstWave(server));
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
		}
		boolean down = Downed.is(client.player);
		if (down && downAt < 0) {
			downAt = f;
			ShootingStar.LOGGER.info("[selftest] revive: down at f={}", f);
			slowAt = f + 14;
			// The strays have shot their last (their bows are empty-handed from here, so nothing is loosed at the friend as he comes).
			server.execute(() -> {
				for (net.minecraft.entity.mob.StrayEntity stray : server.getOverworld().getEntitiesByClass(net.minecraft.entity.mob.StrayEntity.class,
						new Box(centre).expand(40.0, 20.0, 40.0), m -> NEAR.contains(m.getId()))) {
					stray.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					stray.setTarget(null);
				}
			});
		}
		ReviveCord.Pull pull = ReviveCord.of(client.player);
		if (pull != null && pull.how() == ReviveCord.PULLING && gripAt < 0) {
			gripAt = f;
			ShootingStar.LOGGER.info("[selftest] revive: the friend has the cord at f={}", f);
		}
		if (pull != null && pull.how() == ReviveCord.YANKED && yankAt < 0) {
			yankAt = f;
			yankedAtTick = f;
			ShootingStar.LOGGER.info("[selftest] revive: yanked at f={}", f);
			// Up, with the horde round him: whole again for the transformation (the friend's blood is three hearts; the rest is staging).
			server.execute(() -> {
				for (net.minecraft.entity.mob.MobEntity mob : server.getOverworld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class,
						new Box(centre).expand(40.0, 20.0, 40.0), m -> true)) {
					mob.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
				}
				ServerPlayerEntity you = player(server);
				you.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20.0);
				you.setHealth(20.0F);
			});
		}
		if (yankAt >= 0 && f == yankAt + 14) {
			// The rest of the horde, from the edge of the clearing: it is on him as the transformation ends.
			server.execute(() -> horde(server, 9, 2, 7.0, 11.0, 11, 70.0));
		}
		if (f == slowAt) {
			server.execute(() -> slowHusks(server));
		}
		if (f == FRIEND_RUNS) {
			server.execute(() -> {
				friendMode = 1;
				friendRanFor = 0;
			});
		}
		// The fight before he goes down: low on health, so the horde can down him; if it has not by now, one blow away.
		if (bare() && downAt < 0 && f == 30) {
			server.execute(() -> Downed.down(player(server)));
		}
		if (downAt < 0 && f >= HURT_BY && !bare()) {
			// (Kept at that, whatever the saws drink: the next blow is the one.)
			server.execute(() -> {
				ServerPlayerEntity you = player(server);
				if (you.getHealth() > 1.5F && !Downed.is(you)) {
					you.setHealth(1.5F);
				}
			});
		}
		int sinceYank = yankAt < 0 ? -1 : f - yankAt;
		int rampage = yankAt + FormTimeline.END - FormTimeline.REVIVE_SKIP - 8;
		boolean fighting = downAt < 0 || yankAt >= 0 && f >= rampage;
		if (fighting) {
			fight(client, server, f);
		} else if (!down || pull == null) {
			wantYaw = Float.NaN;
		}
		if (yankAt >= 0 && f >= rampage + RAMPAGE - ASKED_FOR && askAt == Integer.MAX_VALUE) {
			askAt = f;
		}
		cameras(client, f, sinceYank, rampage);
		// Stills along the story (where each lands is by when the story's own moments came).
		if (f == 12) {
			still(client, "1_fight");
		} else if (downAt >= 0 && f == downAt + 8) {
			still(client, "2_down");
		} else if (downAt >= 0 && f == downAt + 38) {
			still(client, "3_friend_runs");
		} else if (gripAt >= 0 && f == gripAt + 10) {
			still(client, "4_grip");
		} else if (gripAt >= 0 && f == gripAt + 30) {
			still(client, "5_stretch");
		} else if (gripAt >= 0 && f == gripAt + 56) {
			still(client, "6_taut");
		} else if (yankAt >= 0 && f == yankAt + 3) {
			still(client, "7_yank");
		} else if (yankAt >= 0 && f == yankAt + 16) {
			still(client, "8_burst");
		} else if (yankAt >= 0 && f == yankAt + 40) {
			still(client, "9_roar");
		} else if (yankAt >= 0 && f == rampage + 30) {
			still(client, "a_rampage");
		} else if (yankAt >= 0 && f == rampage + RAMPAGE - 6) {
			still(client, "b_ask");
		}
		if (yankAt >= 0 && f >= rampage + RAMPAGE) {
			stage = Stage.DONE;
		}
		if (f > 900 && stage == Stage.RUN) {
			ShootingStar.LOGGER.error("[selftest] revive: gave up at f={} (down {}, grip {}, yank {})", f, downAt, gripAt, yankAt);
			stage = Stage.DONE;
		}
		if (f % 20 == 0) {
			ShootingStar.LOGGER.info("[selftest] revive f={} down {} grip {} yank {} downed {} form {} health {} mobs {}", f, downAt, gripAt, yankAt,
					Downed.is(client.player), io.github.kortev.chainsawman.client.ClientForms.on(client.player), client.player.getHealth(), mobsAlive(client));
		}
	}

	private static int mobsAlive(MinecraftClient client) {
		int n = 0;
		for (int id : MOBS) {
			Entity e = client.world.getEntityById(id);
			if (e != null && e.isAlive()) {
				n++;
			}
		}
		return n;
	}

	/** Your slashes (the server's own slash path, as the attack key's packet reaches it) at the nearest of the horde, your head turning to it. */
	private static void fight(MinecraftClient client, IntegratedServer server, int f) {
		Entity near = nearest(client, client.player.getPos(), 7.0, false);
		if (near == null) {
			return;
		}
		Vec3d to = near.getPos().add(0.0, 0.9, 0.0).subtract(client.player.getEyePos());
		face((float) Math.toDegrees(Math.atan2(-to.x, to.z)), (float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z))));
		int id = near.getId();
		if (f - slashAt >= Saw.SLASH_EVERY + 1 && client.player.distanceTo(near) < 3.4 && yawOff(client.player, near) < 35.0F) {
			slashAt = f;
			server.execute(() -> Saw.slash(player(server), id));
		}
	}

	private static float yawOff(Entity from, Entity to) {
		Vec3d d = to.getPos().subtract(from.getPos());
		return Math.abs(MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(-d.x, d.z)) - from.getYaw()));
	}

	private static Entity nearest(MinecraftClient client, Vec3d from, double within, boolean any) {
		Entity best = null;
		double bestD = within * within;
		for (int id : MOBS) {
			Entity e = client.world.getEntityById(id);
			if (e == null || !e.isAlive() || e instanceof LivingEntity l && l.getHealth() <= 0.0F) {
				continue;
			}
			double d = e.getPos().squaredDistanceTo(from);
			if (d < bestD) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ the cameras

	/** Which shot is installed, and the capture time it began at: a shot is installed once, and drifts on its own clock. */
	private static String shotKey = "";
	private static double shotT0;

	private static boolean installs(String key) {
		if (key.equals(shotKey)) {
			return false;
		}
		shotKey = key;
		shotT0 = Capture.time();
		steered = Double.NaN;
		return true;
	}

	/** The angle (radians, round the subject) the camera stands at now: the wanted one, or the nearest to it with no mob in the way, moved to a few degrees a frame. */
	private static double steered = Double.NaN;

	private static double steer(double want, Vec3d subject, double dist, double high, Vec3d aim) {
		MinecraftClient m = MinecraftClient.getInstance();
		double best = want;
		double bestScore = Double.MAX_VALUE;
		for (int k = 0; k < 48; k++) {
			double off = k * 7.5 - 180.0;
			double a = want + Math.toRadians(off);
			Vec3d at = subject.add(Math.cos(a) * dist, high, Math.sin(a) * dist);
			double score = blockage(m, at, aim) + 0.0015 * Math.abs(off);
			if (score < bestScore) {
				bestScore = score;
				best = a;
			}
		}
		if (Double.isNaN(steered)) {
			steered = best;
		}
		double step = Math.toRadians(7.0);
		steered += MathHelper.clamp(angleDiff(best, steered), -step, step);
		return steered;
	}

	private static double angleDiff(double a, double b) {
		double d = (a - b) % (2.0 * Math.PI);
		if (d > Math.PI) {
			d -= 2.0 * Math.PI;
		}
		if (d < -Math.PI) {
			d += 2.0 * Math.PI;
		}
		return d;
	}

	/** How much the mobs are in the camera's way at {@code at} looking at {@code aim}: each near the camera, or near the line of sight, adds to it. */
	private static double blockage(MinecraftClient m, Vec3d at, Vec3d aim) {
		double sum = 0.0;
		for (int id : MOBS) {
			Entity e = m.world.getEntityById(id);
			if (e == null || !e.isAlive()) {
				continue;
			}
			Vec3d c = e.getLerpedPos(td()).add(0.0, 0.9, 0.0);
			double near = Math.max(0.0, 2.3 - c.distanceTo(at));
			sum += near * near * 2.0;
			Vec3d ab = aim.subtract(at);
			double t = MathHelper.clamp(c.subtract(at).dotProduct(ab) / ab.lengthSquared(), 0.0, 1.0);
			if (t < 0.95) {
				double off = Math.max(0.0, 1.3 - c.distanceTo(at.add(ab.multiply(t))));
				sum += off * off * 2.0;
			}
		}
		return sum;
	}

	/** The camera for the frame: by where the story is. {@code since} ticks since the yank (-1 before), {@code rampage} when he fights again. */
	private static void cameras(MinecraftClient client, int f, int since, int rampage) {
		if (since >= 0 && f < rampage) {
			// The yank in my own shot (the cord snapping back, the blood), then the mod's own transformation camera.
			if (since < 9) {
				if (installs("c2")) {
					pull(4.4, 90.0, 1.5, 34.0F, 0.2);
				}
			} else if (installs("mod")) {
				Capture.camera = null;
				lens = 0.0F;
			}
			return;
		}
		Supplier<Vec3d> you = () -> client.player.getLerpedPos(td());
		if (since >= 0) {
			// E: he tears through the horde.
			int e = f - rampage;
			if (e < 28) {
				if (installs("e1")) {
					orbit(() -> you.get().add(0.0, 1.0, 0.0), 5.6, 25.0, 0.0, 1.8, 0.0, 60.0F);
				}
			} else if (e < 56) {
				if (installs("e2")) {
					orbit(() -> you.get().add(0.0, 1.2, 0.0), 5.6, 195.0, 0.0, 2.4, 0.0, 62.0F);
				}
			} else if (e < 80) {
				if (installs("e3")) {
					orbit(() -> you.get().add(0.0, 1.0, 0.0), 4.4, 100.0, 40.0, 1.2, 0.0, 58.0F);
				}
			} else if (installs("e4")) {
				orbit(() -> you.get().add(0.0, 1.1, 0.0), 4.8, 330.0, -14.0, 0.9, 0.0, 56.0F);
			}
			return;
		}
		if (gripAt >= 0) {
			// C: the pull: close on the grip and the cord leaving the chest, then the cord from the other side, then the strain.
			int c = f - gripAt;
			if (c < 14) {
				if (installs("c1")) {
					pull(4.6, 140.0, 1.6, 40.0F, 0.2);
				}
			} else if (installs("c2")) {
				// Low by his chest, side on, held through both tugs and the yank.
				pull(4.4, 90.0, 1.5, 34.0F, 0.2);
			}
			return;
		}
		if (downAt >= 0) {
			// B: the body, the horde turning on the friend running in.
			int d = f - downAt;
			if (d < 26) {
				if (installs("b1")) {
					orbit(() -> you.get().add(0.0, 0.25, 0.0), 4.4, 120.0, -24.0, 1.3, 0.0, 46.0F);
				}
			} else if (gripAt < 0 && d < 60) {
				if (installs("b2")) {
					follow(4.4, 1.2);
				}
			} else if (installs("b3")) {
				orbit(() -> bodyPos(client).lerp(friendPos(client), 0.4).add(0.0, 0.7, 0.0), 5.0, 250.0, 14.0, 1.0, 0.0, 55.0F);
			}
			return;
		}
		// A: Chainsaw Man in the horde.
		if (f < 26) {
			if (installs("a1")) {
				orbit(() -> you.get().add(0.0, 1.1, 0.0), 5.4, 35.0, 0.0, 0.7, 0.0, 58.0F);
			}
		} else if (f < 52) {
			if (installs("a2")) {
				orbit(() -> you.get().add(0.0, 1.0, 0.0), 4.4, 160.0, 24.0, 0.4, 0.0, 58.0F);
			}
		} else if (installs("a3")) {
			orbit(() -> you.get().add(0.0, 0.8, 0.0), 7.5, 290.0, -16.0, 4.8, 0.0, 0.0F);
		}
	}

	private static float td() {
		return MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
	}

	private static Vec3d flat(Vec3d v) {
		return new Vec3d(v.x, 0.0, v.z);
	}

	private static Vec3d bodyPos(MinecraftClient client) {
		return client.player.getLerpedPos(td());
	}

	private static Vec3d friendPos(MinecraftClient client) {
		Entity e = client.world.getEntityById(friendId);
		return e != null ? e.getLerpedPos(td()) : bodyPos(client).add(1.2, 0.0, 0.0);
	}

	/** Where the friend's hand is, from the same geometry the mod draws it by. */
	private static Vec3d handPos(MinecraftClient client) {
		Entity e = client.world.getEntityById(friendId);
		if (!(e instanceof net.minecraft.entity.player.PlayerEntity friend)) {
			return bodyPos(client).add(0.0, 0.5, 0.0);
		}
		ReviveCord.Pull pull = ReviveCord.of(client.player);
		if (pull == null) {
			return bodyPos(client).add(0.0, 0.5, 0.0);
		}
		return ReviveCord.grip(pull, friend, client.player, client.world.getTime(), td()).hand();
	}

	/** A camera {@code dist} out from the middle of the cord (the body and the friend's hand), {@code angle} degrees round the line between them, {@code high} above it, drifting round. */
	private static void pull(double dist, double angle, double high, float fov, double toward) {
		lens = fov;
		final double t0 = shotT0;
		Capture.camera = t -> {
			MinecraftClient m = MinecraftClient.getInstance();
			if (m.world == null || m.player == null) {
				return new Capture.Pose(0, 0, 0, 0, 0);
			}
			Vec3d body = bodyPos(m);
			Vec3d hand = handPos(m);
			Vec3d aim = body.add(0.0, 0.25, 0.0).lerp(hand, 0.5 + 0.4 * toward);
			Vec3d axis = flat(friendPos(m).subtract(body));
			double base = axis.lengthSquared() < 1.0E-4 ? 0.0 : Math.atan2(axis.z, axis.x);
			double a = steer(base + Math.toRadians(angle + 0.9 * (t - t0)), aim, dist, high, aim);
			Vec3d at = aim.add(Math.cos(a) * dist, high, Math.sin(a) * dist);
			Vec3d shake = io.github.kortev.chainsawman.client.FormCinematic.jitter(io.github.kortev.chainsawman.client.FormCinematic.shake(td()), t);
			return look(framed(at.add(shake), aim), aim);
		};
	}

	/**
	 * A camera {@code dist} from the point {@code subject} at {@code angle} degrees (from the way he faced at the start, round), turning
	 * {@code spin} degrees a second, {@code high} above it, looking at the point {@code lookUp} above it.
	 */
	private static void orbit(Supplier<Vec3d> subject, double dist, double angle, double spin, double high, double lookUp, float fov) {
		lens = fov;
		final double t0 = shotT0;
		Capture.camera = t -> {
			MinecraftClient m = MinecraftClient.getInstance();
			if (m.world == null || m.player == null) {
				return new Capture.Pose(0, 0, 0, 0, 0);
			}
			Vec3d s = subject.get();
			Vec3d aim = s.add(0.0, lookUp, 0.0);
			// (The angle is turned to the cos/sin convention: 0 degrees is the way he faces, east, round toward the north.)
			double a = steer(Math.toRadians(90.0 + FACING + angle + spin * (t - t0) / 20.0) , s, dist, high, aim);
			Vec3d at = s.add(Math.cos(a) * dist, high, Math.sin(a) * dist);
			return look(framed(at, aim), aim);
		};
	}

	/** Following the friend sprinting: from the front and low, so he runs at the lens through the mobs. */
	private static void follow(double dist, double high) {
		lens = 54.0F;
		Capture.camera = t -> {
			MinecraftClient m = MinecraftClient.getInstance();
			if (m.world == null || m.player == null) {
				return new Capture.Pose(0, 0, 0, 0, 0);
			}
			Vec3d f = friendPos(m);
			Vec3d body = bodyPos(m);
			Vec3d run = flat(body.subtract(f)).normalize();
			Vec3d side = new Vec3d(-run.z, 0.0, run.x);
			Vec3d at = f.add(run.multiply(dist)).add(side.multiply(2.2)).add(0.0, high - 0.2, 0.0);
			Vec3d aim = f.add(run.multiply(1.0)).add(0.0, 1.0, 0.0);
			return look(framed(at, aim), aim);
		};
	}

	private static void fixed(Vec3d at, Vec3d look) {
		Capture.Pose pose = look(at, look);
		Capture.camera = t -> pose;
	}

	private static Capture.Pose look(Vec3d at, Vec3d look) {
		Vec3d to = look.subtract(at);
		float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		return new Capture.Pose(at.x, at.y, at.z, yaw, pitch);
	}

	/** {@code at}: the clearing is open for fifteen blocks round (the snow lying in it is not in the way). */
	private static Vec3d framed(Vec3d at, Vec3d subject) {
		return at;
	}

	private static void still(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot revive_{}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "revive_" + name + ".png", client.getFramebuffer(), message -> {
		});
		shots++;
	}

	// ------------------------------------------------------------------ the head

	private static void face(float yaw, float pitch) {
		wantYaw = yaw;
		wantPitch = MathHelper.clamp(pitch, -40.0F, 40.0F);
	}

	/** Your head this tick, as a person's moves: easing toward where you are to look, with a little sway. */
	private static void head(MinecraftClient client) {
		if (client.player == null || Float.isNaN(wantYaw) || Downed.is(client.player) || pullingNow(client)) {
			return;
		}
		if (Float.isNaN(headYaw)) {
			headYaw = client.player.getYaw();
			headPitch = client.player.getPitch();
		}
		headYaw += MathHelper.clamp(MathHelper.wrapDegrees(wantYaw - headYaw) * 0.4F, -30.0F, 30.0F);
		headPitch += MathHelper.clamp((wantPitch - headPitch) * 0.4F, -30.0F, 30.0F);
		float t = ticks;
		float sway = 0.7F * MathHelper.sin(t * 0.061F) + 0.35F * MathHelper.sin(t * 0.173F + 1.3F);
		client.player.setYaw(headYaw + sway);
		client.player.setHeadYaw(headYaw + sway);
		client.player.setPitch(MathHelper.clamp(headPitch, -90.0F, 90.0F));
	}

	private static boolean pullingNow(MinecraftClient client) {
		return io.github.kortev.chainsawman.client.ClientForms.pullAge(client.player, 0.0F) >= 0.0F;
	}

	// ------------------------------------------------------------------ the lettering

	private static void lettering(DrawContext context, double t) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();
		outlined(context, font, TITLE, w / 2.0, h * 0.14, w * 0.78 / font.getWidth(TITLE), 0xFFFF8A1E);
		if (t < 46) {
			double scale = w * 0.86 / Math.max(font.getWidth(HOOK[0]), font.getWidth(HOOK[1]));
			outlined(context, font, HOOK[0], w / 2.0, h * 0.64, scale, 0xFFFFFFFF);
			outlined(context, font, HOOK[1], w / 2.0, h * 0.64 + scale * font.fontHeight * 1.3, scale, 0xFFFF8A1E);
		}
		if (askAt != Integer.MAX_VALUE && t >= askAt) {
			int cut = -1;
			for (int i = 0; i < ASK.length(); i++) {
				if (ASK.charAt(i) == ' ' && (cut < 0 || Math.abs(i - ASK.length() / 2) < Math.abs(cut - ASK.length() / 2))) {
					cut = i;
				}
			}
			String[] lines = {ASK.substring(0, cut), ASK.substring(cut + 1)};
			double k = MathHelper.clamp((t - askAt) / 4.0, 0.0, 1.0);
			k = k * k * (3.0 - 2.0 * k);
			int widest = Math.max(font.getWidth(lines[0]), font.getWidth(lines[1]));
			double scale = w * 0.84 / widest * (1.0 + 0.45 * (1.0 - k));
			for (int i = 0; i < lines.length; i++) {
				outlined(context, font, lines[i], w / 2.0, h * 0.64 + i * scale * font.fontHeight * 1.3, scale, 0xFFFFFFFF);
			}
		}
	}

	private static void outlined(DrawContext context, TextRenderer font, String text, double x, double y, double scale, int color) {
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0.0);
		context.getMatrices().scale((float) scale, (float) scale, 1.0F);
		int left = -font.getWidth(text) / 2;
		int top = -font.fontHeight / 2;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					context.drawText(font, text, left + dx, top + dy, 0xFF000000, false);
				}
			}
		}
		context.drawText(font, text, left, top, color, false);
		context.getMatrices().pop();
	}

	// ------------------------------------------------------------------ the server's side

	private static ServerPlayerEntity player(IntegratedServer server) {
		return server.getPlayerManager().getPlayerList().get(0);
	}

	/**
	 * The friend, every server tick: waiting off in the snow; running to the body (moved along his route, sprinting: he is a real
	 * player, only his feet are the test's); then kneeling (crouched, facing the body) holding use on it every four ticks, the
	 * call a real client's held use makes, until the pull is done.
	 */
	private static void friendTick(MinecraftServer server) {
		ServerPlayerEntity friend = ReviveSelfTest.friend;
		if (friend == null || server.getPlayerManager().getPlayerList().isEmpty() || stage == null) {
			return;
		}
		ServerPlayerEntity you = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = friend.getServerWorld();
		if (yankedAtTick < 0) {
			// The first wave (the strays that shot him down, the husks he left) has done its work: it does not turn on the friend while he pulls (their target is left empty).
			for (net.minecraft.entity.mob.MobEntity mob : world.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class,
					new Box(you.getBlockPos()).expand(48.0, 24.0, 48.0), m -> NEAR.contains(m.getId()) && m.getTarget() == friend)) {
				mob.setTarget(null);
			}
		}
		if (friendMode == 0) {
			friend.refreshPositionAndAngles(friendFrom.x, friendFrom.y, friendFrom.z, yawTo(friendFrom, middle), 0.0F);
			return;
		}
		if (friendMode == 1) {
			friendRanFor++;
			Vec3d to = kneel(you);
			double along = SPRINT * friendRanFor;
			Vec3d dir = to.subtract(friendFrom).multiply(1.0, 0.0, 1.0);
			double length = dir.length();
			Vec3d at = friendFrom.add(dir.normalize().multiply(Math.min(along, length)));
			double y = groundAt(world, at);
			float yaw = yawTo(at, to);
			friend.refreshPositionAndAngles(at.x, y, at.z, yaw, 8.0F);
			friend.setHeadYaw(yaw);
			friend.setBodyYaw(yaw);
			friend.setSprinting(along < length);
			friend.setVelocity(dir.normalize().multiply(along < length ? SPRINT : 0.0));
			if (along >= length) {
				friend.setSprinting(false);
				friendMode = 2;
				friendRanFor = 0;
			}
			return;
		}
		if (!Downed.is(you) && friendRanFor > 14) {
			// The pull is done: he steps back out of the way (the saws are about to swing).
			Vec3d out = friendFrom.subtract(you.getPos()).multiply(1.0, 0.0, 1.0).normalize();
			Vec3d at = friend.getPos().add(out.multiply(0.25));
			if (at.distanceTo(you.getPos()) < 7.0) {
				friend.refreshPositionAndAngles(at.x, groundAt(world, at), at.z, friend.getYaw(), 8.0F);
				friend.setSprinting(true);
				friendRanFor++;
				return;
			}
			friend.setSprinting(false);
			return;
		}
		// Kneeling.
		friendRanFor++;
		Vec3d at = kneel(you);
		float yaw = yawTo(at, you.getPos());
		friend.refreshPositionAndAngles(at.x, groundAt(world, at), at.z, yaw, 55.0F);
		friend.setHeadYaw(yaw);
		friend.setBodyYaw(yaw);
		friend.setSneaking(Downed.is(you) || friendRanFor < 4);
		if (Downed.is(you) && friendRanFor % 4 == 1 && friendRanFor > 3) {
			net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(friend, world, Hand.MAIN_HAND, you, null);
		}
		if (!Downed.is(you) && friendRanFor > 8) {
			friend.setSneaking(false);
		}
	}

	/** Where the friend kneels: {@link #FRIEND_KNEELS} from the body, on the side he came from. */
	private static Vec3d kneel(ServerPlayerEntity you) {
		Vec3d side = friendFrom.subtract(you.getPos()).multiply(1.0, 0.0, 1.0).normalize();
		// (Right over the chest, which is a little toward the head from where the body's place is.)
		return you.getPos().add(side.multiply(FRIEND_KNEELS));
	}

	private static float yawTo(Vec3d from, Vec3d to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	private static double groundAt(ServerWorld world, Vec3d at) {
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(at.x), MathHelper.floor(at.z));
	}

	/**
	 * A horde of husks and strays: their own AI, after whoever they find; spawned on a ring {@code from} to {@code to} blocks round the
	 * middle, the first {@code weak} of them with a little health (so a saw's cut kills them), the rest with their own.
	 */
	private static void horde(IntegratedServer server, int husks, int strays, double from, double to, int weak, double turn) {
		ServerWorld world = server.getOverworld();
		int count = bare() ? 0 : husks + strays;
		for (int i = 0; i < count; i++) {
			boolean stray = strays > 0 && (i + 1) * strays / count != i * strays / count;
			EntityType<? extends HostileEntity> type = stray ? EntityType.STRAY : EntityType.HUSK;
			double angle = Math.toRadians(turn + i * (360.0 / count));
			double r = from + (to - from) * ((i * 7) % 5) / 4.0;
			Vec3d at = middle.add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r);
			HostileEntity mob = type.create(world);
			if (mob == null) {
				continue;
			}
			double y = groundAt(world, at);
			mob.refreshPositionAndAngles(at.x, y, at.z, (float) Math.toDegrees(angle) + 90.0F, 0.0F);
			mob.setPersistent();
			if (!stray && i < weak) {
				mob.setHealth(8.0F);
			}
			if (type == EntityType.STRAY) {
				mob.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			}
			world.spawnEntity(mob);
			MOBS.add(mob.getId());
		}
	}

	/** One mob on the ring: {@code r} blocks from the middle at {@code deg} degrees round, facing the middle; {@code health} 0 for its own; a stray gets its bow. */
	private static void spawn(ServerWorld world, EntityType<? extends HostileEntity> type, double r, double deg, float health, int slow) {
		double angle = Math.toRadians(deg);
		Vec3d at = middle.add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r);
		HostileEntity mob = type.create(world);
		if (mob == null || bare()) {
			return;
		}
		mob.refreshPositionAndAngles(at.x, groundAt(world, at), at.z, (float) Math.toDegrees(angle) + 90.0F, 0.0F);
		mob.setPersistent();
		if (health > 0.0F) {
			mob.setHealth(health);
		}
		if (type == EntityType.STRAY) {
			mob.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
		if (slow > 0) {
			mob.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOWNESS, 20 * 60, slow - 1, true, false, false));
		}
		world.spawnEntity(mob);
		MOBS.add(mob.getId());
	}

	/** The first wave: husks close round him (a little health: a cut kills), strays far off on the side away from where the friend will come, shooting. */
	private static void firstWave(IntegratedServer server) {
		ServerWorld world = server.getOverworld();
		for (int i = 0; i < 4; i++) {
			spawn(world, EntityType.HUSK, 3.8 + (i % 2) * 1.4, 25.0 + i * 90.0, 3.0F, 0);
		}
		for (int i = 0; i < 6; i++) {
			spawn(world, EntityType.STRAY, 14.0 + (i % 2) * 0.8, 25.0 - 45.0 + i * 18.0, 0.0F, 0);
		}
		NEAR.addAll(MOBS);
	}

	/** Slow husks (slowness) closing in from the far side of the clearing: they walk at him, but are too slow to reach before the yank. */
	private static void slowHusks(IntegratedServer server) {
		ServerWorld world = server.getOverworld();
		for (int i = 0; i < 6; i++) {
			spawn(world, EntityType.HUSK, 15.5 + (i % 2) * 1.5, 110.0 + i * 36.0, 8.0F, 4);
		}
	}

	// ------------------------------------------------------------------ the set

	/**
	 * The set: a snowy taiga (the nearest the world has), at dusk, snow falling; a round clearing levelled and snowed, trees left
	 * round it; you there as Chainsaw Man (Pochita your heart, full blood), facing east; the friend off at the edge of the trees.
	 */
	private static void setup(IntegratedServer server) {
		ServerPlayerEntity you = player(server);
		ServerWorld world = server.getOverworld();
		world.setTimeOfDay(12350);
		world.setWeather(0, 24000, true, false);
		server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_LOOT).set(false, server);
		server.getGameRules().get(GameRules.DO_TILE_DROPS).set(false, server);
		server.setDifficulty(Difficulty.NORMAL, true);
		var found = world.locateBiome(b -> b.matchesKey(BiomeKeys.SNOWY_TAIGA), you.getBlockPos(), 6000, 48, 96);
		if (found == null) {
			found = world.locateBiome(b -> b.matchesKey(BiomeKeys.SNOWY_PLAINS) || b.matchesKey(BiomeKeys.GROVE), you.getBlockPos(), 6000, 48, 96);
		}
		ShootingStar.LOGGER.info("[selftest] revive: snowy ground at {}", found == null ? "nowhere" : found.getFirst());
		BlockPos near = found != null ? found.getFirst() : you.getBlockPos().add(200, 0, 0);
		world.getChunk(near.getX() >> 4, near.getZ() >> 4);
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, near);
		// The middle sits at the clearing's ground: the highest ground within a few blocks, so the rest is filled up to it.
		int base = top.getY();
		for (int dx = -3; dx <= 3; dx += 3) {
			for (int dz = -3; dz <= 3; dz += 3) {
				world.getChunk((near.getX() + dx) >> 4, (near.getZ() + dz) >> 4);
				base = Math.max(base, surface(world, near.getX() + dx, near.getZ() + dz) + 1);
			}
		}
		centre = new BlockPos(near.getX(), base, near.getZ());
		clearing(world, centre, 19);
		middle = Vec3d.ofBottomCenter(centre);
		for (Entity e : world.getOtherEntities(you, new Box(centre).expand(64.0, 40.0, 64.0))) {
			if (!(e instanceof ServerPlayerEntity)) {
				e.discard();
			}
		}
		if (Downed.is(you)) {
			Downed.up(you);
		}
		Form.end(you);
		you.changeGameMode(GameMode.SURVIVAL);
		you.getInventory().clear();
		you.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20.0);
		you.setHealth(8.0F);
		you.getHungerManager().setFoodLevel(20);
		you.setAttached(Heart.MERGED, true);
		Blood.set(you, Blood.MAX);
		you.teleport(world, middle.x, middle.y, middle.z, FACING, 0.0F);
		Form.start(you);
		// The friend, off in the snow behind the trees' edge.
		double angle = Math.toRadians(205.0);
		Vec3d far = middle.add(Math.cos(angle) * FRIEND_FROM, 0.0, Math.sin(angle) * FRIEND_FROM);
		world.getChunk(MathHelper.floor(far.x) >> 4, MathHelper.floor(far.z) >> 4);
		friendFrom = new Vec3d(far.x, groundAt(world, far), far.z);
		friend = makeFriend(server, world, friendFrom);
		friendId = friend.getId();
		ShootingStar.LOGGER.info("[selftest] revive: clearing at {}, friend {}", centre, friendFrom);
	}

	private static int surface(ServerWorld world, int x, int z) {
		int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		while (y > world.getBottomY()) {
			BlockState s = world.getBlockState(new BlockPos(x, y, z));
			if (s.isAir() || s.isIn(net.minecraft.registry.tag.BlockTags.LEAVES) || s.isIn(net.minecraft.registry.tag.BlockTags.LOGS)
					|| s.isReplaceable() && s.getFluidState().isEmpty() || s.isOf(Blocks.SNOW)) {
				y--;
			} else {
				break;
			}
		}
		return y;
	}

	/** A round clearing of {@code radius} levelled to {@code at}'s height: everything above taken away (trees too), the dips filled, snow on top. */
	private static void clearing(ServerWorld world, BlockPos at, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double d = Math.hypot(dx, dz);
				if (d > radius) {
					continue;
				}
				int x = at.getX() + dx;
				int z = at.getZ() + dz;
				world.getChunk(x >> 4, z >> 4);
				for (int y = at.getY() + 26; y >= at.getY(); y--) {
					BlockPos p = new BlockPos(x, y, z);
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
					}
				}
				for (int y = at.getY() - 1; y >= at.getY() - 6; y--) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState state = world.getBlockState(p);
					if (y == at.getY() - 1) {
						world.setBlockState(p, Blocks.SNOW_BLOCK.getDefaultState(), 2);
					} else if (state.isAir() || state.isReplaceable() || !state.isSolid() || !state.getFluidState().isEmpty()) {
						world.setBlockState(p, Blocks.DIRT.getDefaultState(), 2);
					} else {
						break;
					}
				}
			}
		}
	}

	private static ServerPlayerEntity makeFriend(MinecraftServer server, ServerWorld world, Vec3d at) {
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), "Yakuza1"), false);
		ServerPlayerEntity p = new ServerPlayerEntity(server, world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerManager().onPlayerConnect(connection, p, data);
		p.changeGameMode(GameMode.SURVIVAL);
		p.teleport(world, at.x, at.y, at.z, yawTo(at, middle), 0.0F);
		p.onTeleportationDone();
		p.setNoGravity(true);
		p.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000.0);
		p.setHealth(1000.0F);
		return p;
	}
}
