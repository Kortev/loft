package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.PochitaEntity;
import io.github.kortev.chainsawman.client.ClientCombat;
import io.github.kortev.chainsawman.client.LensBlood;
import io.github.kortev.chainsawman.client.MergeCinematic;
import io.github.kortev.chainsawman.client.Sfx;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.Merge;
import io.github.kortev.chainsawman.form.FormTimeline;
import io.github.kortev.chainsawman.form.MergeTimeline;
import io.github.kortev.chainsawman.form.Saw;
import io.github.kortev.chainsawman.network.FormEventPayload;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.joml.Vector3f;

/**
 * With -Dshootingstar.selftest=reel1: the first reel, episode 1 recreated in the mod (kortev's storyboard, 2026-10-20,
 * restaged as the episode has it), fully scripted, filmed tall (1080x1920) for reels: Denji and Pochita at home, the
 * yakuza's job, the trap: Denji and Pochita stabbed in the back at once, the Zombie Devil, the yakuza turning, the
 * zombies finishing Denji off; thrown in a dumpster with Pochita, dead a while, his blood dripping onto Pochita, who
 * stirs; the mod's own merge; and Denji rising out of the dumpster with the cord in his chest. No voices but the merge's.
 * The actors are stand-ins drawn only here (each in their own skin from build/selftest/skins, seeded by gradle's
 * seedReelSkins from tools/skins/reel), Denji in kortev's own onesie; you, playing as Denji (-Pusername=Denji), are
 * swapped in for his stand-in in the dumpster, for the merge. With -Pcapture=true it's recorded with its sounds
 * (Capture) into build/selftest/capture. -Ptarget=cast photographs the cast and the Zombie Devil side by side;
 * -Ptarget=set the set, to look them over.
 */
public class ReelSelfTest implements ClientModInitializer {
	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	/** What a stand-in is doing with its arms and its body (posed by {@link #pose} and {@link #body}). */
	enum Act {
		STAND, SIT_EAT, HOLD, STAB, HURT, KNEEL, LIE, ZOMBIE, TWITCH, GRAB, CHOP, DRAG, HEAVE
	}

	/** What a stand-in is doing, since when (reel ticks). */
	private record Acting(Act act, double since) {
	}

	/** Where a stand-in is to be, the way its body and head face, its head's tilt. */
	private record Placed(Vec3d at, float body, float head, float pitch) {
	}

