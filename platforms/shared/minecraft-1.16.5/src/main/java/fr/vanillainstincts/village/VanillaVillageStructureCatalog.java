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
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.nbt.INBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.block.BlockState;
import net.minecraft.world.gen.feature.template.Template;

/** Catalogue vanilla. */
public final class VanillaVillageStructureCatalog {
    private static final Map<ServerWorld, Map<String, Catalog>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private VanillaVillageStructureCatalog() {
    }

    public static List<TemplatePlan> plans(ServerWorld level, String style) {
        Catalog catalog = catalog(level, style);
        return catalog == null ? fr.vanillainstincts.compat.LegacyJava8.listOf() : catalog.plans();
    }

    public static List<TemplatePlan> plansContaining(ServerWorld level,
            String style, Block block) {
        Catalog catalog = catalog(level, style);
        if (catalog == null || block == null) return fr.vanillainstincts.compat.LegacyJava8.listOf();
        return catalog.byBlock().getOrDefault(block, fr.vanillainstincts.compat.LegacyJava8.listOf());
    }

    public static TemplatePlan plan(ServerWorld level, ResourceLocation id) {
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

    public static void clear(ServerWorld level) {
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

    private static Catalog catalog(ServerWorld level, String style) {
        if (level == null || style == null || style.trim().isEmpty()) return null;
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(level, ignored -> new HashMap<>())
                    .computeIfAbsent(style, key -> discover(level, key));
        }
    }

    private static Catalog discover(ServerWorld level, String style) {
        LinkedHashMap<ResourceLocation, TemplatePlan> result =
                new LinkedHashMap<>();
        for (ResourceLocation id : candidateIds(style)) {
            TemplatePlan plan = read(level, id);
            if (plan != null && !plan.blocks().isEmpty()) {
                result.putIfAbsent(id, plan);
            }
        }
        List<TemplatePlan> plans =
                fr.vanillainstincts.compat.LegacyJava8.copyList(result.values());
        Map<Block, List<TemplatePlan>> byBlock = new HashMap<>();
        for (TemplatePlan plan : plans) {
            for (Block block : plan.blockTypes()) {
                byBlock.computeIfAbsent(block, ignored -> new ArrayList<>())
                        .add(plan);
            }
        }
        byBlock.replaceAll((block, values) ->
                fr.vanillainstincts.compat.LegacyJava8.copyList(values));
        return new Catalog(plans,
                fr.vanillainstincts.compat.LegacyJava8.copyMap(byBlock));
    }

    /**
     * Minecraft 1.16.x does not expose the server resource manager through the
     * same public API used by newer versions. Probe the vanilla village house
     * naming families through TemplateManager instead; missing templates are
     * simply ignored by read().
     */
    private static List<ResourceLocation> candidateIds(String style) {
        List<ResourceLocation> ids = new ArrayList<>();
        String[] names = {
                "small_house", "medium_house", "big_house",
                "armorer_house", "butcher_shop", "cartographer",
                "fisher_cottage", "fisher", "fletcher_house",
                "library", "mason_house", "masons_house", "mason",
                "shepherd_house", "shepherds_house", "stable",
                "tannery", "temple", "tool_smith", "toolsmith",
                "weaponsmith", "weaponsmith_house", "animal_pen"
        };
        for (String name : names) {
            for (int index = 1; index <= 5; index++) {
                ids.add(new ResourceLocation("minecraft",
                        "village/" + style + "/houses/" + style + "_"
                                + name + "_" + index));
            }
        }
        return ids;
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
        return new ResourceLocation(resource.getNamespace(),
                path);
    }

    private static TemplatePlan read(ServerWorld level, ResourceLocation id) {
        Template template;
        try {
            template = level.getStructureManager().get(id);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (template == null) return null;
        CompoundNBT saved = template.save(new CompoundNBT());
        ListNBT palette = palette(saved);
        ListNBT blocks = saved.getList("blocks", 10);
        if (palette.isEmpty() || blocks.isEmpty()) return null;

        List<BlockState> states = new ArrayList<>(palette.size());
        for (int index = 0; index < palette.size(); index++) {
            states.add(NBTUtil.readBlockState(palette.getCompound(index)));
        }

        List<TemplateBlock> parsed = new ArrayList<>(blocks.size());
        Set<Block> types = new HashSet<>();
        int maxX = -1;
        int maxY = -1;
        int maxZ = -1;
        int bedBlocks = 0;
        for (int index = 0; index < blocks.size(); index++) {
            CompoundNBT blockTag = blocks.getCompound(index);
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= states.size()) continue;
            BlockState state = states.get(stateIndex);
            // Air explicite
            if (state.is(Blocks.STRUCTURE_VOID)
                    || state.is(Blocks.STRUCTURE_BLOCK)
                    || state.is(Blocks.JIGSAW)) continue;
            ListNBT position = blockTag.getList("pos", 3);
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
        return new TemplatePlan(id, fr.vanillainstincts.compat.LegacyJava8.copyList(parsed), fr.vanillainstincts.compat.LegacyJava8.copySet(types),
                maxX + 1, maxY + 1, maxZ + 1, bedCount);
    }

    private static ListNBT palette(CompoundNBT saved) {
        ListNBT direct = saved.getList("palette", 10);
        if (!direct.isEmpty()) return direct;
        ListNBT palettes = saved.getList("palettes", 9);
        if (palettes.isEmpty() || !(palettes.get(0) instanceof ListNBT)) {
            return new ListNBT();
        } ListNBT first = (ListNBT) (palettes.get(0));
        return first;
    }

    private static class Catalog {
        private final List<TemplatePlan> plans;
        private final Map<Block, List<TemplatePlan>> byBlock;

        public Catalog(List<TemplatePlan> plans, Map<Block, List<TemplatePlan>> byBlock) {
            this.plans = plans;
            this.byBlock = byBlock;
        }

        public List<TemplatePlan> plans() { return this.plans; }

        public Map<Block, List<TemplatePlan>> byBlock() { return this.byBlock; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Catalog)) return false;
            Catalog that = (Catalog) other;
            return java.util.Objects.equals(this.plans, that.plans) && java.util.Objects.equals(this.byBlock, that.byBlock);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.plans, this.byBlock); }

