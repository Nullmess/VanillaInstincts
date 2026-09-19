package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
/** Lecture des mémoires vanilla puis repli local borné. */
public final class VillagePoiScanner {
    private VillagePoiScanner() {
    }

    public static void refresh(Villager villager, VillagerRuntimeState state,
                               ServerLevel level, long gameTime) {
        if (!state.poiScanReady(gameTime)
                || !VanillaInstinctsScheduler.claim(level, villager,
                VillageSocialRules.VILLAGER_POI_SCAN_COST)) {
            return;
        }
        state.setPoiScanCooldown(gameTime,
                VillageSocialRules.VILLAGER_POI_SCAN_INTERVAL_TICKS);

        readMemory(villager, level, MemoryModuleType.HOME)
                .or(() -> scanNearest(level, villager, villager.blockPosition(),
                        VillageSocialRules.VILLAGER_POI_SCAN_RADIUS,
                        block -> block.defaultBlockState().is(BlockTags.BEDS)))
                .ifPresent(pos -> state.cacheHome(pos,
                        gameTime + VillageSocialRules.VILLAGER_POI_CACHE_TICKS));

        VillagerProfession profession = villager.getVillagerData().getProfession();
        readMemory(villager, level, MemoryModuleType.JOB_SITE)
                .or(() -> {
                    Block jobBlock = jobSiteBlock(profession);
                    return jobBlock == null ? Optional.empty()
                            : scanNearest(level, villager, villager.blockPosition(),
                            VillageSocialRules.VILLAGER_POI_SCAN_RADIUS,
                            block -> block == jobBlock);
                })
                .ifPresent(pos -> state.cacheJobSite(pos,
                        gameTime + VillagerGrandMasterIntelligenceController
                                .jobMemoryTicks(villager)));
    }

    public static Block jobSiteBlock(VillagerProfession profession) {
        if (profession == VillagerProfession.FARMER) return Blocks.COMPOSTER;
        if (profession == VillagerProfession.ARMORER) return Blocks.BLAST_FURNACE;
        if (profession == VillagerProfession.BUTCHER) return Blocks.SMOKER;
        if (profession == VillagerProfession.CARTOGRAPHER) return Blocks.CARTOGRAPHY_TABLE;
        if (profession == VillagerProfession.CLERIC) return Blocks.BREWING_STAND;
        if (profession == VillagerProfession.FISHERMAN) return Blocks.BARREL;
        if (profession == VillagerProfession.FLETCHER) return Blocks.FLETCHING_TABLE;
        if (profession == VillagerProfession.LEATHERWORKER) return Blocks.CAULDRON;
        if (profession == VillagerProfession.LIBRARIAN) return Blocks.LECTERN;
        if (profession == VillagerProfession.MASON) return Blocks.STONECUTTER;
        if (profession == VillagerProfession.SHEPHERD) return Blocks.LOOM;
        if (profession == VillagerProfession.TOOLSMITH) return Blocks.SMITHING_TABLE;
        if (profession == VillagerProfession.WEAPONSMITH) return Blocks.GRINDSTONE;
        return null;
    }

    private static Optional<BlockPos> readMemory(Villager villager,
                                                  ServerLevel level,
                                                  MemoryModuleType<GlobalPos> type) {
        return villager.getBrain().getMemory(type)
                .filter(global -> global.dimension().equals(level.dimension()))
                .map(GlobalPos::pos)
                .filter(level::hasChunkAt)
                .map(BlockPos::immutable);
    }

    private static Optional<BlockPos> scanNearest(ServerLevel level,
                                                   Villager villager,
                                                   BlockPos origin,
                                                   int radius,
                                                   java.util.function.Predicate<Block> predicate) {
        int horizontal = VanillaInstinctsScheduler.precisionLimit(level,
                villager, Math.max(1, radius),
                PerformanceRules.MINIMUM_CANDIDATE_LIMIT);
        int vertical = Math.min(4, horizontal);
        return BlockPos.betweenClosedStream(origin.offset(-horizontal, -vertical, -horizontal),
                        origin.offset(horizontal, vertical, horizontal))
                .filter(level::hasChunkAt)
                .filter(pos -> predicate.test(level.getBlockState(pos).getBlock()))
                .min(Comparator.comparingDouble(origin::distSqr))
                .map(BlockPos::immutable);
    }
}
