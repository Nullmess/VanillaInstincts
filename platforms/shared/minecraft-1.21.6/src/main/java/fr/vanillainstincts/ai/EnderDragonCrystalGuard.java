package fr.vanillainstincts.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;

/** Rend le dragon invulnérable tant qu'un cristal actif existe dans l'End. */
public final class EnderDragonCrystalGuard {
    private EnderDragonCrystalGuard() {
    }

    public static boolean shouldProtect(Entity entity) {
        if (!(entity instanceof EnderDragon dragon)
                || !(dragon.level() instanceof ServerLevel level)
                || !Level.END.equals(level.dimension())) {
            return false;
        }
        return hasActiveCrystal(level);
    }

    public static boolean hasActiveCrystal(ServerLevel level) {
        return level != null && containsActiveCrystal(level.getAllEntities());
    }

    public static boolean containsActiveCrystal(
            Iterable<? extends Entity> entities) {
        if (entities == null) {
            return false;
        }
        for (Entity entity : entities) {
            if (entity instanceof EndCrystal crystal
                    && crystal.isAlive() && !crystal.isRemoved()) {
                return true;
            }
        }
        return false;
    }
}
