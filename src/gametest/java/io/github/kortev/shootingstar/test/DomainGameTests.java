package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.GojoDamage;
import io.github.kortev.gojolander.GojoRules;
import io.github.kortev.gojolander.Owner;
import io.github.kortev.gojolander.domain.DomainManager;
import io.github.kortev.gojolander.domain.DomainTimeline;
import io.github.kortev.gojolander.sight.SixEyes;
import io.github.kortev.shootingstar.OwnerOnly;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Gojolander's Unlimited Void on flat stone: who may craft the blindfold and cast it, nobody's and someone's; a whole Domain, catching what is
 * in it and not what is not, holding it still (in the air too), letting nobody and nothing across the barrier either
 * way, and letting them go dazed; taking them down a step at a time, by the void, through armour and Resistance, to
 * exactly half a heart and never further; and a Domain ending early when its caster falls, or after a step.
 */
public class DomainGameTests implements FabricGameTest {
	/** The Domain these tests cast: small and short, so a whole one fits in a test. */
	private static final int RADIUS = 12;
	private static final int HOLD_SECONDS = 2;
	private static final int HOLD = HOLD_SECONDS * 20;
	/** How long a Domain test may wait for its ground to load before casting ({@link Marks}), at most. */
	private static final int WAIT = 4000;
	/** Held long enough for a step down before the last. */
	private static final int LONG_HOLD_SECONDS = 4;
	private static final int LONG_HOLD = LONG_HOLD_SECONDS * 20;
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

