package fr.vanillainstincts.ai;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
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
import fr.vanillainstincts.compat.BlockPos;
import fr.vanillainstincts.compat.EnumFacing;
import net.minecraft.world.WorldServer;
import net.minecraft.inventory.IInventory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.PillagerEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.BlockChest;
import net.minecraft.tileentity.TileEntity;
import fr.vanillainstincts.compat.LegacyBlockState;
import net.minecraft.state.properties.ChestType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.util.AxisAlignedBB;
/**
 * Après la mort d'un joueur près d'un avant-poste, un seul pillard prend en
 * charge la mission complète. Il rejoint le point de mort, récupère toutes les
 * piles simultanément, effectue un unique trajet jusqu'au coffre supérieur et
 * y dépose le lot entier.
 *
 * <p>Les piles restent des {@link EntityItem} réelles : leurs composants ne
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
    public static int onPlayerDrops(EntityPlayer player, WorldServer level,
                                    Entity killer,
                                    Iterable<EntityItem> drops,
                                    long gameTime) {
        if (player == null || level == null || drops == null) return 0;
        java.util.List<EntityItem> valid = new java.util.ArrayList<>();
        for (EntityItem drop : drops) {
            if (drop == null || !drop.isEntityAlive() || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(drop.getStackInSlot())) {
                continue;
            }
            RecoveredTradeController.markPlayerDeathDrop(drop, player);
            valid.add(drop);
        }
        if (valid.isEmpty()) return 0;
        PillagerEntity preferred = killer instanceof PillagerEntity
                ? ((PillagerEntity) (killer)) : null;
        BlockPos death = entityBlockPos(player);
        BlockPos chest = findOutpostChest(level, death);
        if (chest == null) return valid.size();
        PillagerEntity carrier = selectCarrier(level, preferred, death);
        if (carrier == null) return valid.size();

        UUID mission = UUID.randomUUID();
        beginMission(carrier, mission, chest, death, valid, gameTime);
        return valid.size();
    }

    private static PillagerEntity selectCarrier(WorldServer level,
                                          PillagerEntity preferred,
                                          BlockPos death) {
        if (availableForMission(preferred)) return preferred;
        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(death.getX(), death.getY(), death.getZ(),
                death.getX() + 1.0D, death.getY() + 1.0D,
                death.getZ() + 1.0D).expand(
                PillagerRules.OUTPOST_LOOT_SCAN_RADIUS,
                PillagerRules.OUTPOST_LOOT_VERTICAL_RADIUS,
                PillagerRules.OUTPOST_LOOT_SCAN_RADIUS);
        return fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, PillagerEntity.class, area,
                        PillagerOutpostLootController::availableForMission)
                .stream()
                .min(Comparator.comparingDouble(pillager ->
                        entityBlockPos(pillager).distanceSq(death)))
                .orElse(null);
    }

    public static boolean availableForMission(PillagerEntity pillager) {
        return pillager != null && pillager.isEntityAlive()
                && !fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(pillager.getEntityData(), PILLAGER_MISSION)
                && !pillager.getEntityData().getBoolean(PILLAGER_RETURNING);
    }

    private static void beginMission(PillagerEntity pillager, UUID mission,
                                     BlockPos chest, BlockPos death,
                                     java.util.List<EntityItem> items,
                                     long gameTime) {
        clearMissionKeys(pillager);
        pillager.getEntityData().setLong(PILLAGER_HOME,
                entityBlockPos(pillager).toLong());
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(pillager.getEntityData(), PILLAGER_MISSION, mission);
        pillager.getEntityData().setLong(PILLAGER_CHEST, chest.toLong());
        pillager.getEntityData().setLong(PILLAGER_DEATH, death.toLong());
        pillager.getEntityData().setBoolean(PILLAGER_CARRYING, false);
        pillager.getEntityData().setBoolean(PILLAGER_RETURNING, false);
        pillager.getEntityData().setLong(PILLAGER_STARTED_AT, gameTime);
        pillager.getEntityData().setInteger(PILLAGER_ITEM_COUNT, items.size());
        for (int index = 0; index < items.size(); index++) {
            EntityItem item = items.get(index);
            fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(pillager.getEntityData(), PILLAGER_ITEM_PREFIX + index,
                    item.getUniqueID());
            reserve(item, mission, pillager.getUniqueID(), chest, death, gameTime);
        }
    }

    /** Priorité complète tant que le pillard collecte, transporte ou revient. */
    public static boolean maintain(PillagerEntity pillager, WorldServer level,
                                   long gameTime) {
        if (pillager == null || level == null || !pillager.isEntityAlive()) {
            return false;
        }
        migrateLegacyAssignment(pillager, level);
        if (pillager.getEntityData().getBoolean(PILLAGER_RETURNING)) {
            return returnHome(pillager);
        }
        if (!fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(pillager.getEntityData(), PILLAGER_MISSION)) {
            return false;
        }

        pillager.setAttackTarget(null);
        java.util.List<EntityItem> items = resolveMissionItems(pillager, level);
        BlockPos death = BlockPos.fromLong(pillager.getEntityData()
                .getLong(PILLAGER_DEATH));
        int expectedCount = Math.max(0, pillager.getEntityData()
                .getInteger(PILLAGER_ITEM_COUNT));
        long startedAt = pillager.getEntityData().hasKey(
                PILLAGER_STARTED_AT)
                ? pillager.getEntityData().getLong(PILLAGER_STARTED_AT)
                : gameTime;

        // LivingDropsEvent peut précéder de très peu l'ajout définitif des
        // entités dans le niveau. Le porteur attend donc que le lot complet soit
        // visible au lieu de partir avec une partie des objets.
        if (items.size() < expectedCount) {
            pillager.getNavigator().stop();
            if (!assignmentExpired(startedAt, gameTime)) return true;
            releaseMissionItems(pillager, level);
            clearTask(pillager);
            return false;
        }

        boolean carrying = pillager.getEntityData()
                .getBoolean(PILLAGER_CARRYING);

        if (!carrying) {
            if (entityBlockPos(pillager).distanceSq(death)
                    > PillagerRules.OUTPOST_LOOT_PICKUP_DISTANCE_SQR) {
                pillager.getNavigator().tryMoveToXYZ(death.getX() + 0.5D,
                        death.getY(), death.getZ() + 0.5D,
                        PillagerRules.OUTPOST_LOOT_SPEED);
                return true;
            }
            // Toutes les piles sont prises au même tick : aucun second pillard
            // ne peut s'intercaler entre deux ramassages.
            pillager.getEntityData().setBoolean(PILLAGER_CARRYING, true);
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

        BlockPos chest = BlockPos.fromLong(pillager.getEntityData()
                .getLong(PILLAGER_CHEST));
        BlockPos depositTarget = nextStorageTarget(level, chest,
                items.get(0).getStackInSlot());
        if (entityBlockPos(pillager).distanceSq(depositTarget)
                > PillagerRules.OUTPOST_LOOT_CHEST_REACH_SQR) {
            pillager.getNavigator().tryMoveToXYZ(depositTarget.getX() + 0.5D,
                    depositTarget.getY() + 1.0D,
                    depositTarget.getZ() + 0.5D,
                    PillagerRules.OUTPOST_LOOT_SPEED);
            return true;
        }

        if (!fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, depositTarget).getBlock().equals(Blocks.chest)) {
            pillager.swingItem();
        }
        for (EntityItem item : items) {
            deposit(level, pillager, chest, item);
        }
        finishMission(pillager);
        return returnHome(pillager);
    }

    /** Libère sans perte le lot si le pillard porteur meurt. */
    public static void onCarrierDeath(PillagerEntity pillager, WorldServer level) {
        if (pillager == null || level == null) return;
        releaseMissionItems(pillager, level);
        clearTask(pillager);
    }

    public static boolean isReservedLoot(EntityItem item) {
        return item != null && fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(item.getEntityData(), LOOT_MISSION);
    }

    private static void reserve(EntityItem item, UUID mission, UUID carrier,
                                BlockPos chest, BlockPos death,
                                long gameTime) {
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(item.getEntityData(), LOOT_MISSION, mission);
        item.getEntityData().setLong(LOOT_CHEST, chest.toLong());
        item.getEntityData().setLong(LOOT_DEATH, death.toLong());
        item.getEntityData().setLong(LOOT_CLAIMED_AT, gameTime);
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(item.getEntityData(), LOOT_CARRIER, carrier);
        item.setPickUpDelay(32_767);
        item.lifespan = Integer.MAX_VALUE;
        item.setInvulnerable(true);
    }

    private static java.util.List<EntityItem> resolveMissionItems(
            PillagerEntity pillager, WorldServer level) {
        java.util.List<EntityItem> result = new java.util.ArrayList<>();
        UUID mission = fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(pillager.getEntityData(), PILLAGER_MISSION)
                ? fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(pillager.getEntityData(), PILLAGER_MISSION) : null;
        int count = Math.max(0, pillager.getEntityData()
                .getInteger(PILLAGER_ITEM_COUNT));
        for (int index = 0; index < count; index++) {
            String key = PILLAGER_ITEM_PREFIX + index;
            if (!fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(pillager.getEntityData(), key)) continue;
            Entity entity = fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, pillager.getEntityData()
                    .getUniqueId(key));
            if (entity instanceof EntityItem && ((EntityItem) (entity)).isEntityAlive()
                    && isReservedLoot(((EntityItem) (entity)))
                    && mission != null && mission.equals(((EntityItem) (entity))
                    fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(.getEntityData(), LOOT_MISSION))
                    && fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(((EntityItem) (entity)).getEntityData(), LOOT_CARRIER)
                    && pillager.getUniqueID().equals(((EntityItem) (entity)).getEntityData()
                    .getUniqueId(LOOT_CARRIER))) { EntityItem item = (EntityItem) (entity); 
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
    private static void releaseMissionItems(PillagerEntity pillager,
                                            WorldServer level) {
        if (!fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(pillager.getEntityData(), PILLAGER_MISSION)) return;
        UUID mission = fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(pillager.getEntityData(), PILLAGER_MISSION);
        Set<UUID> released = new java.util.HashSet<>();
        for (EntityItem item : resolveMissionItems(pillager, level)) {
            if (released.add(item.getUniqueID())) {
                release(item, entityBlockPos(pillager));
            }
        }
        BlockPos death = pillager.getEntityData().hasKey(PILLAGER_DEATH)
                ? BlockPos.fromLong(pillager.getEntityData().getLong(
                PILLAGER_DEATH)) : entityBlockPos(pillager);
        double radius = PillagerRules.OUTPOST_LOOT_SCAN_RADIUS;
        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(death.getX(), death.getY(), death.getZ(),
                death.getX() + 1.0D, death.getY() + 1.0D,
                death.getZ() + 1.0D).expand(radius,
                PillagerRules.OUTPOST_LOOT_VERTICAL_RADIUS, radius);
        for (EntityItem item : fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityItem.class, area,
                candidate -> candidate.isEntityAlive()
                        && fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(candidate.getEntityData(), LOOT_MISSION)
                        && mission.equals(candidate.getEntityData()
                        .getUniqueId(LOOT_MISSION)))) {
            if (released.add(item.getUniqueID())) release(item, death);
        }
    }

    private static void migrateLegacyAssignment(PillagerEntity pillager,
                                                 WorldServer level) {
        if (pillager.getEntityData().getInteger(PILLAGER_ITEM_COUNT) > 0
                || !fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(pillager.getEntityData(), PILLAGER_ITEM)) {
            return;
        }
        UUID itemId = fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(pillager.getEntityData(), PILLAGER_ITEM);
        Entity entity = fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, itemId);
        if (!(entity instanceof EntityItem) || !((EntityItem) (entity)).isEntityAlive()) return; EntityItem item = (EntityItem) (entity);
        pillager.getEntityData().setInteger(PILLAGER_ITEM_COUNT, 1);
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(pillager.getEntityData(), PILLAGER_ITEM_PREFIX + 0, itemId);
        if (!pillager.getEntityData().hasKey(PILLAGER_DEATH)) {
            long death = item.getEntityData().hasKey(LOOT_DEATH)
                    ? item.getEntityData().getLong(LOOT_DEATH)
                    : entityBlockPos(item).toLong();
            pillager.getEntityData().setLong(PILLAGER_DEATH, death);
        }
    }

    private static void prepareCarriedItem(PillagerEntity pillager, EntityItem item,
                                           int index) {
        item.setNoGravity(true);
        item.setDeltaMovement(0.0D, 0.0D, 0.0D);
        carryBesidePillager(pillager, item, index, 1);
    }

    private static void carryBesidePillager(PillagerEntity pillager,
                                            EntityItem item, int index,
                                            int total) {
        double angle = total <= 1 ? 0.0D
                : Math.PI * 2.0D * index / total;
        double radius = total <= 1 ? 0.0D : 0.45D;
        fr.vanillainstincts.compat.Minecraft112Compat.teleport(item, pillager.getX() + Math.cos(angle) * radius,
                pillager.getY() + 1.05D + (index % 3) * 0.08D,
                pillager.getZ() + Math.sin(angle) * radius);
        item.setDeltaMovement(0.0D, 0.0D, 0.0D);
        item.setNoGravity(true);
        item.setPickUpDelay(32_767);
    }

    private static void deposit(WorldServer level, PillagerEntity pillager,
                                BlockPos chest, EntityItem item) {
        ItemStack remainder = insertIntoOutpostStorage(level, pillager, chest,
                item.getStackInSlot());
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remainder)) {
            item.remove();
            return;
        }
        item.setInventorySlotContents(remainder);
        release(item, chest.up());
    }

    private static void release(EntityItem item, BlockPos position) {
        item.getEntityData().removeTag(LOOT_MISSION);
        item.getEntityData().removeTag(LOOT_CARRIER);
        item.getEntityData().removeTag(LOOT_CHEST);
        item.getEntityData().removeTag(LOOT_DEATH);
        item.getEntityData().removeTag(LOOT_CLAIMED_AT);
        item.setNoGravity(false);
        item.setInvulnerable(false);
        item.setNoPickUpDelay();
        fr.vanillainstincts.compat.Minecraft112Compat.teleport(item, position.getX() + 0.5D, position.getY() + 0.2D,
                position.getZ() + 0.5D);
    }

    private static void finishMission(PillagerEntity pillager) {
        clearMissionKeys(pillager);
        pillager.getEntityData().setBoolean(PILLAGER_RETURNING, true);
    }

    /**
     * Insère une pile dans le réseau de stockage de l'avant-poste. Le coffre
     * principal est d'abord utilisé, puis agrandi en double coffre. Lorsque
     * cette paire est pleine, des groupes simples sont créés à partir de deux
     * blocs du coffre principal et chacun est agrandi seulement si nécessaire.
     */
    public static ItemStack insertIntoOutpostStorage(WorldServer level,
                                                     BlockPos chest,
                                                     ItemStack input) {
        return insertIntoOutpostStorage(level, null, chest, input);
    }

    private static ItemStack insertIntoOutpostStorage(WorldServer level,
                                                       Entity actor,
                                                       BlockPos chest,
                                                       ItemStack input) {
        if (level == null || chest == null || input == null
                || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(input)) {
            return null;
        }
        ItemStack remainder = input.copy();
        Set<BlockPos> visited = new LinkedHashSet<>();

        remainder = insertChestGroup(level, actor, chest, remainder, visited);
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remainder)) return null;

        BlockPos rootPartner = tryExpandChest(level, actor, chest, null);
        if (rootPartner != null) {
            remainder = insertChestAt(level, actor, rootPartner, remainder,
                    visited);
            if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remainder)) return null;
        }

        for (int index = 0; index < PillagerRules.OUTPOST_STORAGE_MAX_GROUPS
                && !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remainder); index++) {
            BlockPos anchor = storageAnchor(chest, index);
            EnumFacing outward = storageOutwardDirection(chest, anchor);
            if (fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, anchor).getBlock().equals(Blocks.chest)) {
                remainder = insertChestGroup(level, actor, anchor, remainder,
                        visited);
                if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remainder)) break;
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
        return fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remainder) ? null : remainder;
    }

    /** Cible visible choisie avant que le pillard dépose ou pose un coffre. */
    public static BlockPos nextStorageTarget(WorldServer level,
                                             BlockPos chest,
                                             ItemStack input) {
        if (level == null || chest == null || input == null
                || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(input)) return chest;
        BlockPos accepting = firstAcceptingChest(level, chest, input);
        if (accepting != null) return accepting;

        BlockPos rootExpansion = expansionPosition(level, chest, null);
        if (rootExpansion != null) return rootExpansion;

        for (int index = 0; index < PillagerRules.OUTPOST_STORAGE_MAX_GROUPS;
             index++) {
            BlockPos anchor = storageAnchor(chest, index);
            EnumFacing outward = storageOutwardDirection(chest, anchor);
            if (fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, anchor).getBlock().equals(Blocks.chest)) {
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

    private static ItemStack insertChestGroup(WorldServer level,
                                              Entity actor,
                                              BlockPos center,
                                              ItemStack input,
                                              Set<BlockPos> visited) {
        ItemStack remainder = insertChestAt(level, actor, center, input,
                visited);
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remainder)) return null;
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbor = center.offset(direction);
            if (!fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, neighbor).getBlock().equals(Blocks.chest)) continue;
            remainder = insertChestAt(level, actor, neighbor, remainder,
                    visited);
            if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remainder)) return null;
        }
        return remainder;
    }

    private static ItemStack insertChestAt(WorldServer level,
                                           Entity actor,
                                           BlockPos position,
                                           ItemStack input,
                                           Set<BlockPos> visited) {
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(input) || !visited.add(immutableBlockPos(position))) {
            return input;
        }
        if (!WorldPermissionService.canMutateContainer(level, actor,
                position)) {
            return input;
        }
        return insert(fr.vanillainstincts.compat.Minecraft17Compat.getTileEntity(level, position), input);
    }

    private static BlockPos firstAcceptingChest(WorldServer level,
                                                BlockPos center,
                                                ItemStack input) {
        if (chestCanAccept(level, center, input)) return center;
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbor = center.offset(direction);
            if (chestCanAccept(level, neighbor, input)) return neighbor;
        }
        return null;
    }

    private static boolean chestCanAccept(WorldServer level, BlockPos position,
                                          ItemStack input) {
        TileEntity entity = fr.vanillainstincts.compat.Minecraft17Compat.getTileEntity(level, position);
        return entity instanceof IInventory
                && canAccept(((IInventory) (entity)), input);
    }

    /** Fonction pure réutilisée par les GameTests. */
    public static boolean canAccept(IInventory container, ItemStack input) {
        if (container == null || input == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(input)) return false;
        for (int slot = 0; slot < container.getSizeInventory(); slot++) {
            ItemStack existing = container.getStackInSlot(slot);
            if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(existing)) {
                if (container.canPlaceItem(slot, input)) return true;
                continue;
            }
            if (fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(existing, input)
                    && existing.stackSize < Math.min(existing.getMaxStackSize(),
                    container.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos tryExpandChest(WorldServer level, Entity actor,
                                           BlockPos base,
                                           EnumFacing preferred) {
        BlockPos partner = expansionPosition(level, base, preferred);
        if (partner == null) return null;
        LegacyBlockState baseState = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, base);
        EnumFacing facing = (EnumFacing) baseState.getValue(BlockChest.FACING);
        EnumFacing extension = directionBetween(base, partner);
        ChestType baseType = extension == facing.getClockWise()
                ? ChestType.LEFT : ChestType.RIGHT;
        ChestType partnerType = baseType == ChestType.LEFT
                ? ChestType.RIGHT : ChestType.LEFT;
        LegacyBlockState partnerState = fr.vanillainstincts.compat.Minecraft17Compat.defaultState(Blocks.chest)
                .setValue(BlockChest.FACING, facing)
                .setValue(BlockChest.TYPE, partnerType);
        List<WorldPermissionService.BlockChange> changes = fr.vanillainstincts.compat.LegacyJava8.listOf(
                new WorldPermissionService.BlockChange(base,
                        baseState.setValue(BlockChest.TYPE, baseType),
                        3),
                new WorldPermissionService.BlockChange(partner, partnerState,
                        3));
        return WorldPermissionService.setBlocksAtomically(level, actor,
                changes, WorldActionType.PLACE_BLOCK) ? partner : null;
    }

    private static BlockPos expansionPosition(WorldServer level, BlockPos base,
                                              EnumFacing preferred) {
        LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, base);
        if (!state.getBlock().equals(Blocks.chest)
                || state.getValue(BlockChest.TYPE) != ChestType.SINGLE) {
            return null;
        }
        EnumFacing facing = (EnumFacing) state.getValue(BlockChest.FACING);
        EnumFacing[] candidates = preferred == null
                ? new EnumFacing[]{facing.getClockWise(),
                facing.getCounterClockWise()}
                : new EnumFacing[]{preferred};
        for (EnumFacing extension : candidates) {
            if (extension.getAxis() == EnumFacing.Axis.Y
                    || extension.getAxis() == facing.getAxis()) continue;
            BlockPos partner = base.offset(extension);
            if (canPlacePartnerChest(level, partner, base)) return partner;
        }
        return null;
    }

    private static boolean placeSingleChest(WorldServer level, Entity actor,
                                            BlockPos position,
                                            EnumFacing facing) {
        LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.defaultState(Blocks.chest)
                .setValue(BlockChest.FACING, facing)
                .setValue(BlockChest.TYPE, ChestType.SINGLE);
        return WorldPermissionService.setBlock(level, actor, position, state,
                3, WorldActionType.PLACE_BLOCK);
    }

    private static boolean canPlaceSingleChest(WorldServer level,
                                               BlockPos position) {
        if (!isSafeChestSpace(level, position)) return false;
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            if (fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, position.offset(direction))
                    .getBlock().equals(Blocks.chest)) return false;
        }
        return true;
    }

    private static boolean canPlacePartnerChest(WorldServer level,
                                                BlockPos position,
                                                BlockPos base) {
        if (!isSafeChestSpace(level, position)) return false;
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbor = position.offset(direction);
            if (neighbor.equals(base)) continue;
            if (fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, neighbor).getBlock().equals(Blocks.chest)) return false;
        }
        return true;
    }

    private static boolean isSafeChestSpace(WorldServer level,
                                            BlockPos position) {
        return fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, position)
                && fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, position).isAir()
                && fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, position.up()).isAir()
                && fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, position.down()).isFaceSturdy(level,
                position.down(), EnumFacing.UP);
    }

    /** Position déterministe du groupe, le premier étant à deux blocs. */
    public static BlockPos storageAnchor(BlockPos root, int index) {
        if (root == null || index < 0) return root;
        int ring = index / 8;
        int distance = 2 + ring * 3;
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((index % 8)) { case 0:  return root.offset(distance, 0, 0); case 1:  return root.offset(-distance, 0, 0); case 2:  return root.offset(0, 0, distance); case 3:  return root.offset(0, 0, -distance); case 4:  return root.offset(distance, 0, distance); case 5:  return root.offset(distance, 0, -distance); case 6:  return root.offset(-distance, 0, distance); default:  return root.offset(-distance, 0, -distance); } });
    }

    public static EnumFacing storageOutwardDirection(BlockPos root,
                                                     BlockPos anchor) {
        if (root == null || anchor == null) return EnumFacing.EAST;
        int dx = anchor.getX() - root.getX();
        int dz = anchor.getZ() - root.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? EnumFacing.EAST : EnumFacing.WEST;
        }
        return dz >= 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }

    private static EnumFacing facingForExtension(EnumFacing extension) {
        return extension.getClockWise();
    }

    private static EnumFacing directionBetween(BlockPos first,
                                              BlockPos second) {
        int dx = second.getX() - first.getX();
        int dz = second.getZ() - first.getZ();
        if (dx > 0) return EnumFacing.EAST;
        if (dx < 0) return EnumFacing.WEST;
        if (dz > 0) return EnumFacing.SOUTH;
        return EnumFacing.NORTH;
    }

    private static ItemStack insert(TileEntity blockEntity,
                                    ItemStack input) {
        if (!(blockEntity instanceof IInventory)) return input; IInventory container = (IInventory) (blockEntity);
        return insert(container, input);
    }

    /** Fonction pure réutilisée par les GameTests. */
    public static ItemStack insert(IInventory container, ItemStack input) {
        if (container == null || input == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(input)) {
            return null;
        }
        ItemStack remainder = input.copy();
        for (int slot = 0; slot < container.getSizeInventory()
                && !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remainder); slot++) {
            ItemStack existing = container.getStackInSlot(slot);
            if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(existing)
                    || !fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(existing,
                    remainder)) {
                continue;
            }
            int capacity = Math.min(existing.getMaxStackSize(),
                    container.getMaxStackSize()) - existing.stackSize;
            if (capacity <= 0) continue;
            int moved = Math.min(capacity, remainder.stackSize);
            fr.vanillainstincts.compat.Minecraft110ItemStackCompat.grow(existing, moved);
            fr.vanillainstincts.compat.Minecraft110ItemStackCompat.shrink(remainder, moved);
            container.setInventorySlotContents(slot, existing);
        }
        for (int slot = 0; slot < container.getSizeInventory()
                && !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remainder); slot++) {
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(container.getStackInSlot(slot))
                    || !container.canPlaceItem(slot, remainder)) {
                continue;
            }
            int moved = Math.min(remainder.stackSize, Math.min(
                    remainder.getMaxStackSize(), container.getMaxStackSize()));
            ItemStack placed = remainder.copy();
            fr.vanillainstincts.compat.Minecraft110ItemStackCompat.setCount(placed, moved);
            container.setInventorySlotContents(slot, placed);
            fr.vanillainstincts.compat.Minecraft110ItemStackCompat.shrink(remainder, moved);
        }
        container.markDirty();
        return fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remainder) ? null : remainder;
    }

    private static boolean returnHome(PillagerEntity pillager) {
        if (!pillager.getEntityData().hasKey(PILLAGER_HOME)) {
            clearTask(pillager);
            return false;
        }
        BlockPos home = BlockPos.fromLong(pillager.getEntityData()
                .getLong(PILLAGER_HOME));
        if (entityBlockPos(pillager).distanceSq(home)
                <= PillagerRules.OUTPOST_LOOT_HOME_REACH_SQR) {
            pillager.getNavigator().stop();
            clearTask(pillager);
            return false;
        }
        pillager.setAttackTarget(null);
        pillager.getNavigator().tryMoveToXYZ(home.getX() + 0.5D,
                home.getY(), home.getZ() + 0.5D,
                PillagerRules.OUTPOST_LOOT_RETURN_SPEED);
        return true;
    }

    private static void clearMissionKeys(PillagerEntity pillager) {
        int count = Math.max(0, pillager.getEntityData()
                .getInteger(PILLAGER_ITEM_COUNT));
        for (int index = 0; index < count; index++) {
            pillager.getEntityData().removeTag(PILLAGER_ITEM_PREFIX + index);
        }
        pillager.getEntityData().removeTag(PILLAGER_ITEM);
        pillager.getEntityData().removeTag(PILLAGER_ITEM_COUNT);
        pillager.getEntityData().removeTag(PILLAGER_MISSION);
        pillager.getEntityData().removeTag(PILLAGER_CHEST);
        pillager.getEntityData().removeTag(PILLAGER_DEATH);
        pillager.getEntityData().removeTag(PILLAGER_CARRYING);
        pillager.getEntityData().removeTag(PILLAGER_STARTED_AT);
    }

    private static void clearTask(PillagerEntity pillager) {
        clearMissionKeys(pillager);
        pillager.getEntityData().removeTag(PILLAGER_HOME);
        pillager.getEntityData().removeTag(PILLAGER_RETURNING);
    }

    /** Recherche uniquement les coffres déjà chargés. */
    public static BlockPos findOutpostChest(WorldServer level,
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
                Chunk chunk = level.getChunkSource().getChunkNow(chunkX,
                        chunkZ);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, TileEntity> entry
                        : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (!fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos).getBlock().equals(Blocks.chest)
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
                        best = immutableBlockPos(pos);
                    }
                }
            }
        }
        return best;
    }

    private static int outpostMaterialScore(WorldServer level,
                                            BlockPos chest) {
        int score = 0;
        for (BlockPos pos : fr.vanillainstincts.compat.Minecraft112BlockPosCompat.betweenClosed(chest.offset(-5, -7, -5),
                chest.offset(5, 3, 5))) {
            if (isOutpostMaterial(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos))) score++;
        }
        return score;
    }

    public static boolean isOutpostMaterial(LegacyBlockState state) {
        return state != null && (state.getBlock().equals(Blocks.log2)
                || state.getBlock().equals(Blocks.planks)
                || state.getBlock().equals(Blocks.cobblestone)
                || state.getBlock().equals(Blocks.mossy_cobblestone));
    }

    public static boolean isOutpostChestScore(int materialCount) {
        return materialCount >= PillagerRules.OUTPOST_CHEST_MATERIAL_THRESHOLD;
    }

    public static boolean shouldReservePlayerDrop(ItemStack stack) {
        return stack != null && !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack);
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
