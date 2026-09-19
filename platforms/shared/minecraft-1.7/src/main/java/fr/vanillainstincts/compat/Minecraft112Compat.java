package fr.vanillainstincts.compat;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockDoor;
import fr.vanillainstincts.compat.LegacyBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;
import java.util.Random;
import fr.vanillainstincts.compat.BlockPos;
import fr.vanillainstincts.compat.Vec3;
import fr.vanillainstincts.compat.LegacyRotation;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.village.MerchantRecipe;

/**
 * Mapping-neutral bridge for methods whose names/signatures changed after 1.12.
 * Keep 1.12, 1.12.1 and 1.12.2 gameplay sources on one common implementation.
 */
public final class Minecraft112Compat {
    private Minecraft112Compat() {}

    private static double x(Object value) {
        if (value instanceof Entity) return ((Entity) value).posX;
        if (value instanceof net.minecraft.util.Vec3) return ((net.minecraft.util.Vec3) value).xCoord;
        if (value instanceof BlockPos) return ((BlockPos) value).getX();
        return 0.0D;
    }
    private static double y(Object value) {
        if (value instanceof Entity) return ((Entity) value).posY;
        if (value instanceof net.minecraft.util.Vec3) return ((net.minecraft.util.Vec3) value).yCoord;
        if (value instanceof BlockPos) return ((BlockPos) value).getY();
        return 0.0D;
    }
    private static double z(Object value) {
        if (value instanceof Entity) return ((Entity) value).posZ;
        if (value instanceof net.minecraft.util.Vec3) return ((net.minecraft.util.Vec3) value).zCoord;
        if (value instanceof BlockPos) return ((BlockPos) value).getZ();
        return 0.0D;
    }

    public static double distanceSq(Object first, Object second) {
        double dx = x(first) - x(second);
        double dy = y(first) - y(second);
        double dz = z(first) - z(second);
        return dx * dx + dy * dy + dz * dz;
    }

    public static double distanceSq(Entity entity, double x, double y, double z) {
        if (entity == null) return Double.MAX_VALUE;
        double dx = entity.posX - x;
        double dy = entity.posY - y;
        double dz = entity.posZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static Random random(Object owner) {
        if (owner instanceof World) return ((World) owner).rand;
        if (owner instanceof Entity) {
            World world = ((Entity) owner).worldObj;
            return world == null ? new Random(0L) : world.rand;
        }
        return new Random(0L);
    }

    public static double lengthSq(net.minecraft.util.Vec3 value) {
        return value == null ? 0.0D : value.xCoord * value.xCoord + value.yCoord * value.yCoord + value.zCoord * value.zCoord;
    }

    public static double dot(net.minecraft.util.Vec3 first, net.minecraft.util.Vec3 second) {
        if (first == null || second == null) return 0.0D;
        return first.xCoord * second.xCoord + first.yCoord * second.yCoord + first.zCoord * second.zCoord;
    }

    public static Vec3 scale(net.minecraft.util.Vec3 value, double factor) {
        if (value == null) return new Vec3(0.0D, 0.0D, 0.0D);
        return new Vec3(value.xCoord * factor, value.yCoord * factor, value.zCoord * factor);
    }

    public static net.minecraft.util.AxisAlignedBB blockBox(BlockPos pos) {
        if (pos == null) return net.minecraft.util.AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1);
        return net.minecraft.util.AxisAlignedBB.getBoundingBox(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+1, pos.getY()+1, pos.getZ()+1);
    }

    public static boolean startRiding(Entity passenger, Entity vehicle) {
        if (passenger == null || vehicle == null) return false;
        passenger.mountEntity(vehicle);
        return passenger.ridingEntity == vehicle;
    }

    public static boolean isRainingAt(World world, BlockPos pos) {
        return world != null && pos != null && world.isRaining() && Minecraft17Compat.canSeeSky(world, pos);
    }

    public static net.minecraft.util.AxisAlignedBB expandBox(net.minecraft.util.AxisAlignedBB box, double amount) {
        return box == null ? null : box.expand(amount, amount, amount);
    }

