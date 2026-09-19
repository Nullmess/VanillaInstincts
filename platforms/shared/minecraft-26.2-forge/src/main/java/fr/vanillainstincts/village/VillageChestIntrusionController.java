package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.AABB;

/** Detects actual chest theft inside a village house and sends a witness to a golem. */
public final class VillageChestIntrusionController {
    private static final long WATCH_TIMEOUT_TICKS = 20L * 60L;
    private static final int OPEN_GRACE_TICKS = 8;
    private static final double MAX_WATCH_DISTANCE_SQR = 196.0D;
    private static final Map<ServerLevel, Map<UUID, ChestWatch>> WATCHES =
            new WeakHashMap<>();

    private VillageChestIntrusionController() {
    }

    /** Starts watching a chest. The first open-menu tick establishes the baseline. */
    public static void beginContainerWatch(ServerLevel level, BlockPos container,
                                           ServerPlayer player, long gameTime) {
        if (level == null || container == null || player == null
                || player.isCreative() || player.isSpectator()
                || !(level.getBlockState(container).getBlock() instanceof ChestBlock)) {
            return;
        }
        int baseline = chestItemCount(level, container);
        if (baseline < 0) return;
        synchronized (WATCHES) {
            WATCHES.computeIfAbsent(level, ignored -> new HashMap<>())
                    .put(player.getUUID(), new ChestWatch(container.immutable(), baseline,
                            gameTime, gameTime));
        }
    }

    /** Called every server tick while the feature is enabled. */
    public static void tickPlayer(ServerLevel level, ServerPlayer player,
                                  long gameTime) {
        ChestWatch watch = watch(level, player);
        if (watch == null) return;
        if (player.isCreative() || player.isSpectator()
                || gameTime - watch.startedAt() > WATCH_TIMEOUT_TICKS
                || player.blockPosition().distSqr(watch.container())
                > MAX_WATCH_DISTANCE_SQR) {
            clearPlayer(level, player);
            return;
        }

        // The right-click event runs before the chest menu is installed. Give it a
        // short grace period; after that, a closed menu ends the watch.
        if (player.containerMenu == player.inventoryMenu) {
            if (gameTime - watch.lastMenuOpenAt() > OPEN_GRACE_TICKS) {
                clearPlayer(level, player);
            }
            return;
        }

        int current = chestItemCount(level, watch.container());
        if (current < 0) {
            clearPlayer(level, player);
            return;
        }
        if (watch.baselineCount() < 0) {
            replaceWatch(level, player, new ChestWatch(watch.container(), current,
                    watch.startedAt(), gameTime));
            return;
        }

        if (current < watch.baselineCount()) {
            reportTheft(level, watch.container(), player, gameTime);
            clearPlayer(level, player);
            return;
        }

        // Depositing items is not theft. Raise the baseline so a later withdrawal
        // during the same open session is still detected correctly.
        if (current > watch.baselineCount()) {
            replaceWatch(level, player, new ChestWatch(watch.container(), current,
                    watch.startedAt(), gameTime));
        }
    }

    public static void clearPlayer(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) return;
        synchronized (WATCHES) {
            Map<UUID, ChestWatch> levelWatches = WATCHES.get(level);
            if (levelWatches == null) return;
            levelWatches.remove(player.getUUID());
            if (levelWatches.isEmpty()) WATCHES.remove(level);
        }
    }

    public static void clearLevel(ServerLevel level) {
        if (level == null) return;
        synchronized (WATCHES) {
            WATCHES.remove(level);
        }
    }

    /** Compatibility entry point: now represents a confirmed item-removal theft. */
    public static int onContainerOpened(ServerLevel level, BlockPos container,
                                        ServerPlayer player, long gameTime) {
        return reportTheft(level, container, player, gameTime);
    }

    private static int reportTheft(ServerLevel level, BlockPos container,
                                   ServerPlayer player, long gameTime) {
        if (level == null || container == null || player == null
                || player.isCreative() || player.isSpectator()) {
            return 0;
        }
        AABB area = new AABB(container.getX(), container.getY(),
                container.getZ(), container.getX() + 1.0D,
                container.getY() + 1.0D, container.getZ() + 1.0D).inflate(
                VillageSocialRules.VILLAGE_CHEST_WITNESS_RADIUS);
        int alerted = 0;
        for (Villager witness : level.getEntitiesOfClass(Villager.class, area,
                Villager::isAlive)) {
            VillagerRuntimeState state = VillagerStateStore.stateFor(witness);
            VillagePoiScanner.refresh(witness, state, level, gameTime);
            if (!isHouseWitness(witness, state, level, container, gameTime)) {
                VillagerStateStore.save(witness, state);
                continue;
            }

            VillagerEconomyController.record(witness, player,
                    VillagerEconomyController.Incident.THEFT, gameTime);
            VillageThreatRegistry.ThreatSnapshot threat =
                    VillageThreatRegistry.reportVillagerAttack(witness, level,
                            player, 0.5F, gameTime);
            if (threat == null) {
                VillagerStateStore.save(witness, state);
                continue;
            }
            if (witness.isBaby()) {
                ChildVillageAlertController.begin(witness, threat, gameTime);
            } else {
                // The report controller pathfinds this witness to the nearest golem
                // and hands the threat over only when the villager reaches it.
                VillagerGolemReportController.beginReport(witness, state,
                        threat, gameTime);
            }
            VillagerSafetyController.rememberDanger(witness, level, container);
            VillagerStateStore.save(witness, state);
            alerted++;
        }
        return alerted;
    }

    public static boolean isHouseWitness(Villager villager,
                                         VillagerRuntimeState state,
                                         ServerLevel level,
                                         BlockPos container,
                                         long gameTime) {
        BlockPos home = state.home(gameTime);
        boolean nearOwnHome = home != null
                && home.distSqr(container) <= 144.0D;
        boolean indoors = !level.canSeeSky(villager.blockPosition())
                && villager.blockPosition().distSqr(container) <= 64.0D;
        return isHouseContext(nearOwnHome, indoors);
    }

    public static boolean isHouseContext(boolean nearOwnHome,
                                         boolean indoors) {
        return nearOwnHome || indoors;
    }

    private static ChestWatch watch(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) return null;
        synchronized (WATCHES) {
            Map<UUID, ChestWatch> levelWatches = WATCHES.get(level);
            return levelWatches == null ? null : levelWatches.get(player.getUUID());
        }
    }

    private static void replaceWatch(ServerLevel level, ServerPlayer player,
                                     ChestWatch watch) {
        synchronized (WATCHES) {
            WATCHES.computeIfAbsent(level, ignored -> new HashMap<>())
                    .put(player.getUUID(), watch);
        }
    }

    private static int chestItemCount(ServerLevel level, BlockPos container) {
        int total = 0;
        boolean found = false;
        int[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            BlockPos scan = container.offset(offset[0], 0, offset[1]);
            if (!(level.getBlockState(scan).getBlock() instanceof ChestBlock)) {
                continue;
            }
            if (!(level.getBlockEntity(scan) instanceof Container inventory)) {
                continue;
            }
            found = true;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty()) total += stack.getCount();
            }
        }
        return found ? total : -1;
    }

    private record ChestWatch(BlockPos container, int baselineCount,
                              long startedAt, long lastMenuOpenAt) {
    }
}
