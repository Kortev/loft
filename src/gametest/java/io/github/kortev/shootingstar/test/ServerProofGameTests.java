package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.Owner;
import io.github.kortev.chainsawman.PochitaEntity;
import io.github.kortev.chainsawman.PochitaItem;
import io.github.kortev.chainsawman.PochitaPlace;
import io.github.kortev.chainsawman.form.Beam;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Fallen;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Frenzy;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.Hearts;
import io.github.kortev.chainsawman.form.Merge;
import io.github.kortev.chainsawman.form.TeamRules;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

/**
 * What's built, made safe on a server with other people (package 0.7a): Pochita belongs to his tamer and can't be lost for good, the
 * hearts are counted at the merge, logging out while down doesn't save you, a totem works, bleeding out is the killer's kill, the owner
 * is kept by player id, and the legend's roar is off unless a server wants it. Mock players stand in for the clients.
 *
 * <p>(A mock player has no connection that ticks it, so the game never runs its inventory's tick by itself: where that matters the test
 * runs it, every tick, as the game does for a real player.)
 */
public class ServerProofGameTests implements FabricGameTest {
	/** How long a player who has just joined can't be hurt (ticks). */
	private static final int JOINED = 61;

	// ------------------------------------------------------------------ Pochita is his tamer's

	/** A thief carrying someone else's Pochita can't merge with him; an old Pochita nobody tamed takes the first who holds him, and stays theirs over a relog. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_proof_thief", tickLimit = 20)
	public void pochita_thief_cannot_merge(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		Owner.set(server, null);
		GameProfile tamerProfile = new GameProfile(UUID.randomUUID(), "pochita_tamer");
		ServerPlayerEntity tamer = join(context, tamerProfile, 0.0);
		ServerPlayerEntity thief = player(context, "pochita_thief", 3.0);
		ServerPlayerEntity back = null;
		try {
			ItemStack his = new ItemStack(ChainsawMan.POCHITA);
			his.set(PochitaItem.TAMER, tamer.getUuid());
			thief.getInventory().insertStack(his);
			context.assertTrue(!PochitaItem.carried(thief), "someone else's Pochita counted as carried");
			context.assertTrue(!Merge.comes(thief), "the thief could merge with someone else's Pochita");
			context.assertTrue(tamer.getUuid().equals(pochitaIn(thief).get(PochitaItem.TAMER)), "the thief holding him made him theirs");

			tamer.getInventory().insertStack(new ItemStack(ChainsawMan.POCHITA));
			context.assertTrue(PochitaItem.carried(tamer) && Merge.comes(tamer), "the first to hold an untamed Pochita couldn't merge with him");
			context.assertTrue(tamer.getUuid().equals(pochitaIn(tamer).get(PochitaItem.TAMER)), "an untamed Pochita didn't take the first who held him");

			leave(tamer);
			back = join(context, tamerProfile, 0.0);
			context.assertTrue(PochitaItem.carried(back), "after a relog their own Pochita wasn't theirs");
		} finally {
			done(context, tamer);
			done(context, thief);
			if (back != null) {
				done(context, back);
			}
		}
		context.complete();
	}

	/** In a stranger's inventory Pochita jumps out within a second, as his tamed self, still his tamer's. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_proof_jump_out", tickLimit = 40)
	public void pochita_jumps_out_of_a_strangers_inventory(TestContext context) {
		ServerPlayerEntity tamer = player(context, "jump_tamer", 0.0);
		ServerPlayerEntity stranger = player(context, "jump_stranger", 4.0);
		ItemStack his = new ItemStack(ChainsawMan.POCHITA);
		his.set(PochitaItem.TAMER, tamer.getUuid());
		stranger.getInventory().insertStack(his);
		for (int t = 1; t <= 20; t++) {
			context.runAtTick(t, () -> stranger.getInventory().updateItems());
		}
		context.runAtTick(21, () -> {
			List<PochitaEntity> out = context.getWorld().getEntitiesByClass(PochitaEntity.class, stranger.getBoundingBox().expand(30.0), p -> true);
			try {
				context.assertTrue(!stranger.getInventory().contains(new ItemStack(ChainsawMan.POCHITA)), "Pochita stayed in the stranger's inventory");
				context.assertTrue(out.size() == 1, out.size() + " Pochitas came out, not one");
				context.assertTrue(out.get(0).isTamed() && tamer.getUuid().equals(out.get(0).getOwnerUuid()), "he came out wild, or someone else's");
			} finally {
				out.forEach(PochitaEntity::discard);
				done(context, tamer);
				done(context, stranger);
			}
			context.complete();
		});
	}

	// ------------------------------------------------------------------ a lost Pochita goes back to a den

	/**
	 * A dropped Pochita never despawns from age and floats in lava unburnt (he was always fireproof, as netherite is); destroyed (a
	 * cactus here, as a blast would), he's lost, and the den gets him again.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_proof_item_destroyed", tickLimit = 20)
	public void pochita_item_burnt_goes_back_to_a_den(TestContext context) {
		ServerWorld world = context.getWorld();
		ItemEntity item = drop(context, 0.0);
		context.runAtTick(2, () -> {
			try {
				context.assertTrue(item.getItemAge() == -32768, "a dropped Pochita would despawn: age " + item.getItemAge());
				context.assertTrue(!item.damage(world.getDamageSources().lava(), 10.0F) && item.isAlive(), "lava burnt him");
				long before = PochitaPlace.lostAt(world.getServer(), null);
				item.damage(world.getDamageSources().cactus(), 10.0F);
				context.assertTrue(item.isRemoved(), "the cactus didn't destroy him");
				long at = PochitaPlace.lostAt(world.getServer(), null);
				context.assertTrue(at == world.getServer().getOverworld().getTime() && at != before, "destroyed, he wasn't lost to the den (lost at " + at + ")");
			} finally {
				item.discard();
			}
			context.complete();
		});
	}

	/** A dropped Pochita fallen into the void is lost, and the den gets him again. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_proof_item_void", tickLimit = 20)
	public void pochita_item_in_the_void_goes_back_to_a_den(TestContext context) {
		ServerWorld world = context.getWorld();
		ItemEntity item = drop(context, 0.0);
		long[] before = new long[1];
		context.runAtTick(1, () -> {
			before[0] = PochitaPlace.lostAt(world.getServer(), null);
			item.setPosition(item.getX(), world.getBottomY() - 80, item.getZ());
		});
		context.runAtTick(6, () -> {
			try {
				context.assertTrue(item.isRemoved(), "in the void he wasn't gone");
				long at = PochitaPlace.lostAt(world.getServer(), null);
				context.assertTrue(at >= 0 && at != before[0], "gone in the void, he wasn't lost to the den (lost at " + at + ")");
			} finally {
				item.discard();
			}
			context.complete();
		});
	}

	// ------------------------------------------------------------------ the hearts are counted

	/** With the one heart taken (by a hybrid in the game, or away), a second player dying with their own Pochita goes down, no merge; with two hearts they would merge. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_proof_hearts_full", tickLimit = JOINED + 20)
	public void second_merge_refused_when_hearts_are_full(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		Owner.set(server, null);
		ServerPlayerEntity first = player(context, "first_heart", 0.0);
		ServerPlayerEntity second = player(context, "second_heart", 3.0);
		first.setAttached(Heart.MERGED, true);
		second.getInventory().insertStack(new ItemStack(ChainsawMan.POCHITA));
		context.runAtTick(JOINED, () -> {
			try {
				context.assertTrue(PochitaItem.carried(second), "the second didn't carry their Pochita");
				context.assertTrue(server.getGameRules().getInt(TeamRules.CHAINSAW_HEARTS) == 1 && Hearts.full(server), "the one heart wasn't counted as taken");
				context.assertTrue(!Merge.comes(second), "a second player could merge with the heart taken");
				// Away, the hybrid still holds it.
				Hearts.update(server);
				leave(first);
				context.assertTrue(!Merge.comes(second), "with the hybrid logged out, a second player could merge");
				server.getGameRules().get(TeamRules.CHAINSAW_HEARTS).set(2, server);
				context.assertTrue(Merge.comes(second), "with two hearts, the second couldn't merge");
				server.getGameRules().get(TeamRules.CHAINSAW_HEARTS).set(1, server);
				second.damage(second.getDamageSources().generic(), 1000.0F);
				context.assertTrue(!Merge.is(second) && Fallen.is(second), "the second's death didn't go on as any other (merging " + Merge.is(second) + ")");
			} finally {
				server.getGameRules().get(TeamRules.CHAINSAW_HEARTS).set(1, server);
				done(context, first);
				done(context, second);
			}
			context.complete();
		});
	}

	/** When the last heart is taken, every other tamed Pochita turns wild: one about in the world, and one in someone's pack (he jumps out). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_proof_go_wild", tickLimit = JOINED + 50)
	public void other_pochitas_go_wild_after_the_merge(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		Owner.set(server, null);
		ServerPlayerEntity merger = player(context, "wild_merger", 0.0);
		ServerPlayerEntity walker = player(context, "wild_walker", 6.0);
		ServerPlayerEntity holder = player(context, "wild_holder", -6.0);
		merger.getInventory().insertStack(new ItemStack(ChainsawMan.POCHITA));
		ItemStack held = new ItemStack(ChainsawMan.POCHITA);
		held.set(PochitaItem.TAMER, holder.getUuid());
		holder.getInventory().insertStack(held);
		PochitaEntity about = new PochitaEntity(ChainsawMan.POCHITA_ENTITY, context.getWorld());
		Vec3d at = spot(context).add(6.0, 0.0, 2.0);
		about.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		about.setNoGravity(true);
		about.setOwner(walker);
		about.setTamed(true, true);
		context.getWorld().spawnEntity(about);
		for (int t = 1; t <= JOINED + 30; t++) {
			context.runAtTick(t, () -> holder.getInventory().updateItems());
		}
		context.runAtTick(JOINED - 5, () -> context.assertTrue(about.isTamed() && PochitaItem.carried(holder), "a Pochita went wild before anyone merged"));
		context.runAtTick(JOINED, () -> {
			merger.damage(merger.getDamageSources().generic(), 1000.0F);
			context.assertTrue(Merge.is(merger), "the merger didn't merge");
		});
		context.runAtTick(JOINED + 30, () -> {
			List<PochitaEntity> out = context.getWorld().getEntitiesByClass(PochitaEntity.class, holder.getBoundingBox().expand(40.0), p -> p != about);
			try {
				context.assertTrue(!about.isTamed() && about.getOwnerUuid() == null, "the tamed Pochita about the world didn't go wild");
				context.assertTrue(!holder.getInventory().contains(new ItemStack(ChainsawMan.POCHITA)), "the held Pochita stayed in the pack");
				context.assertTrue(out.size() == 1 && !out.get(0).isTamed(), "the held Pochita didn't come out wild (" + out.size() + " came out)");
			} finally {
				out.forEach(PochitaEntity::discard);
				about.discard();
				done(context, merger);
				done(context, walker);
				done(context, holder);
			}
			context.complete();
		});
	}

	// ------------------------------------------------------------------ logging out while down

	/**
	 * Logged out while down, the world's clock goes on: back before the time is up, still down with the time they were away gone from it
	 * (a hybrid too); back after it, a human dies on arrival.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_proof_logout_down", tickLimit = JOINED + 120)
	public void logging_out_down_keeps_the_timer(TestContext context) {
		GameProfile human = new GameProfile(UUID.randomUUID(), "logout_human");
		GameProfile hybrid = new GameProfile(UUID.randomUUID(), "logout_hybrid");
		ServerPlayerEntity[] p = {join(context, human, 0.0), join(context, hybrid, 3.0)};
		p[1].setAttached(Heart.MERGED, true);
		context.runAtTick(JOINED, () -> {
			p[0].damage(p[0].getDamageSources().generic(), 1000.0F);
			Downed.down(p[1]);
			context.assertTrue(Fallen.is(p[0]) && Downed.is(p[1]), "they didn't go down");
			leave(p[0]);
			leave(p[1]);
		});
		context.runAtTick(JOINED + 40, () -> {
			p[0] = join(context, human, 0.0);
			p[1] = join(context, hybrid, 3.0);
			int left = p[0].getAttachedOrElse(Fallen.LEFT, 0);
			int alone = p[1].getAttachedOrElse(Downed.LEFT, 0);
			context.assertTrue(Fallen.is(p[0]) && Downed.is(p[1]), "back, they weren't down");
			context.assertTrue(Math.abs(left - (Fallen.TIMER - 40)) <= 3, "back after 40 ticks away, the human had " + left + " of " + Fallen.TIMER);
			context.assertTrue(Math.abs(alone - (Downed.ALONE - 40)) <= 3, "back after 40 ticks away, the hybrid had " + alone + " of " + Downed.ALONE);
			// Now with less time left than they'll be away.
			p[0].setAttached(Fallen.LEFT, 20);
			leave(p[0]);
		});
		context.runAtTick(JOINED + 80, () -> p[0] = join(context, human, 0.0));
		context.runAtTick(JOINED + 84, () -> {
			try {
				context.assertTrue(p[0].isDead() && !Fallen.is(p[0]), "back after their time ran out, they were still alive");
			} finally {
				done(context, p[0]);
				done(context, p[1]);
			}
			context.complete();
		});
	}

	// ------------------------------------------------------------------ the totem, and the killer

	/** A held totem pops (as in plain Minecraft) instead of a human going down, and instead of a hybrid going down. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_proof_totem", tickLimit = JOINED + 20)
	public void totem_pops_before_going_down(TestContext context) {
		ServerPlayerEntity human = player(context, "totem_human", 0.0);
		ServerPlayerEntity hybrid = player(context, "totem_hybrid", 3.0);
		hybrid.setAttached(Heart.MERGED, true);
		human.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		hybrid.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		context.runAtTick(JOINED, () -> {
			try {
				human.damage(human.getDamageSources().generic(), 1000.0F);
				hybrid.damage(hybrid.getDamageSources().generic(), 1000.0F);
				context.assertTrue(human.isAlive() && hybrid.isAlive(), "with a totem they died");
				context.assertTrue(human.getMainHandStack().isEmpty(), "the human's totem did not pop (down: " + Fallen.is(human) + ")");
				context.assertTrue(!Fallen.is(human), "the totem popped and the human went down as well");
				context.assertTrue(hybrid.getOffHandStack().isEmpty(), "the hybrid's totem did not pop (down: " + Downed.is(hybrid) + ")");
				context.assertTrue(!Downed.is(hybrid), "the totem popped and the hybrid went down as well");
				// Without one, down as before.
				// (Harder than the first blow, which the moment's invulnerability after a hit would take off it.)
				human.damage(human.getDamageSources().generic(), 5000.0F);
				context.assertTrue(Fallen.is(human), "without a totem they didn't go down");
			} finally {
				done(context, human);
				done(context, hybrid);
			}
			context.complete();
		});
	}

	/** Bleeding out (here, giving up) is the death the blow that put them down would have been: the killer named, and the kill theirs. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_proof_bleed_killer", tickLimit = JOINED + 20)
	public void bleed_out_names_the_killer(TestContext context) {
		ServerPlayerEntity victim = player(context, "bleed_victim", 0.0);
		ServerPlayerEntity killer = player(context, "bleed_killer", 2.0);
		MinecraftServer server = context.getWorld().getServer();
		boolean pvp = server.isPvpEnabled();
		listen();
		context.runAtTick(JOINED, () -> {
			// (The test server has PvP off; a real one has it on.)
			server.setPvpEnabled(true);
			try {
				int kills = killer.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAYER_KILLS));
				victim.damage(victim.getDamageSources().playerAttack(killer), 1000.0F);
				context.assertTrue(Fallen.is(victim), "the killer's blow didn't put them down");
				Fallen.giveUp(victim);
				context.assertTrue(victim.isDead(), "bleeding out, they didn't die");
				Text message = DEATHS.get(victim.getUuid());
				context.assertTrue(message != null && names(message, "bleed_killer"), "the death message didn't name the killer: " + (message == null ? "none" : message.getString()));
				context.assertTrue(killer.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAYER_KILLS)) == kills + 1, "the kill wasn't the killer's");
			} finally {
				server.setPvpEnabled(pvp);
				done(context, victim);
				done(context, killer);
			}
			context.complete();
		});
	}

	// ------------------------------------------------------------------ the owner, and the roar

	/**
	 * The owner is kept by player id: someone else with their name isn't them. A name kept from before (no id yet) turns into the id of
	 * the player with that name the next time they join.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_proof_owner_id", tickLimit = 20)
	public void owner_is_kept_by_id(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		ServerPlayerEntity keeper = player(context, "owner_keeper", 0.0);
		ServerPlayerEntity impostor = null;
		ServerPlayerEntity later = null;
		try {
			Owner.set(server, "owner_keeper");
			context.assertTrue(keeper.getUuid().equals(Owner.id()) && "owner_keeper".equals(Owner.get()), "the owner wasn't kept by their id (and shown by name)");
			leave(keeper);
			impostor = player(context, "owner_keeper", 2.0);
			context.assertTrue(!Owner.is(impostor), "someone else with the owner's name passed as the owner");
			context.assertTrue(keeper.getUuid().equals(Owner.id()), "the impostor joining changed the owner's id");
			done(context, impostor);

			// A name saved from before, with no id.
			Owner.set(server, "owner_later", null);
			context.assertTrue(Owner.id() == null, "a name from before had an id");
			later = player(context, "owner_later", 4.0);
			context.assertTrue(later.getUuid().equals(Owner.id()), "the name from before didn't turn into their id when they joined");
		} finally {
			Owner.set(server, null);
			done(context, keeper);
			if (later != null) {
				done(context, later);
			}
		}
		context.complete();
	}

	/** The legend's roar (the marker on everyone's screen) is off unless a server turns it on. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_proof_roar_off", tickLimit = 20)
	public void legend_roar_is_off_by_default(TestContext context) {
		context.assertTrue(!new GameRules().getBoolean(TeamRules.LEGEND_ROAR), "legendRoar was on by default");
		context.complete();
	}

	// ------------------------------------------------------------------ helpers

	/** Each player's death message as they died (the game forgets it once they are dead). */
	private static final java.util.Map<UUID, Text> DEATHS = new java.util.concurrent.ConcurrentHashMap<>();
	private static boolean listening;

