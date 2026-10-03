package com.leclowndu93150.mcredstone.compiler.voxel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class VoxelGrid {
    private final Map<Long, Voxel> voxels = new LinkedHashMap<>();

    public Voxel get(long pos) {
        return voxels.get(pos);
    }

    public Voxel get(int x, int y, int z) {
        return voxels.get(Pos.of(x, y, z));
    }

    public void set(long pos, Voxel voxel) {
        if (voxel == null) {
            voxels.remove(pos);
        } else {
            voxels.put(pos, voxel);
        }
    }

    public void set(int x, int y, int z, Voxel voxel) {
        set(Pos.of(x, y, z), voxel);
    }

    public boolean is(long pos, Kind kind) {
        Voxel voxel = voxels.get(pos);
        return voxel != null && voxel.kind() == kind;
    }

    public int size() {
        return voxels.size();
    }

    public void forEach(BiConsumer<Long, Voxel> action) {
        voxels.forEach(action);
    }

    public int[] bounds() {
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (long pos : voxels.keySet()) {
            int x = Pos.x(pos);
            int y = Pos.y(pos);
            int z = Pos.z(pos);
            bounds[0] = Math.min(bounds[0], x);
            bounds[1] = Math.min(bounds[1], y);
            bounds[2] = Math.min(bounds[2], z);
            bounds[3] = Math.max(bounds[3], x);
            bounds[4] = Math.max(bounds[4], y);
            bounds[5] = Math.max(bounds[5], z);
        }
        return bounds;
    }
}
