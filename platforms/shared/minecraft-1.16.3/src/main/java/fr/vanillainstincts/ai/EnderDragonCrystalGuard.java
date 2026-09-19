package fr.vanillainstincts.ai;

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
        if (!World.END.equals(level.dimension())) return false;
        return hasActiveCrystal(level);
    }

    public static boolean hasActiveCrystal(ServerWorld level) {
        return level != null && containsActiveCrystal(level.getAllEntities());
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
