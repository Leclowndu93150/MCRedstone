package com.leclowndu93150.mcredstone.compiler.place;

import com.leclowndu93150.mcredstone.compiler.lib.ClockMacro;
import com.leclowndu93150.mcredstone.compiler.lib.Library;
import com.leclowndu93150.mcredstone.compiler.lib.Pin;
import com.leclowndu93150.mcredstone.compiler.lib.Rotation;
import com.leclowndu93150.mcredstone.compiler.lib.Template;
import com.leclowndu93150.mcredstone.compiler.netlist.DesignMeta;
import com.leclowndu93150.mcredstone.compiler.netlist.Gate;
import com.leclowndu93150.mcredstone.compiler.netlist.GateKind;
import com.leclowndu93150.mcredstone.compiler.netlist.IoPort;
import com.leclowndu93150.mcredstone.compiler.netlist.Netlist;
import com.leclowndu93150.mcredstone.compiler.netlist.Port;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class AnnealPlacer {
    public record Params(int logicTiers, int slotsX, int slotsZ, int pitch, int firstZ, int spareTiers, int margin, long seed, int tierStride) {
        public int slots() {
            return logicTiers * slotsX * slotsZ;
        }

        public int volume() {
            return (slotsX * pitch) * (slotsZ * pitch + firstZ) * (4 * (logicTiers * tierStride + spareTiers) + 3);
        }
    }

    public record Clocking(int holdNet, int k1Net, int k2Net, ClockMacro macro) {
    }

    private static final float TIER_WEIGHT = 4f;
    private static final int MAX_TEMPERATURE_STEPS = 300;
    private static final float CENTER = 1.5f;

    private final Netlist netlist;
    private final DesignMeta meta;
    private final Params params;
    private final Random random;
    private final List<PlacedCell> cells = new ArrayList<>();
    private final Map<Integer, float[]> fixedPositions = new HashMap<>();
    private final List<IoBit> inputs = new ArrayList<>();
    private final List<IoBit> outputs = new ArrayList<>();
    private final Map<Integer, List<float[]>> fixedSinks = new HashMap<>();
    private final Clocking clocking;
    private PlacedCell clockCell;
    private int zoneEnd;
    private int topIoTier;
    private int[][] netGates;
    private int[] slotOf;
    private int[] gateAt;
    private float[] netCost;
    private int[][] gateNets;

    public AnnealPlacer(Netlist netlist, DesignMeta meta, Params params, Clocking clocking) {
        this.netlist = netlist;
        this.meta = meta;
        this.params = params;
        this.clocking = clocking;
        this.random = new Random(params.seed());
    }

    public Floorplan place() {
        int gates = netlist.gates().size();
        if (gates > params.slots()) {
            throw new IllegalArgumentException("not enough slots");
        }
        int displayWidth = 0;
        for (IoPort io : meta.outputs()) {
            if (io.matrix()) {
                displayWidth = Math.max(displayWidth, cellWidth(io) * io.columns());
            }
        }
        placeIo(meta.inputs(), true, 0, displayWidth == 0 ? 0 : displayWidth + 2);
        int nextTier = 1;
        List<IoPort> lamps = new ArrayList<>();
        for (IoPort io : meta.outputs()) {
            if (io.matrix()) {
                int base = io.scale() == 1 ? nextTier : walkable(nextTier);
                placeMatrix(io, base);
                nextTier = base + io.rows() * io.scale();
            } else {
                lamps.add(io);
            }
        }
        int rows = nextTier - 1;
        int lampTier = walkable(rows > 0 ? rows + 1 : topLogicTier() + 2);
        topIoTier = lamps.isEmpty() ? rows : lampTier;
        placeIo(lamps, false, lampTier, 0);
        zoneEnd = rows > 0 ? params.firstZ() : 0;
        if (clocking != null) {
            placeClock();
        }
        indexNets();
        initialPlacement();
        anneal();
        return build();
    }

    private static int cellWidth(IoPort io) {
        if (io.scale() != 1 && io.scale() != 2) {
            throw new IllegalArgumentException(io.name() + ": lamp matrix scale must be 1 or 2");
        }
        return io.scale() == 1 ? Library.PIXEL_WIDTH : Library.BIG_PIXEL_WIDTH;
    }

    private void placeMatrix(IoPort io, int baseTier) {
        Port port = netlist.output(io.name());
        int columns = io.columns();
        int rows = io.rows();
        for (int bit = 0; bit < port.width(); bit++) {
            int row = bit / columns;
            int column = bit % columns;
            int slot = columns - 1 - column;
            int x = cellWidth(io) * slot;
            int tier = baseTier + io.scale() * (rows - 1 - row);
            int net = port.bits()[bit];
            boolean constant = net == Netlist.CONST0 || net == Netlist.CONST1;
            Template template = io.scale() == 2
                ? net == Netlist.CONST1 ? Library.BIG_PIXEL_LIT : Library.BIG_PIXEL
                : net == Netlist.CONST1 ? Library.PIXEL_LIT : slot % 2 == 0 ? Library.PIXEL_A : Library.PIXEL_B;
            Map<String, Integer> pins = constant ? Map.of() : Map.of(Library.IN, net);
            PlacedCell cell = new PlacedCell(cells.size(), template, x, tier, 0, pins, io.name() + "[" + bit + "]");
            cells.add(cell);
            outputs.add(new IoBit(io.name(), bit, cell.id(), io.kind(), null));
            if (!constant) {
                Pin pin = template.pin(Library.IN);
                fixedSinks.computeIfAbsent(net, k -> new ArrayList<>()).add(new float[]{x + pin.dx(), pin.dz(), TIER_WEIGHT * tier});
            }
        }
    }

    private void placeIo(List<IoPort> ports, boolean input, int tier, int startX) {
        int x = startX;
        for (int p = ports.size() - 1; p >= 0; p--) {
            IoPort io = ports.get(p);
            Port port = input ? netlist.input(io.name()) : netlist.output(io.name());
            for (int bit = 0; bit < port.width(); bit++) {
                int net = port.bits()[bit];
                if (!input && net == Netlist.CONST1) {
                    throw new IllegalStateException("output " + io.name() + "[" + bit + "] is constant high, not supported yet");
                }
                Template template = input ? Library.INPUT_PAD : Library.LED_OUT;
                String pin = input ? Library.OUT : Library.IN;
                Map<String, Integer> pins = net == Netlist.CONST0 ? Map.of() : Map.of(pin, net);
                PlacedCell cell = new PlacedCell(cells.size(), template, x, tier, 0, pins, io.name() + "[" + bit + "]");
                cells.add(cell);
                (input ? inputs : outputs).add(new IoBit(io.name(), bit, cell.id(), io.kind(), io.key()));
                if (net != Netlist.CONST0) {
                    float[] position = {x, 2, TIER_WEIGHT * tier};
                    if (input) {
                        fixedPositions.put(net, position);
                    } else {
                        fixedSinks.computeIfAbsent(net, k -> new ArrayList<>()).add(position);
                    }
                }
                x += 2;
            }
            x += 2;
        }
    }

    private void placeClock() {
        int x = 0;
        for (PlacedCell cell : cells) {
            x = Math.max(x, cell.x() + 2);
        }
        x = Math.max(x, params.slotsX() * params.pitch()) + 3;
        Template template = clocking.macro().template();
        clockCell = new PlacedCell(cells.size(), template, x, 0, 2,
            Map.of(ClockMacro.HOLD, clocking.holdNet(), ClockMacro.K1, clocking.k1Net(), ClockMacro.K2, clocking.k2Net()), "clock");
        cells.add(clockCell);
        Pin hold = template.pin(ClockMacro.HOLD);
        fixedSinks.computeIfAbsent(clocking.holdNet(), k -> new ArrayList<>()).add(new float[]{x + hold.dx(), 2 + hold.dz(), 0});
    }

    private void indexNets() {
        int netCount = netlist.netCount();
        List<List<Integer>> members = new ArrayList<>();
        for (int i = 0; i < netCount; i++) {
            members.add(new ArrayList<>());
        }
        List<List<Integer>> perGate = new ArrayList<>();
        for (int g = 0; g < netlist.gates().size(); g++) {
            Gate gate = netlist.gates().get(g);
            perGate.add(new ArrayList<>());
            members.get(gate.output()).add(g);
            for (int input : gate.inputs()) {
                members.get(input).add(g);
            }
        }
        netGates = new int[netCount][];
        for (int net = 0; net < netCount; net++) {
            netGates[net] = members.get(net).stream().distinct().mapToInt(Integer::intValue).toArray();
            for (int g : netGates[net]) {
                perGate.get(g).add(net);
            }
        }
        gateNets = new int[perGate.size()][];
        for (int g = 0; g < perGate.size(); g++) {
            gateNets[g] = perGate.get(g).stream().distinct().mapToInt(Integer::intValue).toArray();
        }
    }

    private void initialPlacement() {
        int gates = netlist.gates().size();
        slotOf = new int[gates];
        gateAt = new int[params.slots()];
        Arrays.fill(gateAt, -1);
        int[] levels = levels();
        Integer[] order = new Integer[gates];
        for (int g = 0; g < gates; g++) {
            order[g] = g;
        }
        Arrays.sort(order, (a, b) -> Integer.compare(levels[a], levels[b]));
        int[] slotOrder = slotOrder();
        for (int k = 0; k < gates; k++) {
            slotOf[order[k]] = slotOrder[k];
            gateAt[slotOrder[k]] = order[k];
        }
        netCost = new float[netlist.netCount()];
        for (int net = 0; net < netCost.length; net++) {
            netCost[net] = cost(net);
        }
    }

    private int[] slotOrder() {
        List<Integer> slots = new ArrayList<>();
        for (int j = 0; j < params.slotsZ(); j++) {
            for (int t = 0; t < params.logicTiers(); t++) {
                for (int i = 0; i < params.slotsX(); i++) {
                    slots.add(slot(i, j, t));
                }
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private int[] levels() {
        int[] netLevel = new int[netlist.netCount()];
        int[] levels = new int[netlist.gates().size()];
        for (int index : netlist.topologicalOrder()) {
            Gate gate = netlist.gates().get(index);
            int level = 0;
            for (int input : gate.inputs()) {
                level = Math.max(level, netLevel[input]);
            }
            levels[index] = level + 1;
            netLevel[gate.output()] = level + 1;
        }
        return levels;
    }

    private int slot(int i, int j, int t) {
        return (t * params.slotsZ() + j) * params.slotsX() + i;
    }

    private int slotI(int slot) {
        return slot % params.slotsX();
    }

    private int slotJ(int slot) {
        return (slot / params.slotsX()) % params.slotsZ();
    }

    private int slotT(int slot) {
        return slot / (params.slotsX() * params.slotsZ());
    }

    private float gateX(int slot) {
        return slotI(slot) * params.pitch() + CENTER;
    }

    private float gateZ(int slot) {
        return params.firstZ() + slotJ(slot) * params.pitch() + CENTER;
    }

    private int physicalTier(int slot) {
        return 2 * slotT(slot) * params.tierStride();
    }

    private int topLogicTier() {
        return 2 * (params.logicTiers() - 1) * params.tierStride();
    }

    private static int walkable(int tier) {
        return tier + Math.floorMod(tier, 2);
    }

    private float gateT(int slot) {
        return TIER_WEIGHT * physicalTier(slot);
    }

    private float cost(int net) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        float minT = Float.MAX_VALUE;
        float maxT = -Float.MAX_VALUE;
        int count = 0;
        float[] fixed = fixedPositions.get(net);
        if (fixed != null) {
            minX = maxX = fixed[0];
            minZ = maxZ = fixed[1];
            minT = maxT = fixed[2];
            count++;
        }
        List<float[]> sinks = fixedSinks.get(net);
        if (sinks != null) {
            for (float[] sink : sinks) {
                minX = Math.min(minX, sink[0]);
                maxX = Math.max(maxX, sink[0]);
                minZ = Math.min(minZ, sink[1]);
                maxZ = Math.max(maxZ, sink[1]);
                minT = Math.min(minT, sink[2]);
                maxT = Math.max(maxT, sink[2]);
                count++;
            }
        }
        for (int g : netGates[net]) {
            int slot = slotOf[g];
            float x = gateX(slot);
            float z = gateZ(slot);
            float t = gateT(slot);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            minT = Math.min(minT, t);
            maxT = Math.max(maxT, t);
            count++;
        }
        if (count < 2) {
            return 0;
        }
        return (maxX - minX) + (maxZ - minZ) + (maxT - minT);
    }

    private void anneal() {
        int gates = netlist.gates().size();
        if (gates < 2) {
            return;
        }
        float temperature = initialTemperature();
        float total = 0;
        int active = 0;
        for (float c : netCost) {
            total += c;
            if (c > 0) {
                active++;
            }
        }
        float floor = 0.005f * total / Math.max(1, active);
        int range = Math.max(params.slotsX(), Math.max(params.slotsZ(), params.logicTiers()));
        int movesPerTemperature = (int) Math.max(200, 10 * Math.pow(gates, 4.0 / 3.0));
        for (int step = 0; step < MAX_TEMPERATURE_STEPS && temperature > floor; step++) {
            int accepted = 0;
            for (int m = 0; m < movesPerTemperature; m++) {
                float delta = tryMove(range, temperature);
                if (!Float.isNaN(delta)) {
                    accepted++;
                }
            }
            float rate = accepted / (float) movesPerTemperature;
            if (rate > 0.96f) {
                temperature *= 0.5f;
            } else if (rate > 0.8f) {
                temperature *= 0.9f;
            } else if (rate > 0.15f) {
                temperature *= 0.95f;
            } else {
                temperature *= 0.8f;
            }
            range = Math.max(1, Math.min(range, Math.round(range * (1 - 0.44f + rate))));
        }
        for (int i = 0; i < movesPerTemperature; i++) {
            tryMove(1, 0);
        }
    }

    private float initialTemperature() {
        float sum = 0;
        float sumSquares = 0;
        int samples = Math.max(20, netlist.gates().size());
        for (int i = 0; i < samples; i++) {
            float delta = tryMove(Integer.MAX_VALUE, Float.MAX_VALUE);
            if (!Float.isNaN(delta)) {
                sum += delta;
                sumSquares += delta * delta;
            }
        }
        float mean = sum / samples;
        float deviation = (float) Math.sqrt(Math.max(0, sumSquares / samples - mean * mean));
        return 20 * Math.max(1, deviation);
    }

    private float tryMove(int range, float temperature) {
        int gates = netlist.gates().size();
        int g = random.nextInt(gates);
        int from = slotOf[g];
        int i = clamp(slotI(from) + random.nextInt(2 * Math.min(range, params.slotsX()) + 1) - Math.min(range, params.slotsX()), params.slotsX());
        int j = clamp(slotJ(from) + random.nextInt(2 * Math.min(range, params.slotsZ()) + 1) - Math.min(range, params.slotsZ()), params.slotsZ());
        int t = clamp(slotT(from) + random.nextInt(2 * Math.min(range, params.logicTiers()) + 1) - Math.min(range, params.logicTiers()), params.logicTiers());
        int to = slot(i, j, t);
        if (to == from) {
            return Float.NaN;
        }
        int other = gateAt[to];
        apply(g, other, from, to);
        float delta = 0;
        float[] saved = new float[gateNets[g].length + (other >= 0 ? gateNets[other].length : 0)];
        int[] touched = new int[saved.length];
        int count = 0;
        for (int net : gateNets[g]) {
            touched[count] = net;
            saved[count++] = netCost[net];
            float updated = cost(net);
            delta += updated - netCost[net];
            netCost[net] = updated;
        }
        if (other >= 0) {
            for (int net : gateNets[other]) {
                boolean seen = false;
                for (int k = 0; k < gateNets[g].length; k++) {
                    if (gateNets[g][k] == net) {
                        seen = true;
                        break;
                    }
                }
                if (seen) {
                    continue;
                }
                touched[count] = net;
                saved[count++] = netCost[net];
                float updated = cost(net);
                delta += updated - netCost[net];
                netCost[net] = updated;
            }
        }
        if (delta <= 0 || (temperature > 0 && random.nextFloat() < Math.exp(-delta / temperature))) {
            return delta;
        }
        apply(g, other, to, from);
        for (int k = 0; k < count; k++) {
            netCost[touched[k]] = saved[k];
        }
        return Float.NaN;
    }

    private void apply(int g, int other, int from, int to) {
        slotOf[g] = to;
        gateAt[to] = g;
        gateAt[from] = other;
        if (other >= 0) {
            slotOf[other] = from;
        }
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    private Floorplan build() {
        Map<Integer, float[]> driverPosition = new HashMap<>(fixedPositions);
        Map<Integer, List<float[]>> sinkPositions = new HashMap<>();
        for (Map.Entry<Integer, List<float[]>> entry : fixedSinks.entrySet()) {
            sinkPositions.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
        }
        for (int g = 0; g < netlist.gates().size(); g++) {
            Gate gate = netlist.gates().get(g);
            float[] center = {gateX(slotOf[g]), gateZ(slotOf[g]), gateT(slotOf[g])};
            driverPosition.put(gate.output(), center);
            for (int input : gate.inputs()) {
                sinkPositions.computeIfAbsent(input, k -> new ArrayList<>()).add(center);
            }
        }
        Map<Integer, PinRef> drivers = new HashMap<>();
        Map<Integer, List<PinRef>> sinks = new LinkedHashMap<>();
        for (IoBit io : inputs) {
            PlacedCell cell = cells.get(io.cell());
            Integer net = cell.pinNets().get(Library.OUT);
            if (net != null) {
                drivers.put(net, new PinRef(cell.id(), Library.OUT));
            }
        }
        for (int g = 0; g < netlist.gates().size(); g++) {
            Gate gate = netlist.gates().get(g);
            int slot = slotOf[g];
            int originX = slotI(slot) * params.pitch();
            int originZ = params.firstZ() + slotJ(slot) * params.pitch();
            boolean flipFlop = gate.kind() == GateKind.DFF;
            Template base = flipFlop ? Library.MSFF : Library.TNOR;
            List<String> inputPins = flipFlop ? List.of(Library.D) : Library.NOR_INPUTS;
            float[] outTarget = centroid(sinkPositions.get(gate.output()));
            Template best = null;
            int[] bestAssignment = null;
            float bestScore = Float.MAX_VALUE;
            for (int turns = 0; turns < 4; turns++) {
                Template template = Rotation.rotate(base, turns);
                Pin out = template.pin(Library.OUT);
                float score = outTarget == null ? 0 : distance(originX + out.dx(), originZ + out.dz(), outTarget);
                int[] assignment = assignInputs(gate, template, inputPins, originX, originZ, driverPosition);
                for (int k = 0; k < gate.inputs().length; k++) {
                    Pin pin = template.pin(inputPins.get(assignment[k]));
                    float[] driver = driverPosition.get(gate.inputs()[k]);
                    score += driver == null ? 0 : distance(originX + pin.dx(), originZ + pin.dz(), driver);
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = template;
                    bestAssignment = assignment;
                }
            }
            Map<String, Integer> pins = new LinkedHashMap<>();
            for (int k = 0; k < gate.inputs().length; k++) {
                if (gate.inputs()[k] != Netlist.CONST0) {
                    pins.put(inputPins.get(bestAssignment[k]), gate.inputs()[k]);
                }
            }
            pins.put(Library.OUT, gate.output());
            if (flipFlop) {
                pins.put(Library.K1, clocking.k1Net());
                pins.put(Library.K2, clocking.k2Net());
            }
            PlacedCell cell = new PlacedCell(cells.size(), best, originX, physicalTier(slot), originZ, pins, "g" + g);
            cells.add(cell);
            for (Map.Entry<String, Integer> pin : pins.entrySet()) {
                if (!pin.getKey().equals(Library.OUT)) {
                    sinks.computeIfAbsent(pin.getValue(), key -> new ArrayList<>()).add(new PinRef(cell.id(), pin.getKey()));
                }
            }
            drivers.put(gate.output(), new PinRef(cell.id(), Library.OUT));
        }
        if (clockCell != null) {
            drivers.put(clocking.k1Net(), new PinRef(clockCell.id(), ClockMacro.K1));
            drivers.put(clocking.k2Net(), new PinRef(clockCell.id(), ClockMacro.K2));
            sinks.computeIfAbsent(clocking.holdNet(), key -> new ArrayList<>()).add(new PinRef(clockCell.id(), ClockMacro.HOLD));
        }
        for (IoBit io : outputs) {
            PlacedCell cell = cells.get(io.cell());
            Integer net = cell.pinNets().get(Library.IN);
            if (net != null) {
                sinks.computeIfAbsent(net, k -> new ArrayList<>()).add(new PinRef(cell.id(), Library.IN));
            }
        }
        List<NetSpec> nets = new ArrayList<>();
        for (Map.Entry<Integer, List<PinRef>> entry : sinks.entrySet()) {
            PinRef source = drivers.get(entry.getKey());
            if (source == null) {
                throw new IllegalStateException("net " + entry.getKey() + " has readers but no driver");
            }
            nets.add(new NetSpec(nets.size(), entry.getKey(), source, entry.getValue()));
        }
        int maxX = 0;
        int maxZ = 0;
        for (PlacedCell cell : cells) {
            String name = cell.template().name();
            int extentX = cell == clockCell ? ClockMacro.WIDTH : name.startsWith("big_pixel") ? Library.BIG_PIXEL_WIDTH : 5;
            int extentZ = cell == clockCell ? ClockMacro.DEPTH : name.startsWith("big_pixel") ? Library.BIG_PIXEL_DEPTH : name.startsWith("pixel") ? Library.PIXEL_DEPTH : 5;
            maxX = Math.max(maxX, cell.x() + extentX);
            maxZ = Math.max(maxZ, cell.z() + extentZ);
        }
        int tiers = walkable(Math.max(topLogicTier(), topIoTier) + 1) + 2 * params.spareTiers();
        return new Floorplan(cells, nets, inputs, outputs, tiers, -params.margin(), maxX + params.margin(), 0, maxZ + params.margin(), zoneEnd);
    }

    private int[] assignInputs(Gate gate, Template template, List<String> inputPins, int originX, int originZ, Map<Integer, float[]> driverPosition) {
        int n = gate.inputs().length;
        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int[] permutation : PERMUTATIONS) {
            boolean valid = true;
            for (int k = 0; k < n; k++) {
                if (permutation[k] >= inputPins.size()) {
                    valid = false;
                }
            }
            if (!valid) {
                continue;
            }
            float score = 0;
            for (int k = 0; k < n; k++) {
                Pin pin = template.pin(inputPins.get(permutation[k]));
                float[] driver = driverPosition.get(gate.inputs()[k]);
                score += driver == null ? 0 : distance(originX + pin.dx(), originZ + pin.dz(), driver);
            }
            if (score < bestScore) {
                bestScore = score;
                best = Arrays.copyOf(permutation, n);
            }
        }
        return best;
    }

    private static final int[][] PERMUTATIONS = {
        {0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}
    };

    private static float distance(float x, float z, float[] target) {
        return Math.abs(x - target[0]) + Math.abs(z - target[1]);
    }

    private static float[] centroid(List<float[]> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        float x = 0;
        float z = 0;
        for (float[] point : points) {
            x += point[0];
            z += point[1];
        }
        return new float[]{x / points.size(), z / points.size()};
    }
}
