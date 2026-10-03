package com.leclowndu93150.mcredstone.compiler.synth;

import com.leclowndu93150.mcredstone.compiler.netlist.Gate;
import com.leclowndu93150.mcredstone.compiler.netlist.GateKind;
import com.leclowndu93150.mcredstone.compiler.netlist.Netlist;
import com.leclowndu93150.mcredstone.compiler.netlist.Port;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NorMerge {
    public static final int MAX_INPUTS = 3;

    private NorMerge() {
    }

    public static Netlist run(Netlist netlist) {
        Netlist current = netlist;
        boolean changed = true;
        while (changed) {
            Netlist next = removeDoubleInversions(current);
            next = absorbOr(next);
            next = removeDead(next);
            changed = next.gates().size() != current.gates().size() || !sameInputs(next, current);
            current = next;
        }
        return current;
    }

    private static Netlist removeDoubleInversions(Netlist netlist) {
        int[] drivers = netlist.driverGates();
        int[] alias = new int[netlist.netCount()];
        for (int i = 0; i < alias.length; i++) {
            alias[i] = i;
        }
        for (Gate gate : netlist.gates()) {
            if (gate.kind() == GateKind.NOR && gate.inputs().length == 1 && drivers[gate.inputs()[0]] >= 0) {
                Gate inner = netlist.gates().get(drivers[gate.inputs()[0]]);
                if (inner.kind() == GateKind.NOR && inner.inputs().length == 1) {
                    alias[gate.output()] = inner.inputs()[0];
                }
            }
        }
        boolean any = false;
        for (int i = 0; i < alias.length; i++) {
            if (alias[i] != i) {
                any = true;
                break;
            }
        }
        if (!any) {
            return netlist;
        }
        List<Gate> gates = new ArrayList<>();
        for (Gate gate : netlist.gates()) {
            if (alias[gate.output()] != gate.output()) {
                continue;
            }
            Set<Integer> inputs = new LinkedHashSet<>();
            for (int input : gate.inputs()) {
                inputs.add(resolve(alias, input));
            }
            gates.add(new Gate(gate.kind(), toArray(inputs), gate.output()));
        }
        List<Port> outputs = new ArrayList<>();
        for (Port port : netlist.outputs()) {
            int[] bits = port.bits().clone();
            for (int i = 0; i < bits.length; i++) {
                bits[i] = resolve(alias, bits[i]);
            }
            outputs.add(new Port(port.name(), bits));
        }
        return netlist.with(outputs, gates);
    }

    private static int resolve(int[] alias, int net) {
        while (alias[net] != net) {
            net = alias[net];
        }
        return net;
    }

    private static Netlist absorbOr(Netlist netlist) {
        int[] drivers = netlist.driverGates();
        int[] fanouts = netlist.fanouts();
        List<Gate> gates = new ArrayList<>(netlist.gates());
        for (int g = 0; g < gates.size(); g++) {
            Gate gate = gates.get(g);
            if (gate.kind() != GateKind.NOR) {
                continue;
            }
            for (int input : gate.inputs()) {
                int inverter = drivers[input];
                if (inverter < 0 || fanouts[input] != 1) {
                    continue;
                }
                Gate not = gates.get(inverter);
                if (not.kind() != GateKind.NOR || not.inputs().length != 1 || drivers[not.inputs()[0]] < 0) {
                    continue;
                }
                Gate nor = gates.get(drivers[not.inputs()[0]]);
                if (nor.kind() != GateKind.NOR) {
                    continue;
                }
                Set<Integer> merged = new LinkedHashSet<>();
                for (int other : gate.inputs()) {
                    if (other != input) {
                        merged.add(other);
                    }
                }
                for (int inner : nor.inputs()) {
                    merged.add(inner);
                }
                if (merged.size() > MAX_INPUTS || merged.contains(gate.output())) {
                    continue;
                }
                gates.set(g, new Gate(gate.kind(), toArray(merged), gate.output()));
                return netlist.withGates(gates);
            }
        }
        return netlist;
    }

    private static Netlist removeDead(Netlist netlist) {
        List<Gate> gates = new ArrayList<>(netlist.gates());
        boolean removed = true;
        while (removed) {
            removed = false;
            Netlist probe = netlist.withGates(gates);
            int[] fanouts = probe.fanouts();
            List<Gate> live = new ArrayList<>();
            for (Gate gate : gates) {
                if (fanouts[gate.output()] > 0) {
                    live.add(gate);
                } else {
                    removed = true;
                }
            }
            gates = live;
        }
        return netlist.withGates(gates);
    }

    private static boolean sameInputs(Netlist a, Netlist b) {
        for (int i = 0; i < a.gates().size(); i++) {
            if (a.gates().get(i).inputs().length != b.gates().get(i).inputs().length) {
                return false;
            }
        }
        return true;
    }

    private static int[] toArray(Set<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }
}
