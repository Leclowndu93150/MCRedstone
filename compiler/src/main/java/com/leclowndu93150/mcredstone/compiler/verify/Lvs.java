package com.leclowndu93150.mcredstone.compiler.verify;

import com.leclowndu93150.mcredstone.compiler.voxel.Kind;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Lvs {
    public record Report(List<String> errors, int settleTicks, Set<Long> suspects) {
        public boolean clean() {
            return errors.isEmpty();
        }
    }

    private final VoxelGrid grid;
    private final Map<Long, Set<Long>> expected;
    private final Map<Long, Set<Long>> sides;
    private final Set<Long> transparent;
    private final Map<Long, Reach> reach;
    private final Map<Long, Set<Long>> logical = new HashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final Set<Long> suspects = new LinkedHashSet<>();

    public Lvs(VoxelGrid grid, Map<Long, Set<Long>> expected, Map<Long, Set<Long>> sides, Set<Long> transparent) {
        this.grid = grid;
        this.expected = expected;
        this.sides = sides;
        this.transparent = transparent;
        this.reach = new Extractor(grid).extract();
    }

    public Report run() {
        for (Map.Entry<Long, Set<Long>> entry : expected.entrySet()) {
            long source = entry.getKey();
            if (!reach.containsKey(source)) {
                errors.add("missing source " + describe(source));
                continue;
            }
            for (long side : sides.getOrDefault(source, Set.of())) {
                if (!reach.get(source).sides().containsKey(side)) {
                    errors.add("open: " + describe(source) + " does not lock " + describe(side));
                    suspects.add(source);
                }
            }
            for (long side : reach.get(source).sides().keySet()) {
                if (!sides.getOrDefault(source, Set.of()).contains(side)) {
                    errors.add("short: " + describe(source) + " also locks " + describe(side));
                    suspects.add(source);
                }
            }
            Set<Long> actual = logicalReach(source, new HashSet<>());
            for (long reader : entry.getValue()) {
                if (!actual.contains(reader)) {
                    errors.add("open: " + describe(source) + " does not reach " + describe(reader));
                    suspects.add(source);
                }
            }
            for (long reader : actual) {
                if (!entry.getValue().contains(reader)) {
                    errors.add("short: " + describe(source) + " also reaches " + describe(reader));
                    suspects.add(source);
                    suspects.add(reader);
                }
            }
        }
        for (long source : reach.keySet()) {
            if (!expected.containsKey(source) && !transparent.contains(source)) {
                errors.add("unexpected source " + describe(source));
                suspects.add(source);
            }
        }
        return new Report(errors, errors.isEmpty() ? settleTicks() : -1, suspects);
    }

    private Set<Long> logicalReach(long source, Set<Long> visiting) {
        Set<Long> cached = logical.get(source);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(source)) {
            errors.add("loop through " + describe(source));
            suspects.add(source);
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        Reach direct = reach.get(source);
        for (long reader : direct == null ? Set.<Long>of() : direct.inputs().keySet()) {
            if (transparent.contains(reader)) {
                result.addAll(logicalReach(reader, visiting));
            } else {
                result.add(reader);
            }
        }
        visiting.remove(source);
        logical.put(source, result);
        return result;
    }

    private int settleTicks() {
        Map<Long, Integer> memo = new HashMap<>();
        int longest = 0;
        for (Map.Entry<Long, Reach> entry : reach.entrySet()) {
            if (grid.get(entry.getKey()).kind() == Kind.LEVER) {
                longest = Math.max(longest, longestFrom(entry.getKey(), memo, new HashSet<>()));
            }
        }
        return longest + 6;
    }

    private int longestFrom(long node, Map<Long, Integer> memo, Set<Long> visiting) {
        Integer cached = memo.get(node);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(node)) {
            return 0;
        }
        int best = 0;
        Reach direct = reach.get(node);
        if (direct != null) {
            for (long reader : direct.inputs().keySet()) {
                best = Math.max(best, delay(grid.get(reader)) + longestFrom(reader, memo, visiting));
            }
        }
        visiting.remove(node);
        memo.put(node, best);
        return best;
    }

    private static int delay(Voxel voxel) {
        return switch (voxel.kind()) {
            case TORCH, WALL_TORCH, COMPARATOR, OBSERVER -> 2;
            case REPEATER -> 2 * voxel.delay();
            default -> 0;
        };
    }

    private String describe(long pos) {
        Voxel voxel = grid.get(pos);
        return (voxel == null ? "air" : voxel.kind().name().toLowerCase()) + Pos.toString(pos);
    }
}
