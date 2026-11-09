package io.github.kortev.chainsawman.hotel;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * One of the hunters of the missing squad, still alive in the loop (our own characters: Yamada, Mori, Sato): starving, sitting against the
 * corridor's wall until somebody feeds them. Right-click with food and they get up and follow you, and in the fight they cut the devil with
 * katanas (an iron sword's damage). They are downed, not killed: a downed one lies against the wall until it is fed again. One of them has gone
 * mad with fear: he attacks any player on sight until he is fed or knocked low, then he calms down and follows. When the loop breaks the ones who
 * were rescued walk to the exit and leave ({@link Squad}).
 */
public class SquadMemberEntity extends PathAwareEntity {
	public static final int HUNGRY = 0;
	public static final int FOLLOWING = 1;
	public static final int DOWN = 2;
	public static final int LEAVING = 3;
	/** A mad one calms when he is knocked below this fraction of his health. */
	public static final float CALM_BELOW = 0.4F;
	/** What a katana does (an iron sword). */
	public static final float KATANA = 6.0F;
	/** The mad one hits with shaking hands, not a hunter's blade. */
	public static final float MAD_DAMAGE = 3.0F;
	private static final EntityDimensions SITTING = EntityDimensions.fixed(0.6F, 1.2F);

	private static final TrackedData<Integer> STATE = DataTracker.registerData(SquadMemberEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> VARIANT = DataTracker.registerData(SquadMemberEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> MAD = DataTracker.registerData(SquadMemberEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** Who they follow, whether they have ever been fed, where they are walking to as they leave. */
	public UUID owner;
	public boolean rescued;
	public Vec3d exit;
	/** A note he carries, and drops when he is fed. */
	public ItemStack note = ItemStack.EMPTY;
	private int leaving;

	public SquadMemberEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		this.setPersistent();
		this.experiencePoints = 0;
		this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return PathAwareEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, KATANA).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0).add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.2);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(STATE, HUNGRY);
		builder.add(VARIANT, 0);
		builder.add(MAD, false);
	}

	public int state() {
		return this.dataTracker.get(STATE);
	}

	public void setState(int state) {
		this.dataTracker.set(STATE, state);
		this.calculateDimensions();
	}

	public int variant() {
		return this.dataTracker.get(VARIANT);
	}

	public void setVariant(int variant) {
		this.dataTracker.set(VARIANT, variant);
	}

	public boolean isMad() {
		return this.dataTracker.get(MAD);
	}

	public void setMad(boolean mad) {
		this.dataTracker.set(MAD, mad);
	}

	/** Sitting against the wall: starving (and not the mad one), or downed. */
	public boolean sitting() {
		int s = state();
		return s == DOWN || s == HUNGRY && !isMad();
	}

	/** Up and about: following, or the mad one on the loose. */
	public boolean awake() {
		int s = state();
		return s == FOLLOWING || s == HUNGRY && isMad();
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (STATE.equals(data) || MAD.equals(data)) {
			this.calculateDimensions();
		}
	}

	@Override
	protected EntityDimensions getBaseDimensions(EntityPose pose) {
		return sitting() ? SITTING : super.getBaseDimensions(pose);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new MeleeAttackGoal(this, 1.2, true) {
			@Override
			public boolean canStart() {
				return awake() && super.canStart();
			}

			@Override
			public boolean shouldContinue() {
				return awake() && super.shouldContinue();
			}
		});
		this.goalSelector.add(2, new FollowOwner());
		this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		// The mad one goes for any player; the rest, once fed, for the devil (until it begs).
		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 0, true, false, e -> isMad() && state() == HUNGRY && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()))));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, EternityDevilEntity.class, 0, false, false, e -> state() == FOLLOWING && e instanceof EternityDevilEntity d && d.fightable()));
	}

	/** Following whoever fed them. */
	private class FollowOwner extends Goal {
		FollowOwner() {
			this.setControls(EnumSet.of(Control.MOVE));
		}

		private PlayerEntity who() {
			return owner == null ? null : getWorld().getPlayerByUuid(owner);
		}

		@Override
		public boolean canStart() {
			PlayerEntity p = who();
			return state() == FOLLOWING && p != null && getTarget() == null && squaredDistanceTo(p) > 25.0;
		}

		@Override
		public boolean shouldContinue() {
			PlayerEntity p = who();
			return state() == FOLLOWING && p != null && getTarget() == null && squaredDistanceTo(p) > 9.0 && !getNavigation().isIdle();
		}

		@Override
		public void tick() {
			PlayerEntity p = who();
			if (p == null) {
				return;
			}
			if (squaredDistanceTo(p) > 30.0 * 30.0 && getWorld() instanceof ServerWorld) {
				// Too far behind (a loop, a staircase): stand beside them.
				refreshPositionAndAngles(p.getX() + 1.0, p.getY(), p.getZ(), getYaw(), 0.0F);
				getNavigation().stop();
			} else if (age % 10 == 0) {
				getNavigation().startMovingTo(p, 1.15);
			}
		}

		@Override
		public void start() {
			PlayerEntity p = who();
			if (p != null) {
				getNavigation().startMovingTo(p, 1.15);
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (state() == DOWN || sitting()) {
			getNavigation().stop();
			setVelocity(getVelocity().multiply(0.0, 1.0, 0.0));
		}
		if (state() == LEAVING) {
			setTarget(null);
			if (exit != null && age % 10 == 0) {
				getNavigation().startMovingTo(exit.x, exit.y, exit.z, 1.1);
			}
			leaving++;
			// They go into the stairwell and are gone once nobody can see them (or, if somebody keeps watching, long after).
			boolean there = leaving > 220 || exit != null && squaredDistanceTo(exit) < 4.0;
			boolean seen = world.getPlayers().stream().anyMatch(p -> p.squaredDistanceTo(this) < 64.0 * 64.0 && p.canSee(this));
			if (there && (!seen || leaving > 450)) {
				world.spawnParticles(ParticleTypes.POOF, getX(), getY() + 1.0, getZ(), 12, 0.3, 0.6, 0.3, 0.02);
				discard();
			}
		}
	}

	/** Fed, they get up and follow whoever fed them; a mad one is calm. */
	public void feed(PlayerEntity by, int nutrition) {
		boolean was = state() == DOWN;
		setMad(false);
		setState(FOLLOWING);
		owner = by.getUuid();
		rescued = true;
		setTarget(null);
		if (!note.isEmpty()) {
			this.dropStack(note);
			note = ItemStack.EMPTY;
		}
		setHealth(Math.min(getMaxHealth(), Math.max(getHealth() + nutrition, was ? getMaxHealth() * 0.6F : 0.0F)));
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 1.0F, 1.0F);
		if (getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.0, getZ(), 8, 0.4, 0.6, 0.4, 0.02);
		}
		if (by instanceof ServerPlayerEntity p) {
			p.sendMessage(Text.translatable(was ? "message.chainsawman.squad.up" : "message.chainsawman.squad.fed", getName()), true);
		}
	}

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		var food = stack.get(DataComponentTypes.FOOD);
		if (food != null && (state() == HUNGRY || state() == DOWN)) {
			if (!getWorld().isClient) {
				if (!player.isCreative()) {
					stack.decrement(1);
				}
				feed(player, food.nutrition());
			}
			return ActionResult.success(getWorld().isClient);
		}
		return super.interactMob(player, hand);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (getWorld().isClient) {
			return false;
		}
		boolean killing = source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD);
		if (!killing && (state() == LEAVING || state() == DOWN || source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.CRAMMING))) {
			return false;
		}
		if (!killing && getHealth() - amount <= 1.0F) {
			// Downed, not killed: a mad one is calm now, and a hunter lies against the wall until someone feeds them again.
			setMad(false);
			setTarget(null);
			setHealth(2.0F);
			setState(DOWN);
			getNavigation().stop();
			return true;
		}
		boolean hurt = super.damage(source, amount);
		if (hurt && isMad() && state() == HUNGRY && getHealth() <= getMaxHealth() * CALM_BELOW) {
			// Knocked low: the fear is beaten out of him, and he follows whoever did it.
			setMad(false);
			setState(FOLLOWING);
			setTarget(null);
			if (source.getAttacker() instanceof PlayerEntity p) {
				owner = p.getUuid();
				if (p instanceof ServerPlayerEntity sp) {
					sp.sendMessage(Text.translatable("message.chainsawman.squad.calm", getName()), true);
				}
			}
		}
		return hurt;
	}

	@Override
	public boolean tryAttack(net.minecraft.entity.Entity target) {
		if (isMad()) {
			this.swingHand(Hand.MAIN_HAND);
			return target.damage(this.getDamageSources().mobAttack(this), MAD_DAMAGE);
		}
		return super.tryAttack(target);
	}

	@Override
	public boolean canTarget(EntityType<?> type) {
		return true;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return state() != DOWN && super.isPushable();
	}

	@Override
	protected void dropLoot(DamageSource damageSource, boolean causedByPlayer) {
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("State", state());
		nbt.putInt("Variant", variant());
		nbt.putBoolean("Mad", isMad());
		nbt.putBoolean("Rescued", rescued);
		// The note in his pocket goes with him (it is dropped when he is fed, not lost when he unloads).
		if (!note.isEmpty()) {
			nbt.put("Note", note.encode(this.getRegistryManager()));
		}
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		setState(nbt.getInt("State"));
		setVariant(nbt.getInt("Variant"));
		setMad(nbt.getBoolean("Mad"));
		rescued = nbt.getBoolean("Rescued");
		note = nbt.contains("Note") ? ItemStack.fromNbt(this.getRegistryManager(), nbt.get("Note")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
	}
}
