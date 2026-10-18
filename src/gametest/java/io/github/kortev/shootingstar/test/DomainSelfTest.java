package io.github.kortev.shootingstar.test;

import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.GojoRules;
import io.github.kortev.gojolander.Owner;
import io.github.kortev.gojolander.client.ClientDomain;
import io.github.kortev.gojolander.client.ClientDomains;
import io.github.kortev.gojolander.domain.DomainManager;
import io.github.kortev.gojolander.domain.DomainTimeline;
import io.github.kortev.gojolander.network.DomainCastPayload;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayDeque;
import java.util.Deque;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

/**
 * With -Dshootingstar.selftest=domain: joins the quick-play world as the blindfold's owner, on a cleared disc of grass
 * with creatures stood round, and photographs Unlimited Void three ways: cast by this player (the blindfold, the hand
 * sign shot, the black closing, the void, the shattering); cast by nobody a way off, watched from outside it (the ring,
 * the sphere, its shattering); and cast over this player, caught in it (the flood). The pictures are in the run
 * directory's screenshots folder.
 */
public class DomainSelfTest implements ClientModInitializer {
	private enum Stage { WAIT_WORLD, SETUP, SETTLE, CAST, WATCH, OUTSIDE, CAUGHT, DONE, FINISHED }

	/** How far the disc reaches, how far off the watched Domain is, and where the creatures stand round the caster. */
	private static final int DISC = 40;
	private static final int AWAY = 34;
	private static final int RADIUS = 16;
	private static final int HOLD_SECONDS = 5;
	private static final int HOLD = HOLD_SECONDS * 20;

	private static final Deque<Integer> STILLS = new ArrayDeque<>();
	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static BlockPos home;
	private static float facing;

