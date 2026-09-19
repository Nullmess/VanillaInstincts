package fr.vanillainstincts.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Collections;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.potion.PotionEffect;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockDoor;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Translation layer from post-1.8 coordinate/state calls to Minecraft 1.7.x. */
public final class Minecraft17Compat {
    private Minecraft17Compat() {}
    private static final Map<EntityVillager, InventoryBasic> VILLAGER_INVENTORIES =
            Collections.synchronizedMap(new WeakHashMap<EntityVillager, InventoryBasic>());

    public static Vec3 position(Entity entity){
        return entity == null ? new Vec3(0.0D,0.0D,0.0D) : new Vec3(entity.posX,entity.posY,entity.posZ);
    }
    public static Vec3 eyes(EntityLivingBase entity, float partialTicks){
        return entity == null ? new Vec3(0.0D,0.0D,0.0D) : new Vec3(entity.posX,entity.posY + entity.getEyeHeight(),entity.posZ);
    }
    public static Vec3 look(EntityLivingBase entity){ return entity == null ? new Vec3(0.0D,0.0D,0.0D) : Vec3.of(entity.getLookVec()); }
    public static AxisAlignedBB boundingBox(Entity entity){ return entity == null ? null : entity.boundingBox; }
    public static InventoryBasic villagerInventory(EntityVillager villager){
        if(villager==null) return new InventoryBasic("Villager",false,8);
        InventoryBasic inv=VILLAGER_INVENTORIES.get(villager);
        if(inv==null){ inv=new InventoryBasic("Villager",false,8); VILLAGER_INVENTORIES.put(villager,inv); }
        return inv;
    }
    public static void spawnParticle(WorldServer world, EnumParticleTypes type, double x,double y,double z,int count,double dx,double dy,double dz,double speed){
        if(world!=null && type!=null) world.func_147487_a(type.id(),x,y,z,count,dx,dy,dz,speed);
    }
    public static MinecraftServer server(WorldServer world){ return world==null ? null : world.func_73046_m(); }
    public static EntityPlayer player(WorldServer world, java.util.UUID id){ return player(server(world), id); }
    public static EntityPlayerMP player(MinecraftServer server, java.util.UUID id){
        if(server==null||id==null) return null;
        for(Object raw:server.getConfigurationManager().playerEntityList){
            if(raw instanceof EntityPlayerMP && id.equals(((EntityPlayerMP)raw).getUniqueID())) return (EntityPlayerMP)raw;
        }
        return null;
    }
    public static LegacyBlockState fenceGateState(boolean open, EnumFacing facing){
        int dir = facing==EnumFacing.WEST ? 1 : facing==EnumFacing.NORTH ? 2 : facing==EnumFacing.EAST ? 3 : 0;
        return stateFromMeta(Blocks.fence_gate, dir | (open?4:0));
    }
    public static boolean fenceGateOpen(LegacyBlockState state){ return state!=null && (state.getMeta() & 4)!=0; }
    public static boolean isPassable(Block block, World world, BlockPos pos){
        return block != null && world != null && pos != null
                && block.isPassable(world, pos.getX(), pos.getY(), pos.getZ());
    }
    public static boolean trapDoorOpen(LegacyBlockState state){ return state != null && (state.getMeta() & 4) != 0; }
    public static boolean creeperIgnited(net.minecraft.entity.monster.EntityCreeper creeper){
        return creeper != null && creeper.getCreeperState() > 0;
    }
    public static boolean igniteCreeper(net.minecraft.entity.monster.EntityCreeper creeper){
        if (creeper == null) return false;
        creeper.setCreeperState(1);
        return creeper.getCreeperState() > 0;
    }
    public static Block itemBlockBlock(net.minecraft.item.ItemBlock item){
        return item == null ? null : net.minecraft.block.Block.getBlockFromItem(item);
    }
    public static LegacyBlockState withFenceGateOpen(LegacyBlockState state, boolean open){
        if(state==null) return null; int meta=state.getMeta(); meta=open?(meta|4):(meta&~4); return stateFromMeta(state.getBlock(),meta);
    }
    public static int maxDamage(ItemStack stack){
        return stack==null || stack.getItem()==null ? 0 : stack.getItem().getMaxDamage(stack);
    }
    public static int itemDamage(ItemStack stack){
        return stack==null || stack.getItem()==null ? 0 : stack.getItem().getDamage(stack);
    }
    public static void setItemDamage(ItemStack stack, int damage){
        if(stack!=null && stack.getItem()!=null) stack.getItem().setDamage(stack, Math.max(0, damage));
    }

