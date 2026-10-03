package com.leclowndu93150.mcredstone.compiler.route;

import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NetRoute {
    final Map<Integer, Voxel> voxels = new LinkedHashMap<>();
    final Map<Integer, Integer> tiles = new LinkedHashMap<>();

    public Map<Integer, Voxel> voxels() {
        return voxels;
    }
}
