package com.leclowndu93150.mcredstone.compiler.layout;

import com.leclowndu93150.mcredstone.compiler.lib.ClockMacro;
import com.leclowndu93150.mcredstone.compiler.lib.Edge;
import com.leclowndu93150.mcredstone.compiler.lib.Library;
import com.leclowndu93150.mcredstone.compiler.lib.Pin;
import com.leclowndu93150.mcredstone.compiler.lib.PinRole;
import com.leclowndu93150.mcredstone.compiler.lib.Placed;
import com.leclowndu93150.mcredstone.compiler.netlist.Design;
import com.leclowndu93150.mcredstone.compiler.netlist.DesignMeta;
import com.leclowndu93150.mcredstone.compiler.netlist.IoPort;
import com.leclowndu93150.mcredstone.compiler.netlist.Netlist;
import com.leclowndu93150.mcredstone.compiler.netlist.NetlistSim;
import com.leclowndu93150.mcredstone.compiler.place.AnnealPlacer;
import com.leclowndu93150.mcredstone.compiler.place.Floorplan;
import com.leclowndu93150.mcredstone.compiler.place.IoBit;
import com.leclowndu93150.mcredstone.compiler.place.NetSpec;
import com.leclowndu93150.mcredstone.compiler.place.PinRef;
import com.leclowndu93150.mcredstone.compiler.place.PlacedCell;
import com.leclowndu93150.mcredstone.compiler.route.RouteGrid;
import com.leclowndu93150.mcredstone.compiler.route.RouteNet;
import com.leclowndu93150.mcredstone.compiler.route.Router;
import com.leclowndu93150.mcredstone.compiler.route.Terminal;
import com.leclowndu93150.mcredstone.compiler.sim.GraphSim;
import com.leclowndu93150.mcredstone.compiler.synth.ClockInsert;
import com.leclowndu93150.mcredstone.compiler.synth.ConstantFold;
import com.leclowndu93150.mcredstone.compiler.synth.NorMerge;
import com.leclowndu93150.mcredstone.compiler.timing.ClockBalancer;
import com.leclowndu93150.mcredstone.compiler.timing.ClockTuner;
import com.leclowndu93150.mcredstone.compiler.timing.Sta;
import com.leclowndu93150.mcredstone.compiler.verify.Extractor;
import com.leclowndu93150.mcredstone.compiler.verify.Lvs;
import com.leclowndu93150.mcredstone.compiler.verify.Reach;
import com.leclowndu93150.mcredstone.compiler.voxel.Kind;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelGrid;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class Compiler {
    public static final String VERSION = "m6-1";
    private static final int ATTEMPTS = 6;
    private static final int ROUTER_ITERATIONS = 150;
    private static final int FIRST_Z = 9;
    private static final int PAINT_ITERATIONS = 1000;
    private static final int REPAIR_ROUNDS = 4;
    private static final int LIGHT_SPACING = 6;
    private static final String ROUTE_COLOR = "gray_wool";

    public record Assembly(Layout layout, Lvs.Report lvs) {
    }

    private record Clock(AnnealPlacer.Clocking clocking) {
    }

    private Compiler() {
    }

    public static Netlist optimize(Netlist netlist) {
        return NorMerge.run(ConstantFold.run(netlist));
    }

    public static Layout compile(Design design) {
        return compile(design, message -> {
        });
    }

    public static Layout compile(Design design, Consumer<String> progress) {
        Assembly assembly = assemble(design, progress);
        if (!assembly.lvs().clean()) {
            List<String> errors = assembly.lvs().errors();
            throw new CompileException("layout check failed: " + String.join("; ", errors.subList(0, Math.min(8, errors.size()))));
        }
        return assembly.layout();
    }

    public static Assembly assemble(Design design) {
        return assemble(design, message -> {
        });
    }

    public static Assembly assemble(Design design, Consumer<String> progress) {
        Netlist netlist = optimize(design.netlist());
        if (design.vectors() != null) {
            int failures = new NetlistSim(netlist).check(design.vectors());
            if (failures > 0) {
                throw new CompileException("optimized netlist fails " + failures + " vectors");
            }
        }
        DesignMeta meta = design.meta();
        Clock clock = null;
        if (netlist.sequential()) {
            ClockInsert.Result inserted = ClockInsert.run(netlist);
            netlist = inserted.netlist();
            List<IoPort> inputs = new ArrayList<>(meta.inputs());
            inputs.add(new IoPort(ClockInsert.RUN, 1, "lever", null));
            meta = new DesignMeta(meta.name(), meta.clock(), inputs, meta.outputs());
            ClockMacro macro = new ClockMacro(ClockMacro.MAX_RETURN_REPEATERS);
            clock = new Clock(new AnnealPlacer.Clocking(inserted.hold(), inserted.k1(), inserted.k2(), macro));
        }
        List<String> failures = new ArrayList<>();
        int attempt = 0;
        int displayRows = 0;
        int pixelDepth = 0;
        int zoneExtra = 0;
        for (IoPort io : meta.outputs()) {
            if (io.matrix()) {
                displayRows += io.rows() * io.scale();
                pixelDepth = Math.max(pixelDepth, io.scale() == 1 ? Library.PIXEL_DEPTH : Library.BIG_PIXEL_DEPTH);
                zoneExtra += 2 * Math.max(4, io.rows()) / io.scale();
            }
        }
        for (AnnealPlacer.Params params : candidates(netlist.gates().size(), clock != null, displayRows, pixelDepth + zoneExtra)) {
            if (attempt >= ATTEMPTS) {
                break;
            }
            long start = System.nanoTime();
            progress.accept("attempt " + (attempt + 1) + ": placing " + netlist.gates().size() + " gates on " + params.logicTiers() + " tiers");
            Floorplan floorplan = new AnnealPlacer(netlist, meta, params, clock == null ? null : clock.clocking()).place();
            RouteGrid grid = new RouteGrid(floorplan.minX(), floorplan.maxX(), floorplan.minZ(), floorplan.maxZ(), floorplan.tiers(), floorplan.zoneEnd());
            List<RouteNet> nets = fixCells(grid, floorplan);
            Router router = new Router(grid, nets);
            router.onProgress(progress);
            Router.Result result = router.run(ROUTER_ITERATIONS);
            long millis = (System.nanoTime() - start) / 1_000_000;
            if (result.success()) {
                Assembly assembly = finish(meta.name(), netlist, floorplan, grid, router, result, attempt, millis, failures, clock);
                for (int round = 0; round < REPAIR_ROUNDS && !assembly.lvs().clean(); round++) {
                    Set<Integer> broken = brokenNets(floorplan, router, assembly.lvs().suspects());
                    if (broken.isEmpty()) {
                        break;
                    }
                    Router.Result repaired = router.repair(broken, ROUTER_ITERATIONS);
                    if (!repaired.success()) {
                        break;
                    }
                    assembly = finish(meta.name(), netlist, floorplan, grid, router, repaired, attempt, (System.nanoTime() - start) / 1_000_000, failures, clock);
                }
                if (assembly.lvs().clean() || attempt + 1 >= ATTEMPTS) {
                    return assembly;
                }
                failures.add("attempt " + (attempt + 1) + ": layout check failed after repairs: " + assembly.lvs().errors().get(0));
                progress.accept("attempt " + (attempt + 1) + " failed the layout check: " + String.join("; ", assembly.lvs().errors().subList(0, Math.min(6, assembly.lvs().errors().size()))));
                attempt++;
                continue;
            }
            failures.add("attempt " + (attempt + 1) + " " + params.logicTiers() + "x" + params.slotsX() + "x" + params.slotsZ() + ": " + result.failure() + " after " + result.iterations() + " iterations, " + millis + " ms" + result.details());
            progress.accept("attempt " + (attempt + 1) + " failed: " + result.failure());
            attempt++;
        }
        throw new CompileException("routing failed: " + String.join("; ", failures));
    }

    private static Set<Integer> brokenNets(Floorplan floorplan, Router router, Set<Long> suspects) {
        Set<Integer> broken = new LinkedHashSet<>(router.netsAt(suspects));
        for (NetSpec spec : floorplan.nets()) {
            PlacedCell cell = floorplan.cell(spec.source().cell());
            for (long owner : cell.ownerPositions(cell.template().pin(spec.source().pin()))) {
                if (suspects.contains(owner)) {
                    broken.add(spec.id());
                }
            }
            for (PinRef sink : spec.sinks()) {
                PlacedCell sinkCell = floorplan.cell(sink.cell());
                if (suspects.contains(sinkCell.ownerPos(sinkCell.template().pin(sink.pin())))) {
                    broken.add(spec.id());
                }
            }
        }
        return broken;
    }

    static List<AnnealPlacer.Params> candidates(int gates, boolean sequential, int displayRows, int displayDepth) {
        int pitch = sequential ? 8 : 7;
        boolean large = gates > 150;
        int stride = large ? 2 : 1;
        int minTiers = displayRows > 0 ? Math.max(2, (displayRows + 2 * stride - 1) / (2 * stride)) : 2;
        double[] fills = large ? new double[]{1.8, 2.4, 3.0} : new double[]{1.15, 1.5, 2.0};
        List<AnnealPlacer.Params> candidates = new ArrayList<>();
        for (double fill : fills) {
            for (int tiers = minTiers; tiers <= minTiers + 3; tiers++) {
                int needed = Math.max(1, (int) Math.ceil(gates * fill));
                int perTier = (int) Math.ceil(needed / (double) tiers);
                int slotsX = Math.max(1, (int) Math.ceil(Math.sqrt(perTier)));
                int slotsZ = Math.max(1, (int) Math.ceil(perTier / (double) slotsX));
                int firstZ = displayRows > 0 ? displayDepth + FIRST_Z : FIRST_Z;
                candidates.add(new AnnealPlacer.Params(tiers, slotsX, slotsZ, pitch, firstZ, 1, 3, 1, stride));
            }
        }
        candidates.sort(Comparator.comparingInt(AnnealPlacer.Params::volume));
        return candidates;
    }

    private static List<RouteNet> fixCells(RouteGrid grid, Floorplan floorplan) {
        int owners = floorplan.nets().size();
        Map<Integer, Integer> netOfBit = new HashMap<>();
        for (NetSpec net : floorplan.nets()) {
            netOfBit.put(net.bit(), net.id());
        }
        for (PlacedCell cell : floorplan.cells()) {
            int cellOwner = owners + cell.id();
            for (Placed placed : cell.template().voxels()) {
                long pos = cell.at(placed.dx(), placed.dy(), placed.dz());
                grid.fix(Pos.x(pos), Pos.y(pos), Pos.z(pos), cellOwner, placed.voxel());
            }
            for (int[] blocked : cell.template().blocked()) {
                long pos = cell.at(blocked[0], blocked.length > 2 ? blocked[2] : 0, blocked[1]);
                grid.fix(Pos.x(pos), Pos.y(pos), Pos.z(pos), RouteGrid.BLOCKED, null);
            }
            for (Pin pin : cell.template().pins()) {
                long pos = cell.pinPos(pin);
                Integer bit = cell.pinNets().get(pin.name());
                Integer net = bit == null ? null : netOfBit.get(bit);
                grid.fix(Pos.x(pos), Pos.y(pos), Pos.z(pos), net == null ? RouteGrid.BLOCKED : net, net == null ? null : Voxel.WIRE);
            }
        }
        List<RouteNet> nets = new ArrayList<>();
        for (NetSpec spec : floorplan.nets()) {
            List<Terminal> sinks = new ArrayList<>();
            for (PinRef sink : spec.sinks()) {
                sinks.add(terminal(grid, floorplan, sink, false));
            }
            nets.add(new RouteNet(spec.id(), terminal(grid, floorplan, spec.source(), true), sinks));
        }
        return nets;
    }

    private static Terminal terminal(RouteGrid grid, Floorplan floorplan, PinRef ref, boolean source) {
        PlacedCell cell = floorplan.cell(ref.cell());
        Pin pin = cell.template().pin(ref.pin());
        long pos = cell.pinPos(pin);
        return new Terminal(grid.index(Pos.x(pos), Pos.y(pos), Pos.z(pos)), pin.dirs(), source ? pin.strength() : 0);
    }

    private static Assembly finish(String name, Netlist netlist, Floorplan floorplan, RouteGrid grid, Router router, Router.Result result,
                                   int attempt, long millis, List<String> failures, Clock clock) {
        VoxelGrid voxels = new VoxelGrid();
        Map<Long, String> decor = new HashMap<>();
        Map<Long, String> floorColors = new HashMap<>();
        Map<Long, Set<Long>> expected = new LinkedHashMap<>();
        Map<Long, Set<Long>> sides = new LinkedHashMap<>();
        for (PlacedCell cell : floorplan.cells()) {
            String color = sectionColor(cell);
            for (Placed placed : cell.template().voxels()) {
                long pos = cell.at(placed.dx(), placed.dy(), placed.dz());
                voxels.set(pos, placed.voxel());
                if (placed.voxel().kind() == Kind.SOLID) {
                    decor.put(pos, color);
                }
                if (placed.dy() == 0) {
                    floorColors.put(cell.at(placed.dx(), -1, placed.dz()), color);
                }
            }
            for (int[] blocked : cell.template().blocked()) {
                floorColors.put(cell.at(blocked[0], (blocked.length > 2 ? blocked[2] : 0) - 1, blocked[1]), color);
            }
            for (Pin pin : cell.template().pins()) {
                floorColors.put(cell.at(pin.dx(), -1, pin.dz()), color);
                if (cell.pinNets().containsKey(pin.name())) {
                    voxels.set(cell.pinPos(pin), Voxel.WIRE);
                }
                if (pin.role() == PinRole.OUTPUT) {
                    for (long owner : cell.ownerPositions(pin)) {
                        expected.computeIfAbsent(owner, k -> new LinkedHashSet<>());
                    }
                }
            }
            for (Edge edge : cell.template().internalEdges()) {
                long from = cell.at(edge.fromDx(), edge.fromDy(), edge.fromDz());
                long to = cell.at(edge.toDx(), edge.toDy(), edge.toDz());
                expected.computeIfAbsent(from, k -> new LinkedHashSet<>());
                (edge.side() ? sides : expected).computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
            }
        }
        Set<Long> transparent = new HashSet<>();
        Set<Long> holes = new HashSet<>();
        List<String> overlaps = new ArrayList<>();
        for (NetSpec spec : floorplan.nets()) {
            for (Map.Entry<Integer, Voxel> entry : router.route(spec.id()).voxels().entrySet()) {
                int index = entry.getKey();
                long pos = Pos.of(grid.x(index), grid.y(index), grid.z(index));
                Voxel voxel = entry.getValue();
                Voxel previous = voxels.get(pos);
                if (previous != null) {
                    overlaps.add("overlap at " + Pos.toString(pos) + ": " + previous.kind() + " vs " + voxel.kind() + " of net " + spec.id());
                }
                if (voxel.kind() == null) {
                    voxels.set(pos, null);
                    holes.add(pos);
                } else {
                    voxels.set(pos, voxel);
                    if (voxel.kind() == Kind.REPEATER) {
                        transparent.add(pos);
                    }
                    if (voxel.kind() == Kind.SOLID) {
                        decor.put(pos, ROUTE_COLOR);
                    }
                }
            }
            PlacedCell sourceCell = floorplan.cell(spec.source().cell());
            Set<Long> readers = new LinkedHashSet<>();
            for (PinRef sink : spec.sinks()) {
                PlacedCell sinkCell = floorplan.cell(sink.cell());
                readers.add(sinkCell.ownerPos(sinkCell.template().pin(sink.pin())));
            }
            for (long owner : sourceCell.ownerPositions(sourceCell.template().pin(spec.source().pin()))) {
                expected.computeIfAbsent(owner, k -> new LinkedHashSet<>()).addAll(readers);
            }
        }
        layFloors(voxels, floorplan, holes, floorColors, decor);
        Lvs.Report lvs = new Lvs(voxels, expected, sides, transparent).run();
        lvs.errors().addAll(0, overlaps);
        List<IoPin> inputs = new ArrayList<>();
        for (IoBit io : floorplan.inputs()) {
            PlacedCell cell = floorplan.cell(io.cell());
            inputs.add(new IoPin(io.port(), io.bit(), cell.at(0, 0, -1), io.kind(), io.key()));
        }
        List<IoPin> outputs = new ArrayList<>();
        for (IoBit io : floorplan.outputs()) {
            PlacedCell cell = floorplan.cell(io.cell());
            outputs.add(new IoPin(io.port(), io.bit(), cell.at(0, 0, 0), io.kind(), io.key()));
        }
        Timing timing = null;
        Map<Long, Boolean> latches = new HashMap<>();
        if (lvs.clean() && clock != null) {
            timing = tuneClock(voxels, floorplan, clock, latches, netlist, lvs);
        }
        Map<Long, VoxelState> states = Map.of();
        if (lvs.clean()) {
            GraphSim sim = new GraphSim(voxels);
            if (sim.paint(latches, PAINT_ITERATIONS)) {
                states = sim.states(voxels);
            } else {
                lvs.errors().add("initial state does not settle");
            }
        }
        int[] size = sizeOf(voxels);
        String report = String.format("%s: %d gates, %d nets, %d blocks, %dx%dx%d, settle %d gt, routed in %d iterations, %d ms (attempt %d)%s%s",
            name, netlist.gates().size(), floorplan.nets().size(), voxels.size(), size[0], size[1], size[2], lvs.settleTicks(), result.iterations(), millis, attempt + 1,
            timing == null ? "" : ", " + timing.report(),
            failures.isEmpty() ? "" : " [" + String.join("; ", failures) + "]");
        return new Assembly(new Layout(name, voxels, states, inputs, outputs, lvs.settleTicks(), timing, report, decor), lvs);
    }

    private static void layFloors(VoxelGrid voxels, Floorplan floorplan, Set<Long> holes, Map<Long, String> floorColors, Map<Long, String> decor) {
        Set<Long> floors = new HashSet<>();
        for (int tier = 0; tier <= floorplan.tiers(); tier++) {
            int y = Floorplan.floorY(tier);
            boolean walkable = RouteGrid.walkableFloor(y);
            String tierColor = (y / 4) % 2 == 0 ? "white_wool" : "light_gray_wool";
            for (int x = floorplan.minX(); x <= floorplan.maxX(); x++) {
                for (int z = floorplan.minZ(); z <= floorplan.maxZ(); z++) {
                    boolean zone = z < floorplan.zoneEnd();
                    if (!walkable && !zone) {
                        continue;
                    }
                    long pos = Pos.of(x, y, z);
                    if (voxels.get(pos) != null || holes.contains(pos)) {
                        continue;
                    }
                    boolean wool = walkable && !zone;
                    voxels.set(pos, wool ? Voxel.SOLID : Voxel.GLASS);
                    if (wool) {
                        decor.put(pos, floorColors.getOrDefault(pos, tierColor));
                        floors.add(pos);
                    }
                }
            }
        }
        for (long pos : floors) {
            if (Math.floorMod(Pos.x(pos), LIGHT_SPACING) == LIGHT_SPACING / 2 && Math.floorMod(Pos.z(pos), LIGHT_SPACING) == LIGHT_SPACING / 2 && isolated(voxels, floors, pos)) {
                decor.put(pos, "sea_lantern");
            }
        }
    }

    private static boolean isolated(VoxelGrid voxels, Set<Long> floors, long pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long neighbor = Pos.offset(pos, dx, dy, dz);
                    if (neighbor != pos && voxels.get(neighbor) != null && !floors.contains(neighbor)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static String sectionColor(PlacedCell cell) {
        String base = cell.template().name().split("@")[0];
        if (base.startsWith("clock")) {
            return "red_wool";
        }
        return switch (base) {
            case "tnor" -> "light_blue_wool";
            case "msff" -> "lime_wool";
            case "input_pad" -> "yellow_wool";
            case "led_out" -> "orange_wool";
            default -> "black_wool";
        };
    }

    private static Timing tuneClock(VoxelGrid voxels, Floorplan floorplan, Clock clock, Map<Long, Boolean> latches, Netlist netlist, Lvs.Report lvs) {
        Set<Long> masters = new HashSet<>();
        Set<Long> slaves = new HashSet<>();
        Set<Long> k1Locks = new HashSet<>();
        Set<Long> k2Locks = new HashSet<>();
        Set<Long> k1Taps = new HashSet<>();
        Set<Long> k2Taps = new HashSet<>();
        Set<Long> pads = new HashSet<>();
        Set<Long> lamps = new HashSet<>();
        Set<Long> opaque = new HashSet<>();
        PlacedCell clockCell = null;
        for (PlacedCell cell : floorplan.cells()) {
            String base = cell.template().name().split("@")[0];
            switch (base) {
                case "msff" -> {
                    long slave = cell.ownerPos(cell.template().pin(Library.OUT));
                    masters.add(cell.ownerPos(cell.template().pin(Library.D)));
                    slaves.add(slave);
                    k1Locks.add(cell.ownerPos(cell.template().pin(Library.K1)));
                    k2Locks.add(cell.ownerPos(cell.template().pin(Library.K2)));
                    Integer q = cell.pinNets().get(Library.OUT);
                    latches.put(slave, q != null && netlist.initialHigh().contains(q));
                }
                case "input_pad" -> pads.add(cell.ownerPos(cell.template().pin(Library.OUT)));
                case "led_out", "pixel_a", "pixel_b", "big_pixel" -> lamps.add(cell.at(0, 0, 0));
                default -> {
                    if (base.startsWith("clock")) {
                        clockCell = cell;
                        k1Taps.addAll(cell.ownerPositions(cell.template().pin(ClockMacro.K1)));
                        k2Taps.addAll(cell.ownerPositions(cell.template().pin(ClockMacro.K2)));
                        for (Placed placed : cell.template().voxels()) {
                            opaque.add(cell.at(placed.dx(), placed.dy(), placed.dz()));
                        }
                    }
                }
            }
        }
        ClockMacro macro = clock.clocking().macro();
        Map<Long, Reach> reach = new Extractor(voxels).extract();
        new ClockBalancer(voxels, reach, clockCell.at(ClockMacro.TAP1[0], 0, ClockMacro.TAP1[1]), k1Locks, opaque).balance();
        new ClockBalancer(voxels, reach, clockCell.at(ClockMacro.TAP3[0], 0, ClockMacro.TAP3[1]), k2Locks, opaque).balance();
        Sta.Result sta = new Sta(voxels, new Extractor(voxels).extract(),
            new Sta.Endpoints(masters, slaves, k1Locks, k2Locks, k1Taps, k2Taps, pads, lamps, opaque)).analyze();
        ClockTuner.Choice choice;
        try {
            choice = ClockTuner.tune(macro, sta);
        } catch (IllegalStateException e) {
            lvs.errors().add(e.getMessage());
            return null;
        }
        ClockTuner.apply(voxels, macro, choice.delays(), clockCell.x(), clockCell.y() - 1, clockCell.z());
        latches.put(clockCell.at(ClockMacro.LOCKED[0], 0, ClockMacro.LOCKED[1]), true);
        String report = String.format("clock period %d gt (logic %d, k1 skew %d-%d, k2 skew %d-%d, delays %s)",
            choice.period(), sta.logic(), sta.k1Min(), sta.k1Max(), sta.k2Min(), sta.k2Max(), choice.delays());
        return new Timing(choice.period(), choice.inputDelay(), clockCell.at(ClockMacro.TAP1[0], 0, ClockMacro.TAP1[1]), report);
    }

    private static int[] sizeOf(VoxelGrid voxels) {
        int[] bounds = voxels.bounds();
        return new int[]{bounds[3] - bounds[0] + 1, bounds[4] - bounds[1] + 1, bounds[5] - bounds[2] + 1};
    }
}
