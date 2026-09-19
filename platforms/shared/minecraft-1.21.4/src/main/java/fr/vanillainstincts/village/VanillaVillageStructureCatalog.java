package fr.vanillainstincts.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Catalogue vanilla. */
public final class VanillaVillageStructureCatalog {
    private static final Map<ServerLevel, Map<String, Catalog>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private VanillaVillageStructureCatalog() {
    }

    public static List<TemplatePlan> plans(ServerLevel level, String style) {
        Catalog catalog = catalog(level, style);
        return catalog == null ? List.of() : catalog.plans();
    }

    public static List<TemplatePlan> plansContaining(ServerLevel level,
            String style, Block block) {
        Catalog catalog = catalog(level, style);
        if (catalog == null || block == null) return List.of();
        return catalog.byBlock().getOrDefault(block, List.of());
    }

    public static TemplatePlan plan(ServerLevel level, ResourceLocation id) {
        if (level == null || id == null) return null;
        String path = id.getPath();
        String marker = "village/";
        int village = path.indexOf(marker);
        int houses = path.indexOf("/houses/");
        if (village < 0 || houses < 0) return null;
        String style = path.substring(village + marker.length(), houses);
        return plans(level, style).stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst().orElseGet(() -> read(level, id));
    }

    public static void clear(ServerLevel level) {
        CACHE.remove(level);
    }

    public static boolean isVanillaHouseId(ResourceLocation id) {
        if (id == null || !"minecraft".equals(id.getNamespace())) return false;
        String path = id.getPath();
        return path.startsWith("village/") && path.contains("/houses/");
    }

    public static RotatedPlan rotate(TemplatePlan plan, Rotation rotation) {
        return plan == null ? null : plan.rotated(rotation);
    }

