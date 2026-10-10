package io.github.kortev.shootingstar.test.mixin;

import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The world's section meshes, for the self test's render diagnostics. */
@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {
	@Accessor("chunks")
	BuiltChunkStorage shootingstarTest$getChunks();
}
