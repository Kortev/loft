package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.HeartLevel;
import io.github.kortev.chainsawman.form.Saw;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Chainsaw Man made the strongest single player (slash 13, finisher 18, armour 14 and toughness 6 in the form, heals 2 a hit, blood a third
 * faster from drinking) and the heart levelling up: XP from devil kills and from Eternity's blood, three levels, each with its effects.
 */
public class BuffGameTests implements FabricGameTest {
	/** The buffed numbers, as the code states them. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_buff_numbers", tickLimit = 20)
	public void theBuffedNumbers(TestContext context) {
		context.assertTrue(Saw.SLASH_DAMAGE == 13.0F && Saw.FINISHER_DAMAGE == 18.0F, "the saws do " + Saw.SLASH_DAMAGE + " and " + Saw.FINISHER_DAMAGE);
		context.assertTrue(Saw.HIT_HEAL == 2.0F, "a hit heals " + Saw.HIT_HEAL);
		context.assertTrue(Form.ARMOR == 14.0 && Form.TOUGHNESS == 6.0, "the form's armour is " + Form.ARMOR + " and toughness " + Form.TOUGHNESS);
		// A third faster from drinking: 8, 3 and 25 became 11, 4 and 33.
		context.assertTrue(Blood.HIT == 11 && Blood.BITE == 4 && Blood.KILL == 33, "the blood a cut gives is " + Blood.HIT + ", " + Blood.BITE + ", " + Blood.KILL);
		context.complete();
	}

	/** In the form a slash cuts 13 (more at higher heart levels), heals 2 and drinks 11 blood; the form's armour is 14 with toughness 6. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_buff_slash", tickLimit = 30)
	public void aSlashCutsHealsAndDrinksTheBuffedAmount(TestContext context) {
		ServerPlayerEntity[] fighters = {fighter(context, "level_one", 0.0), fighter(context, "level_two", 8.0), fighter(context, "level_three", 16.0)};
		HuskEntity[] husks = {husk(context, 0.0), husk(context, 8.0), husk(context, 16.0)};
		HeartLevel.set(fighters[1], HeartLevel.LEVEL_2);
		HeartLevel.set(fighters[2], HeartLevel.LEVEL_3);
		float[] want = {13.0F, 15.0F, 17.0F};
		try {
			for (int i = 0; i < 3; i++) {
				fighters[i].setHealth(10.0F);
				Blood.set(fighters[i], 50);
				context.assertTrue(Saw.slash(fighters[i]), "the slash did not come, level " + (i + 1));
				float lost = 200.0F - husks[i].getHealth();
				context.assertTrue(Math.abs(lost - want[i]) < 0.05F, "a level " + (i + 1) + " slash did " + lost + ", not " + want[i]);
				context.assertTrue(fighters[i].getHealth() == 12.0F, "the hit healed " + (fighters[i].getHealth() - 10.0F) + ", not 2");
				context.assertTrue(Blood.get(fighters[i]) == 50 + Blood.HIT, "the hit drank " + (Blood.get(fighters[i]) - 50));
			}
			context.assertTrue(fighters[0].getAttributeValue(EntityAttributes.GENERIC_ARMOR) == 14.0 && fighters[0].getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS) == 6.0,
					"the form's armour is " + fighters[0].getAttributeValue(EntityAttributes.GENERIC_ARMOR) + "/" + fighters[0].getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS));
		} finally {
			for (int i = 0; i < 3; i++) {
				done(context, fighters[i]);
				husks[i].discard();
			}
		}
		context.complete();
	}

	/** Heart XP: a devil kill and a bottle of Eternity's blood feed it; 30 and 80 are levels 2 and 3, with their effects; a non-hybrid has no heart level. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_buff_xp", tickLimit = 40)
	public void heartXpFromDevilKillsAndBloodLevelsTheHeart(TestContext context) {
		ServerPlayerEntity player = fighter(context, "feeder", 0.0);
		ServerPlayerEntity human = plain(context, "human", 20.0);
		try {
			context.assertTrue(HeartLevel.level(player) == 1 && HeartLevel.xp(player) == 0 && HeartLevel.damage(player) == 0.0F, "a new heart is not level 1");
			context.assertTrue(HeartLevel.level(human) == 0, "a human has a heart level");
			// A cow is no devil.
			net.minecraft.entity.passive.CowEntity cow = new net.minecraft.entity.passive.CowEntity(EntityType.COW, context.getWorld());
			context.assertTrue(HeartLevel.xpFor(cow) == 0, "a cow was worth heart XP");
			// The Eternity Devil is (the tag), and killing it by their hand gives it.
			io.github.kortev.chainsawman.hotel.EternityDevilEntity devil = ChainsawMan.ETERNITY_DEVIL.create(context.getWorld());
			Vec3d at = player.getPos().add(0.0, 0.0, 3.0);
			devil.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
			context.assertTrue(HeartLevel.xpFor(devil) == HeartLevel.KILL_XP, "the Eternity Devil is not in the devils tag");
			context.assertTrue(HeartLevel.xpFor(new io.github.kortev.chainsawman.hotel.FleshPatchEntity(ChainsawMan.FLESH_PATCH, context.getWorld())) == HeartLevel.PART_XP, "the devil's flesh is not in the parts tag");
			context.getWorld().spawnEntity(devil);
			devil.forgo();
			// (Past its growing, when it can be hurt.)
			devil.age = 1000;
			devil.damage(player.getDamageSources().playerAttack(player), 10000.0F);
			context.assertTrue(HeartLevel.xp(player) == HeartLevel.KILL_XP, "killing the devil gave " + HeartLevel.xp(player) + " XP");
			context.assertTrue(HeartLevel.level(player) == 2 && HeartLevel.damage(player) == 2.0F, "40 XP is not level 2 with +2 damage");
			// Eternity's blood feeds it too.
			for (int i = 0; i < 4; i++) {
				ChainsawMan.ETERNITY_BLOOD.finishUsing(new ItemStack(ChainsawMan.ETERNITY_BLOOD), context.getWorld(), player);
			}
			context.assertTrue(HeartLevel.xp(player) == HeartLevel.KILL_XP + 4 * HeartLevel.BLOOD_XP && HeartLevel.level(player) == 3, "drinking blood did not level it: " + HeartLevel.xp(player));
			// A human drinking it gets nothing.
			ChainsawMan.ETERNITY_BLOOD.finishUsing(new ItemStack(ChainsawMan.ETERNITY_BLOOD), context.getWorld(), human);
			context.assertTrue(HeartLevel.xp(human) == 0, "a human's drink fed a heart");
			// The level 3 effects.
			context.assertTrue(HeartLevel.damage(player) == 4.0F, "level 3 does not add 4 damage in all");
			context.assertTrue(HeartLevel.chainSpeed(player) == 2.0 && HeartLevel.chainSpeed(human) == 1.0, "the chain does not pull twice as fast at level 3");
			context.assertTrue(HeartLevel.beamBite(player) > 1.0F && HeartLevel.spray(player) > 1.0F, "Beam does not bite harder or blood spray further at level 3");
			// Giving the heart up takes the levels with it.
			Heart.take(player);
			context.assertTrue(HeartLevel.xp(player) == 0 && HeartLevel.level(player) == 0, "the levels stayed after the heart went");
		} finally {
			done(context, player);
			done(context, human);
		}
		context.complete();
	}

	/** Level 2 is 30 XP and level 3 is 80: below the line, the level below. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_buff_levels", tickLimit = 20)
	public void theLevelsComeAtThirtyAndEightyXp(TestContext context) {
		context.assertTrue(HeartLevel.levelOf(0) == 1 && HeartLevel.levelOf(29) == 1 && HeartLevel.levelOf(30) == 2 && HeartLevel.levelOf(79) == 2 && HeartLevel.levelOf(80) == 3, "the levels are not at 30 and 80");
		context.complete();
	}

	// ---- Actors.

	private static ServerPlayerEntity fighter(TestContext context, String name, double x) {
		ServerPlayerEntity player = plain(context, name, x);
		player.setAttached(Heart.MERGED, true);
		Form.start(player);
		return player;
	}

	private static ServerPlayerEntity plain(TestContext context, String name, double x) {
		ServerWorld world = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		Vec3d at = context.getAbsolute(new Vec3d(0.5, 40.0, 0.5)).add(x, 0.0, 0.0);
		player.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		player.onTeleportationDone();
		player.setNoGravity(true);
		return player;
	}

	/** A husk with 200 health and no armour, 2 blocks ahead of the player at {@code x}. */
	private static HuskEntity husk(TestContext context, double x) {
		Vec3d at = context.getAbsolute(new Vec3d(0.5, 40.0, 0.5)).add(x, 0.0, 2.0);
		HuskEntity husk = new HuskEntity(EntityType.HUSK, context.getWorld());
		husk.refreshPositionAndAngles(at.x, at.y, at.z, 180.0F, 0.0F);
		husk.setAiDisabled(true);
		husk.setNoGravity(true);
		husk.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200.0);
		husk.setHealth(200.0F);
		husk.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(0.0);
		husk.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		context.getWorld().spawnEntity(husk);
		return husk;
	}

	private static void done(TestContext context, ServerPlayerEntity player) {
		io.github.kortev.chainsawman.form.Beam.end(player);
		Form.end(player);
		io.github.kortev.chainsawman.form.Downed.up(player);
		context.getWorld().getServer().getPlayerManager().remove(player);
	}
}
