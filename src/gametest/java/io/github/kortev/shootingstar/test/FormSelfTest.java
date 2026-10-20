package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.client.ClientCombat;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Frenzy;
import io.github.kortev.chainsawman.form.Grapple;
import io.github.kortev.chainsawman.form.Saw;
import io.github.kortev.chainsawman.network.FormEventPayload;
import io.github.kortev.shootingstar.test.mixin.FilmClickInvoker;
import io.github.kortev.chainsawman.form.FormTimeline;
import io.github.kortev.shootingstar.ShootingStar;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.client.ClientForms;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * With -Dshootingstar.selftest=form: joins the quick-play world, gives the player Pochita, pulls the cord, and
 * photographs the form (Chainsaw Man) from round about: from invisible armour stands set in a ring round them, each
 * looking at their head, the camera handed to each in turn. Screenshots land in build/selftest/screenshots.
 */
public class FormSelfTest implements ClientModInitializer {
	private enum Stage { WAIT_WORLD, SETUP, SETTLE, SHOOT, FIGHT, DONE }

	/** Where the cameras stand, as (angle round from the player's front in degrees, distance, height over their feet). */
	private static final double[][] CAMERAS = {{-30, 3.0, 1.9}, {0, 2.6, 1.7}, {90, 3.2, 1.7}, {-90, 3.2, 1.7}, {160, 3.0, 2.0},
			{-40, 1.4, 1.8}};
	/** The cameras that photograph the skin again with the form off. */
	private static final int[] PLAIN = {0, 4};
	private static final String[] NAMES = {"front_three_quarter", "front", "side_left", "side_right", "back", "close"};

	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static int shot;
	private static final List<Integer> CAMERA_IDS = new ArrayList<>();
	private static UUID standInId;
	/** The ticks after the pull to photograph, and how far it has got. */
	private static final int[] PULL_SHOTS = {2, 6, 10, 14, 16, 17, 18, 19, 20, 21, 22, 23, 25, 28, 32, 38, 44, 56};
	private static int pullTick;
	/** Your own transformation, filmed from your own camera: the ticks after the pull to photograph, and how far it has got. */
	private static final int[] CINE_SHOTS = {2, 6, 10, 14, 16, 17, 18, 19, 20, 21, 22, 23, 24, 26, 28, 30, 32, 33, 34, 36, 37, 38, 39, 42, 48, 56, 61, 63};
	private static final int CINE_START = 10;
	private static int cineTick;

