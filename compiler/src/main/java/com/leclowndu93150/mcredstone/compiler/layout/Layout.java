package com.leclowndu93150.mcredstone.compiler.layout;

import com.leclowndu93150.mcredstone.compiler.voxel.VoxelGrid;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelState;
import java.util.List;
import java.util.Map;

public record Layout(String name, VoxelGrid grid, Map<Long, VoxelState> states, List<IoPin> inputs, List<IoPin> outputs, int settleTicks, Timing timing, String report, Map<Long, String> decor) {
    public int[] size() {
        int[] bounds = grid.bounds();
        return new int[]{bounds[3] - bounds[0] + 1, bounds[4] - bounds[1] + 1, bounds[5] - bounds[2] + 1};
    }

    public boolean sequential() {
        return timing != null;
    }
}
