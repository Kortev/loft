package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.Growth;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import io.github.kortev.chainsawman.devil.TomatoSeedEntity;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Devils grow (devil/Growth, the Tomato Devil first): a night lived through near people adds a step, a night alone none; a player's death
 * adds one, once a night; a comeback adds one; never above 5, never more than two a night; health, damage and size follow the step; one
 * more carcass at steps 2 and 4; growth keeps through a save and can be set by NBT; a grown devil never despawns.
 *
 * <p>(The rule is fed its own times here, so no test waits on the world's clock or moves it for the others: the devils' own AI is off, so
 * their ticks don't count nights by the real clock meanwhile.)
 */
public class GrowthGameTests implements FabricGameTest {
	/** A night far from the world's own clock: its dusk, deep in it, and its dawn. */
	private static final long DUSK = 24000L * 40 + 13000L;
	private static final long MIDNIGHT = 24000L * 40 + 18000L;
	private static final long DAWN = 24000L * 40 + 23500L;
	private static final long NEXT_NIGHT = MIDNIGHT + 24000L;
	private static final long NEXT_DAWN = DAWN + 24000L;

	private static void floor(TestContext context) {
		ServerWorld w = context.getWorld();
		for (int x = -10; x <= 10; x++) {
			for (int z = -10; z <= 10; z++) {
				w.setBlockState(context.getAbsolutePos(new BlockPos(x, 0, z)), Blocks.STONE.getDefaultState());
				for (int y = 1; y <= 6; y++) {
					w.setBlockState(context.getAbsolutePos(new BlockPos(x, y, z)), Blocks.AIR.getDefaultState());
				}
			}
		}
	}

	private static Vec3d at(TestContext context, double x, double z) {
		return context.getAbsolute(new Vec3d(0.5 + x, 1.0, 0.5 + z));
	}

	/** A devil standing still (its own mind off). */
	private static TomatoDevilEntity devil(TestContext context, double x, double z) {
		ServerWorld w = context.getWorld();
		Vec3d p = at(context, x, z);
		TomatoDevilEntity d = Devils.TOMATO_DEVIL.create(w);
		d.refreshPositionAndAngles(p.x, p.y, p.z, 0.0F, 0.0F);
		d.setAiDisabled(true);
		w.spawnEntity(d);
		return d;
	}

	private static Box area(TestContext context) {
		return new Box(at(context, 0, 0), at(context, 0, 0)).expand(18.0, 8.0, 18.0);
	}

	private static void clear(TestContext context) {
		for (Entity e : context.getWorld().getEntitiesByClass(Entity.class, area(context),
				e -> e instanceof TomatoDevilEntity || e instanceof TomatoSeedEntity || e instanceof ItemEntity)) {
			e.discard();
		}
	}

	/** A night with a player near: one step when it ends, and only one, however long the day after. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth_night")
	public void a_night_near_people_adds_one_step(TestContext context) {
		floor(context);
		clear(context);
		TomatoDevilEntity d = devil(context, 0, 0);
		Growth g = d.growth();
		context.assertTrue(!g.tick(DUSK, false) && !g.tick(MIDNIGHT, true) && !g.tick(MIDNIGHT + 100, false), "it grew in the dark");
		context.assertTrue(g.level() == 0, "it grew before the night ended");
		context.assertTrue(g.tick(DAWN, false) && g.level() == 1 && d.growthLevel() == 1, "the night near people ended with no step");
		context.assertTrue(!g.tick(DAWN + 10, false) && !g.tick(DAWN + 6000, true) && g.level() == 1, "the same night gave a second step");
		// the next night, near people again: another
		g.tick(NEXT_NIGHT, true);
		context.assertTrue(g.tick(NEXT_DAWN, false) && g.level() == 2, "the second night near people gave no step");
		// and the real check of who is near: a player 40 blocks off counts, nobody does not
		context.assertTrue(!Growth.peopleNear(d), "someone counted as near with nobody there");
		ServerPlayerEntity p = ForeverGameTests.player(context, "neighbour");
		try {
			Vec3d pp = at(context, 0, 0).add(40.0, 0.0, 0.0);
			p.teleport(context.getWorld(), pp.x, pp.y, pp.z, 0.0F, 0.0F);
			context.assertTrue(Growth.peopleNear(d), "a player 40 blocks off did not count as near");
		} finally {
			ForeverGameTests.leave(context, p);
			clear(context);
		}
		context.complete();
	}

	/** A night with nobody near: nothing. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth_alone")
	public void a_night_alone_adds_none(TestContext context) {
		floor(context);
		clear(context);
		TomatoDevilEntity d = devil(context, 0, 0);
		Growth g = d.growth();
		g.tick(DUSK, false);
		g.tick(MIDNIGHT, false);
		context.assertTrue(!g.tick(DAWN, false) && g.level() == 0, "a night alone gave a step");
		// people by day don't count either
		g.tick(DAWN + 2000, true);
		g.tick(NEXT_NIGHT, false);
		context.assertTrue(!g.tick(NEXT_DAWN, false) && g.level() == 0, "people near only by day gave a step");
		clear(context);
		context.complete();
	}

	/** A player dying to it: a step, once a night (the real kill hook too). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth_death")
	public void a_players_death_adds_one_once_a_night(TestContext context) {
		floor(context);
		clear(context);
		TomatoDevilEntity d = devil(context, 0, 0);
		Growth g = d.growth();
		context.assertTrue(g.killedPlayer(MIDNIGHT) && g.level() == 1, "a player's death gave no step");
		context.assertTrue(!g.killedPlayer(MIDNIGHT + 200) && g.level() == 1, "a second death the same night gave a step");
		context.assertTrue(g.killedPlayer(NEXT_NIGHT) && g.level() == 2, "a death the next night gave no step");
		ServerPlayerEntity p = ForeverGameTests.player(context, "victim");
		try {
			TomatoDevilEntity e = devil(context, 4, 0);
			e.onKilledOther(context.getWorld(), p);
			context.assertTrue(e.growthLevel() == 1, "the kill hook gave no step: " + e.growthLevel());
			e.onKilledOther(context.getWorld(), p);
			context.assertTrue(e.growthLevel() == 1, "the kill hook gave two steps in one night");
		} finally {
			ForeverGameTests.leave(context, p);
			clear(context);
		}
		context.complete();
	}

	/** Coming back out of its seeds: the devil that climbs out is a step worse than the one that burst (and one from nothing is 1). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth_comeback", tickLimit = 120)
	public void a_comeback_adds_one(TestContext context) {
		floor(context);
		clear(context);
		TomatoDevilEntity d = devil(context, 0, 0);
		d.growth().set(2, true);
		context.runAtTick(3, () -> d.damage(d.getDamageSources().generic(), 1000.0F));
		context.runAtTick(80, () -> {
			List<TomatoSeedEntity> seeds = context.getWorld().getEntitiesByClass(TomatoSeedEntity.class, area(context), s -> s.isAlive());
			context.assertTrue(seeds.size() == TomatoDevilEntity.SEEDS, "not five seeds: " + seeds.size());
			context.assertTrue(seeds.get(0).growth != null && seeds.get(0).growth.getInt("Growth") == 2, "the seeds did not carry its growth");
			seeds.get(0).swellTo(TomatoSeedEntity.SWELL - 2);
		});
		context.runAtTick(86, () -> {
			List<TomatoDevilEntity> back = context.getWorld().getEntitiesByClass(TomatoDevilEntity.class, area(context), e -> e.isAlive());
			context.assertTrue(back.size() == 1, "no comeback: " + back.size());
			TomatoDevilEntity b = back.get(0);
			context.assertTrue(b.growthLevel() == 3, "the comeback is at growth " + b.growthLevel() + ", not 3");
			context.assertTrue(Math.abs(b.getHealth() - b.getMaxHealth() * TomatoDevilEntity.COMEBACK_HEALTH) < 0.01F, "the comeback's health is off: " + b.getHealth());
			clear(context);
			// a seed from nothing
			TomatoSeedEntity s = new TomatoSeedEntity(Devils.TOMATO_SEED, context.getWorld());
			Vec3d p = at(context, 0, 0);
			s.refreshPositionAndAngles(p.x, p.y + 0.2, p.z, 0.0F, 0.0F);
			s.brood = UUID.randomUUID();
			context.getWorld().spawnEntity(s);
			s.swellTo(TomatoSeedEntity.SWELL - 2);
		});
		context.runAtTick(94, () -> {
			List<TomatoDevilEntity> back = context.getWorld().getEntitiesByClass(TomatoDevilEntity.class, area(context), e -> e.isAlive());
			context.assertTrue(back.size() == 1 && back.get(0).growthLevel() == 1, "a comeback from a plain seed is not at growth 1");
			clear(context);
			context.complete();
		});
	}

	/** Never above 5; never more than two steps in one night, whatever comes. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth_caps")
	public void never_above_five_nor_two_a_night(TestContext context) {
		floor(context);
		clear(context);
		TomatoDevilEntity d = devil(context, 0, 0);
		Growth g = d.growth();
		g.tick(MIDNIGHT, true);
		context.assertTrue(g.killedPlayer(MIDNIGHT) && g.cameBack(MIDNIGHT + 10), "the first two steps of the night did not come");
		context.assertTrue(!g.cameBack(MIDNIGHT + 20) && !g.tick(DAWN, false) && g.level() == 2 && g.stepsTonight() == Growth.MOST_A_NIGHT,
				"a third step came in one night: " + g.level());
		TomatoDevilEntity top = devil(context, 4, 0);
		top.growth().set(5, true);
		context.assertTrue(!top.growth().cameBack(NEXT_NIGHT) && !top.growth().killedPlayer(NEXT_NIGHT) && top.growthLevel() == Growth.MAX, "it went above 5");
		top.growth().set(9, true);
		context.assertTrue(top.growthLevel() == 5, "set above 5 it went to " + top.growthLevel());
		clear(context);
		context.complete();
	}

	/** Each step: health +10% of its base (healed by the gain), damage +6%, size +4% (the hitbox too); its timings stay. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth_stats")
	public void health_damage_and_size_follow_the_step(TestContext context) {
		floor(context);
		clear(context);
		for (int level = 0; level <= Growth.MAX; level++) {
			TomatoDevilEntity d = devil(context, -7 + level * 2.8, 0);
			d.growth().set(level, true);
			float max = TomatoDevilEntity.MAX_HEALTH * (1.0F + 0.1F * level);
			context.assertTrue(Math.abs(d.getMaxHealth() - max) < 0.01F, "growth " + level + ": max health " + d.getMaxHealth() + ", not " + max);
			context.assertTrue(Math.abs(d.getHealth() - max) < 0.01F, "growth " + level + ": not healed by the gain: " + d.getHealth());
			context.assertTrue(Math.abs(d.growth().damage() - (1.0F + 0.06F * level)) < 1.0E-4F, "growth " + level + ": damage x" + d.growth().damage());
			float w = TomatoDevilEntity.WIDTH * (1.0F + 0.08F * level);
			float h = TomatoDevilEntity.HEIGHT * (1.0F + 0.08F * level);
			context.assertTrue(Math.abs(d.getWidth() - w) < 0.01F && Math.abs(d.getHeight() - h) < 0.01F && Math.abs(d.getScale() - (1.0F + 0.08F * level)) < 1.0E-4F,
					"growth " + level + ": size " + d.getWidth() + " x " + d.getHeight());
			context.assertTrue(Math.abs(Growth.voice(level) - (1.0F - 0.05F * level)) < 1.0E-4F, "growth " + level + ": voice " + Growth.voice(level));
		}
		// healed by the gain only: hurt, then a step
		TomatoDevilEntity hurt = devil(context, 0, 6);
		hurt.setHealth(50.0F);
		hurt.growth().cameBack(MIDNIGHT);
		context.assertTrue(Math.abs(hurt.getHealth() - 59.0F) < 0.01F, "a step healed " + (hurt.getHealth() - 50.0F) + ", not 9");
		// the tells don't change
		context.assertTrue(TomatoDevilEntity.SLAP_TELL == 16 && TomatoDevilEntity.ROLL_TELL_TICKS == 18 && TomatoDevilEntity.SPIT_TELL == 18 && TomatoDevilEntity.HOP_TELL == 18,
				"a tell changed");
		clear(context);
		context.complete();
	}

	/** One carcass at growth 0 and 1, two at 2 and 3, three at 4 and 5. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth_carcass", tickLimit = 200)
	public void more_carcasses_at_two_and_four(TestContext context) {
		floor(context);
		clear(context);
		int[][] cases = {{1, 1}, {2, 2}, {4, 3}};
		for (int i = 0; i < cases.length; i++) {
			int level = cases[i][0];
			int want = cases[i][1];
			int start = 3 + i * 60;
			TomatoDevilEntity[] d = new TomatoDevilEntity[1];
			context.runAtTick(start, () -> {
				clear(context);
				d[0] = devil(context, 0, 0);
				d[0].growth().set(level, true);
				d[0].damage(d[0].getDamageSources().generic(), 1000.0F);
			});
			context.runAtTick(start + TomatoDevilEntity.BURST_AT + 12, () -> {
				int n = 0;
				for (ItemEntity it : context.getWorld().getEntitiesByClass(ItemEntity.class, area(context), it -> it.getStack().isOf(Devils.TOMATO_CARCASS))) {
					n += it.getStack().getCount();
				}
				context.assertTrue(n == want, "growth " + level + " left " + n + " carcasses, not " + want);
				context.assertTrue(Growth.carcasses(0) == 1 && Growth.carcasses(3) == 2 && Growth.carcasses(5) == 3, "the carcass count is off");
			});
		}
		context.runAtTick(3 + cases.length * 60, () -> {
			clear(context);
			context.complete();
		});
	}

	/** Growth keeps through a save; /summon's {Growth:5} makes a full-health growth 5; set on one already about, it shudders for anyone near. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth_save", tickLimit = 40)
	public void growth_survives_a_save(TestContext context) {
		floor(context);
		clear(context);
		ServerWorld w = context.getWorld();
		TomatoDevilEntity d = devil(context, 0, 0);
		d.growth().set(3, true);
		d.growth().killedPlayer(MIDNIGHT);
		d.setHealth(70.0F);
		NbtCompound nbt = new NbtCompound();
		d.saveSelfNbt(nbt);
		d.discard();
		var back = EntityType.getEntityFromNbt(nbt, w);
		context.assertTrue(back.isPresent() && back.get() instanceof TomatoDevilEntity, "it did not load");
		TomatoDevilEntity b = (TomatoDevilEntity) back.get();
		context.assertTrue(b.growthLevel() == 4 && b.growth().stepsTonight() == 1, "it lost its growth: " + b.growthLevel() + " (" + b.growth().stepsTonight() + " tonight)");
		context.assertTrue(Math.abs(b.getMaxHealth() - 126.0F) < 0.01F && Math.abs(b.getHealth() - 70.0F) < 0.01F, "its health came back as " + b.getHealth() + " of " + b.getMaxHealth());
		context.assertTrue(Math.abs(b.getWidth() - TomatoDevilEntity.WIDTH * 1.32F) < 0.01F, "its size came back as " + b.getWidth());
		context.assertTrue(!b.growth().killedPlayer(MIDNIGHT + 5), "the night's death was forgotten");
		// as /summon chainsawman:tomato_devil ~ ~ ~ {Growth:5} does it
		NbtCompound summon = new NbtCompound();
		summon.putString("id", "chainsawman:tomato_devil");
		summon.putInt("Growth", 5);
		var made = EntityType.getEntityFromNbt(summon, w);
		context.assertTrue(made.isPresent() && made.get() instanceof TomatoDevilEntity m && m.growthLevel() == 5 && Math.abs(m.getHealth() - 135.0F) < 0.01F,
				"{Growth:5} did not make a full growth 5");
		context.assertTrue(made.get() instanceof TomatoDevilEntity m2 && m2.grewAt() < 0, "a summoned one shuddered");
		ServerPlayerEntity p = ForeverGameTests.player(context, "watcher");
		TomatoDevilEntity live = devil(context, 3, 0);
		Vec3d pp = at(context, 3, -6);
		p.teleport(w, pp.x, pp.y, pp.z, 0.0F, 0.0F);
		context.runAtTick(5, () -> {
			try {
				NbtCompound set = new NbtCompound();
				live.writeNbt(set);
				set.putInt("Growth", 2);
				live.readNbt(set);
				context.assertTrue(live.growthLevel() == 2 && live.grewAt() == w.getTime(), "set by NBT with a player near, it did not shudder");
				context.assertTrue(Math.abs(live.getMaxHealth() - 108.0F) < 0.01F && Math.abs(live.getHealth() - 108.0F) < 0.01F, "set by NBT, its health is " + live.getHealth());
			} finally {
				ForeverGameTests.leave(context, p);
				clear(context);
			}
			context.complete();
		});
	}

	/** Growth 1 or more: it never despawns; at 0 a wild one still may. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth_despawn")
	public void a_grown_devil_does_not_despawn(TestContext context) {
		floor(context);
		clear(context);
		TomatoDevilEntity young = devil(context, -3, 0);
		TomatoDevilEntity grown = devil(context, 3, 0);
		grown.growth().cameBack(MIDNIGHT);
		context.assertTrue(young.canImmediatelyDespawn(200.0) && !young.isPersistent(), "a growth 0 devil can't despawn (the test is wrong)");
		context.assertTrue(!grown.canImmediatelyDespawn(200.0) && grown.isPersistent(), "a grown devil could despawn");
		grown.checkDespawn();
		context.assertTrue(!grown.isRemoved(), "a grown devil despawned");
		clear(context);
		context.complete();
	}

	/** From growth 3 the spit throws seven seeds, not five, at the same gaps (still a sidestep between two); below, five. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth_spit", tickLimit = 80)
	public void a_grown_spit_throws_a_wider_fan(TestContext context) {
		floor(context);
		clear(context);
		ServerWorld w = context.getWorld();
		TomatoDevilEntity young = devil(context, -5, 0);
		young.growth().set(2, true);
		TomatoDevilEntity grown = devil(context, 5, 0);
		grown.growth().set(TomatoDevilEntity.SPIT_MORE_AT, true);
		context.assertTrue(young.spitCount() == TomatoDevilEntity.SPIT_COUNT && grown.spitCount() == TomatoDevilEntity.SPIT_COUNT + 2, "the spit counts are off");
		for (TomatoDevilEntity d : new TomatoDevilEntity[] {young, grown}) {
			context.runAtTick(3, () -> {
				d.setAiDisabled(false);
				d.aimAt(0.0F);
				d.begin(TomatoDevilEntity.SPIT);
			});
		}
		context.runAtTick(3 + TomatoDevilEntity.SPIT_TELL - 2, () -> context.assertTrue(w.getEntitiesByClass(io.github.kortev.chainsawman.devil.TomatoSpitEntity.class, area(context), e -> true).isEmpty(),
				"a seed flew during the tell"));
		context.runAtTick(3 + TomatoDevilEntity.SPIT_TELL + 2, () -> {
			for (TomatoDevilEntity d : new TomatoDevilEntity[] {young, grown}) {
				List<io.github.kortev.chainsawman.devil.TomatoSpitEntity> spat = w.getEntitiesByClass(io.github.kortev.chainsawman.devil.TomatoSpitEntity.class,
						new Box(d.getPos(), d.getPos()).expand(4.5, 5.0, 4.5), e -> true);
				int want = d == grown ? 7 : 5;
				context.assertTrue(spat.size() == want, "growth " + d.growthLevel() + " spat " + spat.size() + ", not " + want);
				List<Double> yaws = new java.util.ArrayList<>();
				for (var e : spat) {
					yaws.add(Math.toDegrees(Math.atan2(-e.getVelocity().x, e.getVelocity().z)));
				}
				java.util.Collections.sort(yaws);
				for (int k = 1; k < yaws.size(); k++) {
					double gap = yaws.get(k) - yaws.get(k - 1);
					context.assertTrue(Math.abs(gap - TomatoDevilEntity.SPIT_FAN) < 1.0, "growth " + d.growthLevel() + ": a gap of " + gap + " degrees, not " + TomatoDevilEntity.SPIT_FAN);
				}
			}
			for (var e : w.getEntitiesByClass(io.github.kortev.chainsawman.devil.TomatoSpitEntity.class, area(context), e -> true)) {
				e.discard();
			}
			clear(context);
			context.complete();
		});
	}

	/** At growth 5 the hop comes twice: the second after its own whole squat (nothing lands in it); at 4, once. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth_hop", tickLimit = 160)
	public void a_full_grown_hop_comes_again_after_its_tell(TestContext context) {
		floor(context);
		clear(context);
		ServerWorld w = context.getWorld();
		TomatoDevilEntity d = devil(context, 0, 0);
		d.growth().set(Growth.MAX, true);
		TomatoDevilEntity four = devil(context, 0, -9);
		four.growth().set(4, true);
		var near = EntityType.SHEEP.create(w);
		Vec3d a = at(context, 3.0, 0);
		near.refreshPositionAndAngles(a.x, a.y, a.z, 0.0F, 0.0F);
		near.setAiDisabled(true);
		w.spawnEntity(near);
		ServerPlayerEntity p = ForeverGameTests.player(context, "hopped");
		Vec3d pp = at(context, 0, 13);
		p.teleport(w, pp.x, pp.y, pp.z, 0.0F, 0.0F);
		int s = 3;
		float[] hp = new float[1];
		context.runAtTick(s, () -> {
			for (TomatoDevilEntity e : new TomatoDevilEntity[] {d, four}) {
				e.setAiDisabled(false);
				e.begin(TomatoDevilEntity.HOP);
			}
		});
		context.runAtTick(s + TomatoDevilEntity.HOP_LAND + 2, () -> {
			context.assertTrue(near.getHealth() < near.getMaxHealth(), "the first hop did not land");
			near.refreshPositionAndAngles(a.x, a.y, a.z, 0.0F, 0.0F);
			near.setVelocity(Vec3d.ZERO);
			hp[0] = near.getHealth();
		});
		context.runAtTick(s + TomatoDevilEntity.HOP_END + 2, () -> {
			context.assertTrue(d.state() == TomatoDevilEntity.HOP && d.hopAgain() && d.stateTime(0.0F) <= 3.0F, "growth 5 did not hop again from the top: state " + d.state());
			context.assertTrue(four.state() != TomatoDevilEntity.HOP, "growth 4 hopped again");
		});
		context.runAtTick(s + TomatoDevilEntity.HOP_END + TomatoDevilEntity.HOP_TELL, () -> {
			context.assertTrue(near.getHealth() == hp[0], "the second hop landed during its squat");
			near.refreshPositionAndAngles(a.x, a.y, a.z, 0.0F, 0.0F);
			near.setVelocity(Vec3d.ZERO);
		});
		context.runAtTick(s + TomatoDevilEntity.HOP_END + TomatoDevilEntity.HOP_LAND + 2, () -> context.assertTrue(near.getHealth() < hp[0], "the second hop did not land"));
		context.runAtTick(s + 2 * TomatoDevilEntity.HOP_END + 3, () -> {
			try {
				context.assertTrue(d.state() != TomatoDevilEntity.HOP && !d.hopAgain(), "it hopped a third time");
			} finally {
				near.discard();
				ForeverGameTests.leave(context, p);
				clear(context);
			}
			context.complete();
		});
	}

	/** The slap and the hop reach as far as its hands are drawn: further as it grows. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth_reach")
	public void reach_follows_its_size(TestContext context) {
		floor(context);
		clear(context);
		TomatoDevilEntity d = devil(context, 0, 0);
		context.assertTrue(Math.abs(d.slapReach() - TomatoDevilEntity.SLAP_REACH) < 1.0E-4, "growth 0's slap reach changed");
		d.growth().set(Growth.MAX, true);
		double k = 1.0 + Growth.SIZE_STEP * Growth.MAX;
		context.assertTrue(Math.abs(d.slapReach() - TomatoDevilEntity.SLAP_REACH * k) < 1.0E-3 && Math.abs(d.hopReach() - TomatoDevilEntity.HOP_REACH * k) < 1.0E-3
				&& Math.abs(d.slapHalfWidth() - TomatoDevilEntity.SLAP_HALF_WIDTH * k) < 1.0E-3, "growth 5's reach does not follow its size: " + d.slapReach());
		clear(context);
		context.complete();
	}
}
