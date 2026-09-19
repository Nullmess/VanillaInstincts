package fr.vanillainstincts.village;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115WorldCompat;

import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.compat.LegacyDimensionType;
import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.NetherPortalService;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.rules.WorldRules;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.util.DamageSource;
import net.minecraft.potion.PotionEffect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.BlockPortal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.Vec3;

/**
 * Persistent cleric mission for obtaining real Nether brewing stock.
 * Observed missions use the normal portal and loaded Nether entities. When no
 * player observes the destination, only the departure/return is simulated and
 * no remote chunk is loaded by this controller.
 */
public final class ClericNetherExpeditionController {
    private static final String PHASE = "vanillainstincts_cleric_nether_phase";
    private static final String READY_AT =
            "vanillainstincts_cleric_nether_ready_at";
    private static final String STARTED_AT =
            "vanillainstincts_cleric_nether_started_at";
    private static final String RETURN_AT =
            "vanillainstincts_cleric_nether_return_at";
    private static final String PORTAL_POS =
            "vanillainstincts_cleric_nether_portal";
    private static final String HOME_PORTAL_POS =
            "vanillainstincts_cleric_nether_home_portal";
    private static final String BLAZE_KILLS =
            "vanillainstincts_cleric_nether_blaze_kills";
    private static final String ACTION_AT =
            "vanillainstincts_cleric_nether_action_at";
    private static final String BARTERS =
            "vanillainstincts_cleric_nether_barters";
    private static final String PIGLIN =
            "vanillainstincts_cleric_nether_piglin";
    private static final String BARTER_READY_AT =
            "vanillainstincts_cleric_nether_barter_ready_at";
    private static final String BARTER_PAYMENT =
            "vanillainstincts_cleric_nether_barter_payment";

    private static final int IDLE = 0;
    private static final int APPROACH = 1;
    private static final int VIRTUAL = 2;
    private static final int LIVE = 3;
    private static final int RETURN = 4;

    private ClericNetherExpeditionController() {
    }

    public static boolean contribute(EntityVillager cleric,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        if (!eligible(cleric) || state.danger(gameTime) != null) return false;
        int phase = phase(cleric);
        if (phase == VIRTUAL || phase == LIVE || phase == RETURN) return true;
        if (!LegacyDimensionType.OVERWORLD.equals(LegacyDimensionType.of(level))) return phase != IDLE;

        if (phase == IDLE) {
            if (gameTime < cleric.getEntityData().getLong(READY_AT)
                    || Math.floorMod(gameTime + cleric.getEntityId() * 37L,
                    ProfessionRules.CLERIC_NETHER_SCAN_TICKS) != 0L
                    || !needsNetherStock(cleric.getVillagerInventory())) {
                return false;
            }
            BlockPos portal = NetherPortalService.findNearestActivePortal(
                    level, entityBlockPos(cleric),
                    ProfessionRules.CLERIC_NETHER_PORTAL_SEARCH_RADIUS, 24);
            if (portal == null) {
                portal = tryBuildPortal(cleric, level);
            }
            if (portal == null) return false;
            begin(cleric, portal, gameTime);
        }

        BlockPos portal = portalPos(cleric);
        if (portal == null || !NetherPortalService.isActivePortal(level,
                portal)) {
            clearMission(cleric, level, gameTime);
            return false;
        }
        Vec3 destination = Minecraft115VectorCompat.atBottomCenterOf(portal);
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(cleric, destination) > 2.25D) {
            plan.offerNavigation(VanillaInstinctsState.CLERIC_NETHER,
                    ActionOwner.VILLAGER_PROFESSION,
                    ProfessionRules.PRIORITY_CLERIC_NETHER,
                    destination, ProfessionRules.CLERIC_NETHER_SPEED,
                    WorldRules.STATE_HOLD_FARM_TICKS, null);
            return true;
        }

        if (!hasNetherObserver(level, portal)) {
            plan.offerSpecial(VanillaInstinctsState.CLERIC_NETHER,
                    ActionOwner.VILLAGER_PROFESSION,
                    ProfessionRules.PRIORITY_CLERIC_NETHER + 2,
                    WorldRules.STATE_HOLD_FARM_TICKS,
                    () -> virtualize(cleric, level, portal, gameTime));
            return true;
        }

