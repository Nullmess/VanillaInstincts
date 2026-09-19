package fr.vanillainstincts.ai;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.config.VanillaInstinctsServerConfig;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.AnimalRules;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.village.GolemDefenseController;
import fr.vanillainstincts.village.GolemDefenseState;
import fr.vanillainstincts.village.GolemDefenseStateStore;
import fr.vanillainstincts.village.GolemPatrolController;
import fr.vanillainstincts.village.GolemRepairController;
import fr.vanillainstincts.village.LearnedDoorController;
import fr.vanillainstincts.village.RecoveredTradeController;
import fr.vanillainstincts.village.VillageController;
import fr.vanillainstincts.village.VillagerGrandMasterController;
import fr.vanillainstincts.village.VillagerRuntimeState;
import fr.vanillainstincts.village.VillagerStateStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
/**
 * Pipeline explicite : villageois, golems, Endermen, creepers, araignées,
 * loups sauvages, troupeaux passifs et missions cochon/vache. Les anciens
 * systèmes globaux de difficulté, équipement et factions restent absents.
 */
public final class VanillaInstinctsController {
    private VanillaInstinctsController() {
    }

    public static void tick(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive()) {
            return;
        }
        if (!RuntimeConfig.snapshot().enabled()) return;
        long workStarted = VanillaInstinctsScheduler.beginWork();
        try {
            long gameTime = level.getGameTime();
            MobMovementPolicy.observeLifecycle(mob);
            if (FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, level)) {
                MobPerceptionMemory.observe(mob, level, gameTime);
            }
            if (FeatureGate.enabled(FeatureFlag.TARGET_ACQUISITION, level)) {
                TargetAcquisitionController.maintain(mob, level, gameTime);
            }

            LivingEntity target = mob.getTarget();
            MobRunController.setCombatSprint(mob,
                    MobMovementPolicy.shouldCombatSprint(mob, target, gameTime));

            // Le pillage d'avant-poste est prioritaire sur le combat et la patrouille.
            if (mob instanceof Pillager pillager
                    && FeatureGate.enabled(FeatureFlag.PILLAGER_RECOVERY, level)
                    && PillagerOutpostLootController.maintain(pillager, level,
                    gameTime)) {
                return;
            }

