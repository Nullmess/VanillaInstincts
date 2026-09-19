package fr.vanillainstincts.village;

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
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

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

    public static boolean contribute(Villager cleric,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerLevel level, long gameTime) {
        if (!eligible(cleric) || state.danger(gameTime) != null) return false;
        int phase = phase(cleric);
        if (phase == VIRTUAL || phase == LIVE || phase == RETURN) return true;
        if (!Level.OVERWORLD.equals(level.dimension())) return phase != IDLE;

        if (phase == IDLE) {
            if (gameTime < cleric.getPersistentData().getLong(READY_AT)
                    || Math.floorMod(gameTime + cleric.getId() * 37L,
                    ProfessionRules.CLERIC_NETHER_SCAN_TICKS) != 0L
                    || !needsNetherStock(cleric.getInventory())) {
                return false;
            }
            BlockPos portal = NetherPortalService.findNearestActivePortal(
                    level, cleric.blockPosition(),
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
        Vec3 destination = Vec3.atBottomCenterOf(portal);
        if (cleric.distanceToSqr(destination) > 2.25D) {
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

    public static void maintain(Villager cleric, ServerLevel level,
                                long gameTime) {
        int phase = phase(cleric);
        if (phase == IDLE) return;
        if (!eligible(cleric)) {
            restore(cleric);
            if (!Level.OVERWORLD.equals(level.dimension())
                    && homePortalPos(cleric) != null) {
                cleric.setInvulnerable(true);
                forceReturnHome(cleric, level, gameTime);
                return;
            }
            clearMission(cleric, level, gameTime);
            return;
        }

        if (phase == APPROACH && Level.NETHER.equals(level.dimension())) {
            cleric.getPersistentData().putInt(PHASE, LIVE);
            cleric.getPersistentData().putLong(STARTED_AT, gameTime);
            BlockPos arrival = NetherPortalService.findNearestActivePortal(
                    level, cleric.blockPosition(), 24, 24);
            if (arrival != null) storePortal(cleric, arrival);
            phase = LIVE;
        }
        if (phase == RETURN && Level.OVERWORLD.equals(level.dimension())) {
            finish(cleric, level, gameTime);
            return;
        }
        if (phase == VIRTUAL) {
            maintainVirtual(cleric, level, gameTime);
            return;
        }
        if (phase == LIVE && Level.NETHER.equals(level.dimension())) {
            maintainLive(cleric, level, gameTime);
            return;
        }
        if (phase == RETURN && Level.NETHER.equals(level.dimension())) {
            maintainReturn(cleric, level, gameTime);
        }
    }

    private static void maintainVirtual(Villager cleric, ServerLevel level,
                                        long gameTime) {
        cleric.getNavigation().stop();
        cleric.setNoAi(true);
        cleric.setInvisible(true);
        cleric.setInvulnerable(true);
        cleric.setSilent(true);
        if (gameTime < cleric.getPersistentData().getLong(RETURN_AT)) return;
        grantVirtualLoot(cleric, gameTime);
        BlockPos portal = portalPos(cleric);
        if (portal != null && level.hasChunkAt(portal)) {
            cleric.teleportTo(portal.getX() + 0.5D, portal.getY(),
                    portal.getZ() + 0.5D);
        }
        finish(cleric, level, gameTime);
    }

    private static void maintainLive(Villager cleric, ServerLevel level,
                                     long gameTime) {
        cleric.setInvulnerable(true);
        cleric.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                240, 0, true, false));
        cleric.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                240, 1, true, false));
        cleric.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                120, 1, true, false));
        if (cleric.getHealth() < cleric.getMaxHealth()) {
            cleric.heal(Math.min(4.0F,
                    cleric.getMaxHealth() - cleric.getHealth()));
        }
        collectNetherDrops(cleric, level);
        completePendingBarter(cleric, level, gameTime);

        long elapsed = gameTime - cleric.getPersistentData()
                .getLong(STARTED_AT);
        if (cleric.getPersistentData().getInt(BLAZE_KILLS)
                >= ProfessionRules.CLERIC_NETHER_REQUIRED_BLAZE_KILLS
                || elapsed >= ProfessionRules.CLERIC_NETHER_LIVE_TIMEOUT_TICKS) {
            cleric.getPersistentData().putInt(PHASE, RETURN);
            cleric.getPersistentData().putLong(RETURN_AT,
                    gameTime
                            + ProfessionRules.CLERIC_NETHER_RETURN_TIMEOUT_TICKS);
            cleric.getNavigation().stop();
            return;
        }

        if (gameTime >= cleric.getPersistentData().getLong(ACTION_AT)) {
            if (tryBarter(cleric, level, gameTime)) return;
            Blaze blaze = level.getEntitiesOfClass(Blaze.class,
                    cleric.getBoundingBox().inflate(
                            ProfessionRules.CLERIC_NETHER_LIVE_SEARCH_RADIUS),
                    Entity::isAlive).stream()
                    .min(Comparator.comparingDouble(cleric::distanceToSqr))
                    .orElse(null);
            if (blaze != null) {
                if (cleric.distanceToSqr(blaze) > 9.0D) {
                    cleric.getNavigation().moveTo(blaze,
                            ProfessionRules.CLERIC_NETHER_SPEED);
                    placeBridgeToward(cleric, level, blaze.blockPosition());
                } else {
                    cleric.setItemInHand(InteractionHand.MAIN_HAND,
                            new ItemStack(Items.IRON_SWORD));
                    cleric.swing(InteractionHand.MAIN_HAND);
                    boolean aliveBefore = blaze.isAlive();
                    blaze.hurt(level.damageSources().mobAttack(cleric),
                            blaze.getHealth() + 2.0F);
                    if (aliveBefore && !blaze.isAlive()) {
                        cleric.getPersistentData().putInt(BLAZE_KILLS,
                                cleric.getPersistentData().getInt(BLAZE_KILLS)
                                        + 1);
                        insert(cleric, new ItemStack(Items.BLAZE_ROD));
                    }
                }
                cleric.getPersistentData().putLong(ACTION_AT,
                        gameTime
                                + ProfessionRules.CLERIC_NETHER_COMBAT_COOLDOWN_TICKS);
                return;
            }

            BlockPos spawner = findLoadedSpawner(level,
                    cleric.blockPosition(),
                    ProfessionRules.CLERIC_NETHER_LIVE_SEARCH_RADIUS);
            if (spawner != null) {
                cleric.getNavigation().moveTo(spawner.getX() + 0.5D,
                        spawner.getY(), spawner.getZ() + 0.5D,
                        ProfessionRules.CLERIC_NETHER_SPEED);
                placeBridgeToward(cleric, level, spawner);
            } else {
                BlockPos portal = portalPos(cleric);
                if (portal != null) {
                    cleric.getNavigation().moveTo(portal.getX() + 0.5D,
                            portal.getY(), portal.getZ() + 0.5D,
                            ProfessionRules.CLERIC_NETHER_SPEED);
                }
            }
            cleric.getPersistentData().putLong(ACTION_AT, gameTime + 40L);
        }
    }

    private static void maintainReturn(Villager cleric, ServerLevel level,
                                       long gameTime) {
        cleric.setInvulnerable(true);
        cleric.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                240, 0, true, false));
        BlockPos portal = NetherPortalService.findNearestActivePortal(level,
                cleric.blockPosition(),
                ProfessionRules.CLERIC_NETHER_PORTAL_SEARCH_RADIUS, 32);
        if (portal == null) portal = portalPos(cleric);
        if (portal != null && level.hasChunkAt(portal)) {
            storePortal(cleric, portal);
            cleric.getNavigation().moveTo(portal.getX() + 0.5D, portal.getY(),
                    portal.getZ() + 0.5D,
                    ProfessionRules.CLERIC_NETHER_SPEED);
            placeBridgeToward(cleric, level, portal);
        }
        if (gameTime >= cleric.getPersistentData().getLong(RETURN_AT)) {
            forceReturnHome(cleric, level, gameTime);
        }
    }

    private static boolean tryBarter(Villager cleric, ServerLevel level,
                                     long gameTime) {
        if (cleric.getPersistentData().contains(PIGLIN)
                || count(cleric.getInventory(), Items.GOLD_INGOT) <= 0
                || cleric.getPersistentData().getInt(BARTERS) >= 2) {
            return false;
        }
        Piglin piglin = level.getEntitiesOfClass(Piglin.class,
                cleric.getBoundingBox().inflate(12.0D), Entity::isAlive)
                .stream().min(Comparator.comparingDouble(cleric::distanceToSqr))
                .orElse(null);
        if (piglin == null) return false;
        if (cleric.distanceToSqr(piglin) > 9.0D) {
            cleric.getNavigation().moveTo(piglin,
                    ProfessionRules.CLERIC_NETHER_SPEED);
            return true;
        }
        if (!consume(cleric.getInventory(), Items.GOLD_INGOT, 1)) return false;
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        cleric.setItemInHand(InteractionHand.MAIN_HAND, gold.copy());
        cleric.swing(InteractionHand.MAIN_HAND);
        ItemEntity payment = new ItemEntity(level, cleric.getX(),
                cleric.getY() + 0.7D, cleric.getZ(), gold);
        payment.setThrower(cleric.getUUID());
        // The gold is a visible payment token. Keeping it uncollectable avoids
        // running the vanilla piglin barter in parallel with this persistent
        // mission and therefore prevents duplicate rewards.
        payment.setNeverPickUp();
        payment.setDeltaMovement(piglin.position().subtract(cleric.position())
                .normalize().scale(0.22D).add(0.0D, 0.2D, 0.0D));
        level.addFreshEntity(payment);
        cleric.getPersistentData().putUUID(PIGLIN, piglin.getUUID());
        cleric.getPersistentData().putUUID(BARTER_PAYMENT,
                payment.getUUID());
        cleric.getPersistentData().putLong(BARTER_READY_AT,
                gameTime + ProfessionRules.CLERIC_NETHER_BARTER_WAIT_TICKS);
        return true;
    }

    private static void completePendingBarter(Villager cleric,
                                              ServerLevel level,
                                              long gameTime) {
        if (!cleric.getPersistentData().contains(PIGLIN)
                || gameTime < cleric.getPersistentData()
                .getLong(BARTER_READY_AT)) {
            return;
        }
        discardPendingPayment(cleric, level);
        int sequence = cleric.getPersistentData().getInt(BARTERS);
        ItemStack result = barterResult(cleric.getUUID().hashCode()
                + sequence * 31 + Long.hashCode(gameTime / 20L));
        insert(cleric, result);
        cleric.getPersistentData().putInt(BARTERS, sequence + 1);
        cleric.getPersistentData().remove(PIGLIN);
        cleric.getPersistentData().remove(BARTER_READY_AT);
        cleric.getPersistentData().remove(BARTER_PAYMENT);
        cleric.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static ItemStack barterResult(int seed) {
        return switch (Math.floorMod(seed, 8)) {
            case 0 -> new ItemStack(Items.ENDER_PEARL, 2);
            case 1 -> new ItemStack(Items.CRYING_OBSIDIAN, 2);
            case 2 -> new ItemStack(Items.GLOWSTONE_DUST, 4);
            case 3 -> new ItemStack(Items.QUARTZ, 5);
            case 4 -> new ItemStack(Items.MAGMA_CREAM, 2);
            case 5 -> new ItemStack(Items.SOUL_SAND, 4);
            case 6 -> new ItemStack(Items.FIRE_CHARGE, 2);
            default -> new ItemStack(Items.STRING, 6);
        };
    }

    private static void collectNetherDrops(Villager cleric,
                                           ServerLevel level) {
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class,
                cleric.getBoundingBox().inflate(3.0D), item -> item.isAlive()
                        && isNetherResource(item.getItem()))) {
            ItemStack stack = entity.getItem().copy();
            int original = stack.getCount();
            insert(cleric, stack);
            int inserted = original - stack.getCount();
            if (inserted <= 0) continue;
            if (stack.isEmpty()) entity.discard();
            else entity.setItem(stack);
        }
    }

    private static boolean isNetherResource(ItemStack stack) {
        return stack.is(Items.BLAZE_ROD) || stack.is(Items.NETHER_WART)
                || stack.is(Items.QUARTZ) || stack.is(Items.GLOWSTONE_DUST)
                || stack.is(Items.MAGMA_CREAM) || stack.is(Items.GHAST_TEAR)
                || stack.is(Items.SOUL_SAND) || stack.is(Items.ENDER_PEARL)
                || stack.is(Items.CRYING_OBSIDIAN)
                || stack.is(Items.FIRE_CHARGE);
    }

    private static void grantVirtualLoot(Villager cleric, long gameTime) {
        int seed = cleric.getUUID().hashCode()
                ^ Long.hashCode(gameTime / 20L);
        insert(cleric, new ItemStack(Items.BLAZE_ROD,
                2 + Math.floorMod(seed, 3)));
        insert(cleric, new ItemStack(Items.NETHER_WART,
                3 + Math.floorMod(seed >>> 3, 4)));
        insert(cleric, new ItemStack(Items.QUARTZ,
                4 + Math.floorMod(seed >>> 5, 5)));
        insert(cleric, new ItemStack(Items.GLOWSTONE_DUST,
                2 + Math.floorMod(seed >>> 7, 4)));
        int gold = Math.min(2, count(cleric.getInventory(), Items.GOLD_INGOT));
        for (int i = 0; i < gold; i++) {
            if (consume(cleric.getInventory(), Items.GOLD_INGOT, 1)) {
                insert(cleric, barterResult(seed + i * 17));
            }
        }
    }

    private static void virtualize(Villager cleric, ServerLevel level,
                                   BlockPos portal, long gameTime) {
        cleric.getNavigation().stop();
        storePortal(cleric, portal);
        cleric.getPersistentData().putInt(PHASE, VIRTUAL);
        cleric.getPersistentData().putLong(RETURN_AT,
                gameTime + virtualDuration(cleric.getUUID().hashCode()
                        ^ gameTime));
        cleric.getPersistentData().putLong(STARTED_AT, gameTime);
        cleric.setNoAi(true);
        cleric.setInvisible(true);
        cleric.setInvulnerable(true);
        cleric.setSilent(true);
        cleric.teleportTo(portal.getX() + 0.5D, portal.getY(),
                portal.getZ() + 0.5D);
    }

    public static int virtualDuration(long seed) {
        return ProfessionRules.CLERIC_NETHER_VIRTUAL_MIN_TICKS
                + Math.floorMod(Long.hashCode(seed),
                ProfessionRules.CLERIC_NETHER_VIRTUAL_VARIATION_TICKS + 1);
    }

    private static boolean hasNetherObserver(ServerLevel overworld,
                                             BlockPos portal) {
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        if (nether == null) return false;
        double x = portal.getX() / 8.0D;
        double z = portal.getZ() / 8.0D;
        double radiusSqr = (double) ProfessionRules.CLERIC_NETHER_OBSERVER_RADIUS
                * ProfessionRules.CLERIC_NETHER_OBSERVER_RADIUS;
        for (ServerPlayer player : nether.players()) {
            if (!player.isSpectator()) {
                double dx = player.getX() - x;
                double dz = player.getZ() - z;
                if (dx * dx + dz * dz <= radiusSqr) return true;
            }
        }
        return false;
    }

    private static BlockPos tryBuildPortal(Villager cleric,
                                           ServerLevel level) {
        if (count(cleric.getInventory(), Items.OBSIDIAN)
                < requiredPortalObsidian()
                || findSlot(cleric.getInventory(), Items.FLINT_AND_STEEL) < 0) {
            return null;
        }
        PortalPlan plan = findPortalSite(level, cleric.blockPosition());
        if (plan == null) return null;
        List<WorldPermissionService.BlockChange> changes = new ArrayList<>();
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                boolean frame = (x == 0 || x == 3) && y >= 1 && y <= 3
                        || (y == 0 || y == 4) && x >= 1 && x <= 2;
                boolean interior = x >= 1 && x <= 2 && y >= 1 && y <= 3;
                if (!frame && !interior) continue;
                BlockPos pos = plan.base().offset(x, y, 0);
                BlockState state = frame
                        ? Blocks.OBSIDIAN.defaultBlockState()
                        : Blocks.NETHER_PORTAL.defaultBlockState().setValue(
                        NetherPortalBlock.AXIS, Direction.Axis.X);
                changes.add(new WorldPermissionService.BlockChange(pos, state,
                        Block.UPDATE_ALL));
            }
        }
        if (!WorldPermissionService.setBlocksAtomically(level, cleric,
                changes, WorldActionType.PLACE_BLOCK)) {
            return null;
        }
        if (!consume(cleric.getInventory(), Items.OBSIDIAN,
                requiredPortalObsidian())) {
            // Inputs were checked before the atomic world change. This branch
            // is defensive; normal single-threaded server execution cannot
            // reach it.
            return null;
        }
        damageFlintAndSteel(cleric);
        cleric.swing(InteractionHand.MAIN_HAND);
        return plan.base().offset(1, 1, 0);
    }

    public static int requiredPortalObsidian() {
        return ProfessionRules.CLERIC_NETHER_REQUIRED_OBSIDIAN;
    }

    private static PortalPlan findPortalSite(ServerLevel level,
                                             BlockPos origin) {
        int radius = ProfessionRules.CLERIC_NETHER_PORTAL_BUILD_RADIUS;
        for (int ring = 4; ring <= radius; ring += 2) {
            for (int dx = -ring; dx <= ring; dx += 2) {
                for (int dz = -ring; dz <= ring; dz += 2) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) continue;
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    int y = level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos base = new BlockPos(x, y, z);
                    if (portalSiteClear(level, base)) return new PortalPlan(base);
                }
            }
        }
        return null;
    }

    public static boolean portalSiteClear(ServerLevel level, BlockPos base) {
        if (level == null || base == null) return false;
        for (int x = 0; x < 4; x++) {
            BlockPos ground = base.offset(x, -1, 0);
            if (!level.hasChunkAt(ground)
                    || !level.getBlockState(ground).isSolidRender(level,
                    ground)) return false;
        }
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                BlockPos pos = base.offset(x, y, 0);
                if (!level.hasChunkAt(pos)) return false;
                boolean relevant = (x == 0 || x == 3) && y >= 1 && y <= 3
                        || (y == 0 || y == 4) && x >= 1 && x <= 2
                        || x >= 1 && x <= 2 && y >= 1 && y <= 3;
                if (!relevant) continue;
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && !state.canBeReplaced()) return false;
            }
        }
        return true;
    }

    private static void placeBridgeToward(Villager cleric, ServerLevel level,
                                          BlockPos target) {
        if (!cleric.getNavigation().isStuck()) return;
        Item material = count(cleric.getInventory(), Items.COBBLESTONE) > 0
                ? Items.COBBLESTONE
                : count(cleric.getInventory(), Items.NETHERRACK) > 0
                ? Items.NETHERRACK : null;
        if (material == null) return;
        int dx = Integer.compare(target.getX(), cleric.blockPosition().getX());
        int dz = Integer.compare(target.getZ(), cleric.blockPosition().getZ());
        BlockPos support = cleric.blockPosition().offset(dx, -1, dz);
        if (!level.hasChunkAt(support) || !level.getBlockState(support).isAir()) {
            return;
        }
        BlockState state = material == Items.COBBLESTONE
                ? Blocks.COBBLESTONE.defaultBlockState()
                : Blocks.NETHERRACK.defaultBlockState();
        if (WorldPermissionService.setBlock(level, cleric, support, state,
                Block.UPDATE_ALL, WorldActionType.PLACE_BLOCK)) {
            consume(cleric.getInventory(), material, 1);
            cleric.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static BlockPos findLoadedSpawner(ServerLevel level,
                                              BlockPos center, int radius) {
        int vertical = Math.min(20, radius);
        for (int ring = 0; ring <= radius; ring += 4) {
            for (int dx = -ring; dx <= ring; dx += 4) {
                for (int dz = -ring; dz <= ring; dz += 4) {
                    if (ring > 0 && Math.abs(dx) != ring
                            && Math.abs(dz) != ring) continue;
                    BlockPos column = center.offset(dx, 0, dz);
                    if (!level.hasChunkAt(column)) continue;
                    for (int dy = -vertical; dy <= vertical; dy++) {
                        BlockPos pos = column.offset(0, dy, 0);
                        if (level.getBlockState(pos).is(Blocks.SPAWNER)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean needsNetherStock(SimpleContainer inventory) {
        return needsNetherStock(count(inventory, Items.BLAZE_ROD),
                count(inventory, Items.NETHER_WART),
                count(inventory, Items.GLOWSTONE_DUST));
    }

    public static boolean needsNetherStock(int blazeRods, int netherWart,
                                           int glowstoneDust) {
        return blazeRods < 3 || netherWart < 4 || glowstoneDust < 4;
    }

    private static void begin(Villager cleric, BlockPos portal,
                              long gameTime) {
        storeHomePortal(cleric, portal);
        storePortal(cleric, portal);
        cleric.getPersistentData().putInt(PHASE, APPROACH);
        cleric.getPersistentData().putLong(STARTED_AT, gameTime);
        cleric.getPersistentData().putInt(BLAZE_KILLS, 0);
        cleric.getPersistentData().putInt(BARTERS, 0);
    }


    private static boolean forceReturnHome(Villager cleric, ServerLevel level,
                                           long gameTime) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        BlockPos home = homePortalPos(cleric);
        if (overworld == null || home == null) return false;
        discardPendingPayment(cleric, level);
        boolean teleported = cleric.teleportTo(overworld, home.getX() + 0.5D,
                home.getY(), home.getZ() + 0.5D, Set.of(),
                cleric.getYRot(), cleric.getXRot());
        if (teleported) finish(cleric, overworld, gameTime);
        return teleported;
    }

    private static void finish(Villager cleric, ServerLevel level,
                               long gameTime) {
        discardPendingPayment(cleric, level);
        restore(cleric);
        cleric.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        cleric.getPersistentData().putLong(READY_AT,
                gameTime + ProfessionRules.CLERIC_NETHER_COOLDOWN_TICKS);
        clearTransient(cleric);
        level.broadcastEntityEvent(cleric, (byte) 14);
    }

    private static void clearMission(Villager cleric, ServerLevel level,
                                     long gameTime) {
        discardPendingPayment(cleric, level);
        restore(cleric);
        cleric.getPersistentData().putLong(READY_AT,
                gameTime + ProfessionRules.CLERIC_NETHER_SCAN_TICKS);
        clearTransient(cleric);
    }

    private static void clearTransient(Villager cleric) {
        cleric.getPersistentData().remove(PHASE);
        cleric.getPersistentData().remove(STARTED_AT);
        cleric.getPersistentData().remove(RETURN_AT);
        cleric.getPersistentData().remove(PORTAL_POS);
        cleric.getPersistentData().remove(HOME_PORTAL_POS);
        cleric.getPersistentData().remove(BLAZE_KILLS);
        cleric.getPersistentData().remove(ACTION_AT);
        cleric.getPersistentData().remove(BARTERS);
        cleric.getPersistentData().remove(PIGLIN);
        cleric.getPersistentData().remove(BARTER_READY_AT);
        cleric.getPersistentData().remove(BARTER_PAYMENT);
    }

    private static void discardPendingPayment(Villager cleric,
                                              ServerLevel level) {
        if (level == null || !cleric.getPersistentData()
                .hasUUID(BARTER_PAYMENT)) return;
        Entity payment = level.getEntity(cleric.getPersistentData()
                .getUUID(BARTER_PAYMENT));
        if (payment != null) payment.discard();
    }

    private static void restore(Villager cleric) {
        cleric.setNoAi(false);
        cleric.setInvisible(false);
        cleric.setInvulnerable(false);
        cleric.setSilent(false);
    }

    private static boolean eligible(Villager cleric) {
        return cleric != null && cleric.isAlive() && !cleric.isBaby()
                && !cleric.isTrading()
                && cleric.getVillagerData().getProfession()
                == VillagerProfession.CLERIC
                && cleric.getVillagerData().getLevel() >= 3;
    }

    private static int phase(Villager cleric) {
        return cleric.getPersistentData().getInt(PHASE);
    }


    private static void storeHomePortal(Villager cleric, BlockPos portal) {
        cleric.getPersistentData().putLong(HOME_PORTAL_POS, portal.asLong());
    }

    private static BlockPos homePortalPos(Villager cleric) {
        return cleric.getPersistentData().contains(HOME_PORTAL_POS)
                ? BlockPos.of(cleric.getPersistentData()
                .getLong(HOME_PORTAL_POS)) : null;
    }

    private static void storePortal(Villager cleric, BlockPos portal) {
        cleric.getPersistentData().putLong(PORTAL_POS, portal.asLong());
    }

    private static BlockPos portalPos(Villager cleric) {
        return cleric.getPersistentData().contains(PORTAL_POS)
                ? BlockPos.of(cleric.getPersistentData().getLong(PORTAL_POS))
                : null;
    }

    private static int count(SimpleContainer inventory, Item item) {
        return ProfessionRecipeCatalog.countItem(inventory, item);
    }

    private static int findSlot(SimpleContainer inventory, Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)
                    && !inventory.getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private static boolean consume(SimpleContainer inventory, Item item,
                                   int amount) {
        if (count(inventory, item) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize()
                && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(item)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        inventory.setChanged();
        return remaining == 0;
    }

    private static void damageFlintAndSteel(Villager cleric) {
        int slot = findSlot(cleric.getInventory(), Items.FLINT_AND_STEEL);
        if (slot < 0) return;
        ItemStack tool = cleric.getInventory().getItem(slot);
        tool.setDamageValue(tool.getDamageValue() + 1);
        if (tool.getDamageValue() >= tool.getMaxDamage()) {
            tool.shrink(1);
        }
        cleric.getInventory().setChanged();
    }

    private static void insert(Villager cleric, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ProfessionStockController.insert(cleric.getInventory(), stack);
        if (!stack.isEmpty()) cleric.spawnAtLocation(stack);
        cleric.getInventory().setChanged();
    }

    private record PortalPlan(BlockPos base) {
        private PortalPlan {
            base = base.immutable();
        }
    }
}