        // A player is observing the expected Nether arrival. Keep navigating
        // inside the real portal and let vanilla perform dimension travel.
        plan.offerNavigation(VanillaInstinctsState.CLERIC_NETHER,
                ActionOwner.VILLAGER_PROFESSION,
                ProfessionRules.PRIORITY_CLERIC_NETHER + 1,
                destination, ProfessionRules.CLERIC_NETHER_SPEED,
                WorldRules.STATE_HOLD_FARM_TICKS, null);
        return true;
    }

    public static void maintain(EntityVillager cleric, WorldServer level,
                                long gameTime) {
        int phase = phase(cleric);
        if (phase == IDLE) return;
        if (!eligible(cleric)) {
            restore(cleric);
            if (!LegacyDimensionType.OVERWORLD.equals(LegacyDimensionType.of(level))
                    && homePortalPos(cleric) != null) {
                fr.vanillainstincts.compat.Minecraft112Compat.setInvulnerable(cleric, true);
                forceReturnHome(cleric, level, gameTime);
                return;
            }
            clearMission(cleric, level, gameTime);
            return;
        }

        if (phase == APPROACH && LegacyDimensionType.NETHER.equals(LegacyDimensionType.of(level))) {
            cleric.getEntityData().setInteger(PHASE, LIVE);
            cleric.getEntityData().setLong(STARTED_AT, gameTime);
            BlockPos arrival = NetherPortalService.findNearestActivePortal(
                    level, entityBlockPos(cleric), 24, 24);
            if (arrival != null) storePortal(cleric, arrival);
            phase = LIVE;
        }
        if (phase == RETURN && LegacyDimensionType.OVERWORLD.equals(LegacyDimensionType.of(level))) {
            finish(cleric, level, gameTime);
            return;
        }
        if (phase == VIRTUAL) {
            maintainVirtual(cleric, level, gameTime);
            return;
        }
        if (phase == LIVE && LegacyDimensionType.NETHER.equals(LegacyDimensionType.of(level))) {
            maintainLive(cleric, level, gameTime);
            return;
        }
        if (phase == RETURN && LegacyDimensionType.NETHER.equals(LegacyDimensionType.of(level))) {
            maintainReturn(cleric, level, gameTime);
        }
    }

    private static void maintainVirtual(EntityVillager cleric, WorldServer level,
                                        long gameTime) {
        cleric.getNavigator().clearPathEntity();
        fr.vanillainstincts.compat.Minecraft112Compat.setNoAi(cleric, true);
        cleric.setInvisible(true);
        fr.vanillainstincts.compat.Minecraft112Compat.setInvulnerable(cleric, true);
        cleric.setSilent(true);
        if (gameTime < cleric.getEntityData().getLong(RETURN_AT)) return;
        grantVirtualLoot(cleric, gameTime);
        BlockPos portal = portalPos(cleric);
        if (portal != null && level.isBlockLoaded(portal)) {
            fr.vanillainstincts.compat.Minecraft112Compat.teleport(cleric, portal.getX() + 0.5D, portal.getY(),
                    portal.getZ() + 0.5D);
        }
        finish(cleric, level, gameTime);
    }

    private static void maintainLive(EntityVillager cleric, WorldServer level,
                                     long gameTime) {
        fr.vanillainstincts.compat.Minecraft112Compat.setInvulnerable(cleric, true);
        fr.vanillainstincts.compat.Minecraft112Compat.addEffect(cleric, new PotionEffect(net.minecraft.potion.Potion.fireResistance.getId(),
                240, 0, true, false));
        fr.vanillainstincts.compat.Minecraft112Compat.addEffect(cleric, new PotionEffect(net.minecraft.potion.Potion.damageBoost.getId(),
                240, 1, true, false));
        fr.vanillainstincts.compat.Minecraft112Compat.addEffect(cleric, new PotionEffect(net.minecraft.potion.Potion.regeneration.getId(),
                120, 1, true, false));
        if (cleric.getHealth() < cleric.getMaxHealth()) {
            cleric.heal(Math.min(4.0F,
                    cleric.getMaxHealth() - cleric.getHealth()));
        }
        collectNetherDrops(cleric, level);
        completePendingBarter(cleric, level, gameTime);

        long elapsed = gameTime - cleric.getEntityData()
                .getLong(STARTED_AT);
        if (cleric.getEntityData().getInteger(BLAZE_KILLS)
                >= ProfessionRules.CLERIC_NETHER_REQUIRED_BLAZE_KILLS
                || elapsed >= ProfessionRules.CLERIC_NETHER_LIVE_TIMEOUT_TICKS) {
            cleric.getEntityData().setInteger(PHASE, RETURN);
            cleric.getEntityData().setLong(RETURN_AT,
                    gameTime
                            + ProfessionRules.CLERIC_NETHER_RETURN_TIMEOUT_TICKS);
            cleric.getNavigator().clearPathEntity();
            return;
        }

        if (gameTime >= cleric.getEntityData().getLong(ACTION_AT)) {
            if (tryBarter(cleric, level, gameTime)) return;
            EntityBlaze blaze = level.getEntitiesWithinAABB(EntityBlaze.class,
                    fr.vanillainstincts.compat.Minecraft112Compat.expandBox(cleric.getEntityBoundingBox(), 
                            ProfessionRules.CLERIC_NETHER_LIVE_SEARCH_RADIUS),
                    Entity::isEntityAlive).stream()
                    .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(cleric, value))))
                    .orElse(null);
            if (blaze != null) {
                if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(cleric, blaze) > 9.0D) {
                    cleric.getNavigator().tryMoveToEntityLiving(blaze,
                            ProfessionRules.CLERIC_NETHER_SPEED);
                    placeBridgeToward(cleric, level, entityBlockPos(blaze));
                } else {
                    cleric.setCurrentItemOrArmor(0,
                            new ItemStack(Items.iron_sword));
                    cleric.swingItem();
                    boolean aliveBefore = blaze.isEntityAlive();
                    blaze.attackEntityFrom(DamageSource.causeMobDamage(cleric),
                            blaze.getHealth() + 2.0F);
                    if (aliveBefore && !blaze.isEntityAlive()) {
                        cleric.getEntityData().setInteger(BLAZE_KILLS,
                                cleric.getEntityData().getInteger(BLAZE_KILLS)
                                        + 1);
                        insert(cleric, new ItemStack(Items.blaze_rod));
                    }
                }
                cleric.getEntityData().setLong(ACTION_AT,
                        gameTime
                                + ProfessionRules.CLERIC_NETHER_COMBAT_COOLDOWN_TICKS);
                return;
            }

            BlockPos spawner = findLoadedSpawner(level,
                    entityBlockPos(cleric),
                    ProfessionRules.CLERIC_NETHER_LIVE_SEARCH_RADIUS);
            if (spawner != null) {
                cleric.getNavigator().tryMoveToXYZ(spawner.getX() + 0.5D,
                        spawner.getY(), spawner.getZ() + 0.5D,
                        ProfessionRules.CLERIC_NETHER_SPEED);
                placeBridgeToward(cleric, level, spawner);
            } else {
                BlockPos portal = portalPos(cleric);
                if (portal != null) {
                    cleric.getNavigator().tryMoveToXYZ(portal.getX() + 0.5D,
                            portal.getY(), portal.getZ() + 0.5D,
                            ProfessionRules.CLERIC_NETHER_SPEED);
                }
            }
            cleric.getEntityData().setLong(ACTION_AT, gameTime + 40L);
        }
    }

    private static void maintainReturn(EntityVillager cleric, WorldServer level,
                                       long gameTime) {
        fr.vanillainstincts.compat.Minecraft112Compat.setInvulnerable(cleric, true);
        fr.vanillainstincts.compat.Minecraft112Compat.addEffect(cleric, new PotionEffect(net.minecraft.potion.Potion.fireResistance.getId(),
                240, 0, true, false));
        BlockPos portal = NetherPortalService.findNearestActivePortal(level,
                entityBlockPos(cleric),
                ProfessionRules.CLERIC_NETHER_PORTAL_SEARCH_RADIUS, 32);
        if (portal == null) portal = portalPos(cleric);
        if (portal != null && level.isBlockLoaded(portal)) {
            storePortal(cleric, portal);
            cleric.getNavigator().tryMoveToXYZ(portal.getX() + 0.5D, portal.getY(),
                    portal.getZ() + 0.5D,
                    ProfessionRules.CLERIC_NETHER_SPEED);
            placeBridgeToward(cleric, level, portal);
        }
        if (gameTime >= cleric.getEntityData().getLong(RETURN_AT)) {
            forceReturnHome(cleric, level, gameTime);
        }
    }

    private static boolean tryBarter(EntityVillager cleric, WorldServer level,
                                     long gameTime) {
        // Minecraft 1.15.x predates Piglins and vanilla bartering.
        return false;
    }

    private static void completePendingBarter(EntityVillager cleric,
                                              WorldServer level,
                                              long gameTime) {
        if (!cleric.getEntityData().hasKey(PIGLIN)
                || gameTime < cleric.getEntityData()
                .getLong(BARTER_READY_AT)) {
            return;
        }
        discardPendingPayment(cleric, level);
        int sequence = cleric.getEntityData().getInteger(BARTERS);
        ItemStack result = barterResult(cleric.getUniqueID().hashCode()
                + sequence * 31 + Long.hashCode(gameTime / 20L));
        insert(cleric, result);
        cleric.getEntityData().setInteger(BARTERS, sequence + 1);
        cleric.getEntityData().removeTag(PIGLIN);
        cleric.getEntityData().removeTag(BARTER_READY_AT);
        cleric.getEntityData().removeTag(BARTER_PAYMENT);
        cleric.setCurrentItemOrArmor(0, null);
    }

    private static ItemStack barterResult(int seed) {
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((Math.floorMod(seed, 8))) { case 0:  return new ItemStack(Items.ender_pearl, 2); case 1:  return new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.obsidian), 2); case 2:  return new ItemStack(Items.glowstone_dust, 4); case 3:  return new ItemStack(Items.quartz, 5); case 4:  return new ItemStack(Items.magma_cream, 2); case 5:  return new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.soul_sand), 4); case 6:  return new ItemStack(Items.fire_charge, 2); default:  return new ItemStack(Items.string, 6); } });
    }

    private static void collectNetherDrops(EntityVillager cleric,
                                           WorldServer level) {
        for (EntityItem entity : level.getEntitiesWithinAABB(EntityItem.class,
                cleric.getEntityBoundingBox().expand(3.0D, 3.0D, 3.0D), item -> item.isEntityAlive()
                        && isNetherResource(item.getEntityItem()))) {
            ItemStack stack = entity.getEntityItem().copy();
            int original = stack.stackSize;
            insert(cleric, stack);
            int inserted = original - stack.stackSize;
            if (inserted <= 0) continue;
            if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(entity);
            else fr.vanillainstincts.compat.Minecraft112Compat.setItem(entity, stack);
        }
    }

    private static boolean isNetherResource(ItemStack stack) {
        return stack.getItem().equals(Items.blaze_rod) || stack.getItem().equals(Items.nether_wart)
                || stack.getItem().equals(Items.quartz) || stack.getItem().equals(Items.glowstone_dust)
                || stack.getItem().equals(Items.magma_cream) || stack.getItem().equals(Items.ghast_tear)
                || stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.soul_sand)) || stack.getItem().equals(Items.ender_pearl)
                || stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.obsidian))
                || stack.getItem().equals(Items.fire_charge);
    }

    private static void grantVirtualLoot(EntityVillager cleric, long gameTime) {
        int seed = cleric.getUniqueID().hashCode()
                ^ Long.hashCode(gameTime / 20L);
        insert(cleric, new ItemStack(Items.blaze_rod,
                2 + Math.floorMod(seed, 3)));
        insert(cleric, new ItemStack(Items.nether_wart,
                3 + Math.floorMod(seed >>> 3, 4)));
        insert(cleric, new ItemStack(Items.quartz,
                4 + Math.floorMod(seed >>> 5, 5)));
        insert(cleric, new ItemStack(Items.glowstone_dust,
                2 + Math.floorMod(seed >>> 7, 4)));
        int gold = Math.min(2, count(cleric.getVillagerInventory(), Items.gold_ingot));
        for (int i = 0; i < gold; i++) {
            if (consume(cleric.getVillagerInventory(), Items.gold_ingot, 1)) {
                insert(cleric, barterResult(seed + i * 17));
            }
        }
    }

    private static void virtualize(EntityVillager cleric, WorldServer level,
                                   BlockPos portal, long gameTime) {
        cleric.getNavigator().clearPathEntity();
        storePortal(cleric, portal);
        cleric.getEntityData().setInteger(PHASE, VIRTUAL);
        cleric.getEntityData().setLong(RETURN_AT,
                gameTime + virtualDuration(cleric.getUniqueID().hashCode()
                        ^ gameTime));
        cleric.getEntityData().setLong(STARTED_AT, gameTime);
        fr.vanillainstincts.compat.Minecraft112Compat.setNoAi(cleric, true);
        cleric.setInvisible(true);
        fr.vanillainstincts.compat.Minecraft112Compat.setInvulnerable(cleric, true);
        cleric.setSilent(true);
        fr.vanillainstincts.compat.Minecraft112Compat.teleport(cleric, portal.getX() + 0.5D, portal.getY(),
                portal.getZ() + 0.5D);
    }

    public static int virtualDuration(long seed) {
        return ProfessionRules.CLERIC_NETHER_VIRTUAL_MIN_TICKS
                + Math.floorMod(Long.hashCode(seed),
                ProfessionRules.CLERIC_NETHER_VIRTUAL_VARIATION_TICKS + 1);
    }

    private static boolean hasNetherObserver(WorldServer overworld,
                                             BlockPos portal) {
        WorldServer nether = Minecraft115WorldCompat.world(overworld.getMinecraftServer(), LegacyDimensionType.NETHER);
        if (nether == null) return false;
        double x = portal.getX() / 8.0D;
        double z = portal.getZ() / 8.0D;
        double radiusSqr = (double) ProfessionRules.CLERIC_NETHER_OBSERVER_RADIUS
                * ProfessionRules.CLERIC_NETHER_OBSERVER_RADIUS;
        for (EntityPlayerMP player : fr.vanillainstincts.compat.Minecraft112Compat.players(nether)) {
            if (!player.isSpectator()) {
                double dx = player.posX - x;
                double dz = player.posZ - z;
                if (dx * dx + dz * dz <= radiusSqr) return true;
            }
        }
        return false;
    }

    private static BlockPos tryBuildPortal(EntityVillager cleric,
                                           WorldServer level) {
        if (count(cleric.getVillagerInventory(), net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.obsidian))
                < requiredPortalObsidian()
                || findSlot(cleric.getVillagerInventory(), Items.flint_and_steel) < 0) {
            return null;
        }
        PortalPlan plan = findPortalSite(level, entityBlockPos(cleric));
        if (plan == null) return null;
        List<WorldPermissionService.BlockChange> changes = new ArrayList<>();
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                boolean frame = (x == 0 || x == 3) && y >= 1 && y <= 3
                        || (y == 0 || y == 4) && x >= 1 && x <= 2;
                boolean interior = x >= 1 && x <= 2 && y >= 1 && y <= 3;
                if (!frame && !interior) continue;
                BlockPos pos = fr.vanillainstincts.compat.Minecraft112Compat.offset(plan.base(), x, y, 0);
                IBlockState state = frame
                        ? Blocks.obsidian.getDefaultState()
                        : Blocks.portal.getDefaultState().withProperty(
                        BlockPortal.AXIS, EnumFacing.Axis.X);
                changes.add(new WorldPermissionService.BlockChange(pos, state,
                        3));
            }
        }
        if (!WorldPermissionService.setBlocksAtomically(level, cleric,
                changes, WorldActionType.PLACE_BLOCK)) {
            return null;
        }
        if (!consume(cleric.getVillagerInventory(), net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.obsidian),
                requiredPortalObsidian())) {
            // Inputs were checked before the atomic world change. This branch
            // is defensive; normal single-threaded server execution cannot
            // reach it.
            return null;
        }
        damageFlintAndSteel(cleric);
        cleric.swingItem();
        return fr.vanillainstincts.compat.Minecraft112Compat.offset(plan.base(), 1, 1, 0);
    }

    public static int requiredPortalObsidian() {
        return ProfessionRules.CLERIC_NETHER_REQUIRED_OBSIDIAN;
    }

    private static PortalPlan findPortalSite(WorldServer level,
                                             BlockPos origin) {
        int radius = ProfessionRules.CLERIC_NETHER_PORTAL_BUILD_RADIUS;
        for (int ring = 4; ring <= radius; ring += 2) {
            for (int dx = -ring; dx <= ring; dx += 2) {
                for (int dz = -ring; dz <= ring; dz += 2) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) continue;
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    int y = level.getHeight(new BlockPos(x, 0, z)).getY();
                    BlockPos base = new BlockPos(x, y, z);
                    if (portalSiteClear(level, base)) return new PortalPlan(base);
                }
            }
        }
        return null;
    }

    public static boolean portalSiteClear(WorldServer level, BlockPos base) {
        if (level == null || base == null) return false;
        for (int x = 0; x < 4; x++) {
            BlockPos ground = fr.vanillainstincts.compat.Minecraft112Compat.offset(base, x, -1, 0);
            if (!level.isBlockLoaded(ground)
                    || !fr.vanillainstincts.compat.Minecraft112Compat.isSolidRender(level.getBlockState(ground))) return false;
        }
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                BlockPos pos = fr.vanillainstincts.compat.Minecraft112Compat.offset(base, x, y, 0);
                if (!level.isBlockLoaded(pos)) return false;
                boolean relevant = (x == 0 || x == 3) && y >= 1 && y <= 3
                        || (y == 0 || y == 4) && x >= 1 && x <= 2
                        || x >= 1 && x <= 2 && y >= 1 && y <= 3;
                if (!relevant) continue;
                IBlockState state = level.getBlockState(pos);
                if (!fr.vanillainstincts.compat.Minecraft112Compat.isAir(state) && !state.getBlock().getMaterial().isReplaceable()) return false;
            }
        }
        return true;
    }

    private static void placeBridgeToward(EntityVillager cleric, WorldServer level,
                                          BlockPos target) {
        if (!cleric.getNavigator().noPath()) return;
        Item material = count(cleric.getVillagerInventory(), net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.cobblestone)) > 0
                ? net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.cobblestone)
                : count(cleric.getVillagerInventory(), net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.netherrack)) > 0
                ? net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.netherrack) : null;
        if (material == null) return;
        int dx = Integer.compare(target.getX(), entityBlockPos(cleric).getX());
        int dz = Integer.compare(target.getZ(), entityBlockPos(cleric).getZ());
        BlockPos support = fr.vanillainstincts.compat.Minecraft112Compat.offset(entityBlockPos(cleric), dx, -1, dz);
        if (!level.isBlockLoaded(support) || !fr.vanillainstincts.compat.Minecraft112Compat.isAir(level.getBlockState(support))) {
            return;
        }
        IBlockState state = material == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.cobblestone)
                ? Blocks.cobblestone.getDefaultState()
                : Blocks.netherrack.getDefaultState();
        if (WorldPermissionService.setBlock(level, cleric, support, state,
                3, WorldActionType.PLACE_BLOCK)) {
            consume(cleric.getVillagerInventory(), material, 1);
            cleric.swingItem();
        }
    }

    private static BlockPos findLoadedSpawner(WorldServer level,
                                              BlockPos center, int radius) {
        int vertical = Math.min(20, radius);
        for (int ring = 0; ring <= radius; ring += 4) {
            for (int dx = -ring; dx <= ring; dx += 4) {
                for (int dz = -ring; dz <= ring; dz += 4) {
                    if (ring > 0 && Math.abs(dx) != ring
                            && Math.abs(dz) != ring) continue;
                    BlockPos column = fr.vanillainstincts.compat.Minecraft112Compat.offset(center, dx, 0, dz);
                    if (!level.isBlockLoaded(column)) continue;
                    for (int dy = -vertical; dy <= vertical; dy++) {
                        BlockPos pos = fr.vanillainstincts.compat.Minecraft112Compat.offset(column, 0, dy, 0);
                        if (level.getBlockState(pos).getBlock().equals(Blocks.mob_spawner)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean needsNetherStock(InventoryBasic inventory) {
        return needsNetherStock(count(inventory, Items.blaze_rod),
                count(inventory, Items.nether_wart),
                count(inventory, Items.glowstone_dust));
    }

    public static boolean needsNetherStock(int blazeRods, int netherWart,
                                           int glowstoneDust) {
        return blazeRods < 3 || netherWart < 4 || glowstoneDust < 4;
    }

    private static void begin(EntityVillager cleric, BlockPos portal,
                              long gameTime) {
        storeHomePortal(cleric, portal);
        storePortal(cleric, portal);
        cleric.getEntityData().setInteger(PHASE, APPROACH);
        cleric.getEntityData().setLong(STARTED_AT, gameTime);
        cleric.getEntityData().setInteger(BLAZE_KILLS, 0);
        cleric.getEntityData().setInteger(BARTERS, 0);
    }


    private static boolean forceReturnHome(EntityVillager cleric, WorldServer level,
                                           long gameTime) {
        WorldServer overworld = Minecraft115WorldCompat.world(level.getMinecraftServer(), LegacyDimensionType.OVERWORLD);
        BlockPos home = homePortalPos(cleric);
        if (overworld == null || home == null) return false;
        discardPendingPayment(cleric, level);
        EntityVillager returned = cleric;
        if (cleric.worldObj != overworld) {
            java.util.UUID clericId = cleric.getUniqueID();
            cleric.travelToDimension(LegacyDimensionType.OVERWORLD.id());
            Entity changed = overworld.getEntityFromUuid(clericId);
            if (!(changed instanceof EntityVillager)) return false;
            returned = (EntityVillager) changed;
        }
        fr.vanillainstincts.compat.Minecraft112Compat.teleport(returned, home.getX() + 0.5D, home.getY(),
                home.getZ() + 0.5D);
        returned.rotationYaw = cleric.rotationYaw;
        returned.rotationPitch = cleric.rotationPitch;
        finish(returned, overworld, gameTime);
        return true;
    }

    private static void finish(EntityVillager cleric, WorldServer level,
                               long gameTime) {
        discardPendingPayment(cleric, level);
        restore(cleric);
        cleric.setCurrentItemOrArmor(0, null);
        cleric.getEntityData().setLong(READY_AT,
                gameTime + ProfessionRules.CLERIC_NETHER_COOLDOWN_TICKS);
        clearTransient(cleric);
        fr.vanillainstincts.compat.Minecraft112Compat.broadcastEntityEvent(level, cleric, (byte) 14);
    }

    private static void clearMission(EntityVillager cleric, WorldServer level,
                                     long gameTime) {
        discardPendingPayment(cleric, level);
        restore(cleric);
        cleric.getEntityData().setLong(READY_AT,
                gameTime + ProfessionRules.CLERIC_NETHER_SCAN_TICKS);
        clearTransient(cleric);
    }

    private static void clearTransient(EntityVillager cleric) {
        cleric.getEntityData().removeTag(PHASE);
        cleric.getEntityData().removeTag(STARTED_AT);
        cleric.getEntityData().removeTag(RETURN_AT);
        cleric.getEntityData().removeTag(PORTAL_POS);
        cleric.getEntityData().removeTag(HOME_PORTAL_POS);
        cleric.getEntityData().removeTag(BLAZE_KILLS);
        cleric.getEntityData().removeTag(ACTION_AT);
        cleric.getEntityData().removeTag(BARTERS);
        cleric.getEntityData().removeTag(PIGLIN);
        cleric.getEntityData().removeTag(BARTER_READY_AT);
        cleric.getEntityData().removeTag(BARTER_PAYMENT);
    }

    private static void discardPendingPayment(EntityVillager cleric,
                                              WorldServer level) {
        if (level == null || !fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(
                cleric.getEntityData(), BARTER_PAYMENT)) return;
        Entity payment = level.getEntityFromUuid(
                fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(
                        cleric.getEntityData(), BARTER_PAYMENT));
        if (payment != null) fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(payment);
    }

    private static void restore(EntityVillager cleric) {
        fr.vanillainstincts.compat.Minecraft112Compat.setNoAi(cleric, false);
        cleric.setInvisible(false);
        fr.vanillainstincts.compat.Minecraft112Compat.setInvulnerable(cleric, false);
        cleric.setSilent(false);
    }

    private static boolean eligible(EntityVillager cleric) {
        return cleric != null && cleric.isEntityAlive() && !cleric.isChild()
                && !cleric.isTrading()
                && LegacyVillagerProfession.of(cleric)
                == LegacyVillagerProfession.CLERIC
                && LegacyVillagerProfession.level(cleric) >= 3;
    }

    private static int phase(EntityVillager cleric) {
        return cleric.getEntityData().getInteger(PHASE);
    }


    private static void storeHomePortal(EntityVillager cleric, BlockPos portal) {
        cleric.getEntityData().setLong(HOME_PORTAL_POS, portal.toLong());
    }

    private static BlockPos homePortalPos(EntityVillager cleric) {
        return cleric.getEntityData().hasKey(HOME_PORTAL_POS)
                ? BlockPos.fromLong(cleric.getEntityData()
                .getLong(HOME_PORTAL_POS)) : null;
    }

    private static void storePortal(EntityVillager cleric, BlockPos portal) {
        cleric.getEntityData().setLong(PORTAL_POS, portal.toLong());
    }

    private static BlockPos portalPos(EntityVillager cleric) {
        return cleric.getEntityData().hasKey(PORTAL_POS)
                ? BlockPos.fromLong(cleric.getEntityData().getLong(PORTAL_POS))
                : null;
    }

    private static int count(InventoryBasic inventory, Item item) {
        return ProfessionRecipeCatalog.countItem(inventory, item);
    }

    private static int findSlot(InventoryBasic inventory, Item item) {
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (inventory.getStackInSlot(i).getItem().equals(item)
                    && !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(inventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    private static boolean consume(InventoryBasic inventory, Item item,
                                   int amount) {
        if (count(inventory, item) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < inventory.getSizeInventory()
                && remaining > 0; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.getItem().equals(item)) continue;
            int removed = Math.min(remaining, stack.stackSize);
            fr.vanillainstincts.compat.Minecraft110ItemStackCompat.shrink(stack, removed);
            remaining -= removed;
        }
        inventory.markDirty();
        return remaining == 0;
    }

    private static void damageFlintAndSteel(EntityVillager cleric) {
        int slot = findSlot(cleric.getVillagerInventory(), Items.flint_and_steel);
        if (slot < 0) return;
        ItemStack tool = cleric.getVillagerInventory().getStackInSlot(slot);
        tool.setItemDamage(tool.getItemDamage() + 1);
        if (tool.getItemDamage() >= tool.getMaxDamage()) {
            fr.vanillainstincts.compat.Minecraft110ItemStackCompat.shrink(tool, 1);
        }
        cleric.getVillagerInventory().markDirty();
    }

    private static void insert(EntityVillager cleric, ItemStack stack) {
        if (stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return;
        ProfessionStockController.insert(cleric.getVillagerInventory(), stack);
        if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) fr.vanillainstincts.compat.Minecraft112Compat.spawnItem(cleric, stack);
        cleric.getVillagerInventory().markDirty();
    }

    private static class PortalPlan {
        private final BlockPos base;

        public BlockPos base() { return this.base; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PortalPlan)) return false;
            PortalPlan that = (PortalPlan) other;
            return java.util.Objects.equals(this.base, that.base);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.base); }

        @Override
        public String toString() {
            return "PortalPlan[" + "base=" + this.base + "]";
        }

        private PortalPlan(BlockPos base) {
            base = immutableBlockPos(base);
        
            this.base = base;
        }
    }
}
