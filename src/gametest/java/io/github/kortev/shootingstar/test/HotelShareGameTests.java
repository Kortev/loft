package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.form.Fallen;
import io.github.kortev.chainsawman.hotel.EternityDevilEntity;
import io.github.kortev.chainsawman.hotel.HomeBlockEntity;
import io.github.kortev.chainsawman.hotel.Hotel;
import io.github.kortev.chainsawman.hotel.HotelLedger;
import io.github.kortev.chainsawman.hotel.HotelRules;
import io.github.kortev.chainsawman.hotel.Squad;
import io.github.kortev.chainsawman.hotel.SquadMemberEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameRules;

/**
 * The hotel made safe on a server with other people (package 0.7b): everyone who fought the Eternity Devil gets its whole drop in their pack
 * and the squad's pay, alive or dead, here or away (paid when they are in the game, alive and up); an idle player inside gets nothing; with
 * nobody who fought, the old glowing pile; and with oneContractPerDevil one contract in the whole world, whichever hotel gives it.
 */
public class HotelShareGameTests implements FabricGameTest {
	/** When the devil's loot is given after the blow that kills it: its collapse, and a little. */
	private static final int AFTER_COLLAPSE = EternityDevilEntity.DEATH_TICKS + 20;

	/** Two fighters each get the whole drop in their pack (one with a full pack gets it at their feet); none is left on the floor. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_share_each", tickLimit = 900 + HotelGameTests.READY_WAIT)
	public void each_fighter_gets_the_whole_drop(TestContext context) {
		HomeBlockEntity h = HotelGameTests.hotel(context, 0, 60);
		ServerPlayerEntity a = walker(context, h, "share_a", 26.0);
		ServerPlayerEntity b = walker(context, h, "share_b", 24.0);
		EternityDevilEntity[] d = new EternityDevilEntity[1];
		HotelGameTests.ready(context, h, () -> {
			HotelGameTests.later(context, 15, () -> {
				noSquad(context, h);
				d[0] = fightAt(context, h);
			});
			HotelGameTests.later(context, 110, () -> {
				hit(a, d[0]);
				hit(b, d[0]);
				// b's pack is full: theirs lands at their feet.
				for (int s = 0; s < b.getInventory().main.size(); s++) {
					b.getInventory().main.set(s, new ItemStack(Items.COBBLESTONE, 64));
				}
				slay(d[0], a);
			});
			HotelGameTests.later(context, 110 + AFTER_COLLAPSE, () -> {
				context.assertTrue(pack(a, ChainsawMan.ETERNITY_FLESH) == Hotel.FLESH_DROP && pack(a, ChainsawMan.ETERNITY_BLOOD) == Hotel.BLOOD_DROP,
						"the first fighter's pack has " + pack(a, ChainsawMan.ETERNITY_FLESH) + " flesh and " + pack(a, ChainsawMan.ETERNITY_BLOOD) + " blood");
				int nearB = near(context, b, ChainsawMan.ETERNITY_FLESH);
				context.assertTrue(nearB == Hotel.FLESH_DROP && near(context, b, ChainsawMan.ETERNITY_BLOOD) == Hotel.BLOOD_DROP,
						"the fighter with a full pack did not get the whole drop at their feet: " + nearB + " flesh");
				context.assertTrue(floor(context, ChainsawMan.ETERNITY_FLESH) == Hotel.FLESH_DROP, "more lies on the floor than the full pack's share: "
						+ floor(context, ChainsawMan.ETERNITY_FLESH) + " flesh");
				tidy(context, h, a, b);
				context.complete();
			});
		});
	}

	/** A second player inside who never hit it gets no loot; the one who fought gets it; nothing lies on the floor. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_share_idle", tickLimit = 900 + HotelGameTests.READY_WAIT)
	public void an_idle_player_inside_gets_nothing(TestContext context) {
		HomeBlockEntity h = HotelGameTests.hotel(context, 0, 61);
		ServerPlayerEntity a = walker(context, h, "idle_fighter", 26.0);
		ServerPlayerEntity idle = walker(context, h, "idle_one", 24.0);
		EternityDevilEntity[] d = new EternityDevilEntity[1];
		HotelGameTests.ready(context, h, () -> {
			HotelGameTests.later(context, 15, () -> {
				noSquad(context, h);
				d[0] = fightAt(context, h);
			});
			HotelGameTests.later(context, 110, () -> {
				context.assertTrue(h.run.inside.contains(idle.getUuid()), "the idle player is not inside");
				hit(a, d[0]);
				slay(d[0], a);
			});
			HotelGameTests.later(context, 110 + AFTER_COLLAPSE, () -> {
				context.assertTrue(pack(idle, ChainsawMan.ETERNITY_FLESH) == 0 && pack(idle, ChainsawMan.ETERNITY_BLOOD) == 0, "the idle player got loot");
				context.assertTrue(pack(a, ChainsawMan.ETERNITY_FLESH) == Hotel.FLESH_DROP, "the fighter has " + pack(a, ChainsawMan.ETERNITY_FLESH) + " flesh");
				context.assertTrue(floor(context, ChainsawMan.ETERNITY_FLESH) == 0, "loot was left on the floor for the idle player to take");
				tidy(context, h, a, idle);
				context.complete();
			});
		});
	}

	/**
	 * A fighter who died is paid when they are back, whatever keepInventory says: one respawns with it off, one with it on, and each gets
	 * the whole drop once.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_share_dead", tickLimit = 900 + HotelGameTests.READY_WAIT)
	public void a_dead_fighter_is_paid_on_respawn(TestContext context) {
		HomeBlockEntity h = HotelGameTests.hotel(context, 0, 62);
		MinecraftServer server = context.getWorld().getServer();
		ServerPlayerEntity a = walker(context, h, "dead_keepless", 26.0);
		ServerPlayerEntity b = walker(context, h, "dead_keeper", 25.0);
		ServerPlayerEntity c = walker(context, h, "dead_survivor", 24.0);
		ServerPlayerEntity[] back = new ServerPlayerEntity[2];
		EternityDevilEntity[] d = new EternityDevilEntity[1];
		HotelGameTests.ready(context, h, () -> {
			HotelGameTests.later(context, 15, () -> {
				noSquad(context, h);
				d[0] = fightAt(context, h);
			});
			HotelGameTests.later(context, 110, () -> {
				hit(a, d[0]);
				hit(b, d[0]);
				hit(c, d[0]);
				for (ServerPlayerEntity dying : new ServerPlayerEntity[] {a, b}) {
					Fallen.fall(dying);
					Fallen.giveUp(dying);
				}
				context.assertTrue(a.isDead() && b.isDead(), "the two did not die");
				slay(d[0], c);
			});
			HotelGameTests.later(context, 110 + AFTER_COLLAPSE, () -> {
				context.assertTrue(pack(a, ChainsawMan.ETERNITY_FLESH) == 0 && pack(b, ChainsawMan.ETERNITY_FLESH) == 0, "the loot went into a dead player's pack");
				context.assertTrue(floor(context, ChainsawMan.ETERNITY_FLESH) == 0, "the loot was left on the floor");
				server.getGameRules().get(GameRules.KEEP_INVENTORY).set(false, server);
				back[0] = server.getPlayerManager().respawnPlayer(a, false, Entity.RemovalReason.KILLED);
				server.getGameRules().get(GameRules.KEEP_INVENTORY).set(true, server);
				back[1] = server.getPlayerManager().respawnPlayer(b, false, Entity.RemovalReason.KILLED);
			});
			HotelGameTests.later(context, 112 + AFTER_COLLAPSE, () -> {
				try {
					for (ServerPlayerEntity p : back) {
						context.assertTrue(pack(p, ChainsawMan.ETERNITY_FLESH) == Hotel.FLESH_DROP && pack(p, ChainsawMan.ETERNITY_BLOOD) == Hotel.BLOOD_DROP,
								p.getName().getString() + " respawned with " + pack(p, ChainsawMan.ETERNITY_FLESH) + " flesh and " + pack(p, ChainsawMan.ETERNITY_BLOOD) + " blood");
					}
					context.assertTrue(pack(c, ChainsawMan.ETERNITY_FLESH) == Hotel.FLESH_DROP, "the one who lived was not paid");
				} finally {
					server.getGameRules().get(GameRules.KEEP_INVENTORY).set(false, server);
					tidy(context, h, back[0], back[1], c);
				}
				context.complete();
			});
		});
	}

	/** A fighter who logged out before it died gets the whole drop when they join again. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_share_left", tickLimit = 900 + HotelGameTests.READY_WAIT)
	public void a_fighter_who_left_is_paid_on_joining(TestContext context) {
		HomeBlockEntity h = HotelGameTests.hotel(context, 0, 63);
		GameProfile profile = new GameProfile(UUID.randomUUID(), "left_fighter");
		ServerPlayerEntity a = ServerProofGameTests.join(context, profile, 0.0);
		HotelGameTests.put(a, h, 26.0, 29.0, 6.5, 90.0F, 0.0F);
		armed(a);
		context.getWorld().setTimeOfDay(14000);
		ServerPlayerEntity b = walker(context, h, "stayed_fighter", 24.0);
		ServerPlayerEntity[] back = new ServerPlayerEntity[1];
		EternityDevilEntity[] d = new EternityDevilEntity[1];
		HotelGameTests.ready(context, h, () -> {
			HotelGameTests.later(context, 15, () -> {
				noSquad(context, h);
				d[0] = fightAt(context, h);
			});
			HotelGameTests.later(context, 110, () -> {
				hit(a, d[0]);
				hit(b, d[0]);
				ServerProofGameTests.leave(a);
				slay(d[0], b);
			});
			HotelGameTests.later(context, 110 + AFTER_COLLAPSE, () -> {
				context.assertTrue(floor(context, ChainsawMan.ETERNITY_FLESH) == 0, "the loot was left on the floor");
				back[0] = ServerProofGameTests.join(context, profile, 0.0);
			});
			HotelGameTests.later(context, 112 + AFTER_COLLAPSE, () -> {
				try {
					context.assertTrue(pack(back[0], ChainsawMan.ETERNITY_FLESH) == Hotel.FLESH_DROP && pack(back[0], ChainsawMan.ETERNITY_BLOOD) == Hotel.BLOOD_DROP,
							"back, the one who left has " + pack(back[0], ChainsawMan.ETERNITY_FLESH) + " flesh");
					context.assertTrue(pack(b, ChainsawMan.ETERNITY_FLESH) == Hotel.FLESH_DROP, "the one who stayed was not paid");
				} finally {
					tidy(context, h, back[0], b);
				}
				context.complete();
			});
		});
	}

	/**
	 * The squad's pay (its numbers and its line kept) goes to everyone who fought and nobody else: a fighter who is down is paid once they
	 * are up, an idle player inside is not.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_share_rescue", tickLimit = 900 + HotelGameTests.READY_WAIT)
	public void rescue_pay_goes_to_who_fought(TestContext context) {
		HomeBlockEntity h = HotelGameTests.hotel(context, 0, 64);
		ServerPlayerEntity a = walker(context, h, "rescue_fighter", 20.5);
		ServerPlayerEntity down = walker(context, h, "rescue_down", 22.0);
		ServerPlayerEntity idle = walker(context, h, "rescue_idle", 24.0);
		EternityDevilEntity[] d = new EternityDevilEntity[1];
		int[] xp = new int[3];
		HotelGameTests.ready(context, h, () -> {
			HotelGameTests.later(context, 15, () -> {
				// Two of the hunters are fed (rescued); the mad one is sent away.
				int fed = 0;
				for (SquadMemberEntity m : Squad.members(context.getWorld(), h)) {
					if (m.isMad() || fed == 2) {
						m.discard();
					} else {
						a.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.BREAD, 4));
						m.interactMob(a, net.minecraft.util.Hand.MAIN_HAND);
						fed++;
					}
				}
				xp[0] = a.totalExperience;
				xp[1] = down.totalExperience;
				xp[2] = idle.totalExperience;
				d[0] = fightAt(context, h);
			});
			HotelGameTests.later(context, 110, () -> {
				hit(a, d[0]);
				hit(down, d[0]);
				Fallen.fall(down);
				context.assertTrue(Fallen.is(down), "the second fighter is not down");
				slay(d[0], a);
			});
			HotelGameTests.later(context, 115, () -> {
				context.assertTrue(h.run.saved == 2, "the hotel counted " + h.run.saved + " saved");
				context.assertTrue(a.totalExperience - xp[0] == 2 * Squad.REWARD_XP && pack(a, Items.GOLD_INGOT) == 2 * Squad.REWARD_GOLD,
						"the fighter got " + (a.totalExperience - xp[0]) + " experience and " + pack(a, Items.GOLD_INGOT) + " gold");
				context.assertTrue(idle.totalExperience == xp[2] && pack(idle, Items.GOLD_INGOT) == 0, "the idle player was paid");
				context.assertTrue(down.totalExperience == xp[1] && pack(down, Items.GOLD_INGOT) == 0, "the fighter was paid while down");
				Fallen.up(down, null);
			});
			HotelGameTests.later(context, 118, () -> {
				context.assertTrue(down.totalExperience - xp[1] == 2 * Squad.REWARD_XP && pack(down, Items.GOLD_INGOT) == 2 * Squad.REWARD_GOLD,
						"up again, the fighter got " + (down.totalExperience - xp[1]) + " experience and " + pack(down, Items.GOLD_INGOT) + " gold");
				tidy(context, h, a, down, idle);
				context.complete();
			});
		});
	}

	/** Killed with nobody having fought it (a /kill): the old glowing pile on the floor, and nothing in anyone's pack. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_share_pile", tickLimit = 900 + HotelGameTests.READY_WAIT)
	public void no_fighters_leaves_the_pile(TestContext context) {
		HomeBlockEntity h = HotelGameTests.hotel(context, 0, 65);
		ServerPlayerEntity a = walker(context, h, "pile_watcher", 26.0);
		EternityDevilEntity[] d = new EternityDevilEntity[1];
		HotelGameTests.ready(context, h, () -> {
			HotelGameTests.later(context, 15, () -> {
				noSquad(context, h);
				d[0] = fightAt(context, h);
			});
			HotelGameTests.later(context, 110, () -> {
				d[0].kill();
				context.assertTrue(d[0].isDead(), "it did not die");
			});
			HotelGameTests.later(context, 110 + AFTER_COLLAPSE, () -> {
				context.assertTrue(floor(context, ChainsawMan.ETERNITY_FLESH) == Hotel.FLESH_DROP && floor(context, ChainsawMan.ETERNITY_BLOOD) == Hotel.BLOOD_DROP,
						"the pile is " + floor(context, ChainsawMan.ETERNITY_FLESH) + " flesh and " + floor(context, ChainsawMan.ETERNITY_BLOOD) + " blood");
				context.assertTrue(pack(a, ChainsawMan.ETERNITY_FLESH) == 0, "a player who did not fight got loot");
				tidy(context, h, a);
				context.complete();
			});
		});
	}

	/** With oneContractPerDevil on, a player who took the deal in one hotel means a second hotel's devil gives no second contract. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_share_two_hotels", tickLimit = 900 + HotelGameTests.READY_WAIT)
	public void second_hotel_gives_no_second_contract(TestContext context) {
		HomeBlockEntity first = HotelGameTests.hotel(context, 0, 66);
		HomeBlockEntity second = HotelGameTests.hotel(context, 0, 67);
		context.getWorld().getGameRules().get(HotelRules.ONE_CONTRACT_PER_DEVIL).set(true, context.getWorld().getServer());
		ServerPlayerEntity a = walker(context, first, "first_taker", 26.0);
		ServerPlayerEntity b = walker(context, second, "second_taker", 26.0);
		EternityDevilEntity[] d = new EternityDevilEntity[2];
		// (Both hotels awake, whichever is ready last.)
		int[] up = new int[1];
		Runnable both = () -> {
			if (++up[0] < 2) {
				return;
			}
			HotelGameTests.later(context, 15, () -> {
				noSquad(context, first);
				noSquad(context, second);
				d[0] = fightAt(context, first);
				d[1] = fightAt(context, second);
			});
			HotelGameTests.later(context, 110, () -> {
				toTheLine(a, d[0]);
				toTheLine(b, d[1]);
			});
			HotelGameTests.later(context, 116, () -> {
				context.assertTrue(first.run.beg != null && second.run.beg != null && second.run.beg.players.contains(b.getUuid()), "both devils do not beg to their player");
				Hotel.answer(a, true);
				context.assertTrue(Contracts.has(a, Contract.ETERNITY), "the first hotel gave no contract");
				Hotel.answer(b, true);
				context.assertTrue(!Contracts.has(b, Contract.ETERNITY), "a second hotel gave a second contract");
			});
			HotelGameTests.later(context, 124, () -> {
				tidy(context, first, a);
				tidy(context, second, b);
				context.complete();
			});
		};
		HotelGameTests.ready(context, first, both);
		HotelGameTests.ready(context, second, both);
	}

	/** Pay owed to someone away is written with the world and read back whole (as a restart does), and paid when they join. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_share_save", tickLimit = 40)
	public void owed_pay_survives_a_save(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		GameProfile profile = new GameProfile(UUID.randomUUID(), "owed_away");
		HotelLedger.owe(server, profile.getId(), java.util.List.of(new ItemStack(ChainsawMan.ETERNITY_FLESH, Hotel.FLESH_DROP), new ItemStack(ChainsawMan.ETERNITY_BLOOD, Hotel.BLOOD_DROP)),
				0, "message.chainsawman.hotel.loot", -1);
		Squad.reward(server, profile.getId(), 2);
		HotelLedger ledger = HotelLedger.of(server);
		context.assertTrue(ledger.owed.containsKey(profile.getId()) && ledger.isDirty(), "nothing is owed to the one away, or it would not be saved");
		// Written and read back, as the world is over a restart; the server's ledger is the one read back from then on.
		net.minecraft.nbt.NbtCompound nbt = ledger.writeNbt(new net.minecraft.nbt.NbtCompound(), server.getRegistryManager());
		HotelLedger read = HotelLedger.fromNbt(nbt, server.getRegistryManager());
		HotelLedger.Owed o = read.owed.get(profile.getId());
		context.assertTrue(o != null && o.xp == 2 * Squad.REWARD_XP && o.items.size() == 3 && o.keys.size() == 2, "read back, the pay owed is " + (o == null ? "gone" : o.xp + " experience, " + o.items.size() + " stacks"));
		ledger.owed.clear();
		ledger.owed.putAll(read.owed);
		ServerPlayerEntity[] p = new ServerPlayerEntity[1];
		context.runAtTick(2, () -> p[0] = ServerProofGameTests.join(context, profile, 0.0));
		context.runAtTick(4, () -> {
			try {
				context.assertTrue(pack(p[0], ChainsawMan.ETERNITY_FLESH) == Hotel.FLESH_DROP && pack(p[0], ChainsawMan.ETERNITY_BLOOD) == Hotel.BLOOD_DROP
						&& pack(p[0], Items.GOLD_INGOT) == 2 * Squad.REWARD_GOLD && p[0].totalExperience == 2 * Squad.REWARD_XP,
						"joined, they have " + pack(p[0], ChainsawMan.ETERNITY_FLESH) + " flesh, " + pack(p[0], Items.GOLD_INGOT) + " gold and " + p[0].totalExperience + " experience");
				context.assertTrue(!HotelLedger.of(server).owed.containsKey(profile.getId()), "paid, it is still owed");
			} finally {
				ServerProofGameTests.done(context, p[0]);
			}
			context.complete();
		});
	}

	/** A hotel saved with its own holder (before the world kept it) gives it to the world when it loads, unless the world has one already. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_share_old_holder", tickLimit = 60)
	public void old_hotel_holder_moves_to_the_world(TestContext context) {
		HomeBlockEntity h = HotelGameTests.hotel(context, 0, 68);
		MinecraftServer server = context.getWorld().getServer();
		UUID old = UUID.randomUUID();
		UUID later = UUID.randomUUID();
		context.assertTrue(Hotel.holder(context.getWorld()) == null, "someone held the contract before the test");
		h.read(saved(h, old), server.getRegistryManager());
		context.assertTrue(old.equals(h.holder), "the old save's holder was not read");
		context.runAtTick(5, () -> {
			context.assertTrue(old.equals(Hotel.holder(context.getWorld())) && h.holder == null, "the old holder did not move to the world: " + Hotel.holder(context.getWorld()));
			h.read(saved(h, later), server.getRegistryManager());
		});
		context.runAtTick(10, () -> {
			try {
				context.assertTrue(old.equals(Hotel.holder(context.getWorld())) && h.holder == null, "a second old holder took the world's: " + Hotel.holder(context.getWorld()));
			} finally {
				HotelLedger.setHolder(server, Contract.ETERNITY.id, null);
				HotelGameTests.forced(context, h.origin, false);
			}
			context.complete();
		});
	}

	/** The hotel's save as an older version wrote it: with its own Holder. */
	private static net.minecraft.nbt.NbtCompound saved(HomeBlockEntity h, UUID holder) {
		net.minecraft.nbt.NbtCompound nbt = h.createNbt(h.getWorld().getRegistryManager());
		nbt.putUuid("Holder", holder);
		return nbt;
	}

