package io.github.kortev.shootingstar.test.mixin;

import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BuiltChunkStorage.class)
public interface BuiltChunkStorageAccessor {
	@Accessor("chunks")
	ChunkBuilder.BuiltChunk[] shootingstarTest$getChunks();
}
