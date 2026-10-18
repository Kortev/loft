package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.GojoRules;
import io.github.kortev.gojolander.Owner;
import io.github.kortev.gojolander.domain.DomainManager;
import io.github.kortev.gojolander.domain.DomainTimeline;
import io.github.kortev.shootingstar.OwnerOnly;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Gojolander's Unlimited Void on flat stone: who may craft the blindfold and cast it, nobody's and someone's; a whole Domain, catching what is
 * in it and not what is not, holding it still (in the air too), letting nobody and nothing across the barrier either
 * way, and letting them go dazed; and a Domain ending early when its caster falls.
 */
public class DomainGameTests implements FabricGameTest {
	/** The Domain these tests cast: small and short, so a whole one fits in a test. */
	private static final int RADIUS = 12;
	private static final int HOLD_SECONDS = 2;
	private static final int HOLD = HOLD_SECONDS * 20;
	/**
	 * How far from its test box each Domain test lays its floor: clear of every other test's ground (Mjölnir's and
	 * Gungnir's at 40 to 48, the Genesis Key's at 80 and -120), since tests stand close together and their ground, the
	 * chunks a Domain test keeps loaded and the Domain itself would otherwise reach into theirs.
	 */
	private static final int FAR = 220;
	/** Whose the blindfold is in these tests. */
	private static final String OWNER = "blindfold_owner";
	/** Ticks of slack either side of a mark: the test and the Domain tick at different points of the server's tick. */
	private static final int SLACK = 3;

	/**
	 * While the blindfold is nobody's, anyone crafts it; once it is someone's, only they do: kortev, who crafts
	 * everything of his own, does not, and anyone may still make sticks.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_gojo_owner", tickLimit = 20)
	public void craftingFollowsTheOwner(TestContext context) {
		ServerPlayerEntity owner = player(context, OWNER, null);
		ServerPlayerEntity kortev = player(context, OwnerOnly.OWNER, null);
		ServerPlayerEntity other = player(context, "someone_else", null);
		try {
			Owner.set(context.getWorld().getServer(), Owner.PLACEHOLDER);
			context.assertTrue(!Owner.claimed(), "it was someone's after being made nobody's");
			context.assertTrue(crafted(context, other, true).isOf(Gojolander.BLINDFOLD), "someone could not craft it while it was nobody's");
			context.assertTrue(crafted(context, kortev, true).isOf(Gojolander.BLINDFOLD), "kortev could not craft it while it was nobody's");
			owner(context);
			context.assertTrue(crafted(context, owner, true).isOf(Gojolander.BLINDFOLD), "the owner could not craft the blindfold");
			context.assertTrue(crafted(context, kortev, true).isEmpty(), "kortev crafted the blindfold");
			context.assertTrue(crafted(context, other, true).isEmpty(), "someone else crafted the blindfold");
			context.assertTrue(crafted(context, other, false).isOf(Items.STICK), "someone else could not craft sticks");
		} finally {
			owner(context);
			remove(context, owner, kortev, other);
		}
		context.complete();
	}

	/**
	 * Nobody casts it without the blindfold on, nor, once it is someone's, anybody but its owner with it on; while it
	 * is nobody's, anyone with it on does.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_gojo_refuse", tickLimit = 20)
	public void castingFollowsTheOwner(TestContext context) {
		Vec3d at = context.getAbsolute(new Vec3d(4.5, 2.0, 4.5));
		ServerPlayerEntity bare = player(context, owner(context), at);
		ServerPlayerEntity other = player(context, "not_the_owner", at.add(3.0, 0.0, 0.0));
		other.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
		context.getWorld().getGameRules().get(GojoRules.VOID_COOLDOWN).set(0, context.getWorld().getServer());
		try {
			context.assertTrue(!DomainManager.cast(bare), "the owner cast it without the blindfold on");
			context.assertTrue(!DomainManager.cast(other), "someone else cast it with the blindfold on");
			context.assertTrue(DomainManager.active().isEmpty(), "a Domain is running after two refusals");
			Owner.set(context.getWorld().getServer(), Owner.PLACEHOLDER);
			context.assertTrue(DomainManager.cast(other), "someone could not cast it while it was nobody's");
		} finally {
			DomainManager.collapseAll();
			owner(context);
			remove(context, bare, other);
		}
		context.complete();
	}

	/** Makes the test's owner the blindfold's owner, and returns their name. */
	private static String owner(TestContext context) {
		Owner.set(context.getWorld().getServer(), OWNER);
		return OWNER;
	}

