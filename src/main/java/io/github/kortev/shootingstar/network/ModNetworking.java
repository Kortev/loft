package io.github.kortev.shootingstar.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(StrikeLockPayload.ID, StrikeLockPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StrikeImpactPayload.ID, StrikeImpactPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StrikeCancelPayload.ID, StrikeCancelPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GapLockPayload.ID, GapLockPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GapSwapPayload.ID, GapSwapPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GapEndPayload.ID, GapEndPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GapFloorPayload.ID, GapFloorPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GapWarpPayload.ID, GapWarpPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GapSettlePayload.ID, GapSettlePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ThunderLockPayload.ID, ThunderLockPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ThunderStrokePayload.ID, ThunderStrokePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ThunderArcsPayload.ID, ThunderArcsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ThunderCancelPayload.ID, ThunderCancelPayload.CODEC);
	}

	public static void send(ServerPlayerEntity player, CustomPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.getId())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** Sends to every player in the world; clients decide what is in range to draw or hear. */
	public static void broadcast(ServerWorld world, CustomPayload payload) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			send(player, payload);
		}
	}
}