    private static Catalog catalog(ServerLevel level, String style) {
        if (level == null || style == null || style.isBlank()) return null;
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(level, ignored -> new HashMap<>())
                    .computeIfAbsent(style, key -> discover(level, key));
        }
    }

    private static Catalog discover(ServerLevel level, String style) {
        LinkedHashMap<ResourceLocation, TemplatePlan> result =
                new LinkedHashMap<>();
        String relative = "village/" + style + "/houses/";
        for (String root : List.of("structure/", "structures/")) {
            String searchRoot = root + relative.substring(0,
                    relative.length() - 1);
            level.getServer().getResourceManager().listResources(searchRoot,
                            id -> id.getPath().endsWith(".nbt"))
                    .keySet().stream()
                    .map(VanillaVillageStructureCatalog::toStructureId)
                    .filter(VanillaVillageStructureCatalog::isVanillaHouseId)
                    .sorted()
                    .forEach(id -> {
                        TemplatePlan plan = read(level, id);
                        if (plan != null && !plan.blocks().isEmpty()) {
                            result.putIfAbsent(id, plan);
                        }
                    });
        }
        List<TemplatePlan> plans = List.copyOf(result.values());
        Map<Block, List<TemplatePlan>> byBlock = new HashMap<>();
        for (TemplatePlan plan : plans) {
            for (Block block : plan.blockTypes()) {
                byBlock.computeIfAbsent(block, ignored -> new ArrayList<>())
                        .add(plan);
            }
        }
        byBlock.replaceAll((block, values) -> List.copyOf(values));
        return new Catalog(plans, Map.copyOf(byBlock));
    }

    private static ResourceLocation toStructureId(ResourceLocation resource) {
        String path = resource.getPath();
        if (path.startsWith("structure/")) {
            path = path.substring("structure/".length());
        } else if (path.startsWith("structures/")) {
            path = path.substring("structures/".length());
        }
        if (path.endsWith(".nbt")) {
            path = path.substring(0, path.length() - 4);
        }
        return ResourceLocation.fromNamespaceAndPath(resource.getNamespace(),
                path);
    }

    private static TemplatePlan read(ServerLevel level, ResourceLocation id) {
        StructureTemplate template = level.getStructureManager().get(id)
                .orElse(null);
        if (template == null) return null;
        CompoundTag saved = template.save(new CompoundTag());
        ListTag palette = palette(saved);
        ListTag blocks = saved.getList("blocks", Tag.TAG_COMPOUND);
        if (palette.isEmpty() || blocks.isEmpty()) return null;

        HolderGetter<Block> blockLookup = level.registryAccess()
                .lookupOrThrow(Registries.BLOCK);
        List<BlockState> states = new ArrayList<>(palette.size());
        for (int index = 0; index < palette.size(); index++) {
            states.add(NbtUtils.readBlockState(blockLookup,
                    palette.getCompound(index)));
        }

        List<TemplateBlock> parsed = new ArrayList<>(blocks.size());
        Set<Block> types = new HashSet<>();
        int maxX = -1;
        int maxY = -1;
        int maxZ = -1;
        int bedBlocks = 0;
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag blockTag = blocks.getCompound(index);
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= states.size()) continue;
            BlockState state = states.get(stateIndex);
            // Air explicite
            if (state.is(Blocks.STRUCTURE_VOID)
                    || state.is(Blocks.STRUCTURE_BLOCK)
                    || state.is(Blocks.JIGSAW)) continue;
            ListTag position = blockTag.getList("pos", Tag.TAG_INT);
            if (position.size() < 3) continue;
            BlockPos relative = new BlockPos(position.getInt(0),
                    position.getInt(1), position.getInt(2));
            parsed.add(new TemplateBlock(relative, state,
                    lootTableFromBlockTag(blockTag),
                    lootTableSeedFromBlockTag(blockTag)));
            if (!state.isAir()) types.add(state.getBlock());
            if (state.getBlock() instanceof BedBlock) bedBlocks++;
            maxX = Math.max(maxX, relative.getX());
            maxY = Math.max(maxY, relative.getY());
            maxZ = Math.max(maxZ, relative.getZ());
        }
        parsed.sort(Comparator.comparingInt((TemplateBlock block) ->
                        block.relative().getY())
                .thenComparingInt(block -> block.relative().getX())
                .thenComparingInt(block -> block.relative().getZ()));
        int bedCount = (bedBlocks + 1) / 2;
        return new TemplatePlan(id, List.copyOf(parsed), Set.copyOf(types),
                maxX + 1, maxY + 1, maxZ + 1, bedCount);
    }

    private static ListTag palette(CompoundTag saved) {
        ListTag direct = saved.getList("palette", Tag.TAG_COMPOUND);
        if (!direct.isEmpty()) return direct;
        ListTag palettes = saved.getList("palettes", Tag.TAG_LIST);
        if (palettes.isEmpty() || !(palettes.get(0) instanceof ListTag first)) {
            return new ListTag();
        }
        return first;
    }

    private record Catalog(List<TemplatePlan> plans,
                           Map<Block, List<TemplatePlan>> byBlock) {
    }

    public static final class TemplatePlan {
        private final ResourceLocation id;
        private final List<TemplateBlock> blocks;
        private final Set<Block> blockTypes;
        private final int width;
        private final int height;
        private final int depth;
        private final int bedCount;
        private final Map<Rotation, RotatedPlan> rotations =
                new EnumMap<>(Rotation.class);

        private TemplatePlan(ResourceLocation id, List<TemplateBlock> blocks,
                Set<Block> blockTypes, int width, int height, int depth,
                int bedCount) {
            this.id = id;
            this.blocks = blocks;
            this.blockTypes = blockTypes;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.bedCount = bedCount;
        }

        public ResourceLocation id() {
            return id;
        }

        public List<TemplateBlock> blocks() {
            return blocks;
        }

        public Set<Block> blockTypes() {
            return blockTypes;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int depth() {
            return depth;
        }

        public int bedCount() {
            return bedCount;
        }

        public boolean contains(Block block) {
            return block != null && blockTypes.contains(block);
        }

        private synchronized RotatedPlan rotated(Rotation rotation) {
            Rotation effective = rotation == null ? Rotation.NONE : rotation;
            return rotations.computeIfAbsent(effective,
                    value -> buildRotation(this, value));
        }
    }

    private static RotatedPlan buildRotation(TemplatePlan plan,
                                              Rotation rotation) {
        List<TemplateBlock> raw = new ArrayList<>(plan.blocks().size());
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TemplateBlock block : plan.blocks()) {
            BlockPos transformed = StructureTemplate.transform(block.relative(),
                    Mirror.NONE, rotation, BlockPos.ZERO);
            BlockState state = block.state().rotate(rotation);
            raw.add(new TemplateBlock(transformed, state,
                    block.lootTable(), block.lootTableSeed()));
            minX = Math.min(minX, transformed.getX());
            minY = Math.min(minY, transformed.getY());
            minZ = Math.min(minZ, transformed.getZ());
            maxX = Math.max(maxX, transformed.getX());
            maxY = Math.max(maxY, transformed.getY());
            maxZ = Math.max(maxZ, transformed.getZ());
        }
        if (raw.isEmpty()) {
            return new RotatedPlan(plan.id(), rotation, List.of(), 0, 0, 0);
        }
        int offsetX = -minX;
        int offsetY = -minY;
        int offsetZ = -minZ;
        List<TemplateBlock> normalized = raw.stream()
                .map(block -> new TemplateBlock(block.relative().offset(
                        offsetX, offsetY, offsetZ), block.state(),
                        block.lootTable(), block.lootTableSeed()))
                .sorted(Comparator.comparingInt((TemplateBlock block) ->
                                block.relative().getY())
                        .thenComparingInt(block -> block.relative().getX())
                        .thenComparingInt(block -> block.relative().getZ()))
                .toList();
        return new RotatedPlan(plan.id(), rotation, normalized,
                maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    public record RotatedPlan(ResourceLocation id, Rotation rotation,
                              List<TemplateBlock> blocks,
                              int width, int height, int depth) {
    }

    public static ResourceLocation lootTableFromBlockTag(CompoundTag blockTag) {
        if (blockTag == null) return null;
        CompoundTag blockEntity = blockTag.getCompound("nbt");
        String value = blockEntity.getString("LootTable");
        if (value == null || value.isBlank()) return null;
        int separator = value.indexOf(':');
        try {
            return separator < 0
                    ? ResourceLocation.fromNamespaceAndPath("minecraft", value)
                    : ResourceLocation.fromNamespaceAndPath(
                    value.substring(0, separator),
                    value.substring(separator + 1));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static long lootTableSeedFromBlockTag(CompoundTag blockTag) {
        return blockTag == null ? 0L
                : blockTag.getCompound("nbt").getLong("LootTableSeed");
    }

    public record TemplateBlock(BlockPos relative, BlockState state,
                                ResourceLocation lootTable,
                                long lootTableSeed) {
        public TemplateBlock(BlockPos relative, BlockState state) {
            this(relative, state, null, 0L);
        }
    }
}