	/**
	 * The Six Eyes: its owner sees through walls with the blindfold on, as far as the rule says, and not with it off;
	 * nobody else does with it on; and with the rule at 0, not even he does.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_gojo_eyes", tickLimit = 20)
	public void sixEyesFollowTheBlindfold(TestContext context) {
		ServerWorld world = context.getWorld();
		world.getGameRules().get(GojoRules.SIX_EYES_RANGE).set(24, world.getServer());
		ServerPlayerEntity owner = player(context, owner(context), null);
		ServerPlayerEntity other = player(context, "not_the_owner", null);
		try {
			context.assertTrue(SixEyes.range(owner) == 0, "the owner saw through walls without the blindfold on");
			owner.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
			other.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
			context.assertTrue(SixEyes.range(owner) == 24, "the owner did not see 24 blocks with it on: " + SixEyes.range(owner));
			context.assertTrue(SixEyes.range(other) == 0, "someone else saw through walls with it on");
			world.getGameRules().get(GojoRules.SIX_EYES_RANGE).set(0, world.getServer());
			context.assertTrue(SixEyes.range(owner) == 0, "the owner saw with the rule at 0");
		} finally {
			world.getGameRules().get(GojoRules.SIX_EYES_RANGE).set(24, world.getServer());
			remove(context, owner, other);
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
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_gojo_domain", tickLimit = WAIT + DomainTimeline.VOID + HOLD + 80)
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

		DomainManager.Domain[] domain = new DomainManager.Domain[1];
		VillagerEntity[] villager = new VillagerEntity[1];
		Vec3d[] held = new Vec3d[3];
		ArrowEntity[] arrow = new ArrowEntity[1];
		Marks marks = new Marks();
		marks.at(DomainTimeline.CLOSE - 2, () -> {
			if (DomainManager.isHeld(cow) || DomainManager.isHeld(inside)) {
				problems.add("something was held before the barrier closed");
			}
			// Dropped from on high just before it closes: caught in the air.
			villager[0] = context.spawnEntity(EntityType.VILLAGER, relative(context, center.add(-3.0, 6.0, 3.0)));
		});
		marks.at(DomainTimeline.CLOSE + SLACK, () -> {
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
			marks.at(DomainTimeline.CLOSE + SLACK + step, () -> {
				if (!arrow[0].isRemoved()) {
					arrow[0].setPosition(center.add(-(RADIUS + 4.0) + 2.0 * step, 1.5, 0.0));
				}
			});
		}
		marks.at(DomainTimeline.CLOSE + 15, () -> {
			if (cow.getPos().squaredDistanceTo(held[0]) > 0.01) {
				problems.add("the cow moved while held: " + held[0] + " to " + cow.getPos());
			}
			if (villager[0].getPos().squaredDistanceTo(held[1]) > 0.01) {
				problems.add("the villager fell while held: " + held[1] + " to " + villager[0].getPos());
			}
			if (inside.getPos().squaredDistanceTo(held[2]) > 0.05) {
				problems.add("the caught player was not put back: " + held[2] + " to " + inside.getPos());
			}
			if (domain[0].contains(outside.getBoundingBox().getCenter())) {
				problems.add("the player outside got in");
			}
			if (!domain[0].contains(caster.getBoundingBox().getCenter())) {
				problems.add("the caster got out");
			}
			if (!arrow[0].isRemoved()) {
				problems.add("the arrow from outside was not stopped (at " + arrow[0].getPos().subtract(center) + " from the middle)");
			}
		});
		marks.at(DomainTimeline.collapse(HOLD) + SLACK, () -> {
			if (DomainManager.isHeld(cow) || DomainManager.isHeld(inside) || DomainManager.isHeld(villager[0])) {
				problems.add("something was still held after the barrier shattered");
			}
			if (!inside.hasStatusEffect(StatusEffects.SLOWNESS) || !inside.hasStatusEffect(StatusEffects.DARKNESS)) {
				problems.add("the caught player came out of it without being dazed");
			}
			if (!cow.hasStatusEffect(StatusEffects.SLOWNESS)) {
				problems.add("the cow came out of it without being slowed");
			}
			if (inside.getHealth() != 1.0F || cow.getHealth() != 1.0F) {
				problems.add("the void did not leave them at half a heart (" + inside.getHealth() + ", " + cow.getHealth() + ")");
			}
			if (!brokenByTheVoid(inside) || !brokenByTheVoid(cow)) {
				problems.add("what broke them was not the void (" + inside.getRecentDamageSource() + ", " + cow.getRecentDamageSource() + ")");
			}
			if (outside.getHealth() < outside.getMaxHealth()) {
				problems.add("the player outside was hurt");
			}
			if (outside.hasStatusEffect(StatusEffects.SLOWNESS)) {
				problems.add("the player outside was dazed");
			}
		});
		marks.at(DomainTimeline.end(HOLD) + SLACK, () -> {
			if (DomainManager.active().contains(domain[0])) {
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
		marks.run(context, center, List.of(caster, inside, outside, creative, cow), () -> {
			context.assertTrue(DomainManager.cast(caster), "the owner could not cast it");
			domain[0] = DomainManager.active().get(DomainManager.active().size() - 1);
		});
	}

	/** The caster falls in his own Domain: it shatters at once, and lets go of everyone, shaken but not broken. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_gojo_early", tickLimit = WAIT + DomainTimeline.VOID + HOLD + 60)
	public void endsWhenTheCasterFalls(TestContext context) {
		ServerWorld world = context.getWorld();
		rules(world, 0);
		Vec3d center = floor(context);
		ServerPlayerEntity caster = player(context, owner(context), center);
		caster.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
		CowEntity cow = context.spawnEntity(EntityType.COW, relative(context, center.add(5.0, 0.0, 0.0)));
		boolean[] caught = new boolean[1];
		Marks marks = new Marks();
		marks.at(DomainTimeline.CLOSE + SLACK, () -> {
			caught[0] = DomainManager.isHeld(cow);
			caster.kill();
		});
		marks.at(DomainTimeline.CLOSE + SLACK + 3, () -> {
			boolean still = DomainManager.isHeld(cow);
			float health = cow.getHealth();
			finish(context, center, caster);
			context.assertTrue(caught[0], "the cow was not caught");
			context.assertTrue(!still, "the cow was still held after the caster fell");
			context.assertTrue(health > 1.0F, "a Domain cut short broke the cow");
			context.complete();
		});
		marks.run(context, center, List.of(caster, cow), () -> context.assertTrue(DomainManager.cast(caster), "the owner could not cast it"));
	}

	/**
	 * Held longer (two steps: one halfway, the last as it lets go), it takes them down a step at a time, by the void
	 * itself, through anything: a player bare, one in netherite with Protection IV, Resistance V and absorption hearts,
	 * and a cow are each taken halfway down to half a heart by the first step, held where they were, and left at
	 * exactly half a heart by the last; one already below half a heart is not touched; nobody dies of it; and the next
	 * blow after it lands as it would have.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_gojo_steps", tickLimit = WAIT + DomainTimeline.VOID + LONG_HOLD + 60)
	public void breaksThemAStepAtATime(TestContext context) {
		ServerWorld world = context.getWorld();
		rules(world, 0, LONG_HOLD_SECONDS);
		Vec3d center = floor(context);
		ServerPlayerEntity bare = player(context, "bare", center.add(4.0, 0.0, 0.0));
		ServerPlayerEntity armoured = player(context, "armoured", center.add(-4.0, 0.0, 0.0));
		ServerPlayerEntity frail = player(context, "frail", center.add(0.0, 0.0, 4.0));
		armour(world, armoured);
		armoured.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 60, 4));
		armoured.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20 * 60, 1));
		frail.setHealth(0.5F);
		CowEntity cow = context.spawnEntity(EntityType.COW, relative(context, center.add(0.0, 0.0, -5.0)));
		List<String> problems = new ArrayList<>();
		for (TagKey<DamageType> tag : List.of(DamageTypeTags.BYPASSES_ARMOR, DamageTypeTags.BYPASSES_ENCHANTMENTS, DamageTypeTags.BYPASSES_EFFECTS,
				DamageTypeTags.BYPASSES_RESISTANCE, DamageTypeTags.BYPASSES_SHIELD, DamageTypeTags.BYPASSES_COOLDOWN, DamageTypeTags.NO_KNOCKBACK)) {
			if (!GojoDamage.unlimitedVoid(world).isIn(tag)) {
				problems.add("the void's damage is not in " + tag.id());
			}
		}
		int first = DomainTimeline.step(LONG_HOLD, 1);
		context.assertTrue(DomainTimeline.steps(LONG_HOLD) == 2 && first < DomainTimeline.collapse(LONG_HOLD),
				"a hold of " + LONG_HOLD_SECONDS + " s did not have one step before the last");
		DomainManager.Domain[] domain = new DomainManager.Domain[1];
		Vec3d[] held = new Vec3d[1];
		Marks marks = new Marks();
		marks.at(DomainTimeline.CLOSE + SLACK, () -> {
			for (var e : List.of(bare, armoured, frail, cow)) {
				if (!DomainManager.isHeld(e)) {
					problems.add(e.getName().getString() + " was not caught");
				}
			}
		});
		marks.at(first - 2, () -> {
			if (bare.getHealth() < bare.getMaxHealth() || cow.getHealth() < cow.getMaxHealth()) {
				problems.add("they were hurt before the first step (" + bare.getHealth() + ", " + cow.getHealth() + ")");
			}
			held[0] = bare.getPos();
		});
		marks.at(first + SLACK, () -> {
			// Halfway down: from 20 (bare), from 20 and 8 of absorption (armoured), from 10 (the cow), to half a heart.
			near(problems, "the bare player after the first step", bare.getHealth(), 10.5F);
			near(problems, "the armoured player after the first step", armoured.getHealth() + armoured.getAbsorptionAmount(), 14.5F);
			near(problems, "the cow after the first step", cow.getHealth(), 5.5F);
			near(problems, "the frail player after the first step", frail.getHealth(), 0.5F);
			if (!brokenByTheVoid(bare) || !brokenByTheVoid(armoured) || !brokenByTheVoid(cow)) {
				problems.add("the first step was not the void's");
			}
			if (bare.getPos().squaredDistanceTo(held[0]) > 0.01) {
				problems.add("the first step moved the bare player: " + held[0] + " to " + bare.getPos());
			}
		});
		marks.at(DomainTimeline.collapse(LONG_HOLD) + SLACK, () -> {
			for (var e : List.of(bare, armoured, cow)) {
				if (e.getHealth() != 1.0F || e.getAbsorptionAmount() != 0.0F) {
					problems.add(e.getName().getString() + " was not left at exactly half a heart (" + e.getHealth() + " + " + e.getAbsorptionAmount() + ")");
				}
			}
			near(problems, "the frail player after the last step", frail.getHealth(), 0.5F);
			for (var e : List.of(bare, armoured, frail, cow)) {
				if (!e.isAlive()) {
					problems.add(e.getName().getString() + " died of it");
				}
			}
			if (domain[0].standing()) {
				problems.add("the Domain was still standing after its hold");
			}
			// It leaves them no moment's grace: the next blow, however soon and however light, lands.
			bare.damage(world.getDamageSources().generic(), 0.5F);
			near(problems, "the bare player after a light blow just after it", bare.getHealth(), 0.5F);
		});
		// Over only once the last of it is gone, so the next test finds none running.
		marks.at(DomainTimeline.end(LONG_HOLD) + SLACK, () -> {
			if (DomainManager.active().contains(domain[0])) {
				problems.add("the Domain was still running after its end");
			}
			finish(context, center, bare, armoured, frail);
			context.assertTrue(problems.isEmpty(), String.join("; ", problems));
			context.complete();
		});
		marks.run(context, center, List.of(bare, armoured, frail, cow), () -> domain[0] = DomainManager.launch(world, center, null));
	}

	/** Cut short after its first step, it leaves them as far down as it had taken them, not broken, and only shaken. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_gojo_steps_early", tickLimit = WAIT + DomainTimeline.VOID + LONG_HOLD + 40)
	public void cutShortKeepsWhatItTook(TestContext context) {
		ServerWorld world = context.getWorld();
		rules(world, 0, LONG_HOLD_SECONDS);
		Vec3d center = floor(context);
		ServerPlayerEntity caught = player(context, "caught", center.add(4.0, 0.0, 0.0));
		DomainManager.Domain[] domain = new DomainManager.Domain[1];
		int first = DomainTimeline.step(LONG_HOLD, 1);
		boolean[] wasCaught = new boolean[1];
		float[] health = new float[1];
		boolean[] held = new boolean[1];
		boolean[] dazed = new boolean[1];
		Marks marks = new Marks();
		marks.at(DomainTimeline.CLOSE + SLACK, () -> wasCaught[0] = DomainManager.isHeld(caught));
		marks.at(first + SLACK, DomainManager::collapseAll);
		marks.at(first + SLACK + 3, () -> {
			health[0] = caught.getHealth();
			held[0] = DomainManager.isHeld(caught);
			dazed[0] = caught.hasStatusEffect(StatusEffects.SLOWNESS);
		});
		// Over only once the last of its shattering is gone, so the next test finds none running.
		marks.at(first + SLACK + 3 + DomainTimeline.SHATTER + SLACK, () -> {
			boolean running = DomainManager.active().contains(domain[0]);
			float after = caught.getHealth();
			finish(context, center, caught);
			context.assertTrue(wasCaught[0], "they were not caught");
			context.assertTrue(!held[0], "they were still held after it was cut short");
			context.assertTrue(Math.abs(health[0] - 10.5F) < 0.01F, "cut short after its first step, it did not leave them halfway down (" + health[0] + ")");
			context.assertTrue(dazed[0], "cut short, it did not leave them shaken");
			context.assertTrue(after == health[0], "the shattering took more from them (" + health[0] + " to " + after + ")");
			context.assertTrue(!running, "the Domain was still running after its shattering");
			context.complete();
		});
		marks.run(context, center, List.of(caught), () -> domain[0] = DomainManager.launch(world, center, null));
	}

	/**
	 * Whoever is holding on to him as it closes is spared, as in the show: a player crouched right against him is not
	 * caught; one standing beside him is, and so is one crouching a few blocks off.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "g_gojo_touch", tickLimit = WAIT + DomainTimeline.VOID + HOLD + 60)
	public void sparesWhoeverHoldsOnToHim(TestContext context) {
		ServerWorld world = context.getWorld();
		rules(world, 0);
		Vec3d center = floor(context);
		ServerPlayerEntity caster = player(context, owner(context), center);
		caster.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
		ServerPlayerEntity holding = player(context, "holding_on", center.add(0.8, 0.0, 0.0));
		ServerPlayerEntity beside = player(context, "standing_by", center.add(-0.9, 0.0, 0.0));
		ServerPlayerEntity off = player(context, "crouched_off", center.add(0.0, 0.0, 3.0));
		holding.setSneaking(true);
		off.setSneaking(true);
		boolean[] held = new boolean[3];
		Marks marks = new Marks();
		marks.at(DomainTimeline.CLOSE + SLACK, () -> {
			held[0] = DomainManager.isHeld(holding);
			held[1] = DomainManager.isHeld(beside);
			held[2] = DomainManager.isHeld(off);
		});
		marks.at(DomainTimeline.end(HOLD) + SLACK, () -> {
			float health = holding.getHealth();
			finish(context, center, caster, holding, beside, off);
			context.assertTrue(!held[0], "the player crouched against him was caught");
			context.assertTrue(held[1], "the player standing beside him was not caught");
			context.assertTrue(held[2], "the player crouching a few blocks off was not caught");
			context.assertTrue(health == holding.getMaxHealth(), "the one holding on to him was hurt (" + health + ")");
			context.complete();
		});
		marks.run(context, center, List.of(caster, holding, beside, off),
				() -> context.assertTrue(DomainManager.cast(caster), "the owner could not cast it"));
	}

	/**
	 * What a Domain test does, each at a tick counted from its cast. Out here its ground's chunks are still coming in
	 * while the test server races ahead, and whatever stands in a chunk not yet tracking entities is neither found nor
	 * caught: so it is cast only once all that stands there can be found (each put back first where the test stood it,
	 * should it have wandered off while it waited), and everything after counts from then.
	 */
	private static final class Marks {
		private final List<Map.Entry<Integer, Runnable>> marks = new ArrayList<>();

