package io.github.kortev.chitty.client;

import io.github.kortev.chitty.ChittyControls;
import io.github.kortev.chitty.airship.Airship;
import io.github.kortev.chitty.airship.AirshipActionPayload;
import io.github.kortev.chitty.airship.AirshipEntity;
import io.github.kortev.chitty.airship.AirshipInputPayload;
import io.github.kortev.chitty.airship.AirshipWalkPayload;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The airship on the client: how she is drawn and heard, the pilot's controls, walking about her gondola, and the
 * crew's keys for the grapple, the ladder, the bombs and (the pilot's) overboard. Set up from Chitty's client
 * initializer.
 */
public final class AirshipClient {
	public static KeyBinding GRAPPLE;
	public static KeyBinding LADDER;
	public static KeyBinding BOMB;
	public static KeyBinding OVERBOARD;

	private static final Set<AirshipEntity> SOUNDING = Collections.newSetFromMap(new WeakHashMap<>());
	private static byte lastControls = -1;
	private static int sinceSent;
	/** What the rider was last shown on the action bar: 0 nothing, 1 crew, 2 pilot, 3 overloaded. */
	private static int hint;
	/**
	 * Where this player stands in the airship they are aboard, as they walk about her gondola: theirs to move (so it
	 * answers at once), sent to the server as it changes. Taken from the server when they come aboard and whenever they
	 * take the wheel or let it go.
	 */
	@Nullable
	private static AirshipEntity walkingOn;
	@Nullable
	private static Vec3d myStand;
	private static boolean wasAtHelm;
	/**
	 * The view this player had before they took the wheel, which they get back when they let it go: at the wheel they
	 * watch her from behind (her whole length, ChittyCameraMixin). Null when they are not at a wheel.
	 */
	@Nullable
	private static Perspective viewBeforeWheel;

