package fr.vanillainstincts.compat;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.util.BlockPos;

/** 1.12 replacements for BlockPos box helpers introduced in later Minecraft versions. */
public final class Minecraft112BlockPosCompat {
    private Minecraft112BlockPosCompat() {}

    public static Iterable<BlockPos> betweenClosed(BlockPos from, BlockPos to) {
        return BlockPos.getAllInBox(from, to);
    }

    public static Stream<BlockPos> betweenClosedStream(BlockPos from, BlockPos to) {
        return StreamSupport.stream(BlockPos.getAllInBox(from, to).spliterator(), false);
    }
}
