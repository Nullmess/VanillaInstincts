package fr.vanillainstincts.ai;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.AbstractIllagerEntity;
import net.minecraft.entity.monster.AbstractSkeletonEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.monster.EndermanEntity;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.entity.monster.WitchEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.monster.ZombiePigmanEntity;

/** Filtre minimal utilisé uniquement par l'Enderman pour sauver un allié. */
public final class EndermanAllyPolicy {
    private EndermanAllyPolicy() {
    }

    public static boolean canCoordinate(MobEntity first, MobEntity second) {
        if (!(first instanceof EndermanEntity) || second == null) return false;
        if (second instanceof ZombiePigmanEntity) return false;
        return second instanceof EndermanEntity
                || second instanceof AbstractSkeletonEntity
                || second instanceof ZombieEntity
                || second instanceof AbstractIllagerEntity
                || second instanceof WitchEntity
                || second instanceof SpiderEntity
                || second instanceof CreeperEntity;
    }
}