	/**
	 * A whole Domain: a cow, a villager caught falling, and a player inside are caught; a player outside is not. The
	 * caught stay exactly where they were; the one outside cannot step in, an arrow from outside stops at the barrier,
	 * and the caster cannot step out. When it shatters they are let go broken (half a heart) and dazed, the one outside
	 * untouched, and he cannot cast it again until it has cooled.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_gojo_domain", tickLimit = DomainTimeline.VOID + HOLD + 80)
	public void wholeDomain(TestContext context) {
		ServerWorld world = context.getWorld();
		rules(world, 600);
		Vec3d center = floor(context);
		ServerPlayerEntity caster = player(context, owner(context), center);
		caster.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
		ServerPlayerEntity inside = player(context, "caught", center.add(8.0, 0.0, 0.0));
		ServerPlayerEntity outside = player(context, "watching", center.add(0.0, 0.0, RADIUS + 3.0));
		ServerPlayerEntity creative = player(context, "builder", center.add(-5.0, 0.0, 0.0));
		creative.changeGameMode(GameMode.CREATIVE);
		CowEntity cow = context.spawnEntity(EntityType.COW, relative(context, center.add(0.0, 0.0, -6.0)));
		List<String> problems = new ArrayList<>();

		context.assertTrue(DomainManager.cast(caster), "the owner could not cast it");
		DomainManager.Domain domain = DomainManager.active().get(DomainManager.active().size() - 1);
		VillagerEntity[] villager = new VillagerEntity[1];
		Vec3d[] held = new Vec3d[3];
		ArrowEntity[] arrow = new ArrowEntity[1];
		context.runAtTick(DomainTimeline.CLOSE - 2, () -> {
			if (DomainManager.isHeld(cow) || DomainManager.isHeld(inside)) {
				problems.add("something was held before the barrier closed");
			}
			// Dropped from on high just before it closes: caught in the air.
			villager[0] = context.spawnEntity(EntityType.VILLAGER, relative(context, center.add(-3.0, 6.0, 3.0)));
		});
		context.runAtTick(DomainTimeline.CLOSE + SLACK, () -> {
			for (var e : List.of(cow, villager[0], inside)) {
				if (!DomainManager.isHeld(e)) {
					problems.add(e.getName().getString() + " was not caught");
				}
			}
			if (DomainManager.isHeld(outside)) {
				problems.add("the player outside was caught");
			}
			if (DomainManager.isHeld(creative)) {
				problems.add("the player in creative was caught");
			}
			if (DomainManager.isHeld(caster)) {
				problems.add("the caster caught himself");
			}
			held[0] = cow.getPos();
			held[1] = villager[0].getPos();
			held[2] = inside.getPos();
			// Try to move what is held; try to step in from outside; fire at it from outside; try to step out.
			cow.setVelocity(1.0, 0.5, 0.0);
			inside.networkHandler.requestTeleport(held[2].x + 2.0, held[2].y, held[2].z, 0.0F, 0.0F);
			outside.networkHandler.requestTeleport(center.x, center.y, center.z + RADIUS - 3.0, 0.0F, 0.0F);
			caster.networkHandler.requestTeleport(center.x + RADIUS + 3.0, center.y, center.z, 0.0F, 0.0F);
			arrow[0] = new ArrowEntity(EntityType.ARROW, world);
			arrow[0].setPosition(center.add(-(RADIUS + 4.0), 1.5, 0.0));
			arrow[0].setVelocity(1.6, 0.0, 0.0);
			arrow[0].setNoGravity(true);
			world.spawnEntity(arrow[0]);
		});
		// The arrow's flight, a tick at a time (out here, so far from the test, the world does not move it itself).
		for (int i = 1; i <= 4; i++) {
			int step = i;
			context.runAtTick(DomainTimeline.CLOSE + SLACK + step, () -> {
				if (!arrow[0].isRemoved()) {
					arrow[0].setPosition(center.add(-(RADIUS + 4.0) + 2.0 * step, 1.5, 0.0));
				}
			});
		}
		context.runAtTick(DomainTimeline.CLOSE + 15, () -> {
			if (cow.getPos().squaredDistanceTo(held[0]) > 0.01) {
				problems.add("the cow moved while held: " + held[0] + " to " + cow.getPos());
			}
			if (villager[0].getPos().squaredDistanceTo(held[1]) > 0.01) {
				problems.add("the villager fell while held: " + held[1] + " to " + villager[0].getPos());
			}
			if (inside.getPos().squaredDistanceTo(held[2]) > 0.05) {
				problems.add("the caught player was not put back: " + held[2] + " to " + inside.getPos());
			}
			if (domain.contains(outside.getBoundingBox().getCenter())) {
				problems.add("the player outside got in");
			}
			if (!domain.contains(caster.getBoundingBox().getCenter())) {
				problems.add("the caster got out");
			}
			if (!arrow[0].isRemoved()) {
				problems.add("the arrow from outside was not stopped (at " + arrow[0].getPos().subtract(center) + " from the middle)");
			}
		});
		context.runAtTick(DomainTimeline.collapse(HOLD) + SLACK, () -> {
			if (DomainManager.isHeld(cow) || DomainManager.isHeld(inside) || DomainManager.isHeld(villager[0])) {
				problems.add("something was still held after the barrier shattered");
			}
			if (!inside.hasStatusEffect(StatusEffects.SLOWNESS) || !inside.hasStatusEffect(StatusEffects.DARKNESS)) {
				problems.add("the caught player came out of it without being dazed");
			}
			if (!cow.hasStatusEffect(StatusEffects.SLOWNESS)) {
				problems.add("the cow came out of it without being slowed");
			}
			if (inside.getHealth() > 1.0F || cow.getHealth() > 1.0F) {
				problems.add("the void did not break them (" + inside.getHealth() + ", " + cow.getHealth() + ")");
			}
			if (outside.getHealth() < outside.getMaxHealth()) {
				problems.add("the player outside was hurt");
			}
			if (outside.hasStatusEffect(StatusEffects.SLOWNESS)) {
				problems.add("the player outside was dazed");
			}
		});
		context.runAtTick(DomainTimeline.end(HOLD) + SLACK, () -> {
			if (DomainManager.active().contains(domain)) {
				problems.add("the Domain was still running after its end");
			}
			if (DomainManager.cast(caster)) {
				problems.add("he cast it again before it had cooled");
			}
			// With no cooldown it is ready at once.
			rules(world, 0);
			DomainManager.ready(caster);
			if (!DomainManager.cast(caster)) {
				problems.add("he could not cast it again once it was ready");
			}
			finish(context, center, caster, inside, outside, creative);
			context.assertTrue(problems.isEmpty(), String.join("; ", problems));
			context.complete();
		});
	}

	/** The caster falls in his own Domain: it shatters at once, and lets go of everyone, shaken but not broken. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_gojo_early", tickLimit = DomainTimeline.VOID + HOLD + 60)
	public void endsWhenTheCasterFalls(TestContext context) {
		ServerWorld world = context.getWorld();
		rules(world, 0);
		Vec3d center = floor(context);
		ServerPlayerEntity caster = player(context, owner(context), center);
		caster.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
		CowEntity cow = context.spawnEntity(EntityType.COW, relative(context, center.add(5.0, 0.0, 0.0)));
		context.assertTrue(DomainManager.cast(caster), "the owner could not cast it");
		boolean[] caught = new boolean[1];
		context.runAtTick(DomainTimeline.CLOSE + SLACK, () -> {
			caught[0] = DomainManager.isHeld(cow);
			caster.kill();
		});
		context.runAtTick(DomainTimeline.CLOSE + SLACK + 3, () -> {
			boolean still = DomainManager.isHeld(cow);
			float health = cow.getHealth();
			finish(context, center, caster);
			context.assertTrue(caught[0], "the cow was not caught");
			context.assertTrue(!still, "the cow was still held after the caster fell");
			context.assertTrue(health > 1.0F, "a Domain cut short broke the cow");
			context.complete();
		});
	}

	private static void rules(ServerWorld world, int cooldownSeconds) {
		world.getGameRules().get(GojoRules.VOID_BREAKS).set(true, world.getServer());
		world.getGameRules().get(GojoRules.VOID_RADIUS).set(RADIUS, world.getServer());
		world.getGameRules().get(GojoRules.VOID_HOLD).set(HOLD_SECONDS, world.getServer());
		world.getGameRules().get(GojoRules.VOID_COOLDOWN).set(cooldownSeconds, world.getServer());
	}

	/**
	 * A disc of stone well clear of the test box and every other test's ground, open air over it, its chunks kept loaded
	 * and ticking (out here they would not be: what is in them would neither move nor be found); returns the point on it
	 * the Domain is cast from.
	 */
	private static Vec3d floor(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockPos center = context.getAbsolutePos(new BlockPos(4, 1, FAR));
		int r = RADIUS + 8;
		forceChunks(world, center, r, true);
		for (BlockPos pos : BlockPos.iterate(center.add(-r, -1, -r), center.add(r, 12, r))) {
			world.setBlockState(pos, pos.getY() < center.getY() ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
		}
		return Vec3d.ofBottomCenter(center);
	}

	private static void forceChunks(ServerWorld world, BlockPos center, int r, boolean forced) {
		for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
			for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
				world.setChunkForced(cx, cz, forced);
			}
		}
	}

	/** The end of a test: its players gone, its chunks let go, and no Domain left running. */
	private static void finish(TestContext context, Vec3d center, ServerPlayerEntity... players) {
		remove(context, players);
		DomainManager.collapseAll();
		forceChunks(context.getWorld(), BlockPos.ofFloored(center), RADIUS + 8, false);
	}

	private static Vec3d relative(TestContext context, Vec3d absolute) {
		return absolute.subtract(context.getAbsolute(Vec3d.ZERO));
	}

	/** What a crafting table makes for the player from the blindfold's recipe (or, if not, two planks: sticks). */
	private static ItemStack crafted(TestContext context, ServerPlayerEntity player, boolean blindfold) {
		CraftingScreenHandler table = new CraftingScreenHandler(1, player.getInventory(),
				ScreenHandlerContext.create(context.getWorld(), context.getAbsolutePos(BlockPos.ORIGIN)));
		ItemStack[] grid = blindfold
				? new ItemStack[] {ItemStack.EMPTY, new ItemStack(Items.ECHO_SHARD), ItemStack.EMPTY, new ItemStack(Items.BLACK_WOOL),
						new ItemStack(Items.NETHER_STAR), new ItemStack(Items.BLACK_WOOL), ItemStack.EMPTY, new ItemStack(Items.ECHO_SHARD),
						ItemStack.EMPTY}
				: new ItemStack[] {new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS),
						ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
		for (int i = 0; i < grid.length; i++) {
			table.getSlot(1 + i).setStack(grid[i]);
		}
		return table.getSlot(0).getStack();
	}

	/** A player connected to the test server under the given name, in survival, standing at {@code at} if given. */
	private static ServerPlayerEntity player(TestContext context, String name, Vec3d at) {
		ServerWorld world = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		if (at != null) {
			player.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		}
		return player;
	}

	private static void remove(TestContext context, ServerPlayerEntity... players) {
		for (ServerPlayerEntity player : players) {
			context.getWorld().getServer().getPlayerManager().remove(player);
		}
	}
}
