package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.item.GenesisKeyItem;
import io.github.kortev.shootingstar.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;

/**
 * The server going down without warning in the middle of a Ginnungagap event (tools/crashtest.sh), on a dedicated
 * server with one player (connected the way the game tests connect theirs).
 * <p>
 * With -Dshootingstar.crashtest=crash: the player turns a key from well outside the zone, is carried to the rim with
 * everyone, and partway through the carving of the hole the server saves everything, as an autosave would, and the
 * JVM is killed outright: nothing of the server's own going down runs.
 * <p>
 * With -Dshootingstar.crashtest=recover, the next start: the hole must be finished all the way down with no barrier
 * left standing over it, lit, the world's spawn out of it; and when the player comes back they are sent home to solid
 * ground with their cracked key shattered, held by nothing.
 */
public class CrashTestServer implements ModInitializer {
	private static final UUID HOLDER = UUID.nameUUIDFromBytes("shootingstar-crashtest-holder".getBytes(StandardCharsets.UTF_8));
	private static final String EXPECT = "crashtest-expect.properties";
	private static String mode;
	private static int ticks;
	private static BlockPos target;
	private static Vec3d home;
	private static int radius;
	private static ServerPlayerEntity holder;
	private static final StringBuilder PROBLEMS = new StringBuilder();

