package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.TomatoArrivalEntity;
import io.github.kortev.chainsawman.devil.TomatoArrivals;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import io.github.kortev.chainsawman.devil.TomatoSeedEntity;
import io.github.kortev.chainsawman.devil.TomatoSpitEntity;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.HeartLevel;
import io.github.kortev.chainsawman.form.Saw;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The Tomato Devil (devil/): every move has a tell; a roll into a wall lays it on its back, where hits count half again; killed, it bursts
 * into five seeds that fly out and stick; a torch or fire burns a seed; one seed left brings it back and the others wither; each comeback
 * has a seed fewer; all burned, it stays dead; seeds keep through a save; it spawns by itself only at night on the surface; the saws cut it.
 */
public class TomatoGameTests implements FabricGameTest {
	/** A stone floor 25 blocks across at the test's own height, and the air above it cleared. */
	private static void floor(TestContext context) {
		ServerWorld w = context.getWorld();
		for (int x = -12; x <= 12; x++) {
			for (int z = -12; z <= 12; z++) {
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

	private static TomatoDevilEntity devil(TestContext context, double x, double z) {
		ServerWorld w = context.getWorld();
		Vec3d p = at(context, x, z);
		TomatoDevilEntity d = Devils.TOMATO_DEVIL.create(w);
		d.refreshPositionAndAngles(p.x, p.y, p.z, 0.0F, 0.0F);
		d.setBodyYaw(0.0F);
		d.setHeadYaw(0.0F);
		w.spawnEntity(d);
		return d;
	}

	private static TomatoSeedEntity seed(TestContext context, double x, double z, UUID brood) {
		ServerWorld w = context.getWorld();
		Vec3d p = at(context, x, z);
		TomatoSeedEntity s = new TomatoSeedEntity(Devils.TOMATO_SEED, w);
		s.refreshPositionAndAngles(p.x, p.y + 0.2, p.z, 0.0F, 0.0F);
		s.brood = brood;
		w.spawnEntity(s);
		return s;
	}

	private static HuskEntity dummy(TestContext context, double x, double z) {
		Vec3d p = at(context, x, z);
		HuskEntity h = new HuskEntity(EntityType.HUSK, context.getWorld());
		h.refreshPositionAndAngles(p.x, p.y, p.z, 180.0F, 0.0F);
		h.setAiDisabled(true);
		context.getWorld().spawnEntity(h);
		return h;
	}

	private static Box area(TestContext context) {
		return new Box(at(context, 0, 0), at(context, 0, 0)).expand(20.0, 8.0, 20.0);
	}

	private static List<TomatoSeedEntity> seeds(TestContext context) {
		return context.getWorld().getEntitiesByClass(TomatoSeedEntity.class, area(context), s -> s.isAlive());
	}

	private static List<TomatoDevilEntity> devils(TestContext context) {
		return context.getWorld().getEntitiesByClass(TomatoDevilEntity.class, area(context), d -> d.isAlive());
	}

	private static void clear(TestContext context) {
		for (var e : context.getWorld().getEntitiesByClass(net.minecraft.entity.Entity.class, area(context),
				e -> e instanceof TomatoDevilEntity || e instanceof TomatoSeedEntity || e instanceof TomatoSpitEntity || e instanceof HuskEntity || e instanceof ItemEntity)) {
			e.discard();
		}
	}

	/** Each move: nothing lands during its tell (12 ticks or more), and it lands after. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_tell", tickLimit = 200)
	public void every_move_has_a_tell(TestContext context) {
		context.assertTrue(TomatoDevilEntity.SLAP_TELL >= 12 && TomatoDevilEntity.ROLL_TELL_TICKS >= 12 && TomatoDevilEntity.SPIT_TELL >= 12,
				"a tell is shorter than 12 ticks");
		floor(context);
		clear(context);
		TomatoDevilEntity d = devil(context, 0, -4);
		HuskEntity h = dummy(context, 0, -1.5);
		context.runAtTick(5, () -> d.begin(TomatoDevilEntity.SLAP));
		context.runAtTick(5 + TomatoDevilEntity.SLAP_TELL, () -> context.assertTrue(h.getHealth() == h.getMaxHealth(), "the slap landed during its tell"));
		context.runAtTick(5 + TomatoDevilEntity.SLAP_HIT + 2, () -> {
			context.assertTrue(h.getHealth() < h.getMaxHealth() - 3.0F, "the slap did not land: " + h.getHealth());
			h.discard();
		});
		context.runAtTick(60, () -> d.begin(TomatoDevilEntity.SPIT));
		context.runAtTick(60 + TomatoDevilEntity.SPIT_TELL - 1, () -> context.assertTrue(
				context.getWorld().getEntitiesByClass(TomatoSpitEntity.class, area(context), s -> true).isEmpty(), "seeds flew during the spit's tell"));
		context.runAtTick(60 + TomatoDevilEntity.SPIT_TELL + 1, () -> {
			int n = context.getWorld().getEntitiesByClass(TomatoSpitEntity.class, area(context), s -> true).size();
			context.assertTrue(n == TomatoDevilEntity.SPIT_COUNT, "the spit was not five seeds: " + n);
		});
		Vec3d[] from = new Vec3d[1];
		context.runAtTick(110, () -> {
			d.begin(TomatoDevilEntity.ROLL_TELL);
			from[0] = d.getPos();
		});
		context.runAtTick(110 + TomatoDevilEntity.ROLL_TELL_TICKS - 1,
				() -> context.assertTrue(d.getPos().distanceTo(from[0]) < 0.2, "it rolled during the roll's tell"));
		context.runAtTick(110 + TomatoDevilEntity.ROLL_TELL_TICKS + 6, () -> {
			context.assertTrue(d.getPos().distanceTo(from[0]) > 2.0, "the roll did not go: " + d.getPos().distanceTo(from[0]));
			clear(context);
			context.complete();
		});
	}

	/** Rolled into a wall it lies on its back, and a hit then counts half again. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_wall", tickLimit = 120)
	public void a_roll_into_a_wall_stuns_it(TestContext context) {
		floor(context);
		clear(context);
		ServerWorld w = context.getWorld();
		for (int x = -4; x <= 4; x++) {
			for (int y = 1; y <= 4; y++) {
				w.setBlockState(context.getAbsolutePos(new BlockPos(x, y, 6)), Blocks.OAK_LOG.getDefaultState());
			}
		}
		TomatoDevilEntity d = devil(context, 0, -2);
		context.runAtTick(3, () -> d.begin(TomatoDevilEntity.ROLL_TELL));
		context.runAtTick(3 + TomatoDevilEntity.ROLL_TELL_TICKS + 20, () -> {
			context.assertTrue(d.state() == TomatoDevilEntity.ON_BACK, "it is not on its back after the wall: state " + d.state() + " at " + d.getPos());
			float before = d.getHealth();
			d.damage(d.getDamageSources().generic(), 10.0F);
			context.assertTrue(Math.abs(before - d.getHealth() - 15.0F) < 0.01F, "a hit on its back did not count 1.5: " + (before - d.getHealth()));
			clear(context);
			context.complete();
		});
	}

	/** Killed, it bursts into five seeds that land within 6 blocks, and its carcass. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_scatter", tickLimit = 120)
	public void five_seeds_scatter(TestContext context) {
		floor(context);
		clear(context);
		TomatoDevilEntity d = devil(context, 0, 0);
		Vec3d dead = d.getPos();
		context.runAtTick(3, () -> d.damage(d.getDamageSources().generic(), 1000.0F));
		context.runAtTick(3 + TomatoDevilEntity.BURST_AT - 2, () -> context.assertTrue(seeds(context).isEmpty(), "the seeds came before it burst"));
		context.runAtTick(80, () -> {
			List<TomatoSeedEntity> s = seeds(context);
			context.assertTrue(s.size() == TomatoDevilEntity.SEEDS, "not five seeds: " + s.size());
			for (TomatoSeedEntity x : s) {
				double far = Math.hypot(x.getX() - dead.x, x.getZ() - dead.z);
				context.assertTrue(x.landed(), "a seed did not stick in the ground");
				context.assertTrue(far <= TomatoDevilEntity.SEED_FLY + 0.4 && far >= 1.0, "a seed flew " + far);
				context.assertTrue(x.next == TomatoDevilEntity.SEEDS - 1, "a seed would come back with " + x.next);
			}
			context.assertTrue(!context.getWorld().getEntitiesByClass(ItemEntity.class, area(context), i -> i.getStack().isOf(Devils.TOMATO_CARCASS)).isEmpty(),
					"no carcass");
			clear(context);
			context.complete();
		});
	}

	/** A torch used on a seed burns it (and is used up); flint and steel and a fire charge do too. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_torch", tickLimit = 40)
	public void a_torch_burns_a_seed(TestContext context) {
		floor(context);
		clear(context);
		UUID brood = UUID.randomUUID();
		TomatoSeedEntity a = seed(context, 0, 0, brood);
		TomatoSeedEntity b = seed(context, 2, 0, brood);
		TomatoSeedEntity c = seed(context, -2, 0, brood);
		ServerPlayerEntity p = ForeverGameTests.player(context, "burner");
		try {
			p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.TORCH, 4));
			a.interact(p, Hand.MAIN_HAND);
			context.assertTrue(a.burning() >= 0 && a.ended(), "the torch did not set the seed burning");
			context.assertTrue(p.getMainHandStack().getCount() == 3, "the torch was not used up");
			p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
			b.interact(p, Hand.MAIN_HAND);
			context.assertTrue(b.burning() >= 0 && p.getMainHandStack().getDamage() == 1, "flint and steel did not burn it");
			p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.FIRE_CHARGE, 2));
			c.interact(p, Hand.MAIN_HAND);
			context.assertTrue(c.burning() >= 0 && p.getMainHandStack().getCount() == 1, "a fire charge did not burn it");
			p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
			TomatoSeedEntity d = seed(context, 4, 0, brood);
			d.interact(p, Hand.MAIN_HAND);
			d.damage(p.getDamageSources().playerAttack(p), 10.0F);
			context.assertTrue(!d.isRemoved(), "a stick or a hit did away with it");
		} finally {
			ForeverGameTests.leave(context, p);
			clear(context);
		}
		context.complete();
	}

	/** Fire, a lit campfire and lava burn a seed that lies in them. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_fire", tickLimit = 40)
	public void fire_burns_a_seed(TestContext context) {
		floor(context);
		clear(context);
		UUID brood = UUID.randomUUID();
		TomatoSeedEntity a = seed(context, 0, 0, brood);
		TomatoSeedEntity b = seed(context, 3, 0, brood);
		TomatoSeedEntity c = seed(context, -3, 0, brood);
		TomatoSeedEntity d = seed(context, 0, 3, brood);
		ServerWorld w = context.getWorld();
		context.runAtTick(6, () -> {
			w.setBlockState(context.getAbsolutePos(new BlockPos(0, 1, 0)), Blocks.FIRE.getDefaultState());
			w.setBlockState(context.getAbsolutePos(new BlockPos(3, 1, 0)), Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true));
			w.setBlockState(context.getAbsolutePos(new BlockPos(-3, 1, 0)), Blocks.LAVA.getDefaultState());
			w.setBlockState(context.getAbsolutePos(new BlockPos(0, 1, 3)), Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, false));
		});
		context.runAtTick(14, () -> {
			context.assertTrue(a.ended(), "fire did not burn it");
			context.assertTrue(b.ended(), "a lit campfire did not burn it");
			context.assertTrue(c.ended(), "lava did not burn it");
			context.assertTrue(!d.ended(), "an unlit campfire burned it");
			for (BlockPos p : new BlockPos[] {new BlockPos(0, 1, 0), new BlockPos(3, 1, 0), new BlockPos(-3, 1, 0), new BlockPos(0, 1, 3)}) {
				w.setBlockState(context.getAbsolutePos(p), Blocks.AIR.getDefaultState());
			}
			clear(context);
			context.complete();
		});
	}

	/** A seed left long enough swells (the tell) and the devil climbs out of it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_back", tickLimit = 60)
	public void one_seed_left_brings_it_back(TestContext context) {
		floor(context);
		clear(context);
		TomatoSeedEntity s = seed(context, 0, 0, UUID.randomUUID());
		context.runAtTick(8, () -> {
			context.assertTrue(s.landed(), "the seed did not land");
			s.lain(TomatoSeedEntity.COMEBACK - 1);
		});
		context.runAtTick(11, () -> {
			context.assertTrue(s.swelling() >= 0, "it did not start to swell");
			context.assertTrue(devils(context).isEmpty(), "the devil came with no swell first");
			s.swellTo(TomatoSeedEntity.SWELL - 3);
		});
		context.runAtTick(18, () -> {
			List<TomatoDevilEntity> d = devils(context);
			context.assertTrue(d.size() == 1, "no devil came back: " + d.size());
			context.assertTrue(s.isRemoved(), "the seed is still there");
			context.assertTrue(d.get(0).state() == TomatoDevilEntity.EMERGE || d.get(0).state() == TomatoDevilEntity.WALK, "it did not climb out");
			clear(context);
			context.complete();
		});
	}

	/** When one swells, the others of its burst wither away. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_wither", tickLimit = 80)
	public void the_other_seeds_wither(TestContext context) {
		floor(context);
		clear(context);
		UUID brood = UUID.randomUUID();
		TomatoSeedEntity a = seed(context, 0, 0, brood);
		TomatoSeedEntity b = seed(context, 3, 1, brood);
		TomatoSeedEntity c = seed(context, -2, 3, brood);
		TomatoSeedEntity other = seed(context, 0, -4, UUID.randomUUID());
		context.runAtTick(8, () -> {
			a.lain(TomatoSeedEntity.COMEBACK - 1);
			b.lain(300);
			c.lain(300);
		});
		context.runAtTick(11, () -> {
			context.assertTrue(a.swelling() >= 0, "the first did not swell");
			context.assertTrue(b.withering() >= 0 && c.withering() >= 0, "the others did not wither");
			context.assertTrue(other.withering() < 0, "a seed of another burst withered");
		});
		context.runAtTick(11 + TomatoSeedEntity.WITHER + 3, () -> {
			context.assertTrue(b.isRemoved() && c.isRemoved(), "the withered seeds are still there");
			context.assertTrue(!other.isRemoved(), "a seed of another burst went");
			clear(context);
			context.complete();
		});
	}

	/** The devil that comes back bursts into one seed fewer, and theirs into one fewer again. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_fewer", tickLimit = 140)
	public void each_comeback_has_one_seed_fewer(TestContext context) {
		floor(context);
		clear(context);
		TomatoSeedEntity s = seed(context, 0, 0, UUID.randomUUID());
		s.next = 4;
		context.runAtTick(8, () -> s.swellTo(TomatoSeedEntity.SWELL - 2));
		context.runAtTick(14, () -> {
			List<TomatoDevilEntity> d = devils(context);
			context.assertTrue(d.size() == 1 && d.get(0).seeds == 4, "the comeback would not burst into 4: " + (d.isEmpty() ? "none" : d.get(0).seeds));
			d.get(0).damage(d.get(0).getDamageSources().generic(), 1000.0F);
		});
		context.runAtTick(100, () -> {
			List<TomatoSeedEntity> ss = seeds(context);
			context.assertTrue(ss.size() == 4, "it burst into " + ss.size() + ", not 4");
			for (TomatoSeedEntity x : ss) {
				context.assertTrue(x.next == 3, "its seeds would come back with " + x.next);
			}
			clear(context);
			context.complete();
		});
	}

	/** Every seed burned, nothing comes back; and the last of a line (no seeds) leaves none. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_dead", tickLimit = 160)
	public void all_seeds_burned_it_stays_dead(TestContext context) {
		floor(context);
		clear(context);
		TomatoDevilEntity d = devil(context, 0, 0);
		context.runAtTick(3, () -> d.damage(d.getDamageSources().generic(), 1000.0F));
		context.runAtTick(70, () -> {
			List<TomatoSeedEntity> ss = seeds(context);
			context.assertTrue(ss.size() == TomatoDevilEntity.SEEDS, "not five seeds to burn: " + ss.size());
			for (TomatoSeedEntity x : ss) {
				x.burn();
			}
			TomatoDevilEntity last = devil(context, 0, 0);
			last.seeds = 0;
			last.damage(last.getDamageSources().generic(), 1000.0F);
		});
		context.runAtTick(150, () -> {
			context.assertTrue(seeds(context).isEmpty(), "seeds are left: " + seeds(context).size());
			context.assertTrue(devils(context).isEmpty(), "a devil is about");
			clear(context);
			context.complete();
		});
	}

	/** A seed and a devil keep what they are through a save and a load. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_save", tickLimit = 30)
	public void seeds_survive_a_reload(TestContext context) {
		floor(context);
		clear(context);
		ServerWorld w = context.getWorld();
		UUID brood = UUID.randomUUID();
		TomatoSeedEntity s = seed(context, 0, 0, brood);
		s.next = 3;
		context.runAtTick(8, () -> {
			s.lain(777);
			NbtCompound nbt = new NbtCompound();
			s.saveSelfNbt(nbt);
			s.discard();
			var back = net.minecraft.entity.EntityType.getEntityFromNbt(nbt, w);
			context.assertTrue(back.isPresent() && back.get() instanceof TomatoSeedEntity, "the seed did not load");
			TomatoSeedEntity t = (TomatoSeedEntity) back.get();
			context.assertTrue(t.brood.equals(brood) && t.next == 3 && t.ground == 777 && t.landed(), "the seed lost what it was: " + t.brood + " " + t.next + " " + t.ground);
			context.assertTrue(!t.isRemoved() && w.spawnEntity(t), "the loaded seed could not be put back");
			TomatoDevilEntity d = devil(context, 4, 0);
			d.seeds = 2;
			d.setPersistent();
			NbtCompound dn = new NbtCompound();
			d.saveSelfNbt(dn);
			d.discard();
			var db = net.minecraft.entity.EntityType.getEntityFromNbt(dn, w);
			context.assertTrue(db.isPresent() && db.get() instanceof TomatoDevilEntity e && e.seeds == 2 && e.isPersistent(), "the devil lost what it was");
			context.assertTrue(TomatoSeedEntity.class.getSuperclass() == net.minecraft.entity.Entity.class, "a seed is a mob that could despawn");
			clear(context);
			context.complete();
		});
	}

	/** A Tomato kill feeds a hybrid's heart only a little: 8 XP (a comeback the same), and one kill leaves the heart at level 1. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_heart", tickLimit = 80)
	public void a_tomato_feeds_the_heart_a_little(TestContext context) {
		floor(context);
		clear(context);
		ServerPlayerEntity p = ForeverGameTests.player(context, "feeder");
		try {
			p.setAttached(Heart.MERGED, true);
			HeartLevel.set(p, 0);
			TomatoDevilEntity d = devil(context, 0, 0);
			context.assertTrue(d.getType().isIn(HeartLevel.DEVILS), "the Tomato is not in the devils tag");
			context.assertTrue(HeartLevel.WEAK_KILL_XP == 8 && HeartLevel.xpFor(d) == HeartLevel.WEAK_KILL_XP, "a Tomato is worth " + HeartLevel.xpFor(d));
			d.damage(p.getDamageSources().playerAttack(p), 1000.0F);
			context.assertTrue(d.isDead(), "the Tomato did not die");
			context.assertTrue(HeartLevel.xp(p) == 8, "the kill gave " + HeartLevel.xp(p) + " heart XP, not 8");
			context.assertTrue(HeartLevel.level(p) == 1, "one Tomato raised the heart's level");
		} catch (RuntimeException e) {
			ForeverGameTests.leave(context, p);
			clear(context);
			throw e;
		}
		TomatoSeedEntity s = seed(context, 4, 0, UUID.randomUUID());
		s.next = 3;
		context.runAtTick(10, () -> s.swellTo(TomatoSeedEntity.SWELL - 2));
		context.runAtTick(16, () -> {
			TomatoDevilEntity back = devils(context).isEmpty() ? null : devils(context).get(0);
			context.assertTrue(back != null && HeartLevel.xpFor(back) == HeartLevel.WEAK_KILL_XP, "a comeback is not worth 8: " + (back == null ? "none" : HeartLevel.xpFor(back)));
			ForeverGameTests.leave(context, p);
			clear(context);
			context.complete();
		});
	}

	/** The world never just puts one down: it is in no biome's monster list; a spawn egg (or /summon) still puts one down at once, with no quiet spell. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_spawn", tickLimit = 30)
	public void it_comes_only_by_its_arrival(TestContext context) {
		floor(context);
		clear(context);
		ServerWorld w = context.getWorld();
		var biomes = w.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME);
		for (var key : List.of(net.minecraft.world.biome.BiomeKeys.PLAINS, net.minecraft.world.biome.BiomeKeys.FOREST, net.minecraft.world.biome.BiomeKeys.DESERT)) {
			var biome = biomes.get(key);
			boolean listed = biome != null && biome.getSpawnSettings().getSpawnEntries(net.minecraft.entity.SpawnGroup.MONSTER).getEntries().stream()
					.anyMatch(entry -> entry.type == Devils.TOMATO_DEVIL);
			context.assertTrue(!listed, "the Tomato is in " + key.getValue() + "'s monster list");
		}
		ServerPlayerEntity p = standing(context, "egged", 0, -6);
		try {
			p.removeAttached(TomatoArrivals.QUIET_UNTIL);
			TomatoDevilEntity d = devil(context, 0, 0);
			context.assertTrue(d.isAlive() && !d.isRemoved(), "a devil could not be put down at once");
			context.assertTrue(p.getAttached(TomatoArrivals.QUIET_UNTIL) == null, "a devil put down by hand started a quiet spell");
		} finally {
			ForeverGameTests.leave(context, p);
			clear(context);
		}
		context.complete();
	}

	// ---- The arrival (TomatoArrivals, TomatoArrivalEntity).

	/** A stone plain 45 blocks across at the test's height with the sky clear over it, every Tomato thing within 140 blocks gone. */
	private static void plain(TestContext context) {
		ServerWorld w = context.getWorld();
		for (int x = -22; x <= 22; x++) {
			for (int z = -22; z <= 22; z++) {
				w.setBlockState(context.getAbsolutePos(new BlockPos(x, 0, z)), Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
				// (Up to whatever is highest over the column: something another test left high up would roof it.)
				BlockPos col = context.getAbsolutePos(new BlockPos(x, 0, z));
				int top = Math.max(col.getY() + 26, w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ()));
				for (int y = 1; y <= top - col.getY(); y++) {
					BlockPos b = col.up(y);
					if (!w.getBlockState(b).isAir()) {
						w.setBlockState(b, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
		clearTomatoes(context);
	}

	private static void clearTomatoes(TestContext context) {
		Box all = new Box(at(context, 0, 0), at(context, 0, 0)).expand(140.0, 60.0, 140.0);
		for (var e : context.getWorld().getEntitiesByClass(net.minecraft.entity.Entity.class, all,
				e -> e instanceof TomatoDevilEntity || e instanceof TomatoSeedEntity || e instanceof TomatoSpitEntity || e instanceof TomatoArrivalEntity)) {
			e.discard();
		}
	}

	/** The world at {@code timeOfDay} on Normal (what it was put back by the returned call). */
	private static Runnable world(TestContext context, long timeOfDay) {
		ServerWorld w = context.getWorld();
		long was = w.getTimeOfDay();
		var difficulty = w.getDifficulty();
		w.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		w.setTimeOfDay(timeOfDay);
		return () -> {
			w.setTimeOfDay(was);
			w.getServer().setDifficulty(difficulty, true);
		};
	}

	/** The second night (night 1), at midnight. */
	private static final long SECOND_NIGHT = 24000L + 18000L;

	/** Why the last forced roll brought nothing. */
	private static String lastWhy = "";

	/** A roll won for {@code p} (mob spawning on only for the call, so nothing else spawns in the tests). */
	private static TomatoArrivalEntity force(TestContext context, ServerPlayerEntity p) {
		var rule = context.getWorld().getGameRules().get(net.minecraft.world.GameRules.DO_MOB_SPAWNING);
		boolean was = rule.get();
		rule.set(true, context.getWorld().getServer());
		try {
			TomatoArrivalEntity a = TomatoArrivals.arrive(p);
			if (a == null) {
				String why = TomatoArrivals.whyNot(p);
				lastWhy = why != null ? why : TomatoArrivals.whyNoSpot(p);
			}
			return a;
		} finally {
			rule.set(was, context.getWorld().getServer());
		}
	}

	private static ServerPlayerEntity arrivalPlayer(TestContext context, String name, double x, double z) {
		ServerPlayerEntity p = standing(context, name, x, z);
		p.removeAttached(TomatoArrivals.QUIET_UNTIL);
		return p;
	}

	/** On the world's first night nothing comes; on its second, one may. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_arrive_first", tickLimit = 20)
	public void no_arrival_before_the_second_night(TestContext context) {
		plain(context);
		ServerPlayerEntity p = arrivalPlayer(context, "newcomer", 0, 0);
		Runnable back = world(context, 18000L);
		try {
			context.assertTrue(force(context, p) == null, "an arrival came on the world's first night");
			context.getWorld().setTimeOfDay(SECOND_NIGHT);
			context.assertTrue(force(context, p) != null, "no arrival could come on the second night" + ": " + lastWhy);
		} finally {
			back.run();
			ForeverGameTests.leave(context, p);
			clearTomatoes(context);
		}
		context.complete();
	}

	/** Under the open sky at night: it comes up 14-18 blocks off, on the ground, in front of the player, and they get a quiet spell. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_arrive_sky", tickLimit = 20)
	public void an_arrival_comes_at_night_under_open_sky(TestContext context) {
		plain(context);
		ServerPlayerEntity p = arrivalPlayer(context, "walker", 0, 0);
		Runnable back = world(context, SECOND_NIGHT);
		try {
			TomatoArrivalEntity a = force(context, p);
			context.assertTrue(a != null, "no arrival under the open sky at night" + ": " + lastWhy);
			double d = Math.hypot(a.getX() - p.getX(), a.getZ() - p.getZ());
			context.assertTrue(d >= TomatoArrivals.NEAR_MIN - 0.8 && d <= TomatoArrivals.NEAR_MAX + 0.8, "it came " + d + " blocks off, not 14-18");
			context.assertTrue(Math.abs(a.getY() - p.getY()) < 0.01, "it is not on the ground: " + a.getY() + " vs " + p.getY());
			Vec3d look = p.getRotationVector().multiply(1.0, 0.0, 1.0).normalize();
			Vec3d to = new Vec3d(a.getX() - p.getX(), 0.0, a.getZ() - p.getZ()).normalize();
			context.assertTrue(look.dotProduct(to) > Math.cos(Math.toRadians(56.0)), "it did not come in front of the player");
			Long quiet = p.getAttached(TomatoArrivals.QUIET_UNTIL);
			context.assertTrue(quiet != null && quiet == TomatoArrivals.night(context.getWorld()) + TomatoArrivals.QUIET_NIGHTS, "no quiet spell: " + quiet);
		} finally {
			back.run();
			ForeverGameTests.leave(context, p);
			clearTomatoes(context);
		}
		context.complete();
	}

	/** Under a roof, or by day, nothing comes. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_arrive_roof", tickLimit = 20)
	public void no_arrival_underground_or_by_day(TestContext context) {
		plain(context);
		ServerPlayerEntity p = arrivalPlayer(context, "miner", 0, 0);
		BlockPos roof = context.getAbsolutePos(new BlockPos(0, 4, 0));
		Runnable back = world(context, SECOND_NIGHT);
		try {
			context.getWorld().setBlockState(roof, Blocks.STONE.getDefaultState());
			context.assertTrue(force(context, p) == null, "an arrival came at someone under a roof");
			context.getWorld().setBlockState(roof, Blocks.AIR.getDefaultState());
			context.getWorld().setTimeOfDay(24000L + 6000L);
			context.assertTrue(force(context, p) == null, "an arrival came by day");
		} finally {
			context.getWorld().setBlockState(roof, Blocks.AIR.getDefaultState());
			back.run();
			ForeverGameTests.leave(context, p);
			clearTomatoes(context);
		}
		context.complete();
	}

	/** Never within 128 blocks of a Tomato Devil or one of its seeds. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_arrive_clear", tickLimit = 20)
	public void none_within_128_blocks_of_another_or_its_seeds(TestContext context) {
		plain(context);
		ServerPlayerEntity p = arrivalPlayer(context, "neighbour", 0, 0);
		Runnable back = world(context, SECOND_NIGHT);
		try {
			TomatoSeedEntity s = seed(context, 10, 10, UUID.randomUUID());
			context.assertTrue(force(context, p) == null, "an arrival came near a seed");
			s.discard();
			TomatoDevilEntity d = devil(context, -12, 12);
			d.setAiDisabled(true);
			context.assertTrue(force(context, p) == null, "an arrival came near a Tomato Devil");
			d.discard();
			context.assertTrue(force(context, p) != null, "with the seed and the devil gone, still no arrival" + ": " + lastWhy);
		} finally {
			back.run();
			ForeverGameTests.leave(context, p);
			clearTomatoes(context);
		}
		context.complete();
	}

	/** Two players together: one arrival, and both get the quiet spell. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_arrive_share", tickLimit = 20)
	public void players_together_share_one(TestContext context) {
		plain(context);
		ServerPlayerEntity a = arrivalPlayer(context, "first", 0, 0);
		ServerPlayerEntity b = arrivalPlayer(context, "second", 3, 0);
		Runnable back = world(context, SECOND_NIGHT);
		try {
			context.assertTrue(force(context, a) != null, "no arrival for the first" + ": " + lastWhy);
			context.assertTrue(force(context, b) == null, "a second arrival came for the one beside them");
			context.assertTrue(TomatoArrivals.quiet(b), "the one beside them got no quiet spell");
			context.assertTrue(context.getWorld().getEntitiesByClass(TomatoArrivalEntity.class, area(context).expand(10.0), e -> true).size() == 1, "not one arrival");
		} finally {
			back.run();
			ForeverGameTests.leave(context, a);
			ForeverGameTests.leave(context, b);
			clearTomatoes(context);
		}
		context.complete();
	}

	/** In peace, or with mob spawning off, nothing comes. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_arrive_peace", tickLimit = 20)
	public void peaceful_and_mob_spawning_off_mean_none(TestContext context) {
		plain(context);
		ServerPlayerEntity p = arrivalPlayer(context, "peaceful", 0, 0);
		Runnable back = world(context, SECOND_NIGHT);
		var rule = context.getWorld().getGameRules().get(net.minecraft.world.GameRules.DO_MOB_SPAWNING);
		boolean was = rule.get();
		try {
			context.getWorld().getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
			context.assertTrue(force(context, p) == null, "an arrival came in peace");
			context.getWorld().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
			rule.set(false, context.getWorld().getServer());
			context.assertTrue(TomatoArrivals.arrive(p) == null, "an arrival came with mob spawning off");
		} finally {
			rule.set(was, context.getWorld().getServer());
			back.run();
			ForeverGameTests.leave(context, p);
			clearTomatoes(context);
		}
		context.complete();
	}

	/**
	 * The arrival: nothing to hit and nothing hurt till the burst (7 s), then the devil stands where it was, every eye on the player; no
	 * block changed.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_arrive_eight", tickLimit = 200)
	public void the_arrival_has_its_eight_seconds(TestContext context) {
		context.assertTrue(TomatoArrivalEntity.EYES == 40 && TomatoArrivalEntity.BULGE == 80 && TomatoArrivalEntity.BURST == 140, "the arrival's times are not 2, 4 and 7 s");
		plain(context);
		ServerWorld w = context.getWorld();
		ServerPlayerEntity p = arrivalPlayer(context, "watcher", 0, 0);
		Runnable back = world(context, SECOND_NIGHT);
		TomatoArrivalEntity a = force(context, p);
		if (a == null) {
			back.run();
			ForeverGameTests.leave(context, p);
			throw new net.minecraft.test.GameTestException("no arrival: " + lastWhy);
		}
		Vec3d spot = a.getPos();
		BlockPos c = a.getBlockPos();
		java.util.Map<BlockPos, net.minecraft.block.BlockState> blocks = new java.util.HashMap<>();
		for (BlockPos b : BlockPos.iterate(c.add(-3, -2, -3), c.add(3, 4, 3))) {
			blocks.put(b.toImmutable(), w.getBlockState(b));
		}
		Box near = new Box(spot, spot).expand(4.0);
		Runnable end = () -> {
			back.run();
			ForeverGameTests.leave(context, p);
			clearTomatoes(context);
		};
		for (int tick = 2; tick < TomatoArrivalEntity.BURST - 2; tick += 10) {
			context.runAtTick(tick, () -> {
				try {
					context.assertTrue(!a.canHit() && !a.damage(w.getDamageSources().playerAttack(p), 100.0F) && !a.isRemoved(), "the arrival could be hit, or ended early");
					context.assertTrue(w.getEntitiesByClass(TomatoDevilEntity.class, near, e -> true).isEmpty(), "the devil was there before the burst");
					context.assertTrue(p.getHealth() == p.getMaxHealth(), "the player was hurt before the burst");
				} catch (RuntimeException e) {
					end.run();
					throw e;
				}
			});
		}
		context.runAtTick(TomatoArrivalEntity.BURST + 6, () -> {
			try {
				List<TomatoDevilEntity> ds = w.getEntitiesByClass(TomatoDevilEntity.class, near, e -> true);
				context.assertTrue(a.isRemoved(), "the arrival is still there after the burst");
				context.assertTrue(ds.size() == 1 && ds.get(0).getPos().distanceTo(spot) < 1.0, "the devil does not stand where it came up: " + ds.size());
				context.assertTrue(ds.get(0).noticedAt() >= 0, "its eyes did not snap to the player");
				for (var e : blocks.entrySet()) {
					context.assertTrue(w.getBlockState(e.getKey()) == e.getValue(), "the arrival changed a block at " + e.getKey());
				}
			} finally {
				end.run();
			}
			context.complete();
		});
	}

	/** After an arrival, everyone near it gets none that night or the next three; the fourth night it may come again. The spell is saved on the player. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_arrive_quiet", tickLimit = 20)
	public void after_an_arrival_the_next_nights_are_quiet(TestContext context) {
		plain(context);
		ServerPlayerEntity p = arrivalPlayer(context, "survivor", 0, 0);
		Runnable back = world(context, SECOND_NIGHT);
		try {
			context.assertTrue(TomatoArrivals.QUIET_NIGHTS == 3, "the quiet spell is not three nights");
			context.assertTrue(force(context, p) != null, "no first arrival" + ": " + lastWhy);
			clearTomatoes(context);
			// Saved on the player: written out and read back in, it is still there.
			NbtCompound nbt = new NbtCompound();
			p.writeNbt(nbt);
			p.removeAttached(TomatoArrivals.QUIET_UNTIL);
			p.readNbt(nbt);
			context.assertTrue(TomatoArrivals.quiet(p), "the quiet spell did not survive a save");
			context.assertTrue(force(context, p) == null, "a second arrival came the same night");
			for (int n = 1; n <= TomatoArrivals.QUIET_NIGHTS; n++) {
				context.getWorld().setTimeOfDay(SECOND_NIGHT + n * 24000L);
				context.assertTrue(force(context, p) == null, "an arrival came " + n + " nights later, in the quiet spell");
			}
			context.getWorld().setTimeOfDay(SECOND_NIGHT + (TomatoArrivals.QUIET_NIGHTS + 1) * 24000L);
			context.assertTrue(force(context, p) != null, "no arrival after the quiet spell" + ": " + lastWhy);
		} finally {
			back.run();
			ForeverGameTests.leave(context, p);
			clearTomatoes(context);
		}
		context.complete();
	}

	private static ServerPlayerEntity standing(TestContext context, String name, double x, double z) {
		ServerPlayerEntity p = ForeverGameTests.player(context, name);
		Vec3d a = at(context, x, z);
		p.teleport(context.getWorld(), a.x, a.y, a.z, 180.0F, 0.0F);
		p.setNoGravity(false);
		return p;
	}

	/** The hop: nothing during its squat (14 ticks or more); landing, it hurts and throws everyone near it back about 5 blocks; further off, nobody. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_hop", tickLimit = 120)
	public void the_hop_has_a_tell_and_throws(TestContext context) {
		context.assertTrue(TomatoDevilEntity.HOP_TELL >= 14, "the hop's squat is shorter than 14 ticks");
		floor(context);
		clear(context);
		ServerWorld w = context.getWorld();
		TomatoDevilEntity d = devil(context, 0, 0);
		d.setAiDisabled(true);
		var near = EntityType.SHEEP.create(w);
		Vec3d a = at(context, 2.6, 0);
		near.refreshPositionAndAngles(a.x, a.y, a.z, 0.0F, 0.0F);
		w.spawnEntity(near);
		var far = EntityType.SHEEP.create(w);
		Vec3d b = at(context, 0, -7.0);
		far.refreshPositionAndAngles(b.x, b.y, b.z, 0.0F, 0.0F);
		w.spawnEntity(far);
		near.setAiDisabled(false);
		// A hurt sheep panics and runs: with no walking speed it goes only where the hop throws it.
		near.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.0);
		far.setAiDisabled(true);
		Vec3d[] from = new Vec3d[1];
		context.runAtTick(5, () -> {
			d.setAiDisabled(false);
			d.begin(TomatoDevilEntity.HOP);
		});
		context.runAtTick(5 + TomatoDevilEntity.HOP_TELL, () -> context.assertTrue(near.getHealth() == near.getMaxHealth(), "the hop landed during its squat"));
		context.runAtTick(5 + TomatoDevilEntity.HOP_LAND + 2, () -> {
			context.assertTrue(near.getHealth() <= near.getMaxHealth() - TomatoDevilEntity.HOP_DAMAGE + 0.01F, "the hop did not hurt what was near: " + near.getHealth());
			context.assertTrue(far.getHealth() == far.getMaxHealth(), "the hop hurt something 7 blocks off");
			from[0] = d.getPos();
		});
		context.runAtTick(5 + TomatoDevilEntity.HOP_LAND + 30, () -> {
			double thrown = Math.hypot(near.getX() - from[0].x, near.getZ() - from[0].z) - 2.6;
			context.assertTrue(thrown >= 3.5 && thrown <= 8.0, "the hop threw it " + thrown + " blocks, not about 5");
			near.discard();
			far.discard();
			clear(context);
			context.complete();
		});
	}

	/** Fought standing still in front of it for half a minute: never one move three times running, and more than one kind of move. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_three", tickLimit = 700)
	public void no_move_three_times_running(TestContext context) {
		floor(context);
		clear(context);
		TomatoDevilEntity d = devil(context, 0, 0);
		ServerPlayerEntity p = standing(context, "hugger", 0, -3.2);
		List<Integer> moves = new java.util.ArrayList<>();
		d.moveListener = moves::add;
		for (int t = 1; t < 640; t++) {
			context.runAtTick(t, () -> {
				p.setHealth(p.getMaxHealth());
				Vec3d a = at(context, 0, -3.2);
				p.requestTeleport(a.x, a.y, a.z);
			});
		}
		context.runAtTick(650, () -> {
			ForeverGameTests.leave(context, p);
			clear(context);
			context.assertTrue(moves.size() >= 5, "too few moves to judge: " + moves);
			for (int i = 2; i < moves.size(); i++) {
				context.assertTrue(!(moves.get(i).equals(moves.get(i - 1)) && moves.get(i).equals(moves.get(i - 2))), "a move three times running: " + moves);
			}
			context.assertTrue(moves.contains(TomatoDevilEntity.HOP), "hugging it never brought the hop: " + moves);
			context.complete();
		});
	}

	/** From 5 blocks, a sidestep at a walk once its aim is set slips between the seeds; standing still, they hit. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_sidestep", tickLimit = 160)
	public void a_sidestep_clears_the_spit(TestContext context) {
		floor(context);
		clear(context);
		TomatoDevilEntity d = devil(context, 0, 0);
		ServerPlayerEntity p = standing(context, "dodger", 0, 5.0);
		// (Facing +Z: the player 5 blocks in front.) A walk is 0.216 blocks a tick: five ticks of it once the aim is set, then stand.
		double walk = 0.216;
		int steps = 5;
		context.runAtTick(3, () -> {
			d.aimAt(0.0F);
			d.begin(TomatoDevilEntity.SPIT);
		});
		float[] hp = new float[1];
		for (int t = 3; t < 3 + TomatoDevilEntity.SPIT_END + 12; t++) {
			int tt = t;
			context.runAtTick(t, () -> {
				int into = tt - 3;
				if (into == 1) {
					hp[0] = p.getHealth();
				}
				if (into > TomatoDevilEntity.SPIT_AIM + 2 && into <= TomatoDevilEntity.SPIT_AIM + 2 + steps) {
					p.requestTeleport(p.getX() + walk, p.getY(), p.getZ());
				}
			});
		}
		context.runAtTick(3 + TomatoDevilEntity.SPIT_END + 14, () -> {
			context.assertTrue(p.getHealth() == hp[0], "a sidestep of " + (walk * steps) + " blocks was hit: " + p.getHealth() + " of " + hp[0]);
			ForeverGameTests.leave(context, p);
			clear(context);
			context.complete();
		});
	}

	/** A set-down Pochita near a seed runs to it and eats it (about 30 ticks of chewing). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_pochita", tickLimit = 260)
	public void pochita_eats_a_seed(TestContext context) {
		floor(context);
		clear(context);
		ServerWorld w = context.getWorld();
		TomatoSeedEntity s = seed(context, 0, 0, UUID.randomUUID());
		var pochita = io.github.kortev.chainsawman.ChainsawMan.POCHITA_ENTITY.create(w);
		Vec3d a = at(context, 7, 3);
		pochita.refreshPositionAndAngles(a.x, a.y, a.z, 0.0F, 0.0F);
		pochita.setTamed(true, true);
		// (Someone's: a tamed one whose owner is not about sits where he is, as a wolf does.)
		ServerPlayerEntity owner = standing(context, "owner", -6, -6);
		pochita.setOwner(owner);
		w.spawnEntity(pochita);
		boolean[] chewing = new boolean[1];
		for (int t = 10; t < 240; t += 2) {
			context.runAtTick(t, () -> chewing[0] |= !s.isRemoved() && s.chewed() > 0);
		}
		context.runAtTick(240, () -> {
			context.assertTrue(chewing[0], "Pochita never chewed it");
			context.assertTrue(s.isRemoved(), "the seed is still there");
			context.assertTrue(devils(context).isEmpty(), "something came back");
			ForeverGameTests.leave(context, owner);
			pochita.discard();
			clear(context);
			context.complete();
		});
	}

	/** A seed too far off for him to smell: he follows you over to it, and eats it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_pochita2", tickLimit = 500)
	public void pochita_follows_you_to_a_seed(TestContext context) {
		floor(context);
		clear(context);
		ServerWorld w = context.getWorld();
		TomatoSeedEntity s = seed(context, 10, 10, UUID.randomUUID());
		var pochita = io.github.kortev.chainsawman.ChainsawMan.POCHITA_ENTITY.create(w);
		Vec3d a = at(context, -10, -10);
		pochita.refreshPositionAndAngles(a.x, a.y, a.z, 0.0F, 0.0F);
		pochita.setTamed(true, true);
		ServerPlayerEntity owner = standing(context, "owner2", 8, 8);
		pochita.setOwner(owner);
		pochita.setSitting(true);
		w.spawnEntity(pochita);
		context.runAtTick(20, () -> pochita.setSitting(false));
		context.runAtTick(480, () -> {
			String where = String.format("%.1f %.1f", pochita.getX() - a.x, pochita.getZ() - a.z);
			ForeverGameTests.leave(context, owner);
			pochita.discard();
			clear(context);
			context.assertTrue(s.isRemoved(), "the seed is still there; Pochita moved " + where);
			context.complete();
		});
	}

	/** Pochita held as a chainsaw shreds a seed with a hit or a slash; so do Chainsaw Man's saws. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_shred", tickLimit = 60)
	public void a_chainsaw_shreds_a_seed(TestContext context) {
		floor(context);
		clear(context);
		UUID brood = UUID.randomUUID();
		TomatoSeedEntity a = seed(context, 0, 0, brood);
		TomatoSeedEntity b = seed(context, 0, -2.0, brood);
		ServerPlayerEntity p = standing(context, "shredder", 0, -4.0);
		try {
			p.setStackInHand(Hand.MAIN_HAND, new ItemStack(io.github.kortev.chainsawman.ChainsawMan.POCHITA));
			a.damage(p.getDamageSources().playerAttack(p), 5.0F);
			context.assertTrue(a.isRemoved(), "a hit with Pochita held did not shred the seed");
			p.setPitch(40.0F);
			p.setYaw(0.0F);
			context.assertTrue(Saw.slash(p), "the held chainsaw's slash did not come");
			context.assertTrue(b.isRemoved(), "the held chainsaw's slash did not shred the seed in front");
		} finally {
			ForeverGameTests.leave(context, p);
			clear(context);
		}
		context.complete();
	}

	/** Three hits from a player end a seed: a sword cuts it smaller, a fist splits it open, an arrow pops it. Cuts survive a save. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_sword", tickLimit = 40)
	public void three_hits_end_a_seed(TestContext context) {
		floor(context);
		clear(context);
		TomatoSeedEntity s = seed(context, 0, 0, UUID.randomUUID());
		ServerPlayerEntity p = standing(context, "swordsman", 0, -2.0);
		try {
			p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			s.damage(p.getDamageSources().playerAttack(p), 6.0F);
			context.assertTrue(!s.isRemoved() && !s.ended() && s.cuts() == 1, "one hit: the seed is gone or not cut (" + s.cuts() + ")");
			context.assertTrue(context.getWorld().getTime() - s.shudderAt() < 2, "the seed did not shudder");
			p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
			s.damage(p.getDamageSources().playerAttack(p), 1.0F);
			context.assertTrue(!s.isRemoved() && !s.ended() && s.cuts() == 2, "two hits: the seed is gone or not cut (" + s.cuts() + ")");
			net.minecraft.nbt.NbtCompound saved = new net.minecraft.nbt.NbtCompound();
			s.writeNbt(saved);
			TomatoSeedEntity again = io.github.kortev.chainsawman.devil.Devils.TOMATO_SEED.create(context.getWorld());
			again.readNbt(saved);
			context.assertTrue(again.cuts() == 2, "the cuts did not survive a save: " + again.cuts());
			var arrow = new net.minecraft.entity.projectile.ArrowEntity(EntityType.ARROW, context.getWorld());
			arrow.setOwner(p);
			s.damage(p.getDamageSources().arrow(arrow, p), 4.0F);
			context.assertTrue(s.isRemoved(), "the third hit (an arrow) did not pop the seed");
			context.assertTrue(devils(context).isEmpty(), "something came back");
		} finally {
			ForeverGameTests.leave(context, p);
			clear(context);
		}
		context.complete();
	}

	/** Cutting up the seed that is swelling stops the comeback. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_sword2", tickLimit = 80)
	public void cutting_the_swelling_seed_stops_the_comeback(TestContext context) {
		floor(context);
		clear(context);
		TomatoSeedEntity s = seed(context, 0, 0, UUID.randomUUID());
		ServerPlayerEntity p = standing(context, "cutter", 0, -2.0);
		context.runAtTick(4, () -> s.swellTo(TomatoSeedEntity.SWELL - 20));
		for (int i = 0; i < TomatoSeedEntity.CUTS; i++) {
			context.runAtTick(8 + 3 * i, () -> {
				context.assertTrue(s.swelling() >= 0 || s.isRemoved(), "the seed stopped swelling by itself");
				s.damage(p.getDamageSources().playerAttack(p), 1.0F);
			});
		}
		context.runAtTick(60, () -> {
			context.assertTrue(s.isRemoved(), "the swelling seed is still there after " + TomatoSeedEntity.CUTS + " hits");
			context.assertTrue(devils(context).isEmpty(), "the devil came back");
			ForeverGameTests.leave(context, p);
			clear(context);
			context.complete();
		});
	}

	/** The devil that climbs out of a seed has 60% of its health: a real fight, not a full one. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_comeback", tickLimit = 40)
	public void a_comeback_has_less_health(TestContext context) {
		floor(context);
		clear(context);
		TomatoSeedEntity s = seed(context, 0, 0, UUID.randomUUID());
		context.runAtTick(6, () -> s.swellTo(TomatoSeedEntity.SWELL - 2));
		context.runAtTick(12, () -> {
			List<TomatoDevilEntity> d = devils(context);
			context.assertTrue(d.size() == 1, "no comeback");
			float want = d.get(0).getMaxHealth() * TomatoDevilEntity.COMEBACK_HEALTH;
			context.assertTrue(Math.abs(d.get(0).getHealth() - want) < 0.01F && TomatoDevilEntity.COMEBACK_HEALTH == 0.6F,
					"the comeback has " + d.get(0).getHealth() + " health, not " + want);
			clear(context);
			context.complete();
		});
	}

	/** The Chainsaw Man form's saws cut it like any mob, and a kill by them bursts it into its seeds (it is not torn away). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tomato_saws", tickLimit = 100)
	public void saws_cut_it(TestContext context) {
		clear(context);
		ServerWorld w = context.getWorld();
		ServerPlayerEntity p = ForeverGameTests.player(context, "sawer");
		p.setAttached(Heart.MERGED, true);
		Form.start(p);
		Vec3d at = context.getAbsolute(new Vec3d(0.5, 39.0, 0.5 + 2.6));
		TomatoDevilEntity d = Devils.TOMATO_DEVIL.create(w);
		d.refreshPositionAndAngles(at.x, at.y, at.z, 180.0F, 0.0F);
		d.setNoGravity(true);
		d.setAiDisabled(true);
		w.spawnEntity(d);
		try {
			context.assertTrue(Saw.slash(p), "the slash did not come");
			context.assertTrue(d.getHealth() <= d.getMaxHealth() - 10.0F, "the saws did not cut it: " + d.getHealth());
		} catch (RuntimeException e) {
			Form.end(p);
			ForeverGameTests.leave(context, p);
			d.discard();
			throw e;
		}
		context.runAtTick(Saw.SLASH_EVERY + 4, () -> {
			d.setHealth(1.0F);
			d.timeUntilRegen = 0;
			context.assertTrue(Saw.slash(p), "the second slash did not come");
			context.assertTrue(d.isDead() && !d.isRemoved(), "a kill by the saws tore it away instead of letting it burst");
		});
		context.runAtTick(Saw.SLASH_EVERY + 4 + TomatoDevilEntity.BURST_AT + 4, () -> {
			int n = w.getEntitiesByClass(TomatoSeedEntity.class, new Box(at, at).expand(12.0), s -> true).size();
			context.assertTrue(n == TomatoDevilEntity.SEEDS, "the saws' kill did not burst it into five seeds: " + n);
			for (TomatoSeedEntity s : w.getEntitiesByClass(TomatoSeedEntity.class, new Box(at, at).expand(30.0, 60.0, 30.0), s -> true)) {
				s.discard();
			}
			Form.end(p);
			ForeverGameTests.leave(context, p);
			context.complete();
		});
	}
}
