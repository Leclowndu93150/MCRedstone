package com.leclowndu93150.mcredstone.compiler.route;

import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import java.util.Arrays;

public final class RouteGrid {
    public static final int FREE = -1;
    public static final int BLOCKED = -2;
    private static final int PAD = 3;

    public final int minX;
    public final int minZ;
    public final int maxX;
    public final int maxZ;
    public final int tiers;
    public final int zoneEnd;
    final int originX;
    final int originY;
    final int originZ;
    final int sizeX;
    final int sizeY;
    final int sizeZ;
    final int strideZ;
    final int strideY;
    final int[] fixedOwner;
    final Voxel[] fixedVoxel;
    final int[] usage;
    final int[] occupied;
    final float[] history;

    public RouteGrid(int minX, int maxX, int minZ, int maxZ, int tiers, int zoneEnd) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.tiers = tiers;
        this.zoneEnd = zoneEnd;
        this.originX = minX - PAD;
        this.originZ = minZ - PAD;
        this.originY = -PAD;
        this.sizeX = maxX - minX + 1 + 2 * PAD;
        this.sizeZ = maxZ - minZ + 1 + 2 * PAD;
        this.sizeY = 2 * tiers + 1 + 2 * PAD;
        this.strideZ = sizeX;
        this.strideY = sizeX * sizeZ;
        int size = sizeX * sizeY * sizeZ;
        this.fixedOwner = new int[size];
        this.fixedVoxel = new Voxel[size];
        this.usage = new int[size];
        this.occupied = new int[size];
        this.history = new float[size];
        Arrays.fill(fixedOwner, BLOCKED);
        for (int y = 0; y <= 2 * tiers; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                int row = index(minX, y, z);
                Arrays.fill(fixedOwner, row, row + (maxX - minX + 1), FREE);
            }
        }
    }

    public int size() {
        return fixedOwner.length;
    }

    public int index(int x, int y, int z) {
        return (y - originY) * strideY + (z - originZ) * strideZ + (x - originX);
    }

    public int x(int index) {
        return index % sizeX + originX;
    }

    public int z(int index) {
        return (index / strideZ) % sizeZ + originZ;
    }

    public int y(int index) {
        return index / strideY + originY;
    }

    public int delta(int dx, int dy, int dz) {
        return dx + dz * strideZ + dy * strideY;
    }

    public static boolean walkableFloor(int y) {
        return Math.floorMod(y, 4) == 0;
    }

    public static boolean openFloor(int y) {
        return Math.floorMod(y, 4) == 2;
    }

    public static boolean openDevice(int y) {
        return Math.floorMod(y, 4) == 3;
    }

    public boolean inZone(int z) {
        return z < zoneEnd;
    }

    boolean woolAbove(int index) {
        return walkableFloor(y(index) + 1) && !inZone(z(index));
    }

    boolean unsupported(int index) {
        return openDevice(y(index)) && !inZone(z(index));
    }

    public boolean inside(int x, int y, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= 0 && y <= 2 * tiers;
    }

    public void fix(int x, int y, int z, int owner, Voxel voxel) {
        if (!inside(x, y, z)) {
            return;
        }
        int index = index(x, y, z);
        fixedOwner[index] = owner;
        fixedVoxel[index] = voxel;
    }
}
