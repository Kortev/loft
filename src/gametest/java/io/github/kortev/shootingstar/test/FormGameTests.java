package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.PochitaEntity;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Frenzy;
import io.github.kortev.chainsawman.form.Grapple;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.Limbs;
import io.github.kortev.chainsawman.form.Merge;
import io.github.kortev.chainsawman.form.MergeTimeline;
import io.github.kortev.chainsawman.form.Saw;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Chainsaw Man's fight, on the server: a slash cuts what is in front (not what is behind) and heals; slashes come no
 * faster than they may; a kill heals a lot; grinding chews and heals; the form drains health, and running out of it
 * leaves you down, not dead; with Pochita nothing kills you, without him it does; someone giving you blood brings you
 * back up at their cost, and lying there long enough does too; the chain yanks what it catches to you and pulls you to
 * a block; the frenzy doubles the saws' damage and won't come again straight away.
 */
public class FormGameTests implements FabricGameTest {
	/** How long a player who has just joined can't be hurt (ticks), as the game keeps them. */
	private static final int JOINED = 61;

	/** A slash cuts the thing in front, not the thing behind, and the blood heals. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_slash", tickLimit = 20)
	public void slashCutsWhatIsInFront(TestContext context) {
		ServerPlayerEntity player = fighter(context, "slasher", 0.0);
		HuskEntity front = husk(context, 0.0, 2.0);
		HuskEntity behind = husk(context, 0.0, -2.0);
		player.setHealth(10.0F);
		try {
			context.assertTrue(Saw.slash(player), "the slash did not come");
			context.assertTrue(front.getHealth() < front.getMaxHealth() - 8.0F, "the husk in front was not cut: " + front.getHealth());
			context.assertTrue(behind.getHealth() == behind.getMaxHealth(), "the husk behind was cut");
			context.assertTrue(player.getHealth() == 10.0F + Saw.HIT_HEAL, "the hit did not heal: " + player.getHealth());
			context.assertTrue(!Saw.slash(player), "a second slash came at once");
		} finally {
			done(context, player);
			front.discard();
			behind.discard();
		}
		context.complete();
	}

	/** The kick: what's in front kicked, slowed (dazed), and no second kick at once. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_kick", tickLimit = 20)
	public void kickDazes(TestContext context) {
		ServerPlayerEntity player = fighter(context, "kicker", 0.0);
		HuskEntity front = husk(context, 0.0, 2.0);
		try {
			context.assertTrue(Saw.kick(player), "the kick did not come");
			context.assertTrue(front.getHealth() < front.getMaxHealth(), "the husk was not kicked");
			context.assertTrue(front.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS), "the husk was not dazed");
			context.assertTrue(!Saw.kick(player), "a second kick came at once");
		} finally {
			done(context, player);
			front.discard();
		}
		context.complete();
	}

	/** In the form the saws cut through blocks many times as fast as hands; out of it, as hands. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_dig", tickLimit = 20)
	public void sawsCutWalls(TestContext context) {
		ServerPlayerEntity player = fighter(context, "cutter", 0.0);
		try {
			float cutting = player.getBlockBreakingSpeed(net.minecraft.block.Blocks.STONE.getDefaultState());
			Form.end(player);
			float hands = player.getBlockBreakingSpeed(net.minecraft.block.Blocks.STONE.getDefaultState());
			context.assertTrue(cutting >= hands * Saw.CUTS * 0.99F, "the saws didn't cut faster: " + cutting + " against " + hands);
		} finally {
			done(context, player);
		}
		context.complete();
	}

	/** A kill drinks deep: the hit's heal and the kill's. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_kill", tickLimit = 20)
	public void killHeals(TestContext context) {
		ServerPlayerEntity player = fighter(context, "killer", 0.0);
		HuskEntity front = husk(context, 0.0, 2.0);
		front.setHealth(1.0F);
		player.setHealth(4.0F);
		try {
			Saw.slash(player);
			context.assertTrue(!front.isAlive(), "the husk lived");
			context.assertTrue(player.getHealth() == 4.0F + Saw.HIT_HEAL + Saw.KILL_HEAL, "the kill did not heal: " + player.getHealth());
		} finally {
			done(context, player);
			front.discard();
		}
		context.complete();
	}

	/** Held, the saws grind into what is in front every few ticks. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_grind", tickLimit = 40)
	public void grindChews(TestContext context) {
		ServerPlayerEntity player = fighter(context, "grinder", 0.0);
		HuskEntity front = husk(context, 0.0, 1.8);
		Saw.grind(player, true);
		context.waitAndRun(Saw.GRIND_EVERY * 5L, () -> {
			try {
				context.assertTrue(Saw.grinders().contains(player.getUuid()), "the grinding stopped");
				context.assertTrue(front.getHealth() < front.getMaxHealth() - Saw.GRIND_DAMAGE * 3.0F, "the grind did not chew: " + front.getHealth());
			} finally {
				Saw.grind(player, false);
				done(context, player);
				front.discard();
			}
			context.complete();
		});
	}

	/** The form costs a point of health every DRAIN_EVERY ticks; out of it, you go down, not die. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_drain", tickLimit = Saw.DRAIN_EVERY * 4 + 20)
	public void drainTakesYouDown(TestContext context) {
		ServerPlayerEntity player = fighter(context, "drained", 0.0);
		player.setHealth(3.0F);
		context.waitAndRun(Saw.DRAIN_EVERY * 4L + 5, () -> {
			try {
				context.assertTrue(player.isAlive(), "the drain killed");
				context.assertTrue(Downed.is(player), "out of blood, they were not down");
				context.assertTrue(!Form.on(player), "down, they were still in the form");
			} finally {
				done(context, player);
			}
			context.complete();
		});
	}

	/** With Pochita what would kill you leaves you down, and nothing more hurts you; without him you die. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_down", tickLimit = JOINED + 20)
	public void pochitaKeepsYouAlive(TestContext context) {
		ServerPlayerEntity carrier = fighter(context, "carrier", 0.0);
		ServerPlayerEntity unarmed = player(context, "no_pochita", 3.0);
		context.waitAndRun(JOINED, () -> {
			try {
				carrier.damage(carrier.getDamageSources().generic(), 1000.0F);
				context.assertTrue(carrier.isAlive() && carrier.getHealth() >= 1.0F, "with Pochita, they died");
				context.assertTrue(Downed.is(carrier), "with Pochita, they were not down");
				context.assertTrue(!carrier.damage(carrier.getDamageSources().generic(), 5.0F), "down, they were hurt");
				unarmed.damage(unarmed.getDamageSources().generic(), 1000.0F);
				context.assertTrue(unarmed.isDead(), "without Pochita, they lived");
			} finally {
				done(context, carrier);
				done(context, unarmed);
			}
			context.complete();
		});
	}

	/** Someone holding use on you, crouched, gives you their blood and brings you back up. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_feed", tickLimit = Downed.FEED + 40)
	public void bloodBringsYouBack(TestContext context) {
		ServerPlayerEntity down = fighter(context, "fallen", 0.0);
		ServerPlayerEntity friend = player(context, "friend", 1.2);
		Downed.down(down);
		friend.setSneaking(true);
		float before = friend.getHealth();
		for (int t = 0; t <= Downed.FEED + 8; t += 4) {
			context.runAtTick(t + 1, () -> Downed.feeding(friend, down));
		}
		context.runAtTick(Downed.FEED + 14, () -> {
			try {
				context.assertTrue(!Downed.is(down), "given blood, they were still down");
				context.assertTrue(down.getHealth() == Downed.GIVEN, "they came back with " + down.getHealth());
				context.assertTrue(friend.getHealth() <= before - Downed.GIVEN + 0.01F, "the friend gave nothing: " + friend.getHealth());
			} finally {
				done(context, down);
				done(context, friend);
			}
			context.complete();
		});
	}

	/** Left alone long enough, you come back by yourself. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_alone", tickLimit = 20)
	public void aloneYouComeBack(TestContext context) {
		ServerPlayerEntity down = fighter(context, "alone", 0.0);
		Downed.down(down);
		down.setAttached(Downed.LEFT, 3);
		context.waitAndRun(6, () -> {
			try {
				context.assertTrue(!Downed.is(down), "they were still down");
				context.assertTrue(down.getHealth() == Downed.GIVEN, "they came back with " + down.getHealth());
			} finally {
				done(context, down);
			}
			context.complete();
		});
	}

	/** The chain yanks what it catches to you and holds it; let go and fired at a block, it pulls you to it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_chain", tickLimit = 20)
	public void chainYanksAndPulls(TestContext context) {
		ServerPlayerEntity player = fighter(context, "chainer", 0.0);
		HuskEntity far = husk(context, 0.0, 5.0);
		player.setYaw(0.0F);
		player.setPitch(0.0F);
		try {
			HitResult hit = Grapple.fire(player);
			context.assertTrue(hit != null && hit.getType() == HitResult.Type.ENTITY, "the chain did not catch the husk: "
					+ (hit == null ? "nothing" : hit.getType() + " at " + hit.getPos()));
			context.assertTrue(far.getVelocity().z < -0.5, "the husk was not yanked: " + far.getVelocity());
			far.discard();
			BlockPos wall = BlockPos.ofFloored(player.getEyePos().add(0.0, 0.0, 4.0));
			context.getWorld().setBlockState(wall, Blocks.STONE.getDefaultState());
			context.assertTrue(Grapple.holding(player), "the husk was not held on the chain");
			Grapple.release(player);
			context.assertTrue(!Grapple.holding(player), "the chain did not let go");
			Grapple.ready(player);
			player.setVelocity(Vec3d.ZERO);
			player.setYaw(0.0F);
			HitResult block = Grapple.fire(player);
			context.assertTrue(block != null && block.getType() == HitResult.Type.BLOCK, "the chain did not catch the block");
			context.assertTrue(player.getVelocity().z > 0.5, "they were not pulled: " + player.getVelocity());
			context.getWorld().setBlockState(wall, Blocks.AIR.getDefaultState());
		} finally {
			done(context, player);
			far.discard();
		}
		context.complete();
	}

	/** In a frenzy the saws do double, and it won't come again straight away. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_frenzy", tickLimit = 20)
	public void frenzyDoubles(TestContext context) {
		ServerPlayerEntity player = fighter(context, "berserk", 0.0);
		HuskEntity front = husk(context, 0.0, 2.0);
		try {
			context.assertTrue(Frenzy.start(player), "the frenzy did not come");
			context.assertTrue(Frenzy.damage(player) == Frenzy.DAMAGE, "the frenzy did not double the saws");
			Saw.slash(player);
			context.assertTrue(!front.isAlive() || front.getHealth() < front.getMaxHealth() - 18.0F, "the frenzied slash was not doubled: " + front.getHealth());
			Frenzy.end(player);
			context.assertTrue(!Frenzy.start(player), "the frenzy came again at once");
		} finally {
			Frenzy.ready(player);
			done(context, player);
			front.discard();
		}
		context.complete();
	}

	/** Enough harm in the form takes an arm off, then the other; a kill grows one back; out of the form both are back. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_limbs", tickLimit = JOINED + 20)
	public void armsComeOffAndGrowBack(TestContext context) {
		ServerPlayerEntity player = fighter(context, "limbless", 0.0);
		context.waitAndRun(JOINED, () -> {
			HuskEntity front = husk(context, 0.0, 2.0);
			try {
				player.damage(player.getDamageSources().generic(), Limbs.EVERY + 0.5F);
				context.assertTrue(!Limbs.has(player, Limbs.RIGHT) && Limbs.has(player, Limbs.LEFT), "the right arm did not come off first");
				player.setHealth(player.getMaxHealth());
				player.timeUntilRegen = 0;
				player.damage(player.getDamageSources().generic(), Limbs.EVERY + 0.5F);
				context.assertTrue(!Limbs.has(player, Limbs.LEFT), "the left arm did not come off");
				front.setHealth(1.0F);
				context.assertTrue(Saw.slash(player), "with no arms, the head's saw did not slash");
				context.assertTrue(!front.isAlive(), "the head's saw did not kill");
				context.assertTrue(Limbs.has(player, Limbs.LEFT), "the kill did not grow an arm back");
				Form.end(player);
				context.assertTrue(Limbs.has(player, Limbs.RIGHT), "out of the form, the arm was still gone");
			} finally {
				done(context, player);
				front.discard();
			}
			context.complete();
		});
	}

	/** The cord pulled in the form turns you back: no fighting while the saws go in, and out of the form at the splash. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_back", tickLimit = io.github.kortev.chainsawman.form.FormTimeline.BACK_SPLASH + 20)
	public void cordTurnsYouBack(TestContext context) {
		ServerPlayerEntity player = player(context, "turning", 0.0);
		player.setAttached(Heart.MERGED, true);
		Form.start(player);
		Form.pullCord(player);
		try {
			context.assertTrue(Form.on(player) && Form.turning(player) && !Form.fighting(player), "pulling the cord in the form didn't start turning back");
		} catch (RuntimeException e) {
			done(context, player);
			throw e;
		}
		context.runAtTick(io.github.kortev.chainsawman.form.FormTimeline.BACK_SPLASH + 5, () -> {
			try {
				context.assertTrue(!Form.on(player) && !Form.turning(player), "they were still in the form after turning back");
			} finally {
				done(context, player);
			}
			context.complete();
		});
	}

	/** The cord needs blood to start the engine, and takes it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_blood", tickLimit = 20)
	public void cordNeedsBlood(TestContext context) {
		ServerPlayerEntity player = player(context, "bloodless", 0.0);
		player.setAttached(Heart.MERGED, true);
		try {
			Blood.set(player, Blood.TRANSFORM - 1);
			Form.pullCord(player);
			context.assertTrue(Blood.get(player) == Blood.TRANSFORM - 1, "the cord took blood it didn't have");
			Blood.set(player, Blood.MAX);
			Form.pullCord(player);
			context.assertTrue(Blood.get(player) == Blood.MAX - Blood.TRANSFORM, "the cord didn't take its blood: " + Blood.get(player));
		} finally {
			done(context, player);
		}
		context.complete();
	}

	/**
	 * Carrying Pochita, kortev dying the first time doesn't: he becomes their heart, and they come back on their feet,
	 * waiting to pull the cord; the pull is free, and transforms them.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_merge", tickLimit = JOINED + MergeTimeline.END + 30)
	public void pochitaBecomesYourHeart(TestContext context) {
		ServerPlayerEntity player = player(context, ChainsawMan.OWNER, 0.0);
		player.getInventory().insertStack(new ItemStack(ChainsawMan.POCHITA));
		context.waitAndRun(JOINED, () -> {
			try {
				player.damage(player.getDamageSources().generic(), 1000.0F);
				context.assertTrue(player.isAlive() && Merge.is(player), "they didn't start merging");
				context.assertTrue(!io.github.kortev.chainsawman.PochitaItem.carried(player), "Pochita was still in their pack");
				context.assertTrue(!player.damage(player.getDamageSources().generic(), 5.0F), "merging, they were hurt");
			} catch (RuntimeException e) {
				done(context, player);
				throw e;
			}
			context.runAtTick(JOINED + MergeTimeline.END + 10, () -> {
				try {
					context.assertTrue(Heart.merged(player), "Pochita didn't become their heart");
					context.assertTrue(player.getHealth() == player.getMaxHealth(), "they didn't come back whole");
					context.assertTrue(!Downed.is(player), "they were down, not back");
					context.assertTrue(Merge.waiting(player) && !Form.pulling(player), "the cord didn't wait for them to pull it");
					context.assertTrue(!player.damage(player.getDamageSources().generic(), 5.0F), "waiting to pull it, they were hurt");
					Form.pullCord(player);
					context.assertTrue(!Merge.is(player) && Form.pulling(player), "pulling the cord didn't end the merge and transform them");
					context.assertTrue(Blood.get(player) == Blood.MAX, "the first pull took blood: " + Blood.get(player));
				} finally {
					done(context, player);
					player.removeAttached(Heart.MERGED);
				}
				context.complete();
			});
		});
	}

	/** Pochita takes kortev's blood until he's theirs; anyone else's he won't. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_tame", tickLimit = PochitaEntity.FEED + 40)
	public void bloodTamesPochita(TestContext context) {
		ServerPlayerEntity owner = player(context, ChainsawMan.OWNER, 0.0);
		ServerPlayerEntity other = player(context, "stranger", 2.0);
		PochitaEntity pochita = new PochitaEntity(ChainsawMan.POCHITA_ENTITY, context.getWorld());
		Vec3d at = spot(context).add(0.0, 0.0, 1.5);
		pochita.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		pochita.setNoGravity(true);
		context.getWorld().spawnEntity(pochita);
		for (int t = 0; t <= 8; t += 4) {
			context.runAtTick(t + 1, () -> pochita.feed(other));
		}
		for (int t = 12; t <= PochitaEntity.FEED + 20; t += 4) {
			context.runAtTick(t + 1, () -> {
				if (!pochita.isTamed()) {
					pochita.feed(owner);
				}
			});
		}
		context.runAtTick(PochitaEntity.FEED + 30, () -> {
			try {
				context.assertTrue(pochita.isTamed() && pochita.isOwner(owner), "kortev's blood didn't tame him");
			} finally {
				pochita.discard();
				done(context, owner);
				done(context, other);
			}
			context.complete();
		});
	}

	/** Pochita doesn't die: he faints, and kortev's blood brings him round. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_faint", tickLimit = PochitaEntity.WAKE + 40)
	public void pochitaFaints(TestContext context) {
		ServerPlayerEntity owner = player(context, ChainsawMan.OWNER, 0.0);
		PochitaEntity pochita = new PochitaEntity(ChainsawMan.POCHITA_ENTITY, context.getWorld());
		Vec3d at = spot(context).add(0.0, 0.0, 1.5);
		pochita.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		pochita.setNoGravity(true);
		pochita.setOwner(owner);
		pochita.setTamed(true, true);
		context.getWorld().spawnEntity(pochita);
		pochita.damage(context.getWorld().getDamageSources().generic(), 1000.0F);
		context.assertTrue(pochita.isAlive() && pochita.fainted(), "Pochita died, or didn't faint");
		for (int t = 0; t <= PochitaEntity.WAKE + 12; t += 4) {
			context.runAtTick(t + 1, () -> {
				if (pochita.fainted()) {
					pochita.feed(owner);
				}
			});
		}
		context.runAtTick(PochitaEntity.WAKE + 25, () -> {
			try {
				context.assertTrue(!pochita.fainted(), "kortev's blood didn't bring him round");
			} finally {
				pochita.discard();
				done(context, owner);
			}
			context.complete();
		});
	}

	/** Pochita held as a chainsaw: the same slash, at half the damage, and nothing drunk. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_form_wield", tickLimit = 20)
	public void heldPochitaSlashes(TestContext context) {
		ServerPlayerEntity player = player(context, "wielder", 0.0);
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(ChainsawMan.POCHITA));
		HuskEntity front = husk(context, 0.0, 2.0);
		try {
			context.assertTrue(Saw.slash(player), "held, Pochita didn't slash");
			float lost = front.getMaxHealth() - front.getHealth();
			context.assertTrue(lost > Saw.SLASH_DAMAGE * Saw.WIELDED * 0.8F && lost < Saw.SLASH_DAMAGE * 0.8F, "the slash did " + lost);
		} finally {
			done(context, player);
			front.discard();
		}
		context.complete();
	}

	/** A player in the form, with Pochita's heart, at {@code x} across the test's spot, facing along +z. */
	private static ServerPlayerEntity fighter(TestContext context, String name, double x) {
		ServerPlayerEntity player = player(context, name, x);
		player.setAttached(Heart.MERGED, true);
		Form.start(player);
		return player;
	}