	// The cuts (reel ticks), as the storyboard has them, restaged as the episode goes.
	private static final int KNOCK = 100;
	private static final int WAREHOUSE = 160;
	private static final int SHUT = 260;
	private static final int STABBED = 320;
	/** The knives going in, Denji's and Pochita's at once. */
	private static final int STAB = 355;
	private static final int DEVIL = 410;
	private static final int TURN = 500;
	private static final int TOO_MANY = 580;
	private static final int CUT_DOWN = 660;
	private static final int DRAGGED = 740;
	private static final int LID = 840;
	/** The lid shut. */
	private static final int SHUT_IN = 862;
	/** In the dark of the shut bin: dead a while; his blood dripping onto Pochita, Pochita stirring; then black. */
	private static final int DEAD = 870;
	private static final int DRIPS = 905;
	private static final int STIRS = 925;
	private static final int BLACK = 945;
	/** You swapped in for Denji's stand-in in the dumpster; the merge starting, in the black. */
	private static final int SWAP = 955;
	private static final int MERGE = 960;
	/** The merge's own camera from here (it's in the dark of the dream by then), to your getting up. */
	private static final int ITS_OWN = MERGE + 74;
	private static final int AWAKE = MERGE + MergeTimeline.RISE;
	/** On your feet at the merge's end, the camera easing in on the cord in your chest, until you pull it. */
	private static final int PULL = MERGE + MergeTimeline.END + 34;
	/** Chainsaw Man: the transformation all its own (FormTimeline), then the fight, filmed again. */
	private static final int FORM_ON = PULL + FormTimeline.END;
	/** Out of the bin, over its front. */
	private static final int OUT = FORM_ON + 6;
	/** The kills, the three by the bin and the five coming out of the back door: the swings land on these ticks. */
	private static final int[] KILLS = {OUT + 24, OUT + 36, OUT + 48, OUT + 62, OUT + 74, OUT + 86, OUT + 98, OUT + 110};
	private static final String[] KILLED = {"Y2", "Y3", "Z5", "Y1", "Y4", "Boss", "Z6", "Z7"};
	/** At the ones coming out of the back door. */
	private static final int DASH = OUT + 54;
	/** In through the back door at the Zombie Devil; the leap at it; cut in two; down again, in its blood. */
	private static final int IN = OUT + 114;
	private static final int LEAP = IN + 28;
	private static final int CUT = LEAP + 11;
	private static final int LAND = LEAP + 22;
	private static final int END = LAND + 63;
	/** How far ahead of the clock the stand-ins are placed: they get there over the next two ticks, as they're drawn. */
	private static final int LEAD = 2;

	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	/** Where everything is built: the middle of the set's floor. */
	private static Vec3d origin;
	/** The stand-ins (and Pochita), by who they are in the scene. */
	private static final Map<String, LivingEntity> ACTORS = new HashMap<>();
	private static final Map<String, Placed> PLACES = new HashMap<>();
	private static final Map<Integer, Acting> ACTING = new HashMap<>();
	private static int nextId = -6000;
	/** For the cast: the Zombie Devil, where the base of its neck is (null: not there), the way it faces. */
	private static Vec3d devil;
	private static float devilYaw;
	private static final float DEVIL_SCALE = 1.35F;
	/** The dumpster in the alley, drawn (not built): its corners on the ground, its height. */
	static final double BIN_WEST = 25.7;
	static final double BIN_EAST = 28.3;
	static final double BIN_NORTH = 3.05;
	static final double BIN_SOUTH = 4.55;
	static final double BIN_HIGH = 1.0;
	/** Where you lie in it, feet to the west end, your head the other way. */
	private static final Vec3d IN_THE_BIN = new Vec3d(26.15, 0.0, 3.8);
	/** Where Denji falls (his feet), and where the swarm closes round him (his middle, lying). */
	private static final Vec3d DENJI = new Vec3d(8.5, 0.0, 0.0);
	private static final Vec3d DOWN = new Vec3d(9.4, 0.0, 0.0);
	/** Where Pochita lies once he's been stabbed, by Denji's chest; and in the bin, under Denji. */
	private static final Vec3d POCHITA_DOWN = new Vec3d(9.3, 0.0, -0.6);
	private static final Vec3d POCHITA_IN = new Vec3d(27.0, 0.1, 4.15);
	/** Where Denji's stand-in lands in the bin (his feet's place, had he any: his body lies west of it, face down). */
	private static final Vec3d DENJI_IN = new Vec3d(28.05, 0.12, 3.75);
	/** Where each of them is when cut down: the three by the bin, the five out of the back door. */
	private static final Vec3d[] KILLED_AT = {new Vec3d(27.45, 0.0, 1.85), new Vec3d(27.5, 0.0, 2.7), new Vec3d(25.2, 0.0, 2.5),
			new Vec3d(23.55, 0.0, 1.2), new Vec3d(23.25, 0.0, 0.9), new Vec3d(22.95, 0.0, 0.8), new Vec3d(22.65, 0.0, 0.6), new Vec3d(22.35, 0.0, 0.6)};
	/** Where you stand, out of the bin; your steps at the back door, a kill at each; inside; landing. */
	private static final Vec3d STAND = new Vec3d(26.35, 0.0, 2.1);
	private static final Vec3d[] STEPS = {new Vec3d(24.7, 0.0, 1.2), new Vec3d(24.4, 0.0, 0.9), new Vec3d(24.1, 0.0, 0.8), new Vec3d(23.8, 0.0, 0.6),
			new Vec3d(23.5, 0.0, 0.6)};
	private static final Vec3d INSIDE = new Vec3d(20.2, 0.0, 0.4);
	private static final Vec3d LANDING = new Vec3d(16.4, 0.0, 0.5);
	/** The ones who close round him, in order round him. */
	private static final String[] SWARM = {"Y1", "Y2", "Y3", "Y4", "Boss", "Z5", "Z6", "Z7"};
	/** Their arms coming down on him. */
	private static final int[] CHOPS = {668, 677, 686, 696, 706, 715};
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 1.3F);
	private static final DustParticleEffect DARK_BLOOD = new DustParticleEffect(new Vector3f(0.35F, 0.0F, 0.02F), 1.5F);
	private static final BlockStateParticleEffect DROP = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());

	public static boolean active() {
		return "reel1".equals(System.getProperty("shootingstar.selftest"));
	}

	private static String target() {
		return System.getProperty("shootingstar.selftest.target", "");
	}

	private static boolean scene() {
		return target().isEmpty();
	}

	private static boolean capturing() {
		return "true".equals(System.getProperty("shootingstar.selftest.capture"));
	}

	@Override
	public void onInitializeClient() {
		if (!active()) {
			return;
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(40 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] reel1 timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-reel-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		if (capturing()) {
			// The server in step with the frames (Capture).
			ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
			ServerTickEvents.END_SERVER_TICK.register(server -> Capture.serverTickEnd());
		}
		ClientTickEvents.END_CLIENT_TICK.register(ReelSelfTest::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if (context.consumers() == null || origin == null) {
				return;
			}
			MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
			VertexConsumerProvider consumers = context.consumers();
			Vec3d cam = context.camera().getPos();
			double t = now();
			if (devil != null) {
				ReelZombieDevil.render(matrices, consumers, devil, cam, devilYaw, DEVIL_SCALE, (float) t, LightmapTextureManager.pack(12, 0));
			}
			if (stage != Stage.RUN || !scene() || dreaming()) {
				return;
			}
			if (t >= DEVIL) {
				float facing = (float) MathHelper.lerp(ease(span(t, IN, IN + 20)), 90.0, 270.0);
				ReelZombieDevil.render(matrices, consumers, origin.add(devilAt(t)), cam, facing, DEVIL_SCALE, (float) t,
						LightmapTextureManager.pack((int) MathHelper.lerp(ease(span(t, DEVIL + 10, DEVIL + 72)), 2.0, 10.0), 0),
						t >= CUT ? (float) (t - CUT) : -1.0F, origin.y);
			}
			ClientWorld world = context.world();
			int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(origin.add(27.0, BIN_HIGH + 0.4, 3.8)));
			ReelDumpster.render(matrices, consumers, origin, cam, lid(t), light);
		});
		HudRenderCallback.EVENT.register((context, counter) -> {
			if (stage != Stage.RUN || !scene()) {
				return;
			}
			float black = black(now());
			if (black > 0.0F) {
				context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), (int) (black * 255) << 24);
			}
		});
	}

	private static void tick(MinecraftClient client) {
		TestWindow.keepToItself(client);
		ticks++;
		IntegratedServer server = client.getServer();
		client.options.pauseOnLostFocus = false;
		if (stage == null) {
			// Done, the game stopping.
			return;
		}
		if (stage != Stage.WAIT_WORLD && client.currentScreen != null) {
			client.setScreen(null);
		}
		switch (stage) {
			case WAIT_WORLD -> {
				if (client.world != null && client.player != null && client.player.isAlive() && server != null && ticks > 60) {
					stage = Stage.SETUP;
				}
			}
			case SETUP -> {
				server.execute(() -> setup(server));
				stage = Stage.SETTLE;
				ticks = 0;
			}
			case SETTLE -> {
				if (ticks > 80 && origin != null) {
					stage = Stage.RUN;
					ticks = 0;
				}
			}
			case RUN -> {
				if ("cast".equals(target())) {
					cast(client, ticks);
				} else if ("set".equals(target())) {
					set(client, ticks);
				} else if ("den".equals(target())) {
					den(client, ticks);
				} else {
					reel(client, ticks);
				}
			}
			case DONE -> {
				Capture.stop();
				ShootingStar.LOGGER.info("[selftest] reel1: done");
				stage = null;
				client.scheduleStop();
			}
			default -> {
			}
		}
	}

	/** The reel's clock as this frame is drawn (ticks, with the part of one). */
	private static double now() {
		return ticks + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
	}

	// The scene.

	/** One tick of the reel: what happens now (sounds, the doors, the knives, the merge), and where everyone is. */
	private static void reel(MinecraftClient client, int f) {
		IntegratedServer server = client.getServer();
		ClientWorld world = client.world;
		if (f == 1) {
			start(client, server);
		}
		happen(client, server, world, f);
		places(f + LEAD);
		for (Map.Entry<String, LivingEntity> e : ACTORS.entrySet()) {
			Placed p = PLACES.get(e.getKey());
			if (p != null) {
				place(e.getValue(), p);
			}
		}
		if (f >= OUT - 2 && f < END && client.player != null) {
			Vec3d want = origin.add(you(f + LEAD));
			client.player.setVelocity(want.subtract(client.player.getPos()));
			float facing = youYaw(f + LEAD);
			client.player.setYaw(facing);
			client.player.setHeadYaw(facing);
			client.player.setBodyYaw(facing);
			client.player.setPitch(0.0F);
			client.player.fallDistance = 0.0F;
		}
		if (f >= FORM_ON && f < END && client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		if (f >= SWAP && f < MERGE + 10 && client.player != null) {
			// You, lying down along the bin (your head to the east end).
			client.player.setYaw(90.0F);
			client.player.setHeadYaw(90.0F);
			client.player.setBodyYaw(90.0F);
		}
		if (shots < SCREENS.length - 1 && f == SCREENS[shots]) {
			shot(client, "frame_" + f);
			shots++;
		}
		if (f >= END) {
			stage = Stage.DONE;
		}
	}

	/** A few frames saved as pictures along the way, to look at without the video. */
	private static final int[] SCREENS = {40, 130, 230, 290, 340, 357, 380, 400, 450, 480, 512, 540, 570, 600, 640, 690, 700, 760, 790, 830,
			850, 890, 912, 930, AWAKE + 6, AWAKE + 40, PULL - 4, FORM_ON + 4, OUT + 8, OUT + 24, OUT + 37, OUT + 48, DASH + 9, OUT + 75, OUT + 99,
			IN + 14, LEAP + 6, CUT + 1, CUT + 8, LAND + 4, LAND + 30, END - 26, Integer.MAX_VALUE};
	private static int shots;

	private static void start(MinecraftClient client, IntegratedServer server) {
		LensBlood.filmed = true;
		MergeCinematic.prompt = false;
		// Night in the rain is all but black on a phone: the game's brightest gamma, the lamps doing the rest.
		client.options.getGamma().setValue(1.0);
		if (capturing()) {
			Capture.start(client, client.runDirectory.toPath().resolve("capture"));
		}
		Capture.camera = time -> cam(now());
		LivingEntity denji = actor(client, "Denji", "Denji", o(-23.6, 0.0, -0.9), -50.0F);
		denji.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BREAD));
		act("Denji", Act.SIT_EAT, 0);
		actor(client, "Boss", "Boss", o(-2.4, 0.0, -1.2), -90.0F);
		for (int i = 1; i <= 4; i++) {
			actor(client, "Y" + i, "Yakuza" + i, o(-6.0, 0.0, -4.0 + i), -90.0F);
		}
		PochitaEntity pochita = ChainsawMan.POCHITA_ENTITY.create(client.world);
		if (pochita != null) {
			pochita.setId(nextId--);
			pochita.setSilent(true);
			Vec3d at = o(-22.85, 0.0, -1.55);
			pochita.refreshPositionAndAngles(at.x, at.y, at.z, 50.0F, 0.0F);
			pochita.setInSittingPose(true);
			client.world.addEntity(pochita);
			ACTORS.put("Pochita", pochita);
		}
		server.execute(() -> {
			ServerWorld world = server.getOverworld();
			BlockPos o = BlockPos.ofFloored(origin);
			ReelSet.doors(world, o, true);
			ReelSet.shackDoor(world, o, false);
		});
	}

	/** What happens at tick {@code f}. */
	private static void happen(MinecraftClient client, IntegratedServer server, ClientWorld world, int f) {
		long now = world.getTime();
		BlockPos o = BlockPos.ofFloored(origin);
		// 1, The shack: Denji eating his bread, a bite now and then; Pochita watching every one.
		if (f > 0 && f < KNOCK && f % 30 == 4) {
			sound(at("Denji").add(0.0, 1.4, 0.0), SoundEvents.ENTITY_GENERIC_EAT, 0.7F, 0.95F + (f / 30 % 2) * 0.1F);
		}
		// 2, A knock: three; Pochita up; the door opening on one of them; Denji up, picking Pochita up.
		if (f == 104 || f == 111 || f == 118) {
			sound(o(-20.0, 1.2, -1.0), SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.35F, 1.7F);
		}
		if (f == 104) {
			pochita().setInSittingPose(false);
			act("Denji", Act.STAND, f);
		}
		if (f == 128) {
			server.execute(() -> ReelSet.shackDoor(server.getOverworld(), o, true));
			sound(o(-20.0, 1.0, -1.0), SoundEvents.BLOCK_WOODEN_DOOR_OPEN, 0.9F, 0.8F);
		}
		if (f == 150) {
			act("Denji", Act.HOLD, f);
		}
		// 3, The warehouse: its doors grinding open on them in the rain; their steps on the concrete.
		if (f == 168 || f == 174 || f == 180) {
			float open = (f - 162) / 18.0F;
			server.execute(() -> ReelSet.doors(server.getOverworld(), o, open));
		}
		if (f == 168) {
			sound(o(0.0, 2.0, 0.0), SoundEvents.BLOCK_IRON_DOOR_OPEN, 1.0F, 0.5F);
			sound(o(0.0, 2.0, 0.0), SoundEvents.BLOCK_GRINDSTONE_USE, 0.8F, 0.5F);
			sound(o(0.0, 2.0, 0.0), SoundEvents.BLOCK_PISTON_EXTEND, 0.5F, 0.5F);
		}
		if (f > 184 && f < 258 && f % 9 == 0) {
			sound(at("Denji"), SoundEvents.BLOCK_STONE_STEP, 0.35F, 0.8F + (f % 4) * 0.08F);
			sound(at("Y3"), SoundEvents.BLOCK_STONE_STEP, 0.3F, 0.7F + (f % 3) * 0.08F);
		}
		// 4, Shut: the doors booming shut behind them.
		if (f == 263 || f == 265 || f == 267) {
			float open = (267 - f) / 6.0F;
			server.execute(() -> ReelSet.doors(server.getOverworld(), o, open));
		}
		if (f == 267) {
			sound(o(0.0, 2.0, 0.0), SoundEvents.BLOCK_ANVIL_LAND, 0.9F, 0.5F);
			sound(o(0.0, 2.0, 0.0), SoundEvents.BLOCK_IRON_DOOR_CLOSE, 1.0F, 0.5F);
			sound(o(0.0, 2.0, 0.0), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8F, 0.5F);
		}
		// 5, Stabbed: two of them come up on him with knives, one behind him, one at his side; Denji in the back and Pochita
		// in his arms at once. He drops Pochita, goes down on his knees, and over.
		if (f == STABBED) {
			ACTORS.get("Y1").equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			ACTORS.get("Y2").equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		}
		if (f == STAB - 3) {
			act("Y1", Act.STAB, f);
			act("Y2", Act.STAB, f);
		}
		if (f == STAB) {
			act("Denji", Act.HURT, f);
			for (String hurt : new String[] {"Denji", "Pochita"}) {
				LivingEntity e = ACTORS.get(hurt);
				e.hurtTime = 10;
				e.maxHurtTime = 10;
			}
			Vec3d back = at("Denji").add(-0.25, 1.15, 0.0);
			Vec3d side = at("Pochita").add(0.0, 0.4, 0.0);
			sound(back, SoundEvents.ITEM_TRIDENT_HIT, 1.0F, 0.8F);
			sound(side, SoundEvents.ITEM_TRIDENT_HIT, 0.9F, 1.1F);
			sound(back, SoundEvents.ENTITY_PLAYER_HURT, 1.0F, 1.0F);
			sound(side, SoundEvents.ENTITY_WOLF_HURT, 1.0F, 1.4F);
			bleed(world, back, 14);
			bleed(world, side, 10);
			Sfx.show(Sfx.CHOP, 0.11F, 14, now);
		}
		if (f == STAB + 9) {
			pochita().setFainted(true);
			sound(at("Pochita"), SoundEvents.ENTITY_PLAYER_SMALL_FALL, 1.0F, 0.8F);
		}
		if (f == STAB + 11) {
			act("Denji", Act.KNEEL, f);
		}
		if (f == STAB + 17) {
			act("Y1", Act.STAND, f);
			act("Y2", Act.STAND, f);
		}
		if (f == STAB + 37) {
			act("Denji", Act.LIE, f);
		}
		if (f == STAB + 43) {
			sound(at("Denji"), SoundEvents.ENTITY_PLAYER_BIG_FALL, 0.8F, 0.9F);
		}
		// 6, The devil: something rising out of the floor behind the boss; its groan.
		if (f == DEVIL + 10) {
			sound(o(17.6, 0.5, 0.2), SoundEvents.ENTITY_WARDEN_EMERGE, 1.0F, 0.8F);
		}
		if (f == DEVIL + 52) {
			sound(o(17.6, 3.0, 0.2), SoundEvents.ENTITY_ZOMBIE_AMBIENT, 1.6F, 0.4F);
			sound(o(17.6, 3.0, 0.2), SoundEvents.ENTITY_RAVAGER_AMBIENT, 1.2F, 0.55F);
		}
		if (f == DEVIL + 60) {
			sound(o(17.6, 1.0, 0.2), SoundEvents.ENTITY_SLIME_SQUISH, 1.0F, 0.5F);
		}
		// 7, They turn: a twitch, their bones cracking, and their faces gone grey and bloody; more of them out of the dark.
		turning(client, "Y1", "ZYakuza1", TURN + 4, f);
		turning(client, "Boss", "ZBoss", TURN + 30, f);
		turning(client, "Y2", "ZYakuza2", TURN + 36, f);
		turning(client, "Y3", "ZYakuza3", TURN + 38, f);
		turning(client, "Y4", "ZYakuza4", TURN + 40, f);
		if (f == TURN + 54) {
			actor(client, "Z5", "ZYakuza2", o(20.4, 0.0, -6.4), 120.0F);
			actor(client, "Z6", "ZYakuza3", o(20.4, 0.0, 6.2), 60.0F);
			actor(client, "Z7", "ZYakuza1", o(3.6, 0.0, -5.6), -40.0F);
			for (String z : new String[] {"Z5", "Z6", "Z7"}) {
				act(z, Act.ZOMBIE, f);
			}
		}
		if (f == TURN + 58 || f == TURN + 66 || f == TURN + 74) {
			String who = f == TURN + 58 ? "Z5" : f == TURN + 66 ? "Y3" : "Z6";
			sound(at(who).add(0.0, 1.6, 0.0), SoundEvents.ENTITY_ZOMBIE_AMBIENT, 1.0F, 0.75F + (f % 3) * 0.06F);
		}
		// 8, Too many: they close round him from every side, arms out.
		if (f == TOO_MANY + 10 || f == TOO_MANY + 22 || f == TOO_MANY + 36 || f == TOO_MANY + 50) {
			String who = SWARM[(f / 7) % SWARM.length];
			sound(at(who).add(0.0, 1.6, 0.0), SoundEvents.ENTITY_ZOMBIE_AMBIENT, 1.1F, 0.7F + (f % 5) * 0.05F);
		}
		if (f == TOO_MANY + 50) {
			for (String z : SWARM) {
				act(z, Act.GRAB, f);
			}
		}
		// 9, Cut down: their arms coming down on him, out of sight below; blood up over the devil's face and the lens.
		if (f == CUT_DOWN + 2) {
			for (int i = 0; i < SWARM.length; i++) {
				act(SWARM[i], Act.CHOP, f + i * 1.7);
			}
		}
		for (int chop : CHOPS) {
			if (f == chop) {
				Vec3d d = origin.add(DOWN).add(0.0, 0.4, 0.0);
				sound(d, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.9F, 0.7F);
				sound(d, SoundEvents.ITEM_TRIDENT_HIT, 0.8F, 0.7F);
				sound(d, SoundEvents.BLOCK_SLIME_BLOCK_BREAK, 0.9F, 0.6F);
				bleed(world, d, 7);
			}
		}
		if (f == CHOPS[1] || f == CHOPS[4]) {
			Sfx.show(Sfx.CHOP, 0.11F, 12, now);
		}
		if (f == CHOPS[2]) {
			LensBlood.splash(4, now);
		}
		if (f == CHOPS[3]) {
			// In pieces: his arms and legs off.
			ClientCombat.event(new FormEventPayload(ACTORS.get("Denji").getUuid(), FormEventPayload.DOWNED, 1));
			act("Denji", Act.LIE, f - 20);
			LensBlood.splash(6, now);
		}
		if (f == CUT_DOWN + 60) {
			for (String z : SWARM) {
				act(z, Act.ZOMBIE, f);
			}
		}
		// 10, Dragged out: two of them drag what's left of him out the back and heave it in the dumpster; Pochita after him.
		if (f == DRAGGED) {
			act("Y2", Act.DRAG, f);
			act("Y3", Act.DRAG, f);
			act("Z5", Act.HOLD, f);
		}
		if (f > DRAGGED + 6 && f < DRAGGED + 52 && f % 7 == 0) {
			sound(at("Denji"), SoundEvents.BLOCK_GRAVEL_STEP, 0.5F, 0.6F);
		}
		if (f == DRAGGED + 52) {
			act("Y2", Act.HEAVE, f);
			act("Y3", Act.HEAVE, f);
		}
		if (f == DRAGGED + 68) {
			sound(o(27.0, 0.6, 3.8), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.7F, 0.6F);
		}
		if (f == DRAGGED + 72) {
			act("Z5", Act.HEAVE, f);
		}
		if (f == DRAGGED + 82) {
			sound(o(27.6, 0.6, 3.8), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.4F, 0.9F);
		}
		if (f == DRAGGED + 86) {
			act("Y2", Act.ZOMBIE, f);
			act("Y3", Act.ZOMBIE, f);
			act("Z5", Act.ZOMBIE, f);
		}
		// 11, The lid: one of them slams it down on them.
		if (f == LID + 4) {
			act("Y3", Act.HEAVE, f);
		}
		if (f == SHUT_IN - 1) {
			act("Y3", Act.ZOMBIE, f);
		}
		if (f == SHUT_IN) {
			sound(o(27.0, 1.0, 3.8), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0F, 0.5F);
			sound(o(27.0, 1.0, 3.8), SoundEvents.BLOCK_ANVIL_LAND, 0.5F, 0.6F);
		}
		if (f > SHUT_IN && f < ITS_OWN - 10 && (f - SHUT_IN) % 22 == 4) {
			// The rain on the lid, muffled.
			sound(o(27.0, 1.2, 3.8), SoundEvents.WEATHER_RAIN_ABOVE, 0.35F, 0.55F);
		}
		// 12, Dead, in the dark of it: his blood drips onto Pochita, and Pochita stirs, just.
		if (f == DRIPS || f == DRIPS + 10 || f == DRIPS + 16) {
			Vec3d drop = origin.add(POCHITA_IN).add(-0.15, 0.55, -0.2);
			for (int i = 0; i < 3; i++) {
				world.addParticle(DROP, drop.x, drop.y + i * 0.06, drop.z, 0.0, -0.05, 0.0);
			}
			world.addParticle(BLOOD, drop.x, drop.y, drop.z, 0.0, -0.08, 0.0);
		}
		if (f == DRIPS + 6 || f == DRIPS + 16 || f == DRIPS + 22) {
			sound(origin.add(POCHITA_IN), SoundEvents.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, 0.7F, 0.7F);
		}
		if (f == STIRS) {
			sound(origin.add(POCHITA_IN), SoundEvents.ENTITY_WOLF_WHINE, 0.35F, 1.5F);
		}
		// 13, You, swapped in for his stand-in, and the merge, under the black.
		if (f == SWAP) {
			for (String gone : new String[] {"Denji", "Pochita"}) {
				LivingEntity e = ACTORS.remove(gone);
				if (e != null) {
					world.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
				}
			}
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			server.execute(() -> {
				ServerPlayerEntity p = player(server);
				Vec3d feet = origin.add(IN_THE_BIN);
				p.changeGameMode(GameMode.SURVIVAL);
				p.setInvisible(true);
				p.teleport(server.getOverworld(), feet.x, feet.y, feet.z, 90.0F, 0.0F);
				p.setBodyYaw(90.0F);
				p.setHeadYaw(90.0F);
				p.getInventory().setStack(p.getInventory().selectedSlot, new ItemStack(ChainsawMan.POCHITA));
			});
		}
		if (f == MERGE) {
			server.execute(() -> Merge.start(player(server)));
		}
		if (f == ITS_OWN - 10) {
			server.execute(() -> player(server).setInvisible(false));
		}
		if (f == ITS_OWN) {
			Capture.camera = null;
		}
		// The merge, all its own; under the lid again as your eyes open, the rain muffled on it.
		if (f >= AWAKE - 14 && f < AWAKE && (f - AWAKE) % 8 == 0) {
			sound(o(27.0, 1.2, 3.8), SoundEvents.WEATHER_RAIN_ABOVE, 0.3F, 0.55F);
		}
		// 14, Awake: the lid bursts open; you rise out of it in the rain, the cord in your chest; the ones left flinch.
		if (f == AWAKE) {
			Capture.camera = time -> cam(now());
			sound(o(27.0, 1.0, 3.8), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0F, 0.7F);
			sound(o(27.0, 1.0, 3.8), SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, 1.0F, 0.6F);
			sound(o(27.0, 1.0, 3.8), SoundEvents.BLOCK_ANVIL_LAND, 0.4F, 0.8F);
			for (String z : new String[] {"Y2", "Y3", "Z5"}) {
				act(z, Act.TWITCH, f);
			}
		}
		if (f == AWAKE + 14) {
			for (String z : new String[] {"Y2", "Y3", "Z5"}) {
				act(z, Act.ZOMBIE, f);
			}
		}
		if (f == AWAKE + 18 || f == AWAKE + 46) {
			sound(origin.add(IN_THE_BIN).add(0.0, 1.5, 0.0), SoundEvents.ENTITY_PLAYER_BREATH, 0.9F, 0.9F);
		}
		// 15, The cord pulled: the transformation, all its own, its own camera.
		if (f == PULL) {
			Capture.camera = null;
			server.execute(() -> Form.pullCord(player(server)));
		}
		if (f == PULL + FormTimeline.ROAR_END) {
			Capture.camera = time -> cam(now());
		}
		// 16, Chainsaw Man: the ones by the bin come at him, and the rest out of the back door; he cuts them all down.
		for (int i = 0; i < KILLS.length; i++) {
			if (f == KILLS[i] - 2) {
				server.execute(() -> Saw.slash(player(server)));
				sound(origin.add(you(f)).add(0.0, 1.2, 0.0), ChainsawSounds.SLASH, 1.0F, 1.0F);
			}
			if (f == KILLS[i]) {
				LivingEntity dead = ACTORS.get(KILLED[i]);
				if (dead != null) {
					Vec3d hit = dead.getPos().add(0.0, 1.1, 0.0);
					ClientCombat.event(new FormEventPayload(client.player.getUuid(), FormEventPayload.IMPACT, dead.getId(), hit.x, hit.y, hit.z));
					ClientCombat.event(new FormEventPayload(client.player.getUuid(), FormEventPayload.KILL, dead.getId()));
					ACTORS.remove(KILLED[i]);
					world.removeEntity(dead.getId(), Entity.RemovalReason.KILLED);
					sound(hit, ChainsawSounds.HIT, 1.0F, 0.85F + i % 3 * 0.08F);
				}
			}
		}
		if (f == FORM_ON + 2 || f == OUT + 40 || f == DASH + 2 || f == OUT + 80) {
			String who = f == FORM_ON + 2 ? "Y2" : f == OUT + 40 ? "Z5" : f == DASH + 2 ? "Y1" : "Z6";
			sound(at(who).add(0.0, 1.6, 0.0), SoundEvents.ENTITY_ZOMBIE_AMBIENT, 1.1F, 0.7F + (f % 3) * 0.06F);
		}
		// 17, The Zombie Devil: the leap, the cut through it, the blood raining down on him.
		if (f == LEAP) {
			sound(origin.add(INSIDE).add(0.0, 1.0, 0.0), ChainsawSounds.START, 1.2F, 1.0F);
		}
		if (f == CUT - 2) {
			server.execute(() -> Saw.slash(player(server)));
			sound(origin.add(you(f)).add(0.0, 1.2, 0.0), ChainsawSounds.SLASH, 1.2F, 0.8F);
		}
		if (f == CUT) {
			Vec3d c = origin.add(devilAt(CUT)).add(0.0, -1.3, 0.0);
			sound(c, ChainsawSounds.HIT, 1.4F, 0.6F);
			sound(c, ChainsawSounds.CRUNCH, 1.4F, 0.6F);
			sound(c, SoundEvents.ENTITY_RAVAGER_DEATH, 1.3F, 0.6F);
			sound(c, SoundEvents.ENTITY_SLIME_DEATH, 1.4F, 0.5F);
			bleed(world, c, 70);
			for (int i = 0; i < 40; i++) {
				world.addParticle(DROP, c.x, c.y, c.z, (world.random.nextDouble() - 0.5) * 0.9, world.random.nextDouble() * 0.6,
						(world.random.nextDouble() - 0.5) * 0.9);
			}
			Sfx.show(Sfx.KILL, 0.2F, 26, now);
			LensBlood.splash(8, now);
		}
		if (f >= CUT && f < CUT + 70) {
			// Its blood, raining down.
			for (int i = 0; i < 12; i++) {
				Vec3d drop = origin.add(LANDING).add((world.random.nextDouble() - 0.4) * 7.0, 6.5 + world.random.nextDouble(),
						(world.random.nextDouble() - 0.5) * 6.0);
				world.addParticle(i % 3 == 0 ? BLOOD : DROP, drop.x, drop.y, drop.z, 0.0, -0.25, 0.0);
			}
		}
		if (f == CUT + 15) {
			Vec3d c = origin.add(devilAt(CUT)).withAxis(net.minecraft.util.math.Direction.Axis.Y, origin.y + 0.3);
			sound(c, SoundEvents.ENTITY_SLIME_SQUISH, 1.4F, 0.4F);
			sound(c, SoundEvents.BLOCK_ANVIL_LAND, 0.5F, 0.5F);
		}
		if (f == LAND) {
			sound(origin.add(LANDING), SoundEvents.ENTITY_PLAYER_BIG_FALL, 1.0F, 0.8F);
		}
		if (f == LAND + 10 || f == LAND + 34) {
			sound(origin.add(LANDING).add(0.0, 1.5, 0.0), ChainsawSounds.IDLE, 1.0F, 1.0F);
		}
		if (f == LAND + 22) {
			sound(origin.add(LANDING).add(0.0, 1.5, 0.0), ChainsawSounds.START, 1.0F, 1.1F);
		}
	}

	/** Where you are at {@code s} as Chainsaw Man: out over the bin's front; at the back door; in at the devil; landing. */
	private static Vec3d you(double s) {
		if (s < OUT) {
			return IN_THE_BIN;
		}
		if (s < OUT + 10) {
			double k = span(s, OUT, OUT + 10);
			return IN_THE_BIN.lerp(STAND, k).add(0.0, 1.35 * 4.0 * k * (1.0 - k), 0.0);
		}
		if (s < DASH) {
			return STAND;
		}
		if (s < DASH + 8) {
			return STAND.lerp(STEPS[0], ease(span(s, DASH, DASH + 8)));
		}
		for (int i = 1; i < STEPS.length; i++) {
			if (s < KILLS[3 + i] - 4) {
				return STEPS[i - 1];
			}
			if (s < KILLS[3 + i]) {
				return STEPS[i - 1].lerp(STEPS[i], ease(span(s, KILLS[3 + i] - 4, KILLS[3 + i])));
			}
		}
		if (s < LEAP) {
			return STEPS[STEPS.length - 1].lerp(INSIDE, span(s, KILLS[7] + 4, LEAP - 2));
		}
		if (s < LAND) {
			double k = span(s, LEAP, LAND);
			return INSIDE.lerp(LANDING, k).add(0.0, 2.6 * 4.0 * k * (1.0 - k), 0.0);
		}
		return LANDING;
	}

	/** The way you face at {@code s}: at each of them as they come; at the door, and in; at the camera, landed. */
	private static float youYaw(double s) {
		if (s < OUT) {
			return 90.0F;
		}
		for (int i = 0; i < 3; i++) {
			if (s < KILLS[i] + 2) {
				float from = i == 0 ? 90.0F : yaw(STAND, KILLED_AT[i - 1]);
				return turn(from, yaw(STAND, KILLED_AT[i]), ease(span(s, i == 0 ? OUT : KILLS[i - 1] + 2, KILLS[i] - 3)));
			}
		}
		return turn(yaw(STAND, KILLED_AT[2]), 90.0F, ease(span(s, KILLS[2] + 2, DASH)));
	}

	/** Blood out of {@code at}, up and a little out. */
	private static void bleed(ClientWorld world, Vec3d at, int count) {
		for (int i = 0; i < count; i++) {
			double a = world.random.nextDouble() * Math.PI * 2.0;
			double out = 0.06 + world.random.nextDouble() * 0.12;
			world.addParticle(i % 3 == 0 ? DARK_BLOOD : BLOOD, at.x, at.y, at.z, Math.cos(a) * out, 0.3 + world.random.nextDouble() * 0.45,
					Math.sin(a) * out);
		}
	}

	/** {@code key} twitching from {@code at}, then turned: the zombie's skin for theirs, the cracking, the gurgle. */
	private static void turning(MinecraftClient client, String key, String skin, int at, int f) {
		if (f == at) {
			act(key, Act.TWITCH, f);
		}
		if (f == at + 2 || f == at + 8) {
			sound(at(key).add(0.0, 1.4, 0.0), f == at + 2 ? SoundEvents.ENTITY_SKELETON_HURT : SoundEvents.BLOCK_BONE_BLOCK_BREAK, 0.9F,
					0.6F + (f % 3) * 0.1F);
		}
		if (f == at + 14) {
			LivingEntity was = ACTORS.remove(key);
			if (was != null) {
				client.world.removeEntity(was.getId(), Entity.RemovalReason.DISCARDED);
				actor(client, key, skin, was.getPos(), was.getBodyYaw()).equipStack(EquipmentSlot.MAINHAND, was.getMainHandStack().copy());
			}
			act(key, Act.ZOMBIE, f);
		}
		if (f == at + 16) {
			sound(at(key).add(0.0, 1.5, 0.0), SoundEvents.ENTITY_DROWNED_AMBIENT, 1.0F, 0.6F);
		}
	}

	/** Everyone where they are at {@code s} (reel ticks): each cut's blocking. */
	private static void places(double s) {
		Vec3d door = o(-20.0, 1.5, -1.0);
		Vec3d d = origin.add(DENJI);
		if (s < KNOCK) {
			// 1, On the floor of the shack: Denji eating, facing out to the room; Pochita sitting by him, watching the bread.
			put("Denji", o(-23.6, 0.0, -0.9), -50.0F, -45.0F + (float) Math.sin(s * 0.05) * 8.0F, 18.0F);
			Vec3d p = o(-22.85, 0.0, -1.55);
			float to = yaw(p, o(-23.6, 0.0, -0.9));
			put("Pochita", p, to, to, -28.0F);
			put("Y1", o(-18.4, 0.0, -0.75), 90.0F, 90.0F, 10.0F);
		} else if (s < WAREHOUSE) {
			// 2, The knock: Denji looking up at the door; Pochita up and to it; the door open on a yakuza; Denji up, Pochita picked up.
			Vec3d sat = o(-23.6, 0.0, -0.9);
			Vec3d by = o(-22.95, 0.0, -1.15);
			float look = yaw(sat, door);
			Vec3d at = s < 138 ? sat : walk(sat, by, s, 138, 150);
			float body = s < 136 ? -50.0F : turn(-50.0F, -90.0F, ease(span(s, 136, 142)));
			put("Denji", at, body, s < 104 ? -45.0F : turn(-45.0F, look, ease(span(s, 104, 112))), s < 150 ? 5.0F : 25.0F);
			Vec3d sits = o(-22.85, 0.0, -1.55);
			Vec3d near = o(-22.3, 0.0, -1.25);
			Vec3d p = walk(sits, near, s, 118, 132);
			float to = turn(yaw(sits, o(-23.6, 0.0, -0.9)), yaw(p, door), ease(span(s, 104, 112)));
			if (s >= 150) {
				// Picked up.
				p = near.lerp(held(by, -90.0F), ease(span(s, 150, 155)));
				to = turn(to, -90.0F, ease(span(s, 150, 155)));
			}
			put("Pochita", p, to, to, -10.0F);
			put("Y1", o(-19.15, 0.0, -0.75), 90.0F, 90.0F, 12.0F);
		} else if (s < SHUT) {
			// 3, In through the warehouse's doors as they open: Denji with Pochita in his arms, the boss ahead, four behind.
			Vec3d at = walk(o(-3.6, 0.0, 0.0), d, s, 182, 258);
			put("Denji", at, -90.0F, -90.0F, 4.0F);
			put("Pochita", held(at, -90.0F), -90.0F, -90.0F, 0.0F);
			put("Boss", walk(o(-2.4, 0.0, -1.2), o(12.6, 0.0, -0.5), s, 180, 258), -90.0F, -90.0F, 0.0F);
			put("Y1", walk(o(-5.2, 0.0, 1.1), o(6.9, 0.0, 1.1), s, 184, 260), -90.0F, -90.0F, 0.0F);
			put("Y2", walk(o(-5.2, 0.0, -1.1), o(6.9, 0.0, -1.1), s, 186, 260), -90.0F, -90.0F, 0.0F);
			put("Y3", walk(o(-6.6, 0.0, 0.9), o(5.5, 0.0, 0.9), s, 183, 258), -90.0F, -90.0F, 0.0F);
			put("Y4", walk(o(-6.6, 0.0, -0.9), o(5.5, 0.0, -0.9), s, 185, 259), -90.0F, -90.0F, 0.0F);
		} else if (s < STABBED) {
			// 4, The doors slam; Denji looks back at them, and round again; they step back into the dark round him.
			float body = (float) MathHelper.lerp(ease(span(s, 272, 282)) - ease(span(s, 300, 312)), -90.0, -270.0);
			put("Denji", d, body, body, 6.0F);
			put("Pochita", held(d, body), body, body, 0.0F);
			Vec3d boss = walk(o(12.6, 0.0, -0.5), o(14.0, 0.0, 0.0), s, 262, 280);
			float bossYaw = (float) MathHelper.lerp(ease(span(s, 280, 290)), -90.0, 90.0);
			put("Boss", boss, bossYaw, bossYaw, 0.0F);
			back("Y1", o(6.9, 0.0, 1.1), o(6.0, 0.0, 2.4), s, d);
			back("Y2", o(6.9, 0.0, -1.1), o(6.3, 0.0, -2.5), s, d);
			back("Y3", o(5.5, 0.0, 0.9), o(9.6, 0.0, 5.6), s, d);
			back("Y4", o(5.5, 0.0, -0.9), o(10.6, 0.0, -5.4), s, d);
		} else if (s < TOO_MANY) {
			// 5 to 7: two of them coming up on him and Pochita with knives; Denji down, Pochita beside him; the devil rising
			// behind the boss; them turning; more of them out of the dark, creeping in.
			Acting a = ACTORS.containsKey("Denji") ? ACTING.get(ACTORS.get("Denji").getId()) : null;
			put("Denji", d, -90.0F, -90.0F, a != null && a.act() == Act.HURT ? -25.0F : 10.0F);
			Vec3d pochita = s < STAB + 3 ? held(d, -90.0F) : held(d, -90.0F).lerp(origin.add(POCHITA_DOWN), span(s, STAB + 3, STAB + 9))
					.add(0.0, 0.25 * Math.sin(Math.PI * span(s, STAB + 3, STAB + 9)), 0.0);
			put("Pochita", pochita, -60.0F, -60.0F, 0.0F);
			Vec3d y1 = walk(o(6.0, 0.0, 2.4), o(7.75, 0.0, 0.3), s, STABBED + 6, STABBED + 30);
			if (s >= STAB + 25) {
				y1 = walk(o(7.75, 0.0, 0.3), o(6.6, 0.0, 1.3), s, STAB + 25, STAB + 43);
			}
			float y1Yaw = s < STAB + 25 ? yaw(y1, d.add(0.0, 0.0, 0.0)) : turn(yaw(o(7.75, 0.0, 0.3), d), -90.0F, ease(span(s, STAB + 25, STAB + 40)));
			put("Y1", y1, y1Yaw, y1Yaw, 15.0F);
			Vec3d y2 = walk(o(6.3, 0.0, -2.5), o(9.05, 0.0, -1.0), s, STABBED + 10, STABBED + 32);
			if (s >= STAB + 25) {
				y2 = walk(o(9.05, 0.0, -1.0), o(8.2, 0.0, -2.3), s, STAB + 25, STAB + 43);
			}
			float y2Yaw = yaw(y2, s < STAB + 25 ? held(d, -90.0F) : origin.add(DOWN));
			put("Y2", y2, y2Yaw, y2Yaw, 20.0F);
			put("Boss", s < TURN + 60 ? o(14.0, 0.0, 0.0) : walk(o(14.0, 0.0, 0.0), o(12.6, 0.0, 0.8), s, TURN + 60, TOO_MANY + 40), 90.0F, 90.0F, 8.0F);
			creep("Y3", o(9.6, 0.0, 5.6), o(11.0, 0.0, 3.6), s, TURN + 60);
			creep("Y4", o(10.6, 0.0, -5.4), o(11.4, 0.0, -3.4), s, TURN + 60);
			creep("Z5", o(20.4, 0.0, -6.4), o(14.5, 0.0, -3.6), s, TURN + 54);
			creep("Z6", o(20.4, 0.0, 6.2), o(14.0, 0.0, 3.8), s, TURN + 54);
			creep("Z7", o(3.6, 0.0, -5.6), o(5.0, 0.0, -4.0), s, TURN + 54);
		} else if (s < DRAGGED) {
			// 8 and 9: the swarm closing round him where he lies, from every side; their arms coming down; him in pieces.
			put("Denji", d, -90.0F, -90.0F, 10.0F);
			put("Pochita", origin.add(POCHITA_DOWN), -60.0F, -60.0F, 0.0F);
			double k = ease(span(s, TOO_MANY + 2, TOO_MANY + 54));
			double rx = MathHelper.lerp(k, 4.2, 1.55);
			double rz = MathHelper.lerp(k, 4.2, 1.0);
			for (int i = 0; i < SWARM.length; i++) {
				double angle = Math.toRadians(i * 45.0 + 15.0);
				Vec3d z = origin.add(DOWN).add(Math.cos(angle) * rx, 0.0, Math.sin(angle) * rz);
				float at = yaw(z, origin.add(DOWN));
				put(SWARM[i], z, at, at, 25.0F);
			}
		} else if (s < LID) {
			// 10, The alley: dragged out the back door between two of them, heaved in the bin; Pochita carried out and thrown
			// in after him by a third. Straight out through the middle of the door, then over to the bin.
			Vec3d[] way = {o(20.6, 0.0, 0.5), o(23.8, 0.0, 0.5), o(25.4, 0.0, 1.75)};
			Vec3d by = path(way, s, DRAGGED + 6, DRAGGED + 30, DRAGGED + 52);
			Vec3d along = (s < DRAGGED + 30 ? way[1].subtract(way[0]) : way[2].subtract(way[1])).normalize();
			Vec3d across = new Vec3d(-along.z, 0.0, along.x);
			float ahead = yaw(Vec3d.ZERO, along);
			Vec3d at = by;
			Vec3d in = origin.add(DENJI_IN);
			float body = ahead;
			if (s >= DRAGGED + 56) {
				double k = span(s, DRAGGED + 56, DRAGGED + 68);
				at = way[2].lerp(in, k).add(0.0, 1.9 * 4.0 * k * (1.0 - k), 0.0);
				body = turn(ahead, 90.0F, k);
			}
			put("Denji", at, body, body, 0.0F);
			put("Y2", by.add(along.multiply(1.05)).add(across.multiply(0.42)), ahead, ahead + 10.0F, 25.0F);
			put("Y3", by.add(along.multiply(1.05)).add(across.multiply(-0.42)), ahead, ahead - 10.0F, 25.0F);
			Vec3d[] carrying = {o(20.2, 0.0, 0.5), o(23.6, 0.0, 0.5), o(24.9, 0.0, 2.4)};
			Vec3d carry = path(carrying, s, DRAGGED + 22, DRAGGED + 42, DRAGGED + 68);
			float carryYaw = s < DRAGGED + 42 ? -90.0F : yaw(carrying[1], o(27.0, 0.0, 3.8));
			put("Z5", carry, carryYaw, carryYaw, 20.0F);
			Vec3d pochita = held(carry, carryYaw).add(0.0, 0.15, 0.0);
			if (s >= DRAGGED + 74) {
				double k = span(s, DRAGGED + 74, DRAGGED + 82);
				pochita = held(carrying[2], carryYaw).lerp(origin.add(POCHITA_IN), k).add(0.0, 1.2 * 4.0 * k * (1.0 - k), 0.0);
			}
			put("Pochita", pochita, carryYaw, carryYaw, 0.0F);
		} else if (s < AWAKE) {
			// 11, One of them at the bin's front, reaching up for the lid, slamming it down.
			put("Y3", walk(o(26.49, 0.0, 2.06), o(27.6, 0.0, 2.45), s, LID, LID + 8), 0.0F, 0.0F, -20.0F);
			// 12, Pochita stirring at the blood on him: just.
			double stir = Math.max(Math.exp(-Math.abs(s - STIRS) * 0.9), Math.exp(-Math.abs(s - STIRS - 7) * 0.9));
			put("Pochita", origin.add(POCHITA_IN).add(0.0, s > STIRS - 4 && s < STIRS + 12 ? 0.06 * stir : 0.0, 0.0), 60.0F,
					60.0F + (s > STIRS ? 12.0F * (float) stir : 0.0F), 0.0F);
		} else {
			// 14 and 16, The ones left, round the bin as you rise out of it, then coming at you; the rest out of the back
			// door at you, one after another.
			Vec3d you = origin.add(you(s));
			Vec3d[][] ways = {{o(28.6, 0.0, 1.1), o(28.6, 0.0, 1.1), origin.add(KILLED_AT[0])},
					{o(30.0, 0.0, 4.6), o(29.4, 0.0, 2.4), origin.add(KILLED_AT[1])},
					{o(29.2, 0.0, 6.6), o(24.9, 0.0, 5.6), origin.add(KILLED_AT[2])}};
			for (int i = 0; i < 3; i++) {
				Vec3d at = path(ways[i], s, FORM_ON + 2 + i * 2, FORM_ON + 30, KILLS[i] - 2);
				float face = s < FORM_ON ? yaw(at, o(27.0, 0.0, 3.8)) : yaw(at, you);
				put(KILLED[i], at, face, face, 10.0F);
			}
			for (int i = 3; i < KILLED.length; i++) {
				Vec3d from = o(20.6 - (i - 3) * 0.9, 0.0, 0.5);
				Vec3d at = walk(from, origin.add(KILLED_AT[i]), s, FORM_ON + 10 + (i - 3) * 6, KILLS[i] - 2);
				float face = yaw(at, you);
				put(KILLED[i], at, face, face, 15.0F);
			}
		}
	}

	/** {@code key} stepping back from beside Denji into the dark, as the doors slam, facing him. */
	private static void back(String key, Vec3d by, Vec3d to, double s, Vec3d denji) {
		Vec3d at = walk(by, to, s, 276, 306);
		float face = yaw(at, denji);
		put(key, at, face, face, 0.0F);
	}

	/** {@code key} creeping in on him from {@code from}, out of the dark, from {@code at} on. */
	private static void creep(String key, Vec3d from, Vec3d to, double s, double at) {
		if (!ACTORS.containsKey(key)) {
			return;
		}
		Vec3d now = walk(from, to, s, at, TOO_MANY + 10);
		float face = yaw(now, origin.add(DOWN));
		put(key, now, face, face, 20.0F);
	}

	/** At a walk along {@code way}: its first leg from {@code from} to {@code turn}, its second from there to {@code to}. */
	private static Vec3d path(Vec3d[] way, double s, double from, double turn, double to) {
		return s < turn ? walk(way[0], way[1], s, from, turn) : walk(way[1], way[2], s, turn, to);
	}

	/** Where Pochita is held in the arms of whoever is at {@code at} facing {@code yaw}: out in front, at the chest. */
	private static Vec3d held(Vec3d at, float yaw) {
		return at.add(Vec3d.fromPolar(0.0F, yaw).multiply(0.5)).add(0.0, 0.62, 0.0);
	}

	/** The Zombie Devil's neck at {@code t}: rising up out of the floor behind the boss, then hanging there. */
	private static Vec3d devilAt(double t) {
		double k = ease(span(t, DEVIL + 8, DEVIL + 72));
		return new Vec3d(MathHelper.lerp(k, 18.6, 17.6), MathHelper.lerp(k, -1.9, 3.3), 0.2);
	}

	/** How far open the bin's lid is at {@code t} (radians: 0 shut, 1.95 thrown back). */
	private static float lid(double t) {
		if (t < LID + 8) {
			return 1.95F;
		}
		if (t < SHUT_IN) {
			double k = span(t, LID + 8, SHUT_IN);
			return (float) (1.95 * (1.0 - k * k));
		}
		if (t < AWAKE - 7) {
			return 0.0F;
		}
		if (t < AWAKE) {
			// A crack of light at its edge as you gasp.
			return 0.12F * (float) ease(span(t, AWAKE - 7, AWAKE - 4));
		}
		// Burst open, past where it rests, and falling back to it.
		double k = span(t, AWAKE, AWAKE + 4);
		if (t < AWAKE + 4) {
			return (float) MathHelper.lerp(k * k, 0.12, 2.2);
		}
		return (float) MathHelper.lerp(ease(span(t, AWAKE + 4, AWAKE + 14)), 2.2, 1.95);
	}

	/**
	 * How black the screen is at {@code t}: in from it at the start; the lid banging shut, then the dark inside the bin
	 * (half), black again; the merge's dream out of it; out to it at the end.
	 */
	private static float black(double t) {
		float in = 1.0F - (float) span(t, 0.0, 10.0);
		float shut = (float) (span(t, SHUT_IN, SHUT_IN + 4) * (1.0 - 0.65 * span(t, DEAD + 6, DEAD + 16)));
		float dark = (float) (0.35 + 0.65 * span(t, BLACK, BLACK + 10));
		float lid = t < SHUT_IN ? 0.0F : t < BLACK ? shut : (float) (dark * (1.0 - span(t, ITS_OWN - 2, ITS_OWN + 6)));
		float out = (float) span(t, END - 22, END - 4);
		return MathHelper.clamp(Math.max(in, Math.max(lid, out)), 0.0F, 1.0F);
	}

	/** The camera at {@code t}, cut by cut. */
	private static Capture.Pose cam(double t) {
		Vec3d at;
		Vec3d look;
		if (t < KNOCK) {
			// 1, Wide, a little high, from the corner; easing in.
			double k = ease(span(t, 0, KNOCK));
			at = o(-21.15, 2.55, 0.75).lerp(o(-21.65, 2.2, 0.3), k);
			look = o(-23.3, 0.55, -1.2);
		} else if (t < WAREHOUSE) {
			// 2, Low in the corner, past Pochita at the door.
			at = o(-25.1, 0.55, -2.9).add(0.0, 0.0, span(t, KNOCK, WAREHOUSE) * 0.15);
			look = o(-20.0, 1.2, -0.9);
		} else if (t < SHUT) {
			// 3, Wide from inside, the doors at the end; easing in.
			double k = ease(span(t, WAREHOUSE, SHUT));
			at = o(15.5, 2.8, 2.4).lerp(o(14.8, 2.6, 2.1), k);
			look = o(2.0, 1.4, 0.0).lerp(o(4.5, 1.3, 0.0), k);
		} else if (t < STABBED) {
			// 4, Low, off to his side: him, the doors slamming beyond him.
			at = o(11.6, 0.7, 1.9);
			look = o(2.0, 1.6, -0.4).lerp(o(5.0, 1.5, 0.0), ease(span(t, 300, STABBED)));
		} else if (t < DEVIL) {
			// 5, Side on, close: one coming up behind him, one at his side; the knives; him going down.
			double k = ease(span(t, STABBED, DEVIL));
			at = o(8.6, 1.05, 3.2).lerp(o(8.75, 0.85, 2.85), k);
			look = o(8.4, 1.1, 0.0).lerp(o(8.9, 0.45, 0.0), ease(span(t, STAB + 11, STAB + 43)));
		} else if (t < TURN) {
			// 6, From the floor by his head, up past the boss at the devil rising behind him; slowly.
			double k = ease(span(t, DEVIL, TURN));
			at = o(10.6, 0.3, -0.9).lerp(o(10.8, 0.32, -0.8), k);
			look = o(15.8, 2.4, 0.1).lerp(o(16.9, 3.7, 0.1), k);
		} else if (t < TURN + 56) {
			// 7, Close on them, one after another, as they turn.
			boolean first = t < TURN + 28;
			Vec3d who = first ? o(6.6, 0.0, 1.3) : o(14.0, 0.0, 0.0);
			float yaw = first ? -90.0F : 90.0F;
			Vec3d face = who.add(0.0, 1.62, 0.0);
			Vec3d front = Vec3d.fromPolar(0.0F, yaw);
			Vec3d side = Vec3d.fromPolar(0.0F, yaw + 90.0F);
			double k = span(t, first ? TURN : TURN + 28, first ? TURN + 28 : TURN + 56);
			at = face.add(front.multiply(MathHelper.lerp(k, 2.4, 2.1))).add(side.multiply(0.4)).add(0.0, -0.1, 0.0);
			look = face.add(0.0, -0.2, 0.0);
		} else if (t < TOO_MANY) {
			// More of them out of the dark: wide from behind him.
			at = o(4.2, 2.2, -2.6);
			look = o(11.5, 1.2, 0.8);
		} else if (t < CUT_DOWN) {
			// 8, Overhead, him in the middle; turning a little, coming down.
			double k = span(t, TOO_MANY, CUT_DOWN);
			Vec3d over = origin.add(DOWN).add(0.0, MathHelper.lerp(ease(k), 5.6, 4.9), 0.0);
			return shaken(new Capture.Pose(over.x, over.y, over.z, (float) MathHelper.lerp(k, 90.0, 112.0), 89.9F), t);
		} else if (t < DRAGGED) {
			// 9, Low behind them, up at the Zombie Devil watching; easing in.
			double k = ease(span(t, CUT_DOWN, DRAGGED));
			at = o(5.9, 2.65, -1.45).lerp(o(6.4, 2.6, -1.35), k);
			look = o(17.4, 3.25, 0.15);
		} else if (t < LID) {
			// 10, The alley in the rain, from its far corner: out the back door, and across to the bin.
			at = o(30.8, 2.0, -2.0);
			look = o(23.5, 1.0, 0.6).lerp(o(27.0, 0.6, 3.8), ease(span(t, DRAGGED + 30, DRAGGED + 90)));
		} else if (t < DEAD) {
			// 11, Low off the bin's corner: him reaching up for its lid, and slamming it.
			at = o(29.6, 1.25, 0.6);
			look = o(27.3, 0.95, 3.6);
		} else {
			if (t < AWAKE) {
				// 12, In the dark of the shut bin, low, on Pochita by him.
				at = o(26.4, 0.92, 3.25);
				look = o(27.0, 0.2, 4.1);
			} else {
				if (t < PULL) {
					// 14, Low at the bin's end as it bursts open and you rise; then easing in on the cord in your chest.
					double k = ease(span(t, AWAKE + 40, PULL));
					double rise = ease(span(t, AWAKE, AWAKE + 40));
					at = o(23.6, 0.8, 2.4).lerp(o(23.9, 1.15, 2.9), rise).lerp(o(24.9, 1.45, 3.65), k);
					look = o(26.6, 1.0, 3.8).lerp(o(26.3, 1.4, 3.8), rise).lerp(o(26.0, 1.2, 3.8), k);
				} else if (t < DASH) {
					// 16, Low off the bin's front corner: out of it, and at the three of them by it.
					at = o(23.6, 1.1, -1.0);
					look = origin.add(you(t)).add(0.5, 1.1, 0.3);
				} else if (t < IN) {
					// From behind him at the back door, them coming out of it at him.
					at = o(28.6, 1.7, 2.6);
					look = origin.add(you(t)).add(0.0, 1.1, 0.0).lerp(o(22.4, 1.2, 0.6), 0.45);
				} else if (t < LEAP) {
					// 17, Inside, low, past the Zombie Devil: him coming in through the back door.
					at = o(15.0, 0.7, 2.6);
					look = o(21.0, 1.5, 0.4);
				} else if (t < LAND) {
					// Low and side on: the leap, and the cut.
					at = o(19.6, 0.35, 4.4);
					look = o(18.2, 2.6, 0.2);
				} else {
					// Low in front of him, in its blood coming down; easing in.
					double k = ease(span(t, LAND, END));
					at = o(12.6, 0.6, 1.6).lerp(o(13.2, 0.75, 1.4), k);
					look = o(16.4, 1.5, 0.5);
				}
			}
		}
		return shaken(look(at, look), t);
	}

	/** The lens for each cut (vertical field of view, degrees); 0 for the game's own (the merge's). */
	public static float lens() {
		if (stage != Stage.RUN || !scene() || Capture.camera == null) {
			return 0.0F;
		}
		double t = now();
		float[][] lenses = {{KNOCK, 78.0F}, {WAREHOUSE, 70.0F}, {SHUT, 60.0F}, {STABBED, 70.0F}, {DEVIL, 62.0F}, {TURN, 66.0F}, {TURN + 56, 58.0F},
				{TOO_MANY, 72.0F}, {CUT_DOWN, 85.0F}, {DRAGGED, 40.0F}, {LID, 62.0F}, {DEAD, 60.0F}, {AWAKE, 66.0F}};
		for (float[] lens : lenses) {
			if (t < lens[0]) {
				return lens[1];
			}
		}
		if (t < PULL) {
			return (float) MathHelper.lerp(ease(span(t, AWAKE + 40, PULL)), 70.0, 56.0);
		}
		float[][] fight = {{DASH, 62.0F}, {IN, 64.0F}, {LEAP, 60.0F}, {LAND, 72.0F}};
		for (float[] lens : fight) {
			if (t < lens[0]) {
				return lens[1];
			}
		}
		return 62.0F;
	}

	/** The camera shaken: the doors slamming, the knives, the devil rising, the lid banging shut and bursting open. */
	private static Capture.Pose shaken(Capture.Pose pose, double t) {
		double s = 0.0;
		for (int at : new int[] {267, SHUT_IN, AWAKE, CUT, LAND}) {
			s = Math.max(s, t >= at ? (at == CUT ? 0.09 : 0.06) * Math.exp(-(t - at) / 4.0) : 0.0);
		}
		s = Math.max(s, t >= STAB ? 0.025 * Math.exp(-(t - STAB) / 3.0) : 0.0);
		if (t > DEVIL + 10 && t < DEVIL + 72) {
			s = Math.max(s, 0.012);
		}
		if (s < 1.0E-4) {
			return pose;
		}
		return new Capture.Pose(pose.x() + Math.sin(t * 7.3) * s, pose.y() + Math.sin(t * 9.1 + 1.0) * s, pose.z() + Math.sin(t * 8.2 + 2.0) * s,
				pose.yaw(), pose.pitch());
	}

	/** Whether the world is to be gone (your merge's dream): its blocks, its weather, the bin. */
	public static boolean dreaming() {
		return active() && scene() && stage == Stage.RUN
				&& MergeCinematic.dream(MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true)) > 0.3F;
	}

	/** Whether the rain is shut out: under the lid (the dream and all). */
	public static boolean underTheLid() {
		if (!active() || !scene() || stage != Stage.RUN) {
			return false;
		}
		double t = now();
		return dreaming() || t >= SHUT_IN && t < AWAKE;
	}

	// The stand-ins.

	/** A stand-in for {@code key}, wearing {@code skin}'s skin, at {@code at}, facing {@code yaw}. */
	private static OtherClientPlayerEntity actor(MinecraftClient client, String key, String skin, Vec3d at, float yaw) {
		OtherClientPlayerEntity actor = new OtherClientPlayerEntity(client.world, new GameProfile(UUID.randomUUID(), skin));
		actor.setId(nextId--);
		actor.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
		actor.setHeadYaw(yaw);
		actor.setBodyYaw(yaw);
		actor.prevBodyYaw = yaw;
		actor.prevHeadYaw = yaw;
		FormSelfTest.showEveryLayer(actor);
		client.world.addEntity(actor);
		ACTORS.put(key, actor);
		PLACES.put(key, new Placed(at, yaw, yaw, 0.0F));
		return actor;
	}

	private static void put(String key, Vec3d at, float body, float head, float pitch) {
		PLACES.put(key, new Placed(at, body, head, pitch));
	}

	/** Moves {@code actor} toward where it's to be, over the next tick (or there at once, if it's far: a cut). */
	private static void place(LivingEntity actor, Placed p) {
		if (actor.getPos().squaredDistanceTo(p.at()) > 4.0) {
			actor.refreshPositionAndAngles(p.at().x, p.at().y, p.at().z, p.head(), p.pitch());
			actor.prevBodyYaw = p.body();
			actor.prevHeadYaw = p.head();
			actor.setHeadYaw(p.head());
		}
		actor.updateTrackedPositionAndAngles(p.at().x, p.at().y, p.at().z, p.head(), p.pitch(), 1);
		actor.updateTrackedHeadRotation(p.head(), 1);
		actor.setBodyYaw(p.body());
	}

	private static void act(String key, Act act, double since) {
		LivingEntity actor = ACTORS.get(key);
		if (actor != null) {
			ACTING.put(actor.getId(), new Acting(act, since));
		}
	}

	private static Vec3d at(String key) {
		LivingEntity actor = ACTORS.get(key);
		return actor != null ? actor.getPos() : origin;
	}

	private static PochitaEntity pochita() {
		return (PochitaEntity) ACTORS.get("Pochita");
	}

	/** A stand-in posed as it acts (the model's own angles set, the outer layers after them). */
	public static void pose(AbstractClientPlayerEntity player, PlayerEntityModel<?> model) {
		Acting acting = ACTING.get(player.getId());
		if (acting == null) {
			return;
		}
		double u = now() - acting.since();
		float t = (float) now();
		int id = player.getId();
		switch (acting.act()) {
			case SIT_EAT -> {
				model.rightLeg.pitch = -1.4137167F;
				model.rightLeg.yaw = 0.31415927F;
				model.rightLeg.roll = 0.07853982F;
				model.leftLeg.pitch = -1.4137167F;
				model.leftLeg.yaw = -0.31415927F;
				model.leftLeg.roll = -0.07853982F;
				// The bread up to his mouth, a bite, chewing, and down again.
				double p = (u + 26.0) % 30.0 / 30.0;
				double up = ease(span(p, 0.0, 0.12)) * (1.0 - ease(span(p, 0.35, 0.47)));
				model.rightArm.pitch = (float) MathHelper.lerp(up, -0.75, -2.05);
				model.rightArm.yaw = (float) MathHelper.lerp(up, -0.1, -0.55);
				model.leftArm.pitch = -0.6F;
				model.leftArm.yaw = 0.15F;
				model.head.pitch += (float) (up * Math.sin(t * 1.6) * 0.05);
			}
			case HOLD -> {
				model.rightArm.pitch = -1.05F;
				model.rightArm.yaw = -0.42F;
				model.rightArm.roll = 0.0F;
				model.leftArm.pitch = -1.05F;
				model.leftArm.yaw = 0.42F;
				model.leftArm.roll = 0.0F;
			}
			case STAB -> {
				// The knife driven in, held there, and pulled out; the other hand on him.
				double in = ease(span(u, 0.0, 3.0)) * (1.0 - ease(span(u, 12.0, 18.0)));
				model.rightArm.pitch = (float) MathHelper.lerp(in, -0.4, -1.55);
				model.rightArm.yaw = -0.1F;
				model.leftArm.pitch = -1.3F;
				model.leftArm.yaw = 0.25F;
			}
			case HURT -> {
				// Jolted: his head back, his arms flung out from Pochita.
				double k = 1.0 - ease(span(u, 6.0, 11.0));
				model.head.pitch = (float) (-0.45 * k);
				model.rightArm.pitch = (float) MathHelper.lerp(k, -0.3, -0.6);
				model.rightArm.roll = (float) (0.6 * k);
				model.leftArm.pitch = (float) MathHelper.lerp(k, -0.3, -0.6);
				model.leftArm.roll = (float) (-0.6 * k);
			}
			case KNEEL -> {
				double k = ease(span(u, 0.0, 12.0));
				model.rightLeg.pitch = (float) (1.45 * k);
				model.leftLeg.pitch = (float) (1.45 * k);
				model.rightLeg.yaw = 0.0F;
				model.leftLeg.yaw = 0.0F;
				model.rightArm.pitch = -0.25F;
				model.rightArm.roll = 0.1F;
				model.leftArm.pitch = -0.25F;
				model.leftArm.roll = -0.1F;
				model.head.pitch = (float) (0.5 * k);
			}
			case LIE -> {
				double k = ease(span(u, 0.0, 6.0));
				model.rightLeg.pitch = (float) (1.45 * (1.0 - k));
				model.leftLeg.pitch = (float) (1.45 * (1.0 - k));
				model.rightLeg.yaw = 0.0F;
				model.leftLeg.yaw = 0.0F;
				model.rightArm.pitch = 0.0F;
				model.rightArm.roll = (float) (0.1 + 0.2 * k);
				model.leftArm.pitch = 0.0F;
				model.leftArm.roll = (float) (-0.1 - 0.2 * k);
				// Down on his face, turned to the side.
				model.head.pitch = (float) (0.5 * (1.0 - k));
				model.head.yaw = (float) (0.9 * k);
			}
			case ZOMBIE -> {
				model.rightArm.pitch = -1.45F + MathHelper.sin(t * 0.1F + id) * 0.08F;
				model.rightArm.yaw = -0.1F;
				model.leftArm.pitch = -1.45F + MathHelper.cos(t * 0.09F + id) * 0.08F;
				model.leftArm.yaw = 0.1F;
				model.head.pitch += 0.25F;
				model.head.roll = 0.18F * MathHelper.sin(id);
			}
			case TWITCH -> {
				int n = (int) (t / 2.0F) * 31 + id;
				model.head.yaw += noise(n) * 0.6F;
				model.head.pitch += noise(n + 1) * 0.5F;
				model.head.roll = noise(n + 2) * 0.5F;
				model.rightArm.pitch += noise(n + 3) * 0.6F;
				model.leftArm.pitch += noise(n + 4) * 0.6F;
				model.rightArm.roll = Math.abs(noise(n + 5)) * 0.5F;
				model.leftArm.roll = -Math.abs(noise(n + 6)) * 0.5F;
			}
			case GRAB -> {
				model.rightArm.pitch = -0.9F + noise((int) t * 7 + id) * 0.12F;
				model.rightArm.yaw = -0.3F;
				model.leftArm.pitch = -0.9F + noise((int) t * 7 + id + 1) * 0.12F;
				model.leftArm.yaw = 0.3F;
				model.head.pitch += 0.45F;
			}
			case CHOP -> {
				// Up, and slammed down on him, over and over.
				double c = (u % 9.0 + 9.0) % 9.0 / 9.0;
				double raise = c < 0.6 ? ease(c / 0.6) : 1.0 - ease((c - 0.6) / 0.15);
				float arms = (float) MathHelper.lerp(MathHelper.clamp(raise, 0.0, 1.0), -0.4, -2.9);
				model.rightArm.pitch = arms;
				model.leftArm.pitch = arms;
				model.rightArm.yaw = -0.15F;
				model.leftArm.yaw = 0.15F;
				model.head.pitch += 0.45F;
			}
			case DRAG -> {
				model.rightArm.pitch = -0.55F;
				model.rightArm.yaw = -0.15F;
				model.leftArm.pitch = -0.55F;
				model.leftArm.yaw = 0.15F;
				model.head.pitch += 0.35F;
			}
			case HEAVE -> {
				float arms = (float) MathHelper.lerp(ease(span(u, 0.0, 6.0)), -0.5, -2.6);
				model.rightArm.pitch = arms;
				model.leftArm.pitch = arms;
			}
			default -> {
			}
		}
		model.hat.copyTransform(model.head);
		model.jacket.copyTransform(model.body);
		model.rightSleeve.copyTransform(model.rightArm);
		model.leftSleeve.copyTransform(model.leftArm);
		model.rightPants.copyTransform(model.rightLeg);
		model.leftPants.copyTransform(model.leftLeg);
	}

	/**
	 * A stand-in's whole body as it acts: sat on the floor, jolted back, on its knees, down on its face, leant over its
	 * work, jerking about. (Here, before the model's own flip, the body faces -z: leaning forward is turning round -x.)
	 */
	public static void body(AbstractClientPlayerEntity player, MatrixStack matrices) {
		Acting acting = ACTING.get(player.getId());
		if (acting == null) {
			return;
		}
		double u = now() - acting.since();
		float t = (float) now();
		int n = (int) (t / 2.0F) * 13 + player.getId();
		switch (acting.act()) {
			case SIT_EAT -> matrices.translate(0.0F, -0.62F, 0.0F);
			case HURT -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(10.0F * (float) (1.0 - ease(span(u, 6.0, 11.0)))));
			case KNEEL -> {
				double k = ease(span(u, 0.0, 12.0));
				matrices.translate(0.0, -0.6 * k, 0.0);
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12.0F * (float) k));
			}
			case LIE -> {
				if (player.isInSwimmingPose()) {
					return;
				}
				double k = ease(span(u, 0.0, 6.0));
				matrices.translate(0.0, MathHelper.lerp(k, -0.6, 0.15), 0.0);
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) MathHelper.lerp(k, -12.0, -90.0)));
			}
			case DRAG, CHOP, GRAB -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-14.0F));
			case TWITCH -> {
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(noise(n) * 5.0F));
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(noise(n + 1) * 4.0F));
			}
			default -> {
			}
		}
	}

	/** -1 to 1, the same for the same {@code n}. */
	private static float noise(int n) {
		int h = n * 0x27D4EB2D;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h & 0xFFFF) / 32767.5F - 1.0F;
	}

	// Little things.

	private static Vec3d o(double x, double y, double z) {
		return origin.add(x, y, z);
	}

	private static double span(double t, double from, double to) {
		return MathHelper.clamp((t - from) / (to - from), 0.0, 1.0);
	}

	private static double ease(double k) {
		k = MathHelper.clamp(k, 0.0, 1.0);
		return k * k * (3.0 - 2.0 * k);
	}

	/** At a walk from {@code a} at {@code from} to {@code b} at {@code to}. */
	private static Vec3d walk(Vec3d a, Vec3d b, double s, double from, double to) {
		return a.lerp(b, span(s, from, to));
	}

	/** The way from {@code from} to {@code to} faces (degrees, as the game turns). */
	private static float yaw(Vec3d from, Vec3d to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	private static float turn(float from, float to, double k) {
		return from + MathHelper.wrapDegrees(to - from) * (float) k;
	}

	private static Capture.Pose look(Vec3d at, Vec3d look) {
		Vec3d to = look.subtract(at);
		float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		return new Capture.Pose(at.x, at.y, at.z, yaw, pitch);
	}

	private static void sound(Vec3d at, SoundEvent sound, float volume, float pitch) {
		MinecraftClient.getInstance().world.playSound(at.x, at.y, at.z, sound, SoundCategory.NEUTRAL, volume, pitch, false);
	}

	private static ServerPlayerEntity player(IntegratedServer server) {
		return server.getPlayerManager().getPlayerList().get(0);
	}

	// Looking it over: the cast, the set.

	/** The cast in a row, the Zombie Devil behind them: photographed from the front, from the side, and close on it. */
	private static void cast(MinecraftClient client, int f) {
		String[] names = {"Denji", "Boss", "Yakuza1", "Yakuza2", "Yakuza3", "Yakuza4", "ZBoss", "ZYakuza1", "ZYakuza2", "ZYakuza3", "ZYakuza4"};
		if (f == 1) {
			client.options.hudHidden = true;
			for (int i = 0; i < names.length; i++) {
				actor(client, names[i], names[i], origin.add(-7.5 + i * 1.5, 0.0, 0.0), 180.0F);
			}
			devil = origin.add(0.0, 3.5, 4.5);
			devilYaw = 180.0F;
		}
		if (f == 5) {
			camera(origin.add(0.0, 2.2, -11.0), origin.add(0.0, 1.6, 2.0));
		}
		if (f == 30) {
			shot(client, "cast_front");
			camera(origin.add(-9.0, 2.0, -5.0), origin.add(-3.0, 1.2, 1.0));
		}
		if (f == 50) {
			shot(client, "cast_side");
			camera(origin.add(1.8, 2.6, -1.2), devil.add(0.0, 0.4, 0.0));
		}
		if (f == 70) {
			shot(client, "cast_devil_close");
			camera(origin.add(-3.5, 1.4, 1.2), devil.add(0.0, -0.4, 0.0));
		}
		if (f == 90) {
			shot(client, "cast_devil_low");
			stage = Stage.DONE;
		}
	}

	/** The set, at night in the rain, from where its cuts will look at it. */
	private static void set(MinecraftClient client, int f) {
		Vec3d o = origin;
		if (f == 1) {
			client.options.hudHidden = true;
			// In the shack from its corner; at its door from outside; the warehouse from inside, at its doors and deep in;
			// the alley.
			camera(o.add(ReelSet.SHACK_EAST - 1.2, 2.6, ReelSet.SHACK_SOUTH - 1.2), o.add(ReelSet.SHACK_WEST + 2.0, 0.5, ReelSet.SHACK_NORTH + 1.5));
		}
		if (f == 30) {
			shot(client, "set_shack");
			camera(o.add(ReelSet.SHACK_EAST + 3.5, 1.4, ReelSet.SHACK_DOOR + 2.5), o.add(ReelSet.SHACK_EAST, 1.2, ReelSet.SHACK_DOOR));
		}
		if (f == 60) {
			shot(client, "set_shack_door");
			camera(o.add(ReelSet.EAST - 4.0, 3.0, 1.0), o.add(ReelSet.WEST, 2.0, 0.0));
		}
		if (f == 90) {
			shot(client, "set_warehouse");
			camera(o.add(ReelSet.WEST + 3.0, 1.5, -3.0), o.add(ReelSet.EAST - 4.0, 2.0, 1.0));
		}
		if (f == 120) {
			shot(client, "set_warehouse_deep");
			camera(o.add(ReelSet.EAST + 2.0, 2.2, -6.0), o.add(ReelSet.EAST + 5.0, 0.8, 4.0));
		}
		if (f == 150) {
			shot(client, "set_alley");
			stage = Stage.DONE;
		}
	}

	/**
	 * Where the wild Pochita waits (PochitaPlace's fallen tree), built on the world's own ground in the morning:
	 * photographed wide, along his blood's trail, close under the trunk, at its roots, at its broken end and its crown,
	 * and from the other side.
	 */
	private static void den(MinecraftClient client, int f) {
		// (The wide shots from inside the clearing's edge: in woods, any further out is in among the trees.)
		double[][][] shots = {{{-8.0, 6.0, -7.0}, {1.0, 0.5, 0.5}}, {{6.0, 2.0, -7.0}, {0.0, 0.3, 0.0}}, {{0.6, 0.5, -2.6}, {0.0, 0.3, 0.3}},
				{{-11.0, 2.0, -4.0}, {-6.0, 1.5, 0.5}}, {{9.0, 3.0, -6.0}, {5.0, 1.0, 0.5}}, {{-2.0, 1.4, 6.0}, {0.0, 0.6, 0.0}}};
		String[] names = {"den_wide", "den_trail", "den_under", "den_roots", "den_break", "den_other_side"};
		if (f == 1) {
			client.options.hudHidden = true;
		}
		int shot = (f - 1) / 30;
		if (shot < shots.length && (f - 1) % 30 == 0) {
			camera(origin.add(alongTree(shots[shot][0])), origin.add(alongTree(shots[shot][1])));
		}
		if (shot < shots.length && (f - 1) % 30 == 25) {
			shot(client, names[shot]);
		}
		if (shot >= shots.length) {
			stage = Stage.DONE;
		}
	}

	/** The way the tree photographed lies. */
	private static net.minecraft.util.math.Direction.Axis denAxis = net.minecraft.util.math.Direction.Axis.X;

	/** {@code d}, given along the tree (x), up (y) and across it (z), turned to the way it lies. */
	private static Vec3d alongTree(double[] d) {
		return denAxis == net.minecraft.util.math.Direction.Axis.X ? new Vec3d(d[0], d[1], d[2]) : new Vec3d(-d[2], d[1], d[0]);
	}

	/** The camera fixed at {@code at}, looking at {@code look} (through the capture's camera, filming or not). */
	private static void camera(Vec3d at, Vec3d look) {
		Capture.Pose pose = look(at, look);
		Capture.camera = t -> pose;
	}

	private static void shot(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot reel_{}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "reel_" + name + ".png", client.getFramebuffer(), message -> {
		});
	}

	/**
	 * On the server: the set (for the cast alone, a stone floor in daylight), high in the open air; at night in the rain;
	 * the player a spectator (unseen) until they're swapped in for Denji, not yet merged; the camera moved round by the
	 * capture's own.
	 */
	private static void setup(IntegratedServer server) {
		ServerPlayerEntity player = player(server);
		ServerWorld world = server.getOverworld();
		boolean cast = "cast".equals(target());
		if ("den".equals(target())) {
			// Pochita's fallen tree, on the world's own ground a way off, in the morning.
			world.setTimeOfDay(2500);
			world.setWeather(12000, 0, false, false);
			server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
			// The world's own, if it has one (a copy of a world played in); else one built in woods or grassland near spawn,
			// where the world's own would be put (PochitaPlace.choose).
			BlockPos own = io.github.kortev.chainsawman.PochitaPlace.tree(server);
			BlockPos spot = own != null ? null
					: io.github.kortev.chainsawman.PochitaPlace.woods(world, player.getBlockPos().add(40, 0, 12), net.minecraft.util.math.random.Random.create(7));
			if (spot == null) {
				spot = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, player.getBlockPos().add(40, 0, 12));
			}
			if (own != null) {
				spot = own;
				denAxis = io.github.kortev.chainsawman.PochitaPlace.treeAxis(server);
			} else {
				io.github.kortev.chainsawman.PochitaPlace.waiting(world, spot, net.minecraft.util.math.Direction.Axis.X);
			}
			player.changeGameMode(GameMode.SPECTATOR);
			player.teleport(world, spot.getX() + 0.5, spot.getY() + 4.0, spot.getZ() - 8.0, 0.0F, 0.0F);
			origin = Vec3d.ofBottomCenter(spot);
			ShootingStar.LOGGER.info("[selftest] reel1: den at {}", spot);
			return;
		}
		world.setTimeOfDay(cast ? 6000 : 18000);
		if (cast) {
			world.setWeather(12000, 0, false, false);
		} else {
			world.setWeather(0, 30000, true, false);
		}
		server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		// High in the open air, over the clouds (the same height every run), so the light is the sky's.
		BlockPos ground = player.getBlockPos().withY(220);
		if (cast) {
			for (BlockPos p : BlockPos.iterate(ground.add(-16, -1, -16), ground.add(16, 8, 16))) {
				world.setBlockState(p, p.getY() == ground.getY() - 1 ? Blocks.SMOOTH_STONE.getDefaultState() : Blocks.AIR.getDefaultState());
			}
		} else {
			ReelSet.build(world, ground);
		}
		for (Entity e : world.getOtherEntities(player, new net.minecraft.util.math.Box(ground).expand(64.0, 24.0, 64.0))) {
			if (!(e instanceof ServerPlayerEntity)) {
				e.discard();
			}
		}
		Form.end(player);
		player.removeAttached(Heart.MERGED);
		player.getInventory().clear();
		player.changeGameMode(GameMode.SPECTATOR);
		Vec3d feet = Vec3d.ofBottomCenter(ground);
		player.teleport(world, feet.x + (cast ? 0.0 : 4.0), feet.y + 2.0, feet.z - (cast ? 10.0 : 0.0), 0.0F, 0.0F);
		origin = feet;
		ShootingStar.LOGGER.info("[selftest] reel1: set at {}", feet);
	}

	static List<LivingEntity> actors() {
		return List.copyOf(ACTORS.values());
	}
}