	@Override
	public void onInitializeClient() {
		if (!"domain".equals(System.getProperty("shootingstar.selftest"))) {
			return;
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(15 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "gojolander-domain-selftest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ClientTickEvents.END_CLIENT_TICK.register(DomainSelfTest::tick);
	}

	private static void shot(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot {}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {
		});
	}

	private static void stills(int... ages) {
		STILLS.clear();
		for (int age : ages) {
			STILLS.add(age);
		}
	}

	/** Photographs every still the Domain's age has reached, named {@code prefix} and the age. */
	private static void photograph(MinecraftClient client, ClientDomain domain, String prefix) {
		while (domain != null && !STILLS.isEmpty() && domain.age >= STILLS.peek()) {
			int age = STILLS.poll();
			shot(client, String.format("%s_age%03d_%s.png", prefix, age, phase(domain, age)));
		}
	}

	private static String phase(ClientDomain domain, int age) {
		if (age < DomainTimeline.RISE) return "sign";
		if (age < DomainTimeline.CLOSE) return "rise";
		if (age < DomainTimeline.VOID) return "close";
		if (age < domain.collapse()) return "void";
		return "shatter";
	}

	private static void tick(MinecraftClient client) {
		ticks++;
		IntegratedServer server = client.getServer();
		switch (stage) {
			case WAIT_WORLD -> {
				if (client.world != null && client.player != null && server != null) {
					ShootingStar.LOGGER.info("[selftest] joined world");
					stage = Stage.SETUP;
					ticks = 0;
				}
			}
			case SETUP -> {
				if (ticks == 40) {
					server.execute(() -> setUp(server));
				}
				if (ticks >= 60) {
					stage = Stage.SETTLE;
					ticks = 0;
				}
			}
			case SETTLE -> {
				client.options.hudHidden = true;
				if (ticks == 120) {
					client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
				}
				if (ticks == 135) {
					shot(client, "a00_blindfold_front.png");
					client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				}
				if (ticks == 150) {
					shot(client, "a01_blindfold_back.png");
					client.options.setPerspective(Perspective.FIRST_PERSON);
				}
				if (ticks >= 165) {
					stage = Stage.CAST;
					ticks = 0;
				}
			}
			case CAST -> {
				if (ticks == 1) {
					client.options.hudHidden = false;
					ShootingStar.LOGGER.info("[selftest] casting");
					ClientPlayNetworking.send(new DomainCastPayload());
					stills(2, 6, 10, 15, 19, 22, 25, 28, 30, 33, 36, 40, 44, 52, 70, 90, 110, DomainTimeline.collapse(HOLD) + 1,
							DomainTimeline.collapse(HOLD) + 4, DomainTimeline.collapse(HOLD) + 8, DomainTimeline.collapse(HOLD) + 13,
							DomainTimeline.end(HOLD) + 3);
				}
				if (ClientDomains.mine() != null) {
					stage = Stage.WATCH;
					ticks = 0;
				} else if (ticks > 100) {
					ShootingStar.LOGGER.error("[selftest] the Domain never started");
					stage = Stage.DONE;
				}
			}
			case WATCH -> {
				ClientDomain domain = latest();
				if (domain != null) {
					// Look round the void: down at the water, round at the held, up again at the eye.
					if (domain.age == 60) {
						client.player.setPitch(55.0F);
					} else if (domain.age == 80) {
						client.player.setPitch(5.0F);
						client.player.setYaw(facing + 180.0F);
					} else if (domain.age == 100) {
						client.player.setYaw(facing);
						client.player.setPitch(-12.0F);
					}
				}
				photograph(client, domain, "b");
				if (domain == null || domain.age > DomainTimeline.end(HOLD) + 5) {
					stage = Stage.OUTSIDE;
					ticks = 0;
				}
			}
			case OUTSIDE -> {
				if (ticks == 40) {
					// Back from it, then one cast a way off, by nobody, to watch from outside.
					server.execute(() -> {
						ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
						ServerWorld world = player.getServerWorld();
						Vec3d center = Vec3d.ofBottomCenter(home);
						Vec3d from = center.add(direction(facing + 180.0F).multiply(AWAY)).add(0.0, 7.0, 0.0);
						player.changeGameMode(GameMode.CREATIVE);
						player.getAbilities().flying = true;
						player.sendAbilitiesUpdate();
						player.networkHandler.requestTeleport(from.x, from.y, from.z, facing, 10.0F);
						DomainManager.launch(world, center, null);
					});
					stills(4, 10, 16, 21, 24, 27, 30, 36, 60, DomainTimeline.collapse(HOLD) + 2, DomainTimeline.collapse(HOLD) + 5,
							DomainTimeline.collapse(HOLD) + 9, DomainTimeline.collapse(HOLD) + 13);
				}
				if (ticks > 41) {
					ClientDomain domain = latest();
					photograph(client, domain, "c");
					if (domain == null && ticks > 60 || domain != null && domain.age > DomainTimeline.end(HOLD) + 5) {
						stage = Stage.CAUGHT;
						ticks = 0;
					}
				}
			}
			case CAUGHT -> {
				if (ticks == 40) {
					// One cast over this player by nobody: caught in it.
					server.execute(() -> {
						ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
						Vec3d center = Vec3d.ofBottomCenter(home);
						player.changeGameMode(GameMode.SURVIVAL);
						player.networkHandler.requestTeleport(center.x + 4.0, center.y, center.z + 4.0, facing + 40.0F, 0.0F);
						DomainManager.launch(player.getServerWorld(), center, null);
					});
					stills(32, 34, 37, 45, 60, 90, DomainTimeline.collapse(HOLD) + 3);
				}
				if (ticks > 41) {
					ClientDomain domain = latest();
					photograph(client, domain, "d");
					if (domain == null && ticks > 60 || domain != null && domain.age > DomainTimeline.end(HOLD) + 5) {
						stage = Stage.DONE;
					}
				}
			}
			case DONE -> {
				ShootingStar.LOGGER.info("[selftest] finished");
				stage = Stage.FINISHED;
				client.scheduleStop();
			}
			case FINISHED -> {
			}
		}
	}

	private static ClientDomain latest() {
		ClientDomain last = null;
		for (ClientDomain domain : ClientDomains.all()) {
			last = domain;
		}
		return last;
	}

	private static Vec3d direction(float yaw) {
		float r = yaw * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
	}

	private static void setUp(IntegratedServer server) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = player.getServerWorld();
		world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		world.getGameRules().get(GojoRules.VOID_RADIUS).set(RADIUS, server);
		world.getGameRules().get(GojoRules.VOID_HOLD).set(HOLD_SECONDS, server);
		world.getGameRules().get(GojoRules.VOID_COOLDOWN).set(0, server);
		world.setTimeOfDay(5000);
		world.setWeather(12000, 0, false, false);
		Owner.set(server, player.getGameProfile().getName());
		player.changeGameMode(GameMode.SURVIVAL);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 60 * 10, 4, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 20 * 60 * 10, 0, false, false));
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));

		// A disc of grass round the spawn, the air over it clear, a few trees left standing at its edge.
		BlockPos spawn = world.getSpawnPos();
		int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
		home = new BlockPos(spawn.getX(), ground, spawn.getZ());
		for (BlockPos pos : BlockPos.iterate(home.add(-DISC, -3, -DISC), home.add(DISC, 24, DISC))) {
			int dx = pos.getX() - home.getX();
			int dz = pos.getZ() - home.getZ();
			if (dx * dx + dz * dz > DISC * DISC) {
				continue;
			}
			int dy = pos.getY() - home.getY();
			world.setBlockState(pos, dy < -1 ? Blocks.DIRT.getDefaultState() : dy == -1 ? Blocks.GRASS_BLOCK.getDefaultState()
					: Blocks.AIR.getDefaultState());
		}
		facing = 0.0F;
		Vec3d at = Vec3d.ofBottomCenter(home);
		player.networkHandler.requestTeleport(at.x, at.y, at.z, facing, 0.0F);

		// Creatures stood round him: some in front, where the camera looks, some behind and to the sides.
		set(world, EntityType.VILLAGER, at.add(direction(facing).multiply(6.0)).add(direction(facing + 90.0F).multiply(3.0)), facing + 180.0F);
		set(world, EntityType.ZOMBIE, at.add(direction(facing).multiply(9.0)).add(direction(facing - 90.0F).multiply(4.0)), facing + 160.0F);
		set(world, EntityType.COW, at.add(direction(facing + 120.0F).multiply(7.0)), facing);
		set(world, EntityType.SHEEP, at.add(direction(facing - 130.0F).multiply(10.0)), facing + 30.0F);
		set(world, EntityType.PILLAGER, at.add(direction(facing + 200.0F).multiply(12.0)), facing);
		set(world, EntityType.PIG, at.add(direction(facing + 40.0F).multiply(13.0)), facing + 200.0F);
		ShootingStar.LOGGER.info("[selftest] {} owns the blindfold, standing at {}", Owner.get(), home);
	}

	private static void set(ServerWorld world, EntityType<? extends MobEntity> type, Vec3d at, float yaw) {
		MobEntity mob = type.create(world);
		if (mob == null) {
			return;
		}
		mob.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
		mob.setHeadYaw(yaw);
		mob.setBodyYaw(yaw);
		mob.setAiDisabled(true);
		mob.setPersistent();
		mob.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(at)), SpawnReason.COMMAND, null);
		world.spawnEntity(mob);
	}
}
