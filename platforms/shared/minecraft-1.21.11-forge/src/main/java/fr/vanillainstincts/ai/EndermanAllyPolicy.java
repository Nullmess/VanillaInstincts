package fr.vanillainstincts.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/** Filtre minimal utilisé uniquement par l'Enderman pour sauver un allié. */
public final class EndermanAllyPolicy {
    private EndermanAllyPolicy() {
    }

    public static boolean canCoordinate(Mob first, Mob second) {
        if (!(first instanceof EnderMan) || second == null) return false;
        if (second instanceof AbstractPiglin) return false;
        return second instanceof EnderMan
                || second instanceof AbstractSkeleton
                || second instanceof Zombie
                || second instanceof AbstractIllager
                || second instanceof Witch
                || second instanceof Spider
                || second instanceof Creeper;
    }
}
