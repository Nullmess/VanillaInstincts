package fr.vanillainstincts.world;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Portail spécial construit exclusivement en obsidienne pleureuse.
 *
 * <p>Les portails pleureurs utilisent un réseau séparé du réseau vanilla :
 * le rapport de coordonnées 8:1 est conservé, mais les sorties du Nether sont
 * placées dans une voie dédiée, suffisamment éloignée pour qu'un portail en
 * obsidienne normale construit juste à côté ne puisse jamais sélectionner la
 * même sortie. Les associations sont persistantes et un portail existant est
 * toujours réutilisé avant toute nouvelle construction.</p>
 */
public final class CryingObsidianPortalController {
    private static final int MAX_INTERIOR_WIDTH = 21;
    private static final int MAX_INTERIOR_HEIGHT = 21;
    private static final int ROOF_FRAME_Y = 128;
    private static final int VANILLA_PORTAL_SEARCH_RADIUS = 128;
    private static final int CRYING_NETWORK_OFFSET_X = 1_024;
    private static final int LOCAL_DISCOVERY_RADIUS = 32;
    private static final int BUILD_SEARCH_RADIUS = 32;

    private CryingObsidianPortalController() {
    }


    /**
     * Converts an ignition position inside a complete pure crying-obsidian
     * frame.  Runtime callers normally reach this only after vanilla FIRE has
     * actually been placed, so vanilla owns item use and this controller owns
     * only frame validation plus portal construction.
     */
    public static boolean tryActivateAtIgnitionPosition(ServerLevel level,
                                                         BlockPos ignitionPos) {
        if (level == null || ignitionPos == null || !isPortalDimension(level)) {
            return false;
        }
        PortalFrame frame = findIgnitableFrame(level, ignitionPos);
        if (frame == null) return false;
        if (!createPortalDirect(level, frame)) return false;
        VanillaInstincts.LOGGER.info(
                "Crying portal activated at {} in {} (axis={}, {}x{})",
                frame.bottomLeft(), level.dimension().identifier(),
                frame.axis(), frame.width(), frame.height());
        level.playSound(null, frame.bottomLeft(),
                SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS,
                1.0F, 0.85F + level.getRandom().nextFloat() * 0.2F);
        return true;
    }

