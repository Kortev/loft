package io.github.kortev.chainsawman;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Pochita, the little chainsaw devil: a round orange dog with a chainsaw's blade out of his forehead. One turns up in
 * the wild now and then (forests and plains), hurt and alone: he spawns with little health and barely moves. You tame
 * him as Denji did, by giving him your blood: hold use on him and your blood goes into him ({@link #FEED} ticks, half a
 * heart of yours every {@link #FEED_COST_EVERY}), until he's well and yours. Only kortev can (he's theirs).
 *
 * <p>Tamed, he follows you and sits when told (use on him); crouching, use on him picks him up: he's the Pochita item,
 * a chainsaw you can fight with, and using it on the ground sets him down again. Carry him when you die and he becomes
 * your heart ({@code Heart}): you come back as Chainsaw Man, and from then on you never need him to.
 */
public class PochitaEntity extends TameableEntity {
	/** How long you give him blood to tame him (ticks), how often it costs you, and how long a gap breaks it off. */
	public static final int FEED = 80;
	private static final int FEED_COST_EVERY = 20;
	private static final int FEED_GAP = 6;
	/** How hurt a wild one is when he turns up (health points). */
	private static final float HURT = 4.0F;

	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 1.0F);

	/** Who is giving each Pochita blood, how far they've got, and when they last held use on him. */
	private record Feed(UUID by, int done, long last) {
	}

	private static final Map<UUID, Feed> FED = new ConcurrentHashMap<>();

	public PochitaEntity(EntityType<? extends TameableEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 16.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new SwimGoal(this));
		goalSelector.add(2, new SitGoal(this));
		goalSelector.add(5, new FollowOwnerGoal(this, 1.1, 6.0F, 2.0F));
		goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
		goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		goalSelector.add(9, new LookAroundGoal(this));
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
		if (reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GENERATION || reason == SpawnReason.COMMAND
				|| reason == SpawnReason.SPAWN_EGG) {
			// Found hurt and alone, as he was.
			setHealth(HURT);
		}
		return super.initialize(world, difficulty, reason, data);
	}

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		if (getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		ServerPlayerEntity server = (ServerPlayerEntity) player;
		if (!isTamed()) {
			feed(server);
			return ActionResult.SUCCESS;
		}
		if (!isOwner(player)) {
			return ActionResult.PASS;
		}
		if (player.isSneaking()) {
			// Picked up: in your hands he's the chainsaw.
			ItemStack item = new ItemStack(ChainsawMan.POCHITA);
			if (!player.getInventory().insertStack(item)) {
				player.dropItem(item, false);
			}
			discard();
			return ActionResult.SUCCESS;
		}
		setSitting(!isSitting());
		jumping = false;
		navigation.stop();
		return ActionResult.SUCCESS;
	}

	/** {@code player} holding use on him: more of their blood into him, and once it's enough, he's theirs. */
	public void feed(ServerPlayerEntity player) {
		if (!player.getGameProfile().getName().equalsIgnoreCase(ChainsawMan.OWNER)) {
			player.sendMessage(Text.translatable("message.chainsawman.pochita_wary").formatted(Formatting.GRAY), true);
			return;
		}
		long now = getWorld().getTime();
		Feed feed = FED.get(getUuid());
		if (feed == null || !feed.by().equals(player.getUuid()) || now - feed.last() > FEED_GAP) {
			feed = new Feed(player.getUuid(), 0, now);
		}
		int done = feed.done() + (int) Math.max(1, Math.min(FEED_GAP, now - feed.last()));
		if (done / FEED_COST_EVERY > feed.done() / FEED_COST_EVERY) {
			player.damage(player.getDamageSources().magic(), 1.0F);
			heal(2.0F);
		}
		((ServerWorld) getWorld()).spawnParticles(BLOOD, getX(), getY() + 0.5, getZ(), 4, 0.2, 0.2, 0.2, 0.02);
		player.sendMessage(Text.translatable("message.chainsawman.pochita_feeding", Math.min(100, done * 100 / FEED)).formatted(Formatting.DARK_RED), true);
		if (done >= FEED) {
			FED.remove(getUuid());
			setOwner(player);
			setTamed(true, true);
			setHealth(getMaxHealth());
			navigation.stop();
			((ServerWorld) getWorld()).spawnParticles(ParticleTypes.HEART, getX(), getY() + 0.7, getZ(), 6, 0.3, 0.3, 0.3, 0.0);
			player.sendMessage(Text.translatable("message.chainsawman.pochita_tamed").formatted(Formatting.GOLD), true);
			return;
		}
		FED.put(getUuid(), new Feed(player.getUuid(), done, now));
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient && !isTamed() && getHealth() < getMaxHealth() * 0.5F) {
			// Hurt, he hardly moves.
			navigation.stop();
		}
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) {
		return false;
	}

	@Nullable
	@Override
	public PassiveEntity createChild(ServerWorld world, PassiveEntity other) {
		return null;
	}

	@Override
	public boolean canImmediatelyDespawn(double distance) {
		return false;
	}
}
