package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.LeechDevilEntity;
import io.github.kortev.chainsawman.devil.LeechPartEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The Leech Devil (devil/): each move has its tell (nothing lands before it ends); the tongue misses whoever moved off its line during the
 * tell, and a cut on it while it is out counts more; the latch drains whoever it sticks to and heals the devil; three hits on the arm tear
 * it loose and stun the arm, which lies near you where a cut counts more; it lets go by itself after 6 s; the trample only starts from 8 blocks or more; killed, it leaves its sucker.
 */
public class LeechGameTests implements FabricGameTest {
	/** Players are untouchable for their first 60 ticks in a world: the tests that hurt one wait this long first. */
	private static final int JOINED = 70;

	private static void floor(TestContext context) {
		ServerWorld w = context.getWorld();
		for (int x = -14; x <= 14; x++) {
			for (int z = -14; z <= 14; z++) {
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

	private static LeechDevilEntity devil(TestContext context, double x, double z, float yaw) {
		ServerWorld w = context.getWorld();
		Vec3d p = at(context, x, z);
		LeechDevilEntity d = Devils.LEECH_DEVIL.create(w);
		d.refreshPositionAndAngles(p.x, p.y, p.z, yaw, 0.0F);
		d.setBodyYaw(yaw);
		d.setHeadYaw(yaw);
		d.aimAt(yaw);
		w.spawnEntity(d);
		return d;
	}

	/** Holds it where it stands (it may still turn and use its moves). */
	private static void rooted(LeechDevilEntity d) {
		d.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.0);
	}

	private static HuskEntity dummy(TestContext context, Vec3d p) {
		HuskEntity h = new HuskEntity(EntityType.HUSK, context.getWorld());
		h.refreshPositionAndAngles(p.x, p.y, p.z, 180.0F, 0.0F);
		h.setAiDisabled(true);
		context.getWorld().spawnEntity(h);
		return h;
	}

	private static ServerPlayerEntity player(TestContext context, String name, double x, double z) {
		ServerPlayerEntity p = ForeverGameTests.player(context, name);
		Vec3d a = at(context, x, z);
		p.teleport(context.getWorld(), a.x, a.y, a.z, 180.0F, 0.0F);
		return p;
	}

	private static Box area(TestContext context) {
		return new Box(at(context, 0, 0), at(context, 0, 0)).expand(24.0, 8.0, 24.0);
	}

	private static void clear(TestContext context) {
		for (Entity e : context.getWorld().getEntitiesByClass(Entity.class, area(context),
				e -> e instanceof LeechDevilEntity || e instanceof LeechPartEntity || e instanceof HuskEntity || e instanceof ItemEntity)) {
			e.discard();
		}
	}

	/** Each move: nothing lands while its tell plays, and it lands after. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "leech_tell", tickLimit = 240)
	public void every_move_has_a_tell(TestContext context) {
		context.assertTrue(LeechDevilEntity.TONGUE_TELL >= 12 && LeechDevilEntity.LATCH_TELL >= 12 && LeechDevilEntity.TRAMPLE_TELL_TICKS >= 12,
				"a tell is shorter than 12 ticks");
		floor(context);
		clear(context);
		LeechDevilEntity d = devil(context, 0, -6, 0.0F);
		rooted(d);
		HuskEntity[] h = new HuskEntity[1];
		// The tongue, straight ahead at a husk 4 blocks off its mouth.
		context.runAtTick(5, () -> {
			h[0] = dummy(context, d.bodyPoint(0.0F, 0.0, 0.0, 6.5));
			d.aimAt(0.0F);
			d.begin(LeechDevilEntity.TONGUE);
		});
		context.runAtTick(5 + LeechDevilEntity.TONGUE_TELL - 2,
				() -> context.assertTrue(h[0].getHealth() == h[0].getMaxHealth(), "the tongue landed during its tell"));
		context.runAtTick(5 + LeechDevilEntity.TONGUE_HIT + 3, () -> {
			context.assertTrue(h[0].getHealth() <= h[0].getMaxHealth() - LeechDevilEntity.TONGUE_DAMAGE + 0.5F, "the tongue did not land: " + h[0].getHealth());
			h[0].discard();
		});
		// The latch: the left arm, at a husk in front of its left shoulder.
		context.runAtTick(60, () -> {
			d.begin(LeechDevilEntity.WALK);
			d.aimAt(0.0F);
			h[0] = dummy(context, d.bodyPoint(0.0F, 0.66, 0.0, 4.5));
			d.begin(LeechDevilEntity.LATCH);
		});
		context.runAtTick(60 + LeechDevilEntity.LATCH_TELL - 2, () -> context.assertTrue(d.stuckTo() < 0, "the arm stuck during its tell"));
		context.runAtTick(60 + LeechDevilEntity.LATCH_TELL + LeechDevilEntity.LATCH_OUT + 3, () -> {
			context.assertTrue(d.state() == LeechDevilEntity.LATCHED && d.stuckTo() == h[0].getId(),
					"the arm did not stick: state " + d.state() + ", stuck to " + d.stuckTo());
			d.begin(LeechDevilEntity.WALK);
			h[0].discard();
		});
		// The trample: it does not move in its tell, and runs after.
		Vec3d[] from = new Vec3d[1];
		context.runAtTick(120, () -> {
			d.aimAt(0.0F);
			from[0] = d.getPos();
			d.begin(LeechDevilEntity.TRAMPLE_TELL);
		});
		context.runAtTick(120 + LeechDevilEntity.TRAMPLE_TELL_TICKS - 2,
				() -> context.assertTrue(d.getPos().distanceTo(from[0]) < 0.2, "it ran during the trample's tell"));
		context.runAtTick(120 + LeechDevilEntity.TRAMPLE_TELL_TICKS + 8, () -> {
			context.assertTrue(d.getPos().distanceTo(from[0]) > 3.0, "the trample did not go: " + d.getPos().distanceTo(from[0]));
			clear(context);
			context.complete();
		});
	}

	/** The tongue lances where it heard you a moment before the tell ended: stepped off its line, you are missed; stood on it, hit. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "leech_tongue", tickLimit = 260)
	public void the_tongue_misses_off_its_line(TestContext context) {
		floor(context);
		clear(context);
		ServerPlayerEntity p = player(context, "dodger", 0, 3);
		LeechDevilEntity d = devil(context, 0, -5, 0.0F);
		rooted(d);
		float[] hp = new float[1];
		context.runAtTick(JOINED, () -> {
			p.teleport(context.getWorld(), at(context, 0, 3).x, at(context, 0, 3).y, at(context, 0, 3).z, 180.0F, 0.0F);
			p.setHealth(p.getMaxHealth());
			p.timeUntilRegen = 0;
			hp[0] = p.getHealth();
			d.begin(LeechDevilEntity.WALK);
			d.aimAt(0.0F);
			d.begin(LeechDevilEntity.TONGUE);
		});
		// Late in the tell, a step and a half to the side.
		context.runAtTick(JOINED + LeechDevilEntity.TONGUE_TELL - 3, () -> p.teleport(context.getWorld(), at(context, 2.6, 3).x, at(context, 2.6, 3).y, at(context, 2.6, 3).z,
				180.0F, 0.0F));
		context.runAtTick(JOINED + LeechDevilEntity.TONGUE_HIT + 4, () -> context.assertTrue(p.getHealth() >= hp[0] - 0.01F, "the tongue hit someone who stepped off its line: "
				+ p.getHealth() + " (aim " + d.aim() + ")"));
		// Again, standing still: it hits.
		int again = JOINED + LeechDevilEntity.TONGUE_END + 20;
		context.runAtTick(again, () -> {
			p.timeUntilRegen = 0;
			hp[0] = p.getHealth();
			d.begin(LeechDevilEntity.WALK);
			d.begin(LeechDevilEntity.TONGUE);
		});
		context.runAtTick(again + LeechDevilEntity.TONGUE_HIT + 4, () -> {
			// (how much depends on the difficulty: a player takes mobs' hits scaled by it)
			context.assertTrue(p.getHealth() <= hp[0] - 3.0F, "the tongue missed someone standing still: " + p.getHealth());
		});
		// A cut on the tongue while it is out counts half again.
		context.runAtTick(again + LeechDevilEntity.TONGUE_HIT + 2, () -> {
			LeechPartEntity part = d.tonguePart();
			context.assertTrue(part != null && part.isAlive(), "the tongue held out cannot be cut");
			d.setHealth(100.0F);
			d.timeUntilRegen = 0;
			var src = p.getDamageSources().playerAttack(p);
			part.damage(src, 4.0F);
			// (through its hide, as any cut is)
			float want = net.minecraft.entity.DamageUtil.getDamageLeft(d, 4.0F * LeechDevilEntity.TONGUE_CUT_TAKES, src, (float) d.getArmor(), 0.0F);
			context.assertTrue(Math.abs(100.0F - d.getHealth() - want) < 0.01F, "a cut on the tongue took " + (100.0F - d.getHealth()) + ", not " + want);
		});
		context.runAtTick(again + LeechDevilEntity.TONGUE_END + 2, () -> {
			context.assertTrue(d.tonguePart() == null, "the tongue's part was left behind");
			ForeverGameTests.leave(context, p);
			clear(context);
			context.complete();
		});
	}

	/** Latched: every pulse drains whoever it is stuck to and heals it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "leech_drain", tickLimit = 220)
	public void the_latch_drains_and_heals(TestContext context) {
		floor(context);
		clear(context);
		ServerPlayerEntity p = player(context, "drained", 0, -0.5);
		LeechDevilEntity d = devil(context, 0, -5, 0.0F);
		rooted(d);
		float[] before = new float[2];
		context.runAtTick(JOINED, () -> {
			p.setHealth(p.getMaxHealth());
			d.setHealth(100.0F);
			d.begin(LeechDevilEntity.WALK);
			d.aimAt(0.0F);
			d.begin(LeechDevilEntity.LATCH);
		});
		int stuck = JOINED + LeechDevilEntity.LATCH_TELL + LeechDevilEntity.LATCH_OUT + 2;
		context.runAtTick(stuck, () -> {
			context.assertTrue(d.stuckTo() == p.getId(), "the arm did not stick to the player: " + d.stuckTo() + " state " + d.state());
			before[0] = p.getHealth();
			before[1] = d.getHealth();
		});
		context.runAtTick(stuck + 4 * LeechDevilEntity.LATCH_PULSE + 2, () -> {
			context.assertTrue(p.getHealth() <= before[0] - 3.0F * LeechDevilEntity.LATCH_DRAIN + 0.01F, "it did not drain: " + before[0] + " to " + p.getHealth());
			context.assertTrue(d.getHealth() >= before[1] + 3.0F * LeechDevilEntity.LATCH_HEAL - 0.01F, "it did not heal: " + before[1] + " to " + d.getHealth());
			context.assertTrue(p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS), "the one it drinks from is not slowed");
			context.assertTrue(d.armPart() != null && d.armPart().isAlive(), "the stuck arm cannot be cut");
			ForeverGameTests.leave(context, p);
			clear(context);
			context.complete();
		});
	}

	/** Three hits on the arm tear it loose: the player is free, the arm hangs stunned, it reels back. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "leech_cut", tickLimit = 240)
	public void three_hits_on_the_arm_free_you(TestContext context) {
		floor(context);
		clear(context);
		ServerPlayerEntity p = player(context, "cutter", 0, -0.5);
		LeechDevilEntity d = devil(context, 0, -5, 0.0F);
		rooted(d);
		context.runAtTick(JOINED, () -> {
			d.begin(LeechDevilEntity.WALK);
			d.aimAt(0.0F);
			d.begin(LeechDevilEntity.LATCH);
		});
		int stuck = JOINED + LeechDevilEntity.LATCH_TELL + LeechDevilEntity.LATCH_OUT + 2;
		for (int k = 0; k < LeechDevilEntity.CUT_HITS; k++) {
			int n = k;
			context.runAtTick(stuck + 2 + 14 * k, () -> {
				context.assertTrue(d.stuckTo() == p.getId(), "not stuck before cut " + (n + 1) + ": " + d.stuckTo() + " state " + d.state());
				LeechPartEntity arm = d.armPart();
				context.assertTrue(arm != null && arm.isAlive(), "no arm to cut");
				// (a hit, not enough damage alone: 3 x 3.5 is under the 12 that tears it too)
				context.assertTrue(arm.damage(p.getDamageSources().playerAttack(p), 3.5F), "the arm took no cut");
			});
		}
		context.runAtTick(stuck + 4 + 14 * (LeechDevilEntity.CUT_HITS - 1), () -> {
			context.assertTrue(d.stuckTo() < 0, "still stuck after " + LeechDevilEntity.CUT_HITS + " cuts");
			context.assertTrue(d.state() == LeechDevilEntity.RECOIL, "it did not reel back: state " + d.state());
			context.assertTrue(d.armStunned(), "the cut arm is not stunned");
			context.assertTrue(d.armPart() == null, "the arm's part was left behind");
			// it lies stunned on the ground near you, and a cut there counts half again
			LeechPartEntity limp = d.limpPart();
			context.assertTrue(limp != null && limp.isAlive() && d.limpAt() != null, "the stunned arm is not lying out to be cut");
			context.assertTrue(limp.squaredDistanceTo(p) < 4.0 * 4.0, "the stunned arm lies out of reach: " + Math.sqrt(limp.squaredDistanceTo(p)));
			d.setHealth(100.0F);
			d.timeUntilRegen = 0;
			var src = p.getDamageSources().playerAttack(p);
			limp.damage(src, 4.0F);
			float want = net.minecraft.entity.DamageUtil.getDamageLeft(d, 4.0F * LeechDevilEntity.LIMP_TAKES, src, (float) d.getArmor(), 0.0F);
			context.assertTrue(Math.abs(100.0F - d.getHealth() - want) < 0.01F, "a cut on the stunned arm took " + (100.0F - d.getHealth()) + ", not " + want);
			ForeverGameTests.leave(context, p);
			clear(context);
			context.complete();
		});
	}

	/** Left alone, the latch lets go after 6 s. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "leech_letgo", tickLimit = 300)
	public void the_latch_lets_go_after_6_s(TestContext context) {
		floor(context);
		clear(context);
		ServerPlayerEntity p = player(context, "held", 0, -0.5);
		LeechDevilEntity d = devil(context, 0, -5, 0.0F);
		rooted(d);
		context.runAtTick(JOINED, () -> {
			d.begin(LeechDevilEntity.WALK);
			d.aimAt(0.0F);
			d.begin(LeechDevilEntity.LATCH);
		});
		int stuck = JOINED + LeechDevilEntity.LATCH_TELL + LeechDevilEntity.LATCH_OUT + 2;
		context.runAtTick(stuck, () -> context.assertTrue(d.stuckTo() == p.getId(), "the arm did not stick"));
		context.runAtTick(stuck + LeechDevilEntity.LATCH_MAX - 10, () -> {
			context.assertTrue(d.stuckTo() == p.getId(), "it let go too soon");
			p.setHealth(p.getMaxHealth());
		});
		context.runAtTick(stuck + LeechDevilEntity.LATCH_MAX + 4, () -> {
			context.assertTrue(d.stuckTo() < 0 && d.state() != LeechDevilEntity.LATCHED, "it did not let go after 6 s");
			context.assertTrue(p.isAlive(), "the player died");
			ForeverGameTests.leave(context, p);
			clear(context);
			context.complete();
		});
	}

	/** The trample only from 8 blocks or more: one devil 5 blocks off never tramples; one 12 off does. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "leech_trample", tickLimit = 300)
	public void the_trample_only_from_8_blocks(TestContext context) {
		context.assertTrue(!LeechDevilEntity.trampleFrom(LeechDevilEntity.TRAMPLE_MIN - 0.1) && LeechDevilEntity.trampleFrom(LeechDevilEntity.TRAMPLE_MIN),
				"the trample's distance rule");
		floor(context);
		clear(context);
		ServerPlayerEntity p = player(context, "far", 0, 0);
		p.getAbilities().invulnerable = true;
		LeechDevilEntity near = devil(context, 0, -5, 0.0F);
		LeechDevilEntity far = devil(context, 0, 12, 180.0F);
		rooted(near);
		rooted(far);
		List<Integer> nearMoves = new ArrayList<>();
		List<Integer> farMoves = new ArrayList<>();
		near.moveListener = nearMoves::add;
		far.moveListener = m -> {
			farMoves.add(m);
			if (m == LeechDevilEntity.TRAMPLE_TELL) {
				// (seen: away with it, before it runs through the other)
				far.discard();
			}
		};
		context.runAtTick(260, () -> {
			context.assertTrue(!nearMoves.contains(LeechDevilEntity.TRAMPLE_TELL), "it trampled from 5 blocks: " + nearMoves);
			context.assertTrue(!nearMoves.isEmpty(), "the near one never moved: " + nearMoves);
			context.assertTrue(farMoves.contains(LeechDevilEntity.TRAMPLE_TELL), "the far one never trampled: " + farMoves);
			p.getAbilities().invulnerable = false;
			ForeverGameTests.leave(context, p);
			clear(context);
			context.complete();
		});
	}

	/** Killed, it leaves its sucker, and is gone once it has folded up. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "leech_drop", tickLimit = 160)
	public void it_drops_its_part(TestContext context) {
		floor(context);
		clear(context);
		LeechDevilEntity d = devil(context, 0, 0, 0.0F);
		context.runAtTick(3, () -> d.damage(d.getDamageSources().generic(), 1000.0F));
		context.runAtTick(30, () -> {
			context.assertTrue(d.isDead() && !d.isRemoved(), "it is not lying there dying");
			context.assertTrue(!context.getWorld().getEntitiesByClass(ItemEntity.class, area(context), i -> i.getStack().isOf(Devils.LEECH_SUCKER)).isEmpty(),
					"no sucker");
		});
		context.runAtTick(3 + LeechDevilEntity.DEATH_TICKS + 10, () -> {
			context.assertTrue(d.isRemoved(), "it never went");
			clear(context);
			context.complete();
		});
	}
}
