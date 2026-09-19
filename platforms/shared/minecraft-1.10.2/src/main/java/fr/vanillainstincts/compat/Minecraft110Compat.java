package fr.vanillainstincts.compat;

import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;

/** Compatibility helpers for entity variants that were split into classes after 1.10.2. */
public final class Minecraft110Compat {
    private Minecraft110Compat() {}

    /** In 1.10.2 the Wither Skeleton is skeleton type 1, not a distinct entity class. */
    public static boolean isWitherSkeleton(EntitySkeleton skeleton) {
        return skeleton != null && skeleton.getSkeletonType() != null && skeleton.getSkeletonType().ordinal() == 1;
    }

    /** In 1.10.2 the zombie villager is a flag on EntityZombie. */
    public static boolean isZombieVillager(EntityZombie zombie) {
        return zombie != null && zombie.isVillager();
    }
}
