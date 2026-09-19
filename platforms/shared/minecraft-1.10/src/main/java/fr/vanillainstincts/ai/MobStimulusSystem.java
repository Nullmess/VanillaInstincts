package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.StimulusType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;

/**
 * Single entry point for world/combat stimuli that feed fair sensory memory.
 * The emitter describes what happened and where; Perception 3.0 decides which
 * observers can hear it and stores a deliberately imprecise point of interest.
 */
public final class MobStimulusSystem {
    private MobStimulusSystem() {
    }

    public static void emit(WorldServer level, StimulusType type,
                            BlockPos position, double radius,
                            int memoryTicks, EntityLivingBase source) {
        emit(level, type, position, radius, memoryTicks, source,
                defaultConfidence(type));
    }

    public static void emit(WorldServer level, StimulusType type,
                            BlockPos position, double radius,
                            int memoryTicks, EntityLivingBase source,
                            double confidence) {
        if (level == null || position == null || type == null
                || radius <= 0.0D || memoryTicks <= 0) {
            return;
        }
        MobPerceptionMemory.broadcastProfiledStimulus(level, position,
                radius, memoryTicks, source, type, confidence);
    }

    public static double defaultConfidence(StimulusType type) {
        if (type == null) return 0.5D;
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((type)) { case DAMAGE: case EXPLOSION:  return 0.95D; case PROJECTILE_IMPACT:  return 0.85D; case BLOCK_BREAK:  return 0.80D; case ITEM_USE:  return 0.65D; case ALLY_ALERT:  return 0.90D; case FOOD:  return 0.60D; case VISUAL:  return 1.0D; case SOUND:  return 0.70D;  default: throw new AssertionError("Unexpected switch value"); } });
    }
}
