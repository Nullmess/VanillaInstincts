package fr.vanillainstincts.mixin;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Preserves selector state from vanilla and other mods across possession. */
@Mixin(GoalSelector.class)
public interface GoalSelectorDisabledFlagsAccessor {
    @Accessor("disabledFlags")
    EnumSet<Goal.Flag> vanillaInstincts$getDisabledFlags();
}
