package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.gojolander.GojoRules;
import io.github.kortev.gojolander.GojoState;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.Owner;
import io.github.kortev.gojolander.domain.DomainManager;
import io.github.kortev.gojolander.purple.PurpleManager;
import io.github.kortev.gojolander.purple.PurpleTimeline;
import io.github.kortev.shootingstar.gap.GapManager;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

/**
 * Gojolander's Hollow Purple on a stone field: it erases what is in its path (a wall, a chest with things in it, the
 * ground under it) leaving a clean round tunnel and dropping nothing; leaves bedrock; kills a mob in its path and
 * not one beside it; leaves the world alone with {@code hollowPurpleErases} off (and still kills); keeps its
 * cooldown; is cast only by its owner (or anyone while nobody's); and is not cast during a Genesis Key event or in the
 * middle of a Domain.
 */
public class PurpleGameTests implements FabricGameTest {
	private static final int RADIUS = 3;
	private static final int RANGE = 60;
	/** Far from every other test's ground (the Domain's tests use 220). */
	private static final int FAR = 300;
	private static final String OWNER = "purple_owner";
	/** The wall across its path starts this far in front of the caster (blocks), and is this thick. */
	private static final int WALL_FROM = 10;
	private static final int WALL_THICK = 6;
	/** How long the tests wait for their ground and chunks before casting. */
	private static final int SETTLE = 10;
	/** Ticks it flies for RANGE blocks (PurpleTimeline.flight(60)): a constant, for the annotations. */
	private static final int FLIGHT = 18;
	private static final int WHOLE = SETTLE + PurpleTimeline.RELEASE + FLIGHT + 8;