    public static Vec3 multiply(net.minecraft.util.Vec3 value, double x, double y, double z) {
        if (value == null) return new Vec3(0.0D, 0.0D, 0.0D);
        return new Vec3(value.xCoord * x, value.yCoord * y, value.zCoord * z);
    }

    public static Vec3 add(net.minecraft.util.Vec3 value, double x, double y, double z) {
        if (value == null) return new Vec3(x, y, z);
        return new Vec3(value.xCoord + x, value.yCoord + y, value.zCoord + z);
    }

    public static BlockPos add(BlockPos value, int x, int y, int z) {
        return offset(value, x, y, z);
    }

    public static BlockPos offset(BlockPos pos, int x, int y, int z) {
        return pos == null ? BlockPos.ORIGIN : new BlockPos(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
    }

    public static BlockPos immutable(BlockPos pos) {
        return pos == null ? null : new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos rotate(BlockPos pos, LegacyRotation rotation) {
        if (pos == null) return BlockPos.ORIGIN;
        LegacyRotation effective = rotation == null ? LegacyRotation.NONE : rotation;
        switch (effective) {
            case CLOCKWISE_90:
                return new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case CLOCKWISE_180:
                return new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case COUNTERCLOCKWISE_90:
                return new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
            case NONE:
            default:
                return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        }
    }

    public static boolean closerThan(BlockPos first, BlockPos second, double radius) {
        return first != null && second != null && distanceSq(first, second) < radius * radius;
    }

    public static Vec3 motion(Entity entity) {
        return entity == null ? new Vec3(0.0D, 0.0D, 0.0D) : new Vec3(entity.motionX, entity.motionY, entity.motionZ);
    }

    public static void setMotion(Entity entity, double x, double y, double z) {
        if (entity == null) return;
        entity.motionX = x; entity.motionY = y; entity.motionZ = z;
        entity.velocityChanged = true;
    }

    public static void setMotion(Entity entity, net.minecraft.util.Vec3 motion) {
        if (motion == null) return;
        setMotion(entity, motion.xCoord, motion.yCoord, motion.zCoord);
    }

    public static boolean canSee(EntityLivingBase observer, Entity target) {
        return observer != null && target != null && observer.canEntityBeSeen(target);
    }

    // 1.12 villagers do not use the post-Village-and-Pillage bed sleeping state.
    public static boolean isSleeping(EntityVillager villager) { return false; }
    public static void stopSleeping(EntityVillager villager) { }

    public static boolean isAir(LegacyBlockState state) {
        return state == null || state.getBlock().getMaterial() == Material.air;
    }

    public static boolean isSolidRender(LegacyBlockState state) {
        return state != null && state.isFullCube();
    }

    /** In 1.12 villages are centered around valid wooden doors; bells do not exist yet. */
    public static boolean isVillageCenterMarker(LegacyBlockState state) {
        return state != null && state.getBlock() instanceof BlockDoor
                && state.getBlock().getMaterial() == Material.wood;
    }

    public static WorldServer[] levels(MinecraftServer server) {
        return server == null || server.worldServers == null ? new WorldServer[0] : server.worldServers;
    }

    public static void teleport(Entity entity, double x, double y, double z) {
        if (entity == null) return;
        entity.setLocationAndAngles(x, y, z, entity.rotationYaw, entity.rotationPitch);
    }

    public static void teleport(Entity entity, double x, double y, double z, float yaw, float pitch) {
        if (entity == null) return;
        entity.setLocationAndAngles(x, y, z, yaw, pitch);
    }

    public static boolean noCollision(World world, Entity entity, net.minecraft.util.AxisAlignedBB box) {
        return world != null && box != null && world.getCollidingBoundingBoxes(entity, box).isEmpty();
    }

    public static List<EntityPlayerMP> players(WorldServer level) {
        List<EntityPlayerMP> result = new ArrayList<>();
        if (level == null) return result;
        for (Object raw : level.playerEntities) {
            if (raw instanceof EntityPlayerMP) result.add((EntityPlayerMP) raw);
        }
        return result;
    }

    public static net.minecraft.entity.player.EntityPlayer player(WorldServer level, UUID id) {
        return Minecraft17Compat.player(level, id);
    }

    public static MerchantRecipeList offers(EntityVillager villager) {
        if (villager == null) return new MerchantRecipeList();
        MerchantRecipeList recipes = villager.getRecipes(null);
        return recipes == null ? new MerchantRecipeList() : recipes;
    }

    /** Typed view over the raw MerchantRecipeList exposed by MCP stable_18. */
    public static List<MerchantRecipe> offerList(EntityVillager villager) {
        List<MerchantRecipe> result = new ArrayList<>();
        MerchantRecipeList recipes = offers(villager);
        for (Object raw : recipes) {
            if (raw instanceof MerchantRecipe) result.add((MerchantRecipe) raw);
        }
        return result;
    }

    /** Typed facade over the raw World#getEntitiesWithinAABB surface in MCP stable_18. */
    public static <T extends Entity> List<T> entitiesWithinAABB(
            World world, Class<T> type, net.minecraft.util.AxisAlignedBB box) {
        return entitiesWithinAABB(world, type, box, null);
    }

    public static <T extends Entity> List<T> entitiesWithinAABB(
            World world, Class<T> type, net.minecraft.util.AxisAlignedBB box,
            java.util.function.Predicate<? super T> predicate) {
        List<T> result = new ArrayList<>();
        if (world == null || type == null || box == null) return result;
        java.util.List rawValues = world.getEntitiesWithinAABB(type, box);
        if (rawValues == null) return result;
        for (Object raw : rawValues) {
            if (!type.isInstance(raw)) continue;
            T value = type.cast(raw);
            if (predicate == null || predicate.test(value)) result.add(value);
        }
        return result;
    }

    public static void removeEntity(Entity entity) {
        if (entity != null) entity.setDead();
    }


    public static BlockPos offset(BlockPos pos, BlockPos delta) {
        if (pos == null) return BlockPos.ORIGIN;
        if (delta == null) return immutable(pos);
        return new BlockPos(pos.getX() + delta.getX(), pos.getY() + delta.getY(), pos.getZ() + delta.getZ());
    }

    public static int manhattan(BlockPos first, BlockPos second) {
        if (first == null || second == null) return Integer.MAX_VALUE;
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
    }

    public static long[] getLongArray(net.minecraft.nbt.NBTTagCompound tag, String key) {
        if (tag == null || key == null || !tag.hasKey(key, 11)) return new long[0];
        int[] packed = tag.getIntArray(key);
        int length = packed.length / 2;
        long[] result = new long[length];
        for (int index = 0; index < length; index++) {
            long high = ((long) packed[index * 2]) << 32;
            long low = packed[index * 2 + 1] & 0xffffffffL;
            result[index] = high | low;
        }
        return result;
    }

    public static void setLongArray(net.minecraft.nbt.NBTTagCompound tag, String key, long[] values) {
        if (tag == null || key == null) return;
        long[] safe = values == null ? new long[0] : values;
        int[] packed = new int[safe.length * 2];
        for (int index = 0; index < safe.length; index++) {
            packed[index * 2] = (int) (safe[index] >>> 32);
            packed[index * 2 + 1] = (int) safe[index];
        }
        tag.setIntArray(key, packed);
    }

    public static void lookAt(net.minecraft.entity.EntityLiving observer, Entity target,
                              float yaw, float pitch) {
        if (observer != null && target != null) {
            observer.getLookHelper().setLookPositionWithEntity(target, yaw, pitch);
        }
    }

    public static void lookAt(net.minecraft.entity.EntityLiving observer,
                              double x, double y, double z, float yaw, float pitch) {
        if (observer != null) {
            observer.getLookHelper().setLookPosition(x, y, z, yaw, pitch);
        }
    }

    public static void setPickupDelay(EntityItem item, int delay) {
        if (item != null) item.delayBeforeCanPickup = Math.max(0, delay);
    }

    public static void setItem(EntityItem entity, ItemStack stack) {
        if (entity != null) entity.setEntityItemStack(stack);
    }

    public static boolean give(EntityPlayerMP player, ItemStack stack) {
        return player != null && stack != null && player.inventory.addItemStackToInventory(stack);
    }

    public static void drop(EntityPlayerMP player, ItemStack stack) {
        if (player != null && !Minecraft110ItemStackCompat.isEmpty(stack)) player.dropPlayerItemWithRandomChoice(stack, false);
    }

    public static void setInvulnerable(Entity entity, boolean value) {
        if (entity != null) entity.getEntityData().setBoolean("vanillainstincts_invulnerable_hint", value);
    }

    public static void setNoAi(net.minecraft.entity.EntityLiving entity, boolean value) {
        if (entity == null) return;
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        entity.writeToNBT(tag);
        tag.setBoolean("NoAI", value);
        entity.readFromNBT(tag);
    }

    /** 1.12 has no generic aggressive-state flag; combat intent is represented by attack targets. */
    public static void setAggressive(net.minecraft.entity.EntityLiving entity, boolean value) {
        // Deliberately no-op: callers still set/clear the attack target explicitly.
    }

    public static void addEffect(EntityLivingBase entity, net.minecraft.potion.PotionEffect effect) {
        if (entity != null && effect != null) entity.addPotionEffect(effect);
    }

    public static void broadcastEntityEvent(World world, Entity entity, byte state) {
        if (world != null && entity != null) world.setEntityState(entity, state);
    }

    public static EntityItem spawnItem(Entity entity, ItemStack stack) {
        if (entity == null || Minecraft110ItemStackCompat.isEmpty(stack)) return null;
        return entity.entityDropItem(stack, 0.0F);
    }
    public static double length(net.minecraft.util.Vec3 value) {
        return Math.sqrt(Math.max(0.0D, lengthSq(value)));
    }

    public static int facingX(fr.vanillainstincts.compat.EnumFacing facing) {
        if (facing == null) return 0;
        switch (facing) {
            case EAST: return 1;
            case WEST: return -1;
            default: return 0;
        }
    }

    public static int facingY(fr.vanillainstincts.compat.EnumFacing facing) {
        if (facing == null) return 0;
        switch (facing) {
            case UP: return 1;
            case DOWN: return -1;
            default: return 0;
        }
    }

    public static int facingZ(fr.vanillainstincts.compat.EnumFacing facing) {
        if (facing == null) return 0;
        switch (facing) {
            case SOUTH: return 1;
            case NORTH: return -1;
            default: return 0;
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        if (target == null || name == null) return null;
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(name, types);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeAny(Object target, String[] names, Class<?>[] types, Object... args) {
        for (String name : names) {
            Object result = invoke(target, name, types, args);
            if (result != null) return result;
        }
        return null;
    }

    public static BlockPos navigationTarget(net.minecraft.entity.EntityLiving actor) {
        if (actor == null) return null;
        Object navigator = actor.getNavigator();
        Object path = invoke(navigator, "getPath", new Class<?>[0]);
        Object point = invokeAny(path, new String[]{"getFinalPathPoint", "getFinalPathPoint"}, new Class<?>[0]);
        if (point != null) {
            try {
                java.lang.reflect.Field fx = point.getClass().getField("xCoord");
                java.lang.reflect.Field fy = point.getClass().getField("yCoord");
                java.lang.reflect.Field fz = point.getClass().getField("zCoord");
                return new BlockPos(fx.getInt(point), fy.getInt(point), fz.getInt(point));
            } catch (ReflectiveOperationException ignored) { }
        }
        EntityLivingBase target = actor.getAttackTarget();
        return target == null ? new BlockPos(actor) : new BlockPos(target);
    }

    public static void setBreakDoors(net.minecraft.entity.EntityLiving actor, boolean value) {
        if (actor == null) return;
        String[] names = {"setBreakDoorsAItask", "setCanBreakDoors"};
        for (String name : names) {
            try {
                java.lang.reflect.Method method = actor.getClass().getMethod(name, boolean.class);
                method.setAccessible(true);
                method.invoke(actor, value);
                return;
            } catch (ReflectiveOperationException ignored) { }
        }
    }

    public static net.minecraft.pathfinding.Path pathTo(net.minecraft.entity.EntityLiving actor, BlockPos pos) {
        if (actor == null || pos == null) return null;
        Object navigator = actor.getNavigator();
        Object path = invokeAny(navigator,
                new String[]{"getPathToPos", "getPathToXYZ", "createPath"},
                new Class<?>[]{BlockPos.class}, pos);
        return path instanceof net.minecraft.pathfinding.Path ? (net.minecraft.pathfinding.Path) path : null;
    }

    public static boolean pathCanReach(net.minecraft.pathfinding.Path path) {
        return path != null;
    }

    public static boolean moveToEntity(net.minecraft.entity.EntityLiving actor, Entity target, double speed) {
        if (actor == null || target == null) return false;
        Object result = invokeAny(actor.getNavigator(),
                new String[]{"tryMoveToEntityLiving", "tryMoveToEntity"},
                new Class<?>[]{Entity.class, double.class}, target, speed);
        if (result instanceof Boolean) return (Boolean) result;
        return actor.getNavigator().tryMoveToXYZ(target.posX, target.posY, target.posZ, speed);
    }

    public static boolean setPath(net.minecraft.entity.EntityLiving actor,
                                  net.minecraft.pathfinding.Path path, double speed) {
        if (actor == null || path == null) return false;
        Object result = invoke(actor.getNavigator(), "setPath",
                new Class<?>[]{net.minecraft.pathfinding.Path.class, double.class}, path, speed);
        return !(result instanceof Boolean) || (Boolean) result;
    }

    public static boolean hasCollision(LegacyBlockState state, World world, BlockPos pos) {
        if (state == null || world == null || pos == null || state.getBlock().getMaterial() == Material.air) return false;
        net.minecraft.util.AxisAlignedBB aabb = state.getCollisionBoundingBox(world, pos);
        if (aabb == null) return false;
        return aabb.maxX > aabb.minX && aabb.maxY > aabb.minY && aabb.maxZ > aabb.minZ;
    }

    public static boolean entityCanStandOn(LegacyBlockState state, World world, BlockPos pos, Entity entity) {
        return hasCollision(state, world, pos);
    }

    public static boolean isAllied(Entity first, Entity second) {
        if (first == null || second == null) return false;
        Object result = invokeAny(first, new String[]{"isOnSameTeam", "isAlliedTo"},
                new Class<?>[]{Entity.class}, second);
        return result instanceof Boolean && (Boolean) result;
    }

    public static boolean canAttack(net.minecraft.entity.EntityLiving actor, EntityLivingBase target) {
        return actor != null && target != null && target.isEntityAlive() && !isAllied(actor, target);
    }

    public static boolean isTamed(Entity entity) {
        Object result = invokeAny(entity, new String[]{"isTamed", "isTame"}, new Class<?>[0]);
        return result instanceof Boolean && (Boolean) result;
    }

    public static boolean isPassenger(Entity entity) {
        Object result = invokeAny(entity, new String[]{"isRiding", "isPassenger"}, new Class<?>[0]);
        return result instanceof Boolean && (Boolean) result;
    }

    public static boolean isVehicle(Entity entity) {
        Object result = invokeAny(entity, new String[]{"isBeingRidden", "isVehicle"}, new Class<?>[0]);
        return result instanceof Boolean && (Boolean) result;
    }

    public static void dismount(Entity entity) {
        if (entity == null) return;
        invokeAny(entity, new String[]{"dismountRidingEntity", "stopRiding"}, new Class<?>[0]);
    }

    public static boolean creeperPowered(net.minecraft.entity.monster.EntityCreeper creeper) {
        Object result = invokeAny(creeper, new String[]{"getPowered", "isPowered"}, new Class<?>[0]);
        return result instanceof Boolean && (Boolean) result;
    }

    public static boolean creeperIgnited(net.minecraft.entity.monster.EntityCreeper creeper) {
        Object result = invokeAny(creeper, new String[]{"hasIgnited", "isIgnited"}, new Class<?>[0]);
        return result instanceof Boolean && (Boolean) result;
    }

    public static int creeperSwell(net.minecraft.entity.monster.EntityCreeper creeper) {
        Object result = invokeAny(creeper, new String[]{"getCreeperState", "getSwellDir"}, new Class<?>[0]);
        return result instanceof Number ? ((Number) result).intValue() : 0;
    }

    public static void strikeByLightning(Entity entity, net.minecraft.entity.effect.EntityLightningBolt lightning) {
        if (entity == null || lightning == null) return;
        invokeAny(entity, new String[]{"onStruckByLightning", "thunderHit"},
                new Class<?>[]{net.minecraft.entity.effect.EntityLightningBolt.class}, lightning);
    }

    public static int air(EntityLivingBase entity) {
        Object result = invokeAny(entity, new String[]{"getAir", "getAirSupply"}, new Class<?>[0]);
        return result instanceof Number ? ((Number) result).intValue() : 300;
    }

    public static int maxAir(EntityLivingBase entity) {
        Object result = invokeAny(entity, new String[]{"getMaxAir", "getMaxAirSupply"}, new Class<?>[0]);
        return result instanceof Number ? ((Number) result).intValue() : 300;
    }

    public static boolean hasCarriedBlock(net.minecraft.entity.monster.EntityEnderman enderman) {
        Object result = invokeAny(enderman, new String[]{"getHeldBlockState", "getCarriedBlock"}, new Class<?>[0]);
        return result != null;
    }

    public static net.minecraft.util.AxisAlignedBB moveBox(net.minecraft.util.AxisAlignedBB box, net.minecraft.util.Vec3 delta) {
        if (box == null || delta == null) return box;
        return net.minecraft.util.AxisAlignedBB.getBoundingBox(box.minX + delta.xCoord, box.minY + delta.yCoord, box.minZ + delta.zCoord,
                box.maxX + delta.xCoord, box.maxY + delta.yCoord, box.maxZ + delta.zCoord);
    }

    public static net.minecraft.util.AxisAlignedBB shrinkBox(net.minecraft.util.AxisAlignedBB box, double amount) {
        if (box == null) return null;
        return net.minecraft.util.AxisAlignedBB.getBoundingBox(box.minX + amount, box.minY + amount, box.minZ + amount,
                box.maxX - amount, box.maxY - amount, box.maxZ - amount);
    }

    public static List<net.minecraft.entity.player.EntityPlayer> livingPlayers(WorldServer level,
            java.util.function.Predicate<net.minecraft.entity.player.EntityPlayer> predicate) {
        List<net.minecraft.entity.player.EntityPlayer> result = new ArrayList<>();
        if (level == null) return result;
        for (Object raw : level.playerEntities) {
            if (!(raw instanceof net.minecraft.entity.player.EntityPlayer)) continue;
            net.minecraft.entity.player.EntityPlayer player = (net.minecraft.entity.player.EntityPlayer) raw;
            if (predicate == null || predicate.test(player)) result.add(player);
        }
        return result;
    }

    public static boolean hasAdvancement(EntityPlayerMP player, net.minecraft.util.ResourceLocation id) {
        // Advancements were introduced after 1.10.2. Keep the later-version
        // progression hooks present in source, but never claim an unavailable
        // advancement on this target.
        return false;
    }

    public static net.minecraft.entity.projectile.EntityArrow mobArrow(net.minecraft.entity.monster.EntitySkeleton skeleton,
            ItemStack bow, float distanceFactor) {
        if (skeleton == null || skeleton.worldObj == null) return null;
        try {
            Class<?> type = Class.forName("net.minecraft.entity.projectile.EntityTippedArrow");
            java.lang.reflect.Constructor<?> constructor = type.getConstructor(World.class, EntityLivingBase.class);
            Object value = constructor.newInstance(skeleton.worldObj, skeleton);
            if (value instanceof net.minecraft.entity.projectile.EntityArrow) {
                net.minecraft.entity.projectile.EntityArrow arrow = (net.minecraft.entity.projectile.EntityArrow) value;
                invokeAny(arrow, new String[]{"setEnchantmentEffectsFromEntity"},
                        new Class<?>[]{EntityLivingBase.class, float.class}, skeleton, distanceFactor);
                return arrow;
            }
        } catch (ReflectiveOperationException ignored) { }
        return null;
    }

    public static void reassessWeaponGoal(net.minecraft.entity.monster.EntitySkeleton skeleton) {
        if (skeleton != null) invokeAny(skeleton, new String[]{"setCombatTask", "reassessWeaponGoal"}, new Class<?>[0]);
    }

    public static boolean removed(Entity entity) {
        return entity == null || entity.isDead;
    }

    public static int armorValue(net.minecraft.entity.player.EntityPlayer player) {
        Object result = invokeAny(player, new String[]{"getTotalArmorValue", "getArmorValue"}, new Class<?>[0]);
        return result instanceof Number ? ((Number) result).intValue() : 0;
    }

    public static double explosionExposure(World world, net.minecraft.util.Vec3 center, Entity target) {
        if (world == null || center == null || target == null) return 0.0D;
        Object result = invokeAny(world, new String[]{"getBlockDensity"},
                new Class<?>[]{net.minecraft.util.Vec3.class, net.minecraft.util.AxisAlignedBB.class}, center, fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(target));
        return result instanceof Number ? ((Number) result).doubleValue() : 0.5D;
    }

    public static void setCreeperSwell(net.minecraft.entity.monster.EntityCreeper creeper, int state) {
        if (creeper == null) return;
        invokeAny(creeper, new String[]{"setCreeperState", "setSwellDir"},
                new Class<?>[]{int.class}, state);
    }

    public static boolean isDay(World world) {
        if (world == null) return false;
        long time = Math.floorMod(world.getWorldTime(), 24000L);
        return time < 12000L;
    }

    public static boolean withinWorldBorder(World world, BlockPos pos) { return world != null && pos != null; }

    public static EntityPlayerMP nearestPlayer(WorldServer level, double x, double y, double z,
            double radius, java.util.function.Predicate<net.minecraft.entity.player.EntityPlayer> predicate) {
        if (level == null) return null;
        double best = radius < 0.0D ? Double.POSITIVE_INFINITY : radius * radius;
        EntityPlayerMP nearest = null;
        for (Object raw : level.playerEntities) {
            if (!(raw instanceof EntityPlayerMP)) continue;
            EntityPlayerMP player = (EntityPlayerMP) raw;
            if (predicate != null && !predicate.test(player)) continue;
            double dx = player.posX - x, dy = player.posY - y, dz = player.posZ - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    public static boolean hasEffect(EntityLivingBase entity, net.minecraft.potion.Potion potion) {
        if (entity == null || potion == null) return false;
        Object result = invokeAny(entity, new String[]{"isPotionActive", "hasEffect"},
                new Class<?>[]{net.minecraft.potion.Potion.class}, potion);
        return result instanceof Boolean && (Boolean) result;
    }

    public static void moveHelper(net.minecraft.entity.EntityLiving actor,
            double x, double y, double z, double speed) {
        if (actor == null) return;
        invokeAny(actor.getMoveHelper(), new String[]{"setMoveTo", "setWantedPosition"},
                new Class<?>[]{double.class, double.class, double.class, double.class}, x, y, z, speed);
    }

    public static boolean navigationDone(net.minecraft.entity.EntityLiving actor) {
        if (actor == null) return true;
        Object result = invokeAny(actor.getNavigator(), new String[]{"noPath", "isDone"}, new Class<?>[0]);
        return result instanceof Boolean ? (Boolean) result : true;
    }

    public static Entity vehicle(Entity entity) {
        if (entity == null) return null;
        Object result = invokeAny(entity, new String[]{"getRidingEntity", "getVehicle"}, new Class<?>[0]);
        return result instanceof Entity ? (Entity) result : null;
    }

    public static boolean isSneaking(EntityLivingBase entity) {
        if (entity == null) return false;
        Object result = invokeAny(entity, new String[]{"isSneaking", "isShiftKeyDown"}, new Class<?>[0]);
        return result instanceof Boolean && (Boolean) result;
    }

}
