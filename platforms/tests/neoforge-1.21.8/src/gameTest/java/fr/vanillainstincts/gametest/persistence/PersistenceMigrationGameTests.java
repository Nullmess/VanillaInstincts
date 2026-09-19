package fr.vanillainstincts.gametest.persistence;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.MobRuntimeState;
import fr.vanillainstincts.persistence.EntityDataMigrationService;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import fr.vanillainstincts.persistence.TemporaryWorldSavedData;
import fr.vanillainstincts.village.VillageEvolutionSavedData;
import fr.vanillainstincts.village.VillageGolemCeremonySavedData;
import fr.vanillainstincts.world.CryingPortalLinkSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PersistenceMigrationGameTests {
    private PersistenceMigrationGameTests() {
    }

    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void readsLegacyVersionKey(GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 3);
        helper.assertValueEqual(NbtSchema.readVersion(tag), 3,
                net.minecraft.network.chat.Component.literal("La version historique doit rester lisible"));
        helper.succeed();
    }

    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void writesCanonicalVersionKey(GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1);
        NbtSchema.writeVersion(tag, 4);
        helper.assertValueEqual(fr.vanillainstincts.persistence.NbtCompat.getInt(tag, "data_version"), 4,
                net.minecraft.network.chat.Component.literal("La version canonique doit être écrite"));
        helper.assertFalse(fr.vanillainstincts.persistence.NbtCompat.contains(tag, "version"),
                net.minecraft.network.chat.Component.literal("L'ancienne clé doit être supprimée"));
        helper.succeed();
    }

    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void migratesLegacyEntityPrefix(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        villager.getPersistentData().putInt("mobmind_probe", 7);
        helper.assertTrue(EntityDataMigrationService.migrate(villager, 100L),
                net.minecraft.network.chat.Component.literal("La première lecture doit migrer l'entité"));
        helper.assertValueEqual(fr.vanillainstincts.persistence.NbtCompat.getInt(villager.getPersistentData(), "vanillainstincts_probe"), 7,
                net.minecraft.network.chat.Component.literal("La valeur historique doit être conservée"));
        helper.assertFalse(fr.vanillainstincts.persistence.NbtCompat.contains(villager.getPersistentData(), "mobmind_probe"),
                net.minecraft.network.chat.Component.literal("L'ancienne clé doit disparaître"));
        helper.succeed();
    }

    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void entityMigrationIsIdempotent(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        // L'événement d'apparition migre déjà les nouvelles entités.
        // On retire le marqueur pour simuler un véritable ancien monde.
        villager.getPersistentData().remove(
                "vanillainstincts_entity_schema");
        helper.assertTrue(EntityDataMigrationService.migrate(villager, 100L),
                net.minecraft.network.chat.Component.literal("La première migration doit s'exécuter"));
        helper.assertFalse(EntityDataMigrationService.migrate(villager, 120L),
                net.minecraft.network.chat.Component.literal("La migration ne doit pas se répéter"));
        helper.succeed();
    }

    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void runtimeStateRewritesLegacySchema(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE,
                new BlockPos(2, 1, 2));
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("version", 1);
        legacy.putString("state", "IDLE");
        legacy.putString("owner", "NONE");
        legacy.putString("intent", "NONE");
        zombie.getPersistentData().put("vanillainstincts_runtime", legacy);
        MobRuntimeState state = MobRuntimeState.load(zombie);
        helper.assertTrue(state.isDirty(),
                net.minecraft.network.chat.Component.literal("Un ancien schéma doit demander une réécriture"));
        state.save(zombie);
        CompoundTag saved = zombie.getPersistentData()
                .getCompound("vanillainstincts_runtime").orElseGet(CompoundTag::new);
        helper.assertValueEqual(fr.vanillainstincts.persistence.NbtCompat.getInt(saved, "data_version"),
                PersistentDataVersions.MOB_RUNTIME,
                net.minecraft.network.chat.Component.literal("Le schéma actuel doit être écrit"));
        helper.succeed();
    }

    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void temporaryWebRoundTrips(GameTestHelper helper) {
        TemporaryWorldSavedData source = new TemporaryWorldSavedData();
        source.registerWeb(new BlockPos(4, 1, 4), 400L);
        CompoundTag tag = new CompoundTag();
        source.saveTag(tag, helper.getLevel().registryAccess());
        TemporaryWorldSavedData loaded = TemporaryWorldSavedData.load(tag,
                helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.webCount(), 1,
                net.minecraft.network.chat.Component.literal("Une toile temporaire doit survivre à la sauvegarde"));
        helper.succeed();
    }

    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void scaffoldRoundTrips(GameTestHelper helper) {
        TemporaryWorldSavedData source = new TemporaryWorldSavedData();
        source.registerScaffold(new BlockPos(4, 1, 4),
                Blocks.COBBLESTONE.defaultBlockState(), 400L, null);
        CompoundTag tag = new CompoundTag();
        source.saveTag(tag, helper.getLevel().registryAccess());
        TemporaryWorldSavedData loaded = TemporaryWorldSavedData.load(tag,
                helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.scaffoldCount(), 1,
                net.minecraft.network.chat.Component.literal("Un échafaudage doit survivre à la sauvegarde"));
        helper.succeed();
    }

    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void expiredWebIsRemoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
        TemporaryWorldSavedData data = new TemporaryWorldSavedData();
        data.registerWeb(pos, 1L);
        data.tickWebs(level, 1_000L);
        helper.assertTrue(level.getBlockState(pos).isAir(),
                net.minecraft.network.chat.Component.literal("Une toile expirée doit être retirée"));
        helper.assertValueEqual(data.webCount(), 0,
                net.minecraft.network.chat.Component.literal("La toile retirée ne doit plus être suivie"));
        helper.succeed();
    }

    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void modifiedScaffoldIsPreserved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState());
        TemporaryWorldSavedData data = new TemporaryWorldSavedData();
        data.registerScaffold(pos, Blocks.COBBLESTONE.defaultBlockState(),
                1L, null);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        data.tickScaffolds(level, 1_000L);
        helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE),
                net.minecraft.network.chat.Component.literal("Un bloc modifié par le joueur doit être conservé"));
        helper.assertValueEqual(data.scaffoldCount(), 0,
                net.minecraft.network.chat.Component.literal("Le bloc modifié ne doit plus être suivi"));
        helper.succeed();
    }


    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void villageProjectRoundTrips(GameTestHelper helper) {
        VillageEvolutionSavedData source = new VillageEvolutionSavedData();
        source.begin(new BlockPos(8, 64, 8), new BlockPos(12, 64, 12),
                "HOUSE", "none", "minecraft:village/plains/houses/a",
                "NONE", 17, UUID.randomUUID());
        CompoundTag tag = new CompoundTag();
        source.saveTag(tag, helper.getLevel().registryAccess());
        VillageEvolutionSavedData loaded = VillageEvolutionSavedData.load(tag,
                helper.getLevel().registryAccess());
        helper.assertTrue(loaded.project() != null,
                net.minecraft.network.chat.Component.literal("Un chantier actif doit survivre au redémarrage"));
        helper.assertValueEqual(loaded.project().index(), 17,
                net.minecraft.network.chat.Component.literal("La progression du chantier doit être conservée"));
        helper.succeed();
    }

    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void ceremonySessionRoundTrips(GameTestHelper helper) {
        UUID builder = UUID.randomUUID();
        UUID farmer = UUID.randomUUID();
        UUID supplier = UUID.randomUUID();
        VillageGolemCeremonySavedData.CeremonySessionRecord record =
                new VillageGolemCeremonySavedData.CeremonySessionRecord(
                        44L, new BlockPos(2, 1, 2),
                        new BlockPos(5, 1, 5), builder, farmer, supplier, null,
                        List.of(), List.of(
                        new VillageGolemCeremonySavedData.DestinationRecord(
                                builder, new Vec3(4.5D, 1.0D, 4.5D))),
                        4, 0, "minecraft:carved_pumpkin",
                        2_000L, 200L, 100L, 0L, 12.0D, 2, false);
        VillageGolemCeremonySavedData source =
                new VillageGolemCeremonySavedData();
        source.putSession(record);
        CompoundTag tag = new CompoundTag();
        source.saveTag(tag, helper.getLevel().registryAccess());
        VillageGolemCeremonySavedData loaded =
                VillageGolemCeremonySavedData.load(tag,
                        helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.activeSessions().size(), 1,
                net.minecraft.network.chat.Component.literal("Une cérémonie active doit survivre au redémarrage"));
        helper.assertValueEqual(loaded.activeSessions().getFirst()
                .ironBlocksPlaced(), 2,
                net.minecraft.network.chat.Component.literal("La progression de la cérémonie doit être conservée"));
        helper.succeed();
    }

    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void portalLinkRoundTrips(GameTestHelper helper) {
        CryingPortalLinkSavedData source = new CryingPortalLinkSavedData();
        source.putLink(new BlockPos(8, 64, 8), Direction.Axis.X,
                new BlockPos(1, 128, 1), Direction.Axis.Z);
        CompoundTag tag = new CompoundTag();
        source.saveTag(tag, helper.getLevel().registryAccess());
        CryingPortalLinkSavedData loaded = CryingPortalLinkSavedData.load(tag,
                helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.linkCount(), 1,
                net.minecraft.network.chat.Component.literal("Un lien de portail doit survivre au redémarrage"));
        helper.succeed();
    }

    @GameTest(batch = "persistence_migration",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void invalidVillagerActivityIsCleaned(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        villager.getPersistentData().putInt(
                "vanillainstincts_cartographer_stage", 99);
        villager.getPersistentData().putLong(
                "vanillainstincts_cartographer_destination", 42L);
        EntityDataMigrationService.migrate(villager, 100L);
        helper.assertFalse(fr.vanillainstincts.persistence.NbtCompat.contains(villager.getPersistentData(),
                "vanillainstincts_cartographer_stage"),
                net.minecraft.network.chat.Component.literal("Une activité incohérente doit être supprimée"));
        helper.succeed();
    }
}
