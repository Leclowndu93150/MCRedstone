package com.leclowndu93150.mcredstone.compiler.timing;

import com.leclowndu93150.mcredstone.compiler.verify.Reach;
import com.leclowndu93150.mcredstone.compiler.voxel.Kind;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelGrid;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClockBalancer {
    private static final int MAX_DELAY = 4;

    private final VoxelGrid grid;
    private final Map<Long, Reach> reach;
    private final long root;
    private final Set<Long> locks;
    private final Set<Long> opaque;
    private final Map<Long, List<Long>> children = new HashMap<>();
    private final List<Long> order = new ArrayList<>();

    public ClockBalancer(VoxelGrid grid, Map<Long, Reach> reach, long root, Set<Long> locks, Set<Long> opaque) {
        this.grid = grid;
        this.reach = reach;
        this.root = root;
        this.locks = locks;
        this.opaque = opaque;
        Deque<Long> queue = new ArrayDeque<>();
        Set<Long> seen = new LinkedHashSet<>();
        queue.add(root);
        seen.add(root);
        while (!queue.isEmpty()) {
            long node = queue.poll();
            List<Long> next = new ArrayList<>();
            Reach direct = reach.get(node);
            if (direct != null) {
                for (long reader : direct.inputs().keySet()) {
                    if (opaque.contains(reader) || !seen.add(reader)) {
                        continue;
                    }
                    Voxel voxel = grid.get(reader);
                    if (locks.contains(reader) || voxel.kind() == Kind.REPEATER) {
                        next.add(reader);
                        if (!locks.contains(reader)) {
                            order.add(reader);
                            queue.add(reader);
                        }
                    }
                }
            }
            children.put(node, next);
        }
    }

    public void balance() {
        Map<Long, Integer> arrival = arrivals();
        int target = 0;
        for (long lock : locks) {
            target = Math.max(target, arrival.getOrDefault(lock, 0));
        }
        for (long repeater : order) {
            int slack = Integer.MAX_VALUE;
            for (long lock : downstreamLocks(repeater)) {
                slack = Math.min(slack, target - arrival.getOrDefault(lock, target));
            }
            Voxel voxel = grid.get(repeater);
            int steps = Math.min(MAX_DELAY - voxel.delay(), slack == Integer.MAX_VALUE ? 0 : slack / 2);
            if (steps > 0) {
                setDelay(repeater, voxel.delay() + steps);
                arrival = arrivals();
            }
        }
        for (long lock : locks) {
            Integer time = arrival.get(lock);
            if (time != null) {
                setDelay(lock, 1 + Math.min(MAX_DELAY - 1, (target - time) / 2));
            }
        }
    }

    private void setDelay(long pos, int delay) {
        Voxel voxel = grid.get(pos);
        grid.set(pos, Voxel.repeater(voxel.dir(), delay));
    }

    private Map<Long, Integer> arrivals() {
        Map<Long, Integer> arrival = new HashMap<>();
        walk(root, 0, arrival);
        return arrival;
    }

    private void walk(long node, int time, Map<Long, Integer> arrival) {
        for (long child : children.getOrDefault(node, List.of())) {
            arrival.merge(child, time, Math::max);
            if (!locks.contains(child)) {
                walk(child, time + Sta.delay(grid.get(child)), arrival);
            }
        }
    }

    private Set<Long> downstreamLocks(long node) {
        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            long current = stack.pop();
            for (long child : children.getOrDefault(current, List.of())) {
                if (locks.contains(child)) {
                    result.add(child);
                } else {
                    stack.push(child);
                }
            }
        }
        return result;
    }
}
