package fr.vanillainstincts.compat;

import net.minecraft.entity.Entity;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.world.World;

/** Sound bridge for the pre-SoundEvent / pre-SoundCategory 1.8.x API. */
public final class Minecraft18SoundCompat {
    private Minecraft18SoundCompat() {}

    public static void play(World world, BlockPos pos, String sound,
                            float volume, float pitch) {
        if (world == null || pos == null || sound == null) return;
        world.playSoundEffect(pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D, sound, volume, pitch);
    }

    public static void play(Entity entity, String sound,
                            float volume, float pitch) {
        if (entity != null && sound != null) entity.playSound(sound, volume, pitch);
    }
}
