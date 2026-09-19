package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.rules.PillagerRules;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.List;
import fr.vanillainstincts.village.RecoveredTradeController;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
/**
 * Après la mort d'un joueur près d'un avant-poste, un seul pillard prend en
 * charge la mission complète. Il rejoint le point de mort, récupère toutes les
 * piles simultanément, effectue un unique trajet jusqu'au coffre supérieur et
 * y dépose le lot entier.
 *
 * <p>Les piles restent des {@link ItemEntity} réelles : leurs composants ne
 * sont jamais copiés ni simplifiés. Le pillard mémorise seulement leurs UUID,
 * ce qui rend la mission persistante et empêche plusieurs pillards de se
 * partager le même inventaire.</p>
 */
public final class PillagerOutpostLootController {
    private static final String LOOT_MISSION = "vanillainstincts_outpost_loot_mission";
    private static final String LOOT_CHEST = "vanillainstincts_outpost_loot_chest";
    private static final String LOOT_DEATH = "vanillainstincts_outpost_loot_death";
    private static final String LOOT_CARRIER = "vanillainstincts_outpost_loot_carrier";
    private static final String LOOT_CLAIMED_AT = "vanillainstincts_outpost_loot_claimed_at";

    private static final String PILLAGER_ITEM = "vanillainstincts_outpost_carried_item";
    private static final String PILLAGER_ITEM_PREFIX =
            "vanillainstincts_outpost_carried_item_";
    private static final String PILLAGER_ITEM_COUNT =
            "vanillainstincts_outpost_carried_item_count";
    private static final String PILLAGER_MISSION = "vanillainstincts_outpost_mission";
    private static final String PILLAGER_CHEST = "vanillainstincts_outpost_chest";
    private static final String PILLAGER_DEATH = "vanillainstincts_outpost_death";
    private static final String PILLAGER_HOME = "vanillainstincts_outpost_home";
    private static final String PILLAGER_CARRYING = "vanillainstincts_outpost_carrying";
    private static final String PILLAGER_RETURNING = "vanillainstincts_outpost_returning";
    private static final String PILLAGER_STARTED_AT =
            "vanillainstincts_outpost_started_at";

    private PillagerOutpostLootController() {
    }

    /** Marque les objets puis confie la totalité de la mort à un seul pillard. */
    public static int onPlayerDrops(Player player, ServerLevel level,
                                    Entity killer,
                                    Iterable<ItemEntity> drops,
                                    long gameTime) {
        if (player == null || level == null || drops == null) return 0;
        java.util.List<ItemEntity> valid = new java.util.ArrayList<>();
        for (ItemEntity drop : drops) {
            if (drop == null || !drop.isAlive() || drop.getItem().isEmpty()) {
                continue;
            }
            RecoveredTradeController.markPlayerDeathDrop(drop, player);
            valid.add(drop);
        }
        if (valid.isEmpty()) return 0;
        Pillager preferred = killer instanceof Pillager pillager
                ? pillager : null;
        BlockPos death = player.blockPosition();
        BlockPos chest = findOutpostChest(level, death);
        if (chest == null) return valid.size();
        Pillager carrier = selectCarrier(level, preferred, death);
        if (carrier == null) return valid.size();

        UUID mission = UUID.randomUUID();
        beginMission(carrier, mission, chest, death, valid, gameTime);
        return valid.size();
    }

    private static Pillager selectCarrier(ServerLevel level,
                                          Pillager preferred,
                                          BlockPos death) {
        if (availableForMission(preferred)) return preferred;
        AABB area = new AABB(death.getX(), death.getY(), death.getZ(),
                death.getX() + 1.0D, death.getY() + 1.0D,
                death.getZ() + 1.0D).inflate(
                PillagerRules.OUTPOST_LOOT_SCAN_RADIUS,
                PillagerRules.OUTPOST_LOOT_VERTICAL_RADIUS,
                PillagerRules.OUTPOST_LOOT_SCAN_RADIUS);
        return level.getEntitiesOfClass(Pillager.class, area,
                        PillagerOutpostLootController::availableForMission)
                .stream()
                .min(Comparator.comparingDouble(pillager ->
                        pillager.blockPosition().distSqr(death)))
                .orElse(null);
    }

