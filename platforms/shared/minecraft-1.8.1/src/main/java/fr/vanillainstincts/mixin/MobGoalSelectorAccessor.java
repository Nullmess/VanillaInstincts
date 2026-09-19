package fr.vanillainstincts.mixin;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accesses vanilla selectors so possession can pause goals without NoAI. */
@Mixin(EntityLiving.class)
public interface MobGoalSelectorAccessor {
    @Accessor("goalSelector")
    GoalSelector vanillaInstincts$getGoalSelector();

    @Accessor("targetSelector")
    GoalSelector vanillaInstincts$getTargetSelector();
}