	private AirshipClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(Airship.ENTITY, AirshipRenderer::new);
		EntityRendererRegistry.register(Airship.PART, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(Airship.HOOK, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(Airship.BOMB_ENTITY, AirshipBombRenderer::new);
		GRAPPLE = key("airship_grapple", GLFW.GLFW_KEY_R);
		LADDER = key("airship_ladder", GLFW.GLFW_KEY_K);
		BOMB = key("airship_bomb", GLFW.GLFW_KEY_B);
		OVERBOARD = key("airship_overboard", GLFW.GLFW_KEY_O);
		AirshipEntity.client = new AirshipEntity.ClientHooks() {
			@Override
			public ChittyControls controls(AirshipEntity ship) {
				return AirshipClient.controls(ship);
			}

			@Override
			public void sync(AirshipEntity ship, ChittyControls controls) {
				AirshipClient.sync(controls);
			}

			@Override
			public void tick(AirshipEntity ship) {
				if (SOUNDING.add(ship)) {
					MinecraftClient client = MinecraftClient.getInstance();
					for (AirshipSound.Layer layer : AirshipSound.Layer.values()) {
						client.getSoundManager().play(new AirshipSound(ship, layer));
					}
				}
				walk(ship);
			}

			@Override
			public Vec3d stand(AirshipEntity ship, Entity passenger) {
				return ship == walkingOn && passenger == MinecraftClient.getInstance().player ? myStand : null;
			}
		};
		ClientTickEvents.END_CLIENT_TICK.register(AirshipClient::tick);
	}

	private static KeyBinding key(String name, int code) {
		return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shootingstar." + name, InputUtil.Type.KEYSYM, code,
				"key.categories.shootingstar"));
	}

	private static ChittyControls controls(AirshipEntity ship) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || ship.getControllingPassenger() != player) {
			return ChittyControls.NONE;
		}
		Input in = player.input;
		int forward = (in.pressingForward ? 1 : 0) - (in.pressingBack ? 1 : 0);
		int turn = (in.pressingLeft ? 1 : 0) - (in.pressingRight ? 1 : 0);
		return new ChittyControls(forward, turn, in.jumping, MinecraftClient.getInstance().options.sprintKey.isPressed());
	}

	/**
	 * Walks this player about the gondola of the airship they are aboard, unless they have the wheel: the movement keys
	 * take them the way they face, at a walk, kept to its floor and out of everyone else's way.
	 */
	private static void walk(AirshipEntity ship) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || player.getVehicle() != ship) {
			if (walkingOn == ship) {
				walkingOn = null;
				myStand = null;
			}
			return;
		}
		boolean atHelm = ship.getControllingPassenger() == player;
		if (walkingOn != ship || myStand == null || atHelm != wasAtHelm) {
			Vec3d synced = ship.syncedStandOf(player);
			if (synced == null) {
				// The server has not said where they stand yet.
				walkingOn = null;
				return;
			}
			walkingOn = ship;
			myStand = synced;
			wasAtHelm = atHelm;
		}
		Input in = player.input;
		if (atHelm || in.movementForward == 0.0F && in.movementSideways == 0.0F) {
			return;
		}
		// The way they face and press (as Entity.movementInputToVelocity has it), turned into her frame.
		float yawRad = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		float sin = MathHelper.sin(yawRad);
		float cos = MathHelper.cos(yawRad);
		Vec3d way = new Vec3d(in.movementSideways * cos - in.movementForward * sin, 0.0,
				in.movementForward * cos + in.movementSideways * sin);
		if (way.lengthSquared() > 1.0) {
			way = way.normalize();
		}
		Vec3d step = way.rotateY(ship.getYaw() * MathHelper.RADIANS_PER_DEGREE).multiply(AirshipEntity.WALK);
		// Straight there if there is room; otherwise sliding along whoever is in the way, or not at all.
		Vec3d to = null;
		for (Vec3d tryStep : new Vec3d[] {step, new Vec3d(step.x, 0.0, 0.0), new Vec3d(0.0, 0.0, step.z)}) {
			Vec3d at = AirshipEntity.onFloor(myStand.x + tryStep.x, myStand.z + tryStep.z);
			if (roomAt(ship, player, at)) {
				to = at;
				break;
			}
		}
		if (to == null || to.squaredDistanceTo(myStand) < 1.0E-8) {
			return;
		}
		myStand = to;
		if (ClientPlayNetworking.canSend(AirshipWalkPayload.ID)) {
			ClientPlayNetworking.send(new AirshipWalkPayload((float) to.x, (float) to.z));
		}
	}

	/** Whether a step to a point keeps elbow room from everyone else aboard (or at least does not close on them). */
	private static boolean roomAt(AirshipEntity ship, Entity player, Vec3d to) {
		for (Entity other : ship.getPassengerList()) {
			Vec3d at = other == player ? null : ship.standOf(other);
			if (at == null) {
				continue;
			}
			double after = Math.hypot(to.x - at.x, to.z - at.z);
			if (after < AirshipEntity.ELBOW_ROOM && after < Math.hypot(myStand.x - at.x, myStand.z - at.z)) {
				return false;
			}
		}
		return true;
	}

	/** Tells the server what the pilot is doing whenever it changes, and once a second regardless. */
	private static void sync(ChittyControls controls) {
		byte packed = controls.pack();
		if ((packed != lastControls || ++sinceSent >= 20) && ClientPlayNetworking.canSend(AirshipInputPayload.ID)) {
			ClientPlayNetworking.send(new AirshipInputPayload(packed));
			lastControls = packed;
			sinceSent = 0;
		}
	}

	private static void act(int action) {
		if (ClientPlayNetworking.canSend(AirshipActionPayload.ID)) {
			ClientPlayNetworking.send(new AirshipActionPayload(action));
		}
	}

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		AirshipEntity ship = player != null && player.getVehicle() instanceof AirshipEntity s ? s : null;
		boolean piloting = ship != null && ship.getControllingPassenger() == player;
		while (GRAPPLE.wasPressed()) {
			if (ship != null) {
				act(AirshipEntity.ACTION_GRAPPLE);
			} else if (player != null && AirshipEntity.grappleHeldBy(player) != null) {
				act(AirshipEntity.ACTION_THROW);
			}
		}
		while (LADDER.wasPressed()) {
			if (ship != null) {
				act(AirshipEntity.ACTION_LADDER);
			}
		}
		while (BOMB.wasPressed()) {
			if (ship != null) {
				act(AirshipEntity.ACTION_BOMB);
			}
		}
		while (OVERBOARD.wasPressed()) {
			if (piloting) {
				act(AirshipEntity.ACTION_OVERBOARD);
			}
		}
		// At the wheel the view goes behind her; letting go of it gives back the view there was.
		if (piloting && viewBeforeWheel == null) {
			viewBeforeWheel = client.options.getPerspective();
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		} else if (!piloting && viewBeforeWheel != null) {
			client.options.setPerspective(viewBeforeWheel);
			viewBeforeWheel = null;
		}
		// A word on the controls when someone comes aboard or takes the wheel, and a warning when she is overloaded.
		int now = ship == null ? 0 : ship.isOverloaded() ? 3 : piloting ? 2 : 1;
		if (now != hint) {
			if (now != 0 && player != null) {
				String key = now == 3 ? "hud.shootingstar.airship.heavy" : now == 2 ? "hud.shootingstar.airship.pilot" : "hud.shootingstar.airship.crew";
				player.sendMessage(Text.translatable(key, GRAPPLE.getBoundKeyLocalizedText(), LADDER.getBoundKeyLocalizedText(),
						BOMB.getBoundKeyLocalizedText(), OVERBOARD.getBoundKeyLocalizedText(), AirshipEntity.LIFT), true);
			}
			hint = now;
		}
		if (ship == null) {
			lastControls = -1;
		}
	}
}