	@Override
	public void onInitializeClient() {
		if (!"form".equals(System.getProperty("shootingstar.selftest"))) {
			return;
		}
		// -Ptarget=boxhead: the boxes proposed for the head, in place of the mesh's.
		io.github.kortev.chainsawman.client.FormHeadModel.on = "boxhead".equals(System.getProperty("shootingstar.selftest.target"));
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(30 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "chainsawman-form-selftest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ClientTickEvents.END_CLIENT_TICK.register(FormSelfTest::tick);
	}

	/** Whether this test is running (the stand-in wears Kortev's skin from build/selftest/skins, as in the films). */
	public static boolean active() {
		return "form".equals(System.getProperty("shootingstar.selftest"));
	}

	private static void tick(MinecraftClient client) {
		ticks++;
		IntegratedServer server = client.getServer();
		// Nothing over the view (a click on the window can open the inventory), and no pausing when it loses focus.
		client.options.pauseOnLostFocus = false;
		if (stage != Stage.WAIT_WORLD && stage != null && client.currentScreen != null) {
			client.setScreen(null);
		}
		switch (stage) {
			case WAIT_WORLD -> {
				if (client.player != null && !client.player.isAlive() && ticks % 20 == 0) {
					// The world may have been left with the player dead in it.
					client.player.requestRespawn();
				}
				if (client.world != null && client.player != null && client.player.isAlive() && server != null && ticks > 60) {
					stage = Stage.SETUP;
				}
			}
			case SETUP -> {
				client.options.hudHidden = true;
				server.execute(() -> setup(server));
				stage = Stage.SETTLE;
				ticks = 0;
			}
			case SETTLE -> {
				if (ticks > 80 && CAMERA_IDS.size() == CAMERAS.length && "merge".equals(System.getProperty("shootingstar.selftest.target"))) {
					// Only the merge (-Ptarget=merge), for working on it.
					stage = Stage.FIGHT;
					ticks = 249;
				} else if (ticks > 80 && CAMERA_IDS.size() == CAMERAS.length) {
					// The game never draws your own player while the camera is another entity: a stand-in of them, in the
					// form, where they stand.
					addStandIn(client);
					stage = Stage.SHOOT;
					ticks = 0;
				}
			}
			case SHOOT -> {
				if (shot >= CAMERAS.length && (io.github.kortev.chainsawman.client.FormHeadModel.on
						|| "ring".equals(System.getProperty("shootingstar.selftest.target")))) {
					// Only the form from round about (-Ptarget=ring; and the proposed head, -Ptarget=boxhead).
					stage = Stage.DONE;
					return;
				}
				if (shot >= CAMERAS.length + PLAIN.length && pullTick <= PULL_SHOTS[PULL_SHOTS.length - 1]) {
					// The stand-in pulls the cord, photographed through the yank and the burst.
					Entity camera = client.world.getEntityById(CAMERA_IDS.get(0));
					if (camera != null) {
						client.options.setPerspective(Perspective.FIRST_PERSON);
						client.setCameraEntity(camera);
					}
					if (pullTick == 0) {
						ClientForms.set(standInId, false);
						ClientForms.pulled(standInId, client.world.getTime(), 0.0F);
					}
					if (pullTick == FormTimeline.BURST) {
						ClientForms.set(standInId, true);
					}
					for (int at : PULL_SHOTS) {
						if (at == pullTick) {
							screenshot(client, String.format("pull_%02d", at));
						}
					}
					pullTick++;
					ticks = 0;
					return;
				}
				if (shot >= CAMERAS.length + PLAIN.length && cineTick <= CINE_START + CINE_SHOTS[CINE_SHOTS.length - 1] + 4) {
					// Your own transformation as you see it: back out of the form, then the cord pulled for real.
					client.setCameraEntity(client.player);
					client.options.setPerspective(Perspective.FIRST_PERSON);
					if (cineTick == 0) {
						// With the HUD on, as you play: the cinematic hides it and your hand itself, until the end (63).
						client.options.hudHidden = false;
						client.world.removeEntity(-4242, Entity.RemovalReason.DISCARDED);
						server.execute(() -> Form.pullCord(server.getPlayerManager().getPlayerList().get(0)));
					} else if (cineTick == CINE_START) {
						server.execute(() -> Form.pullCord(server.getPlayerManager().getPlayerList().get(0)));
					}
					for (int at : CINE_SHOTS) {
						if (cineTick == CINE_START + 1 + at) {
							screenshot(client, String.format("cine_%02d", at));
						}
					}
					cineTick++;
					ticks = 0;
					return;
				}
				if (shot >= CAMERAS.length + PLAIN.length) {
					client.setCameraEntity(client.player);
					if (ticks < 22) {
						client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
					}
					if (ticks == 20) {
						screenshot(client, "third_person_front");
					} else if (ticks == 22) {
						// Your own arms in front of you, empty-handed: their saws.
						client.options.setPerspective(Perspective.FIRST_PERSON);
						client.player.setPitch(25.0F);
						// Hiding the HUD hides your hand as well.
						client.options.hudHidden = false;
					} else if (ticks == 32) {
						screenshot(client, "first_person");
					} else if (ticks == 40) {
						stage = Stage.FIGHT;
						ticks = 0;
					}
					return;
				}
				// Last, a few of the same shots with the form off: the skin under it, as it is.
				boolean plain = shot >= CAMERAS.length;
				ClientForms.set(standInId, !plain);
				int which = plain ? PLAIN[shot - CAMERAS.length] : shot;
				Entity camera = client.world.getEntityById(CAMERA_IDS.get(which));
				if (camera != null) {
					client.options.setPerspective(Perspective.FIRST_PERSON);
					client.setCameraEntity(camera);
				}
				if (ticks == 12) {
					screenshot(client, (plain ? "plain_" : "") + NAMES[which]);
				} else if (ticks > 14) {
					shot++;
					ticks = 0;
				}
			}
			case FIGHT -> fight(client, server, ticks - 1);
			case DONE -> {
				ShootingStar.LOGGER.info("[selftest] form: done");
				stage = null;
				client.scheduleStop();
			}
			default -> {
			}
		}
	}

	/**
	 * The fight, frame by frame: first the stand-in, seen from the front, slashing through the combo, grinding, firing
	 * the chain, in a frenzy, and down (from the side and the front); then your own first-person view of the same,
	 * done for real on the server, with the HUD on.
	 */
	private static void fight(MinecraftClient client, IntegratedServer server, int f) {
		if (f == 0) {
			// Back where the cameras look (the cinematic took them away), in the form.
			if (client.world.getEntityById(-4242) == null) {
				addStandIn(client);
			}
		}
		UUID me = standInId;
		if (f == 0) {
			Entity camera = client.world.getEntityById(CAMERA_IDS.get(0));
			client.options.setPerspective(Perspective.FIRST_PERSON);
			client.options.hudHidden = true;
			if (camera != null) {
				client.setCameraEntity(camera);
			}
		}
		int[] slashAt = {0, 14, 28};
		for (int step = 0; step < 3; step++) {
			if (f == slashAt[step]) {
				ClientCombat.event(new FormEventPayload(me, FormEventPayload.SWING, step));
			}
			for (int age : new int[] {2, 4, 6, 8}) {
				if (f == slashAt[step] + age && (age < 8 || step == 2)) {
					screenshot(client, String.format("fight_slash%d_%02d", step, age));
				}
			}
		}
		Entity standIn = client.world.getEntityById(-4242);
		switch (f) {
			case 42 -> ClientCombat.event(new FormEventPayload(me, FormEventPayload.GRIND, 1));
			case 48 -> screenshot(client, "fight_grind");
			case 52 -> ClientCombat.event(new FormEventPayload(me, FormEventPayload.GRIND, 0));
			case 56 -> {
				if (standIn != null) {
					Vec3d to = standIn.getEyePos().add(1.5, 0.5, 10.0);
					ClientCombat.event(new FormEventPayload(me, FormEventPayload.CHAIN, -2, to.x, to.y, to.z));
				}
			}
			case 58 -> screenshot(client, "fight_chain_02");
			case 61 -> screenshot(client, "fight_chain_05");
			case 70 -> ClientCombat.event(new FormEventPayload(me, FormEventPayload.FRENZY, 300));
			case 74 -> screenshot(client, "fight_frenzy");
			case 80 -> {
				// Down, out of the form, as the server would have it.
				ClientCombat.event(new FormEventPayload(me, FormEventPayload.FRENZY, 0));
				ClientCombat.event(new FormEventPayload(me, FormEventPayload.DOWNED, 1));
				ClientForms.set(me, false);
				Entity side = client.world.getEntityById(CAMERA_IDS.get(2));
				if (side != null) {
					client.setCameraEntity(side);
				}
			}
			case 92 -> screenshot(client, "fight_downed_side");
			case 93 -> {
				Entity front = client.world.getEntityById(CAMERA_IDS.get(5));
				if (front != null) {
					client.setCameraEntity(front);
				}
			}
			case 96 -> screenshot(client, "fight_downed_close");
			case 97 -> {
				ClientCombat.event(new FormEventPayload(me, FormEventPayload.DOWNED, 0));
				// Out of the way of your own eyes.
				client.world.removeEntity(-4242, Entity.RemovalReason.DISCARDED);
			}
			// Your own view: the same, for real.
			case 100 -> {
				client.setCameraEntity(client.player);
				client.options.setPerspective(Perspective.FIRST_PERSON);
				client.options.hudHidden = false;
				client.player.setPitch(10.0F);
				click(client);
			}
			// Clicked and held as a player does, through the game's own handling of the attack key.
			case 114, 128 -> click(client);
			case 102, 116, 130 -> checkSwing(client, (f - 102) / 14);
			case 142 -> client.options.attackKey.setPressed(true);
			case 150 -> {
				screenshot(client, "fp_grind");
				if (!ClientCombat.grinding(client.player)) {
					ShootingStar.LOGGER.error("[selftest] form: FAILED: holding the attack key did not grind");
				}
			}
			case 152 -> client.options.attackKey.setPressed(false);
			case 154 -> client.player.setPitch(-25.0F);
			case 156 -> server.execute(() -> Grapple.fire(serverPlayer(server)));
			case 158 -> screenshot(client, "fp_chain_02");
			case 161 -> screenshot(client, "fp_chain_05");
			case 166 -> client.player.setPitch(10.0F);
			case 170 -> server.execute(() -> {
				// With the blood for it (the fight so far has spent most of it).
				io.github.kortev.chainsawman.form.Blood.set(serverPlayer(server), io.github.kortev.chainsawman.form.Blood.MAX);
				Frenzy.start(serverPlayer(server));
			});
			case 175 -> screenshot(client, "fp_frenzy");
			case 180 -> server.execute(() -> Frenzy.end(serverPlayer(server)));
			case 184 -> server.execute(() -> Downed.down(serverPlayer(server)));
			case 194 -> screenshot(client, "fp_downed");
			case 195 -> client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
			case 203 -> screenshot(client, "tp_downed");
			case 204 -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			case 208 -> screenshot(client, "tp_downed_back");
			case 209 -> {
				client.options.setPerspective(Perspective.FIRST_PERSON);
				server.execute(() -> Downed.up(serverPlayer(server)));
			}
			case 212 -> server.execute(() -> {
				// Pochita, tamed, sitting in front of the first camera.
				ServerPlayerEntity p = serverPlayer(server);
				io.github.kortev.chainsawman.PochitaEntity pochita = ChainsawMan.POCHITA_ENTITY.create(p.getServerWorld());
				if (pochita != null) {
					pochita.refreshPositionAndAngles(p.getX() + 0.4, p.getY(), p.getZ() - 0.2, -30.0F, 0.0F);
					pochita.setHeadYaw(-30.0F);
					pochita.setBodyYaw(-30.0F);
					pochita.setAiDisabled(true);
					p.getServerWorld().spawnEntity(pochita);
				}
				// A wolf beside him, to judge him against the game's own animals.
				net.minecraft.entity.passive.WolfEntity wolf = EntityType.WOLF.create(p.getServerWorld());
				if (wolf != null) {
					wolf.refreshPositionAndAngles(p.getX() - 1.1, p.getY(), p.getZ() - 0.2, -30.0F, 0.0F);
					wolf.setHeadYaw(-30.0F);
					wolf.setBodyYaw(-30.0F);
					wolf.setAiDisabled(true);
					p.getServerWorld().spawnEntity(wolf);
				}
			});
			case 218 -> {
				client.options.hudHidden = true;
				Entity camera = client.world.getEntityById(CAMERA_IDS.get(0));
				if (camera != null) {
					client.setCameraEntity(camera);
				}
			}
			case 226 -> screenshot(client, "pochita");
			case 228 -> camera(client, 2);
			case 232 -> screenshot(client, "pochita_side");
			case 234 -> camera(client, 5);
			case 238 -> screenshot(client, "pochita_close");
			case 240 -> camera(client, 4);
			case 244 -> screenshot(client, "pochita_back");
			// The merge, as the first time: out of the form, the heart not theirs yet, their tamed Pochita a few blocks off.
			case 250 -> {
				client.setCameraEntity(client.player);
				client.options.setPerspective(Perspective.FIRST_PERSON);
				client.options.hudHidden = false;
				server.execute(() -> {
					ServerPlayerEntity p = serverPlayer(server);
					Form.end(p);
					p.removeAttached(io.github.kortev.chainsawman.form.Heart.MERGED);
					for (Entity e : p.getServerWorld().getOtherEntities(p, p.getBoundingBox().expand(30.0),
							x -> x instanceof io.github.kortev.chainsawman.PochitaEntity || x instanceof net.minecraft.entity.passive.WolfEntity)) {
						e.discard();
					}
					io.github.kortev.chainsawman.PochitaEntity pochita = ChainsawMan.POCHITA_ENTITY.create(p.getServerWorld());
					if (pochita != null) {
						pochita.refreshPositionAndAngles(p.getX() + 4.0, p.getY(), p.getZ() + 2.0, 0.0F, 0.0F);
						pochita.setOwner(p);
						pochita.setTamed(true, true);
						p.getServerWorld().spawnEntity(pochita);
					}
				});
			}
			case 254 -> server.execute(() -> io.github.kortev.chainsawman.form.Merge.start(serverPlayer(server)));
			case 1180 -> stage = Stage.DONE;
			default -> {
			}
		}
		for (int age : new int[] {3, 30, 75, 100, 125, 150, 165, 182, 205, 230, 270, 300, 340, 380, 410, 460, 490, 512, 522, 526, 540,
				552, 566, 580, 592, 600, 607}) {
			if (f == 255 + age) {
				screenshot(client, String.format("merge_%03d", age));
			}
		}
		// On your feet, waiting to pull the cord; then pulled, as the key does.
		int end = 255 + io.github.kortev.chainsawman.form.MergeTimeline.END;
		if (f == end + 20) {
			screenshot(client, "merge_wait_020");
		} else if (f == end + 160) {
			screenshot(client, "merge_wait_160");
		} else if (f == end + 162) {
			server.execute(() -> Form.pullCord(serverPlayer(server)));
		} else if (f == end + 162 + 12) {
			screenshot(client, "merge_pulled_12");
		} else if (f == end + 162 + 30) {
			screenshot(client, "merge_then_burst");
		} else if (f == end + 250) {
			// Then turning back, seen from the front.
			client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
		} else if (f == end + 254) {
			server.execute(() -> Form.pullCord(serverPlayer(server)));
		} else if (f == end + 290) {
			client.options.setPerspective(Perspective.FIRST_PERSON);
		}
		for (int age : new int[] {3, 7, 11, 14, 16, 19, 24}) {
			if (f == end + 255 + age) {
				screenshot(client, String.format("back_%02d", age));
			}
		}
		int[] fpAt = {100, 114, 128};
		for (int step = 0; step < 3; step++) {
			for (int age : new int[] {2, 4, 6, 8}) {
				if (f == fpAt[step] + age && (age < 8 || step == 2)) {
					screenshot(client, String.format("fp_slash%d_%02d", step, age));
				}
			}
		}
	}

	/** A stand-in of the player, in the form, where they stand: the game never draws your own player while the camera is another entity. */
	private static void addStandIn(MinecraftClient client) {
		OtherClientPlayerEntity standIn = new OtherClientPlayerEntity(client.world, new GameProfile(UUID.randomUUID(), "Kortev"));
		standIn.setId(-4242);
		standIn.refreshPositionAndAngles(client.player.getX(), client.player.getY(), client.player.getZ(), 0.0F, 0.0F);
		standIn.setHeadYaw(0.0F);
		standIn.setBodyYaw(0.0F);
		showEveryLayer(standIn);
		client.world.addEntity(standIn);
		standInId = standIn.getUuid();
		ClientForms.set(standInId, true);
	}

	/** A left click, through the game's own handling of it. */
	private static void click(MinecraftClient client) {
		((FilmClickInvoker) client).shootingstar$leftClick();
	}

	/** That the click before came back from the server as the combo's {@code step}. */
	private static void checkSwing(MinecraftClient client, int step) {
		ClientCombat.Swing swing = ClientCombat.swing(client.player);
		if (swing == null || swing.step() != step || ClientCombat.swingAge(client.player, 0.0F) < 0.0F) {
			ShootingStar.LOGGER.error("[selftest] form: FAILED: click {} did not slash (swing {})", step, swing);
		} else {
			ShootingStar.LOGGER.info("[selftest] form: click {} slashed", step);
		}
	}

	private static void camera(MinecraftClient client, int which) {
		Entity camera = client.world.getEntityById(CAMERA_IDS.get(which));
		if (camera != null) {
			client.setCameraEntity(camera);
		}
	}

	private static ServerPlayerEntity serverPlayer(IntegratedServer server) {
		return server.getPlayerManager().getPlayerList().get(0);
	}

	/**
	 * A player's outer layers (hat, jacket, sleeves, trouser legs) are drawn only as far as their skin settings say,
	 * which a stand-in never sends: all of them on.
	 */
	@SuppressWarnings("unchecked")
	private static void showEveryLayer(PlayerEntity player) {
		try {
			Field parts = PlayerEntity.class.getDeclaredField("PLAYER_MODEL_PARTS");
			parts.setAccessible(true);
			player.getDataTracker().set((TrackedData<Byte>) parts.get(null), (byte) 0x7F);
		} catch (ReflectiveOperationException e) {
			ShootingStar.LOGGER.warn("[selftest] could not show the stand-in's outer layers", e);
		}
	}

	private static void screenshot(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot form_{}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "form_" + name + ".png", client.getFramebuffer(), message -> {
		});
	}

