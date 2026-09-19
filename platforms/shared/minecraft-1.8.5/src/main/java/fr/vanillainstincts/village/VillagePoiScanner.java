package fr.vanillainstincts.village;

import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;

import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;

/** Bounded POI scan for the pre-Brain/pre-POI villager implementation. */
public final class VillagePoiScanner {
    private VillagePoiScanner() {}

    public static void refresh(EntityVillager villager, VillagerRuntimeState state,
                               WorldServer level, long gameTime) {
        if (!state.poiScanReady(gameTime)
                || !VanillaInstinctsScheduler.claim(level, villager,
                VillageSocialRules.VILLAGER_POI_SCAN_COST)) return;
        state.setPoiScanCooldown(gameTime,
                VillageSocialRules.VILLAGER_POI_SCAN_INTERVAL_TICKS);

        BlockPos origin = entityBlockPos(villager);
        Optional<BlockPos> home = scanNearest(level, villager, origin,
                VillageSocialRules.VILLAGER_POI_SCAN_RADIUS,
                block -> block instanceof BlockBed);
        if (home.isPresent()) {
            state.cacheHome(home.get(),
                    gameTime + VillageSocialRules.VILLAGER_POI_CACHE_TICKS);
        }

        Block workstation = jobSiteBlock(LegacyVillagerProfession.of(villager));
        if (workstation != null) {
            Optional<BlockPos> job = scanNearest(level, villager, origin,
                    VillageSocialRules.VILLAGER_POI_SCAN_RADIUS,
                    block -> block == workstation);
            if (job.isPresent()) {
                state.cacheJobSite(job.get(),
                        gameTime + VillagerGrandMasterIntelligenceController
                                .jobMemoryTicks(villager));
            }
        }
    }

    /** 1.12 workstation proxies for professions whose modern POI did not exist yet. */
    public static Block jobSiteBlock(LegacyVillagerProfession profession) {
        if (profession == null) return null;
        switch (profession) {
            case FARMER: return Blocks.farmland;
            case ARMORER:
            case TOOLSMITH:
            case WEAPONSMITH: return Blocks.anvil;
            case BUTCHER: return Blocks.furnace;
            case CARTOGRAPHER:
            case LIBRARIAN: return Blocks.bookshelf;
            case CLERIC: return Blocks.brewing_stand;
            case FLETCHER: return Blocks.crafting_table;
            case LEATHERWORKER: return Blocks.cauldron;
            case MASON: return Blocks.stonebrick;
            case SHEPHERD: return Blocks.wool;
            default: return null;
        }
    }

    private static Optional<BlockPos> scanNearest(WorldServer level,
                                                   EntityVillager villager,
                                                   BlockPos origin, int radius,
                                                   Predicate<Block> predicate) {
        int horizontal = VanillaInstinctsScheduler.precisionLimit(level,
                villager, Math.max(1, radius),
                PerformanceRules.MINIMUM_CANDIDATE_LIMIT);
        int vertical = Math.min(4, horizontal);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -horizontal; dx <= horizontal; dx++) {
            for (int dy = -vertical; dy <= vertical; dy++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    BlockPos pos =fr.vanillainstincts.compat.Minecraft112Compat.add(origin, dx, dy, dz);
                    if (!level.isBlockLoaded(pos)) continue;
                    if (!predicate.test(level.getBlockState(pos).getBlock())) continue;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = immutableBlockPos(pos);
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
