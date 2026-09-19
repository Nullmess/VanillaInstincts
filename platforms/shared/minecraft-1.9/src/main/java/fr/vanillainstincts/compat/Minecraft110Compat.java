package fr.vanillainstincts.compat;

import java.lang.reflect.Method;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/** Compatibility helpers for the native Minecraft 1.10 target. */
public final class Minecraft110Compat {
    private static final String EMULATED_NO_GRAVITY =
            "vanillainstincts_1_10_no_gravity";

    private Minecraft110Compat() {}

    /**
     * 1.10 still stores the Wither Skeleton as a skeleton variant.  The exact
     * MCP name changed around this release, so avoid a compile-time dependency
     * on one mapping snapshot.
     */
    public static boolean isWitherSkeleton(EntitySkeleton skeleton) {
        if (skeleton == null) return false;
        Object type = invokeNoArg(skeleton,
                "getSkeletonType", "func_189771_df", "func_82202_m");
        if (type instanceof Number) return ((Number) type).intValue() == 1;
        if (type instanceof Enum<?>) return ((Enum<?>) type).ordinal() == 1;
        return false;
    }

    /** In 1.10 the zombie villager is a flag on EntityZombie. */
    public static boolean isZombieVillager(EntityZombie zombie) {
        return zombie != null && zombie.isVillager();
    }

    /**
     * World#getBiome(BlockPos) was not exposed under that MCP name by the
     * historical 1.10 workspace. Resolve the equivalent method without tying
     * the source tree to one CSV snapshot.
     */
    public static BiomeGenBase biome(World world, BlockPos pos) {
        if (world == null || pos == null) return plains();
        Object biome = invokeOneArg(world, BlockPos.class, pos,
                "getBiome", "getBiomeGenForCoords", "func_180494_b");
        return biome instanceof BiomeGenBase ? (BiomeGenBase) biome : plains();
    }

    private static BiomeGenBase plains() {
        BiomeGenBase biome = LegacyRegistry.BIOME.get(
                new ResourceLocation("minecraft:plains"));
        if (biome != null) return biome;
        for (BiomeGenBase candidate : LegacyRegistry.BIOME) return candidate;
        return null;
    }

    /** Temperature bridge for the 1.9 BiomeGenBase MCP surface. */
    public static double biomeTemperature(World world, BlockPos pos) {
        BiomeGenBase biome = biome(world, pos);
        if (biome == null) return 0.8D;
        Object value = invokeOneArg(biome, BlockPos.class, pos,
                "getFloatTemperature", "getTemperature", "func_180626_a");
        if (value instanceof Number) return ((Number) value).doubleValue();
        Object noArg = invokeNoArg(biome, "getTemperature", "func_185355_j");
        if (noArg instanceof Number) return ((Number) noArg).doubleValue();
        try {
            java.lang.reflect.Field field = biome.getClass().getField("temperature");
            Object raw = field.get(biome);
            if (raw instanceof Number) return ((Number) raw).doubleValue();
        } catch (ReflectiveOperationException | SecurityException ignored) { }
        return 0.8D;
    }

    /** Center of an AABB for mappings where AxisAlignedBB#getCenter is absent. */
    public static Vec3d boxCenter(AxisAlignedBB box) {
        if (box == null) return Vec3d.ZERO;
        return new Vec3d((box.minX + box.maxX) * 0.5D,
                (box.minY + box.maxY) * 0.5D,
                (box.minZ + box.maxZ) * 0.5D);
    }

    /**
     * setNoGravity exists in later 1.10.x mappings but not in the native 1.10
     * compile surface. Invoke it when present; otherwise keep a local marker
     * and prevent an already-attached entity from immediately falling.
     */
    public static void setNoGravity(Entity entity, boolean noGravity) {
        if (entity == null) return;
        if (invokeBooleanArg(entity, noGravity,
                "setNoGravity", "func_189654_d")) {
            return;
        }
        entity.getEntityData().setBoolean(EMULATED_NO_GRAVITY, noGravity);
        if (noGravity && entity.motionY < 0.0D) {
            entity.motionY = 0.0D;
            entity.velocityChanged = true;
        }
    }

    public static boolean emulatedNoGravity(Entity entity) {
        return entity != null
                && entity.getEntityData().getBoolean(EMULATED_NO_GRAVITY);
    }

    /** Registry-based block lookup avoids static fields absent from old MCP CSVs. */
    public static Block block(String id) {
        if (id == null || id.isEmpty()) return null;
        ResourceLocation key = new ResourceLocation(
                id.indexOf(':') >= 0 ? id : "minecraft:" + id);
        return LegacyRegistry.BLOCK.get(key);
    }

    public static boolean isBlock(IBlockState state, String id) {
        Block expected = block(id);
        return state != null && expected != null && state.getBlock() == expected;
    }

    public static boolean isBlockItem(ItemStack stack, String id) {
        if (stack == null || Minecraft110ItemStackCompat.isEmpty(stack)) {
            return false;
        }
        Block block = block(id);
        if (block == null || block == Blocks.AIR) return false;
        Item item = Item.getItemFromBlock(block);
        return item != null && stack.getItem() == item;
    }

    /**
     * NBTUtil block-state helpers are not available under the native 1.10 MCP
     * surface. Persist the registry id plus legacy metadata instead.
     */
    public static IBlockState readBlockState(NBTTagCompound tag) {
        if (tag == null) return Blocks.AIR.getDefaultState();
        String name = tag.getString("Name");
        Block block = block(name);
        if (block == null) return Blocks.AIR.getDefaultState();
        int meta = tag.getInteger("Meta");
        try {
            return block.getStateFromMeta(meta);
        } catch (RuntimeException ignored) {
            return block.getDefaultState();
        }
    }

    public static NBTTagCompound writeBlockState(NBTTagCompound tag,
                                                   IBlockState state) {
        NBTTagCompound out = tag == null ? new NBTTagCompound() : tag;
        IBlockState safe = state == null ? Blocks.AIR.getDefaultState() : state;
        ResourceLocation id = LegacyRegistry.BLOCK.getKey(safe.getBlock());
        out.setString("Name", id == null ? "minecraft:air" : id.toString());
        int meta = 0;
        try {
            meta = safe.getBlock().getMetaFromState(safe);
        } catch (RuntimeException ignored) { }
        out.setInteger("Meta", meta);
        return out;
    }

    private static Object invokeNoArg(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException | SecurityException ignored) { }
        }
        return null;
    }

    private static Object invokeOneArg(Object target, Class<?> argType,
                                       Object arg, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name, argType);
                method.setAccessible(true);
                return method.invoke(target, arg);
            } catch (ReflectiveOperationException | SecurityException ignored) { }
        }
        return null;
    }

    private static boolean invokeBooleanArg(Object target, boolean value,
                                            String... names) {
        if (target == null) return false;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name, boolean.class);
                method.setAccessible(true);
                method.invoke(target, value);
                return true;
            } catch (ReflectiveOperationException | SecurityException ignored) { }
        }
        return false;
    }
}
