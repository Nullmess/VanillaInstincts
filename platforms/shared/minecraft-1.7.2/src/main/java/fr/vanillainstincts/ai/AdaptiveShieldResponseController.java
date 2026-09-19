package fr.vanillainstincts.ai;

import fr.vanillainstincts.ai.MobDecisionPlan;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

/**
 * 1.8.x has neither shields nor an off-hand slot. Keep the later adaptive
 * shield API callable, but disable only that impossible gameplay branch.
 */
public final class AdaptiveShieldResponseController {
    private static final ResourceLocation DEFLECT_ARROW =
            new ResourceLocation("minecraft", "adventure/deflect_arrow");
    private AdaptiveShieldResponseController() {}

    public static void onZombieJoin(EntityZombie zombie, WorldServer level,
                                    boolean loadedFromDisk) { }
    public static void onSkeletonJoin(EntitySkeleton skeleton, WorldServer level,
                                      boolean loadedFromDisk) { }
    public static void tickSkeleton(EntitySkeleton skeleton, WorldServer level,
                                    long gameTime) { }
    public static void contributeZombie(EntityZombie zombie, MobDecisionPlan plan,
                                        WorldServer level, long gameTime) { }
    public static void contributeSkeleton(EntitySkeleton skeleton, MobDecisionPlan plan,
                                          WorldServer level, long gameTime) { }
    public static boolean isZombieFlanker(EntityZombie zombie) { return false; }
    public static boolean isShieldSkeleton(EntitySkeleton skeleton) { return false; }
    public static boolean isShieldSkeletonMelee(EntitySkeleton skeleton) { return false; }
    public static ResourceLocation deflectArrowAdvancementId() { return DEFLECT_ARROW; }
}
