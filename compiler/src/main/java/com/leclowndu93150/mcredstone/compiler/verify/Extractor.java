package com.leclowndu93150.mcredstone.compiler.verify;

import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import com.leclowndu93150.mcredstone.compiler.voxel.Kind;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelGrid;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelRules;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;

public final class Extractor {
    private final VoxelGrid grid;

    public Extractor(VoxelGrid grid) {
        this.grid = grid;
    }

    public Map<Long, Reach> extract() {
        Map<Long, Reach> reach = new LinkedHashMap<>();
        grid.forEach((pos, voxel) -> {
            if (isSource(voxel)) {
                reach.put(pos, reach(pos));
            }
        });
        return reach;
    }

    public static boolean isSource(Voxel voxel) {
        return VoxelRules.isSource(voxel) || voxel.kind() == Kind.REDSTONE_BLOCK;
    }

    public Reach reach(long source) {
        Trace trace = new Trace(source);
        trace.run();
        return new Reach(trace.inputs, trace.sides);
    }

    public Map<Long, Integer> poweredWires(long source) {
        Trace trace = new Trace(source);
        trace.run();
        return trace.wires;
    }

    private final class Trace {
        private final long source;
        private final Map<Long, Integer> wires = new HashMap<>();
        private final Map<Long, Integer> strongBlocks = new HashMap<>();
        private final Map<Long, Integer> wireBlocks = new HashMap<>();
        private final Map<Long, Integer> inputs = new LinkedHashMap<>();
        private final Map<Long, Integer> sides = new LinkedHashMap<>();
        private final PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Long.compare(b[1], a[1]));

        private Trace(long source) {
            this.source = source;
        }

        private void run() {
            Voxel voxel = grid.get(source);
            for (Dir dir : Dir.ALL) {
                long target = Pos.offset(source, dir);
                Voxel targetVoxel = grid.get(target);
                if (targetVoxel == null) {
                    continue;
                }
                int weak = VoxelRules.weakEmission(voxel, dir);
                int strong = VoxelRules.strongEmission(voxel, dir);
                if (targetVoxel.kind() == Kind.WIRE) {
                    offerWire(target, weak);
                } else if (VoxelRules.conductor(targetVoxel)) {
                    if (strong > 0) {
                        offerStrongBlock(target, strong);
                    }
                    if (weak > 0 && targetVoxel.kind() == Kind.LAMP) {
                        hit(inputs, target, weak);
                    }
                } else {
                    componentNeighbor(target, targetVoxel, voxel, dir, weak, strong);
                }
            }
            while (!queue.isEmpty()) {
                long[] entry = queue.poll();
                long pos = entry[0];
                int power = (int) entry[1];
                if (wires.getOrDefault(pos, 0) == power) {
                    processWire(pos, power);
                }
            }
        }

        private void componentNeighbor(long target, Voxel targetVoxel, Voxel sourceVoxel, Dir fromSource, int weak, int strong) {
            switch (targetVoxel.kind()) {
                case REPEATER -> {
                    if (VoxelRules.diodeInput(targetVoxel, target) == source && weak > 0) {
                        hit(inputs, target, weak);
                    } else if (isSide(targetVoxel, fromSource) && isDiode(sourceVoxel) && strong > 0) {
                        hit(sides, target, strong);
                    }
                }
                case COMPARATOR -> {
                    if (VoxelRules.diodeInput(targetVoxel, target) == source && weak > 0) {
                        hit(inputs, target, weak);
                    } else if (isSide(targetVoxel, fromSource)) {
                        int side = sourceVoxel.kind() == Kind.REDSTONE_BLOCK ? 15 : strong;
                        if (side > 0) {
                            hit(sides, target, side);
                        }
                    }
                }
                case COPPER_BULB -> {
                    if (weak > 0) {
                        hit(inputs, target, weak);
                    }
                }
                default -> {
                }
            }
        }

        private boolean isSide(Voxel diode, Dir fromSource) {
            return fromSource.horizontal() && !diode.dir().sameAxis(fromSource);
        }

        private boolean isDiode(Voxel voxel) {
            return voxel.kind() == Kind.REPEATER || voxel.kind() == Kind.COMPARATOR;
        }

        private void processWire(long pos, int power) {
            for (Dir dir : Dir.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    long candidate = Pos.offset(Pos.offset(pos, dir), 0, dy, 0);
                    if (VoxelRules.wire(grid.get(candidate)) && VoxelRules.incomingWires(grid, candidate).contains(pos)) {
                        offerWire(candidate, power - 1);
                    }
                }
            }
            for (Dir dir : Dir.ALL) {
                if (dir == Dir.UP) {
                    continue;
                }
                long target = Pos.offset(pos, dir);
                Voxel targetVoxel = grid.get(target);
                if (targetVoxel == null || targetVoxel.kind() == Kind.WIRE) {
                    continue;
                }
                if (targetVoxel.kind() == Kind.REPEATER || targetVoxel.kind() == Kind.COMPARATOR) {
                    if (VoxelRules.diodeInput(targetVoxel, target) == pos) {
                        hit(inputs, target, power);
                    } else if (targetVoxel.kind() == Kind.COMPARATOR && isSide(targetVoxel, dir)) {
                        hit(sides, target, power);
                    }
                    continue;
                }
                if (!VoxelRules.wirePowers(grid, pos, dir)) {
                    continue;
                }
                if (VoxelRules.conductor(targetVoxel)) {
                    offerWireBlock(target, power);
                    if (targetVoxel.kind() == Kind.LAMP) {
                        hit(inputs, target, power);
                    }
                } else if (targetVoxel.kind() == Kind.COPPER_BULB) {
                    hit(inputs, target, power);
                }
            }
        }

        private void offerWire(long pos, int power) {
            if (power <= 0 || wires.getOrDefault(pos, 0) >= power) {
                return;
            }
            wires.put(pos, power);
            queue.add(new long[]{pos, power});
        }

        private void offerStrongBlock(long pos, int level) {
            if (strongBlocks.getOrDefault(pos, 0) >= level) {
                return;
            }
            strongBlocks.put(pos, level);
            for (Dir dir : Dir.ALL) {
                long neighbor = Pos.offset(pos, dir);
                if (VoxelRules.wire(grid.get(neighbor))) {
                    offerWire(neighbor, level);
                }
            }
            notifyBlockReaders(pos, level);
        }

        private void offerWireBlock(long pos, int level) {
            if (wireBlocks.getOrDefault(pos, 0) >= level) {
                return;
            }
            wireBlocks.put(pos, level);
            notifyBlockReaders(pos, level);
        }

        private void notifyBlockReaders(long block, int level) {
            for (Dir dir : Dir.ALL) {
                long neighbor = Pos.offset(block, dir);
                Voxel voxel = grid.get(neighbor);
                if (voxel == null) {
                    continue;
                }
                switch (voxel.kind()) {
                    case TORCH, WALL_TORCH -> {
                        if (VoxelRules.torchSupport(voxel, neighbor) == block) {
                            hit(inputs, neighbor, level);
                        }
                    }
                    case REPEATER, COMPARATOR -> {
                        if (VoxelRules.diodeInput(voxel, neighbor) == block) {
                            hit(inputs, neighbor, level);
                        }
                    }
                    case LAMP, COPPER_BULB -> hit(inputs, neighbor, level);
                    default -> {
                    }
                }
            }
        }

        private void hit(Map<Long, Integer> readers, long reader, int level) {
            readers.merge(reader, level, Math::max);
        }
    }
}