		void at(int tick, Runnable mark) {
			marks.add(Map.entry(tick, mark));
		}

		/** Runs {@code cast} once all of {@code standing} can be found round {@code center}, then the marks from there. */
		void run(TestContext context, Vec3d center, List<? extends Entity> standing, Runnable cast) {
			long[] start = {-1L};
			List<Vec3d> stood = standing.stream().map(Entity::getPos).toList();
			context.runAtEveryTick(() -> {
				if (start[0] < 0L) {
					if (found(context.getWorld(), center, standing)) {
						start[0] = context.getTick();
						for (int i = 0; i < standing.size(); i++) {
							Entity entity = standing.get(i);
							Vec3d at = stood.get(i);
							entity.setVelocity(Vec3d.ZERO);
							if (entity instanceof ServerPlayerEntity player) {
								player.networkHandler.requestTeleport(at.x, at.y, at.z, player.getYaw(), player.getPitch());
							} else {
								entity.requestTeleport(at.x, at.y, at.z);
							}
						}
						cast.run();
					}
					return;
				}
				long t = context.getTick() - start[0];
				for (Map.Entry<Integer, Runnable> mark : marks) {
					if (mark.getKey() == t) {
						mark.getValue().run();
					}
				}
			});
		}

		private static boolean found(ServerWorld world, Vec3d center, List<? extends Entity> standing) {
			double reach = (RADIUS + 8) * 2.0;
			return new HashSet<>(world.getOtherEntities(null, Box.of(center, reach, reach, reach))).containsAll(standing);
		}
	}