    public static boolean availableForMission(Pillager pillager) {
        return pillager != null && pillager.isAlive()
                && !fr.vanillainstincts.persistence.NbtCompat.hasUuid(pillager.getPersistentData(), PILLAGER_MISSION)
                && !fr.vanillainstincts.persistence.NbtCompat.getBoolean(pillager.getPersistentData(), PILLAGER_RETURNING);
    }

    private static void beginMission(Pillager pillager, UUID mission,
                                     BlockPos chest, BlockPos death,
                                     java.util.List<ItemEntity> items,
                                     long gameTime) {
        clearMissionKeys(pillager);
        pillager.getPersistentData().putLong(PILLAGER_HOME,
                pillager.blockPosition().asLong());
        fr.vanillainstincts.persistence.NbtCompat.putUuid(pillager.getPersistentData(), PILLAGER_MISSION, mission);
        pillager.getPersistentData().putLong(PILLAGER_CHEST, chest.asLong());
        pillager.getPersistentData().putLong(PILLAGER_DEATH, death.asLong());
        pillager.getPersistentData().putBoolean(PILLAGER_CARRYING, false);
        pillager.getPersistentData().putBoolean(PILLAGER_RETURNING, false);
        pillager.getPersistentData().putLong(PILLAGER_STARTED_AT, gameTime);
        pillager.getPersistentData().putInt(PILLAGER_ITEM_COUNT, items.size());
        for (int index = 0; index < items.size(); index++) {
            ItemEntity item = items.get(index);
            fr.vanillainstincts.persistence.NbtCompat.putUuid(pillager.getPersistentData(), PILLAGER_ITEM_PREFIX + index, item.getUUID());
            reserve(item, mission, pillager.getUUID(), chest, death, gameTime);
        }
    }

    /** Priorité complète tant que le pillard collecte, transporte ou revient. */
    public static boolean maintain(Pillager pillager, ServerLevel level,
                                   long gameTime) {
        if (pillager == null || level == null || !pillager.isAlive()) {
            return false;
        }
        migrateLegacyAssignment(pillager, level);
        if (fr.vanillainstincts.persistence.NbtCompat.getBoolean(pillager.getPersistentData(), PILLAGER_RETURNING)) {
            return returnHome(pillager);
        }
        if (!fr.vanillainstincts.persistence.NbtCompat.hasUuid(pillager.getPersistentData(), PILLAGER_MISSION)) {
            return false;
        }

        pillager.setTarget(null);
        java.util.List<ItemEntity> items = resolveMissionItems(pillager, level);
        BlockPos death = BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(pillager.getPersistentData(), PILLAGER_DEATH));
        int expectedCount = Math.max(0, fr.vanillainstincts.persistence.NbtCompat.getInt(pillager.getPersistentData(), PILLAGER_ITEM_COUNT));
        long startedAt = pillager.getPersistentData().contains(
                PILLAGER_STARTED_AT)
                ? fr.vanillainstincts.persistence.NbtCompat.getLong(pillager.getPersistentData(), PILLAGER_STARTED_AT)
                : gameTime;

        // LivingDropsEvent peut précéder de très peu l'ajout définitif des
        // entités dans le niveau. Le porteur attend donc que le lot complet soit
        // visible au lieu de partir avec une partie des objets.
        if (items.size() < expectedCount) {
            pillager.getNavigation().stop();
            if (!assignmentExpired(startedAt, gameTime)) return true;
            releaseMissionItems(pillager, level);
            clearTask(pillager);
            return false;
        }

        boolean carrying = fr.vanillainstincts.persistence.NbtCompat.getBoolean(pillager.getPersistentData(), PILLAGER_CARRYING);

        if (!carrying) {
            if (pillager.blockPosition().distSqr(death)
                    > PillagerRules.OUTPOST_LOOT_PICKUP_DISTANCE_SQR) {
                pillager.getNavigation().moveTo(death.getX() + 0.5D,
                        death.getY(), death.getZ() + 0.5D,
                        PillagerRules.OUTPOST_LOOT_SPEED);
                return true;
            }
            // Toutes les piles sont prises au même tick : aucun second pillard
            // ne peut s'intercaler entre deux ramassages.
            pillager.getPersistentData().putBoolean(PILLAGER_CARRYING, true);
            for (int index = 0; index < items.size(); index++) {
                prepareCarriedItem(pillager, items.get(index), index);
            }
            carrying = true;
        }