	// ---- helpers

	/** A hotel awake at night with {@code name} on its loop floor, at {@code x} along the corridor. */
	static ServerPlayerEntity walker(TestContext context, HomeBlockEntity h, String name, double x) {
		ServerPlayerEntity p = ForeverGameTests.player(context, name);
		armed(p);
		context.getWorld().setTimeOfDay(14000);
		HotelGameTests.put(p, h, x, 29.0, 6.5, 90.0F, 0.0F);
		return p;
	}

	/** (The hotel's mad hunter attacks anyone on sight: the players here can take it.) */
	static void armed(ServerPlayerEntity p) {
		p.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(2000.0);
		p.setHealth(2000.0F);
	}

	static void noSquad(TestContext context, HomeBlockEntity h) {
		for (SquadMemberEntity m : Squad.members(context.getWorld(), h)) {
			m.discard();
		}
		h.run.squad.clear();
	}

	static EternityDevilEntity fightAt(TestContext context, HomeBlockEntity h) {
		Hotel.startFight(context.getWorld(), h, context.getWorld().getTime());
		return h.run.devil != null && context.getWorld().getEntity(h.run.devil) instanceof EternityDevilEntity d ? d : null;
	}

	/** One blow of {@code by}'s at the devil. */
	static void hit(ServerPlayerEntity by, EternityDevilEntity d) {
		d.timeUntilRegen = 0;
		d.damage(by.getDamageSources().playerAttack(by), 5.0F);
	}

