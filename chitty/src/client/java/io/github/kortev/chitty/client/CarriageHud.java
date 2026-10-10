package io.github.kortev.chitty.client;

import io.github.kortev.chitty.carriage.CarriageEntity;
import io.github.kortev.chitty.carriage.CarriagePartEntity;
import io.github.kortev.chitty.client.AirshipHud.Row;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The carriage's controls on screen, drawn as the airship's are (AirshipHud), so that nobody has to remember them. Up
 * on the box, the reins, the whip and the disguise (lit while held), and how her cage stands: who is in it, whether its
 * door is shut, what bait is out. In the cage, whether you can get out. Looking at her from outside, what using her
 * there will do: get up on the box, open or shut the door, climb in, lead or shove something in; dressed as a trader's
 * wagon, set bait out or take it back, and, to anyone else, the bait on offer (it is a trap: the panel does not say so).
 * (F1 hides it with the rest of the HUD.)
 */
final class CarriageHud {
	private CarriageHud() {
	}

	static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.getDebugHud().shouldShowDebugHud()) {
			return;
		}
		GameOptions o = client.options;
		List<Row> rows = new ArrayList<>();
		Text title;
		if (player.getVehicle() instanceof CarriageEntity carriage) {
			if (carriage.inCage(player)) {
				boolean open = carriage.isDoorOpen();
				title = ui(open ? "in_cage" : "caught");
				if (open) {
					rows.add(AirshipHud.row(o.sneakKey, ui("get_out")));
				} else {
					rows.add(new Row(null, false, ui("locked_hint"), AirshipHud.WARN));
				}
				if (carriage.isDisguised()) {
					rows.add(new Row(null, false, ui("hidden"), AirshipHud.DIM));
				}
			} else {
				title = ui("title");
				if (carriage.getControllingPassenger() == player) {
					rows.add(AirshipHud.row(AirshipHud.keys(o.forwardKey, o.backKey), o.forwardKey.isPressed() || o.backKey.isPressed(),
							ui("reins")));
					rows.add(AirshipHud.row(AirshipHud.keys(o.leftKey, o.rightKey), o.leftKey.isPressed() || o.rightKey.isPressed(),
							ui("steer")));
					rows.add(new Row(o.jumpKey.getBoundKeyLocalizedText(), o.jumpKey.isPressed() || carriage.isGalloping(), ui("whip"),
							AirshipHud.TEXT));
					boolean standing = carriage.getSpeed() < 0.03;
					rows.add(new Row(CarriageClient.DISGUISE.getBoundKeyLocalizedText(), CarriageClient.DISGUISE.isPressed(),
							ui(carriage.isDisguised() ? "disguise_down" : "disguise_up"), standing ? AirshipHud.TEXT : AirshipHud.DIM));
				}
				rows.add(AirshipHud.row(o.sneakKey, ui("get_down")));
				rows.add(null);
				cageRows(carriage, rows);
			}
		} else if (player.getVehicle() == null && targeted(client) instanceof CarriageEntity carriage) {
			title = ui(carriage.isDisguised() ? "trader_title" : "title");
			lookingRows(client, player, carriage, rows);
			if (rows.isEmpty()) {
				return;
			}
		} else {
			return;
		}
		AirshipHud.draw(ctx, client.textRenderer, title, rows, null, counter.getTickDelta(false));
	}

	/** How her cage stands: who is in it, and whether its door is shut; dressed up, what bait is out. */
	private static void cageRows(CarriageEntity carriage, List<Row> rows) {
		int in = carriage.prisoners();
		int places = CarriageEntity.PLACES.length - CarriageEntity.CAGE;
		boolean open = carriage.isDoorOpen();
		MutableText cage = Text.translatable("hud.shootingstar.carriage.ui.cage", in, places).append(" · ")
				.append(ui(open ? "door_open" : "door_shut"));
		rows.add(new Row(null, false, cage, open && in > 0 ? AirshipHud.WARN : in > 0 ? AirshipHud.GOOD : AirshipHud.TEXT));
		if (carriage.isDisguised()) {
			Text bait = baitList(carriage);
			rows.add(new Row(null, false, bait == null ? ui("no_bait") : Text.translatable("hud.shootingstar.carriage.ui.bait", bait),
					AirshipHud.DIM));
		}
	}

	/** What using her where the player is looking will do. */
	private static void lookingRows(MinecraftClient client, ClientPlayerEntity player, CarriageEntity carriage, List<Row> rows) {
		GameOptions o = client.options;
		Vec3d hit = client.crosshairTarget instanceof EntityHitResult result ? result.getPos() : carriage.getPos();
		Vec3d local = hit.subtract(carriage.getPos()).rotateY(carriage.getYaw() * MathHelper.RADIANS_PER_DEGREE);
		if (!CarriageEntity.atDoor(local)) {
			if (carriage.getPassengerList().stream().filter(p -> !carriage.inCage(p)).count() < CarriageEntity.CAGE) {
				rows.add(AirshipHud.row(o.useKey, ui("get_up")));
			}
			return;
		}
		boolean open = carriage.isDoorOpen();
		boolean leading = !player.getWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(10.0),
				mob -> mob.getLeashHolder() == player).isEmpty();
		if (leading) {
			rows.add(new Row(o.useKey.getBoundKeyLocalizedText(), false, ui(open ? "lead_in" : "open_first"),
					open ? AirshipHud.TEXT : AirshipHud.DIM));
			return;
		}
		Text door = ui(open ? "shut_door" : "open_door");
		Text sneakUse = AirshipHud.combo(o.sneakKey, o.useKey);
		if (carriage.isDisguised()) {
			ItemStack held = player.getMainHandStack();
			if (carriage.hasBaitShown()) {
				if (carriage.isBaiter(player)) {
					rows.add(AirshipHud.row(o.useKey, ui("take_back")));
				} else {
					// To anyone else it is a trader's counter, and that is all.
					rows.add(new Row(o.useKey.getBoundKeyLocalizedText(), false,
							Text.translatable("hud.shootingstar.carriage.ui.take", firstBait(carriage).getName()), AirshipHud.TEXT));
					return;
				}
			} else {
				rows.add(AirshipHud.row(o.useKey, door));
			}
			if (!held.isEmpty()) {
				rows.add(new Row(sneakUse, false, Text.translatable("hud.shootingstar.carriage.ui.set_bait", held.getName()), AirshipHud.TEXT));
			}
			rows.add(new Row(sneakUse, false, door, AirshipHud.TEXT));
			return;
		}
		rows.add(AirshipHud.row(o.useKey, door));
		if (open) {
			rows.add(new Row(sneakUse, false, ui("climb_in"), AirshipHud.TEXT));
			rows.add(AirshipHud.row(o.attackKey, ui("shove")));
		}
	}

	/** The carriage the player is looking at (or one of her hitboxes), if any. */
	@Nullable
	private static CarriageEntity targeted(MinecraftClient client) {
		Entity target = client.targetedEntity;
		if (target instanceof CarriagePartEntity part) {
			return part.getCarriage();
		}
		return target instanceof CarriageEntity carriage ? carriage : null;
	}

	@Nullable
	private static Text baitList(CarriageEntity carriage) {
		MutableText list = null;
		for (int slot = 0; slot < 3; slot++) {
			ItemStack stack = carriage.getBait(slot);
			if (!stack.isEmpty()) {
				list = list == null ? Text.empty().append(stack.getName()) : list.append(", ").append(stack.getName());
			}
		}
		return list;
	}

	private static ItemStack firstBait(CarriageEntity carriage) {
		for (int slot = 0; slot < 3; slot++) {
			if (!carriage.getBait(slot).isEmpty()) {
				return carriage.getBait(slot);
			}
		}
		return ItemStack.EMPTY;
	}

	private static Text ui(String name) {
		return Text.translatable("hud.shootingstar.carriage.ui." + name);
	}
}
