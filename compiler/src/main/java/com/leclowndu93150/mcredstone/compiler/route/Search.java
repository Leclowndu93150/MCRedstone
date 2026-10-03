package com.leclowndu93150.mcredstone.compiler.route;

import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import com.leclowndu93150.mcredstone.compiler.voxel.Kind;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Search {
    static final int STATES = 16;
    private static final int LOOKBACK = 12;
    static final int SEARCH_MARGIN = 8;
    private static final float HEURISTIC_WEIGHT = 2.0f;
    private static final float TABOO_COST = 6f;
    private static final float HANGING_PENALTY = 1f;
    private static final MoveType[] MOVE_TYPES = MoveType.values();
    private static final Dir[] DIRS = Dir.values();

    private final Router router;
    private final RouteGrid grid;
    private final int[] neighborhood;
    private final float[] cost;
    private final int[] parent;
    private final byte[] parentMove;
    private final int[] stamp;
    private final int[] ownStamp;
    private final Voxel[] ownVoxel;
    private final int[] pathStamp;
    private final Voxel[] pathVoxel;
    private final int[] pathNear;
    private final int[] ownNear;
    private final int[] ownNearStamp;
    private final Heap heap = new Heap();
    private final Move move = new Move();
    private final Move replay = new Move();
    private int generation;
    private int ownMarker;
    private int pathMarker;
    private float presentFactor;
    private Set<Integer> taboo = Set.of();
    private int boxMinX;
    private int boxMaxX;
    private int boxMinZ;
    private int boxMaxZ;

    Search(Router router) {
        this.router = router;
        this.grid = router.grid;
        this.neighborhood = router.neighborhood;
        int states = grid.size() * STATES;
        this.cost = new float[states];
        this.parent = new int[states];
        this.parentMove = new byte[states];
        this.stamp = new int[states];
        this.ownStamp = new int[grid.size()];
        this.ownVoxel = new Voxel[grid.size()];
        this.pathStamp = new int[grid.size()];
        this.pathVoxel = new Voxel[grid.size()];
        this.pathNear = new int[grid.size()];
        this.ownNear = new int[grid.size()];
        this.ownNearStamp = new int[grid.size()];
    }

    record Outcome(NetRoute route, String failure) {
    }

    Outcome route(RouteNet net, float presentFactor, Set<Integer> taboo) {
        this.presentFactor = presentFactor;
        this.taboo = taboo;
        NetRoute route = new NetRoute();
        int sourceIndex = net.source().index();
        List<Terminal> sinks = new ArrayList<>(net.sinks());
        sinks.sort(Comparator.comparingInt(sink -> distance(sourceIndex, sink.index())));
        for (int attempt = 0; attempt <= sinks.size(); attempt++) {
            route.voxels.clear();
            route.tiles.clear();
            ownMarker++;
            Terminal failed = null;
            for (Terminal sink : sinks) {
                if (!connect(net, route, sink, true) && !connect(net, route, sink, false)) {
                    failed = sink;
                    break;
                }
            }
            if (failed == null) {
                return new Outcome(route, null);
            }
            if (attempt == sinks.size()) {
                return new Outcome(route, "net " + net.id() + " cannot reach sink at " + grid.x(failed.index()) + "," + grid.y(failed.index()) + "," + grid.z(failed.index()));
            }
            sinks.remove(failed);
            sinks.add(0, failed);
        }
        return new Outcome(route, null);
    }

    private int distance(int a, int b) {
        return Math.abs(grid.x(a) - grid.x(b)) + Math.abs(grid.z(a) - grid.z(b)) + Math.abs(grid.y(a) - grid.y(b));
    }

    private boolean connect(RouteNet net, NetRoute route, Terminal sink, boolean prune) {
        generation++;
        heap.clear();
        int target = sink.index();
        int[] preds = new int[sink.dirs().size()];
        for (int i = 0; i < preds.length; i++) {
            Dir dir = sink.dirs().get(i);
            preds[i] = target - grid.delta(dir.dx, 0, dir.dz);
        }
        Terminal source = net.source();
        setBox(target, source.index());
        for (int tile : route.tiles.keySet()) {
            expandBox(tile);
        }
        for (Map.Entry<Integer, Integer> tile : route.tiles.entrySet()) {
            if (isPred(preds, tile.getKey()) && tile.getValue() >= 2) {
                return true;
            }
        }
        open(source.index() * STATES + source.strength(), 0, -1, (byte) -1, target);
        for (Map.Entry<Integer, Integer> tile : route.tiles.entrySet()) {
            open(tile.getKey() * STATES + tile.getValue(), 0, -1, (byte) -1, target);
        }
        while (!heap.isEmpty()) {
            float priority = heap.peekPriority();
            int key = heap.pop();
            float g = cost[key];
            int position = key / STATES;
            if (priority > g + HEURISTIC_WEIGHT * heuristic(position, target) + 1e-3f) {
                continue;
            }
            int strength = key % STATES;
            if (strength >= 2 && parent[key] >= 0 && isPred(preds, position)) {
                commit(route, key);
                return true;
            }
            markPath(key);
            boolean atSource = position == source.index();
            for (Dir dir : atSource ? source.dirs() : Dir.HORIZONTAL) {
                for (MoveType type : MOVE_TYPES) {
                    if (!move.build(grid, type, position, strength, dir) || !inBox(move.landing)) {
                        continue;
                    }
                    float moveCost = evaluate(net.id(), position, target, preds);
                    if (moveCost < 0) {
                        continue;
                    }
                    int next = move.landing * STATES + move.strength;
                    float total = g + moveCost + type.penalty + move.hanging * HANGING_PENALTY;
                    if (prune ? !dominated(move.landing, move.strength, total) : stamp[next] != generation || total < cost[next]) {
                        open(next, total, key, (byte) (type.ordinal() * 4 + dir.ordinal() - 2), target);
                    }
                }
            }
        }
        return false;
    }

    private boolean dominated(int position, int strength, float total) {
        int base = position * STATES;
        for (int s = strength; s < STATES; s++) {
            int key = base + s;
            if (stamp[key] == generation && cost[key] <= total) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPred(int[] preds, int position) {
        for (int pred : preds) {
            if (pred == position) {
                return true;
            }
        }
        return false;
    }

    private void setBox(int a, int b) {
        boxMinX = Math.min(grid.x(a), grid.x(b)) - SEARCH_MARGIN;
        boxMaxX = Math.max(grid.x(a), grid.x(b)) + SEARCH_MARGIN;
        boxMinZ = Math.min(grid.z(a), grid.z(b)) - SEARCH_MARGIN;
        boxMaxZ = Math.max(grid.z(a), grid.z(b)) + SEARCH_MARGIN;
    }

    private void expandBox(int position) {
        boxMinX = Math.min(boxMinX, grid.x(position) - SEARCH_MARGIN);
        boxMaxX = Math.max(boxMaxX, grid.x(position) + SEARCH_MARGIN);
        boxMinZ = Math.min(boxMinZ, grid.z(position) - SEARCH_MARGIN);
        boxMaxZ = Math.max(boxMaxZ, grid.z(position) + SEARCH_MARGIN);
    }

    private boolean inBox(int position) {
        int x = grid.x(position);
        int z = grid.z(position);
        return x >= boxMinX && x <= boxMaxX && z >= boxMinZ && z <= boxMaxZ;
    }

    private void markPath(int key) {
        pathMarker++;
        int steps = 0;
        while (parent[key] >= 0 && steps++ < LOOKBACK) {
            int from = parent[key];
            int code = parentMove[key];
            replay.build(grid, MOVE_TYPES[code / 4], from / STATES, from % STATES, DIRS[code % 4 + 2]);
            for (int i = 0; i < replay.count; i++) {
                int position = replay.positions[i];
                pathStamp[position] = pathMarker;
                pathVoxel[position] = replay.voxels[i];
                for (int offset : neighborhood) {
                    pathNear[position + offset] = pathMarker;
                }
            }
            key = from;
        }
    }

    private Voxel ownVoxel(int position) {
        if (pathStamp[position] == pathMarker) {
            return pathVoxel[position];
        }
        return ownStamp[position] == ownMarker ? ownVoxel[position] : null;
    }

    private int ownNearCount(int position) {
        return ownNearStamp[position] == ownMarker ? ownNear[position] : 0;
    }

    private void open(int key, float g, int from, byte moveCode, int target) {
        stamp[key] = generation;
        cost[key] = g;
        parent[key] = from;
        parentMove[key] = moveCode;
        heap.push(key, g + HEURISTIC_WEIGHT * heuristic(key / STATES, target));
    }

    private float heuristic(int position, int target) {
        return Math.abs(grid.x(position) - grid.x(target)) + Math.abs(grid.z(position) - grid.z(target)) + 1.5f * Math.abs(grid.y(position) - grid.y(target));
    }

    private float evaluate(int net, int attach, int target, int[] preds) {
        int attachSupport = attach - grid.strideY;
        float total = 0;
        for (int i = 0; i < move.count; i++) {
            int position = move.positions[i];
            Voxel voxel = move.voxels[i];
            if (grid.fixedOwner[position] != RouteGrid.FREE) {
                return -1;
            }
            Voxel existing = ownVoxel(position);
            if (existing != null) {
                if (position == attachSupport && existing.kind() == Kind.SOLID && voxel.kind() == Kind.SOLID) {
                    continue;
                }
                if (existing == Move.RESERVE && voxel == Move.RESERVE) {
                    continue;
                }
                return -1;
            }
            if (voxel == Move.RESERVE) {
                int above = position + grid.strideY;
                if (grid.fixedVoxel[above] != null || needsSupport(ownVoxel(above))) {
                    return -1;
                }
            } else if (needsSupport(voxel)) {
                Voxel support = ownVoxel(position - grid.strideY);
                if (support == Move.RESERVE || (support != null && support.kind() == Kind.WIRE)) {
                    return -1;
                }
            }
            boolean newWire = voxel.kind() == Kind.WIRE;
            if (!taboo.isEmpty() && taboo.contains(position)) {
                total += TABOO_COST;
            }
            if (Router.inert(voxel)) {
                total += (1 + grid.history[position]) * (1 + presentFactor * grid.occupied[position]);
                continue;
            }
            int inertHere = grid.occupied[position] - grid.usage[position];
            if (router.hardOwner[position] == RouteGrid.FREE && pathNear[position] != pathMarker && (!newWire || ownNearCount(position) == 0)) {
                total += (1 + grid.history[position]) * (1 + presentFactor * (router.nearUsage[position] + inertHere));
                continue;
            }
            boolean landingPred = position == move.landing && isPred(preds, position);
            int conflicts = 0;
            for (int offset : neighborhood) {
                int neighbor = position + offset;
                int owner = grid.fixedOwner[neighbor];
                if (owner >= 0) {
                    if (owner != net) {
                        return -1;
                    }
                    if (neighbor != attach && !(neighbor == target && landingPred)) {
                        return -1;
                    }
                }
                if (newWire && neighbor != attach && neighbor != attachSupport) {
                    Voxel neighborVoxel = ownVoxel(neighbor);
                    if (neighborVoxel != null && neighborVoxel.kind() == Kind.WIRE) {
                        return -1;
                    }
                }
                conflicts += grid.usage[neighbor];
            }
            total += (1 + grid.history[position]) * (1 + presentFactor * (conflicts + inertHere));
        }
        return total;
    }

    private static boolean needsSupport(Voxel voxel) {
        return voxel != null && voxel != Move.RESERVE && (voxel.kind() == Kind.WIRE || voxel.kind() == Kind.REPEATER);
    }

    private void commit(NetRoute route, int goal) {
        List<int[]> steps = new ArrayList<>();
        int key = goal;
        while (parent[key] >= 0) {
            steps.add(new int[]{parent[key], parentMove[key]});
            key = parent[key];
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            int from = steps.get(i)[0];
            int code = steps.get(i)[1];
            move.build(grid, MOVE_TYPES[code / 4], from / STATES, from % STATES, DIRS[code % 4 + 2]);
            for (int v = 0; v < move.count; v++) {
                int position = move.positions[v];
                if (ownStamp[position] == ownMarker) {
                    continue;
                }
                route.voxels.put(position, move.voxels[v]);
                ownStamp[position] = ownMarker;
                ownVoxel[position] = move.voxels[v];
                for (int offset : neighborhood) {
                    int neighbor = position + offset;
                    if (ownNearStamp[neighbor] != ownMarker) {
                        ownNearStamp[neighbor] = ownMarker;
                        ownNear[neighbor] = 0;
                    }
                    ownNear[neighbor]++;
                }
            }
            route.tiles.merge(move.landing, move.strength, Math::max);
        }
    }
}