	/** Whether the last thing to hurt {@code entity} was the void. */
	private static boolean brokenByTheVoid(LivingEntity entity) {
		DamageSource source = entity.getRecentDamageSource();
		return source != null && source.isOf(GojoDamage.UNLIMITED_VOID);
	}

	private static void near(List<String> problems, String what, float value, float expected) {
		if (Math.abs(value - expected) > 0.01F) {
			problems.add(what + " was at " + value + ", not " + expected);
		}
	}

	/** Netherite all over, with Protection IV on every piece. */
	private static void armour(ServerWorld world, ServerPlayerEntity player) {
		RegistryEntry<Enchantment> protection = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.PROTECTION);
		Item[] pieces = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < pieces.length; i++) {
			ItemStack stack = new ItemStack(pieces[i]);
			stack.addEnchantment(protection, 4);
			player.equipStack(slots[i], stack);
		}
	}

	private static void rules(ServerWorld world, int cooldownSeconds) {
		rules(world, cooldownSeconds, HOLD_SECONDS);
	}

	private static void rules(ServerWorld world, int cooldownSeconds, int holdSeconds) {
		world.getGameRules().get(GojoRules.VOID_BREAKS).set(true, world.getServer());
		world.getGameRules().get(GojoRules.VOID_RADIUS).set(RADIUS, world.getServer());
		world.getGameRules().get(GojoRules.VOID_HOLD).set(holdSeconds, world.getServer());
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
