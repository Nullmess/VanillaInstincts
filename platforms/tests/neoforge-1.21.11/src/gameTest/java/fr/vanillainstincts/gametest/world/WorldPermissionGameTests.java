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
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Blocks;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldPermissionGameTests {
    private WorldPermissionGameTests() {
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void permissionDefaultsAreProtective(GameTestHelper helper) {
        ConfigSnapshot defaults = ConfigSnapshot.defaults();
        helper.assertTrue(defaults.respectMobGriefing(),
                net.minecraft.network.chat.Component.literal("Le gamerule mobGriefing doit être respecté par défaut"));
        helper.assertTrue(defaults.protectSpawnArea(),
                net.minecraft.network.chat.Component.literal("La zone de spawn doit être protégée par défaut"));
        helper.assertTrue(defaults.protectBlockEntities(),
                net.minecraft.network.chat.Component.literal("Les blocs avec données doivent être protégés par défaut"));
        helper.assertValueEqual(defaults.spawnProtectionRadius(), 16,
                net.minecraft.network.chat.Component.literal("Le rayon de protection par défaut doit être borné"));
        helper.succeed();
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void worldSwitchBlocksPlacement(GameTestHelper helper) {
        run(helper, ConfigSnapshot.builder().allowWorldChanges(false)
                .protectSpawnArea(false).build(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            var before = level.getBlockState(pos);
            helper.assertFalse(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.STONE.defaultBlockState(), 3,
                            WorldActionType.PLACE_BLOCK),
                    net.minecraft.network.chat.Component.literal("allowWorldChanges=false doit bloquer le placement"));
            helper.assertValueEqual(level.getBlockState(pos), before,
                    net.minecraft.network.chat.Component.literal("Un placement refusé ne doit rien modifier"));
        });
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void itemSwitchBlocksContainers(GameTestHelper helper) {
        run(helper, ConfigSnapshot.builder().allowItemChanges(false)
                .protectSpawnArea(false).build(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            helper.assertFalse(WorldPermissionService.canMutateContainer(
                            level, null, pos),
                    net.minecraft.network.chat.Component.literal("allowItemChanges=false doit bloquer les conteneurs"));
        });
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void permittedPlacementChangesWorld(GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.assertTrue(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.STONE.defaultBlockState(), 3,
                            WorldActionType.PLACE_BLOCK),
                    net.minecraft.network.chat.Component.literal("Une modification autorisée doit réussir"));
            helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE),
                    net.minecraft.network.chat.Component.literal("Le bloc autorisé doit être réellement posé"));
        });
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void mobGriefingBlocksMobPlacement(GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
            Zombie zombie = helper.spawn(EntityType.ZOMBIE,
                    new BlockPos(1, 1, 1));
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.assertFalse(WorldPermissionService.setBlock(level, zombie,
                            pos, Blocks.COBWEB.defaultBlockState(), 3,
                            WorldActionType.PLACE_BLOCK),
                    net.minecraft.network.chat.Component.literal("Un mob ne doit pas contourner mobGriefing=false"));
        });
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void cleanupIgnoresMobGriefing(GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
            helper.assertTrue(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.AIR.defaultBlockState(), 3,
                            WorldActionType.TEMPORARY_CLEANUP),
                    net.minecraft.network.chat.Component.literal("Le nettoyage d'un bloc temporaire doit rester possible"));
        });
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void protectedTagBlocksReplacement(GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            level.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
            helper.assertFalse(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.STONE.defaultBlockState(), 3,
                            WorldActionType.REPLACE_BLOCK),
                    net.minecraft.network.chat.Component.literal("Un bloc protégé par tag ne doit pas être remplacé"));
            helper.assertTrue(level.getBlockState(pos).is(Blocks.BEDROCK),
                    net.minecraft.network.chat.Component.literal("Le bloc protégé doit rester intact"));
        });
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void blockEntityIsProtected(GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            helper.assertFalse(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.AIR.defaultBlockState(), 3,
                            WorldActionType.REPLACE_BLOCK),
                    net.minecraft.network.chat.Component.literal("Un coffre ne doit pas être effacé par une IA"));
            helper.assertTrue(level.getBlockState(pos).is(Blocks.CHEST),
                    net.minecraft.network.chat.Component.literal("Le coffre protégé doit rester présent"));
        });
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
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
                    net.minecraft.network.chat.Component.literal("Une transaction autorisée doit placer tous les blocs"));
            helper.assertTrue(level.getBlockState(first)
                            .is(Blocks.CRYING_OBSIDIAN)
                            && level.getBlockState(second)
                            .is(Blocks.CRYING_OBSIDIAN),
                    net.minecraft.network.chat.Component.literal("Tous les blocs de la transaction doivent être présents"));
        });
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
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
                    net.minecraft.network.chat.Component.literal("Un seul refus doit annuler toute la transaction"));
            helper.assertValueEqual(level.getBlockState(first), firstBefore,
                    net.minecraft.network.chat.Component.literal("Le premier bloc ne doit pas être modifié partiellement"));
            helper.assertTrue(level.getBlockState(second).is(Blocks.BEDROCK),
                    net.minecraft.network.chat.Component.literal("Le bloc refusé doit rester intact"));
        });
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void deniedPositionUsesRetryCooldown(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean original = level.getGameRules()
                .get(GameRules.MOB_GRIEFING);
        try {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            RuntimeConfig.install(ConfigSnapshot.builder()
                    .allowWorldChanges(false).protectSpawnArea(false).build());
            helper.assertFalse(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.STONE.defaultBlockState(), 3,
                            WorldActionType.PLACE_BLOCK),
                    net.minecraft.network.chat.Component.literal("Le premier refus doit être enregistré"));
            RuntimeConfig.install(ordinaryConfig());
            helper.assertValueEqual(WorldPermissionService.check(level, null,
                            WorldActionType.PLACE_BLOCK, pos,
                            Blocks.STONE.defaultBlockState()),
                    PermissionDecision.RETRY_COOLDOWN,
                    net.minecraft.network.chat.Component.literal("Une IA ne doit pas répéter le même refus chaque tick"));
            helper.succeed();
        } finally {
            RuntimeConfig.reset();
            WorldPermissionService.clearLevel(level);
            level.getGameRules().set(GameRules.MOB_GRIEFING, original, level.getServer());
        }
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void entitySpawnUsesWorldPermission(GameTestHelper helper) {
        run(helper, ConfigSnapshot.builder().allowWorldChanges(false)
                .protectSpawnArea(false).build(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.assertFalse(WorldPermissionService.canSpawnEntity(
                            level, null, pos),
                    net.minecraft.network.chat.Component.literal("Une apparition créée par le mod est un changement monde"));
        });
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void containerMutationWorksWhenAllowed(
            GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            level.setBlockAndUpdate(pos, Blocks.BREWING_STAND
                    .defaultBlockState());
            helper.assertTrue(WorldPermissionService.canMutateContainer(
                            level, null, pos),
                    net.minecraft.network.chat.Component.literal("Un conteneur chargé doit accepter une action autorisée"));
        });
    }

    @GameTest(batch = "world_permission",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void systemActionDoesNotCallMobRuleWithNullActor(
            GameTestHelper helper) {
        run(helper, ordinaryConfig(), level -> {
            level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.assertTrue(WorldPermissionService.setBlock(level, null,
                            pos, Blocks.STONE.defaultBlockState(), 3,
                            WorldActionType.PLACE_BLOCK),
                    net.minecraft.network.chat.Component.literal("Une action système sans entité ne doit pas provoquer d'erreur"));
        });
    }

    private static ConfigSnapshot ordinaryConfig() {
        return ConfigSnapshot.builder().protectSpawnArea(false).build();
    }

    private static void run(GameTestHelper helper, ConfigSnapshot config,
                            Consumer<ServerLevel> assertions) {
        ServerLevel level = helper.getLevel();
        boolean original = level.getGameRules()
                .get(GameRules.MOB_GRIEFING);
        try {
            RuntimeConfig.install(config);
            WorldPermissionService.clearLevel(level);
            assertions.accept(level);
            helper.succeed();
        } finally {
            RuntimeConfig.reset();
            WorldPermissionService.clearLevel(level);
            level.getGameRules().set(GameRules.MOB_GRIEFING, original, level.getServer());
        }
    }
}