	/** Starts keeping death messages (once): the last word on a death, after the mod's own, so only deaths that happen are kept. */
	private static synchronized void listen() {
		if (!listening) {
			listening = true;
			net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
				if (entity instanceof ServerPlayerEntity player) {
					DEATHS.put(player.getUuid(), player.getDamageTracker().getDeathMessage());
				}
				return true;
			});
		}
	}

	/** Whether {@code message} names {@code name} (in its text, or as one of what it is made of). */
	private static boolean names(Text message, String name) {
		if (message.getString().contains(name)) {
			return true;
		}
		if (message.getContent() instanceof TranslatableTextContent content) {
			for (Object arg : content.getArgs()) {
				if (arg instanceof Text text && text.getString().contains(name)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The first Pochita in {@code player}'s inventory (or nothing). */
	private static ItemStack pochitaIn(ServerPlayerEntity player) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			if (player.getInventory().getStack(i).isOf(ChainsawMan.POCHITA)) {
				return player.getInventory().getStack(i);
			}
		}
		return ItemStack.EMPTY;
	}

	/** A Pochita item dropped at the test's spot, floating there. */
	private static ItemEntity drop(TestContext context, double x) {
		Vec3d at = spot(context).add(x, 0.0, 2.0);
		ItemEntity item = new ItemEntity(context.getWorld(), at.x, at.y, at.z, new ItemStack(ChainsawMan.POCHITA));
		item.setNoGravity(true);
		item.setVelocity(Vec3d.ZERO);
		context.getWorld().spawnEntity(item);
		return item;
	}

	static Vec3d spot(TestContext context) {
		return context.getAbsolute(new Vec3d(0.5, 40.0, 0.5));
	}

	static ServerPlayerEntity player(TestContext context, String name, double x) {
		return join(context, new GameProfile(UUID.randomUUID(), name), x);
	}

	/** A player in the game (a stand-in with no real client behind it), joined as the game does it, loading what was saved for them. */
	static ServerPlayerEntity join(TestContext context, GameProfile profile, double x) {
		ServerWorld world = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		Vec3d at = spot(context).add(x, 0.0, 0.0);
		player.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		player.onTeleportationDone();
		player.setNoGravity(true);
		return player;
	}

	/** They leave the way a real player does (what is saved for them is saved; the game's disconnect events run). */
	static void leave(ServerPlayerEntity player) {
		player.networkHandler.onDisconnected(new net.minecraft.network.DisconnectionInfo(Text.literal("test")));
	}

	/** Up, out of everything, gone. */
	static void done(TestContext context, ServerPlayerEntity player) {
		Beam.end(player);
		Form.end(player);
		Frenzy.end(player);
		Downed.up(player);
		Fallen.up(player, null);
		player.removeAttached(Heart.MERGED);
		if (context.getWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) == player) {
			context.getWorld().getServer().getPlayerManager().remove(player);
		}
	}
}