	/**
	 * A wall of stone across its path with a bedrock block in the middle of it, a chest of diamonds beyond, and the
	 * ground itself: all within the tunnel is gone, nothing drops, bedrock stays, stone beyond the radius stays; a cow in
	 * the path dies and one beside it lives.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_purple_erase", tickLimit = WHOLE + 40)
	public void erasesWhatIsInItsWay(TestContext context) {
		ServerWorld world = context.getWorld();
		rules(world, true, 0);
		// What a mob leaves when it dies is the game's own business: the test is about what the blocks leave.
		world.getGameRules().get(GameRules.DO_MOB_LOOT).set(false, world.getServer());
		Scene scene = scene(context);
		ServerPlayerEntity caster = caster(context, scene);
		CowEntity inPath = context.spawnEntity(EntityType.COW, relative(context, scene.at(20.0, 0.0, 0.0)));
		CowEntity beside = context.spawnEntity(EntityType.COW, relative(context, scene.at(20.0, 0.0, 8.0)));
		VillagerEntity villager = context.spawnEntity(EntityType.VILLAGER, relative(context, scene.at(28.0, 0.0, 0.5)));
		for (MobEntity mob : List.of(inPath, beside, villager)) {
			mob.setAiDisabled(true);
		}
		int[] state = {0};
		PurpleManager.Purple[] purple = new PurpleManager.Purple[1];
		context.runAtEveryTick(() -> {
			state[0]++;
			if (state[0] == SETTLE) {
				context.assertTrue(PurpleManager.cast(caster), "the owner could not cast it");
				purple[0] = PurpleManager.active().get(0);
			}
			if (state[0] == WHOLE) {
				List<String> problems = new ArrayList<>();
				// The tunnel: everything inside its radius along the way, from the wall's start to its end, is air.
				int wall = 0;
				for (int dx = WALL_FROM; dx < WALL_FROM + WALL_THICK; dx++) {
					for (int oy = 0; oy <= 4; oy++) {
						for (int dz = -2; dz <= 2; dz++) {
							boolean axisBedrock = dx == WALL_FROM + 2 && oy == 1 && dz == 0;
							double up = oy + 0.5 - 1.62;
							if (!axisBedrock && up * up + (double) dz * dz < (RADIUS - 1) * (RADIUS - 1)
									&& !world.getBlockState(scene.block(dx, oy, dz)).isAir()) {
								wall++;
							}
						}
					}
				}
				if (wall > 0) {
					problems.add(wall + " blocks of the wall were left in the tunnel");
				}
				if (!world.getBlockState(scene.block(WALL_FROM + 2, 1, 0)).isOf(Blocks.BEDROCK)) {
					problems.add("the bedrock in the way was erased");
				}
				if (!world.getBlockState(scene.block(WALL_FROM + 2, 8, 0)).isOf(Blocks.STONE)) {
					problems.add("the wall above the tunnel was erased (stone beyond its radius)");
				}
				if (!world.getBlockState(scene.block(WALL_FROM + 2, 1, RADIUS + 3)).isOf(Blocks.STONE)) {
					problems.add("the wall beside the tunnel was erased");
				}
				if (!world.getBlockState(scene.block(16, 0, 0)).isAir()) {
					problems.add("the chest in the way is still there");
				}
				if (!world.getBlockState(scene.block(30, -1, 0)).isAir()) {
					problems.add("the ground under the sphere was not carved");
				}
				if (!world.getBlockState(scene.block(30, -4, 0)).isOf(Blocks.STONE)) {
					problems.add("the ground far under it was erased");
				}
				// Nothing drops: no item anywhere in the field.
				List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, scene.field(), e -> true);
				if (!items.isEmpty()) {
					problems.add(items.size() + " items dropped, e.g. " + items.get(0).getStack());
				}
				if (inPath.isAlive()) {
					problems.add("the cow in its path lived");
				}
				if (villager.isAlive()) {
					problems.add("the villager in its path lived");
				}
				if (!beside.isAlive()) {
					problems.add("the cow beside its path died");
				}
				if (!caster.isAlive()) {
					problems.add("its caster died");
				}
				if (!world.getBlockState(scene.block(0, -1, 0)).isOf(Blocks.STONE)) {
					problems.add("the ground under the caster was erased");
				}
				if (purple[0] == null || purple[0].erased < 200) {
					problems.add("it counted only " + (purple[0] == null ? 0 : purple[0].erased) + " blocks erased");
				}
				finish(context, scene, caster);
				world.getGameRules().get(GameRules.DO_MOB_LOOT).set(true, world.getServer());
				if (!problems.isEmpty()) {
					context.throwGameTestException(String.join("; ", problems));
				}
				context.complete();
			}
		});
	}

	/** With {@code hollowPurpleErases} off it still kills what is in its way but leaves every block where it was. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_purple_nobreak", tickLimit = WHOLE + 40)
	public void leavesTheWorldWhenToldTo(TestContext context) {
		ServerWorld world = context.getWorld();
		rules(world, false, 0);
		Scene scene = scene(context);
		ServerPlayerEntity caster = caster(context, scene);
		CowEntity inPath = context.spawnEntity(EntityType.COW, relative(context, scene.at(20.0, 0.0, 0.0)));
		inPath.setAiDisabled(true);
		int[] state = {0};
		context.runAtEveryTick(() -> {
			state[0]++;
			if (state[0] == SETTLE) {
				context.assertTrue(PurpleManager.cast(caster), "the owner could not cast it");
			}
			if (state[0] == WHOLE) {
				List<String> problems = new ArrayList<>();
				for (int dx = WALL_FROM; dx < WALL_FROM + WALL_THICK; dx++) {
					if (world.getBlockState(scene.block(dx, 2, 0)).isAir()) {
						problems.add("a block of the wall was erased with the rule off");
						break;
					}
				}
				if (!world.getBlockState(scene.block(30, -1, 0)).isOf(Blocks.STONE)) {
					problems.add("the ground was carved with the rule off");
				}
				if (inPath.isAlive()) {
					problems.add("the cow in its path lived");
				}
				finish(context, scene, caster);
				rules(world, true, 0);
				if (!problems.isEmpty()) {
					context.throwGameTestException(String.join("; ", problems));
				}
				context.complete();
			}
		});
	}

	/**
	 * Its cooldown: casting sets it (and the blindfold shows it), a second cast is refused while it is going and then
	 * while it cools, and an operator makes it ready again.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_purple_cooldown", tickLimit = SETTLE + 20)
	public void cooldownHolds(TestContext context) {
		ServerWorld world = context.getWorld();
		rules(world, true, 300);
		Scene scene = scene(context);
		ServerPlayerEntity caster = caster(context, scene);
		context.waitAndRun(SETTLE, () -> {
			try {
				long now = world.getServer().getOverworld().getTime();
				context.assertTrue(PurpleManager.cast(caster), "the owner could not cast it the first time");
				context.assertTrue(GojoState.get(world.getServer()).purpleReadyAt(caster.getUuid()) > now + 20 * 299, "no cooldown was set");
				context.assertTrue(caster.getItemCooldownManager().isCoolingDown(Gojolander.BLINDFOLD), "the blindfold does not show it cooling");
				context.assertTrue(!PurpleManager.cast(caster), "a second cast went ahead while the first was running");
				PurpleManager.cancelAll();
				context.assertTrue(!PurpleManager.cast(caster), "a second cast went ahead during the cooldown");
				context.assertTrue(PurpleManager.active().isEmpty(), "a refused cast still started one");
				PurpleManager.ready(caster);
				context.assertTrue(!caster.getItemCooldownManager().isCoolingDown(Gojolander.BLINDFOLD), "the blindfold still shows the cooldown");
				context.assertTrue(PurpleManager.cast(caster), "it could not be cast after being made ready");
			} finally {
				PurpleManager.cancelAll();
				GojoState.get(world.getServer()).setPurpleReadyAt(caster.getUuid(), 0L);
				finish(context, scene, caster);
				rules(world, true, 0);
			}
			context.complete();
		});
	}

	/** Only its owner casts it (and with the blindfold on); while it is nobody's, anyone with the blindfold on does. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_purple_owner", tickLimit = SETTLE + 20)
	public void onlyTheOwnerCasts(TestContext context) {
		ServerWorld world = context.getWorld();
		rules(world, true, 0);
		Scene scene = scene(context);
		ServerPlayerEntity bare = player(context, OWNER, scene.at(0.0, 0.0, 0.0));
		ServerPlayerEntity other = player(context, "not_the_purple_owner", scene.at(0.0, 0.0, 12.0));
		other.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
		context.waitAndRun(SETTLE, () -> {
			try {
				Owner.set(world.getServer(), OWNER);
				context.assertTrue(!PurpleManager.cast(bare), "the owner cast it without the blindfold on");
				context.assertTrue(!PurpleManager.cast(other), "someone else cast it with the blindfold on");
				context.assertTrue(PurpleManager.active().isEmpty(), "one is running after two refusals");
				Owner.set(world.getServer(), Owner.PLACEHOLDER);
				context.assertTrue(PurpleManager.cast(other), "someone could not cast it while it was nobody's");
			} finally {
				PurpleManager.cancelAll();
				Owner.set(world.getServer(), OWNER);
				GojoState.get(world.getServer()).setPurpleReadyAt(other.getUuid(), 0L);
				finish(context, scene, bare, other);
			}
			context.complete();
		});
	}

	/** Not while a Genesis Key event is going on, and not in the middle of his Domain (nor a Domain in the middle of it). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_purple_busy", tickLimit = SETTLE + 20)
	public void notDuringAnotherEvent(TestContext context) {
		ServerWorld world = context.getWorld();
		rules(world, true, 0);
		Scene scene = scene(context);
		ServerPlayerEntity caster = caster(context, scene);
		context.waitAndRun(SETTLE, () -> {
			try {
				GapManager.launch(world, scene.block(0, 30, 40), null);
				context.assertTrue(GapManager.running(), "the Genesis Key event did not start");
				context.assertTrue(!PurpleManager.cast(caster), "it was cast during a Genesis Key event");
				GapManager.endNow(world.getServer());
				context.assertTrue(!GapManager.running(), "the Genesis Key event would not end");

				DomainManager.launch(world, caster.getPos(), caster);
				context.assertTrue(!PurpleManager.cast(caster), "it was cast in the middle of his Domain");
				DomainManager.collapseAll();
				context.assertTrue(PurpleManager.cast(caster), "it could not be cast once the Domain was gone");
				context.assertTrue(!DomainManager.cast(caster), "a Domain was cast in the middle of a Purple");
			} finally {
				GapManager.endNow(world.getServer());
				DomainManager.collapseAll();
				PurpleManager.cancelAll();
				finish(context, scene, caster);
			}
			context.complete();
		});
	}

	// =============================================================================================

	/** Where a test's field is: its caster's feet, looking east, and the way every offset is laid out from there. */
	private record Scene(ServerWorld world, BlockPos origin) {
		/** A point {@code ahead} blocks east of the caster, {@code up} above his feet, {@code right} (south) of him. */
		Vec3d at(double ahead, double up, double right) {
			return Vec3d.ofBottomCenter(origin).add(ahead, up, right);
		}

