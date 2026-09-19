package fr.vanillainstincts.mixin;

import net.minecraft.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.entity.monster.EntitySkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minimal accessor used only to tune vanilla's existing bow goal interval. */
@Mixin(EntitySkeleton.class)
public interface EntitySkeletonBowGoalAccessor {
    @Accessor("bowGoal")
    RangedBowAttackGoal<EntitySkeleton> vanillaInstincts$getBowGoal();
}
