package fr.vanillainstincts.ai;

import fr.vanillainstincts.compat.Minecraft115WorldCompat;

import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EnderCrystalEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.world.World;

/** Rend le dragon invulnérable tant qu'un cristal actif existe dans l'End. */
public final class EnderDragonCrystalGuard {
    private EnderDragonCrystalGuard() {
    }

    public static boolean shouldProtect(Entity entity) {
        if (!(entity instanceof EnderDragonEntity)) return false;
        EnderDragonEntity dragon = (EnderDragonEntity) entity;
        if (!(dragon.level instanceof ServerWorld)) return false;
        ServerWorld level = (ServerWorld) dragon.level;
        if (!DimensionType.THE_END.equals(level.dimension.getType())) return false;
        return hasActiveCrystal(level);
    }

    public static boolean hasActiveCrystal(ServerWorld level) {
        return level != null && containsActiveCrystal(Minecraft115WorldCompat.entities(level));
    }

    public static boolean containsActiveCrystal(
            Iterable<? extends Entity> entities) {
        if (entities == null) return false;
        for (Entity entity : entities) {
            if (entity instanceof EnderCrystalEntity && entity.isAlive()) {
                return true;
            }
        }
        return false;
    }
}
