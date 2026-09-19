package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115TagCompat;

import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.tags.BlockTags;
import net.minecraft.entity.ai.brain.memory.MemoryModuleType;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
/** Lecture des mémoires vanilla puis repli local borné. */
public final class VillagePoiScanner {
    private VillagePoiScanner() {
    }

    public static void refresh(VillagerEntity villager, VillagerRuntimeState state,
                               ServerWorld level, long gameTime) {
        if (!state.poiScanReady(gameTime)
                || !VanillaInstinctsScheduler.claim(level, villager,
                VillageSocialRules.VILLAGER_POI_SCAN_COST)) {
            return;
        }
        state.setPoiScanCooldown(gameTime,
                VillageSocialRules.VILLAGER_POI_SCAN_INTERVAL_TICKS);

        Optional<BlockPos> home = readMemory(villager, level, MemoryModuleType.HOME);
        if (!home.isPresent()) {
            home = scanNearest(level, villager, entityBlockPos(villager),
                    VillageSocialRules.VILLAGER_POI_SCAN_RADIUS,
                    block -> Minecraft115TagCompat.blockStateIs(block.defaultBlockState(), BlockTags.BEDS));
        }
        if (home.isPresent()) {
            state.cacheHome(home.get(),
                    gameTime + VillageSocialRules.VILLAGER_POI_CACHE_TICKS);
        }

        VillagerProfession profession = villager.getVillagerData().getProfession();
        Optional<BlockPos> jobSite = readMemory(villager, level, MemoryModuleType.JOB_SITE);
        if (!jobSite.isPresent()) {
            Block jobBlock = jobSiteBlock(profession);
            if (jobBlock != null) {
                jobSite = scanNearest(level, villager, entityBlockPos(villager),
                        VillageSocialRules.VILLAGER_POI_SCAN_RADIUS,
                        block -> block == jobBlock);
            }
        }
        if (jobSite.isPresent()) {
            state.cacheJobSite(jobSite.get(),
                    gameTime + VillagerGrandMasterIntelligenceController
                            .jobMemoryTicks(villager));
        }
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

    private static Optional<BlockPos> readMemory(VillagerEntity villager,
                                                  ServerWorld level,
                                                  MemoryModuleType<GlobalPos> type) {
        return villager.getBrain().getMemory(type)
                .filter(global -> global.dimension().equals(level.dimension.getType()))
                .map(GlobalPos::pos)
                .filter(level::hasChunkAt)
                .map(BlockPos::immutable);
    }

    private static Optional<BlockPos> scanNearest(ServerWorld level,
                                                   VillagerEntity villager,
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
