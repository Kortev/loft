package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.network.GapEndPayload;
import io.github.kortev.shootingstar.network.GapLockPayload;
import io.github.kortev.shootingstar.network.GapSwapPayload;
import io.github.kortev.shootingstar.registry.ModSounds;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Client side of Ginnungagap events: their state and their sound cues. */
public final class ClientGaps {
	private static final Map<Integer, ClientGap> GAPS = new LinkedHashMap<>();
	private static boolean hudOverride;
	private static boolean savedHudHidden;

	private ClientGaps() {
	}

	public static Collection<ClientGap> all() {
		return GAPS.values();
	}

	/** The local player's event, while it still owns their screen. */
	@Nullable
	public static ClientGap mine() {
		for (ClientGap gap : GAPS.values()) {
			if (gap.cinematic()) {
				return gap;
			}
		}
		return null;
	}

	public static void onLock(GapLockPayload payload, MinecraftClient client) {
		if (client.world == null || client.player == null) {
			return;
		}
		boolean mine = payload.shooter().equals(client.player.getUuid());
		PlayerEntity shooter = client.world.getPlayerByUuid(payload.shooter());
		Vec3d from = shooter != null ? shooter.getPos() : client.player.getPos();
		ClientGap gap = new ClientGap(payload.gapId(), payload.target(), payload.shooter(), mine, payload.age(), payload.radius(),
				payload.terrain(), payload.swapSpot(), payload.tree(), from);
		GAPS.put(gap.id, gap);
		if (mine && payload.age() == 0) {
			ClientStrikes.master(ModSounds.GAP_KEY, 1.0F, 1.0F);
		}
	}

	public static void onSwap(GapSwapPayload payload, MinecraftClient client) {
		ClientGap gap = GAPS.get(payload.gapId());
		ClientWorld world = client.world;
		if (gap == null || world == null) {
			return;
		}
		List<BlockPos> positions = payload.positions();
		for (BlockPos pos : positions) {
			int color = world.getBlockState(pos).getMapColor(world, pos).color;
			gap.swaps.add(new ClientGap.Swap(pos.toImmutable(), color, gap.age, payload.kind()));
		}
	}

	public static void onEnd(GapEndPayload payload) {
		ClientGap gap = GAPS.get(payload.gapId());
		if (gap != null) {
			gap.ended = true;
		}
	}

	public static void clear(MinecraftClient client) {
		GAPS.clear();
	}

	public static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null) {
			return;
		}
		hud(client);
		for (Iterator<ClientGap> it = GAPS.values().iterator(); it.hasNext(); ) {
			ClientGap gap = it.next();
			int from = gap.age;
			gap.age++;
			if (gap.cinematic()) {
				cues(gap, from, gap.age);
			}
			boolean over = gap.ended || (!gap.mine && gap.age > GapTimeline.END + 40);
			if (over) {
				it.remove();
			}
		}
	}

	/** The vanilla HUD goes while the camera is away from the shooter's eyes, and comes back with them. */
	public static void hud(MinecraftClient client) {
		boolean away = mine() != null && GapCamera.current(1.0F) != null;
		if (away && !hudOverride) {
			savedHudHidden = client.options.hudHidden;
			client.options.hudHidden = true;
			hudOverride = true;
		} else if (!away && hudOverride) {
			client.options.hudHidden = savedHudHidden;
			hudOverride = false;
		}
	}

	private static boolean crossed(int from, int to, int mark) {
		return from < mark && to >= mark;
	}

	private static void cues(ClientGap gap, int from, int to) {
		if (crossed(from, to, GapTimeline.AIM + 24)) {
			ClientStrikes.master(ModSounds.GAP_LOCK, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.TEAR)) {
			ClientStrikes.master(ModSounds.GAP_TEAR, 1.0F, 1.0F);
		}
		// Six seconds long, ending dead on contact.
		if (crossed(from, to, GapTimeline.CONTACT - 120)) {
			ClientStrikes.master(ModSounds.GAP_DRONE, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.CONTACT)) {
			ClientStrikes.master(ModSounds.GAP_CONTACT, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.FRAMES)) {
			ClientStrikes.master(ModSounds.GAP_IMPACT, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.ERASURE)) {
			ClientStrikes.master(ModSounds.GAP_ERASE, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.NOTHING)) {
			ClientStrikes.master(ModSounds.GAP_VOID, 1.0F, 1.0F);
		}
	}
}
