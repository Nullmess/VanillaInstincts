package fr.vanillainstincts.compat;

import java.util.Collections;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/** Block + legacy metadata wrapper replacing the post-1.8 LegacyBlockState API. */
public final class LegacyBlockState {
    private final Block block;
    private final int meta;
    public LegacyBlockState(Block block, int meta) { this.block=block; this.meta=meta & 15; }
    public Block getBlock(){ return block; }
    public int getMeta(){ return meta; }
    public Material getMaterial(){ return block == null ? Material.air : block.getMaterial(); }
    public boolean isFullCube(){ return block != null && block.isOpaqueCube() && block.renderAsNormalBlock(); }
    public float getBlockHardness(World world, BlockPos pos){ return block == null || world == null || pos == null ? 0F : block.getBlockHardness(world,pos.getX(),pos.getY(),pos.getZ()); }
    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos){
        return block == null || world == null || pos == null ? null : block.getCollisionBoundingBoxFromPool(world,pos.getX(),pos.getY(),pos.getZ());
    }
    @SuppressWarnings("unchecked") public <T extends Comparable<T>> T getValue(Object property){ return (T) Minecraft17Compat.propertyValue(this, property); }
    public <T extends Comparable<T>> LegacyBlockState withProperty(Object property, T value){ return Minecraft17Compat.withProperty(this, property, value); }
    public Iterable<Object> getPropertyNames(){ return Collections.emptyList(); }
    public Map<Object,Comparable<?>> getProperties(){ return Collections.emptyMap(); }
    @Override public boolean equals(Object o){return o instanceof LegacyBlockState && ((LegacyBlockState)o).block==block && ((LegacyBlockState)o).meta==meta;}
    @Override public int hashCode(){return System.identityHashCode(block)*31+meta;}
    @Override public String toString(){return "LegacyBlockState{"+block+",meta="+meta+"}";}
}
