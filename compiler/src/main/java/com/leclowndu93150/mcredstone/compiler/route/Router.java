package com.leclowndu93150.mcredstone.compiler.route;

import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import com.leclowndu93150.mcredstone.compiler.voxel.Kind;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.concurrent.LinkedBlockingQueue;

public final class Router {
    public record Result(boolean success, int iterations, int conflicts, String failure, List<String> details) {
    }

    private static final int MULTIPLE = -3;
    private static final float HISTORY_STEP = 0.5f;
    private static final int MAX_THREADS = 12;
    private static final int STALL_PASSES = 20;
    private static final int KICK_LIMIT = 12;
    private static final int MAX_KICKS = 6;
    private static final int KICK_RADIUS = 6;

    final RouteGrid grid;
    final int[] neighborhood;
    final int[] nearUsage;
    final int[] hardOwner;
    private final List<RouteNet> nets;
    private final NetRoute[] routes;
    private final int[] markStamp;
    private final Map<Integer, Set<Integer>> taboo = new HashMap<>();
    private final int threads;
    private final BlockingQueue<Search> searches;
    private int marker;
    private float presentFactor = 0.5f;
    private Consumer<String> progress = message -> {
    };

    public Router(RouteGrid grid, List<RouteNet> nets) {
        this.grid = grid;
        this.nets = nets;
        this.routes = new NetRoute[nets.size()];
        List<Integer> offsets = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            offsets.add(grid.delta(0, dy, 0));
            for (Dir dir : Dir.HORIZONTAL) {
                offsets.add(grid.delta(dir.dx, dy, dir.dz));
            }
        }
        this.neighborhood = offsets.stream().mapToInt(Integer::intValue).toArray();
        this.nearUsage = new int[grid.size()];
        this.markStamp = new int[grid.size()];
        this.hardOwner = new int[grid.size()];
        Arrays.fill(hardOwner, RouteGrid.FREE);
        for (int position = 0; position < grid.size(); position++) {
            int owner = grid.fixedOwner[position];
            if (owner < 0) {
                continue;
            }
            for (int offset : neighborhood) {
                int neighbor = position + offset;
                if (neighbor < 0 || neighbor >= grid.size()) {
                    continue;
                }
                hardOwner[neighbor] = hardOwner[neighbor] == RouteGrid.FREE || hardOwner[neighbor] == owner ? owner : MULTIPLE;
            }
        }
        for (int i = 0; i < routes.length; i++) {
            routes[i] = new NetRoute();
        }
        long searchBytes = (long) grid.size() * Search.STATES * 13 + (long) grid.size() * 40;
        long budget = Runtime.getRuntime().maxMemory() / 3;
        int byMemory = (int) Math.max(1, budget / searchBytes);
        this.threads = Math.max(1, Math.min(Math.min(MAX_THREADS, byMemory), Runtime.getRuntime().availableProcessors() - 1));
        this.searches = new LinkedBlockingQueue<>();
    }

    public void onProgress(Consumer<String> listener) {
        this.progress = listener;
    }

    public NetRoute route(int net) {
        return routes[net];
    }

    public Result run(int maxIterations) {
        Set<Integer> pending = new LinkedHashSet<>();
        for (RouteNet net : nets) {
            pending.add(net.id());
        }
        return negotiate(pending, maxIterations);
    }

    public Result repair(Set<Integer> broken, int maxIterations) {
        for (int net : broken) {
            taboo.computeIfAbsent(net, k -> new HashSet<>()).addAll(routes[net].voxels.keySet());
        }
        return negotiate(new LinkedHashSet<>(broken), maxIterations);
    }

    public Set<Integer> netsAt(Set<Long> positions) {
        Set<Integer> found = new LinkedHashSet<>();
        for (int net = 0; net < routes.length; net++) {
            for (int index : routes[net].voxels.keySet()) {
                if (positions.contains(Pos.of(grid.x(index), grid.y(index), grid.z(index)))) {
                    found.add(net);
                    break;
                }
            }
        }
        return found;
    }

    private Result negotiate(Set<Integer> pending, int maxIterations) {
        List<RouteNet> order = new ArrayList<>(nets);
        order.sort(Comparator.comparingInt((RouteNet net) -> -span(net)));
        ExecutorService executor = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "mcredstone-router");
            thread.setDaemon(true);
            return thread;
        });
        try {
            int conflicts = 0;
            int best = Integer.MAX_VALUE;
            int bestIteration = 0;
            int kicks = 0;
            for (int iteration = 1; iteration <= maxIterations; iteration++) {
                List<RouteNet> batch = new ArrayList<>();
                for (RouteNet net : order) {
                    if (pending.contains(net.id())) {
                        batch.add(net);
                    }
                }
                List<List<RouteNet>> chunks = iteration == 1 ? List.of(batch) : disjointChunks(batch);
                for (List<RouteNet> chunk : chunks) {
                    for (RouteNet net : chunk) {
                        ripUp(routes[net.id()]);
                    }
                    List<Future<Search.Outcome>> futures = new ArrayList<>();
                    for (RouteNet net : chunk) {
                        float factor = presentFactor;
                        Set<Integer> forbidden = taboo.getOrDefault(net.id(), Set.of());
                        futures.add(executor.submit(() -> {
                            Search search = acquire();
                            try {
                                return search.route(net, factor, forbidden);
                            } finally {
                                searches.offer(search);
                            }
                        }));
                    }
                    List<Search.Outcome> outcomes = new ArrayList<>();
                    for (Future<Search.Outcome> future : futures) {
                        outcomes.add(future.get());
                    }
                    for (int i = 0; i < chunk.size(); i++) {
                        Search.Outcome outcome = outcomes.get(i);
                        if (outcome.failure() != null) {
                            return new Result(false, iteration, conflicts, outcome.failure(), List.of());
                        }
                        NetRoute route = routes[chunk.get(i).id()];
                        route.voxels.putAll(outcome.route().voxels);
                        route.tiles.putAll(outcome.route().tiles);
                        for (Map.Entry<Integer, Voxel> voxel : route.voxels.entrySet()) {
                            changeUsage(voxel.getKey(), voxel.getValue(), 1);
                        }
                    }
                }
                Set<Integer> conflicted = findConflicts();
                conflicts = conflicted.size();
                progress.accept("routing pass " + iteration + ": " + batch.size() + " nets rerouted, " + conflicts + " still conflicting");
                if (conflicted.isEmpty()) {
                    return new Result(true, iteration, 0, null, List.of());
                }
                if (conflicts < best) {
                    best = conflicts;
                    bestIteration = iteration;
                } else if (iteration - bestIteration >= STALL_PASSES) {
                    if (conflicts <= KICK_LIMIT && kicks < MAX_KICKS) {
                        kicks++;
                        bestIteration = iteration;
                        conflicted = neighborsOf(conflicted);
                        for (int net : conflicted) {
                            taboo.computeIfAbsent(net, k -> new HashSet<>()).addAll(routes[net].voxels.keySet());
                        }
                        progress.accept("routing stuck on " + conflicts + " nets (" + conflictSummary() + ") " + describeConflicts().subList(0, Math.min(8, describeConflicts().size())) + ", rerouting " + conflicted.size() + " nearby nets");
                    } else {
                        progress.accept("routing stalled: " + conflictSummary() + " " + describeConflicts());
                        return new Result(false, iteration, conflicts, conflicts + " nets still conflict (stalled)", describeConflicts());
                    }
                }
                pending = conflicted;
                presentFactor = Math.min(presentFactor * 1.4f, 1000f);
            }
            return new Result(false, maxIterations, conflicts, conflicts + " nets still conflict", describeConflicts());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, 0, 0, "interrupted", List.of());
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private List<List<RouteNet>> disjointChunks(List<RouteNet> batch) {
        List<List<RouteNet>> chunks = new ArrayList<>();
        List<RouteNet> remaining = new ArrayList<>(batch);
        while (!remaining.isEmpty()) {
            List<RouteNet> chunk = new ArrayList<>();
            List<int[]> boxes = new ArrayList<>();
            for (Iterator<RouteNet> iterator = remaining.iterator(); iterator.hasNext() && chunk.size() < threads; ) {
                RouteNet net = iterator.next();
                int[] box = box(net);
                boolean free = true;
                for (int[] other : boxes) {
                    if (box[0] <= other[2] && other[0] <= box[2] && box[1] <= other[3] && other[1] <= box[3]) {
                        free = false;
                        break;
                    }
                }
                if (free) {
                    chunk.add(net);
                    boxes.add(box);
                    iterator.remove();
                }
            }
            chunks.add(chunk);
        }
        return chunks;
    }

    private int[] box(RouteNet net) {
        int minX = grid.x(net.source().index());
        int maxX = minX;
        int minZ = grid.z(net.source().index());
        int maxZ = minZ;
        for (Terminal sink : net.sinks()) {
            minX = Math.min(minX, grid.x(sink.index()));
            maxX = Math.max(maxX, grid.x(sink.index()));
            minZ = Math.min(minZ, grid.z(sink.index()));
            maxZ = Math.max(maxZ, grid.z(sink.index()));
        }
        int margin = 2 * Search.SEARCH_MARGIN + 2;
        return new int[]{minX - margin, minZ - margin, maxX + margin, maxZ + margin};
    }

    private Search acquire() {
        Search search = searches.poll();
        return search != null ? search : new Search(this);
    }

    private int span(RouteNet net) {
        int minX = grid.x(net.source().index());
        int maxX = minX;
        int minZ = grid.z(net.source().index());
        int maxZ = minZ;
        for (Terminal sink : net.sinks()) {
            minX = Math.min(minX, grid.x(sink.index()));
            maxX = Math.max(maxX, grid.x(sink.index()));
            minZ = Math.min(minZ, grid.z(sink.index()));
            maxZ = Math.max(maxZ, grid.z(sink.index()));
        }
        return (maxX - minX) + (maxZ - minZ);
    }

    private void ripUp(NetRoute route) {
        for (Map.Entry<Integer, Voxel> voxel : route.voxels.entrySet()) {
            changeUsage(voxel.getKey(), voxel.getValue(), -1);
        }
        route.voxels.clear();
        route.tiles.clear();
    }

    private void changeUsage(int position, Voxel voxel, int delta) {
        grid.occupied[position] += delta;
        if (inert(voxel)) {
            return;
        }
        grid.usage[position] += delta;
        for (int offset : neighborhood) {
            nearUsage[position + offset] += delta;
        }
    }

    static boolean inert(Voxel voxel) {
        return voxel.kind() == Kind.GLASS;
    }

    private List<int[]> clashes(NetRoute route) {
        marker++;
        for (Map.Entry<Integer, Voxel> voxel : route.voxels.entrySet()) {
            if (!inert(voxel.getValue())) {
                markStamp[voxel.getKey()] = marker;
            }
        }
        List<int[]> found = new ArrayList<>();
        for (Map.Entry<Integer, Voxel> voxel : route.voxels.entrySet()) {
            int position = voxel.getKey();
            if (grid.occupied[position] > 1) {
                found.add(new int[]{position, position});
            }
            if (inert(voxel.getValue())) {
                continue;
            }
            for (int offset : neighborhood) {
                int neighbor = position + offset;
                if (grid.usage[neighbor] - (markStamp[neighbor] == marker ? 1 : 0) > 0) {
                    found.add(new int[]{position, neighbor});
                }
            }
        }
        return found;
    }

    private Set<Integer> neighborsOf(Set<Integer> conflicted) {
        Set<Long> buckets = new HashSet<>();
        for (int net : conflicted) {
            for (int index : routes[net].voxels.keySet()) {
                int bx = Math.floorDiv(grid.x(index), KICK_RADIUS);
                int bz = Math.floorDiv(grid.z(index), KICK_RADIUS);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        buckets.add(((long) (bx + dx) << 32) ^ (bz + dz & 0xFFFFFFFFL));
                    }
                }
            }
        }
        Set<Integer> result = new LinkedHashSet<>(conflicted);
        for (int net = 0; net < routes.length; net++) {
            if (result.contains(net)) {
                continue;
            }
            for (int index : routes[net].voxels.keySet()) {
                long bucket = ((long) Math.floorDiv(grid.x(index), KICK_RADIUS) << 32) ^ (Math.floorDiv(grid.z(index), KICK_RADIUS) & 0xFFFFFFFFL);
                if (buckets.contains(bucket)) {
                    result.add(net);
                    break;
                }
            }
        }
        return result;
    }

    private String conflictSummary() {
        Map<Integer, Integer> byZ = new TreeMap<>();
        Map<Integer, Integer> byY = new TreeMap<>();
        for (NetRoute route : routes) {
            Set<Integer> seen = new HashSet<>();
            for (int[] clash : clashes(route)) {
                if (seen.add(clash[0])) {
                    byZ.merge(grid.z(clash[0]) / 4 * 4, 1, Integer::sum);
                    byY.merge(grid.y(clash[0]), 1, Integer::sum);
                }
            }
        }
        return "by z band " + byZ + ", by y " + byY;
    }

    private List<String> describeConflicts() {
        List<String> details = new ArrayList<>();
        for (int net = 0; net < routes.length && details.size() < 20; net++) {
            NetRoute route = routes[net];
            for (int[] clash : clashes(route)) {
                details.add("net " + net + " " + route.voxels.get(clash[0]).kind() + " at " + grid.x(clash[0]) + "," + grid.y(clash[0]) + "," + grid.z(clash[0])
                    + " near " + grid.x(clash[1]) + "," + grid.y(clash[1]) + "," + grid.z(clash[1]));
            }
        }
        return details;
    }

    private Set<Integer> findConflicts() {
        Set<Integer> conflicted = new LinkedHashSet<>();
        for (int net = 0; net < routes.length; net++) {
            List<int[]> found = clashes(routes[net]);
            if (!found.isEmpty()) {
                conflicted.add(net);
            }
            for (int[] clash : found) {
                grid.history[clash[1]] += HISTORY_STEP;
            }
        }
        return conflicted;
    }
}