		/** The block at these offsets from the one his feet are in (up 0 is the layer of air he stands in, -1 the floor). */
		BlockPos block(int ahead, int up, int right) {
			return origin.add(ahead, up, right);
		}

		Box field() {
			return new Box(origin).expand(RANGE + 12.0, 20.0, 24.0);
		}
	}

	private static void rules(ServerWorld world, boolean erases, int cooldownSeconds) {
		world.getGameRules().get(GojoRules.PURPLE_RADIUS).set(RADIUS, world.getServer());
		world.getGameRules().get(GojoRules.PURPLE_RANGE).set(RANGE, world.getServer());
		world.getGameRules().get(GojoRules.PURPLE_ERASES).set(erases, world.getServer());
		world.getGameRules().get(GojoRules.PURPLE_COOLDOWN).set(cooldownSeconds, world.getServer());
		Owner.set(world.getServer(), OWNER);
	}

	/**
	 * A field of stone far from every other test: a floor, air over it, a wall across the way with a bedrock block in
	 * its middle, and a chest of diamonds beyond. Its chunks are kept loaded.
	 */
	private static Scene scene(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockPos origin = context.getAbsolutePos(new BlockPos(4, 1, FAR));
		int ahead = RANGE + 16;
		for (int cx = (origin.getX() - 24) >> 4; cx <= (origin.getX() + ahead + 24) >> 4; cx++) {
			for (int cz = (origin.getZ() - 40) >> 4; cz <= (origin.getZ() + 40) >> 4; cz++) {
				world.setChunkForced(cx, cz, true);
			}
		}
		for (BlockPos pos : BlockPos.iterate(origin.add(-8, -RADIUS * 3, -24), origin.add(ahead, 14, 24))) {
			world.setBlockState(pos, pos.getY() < origin.getY() ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
		}
		// The wall, 12 high and 20 wide, across the way; bedrock in its middle at the height of his eyes.
		for (BlockPos pos : BlockPos.iterate(origin.add(WALL_FROM, 0, -10), origin.add(WALL_FROM + WALL_THICK - 1, 12, 10))) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
		world.setBlockState(origin.add(WALL_FROM + 2, 1, 0), Blocks.BEDROCK.getDefaultState());
		// A chest of diamonds on the floor beyond, in its path.
		BlockPos chest = origin.add(16, 0, 0);
		world.setBlockState(chest, Blocks.CHEST.getDefaultState());
		if (world.getBlockEntity(chest) instanceof ChestBlockEntity entity) {
			entity.setStack(0, new ItemStack(Items.DIAMOND, 32));
			entity.setStack(1, new ItemStack(Items.GOLD_INGOT, 16));
		}
		return new Scene(world, origin);
	}

	/** The blindfold's owner with it on, at the field's start, looking east. */
	private static ServerPlayerEntity caster(TestContext context, Scene scene) {
		ServerPlayerEntity caster = player(context, OWNER, scene.at(0.0, 0.0, 0.0));
		caster.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
		caster.setYaw(-90.0F);
		caster.setPitch(0.0F);
		caster.setHeadYaw(-90.0F);
		return caster;
	}

	private static void finish(TestContext context, Scene scene, ServerPlayerEntity... players) {
		for (ServerPlayerEntity player : players) {
			context.getWorld().getServer().getPlayerManager().remove(player);
		}
		PurpleManager.cancelAll();
		BlockPos origin = scene.origin();
		int ahead = RANGE + 16;
		// Everything it was built of taken away again: other tests stand about here, and what is left would be under their feet.
		for (BlockPos pos : BlockPos.iterate(origin.add(-8, -RADIUS * 3, -24), origin.add(ahead, 14, 24))) {
			context.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState(), 2 | 16);
		}
		for (Entity entity : context.getWorld().getOtherEntities(null, scene.field())) {
			if (!(entity instanceof PlayerEntity)) {
				entity.discard();
			}
		}
		for (int cx = (origin.getX() - 24) >> 4; cx <= (origin.getX() + ahead + 24) >> 4; cx++) {
			for (int cz = (origin.getZ() - 40) >> 4; cz <= (origin.getZ() + 40) >> 4; cz++) {
				context.getWorld().setChunkForced(cx, cz, false);
			}
		}
	}

	private static Vec3d relative(TestContext context, Vec3d absolute) {
		return absolute.subtract(context.getAbsolute(Vec3d.ZERO));
	}

	private static ServerPlayerEntity player(TestContext context, String name, Vec3d at) {
		ServerWorld world = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		if (at != null) {
			player.teleport(world, at.x, at.y, at.z, -90.0F, 0.0F);
		}
		return player;
	}
}