        if (items.isEmpty()) {
            finishMission(pillager);
            return returnHome(pillager);
        }
        for (int index = 0; index < items.size(); index++) {
            carryBesidePillager(pillager, items.get(index), index,
                    items.size());
        }

        BlockPos chest = BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(pillager.getPersistentData(), PILLAGER_CHEST));
        BlockPos depositTarget = nextStorageTarget(level, chest,
                items.get(0).getItem());
        if (pillager.blockPosition().distSqr(depositTarget)
                > PillagerRules.OUTPOST_LOOT_CHEST_REACH_SQR) {
            pillager.getNavigation().moveTo(depositTarget.getX() + 0.5D,
                    depositTarget.getY() + 1.0D,
                    depositTarget.getZ() + 0.5D,
                    PillagerRules.OUTPOST_LOOT_SPEED);
            return true;
        }

        if (!level.getBlockState(depositTarget).is(Blocks.CHEST)) {
            pillager.swing(InteractionHand.MAIN_HAND);
        }
        for (ItemEntity item : items) {
            deposit(level, pillager, chest, item);
        }
        finishMission(pillager);
        return returnHome(pillager);
    }

    /** Libère sans perte le lot si le pillard porteur meurt. */
    public static void onCarrierDeath(Pillager pillager, ServerLevel level) {
        if (pillager == null || level == null) return;
        releaseMissionItems(pillager, level);
        clearTask(pillager);
    }

    public static boolean isReservedLoot(ItemEntity item) {
        return item != null && fr.vanillainstincts.persistence.NbtCompat.hasUuid(item.getPersistentData(), LOOT_MISSION);
    }

    private static void reserve(ItemEntity item, UUID mission, UUID carrier,
                                BlockPos chest, BlockPos death,
                                long gameTime) {
        fr.vanillainstincts.persistence.NbtCompat.putUuid(item.getPersistentData(), LOOT_MISSION, mission);
        item.getPersistentData().putLong(LOOT_CHEST, chest.asLong());
        item.getPersistentData().putLong(LOOT_DEATH, death.asLong());
        item.getPersistentData().putLong(LOOT_CLAIMED_AT, gameTime);
        fr.vanillainstincts.persistence.NbtCompat.putUuid(item.getPersistentData(), LOOT_CARRIER, carrier);
        item.setPickUpDelay(32_767);
        item.setUnlimitedLifetime();
        item.setInvulnerable(true);
    }

    private static java.util.List<ItemEntity> resolveMissionItems(
            Pillager pillager, ServerLevel level) {
        java.util.List<ItemEntity> result = new java.util.ArrayList<>();
        UUID mission = fr.vanillainstincts.persistence.NbtCompat.hasUuid(pillager.getPersistentData(), PILLAGER_MISSION)
                ? fr.vanillainstincts.persistence.NbtCompat.getUuid(pillager.getPersistentData(), PILLAGER_MISSION) : null;
        int count = Math.max(0, fr.vanillainstincts.persistence.NbtCompat.getInt(pillager.getPersistentData(), PILLAGER_ITEM_COUNT));
        for (int index = 0; index < count; index++) {
            String key = PILLAGER_ITEM_PREFIX + index;
            if (!fr.vanillainstincts.persistence.NbtCompat.hasUuid(pillager.getPersistentData(), key)) continue;
            Entity entity = level.getEntity(fr.vanillainstincts.persistence.NbtCompat.getUuid(pillager.getPersistentData(), key));
            if (entity instanceof ItemEntity item && item.isAlive()
                    && isReservedLoot(item)
                    && mission != null && mission.equals(
                    fr.vanillainstincts.persistence.NbtCompat.getUuid(
                            item.getPersistentData(), LOOT_MISSION))
                    && fr.vanillainstincts.persistence.NbtCompat.hasUuid(item.getPersistentData(), LOOT_CARRIER)
                    && pillager.getUUID().equals(fr.vanillainstincts.persistence.NbtCompat.getUuid(item.getPersistentData(), LOOT_CARRIER))) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Libère toutes les piles chargées correspondant à la mission. La recherche
     * par zone complète la résolution directe par UUID et couvre notamment le
     * très court intervalle où les drops viennent d'être créés.
     */
    private static void releaseMissionItems(Pillager pillager,
                                            ServerLevel level) {
        if (!fr.vanillainstincts.persistence.NbtCompat.hasUuid(pillager.getPersistentData(), PILLAGER_MISSION)) return;
        UUID mission = fr.vanillainstincts.persistence.NbtCompat.getUuid(pillager.getPersistentData(), PILLAGER_MISSION);
        Set<UUID> released = new java.util.HashSet<>();
        for (ItemEntity item : resolveMissionItems(pillager, level)) {
            if (released.add(item.getUUID())) {
                release(item, pillager.blockPosition());
            }
        }
        BlockPos death = pillager.getPersistentData().contains(PILLAGER_DEATH)
                ? BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(pillager.getPersistentData(), PILLAGER_DEATH)) : pillager.blockPosition();
        double radius = PillagerRules.OUTPOST_LOOT_SCAN_RADIUS;
        AABB area = new AABB(death.getX(), death.getY(), death.getZ(),
                death.getX() + 1.0D, death.getY() + 1.0D,
                death.getZ() + 1.0D).inflate(radius,
                PillagerRules.OUTPOST_LOOT_VERTICAL_RADIUS, radius);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area,
                candidate -> candidate.isAlive()
                        && fr.vanillainstincts.persistence.NbtCompat.hasUuid(candidate.getPersistentData(), LOOT_MISSION)
                        && mission.equals(fr.vanillainstincts.persistence.NbtCompat.getUuid(candidate.getPersistentData(), LOOT_MISSION)))) {
            if (released.add(item.getUUID())) release(item, death);
        }
    }

    private static void migrateLegacyAssignment(Pillager pillager,
                                                 ServerLevel level) {
        if (fr.vanillainstincts.persistence.NbtCompat.getInt(pillager.getPersistentData(), PILLAGER_ITEM_COUNT) > 0
                || !fr.vanillainstincts.persistence.NbtCompat.hasUuid(pillager.getPersistentData(), PILLAGER_ITEM)) {
            return;
        }
        UUID itemId = fr.vanillainstincts.persistence.NbtCompat.getUuid(pillager.getPersistentData(), PILLAGER_ITEM);
        Entity entity = level.getEntity(itemId);
        if (!(entity instanceof ItemEntity item) || !item.isAlive()) return;
        pillager.getPersistentData().putInt(PILLAGER_ITEM_COUNT, 1);
        fr.vanillainstincts.persistence.NbtCompat.putUuid(pillager.getPersistentData(), PILLAGER_ITEM_PREFIX + 0, itemId);
        if (!pillager.getPersistentData().contains(PILLAGER_DEATH)) {
            long death = item.getPersistentData().contains(LOOT_DEATH)
                    ? fr.vanillainstincts.persistence.NbtCompat.getLong(item.getPersistentData(), LOOT_DEATH)
                    : item.blockPosition().asLong();
            pillager.getPersistentData().putLong(PILLAGER_DEATH, death);
        }
    }

    private static void prepareCarriedItem(Pillager pillager, ItemEntity item,
                                           int index) {
        item.setNoGravity(true);
        item.setDeltaMovement(0.0D, 0.0D, 0.0D);
        carryBesidePillager(pillager, item, index, 1);
    }

    private static void carryBesidePillager(Pillager pillager,
                                            ItemEntity item, int index,
                                            int total) {
        double angle = total <= 1 ? 0.0D
                : Math.PI * 2.0D * index / total;
        double radius = total <= 1 ? 0.0D : 0.45D;
        item.setPos(pillager.getX() + Math.cos(angle) * radius,
                pillager.getY() + 1.05D + (index % 3) * 0.08D,
                pillager.getZ() + Math.sin(angle) * radius);
        item.setDeltaMovement(0.0D, 0.0D, 0.0D);
        item.setNoGravity(true);
        item.setPickUpDelay(32_767);
    }

    private static void deposit(ServerLevel level, Pillager pillager,
                                BlockPos chest, ItemEntity item) {
        ItemStack remainder = insertIntoOutpostStorage(level, pillager, chest,
                item.getItem());
        if (remainder.isEmpty()) {
            item.discard();
            return;
        }
        item.setItem(remainder);
        release(item, chest.above());
    }

    private static void release(ItemEntity item, BlockPos position) {
        item.getPersistentData().remove(LOOT_MISSION);
        item.getPersistentData().remove(LOOT_CARRIER);
        item.getPersistentData().remove(LOOT_CHEST);
        item.getPersistentData().remove(LOOT_DEATH);
        item.getPersistentData().remove(LOOT_CLAIMED_AT);
        item.setNoGravity(false);
        item.setInvulnerable(false);
        item.setNoPickUpDelay();
        item.setPos(position.getX() + 0.5D, position.getY() + 0.2D,
                position.getZ() + 0.5D);
    }

    private static void finishMission(Pillager pillager) {
        clearMissionKeys(pillager);
        pillager.getPersistentData().putBoolean(PILLAGER_RETURNING, true);
    }

    /**
     * Insère une pile dans le réseau de stockage de l'avant-poste. Le coffre
     * principal est d'abord utilisé, puis agrandi en double coffre. Lorsque
     * cette paire est pleine, des groupes simples sont créés à partir de deux
     * blocs du coffre principal et chacun est agrandi seulement si nécessaire.
     */
    public static ItemStack insertIntoOutpostStorage(ServerLevel level,
                                                     BlockPos chest,
                                                     ItemStack input) {
        return insertIntoOutpostStorage(level, null, chest, input);
    }

    private static ItemStack insertIntoOutpostStorage(ServerLevel level,
                                                       Entity actor,
                                                       BlockPos chest,
                                                       ItemStack input) {
        if (level == null || chest == null || input == null
                || input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = input.copy();
        Set<BlockPos> visited = new LinkedHashSet<>();

        remainder = insertChestGroup(level, actor, chest, remainder, visited);
        if (remainder.isEmpty()) return ItemStack.EMPTY;

        BlockPos rootPartner = tryExpandChest(level, actor, chest, null);
        if (rootPartner != null) {
            remainder = insertChestAt(level, actor, rootPartner, remainder,
                    visited);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }

        for (int index = 0; index < PillagerRules.OUTPOST_STORAGE_MAX_GROUPS
                && !remainder.isEmpty(); index++) {
            BlockPos anchor = storageAnchor(chest, index);
            Direction outward = storageOutwardDirection(chest, anchor);
            if (level.getBlockState(anchor).is(Blocks.CHEST)) {
                remainder = insertChestGroup(level, actor, anchor, remainder,
                        visited);
                if (remainder.isEmpty()) break;
                BlockPos partner = tryExpandChest(level, actor, anchor,
                        outward);
                if (partner != null) {
                    remainder = insertChestAt(level, actor, partner,
                            remainder, visited);
                }
                continue;
            }
            if (!canPlaceSingleChest(level, anchor)) continue;
            if (!placeSingleChest(level, actor, anchor,
                    facingForExtension(outward))) {
                continue;
            }
            remainder = insertChestAt(level, actor, anchor, remainder,
                    visited);
        }
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }

    /** Cible visible choisie avant que le pillard dépose ou pose un coffre. */
    public static BlockPos nextStorageTarget(ServerLevel level,
                                             BlockPos chest,
                                             ItemStack input) {
        if (level == null || chest == null || input == null
                || input.isEmpty()) return chest;
        BlockPos accepting = firstAcceptingChest(level, chest, input);
        if (accepting != null) return accepting;

        BlockPos rootExpansion = expansionPosition(level, chest, null);
        if (rootExpansion != null) return rootExpansion;

        for (int index = 0; index < PillagerRules.OUTPOST_STORAGE_MAX_GROUPS;
             index++) {
            BlockPos anchor = storageAnchor(chest, index);
            Direction outward = storageOutwardDirection(chest, anchor);
            if (level.getBlockState(anchor).is(Blocks.CHEST)) {
                accepting = firstAcceptingChest(level, anchor, input);
                if (accepting != null) return accepting;
                BlockPos expansion = expansionPosition(level, anchor, outward);
                if (expansion != null) return expansion;
                continue;
            }
            if (canPlaceSingleChest(level, anchor)) return anchor;
        }
        return chest;
    }

    private static ItemStack insertChestGroup(ServerLevel level,
                                              Entity actor,
                                              BlockPos center,
                                              ItemStack input,
                                              Set<BlockPos> visited) {
        ItemStack remainder = insertChestAt(level, actor, center, input,
                visited);
        if (remainder.isEmpty()) return ItemStack.EMPTY;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = center.relative(direction);
            if (!level.getBlockState(neighbor).is(Blocks.CHEST)) continue;
            remainder = insertChestAt(level, actor, neighbor, remainder,
                    visited);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        return remainder;
    }

    private static ItemStack insertChestAt(ServerLevel level,
                                           Entity actor,
                                           BlockPos position,
                                           ItemStack input,
                                           Set<BlockPos> visited) {
        if (input.isEmpty() || !visited.add(position.immutable())) {
            return input;
        }
        if (!WorldPermissionService.canMutateContainer(level, actor,
                position)) {
            return input;
        }
        return insert(level.getBlockEntity(position), input);
    }

    private static BlockPos firstAcceptingChest(ServerLevel level,
                                                BlockPos center,
                                                ItemStack input) {
        if (chestCanAccept(level, center, input)) return center;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = center.relative(direction);
            if (chestCanAccept(level, neighbor, input)) return neighbor;
        }
        return null;
    }

    private static boolean chestCanAccept(ServerLevel level, BlockPos position,
                                          ItemStack input) {
        BlockEntity entity = level.getBlockEntity(position);
        return entity instanceof Container container
                && canAccept(container, input);
    }

    /** Fonction pure réutilisée par les GameTests. */
    public static boolean canAccept(Container container, ItemStack input) {
        if (container == null || input == null || input.isEmpty()) return false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                if (container.canPlaceItem(slot, input)) return true;
                continue;
            }
            if (ItemStack.isSameItemSameComponents(existing, input)
                    && existing.getCount() < Math.min(existing.getMaxStackSize(),
                    container.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos tryExpandChest(ServerLevel level, Entity actor,
                                           BlockPos base,
                                           Direction preferred) {
        BlockPos partner = expansionPosition(level, base, preferred);
        if (partner == null) return null;
        BlockState baseState = level.getBlockState(base);
        Direction facing = baseState.getValue(ChestBlock.FACING);
        Direction extension = directionBetween(base, partner);
        ChestType baseType = extension == facing.getClockWise()
                ? ChestType.LEFT : ChestType.RIGHT;
        ChestType partnerType = baseType == ChestType.LEFT
                ? ChestType.RIGHT : ChestType.LEFT;
        BlockState partnerState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, facing)
                .setValue(ChestBlock.TYPE, partnerType);
        List<WorldPermissionService.BlockChange> changes = List.of(
                new WorldPermissionService.BlockChange(base,
                        baseState.setValue(ChestBlock.TYPE, baseType),
                        Block.UPDATE_ALL),
                new WorldPermissionService.BlockChange(partner, partnerState,
                        Block.UPDATE_ALL));
        return WorldPermissionService.setBlocksAtomically(level, actor,
                changes, WorldActionType.PLACE_BLOCK) ? partner : null;
    }

    private static BlockPos expansionPosition(ServerLevel level, BlockPos base,
                                              Direction preferred) {
        BlockState state = level.getBlockState(base);
        if (!state.is(Blocks.CHEST)
                || state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return null;
        }
        Direction facing = state.getValue(ChestBlock.FACING);
        Direction[] candidates = preferred == null
                ? new Direction[]{facing.getClockWise(),
                facing.getCounterClockWise()}
                : new Direction[]{preferred};
        for (Direction extension : candidates) {
            if (extension.getAxis() == Direction.Axis.Y
                    || extension.getAxis() == facing.getAxis()) continue;
            BlockPos partner = base.relative(extension);
            if (canPlacePartnerChest(level, partner, base)) return partner;
        }
        return null;
    }

    private static boolean placeSingleChest(ServerLevel level, Entity actor,
                                            BlockPos position,
                                            Direction facing) {
        BlockState state = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, facing)
                .setValue(ChestBlock.TYPE, ChestType.SINGLE);
        return WorldPermissionService.setBlock(level, actor, position, state,
                Block.UPDATE_ALL, WorldActionType.PLACE_BLOCK);
    }

    private static boolean canPlaceSingleChest(ServerLevel level,
                                               BlockPos position) {
        if (!isSafeChestSpace(level, position)) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(position.relative(direction))
                    .is(Blocks.CHEST)) return false;
        }
        return true;
    }

    private static boolean canPlacePartnerChest(ServerLevel level,
                                                BlockPos position,
                                                BlockPos base) {
        if (!isSafeChestSpace(level, position)) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = position.relative(direction);
            if (neighbor.equals(base)) continue;
            if (level.getBlockState(neighbor).is(Blocks.CHEST)) return false;
        }
        return true;
    }

    private static boolean isSafeChestSpace(ServerLevel level,
                                            BlockPos position) {
        return level.hasChunkAt(position)
                && level.getBlockState(position).isAir()
                && level.getBlockState(position.above()).isAir()
                && level.getBlockState(position.below()).isFaceSturdy(level,
                position.below(), Direction.UP);
    }

    /** Position déterministe du groupe, le premier étant à deux blocs. */
    public static BlockPos storageAnchor(BlockPos root, int index) {
        if (root == null || index < 0) return root;
        int ring = index / 8;
        int distance = 2 + ring * 3;
        return switch (index % 8) {
            case 0 -> root.offset(distance, 0, 0);
            case 1 -> root.offset(-distance, 0, 0);
            case 2 -> root.offset(0, 0, distance);
            case 3 -> root.offset(0, 0, -distance);
            case 4 -> root.offset(distance, 0, distance);
            case 5 -> root.offset(distance, 0, -distance);
            case 6 -> root.offset(-distance, 0, distance);
            default -> root.offset(-distance, 0, -distance);
        };
    }

    public static Direction storageOutwardDirection(BlockPos root,
                                                     BlockPos anchor) {
        if (root == null || anchor == null) return Direction.EAST;
        int dx = anchor.getX() - root.getX();
        int dz = anchor.getZ() - root.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static Direction facingForExtension(Direction extension) {
        return extension.getClockWise();
    }

    private static Direction directionBetween(BlockPos first,
                                              BlockPos second) {
        int dx = second.getX() - first.getX();
        int dz = second.getZ() - first.getZ();
        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        if (dz > 0) return Direction.SOUTH;
        return Direction.NORTH;
    }

    private static ItemStack insert(BlockEntity blockEntity,
                                    ItemStack input) {
        if (!(blockEntity instanceof Container container)) return input;
        return insert(container, input);
    }

    /** Fonction pure réutilisée par les GameTests. */
    public static ItemStack insert(Container container, ItemStack input) {
        if (container == null || input == null || input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = input.copy();
        for (int slot = 0; slot < container.getContainerSize()
                && !remainder.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing,
                    remainder)) {
                continue;
            }
            int capacity = Math.min(existing.getMaxStackSize(),
                    container.getMaxStackSize()) - existing.getCount();
            if (capacity <= 0) continue;
            int moved = Math.min(capacity, remainder.getCount());
            existing.grow(moved);
            remainder.shrink(moved);
            container.setItem(slot, existing);
        }
        for (int slot = 0; slot < container.getContainerSize()
                && !remainder.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty()
                    || !container.canPlaceItem(slot, remainder)) {
                continue;
            }
            int moved = Math.min(remainder.getCount(), Math.min(
                    remainder.getMaxStackSize(), container.getMaxStackSize()));
            ItemStack placed = remainder.copy();
            placed.setCount(moved);
            container.setItem(slot, placed);
            remainder.shrink(moved);
        }
        container.setChanged();
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }

    private static boolean returnHome(Pillager pillager) {
        if (!pillager.getPersistentData().contains(PILLAGER_HOME)) {
            clearTask(pillager);
            return false;
        }
        BlockPos home = BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(pillager.getPersistentData(), PILLAGER_HOME));
        if (pillager.blockPosition().distSqr(home)
                <= PillagerRules.OUTPOST_LOOT_HOME_REACH_SQR) {
            pillager.getNavigation().stop();
            clearTask(pillager);
            return false;
        }
        pillager.setTarget(null);
        pillager.getNavigation().moveTo(home.getX() + 0.5D,
                home.getY(), home.getZ() + 0.5D,
                PillagerRules.OUTPOST_LOOT_RETURN_SPEED);
        return true;
    }

    private static void clearMissionKeys(Pillager pillager) {
        int count = Math.max(0, fr.vanillainstincts.persistence.NbtCompat.getInt(pillager.getPersistentData(), PILLAGER_ITEM_COUNT));
        for (int index = 0; index < count; index++) {
            pillager.getPersistentData().remove(PILLAGER_ITEM_PREFIX + index);
        }
        pillager.getPersistentData().remove(PILLAGER_ITEM);
        pillager.getPersistentData().remove(PILLAGER_ITEM_COUNT);
        pillager.getPersistentData().remove(PILLAGER_MISSION);
        pillager.getPersistentData().remove(PILLAGER_CHEST);
        pillager.getPersistentData().remove(PILLAGER_DEATH);
        pillager.getPersistentData().remove(PILLAGER_CARRYING);
        pillager.getPersistentData().remove(PILLAGER_STARTED_AT);
    }

    private static void clearTask(Pillager pillager) {
        clearMissionKeys(pillager);
        pillager.getPersistentData().remove(PILLAGER_HOME);
        pillager.getPersistentData().remove(PILLAGER_RETURNING);
    }

    /** Recherche uniquement les coffres déjà chargés. */
    public static BlockPos findOutpostChest(ServerLevel level,
                                            BlockPos origin) {
        if (level == null || origin == null) return null;
        int radius = PillagerRules.OUTPOST_CHEST_SEARCH_RADIUS;
        int minChunkX = (origin.getX() - radius) >> 4;
        int maxChunkX = (origin.getX() + radius) >> 4;
        int minChunkZ = (origin.getZ() - radius) >> 4;
        int maxChunkZ = (origin.getZ() + radius) >> 4;
        BlockPos best = null;
        int bestHeight = Integer.MIN_VALUE;
        int bestMaterials = Integer.MIN_VALUE;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX,
                        chunkZ);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> entry
                        : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (!level.getBlockState(pos).is(Blocks.CHEST)
                            || horizontalDistanceSqr(origin, pos)
                            > (long) radius * radius) {
                        continue;
                    }
                    int materials = outpostMaterialScore(level, pos);
                    if (!isOutpostChestScore(materials)) continue;
                    if (pos.getY() > bestHeight
                            || pos.getY() == bestHeight
                            && materials > bestMaterials) {
                        bestHeight = pos.getY();
                        bestMaterials = materials;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    private static int outpostMaterialScore(ServerLevel level,
                                            BlockPos chest) {
        int score = 0;
        for (BlockPos pos : BlockPos.betweenClosed(chest.offset(-5, -7, -5),
                chest.offset(5, 3, 5))) {
            if (isOutpostMaterial(level.getBlockState(pos))) score++;
        }
        return score;
    }

    public static boolean isOutpostMaterial(BlockState state) {
        return state != null && (state.is(Blocks.DARK_OAK_LOG)
                || state.is(Blocks.DARK_OAK_PLANKS)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.MOSSY_COBBLESTONE));
    }

    public static boolean isOutpostChestScore(int materialCount) {
        return materialCount >= PillagerRules.OUTPOST_CHEST_MATERIAL_THRESHOLD;
    }

    public static boolean shouldReservePlayerDrop(ItemStack stack) {
        return stack != null && !stack.isEmpty();
    }

    public static boolean assignmentExpired(long claimedAt, long gameTime) {
        return gameTime - claimedAt
                > PillagerRules.OUTPOST_LOOT_CLAIM_TIMEOUT_TICKS;
    }

    public static boolean homeReached(double distanceSqr) {
        return distanceSqr <= PillagerRules.OUTPOST_LOOT_HOME_REACH_SQR;
    }

    private static long horizontalDistanceSqr(BlockPos first,
                                              BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }
}