	private static ServerPlayerEntity player(TestContext context, String name, double x) {
		ServerWorld world = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		Vec3d at = spot(context).add(x, 0.0, 0.0);
		player.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		// (A real client would confirm the teleport; until then the game keeps a player from harm.)
		player.onTeleportationDone();
		player.setNoGravity(true);
		return player;
	}

	/** A husk standing still (no mind, no gravity), {@code z} blocks ahead of the test's spot. */
	private static HuskEntity husk(TestContext context, double x, double z) {
		Vec3d at = spot(context).add(x, 0.0, z);
		HuskEntity husk = new HuskEntity(EntityType.HUSK, context.getWorld());
		husk.refreshPositionAndAngles(at.x, at.y, at.z, 180.0F, 0.0F);
		husk.setAiDisabled(true);
		husk.setNoGravity(true);
		context.getWorld().spawnEntity(husk);
		return husk;
	}

	/**
	 * Where these tests stand: high over their test box, in the air (everything here floats), clear of every other
	 * test's ground and what stands on it (a Domain's cow, say), which the saws and the chain would otherwise reach.
	 */
	private static Vec3d spot(TestContext context) {
		return context.getAbsolute(new Vec3d(0.5, 40.0, 0.5));
	}

	/** Out of the form, up, gone. */
	private static void done(TestContext context, ServerPlayerEntity player) {
		Form.end(player);
		Downed.up(player);
		Frenzy.end(player);
		Frenzy.ready(player);
		Grapple.ready(player);
		context.getWorld().getServer().getPlayerManager().remove(player);
	}
}
