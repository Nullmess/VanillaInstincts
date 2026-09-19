package fr.vanillainstincts.compat;

import java.util.Arrays;
import java.util.Iterator;

/** Minimal 1.8-style facing enum backed by 1.7.x integer offsets. */
public enum EnumFacing {
    DOWN(0,-1,0,Axis.Y), UP(0,1,0,Axis.Y), NORTH(0,0,-1,Axis.Z), SOUTH(0,0,1,Axis.Z), WEST(-1,0,0,Axis.X), EAST(1,0,0,Axis.X);
    private final int x,y,z; private final Axis axis;
    EnumFacing(int x,int y,int z,Axis axis){this.x=x;this.y=y;this.z=z;this.axis=axis;}
    public int getFrontOffsetX(){return x;} public int getFrontOffsetY(){return y;} public int getFrontOffsetZ(){return z;}
    public Axis getAxis(){return axis;}
    public EnumFacing getOpposite(){switch(this){case DOWN:return UP;case UP:return DOWN;case NORTH:return SOUTH;case SOUTH:return NORTH;case WEST:return EAST;default:return WEST;}}
    public EnumFacing rotateY(){switch(this){case NORTH:return EAST;case EAST:return SOUTH;case SOUTH:return WEST;case WEST:return NORTH;default:return this;}}
    public EnumFacing rotateYCCW(){switch(this){case NORTH:return WEST;case WEST:return SOUTH;case SOUTH:return EAST;case EAST:return NORTH;default:return this;}}
    public enum Axis { X,Y,Z }
    public enum Plane implements Iterable<EnumFacing> {
        HORIZONTAL(new EnumFacing[]{NORTH,SOUTH,WEST,EAST}), VERTICAL(new EnumFacing[]{UP,DOWN});
        private final EnumFacing[] values; Plane(EnumFacing[] v){values=v;}
        public Iterator<EnumFacing> iterator(){return Arrays.asList(values).iterator();}
    }
}
