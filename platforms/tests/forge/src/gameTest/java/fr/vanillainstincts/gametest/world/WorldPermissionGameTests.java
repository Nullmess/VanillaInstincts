package fr.vanillainstincts.gametest.world;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.config.ConfigSnapshot;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.permission.PermissionDecision;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class WorldPermissionGameTests {
    private WorldPermissionGameTests() {
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void permissionDefaultsAreProtective(GameTestHelper helper) {
        ConfigSnapshot defaults = ConfigSnapshot.defaults();
        helper.assertTrue(defaults.respectMobGriefing(),
                "Le gamerule mobGriefing doit être respecté par défaut");
        helper.assertTrue(defaults.protectSpawnArea(),
                "La zone de spawn doit être protégée par défaut");
        helper.assertTrue(defaults.protectBlockEntities(),
                "Les blocs avec données doivent être protégés par défaut");
        helper.assertValueEqual(defaults.spawnProtectionRadius(), 16,
                "Le rayon de protection par défaut doit être borné");
        helper.succeed();
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void worldSwitchBlocksPlacement(GameTestHelper helper) {
        run(helper, ConfigSnapshot.builder().allowWorldChanges(false)
                .protectSpawnArea(false).build(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            var before = level.getBlockState(pos);
            helper.assertFalse(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.STONE.defaultBlockState(), 3,
                            WorldActionType.PLACE_BLOCK),
                    "allowWorldChanges=false doit bloquer le placement");
            helper.assertValueEqual(level.getBlockState(pos), before,
                    "Un placement refusé ne doit rien modifier");
        });
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void itemSwitchBlocksContainers(GameTestHelper helper) {
        run(helper, ConfigSnapshot.builder().allowItemChanges(false)
                .protectSpawnArea(false).build(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            helper.assertFalse(WorldPermissionService.canMutateContainer(
                            level, null, pos),
                    "allowItemChanges=false doit bloquer les conteneurs");
        });
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void permittedPlacementChangesWorld(GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.assertTrue(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.STONE.defaultBlockState(), 3,
                            WorldActionType.PLACE_BLOCK),
                    "Une modification autorisée doit réussir");
            helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE),
                    "Le bloc autorisé doit être réellement posé");
        });
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void mobGriefingBlocksMobPlacement(GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(false, level.getServer());
            Zombie zombie = helper.spawn(EntityType.ZOMBIE,
                    new BlockPos(1, 1, 1));
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.assertFalse(WorldPermissionService.setBlock(level, zombie,
                            pos, Blocks.COBWEB.defaultBlockState(), 3,
                            WorldActionType.PLACE_BLOCK),
                    "Un mob ne doit pas contourner mobGriefing=false");
        });
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void cleanupIgnoresMobGriefing(GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(false, level.getServer());
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
            helper.assertTrue(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.AIR.defaultBlockState(), 3,
                            WorldActionType.TEMPORARY_CLEANUP),
                    "Le nettoyage d'un bloc temporaire doit rester possible");
        });
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void protectedTagBlocksReplacement(GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            level.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
            helper.assertFalse(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.STONE.defaultBlockState(), 3,
                            WorldActionType.REPLACE_BLOCK),
                    "Un bloc protégé par tag ne doit pas être remplacé");
            helper.assertTrue(level.getBlockState(pos).is(Blocks.BEDROCK),
                    "Le bloc protégé doit rester intact");
        });
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void blockEntityIsProtected(GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            helper.assertFalse(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.AIR.defaultBlockState(), 3,
                            WorldActionType.REPLACE_BLOCK),
                    "Un coffre ne doit pas être effacé par une IA");
            helper.assertTrue(level.getBlockState(pos).is(Blocks.CHEST),
                    "Le coffre protégé doit rester présent");
        });
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void atomicPlacementAppliesEveryBlock(
            GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            BlockPos first = helper.absolutePos(new BlockPos(2, 1, 2));
            BlockPos second = first.above();
            List<WorldPermissionService.BlockChange> changes = List.of(
                    new WorldPermissionService.BlockChange(first,
                            Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3),
                    new WorldPermissionService.BlockChange(second,
                            Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3));
            helper.assertTrue(WorldPermissionService.setBlocksAtomically(
                            level, null, changes, WorldActionType.PLACE_BLOCK),
                    "Une transaction autorisée doit placer tous les blocs");
            helper.assertTrue(level.getBlockState(first)
                            .is(Blocks.CRYING_OBSIDIAN)
                            && level.getBlockState(second)
                            .is(Blocks.CRYING_OBSIDIAN),
                    "Tous les blocs de la transaction doivent être présents");
        });
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void deniedAtomicPlacementChangesNothing(
            GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            BlockPos first = helper.absolutePos(new BlockPos(2, 1, 2));
            BlockPos second = first.above();
            var firstBefore = level.getBlockState(first);
            level.setBlockAndUpdate(second, Blocks.BEDROCK.defaultBlockState());
            List<WorldPermissionService.BlockChange> changes = List.of(
                    new WorldPermissionService.BlockChange(first,
                            Blocks.STONE.defaultBlockState(), 3),
                    new WorldPermissionService.BlockChange(second,
                            Blocks.STONE.defaultBlockState(), 3));
            helper.assertFalse(WorldPermissionService.setBlocksAtomically(
                            level, null, changes,
                            WorldActionType.REPLACE_BLOCK),
                    "Un seul refus doit annuler toute la transaction");
            helper.assertValueEqual(level.getBlockState(first), firstBefore,
                    "Le premier bloc ne doit pas être modifié partiellement");
            helper.assertTrue(level.getBlockState(second).is(Blocks.BEDROCK),
                    "Le bloc refusé doit rester intact");
        });
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void deniedPositionUsesRetryCooldown(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean original = level.getGameRules()
                .getRule(GameRules.RULE_MOBGRIEFING).get();
        try {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            RuntimeConfig.install(ConfigSnapshot.builder()
                    .allowWorldChanges(false).protectSpawnArea(false).build());
            helper.assertFalse(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.STONE.defaultBlockState(), 3,
                            WorldActionType.PLACE_BLOCK),
                    "Le premier refus doit être enregistré");
            RuntimeConfig.install(ordinaryConfig());
            helper.assertValueEqual(WorldPermissionService.check(level, null,
                            WorldActionType.PLACE_BLOCK, pos,
                            Blocks.STONE.defaultBlockState()),
                    PermissionDecision.RETRY_COOLDOWN,
                    "Une IA ne doit pas répéter le même refus chaque tick");
            helper.succeed();
        } finally {
            RuntimeConfig.reset();
            WorldPermissionService.clearLevel(level);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(original, level.getServer());
        }
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void entitySpawnUsesWorldPermission(GameTestHelper helper) {
        run(helper, ConfigSnapshot.builder().allowWorldChanges(false)
                .protectSpawnArea(false).build(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.assertFalse(WorldPermissionService.canSpawnEntity(
                            level, null, pos),
                    "Une apparition créée par le mod est un changement monde");
        });
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void containerMutationWorksWhenAllowed(
            GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            level.setBlockAndUpdate(pos, Blocks.BREWING_STAND
                    .defaultBlockState());
            helper.assertTrue(WorldPermissionService.canMutateContainer(
                            level, null, pos),
                    "Un conteneur chargé doit accepter une action autorisée");
        });
    }

    @GameTest(batch = "world_permission",
            template = "empty")
    public static void systemActionDoesNotCallMobRuleWithNullActor(
            GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(false, level.getServer());
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.assertTrue(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.STONE.defaultBlockState(), 3,
                            WorldActionType.PLACE_BLOCK),
                    "Une action système sans entité ne doit pas provoquer d'erreur");
        });
    }

    private static ConfigSnapshot ordinaryConfig() {
        return ConfigSnapshot.builder().protectSpawnArea(false).build();
    }

    private static void run(GameTestHelper helper, ConfigSnapshot config,
                            Consumer<ServerLevel> assertions) {
        ServerLevel level = helper.getLevel();
        boolean original = level.getGameRules()
                .getRule(GameRules.RULE_MOBGRIEFING).get();
        try {
            RuntimeConfig.install(config);
            WorldPermissionService.clearLevel(level);
            assertions.accept(level);
            helper.succeed();
        } finally {
            RuntimeConfig.reset();
            WorldPermissionService.clearLevel(level);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(original, level.getServer());
        }
    }
}
