package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.Form;
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
	private enum Stage { WAIT_WORLD, SETUP, SETTLE, SHOOT, DONE }

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
	private static final int[] CINE_SHOTS = {2, 6, 10, 14, 16, 17, 18, 19, 20, 21, 22, 24, 28, 33, 36, 37, 38, 39, 42, 48, 56, 61};
	private static final int CINE_START = 10;
	private static int cineTick;

	@Override
	public void onInitializeClient() {
		if (!"form".equals(System.getProperty("shootingstar.selftest"))) {
			return;
		}
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
				if (ticks > 80 && CAMERA_IDS.size() == CAMERAS.length) {
					// The game never draws your own player while the camera is another entity: a stand-in of them, in the
					// form, where they stand.
					OtherClientPlayerEntity standIn = new OtherClientPlayerEntity(client.world, new GameProfile(UUID.randomUUID(), "Kortev"));
					standIn.setId(-4242);
					standIn.refreshPositionAndAngles(client.player.getX(), client.player.getY(), client.player.getZ(), 0.0F, 0.0F);
					standIn.setHeadYaw(0.0F);
					standIn.setBodyYaw(0.0F);
					showEveryLayer(standIn);
					client.world.addEntity(standIn);
					standInId = standIn.getUuid();
					ClientForms.set(standInId, true);
					stage = Stage.SHOOT;
					ticks = 0;
				}
			}
			case SHOOT -> {
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
						stage = Stage.DONE;
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