	/** To the beg line (15%): 60 health and a blow that would cut it to 30. */
	static void toTheLine(ServerPlayerEntity by, EternityDevilEntity d) {
		d.setHealth(60.0F);
		d.timeUntilRegen = 0;
		d.damage(by.getDamageSources().playerAttack(by), 30.0F);
	}

	/** Beaten with nobody taking the deal, its core cut by {@code by}: it dies. */
	static void slay(EternityDevilEntity d, ServerPlayerEntity by) {
		d.forgo();
		d.core().damage(by.getDamageSources().playerAttack(by), 1000.0F);
	}

	/** How many of {@code item} are in {@code p}'s pack. */
	static int pack(ServerPlayerEntity p, Item item) {
		int n = 0;
		for (int s = 0; s < p.getInventory().size(); s++) {
			if (p.getInventory().getStack(s).isOf(item)) {
				n += p.getInventory().getStack(s).getCount();
			}
		}
		return n;
	}

	/** How many of {@code item} lie on the ground in the test's world (all of it: the test's hotel is the only one about). */
	static int floor(TestContext context, Item item) {
		int n = 0;
		for (ItemEntity e : context.getWorld().getEntitiesByType(EntityType.ITEM, e -> e.getStack().isOf(item))) {
			n += e.getStack().getCount();
		}
		return n;
	}

	/** How many of {@code item} lie on the ground within 4 blocks of {@code p}. */
	static int near(TestContext context, ServerPlayerEntity p, Item item) {
		int n = 0;
		for (ItemEntity e : context.getWorld().getEntitiesByType(EntityType.ITEM, e -> e.getStack().isOf(item) && e.squaredDistanceTo(p) < 16.0)) {
			n += e.getStack().getCount();
		}
		return n;
	}

	static void tidy(TestContext context, HomeBlockEntity h, ServerPlayerEntity... players) {
		for (ServerPlayerEntity p : players) {
			if (p == null) {
				continue;
			}
			Contracts.clear(p);
			Fallen.up(p, null);
			if (context.getWorld().getServer().getPlayerManager().getPlayer(p.getUuid()) == p) {
				ForeverGameTests.leave(context, p);
			}
		}
		if (h.run.devil != null && context.getWorld().getEntity(h.run.devil) instanceof EternityDevilEntity d) {
			d.discard();
		}
		HotelGameTests.forced(context, h.origin, false);
		context.getWorld().setTimeOfDay(3000);
		context.getWorld().getGameRules().get(HotelRules.ONE_CONTRACT_PER_DEVIL).set(false, context.getWorld().getServer());
	}
}