	@Override
	public void onInitialize() {
		mode = System.getProperty("shootingstar.crashtest");
		if (mode == null) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerWorld world = server.getOverworld();
			world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
			world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
			world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
			ShootingStar.LOGGER.info("[crashtest] {} phase ready", mode);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ticks++;
			if ("crash".equals(mode)) {
				crash(server);
			} else {
				recover(server);
			}
			if (ticks > 20 * 60 * 5) {
				ShootingStar.LOGGER.error("[crashtest] timed out");
				Runtime.getRuntime().halt(3);
			}
		});
	}

	private static void crash(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		if (ticks == 40) {
			BlockPos spawn = world.getSpawnPos();
			radius = 96;
			target = new BlockPos(spawn.getX(), top(world, spawn.getX(), spawn.getZ() + 64) - 1, spawn.getZ() + 64);
			int hx = spawn.getX() + 200;
			int hz = spawn.getZ() + 40;
			home = new Vec3d(hx + 0.5, top(world, hx, hz), hz + 0.5);
			holder = connect(server, world, home);
			ItemStack key = new ItemStack(ModItems.GENESIS_KEY);
			NbtCompound cracked = new NbtCompound();
			cracked.putBoolean("Cracked", true);
			key.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(cracked));
			holder.getInventory().setStack(0, key);
			Properties expect = new Properties();
			expect.setProperty("target", target.getX() + " " + target.getY() + " " + target.getZ());
			expect.setProperty("home", home.x + " " + home.y + " " + home.z);
			expect.setProperty("radius", Integer.toString(radius));
			try (Writer out = Files.newBufferedWriter(server.getRunDirectory().resolve(EXPECT))) {
				expect.store(out, "what the recover phase checks against");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			GapManager.launch(world, target, holder);
			ShootingStar.LOGGER.info("[crashtest] key turned on {} by Holder from home at {}", target.toShortString(), fmt(home));
		}
		if (holder != null && !GapManager.active().isEmpty() && GapManager.active().get(0).age() == GapTimeline.NOTHING + 40) {
			ShootingStar.LOGGER.info("[crashtest] mid-carving: Holder at {} held {}; saving everything and killing the server", fmt(holder.getPos()),
					GapManager.held(holder));
			server.saveAll(false, true, true);
			Runtime.getRuntime().halt(0);
		}
	}

	private static void recover(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		if (ticks == 1) {
			Properties expect = new Properties();
			try (Reader in = Files.newBufferedReader(server.getRunDirectory().resolve(EXPECT))) {
				expect.load(in);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			String[] t = expect.getProperty("target").split(" ");
			target = new BlockPos(Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2]));
			String[] h = expect.getProperty("home").split(" ");
			home = new Vec3d(Double.parseDouble(h[0]), Double.parseDouble(h[1]), Double.parseDouble(h[2]));
			radius = Integer.parseInt(expect.getProperty("radius"));
			if (GapManager.running()) {
				PROBLEMS.append("an event is still running after the restart; ");
			}
			checkHole(world);
			double spawnOut = Math.hypot(world.getSpawnPos().getX() - target.getX(), world.getSpawnPos().getZ() - target.getZ());
			if (spawnOut <= radius + 6) {
				PROBLEMS.append("the world's spawn is still in the hole, ").append(String.format(Locale.ROOT, "%.0f", spawnOut)).append(" out; ");
			}
		}
		if (ticks == 40) {
			// Back on the server, from where they were saved: held at the rim, over the hole.
			holder = connect(server, world, null);
		}
		if (ticks == 80) {
			BlockPos under = holder.getBlockPos().down();
			boolean ground = !world.getBlockState(under).getCollisionShape(world, under).isEmpty();
			double off = Math.hypot(holder.getX() - home.x, holder.getZ() - home.z);
			if (!ground || off > 8.0 || Math.abs(holder.getY() - home.y) > 2.0) {
				PROBLEMS.append("Holder was not sent home to solid ground: at ").append(fmt(holder.getPos())).append(", ")
						.append(String.format(Locale.ROOT, "%.1f", off)).append(" from home, over ").append(world.getBlockState(under)).append("; ");
			}
			if (GapManager.held(holder)) {
				PROBLEMS.append("Holder is still held; ");
			}
			for (int i = 0; i < holder.getInventory().size(); i++) {
				ItemStack stack = holder.getInventory().getStack(i);
				if (stack.isOf(ModItems.GENESIS_KEY) && GenesisKeyItem.cracked(stack)) {
					PROBLEMS.append("Holder's cracked key did not shatter; ");
				}
			}
		}
		if (ticks == 200 || ticks == 600) {
			// The light down the hole, once the light engine has had its time with what the recovery queued.
			StringBuilder dark = new StringBuilder();
			for (int[] d : samples()) {
				for (int depth : new int[] {2, 20, 60, 120}) {
					BlockPos at = target.add(d[0], -depth, d[1]);
					world.getChunk(at.getX() >> 4, at.getZ() >> 4);
					int light = world.getLightLevel(LightType.SKY, at);
					if (at.getY() > world.getBottomY() && light < 15) {
						dark.append(at.toShortString()).append(" = ").append(light).append(", ");
					}
				}
			}
			ShootingStar.LOGGER.info("[crashtest] sky light at tick {}: {}", ticks, dark.length() == 0 ? "full everywhere" : dark);
			if (ticks == 600) {
				if (dark.length() > 0) {
					PROBLEMS.append("places down the hole without full sky light: ").append(dark).append("; ");
				}
				ShootingStar.LOGGER.info("[crashtest] recover: {}", PROBLEMS.length() == 0 ? "ok" : PROBLEMS);
				server.stop(false);
			}
		}
	}

	/** The hole all the way down, and nothing of the floor the black was walked on left anywhere over it or its fissures. */
	private static void checkHole(ServerWorld world) {
		int solid = 0;
		for (int[] d : samples()) {
			for (int y = world.getBottomY(); y < world.getTopY(); y++) {
				BlockState state = world.getBlockState(target.add(d[0], y - target.getY(), d[1]));
				if (!state.isAir()) {
					solid++;
				}
			}
		}
		if (solid > 0) {
			PROBLEMS.append(solid).append(" blocks left in columns that should be empty to the bottom; ");
		}
		int barriers = 0;
		int reach = radius + 80;
		BlockPos.Mutable at = new BlockPos.Mutable();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int x = target.getX() + dx;
				int z = target.getZ() + dz;
				int top = top(world, x, z);
				if (top > world.getBottomY() && world.getBlockState(at.set(x, top - 1, z)).isOf(Blocks.BARRIER)) {
					barriers++;
				}
			}
		}
		if (barriers > 0) {
			PROBLEMS.append(barriers).append(" barriers left standing over the hole and its fissures; ");
		}
		ShootingStar.LOGGER.info("[crashtest] hole checked: {} blocks left in the sampled columns, {} barriers", solid, barriers);
	}

	/** Columns well inside the ragged edge: the middle and rings round it. */
	private static int[][] samples() {
		int[][] out = new int[17][];
		out[0] = new int[] {0, 0};
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI / 8.0;
			double r = (i % 2 == 0 ? 0.45 : 0.85) * radius;
			out[i + 1] = new int[] {MathHelper.floor(Math.cos(a) * r), MathHelper.floor(Math.sin(a) * r)};
		}
		return out;
	}

	/** Holder on the server, as the game tests connect their players: at {@code at}, or (null) where they were saved. */
	private static ServerPlayerEntity connect(MinecraftServer server, ServerWorld world, Vec3d at) {
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(HOLDER, "Holder"), false);
		ServerPlayerEntity player = new ServerPlayerEntity(server, world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerManager().onPlayerConnect(connection, player, data);
		ServerPlayerEntity joined = server.getPlayerManager().getPlayer(HOLDER);
		if (at != null) {
			joined.changeGameMode(GameMode.SURVIVAL);
			joined.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		}
		ShootingStar.LOGGER.info("[crashtest] Holder connected at {}", fmt(joined.getPos()));
		return joined;
	}

	private static int top(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
	}

	private static String fmt(Vec3d p) {
		return String.format(Locale.ROOT, "%.1f %.1f %.1f", p.x, p.y, p.z);
	}
}
