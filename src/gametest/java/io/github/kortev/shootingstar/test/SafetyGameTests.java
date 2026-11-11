package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import io.github.kortev.chainsawman.devil.TomatoSeedEntity;
import io.github.kortev.chainsawman.safety.NoticeBoards;
import io.github.kortev.chainsawman.safety.Safety;
import io.github.kortev.chainsawman.safety.SafetyLedger;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Public Safety (safety/): a village gets one notice board by its bell and never two (still one after a reload); the poster gives a
 * licence once (and again when it is lost); the licence's data (yen, devils hunted) is the player's and survives death and a reload; the
 * phone buys devil parts (yen up, parts gone, the first report's bonus once); a devil kill is counted for the player who made it; two
 * players have their own yen.
 */
public class SafetyGameTests implements FabricGameTest {
	/** Grass 27 blocks across at the test's own height, the air above it cleared. */
	private static void floor(TestContext context) {
		ServerWorld w = context.getWorld();
		for (int x = -13; x <= 13; x++) {
			for (int z = -13; z <= 13; z++) {
				w.setBlockState(context.getAbsolutePos(new BlockPos(x, 0, z)), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
				for (int y = 1; y <= 8; y++) {
					w.setBlockState(context.getAbsolutePos(new BlockPos(x, y, z)), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	private static Box area(TestContext context) {
		return new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(16.0, 10.0, 16.0);
	}

	private static int count(TestContext context, Block block) {
		int n = 0;
		for (BlockPos p : BlockPos.iterate(context.getAbsolutePos(new BlockPos(-13, 1, -13)), context.getAbsolutePos(new BlockPos(13, 8, 13)))) {
			if (context.getWorld().getBlockState(p).isOf(block)) {
				n++;
			}
		}
		return n;
	}

	private static int licences(ServerPlayerEntity p) {
		int n = 0;
		for (int i = 0; i < p.getInventory().size(); i++) {
			if (p.getInventory().getStack(i).isOf(Safety.LICENCE)) {
				n += p.getInventory().getStack(i).getCount();
			}
		}
		return n;
	}

	private static void clear(TestContext context) {
		for (Entity e : context.getWorld().getEntitiesByClass(Entity.class, area(context),
				e -> e instanceof TomatoDevilEntity || e instanceof TomatoSeedEntity || e instanceof HuskEntity || e instanceof ItemEntity || e instanceof VillagerEntity)) {
			e.discard();
		}
	}

	/** A bell with a villager by it gets one board when a player comes near, never a second, and the world remembers it through a reload. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "safety_board", tickLimit = 160)
	public void a_village_gets_one_board(TestContext context) {
		floor(context);
		clear(context);
		ServerWorld w = context.getWorld();
		MinecraftServer server = w.getServer();
		BlockPos bell = context.getAbsolutePos(new BlockPos(0, 1, 0));
		String dim = w.getRegistryKey().getValue().toString();
		// (A world kept from an earlier run may remember this spot.)
		SafetyLedger ledger = SafetyLedger.of(server);
		ledger.bells.getOrDefault(dim, new java.util.HashSet<>()).removeIf(l -> BlockPos.fromLong(l).getSquaredDistance(bell) < 64 * 64);
		w.setBlockState(bell, Blocks.BELL.getDefaultState());
		VillagerEntity v = new VillagerEntity(EntityType.VILLAGER, w);
		Vec3d va = Vec3d.ofBottomCenter(bell.add(-8, 0, -8));
		v.refreshPositionAndAngles(va.x, va.y, va.z, 0.0F, 0.0F);
		v.setAiDisabled(true);
		w.spawnEntity(v);
		ServerPlayerEntity p = ForeverGameTests.player(context, "walker");
		context.runAtTick(NoticeBoards.EVERY * 2 + 5, () -> {
			try {
				context.assertTrue(count(context, Safety.POSTER) == 1, "posters by the bell: " + count(context, Safety.POSTER));
				context.assertTrue(count(context, Safety.PHONE) == 1, "phones by the bell: " + count(context, Safety.PHONE));
				context.assertTrue(count(context, Blocks.SPRUCE_PLANKS) == NoticeBoards.WIDTH * 2, "the board is not whole");
				NoticeBoards.lookRound(w, bell);
				NoticeBoards.lookRound(w, p.getBlockPos());
				context.assertTrue(count(context, Safety.POSTER) == 1, "a second board went up");
				var nbt = SafetyLedger.of(server).writeNbt(new net.minecraft.nbt.NbtCompound(), server.getRegistryManager());
				SafetyLedger read = SafetyLedger.fromNbt(nbt, server.getRegistryManager());
				context.assertTrue(read.doneNear(dim, bell, 2), "the board is forgotten after a reload");
				// Broken, it is not put up again.
				for (BlockPos q : BlockPos.iterate(bell.add(-8, 0, -8), bell.add(8, 4, 8))) {
					if (w.getBlockState(q).isOf(Safety.POSTER) || w.getBlockState(q).isOf(Safety.PHONE)) {
						w.breakBlock(q.toImmutable(), false);
					}
				}
				NoticeBoards.lookRound(w, bell);
				context.assertTrue(count(context, Safety.POSTER) == 0, "a broken board was put up again");
			} finally {
				ForeverGameTests.leave(context, p);
				clear(context);
			}
			context.complete();
		});
	}

	/** The poster gives a licence to one who has none, never a second, and a new one when it is lost. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "safety_poster", tickLimit = 40)
	public void the_poster_gives_a_licence_once(TestContext context) {
		BlockPos at = context.getAbsolutePos(new BlockPos(0, 1, 0));
		context.getWorld().setBlockState(at, Safety.POSTER.getDefaultState());
		ServerPlayerEntity p = ForeverGameTests.player(context, "reader");
		try {
			p.getInventory().clear();
			Safety.poster(p, at);
			context.assertTrue(licences(p) == 1, "the poster gave " + licences(p) + " licences");
			Safety.poster(p, at);
			context.assertTrue(licences(p) == 1, "the poster gave a second licence");
			p.getInventory().clear();
			Safety.poster(p, at);
			context.assertTrue(licences(p) == 1, "a lost licence was not given again");
		} finally {
			ForeverGameTests.leave(context, p);
			context.getWorld().setBlockState(at, Blocks.AIR.getDefaultState());
		}
		context.complete();
	}

	/** Yen and the devils hunted are the player's: kept through a death and through leaving and coming back. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "safety_saved", tickLimit = 40)
	public void licence_data_survives_death_and_reload(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		GameProfile profile = new GameProfile(UUID.randomUUID(), "keeper");
		ServerPlayerEntity p = ServerProofGameTests.join(context, profile, 0.0);
		var tomato = Registries.ENTITY_TYPE.getId(Devils.TOMATO_DEVIL);
		p.setAttached(Safety.YEN, 5000L);
		Safety.count(p, tomato);
		Safety.count(p, tomato);
		ServerPlayerEntity back = server.getPlayerManager().respawnPlayer(p, false, Entity.RemovalReason.KILLED);
		context.assertTrue(Safety.yen(back) == 5000L, "yen lost in death: " + Safety.yen(back));
		context.assertTrue(Safety.hunted(back, tomato) == 2, "devils hunted lost in death: " + Safety.hunted(back, tomato));
		ServerProofGameTests.leave(back);
		ServerPlayerEntity again = ServerProofGameTests.join(context, profile, 0.0);
		try {
			context.assertTrue(Safety.yen(again) == 5000L, "yen lost leaving: " + Safety.yen(again));
			context.assertTrue(Safety.hunted(again, tomato) == 2, "devils hunted lost leaving: " + Safety.hunted(again, tomato));
		} finally {
			ServerProofGameTests.leave(again);
		}
		context.complete();
	}

	/** The phone sells every carcass for 3,000 yen each, the first time with 10,000 more; nothing to sell changes nothing. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "safety_phone", tickLimit = 40)
	public void the_phone_buys_parts(TestContext context) {
		BlockPos at = context.getAbsolutePos(new BlockPos(0, 1, 0));
		context.getWorld().setBlockState(at, Safety.PHONE.getDefaultState());
		ServerPlayerEntity p = ForeverGameTests.player(context, "seller");
		try {
			p.getInventory().clear();
			p.getInventory().setStack(0, new ItemStack(Devils.TOMATO_CARCASS, 2));
			p.getInventory().setStack(5, new ItemStack(Devils.TOMATO_CARCASS, 1));
			Safety.phone(p, at);
			context.assertTrue(licences(p) == 1, "the phone gave no licence first");
			context.assertTrue(p.getInventory().count(Devils.TOMATO_CARCASS) == 0, "carcasses are left");
			long first = 3 * Safety.TOMATO_PRICE + Safety.FIRST_BONUS;
			context.assertTrue(Safety.yen(p) == first, "the first sale paid " + Safety.yen(p) + ", not " + first);
			p.getInventory().setStack(0, new ItemStack(Devils.TOMATO_CARCASS, 1));
			Safety.phone(p, at);
			context.assertTrue(Safety.yen(p) == first + Safety.TOMATO_PRICE, "the bonus came twice: " + Safety.yen(p));
			Safety.phone(p, at);
			context.assertTrue(Safety.yen(p) == first + Safety.TOMATO_PRICE, "nothing sold changed the yen");
			context.assertTrue(licences(p) == 1, "the phone gave a second licence");
		} finally {
			ForeverGameTests.leave(context, p);
			context.getWorld().setBlockState(at, Blocks.AIR.getDefaultState());
		}
		context.complete();
	}

	/** A Tomato killed by a player is one more for that player's count; a husk is no devil. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "safety_kill", tickLimit = 60)
	public void a_devil_kill_is_counted(TestContext context) {
		floor(context);
		clear(context);
		ServerWorld w = context.getWorld();
		ServerPlayerEntity p = ForeverGameTests.player(context, "hunter");
		var tomato = Registries.ENTITY_TYPE.getId(Devils.TOMATO_DEVIL);
		try {
			TomatoDevilEntity d = Devils.TOMATO_DEVIL.create(w);
			Vec3d at = context.getAbsolute(new Vec3d(0.5, 1.0, 0.5));
			d.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
			w.spawnEntity(d);
			HuskEntity h = new HuskEntity(EntityType.HUSK, w);
			h.refreshPositionAndAngles(at.x + 4, at.y, at.z, 0.0F, 0.0F);
			w.spawnEntity(h);
			d.damage(p.getDamageSources().playerAttack(p), 1000.0F);
			h.damage(p.getDamageSources().playerAttack(p), 1000.0F);
			context.assertTrue(d.isDead(), "the Tomato did not die");
			context.assertTrue(Safety.hunted(p, tomato) == 1, "the kill counted " + Safety.hunted(p, tomato));
			context.assertTrue(Safety.hunted(p).size() == 1, "something that is no devil was counted: " + Safety.hunted(p));
		} finally {
			ForeverGameTests.leave(context, p);
		}
		context.runAtTick(40, () -> {
			clear(context);
			context.complete();
		});
	}

	/** Two players selling at the same phone: each has their own yen and their own first-report bonus. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "safety_two", tickLimit = 40)
	public void two_players_have_their_own_yen(TestContext context) {
		BlockPos at = context.getAbsolutePos(new BlockPos(0, 1, 0));
		context.getWorld().setBlockState(at, Safety.PHONE.getDefaultState());
		ServerPlayerEntity a = ForeverGameTests.player(context, "first");
		ServerPlayerEntity b = ForeverGameTests.player(context, "second");
		try {
			a.getInventory().clear();
			b.getInventory().clear();
			a.getInventory().setStack(0, new ItemStack(Devils.TOMATO_CARCASS, 1));
			Safety.phone(a, at);
			context.assertTrue(Safety.yen(a) == Safety.TOMATO_PRICE + Safety.FIRST_BONUS, "the first player has " + Safety.yen(a));
			context.assertTrue(Safety.yen(b) == 0L, "the second player was paid for the first's carcass: " + Safety.yen(b));
			b.getInventory().setStack(0, new ItemStack(Devils.TOMATO_CARCASS, 2));
			Safety.phone(b, at);
			context.assertTrue(Safety.yen(b) == 2 * Safety.TOMATO_PRICE + Safety.FIRST_BONUS, "the second player has " + Safety.yen(b));
			context.assertTrue(Safety.yen(a) == Safety.TOMATO_PRICE + Safety.FIRST_BONUS, "the first player's yen moved: " + Safety.yen(a));
		} finally {
			ForeverGameTests.leave(context, a);
			ForeverGameTests.leave(context, b);
			context.getWorld().setBlockState(at, Blocks.AIR.getDefaultState());
		}
		context.complete();
	}
}