        @Override
        public String toString() {
            return "Catalog[" + "plans=" + this.plans + ", " + "byBlock=" + this.byBlock + "]";
        }

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
            BlockPos transformed = Template.transform(block.relative(),
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
            return new RotatedPlan(plan.id(), rotation, fr.vanillainstincts.compat.LegacyJava8.listOf(), 0, 0, 0);
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
                .collect(java.util.stream.Collectors.toList());
        return new RotatedPlan(plan.id(), rotation, normalized,
                maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    public static class RotatedPlan {
        private final ResourceLocation id;
        private final Rotation rotation;
        private final List<TemplateBlock> blocks;
        private final int width;
        private final int height;
        private final int depth;

        public RotatedPlan(ResourceLocation id, Rotation rotation, List<TemplateBlock> blocks, int width, int height, int depth) {
            this.id = id;
            this.rotation = rotation;
            this.blocks = blocks;
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        public ResourceLocation id() { return this.id; }

        public Rotation rotation() { return this.rotation; }

        public List<TemplateBlock> blocks() { return this.blocks; }

        public int width() { return this.width; }

        public int height() { return this.height; }

        public int depth() { return this.depth; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RotatedPlan)) return false;
            RotatedPlan that = (RotatedPlan) other;
            return java.util.Objects.equals(this.id, that.id) && java.util.Objects.equals(this.rotation, that.rotation) && java.util.Objects.equals(this.blocks, that.blocks) && this.width == that.width && this.height == that.height && this.depth == that.depth;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.id, this.rotation, this.blocks, this.width, this.height, this.depth); }

        @Override
        public String toString() {
            return "RotatedPlan[" + "id=" + this.id + ", " + "rotation=" + this.rotation + ", " + "blocks=" + this.blocks + ", " + "width=" + this.width + ", " + "height=" + this.height + ", " + "depth=" + this.depth + "]";
        }

    }

    public static ResourceLocation lootTableFromBlockTag(CompoundNBT blockTag) {
        if (blockTag == null) return null;
        CompoundNBT blockEntity = blockTag.getCompound("nbt");
        String value = blockEntity.getString("LootTable");
        if (value == null || value.trim().isEmpty()) return null;
        int separator = value.indexOf(':');
        try {
            return separator < 0
                    ? new ResourceLocation("minecraft", value)
                    : new ResourceLocation(
                    value.substring(0, separator),
                    value.substring(separator + 1));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static long lootTableSeedFromBlockTag(CompoundNBT blockTag) {
        return blockTag == null ? 0L
                : blockTag.getCompound("nbt").getLong("LootTableSeed");
    }

    public static class TemplateBlock {
        private final BlockPos relative;
        private final BlockState state;
        private final ResourceLocation lootTable;
        private final long lootTableSeed;

        public TemplateBlock(BlockPos relative, BlockState state, ResourceLocation lootTable, long lootTableSeed) {
            this.relative = relative;
            this.state = state;
            this.lootTable = lootTable;
            this.lootTableSeed = lootTableSeed;
        }

        public BlockPos relative() { return this.relative; }

        public BlockState state() { return this.state; }

        public ResourceLocation lootTable() { return this.lootTable; }

        public long lootTableSeed() { return this.lootTableSeed; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TemplateBlock)) return false;
            TemplateBlock that = (TemplateBlock) other;
            return java.util.Objects.equals(this.relative, that.relative) && java.util.Objects.equals(this.state, that.state) && java.util.Objects.equals(this.lootTable, that.lootTable) && this.lootTableSeed == that.lootTableSeed;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.relative, this.state, this.lootTable, this.lootTableSeed); }

        @Override
        public String toString() {
            return "TemplateBlock[" + "relative=" + this.relative + ", " + "state=" + this.state + ", " + "lootTable=" + this.lootTable + ", " + "lootTableSeed=" + this.lootTableSeed + "]";
        }

        public TemplateBlock(BlockPos relative, BlockState state) {
            this(relative, state, null, 0L);
        }
    }
}