	/** On the server: the player stood still at noon on open ground, given Pochita and turned; the cameras set round them. */
	private static void setup(IntegratedServer server) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = server.getOverworld();
		world.setTimeOfDay(6000);
		world.setWeather(12000, 0, false, false);
		server.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		player.changeGameMode(GameMode.CREATIVE);
		player.setHealth(player.getMaxHealth());
		// A platform of stone high in open air (the same height every run), so nothing stands between the cameras and the player.
		BlockPos ground = player.getBlockPos().withY(220);
		for (BlockPos p : BlockPos.iterate(ground.add(-8, -1, -8), ground.add(8, 6, 8))) {
			world.setBlockState(p, p.getY() == ground.getY() - 1 ? Blocks.SMOOTH_STONE.getDefaultState() : Blocks.AIR.getDefaultState());
		}
		Vec3d feet = Vec3d.ofBottomCenter(ground);
		player.teleport(world, feet.x, feet.y, feet.z, 0.0F, 0.0F);
		player.getAbilities().flying = false;
		player.sendAbilitiesUpdate();
		player.getInventory().clear();
		// Carried, not held: the hand stays empty, so it is drawn in first person.
		player.getInventory().setStack(9, new ItemStack(ChainsawMan.POCHITA));
		// Pochita already their heart, as after the first time they died with him.
		player.setAttached(io.github.kortev.chainsawman.form.Heart.MERGED, true);
		Form.pullCord(player);
		Vec3d head = feet.add(0, 1.6, 0);
		for (double[] cam : CAMERAS) {
			// The player faces south (+z) at yaw 0; angles go round toward their left.
			double a = Math.toRadians(cam[0]);
			Vec3d at = feet.add(Math.sin(a) * cam[1], cam[2] - 1.7, Math.cos(a) * cam[1]);
			ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
			Vec3d eye = at.add(0, stand.getStandingEyeHeight(), 0);
			Vec3d d = head.subtract(eye);
			float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
			stand.refreshPositionAndAngles(at.x, at.y, at.z, yaw, MathHelper.clamp(pitch, -90.0F, 90.0F));
			stand.setHeadYaw(yaw);
			stand.setBodyYaw(yaw);
			stand.setNoGravity(true);
			stand.setInvisible(true);
			world.spawnEntity(stand);
			CAMERA_IDS.add(stand.getId());
		}
	}
}
