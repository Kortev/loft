package io.github.kortev.shootingstar.mixin;

import io.github.kortev.shootingstar.gap.Erasure;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * While an erasure is taking blocks out, the light check each one queues is handed to it instead: it checks each
 * column once, at the bottom, when the hole is done, rather than the light engine working through millions of them.
 */
@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {
	@Redirect(method = "setBlockState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/chunk/light/LightingProvider;checkBlock(Lnet/minecraft/util/math/BlockPos;)V"))
	private void shootingstar$gatherLight(LightingProvider light, BlockPos pos) {
		if (!Erasure.defer(pos)) {
			light.checkBlock(pos);
		}
	}
}
