package com.leclowndu93150.mcredstone.compiler.timing;

import com.leclowndu93150.mcredstone.compiler.verify.Reach;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelGrid;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Sta {
    public record Result(int k1Min, int k1Max, int k2Min, int k2Max, int logic, int input, int output, int inputOutput) {
    }

    public record Endpoints(Set<Long> masters, Set<Long> slaves, Set<Long> k1Locks, Set<Long> k2Locks, Set<Long> k1Taps, Set<Long> k2Taps,
                            Set<Long> pads, Set<Long> lamps, Set<Long> opaque) {
    }

    private final VoxelGrid grid;
    private final Map<Long, Reach> reach;
    private final Endpoints endpoints;

    public Sta(VoxelGrid grid, Map<Long, Reach> reach, Endpoints endpoints) {
        this.grid = grid;
        this.reach = reach;
        this.endpoints = endpoints;
    }

    public Result analyze() {
        int[] k1 = arrival(endpoints.k1Taps(), endpoints.k1Locks());
        int[] k2 = arrival(endpoints.k2Taps(), endpoints.k2Locks());
        int logic = 0;
        int output = 0;
        Map<Long, Integer> toMasters = new HashMap<>();
        Map<Long, Integer> toLamps = new HashMap<>();
        for (long slave : endpoints.slaves()) {
            logic = Math.max(logic, longest(slave, endpoints.masters(), toMasters, new HashSet<>()));
            output = Math.max(output, longest(slave, endpoints.lamps(), toLamps, new HashSet<>()));
        }
        int input = 0;
        int inputOutput = 0;
        for (long pad : endpoints.pads()) {
            int padDelay = delay(grid.get(pad));
            input = Math.max(input, padDelay + longest(pad, endpoints.masters(), toMasters, new HashSet<>()));
            inputOutput = Math.max(inputOutput, padDelay + longest(pad, endpoints.lamps(), toLamps, new HashSet<>()));
        }
        return new Result(k1[0], k1[1], k2[0], k2[1], logic, input, output, inputOutput);
    }

    private int[] arrival(Set<Long> taps, Set<Long> locks) {
        Map<Long, int[]> best = new HashMap<>();
        for (long tap : taps) {
            walk(tap, 0, locks, best, new HashSet<>());
        }
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (long lock : locks) {
            int[] range = best.get(lock);
            if (range == null) {
                continue;
            }
            min = Math.min(min, range[0]);
            max = Math.max(max, range[1]);
        }
        return new int[]{min == Integer.MAX_VALUE ? 0 : min, max};
    }

    private void walk(long node, int time, Set<Long> locks, Map<Long, int[]> best, Set<Long> visiting) {
        if (!visiting.add(node)) {
            return;
        }
        Reach direct = reach.get(node);
        if (direct != null) {
            for (long reader : direct.inputs().keySet()) {
                if (locks.contains(reader)) {
                    int lockTime = time + delay(grid.get(reader));
                    int[] range = best.computeIfAbsent(reader, k -> new int[]{Integer.MAX_VALUE, 0});
                    range[0] = Math.min(range[0], lockTime);
                    range[1] = Math.max(range[1], lockTime);
                } else if (!endpoints.opaque().contains(reader)) {
                    walk(reader, time + delay(grid.get(reader)), locks, best, visiting);
                }
            }
        }
        visiting.remove(node);
    }

    private int longest(long node, Set<Long> stops, Map<Long, Integer> memo, Set<Long> visiting) {
        Integer cached = memo.get(node);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(node)) {
            throw new IllegalStateException("combinational loop through " + node);
        }
        int best = Integer.MIN_VALUE / 2;
        Reach direct = reach.get(node);
        if (direct != null) {
            for (long reader : direct.inputs().keySet()) {
                if (stops.contains(reader)) {
                    best = Math.max(best, 0);
                } else if (!endpoints.opaque().contains(reader) && !endpoints.masters().contains(reader)) {
                    best = Math.max(best, delay(grid.get(reader)) + longest(reader, stops, memo, visiting));
                }
            }
        }
        visiting.remove(node);
        memo.put(node, best);
        return best;
    }

    static int delay(Voxel voxel) {
        return switch (voxel.kind()) {
            case TORCH, WALL_TORCH, COMPARATOR -> 2;
            case REPEATER -> 2 * voxel.delay();
            default -> 0;
        };
    }
}
