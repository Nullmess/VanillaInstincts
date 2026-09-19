package fr.vanillainstincts.ai;

import fr.vanillainstincts.compat.Minecraft115WorldCompat;

import fr.vanillainstincts.compat.LegacyDimensionType;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.world.World;

/** Rend le dragon invulnérable tant qu'un cristal actif existe dans l'End. */
public final class EnderDragonCrystalGuard {
    private EnderDragonCrystalGuard() {
    }

    public static boolean shouldProtect(Entity entity) {
        if (!(entity instanceof EntityDragon)) return false;
        EntityDragon dragon = (EntityDragon) entity;
        if (!(dragon.world instanceof WorldServer)) return false;
        WorldServer level = (WorldServer) dragon.world;
        if (!LegacyDimensionType.THE_END.equals(LegacyDimensionType.of(level))) return false;
        return hasActiveCrystal(level);
    }

    public static boolean hasActiveCrystal(WorldServer level) {
        return level != null && containsActiveCrystal(Minecraft115WorldCompat.entities(level));
    }

    public static boolean containsActiveCrystal(
            Iterable<? extends Entity> entities) {
        if (entities == null) return false;
        for (Entity entity : entities) {
            if (entity instanceof EntityEnderCrystal && entity.isEntityAlive()) {
                return true;
            }
        }
        return false;
    }
}