            // Le contrôleur de mission possède sa navigation et sa course propres.
            if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS, level)
                    && NetherReinforcementController.maintain(mob, level, gameTime)) {
                return;
            }

            if (mob instanceof Villager villager) {
                if (FeatureGate.enabled(FeatureFlag.VILLAGER_ROUTINES, level)) {
                    tickVillager(villager, level, gameTime);
                } else if (FeatureGate.enabled(
                        FeatureFlag.VILLAGER_PROFESSIONS, level)) {
                    // Profession progression must not depend on the unrelated
                    // routine switch. This also keeps the Master -> Grand
                    // Master merchant progress payload synchronized.
                    VillagerGrandMasterController.maintain(villager, level);
                }
            } else if (mob instanceof ZombieVillager zombieVillager) {
                if (FeatureGate.enabled(FeatureFlag.IRON_DOOR_LEARNING, level)) {
                    LearnedDoorController.maintainZombieVillager(zombieVillager,
                            level, gameTime);
                }
            } else if (mob instanceof Zombie zombie) {
                if (FeatureGate.enabled(FeatureFlag.ZOMBIE_TACTICS, level)) {
                    tickZombie(zombie, level, gameTime);
                }
            } else if (mob instanceof WanderingTrader trader) {
                if (FeatureGate.enabled(FeatureFlag.SOCIAL_TRADING, level)) {
                    RecoveredTradeController.maintain(trader, level, gameTime);
                }
            } else if (mob instanceof IronGolem golem) {
                if (FeatureGate.enabled(FeatureFlag.GOLEM_AI, level)) {
                    tickGolem(golem, level, gameTime);
                }
            } else if (mob instanceof EnderMan enderman) {
                if (FeatureGate.enabled(FeatureFlag.ENDERMAN_TACTICS, level)) {
                    tickEnderman(enderman, level, gameTime);
                }
            } else if (mob instanceof Creeper creeper) {
                if (FeatureGate.enabled(FeatureFlag.CREEPER_TACTICS, level)) {
                    tickCreeper(creeper, level, gameTime);
                }
            } else if (mob instanceof Spider spider) {
                if (FeatureGate.enabled(FeatureFlag.SPIDER_TACTICS, level)) {
                    tickSpider(spider, level, gameTime);
                }
            } else if (mob instanceof Pillager pillager) {
                if (FeatureGate.enabled(FeatureFlag.PILLAGER_TACTICS, level)) {
                    tickPillager(pillager, level, gameTime);
                }
            } else if (mob instanceof AbstractSkeleton skeleton) {
                tickSkeleton(skeleton, level, gameTime);
            } else if (mob instanceof Wolf wolf) {
                if (FeatureGate.enabled(FeatureFlag.WOLF_PACKS, level)) {
                    tickWolf(wolf, level, gameTime);
                }
            } else if (mob instanceof Animal animal
                    && (FeatureGate.enabled(FeatureFlag.ANIMAL_HERDS, level)
                    || FeatureGate.enabled(FeatureFlag.ANIMAL_COMFORT, level)
                    || FeatureGate.enabled(FeatureFlag.MOB_ECOLOGY, level))) {
                tickAnimal(animal, level, gameTime);
            }
        } finally {
            VanillaInstinctsScheduler.recordWork(level, workStarted);
        }
    }

    private static void tickVillager(Villager villager, ServerLevel level,
                                     long gameTime) {
        VillagerRuntimeState villageState =
                VillagerStateStore.stateFor(villager);
        MobRuntimeState runtime = MobStateStore.stateFor(villager);
        try {
            VillageController.maintain(villager, villageState, level,
                    gameTime);
            runtime.expireMemoryIfNeeded(gameTime);
            runtime.maintainIntent(villager, gameTime);
            if (!scheduled(villager, level, FeatureFlag.VILLAGER_ROUTINES)) return;

            MobDecisionPlan plan = new MobDecisionPlan();
            VillageController.contribute(villager, villageState, plan,
                    level, gameTime);
            if (!MobDecisionExecutor.apply(villager, runtime, plan,
                    gameTime)) {
                runtime.resetToIdle(gameTime);
            }
        } finally {
            VillagerStateStore.save(villager, villageState);
            MobStateStore.save(villager, runtime);
        }
    }

    private static void tickGolem(IronGolem golem, ServerLevel level,
                                  long gameTime) {
        GolemDefenseState defense = GolemDefenseStateStore.stateFor(golem);
        GolemConstructionState combat = GolemConstructionStateStore.stateFor(golem);
        MobRuntimeState runtime = MobStateStore.stateFor(golem);
        try {
            combat.refresh(gameTime);
            LivingEntity target = golem.getTarget();
            boolean constructionEnabled = FeatureGate.enabled(
                    FeatureFlag.GOLEM_CONSTRUCTION, level);
            boolean physicalConstruction = constructionEnabled
                    && GolemConstructionController.maintain(
                            golem, target, combat, level, gameTime);
            GolemDefenseController.maintain(golem, defense, level, gameTime);
            runtime.expireMemoryIfNeeded(gameTime);
            runtime.maintainIntent(golem, gameTime);
            if (!scheduled(golem, level, FeatureFlag.GOLEM_AI)) return;

            MobDecisionPlan plan = new MobDecisionPlan();
            GolemDefenseController.contribute(golem, defense, plan, level,
                    gameTime);
            target = golem.getTarget();
            if (constructionEnabled && target != null && target.isAlive()) {
                GolemConstructionController.contribute(golem, target,
                        combat, plan, level, gameTime);
            } else if (!FeatureGate.enabled(FeatureFlag.VILLAGE_REPAIRS,
                    level)
                    || !GolemRepairController.contributeGolem(golem, plan,
                    level, gameTime)) {
                GolemPatrolController.contribute(golem, plan, level, gameTime);
            }
            boolean applied = MobDecisionExecutor.apply(golem, runtime, plan,
                    gameTime);
            if (!applied && !physicalConstruction) {
                runtime.resetToIdle(gameTime);
            }
        } finally {
            GolemDefenseStateStore.save(golem, defense);
            GolemConstructionStateStore.save(golem, combat);
            MobStateStore.save(golem, runtime);
        }
    }

    private static void tickEnderman(EnderMan enderman, ServerLevel level,
                                     long gameTime) {
        EndermanRuntimeState endermanState =
                EndermanStateStore.stateFor(enderman);
        MobRuntimeState runtime = MobStateStore.stateFor(enderman);
        try {
            EndermanTacticsController.maintain(enderman, endermanState,
                    level, gameTime);
            runtime.expireMemoryIfNeeded(gameTime);
            runtime.maintainIntent(enderman, gameTime);
            if (!scheduled(enderman, level, FeatureFlag.ENDERMAN_TACTICS)) return;

            MobDecisionPlan plan = new MobDecisionPlan();
            EndermanTacticsController.contribute(enderman, endermanState,
                    plan, level, gameTime);
            if (!MobDecisionExecutor.apply(enderman, runtime, plan,
                    gameTime)) {
                runtime.resetToIdle(gameTime);
            }
        } finally {
            EndermanStateStore.save(enderman, endermanState);
            MobStateStore.save(enderman, runtime);
        }
    }

    private static void tickCreeper(Creeper creeper, ServerLevel level,
                                    long gameTime) {
        SpeciesRuntimeState species = SpeciesStateStore.stateFor(creeper);
        MobRuntimeState runtime = MobStateStore.stateFor(creeper);
        try {
            LivingEntity target = creeper.getTarget();
            CreeperTacticsController.maintainTacticalFuse(creeper, target);
            // La feinte est maintenue après la mèche tactique afin que son
            // swell temporaire ne soit pas immédiatement annulé.
            CreeperDiversionController.maintain(creeper, gameTime);
            runtime.maintainIntent(creeper, gameTime);
            if (!scheduled(creeper, level, FeatureFlag.CREEPER_TACTICS)) return;

            MobDecisionPlan plan = new MobDecisionPlan();
            if (FeatureGate.enabled(FeatureFlag.GENERIC_MOBILITY, level)) {
                GenericMobilityController.contribute(creeper, plan, level,
                        gameTime);
            }
            if (target instanceof Player player && validCombatTarget(player)
                    && VanillaInstinctsServerConfig.creeperTacticsAllowedAtY(
                    creeper.getBlockY())) {
                if (!CreeperDiversionController.contribute(creeper, player,
                        plan, level, gameTime)) {
                    CreeperTacticsController.contributeVisible(creeper,
                            player, species, plan, level, gameTime);
                }
            }
            if (!MobDecisionExecutor.apply(creeper, runtime, plan,
                    gameTime)) {
                runtime.resetToIdle(gameTime);
            }
        } finally {
            SpeciesStateStore.save(creeper, species);
            MobStateStore.save(creeper, runtime);
        }
    }

    private static void tickSpider(Spider spider, ServerLevel level,
                                   long gameTime) {
        SpeciesRuntimeState species = SpeciesStateStore.stateFor(spider);
        MobRuntimeState runtime = MobStateStore.stateFor(spider);
        try {
            SpiderTacticsController.maintainAttachment(spider, species,
                    level, gameTime);
            runtime.maintainIntent(spider, gameTime);
            if (!scheduled(spider, level, FeatureFlag.SPIDER_TACTICS)) return;

            LivingEntity target = spider.getTarget();
            MobDecisionPlan plan = new MobDecisionPlan();
            if (FeatureGate.enabled(FeatureFlag.GENERIC_MOBILITY, level)) {
                GenericMobilityController.contribute(spider, plan, level,
                        gameTime);
            }
            if (validCombatTarget(target)) {
                if (!SpiderRetreatController.contribute(spider, target, plan,
                        level, gameTime)
                        && !SpiderDropAssaultController.contribute(spider,
                        target, plan, level, gameTime)
                        && !SpiderMountController.contribute(spider, target,
                        species, plan, level, gameTime)) {
                    SpiderTacticsController.contributeVisible(spider, target,
                            species, plan, level, gameTime);
                }
            }
            if (!MobDecisionExecutor.apply(spider, runtime, plan,
                    gameTime)) {
                runtime.resetToIdle(gameTime);
            }
        } finally {
            SpeciesStateStore.save(spider, species);
            MobStateStore.save(spider, runtime);
        }
    }

    private static void tickZombie(Zombie zombie, ServerLevel level,
                                   long gameTime) {
        MobRuntimeState runtime = MobStateStore.stateFor(zombie);
        try {
            AdaptiveProgressionController.tickZombie(zombie, level, gameTime);
            ZombieTacticsController.maintain(zombie, level, gameTime);
            runtime.maintainIntent(zombie, gameTime);
            if (!scheduled(zombie, level, FeatureFlag.ZOMBIE_TACTICS)) return;
            MobDecisionPlan plan = new MobDecisionPlan();
            if (FeatureGate.enabled(FeatureFlag.GENERIC_MOBILITY, level)) {
                GenericMobilityController.contribute(zombie, plan, level,
                        gameTime);
            }
            AdaptiveShieldResponseController.contributeZombie(
                    zombie, plan, level, gameTime);
            ZombieTacticsController.contribute(zombie, plan, level, gameTime);
            if (!MobDecisionExecutor.apply(zombie, runtime, plan, gameTime)) {
                runtime.resetToIdle(gameTime);
            }
        } finally {
            MobStateStore.save(zombie, runtime);
        }
    }

    private static void tickSkeleton(AbstractSkeleton skeleton,
                                     ServerLevel level, long gameTime) {
        if (FeatureGate.enabled(FeatureFlag.SNIPER_SKELETONS, level)) {
            SniperSkeletonController.tick(skeleton, level, gameTime);
        }
        if (!FeatureGate.enabled(FeatureFlag.SKELETON_TACTICS, level)) return;
        AdaptiveShieldResponseController.tickSkeleton(skeleton, level,
                gameTime);
        MobRuntimeState runtime = MobStateStore.stateFor(skeleton);
        try {
            SkeletonTacticsController.maintain(skeleton, level, gameTime);
            runtime.maintainIntent(skeleton, gameTime);
            if (!scheduled(skeleton, level, FeatureFlag.SKELETON_TACTICS)) return;
            MobDecisionPlan plan = new MobDecisionPlan();
            if (FeatureGate.enabled(FeatureFlag.GENERIC_MOBILITY, level)) {
                GenericMobilityController.contribute(skeleton, plan, level,
                        gameTime);
            }
            AdaptiveShieldResponseController.contributeSkeleton(
                    skeleton, plan, level, gameTime);
            SkeletonTacticsController.contribute(skeleton, plan, level,
                    gameTime);
            if (!MobDecisionExecutor.apply(skeleton, runtime, plan, gameTime)) {
                runtime.resetToIdle(gameTime);
            }
        } finally {
            MobStateStore.save(skeleton, runtime);
        }
    }

    private static void tickPillager(Pillager pillager, ServerLevel level,
                                     long gameTime) {
        MobRuntimeState runtime = MobStateStore.stateFor(pillager);
        try {
            PillagerTacticsController.maintain(pillager, level, gameTime);
            runtime.maintainIntent(pillager, gameTime);
            if (!scheduled(pillager, level, FeatureFlag.PILLAGER_TACTICS)) return;
            MobDecisionPlan plan = new MobDecisionPlan();
            if (FeatureGate.enabled(FeatureFlag.GENERIC_MOBILITY, level)) {
                GenericMobilityController.contribute(pillager, plan, level,
                        gameTime);
            }
            PillagerTacticsController.contribute(pillager, plan, level, gameTime);
            if (!MobDecisionExecutor.apply(pillager, runtime, plan, gameTime)) {
                runtime.resetToIdle(gameTime);
            }
        } finally {
            MobStateStore.save(pillager, runtime);
        }
    }

    private static void tickWolf(Wolf wolf, ServerLevel level,
                                 long gameTime) {
        MobRuntimeState runtime = MobStateStore.stateFor(wolf);
        try {
            runtime.maintainIntent(wolf, gameTime);
            if (!scheduled(wolf, level, FeatureFlag.WOLF_PACKS)) return;
            MobDecisionPlan plan = new MobDecisionPlan();
            WolfPackController.contribute(wolf, plan, level, gameTime);
            if (!MobDecisionExecutor.apply(wolf, runtime, plan, gameTime)) {
                runtime.resetToIdle(gameTime);
            }
        } finally {
            MobStateStore.save(wolf, runtime);
        }
    }

    private static void tickAnimal(Animal animal, ServerLevel level,
                                   long gameTime) {
        MobRuntimeState runtime = MobStateStore.stateFor(animal);
        try {
            runtime.maintainIntent(animal, gameTime);
            FeatureFlag cadence = FeatureGate.enabled(FeatureFlag.ANIMAL_HERDS, level)
                    ? FeatureFlag.ANIMAL_HERDS
                    : FeatureGate.enabled(FeatureFlag.ANIMAL_COMFORT, level)
                    ? FeatureFlag.ANIMAL_COMFORT : FeatureFlag.MOB_ECOLOGY;
            if (!scheduled(animal, level, cadence)) return;
            if (FeatureGate.enabled(FeatureFlag.ANIMAL_COMFORT, level)
                    && VanillaInstinctsScheduler.isScheduled(animal,
                    RuntimeConfig.interval(FeatureFlag.ANIMAL_COMFORT,
                            AnimalRules.ANIMAL_COMFORT_SCAN_INTERVAL_TICKS))) {
                AnimalWelfareController.maintain(animal, level, gameTime);
            }
            MobDecisionPlan plan = new MobDecisionPlan();
            if (FeatureGate.enabled(FeatureFlag.MOB_ECOLOGY, level)) {
                MobEcologyController.contribute(animal, plan, level, gameTime);
            }
            if (FeatureGate.enabled(FeatureFlag.ANIMAL_HERDS, level)) {
                AnimalHerdController.contribute(animal, plan, level, gameTime);
            }
            if (FeatureGate.enabled(FeatureFlag.ANIMAL_COMFORT, level)) {
                AnimalComfortController.contribute(animal, plan, level, gameTime);
            }
            if (!MobDecisionExecutor.apply(animal, runtime, plan, gameTime)) {
                runtime.resetToIdle(gameTime);
            }
        } finally {
            MobStateStore.save(animal, runtime);
        }
    }

    private static boolean validCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        return !(target instanceof Player player)
                || !player.isCreative() && !player.isSpectator();
    }

    private static boolean scheduled(Mob mob, ServerLevel level,
                                     FeatureFlag feature) {
        int interval = RuntimeConfig.interval(feature,
                RuntimeConfig.snapshot().decisionIntervalTicks());
        return VanillaInstinctsScheduler.isScheduled(mob, interval)
                && VanillaInstinctsScheduler.claim(level, mob,
                PerformanceRules.BASE_DECISION_COST);
    }
}