    /** Spectator mode did not exist in 1.7.x. */
    public static boolean isSpectator(EntityPlayer player){ return false; }
    /** Entity#setSilent was added later; there is no equivalent flag in 1.7.x. */
    public static void setSilent(Entity entity, boolean silent){ }
    public static boolean cannotPickup(EntityItem item){ return item != null && item.delayBeforeCanPickup > 0; }
    public static PotionEffect potionEffect(int id, int duration, int amplifier, boolean ambient, boolean showParticles){
        return new PotionEffect(id, duration, amplifier, ambient);
    }
    public static MerchantRecipe merchantRecipe(ItemStack buy, ItemStack buyB, ItemStack sell, int uses, int maxUses){
        MerchantRecipe recipe = new MerchantRecipe(buy, buyB, sell);
        for(int i=0;i<Math.max(0, uses);i++) recipe.incrementToolUses();
        recipe.func_82783_a(maxUses - 7);
        return recipe;
    }
    public static boolean doorOpen(LegacyBlockState state){ return state != null && (state.getMeta() & 4) != 0; }
    public static boolean doorUpper(LegacyBlockState state){ return state != null && (state.getMeta() & 8) != 0; }
    public static boolean mechanismPowered(LegacyBlockState state){ return state != null && (state.getMeta() & 8) != 0; }
    public static LegacyBlockState withMechanismPowered(LegacyBlockState state, boolean powered){
        if(state==null) return null;
        int meta=state.getMeta();
        meta = powered ? (meta | 8) : (meta & ~8);
        return stateFromMeta(state.getBlock(), meta);
    }
    public static void toggleDoor(BlockDoor door, World world, BlockPos pos, boolean open){
        if(door!=null && world!=null && pos!=null) door.func_150014_a(world,pos.getX(),pos.getY(),pos.getZ(),open);
    }
    public static void notifyNeighbors(World world, BlockPos pos, Block block){
        if(world!=null && pos!=null && block!=null) world.notifyBlocksOfNeighborChange(pos.getX(),pos.getY(),pos.getZ(),block);
    }
    public static void scheduleUpdate(World world, BlockPos pos, Block block, int delay){
        if(world!=null && pos!=null && block!=null) world.scheduleBlockUpdate(pos.getX(),pos.getY(),pos.getZ(),block,delay);
    }
    public static boolean withinWorldBorder(World world, BlockPos pos){ return true; }
    public static boolean isAir(World world, BlockPos pos){
        return world!=null && pos!=null && world.isAirBlock(pos.getX(),pos.getY(),pos.getZ());
    }
    public static void dropBlockAsItem(LegacyBlockState state, World world, BlockPos pos, int fortune){
        if(state!=null && state.getBlock()!=null && world!=null && pos!=null)
            state.getBlock().dropBlockAsItem(world,pos.getX(),pos.getY(),pos.getZ(),state.getMeta(),fortune);
    }
    public static LegacyBlockState portalState(EnumFacing.Axis axis){
        // 1.7.x portal orientation is metadata-backed. 1 = X, 2 = Z.
        return stateFromMeta(Blocks.portal, axis==EnumFacing.Axis.Z ? 2 : 1);
    }
    public static LegacyBlockState getBlockState(World world, BlockPos pos){
        if(world==null||pos==null) return defaultState(Blocks.air);
        Block block=world.getBlock(pos.getX(),pos.getY(),pos.getZ());
        int meta=world.getBlockMetadata(pos.getX(),pos.getY(),pos.getZ());
        return new LegacyBlockState(block==null?Blocks.air:block,meta);
    }
    public static LegacyBlockState defaultState(Block block){return new LegacyBlockState(block==null?Blocks.air:block,0);}
    public static LegacyBlockState stateFromMeta(Block block,int meta){return new LegacyBlockState(block==null?Blocks.air:block,meta);}
    public static int metaFromState(LegacyBlockState state){return state==null?0:state.getMeta();}
    public static boolean setBlockState(World world, BlockPos pos, LegacyBlockState state){return setBlockState(world,pos,state,3);}
    public static boolean setBlockState(World world, BlockPos pos, LegacyBlockState state, int flags){
        return world!=null&&pos!=null&&state!=null&&world.setBlock(pos.getX(),pos.getY(),pos.getZ(),state.getBlock(),state.getMeta(),flags);
    }
    public static boolean canPlaceBlockAt(Block block, World world, BlockPos pos){
        return block!=null&&world!=null&&pos!=null&&block.canPlaceBlockAt(world,pos.getX(),pos.getY(),pos.getZ());
    }
    public static boolean isBlockLoaded(World world, BlockPos pos){return world!=null&&pos!=null&&world.blockExists(pos.getX(),pos.getY(),pos.getZ());}
    public static TileEntity getTileEntity(World world, BlockPos pos){return world==null||pos==null?null:world.getTileEntity(pos.getX(),pos.getY(),pos.getZ());}
    public static boolean canSeeSky(World world, BlockPos pos){return world!=null&&pos!=null&&world.canBlockSeeTheSky(pos.getX(),pos.getY(),pos.getZ());}
    public static int worldHeight(World world){ return 256; }
    public static BlockPos getHeight(World world, BlockPos pos){
        if(world==null||pos==null) return pos;
        return new BlockPos(pos.getX(), world.getHeightValue(pos.getX(),pos.getZ()), pos.getZ());
    }
    public static boolean destroyBlock(World world, BlockPos pos, boolean drops){
        if(world==null||pos==null) return false;
        Block block=world.getBlock(pos.getX(),pos.getY(),pos.getZ());
        int meta=world.getBlockMetadata(pos.getX(),pos.getY(),pos.getZ());
        if(drops && block!=null && block!=Blocks.air) block.dropBlockAsItem(world,pos.getX(),pos.getY(),pos.getZ(),meta,0);
        return world.setBlockToAir(pos.getX(),pos.getY(),pos.getZ());
    }
    public static Entity getEntityFromUuid(World world, java.util.UUID id){
        if(world==null||id==null) return null;
        for(Object raw: world.loadedEntityList){ if(raw instanceof Entity && id.equals(((Entity)raw).getUniqueID())) return (Entity)raw; }
        return null;
    }
    public static Object propertyValue(LegacyBlockState state,Object property){
        if(state==null) return Integer.valueOf(0);
        String name=property==null?"":property.toString().toLowerCase(java.util.Locale.ROOT);
        int meta=state.getMeta();
        if(name.contains("open")||name.contains("powered")) return Boolean.valueOf((meta & 4)!=0 || (meta & 8)!=0);
        if(name.contains("axis")) return EnumFacing.Axis.X;
        return Integer.valueOf(meta);
    }
    public static <T extends Comparable<T>> LegacyBlockState withProperty(LegacyBlockState state,Object property,T value){
        if(state==null) return null;
        int meta=state.getMeta();
        String name=property==null?"":property.toString().toLowerCase(java.util.Locale.ROOT);
        if(value instanceof Boolean && (name.contains("open")||name.contains("powered"))){
            if(((Boolean)value).booleanValue()) meta|=4; else meta&=~4;
        } else if(value instanceof Number){ meta=((Number)value).intValue() & 15; }
        return new LegacyBlockState(state.getBlock(),meta);
    }
    public static List<EntityPlayer> players(World world){
        List<EntityPlayer> out=new ArrayList<EntityPlayer>(); if(world==null)return out;
        for(Object raw:world.playerEntities) if(raw instanceof EntityPlayer) out.add((EntityPlayer)raw); return out;
    }
}