    /**
     * Activates a pure crying-obsidian frame when vanilla has just placed
     * fire inside it. This follows the normal Minecraft ignition path, so it
     * also works when the fire came from flint and steel or a fire charge.
     */
    public static boolean tryActivateFromFire(ServerLevel level,
                                              BlockPos firePos) {
        if (level == null || firePos == null) return false;
        if (!isPortalDimension(level)) return false;
        BlockState state = level.getBlockState(firePos);
        if (!state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)) {
            return false;
        }
        return tryActivateAtIgnitionPosition(level, firePos);
    }


    /**
     * Remplace uniquement la destination du portail vanilla. Le délai de
     * transition, le cooldown et la détection d'entrée restent gérés par
     * Minecraft. Les deux sens du réseau pleureur sont pris en charge.
     */
    public static TeleportTransition createRoofTransition(
            ServerLevel sourceLevel, Entity entity, BlockPos portalPos) {
        if (sourceLevel == null || entity == null || portalPos == null) {
            return null;
        }
        BlockState portalState = sourceLevel.getBlockState(portalPos);
        if (!portalState.is(Blocks.NETHER_PORTAL)
                || !portalState.hasProperty(NetherPortalBlock.AXIS)) {
            return null;
        }
        Direction.Axis axis = portalState.getValue(NetherPortalBlock.AXIS);
        PortalFrame sourceFrame = findFrame(sourceLevel, portalPos, axis);
        if (sourceFrame == null || !sourceFrame.containsInterior(portalPos)) {
            return null;
        }

        VanillaInstincts.LOGGER.info(
                "Crying portal travel detected at {} in {} (axis={})",
                portalPos, sourceLevel.dimension().identifier(), axis);

        MinecraftServer server = sourceLevel.getServer();
        if (server == null) {
            VanillaInstincts.LOGGER.warn(
                    "Crying portal travel aborted: no MinecraftServer for {}",
                    sourceLevel.dimension().identifier());
            return null;
        }
        if (Level.OVERWORLD.equals(sourceLevel.dimension())) {
            return transitionFromOverworld(server, sourceLevel, entity,
                    sourceFrame);
        }
        if (Level.NETHER.equals(sourceLevel.dimension())) {
            return transitionFromNether(server, sourceLevel, entity,
                    sourceFrame);
        }
        return null;
    }

    private static TeleportTransition transitionFromOverworld(
            MinecraftServer server, ServerLevel overworld, Entity entity,
            PortalFrame sourceFrame) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) return null;

        CryingPortalLinkSavedData data =
                CryingPortalLinkSavedData.get(server);
        PortalDestination destination = exactLinkedDestination(nether, data,
                sourceFrame);

        BlockPos reference = sourceFrame.centerInterior();
        BlockPos target = new BlockPos(cryingNetherX(reference.getX()),
                ROOF_FRAME_Y, cryingNetherZ(reference.getZ()));
        if (destination == null) {
            destination = nearestRegisteredDestination(nether, data, target,
                    VANILLA_PORTAL_SEARCH_RADIUS);
        }
        if (destination == null) {
            destination = discoverRoofDestination(nether, target,
                    LOCAL_DISCOVERY_RADIUS);
        }
        if (destination == null) {
            Direction.Axis destinationAxis = sourceFrame.axis();
            BlockPos frameBase = findPortalSite(nether, target,
                    destinationAxis, BUILD_SEARCH_RADIUS, true);
            if (frameBase == null) {
                VanillaInstincts.LOGGER.warn(
                        "Crying portal could not find a clear Nether-roof site near {}",
                        target);
                return null;
            }
            if (!buildMinimumCryingPortal(nether, frameBase,
                    destinationAxis)) {
                VanillaInstincts.LOGGER.warn(
                        "Crying portal failed to build Nether-roof destination at {}",
                        frameBase);
                return null;
            }
            destination = new PortalDestination(frameBase,
                    destinationAxis);
        }

        data.putLink(sourceFrame.bottomLeft(), sourceFrame.axis(),
                destination.frameBase(), destination.axis());
        VanillaInstincts.LOGGER.info(
                "Crying portal route {} -> Nether roof {}",
                sourceFrame.bottomLeft(), destination.frameBase());
        return transition(nether,
                arrivalPosition(destination.frameBase(), destination.axis()),
                entity);
    }

    private static TeleportTransition transitionFromNether(
            MinecraftServer server, ServerLevel nether, Entity entity,
            PortalFrame netherFrame) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return null;

        CryingPortalLinkSavedData data =
                CryingPortalLinkSavedData.get(server);
        BlockPos netherBase = outerFrameBase(netherFrame);
        CryingPortalLinkSavedData.PortalLink sourceLink =
                linkedOverworldSource(overworld, data, netherBase,
                        netherFrame.axis());

        PortalFrame overworldFrame = sourceLink == null ? null
                : activeFrame(overworld, sourceLink.sourceBottomLeft(),
                sourceLink.sourceAxis());
        if (overworldFrame == null) {
            int targetX = cryingOverworldX(netherFrame.centerInterior().getX());
            int targetZ = cryingOverworldZ(netherFrame.centerInterior().getZ());
            Direction.Axis axis = netherFrame.axis();
            BlockPos target = new BlockPos(targetX,
                    overworld.getSeaLevel() + 1, targetZ);
            BlockPos frameBase = findPortalSite(overworld, target, axis,
                    BUILD_SEARCH_RADIUS, false);
            if (frameBase == null) {
                VanillaInstincts.LOGGER.warn(
                        "Crying portal could not find an Overworld return site near {}",
                        target);
                return null;
            }
            if (!buildMinimumCryingPortal(overworld, frameBase, axis)) {
                VanillaInstincts.LOGGER.warn(
                        "Crying portal failed to build Overworld return destination at {}",
                        frameBase);
                return null;
            }
            BlockPos interior = arrivalPosition(frameBase, axis);
            overworldFrame = findFrame(overworld, interior, axis);
            if (overworldFrame == null) return null;
            data.putLink(overworldFrame.bottomLeft(), overworldFrame.axis(),
                    netherBase, netherFrame.axis());
        }

        return transition(overworld, overworldFrame.bottomLeft(), entity);
    }

    private static TeleportTransition transition(ServerLevel destination,
                                                  BlockPos arrival,
                                                  Entity entity) {
        TeleportTransition.PostTeleportTransition afterTravel =
                TeleportTransition.PLAY_PORTAL_SOUND
                        .then(TeleportTransition.PLACE_PORTAL_TICKET)
                        .then(teleported -> teleported.setPortalCooldown());
        return new TeleportTransition(destination,
                Vec3.atBottomCenterOf(arrival), Vec3.ZERO,
                entity.getYRot(), entity.getXRot(), Set.of(), afterTravel);
    }

    private static PortalDestination exactLinkedDestination(
            ServerLevel nether, CryingPortalLinkSavedData data,
            PortalFrame sourceFrame) {
        CryingPortalLinkSavedData.PortalLink link = data.linkForSource(
                sourceFrame.bottomLeft());
        if (link == null || link.sourceAxis() != sourceFrame.axis()) {
            return null;
        }
        if (isReusablePortal(nether, link.netherFrameBase(),
                link.netherAxis())) {
            return new PortalDestination(link.netherFrameBase(),
                    link.netherAxis());
        }
        data.removeSource(sourceFrame.bottomLeft());
        return null;
    }

    private static PortalDestination nearestRegisteredDestination(
            ServerLevel nether, CryingPortalLinkSavedData data,
            BlockPos target, int radius) {
        long maxDistance = (long) radius * radius;
        long bestDistance = Long.MAX_VALUE;
        PortalDestination best = null;
        Set<String> checked = new HashSet<>();
        for (CryingPortalLinkSavedData.PortalLink link : data.links()) {
            String key = link.netherFrameBase().asLong() + ":"
                    + link.netherAxis().name();
            if (!checked.add(key)) continue;
            long distance = horizontalDistanceSquared(
                    link.netherFrameBase(), target);
            if (distance > maxDistance || distance >= bestDistance) continue;
            if (!isReusablePortal(nether, link.netherFrameBase(),
                    link.netherAxis())) {
                continue;
            }
            bestDistance = distance;
            best = new PortalDestination(link.netherFrameBase(),
                    link.netherAxis());
        }
        return best;
    }

    private static CryingPortalLinkSavedData.PortalLink linkedOverworldSource(
            ServerLevel overworld, CryingPortalLinkSavedData data,
            BlockPos netherBase, Direction.Axis netherAxis) {
        CryingPortalLinkSavedData.PortalLink best = null;
        long bestDistance = Long.MAX_VALUE;
        int expectedX = cryingOverworldX(netherBase.getX());
        int expectedZ = cryingOverworldZ(netherBase.getZ());
        BlockPos expected = new BlockPos(expectedX, 0, expectedZ);
        for (CryingPortalLinkSavedData.PortalLink link : data.links()) {
            if (!link.netherFrameBase().equals(netherBase)
                    || link.netherAxis() != netherAxis) {
                continue;
            }
            PortalFrame frame = activeFrame(overworld,
                    link.sourceBottomLeft(), link.sourceAxis());
            if (frame == null) continue;
            long distance = horizontalDistanceSquared(
                    frame.centerInterior(), expected);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = link;
            }
        }
        return best;
    }

    private static PortalDestination discoverRoofDestination(
            ServerLevel level, BlockPos target, int radius) {
        long bestDistance = Long.MAX_VALUE;
        PortalDestination best = null;
        Set<String> checked = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = ROOF_FRAME_Y + 1;
                     y <= ROOF_FRAME_Y + MAX_INTERIOR_HEIGHT + 2; y++) {
                    BlockPos sample = new BlockPos(target.getX() + dx, y,
                            target.getZ() + dz);
                    if (!level.getBlockState(sample).is(Blocks.NETHER_PORTAL)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(sample);
                    if (!state.hasProperty(NetherPortalBlock.AXIS)) continue;
                    Direction.Axis axis = state.getValue(NetherPortalBlock.AXIS);
                    PortalFrame frame = activeFrame(level, sample, axis);
                    if (frame == null) continue;
                    BlockPos base = outerFrameBase(frame);
                    if (base.getY() < ROOF_FRAME_Y) continue;
                    String key = base.asLong() + ":" + axis.name();
                    if (!checked.add(key)) continue;
                    long distance = horizontalDistanceSquared(base, target);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new PortalDestination(base, axis);
                    }
                }
            }
        }
        return best;
    }

    private static PortalFrame activeFrame(BlockGetter level,
                                           BlockPos interior,
                                           Direction.Axis axis) {
        if (level == null || interior == null
                || (axis != Direction.Axis.X && axis != Direction.Axis.Z)) {
            return null;
        }
        BlockState state = level.getBlockState(interior);
        if (!state.is(Blocks.NETHER_PORTAL)
                || !state.hasProperty(NetherPortalBlock.AXIS)
                || state.getValue(NetherPortalBlock.AXIS) != axis) {
            return null;
        }
        PortalFrame frame = findFrame(level, interior, axis);
        return frame != null && frame.containsInterior(interior)
                ? frame : null;
    }

    public static boolean isReusablePortal(BlockGetter level,
                                            BlockPos frameBase,
                                            Direction.Axis axis) {
        BlockPos interior = arrivalPosition(frameBase, axis);
        PortalFrame frame = activeFrame(level, interior, axis);
        return frame != null && outerFrameBase(frame).equals(frameBase);
    }

    public static int netherCoordinate(int overworldCoordinate) {
        return clampCoordinate(Math.floorDiv(overworldCoordinate, 8));
    }

    public static int cryingNetherX(int overworldX) {
        return clampCoordinate(netherCoordinate(overworldX)
                + CRYING_NETWORK_OFFSET_X);
    }

    public static int cryingNetherZ(int overworldZ) {
        return netherCoordinate(overworldZ);
    }

    public static int cryingOverworldX(int netherX) {
        long value = ((long) netherX - CRYING_NETWORK_OFFSET_X) * 8L;
        return clampCoordinate((int) Math.max(Integer.MIN_VALUE,
                Math.min(Integer.MAX_VALUE, value)));
    }

    public static int cryingOverworldZ(int netherZ) {
        long value = (long) netherZ * 8L;
        return clampCoordinate((int) Math.max(Integer.MIN_VALUE,
                Math.min(Integer.MAX_VALUE, value)));
    }

    public static int cryingNetworkOffset() {
        return CRYING_NETWORK_OFFSET_X;
    }

    public static int portalReuseRadius() {
        return VANILLA_PORTAL_SEARCH_RADIUS;
    }

    public static boolean isSeparatedFromVanillaNetwork() {
        return CRYING_NETWORK_OFFSET_X > VANILLA_PORTAL_SEARCH_RADIUS * 2;
    }

    public static boolean sourcePortalsCanShareDestination(BlockPos first,
                                                            BlockPos second) {
        if (first == null || second == null) return false;
        BlockPos firstTarget = new BlockPos(cryingNetherX(first.getX()), 0,
                cryingNetherZ(first.getZ()));
        BlockPos secondTarget = new BlockPos(cryingNetherX(second.getX()), 0,
                cryingNetherZ(second.getZ()));
        return horizontalDistanceSquared(firstTarget, secondTarget)
                <= (long) VANILLA_PORTAL_SEARCH_RADIUS
                * VANILLA_PORTAL_SEARCH_RADIUS;
    }

    public static int roofFrameY() {
        return ROOF_FRAME_Y;
    }

    public static int requiredFrameBlocks(int interiorWidth,
                                          int interiorHeight) {
        if (interiorWidth < 2 || interiorWidth > MAX_INTERIOR_WIDTH
                || interiorHeight < 3
                || interiorHeight > MAX_INTERIOR_HEIGHT) {
            return 0;
        }
        return (interiorWidth * 2) + (interiorHeight * 2);
    }

    /**
     * Returns true only for a fully intact active crying-obsidian portal.
     *
     * <p>The frame finder deliberately accepts air/fire while a portal is
     * being ignited. That is useful during construction, but it must not be
     * used to keep an already active portal alive after one of its interior
     * blocks has been broken. Requiring every interior cell to still contain
     * a portal block gives the same collapse behaviour as a vanilla portal:
     * one missing frame or portal block invalidates the whole surface.</p>
     */
    public static boolean isCryingPortal(BlockGetter level, BlockPos pos,
                                         Direction.Axis axis) {
        PortalFrame frame = findFrame(level, pos, axis);
        if (frame == null || !frame.containsInterior(pos)) return false;

        Direction right = right(axis);
        for (int y = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++) {
                BlockState state = level.getBlockState(
                        frame.bottomLeft().relative(right, x).above(y));
                if (!state.is(Blocks.NETHER_PORTAL)
                        || !state.hasProperty(NetherPortalBlock.AXIS)
                        || state.getValue(NetherPortalBlock.AXIS) != axis) {
                    return false;
                }
            }
        }
        return true;
    }

    public static PortalFrame findFrame(BlockGetter level, BlockPos candidate,
                                        Direction.Axis axis) {
        if (level == null || candidate == null
                || (axis != Direction.Axis.X && axis != Direction.Axis.Z)
                || !isInterior(level.getBlockState(candidate))) {
            return null;
        }
        Direction right = right(axis);

        BlockPos bottom = candidate;
        for (int step = 0; step < MAX_INTERIOR_HEIGHT; step++) {
            if (!isInterior(level.getBlockState(bottom.below()))) break;
            bottom = bottom.below();
        }

        BlockPos leftFrame = bottom.relative(right.getOpposite());
        int walked = 0;
        while (walked <= MAX_INTERIOR_WIDTH
                && isInterior(level.getBlockState(leftFrame))) {
            leftFrame = leftFrame.relative(right.getOpposite());
            walked++;
        }
        if (!level.getBlockState(leftFrame).is(Blocks.CRYING_OBSIDIAN)) {
            return null;
        }

        BlockPos bottomLeft = leftFrame.relative(right);
        int width = 0;
        while (width <= MAX_INTERIOR_WIDTH
                && isInterior(level.getBlockState(
                bottomLeft.relative(right, width)))) {
            width++;
        }
        if (width < 2 || width > MAX_INTERIOR_WIDTH
                || !level.getBlockState(bottomLeft.relative(right, width))
                .is(Blocks.CRYING_OBSIDIAN)) {
            return null;
        }

        for (int x = 0; x < width; x++) {
            if (!level.getBlockState(bottomLeft.relative(right, x).below())
                    .is(Blocks.CRYING_OBSIDIAN)) {
                return null;
            }
        }

        for (int height = 0; height <= MAX_INTERIOR_HEIGHT; height++) {
            boolean topRow = true;
            for (int x = 0; x < width; x++) {
                if (!level.getBlockState(bottomLeft.relative(right, x)
                        .above(height)).is(Blocks.CRYING_OBSIDIAN)) {
                    topRow = false;
                    break;
                }
            }
            if (topRow) {
                return height >= 3
                        ? new PortalFrame(bottomLeft.immutable(), axis,
                        width, height) : null;
            }

            if (height >= MAX_INTERIOR_HEIGHT
                    || !level.getBlockState(bottomLeft.relative(
                    right.getOpposite()).above(height))
                    .is(Blocks.CRYING_OBSIDIAN)
                    || !level.getBlockState(bottomLeft.relative(right, width)
                    .above(height)).is(Blocks.CRYING_OBSIDIAN)) {
                return null;
            }
            for (int x = 0; x < width; x++) {
                if (!isInterior(level.getBlockState(
                        bottomLeft.relative(right, x).above(height)))) {
                    return null;
                }
            }
        }
        return null;
    }

    public static BlockPos outerFrameBase(PortalFrame frame) {
        if (frame == null) return null;
        return frame.bottomLeft().relative(
                right(frame.axis()).getOpposite()).below();
    }

    public static BlockPos arrivalPosition(BlockPos frameBase,
                                           Direction.Axis axis) {
        if (frameBase == null) return null;
        return frameBase.relative(right(axis)).above();
    }

    /** Returns a pure crying frame only when the exact ignition position is
     * inside that frame. This mirrors vanilla portal ignition and prevents a
     * flint click merely near a portal from activating it. */
    private static PortalFrame findIgnitableFrame(BlockGetter level,
                                                   BlockPos ignitionPos) {
        if (level == null || ignitionPos == null
                || !isInterior(level.getBlockState(ignitionPos))) {
            return null;
        }
        for (Direction.Axis axis : List.of(Direction.Axis.X,
                Direction.Axis.Z)) {
            PortalFrame frame = findFrame(level, ignitionPos, axis);
            if (frame != null && frame.containsInterior(ignitionPos)) {
                return frame;
            }
        }
        return null;
    }

    private static boolean isPortalDimension(ServerLevel level) {
        return Level.OVERWORLD.equals(level.dimension())
                || Level.NETHER.equals(level.dimension());
    }

    private static boolean isInterior(BlockState state) {
        return state.isAir() || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.NETHER_PORTAL);
    }

    /**
     * Portal ignition is a vanilla-like block interaction, not an AI world
     * mutation.  Keep it independent from mob-griefing, spawn protection and
     * the mod's AI mutation permission gateway.
     */
    private static boolean createPortalDirect(ServerLevel level,
                                              PortalFrame frame) {
        return fillPortalDirectly(level, frame);
    }

    private static boolean fillPortalDirectly(ServerLevel level,
                                              PortalFrame frame) {
        if (level == null || frame == null) return false;
        Direction right = right(frame.axis());
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(NetherPortalBlock.AXIS, frame.axis());

        List<WorldPermissionService.BlockChange> changes = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int y = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++) {
                BlockPos pos = frame.bottomLeft().relative(right, x).above(y);
                positions.add(pos.immutable());
                changes.add(new WorldPermissionService.BlockChange(
                        pos.immutable(), portal, flags));
            }
        }

        // This is the continuation of vanilla FIRE placement, not an AI world
        // mutation. The dedicated atomic path keeps chunk/world-border safety
        // while deliberately ignoring mob-griefing and spawn protection.
        if (!WorldPermissionService.setVanillaTriggeredBlocksAtomically(
                level, changes)) {
            return false;
        }

        // Success means the whole field really survived construction.
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.NETHER_PORTAL)
                    || !state.hasProperty(NetherPortalBlock.AXIS)
                    || state.getValue(NetherPortalBlock.AXIS) != frame.axis()) {
                return false;
            }
        }
        return true;
    }


    private static int clampCoordinate(int value) {
        return Math.max(-29_999_000, Math.min(29_999_000, value));
    }

    private static BlockPos findPortalSite(ServerLevel level,
                                           BlockPos target,
                                           Direction.Axis axis,
                                           int radius,
                                           boolean fixedRoofY) {
        for (int searchRadius = 0; searchRadius <= radius; searchRadius++) {
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    if (searchRadius > 0
                            && Math.abs(dx) != searchRadius
                            && Math.abs(dz) != searchRadius) {
                        continue;
                    }
                    int x = target.getX() + dx;
                    int z = target.getZ() + dz;
                    int firstY = fixedRoofY ? ROOF_FRAME_Y
                            : level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            x, z);
                    int lastY = fixedRoofY
                            ? Math.min(level.getMaxY() - 5,
                            ROOF_FRAME_Y + 64)
                            : Math.min(level.getMaxY() - 5,
                            firstY + 16);
                    for (int y = firstY; y <= lastY; y++) {
                        BlockPos candidate = new BlockPos(x, y, z);
                        level.getChunkAt(candidate);
                        if (portalSiteClear(level, candidate, axis)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        int emergencyX = target.getX() + radius + 16;
        int emergencyZ = target.getZ();
        int emergencyY = fixedRoofY
                ? Math.min(level.getMaxY() - 5,
                ROOF_FRAME_Y + 80)
                : Math.min(level.getMaxY() - 5,
                Math.max(level.getSeaLevel() + 24,
                        level.getHeight(
                                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                emergencyX, emergencyZ) + 12));
        BlockPos emergency = new BlockPos(emergencyX, emergencyY,
                emergencyZ);
        return level.hasChunkAt(emergency)
                && portalSiteClear(level, emergency, axis)
                ? emergency : null;
    }

    public static boolean portalSiteClear(BlockGetter level, BlockPos base,
                                          Direction.Axis axis) {
        if (level == null || base == null
                || (axis != Direction.Axis.X && axis != Direction.Axis.Z)) {
            return false;
        }
        Direction right = right(axis);
        Direction normal = axis == Direction.Axis.X
                ? Direction.SOUTH : Direction.EAST;
        for (int along = -2; along <= 5; along++) {
            for (int y = 0; y <= 5; y++) {
                for (int depth = -2; depth <= 2; depth++) {
                    BlockPos sample = base.relative(right, along)
                            .relative(normal, depth).above(y);
                    BlockState state = level.getBlockState(sample);
                    if (!state.isAir() && !state.is(Blocks.FIRE)
                            && !state.is(Blocks.SOUL_FIRE)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean buildMinimumCryingPortal(ServerLevel level,
                                                     BlockPos base,
                                                     Direction.Axis axis) {
        if (isReusablePortal(level, base, axis)) return true;
        if (level == null || base == null
                || (axis != Direction.Axis.X && axis != Direction.Axis.Z)) {
            return false;
        }

        Direction right = right(axis);
        BlockState crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(NetherPortalBlock.AXIS, axis);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        List<WorldPermissionService.BlockChange> changes = new ArrayList<>();

        // Build the complete 4x5 outer frame, including all four corners.
        // The whole frame and portal field are applied without neighbour
        // shape propagation so vanilla cannot invalidate a half-built field.
        for (int x = 0; x <= 3; x++) {
            changes.add(new WorldPermissionService.BlockChange(
                    base.relative(right, x), crying, flags));
            changes.add(new WorldPermissionService.BlockChange(
                    base.relative(right, x).above(4), crying, flags));
        }
        for (int y = 1; y <= 3; y++) {
            changes.add(new WorldPermissionService.BlockChange(
                    base.above(y), crying, flags));
            changes.add(new WorldPermissionService.BlockChange(
                    base.relative(right, 3).above(y), crying, flags));
        }
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                changes.add(new WorldPermissionService.BlockChange(
                        base.relative(right, x).above(y), portal, flags));
            }
        }

        // Explicitly load every chunk touched by the complete frame. A frame
        // can straddle a chunk edge even when the chosen base chunk is loaded;
        // the atomic writer correctly refuses unloaded positions.
        for (WorldPermissionService.BlockChange change : changes) {
            level.getChunkAt(change.pos());
        }

        // Destination creation is a consequence of portal travel, not an AI
        // placement and not a player placing fourteen individual blocks.
        if (!WorldPermissionService.setVanillaTriggeredBlocksAtomically(
                level, changes)) {
            VanillaInstincts.LOGGER.warn(
                    "Failed to atomically build crying portal destination at {} in {}",
                    base, level.dimension().identifier());
            return false;
        }

        boolean valid = isReusablePortal(level, base, axis);
        if (!valid) {
            VanillaInstincts.LOGGER.warn(
                    "Crying portal destination did not survive construction at {} in {}",
                    base, level.dimension().identifier());
            return false;
        }
        VanillaInstincts.LOGGER.info(
                "Crying portal destination ready at {} in {} (axis={})",
                base, level.dimension().identifier(), axis);
        return true;
    }

    private static Direction right(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? Direction.SOUTH : Direction.EAST;
    }

    private static long horizontalDistanceSquared(BlockPos first,
                                                  BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private record PortalDestination(BlockPos frameBase,
                                     Direction.Axis axis) {
    }

    public record PortalFrame(BlockPos bottomLeft, Direction.Axis axis,
                              int width, int height) {
        public boolean containsInterior(BlockPos pos) {
            if (pos == null) return false;
            Direction right = CryingObsidianPortalController.right(axis);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (bottomLeft.relative(right, x).above(y).equals(pos)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public BlockPos centerInterior() {
            return bottomLeft.relative(
                    CryingObsidianPortalController.right(axis), width / 2)
                    .above(height / 2);
        }
    }
}
