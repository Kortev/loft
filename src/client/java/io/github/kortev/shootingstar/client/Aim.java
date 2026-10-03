package io.github.kortev.shootingstar.client;

import io.github.kortev.shootingstar.item.UplinkItem;
import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.strike.Targeting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Where the uplink in the player's hand is pointing, refreshed every client tick. */
public final class Aim {
	public static boolean holding;
	@Nullable
	public static BlockPos target;
	public static boolean dangerClose;
	public static double distance;
	public static float cooldown;

	private Aim() {
	}

	public static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		holding = player != null && (player.getMainHandStack().isOf(ModItems.GUNGNIR_UPLINK)
				|| player.getOffHandStack().isOf(ModItems.GUNGNIR_UPLINK));
		if (!holding) {
			target = null;
			return;
		}
		cooldown = player.getItemCooldownManager().getCooldownProgress(ModItems.GUNGNIR_UPLINK, 0.0F);
		target = UplinkItem.aim(player);
		if (target != null) {
			distance = Math.sqrt(Vec3d.ofCenter(target).squaredDistanceTo(player.getEyePos()));
			dangerClose = UplinkItem.dangerClose(player, target, Targeting.minRange(Targeting.DEFAULT_RADIUS));
		}
	}
}
