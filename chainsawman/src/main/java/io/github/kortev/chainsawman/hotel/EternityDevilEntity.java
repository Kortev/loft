package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.network.HotelPayloads;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Eternity Devil grown out of the walls at the end of the 8th-floor corridor: one huge mass of flesh (about 11 blocks
 * wide and {@link #HEIGHT} high, filling the corridor's end and the rooms either side, in the arena it tears open) that cannot move, with about 300 health and a
 * boss bar. It fights by mouths that rise from the floor and the walls and bite where you stand, tendrils that slam down the corridor, and
 * flesh that creeps across the floor ({@link FleshPatchEntity}); it regenerates while it is not hit, and the heart inside it shows once
 * enough of it is cut away, where a hit does {@link #HEART_MULT} times as much. It begs when it is low (it does not offer the deal again).
 * Killing it breaks the loop ({@link Hotel#killed}).
 */
public class EternityDevilEntity extends MobEntity {
	/** The entity type's own box (not used: the hitbox is the devil's whole front, see {@link #calculateBoundingBox}). */
	public static final float WIDTH = 4.0F;
	public static final float HEIGHT = 7.0F;
	/** How far its bulk comes out from its back plane (blocks): the great mouth's teeth are the nearest. */
	public static final double BULK = 3.9;
	/** The front, in the devil's own frame (blocks): right (toward the hotel's +z) from the great mouth's axis, and the hitbox's extent. */
	public static final double BOX_LEFT = -5.5;
	public static final double BOX_RIGHT = 3.9;
	public static final float MAX_HEALTH = 300.0F;
	/** Ticks it takes to grow out of the walls, during which it cannot be hurt or hurt anyone. */
	public static final int GROW_TICKS = 80;
	/** How long it takes to collapse when it dies (ticks); its loot is on the floor where it stood when it is gone. */
	public static final int DEATH_TICKS = 40;
	/** Not hit for this long (ticks) and it starts to regenerate; it gains {@link #REGEN} health a half second. */
	public static final int REGEN_DELAY = 80;
	public static final float REGEN = 3.0F;
	/** The heart shows below this fraction of its health, and is covered again above the second. */
	public static final float HEART_SHOW = 0.55F;
	public static final float HEART_HIDE = 0.7F;
	public static final float HEART_MULT = 3.0F;
	public static final float HEART_RADIUS = 1.1F;
	/** What the attacks do, and how long each shows before it lands (ticks). */
	public static final float BITE_DAMAGE = 7.0F;
	public static final float TENDRIL_DAMAGE = 9.0F;
	public static final int BITE_WARN = 24;
	public static final int TENDRIL_WARN = 30;
	/**
	 * Where it stands, in the hotel's own coordinates: its back plane is the inner face of the east outer wall (x 37), the great mouth is on the
	 * corridor's axis (z 6.5), on the floor of floor 8. It comes out of its back plane toward the west, {@link #BULK} blocks at the most.
	 */
	public static final double LOCAL_X = 37.0;
	public static final double LOCAL_Y = 29.0;
	public static final double LOCAL_Z = 6.5;
	/** Where its heart is (from the concept's layout): in the great mouth, a block inside the teeth, when the flesh has been cut away enough to show it. */
	public static final double HEART_X = 33.9;
	public static final double HEART_Y = 30.5;
	public static final double HEART_Z = 6.6;

	private static final TrackedData<Boolean> HEART = DataTracker.registerData(EternityDevilEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Float> FACING = DataTracker.registerData(EternityDevilEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Integer> BORN = DataTracker.registerData(EternityDevilEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private final ServerBossBar bossBar = new ServerBossBar(Text.translatable("entity.chainsawman.eternity_devil"), BossBar.Color.RED, BossBar.Style.NOTCHED_10);
	/** The home that runs this hotel, and the hotel's place and turn. */
	public BlockPos homePos = BlockPos.ORIGIN;
	public BlockPos origin = BlockPos.ORIGIN;
	public int rot;
	/** Where it stands along the corridor (local x of its middle): the end, or wherever it tore out of the wall. */
	public double baseX = LOCAL_X;
	private int lastHurtAge = -1000;
	private int nextAttack = GROW_TICKS + 30;
	/** The beg: it begs at {@link #BEG_AT} of its health, cannot be hurt while it does ({@link #begging}), and after nobody takes its deal it no longer fights ({@link #broken}). */
	public static final float BEG_AT = 0.15F;
	public boolean begStarted;
	public boolean begging;
	public boolean broken;
	public boolean spared;
	private int regenSound;
	private int attackNo;
	private static final byte[] ORDER = {HotelPayloads.Devil.BITE, HotelPayloads.Devil.TENDRIL, HotelPayloads.Devil.BITE, HotelPayloads.Devil.CREEP,
			HotelPayloads.Devil.TENDRIL, HotelPayloads.Devil.BITE};
	private final List<Pending> pending = new ArrayList<>();

	/** One attack on its way: where (local), what, and when it lands. */
	private record Pending(byte kind, double x, double z, int at) {
	}

	public EternityDevilEntity(EntityType<? extends MobEntity> type, World world) {
		super(type, world);
		this.setPersistent();
		this.setNoGravity(true);
		this.setAiDisabled(true);
		this.experiencePoints = 0;
		this.ignoreCameraFrustum = true;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH).add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0).add(EntityAttributes.GENERIC_ARMOR, 0.0);
	}

	/** Grows a devil at the end of {@code home}'s corridor; the walls are bleeding already. */
	public static EternityDevilEntity spawn(ServerWorld world, HomeBlockEntity home) {
		return spawn(world, home, LOCAL_X, MAX_HEALTH);
	}

	/** Grows a devil with {@code x} as the middle of its footprint along the corridor and {@code health} (what cutting it in the walls left). */
	public static EternityDevilEntity spawn(ServerWorld world, HomeBlockEntity home, double x, float health) {
		EternityDevilEntity devil = new EternityDevilEntity(ChainsawMan.ETERNITY_DEVIL, world);
		devil.baseX = x;
		Vec3d at = HotelBuilder.world(home.origin, home.rot, x, LOCAL_Y, LOCAL_Z);
		devil.refreshPositionAndAngles(at.x, at.y, at.z, HotelBuilder.alongYaw(home.rot) + 180.0F, 0.0F);
		devil.homePos = home.getPos();
		devil.origin = home.origin;
		devil.rot = home.rot;
		devil.dataTracker.set(FACING, HotelBuilder.alongYaw(home.rot) + 180.0F);
		devil.setBoundingBox(devil.calculateBoundingBox());
		devil.dataTracker.set(BORN, (int) world.getTime());
		devil.setHealth(Math.min(MAX_HEALTH, Math.max(1.0F, health)));
		world.spawnEntity(devil);
		return devil;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(HEART, false);
		builder.add(FACING, 0.0F);
		builder.add(BORN, 0);
	}

	public boolean heartOut() {
		return this.dataTracker.get(HEART);
	}

	/** The way it faces (degrees, like a yaw): down the corridor toward where players come from. */
	public float facing() {
		return this.dataTracker.get(FACING);
	}

	/** The world tick it began to grow, for those who draw it. */
	public int born() {
		return this.dataTracker.get(BORN);
	}

	/** Whether the devil can be fought now (grown out, and not begging). */
	public boolean fightable() {
		return !this.growing() && this.isAlive() && !this.begging;
	}

	public boolean growing() {
		return this.age < GROW_TICKS;
	}

	/** The heart's place in the world. */
	public Vec3d heartAt() {
		return HotelBuilder.world(origin, rot, baseX + (HEART_X - LOCAL_X), HEART_Y, HEART_Z);
	}

	/** The hotel-local x of the nearest of its bulk (the teeth). */
	public double frontX() {
		return baseX - BULK;
	}

	/**
	 * Its hitbox is its whole front, not a footprint round its middle: {@link #BOX_LEFT} to {@link #BOX_RIGHT} blocks across the great mouth's
	 * axis, {@link #HEIGHT} high, and from its back plane out {@link #BULK} blocks, turned the way it faces.
	 */
	@Override
	protected net.minecraft.util.math.Box calculateBoundingBox() {
		if (this.dataTracker == null) {
			return super.calculateBoundingBox();
		}
		double yaw = Math.toRadians(this.dataTracker.get(FACING));
		double c = Math.cos(yaw);
		double s = Math.sin(yaw);
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (double fx : new double[] {BOX_LEFT, BOX_RIGHT}) {
			for (double fz : new double[] {0.0, BULK}) {
				// (the same turn the renderer gives its frame: x across, z toward the players)
				double wx = fx * c - fz * s;
				double wz = fx * s + fz * c;
				minX = Math.min(minX, wx);
				maxX = Math.max(maxX, wx);
				minZ = Math.min(minZ, wz);
				maxZ = Math.max(maxZ, wz);
			}
		}
		Vec3d p = this.getPos();
		return new net.minecraft.util.math.Box(p.x + minX, p.y, p.z + minZ, p.x + maxX, p.y + HEIGHT, p.z + maxZ);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (FACING.equals(data)) {
			this.setBoundingBox(this.calculateBoundingBox());
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return true;
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean isInsideWall() {
		return false;
	}

	@Override
	protected void pushAway(net.minecraft.entity.Entity entity) {
	}

	@Override
	public void takeKnockback(double strength, double x, double z) {
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (this.getWorld().isClient) {
			return false;
		}
		if (source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.CRAMMING) || source.isOf(DamageTypes.DROWN) || source.isOf(DamageTypes.FALL)
				|| source.isOf(DamageTypes.IN_FIRE) || source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.LAVA)) {
			return false;
		}
		boolean kill = source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD);
		if (this.growing() && !kill) {
			return false;
		}
		// While it begs it cannot be hurt.
		if (this.begging && !kill) {
			return false;
		}
		float line = BEG_AT * this.getMaxHealth();
		if (!this.begStarted && !kill && this.getHealth() <= line + 0.001F && this.getWorld() instanceof ServerWorld world) {
			startBeg(world);
			return false;
		}
		float dealt = amount;
		boolean heart = false;
		if (heartOut() && source.getAttacker() instanceof LivingEntity attacker && aimsAtHeart(attacker)) {
			dealt *= HEART_MULT;
			heart = true;
		}
		// A blow that would take it past the line stops there: it begs before it dies.
		if (!this.begStarted && !kill && this.getHealth() - dealt < line) {
			dealt = Math.max(0.01F, this.getHealth() - line);
		}
		boolean hurt = super.damage(source, dealt);
		if (hurt) {
			this.lastHurtAge = this.age;
			if (this.getWorld() instanceof ServerWorld world) {
				if (this.age % 3 == 0) {
					world.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(), ChainsawSounds.DEVIL_HURT, SoundCategory.HOSTILE, 1.0F, heart ? 0.8F : 1.0F);
				}
				if (heart) {
					Vec3d at = heartAt();
					announce(world, HotelPayloads.Devil.HEART, at, 8);
				}
			}
		}
		return hurt;
	}

	/** Whether the thing that hit looks at the heart (its look passes within the heart's radius of the heart). */
	private boolean aimsAtHeart(LivingEntity attacker) {
		Vec3d eye = attacker.getEyePos();
		Vec3d look = attacker.getRotationVec(1.0F);
		Vec3d to = heartAt().subtract(eye);
		double along = to.dotProduct(look);
		if (along <= 0.0) {
			return false;
		}
		Vec3d closest = eye.add(look.multiply(along));
		return closest.squaredDistanceTo(heartAt()) <= HEART_RADIUS * HEART_RADIUS;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());
		if (!this.isAlive()) {
			return;
		}
		// The heart shows when enough is cut away, and is covered again when it has grown back.
		float frac = this.getHealth() / this.getMaxHealth();
		if (!heartOut() && frac <= HEART_SHOW) {
			this.dataTracker.set(HEART, true);
			announce(world, HotelPayloads.Devil.HEART, heartAt(), 0);
		} else if (heartOut() && frac >= HEART_HIDE) {
			this.dataTracker.set(HEART, false);
		}
		if (this.growing()) {
			if (this.age == 1) {
				world.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(), ChainsawSounds.DEVIL_GROW, SoundCategory.HOSTILE, 3.0F, 1.0F);
			}
			return;
		}
		if (!this.begStarted && this.getHealth() <= BEG_AT * this.getMaxHealth() + 0.001F) {
			startBeg(world);
		}
		if (this.begging || this.broken) {
			// It does not fight, and does not heal: it cowers.
			pending.clear();
			return;
		}
		// Regeneration while it has not been hit for a few seconds.
		if (this.age - this.lastHurtAge >= REGEN_DELAY && this.getHealth() < this.getMaxHealth() && this.age % 10 == 0) {
			this.heal(REGEN);
			if (this.regenSound++ % 2 == 0) {
				world.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(), ChainsawSounds.DEVIL_REGEN, SoundCategory.HOSTILE, 1.4F, 0.9F + 0.2F * frac);
				announce(world, HotelPayloads.Devil.REGEN, this.getPos(), 20);
			}
		}
		attacks(world, frac);
	}

	/** At the line it stops, cowers and begs: the contract goes to everyone inside ({@link Hotel#beg}). */
	private void startBeg(ServerWorld world) {
		this.begStarted = true;
		this.begging = true;
		this.pending.clear();
		this.setTarget(null);
		announce(world, HotelPayloads.Devil.BEG, this.getPos(), 0);
		Hotel.beg(world, this);
	}

	/** Nobody took its deal: it is beaten, and does not fight any more; it can be killed. */
	public void giveUp() {
		this.begging = false;
		this.broken = true;
	}

	/** Someone took the deal: it tears off part of itself and sinks into the walls (the collapse, its loot dropped, and it is gone). */
	public void spare() {
		this.spared = true;
		this.begging = false;
		this.broken = true;
		this.kill();
	}

	/** For the tests and commands: the beg is over without anyone being asked (it is beaten and can be killed). */
	public void forgo() {
		this.begStarted = true;
		this.begging = false;
		this.broken = true;
	}

	// ---- Attacks.

	private void attacks(ServerWorld world, float frac) {
		for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
			Pending a = it.next();
			if (this.age >= a.at()) {
				land(world, a);
				it.remove();
			}
		}
		if (this.age < nextAttack) {
			return;
		}
		List<ServerPlayerEntity> targets = targets(world);
		int gap = frac < 0.25F ? 24 : (frac < 0.5F ? 34 : 46);
		nextAttack = this.age + gap + this.random.nextInt(14);
		if (targets.isEmpty()) {
			return;
		}
		ServerPlayerEntity target = targets.get(this.random.nextInt(targets.size()));
		Vec3d lp = HotelBuilder.local(origin, rot, target.getPos());
		double z = Math.max(1.6, Math.min(11.4, lp.z));
		// A bite, a tendril, a bite, flesh across the floor, a tendril, a bite, and round again (a tendril needs room to be slammed down).
		byte kind = ORDER[attackNo++ % ORDER.length];
		if (kind == HotelPayloads.Devil.TENDRIL && lp.x >= frontX() - 3.0) {
			kind = HotelPayloads.Devil.BITE;
		}
		if (kind == HotelPayloads.Devil.BITE) {
			queue(world, HotelPayloads.Devil.BITE, lp.x, z, BITE_WARN);
		} else if (kind == HotelPayloads.Devil.TENDRIL) {
			queue(world, HotelPayloads.Devil.TENDRIL, lp.x, z, TENDRIL_WARN);
		} else {
			creep(world, lp.x, z);
		}
	}

	/** An attack that lands in {@code warn} ticks at the local point, shown to everyone near. */
	private void queue(ServerWorld world, byte kind, double x, double z, int warn) {
		pending.add(new Pending(kind, x, z, this.age + warn));
		announce(world, kind, HotelBuilder.world(origin, rot, x, LOCAL_Y, z), warn);
	}

	private void land(ServerWorld world, Pending a) {
		DamageSource source = this.getDamageSources().mobAttack(this);
		if (a.kind() == HotelPayloads.Devil.BITE) {
			Vec3d at = HotelBuilder.world(origin, rot, a.x(), LOCAL_Y, a.z());
			world.playSound(null, at.x, at.y + 0.5, at.z, ChainsawSounds.DEVIL_BITE, SoundCategory.HOSTILE, 1.6F, 0.9F + this.random.nextFloat() * 0.2F);
			for (ServerPlayerEntity p : players(world, 64.0)) {
				Vec3d lp = HotelBuilder.local(origin, rot, p.getPos());
				if (Math.hypot(lp.x - a.x(), lp.z - a.z()) <= 1.7 && Math.abs(lp.y - LOCAL_Y) <= 2.4) {
					p.damage(source, BITE_DAMAGE);
					p.addVelocity(0.0, 0.45, 0.0);
					p.velocityModified = true;
				}
			}
		} else if (a.kind() == HotelPayloads.Devil.TENDRIL) {
			Vec3d at = HotelBuilder.world(origin, rot, a.x(), LOCAL_Y, a.z());
			world.playSound(null, at.x, at.y + 0.5, at.z, ChainsawSounds.DEVIL_SLAM, SoundCategory.HOSTILE, 2.0F, 0.9F);
			for (ServerPlayerEntity p : players(world, 64.0)) {
				Vec3d lp = HotelBuilder.local(origin, rot, p.getPos());
				// Anyone in the lane between the devil and the point where it slams.
				if (Math.abs(lp.z - a.z()) <= 1.05 && lp.x >= a.x() - 1.6 && lp.x <= frontX() && Math.abs(lp.y - LOCAL_Y) <= 2.6) {
					p.damage(source, TENDRIL_DAMAGE);
					Vec3d push = HotelBuilder.world(BlockPos.ORIGIN, rot, -1.1, 0.0, 0.0);
					p.addVelocity(push.x, 0.35, push.z);
					p.velocityModified = true;
				}
			}
		}
	}

	/** Flesh creeping toward the point: three patches along the floor between the mass and it. */
	private void creep(ServerWorld world, double x, double z) {
		double front = frontX() - 0.6;
		for (int k = 0; k < 3; k++) {
			double px = Math.max(x - 0.5, front - 1.8 - 2.6 * k) + (this.random.nextDouble() - 0.5);
			double pz = Math.max(1.6, Math.min(11.4, z + (this.random.nextDouble() - 0.5) * 1.6));
			Vec3d at = HotelBuilder.world(origin, rot, px, LOCAL_Y, pz);
			FleshPatchEntity patch = new FleshPatchEntity(ChainsawMan.FLESH_PATCH, world);
			patch.refreshPositionAndAngles(at.x, at.y, at.z, this.random.nextFloat() * 360.0F, 0.0F);
			patch.setLife(FleshPatchEntity.LIFE, 12 * k);
			world.spawnEntity(patch);
		}
		announce(world, HotelPayloads.Devil.CREEP, HotelBuilder.world(origin, rot, x, LOCAL_Y, z), 20);
	}

	/** Players in the corridor and the stairwell end, alive, within the devil's reach. */
	private List<ServerPlayerEntity> targets(ServerWorld world) {
		List<ServerPlayerEntity> out = new ArrayList<>();
		for (ServerPlayerEntity p : players(world, 48.0)) {
			Vec3d lp = HotelBuilder.local(origin, rot, p.getPos());
			if (lp.y >= 28.0 && lp.y < 33.0 && lp.x > 1.0 && lp.x < frontX() && lp.z > 0.5 && lp.z < 12.5 && !p.isCreative() && !p.isSpectator()) {
				out.add(p);
			}
		}
		return out;
	}

	private List<ServerPlayerEntity> players(ServerWorld world, double range) {
		List<ServerPlayerEntity> out = new ArrayList<>();
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.isAlive() && p.squaredDistanceTo(this) <= range * range) {
				out.add(p);
			}
		}
		return out;
	}

	private void announce(ServerWorld world, byte kind, Vec3d at, int ticks) {
		HotelPayloads.Devil payload = new HotelPayloads.Devil(this.getId(), kind, (float) at.x, (float) at.y, (float) at.z, ticks);
		for (ServerPlayerEntity p : players(world, 80.0)) {
			if (ServerPlayNetworking.canSend(p, HotelPayloads.Devil.ID)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	// ---- Boss bar, and dying.

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		this.bossBar.addPlayer(player);
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		this.bossBar.removePlayer(player);
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (this.getWorld() instanceof ServerWorld world) {
			this.bossBar.clearPlayers();
			world.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(), ChainsawSounds.DEVIL_DEATH, SoundCategory.HOSTILE, 3.0F, 1.0F);
			announce(world, HotelPayloads.Devil.DYING, this.getPos(), 100);
			Hotel.killed(world, this);
		}
	}

	/** The collapse takes three and a half seconds, not one. */
	@Override
	protected void updatePostDeath() {
		++this.deathTime;
		if (this.deathTime >= DEATH_TICKS && !this.getWorld().isClient() && !this.isRemoved()) {
			if (this.getWorld() instanceof ServerWorld world) {
				Hotel.dropLoot(world, this);
				Hotel.mendArena(world, this);
			}
			this.getWorld().sendEntityStatus(this, (byte) 60);
			this.remove(RemovalReason.KILLED);
		}
	}

	@Override
	protected void dropLoot(DamageSource damageSource, boolean causedByPlayer) {
	}

	@Override
	public void remove(RemovalReason reason) {
		this.bossBar.clearPlayers();
		super.remove(reason);
	}

	@Override
	public boolean canTarget(EntityType<?> type) {
		return false;
	}

	@Override
	public boolean addStatusEffect(StatusEffectInstance effect, net.minecraft.entity.Entity source) {
		return effect.getEffectType() == StatusEffects.INSTANT_DAMAGE.value() && super.addStatusEffect(effect, source);
	}
}
