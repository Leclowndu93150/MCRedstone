package com.leclowndu93150.mcredstone.compiler.sim;

import com.leclowndu93150.mcredstone.compiler.verify.Extractor;
import com.leclowndu93150.mcredstone.compiler.verify.Reach;
import com.leclowndu93150.mcredstone.compiler.voxel.Kind;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelGrid;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class GraphSim {
    private static final int BURNOUT_WINDOW = 60;
    private static final int BURNOUT_TOGGLES = 8;
    private static final int BURNOUT_RESTART = 160;

    private record Scheduled(long time, Priority priority, long order, Node node) {
    }

    private final List<Node> nodes = new ArrayList<>();
    private final Map<Long, Node> byPos = new HashMap<>();
    private final PriorityQueue<Scheduled> queue = new PriorityQueue<>((a, b) -> {
        int compare = Long.compare(a.time, b.time);
        if (compare != 0) {
            return compare;
        }
        compare = a.priority.compareTo(b.priority);
        return compare != 0 ? compare : Long.compare(a.order, b.order);
    });
    private final Map<Long, Map<Long, Integer>> wireLevels = new HashMap<>();
    private long time;
    private long order;
    private int burnouts;

    public GraphSim(VoxelGrid grid) {
        grid.forEach((pos, voxel) -> {
            if (isNode(voxel)) {
                Node node = new Node(nodes.size(), pos, voxel);
                nodes.add(node);
                byPos.put(pos, node);
            }
        });
        Extractor extractor = new Extractor(grid);
        for (Node source : nodes) {
            if (!Extractor.isSource(source.voxel)) {
                continue;
            }
            Reach reach = extractor.reach(source.pos);
            for (Map.Entry<Long, Integer> entry : reach.inputs().entrySet()) {
                Node reader = byPos.get(entry.getKey());
                if (reader != null) {
                    reader.inputs.add(new int[]{source.id, 15 - entry.getValue()});
                    source.readers.add(reader);
                }
            }
            for (Map.Entry<Long, Integer> entry : reach.sides().entrySet()) {
                Node reader = byPos.get(entry.getKey());
                if (reader != null) {
                    reader.sides.add(new int[]{source.id, 15 - entry.getValue()});
                    if (!source.readers.contains(reader)) {
                        source.readers.add(reader);
                    }
                }
            }
            wireLevels.put(source.pos, extractor.poweredWires(source.pos));
        }
        for (Node node : nodes) {
            if (node.kind == Kind.REPEATER || node.kind == Kind.COMPARATOR) {
                Node front = byPos.get(Pos.offset(node.pos, node.voxel.dir()));
                node.prioritized = front != null
                    && (front.kind == Kind.REPEATER || front.kind == Kind.COMPARATOR)
                    && front.voxel.dir() != node.voxel.dir().opposite();
            }
            if (node.kind == Kind.REDSTONE_BLOCK || node.kind == Kind.TORCH || node.kind == Kind.WALL_TORCH) {
                node.on = true;
            }
        }
    }

    private static boolean isNode(Voxel voxel) {
        return switch (voxel.kind()) {
            case TORCH, WALL_TORCH, REPEATER, COMPARATOR, LAMP, LEVER, STONE_BUTTON, REDSTONE_BLOCK -> true;
            default -> false;
        };
    }

    public void reset() {
        queue.clear();
        time = 0;
        order = 0;
        burnouts = 0;
        for (Node node : nodes) {
            node.on = node.kind == Kind.REDSTONE_BLOCK || node.kind == Kind.TORCH || node.kind == Kind.WALL_TORCH;
            node.output = 0;
            node.pending = false;
            node.dueNow = false;
            node.toggles.clear();
        }
    }

    public void setRepeaterDelay(long pos, int delay) {
        Node node = byPos.get(pos);
        node.voxel = Voxel.repeater(node.voxel.dir(), delay);
    }

    public Collection<Node> nodes() {
        return nodes;
    }

    public Node node(long pos) {
        return byPos.get(pos);
    }

    public long time() {
        return time;
    }

    public int burnouts() {
        return burnouts;
    }

    public boolean quiet() {
        return queue.isEmpty();
    }

    public int input(Node node) {
        return level(node.inputs);
    }

    public int side(Node node) {
        return level(node.sides);
    }

    private int level(List<int[]> edges) {
        int best = 0;
        for (int[] edge : edges) {
            best = Math.max(best, nodes.get(edge[0]).signal() - edge[1]);
        }
        return best;
    }

    public void setState(Node node, boolean on, int output) {
        node.on = on;
        node.output = output;
    }

    public void kickAll() {
        for (Node node : nodes) {
            neighborChanged(node);
        }
    }

    public void setLever(long pos, boolean on) {
        Node node = byPos.get(pos);
        if (node.on != on) {
            node.on = on;
            notifyReaders(node);
        }
    }

    public void step() {
        time++;
        List<Scheduled> due = new ArrayList<>();
        while (!queue.isEmpty() && queue.peek().time <= time) {
            Scheduled scheduled = queue.poll();
            scheduled.node.pending = false;
            scheduled.node.dueNow = true;
            due.add(scheduled);
        }
        for (Scheduled scheduled : due) {
            scheduled.node.dueNow = false;
            tick(scheduled.node);
        }
    }

    public void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            step();
        }
    }

    public boolean settle(int maxTicks) {
        for (int i = 0; i < maxTicks && !queue.isEmpty(); i++) {
            step();
        }
        return queue.isEmpty();
    }

    public int wirePower(long wire) {
        int best = 0;
        for (Node node : nodes) {
            int signal = node.signal();
            if (signal == 0) {
                continue;
            }
            Integer level = wireLevels.getOrDefault(node.pos, Map.of()).get(wire);
            if (level != null) {
                best = Math.max(best, level - (15 - signal));
            }
        }
        return best;
    }

    public boolean paint(Map<Long, Boolean> latches, int maxIterations) {
        for (Map.Entry<Long, Boolean> latch : latches.entrySet()) {
            byPos.get(latch.getKey()).on = latch.getValue();
        }
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            boolean changed = false;
            for (Node node : nodes) {
                switch (node.kind) {
                    case TORCH, WALL_TORCH -> changed |= update(node, input(node) == 0, node.output);
                    case REPEATER -> {
                        if (!locked(node)) {
                            changed |= update(node, input(node) > 0, node.output);
                        } else if (latches.containsKey(node.pos)) {
                            changed |= update(node, latches.get(node.pos), node.output);
                        }
                    }
                    case COMPARATOR -> changed |= update(node, comparatorShouldTurnOn(node), comparatorValue(node));
                    case LAMP -> changed |= update(node, input(node) > 0, node.output);
                    default -> {
                    }
                }
            }
            if (!changed) {
                kickAll();
                return queue.isEmpty();
            }
        }
        return false;
    }

    private static boolean update(Node node, boolean on, int output) {
        boolean changed = node.on != on || node.output != output;
        node.on = on;
        node.output = output;
        return changed;
    }

    public void load(Map<Long, VoxelState> states) {
        for (Node node : nodes) {
            VoxelState state = states.get(node.pos);
            if (state != null && node.kind != Kind.REDSTONE_BLOCK) {
                node.on = state.on();
                node.output = state.power();
            }
        }
    }

    public Map<Long, VoxelState> states(VoxelGrid grid) {
        Map<Long, Integer> wires = new HashMap<>();
        for (Node node : nodes) {
            int signal = node.signal();
            if (signal == 0) {
                continue;
            }
            for (Map.Entry<Long, Integer> entry : wireLevels.getOrDefault(node.pos, Map.of()).entrySet()) {
                int level = entry.getValue() - (15 - signal);
                if (level > 0) {
                    wires.merge(entry.getKey(), level, Math::max);
                }
            }
        }
        Map<Long, VoxelState> states = new HashMap<>();
        grid.forEach((pos, voxel) -> {
            if (voxel.kind() == Kind.WIRE) {
                states.put(pos, new VoxelState(false, wires.getOrDefault(pos, 0), false));
            } else {
                Node node = byPos.get(pos);
                if (node != null) {
                    states.put(pos, new VoxelState(node.on, node.output, locked(node)));
                }
            }
        });
        return states;
    }

    private void schedule(Node node, int delay, Priority priority) {
        if (node.pending) {
            return;
        }
        node.pending = true;
        queue.add(new Scheduled(time + delay, priority, order++, node));
    }

    private void notifyReaders(Node node) {
        for (Node reader : node.readers) {
            neighborChanged(reader);
        }
    }

    private boolean locked(Node node) {
        return node.kind == Kind.REPEATER && side(node) > 0;
    }

    private int comparatorValue(Node node) {
        int input = input(node);
        if (input == 0) {
            return 0;
        }
        int side = side(node);
        if (side > input) {
            return 0;
        }
        return node.voxel.subtract() ? input - side : input;
    }

    private boolean comparatorShouldTurnOn(Node node) {
        int input = input(node);
        if (input == 0) {
            return false;
        }
        int side = side(node);
        return input > side || (input == side && !node.voxel.subtract());
    }

    private void neighborChanged(Node node) {
        switch (node.kind) {
            case TORCH, WALL_TORCH -> {
                if (node.on == (input(node) > 0) && !node.dueNow) {
                    schedule(node, 2, Priority.NORMAL);
                }
            }
            case REPEATER -> {
                if (!locked(node)) {
                    boolean should = input(node) > 0;
                    if (node.on != should && !node.dueNow) {
                        Priority priority = node.prioritized ? Priority.EXTREMELY_HIGH : node.on ? Priority.VERY_HIGH : Priority.HIGH;
                        schedule(node, 2 * node.voxel.delay(), priority);
                    }
                }
            }
            case COMPARATOR -> {
                if (!node.dueNow && (comparatorValue(node) != node.output || node.on != comparatorShouldTurnOn(node))) {
                    schedule(node, 2, node.prioritized ? Priority.HIGH : Priority.NORMAL);
                }
            }
            case LAMP -> {
                boolean powered = input(node) > 0;
                if (node.on != powered) {
                    if (node.on) {
                        schedule(node, 4, Priority.NORMAL);
                    } else {
                        node.on = true;
                    }
                }
            }
            default -> {
            }
        }
    }

    private void tick(Node node) {
        switch (node.kind) {
            case TORCH, WALL_TORCH -> tickTorch(node);
            case REPEATER -> {
                if (!locked(node)) {
                    boolean should = input(node) > 0;
                    if (node.on && !should) {
                        node.on = false;
                        notifyReaders(node);
                    } else if (!node.on) {
                        node.on = true;
                        notifyReaders(node);
                        if (!should) {
                            schedule(node, 2 * node.voxel.delay(), Priority.VERY_HIGH);
                        }
                    }
                }
            }
            case COMPARATOR -> {
                int value = comparatorValue(node);
                int old = node.output;
                node.output = value;
                if (old != value || !node.voxel.subtract()) {
                    boolean should = comparatorShouldTurnOn(node);
                    boolean changed = old != value;
                    if (node.on && !should) {
                        node.on = false;
                        changed = true;
                    } else if (!node.on && should) {
                        node.on = true;
                        changed = true;
                    }
                    if (changed) {
                        notifyReaders(node);
                    }
                }
            }
            case LAMP -> {
                if (node.on && input(node) == 0) {
                    node.on = false;
                }
            }
            default -> {
            }
        }
    }

    private void tickTorch(Node node) {
        boolean powered = input(node) > 0;
        while (!node.toggles.isEmpty() && time - node.toggles.peekFirst() > BURNOUT_WINDOW) {
            node.toggles.pollFirst();
        }
        if (node.on) {
            if (powered) {
                node.on = false;
                notifyReaders(node);
                node.toggles.addLast(time);
                if (node.toggles.size() >= BURNOUT_TOGGLES) {
                    burnouts++;
                    schedule(node, BURNOUT_RESTART, Priority.NORMAL);
                }
            }
        } else if (!powered && node.toggles.size() < BURNOUT_TOGGLES) {
            node.on = true;
            notifyReaders(node);
        }
    }
}
