package fr.vanillainstincts.ai;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.AbstractIllager;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntityPigZombie;

/** Filtre minimal utilisé uniquement par l'Enderman pour sauver un allié. */
public final class EndermanAllyPolicy {
    private EndermanAllyPolicy() {
    }

    public static boolean canCoordinate(EntityLiving first, EntityLiving second) {
        if (!(first instanceof EntityEnderman) || second == null) return false;
        if (second instanceof EntityPigZombie) return false;
        return second instanceof EntityEnderman
                || second instanceof AbstractSkeleton
                || second instanceof EntityZombie
                || second instanceof AbstractIllager
                || second instanceof EntityWitch
                || second instanceof EntitySpider
                || second instanceof EntityCreeper;
    }
}
